@echo off
setlocal
title AscendingMC - Lobby
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -MaximumMemory 1G
exit /b %errorlevel%
