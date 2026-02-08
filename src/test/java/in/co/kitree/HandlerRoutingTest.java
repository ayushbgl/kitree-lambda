package in.co.kitree;

import in.co.kitree.handlers.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that every supported function string is claimed by exactly one handler.
 * Pure static tests — no mocks, no network, safe for CI.
 */
public class HandlerRoutingTest {

    /** Complete list of all known function names. */
    private static final List<String> ALL_FUNCTIONS = Arrays.asList(
        // AdminHandler
        "make_admin",
        "remove_admin",
        "razorpay_webhook",
        "app_startup",
        // ExpertHandler
        "mark_expert_busy",
        "mark_expert_free",
        "expert_earnings_balance",
        "get_expert_booking_metrics",
        "record_expert_payout",
        "set_expert_platform_fee",
        "get_expert_platform_fee",
        "expert_metrics",
        "updateExpertImage",
        // AstrologyHandler
        "get_astrological_details",
        "get_dasha_details",
        "get_divisional_charts",
        "get_gochar_details",
        "rashifal_generate",
        "generate_report",
        "get_certificate_courses",
        "generate_certificate",
        "generate_aura_report",
        // SessionHandler
        "get_stream_user_token",
        "create_call",
        "create_session_plan",
        "add_course_session",
        "start_session",
        "stop_session",
        "join_session",
        "leave_session",
        "raise_hand",
        "lower_hand",
        "promote_participant",
        "demote_participant",
        "mute_participant",
        "kick_participant",
        "toggle_session_gifts",
        "get_session_participants",
        "get_raised_hands",
        "get_live_sessions",
        "get_upcoming_sessions",
        "send_gift",
        // ServiceHandler
        "buy_service",
        "buy_gift",
        "apply_coupon",
        "checkReferralBonus",
        "verify_payment",
        "cancel_subscription",
        "make_call",
        "confirm_appointment",
        "get_expert_availability",
        // ConsultationHandler
        "on_demand_consultation_initiate",
        "on_demand_consultation_connect",
        "on_demand_consultation_heartbeat",
        "update_consultation_max_duration",
        "on_demand_consultation_end",
        "cleanup_stale_order",
        "recalculate_charge",
        "generate_consultation_summary",
        "get_consultation_summary",
        "get_active_call_for_user",
        "submit_review",
        // ProductOrderHandler
        "get_platform_products",
        "get_expert_products",
        "update_expert_product",
        "get_expert_storefront",
        "buy_product",
        "buy_products",
        "verify_product_payment",
        "get_user_product_orders",
        "get_expert_product_orders",
        "get_platform_shipping_orders",
        "update_product_order_status",
        "cancel_product_order",
        "seed_platform_products",
        "admin_upsert_product",
        // WalletHandler
        "wallet_balance",
        "create_wallet_recharge_order"
    );

    private static final List<HandlerDescriptor> HANDLERS = Arrays.asList(
        new HandlerDescriptor("AdminHandler", AdminHandler::handles),
        new HandlerDescriptor("ExpertHandler", ExpertHandler::handles),
        new HandlerDescriptor("AstrologyHandler", AstrologyHandler::handles),
        new HandlerDescriptor("SessionHandler", SessionHandler::handles),
        new HandlerDescriptor("ServiceHandler", ServiceHandler::handles),
        new HandlerDescriptor("ConsultationHandler", ConsultationHandler::handles),
        new HandlerDescriptor("ProductOrderHandler", ProductOrderHandler::handles),
        new HandlerDescriptor("WalletHandler", WalletHandler::handles)
    );

    @Test
    void everyFunctionIsClaimedByExactlyOneHandler() {
        for (String function : ALL_FUNCTIONS) {
            List<String> claimants = new ArrayList<>();
            for (HandlerDescriptor handler : HANDLERS) {
                if (handler.handles(function)) {
                    claimants.add(handler.name);
                }
            }
            assertEquals(1, claimants.size(),
                "Function '" + function + "' must be claimed by exactly 1 handler, but was claimed by: " + claimants);
        }
    }

    @Test
    void noTwoHandlersClaimTheSameFunction() {
        Map<String, String> claimedBy = new HashMap<>();
        for (HandlerDescriptor handler : HANDLERS) {
            for (String function : ALL_FUNCTIONS) {
                if (handler.handles(function)) {
                    String previous = claimedBy.put(function, handler.name);
                    assertNull(previous,
                        "Function '" + function + "' is claimed by both '" + previous + "' and '" + handler.name + "'");
                }
            }
        }
    }

    @Test
    void allKnownFunctionsAreHandled() {
        Set<String> unhandled = new HashSet<>();
        for (String function : ALL_FUNCTIONS) {
            boolean handled = HANDLERS.stream().anyMatch(h -> h.handles(function));
            if (!handled) unhandled.add(function);
        }
        assertTrue(unhandled.isEmpty(), "These functions are not handled by any handler: " + unhandled);
    }

    @Test
    void nullAndEmptyFunctionNamesAreRejectedByAllHandlers() {
        for (HandlerDescriptor handler : HANDLERS) {
            assertFalse(handler.handles(null), handler.name + " must not claim null");
            assertFalse(handler.handles(""), handler.name + " must not claim empty string");
        }
    }

    // Simple functional interface + descriptor wrapper
    @FunctionalInterface
    interface HandlesCheck {
        boolean check(String functionName);
    }

    private static class HandlerDescriptor {
        final String name;
        private final HandlesCheck check;

        HandlerDescriptor(String name, HandlesCheck check) {
            this.name = name;
            this.check = check;
        }

        boolean handles(String functionName) {
            return check.check(functionName);
        }
    }
}
