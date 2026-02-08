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
import in.co.kitree.services.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.*;

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

    Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
    private Firestore db;
    private Razorpay razorpay;
    protected PythonLambdaService pythonLambdaService;

    private AdminHandler adminHandler;
    private ServiceHandler serviceHandler;
    private AstrologyHandler astrologyHandler;
    private SessionHandler sessionHandler;
    private ConsultationHandler consultationHandler;
    private ExpertHandler expertHandler;
    private ProductOrderHandler productOrderHandler;
    private WalletHandler walletHandler;
    private WebhookHandler webhookHandler;

    private static volatile boolean coldStart = true;

    static {
        Sentry.init(options -> {
            options.setDsn(System.getenv("SENTRY_DSN"));
            options.setEnvironment("test".equals(System.getenv("ENVIRONMENT")) ? "development" : "production");
            options.setTracesSampleRate(1.0);
            options.setSendDefaultPii(true);
        });
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
        this.adminHandler = new AdminHandler(db, razorpay);
        this.serviceHandler = new ServiceHandler(db, razorpay);
        this.astrologyHandler = new AstrologyHandler(db, astrologyService, pythonLambdaService, rashifalService);
        this.sessionHandler = new SessionHandler(db, streamService, pythonLambdaService, isTest());
        this.consultationHandler = new ConsultationHandler(db, pythonLambdaService, isTest());
        this.expertHandler = new ExpertHandler(db);
        this.productOrderHandler = new ProductOrderHandler(db, razorpay);
        this.walletHandler = new WalletHandler(db, razorpay);
        this.webhookHandler = new WebhookHandler(db, isTest());
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

    public String handleRequest(RequestEvent event, Context context) {
        LoggingService.initRequest(context);

        Map<String, String> incomingHeaders = event.getHeaders() != null ? event.getHeaders() : Collections.emptyMap();
        TransactionContext sentryCtx = Sentry.continueTrace(
            incomingHeaders.get("sentry-trace"),
            incomingHeaders.get("baggage") != null ? List.of(incomingHeaders.get("baggage")) : null
        );
        sentryCtx.setName("lambda.request");
        sentryCtx.setOperation("http.server");
        TransactionOptions txOptions = new TransactionOptions();
        txOptions.setBindToScope(true);
        ITransaction sentryTx = Sentry.startTransaction(sentryCtx, txOptions);
        sentryTx.setTag("cold_start", String.valueOf(coldStart));
        coldStart = false;

        try {
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

            RequestBody requestBody = this.gson.fromJson(event.getBody(), RequestBody.class);

            // Admin/system functions that bypass normal auth
            if (AdminHandler.handles(requestBody.getFunction())) {
                return adminHandler.handleRequest(requestBody.getFunction(), requestBody);
            }

            // Extract and verify Firebase token
            String userId = extractUserIdFromToken(event, requestBody.getFunction());
            if (userId != null) {
                User sentryUser = new User();
                sentryUser.setId(userId);
                Sentry.setUser(sentryUser);
            }

            if (userId == null && requiresAuthentication(requestBody.getFunction())) {
                return gson.toJson(Map.of("success", false, "errorMessage", "Unauthorized: Authentication required"));
            }

            // Delegate to specialized handlers in order
            if (ExpertHandler.handles(requestBody.getFunction())) {
                return expertHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }
            if (AstrologyHandler.handles(requestBody.getFunction())) {
                return astrologyHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }
            if (SessionHandler.handles(requestBody.getFunction())) {
                return sessionHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }
            if (ServiceHandler.handles(requestBody.getFunction())) {
                return serviceHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }
            if (ConsultationHandler.handles(requestBody.getFunction())) {
                return consultationHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }
            if (ProductOrderHandler.handles(requestBody.getFunction())) {
                return productOrderHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }
            if (WalletHandler.handles(requestBody.getFunction())) {
                return walletHandler.handleRequest(requestBody.getFunction(), userId, requestBody);
            }

        } catch (Exception e) {
            Sentry.captureException(e);
            sentryTx.setThrowable(e);
            sentryTx.setStatus(SpanStatus.INTERNAL_ERROR);
            LoggingService.error("request_handler_exception", e);
            return gson.toJson(Map.of("success", false));
        } finally {
            sentryTx.finish();
            Sentry.flush(1000);
        }
        return null;
    }

    private boolean isTest() {
        return "test".equals(System.getenv("ENVIRONMENT"));
    }

    private boolean requiresAuthentication(String functionName) {
        return true;
    }

    private String extractUserIdFromToken(RequestEvent event, String functionName) {
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

    public class HandlerException extends Exception {
        Razorpay.ErrorCode errorCode;

        public HandlerException(Razorpay.ErrorCode errorCode) {
            super();
            this.errorCode = errorCode;
        }
    }
}
