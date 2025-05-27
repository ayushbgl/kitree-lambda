#!/bin/bash

# Function to check if a service is ready
wait_for_service() {
    local host=$1
    local port=$2
    local max_attempts=30
    local attempt=1
    
    echo "Waiting for service at $host:$port..."
    while [ $attempt -le $max_attempts ]; do
        if nc -z $host $port; then
            echo "Service at $host:$port is ready!"
            return 0
        fi
        echo "Attempt $attempt/$max_attempts: Service not ready yet..."
        sleep 2
        attempt=$((attempt + 1))
    done
    echo "Service at $host:$port failed to start after $max_attempts attempts"
    return 1
}

# Start Firebase emulators and test runner
echo "Starting Firebase emulators and test runner..."
docker-compose up -d firebase-emulator

# Wait for emulators to be ready
echo "Waiting for emulators to be ready..."
wait_for_service localhost 8080 || exit 1  # Firestore
wait_for_service localhost 9099 || exit 1  # Auth

# Additional verification for Auth emulator
echo "Verifying Auth emulator..."
for i in {1..5}; do
    if curl -s -X POST http://localhost:9099/identitytoolkit/v3/relyingparty/signupNewUser \
        -H "Content-Type: application/json" \
        -d '{"email":"test@example.com","password":"password123"}' > /dev/null; then
        echo "Auth emulator is responding"
        break
    fi
    if [ $i -eq 5 ]; then
        echo "Auth emulator failed to respond"
        exit 1
    fi
    echo "Waiting for Auth emulator to respond... (attempt $i/5)"
    sleep 2
done

# Additional verification for Firestore emulator
echo "Verifying Firestore emulator..."
for i in {1..5}; do
    if curl -s http://localhost:8080/emulator/v1/projects/kitree-emulator/databases/\(default\)/documents > /dev/null; then
        echo "Firestore emulator is responding"
        break
    fi
    if [ $i -eq 5 ]; then
        echo "Firestore emulator failed to respond"
        exit 1
    fi
    echo "Waiting for Firestore emulator to respond... (attempt $i/5)"
    sleep 2
done

# Run the tests in Docker
echo "Running tests in Docker..."
docker-compose up test-runner

# Capture test result
TEST_RESULT=$?

# Stop emulators
echo "Stopping emulators..."
docker-compose down

# Exit with test result
exit $TEST_RESULT 