#!/bin/bash

echo "Stopping Java processes..."
pkill -f "spring-boot:run"

echo "Stopping and removing Docker containers..."
docker stop assets-postgres assets-rabbitmq
docker rm assets-postgres assets-rabbitmq

echo "All services stopped!"