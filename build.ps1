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

# Fallback SDK path set by Ensure-WindowsSDK when the system SDK is absent.
# The RSP section in Invoke-MSBuild reads these if vcvarsall did not populate
# WindowsSDKDir (because the registry key was missing or unwritable).
$global:WINSDK_FALLBACK_DIR = $null
$global:WINSDK_FALLBACK_VER = $null

# Ensure the Windows SDK headers and import libs are accessible for MSBuild.
# On VS 2025 Build Tools the vsconfig may install only the UCRT redistribution
# component (Windows10SDK.22621) instead of the full SDK (Windows11SDK.22621),
# leaving no headers or import libs.  When that is the case this function
# installs the Microsoft.Windows.SDK.CPP NuGet packages and creates a standard
# SDK directory layout under C:\winsdk\layout\ using directory junctions so
# that MSBuild can resolve all include and library paths.  No writes to
# system-owned directories are required; the layout path is stored in
# $global:WINSDK_FALLBACK_DIR so Invoke-MSBuild can pass it via RSP.
function Ensure-WindowsSDK {
    $sdkVer  = '10.0.22621.0'
    $pkgVer  = '10.0.22621.3233'
    $marker  = "${env:ProgramFiles(x86)}\Windows Kits\10\Include\$sdkVer\um\windows.h"

    if (Test-Path $marker) {
        Write-Host "Windows SDK $sdkVer already present at system path"
        return
    }

    $nugetDir  = 'C:\winsdk'
    $layoutDir = "$nugetDir\layout"
    $layoutMarker = "$layoutDir\Include\$sdkVer\um\windows.h"

    if (Test-Path $layoutMarker) {
        Write-Host "Windows SDK $sdkVer already present at $layoutDir"
        $global:WINSDK_FALLBACK_DIR = "$layoutDir\"
        $global:WINSDK_FALLBACK_VER = $sdkVer
        return
    }

    Write-Host "Windows SDK headers missing - installing via NuGet packages..."

    # Locate or download nuget.exe
    $nugetCmd = Get-Command nuget.exe -ErrorAction SilentlyContinue
    $nuget = if ($nugetCmd) { $nugetCmd.Source } else { $null }
    if (-not $nuget) {
        $nuget = "$env:TEMP\nuget.exe"
        if (-not (Test-Path $nuget)) {
            Write-Host "Downloading nuget.exe..."
            Invoke-WebRequest -Uri 'https://dist.nuget.org/win-x86-commandline/latest/nuget.exe' `
                              -OutFile $nuget -UseBasicParsing
        }
    }

    New-Item -ItemType Directory -Path $nugetDir -Force | Out-Null

    foreach ($pkg in @('Microsoft.Windows.SDK.CPP',
                        'Microsoft.Windows.SDK.CPP.x86',
                        'Microsoft.Windows.SDK.CPP.x64')) {
        if (-not (Test-Path "$nugetDir\$pkg.$pkgVer")) {
            Write-Host "Installing $pkg $pkgVer..."
            & $nuget install $pkg -Version $pkgVer -OutputDirectory $nugetDir -NonInteractive
            if ($LASTEXITCODE -ne 0) {
                Write-Error "NuGet install failed for $pkg"
                exit $LASTEXITCODE
            }
        }
    }

    $mainPkg = "$nugetDir\Microsoft.Windows.SDK.CPP.$pkgVer\c"
    $x86Pkg  = "$nugetDir\Microsoft.Windows.SDK.CPP.x86.$pkgVer\c"
    $x64Pkg  = "$nugetDir\Microsoft.Windows.SDK.CPP.x64.$pkgVer\c"

    # Build a standard SDK directory tree under layoutDir using junctions.
    # All targets are within C:\winsdk\ which the build agent can write to.
    New-Item -ItemType Directory -Path "$layoutDir\Include"           -Force | Out-Null
    New-Item -ItemType Directory -Path "$layoutDir\Lib\$sdkVer\um"   -Force | Out-Null
    New-Item -ItemType Directory -Path "$layoutDir\Lib\$sdkVer\ucrt" -Force | Out-Null

    $junctions = @(
        @{ Link = "$layoutDir\Include\$sdkVer";       Target = "$mainPkg\Include\$sdkVer" },
        @{ Link = "$layoutDir\DesignTime";             Target = "$mainPkg\DesignTime"       },
        @{ Link = "$layoutDir\bin";                    Target = "$mainPkg\bin"              },
        @{ Link = "$layoutDir\Lib\$sdkVer\um\x86";   Target = "$x86Pkg\um\x86"   },
        @{ Link = "$layoutDir\Lib\$sdkVer\um\x64";   Target = "$x64Pkg\um\x64"   },
        @{ Link = "$layoutDir\Lib\$sdkVer\ucrt\x86"; Target = "$x86Pkg\ucrt\x86" },
        @{ Link = "$layoutDir\Lib\$sdkVer\ucrt\x64"; Target = "$x64Pkg\ucrt\x64" }
    )

    foreach ($j in $junctions) {
        if (-not (Test-Path $j.Link)) {
            New-Item -ItemType Junction -Path $j.Link -Target $j.Target | Out-Null
            Write-Host "Junction: $($j.Link) -> $($j.Target)"
        }
    }

    $global:WINSDK_FALLBACK_DIR = "$layoutDir\"
    $global:WINSDK_FALLBACK_VER = $sdkVer
    Write-Host "Windows SDK $sdkVer layout created at $layoutDir"
}

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

        # Pass the SDK location and version to MSBuild explicitly so it can
        # construct the correct include/lib paths without registry lookups.
        # vcvarsall.bat sets WindowsSDKDir when it finds the SDK via registry;
        # on agents where that key is absent we fall back to the layout created
        # by Ensure-WindowsSDK.  Either way we write the values to an MSBuild
        # response file to avoid trailing-backslash quoting issues: inside a
        # double-quoted /p: argument a path ending in \ makes \" escape the
        # closing quote and corrupts all subsequent arguments.  RSP files use
        # CommandLineToArgvW quoting so we double the trailing backslash ("\\")
        # so the parser sees one literal backslash and the quote closes normally.
        # Determine the SDK dir and version to pass via RSP.
        # Prefer the values vcvarsall.bat populated, but only if the headers are
        # actually present there; on agents missing the full SDK component,
        # WindowsSDKVersion may be set to a bare backslash or a path with no
        # headers, in which case we fall back to the NuGet layout built by
        # Ensure-WindowsSDK.
        $rspSdkDir = $env:WindowsSDKDir
        $rspSdkVer = if ($env:WindowsSDKVersion) { $env:WindowsSDKVersion.TrimEnd('\') } else { $null }
        $headersPresent = $rspSdkDir -and $rspSdkVer -and `
            (Test-Path (Join-Path $rspSdkDir.TrimEnd('\') "Include\$rspSdkVer\um\windows.h"))
        if (-not $headersPresent) {
            $rspSdkDir = $global:WINSDK_FALLBACK_DIR
            $rspSdkVer = $global:WINSDK_FALLBACK_VER
        }

        if ($rspSdkDir -and $rspSdkVer) {
            $sdkDirNoSlash = $rspSdkDir.TrimEnd('\')
            # Always set UniversalCRTSdkDir to the same layout as WindowsSdkDir.
            # vcvarsall.bat sets it to the system SDK path which (when only the
            # Windows10SDK.22621 UCRT redist component is installed) has no
            # Include\<ver>\ucrt\ctype.h; the fallback NuGet layout does.
            $rspLines = "/p:WindowsSdkDir=`"$sdkDirNoSlash\\`"`n" +
                        "/p:WindowsTargetPlatformVersion=$rspSdkVer`n" +
                        "/p:UniversalCRTSdkDir=`"$sdkDirNoSlash\\`""
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

# Ensure the Windows SDK is available before any MSBuild invocation
Ensure-WindowsSDK

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
