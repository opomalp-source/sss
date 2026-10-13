@echo off
rem Dragon Block Zenith auto-update: installs the newest build now, then checks every 10 minutes in the background.
set "DIR=%APPDATA%\.minecraft-dbz"
if not exist "%DIR%" mkdir "%DIR%"
echo Downloading the updater...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://raw.githubusercontent.com/opomalp-source/sss/builds/dbz-update.ps1' -OutFile '%DIR%\dbz-update.ps1' -UseBasicParsing"
if errorlevel 1 goto failed
echo Installing the newest Dragon Block Zenith...
powershell -NoProfile -ExecutionPolicy Bypass -File "%DIR%\dbz-update.ps1" -Force
schtasks /Create /F /SC MINUTE /MO 10 /TN "DBZ Zenith auto-update" /TR "powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File \"%DIR%\dbz-update.ps1\"" >nul
if errorlevel 1 goto failed
echo.
echo Done. The newest build is in your mods folder, and it will update itself every 10 minutes.
echo (If Minecraft is open during an update, it waits and tries again later. Log: %DIR%\dbz-update.log)
echo To stop auto-updates: schtasks /Delete /TN "DBZ Zenith auto-update" /F
pause
exit /b 0
:failed
echo Something went wrong. Check your internet connection and try again.
pause
exit /b 1
