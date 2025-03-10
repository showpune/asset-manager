@echo off
setlocal

rem Get the directory where the script is located
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..

echo Stopping Java processes...

rem Check if web PID file exists and stop the process
if exist "%PROJECT_ROOT%\pids\web.pid" (
    set /p WEB_PID=<"%PROJECT_ROOT%\pids\web.pid"
    echo Stopping web module with PID: %WEB_PID%
    taskkill /F /PID %WEB_PID%
    del "%PROJECT_ROOT%\pids\web.pid"
) else (
    echo Web PID file not found, the service may not be running.
)

rem Check if worker PID file exists and stop the process
if exist "%PROJECT_ROOT%\pids\worker.pid" (
    set /p WORKER_PID=<"%PROJECT_ROOT%\pids\worker.pid"
    echo Stopping worker module with PID: %WORKER_PID%
    taskkill /F /PID %WORKER_PID%
    del "%PROJECT_ROOT%\pids\worker.pid"
) else (
    echo Worker PID file not found, the service may not be running.
)

echo Stopping and removing Docker containers...
docker stop assets-postgres assets-rabbitmq 2>nul
docker rm assets-postgres assets-rabbitmq 2>nul

echo All services stopped!