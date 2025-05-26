#!/bin/bash

# Configuration variables
DEPLOYMENT_BUCKET="kitree-backend"
FIREBASE_PROJECT_ID_TEST="kitree-test"
FIREBASE_PROJECT_ID_PROD="mirai-1111"
FIREBASE_TEST_DIR="firebase-test"
FIREBASE_PROD_DIR="firebase-prod"
# Define the logical resource ID of your Lambda function in template.yaml
LAMBDA_RESOURCE_NAME="JavaFunction" # <-- Make sure this matches your template.yaml
PACKAGED_TEMPLATE_FILE=".aws-sam/build/packaged.yaml" # <-- Output of 'sam package' in this script

# Color definitions
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Error handling
# set -e # Disable immediate exit on error to allow cleanup
trap 'error_handler $? $LINENO "$BASH_COMMAND"' ERR # Keep trap for detailed error logging

error_handler() {
    local exit_code=$1
    local line_no=$2
    local command=$3

    echo -e "${RED}--------------------- ERROR ---------------------${NC}" >&2
    echo -e "${RED}Error on line $line_no: Command exited with status $exit_code${NC}" >&2
    echo -e "${RED}Command: $command${NC}" >&2
    echo -e "${RED}-----------------------------------------------${NC}" >&2

    # Continue script execution for cleanup
    # Note: The exit code is captured later after cleanup
}


# --- Helper Functions (print_status, check_prerequisites, verify_project_structure, Firebase funcs, select_environment) ---
# (Keep these functions as they were in the previous version,
#  ensure check_prerequisites still checks for aws, sam, firebase, and warns about yq)
print_status() {
    echo -e "${BLUE}--- $1 ---${NC}"
}
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}
print_error() {
    echo -e "${RED}✗ $1${NC}"
}
print_warning() {
    echo -e "${YELLOW}! $1${NC}"
}
check_prerequisites() {
    print_status "Checking prerequisites"
    local error_found=0
    if ! command -v sam &> /dev/null; then print_error "AWS SAM CLI is not installed."; error_found=1; fi
    if ! command -v firebase &> /dev/null; then print_error "Firebase CLI is not installed."; error_found=1; fi
    if [ ! -f "./gradlew" ]; then print_error "Gradle wrapper not found."; error_found=1; fi
    if ! command -v aws &> /dev/null; then print_error "AWS CLI is not installed."; error_found=1; fi
    if ! command -v yq &> /dev/null; then print_warning "'yq' command not found. Automatic S3 artifact cleanup will be skipped."; fi # Just warn
    if [ $error_found -ne 0 ]; then exit 1; fi
    print_success "Prerequisites checked."
}
verify_project_structure() {
    print_status "Verifying project structure"
    PROJECT_ROOT=$(pwd); TEMPLATE_FILE="$PROJECT_ROOT/template.yaml"; BUILD_GRADLE="$PROJECT_ROOT/build.gradle"
    if [ ! -f "$TEMPLATE_FILE" ]; then print_error "Template file not found at: $TEMPLATE_FILE"; exit 1; fi
    if [ ! -f "$BUILD_GRADLE" ]; then print_error "build.gradle not found at: $BUILD_GRADLE"; exit 1; fi
    if [ ! -d "$FIREBASE_TEST_DIR" ] || [ ! -d "$FIREBASE_PROD_DIR" ]; then print_error "Firebase directories not found ($FIREBASE_TEST_DIR, $FIREBASE_PROD_DIR)."; exit 1; fi
    mkdir -p .aws-sam/build
    print_success "Project structure verified."
}
init_firebase_test() {
    print_status "Initializing Firebase in test environment"
    cd "$FIREBASE_TEST_DIR"
    print_status "> Running Firebase use $FIREBASE_PROJECT_ID_TEST"
    firebase use "$FIREBASE_PROJECT_ID_TEST"
    print_status "> Running Firebase init firestore (may prompt for overwrite)"
    firebase init firestore || { print_error "Firebase init failed in $FIREBASE_TEST_DIR"; cd ..; exit 1; }
    cd ..
    print_success "Firebase test environment initialized."
}
init_firebase_prod() {
    print_status "Initializing Firebase in production environment"
    cd "$FIREBASE_PROD_DIR"
    print_status "> Running Firebase use $FIREBASE_PROJECT_ID_PROD"
    firebase use "$FIREBASE_PROJECT_ID_PROD"
    print_status "> Running Firebase init firestore (may prompt for overwrite)"
    firebase init firestore || { print_error "Firebase init failed in $FIREBASE_PROD_DIR"; cd ..; exit 1; }
    cd ..
    print_success "Firebase production environment initialized."
}
sync_firebase_rules() {
    print_status "Syncing Firebase rules & indexes (Test -> Prod)"
    init_firebase_test # Fetch latest from test
    init_firebase_prod # Ensure prod dir is ready
    print_status "> Copying rules & indexes to $FIREBASE_PROD_DIR"
    cp "$FIREBASE_TEST_DIR/firestore.rules" "$FIREBASE_PROD_DIR/firestore.rules"
    cp "$FIREBASE_TEST_DIR/firestore.indexes.json" "$FIREBASE_PROD_DIR/firestore.indexes.json"
    print_status "> Deploying rules & indexes to production ($FIREBASE_PROJECT_ID_PROD)"
    cd "$FIREBASE_PROD_DIR"
    firebase use "$FIREBASE_PROJECT_ID_PROD" # Ensure correct project
    firebase deploy --only firestore:rules,firestore:indexes || { print_error "Firebase deploy failed in $FIREBASE_PROD_DIR"; cd ..; exit 1; }
    cd ..
    print_success "Firebase rules & indexes synced successfully!"
}
select_environment() {
    print_status "Select deployment environment"
    select env in "test" "prod"; do
        case $env in
            test | prod)
                ENVIRONMENT=$env
                FIREBASE_PROJECT_ID=$([ "$env" = "test" ] && echo "$FIREBASE_PROJECT_ID_TEST" || echo "$FIREBASE_PROJECT_ID_PROD")
                print_success "Environment set to: $ENVIRONMENT (Firebase: $FIREBASE_PROJECT_ID)"
                break
                ;;
            *)
                print_error "Invalid selection. Please enter 1 or 2."
                ;;
        esac
    done
}
# --- End Helper Functions ---

# Build function
build() {
    print_status "Building project"
    print_status "> Running ./gradlew clean build..."
    chmod +x ./gradlew
    ./gradlew clean build || { print_error "Gradle build failed."; exit 1; }

    print_status "> Running sam build..."
    sam build --template "$TEMPLATE_FILE" --build-dir .aws-sam/build || { print_error "SAM build failed."; exit 1; }
    print_success "Build completed."
}


# Main deployment function
deploy() {
    local stack_name="java-lambda-${ENVIRONMENT}"
    STACK_NAME=$stack_name
    local s3_uri_to_delete="" # Initialize variable to store S3 URI

    print_status "Starting deployment process for '$ENVIRONMENT'"

    build # Call build function

    print_status "Packaging application to s3://${DEPLOYMENT_BUCKET}"
    sam package \
        --template-file .aws-sam/build/template.yaml \
        --output-template-file $PACKAGED_TEMPLATE_FILE \
        --s3-bucket "$DEPLOYMENT_BUCKET" || { print_error "SAM package failed."; exit 1; }
    print_success "Packaging complete."

    # --- Extract S3 URI BEFORE deployment ---
    if command -v yq &> /dev/null; then
        if [ -f "$PACKAGED_TEMPLATE_FILE" ]; then
            s3_uri_to_delete=$(yq e ".Resources.${LAMBDA_RESOURCE_NAME}.Properties.CodeUri" "$PACKAGED_TEMPLATE_FILE" 2>/dev/null)
            if [[ "$s3_uri_to_delete" != s3://* ]]; then
                print_warning "Extracted CodeUri ('$s3_uri_to_delete') is not a valid S3 URI. Cleanup may fail."
                s3_uri_to_delete="" # Reset if invalid
            else
                print_status "Identified artifact for cleanup: $s3_uri_to_delete"
            fi
        else
            print_warning "$PACKAGED_TEMPLATE_FILE not found before deployment. Cannot determine S3 artifact URI."
        fi
    fi
    # --- End Extraction ---

    print_status "Deploying stack: $STACK_NAME"
    # Run sam deploy and capture exit code
    sam deploy \
        --template-file $PACKAGED_TEMPLATE_FILE \
        --stack-name "$STACK_NAME" \
        --capabilities CAPABILITY_IAM \
        --parameter-overrides \
        Environment="$ENVIRONMENT" \
        FirebaseProject="$FIREBASE_PROJECT_ID" \
        --no-fail-on-empty-changeset
    local deploy_exit_code=$? # Capture exit code immediately

    # --- Always attempt Cleanup AFTER deployment attempt ---
    print_status "Attempting S3 artifact cleanup (regardless of deployment outcome)"
    if [[ -n "$s3_uri_to_delete" ]]; then # Check if we have a valid-looking URI
         print_status "> Deleting artifact: $s3_uri_to_delete"
         if aws s3 rm "$s3_uri_to_delete"; then
             print_success "Successfully deleted deployment artifact from S3."
         else
             # This is just a warning, script continues
             print_warning "Failed to delete deployment artifact: $s3_uri_to_delete. Manual cleanup might be required."
         fi
    elif command -v yq &> /dev/null; then
         # Only warn if yq exists but we couldn't find the URI earlier
         print_warning "Skipping S3 artifact cleanup as URI could not be determined."
    else
         # Message if yq wasn't found initially
         # Warning already printed by check_prerequisites
         :
    fi
    # --- End Cleanup ---

    # --- Handle Deployment Outcome ---
    if [ $deploy_exit_code -ne 0 ]; then
        # Error already printed by the trap
        exit $deploy_exit_code # Exit with the original error code from sam deploy
    fi

    # --- Post-Success Actions ---
    print_success "Deployment command completed successfully"

    # Sync Firebase rules ONLY if deploying to prod
    if [ "$ENVIRONMENT" = "prod" ]; then
        sync_firebase_rules
    fi

    # Get deployment outputs
    print_status "Stack Outputs ($STACK_NAME)"
    aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --query 'Stacks[0].Outputs' \
        --output table
}

main() {
    clear
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE} AWS Lambda Deployment Script (Java)    ${NC}"
    echo -e "${BLUE}========================================${NC}"
    check_prerequisites
    verify_project_structure
    select_environment
    deploy
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}      Deployment Script Finished        ${NC}"
    echo -e "${GREEN}========================================${NC}"
}

# Execute main function
main