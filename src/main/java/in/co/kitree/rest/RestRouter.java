package in.co.kitree.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.handlers.*;
import in.co.kitree.pojos.RequestBody;
import in.co.kitree.services.LoggingService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REST router that maps HTTP method + path to handler methods.
 * Delegates to existing handler classes without rewriting business logic.
 */
public class RestRouter {

    private final List<Route> routes = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final AdminHandler adminHandler;
    private final WalletHandler walletHandler;
    private final ConsultationHandler consultationHandler;
    private final ExpertHandler expertHandler;
    private final ProductOrderHandler productOrderHandler;
    private final ServiceHandler serviceHandler;
    private final AstrologyHandler astrologyHandler;
    private final SessionHandler sessionHandler;

    public RestRouter(
            AdminHandler adminHandler,
            WalletHandler walletHandler,
            ConsultationHandler consultationHandler,
            ExpertHandler expertHandler,
            ProductOrderHandler productOrderHandler,
            ServiceHandler serviceHandler,
            AstrologyHandler astrologyHandler,
            SessionHandler sessionHandler
    ) {
        this.adminHandler = adminHandler;
        this.walletHandler = walletHandler;
        this.consultationHandler = consultationHandler;
        this.expertHandler = expertHandler;
        this.productOrderHandler = productOrderHandler;
        this.serviceHandler = serviceHandler;
        this.astrologyHandler = astrologyHandler;
        this.sessionHandler = sessionHandler;

        registerRoutes();
    }

    /**
     * Attempt to route a request. Returns null if no route matches.
     */
    public ApiResponse route(String method, String path, String queryString, String userId, RequestBody body) {
        if (body == null) {
            body = new RequestBody();
        }
        Map<String, String> queryParams = parseQueryString(queryString);

        for (Route route : routes) {
            if (!route.method.equalsIgnoreCase(method)) continue;

            Matcher matcher = route.pattern.matcher(path);
            if (!matcher.matches()) continue;

            // Extract path params
            Map<String, String> pathParams = new HashMap<>();
            for (int i = 0; i < route.paramNames.size(); i++) {
                pathParams.put(route.paramNames.get(i), URLDecoder.decode(matcher.group(i + 1), StandardCharsets.UTF_8));
            }

            LoggingService.setFunction(route.functionName);

            try {
                ApiResponse response = route.handler.handle(userId, body, pathParams, queryParams);
                if (response == null) {
                    return ApiResponse.errorMessage("No response from handler");
                }
                return response;
            } catch (Exception e) {
                LoggingService.error("rest_handler_exception", e);
                return ApiResponse.errorMessage(e.getMessage() != null ? e.getMessage() : "Internal server error");
            }
        }

        return null; // No matching route
    }

    /**
     * Check if a path starts with our REST prefix.
     */
    public static boolean isRestPath(String path) {
        return path != null && path.startsWith("/api/v1/");
    }

    // ============= Route Registration =============

    private void registerRoutes() {
        // --- Wallet ---
        get("/api/v1/wallet/balance", "wallet_balance", (userId, body, pathParams, queryParams) -> {
            body.setCurrency(queryParams.get("currency"));
            String result = walletHandler.handleRequest("wallet_balance", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/wallet/recharge", "create_wallet_recharge_order", (userId, body, pathParams, queryParams) -> {
            String result = walletHandler.handleRequest("create_wallet_recharge_order", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        // --- App ---
        post("/api/v1/app/startup", "app_startup", (userId, body, pathParams, queryParams) -> {
            String result = adminHandler.handleRequest("app_startup", body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Consultations ---
        post("/api/v1/consultations/initiate", "on_demand_consultation_initiate", (userId, body, pathParams, queryParams) -> {
            String result = consultationHandler.handleRequest("on_demand_consultation_initiate", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        post("/api/v1/consultations/{orderId}/connect", "on_demand_consultation_connect", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("on_demand_consultation_connect", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/consultations/{orderId}/heartbeat", "on_demand_consultation_heartbeat", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("on_demand_consultation_heartbeat", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        patch("/api/v1/consultations/{orderId}/max-duration", "update_consultation_max_duration", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("update_consultation_max_duration", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/consultations/{orderId}/end", "on_demand_consultation_end", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("on_demand_consultation_end", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/consultations/{orderId}/cleanup", "cleanup_stale_order", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("cleanup_stale_order", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/consultations/{orderId}/recalculate", "recalculate_charge", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("recalculate_charge", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/consultations/{orderId}/summary/generate", "generate_consultation_summary", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("generate_consultation_summary", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/consultations/{orderId}/summary", "get_consultation_summary", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = consultationHandler.handleRequest("get_consultation_summary", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/consultations/active", "get_active_call_for_user", (userId, body, pathParams, queryParams) -> {
            String result = consultationHandler.handleRequest("get_active_call_for_user", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Reviews ---
        post("/api/v1/reviews", "submit_review", (userId, body, pathParams, queryParams) -> {
            String result = consultationHandler.handleRequest("submit_review", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        // --- Experts ---
        get("/api/v1/experts/{expertId}/storefront", "get_expert_storefront", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            String result = productOrderHandler.handleRequest("get_expert_storefront", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/experts/{expertId}/availability", "get_expert_availability", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            body.setUserId(queryParams.get("userId"));
            body.setOrderId(queryParams.get("orderId"));
            body.setRangeStart(queryParams.get("rangeStart"));
            body.setRangeEnd(queryParams.get("rangeEnd"));
            body.setUserTimeZone(queryParams.get("userTimeZone"));
            String result = serviceHandler.handleRequest("get_expert_availability", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/experts/{expertId}/booking-metrics", "get_expert_booking_metrics", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            if (queryParams.get("startDate") != null) body.setStartDate(Long.parseLong(queryParams.get("startDate")));
            if (queryParams.get("endDate") != null) body.setEndDate(Long.parseLong(queryParams.get("endDate")));
            body.setBookingType(queryParams.get("bookingType"));
            String result = expertHandler.handleRequest("get_expert_booking_metrics", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/experts/{expertId}/earnings", "expert_earnings_balance", (userId, body, pathParams, queryParams) -> {
            body.setCurrency(queryParams.get("currency"));
            String result = expertHandler.handleRequest("expert_earnings_balance", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/experts/{expertId}/payouts", "record_expert_payout", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            String result = expertHandler.handleRequest("record_expert_payout", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        get("/api/v1/experts/{expertId}/platform-fee", "get_expert_platform_fee", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            String result = expertHandler.handleRequest("get_expert_platform_fee", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        put("/api/v1/experts/{expertId}/platform-fee", "set_expert_platform_fee", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            String result = expertHandler.handleRequest("set_expert_platform_fee", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/experts/{expertId}/mark-busy", "mark_expert_busy", (userId, body, pathParams, queryParams) -> {
            String result = expertHandler.handleRequest("mark_expert_busy", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/experts/{expertId}/mark-free", "mark_expert_free", (userId, body, pathParams, queryParams) -> {
            String result = expertHandler.handleRequest("mark_expert_free", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/experts/{expertId}/image", "upload_expert_image", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            String result = expertHandler.handleRequest("upload_expert_image", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/experts/{expertId}/products", "get_expert_products", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            String result = productOrderHandler.handleRequest("get_expert_products", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        patch("/api/v1/experts/{expertId}/products/{productId}", "update_expert_product", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            body.setProductId(pathParams.get("productId"));
            String result = productOrderHandler.handleRequest("update_expert_product", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/experts/{expertId}/orders", "get_expert_product_orders", (userId, body, pathParams, queryParams) -> {
            body.setStatusFilter(queryParams.get("status"));
            String result = productOrderHandler.handleRequest("get_expert_product_orders", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/experts/{expertId}/metrics", "expert_metrics", (userId, body, pathParams, queryParams) -> {
            body.setExpertId(pathParams.get("expertId"));
            body.setCategory(queryParams.get("category"));
            body.setType(queryParams.get("type"));
            String result = expertHandler.handleRequest("expert_metrics", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Products ---
        get("/api/v1/products", "get_platform_products", (userId, body, pathParams, queryParams) -> {
            body.setCategory(queryParams.get("category"));
            body.setType(queryParams.get("type"));
            String result = productOrderHandler.handleRequest("get_platform_products", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Orders ---
        post("/api/v1/orders/products", "buy_products", (userId, body, pathParams, queryParams) -> {
            String result = productOrderHandler.handleRequest("buy_products", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        post("/api/v1/orders/products/{orderId}/verify-payment", "verify_product_payment", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = productOrderHandler.handleRequest("verify_product_payment", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/orders/products", "get_user_product_orders", (userId, body, pathParams, queryParams) -> {
            String result = productOrderHandler.handleRequest("get_user_product_orders", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/orders/products/{orderId}/cancel", "cancel_product_order", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = productOrderHandler.handleRequest("cancel_product_order", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/orders/products/{orderId}/fulfill", "fulfill_digital_order", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = productOrderHandler.handleRequest("fulfill_digital_order", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        patch("/api/v1/orders/products/{orderId}/status", "update_product_order_status", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = productOrderHandler.handleRequest("update_product_order_status", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Services ---
        post("/api/v1/services/buy", "buy_service", (userId, body, pathParams, queryParams) -> {
            String result = serviceHandler.handleRequest("buy_service", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        post("/api/v1/payments/verify", "verify_payment", (userId, body, pathParams, queryParams) -> {
            String result = serviceHandler.handleRequest("verify_payment", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/appointments/{orderId}/confirm", "confirm_appointment", (userId, body, pathParams, queryParams) -> {
            body.setOrderId(pathParams.get("orderId"));
            String result = serviceHandler.handleRequest("confirm_appointment", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/coupons/apply", "apply_coupon", (userId, body, pathParams, queryParams) -> {
            String result = serviceHandler.handleRequest("apply_coupon", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Astrology ---
        post("/api/v1/astrology/details", "get_astrological_details", (userId, body, pathParams, queryParams) -> {
            String result = astrologyHandler.handleRequest("get_astrological_details", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/astrology/dasha", "get_dasha_details", (userId, body, pathParams, queryParams) -> {
            String result = astrologyHandler.handleRequest("get_dasha_details", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/astrology/divisional-charts", "get_divisional_charts", (userId, body, pathParams, queryParams) -> {
            String result = astrologyHandler.handleRequest("get_divisional_charts", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/astrology/gochar", "get_gochar_details", (userId, body, pathParams, queryParams) -> {
            String result = astrologyHandler.handleRequest("get_gochar_details", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/astrology/aura-report", "generate_aura_report", (userId, body, pathParams, queryParams) -> {
            String result = astrologyHandler.handleRequest("generate_aura_report", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Certificates ---
        get("/api/v1/certificates/courses", "get_certificate_courses", (userId, body, pathParams, queryParams) -> {
            body.setLanguage(queryParams.get("language"));
            String result = astrologyHandler.handleRequest("get_certificate_courses", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/certificates/generate", "generate_certificate", (userId, body, pathParams, queryParams) -> {
            String result = astrologyHandler.handleRequest("generate_certificate", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Stream ---
        get("/api/v1/stream/token", "get_stream_user_token", (userId, body, pathParams, queryParams) -> {
            String result = sessionHandler.handleRequest("get_stream_user_token", userId, body);
            // Stream token is returned as plain string, wrap it
            if (result != null && !result.startsWith("{")) {
                return ApiResponse.ok(gson.toJson(Map.of("success", true, "token", result)));
            }
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Sessions ---
        post("/api/v1/sessions", "create_session_plan", (userId, body, pathParams, queryParams) -> {
            String result = sessionHandler.handleRequest("create_session_plan", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        post("/api/v1/sessions/{planId}/course-sessions", "add_course_session", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("add_course_session", userId, body);
            return ResponseConverter.fromHandlerResponseCreated(result);
        });

        post("/api/v1/sessions/{planId}/start", "start_session", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("start_session", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/stop", "stop_session", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("stop_session", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/join", "join_session", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("join_session", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/leave", "leave_session", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("leave_session", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/raise-hand", "raise_hand", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("raise_hand", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/lower-hand", "lower_hand", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("lower_hand", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/promote", "promote_participant", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("promote_participant", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/demote", "demote_participant", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("demote_participant", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/mute", "mute_participant", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("mute_participant", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/kick", "kick_participant", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("kick_participant", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        patch("/api/v1/sessions/{planId}/gifts", "toggle_session_gifts", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("toggle_session_gifts", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/sessions/{planId}/participants", "get_session_participants", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("get_session_participants", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/sessions/{planId}/raised-hands", "get_raised_hands", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("get_raised_hands", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/sessions/live", "get_live_sessions", (userId, body, pathParams, queryParams) -> {
            if (queryParams.get("limit") != null) body.setLimit(Integer.parseInt(queryParams.get("limit")));
            String result = sessionHandler.handleRequest("get_live_sessions", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        get("/api/v1/sessions/upcoming", "get_upcoming_sessions", (userId, body, pathParams, queryParams) -> {
            body.setCategory(queryParams.get("category"));
            if (queryParams.get("limit") != null) body.setLimit(Integer.parseInt(queryParams.get("limit")));
            String result = sessionHandler.handleRequest("get_upcoming_sessions", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/sessions/{planId}/gifts/send", "send_gift", (userId, body, pathParams, queryParams) -> {
            body.setPlanId(pathParams.get("planId"));
            String result = sessionHandler.handleRequest("send_gift", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        // --- Admin ---
        get("/api/v1/admin/shipping-orders", "get_platform_shipping_orders", (userId, body, pathParams, queryParams) -> {
            String result = productOrderHandler.handleRequest("get_platform_shipping_orders", userId, body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/admin/make-admin", "make_admin", (userId, body, pathParams, queryParams) -> {
            String result = adminHandler.handleRequest("make_admin", body);
            return ResponseConverter.fromHandlerResponse(result);
        });

        post("/api/v1/admin/remove-admin", "remove_admin", (userId, body, pathParams, queryParams) -> {
            String result = adminHandler.handleRequest("remove_admin", body);
            return ResponseConverter.fromHandlerResponse(result);
        });
    }

    // ============= Route Helpers =============

    private void get(String pathPattern, String functionName, RouteHandler handler) {
        routes.add(new Route("GET", pathPattern, functionName, handler));
    }

    private void post(String pathPattern, String functionName, RouteHandler handler) {
        routes.add(new Route("POST", pathPattern, functionName, handler));
    }

    private void put(String pathPattern, String functionName, RouteHandler handler) {
        routes.add(new Route("PUT", pathPattern, functionName, handler));
    }

    private void patch(String pathPattern, String functionName, RouteHandler handler) {
        routes.add(new Route("PATCH", pathPattern, functionName, handler));
    }

    private void delete(String pathPattern, String functionName, RouteHandler handler) {
        routes.add(new Route("DELETE", pathPattern, functionName, handler));
    }

    static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) return params;
        for (String pair : queryString.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                params.put(kv[0], "");
            }
        }
        return params;
    }

    // ============= Route Model =============

    @FunctionalInterface
    interface RouteHandler {
        ApiResponse handle(String userId, RequestBody body, Map<String, String> pathParams, Map<String, String> queryParams) throws Exception;
    }

    static class Route {
        final String method;
        final Pattern pattern;
        final List<String> paramNames;
        final String functionName;
        final RouteHandler handler;

        Route(String method, String pathTemplate, String functionName, RouteHandler handler) {
            this.method = method;
            this.functionName = functionName;
            this.handler = handler;
            this.paramNames = new ArrayList<>();

            // Convert path template to regex: /api/v1/experts/{expertId}/products -> /api/v1/experts/([^/]+)/products
            String regex = pathTemplate;
            Matcher m = Pattern.compile("\\{(\\w+)}").matcher(pathTemplate);
            while (m.find()) {
                paramNames.add(m.group(1));
            }
            regex = regex.replaceAll("\\{\\w+}", "([^/]+)");
            this.pattern = Pattern.compile("^" + regex + "$");
        }
    }
}
