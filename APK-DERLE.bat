@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo === Hal Fiyatlari APK derleme ===
echo.

if not exist "%USERPROFILE%\android-sdk" (
  echo HATA: %USERPROFILE%\android-sdk bulunamadi. Android SDK yolunu bu dosyada duzenle.
  pause & exit /b 1
)
if not exist "%USERPROFILE%\gradle-8.9\bin\gradle.bat" (
  echo HATA: %USERPROFILE%\gradle-8.9\bin\gradle.bat bulunamadi.
  pause & exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo HATA: Java bulunamadi. JDK 17 veya 21 kurulu olmali; Android Studio kuruluysa
  echo       onun icindeki JDK yolunu JAVA_HOME olarak ayarla.
  pause & exit /b 1
)

set "ANDROID_HOME=%USERPROFILE%\android-sdk"
set "ANDROID_SDK_ROOT=%USERPROFILE%\android-sdk"

echo Uygulama fiyatlari GitHub'daki data klasorunden okur.
set /p GHUSER=GitHub kullanici adin (ornek: ahmet123):
if "%GHUSER%"=="" (
  echo Kullanici adi bos olamaz.
  pause & exit /b 1
)
set "DATAURL=https://raw.githubusercontent.com/%GHUSER%/hal-fiyatlari/main/data"
echo Veri adresi: %DATAURL%
echo.

echo Lisanslar kabul ediliyor (SDK yoksa gerekli parcalar indirilecek)...
if exist "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" (
  (for /l %%i in (1,1,30) do @echo y) | "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" --licenses >nul 2>nul
)

call "%USERPROFILE%\gradle-8.9\bin\gradle.bat" assembleDebug --no-daemon "-PDATA_BASE_URL=%DATAURL%"
if errorlevel 1 (
  echo.
  echo DERLEME HATASI. Yukaridaki kirmizi/hata satirlarini Claude'a yapistir.
  pause & exit /b 1
)

copy /y "app\build\outputs\apk\debug\app-debug.apk" "%USERPROFILE%\Desktop\HalFiyatlari.apk" >nul
echo.
echo TAMAM! APK Masaustunde: HalFiyatlari.apk
echo Telefona gonderip kur ("bilinmeyen kaynaklardan yukleme" izni gerekir).
pause
