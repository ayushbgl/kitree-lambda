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
 * Uses the Firebase auth emulator to verify:
 *   - Non-admins are rejected when using actAs
 *   - Admins are rejected for blocked functions (call/session/purchase)
 *   - Admins succeed for allowed management functions
 *   - callerUserId always reflects the real caller, never the impersonated expert
 *
 * Uses a TestableHandler subclass that injects the test user without real token verification,
 * while still hitting the real Firebase auth emulator for admin claim checks.
 */
public class ActAsHandlerSecurityTest extends TestBase {

    private static final String ADMIN_USER_ID = "test-admin-actas-001";
    private static final String EXPERT_USER_ID = "test-expert-actas-001";
    private static final String REGULAR_USER_ID = "test-user-actas-001";

    static {
        System.setProperty("ENVIRONMENT", "test");
    }

    /**
     * Handler subclass that bypasses real token verification,
     * allowing us to simulate any authenticated user in tests.
     */
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

        // Admin user with admin claim
        try { createAuthUser(ADMIN_USER_ID); } catch (Exception ignored) {}
        setUserClaims(ADMIN_USER_ID, Map.of("admin", true));

        // Expert user — no special claims
        try { createAuthUser(EXPERT_USER_ID); } catch (Exception ignored) {}

        // Regular user — no admin claim
        try { createAuthUser(REGULAR_USER_ID); } catch (Exception ignored) {}
    }

    // ==================== NON-ADMIN REJECTION ====================

    @Test
    @DisplayName("Non-admin using actAs is rejected with 'Not authorized'")
    void nonAdminActAsIsRejected() {
        handler.setCurrentUser(REGULAR_USER_ID);

        Object response = handler.handleRequest(
            eventWithBody("{\"function\":\"expert_earnings_balance\",\"actAs\":\"" + EXPERT_USER_ID + "\"}"),
            context
        );

        assertTrue(String.valueOf(response).contains("Not authorized"),
            "Non-admin must not be allowed to use actAs. Got: " + response);
    }

    @Test
    @DisplayName("Non-admin actAs for multiple management functions is consistently rejected")
    void nonAdminActAsRejectedForAllManagementFunctions() {
        handler.setCurrentUser(REGULAR_USER_ID);
        String[] functions = {
            "expert_earnings_balance", "expert_metrics", "get_expert_booking_metrics",
            "get_expert_products", "update_expert_product"
        };
        for (String fn : functions) {
            Object response = handler.handleRequest(
                eventWithBody("{\"function\":\"" + fn + "\",\"actAs\":\"" + EXPERT_USER_ID + "\"}"),
                context
            );
            assertTrue(String.valueOf(response).contains("Not authorized"),
                "Non-admin must be rejected for '" + fn + "'. Got: " + response);
        }
    }

    // ==================== BLOCKED FUNCTION REJECTION ====================

    @Test
    @DisplayName("Admin using actAs for create_call is rejected")
    void adminActAsForCreateCallIsRejected() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBody("{\"function\":\"create_call\",\"actAs\":\"" + EXPERT_USER_ID + "\"}"),
            context
        );

        assertTrue(String.valueOf(response).contains("Not authorized"),
            "Admin must not be able to start a call as expert via actAs. Got: " + response);
    }

    @Test
    @DisplayName("Admin using actAs for all blocked functions is rejected")
    void adminActAsRejectedForAllBlockedFunctions() {
        handler.setCurrentUser(ADMIN_USER_ID);
        String[] blockedFunctions = {
            "get_stream_user_token", "create_call", "start_session", "join_session",
            "leave_session", "stop_session", "send_gift", "buy_products",
            "submit_review", "cancel_subscription", "get_user_product_orders"
        };
        for (String fn : blockedFunctions) {
            Object response = handler.handleRequest(
                eventWithBody("{\"function\":\"" + fn + "\",\"actAs\":\"" + EXPERT_USER_ID + "\"}"),
                context
            );
            assertTrue(String.valueOf(response).contains("Not authorized"),
                "Admin must not be able to use '" + fn + "' via actAs. Got: " + response);
        }
    }

    // ==================== ADMIN SUCCESS ====================

    @Test
    @DisplayName("Admin using actAs for expert_earnings_balance is allowed")
    void adminActAsForEarningsIsAllowed() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBody("{\"function\":\"expert_earnings_balance\",\"actAs\":\"" + EXPERT_USER_ID + "\"}"),
            context
        );

        assertFalse(String.valueOf(response).contains("Not authorized"),
            "Admin should be allowed to view earnings via actAs. Got: " + response);
    }

    @Test
    @DisplayName("Admin using actAs for expert_metrics is allowed")
    void adminActAsForMetricsIsAllowed() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBody("{\"function\":\"expert_metrics\",\"expertId\":\"" + EXPERT_USER_ID + "\",\"actAs\":\"" + EXPERT_USER_ID + "\"}"),
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

        // record_expert_payout requires admin in callerUserId — if callerUserId was replaced
        // with the impersonated expert, the admin check would fail
        Object response = handler.handleRequest(
            eventWithBody(
                "{\"function\":\"record_expert_payout\""
                + ",\"actAs\":\"" + EXPERT_USER_ID + "\""
                + ",\"expertId\":\"" + EXPERT_USER_ID + "\""
                + ",\"amount\":100.0,\"currency\":\"INR\""
                + ",\"payoutMethod\":\"UPI\",\"payoutReference\":\"test-ref-001\"}"
            ),
            context
        );

        // Should NOT hit admin rejection — callerUserId is still the admin
        assertFalse(String.valueOf(response).contains("Admin access required"),
            "Admin action should succeed with admin callerUserId. Got: " + response);
        assertFalse(String.valueOf(response).contains("Not authorized"),
            "actAs should not block admin-only functions when caller is admin. Got: " + response);
    }

    @Test
    @DisplayName("Non-admin impersonating cannot escalate to admin actions")
    void nonAdminCannotEscalateViaActAs() {
        handler.setCurrentUser(REGULAR_USER_ID);

        // Try to perform admin action (record_expert_payout) while impersonating expert
        Object response = handler.handleRequest(
            eventWithBody(
                "{\"function\":\"record_expert_payout\""
                + ",\"actAs\":\"" + EXPERT_USER_ID + "\""
                + ",\"expertId\":\"" + EXPERT_USER_ID + "\""
                + ",\"amount\":100.0,\"currency\":\"INR\""
                + ",\"payoutMethod\":\"UPI\",\"payoutReference\":\"escalation-attempt\"}"
            ),
            context
        );

        // Must be rejected at the actAs gate before even reaching the admin check
        assertTrue(String.valueOf(response).contains("Not authorized"),
            "Non-admin must be blocked by actAs check. Got: " + response);
    }

    // ==================== NO actAs — NORMAL FLOW ====================

    @Test
    @DisplayName("Without actAs, request proceeds normally as the caller")
    void withoutActAsRequestProceedsNormally() {
        handler.setCurrentUser(REGULAR_USER_ID);

        Object response = handler.handleRequest(
            eventWithBody("{\"function\":\"expert_earnings_balance\"}"),
            context
        );

        // No auth rejection — user is requesting their own data
        assertFalse(String.valueOf(response).contains("Not authorized"),
            "Own data access should not be rejected. Got: " + response);
    }

    @Test
    @DisplayName("actAs=own userId (self-impersonation) is allowed for admin")
    void adminSelfImpersonationIsAllowed() {
        handler.setCurrentUser(ADMIN_USER_ID);

        Object response = handler.handleRequest(
            eventWithBody("{\"function\":\"expert_earnings_balance\",\"actAs\":\"" + ADMIN_USER_ID + "\"}"),
            context
        );

        assertFalse(String.valueOf(response).contains("Not authorized"),
            "Admin self-impersonation should be allowed. Got: " + response);
    }

    // ==================== HELPER ====================

    private RequestEvent eventWithBody(String body) {
        RequestEvent event = new RequestEvent();
        event.setBody(body);
        event.setRawPath("/api/v1/expert/earnings");
        event.setRequestContext(new RequestContext());
        return event;
    }

    private RequestEvent eventWithBodyAndPath(String body, String path) {
        RequestEvent event = new RequestEvent();
        event.setBody(body);
        event.setRawPath(path);
        event.setRequestContext(new RequestContext());
        return event;
    }
}
