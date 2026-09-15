@echo off
setlocal
title Poppy Network - Velocity
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
exit /b %errorlevel%
