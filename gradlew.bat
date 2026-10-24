@echo off
:: MakayWallpaper Gradle Wrapper
:: Calistirma: gradlew.bat [gorev]

setlocal

:: JDK yolunu tanımla
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

:: Gradle wrapper jar'in yolunu belirle
set WRAPPER_JAR=gradle\wrapper\gradle-wrapper.jar

:: Kontrol: jar var mi?
if not exist "%WRAPPER_JAR%" (
    echo [HATA] gradle-wrapper.jar bulunamadi: %WRAPPER_JAR%
    echo [Cozum] Indy gradle-wrapper.jar dosyasini proje klasorune koyun.
    goto end
)

:: Gradle'i calistir
if "%~1"=="" (
    echo.
    echo [MakayWallpaper] Arguman yok - assembleDebug calistiriliyor...
    echo.
    java -jar "%WRAPPER_JAR%" assembleDebug
) else (
    java -jar "%WRAPPER_JAR%" %*
)

:end
endlocal