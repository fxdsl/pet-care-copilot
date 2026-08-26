@echo off
title Stop RabbitMQ

echo Stopping RabbitMQ safely, please wait...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-rabbitmq.ps1"
set "SCRIPT_EXIT=%ERRORLEVEL%"

if not "%SCRIPT_EXIT%"=="0" (
    echo.
    echo RabbitMQ failed to stop. Please send this error window to Codex.
) else (
    echo.
    echo RabbitMQ stopped safely.
)

echo.
pause
exit /b %SCRIPT_EXIT%
