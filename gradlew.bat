@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem  MakayWallpaper Gradle Wrapper (standart)
@rem  - Argumansiz: assembleDebug
@rem  - Argumanli: verilen Gradle gorevleri
@rem ##########################################################################

if "%OS%"=="Windows_NT" setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Client JVM. --no-daemon iken build de bu heap'i kullanir.
set DEFAULT_JVM_OPTS="-Xmx4096m" "-Xms256m" "-XX:MaxMetaspaceSize=768m" "-Dfile.encoding=UTF-8"

if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo.
echo [HATA] JAVA_HOME ayarli degil ve PATH'te java yok.
echo        JDK 17+ kurup JAVA_HOME tanimlayin.
echo.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo.
echo [HATA] JAVA_HOME gecersiz: %JAVA_HOME%
echo.
goto fail

:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
    echo [HATA] gradle-wrapper.jar bulunamadi: %CLASSPATH%
    goto fail
)

if "%~1"=="" (
    echo.
    echo [MakayWallpaper] Arguman yok - assembleDebug calistiriliyor...
    echo.
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain assembleDebug
) else (
    "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
)

goto end

:fail
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
exit /b %EXIT_CODE%

:end
if %ERRORLEVEL% neq 0 goto fail
if "%OS%"=="Windows_NT" endlocal
exit /b 0
