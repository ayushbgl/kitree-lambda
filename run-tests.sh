#!/bin/bash

# Start Firebase emulators
echo "Starting Firebase emulators..."
docker-compose up -d

# Wait for emulators to be ready
echo "Waiting for emulators to be ready..."
sleep 10

# Run the tests
echo "Running tests..."
./gradlew test

# Stop emulators
echo "Stopping emulators..."
docker-compose down 