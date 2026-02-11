package in.co.kitree;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.razorpay.RazorpayException;
import in.co.kitree.handlers.*;
import in.co.kitree.pojos.*;
import in.co.kitree.rest.ApiResponse;
import in.co.kitree.rest.RestRouter;
import in.co.kitree.services.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.*;
import java.util.stream.Collectors;

import io.sentry.Sentry;
import io.sentry.ITransaction;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.BaggageHeader;
import io.sentry.protocol.User;

/**
 * AWS Lambda entry point.
 * Routes requests to specialized handlers. No business logic lives here.
 */
public class Handler implements RequestHandler<RequestEvent, Object> {

    /**
     * REST paths where actAs impersonation is forbidden (real-time/transactional operations).
     */
    private static final Set<String> BLOCKED_ACT_AS_PATHS = new HashSet<>(Arrays.asList(
        "/api/v1/stream/token", "/api/v1/calls/create",
        "/api/v1/sessions", "/api/v1/consultations",
        "/api/v1/reviews", "/api/v1/products/buy",
        "/api/v1/orders"
    ));

    Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private Firestore db;
    private Razorpay razorpay;
    protected PythonLambdaService pythonLambdaService;

    private RashifalService rashifalService;

    private AdminHandler adminHandler;
    private ServiceHandler serviceHandler;
    private AstrologyHandler astrologyHandler;
    private SessionHandler sessionHandler;
    private ConsultationHandler consultationHandler;
    private ExpertHandler expertHandler;
    private ProductOrderHandler productOrderHandler;
    private WalletHandler walletHandler;
    private WebhookHandler webhookHandler;
    private RestRouter restRouter;

    private static volatile boolean coldStart = true;

    static {
        String sentryDsn = System.getenv("SENTRY_DSN");
        if (sentryDsn != null && !sentryDsn.isEmpty()) {
            Sentry.init(options -> {
                options.setDsn(sentryDsn);
                options.setEnvironment("test".equals(System.getenv("ENVIRONMENT")) ? "development" : "production");
                options.setTracesSampleRate(1.0);
                options.setSendDefaultPii(true);
            });
        }
    }

    public Handler() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                if (!isTest()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(
                                    getClass().getResourceAsStream("/serviceAccountKey.json")))
                            .build();
                    FirebaseApp.initializeApp(options);
                } else {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(
                                    getClass().getResourceAsStream("/serviceAccountKeyTest.json")))
                            .build();
                    FirebaseApp.initializeApp(options);
                }
            }
            this.db = FirestoreClient.getFirestore();
            this.razorpay = new Razorpay(isTest());
            this.pythonLambdaService = createPythonLambdaService();
            AstrologyService astrologyService = new AstrologyService();
            RashifalService rashifalService = new RashifalService(db, isTest());
            StreamService streamService = new StreamService(isTest());
            initHandlers(astrologyService, rashifalService, streamService);
        } catch (Exception e) {
            LoggingService.error("handler_init_failed", e);
            if (isTest()) {
                try {
                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(
                                        getClass().getResourceAsStream("/serviceAccountKeyTest.json")))
                                .build();
                        FirebaseApp.initializeApp(options);
                    }
                    this.db = FirestoreClient.getFirestore();
                } catch (Exception firebaseEx) {
                    LoggingService.error("firebase_init_failed_test_env", firebaseEx);
                    throw new RuntimeException("Failed to initialize Firebase in test environment", firebaseEx);
                }

                try { this.razorpay = new Razorpay(true); } catch (RazorpayException ex) {
                    LoggingService.warn("razorpay_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                try {
                    System.setProperty("aws.region", "ap-south-1");
                    this.pythonLambdaService = createPythonLambdaService();
                } catch (Exception ex) {
                    LoggingService.warn("python_lambda_init_skipped_test_env", Map.of("error", ex.getMessage()));
                }
                AstrologyService astrologyService = null;
                RashifalService rashifalService = null;
                StreamService streamService = null;
                try { astrologyService = new AstrologyService(); } catch (Exception ignored) {}
                try { rashifalService = new RashifalService(db, isTest()); } catch (Exception ignored) {}
                try { streamService = new StreamService(isTest()); } catch (Exception ignored) {}
                try { initHandlers(astrologyService, rashifalService, streamService); } catch (Exception ignored) {}
            } else {
                throw new RuntimeException("Failed to initialize Handler", e);
            }
        }
    }

    private void initHandlers(AstrologyService astrologyService, RashifalService rashifalService, StreamService streamService) {
        this.rashifalService = rashifalService;
        this.adminHandler = new AdminHandler(db, razorpay);
        this.serviceHandler = new ServiceHandler(db, razorpay);
        this.astrologyHandler = new AstrologyHandler(db, astrologyService, pythonLambdaService, rashifalService);
        this.sessionHandler = new SessionHandler(db, streamService, pythonLambdaService, isTest());
        this.consultationHandler = new ConsultationHandler(db, pythonLambdaService, isTest());
        this.expertHandler = new ExpertHandler(db);
        this.productOrderHandler = new ProductOrderHandler(db, razorpay);
        this.walletHandler = new WalletHandler(db, razorpay);
        this.webhookHandler = new WebhookHandler(db, isTest());
        this.restRouter = new RestRouter(
                adminHandler, walletHandler, consultationHandler, expertHandler,
                productOrderHandler, serviceHandler, astrologyHandler, sessionHandler
        );
    }

    protected PythonLambdaService createPythonLambdaService() {
        LambdaClient lambdaClient = LambdaClient.builder()
                .region(software.amazon.awssdk.regions.Region.AP_SOUTH_1)
                .build();

        return new PythonLambdaService() {
            @Override
            public PythonLambdaResponseBody invokePythonLambda(PythonLambdaEventRequest request) {
                try {
                    ISpan currentSpan = Sentry.getSpan();
                    if (currentSpan != null) {
                        request.setSentryTrace(currentSpan.toSentryTrace().getValue());
                        BaggageHeader baggageHeader = currentSpan.toBaggageHeader(List.of());
                        if (baggageHeader != null) {
                            request.setBaggage(baggageHeader.getValue());
                        }
                    }
                    String payload = gson.toJson(request);
                    InvokeRequest invokeRequest = InvokeRequest.builder()
                            .functionName("certgen")
                            .payload(SdkBytes.fromUtf8String(payload))
                            .build();

                    InvokeResponse response = lambdaClient.invoke(invokeRequest);
                    String responsePayload = response.payload().asUtf8String();
                    return gson.fromJson(responsePayload, PythonLambdaResponseBody.class);
                } catch (Exception e) {
                    LoggingService.error("python_lambda_invoke_failed", e, Map.of("exceptionType", e.getClass().getName()));
                    throw new RuntimeException("Failed to invoke Python Lambda", e);
                }
            }
        };
    }

    public Object handleRequest(RequestEvent event, Context context) {
        LoggingService.initRequest(context);

        Map<String, String> incomingHeaders = event.getHeaders() != null ? event.getHeaders() : Collections.emptyMap();
        ITransaction sentryTx = null;
        TransactionContext sentryCtx = Sentry.continueTrace(
            incomingHeaders.get("sentry-trace"),
            incomingHeaders.get("baggage") != null ? List.of(incomingHeaders.get("baggage")) : null
        );
        if (sentryCtx != null) {
            sentryCtx.setName("lambda.request");
            sentryCtx.setOperation("http.server");
            TransactionOptions txOptions = new TransactionOptions();
            txOptions.setBindToScope(true);
            sentryTx = Sentry.startTransaction(sentryCtx, txOptions);
            sentryTx.setTag("cold_start", String.valueOf(coldStart));
        }
        coldStart = false;

        try {
            // Async rashifal worker: direct Lambda self-invocation, no API Gateway request context.
            // Safe against spoofing from HTTP clients because API Gateway always sets requestContext.
            if ("lambda.rashifal_worker".equals(event.getSource()) && event.getRequestContext() == null) {
                if (rashifalService != null) {
                    rashifalService.executeRashifalGeneration(gson.fromJson(event.getBody(), RequestBody.class).getUserId());
                }
                return null;
            }

            // Scheduled cron events
            if ("aws.events".equals(event.getSource())) {
                String detailType = event.getDetailType();
                if ("auto_terminate_consultations".equals(detailType)) {
                    LoggingService.setFunction("auto_terminate_consultations");
                    LoggingService.info("cron_job_started", Map.of("job", "auto_terminate_consultations"));
                    return consultationHandler.handleAutoTerminateConsultations(isTest());
                }
                LoggingService.info("lambda_warmed_up");
                return "Warmed up!";
            }

            // Path-based webhook routing
            String rawPath = event.getRawPath();
            if (WebhookHandler.handlesPath(rawPath)) {
                LoggingService.setFunction("webhook_handler");
                return webhookHandler.handleWebhookRequest(event, rawPath);
            }

            // --- REST API routing ---
            // Routes /api/v1/* paths through RestRouter with proper HTTP status codes.
            // Returns a structured Map response that Lambda Function URL interprets
            // as {statusCode, headers, body} instead of wrapping in HTTP 200.
            if (RestRouter.isRestPath(rawPath)) {
                String httpMethod = extractHttpMethod(event);
                RequestBody requestBody = (event.getBody() != null && !event.getBody().isEmpty())
                        ? this.gson.fromJson(event.getBody(), RequestBody.class)
                        : new RequestBody();

                // Authenticate
                String userId = extractUserIdFromToken(event);
                if (userId != null) {
                    User sentryUser = new User();
                    sentryUser.setId(userId);
                    Sentry.setUser(sentryUser);
                    requestBody.setCallerUserId(userId);
                }

                if (userId == null) {
                    return ApiResponse.unauthorizedMessage("Unauthorized: Authentication required").toLambdaResponse();
                }

                // Resolve effective user ID (handles actAs impersonation)
                String effectiveUserId = resolveEffectiveUserId(userId, requestBody, rawPath);
                if (effectiveUserId == null && requestBody.getActAs() != null && !requestBody.getActAs().isEmpty()) {
                    return ApiResponse.forbiddenMessage("Not authorized").toLambdaResponse();
                }
                if (effectiveUserId == null) {
                    effectiveUserId = userId;
                }

                // Handle rashifal_generate via REST
                if ("/api/v1/rashifal/generate".equals(rawPath) && "POST".equalsIgnoreCase(httpMethod) && rashifalService != null) {
                    String quickResult = rashifalService.prepareRashifalGeneration(effectiveUserId);
                    if (quickResult != null) {
                        return ApiResponse.ok(quickResult).toLambdaResponse();
                    }
                    try {
                        invokeRashifalAsync(context, effectiveUserId);
                    } catch (Exception e) {
                        LoggingService.error("rashifal_async_invoke_failed", e);
                        rashifalService.cleanupRashifalGenerating(effectiveUserId);
                        return ApiResponse.errorMessage("Generation service temporarily unavailable").toLambdaResponse();
                    }
                    return ApiResponse.ok(gson.toJson(Map.of("success", true))).toLambdaResponse();
                }

                ApiResponse response = restRouter.route(httpMethod, rawPath, event.getRawQueryString(), effectiveUserId, requestBody);
                if (response != null) {
                    if (sentryTx != null) sentryTx.setTag("http.status_code", String.valueOf(response.getStatusCode()));
                    return response.toLambdaResponse();
                }

                // No matching REST route
                return ApiResponse.notFoundMessage("Not found: " + httpMethod + " " + rawPath).toLambdaResponse();
            }

        } catch (Exception e) {
            Sentry.captureException(e);
            if (sentryTx != null) {
                sentryTx.setThrowable(e);
                sentryTx.setStatus(SpanStatus.INTERNAL_ERROR);
            }
            LoggingService.error("request_handler_exception", e);
            return ApiResponse.errorMessage("Internal server error").toLambdaResponse();
        } finally {
            if (sentryTx != null) sentryTx.finish();
            Sentry.flush(1000);
        }
        return ApiResponse.notFoundMessage("Not found").toLambdaResponse();
    }

    /**
     * Extract HTTP method from the Lambda Function URL event.
     * Lambda Function URLs put the method in requestContext.http.method.
     * Falls back to the top-level httpMethod field or defaults to POST.
     */
    private String extractHttpMethod(RequestEvent event) {
        // Lambda Function URL format: requestContext.http.method
        if (event.getRequestContext() != null
                && event.getRequestContext().getHttp() != null
                && event.getRequestContext().getHttp().getMethod() != null) {
            return event.getRequestContext().getHttp().getMethod();
        }
        // Fallback to top-level field (API Gateway or custom)
        if (event.getHttpMethod() != null) {
            return event.getHttpMethod();
        }
        return "POST"; // default
    }

    private void invokeRashifalAsync(Context context, String userId) {
        RequestBody asyncBody = new RequestBody();
        asyncBody.setUserId(userId);
        RequestEvent asyncEvent = new RequestEvent();
        asyncEvent.setSource("lambda.rashifal_worker");
        asyncEvent.setBody(gson.toJson(asyncBody));
        LambdaClient client = LambdaClient.builder()
                .region(software.amazon.awssdk.regions.Region.AP_SOUTH_1)
                .build();
        client.invoke(InvokeRequest.builder()
                .functionName(context.getFunctionName())
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(gson.toJson(asyncEvent)))
                .build());
    }

    private boolean isTest() {
        return "test".equals(System.getenv("ENVIRONMENT"));
    }

    protected String extractUserIdFromToken(RequestEvent event) {
        String token = null;
        if (event.getHeaders() != null) {
            token = AuthenticationService.extractTokenFromHeaders(event.getHeaders());
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return AuthenticationService.verifyTokenAndGetUserId(token);
        } catch (FirebaseAuthException e) {
            LoggingService.warn("token_verification_failed", Map.of("error", e.getMessage()));
            return null;
        }
    }

    /**
     * Resolves the effective user ID for data access.
     * If actAs is set: verifies the caller is admin and the path is not blocked.
     * Returns null on authorization failure (caller should return error response).
     */
    private String resolveEffectiveUserId(String callerUserId, RequestBody requestBody, String path) throws FirebaseAuthException {
        String actAs = requestBody.getActAs();
        if (actAs == null || actAs.isEmpty()) {
            return callerUserId;
        }
        // Block impersonation on sensitive paths (real-time, transactional)
        if (path != null) {
            for (String blockedPrefix : BLOCKED_ACT_AS_PATHS) {
                if (path.startsWith(blockedPrefix)) {
                    LoggingService.warn("act_as_blocked_path", Map.of("path", path, "callerUserId", callerUserId));
                    return null;
                }
            }
        }
        boolean adminClaim = Boolean.TRUE.equals(
            com.google.firebase.auth.FirebaseAuth.getInstance().getUser(callerUserId).getCustomClaims().get("admin")
        );
        if (!adminClaim) {
            LoggingService.warn("act_as_non_admin", Map.of("callerUserId", callerUserId));
            return null;
        }
        LoggingService.info("act_as_resolved", Map.of("callerUserId", callerUserId, "actAs", actAs, "path", path != null ? path : ""));
        return actAs;
    }

    public class HandlerException extends Exception {
        Razorpay.ErrorCode errorCode;

        public HandlerException(Razorpay.ErrorCode errorCode) {
            super();
            this.errorCode = errorCode;
        }
    }
}
