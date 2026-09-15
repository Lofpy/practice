@echo off
setlocal
title Poppy Network
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-network.ps1"
exit /b %errorlevel%
