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

# Microsoft.VisualStudio.DevShell.dll provides Enter-VsDevShell, which initialises
# the developer environment (PATH, INCLUDE, LIB, WindowsSdkDir, …) directly in
# the current PowerShell process. This is the VS 2017+ supported PowerShell API
# and avoids the subprocess quoting issues and VsDevCmd.bat SDK-detection failures
# that occur on some VS 2025 Build Tools installations.
$global:VSDEVSHELL_DLL = Join-Path $global:VSINSTALLDIR 'Common7\Tools\Microsoft.VisualStudio.DevShell.dll'
if (-not (Test-Path $global:VSDEVSHELL_DLL)) {
    Write-Error "Microsoft.VisualStudio.DevShell.dll not found at $global:VSDEVSHELL_DLL"
    exit 1
}

# Track which arch the dev environment is currently initialised for so we only
# call Enter-VsDevShell when the architecture actually changes.
$global:VSDEVENV_ARCH = $null

Write-Host "MSBUILD=${global:MSBUILD}"
Write-Host "VSINSTALLDIR=${global:VSINSTALLDIR}"
Write-Host "VSDEVSHELL_DLL=${global:VSDEVSHELL_DLL}"

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

# Initialise the VS developer environment for the given arch in the current process.
# Enter-VsDevShell sets PATH, INCLUDE, LIB, WindowsSdkDir, and all other variables
# that MSBuild needs, without spawning a cmd subprocess or running VsDevCmd.bat.
function Initialize-VsDevEnvironment {
    param([string]$Arch)

    if ($global:VSDEVENV_ARCH -eq $Arch) { return }

    if (-not (Get-Module Microsoft.VisualStudio.DevShell -ErrorAction SilentlyContinue)) {
        Import-Module $global:VSDEVSHELL_DLL
    }

    Write-Host "Entering VS dev shell: arch=$Arch"
    Enter-VsDevShell -VsInstallPath $global:VSINSTALLDIR -SkipAutomaticLocation `
        -DevCmdArguments "-arch=$Arch -no_logo"
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
