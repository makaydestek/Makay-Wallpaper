@echo off
:: MakayWallpaper Gradle Wrapper
:: Run: gradlew.bat [task]

setlocal

:: Set Gradle user home
set GRADLE_USER_HOME=%HOME%

:: Find the gradle wrapper jar relative to this script
set WRAPPER_DIR=%~dp0\gradle\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar

:: Check if jar exists
if not exist "%WRAPPER_JAR%" (
    echo [HATA] gradle-wrapper.jar bulunamadi: %WRAPPER_JAR%
    echo [Cozum] Indy gradle wrapper jar dosyasini yerine koyun.
    goto end
)

:: Run Gradle
if "%~1"=="" (
    echo.
    echo [MakayWallpaper] Arguman yok - assembleDebug calistiriliyor...
    echo.
    java -jar "%WRAPPER_JAR%" assembleDebug
) else (
    java -jar "%WRAPPER_JAR%" %*
)

:end