@echo off
setlocal
title AscendingMC - Survival
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -MaximumMemory 2G
exit /b %errorlevel%
