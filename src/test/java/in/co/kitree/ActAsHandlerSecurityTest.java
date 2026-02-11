package in.co.kitree;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import in.co.kitree.pojos.RequestContext;
import in.co.kitree.pojos.RequestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration security tests for the actAs impersonation flow through the full Handler.
 * Uses REST path-based routing and the Firebase auth emulator to verify:
 *   - Non-admins are rejected when using actAs
 *   - Admins are rejected for blocked paths (sessions/consultations/orders)
 *   - Admins succeed for allowed management paths (expert earnings, metrics)
 *   - callerUserId always reflects the real caller, never the impersonated expert
 */
public class ActAsHandlerSecurityTest extends TestBase {

    private static final String ADMIN_USER_ID = "test-admin-actas-001";
    private static final String EXPERT_USER_ID = "test-expert-actas-001";
    private static final String REGULAR_USER_ID = "test-user-actas-001";

    // Allowed REST paths (not in BLOCKED_ACT_AS_PATHS)
    private static final String EXPERT_EARNINGS_PATH = "/api/v1/experts/" + EXPERT_USER_ID + "/earnings";
    private static final String EXPERT_METRICS_PATH = "/api/v1/experts/" + EXPERT_USER_ID + "/metrics";
    private static final String EXPERT_PAYOUT_PATH = "/api/v1/experts/" + EXPERT_USER_ID + "/payouts";

    // Blocked REST paths (match BLOCKED_ACT_AS_PATHS prefixes)
    private static final String STREAM_TOKEN_PATH = "/api/v1/stream/token";
    private static final String SESSION_START_PATH = "/api/v1/sessions/plan123/start";
    private static final String REVIEW_PATH = "/api/v1/reviews";
    private static final String BUY_PRODUCTS_PATH = "/api/v1/orders/products";

    static {
        System.setProperty("ENVIRONMENT", "test");
    }

    private static class TestableHandler extends Handler {
        private String currentUserId;

        void setCurrentUser(String userId) {
            this.currentUserId = userId;
        }

        @Override
        protected String extractUserIdFromToken(RequestEvent event) {
            return currentUserId;
        }
    }

    private TestableHandler handler;

    @Mock private Context context;
    @Mock private LambdaLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(context.getLogger()).thenReturn(logger);

        handler = new TestableHandler();

        try { createAuthUser(ADMIN_USER_ID); } catch (Exception ignored) {}
        setUserClaims(ADMIN_USER_ID, Map.of("admin", true));
        try { createAuthUser(EXPERT_USER_ID); } catch (Exception ignored) {}
        try { createAuthUser(REGULAR_USER_ID); } catch (Exception ignored) {}
    }

    // ==================== NON-ADMIN REJECTION ====================

    @Test
    @DisplayName("Non-admin using actAs is rejected with 'Not authorized'")
    void nonAdminActAsIsRejected() {
        handler.setCurrentUser(REGULAR_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath("{\"actAs\":\"" + EXPERT_USER_ID + "\"}", EXPERT_EARNINGS_PATH),
            context
        );

        assertTrue(String.valueOf(response).contains("Not authorized"),
            "Non-admin must not be allowed to use actAs. Got: " + response);
    }

    @Test
    @DisplayName("Non-admin actAs for multiple management paths is consistently rejected")
    void nonAdminActAsRejectedForAllManagementPaths() {
        handler.setCurrentUser(REGULAR_USER_ID);
        String[] paths = { EXPERT_EARNINGS_PATH, EXPERT_METRICS_PATH, EXPERT_PAYOUT_PATH };
        for (String path : paths) {
            Object response = handler.handleRequest(
                eventWithBodyAndPath("{\"actAs\":\"" + EXPERT_USER_ID + "\"}", path),
                context
            );
            assertTrue(String.valueOf(response).contains("Not authorized"),
                "Non-admin must be rejected for '" + path + "'. Got: " + response);
        }
    }

    // ==================== BLOCKED PATH REJECTION ====================

    @Test
    @DisplayName("Admin using actAs for stream token path is rejected")
    void adminActAsForStreamTokenIsRejected() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath("{\"actAs\":\"" + EXPERT_USER_ID + "\"}", STREAM_TOKEN_PATH),
            context
        );

        assertTrue(String.valueOf(response).contains("Not authorized"),
            "Admin must not be able to get stream token via actAs. Got: " + response);
    }

    @Test
    @DisplayName("Admin using actAs for all blocked paths is rejected")
    void adminActAsRejectedForAllBlockedPaths() {
        handler.setCurrentUser(ADMIN_USER_ID);
        String[] blockedPaths = { STREAM_TOKEN_PATH, SESSION_START_PATH, REVIEW_PATH, BUY_PRODUCTS_PATH };
        for (String path : blockedPaths) {
            Object response = handler.handleRequest(
                eventWithBodyAndPath("{\"actAs\":\"" + EXPERT_USER_ID + "\"}", path),
                context
            );
            assertTrue(String.valueOf(response).contains("Not authorized"),
                "Admin must not be able to use actAs for '" + path + "'. Got: " + response);
        }
    }

    // ==================== ADMIN SUCCESS ====================

    @Test
    @DisplayName("Admin using actAs for expert earnings is allowed")
    void adminActAsForEarningsIsAllowed() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath("{\"actAs\":\"" + EXPERT_USER_ID + "\"}", EXPERT_EARNINGS_PATH),
            context
        );

        assertFalse(String.valueOf(response).contains("Not authorized"),
            "Admin should be allowed to view earnings via actAs. Got: " + response);
    }

    @Test
    @DisplayName("Admin using actAs for expert metrics is allowed")
    void adminActAsForMetricsIsAllowed() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath(
                "{\"expertId\":\"" + EXPERT_USER_ID + "\",\"actAs\":\"" + EXPERT_USER_ID + "\"}",
                EXPERT_METRICS_PATH
            ),
            context
        );

        assertFalse(String.valueOf(response).contains("Not authorized"),
            "Admin should be allowed to view expert metrics via actAs. Got: " + response);
    }

    // ==================== CALLER IDENTITY PRESERVATION ====================

    @Test
    @DisplayName("callerUserId remains admin even while impersonating expert")
    void callerUserIdRemainsAdminDuringImpersonation() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath(
                "{\"actAs\":\"" + EXPERT_USER_ID + "\""
                + ",\"expertId\":\"" + EXPERT_USER_ID + "\""
                + ",\"amount\":100.0,\"currency\":\"INR\""
                + ",\"payoutMethod\":\"UPI\",\"payoutReference\":\"test-ref-001\"}",
                EXPERT_PAYOUT_PATH
            ),
            context
        );

        assertFalse(String.valueOf(response).contains("Admin access required"),
            "Admin action should succeed with admin callerUserId. Got: " + response);
        assertFalse(String.valueOf(response).contains("Not authorized"),
            "actAs should not block admin-only functions when caller is admin. Got: " + response);
    }

    @Test
    @DisplayName("Non-admin impersonating cannot escalate to admin actions")
    void nonAdminCannotEscalateViaActAs() {
        handler.setCurrentUser(REGULAR_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath(
                "{\"actAs\":\"" + EXPERT_USER_ID + "\""
                + ",\"expertId\":\"" + EXPERT_USER_ID + "\""
                + ",\"amount\":100.0,\"currency\":\"INR\""
                + ",\"payoutMethod\":\"UPI\",\"payoutReference\":\"escalation-attempt\"}",
                EXPERT_PAYOUT_PATH
            ),
            context
        );

        assertTrue(String.valueOf(response).contains("Not authorized"),
            "Non-admin must be blocked by actAs check. Got: " + response);
    }

    // ==================== NO actAs — NORMAL FLOW ====================

    @Test
    @DisplayName("Without actAs, request proceeds normally as the caller")
    void withoutActAsRequestProceedsNormally() {
        handler.setCurrentUser(REGULAR_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath("{}", EXPERT_EARNINGS_PATH),
            context
        );

        assertFalse(String.valueOf(response).contains("Not authorized"),
            "Own data access should not be rejected. Got: " + response);
    }

    @Test
    @DisplayName("actAs=own userId (self-impersonation) is allowed for admin")
    void adminSelfImpersonationIsAllowed() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBodyAndPath("{\"actAs\":\"" + ADMIN_USER_ID + "\"}", EXPERT_EARNINGS_PATH),
            context
        );

        assertFalse(String.valueOf(response).contains("Not authorized"),
            "Admin self-impersonation should be allowed. Got: " + response);
    }

    // ==================== HELPER ====================

    private RequestEvent eventWithBodyAndPath(String body, String path) {
        RequestEvent event = new RequestEvent();
        event.setBody(body);
        event.setRawPath(path);
        event.setRequestContext(new RequestContext());
        return event;
    }
}
