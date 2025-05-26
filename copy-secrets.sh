#!/bin/bash

# Create the resources directory if it doesn't exist
mkdir -p src/main/resources

# Copy the secret files from the sibling directory
cp ../kitree-secrets/kitree-lambda/secrets.json src/main/resources/
cp ../kitree-secrets/kitree-lambda/serviceAccountKey.json src/main/resources/
cp ../kitree-secrets/kitree-lambda/serviceAccountKeyTest.json src/main/resources/

echo "Secret files have been copied to src/main/resources/" 