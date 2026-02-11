package in.co.kitree;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.google.firebase.auth.FirebaseAuthException;
import in.co.kitree.pojos.RequestEvent;
import in.co.kitree.pojos.RequestContext;
import in.co.kitree.pojos.RequestContextAuthorizer;
import in.co.kitree.pojos.RequestContextAuthorizerJwt;
import in.co.kitree.pojos.RequestContextAuthorizerJwtClaims;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandlerTest extends TestBase {
    private Handler handler;
    private static final String TEST_USER_ID = "test-user-id";
    
    @Mock
    private Context context;
    
    @Mock
    private LambdaLogger logger;

    static {
        // Ensure ENVIRONMENT is set to 'test' for the Handler
        System.setProperty("ENVIRONMENT", "test");
    }

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);
        handler = new Handler();
        // Create the test user in the emulator
        try {
            createAuthUser(TEST_USER_ID);
        } catch (FirebaseAuthException e) {
            // Ignore if already exists
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up the test user
        try {
            deleteAuthUser(TEST_USER_ID);
        } catch (FirebaseAuthException e) {
            // Ignore if already deleted
        }
    }

    @Test
    void testWarmupRequest() {
        // Create a warmup request event
        RequestEvent event = new RequestEvent();
        event.setSource("aws.events");

        // Execute the handler
        Object response = handler.handleRequest(event, context);

        // Verify the response
        assertEquals("Warmed up!", response);
        verify(logger).log("warmed up\n");
    }

    @Test
    void testMakeAdminRequest() throws Exception {
        // Create a make_admin request with proper JWT claims via REST path
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/make");
        event.setBody("{\"adminSecret\":\"C6DC17344FA8287F92C93B11CDF99\",\"adminUid\":\"test-user-id\"}");

        // Execute the handler
        Object response = handler.handleRequest(event, context);

        assertNotNull(response);
    }

    @Test
    void testMakeAdminRequestInvalidSecret() {
        // Create a make_admin request with invalid secret via REST path
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/make");
        event.setBody("{\"adminSecret\":\"invalid-secret\",\"adminUid\":\"test-user-id\"}");

        // Execute the handler
        Object response = handler.handleRequest(event, context);

        assertNotNull(response);
    }

    @Test
    void testRemoveAdminRequest() throws Exception {
        // Create a remove_admin request with proper JWT claims via REST path
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/remove");
        event.setBody("{\"adminSecret\":\"C6DC17344FA8287F92C93B11CDF99\",\"adminUid\":\"test-user-id\"}");

        // Execute the handler
        Object response = handler.handleRequest(event, context);

        assertNotNull(response);
    }

    @Test
    void testRemoveAdminRequestInvalidSecret() {
        // Create a remove_admin request with invalid secret via REST path
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/remove");
        event.setBody("{\"adminSecret\":\"invalid-secret\",\"adminUid\":\"test-user-id\"}");

        // Execute the handler
        Object response = handler.handleRequest(event, context);

        assertNotNull(response);
    }

    private RequestEvent createRequestEventWithUser(String userId) {
        RequestEvent event = new RequestEvent();
        RequestContext requestContext = new RequestContext();
        RequestContextAuthorizer authorizer = new RequestContextAuthorizer();
        RequestContextAuthorizerJwt jwt = new RequestContextAuthorizerJwt();
        RequestContextAuthorizerJwtClaims claims = new RequestContextAuthorizerJwtClaims();
        claims.setUser_id(userId);
        jwt.setClaims(claims);
        authorizer.setJwt(jwt);
        requestContext.setAuthorizer(authorizer);
        event.setRequestContext(requestContext);
        return event;
    }
} 