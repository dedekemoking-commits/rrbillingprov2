@echo off
echo Building TV Client APK...

cd tv-client
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Copying APK to billing app assets...
copy /Y app\build\outputs\apk\debug\app-debug.apk ..\app\src\main\assets\tv-client.apk

echo Done! TV Client APK built and copied.
cd ..
pause
