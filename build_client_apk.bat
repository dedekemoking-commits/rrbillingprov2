@echo off
setlocal
set "CLIENT_DIR=%~dp0..\BillingPSkuDesktop\android_tv_client"
set "ASSET_DIR=%~dp0app\src\main\assets"

if not exist "%CLIENT_DIR%\gradlew.bat" (
    echo Client project not found:
    echo   %CLIENT_DIR%
    echo Update CLIENT_DIR in this script if the RRBilling TV source moved.
    pause
    exit /b 1
)

echo Building RRBilling TV Client APK...
pushd "%CLIENT_DIR%"
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo Build failed!
    popd
    pause
    exit /b 1
)
popd

echo Copying APK to billing app assets...
copy /Y "%CLIENT_DIR%\app\build\outputs\apk\debug\app-debug.apk" "%ASSET_DIR%\tv-client.apk"

echo Done! RRBilling TV Client APK built and copied.
pause
