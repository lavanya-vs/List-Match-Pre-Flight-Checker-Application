@echo off
echo Starting List Match Pre-Flight Checker backend...
echo Requires Java 11 on PATH.
echo Backend will run on http://localhost:8085
cd /d "%~dp0..\backend"

for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8085 " ^| findstr LISTENING') do (
  echo Stopping process on port 8085 PID %%a
  taskkill /F /PID %%a >nul 2>&1
)

set "MAVEN_CMD="
for /f "delims=" %%m in ('where /r "%USERPROFILE%\.m2\wrapper\dists" mvn.cmd 2^>nul') do (
  if not defined MAVEN_CMD set "MAVEN_CMD=%%m"
)
if not defined MAVEN_CMD (
  where mvn >nul 2>&1 && set "MAVEN_CMD=mvn"
)
if not defined MAVEN_CMD (
  echo ERROR: Maven 3.8 or newer was not found.
  echo Install Maven or add mvn to PATH, then run this script again.
  pause
  exit /b 1
)

echo Using Maven: %MAVEN_CMD%
set "MAVEN_OPTS=-Xmx4g"
call "%MAVEN_CMD%" spring-boot:run
pause
