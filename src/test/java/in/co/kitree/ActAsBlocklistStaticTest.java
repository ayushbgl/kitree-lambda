package in.co.kitree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Static tests for the BLOCKED_ACT_AS_FUNCTIONS constant in Handler.
 * Verifies that all call/session/purchase actions are blocked from impersonation,
 * and that safe management actions (earnings, metrics, plans, products) are allowed.
 * No Firebase, no mocks — pure compile-time constant validation.
 */
public class ActAsBlocklistStaticTest {

    @SuppressWarnings("unchecked")
    private static Set<String> getBlockedFunctions() throws Exception {
        Field field = Handler.class.getDeclaredField("BLOCKED_ACT_AS_FUNCTIONS");
        field.setAccessible(true);
        return (Set<String>) field.get(null);
    }

    // --- Functions that MUST be blocked (session/call/purchase) ---

    private static final List<String> MUST_BE_BLOCKED = Arrays.asList(
        // Streaming / calling
        "get_stream_user_token",
        "create_call",
        "make_call",
        // Session lifecycle
        "start_session",
        "join_session",
        "leave_session",
        "stop_session",
        // Session moderation
        "raise_hand",
        "lower_hand",
        "promote_participant",
        "demote_participant",
        "mute_participant",
        "kick_participant",
        "toggle_session_gifts",
        "send_gift",
        // On-demand consultation
        "on_demand_consultation_initiate",
        "on_demand_consultation_connect",
        "on_demand_consultation_heartbeat",
        "update_consultation_max_duration",
        "on_demand_consultation_end",
        "cleanup_stale_order",
        "recalculate_charge",
        // Reviews and purchases
        "submit_review",
        "buy_gift",
        "buy_product",
        "buy_products",
        "cancel_subscription",
        "get_user_product_orders"
    );

    // --- Functions that MUST NOT be blocked (safe for admin to impersonate) ---

    private static final List<String> MUST_NOT_BE_BLOCKED = Arrays.asList(
        // Expert management
        "expert_earnings_balance",
        "get_expert_booking_metrics",
        "expert_metrics",
        "mark_expert_busy",
        "mark_expert_free",
        "record_expert_payout",
        "set_expert_platform_fee",
        "get_expert_platform_fee",
        // Plans / services
        "create_session_plan",
        "add_course_session",
        // Products
        "get_expert_products",
        "update_expert_product",
        "get_expert_product_orders",
        // Availability
        "get_expert_availability",
        // Info
        "app_startup"
    );

    @Test
    @DisplayName("All call/session/purchase functions are in the blocklist")
    void dangerousFunctionsAreBlocked() throws Exception {
        Set<String> blocked = getBlockedFunctions();
        for (String fn : MUST_BE_BLOCKED) {
            assertTrue(blocked.contains(fn),
                "'" + fn + "' must be in BLOCKED_ACT_AS_FUNCTIONS — admin must not act on behalf of user for this action");
        }
    }

    @Test
    @DisplayName("Management functions are NOT in the blocklist")
    void managementFunctionsAreAllowedForImpersonation() throws Exception {
        Set<String> blocked = getBlockedFunctions();
        for (String fn : MUST_NOT_BE_BLOCKED) {
            assertFalse(blocked.contains(fn),
                "'" + fn + "' must NOT be blocked — admin should be able to manage expert data via actAs");
        }
    }

    @Test
    @DisplayName("Blocklist is non-empty")
    void blocklistIsNonEmpty() throws Exception {
        Set<String> blocked = getBlockedFunctions();
        assertTrue(blocked.size() >= MUST_BE_BLOCKED.size(),
            "BLOCKED_ACT_AS_FUNCTIONS must contain at least all expected dangerous functions");
    }

    @Test
    @DisplayName("Blocklist contains no null or empty entries")
    void blocklistHasNoBlankEntries() throws Exception {
        Set<String> blocked = getBlockedFunctions();
        for (String fn : blocked) {
            assertNotNull(fn, "Blocklist must not contain null entries");
            assertFalse(fn.isBlank(), "Blocklist must not contain blank entries");
        }
    }
}
