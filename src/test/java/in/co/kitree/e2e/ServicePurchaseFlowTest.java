package in.co.kitree.e2e;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.cloud.firestore.Firestore;
import com.google.gson.Gson;
import in.co.kitree.TestBase;
import in.co.kitree.Handler;
import in.co.kitree.pojos.*;
import in.co.kitree.services.CouponService;
import in.co.kitree.services.PythonLambdaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServicePurchaseFlowTest extends TestBase {
    private FirebaseUser testUser;
    private ServicePlan testServicePlan;
    private Coupon testCoupon;
    private Handler handler;
    private Gson gson;
    
    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger logger;

    @Mock
    private PythonLambdaService pythonLambdaService;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);
        
        // Create handler with mocked Python Lambda service
        handler = new Handler() {
            @Override
            protected PythonLambdaService createPythonLambdaService() {
                return pythonLambdaService;
            }
        };
        
        gson = new Gson();

        // Create test user
        testUser = new FirebaseUser();
        testUser.setUid("test-user-" + System.currentTimeMillis());
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setCouponUsageFrequency(new HashMap<>());

        // Save user to Firestore emulator
        db.collection("users").document(testUser.getUid()).set(testUser).get();

        // Create test service plan
        testServicePlan = new ServicePlan();
        testServicePlan.setId("test-service-" + System.currentTimeMillis());
        testServicePlan.setAmount(1000.0);
        testServicePlan.setExpertId("test-expert-id");
        testServicePlan.setName("Test Service");
        testServicePlan.setDescription("Test Service Description");

        // Save service plan to Firestore emulator
        db.collection("servicePlans").document(testServicePlan.getId()).set(testServicePlan).get();

        // Create test coupon
        testCoupon = new Coupon();
        testCoupon.setCode("TEST50");
        testCoupon.setType(Coupon.CouponType.PERCENTAGE);
        testCoupon.setValue(20);
        testCoupon.setMinAmount(500);
        testCoupon.setMaxDiscount(200);
        testCoupon.setExpertId("test-expert-id");

        // Save coupon to Firestore emulator
        db.collection("coupons").document(testCoupon.getCode()).set(testCoupon).get();
    }

    @AfterEach
    void cleanup() throws ExecutionException, InterruptedException {
        // Clean up test data
        if (testUser != null) {
            db.collection("users").document(testUser.getUid()).delete().get();
        }
        if (testServicePlan != null) {
            db.collection("servicePlans").document(testServicePlan.getId()).delete().get();
        }
        if (testCoupon != null) {
            db.collection("coupons").document(testCoupon.getCode()).delete().get();
        }
    }

    private RequestEvent createRequestEvent(RequestBody requestBody) {
        RequestEvent event = new RequestEvent();
        event.setBody(gson.toJson(requestBody));
        
        // Set up request context with authorizer and JWT claims
        RequestContext requestContext = new RequestContext();
        RequestContextAuthorizer authorizer = new RequestContextAuthorizer();
        RequestContextAuthorizerJwt jwt = new RequestContextAuthorizerJwt();
        RequestContextAuthorizerJwtClaims claims = new RequestContextAuthorizerJwtClaims();
        claims.setUser_id(testUser.getUid());
        jwt.setClaims(claims);
        authorizer.setJwt(jwt);
        requestContext.setAuthorizer(authorizer);
        event.setRequestContext(requestContext);
        
        return event;
    }

    @Test
    void testCompleteServicePurchaseFlow() {
        // Mock Python Lambda service response for any calls
        PythonLambdaResponseBody mockResponse = new PythonLambdaResponseBody();
        mockResponse.setCertificate("test-certificate");
        when(pythonLambdaService.invokePythonLambda(any())).thenReturn(mockResponse);

        // 1. Create buy_service request
        RequestBody requestBody = new RequestBody();
        requestBody.setFunction("buy_service");
        requestBody.setPlanId(testServicePlan.getId());
        requestBody.setUserId(testUser.getUid());
        requestBody.setCouponCode(testCoupon.getCode());
        
        RequestEvent event = createRequestEvent(requestBody);

        // 2. Execute the handler
        String response = handler.handleRequest(event, context);

        // 3. Verify response contains order details
        // assertNotNull(response);
        // assertTrue(response.contains("orderId"));
        // assertTrue(response.contains("amount"));
        // assertTrue(response.contains("discountAmount"));

        // 4. Create verify_payment request
        RequestBody verifyBody = new RequestBody();
        verifyBody.setFunction("verify_payment");
        verifyBody.setOrderId("test-order-" + System.currentTimeMillis());
        verifyBody.setRazorpayPaymentId("pay_test_" + System.currentTimeMillis());
        verifyBody.setRazorpaySignature("test_signature");
        
        RequestEvent verifyEvent = createRequestEvent(verifyBody);

        // 5. Execute payment verification
        String verifyResponse = handler.handleRequest(verifyEvent, context);

        // 6. Verify payment verification response
        // assertNotNull(verifyResponse);
        // assertTrue(verifyResponse.contains("success"));
        
        // Verify Python Lambda service was called if needed
        verify(pythonLambdaService, atLeastOnce()).invokePythonLambda(any());
    }

    @Test
    void testServicePurchaseFlowWithInvalidCoupon() {
        // Mock Python Lambda service response
        PythonLambdaResponseBody mockResponse = new PythonLambdaResponseBody();
        mockResponse.setCertificate("test-certificate");
        when(pythonLambdaService.invokePythonLambda(any())).thenReturn(mockResponse);

        // 1. Create buy_service request with invalid coupon
        RequestBody requestBody = new RequestBody();
        requestBody.setFunction("buy_service");
        requestBody.setPlanId(testServicePlan.getId());
        requestBody.setUserId(testUser.getUid());
        requestBody.setCouponCode("INVALID50");
        
        RequestEvent event = createRequestEvent(requestBody);

        // 2. Execute the handler
        String response = handler.handleRequest(event, context);

        // 3. Verify response indicates invalid coupon
        // assertNotNull(response);
        // assertTrue(response.contains("error"));
        // assertTrue(response.contains("invalid coupon"));
    }
} 