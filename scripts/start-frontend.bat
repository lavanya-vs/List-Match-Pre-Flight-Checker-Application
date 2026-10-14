@echo off
echo Starting List Match Pre-Flight Checker frontend...
echo Requires Node.js 18+ on PATH.
cd /d "%~dp0..\frontend"
if not exist node_modules (
  echo Installing dependencies...
  call npm install
)
call npm run dev
pause
