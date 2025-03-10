@echo off
setlocal

rem Get the directory where the script is located
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..

echo Starting PostgreSQL container...
docker run -d --name assets-postgres -e POSTGRES_DB=assets_manager -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:latest

echo Starting RabbitMQ container...
docker run -d --name assets-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management

echo Waiting for services to start...
timeout /t 10 /nobreak

rem Create logs directory if it doesn't exist
if not exist "%PROJECT_ROOT%\logs" mkdir "%PROJECT_ROOT%\logs"

echo Starting web module...
start /B cmd /c "cd "%PROJECT_ROOT%\web" && mvnw spring-boot:run -Dspring-boot.run.profiles=dev > "%PROJECT_ROOT%\logs\web.log" 2>&1"

echo Starting worker module...
start /B cmd /c "cd "%PROJECT_ROOT%\worker" && mvnw spring-boot:run -Dspring-boot.run.profiles=dev > "%PROJECT_ROOT%\logs\worker.log" 2>&1"

echo All services started! Check logs directory for output.
echo Web application: http://localhost:8080
echo Worker application: http://localhost:8081
echo RabbitMQ Management: http://localhost:15672 (guest/guest)