@echo off
echo ============================================
echo  List Match Pre-Flight Checker
echo ============================================
echo.
echo Starting backend (Spring Boot) in a new window...
start "Preflight Backend" cmd /k "%~dp0scripts\start-backend.bat"
echo.
echo Open http://localhost:8085 in your browser.
echo Backend must show "Started PreflightApplication" before uploading.
echo.
pause
