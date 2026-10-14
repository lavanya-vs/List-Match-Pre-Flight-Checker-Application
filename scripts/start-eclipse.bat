@echo off
echo ============================================
echo  List Match Pre-Flight Checker - Eclipse
echo ============================================
echo.
echo I cannot click Eclipse for you, but follow these steps:
echo.
echo 1. File -^> Switch Workspace -^> Other
echo    Use: C:\Users\u1230722\eclipse-workspace-preflight
echo    (NOT the project folder itself)
echo.
echo 2. File -^> Import -^> General -^> Existing Projects into Workspace
echo    Root directory:
echo    %~dp0..\backend
echo    Check "backend" -^> Finish
echo.
echo 3. Run: Right-click PreflightApplication.java
echo    Run As -^> Java Application
echo    OR use Run Configurations -^> PreflightApplication
echo.
echo 4. Open browser: http://localhost:8085
echo.
echo Launch file saved at: backend\PreflightApplication.launch
echo ============================================
echo.

set "ECLIPSE="
for %%P in (
  "C:\eclipse\eclipse.exe"
  "C:\Program Files\Eclipse\eclipse.exe"
  "C:\Program Files\Eclipse Adoptium\eclipse.exe"
  "%LOCALAPPDATA%\Programs\Eclipse\eclipse.exe"
) do if exist %%P set "ECLIPSE=%%~P"

if defined ECLIPSE (
  echo Found Eclipse: %ECLIPSE%
  echo Opening Eclipse with separate workspace...
  if not exist "C:\Users\u1230722\eclipse-workspace-preflight" mkdir "C:\Users\u1230722\eclipse-workspace-preflight"
  start "" "%ECLIPSE%" -data "C:\Users\u1230722\eclipse-workspace-preflight"
) else (
  echo Eclipse not found automatically. Open Eclipse manually, then follow steps above.
)

pause
