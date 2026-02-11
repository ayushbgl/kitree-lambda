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
        System.setProperty("ENVIRONMENT", "test");
    }

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);
        handler = new Handler();
        try {
            createAuthUser(TEST_USER_ID);
        } catch (FirebaseAuthException e) {
            // Ignore if already exists
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            deleteAuthUser(TEST_USER_ID);
        } catch (FirebaseAuthException e) {
            // Ignore if already deleted
        }
    }

    @Test
    void testWarmupRequest() {
        RequestEvent event = new RequestEvent();
        event.setSource("aws.events");

        Object response = handler.handleRequest(event, context);

        assertEquals("Warmed up!", response);
    }

    @Test
    void testMakeAdminRequest() throws Exception {
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/make-admin");
        event.setBody("{\"adminSecret\":\"C6DC17344FA8287F92C93B11CDF99\",\"adminUid\":\"test-user-id\"}");

        Object response = handler.handleRequest(event, context);

        assertNotNull(response);
    }

    @Test
    void testMakeAdminRequestInvalidSecret() {
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/make-admin");
        event.setBody("{\"adminSecret\":\"invalid-secret\",\"adminUid\":\"test-user-id\"}");

        Object response = handler.handleRequest(event, context);

        assertNotNull(response);
    }

    @Test
    void testRemoveAdminRequest() throws Exception {
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/remove-admin");
        event.setBody("{\"adminSecret\":\"C6DC17344FA8287F92C93B11CDF99\",\"adminUid\":\"test-user-id\"}");

        Object response = handler.handleRequest(event, context);

        assertNotNull(response);
    }

    @Test
    void testRemoveAdminRequestInvalidSecret() {
        RequestEvent event = createRequestEventWithUser(TEST_USER_ID);
        event.setRawPath("/api/v1/admin/remove-admin");
        event.setBody("{\"adminSecret\":\"invalid-secret\",\"adminUid\":\"test-user-id\"}");

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
