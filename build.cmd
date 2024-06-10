setlocal

set VS2019=C:\Program Files (x86)\Microsoft Visual Studio\2019
set VS=

if exist "%VS2019%\Community\" set VS=%VS2019%\Community
if exist "%VS2019%\Enterprise\" set VS=%VS2019%\Enterprise
if "%VS%" == "" (
    echo Can't find VS2019 install
    exit /b 1
)

call "%VS%/VC/Auxiliary/Build/vcvarsall.bat" amd64 || exit /b 1
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
msbuild winp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Arm64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Arm64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /t:Clean /p:Configuration=%configuration% /verbosity:minimal /nologo /p:Platform="Arm64"
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
msbuild winp.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="Arm64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild sendctrlc\sendctrlc.vcxproj /p:Configuration=%configuration% /nologo /p:Platform="Arm64"
if %errorlevel% neq 0 exit /b %errorlevel%

echo ### Building test applications
msbuild ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=%configuration% /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=%configuration% /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild ..\native_test\testapp\testapp.vcxproj /verbosity:minimal /p:Configuration=%configuration% /nologo /p:Platform="Arm64"
if %errorlevel% neq 0 exit /b %errorlevel%

echo ### Copying WinP resource files for the %configuration% build
cd %BUIDROOT%
if not exist target\classes mkdir target\classes
COPY native\Win32\%configuration%\winp.dll target\classes\
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\x64\%configuration%\winp.x64.dll target\classes\
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\Arm64\%configuration%\winp.arm64.dll target\classes\
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\sendctrlc\Win32\%configuration%\sendctrlc.exe target\classes\
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\sendctrlc\x64\%configuration%\sendctrlc.x64.exe target\classes\
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\sendctrlc\Arm64\%configuration%\sendctrlc.arm64.exe target\classes\
if %errorlevel% neq 0 exit /b %errorlevel%
goto :exit

:exit
endlocal
