@echo off
setlocal
set PATH=%PATH%;%ProgramFiles(x86)%\Microsoft Visual Studio\2017\BuildTools\MSBuild\15.0\bin\amd64
set VCTargetsPath=C:\Program Files (x86)\MSBuild\Microsoft.Cpp\v4.0\V140
set BUIDROOT=%cd%

:getopts
if "%2"=="" (
	set configuration="Release"
) else (
	set configuration=%2%
)


if "%1"=="" (goto :default) else (goto :%1)
goto :exit

:default
goto :build

:clean
echo ### Cleaning the %configuration% build directory
cd %BUIDROOT%\native
msbuild winp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="arm64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="arm64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="arm64"
if %errorlevel% neq 0 exit /b %errorlevel%
goto :exit

:build
echo ### Building the %configuration% configuration
cd %BUIDROOT%\native
REM /verbosity:minimal
msbuild winp.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="arm64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="arm64"
if %errorlevel% neq 0 exit /b %errorlevel%

echo ### Building test applications
msbuild ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=%configuration% /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=%configuration% /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=%configuration% /nologo /p:Platform="arm64"
if %errorlevel% neq 0 exit /b %errorlevel%

:exit
endlocal
