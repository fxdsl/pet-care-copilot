@echo off
title Start RabbitMQ

echo Starting RabbitMQ, please wait...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-rabbitmq.ps1"
set "SCRIPT_EXIT=%ERRORLEVEL%"

if not "%SCRIPT_EXIT%"=="0" (
    echo.
    echo RabbitMQ failed to start. Please send this error window to Codex.
) else (
    echo.
    echo RabbitMQ is ready.
    echo Management UI: http://localhost:15672
)

echo.
pause
exit /b %SCRIPT_EXIT%
