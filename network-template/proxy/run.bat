@echo off
setlocal
title AscendingMC - Velocity
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
exit /b %errorlevel%
