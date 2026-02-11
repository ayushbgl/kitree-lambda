# Kitree Lambda

Java 21 Lambda backend for the Kitree platform, deployed via AWS SAM + CloudFront.

## Architecture

- **Runtime:** Java 21 on AWS Lambda, exposed via Lambda Function URL + CloudFront
- **Auth:** Firebase Authentication (token verified on every request)
- **Database:** Cloud Firestore
- **Payments:** Razorpay + Stripe (via PaymentGateway interface)
- **Astrology:** Invokes `kitree-astrology-api` Lambda via AWS SDK (IAM-authorized)
- **Python scripts:** Invokes `kitree-python-scripts` Lambda via AWS SDK (IAM-authorized)
- **Streaming:** Stream Chat SDK for real-time messaging
- **Monitoring:** Sentry for error tracking and performance monitoring

## secrets.json Keys

All secrets are loaded via `SecretsProvider` (package-private, cached singleton). Required keys:

| Key | Service |
|-----|---------|
| `RAZORPAY_KEY`, `RAZORPAY_SECRET` | Razorpay (prod) |
| `RAZORPAY_TEST_KEY`, `RAZORPAY_TEST_SECRET` | Razorpay (test) |
| `RAZORPAY_WEBHOOK_SECRET`, `RAZORPAY_WEBHOOK_SECRET_TEST` | Razorpay webhooks |
| `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` | Stripe (prod) |
| `STRIPE_TEST_SECRET_KEY`, `STRIPE_TEST_PUBLISHABLE_KEY` | Stripe (test) |
| `CLOUDINARY_URL` | Cloudinary media uploads |
| `GEMINI_API_KEY`, `GEMINI_API_KEY_TEST` | Google Gemini AI |
| `STREAM_API_KEY`, `STREAM_API_SECRET` | Stream Chat (prod) |
| `STREAM_API_KEY_TEST`, `STREAM_API_SECRET_TEST` | Stream Chat (test) |

These files are stored in the `kitree-secrets` private repo under `kitree-lambda/`.

## Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "in.co.kitree.services.AstrologyServiceTest"
```

## Deployment

### Via GitHub Actions (recommended)

1. Go to **Actions > Deploy Lambda Stack > Run workflow**
2. Select environment: `test`, `prod`, or `both`
3. Pipeline: CI tests -> SAM build/deploy -> Health check verification

### What the deploy does

**Note:** There are no local deploy scripts — all deployments go through GitHub Actions.

- Creates an S3 deployment bucket `kitree-backend-{AccountId}` if it doesn't exist (SAM artifacts stored under `kitree-lambda/` prefix)
- Deploys CloudFormation stack `kitree-lambda-{env}` with Lambda Function URL + CloudFront distribution
- Creates a CloudWatch log group `/aws/lambda/kitree-lambda-{env}` with retention (7 days prod, 1 day test)
- Creates an EventBridge rule for auto-terminating stale consultations

### New AWS Account Setup

1. **IAM deployer user** — create a user (e.g. `github`) with `AdministratorAccess` (or scoped to CloudFormation, Lambda, S3, IAM, CloudFront, EventBridge). This user's credentials are used by GitHub Actions.

2. **GitHub Secrets** (repository settings):
   | Secret | Description |
   |--------|-------------|
   | `AWS_ACCESS_KEY_ID` | IAM deployer user access key |
   | `AWS_SECRET_ACCESS_KEY` | IAM deployer user secret key |
   | `REPO_ACCESS_TOKEN` | GitHub PAT to clone `kitree-secrets` |
   | `FIREBASE_SERVICE_ACCOUNT_KEY_TEST` | Firebase service account JSON (test project) |
   | `FIREBASE_SERVICE_ACCOUNT_KEY_PROD` | Firebase service account JSON (prod project) |

3. **kitree-secrets repo** must contain:
   - `kitree-lambda/secrets.json`
   - `kitree-lambda/serviceAccountKey.json` (Firebase prod)
   - `kitree-lambda/serviceAccountKeyTest.json` (Firebase test)

4. **ACM Certificate** — create a `*.kitree.co.in` wildcard certificate in **us-east-1** (required by CloudFront). Update `API_CERTIFICATE_ARN` in `deploy.yml` with the new ARN.

5. **DNS** — after the first deploy, the workflow output shows the CloudFront domain (e.g. `d1234abc.cloudfront.net`). Create CNAME records:
   - `api-test.kitree.co.in` -> CloudFront distribution domain (test)
   - `api.kitree.co.in` -> CloudFront distribution domain (prod)

   The CloudFront domain is printed in the deploy job output and in the GitHub Actions step summary.

6. **Deploy kitree-astrology and kitree-python-scripts first** — this Lambda invokes both via AWS SDK. The SAM template grants `lambda:InvokeFunction` on `kitree-astrology-api-{env}` and `kitree-python-scripts-{env}`, so those functions must exist before this Lambda can call them.
