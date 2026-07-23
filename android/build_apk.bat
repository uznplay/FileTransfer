@echo off
REM ============================================
REM Build APK tự động (launcher cho build_apk.ps1)
REM ============================================
REM Yêu cầu: Java JDK 17+
REM ============================================

title Build APK - File Transfer Android

REM Kiểm tra quyền admin (recommended)
REM net session >nul 2>&1
REM if %errorLevel% NEQ 0 (
REM     echo [!] Nên chạy với quyền Administrator
REM )

cd /d "%~dp0"

REM Kiểm tra PowerShell
where powershell >nul 2>&1
if %errorLevel% NEQ 0 (
    echo [!] Không tìm thấy PowerShell!
    pause
    exit /b 1
)

REM Kiểm tra Java
where java >nul 2>&1
if %errorLevel% NEQ 0 (
    echo [!] Khong tim thay Java!
    echo.
    echo    Hay cai JDK 17 tu: https://adoptium.net/
    echo    Hoac chay: winget install EclipseAdoptium.Temurin.17.JDK
    pause
    exit /b 1
)

echo ========================================
echo    BUILD APK - File Transfer Android
echo ========================================
echo.
echo  Script nay se tu dong:
echo   1. Tai Android SDK
echo   2. Cai dat SDK components
echo   3. Tai Gradle wrapper
echo   4. Build file APK
echo.
echo  Thoi gian: 3-10 phut (lan dau)
echo.

CHOICE /C YN /M "Bat dau build?"
if errorlevel 2 exit /b

echo.
powershell -ExecutionPolicy Bypass -File "%~dp0build_apk.ps1"
echo.
pause
