@echo off
setlocal
rem iCoordinator - copy the app's data (database) from the phone to this PC, WITHOUT changing anything on the phone.
rem Needs: Android platform-tools (adb.exe) and USB debugging turned on in the phone's Developer options.
cd /d "%~dp0"

set "ADB=adb"
where adb >nul 2>nul
if errorlevel 1 (
  if exist "%~dp0platform-tools\adb.exe" (
    set "ADB=%~dp0platform-tools\adb.exe"
  ) else (
    echo [ERROR] adb.exe not found.
    echo Download "SDK Platform-Tools for Windows" from developer.android.com/tools/releases/platform-tools
    echo and unzip it here so that this file exists: %~dp0platform-tools\adb.exe
    goto :end
  )
)

echo === Phone check ===
"%ADB%" start-server >nul 2>nul
"%ADB%" get-state 1>nul 2>nul
if errorlevel 1 (
  echo [ERROR] No phone found. Connect the phone with USB, turn on USB debugging,
  echo and tap "Allow" on the phone when it asks about this computer. Then run this file again.
  goto :end
)

"%ADB%" shell pm list packages com.icoordinator | findstr /c:"package:com.icoordinator" >nul
if errorlevel 1 (
  echo [ERROR] The app com.icoordinator is not installed on this phone.
  goto :end
)

rem the app must not be writing while we copy
"%ADB%" shell am force-stop com.icoordinator

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%i"
set "OUT=%~dp0handover\rescue\%TS%"
mkdir "%OUT%"

echo === Copying database files to %OUT% ===
"%ADB%" exec-out run-as com.icoordinator cat databases/icoordinator.db > "%OUT%\icoordinator.db"
"%ADB%" exec-out run-as com.icoordinator cat databases/icoordinator.db-wal > "%OUT%\icoordinator.db-wal" 2>nul
"%ADB%" exec-out run-as com.icoordinator cat databases/icoordinator.db-shm > "%OUT%\icoordinator.db-shm" 2>nul
"%ADB%" shell run-as com.icoordinator ls -l databases > "%OUT%\phone-file-list.txt" 2>&1

echo.
dir "%OUT%"
echo.
echo Done. Nothing on the phone was changed. Tell Claude: "rescue done" and the folder name %TS%.
echo DO NOT uninstall the old app yet.

:end
echo.
pause
