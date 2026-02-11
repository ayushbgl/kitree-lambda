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

### Manual Setup (one-time)

1. **AWS IAM deployer user** — `kitree-lambda-github-deployer` with permissions for:
   - CloudFormation, Lambda, S3, IAM (for SAM deployment)
   - CloudFront distribution management
   - EventBridge rule management

2. **GitHub Secrets** (repository settings):
   - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`
   - `REPO_ACCESS_TOKEN` — GitHub PAT to clone `kitree-secrets`
   - `FIREBASE_SERVICE_ACCOUNT_KEY_TEST`, `FIREBASE_SERVICE_ACCOUNT_KEY_PROD`

3. **kitree-secrets repo** must contain:
   - `kitree-lambda/secrets.json`
   - `kitree-lambda/serviceAccountKey.json` (Firebase prod)
   - `kitree-lambda/serviceAccountKeyTest.json` (Firebase test)

4. **DNS** — CNAME records pointing custom domains to CloudFront:
   - `api-test.kitree.co.in` -> CloudFront distribution (test)
   - `api.kitree.co.in` -> CloudFront distribution (prod)

5. **ACM Certificate** — `*.kitree.co.in` in `us-east-1` (required for CloudFront)
