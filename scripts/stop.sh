#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

echo "Stopping Java processes..."
pkill -f "spring-boot:run"

echo "Stopping and removing Docker containers..."
docker stop assets-postgres assets-rabbitmq
docker rm assets-postgres assets-rabbitmq

echo "All services stopped!"