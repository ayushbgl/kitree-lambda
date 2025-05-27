#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print status messages
print_status() {
    echo -e "${YELLOW}[STATUS]${NC} $1"
}

# Function to print success messages
print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Function to print error messages
print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker is not running. Starting Docker..."
        open -a Docker
        # Wait for Docker to start
        while ! docker info > /dev/null 2>&1; do
            sleep 2
        done
        print_success "Docker started successfully"
    else
        print_success "Docker is running"
    fi
}

# Function to cleanup Docker resources
cleanup() {
    print_status "Cleaning up Docker resources..."
    docker-compose down -v
    if [ $? -eq 0 ]; then
        print_success "Docker resources cleaned up successfully"
    else
        print_error "Failed to cleanup Docker resources"
        exit 1
    fi
}

# Function to wait for test completion
wait_for_test_completion() {
    local container_name="kitree-lambda-test-runner-1"
    local max_attempts=300  # 5 minutes timeout
    local attempt=1

    print_status "Waiting for test completion..."
    while [ $attempt -le $max_attempts ]; do
        if ! docker ps | grep -q $container_name; then
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    print_error "Test runner did not complete within timeout"
    return 1
}

# Main execution
main() {
    # Check if Docker is running
    check_docker

    # Cleanup existing resources
    cleanup

    # Build and start containers in detached mode
    print_status "Building and starting containers..."
    docker-compose up --build -d
    if [ $? -ne 0 ]; then
        print_error "Failed to start containers"
        cleanup
        exit 1
    fi

    # Follow logs of the test runner
    print_status "Following test logs..."
    docker-compose logs -f test-runner &
    LOG_PID=$!

    # Wait for test completion
    wait_for_test_completion
    TEST_RESULT=$?

    # Kill the log following process
    kill $LOG_PID 2>/dev/null

    # Get the exit code from the test runner container
    EXIT_CODE=$(docker-compose ps -q test-runner | xargs docker inspect -f '{{.State.ExitCode}}')

    # Cleanup after tests
    cleanup

    # Check the exit code
    if [ "$EXIT_CODE" -eq 0 ]; then
        print_success "Tests completed successfully"
        exit 0
    else
        print_error "Tests failed with exit code $EXIT_CODE"
        exit $EXIT_CODE
    fi
}

# Trap SIGINT (Ctrl+C) and cleanup
trap cleanup SIGINT

# Run main function
main 