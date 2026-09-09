[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Command,

    [Parameter(Position = 1)]
    [string]$Configuration = "Debug",

    [Parameter(Position = 2)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$global:BUILDROOT = Get-Location

# Find MSBuild
Write-Host "Locating MSBuild..."
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) {
    Write-Error "vswhere.exe not found at $vswhere"
    exit 1
}

$global:MSBUILD = & $vswhere -latest -products * `
    -requires Microsoft.Component.MSBuild `
    -find "MSBuild\**\Bin\MSBuild.exe" | Select-Object -First 1

if (-not $global:MSBUILD) {
    Write-Error "MSBuild not found"
    exit 1
}

$vsPath = & $vswhere -latest -products * `
    -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
    -property installationPath | Select-Object -First 1

$global:VSINSTALLDIR = $vsPath
if (-not $global:VSINSTALLDIR) {
    Write-Error "Visual Studio with VC tools not found"
    exit 1
}

# vcvarsall.bat is the core VC toolset script that sets PATH, INCLUDE, LIB and
# WindowsSDKDir for a given target architecture.  Unlike VsDevCmd.bat or
# Enter-VsDevShell (which rely on VS developer-shell infrastructure that can be
# absent in Build-Tools-only installations), vcvarsall.bat is always present
# when the VC.Tools.x86.x64 component is installed and returns a real non-zero
# exit code on failure.
$global:VCVARSALL = Join-Path $global:VSINSTALLDIR 'VC\Auxiliary\Build\vcvarsall.bat'
if (-not (Test-Path $global:VCVARSALL)) {
    Write-Error "vcvarsall.bat not found: $global:VCVARSALL"
    exit 1
}

# Track which arch the VC environment is currently set up for so we only
# call vcvarsall.bat when the architecture actually changes.
$global:VSDEVENV_ARCH = $null

Write-Host "MSBUILD=${global:MSBUILD}"
Write-Host "VSINSTALLDIR=${global:VSINSTALLDIR}"
Write-Host "VCVARSALL=${global:VCVARSALL}"

# Determine version
if ([string]::IsNullOrEmpty($Version)) {
    Write-Host "No target version specified, will determine it from POM"
    try {
        $ErrorActionPreference = "SilentlyContinue"
        $versionOutput = & mvn -q -Dexec.executable="cmd.exe" -Dexec.args='/c echo ${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:3.5.0:exec 2>&1 > $null
        $Version = $versionOutput | Where-Object { $_ -notmatch '^\s*WARNING' -and $_ -notmatch '^\[' } | Where-Object { $_.Trim() } | Select-Object -Last 1
    } catch {
        Write-Error "Failed to extract version from POM: $_"
        exit 1
    } finally {
        $ErrorActionPreference = "Stop"
    }
} else {
    Write-Host "Setting MVN project version to the externally defined $Version"
}

Write-Host "Target version is $Version"

# Set up the VC build environment for the given target architecture by running
# vcvarsall.bat in a cmd subprocess, capturing the resulting environment via
# "set", then applying the variables to the current PowerShell process.
# This is more reliable than VsDevCmd.bat or Enter-VsDevShell on VS 2025 Build
# Tools, which silently fail to discover the Windows SDK on some configurations.
function Initialize-VsDevEnvironment {
    param([string]$Arch)

    if ($global:VSDEVENV_ARCH -eq $Arch) { return }

    Write-Host "Setting up VC build environment via vcvarsall.bat: arch=$Arch"

    $tmpBat = [System.IO.Path]::ChangeExtension([System.IO.Path]::GetTempFileName(), '.bat')
    try {
        # Dump env after calling vcvarsall.bat; propagate its exit code.
        $batContent = "@echo off`r`ncall `"$($global:VCVARSALL)`" $Arch > nul 2>&1`r`nif errorlevel 1 exit /b %errorlevel%`r`nset"
        [System.IO.File]::WriteAllText($tmpBat, $batContent)

        $output = & $env:ComSpec /d /c "`"$tmpBat`"" 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Error "vcvarsall.bat ($Arch) failed with exit code $LASTEXITCODE"
            exit $LASTEXITCODE
        }

        foreach ($line in $output) {
            if ($line -match '^([^=]+)=(.*)$') {
                [System.Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
            }
        }
        Write-Host "VC environment ready. WindowsSDKDir=$env:WindowsSDKDir"
    } finally {
        Remove-Item $tmpBat -Force -ErrorAction SilentlyContinue
    }

    $global:VSDEVENV_ARCH = $Arch
}

# Run MSBuild for a set of project files after setting up the dev environment.
function Invoke-MSBuild {
    param(
        [string[]]$ProjectPaths,
        [string]$Arch,
        [string]$Platform,
        [string]$Configuration,
        [string]$Target = $null,
        [string]$Verbosity = "minimal"
    )

    if (-not $Arch) {
        switch ($Platform) {
            "Win32" { $Arch = "x86" }
            "x64"   { $Arch = "x64" }
            default {
                Write-Error "Unable to infer dev shell architecture for platform '$Platform'"
                exit 1
            }
        }
    }

    Initialize-VsDevEnvironment -Arch $Arch

    foreach ($projectPath in $ProjectPaths) {
        $absPath = if ([System.IO.Path]::IsPathRooted($projectPath)) {
            $projectPath
        } else {
            Join-Path (Get-Location) $projectPath
        }

        $msbuildArgs = @(
            $absPath,
            '/m', '/nologo', "/verbosity:$Verbosity",
            "/p:Configuration=$Configuration",
            "/p:Platform=$Platform"
        )

        # When the environment has WindowsSDKDir set (from vcvarsall.bat) but
        # MSBuild may not auto-detect the SDK version from the registry — e.g.
        # on VS 2025 Build Tools where only the UCRT redist component is
        # installed — pass the SDK location and version explicitly so MSBuild
        # can construct the correct include/lib paths without registry lookups.
        if ($env:WindowsSDKDir -and $env:WindowsSDKVersion) {
            $sdkVer = $env:WindowsSDKVersion.TrimEnd('\')
            # Paths ending in a backslash break Windows command-line quoting
            # inside double-quoted MSBuild /p: arguments (the backslash escapes
            # the closing quote, corrupting all subsequent args).  Avoid this by
            # writing the properties to an MSBuild response file and passing it
            # with @file.  RSP files use CommandLineToArgvW quoting, so paths
            # with spaces must be double-quoted and the trailing backslash must
            # be doubled ("\\") so the parser sees one literal backslash and the
            # following double-quote closes the token.
            $sdkDirNoSlash = $env:WindowsSDKDir.TrimEnd('\')
            $rspLines = "/p:WindowsSdkDir=`"$sdkDirNoSlash\\`"`n" +
                        "/p:WindowsTargetPlatformVersion=$sdkVer"
            if ($env:UniversalCRTSdkDir) {
                $ucrtDirNoSlash = $env:UniversalCRTSdkDir.TrimEnd('\')
                $rspLines += "`n/p:UniversalCRTSdkDir=`"$ucrtDirNoSlash\\`""
            }
            $rspFile = [System.IO.Path]::ChangeExtension([System.IO.Path]::GetTempFileName(), '.rsp')
            [System.IO.File]::WriteAllText($rspFile, $rspLines)
            $msbuildArgs += "@$rspFile"
        }

        if ($Target) { $msbuildArgs += "/t:$Target" }

        Write-Host "Running MSBuild: $(Split-Path $absPath -Leaf) platform=$Platform target=$Target"
        & $global:MSBUILD @msbuildArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Error "MSBuild failed with exit code $LASTEXITCODE"
            exit $LASTEXITCODE
        }
    }
}

# Clean function
function Invoke-Clean {
    Write-Host "### Cleaning the $Configuration build directory"
    Push-Location "$global:BUILDROOT\native"
    try {
        Invoke-MSBuild -ProjectPaths @("winp.vcxproj", "sendctrlc\sendctrlc.vcxproj", "..\native_test\testapp\testapp.vcxproj") -Platform "Win32" -Arch "x86" -Configuration $Configuration -Target "Clean"
        Invoke-MSBuild -ProjectPaths @("winp.vcxproj", "sendctrlc\sendctrlc.vcxproj", "..\native_test\testapp\testapp.vcxproj") -Platform "x64" -Arch "x64" -Configuration $Configuration -Target "Clean"
    } finally {
        Pop-Location
    }
}

# Build function
function Invoke-Build {
    Write-Host "### Building the $Configuration configuration"
    Push-Location "$global:BUILDROOT\native"
    try {
        Invoke-MSBuild -ProjectPaths @("winp.vcxproj", "sendctrlc\sendctrlc.vcxproj", "..\native_test\testapp\testapp.vcxproj") -Platform "Win32" -Arch "x86" -Configuration $Configuration
        Invoke-MSBuild -ProjectPaths @("winp.vcxproj", "sendctrlc\sendctrlc.vcxproj", "..\native_test\testapp\testapp.vcxproj") -Platform "x64" -Arch "x64" -Configuration $Configuration
    } finally {
        Pop-Location
    }

    Write-Host "### Updating WinP resource files for the $Configuration build"
    Set-Location $global:BUILDROOT

    $resourceDir = "src\main\resources"
    if (-not (Test-Path $resourceDir)) {
        New-Item -ItemType Directory -Path $resourceDir -Force | Out-Null
    }

    $filesToCopy = @(
        @{ Source = "native\$Configuration\winp.dll"; Dest = "$resourceDir\winp.dll" },
        @{ Source = "native\x64\$Configuration\winp.dll"; Dest = "$resourceDir\winp.x64.dll" },
        @{ Source = "native\sendctrlc\Win32\$Configuration\sendctrlc.exe"; Dest = "$resourceDir\sendctrlc.exe" },
        @{ Source = "native\sendctrlc\x64\$Configuration\sendctrlc.exe"; Dest = "$resourceDir\sendctrlc.x64.exe" }
    )

    foreach ($file in $filesToCopy) {
        if (-not (Test-Path $file.Source)) {
            Write-Error "Source file not found: $($file.Source)"
            exit 1
        }
        Copy-Item -Path $file.Source -Destination $file.Dest -Force
        Write-Host "Copied $($file.Source) to $($file.Dest)"
    }
}

# Main dispatch
switch ($Command) {
    "clean" { Invoke-Clean }
    "build" { Invoke-Build }
    ""      { Invoke-Build }  # Default to build
    default {
        Write-Host "Unknown command: $Command"
        Write-Host "Valid commands: clean, build"
        exit 1
    }
}

Write-Host "Build completed successfully"
