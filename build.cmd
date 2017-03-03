@echo off
setlocal
set PATH=%PATH%;%ProgramFiles(x86)%\MSBuild\12.0\Bin\;
set BUIDROOT=%cd%

:getopts
if "%1"=="" (goto :default) else (goto :%1)
goto :exit

:default
goto :cleanbuild

:cleanbuild
echo "### Cleaning the build directory"
cd %BUIDROOT%\native
msbuild winp.vcxproj /t:Clean /p:Configuration=Release /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /t:Clean /p:Configuration=Release /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /t:Clean /p:Configuration=Debug /verbosity:minimal /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /t:Clean /p:Configuration=Debug /verbosity:minimal /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
cd %BUIDROOT%
mvn clean
if %errorlevel% neq 0 exit /b %errorlevel%
goto :build

:build
echo "### Building project configurations"
cd %BUIDROOT%\native
REM /verbosity:minimal
msbuild winp.vcxproj /p:Configuration=Release /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /p:Configuration=Release /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /p:Configuration=Debug /nologo /p:Platform="Win32"
if %errorlevel% neq 0 exit /b %errorlevel%
msbuild winp.vcxproj /p:Configuration=Debug /nologo /p:Platform="x64"
if %errorlevel% neq 0 exit /b %errorlevel%

echo "### Updating WinP resource files"
cd %BUIDROOT%
COPY native\Release\winp.dll src\main\resources\winp.dll
if %errorlevel% neq 0 exit /b %errorlevel%
COPY native\x64\Release\winp.dll src\main\resources\winp.x64.dll
if %errorlevel% neq 0 exit /b %errorlevel%

echo "### Build WinP"
cd %BUIDROOT%
mvn package
if %errorlevel% neq 0 exit /b %errorlevel%

goto :exit

:test
cd %BUIDROOT%
mvn verify
if %errorlevel% neq 0 exit /b %errorlevel%
goto :exit

:exit
endlocal