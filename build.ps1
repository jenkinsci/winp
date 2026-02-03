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
$BUILDROOT = Get-Location

# Find MSBuild
Write-Host "Locating MSBuild..."
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) {
    Write-Error "vswhere.exe not found at $vswhere"
    exit 1
}

$MSBUILD = & $vswhere -products * -latest -requires Microsoft.Component.MSBuild -find "MSBuild\**\Bin\MSBuild.exe" | Select-Object -First 1

if (-not $MSBUILD) {
    Write-Error "MSBuild not found"
    exit 1
}

Write-Host "MSBUILD=$MSBUILD"

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

# Helper function to run MSBuild with error checking
function Invoke-MSBuild {
    param(
        [string]$Project,
        [string]$Platform,
        [string]$Target = $null,
        [string]$Verbosity = "minimal"
    )
    
    $args = @(
        $Project,
        "/p:Configuration=$Configuration",
        "/p:Platform=$Platform",
        "/verbosity:$Verbosity",
        "/nologo"
    )
    
    if ($Target) {
        $args = @("/t:$Target") + $args
    }
    
    Write-Host "Running: & '$MSBUILD' $($args -join ' ')"
    & $MSBUILD @args
    if ($LASTEXITCODE -ne 0) {
        Write-Error "MSBuild failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
}

# Clean function
function Invoke-Clean {
    Write-Host "### Cleaning the $Configuration build directory"
    Push-Location "$BUILDROOT\native"
    
    Invoke-MSBuild "winp.vcxproj" "Win32" "Clean"
    Invoke-MSBuild "winp.vcxproj" "x64" "Clean"
    Invoke-MSBuild "sendctrlc\sendctrlc.vcxproj" "Win32" "Clean"
    Invoke-MSBuild "sendctrlc\sendctrlc.vcxproj" "x64" "Clean"
    Invoke-MSBuild "..\native_test\testapp\testapp.vcxproj" "Win32" "Clean"
    Invoke-MSBuild "..\native_test\testapp\testapp.vcxproj" "x64" "Clean"
    
    Pop-Location
}

# Build function
function Invoke-Build {
    Write-Host "### Building the $Configuration configuration"
    Push-Location "$BUILDROOT\native"
    
    Invoke-MSBuild "winp.vcxproj" "Win32"
    Invoke-MSBuild "winp.vcxproj" "x64"
    Invoke-MSBuild "sendctrlc\sendctrlc.vcxproj" "Win32"
    Invoke-MSBuild "sendctrlc\sendctrlc.vcxproj" "x64"
    
    Write-Host "### Building test applications"
    Invoke-MSBuild "..\native_test\testapp\testapp.vcxproj" "Win32" $null "minimal"
    Invoke-MSBuild "..\native_test\testapp\testapp.vcxproj" "x64" $null "minimal"
    
    Pop-Location
    
    Write-Host "### Updating WinP resource files for the $Configuration build"
    Set-Location $BUILDROOT
    
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
    "" { Invoke-Build }  # Default to build
    default { 
        Write-Host "Unknown command: $Command"
        Write-Host "Valid commands: clean, build"
        exit 1
    }
}

Write-Host "Build completed successfully"
