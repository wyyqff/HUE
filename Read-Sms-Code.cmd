@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0local\read-sms-code.ps1"
pause
