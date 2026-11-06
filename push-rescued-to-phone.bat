@echo off
setlocal
rem iCoordinator - copy the rescued data file to the phone's Download folder (for Settings > restore).
cd /d "%~dp0"
set "ADB=adb"
where adb >nul 2>nul
if errorlevel 1 set "ADB=%~dp0platform-tools\adb.exe"
"%ADB%" get-state 1>nul 2>nul
if errorlevel 1 (
  echo [ERROR] No phone found. Connect it with USB debugging on, then run again.
  goto :end
)
"%ADB%" push "%~dp0handover\rescue\iCoordinator-rescued-20261106.db" /sdcard/Download/iCoordinator-rescued-20261106.db
if errorlevel 1 (
  echo [ERROR] Copy failed.
  goto :end
)
echo.
echo Done. On the phone the file is in Download: iCoordinator-rescued-20261106.db
:end
echo.
pause
