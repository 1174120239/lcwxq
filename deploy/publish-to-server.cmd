@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-to-server.ps1" %*
exit /b %ERRORLEVEL%
