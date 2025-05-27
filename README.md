# Kitree Lambda

This repository contains the Lambda functions for the Kitree platform.

## Testing Philosophy

Our testing strategy follows a multi-layered approach to ensure code quality and reliability:

### 1. Unit Tests
- Test individual components in isolation
- Mock external dependencies
- Focus on business logic validation
- Located in `src/test/java/in/co/kitree/unit/`

### 2. Integration Tests
- Test interactions between components
- Use real dependencies where possible
- Focus on component integration
- Located in `src/test/java/in/co/kitree/integration/`

### 3. End-to-End (E2E) Tests
- Test complete user workflows
- Use Firebase emulators for local testing
- Validate entire business processes
- Located in `src/test/java/in/co/kitree/e2e/`

## Running Tests Locally

### Prerequisites
1. Java 17 or higher
2. Gradle
3. Docker (for Firebase emulators)

### Setup and Running Tests
```bash
# Start Firebase emulators using Docker
docker-compose up -d

# Run all tests
./gradlew test

# Run specific test categories
./gradlew test --tests "*UnitTest"
./gradlew test --tests "*IntegrationTest"
./gradlew test --tests "*E2ETest"

# Run specific test class
./gradlew test --tests "ServicePurchaseFlowTest"
```

## CI/CD Pipeline

Our CI/CD pipeline runs on GitHub Actions and includes:

1. **Build & Test**
   - Compiles the code
   - Runs all tests
   - Generates test reports

2. **Code Quality**
   - Runs static code analysis
   - Checks code coverage
   - Validates code style

3. **Deployment**
   - Deploys to staging environment
   - Runs smoke tests
   - Deploys to production if all checks pass

## Test Data Management

- Each test class manages its own test data
- Test data is cleaned up after each test
- Use `@BeforeEach` and `@AfterEach` for test data setup/cleanup
- Avoid test data dependencies between test classes

## Best Practices

1. **Test Isolation**
   - Each test should be independent
   - Clean up test data after each test
   - Don't rely on test execution order

2. **Test Naming**
   - Use descriptive test names
   - Follow pattern: `test[Scenario]_[ExpectedResult]`
   - Example: `testCompleteServicePurchaseFlow`

3. **Assertions**
   - Use specific assertions
   - Test one concept per test
   - Include both positive and negative test cases

4. **Mocking**
   - Mock external services
   - Use Firebase emulators for Firestore/Auth
   - Keep mocks simple and focused

## Troubleshooting

### Common Issues

1. **Firebase Emulator Connection**
   - Ensure Docker containers are running (`docker-compose ps`)
   - Check emulator ports (default: Firestore 8080, Auth 9099)
   - Verify environment variables are set

2. **Test Failures**
   - Check test data setup
   - Verify emulator state
   - Look for concurrent test interference

3. **CI Pipeline Failures**
   - Check test logs
   - Verify emulator configuration
   - Review code coverage reports

## Contributing

1. Write tests for new features
2. Maintain or improve test coverage
3. Follow existing test patterns
4. Document test scenarios
5. Run all tests before submitting PRs

## Deployment

### Prerequisites
1. AWS CLI configured with appropriate credentials
2. AWS SAM CLI installed
3. Docker (for local testing)

### Installing AWS SAM CLI
```bash
# For macOS
brew tap aws/tap
brew install aws-sam-cli

# For other platforms, see:
# https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html
```

### Deployment Process
1. Build the application:
```bash
sam build
```

2. Deploy to AWS:
```bash
./deploy.sh
```

### Important Notes
- The application is currently using AWS API Gateway, which is free only for the first year
- Consider migrating to a different API gateway solution for long-term cost optimization
- Monitor AWS costs regularly through the AWS Console

### Environment Configuration
- Development: `sam deploy --config-env dev`
- Staging: `sam deploy --config-env staging`
- Production: `sam deploy --config-env prod`

### Monitoring and Logs
- View logs: `sam logs -n [FunctionName] --stack-name [StackName]`
- Monitor metrics: AWS CloudWatch Console
- Set up alerts for critical errors and performance issues
