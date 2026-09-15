@echo off
setlocal
title PoppyPractice - WindSpigot

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -MaximumMemory 4G
if errorlevel 1 (
    echo.
    echo Server startup failed. See the error above.
    pause
)
