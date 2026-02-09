package in.co.kitree.handlers;

import com.google.cloud.firestore.Firestore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.co.kitree.pojos.*;
import in.co.kitree.services.*;

import java.util.List;
import java.util.Map;

/**
 * Handler for session/webinar/course video streaming operations.
 * Covers all Stream.io-based session functions.
 * Extracted from Handler.java as part of refactoring.
 */
public class SessionHandler {

    private final Firestore db;
    private final StreamService streamService;
    private final PythonLambdaService pythonLambdaService;
    private final boolean isTest;
    private final Gson gson;

    public SessionHandler(Firestore db, StreamService streamService, PythonLambdaService pythonLambdaService, boolean isTest) {
        this.db = db;
        this.streamService = streamService;
        this.pythonLambdaService = pythonLambdaService;
        this.isTest = isTest;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Check if a function name is handled by this handler.
     */
    public static boolean handles(String functionName) {
        return functionName != null && (
            functionName.equals("get_stream_user_token") ||
            functionName.equals("create_call") ||
            functionName.equals("create_session_plan") ||
            functionName.equals("add_course_session") ||
            functionName.equals("start_session") ||
            functionName.equals("stop_session") ||
            functionName.equals("join_session") ||
            functionName.equals("leave_session") ||
            functionName.equals("raise_hand") ||
            functionName.equals("lower_hand") ||
            functionName.equals("promote_participant") ||
            functionName.equals("demote_participant") ||
            functionName.equals("mute_participant") ||
            functionName.equals("kick_participant") ||
            functionName.equals("toggle_session_gifts") ||
            functionName.equals("get_session_participants") ||
            functionName.equals("get_raised_hands") ||
            functionName.equals("get_live_sessions") ||
            functionName.equals("get_upcoming_sessions") ||
            functionName.equals("send_gift")
        );
    }

    /**
     * Route the request to the appropriate handler method.
     */
    public String handleRequest(String functionName, String userId, RequestBody requestBody) {
        try {
            switch (functionName) {
                case "get_stream_user_token":
                    return handleGetStreamUserToken(userId, requestBody);
                case "create_call":
                    return handleCreateCall(userId, requestBody);
                case "create_session_plan":
                    return handleCreateSessionPlan(userId, requestBody);
                case "add_course_session":
                    return handleAddCourseSession(userId, requestBody);
                case "start_session":
                    return handleStartSession(userId, requestBody);
                case "stop_session":
                    return handleStopSession(userId, requestBody);
                case "join_session":
                    return handleJoinSession(userId, requestBody);
                case "leave_session":
                    return handleLeaveSession(userId, requestBody);
                case "raise_hand":
                    return handleRaiseHand(userId, requestBody);
                case "lower_hand":
                    return handleLowerHand(userId, requestBody);
                case "promote_participant":
                    return handlePromoteParticipant(userId, requestBody);
                case "demote_participant":
                    return handleDemoteParticipant(userId, requestBody);
                case "mute_participant":
                    return handleMuteParticipant(userId, requestBody);
                case "kick_participant":
                    return handleKickParticipant(userId, requestBody);
                case "toggle_session_gifts":
                    return handleToggleSessionGifts(userId, requestBody);
                case "get_session_participants":
                    return handleGetSessionParticipants(userId, requestBody);
                case "get_raised_hands":
                    return handleGetRaisedHands(userId, requestBody);
                case "get_live_sessions":
                    return handleGetLiveSessions(requestBody);
                case "get_upcoming_sessions":
                    return handleGetUpcomingSessions(requestBody);
                case "send_gift":
                    return handleSendGift(userId, requestBody);
                default:
                    return null;
            }
        } catch (Exception e) {
            LoggingService.error("session_handler_exception", e);
            return gson.toJson(Map.of("success", false, "errorMessage", e.getMessage()));
        }
    }

    // ============= Handler Methods =============

    private String handleGetStreamUserToken(String userId, RequestBody requestBody) throws Exception {
        PythonLambdaEventRequest getStreamUserTokenEvent = new PythonLambdaEventRequest();
        getStreamUserTokenEvent.setFunction("get_stream_user_token");
        getStreamUserTokenEvent.setUserId(userId);
        getStreamUserTokenEvent.setTest(isTest);
        getStreamUserTokenEvent.setUserName(UserService.getUserDetails(this.db, userId).getName());
        return pythonLambdaService.invokePythonLambda(getStreamUserTokenEvent).getStreamUserToken();
    }

    private String handleCreateCall(String userId, RequestBody requestBody) throws Exception {
        FirebaseOrder order = fetchOrder(requestBody.getUserId(), requestBody.getOrderId());
        if (order == null) {
            return "Not authorized";
        }
        String expertId = order.getExpertId();
        if (java.util.Objects.equals(requestBody.getUserId(), expertId)) {
            return "Cannot call yourself";
        }
        if (!userId.equals(expertId)) {
            return "Not authorized";
        }

        PythonLambdaEventRequest createCallEvent = new PythonLambdaEventRequest();
        createCallEvent.setFunction("create_call");
        createCallEvent.setType("CONSULTATION");
        createCallEvent.setUserId(requestBody.getUserId());
        createCallEvent.setUserName(order.getUserName());
        createCallEvent.setExpertId(expertId);
        createCallEvent.setOrderId(requestBody.getOrderId());
        createCallEvent.setExpertName(requestBody.getExpertName());
        createCallEvent.setVideo(order.isVideo());
        createCallEvent.setTest(isTest);

        pythonLambdaService.invokePythonLambda(createCallEvent);
        return "Success";
    }

    private String handleCreateSessionPlan(String userId, RequestBody requestBody) throws Exception {
        String title = requestBody.getTitle();
        Long scheduledStartTime = requestBody.getScheduledStartTime();

        if (title == null || title.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Title is required"));
        }
        if (scheduledStartTime == null) {
            return gson.toJson(Map.of("success", false, "error", "Scheduled start time is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.createSessionPlan(
                userId, title, requestBody.getDescription(), requestBody.getCategory(),
                scheduledStartTime, requestBody.getDurationMinutes(), requestBody.getPrice(),
                requestBody.getCurrency(), requestBody.getMaxParticipants(), requestBody.getSessionCount(),
                requestBody.getInteractionMode(), requestBody.getGiftsEnabled(), requestBody.getGiftOptions()
        );
        return gson.toJson(result);
    }

    private String handleAddCourseSession(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        Integer sessionNumber = requestBody.getSessionNumber();

        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        if (sessionNumber == null) {
            return gson.toJson(Map.of("success", false, "error", "Session number is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.addCourseSession(
                userId, planId, sessionNumber, requestBody.getTitle(),
                requestBody.getDescription(), requestBody.getScheduledStartTime(),
                requestBody.getDurationMinutes()
        );
        return gson.toJson(result);
    }

    private String handleStartSession(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.startSession(userId, planId, requestBody.getSessionNumber());
        return gson.toJson(result);
    }

    private String handleStopSession(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.stopSession(userId, planId, requestBody.getSessionNumber());
        return gson.toJson(result);
    }

    private String handleJoinSession(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();
        String expertId = requestBody.getExpertId();
        String planId = requestBody.getPlanId();

        if (orderId == null || orderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
        }
        if (expertId == null || expertId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Expert ID is required"));
        }
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.joinSession(
                userId, orderId, expertId, planId, requestBody.getSessionNumber(),
                requestBody.getUserName(), requestBody.getUserPhotoUrl()
        );
        return gson.toJson(result);
    }

    private String handleLeaveSession(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();
        String expertId = requestBody.getExpertId();
        String planId = requestBody.getPlanId();

        if (orderId == null || orderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
        }
        if (expertId == null || expertId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Expert ID is required"));
        }
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.leaveSession(
                userId, orderId, expertId, planId, requestBody.getSessionNumber()
        );
        return gson.toJson(result);
    }

    private String handleRaiseHand(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();
        if (orderId == null || orderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
        }
        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.raiseHand(userId, orderId);
        return gson.toJson(result);
    }

    private String handleLowerHand(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();
        if (orderId == null || orderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
        }
        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.lowerHand(userId, orderId);
        return gson.toJson(result);
    }

    private String handlePromoteParticipant(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        String targetUserId = requestBody.getTargetUserId();
        String targetOrderId = requestBody.getTargetOrderId();

        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        if (targetUserId == null || targetUserId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
        }
        if (targetOrderId == null || targetOrderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.promoteParticipant(userId, planId, targetUserId, targetOrderId);
        return gson.toJson(result);
    }

    private String handleDemoteParticipant(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        String targetUserId = requestBody.getTargetUserId();
        String targetOrderId = requestBody.getTargetOrderId();

        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        if (targetUserId == null || targetUserId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
        }
        if (targetOrderId == null || targetOrderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.demoteParticipant(userId, planId, targetUserId, targetOrderId);
        return gson.toJson(result);
    }

    private String handleMuteParticipant(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        String targetUserId = requestBody.getTargetUserId();
        String targetOrderId = requestBody.getTargetOrderId();

        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        if (targetUserId == null || targetUserId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
        }
        if (targetOrderId == null || targetOrderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.muteParticipant(userId, planId, targetUserId, targetOrderId);
        return gson.toJson(result);
    }

    private String handleKickParticipant(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        String targetUserId = requestBody.getTargetUserId();
        String targetOrderId = requestBody.getTargetOrderId();

        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        if (targetUserId == null || targetUserId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target user ID is required"));
        }
        if (targetOrderId == null || targetOrderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Target order ID is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.kickParticipant(userId, planId, targetUserId, targetOrderId);
        return gson.toJson(result);
    }

    private String handleToggleSessionGifts(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.toggleGifts(userId, planId, requestBody.getGiftsEnabled());
        return gson.toJson(result);
    }

    private String handleGetSessionParticipants(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        SessionService sessionService = new SessionService(db, streamService, isTest);
        List<Map<String, Object>> participants = sessionService.getParticipants(userId, planId);
        return gson.toJson(Map.of("success", true, "participants", participants));
    }

    private String handleGetRaisedHands(String userId, RequestBody requestBody) throws Exception {
        String planId = requestBody.getPlanId();
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        SessionService sessionService = new SessionService(db, streamService, isTest);
        List<Map<String, Object>> hands = sessionService.getRaisedHands(userId, planId);
        return gson.toJson(Map.of("success", true, "raisedHands", hands));
    }

    private String handleGetLiveSessions(RequestBody requestBody) throws Exception {
        SessionService sessionService = new SessionService(db, streamService, isTest);
        List<Map<String, Object>> sessions = sessionService.getLiveSessions(requestBody.getLimit());
        return gson.toJson(Map.of("success", true, "sessions", sessions));
    }

    private String handleGetUpcomingSessions(RequestBody requestBody) throws Exception {
        SessionService sessionService = new SessionService(db, streamService, isTest);
        List<Map<String, Object>> sessions = sessionService.getUpcomingSessions(
                requestBody.getCategory(), requestBody.getLimit()
        );
        return gson.toJson(Map.of("success", true, "sessions", sessions));
    }

    private String handleSendGift(String userId, RequestBody requestBody) throws Exception {
        String orderId = requestBody.getOrderId();
        String expertId = requestBody.getExpertId();
        String planId = requestBody.getPlanId();
        String giftId = requestBody.getGiftId();

        if (orderId == null || orderId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Order ID is required"));
        }
        if (expertId == null || expertId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Expert ID is required"));
        }
        if (planId == null || planId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Plan ID is required"));
        }
        if (giftId == null || giftId.isEmpty()) {
            return gson.toJson(Map.of("success", false, "error", "Gift ID is required"));
        }

        SessionService sessionService = new SessionService(db, streamService, isTest);
        Map<String, Object> result = sessionService.sendGift(userId, orderId, expertId, planId, giftId, requestBody.getPrice());
        return gson.toJson(result);
    }

    // ============= Private Helpers =============

    private FirebaseOrder fetchOrder(String clientId, String orderId) {
        FirebaseOrder firebaseOrder = new FirebaseOrder();
        firebaseOrder.setOrderId(orderId);
        firebaseOrder.setUserId(clientId);
        try {
            var doc = this.db.collection("users").document(clientId).collection("orders").document(orderId);
            var documentSnapshot = doc.get().get();
            if (documentSnapshot.exists()) {
                var data = java.util.Objects.requireNonNull(documentSnapshot.getData());
                firebaseOrder.setExpertId((String) data.getOrDefault("expert_id", ""));
                firebaseOrder.setUserName((String) data.getOrDefault("user_name", ""));
                firebaseOrder.setVideo((boolean) data.getOrDefault("is_video", false));
                firebaseOrder.setPlanId((String) data.getOrDefault("plan_id", ""));
            }
        } catch (Exception e) {
            return null;
        }
        return firebaseOrder;
    }
}
