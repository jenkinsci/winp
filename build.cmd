@echo off
setlocal enabledelayedexpansion
set BUIDROOT=%cd%

dir /w "%ProgramFiles(x86)%"
dir /w "%ProgramFiles(x86)%\Microsoft Visual Studio"
dir /w "%ProgramFiles(x86)%\Microsoft Visual Studio\Installer"


for /f "usebackq tokens=*" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -requires Microsoft.Component.MSBuild -find MSBuild\**\Bin\MSBuild.exe`) do (
  set MSBUILD="%%i"
)

echo "MSBUILD=%MSBUILD%"

:getopts
if "%2"=="" (
	set configuration="Release"
) else (
	set configuration=%2%
)

if "%3"=="" (
	echo No target version specified, will determine it from POM
	REM TODO: Apply some MADSKILLZ to do it without the temporary file?
	call mvn -q -Dexec.executable="cmd.exe" -Dexec.args="/c echo ${project.version}" --non-recursive org.codehaus.mojo:exec-maven-plugin:3.5.0:exec > version.txt
	for /f "delims=" %%x in (version.txt) do set version=%%x
) else (
	echo Setting MVN project version to the externally defined %3%
	set version=%3%	
)
echo Target version is %version%

if "%1"=="" (goto :default) else (goto :%1)
goto :exit

:default
goto :build

:clean
echo ### Cleaning the %configuration% build directory
cd %BUIDROOT%\native
%MSBUILD% winp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% winp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=Release /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=Release /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=Release /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=Release /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
goto :exit

:build
echo ### Building the %configuration% configuration
cd %BUIDROOT%\native
REM /verbosity:minimal
%MSBUILD% winp.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% winp.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%

echo ### Building test applications
%MSBUILD% ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=Release /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
%MSBUILD% ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=Release /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%

echo ### Updating WinP resource files for the %configuration% build
cd %BUIDROOT%
mkdir src\main\resources
COPY native\%configuration%\winp.dll src\main\resources\winp.dll
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\x64\%configuration%\winp.dll src\main\resources\winp.x64.dll
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\sendctrlc\Win32\%configuration%\sendctrlc.exe src\main\resources\sendctrlc.exe
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\sendctrlc\x64\%configuration%\sendctrlc.exe src\main\resources\sendctrlc.x64.exe
if %errorlevel% neq 0 exit /b %errorlevel%

:exit
endlocal
