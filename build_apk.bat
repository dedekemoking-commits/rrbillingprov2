@echo off
SETLOCAL

echo ============================================
echo  Building BillingPS ATV APK
echo ============================================
echo.

:: Check for local.properties
if not exist local.properties (
    echo [WARNING] local.properties not found!
    echo Creating from template...
    copy local.properties.template local.properties
    echo.
    echo [ACTION REQUIRED] Edit local.properties and set your sdk.dir path.
    echo Example: sdk.dir=C:\Users\dedek\AppData\Local\Android\Sdk
    echo.
    pause
    exit /b 1
)

:: Check for Java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java not found! Install JDK 11+ and set JAVA_HOME.
    pause
    exit /b 1
)

:: Build debug APK
echo [1/2] Cleaning old builds...
call gradlew clean

echo [2/2] Building debug APK...
call gradlew assembleDebug

if %ERRORLEVEL% equ 0 (
    echo.
    echo ============================================
    echo  BUILD SUCCESS!
    echo.
    echo  APK location: app\build\outputs\apk\debug\
    echo  File: app-debug.apk
    echo ============================================
) else (
    echo.
    echo ============================================
    echo  BUILD FAILED! Check errors above.
    echo ============================================
)

pause
