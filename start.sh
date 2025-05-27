#!/bin/bash

# Start Firebase emulator in the background
firebase emulators:start --project kitree-emulator --only auth,firestore --import=/firebase-data --export-on-exit=/firebase-data &

# Wait for emulator to be ready
echo "Waiting for Firebase emulator to start..."
until curl -s http://localhost:8080 > /dev/null; do
    sleep 1
done
echo "Firebase emulator is ready!"

# Run the tests
./gradlew test 