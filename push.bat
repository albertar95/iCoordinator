@echo off
setlocal
rem iCoordinator - commit all changes and push to GitHub (GitHub Actions then builds the app)
cd /d "%~dp0"

where git >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Git is not installed or not in PATH.
  goto :end
)

rem Keep big downloaded asset files out of GitHub
findstr /x /c:"handover/assets/" .gitignore >nul 2>nul
if errorlevel 1 (
  echo handover/assets/>> .gitignore
  echo Added handover/assets/ to .gitignore
)

echo.
echo === Changes ===
git status --short
echo.

git add -A
git diff --cached --quiet
if not errorlevel 1 (
  echo Nothing new to commit.
  goto :push
)

set "MSG="
set /p MSG=Commit message (Enter = automatic): 
if "%MSG%"=="" set "MSG=Update %date% %time:~0,5%"
git commit -m "%MSG%"
if errorlevel 1 (
  echo [ERROR] Commit failed.
  goto :end
)

:push
echo.
echo === Pushing to GitHub ===
git push
if errorlevel 1 (
  echo.
  echo [ERROR] Push failed. If it says "rejected", run: git pull --rebase   and then run this file again.
  goto :end
)

echo.
echo Done. The build is now running in the Actions tab.
start "" "https://github.com/albertar95/iCoordinator/actions"

:end
echo.
pause
