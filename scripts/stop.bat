@echo off
echo Stopping Java processes...
wmic process where "commandline like '%%spring-boot:run%%'" call terminate

echo Stopping and removing Docker containers...
docker stop assets-postgres assets-rabbitmq
docker rm assets-postgres assets-rabbitmq

echo All services stopped!