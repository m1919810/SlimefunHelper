@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

set "WRAPPER_PROPS=%APP_HOME%\gradle\wrapper\gradle-wrapper.properties"
set "LOCAL_GRADLE_ZIP="
for /f "usebackq tokens=1,* delims==" %%a in ("%WRAPPER_PROPS%") do if /i "%%a"=="localDistributionUrl" set "LOCAL_GRADLE_ZIP=%%b"
set "CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar"
set "TEMP_WRAPPER="
set "EXIT_CODE="

if not defined LOCAL_GRADLE_ZIP goto wrapperReady
for %%i in ("%LOCAL_GRADLE_ZIP%") do set "LOCAL_GRADLE_ZIP=%%~fi"
if not exist "%LOCAL_GRADLE_ZIP%" goto wrapperReady

set "TEMP_WRAPPER=%TEMP%\gradle-wrapper-%RANDOM%-%RANDOM%"
mkdir "%TEMP_WRAPPER%"
if errorlevel 1 goto fail
copy /y "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" "%TEMP_WRAPPER%\gradle-wrapper.jar" >NUL
if errorlevel 1 goto fail
copy /y "%WRAPPER_PROPS%" "%TEMP_WRAPPER%\gradle-wrapper.properties" >NUL
if errorlevel 1 goto fail
set "WRAPPER_TEMP_PROPS=%TEMP_WRAPPER%\gradle-wrapper.properties"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$props = $env:WRAPPER_TEMP_PROPS; $url = [Uri]::new($env:LOCAL_GRADLE_ZIP).AbsoluteUri; $content = [IO.File]::ReadAllText($props); if ($content -notmatch '(?m)^distributionUrl=') { throw 'distributionUrl not found in temporary wrapper properties' }; $updated = [regex]::Replace($content, '(?m)^distributionUrl=.*$', ('distributionUrl=' + $url)); [IO.File]::WriteAllText($props, $updated, [Text.Encoding]::ASCII)"
if errorlevel 1 goto fail
set "CLASSPATH=%TEMP_WRAPPER%\gradle-wrapper.jar"

:wrapperReady
@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
set "EXIT_CODE=%ERRORLEVEL%"
if defined TEMP_WRAPPER if exist "%TEMP_WRAPPER%\" rmdir /s /q "%TEMP_WRAPPER%" >NUL 2>&1
if %EXIT_CODE% equ 0 goto mainEnd
goto fail

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if not defined EXIT_CODE set "EXIT_CODE=%ERRORLEVEL%"
if defined TEMP_WRAPPER if exist "%TEMP_WRAPPER%\" rmdir /s /q "%TEMP_WRAPPER%" >NUL 2>&1
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
@rem End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal

:omega
