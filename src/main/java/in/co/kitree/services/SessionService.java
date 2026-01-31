package in.co.kitree.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service handling all session/webinar/course related business logic.
 * Manages session lifecycle, participant permissions, gifts, and Stream integration.
 */
public class SessionService {

    private final Firestore db;
    private final StreamService streamService;
    private final boolean isTest;

    // Session status constants
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_LIVE = "LIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    // Participant role constants
    public static final String ROLE_VIEWER = "viewer";
    public static final String ROLE_SPEAKER = "speaker";
    public static final String ROLE_HOST = "host";

    // Interaction mode constants
    public static final String MODE_COZY = "cozy";           // <=20 participants, all can speak
    public static final String MODE_CLASSROOM = "classroom"; // 21-100, raised hand model
    public static final String MODE_BROADCAST = "broadcast"; // >100, host only

    // Stream call types
    public static final String CALL_TYPE_DEFAULT = "default";
    public static final String CALL_TYPE_AUDIO_ROOM = "audio_room";
    public static final String CALL_TYPE_LIVESTREAM = "livestream";

    // Default gift options (amounts in INR, IDs map to app translations)
    public static final List<Map<String, Object>> DEFAULT_GIFT_OPTIONS = Arrays.asList(
            createGiftOption("mithai", 11),
            createGiftOption("phool", 51),
            createGiftOption("nariyal", 101),
            createGiftOption("vastra", 501),
            createGiftOption("dakshina", 1100)
    );

    private static Map<String, Object> createGiftOption(String id, int amount) {
        Map<String, Object> gift = new HashMap<>();
        gift.put("id", id);
        gift.put("amount", amount);
        return gift;
    }

    public SessionService(Firestore db, StreamService streamService, boolean isTest) {
        this.db = db;
        this.streamService = streamService;
        this.isTest = isTest;
    }

    /**
     * Determine interaction mode based on max participants.
     */
    public static String determineInteractionMode(int maxParticipants) {
        if (maxParticipants <= 20) return MODE_COZY;
        if (maxParticipants <= 100) return MODE_CLASSROOM;
        return MODE_BROADCAST;
    }

    /**
     * Get Stream call type based on interaction mode.
     */
    public static String getStreamCallType(String interactionMode) {
        switch (interactionMode) {
            case MODE_COZY:
                return CALL_TYPE_DEFAULT;
            case MODE_CLASSROOM:
                return CALL_TYPE_AUDIO_ROOM;
            case MODE_BROADCAST:
                return CALL_TYPE_LIVESTREAM;
            default:
                return CALL_TYPE_AUDIO_ROOM;
        }
    }

    /**
     * Create a new session plan (standalone webinar or course).
     */
    public Map<String, Object> createSessionPlan(
            String expertId,
            String title,
            String description,
            String category,
            Long scheduledStartTimeMs,
            Integer durationMinutes,
            Double price,
            String currency,
            Integer maxParticipants,
            Integer sessionCount,
            String interactionMode,
            Boolean giftsEnabled,
            List<Map<String, Object>> giftOptions
    ) throws ExecutionException, InterruptedException {

        String planId = "plan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Timestamp now = Timestamp.now();
        Timestamp scheduledStartTime = Timestamp.ofTimeMicroseconds(scheduledStartTimeMs * 1000);

        // Auto-determine interaction mode if not provided
        if (interactionMode == null || interactionMode.isEmpty()) {
            interactionMode = determineInteractionMode(maxParticipants != null ? maxParticipants : 50);
        }

        // Use default gift options if not provided
        if (giftOptions == null || giftOptions.isEmpty()) {
            giftOptions = DEFAULT_GIFT_OPTIONS;
        }

        Map<String, Object> planDoc = new HashMap<>();
        planDoc.put("planId", planId);
        planDoc.put("type", "SESSION");
        planDoc.put("title", title);
        planDoc.put("description", description);
        planDoc.put("category", category);

        // Pricing
        planDoc.put("amount", price != null ? price : 0.0);
        planDoc.put("currency", currency != null ? currency : "INR");
        planDoc.put("isFree", price == null || price <= 0);

        // Session configuration
        planDoc.put("sessionCount", sessionCount != null ? sessionCount : 1);
        planDoc.put("maxParticipants", maxParticipants != null ? maxParticipants : 50);
        planDoc.put("interactionMode", interactionMode);

        // Scheduling (for standalone session, sessionCount=1)
        planDoc.put("scheduledStartTime", scheduledStartTime);
        planDoc.put("durationMinutes", durationMinutes != null ? durationMinutes : 60);

        // Live state
        planDoc.put("isLive", false);
        planDoc.put("actualStartTime", null);
        planDoc.put("actualEndTime", null);
        planDoc.put("streamCallId", planId);
        planDoc.put("currentParticipantCount", 0);

        // Gifts
        planDoc.put("giftsEnabled", giftsEnabled != null ? giftsEnabled : true);
        planDoc.put("giftOptions", giftOptions);
        planDoc.put("totalGiftsReceived", 0.0);

        // Metadata
        planDoc.put("createdAt", now);
        planDoc.put("updatedAt", now);

        // Write to Firestore
        db.collection("users").document(expertId)
                .collection("plans").document(planId)
                .set(planDoc).get();

        // If this is a course (sessionCount > 1), create session subcollections
        if (sessionCount != null && sessionCount > 1) {
            // Sessions will be created separately via add_course_session
            // For now, just return the course plan
        }

        Map<String, Object> result = new HashMap<>();
        result.put("planId", planId);
        result.put("success", true);
        return result;
    }

    /**
     * Add a session to a course (for multi-session courses).
     */
    public Map<String, Object> addCourseSession(
            String expertId,
            String planId,
            Integer sessionNumber,
            String title,
            String description,
            Long scheduledStartTimeMs,
            Integer durationMinutes
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();
        Timestamp scheduledStartTime = Timestamp.ofTimeMicroseconds(scheduledStartTimeMs * 1000);

        String sessionId = String.valueOf(sessionNumber);
        String streamCallId = planId + "_s" + sessionNumber;

        Map<String, Object> sessionDoc = new HashMap<>();
        sessionDoc.put("sessionNumber", sessionNumber);
        sessionDoc.put("title", title);
        sessionDoc.put("description", description);
        sessionDoc.put("scheduledStartTime", scheduledStartTime);
        sessionDoc.put("durationMinutes", durationMinutes != null ? durationMinutes : 60);
        sessionDoc.put("isLive", false);
        sessionDoc.put("actualStartTime", null);
        sessionDoc.put("actualEndTime", null);
        sessionDoc.put("streamCallId", streamCallId);
        sessionDoc.put("currentParticipantCount", 0);
        sessionDoc.put("recordingUrl", null);
        sessionDoc.put("createdAt", now);
        sessionDoc.put("updatedAt", now);

        db.collection("users").document(expertId)
                .collection("plans").document(planId)
                .collection("sessions").document(sessionId)
                .set(sessionDoc).get();

        Map<String, Object> result = new HashMap<>();
        result.put("sessionNumber", sessionNumber);
        result.put("streamCallId", streamCallId);
        result.put("success", true);
        return result;
    }

    /**
     * Start a session - creates Stream call and returns host token.
     * For courses, specify sessionNumber; for standalone, sessionNumber is null.
     */
    public Map<String, Object> startSession(String expertId, String planId, Integer sessionNumber)
            throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();
        DocumentReference docRef;
        String streamCallId;
        String interactionMode;

        // Get plan document to verify ownership and get settings
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new IllegalArgumentException("Plan not found");
        }

        String planType = planDoc.getString("type");
        if (!"SESSION".equals(planType)) {
            throw new IllegalArgumentException("Plan is not a SESSION type");
        }

        interactionMode = planDoc.getString("interactionMode");
        if (interactionMode == null) {
            interactionMode = MODE_CLASSROOM;
        }

        Integer sessionCount = planDoc.getLong("sessionCount") != null
                ? planDoc.getLong("sessionCount").intValue() : 1;

        // Determine which document to update
        if (sessionCount > 1 && sessionNumber != null) {
            // Course session
            docRef = db.collection("users").document(expertId)
                    .collection("plans").document(planId)
                    .collection("sessions").document(String.valueOf(sessionNumber));
            streamCallId = planId + "_s" + sessionNumber;

            DocumentSnapshot sessionDoc = docRef.get().get();
            if (!sessionDoc.exists()) {
                throw new IllegalArgumentException("Session not found");
            }

            // Check if already live
            Boolean isLive = sessionDoc.getBoolean("isLive");
            if (Boolean.TRUE.equals(isLive)) {
                return generateHostToken(streamCallId, expertId, interactionMode);
            }
        } else {
            // Standalone session
            docRef = db.collection("users").document(expertId)
                    .collection("plans").document(planId);
            streamCallId = planId;

            // Check if already live
            Boolean isLive = planDoc.getBoolean("isLive");
            if (Boolean.TRUE.equals(isLive)) {
                return generateHostToken(streamCallId, expertId, interactionMode);
            }
        }

        // Create Stream call
        String callType = getStreamCallType(interactionMode);
        boolean callCreated = streamService.createCall(callType, streamCallId, expertId);

        if (!callCreated) {
            throw new RuntimeException("Failed to create Stream call");
        }

        // Update document to mark as live
        Map<String, Object> updates = new HashMap<>();
        updates.put("isLive", true);
        updates.put("actualStartTime", now);
        updates.put("updatedAt", now);
        docRef.update(updates).get();

        // If this is a course session, also update the main plan's updatedAt
        if (sessionCount > 1 && sessionNumber != null) {
            db.collection("users").document(expertId)
                    .collection("plans").document(planId)
                    .update("updatedAt", now).get();
        }

        System.out.println("[SessionService] Started session: " + streamCallId + " with call type: " + callType);

        return generateHostToken(streamCallId, expertId, interactionMode);
    }

    /**
     * Stop/end a session.
     */
    public Map<String, Object> stopSession(String expertId, String planId, Integer sessionNumber)
            throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();
        DocumentReference docRef;
        String streamCallId;

        // Get plan document
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new IllegalArgumentException("Plan not found");
        }

        String interactionMode = planDoc.getString("interactionMode");
        if (interactionMode == null) {
            interactionMode = MODE_CLASSROOM;
        }

        Integer sessionCount = planDoc.getLong("sessionCount") != null
                ? planDoc.getLong("sessionCount").intValue() : 1;

        // Determine which document to update
        if (sessionCount > 1 && sessionNumber != null) {
            // Course session
            docRef = db.collection("users").document(expertId)
                    .collection("plans").document(planId)
                    .collection("sessions").document(String.valueOf(sessionNumber));
            streamCallId = planId + "_s" + sessionNumber;
        } else {
            // Standalone session
            docRef = db.collection("users").document(expertId)
                    .collection("plans").document(planId);
            streamCallId = planId;
        }

        // Update document to mark as ended
        Map<String, Object> updates = new HashMap<>();
        updates.put("isLive", false);
        updates.put("actualEndTime", now);
        updates.put("updatedAt", now);
        docRef.update(updates).get();

        // End Stream call
        String callType = getStreamCallType(interactionMode);
        streamService.endCall(callType, streamCallId);

        System.out.println("[SessionService] Stopped session: " + streamCallId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("streamCallId", streamCallId);
        return result;
    }

    /**
     * Join a session as a participant (viewer by default).
     * Validates that the user has a PAID order for this session.
     */
    public Map<String, Object> joinSession(
            String userId,
            String orderId,
            String expertId,
            String planId,
            Integer sessionNumber,
            String userName,
            String userPhotoUrl
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        // Get plan document
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new IllegalArgumentException("Plan not found");
        }

        String interactionMode = planDoc.getString("interactionMode");
        if (interactionMode == null) {
            interactionMode = MODE_CLASSROOM;
        }

        Integer sessionCount = planDoc.getLong("sessionCount") != null
                ? planDoc.getLong("sessionCount").intValue() : 1;

        // Verify session is live
        Boolean isLive;
        String streamCallId;
        DocumentReference sessionDocRef = null;

        if (sessionCount > 1 && sessionNumber != null) {
            // Course session
            sessionDocRef = db.collection("users").document(expertId)
                    .collection("plans").document(planId)
                    .collection("sessions").document(String.valueOf(sessionNumber));
            DocumentSnapshot sessionDoc = sessionDocRef.get().get();
            if (!sessionDoc.exists()) {
                throw new IllegalArgumentException("Session not found");
            }
            isLive = sessionDoc.getBoolean("isLive");
            streamCallId = planId + "_s" + sessionNumber;
        } else {
            isLive = planDoc.getBoolean("isLive");
            streamCallId = planId;
        }

        if (!Boolean.TRUE.equals(isLive)) {
            throw new IllegalStateException("Session is not live");
        }

        // Verify user has a valid PAID order
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        DocumentSnapshot orderDoc = orderRef.get().get();

        if (!orderDoc.exists()) {
            throw new IllegalArgumentException("Order not found");
        }

        String orderPlanId = orderDoc.getString("planId");
        String orderExpertId = orderDoc.getString("expertId");
        String orderStatus = orderDoc.getString("status");

        if (!planId.equals(orderPlanId) || !expertId.equals(orderExpertId)) {
            throw new SecurityException("Order does not match this session");
        }

        if (!"PAID".equals(orderStatus)) {
            throw new SecurityException("Order is not paid");
        }

        // Check if user was kicked
        Boolean isKicked = orderDoc.getBoolean("isKicked");
        if (Boolean.TRUE.equals(isKicked)) {
            throw new SecurityException("You have been removed from this session");
        }

        // Check current role (may have been promoted earlier)
        String role = orderDoc.getString("role");
        if (role == null || role.isEmpty()) {
            // Default role based on interaction mode
            role = MODE_COZY.equals(interactionMode) ? ROLE_SPEAKER : ROLE_VIEWER;
        }

        // Update order with join info
        Map<String, Object> orderUpdates = new HashMap<>();
        orderUpdates.put("joinedAt", now);
        orderUpdates.put("role", role);
        orderUpdates.put("userName", userName);
        orderUpdates.put("userPhotoUrl", userPhotoUrl);
        orderUpdates.put("updatedAt", now);

        // For courses, track which sessions attended
        if (sessionCount > 1 && sessionNumber != null) {
            orderUpdates.put("sessionsAttended", FieldValue.arrayUnion(sessionNumber));
        }

        orderRef.update(orderUpdates).get();

        // Increment participant count
        if (sessionDocRef != null) {
            sessionDocRef.update("currentParticipantCount", FieldValue.increment(1)).get();
        } else {
            db.collection("users").document(expertId)
                    .collection("plans").document(planId)
                    .update("currentParticipantCount", FieldValue.increment(1)).get();
        }

        // Generate token
        String callType = getStreamCallType(interactionMode);
        String token = streamService.createUserToken(userId, role, streamCallId, callType);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("callType", callType);
        result.put("callId", streamCallId);
        result.put("role", role);
        result.put("apiKey", streamService.getApiKey());
        result.put("canSpeak", ROLE_SPEAKER.equals(role) || ROLE_HOST.equals(role));
        return result;
    }

    /**
     * User leaves session.
     */
    public Map<String, Object> leaveSession(
            String userId,
            String orderId,
            String expertId,
            String planId,
            Integer sessionNumber
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        // Update order
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        orderRef.update(
                "leftAt", now,
                "updatedAt", now
        ).get();

        // Decrement participant count
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        Integer sessionCount = planDoc.getLong("sessionCount") != null
                ? planDoc.getLong("sessionCount").intValue() : 1;

        if (sessionCount > 1 && sessionNumber != null) {
            db.collection("users").document(expertId)
                    .collection("plans").document(planId)
                    .collection("sessions").document(String.valueOf(sessionNumber))
                    .update("currentParticipantCount", FieldValue.increment(-1)).get();
        } else {
            db.collection("users").document(expertId)
                    .collection("plans").document(planId)
                    .update("currentParticipantCount", FieldValue.increment(-1)).get();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * User raises hand to request speaking permission.
     */
    public Map<String, Object> raiseHand(String userId, String orderId)
            throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);

        orderRef.update(
                "hasRaisedHand", true,
                "handRaisedAt", now,
                "updatedAt", now
        ).get();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * User lowers hand.
     */
    public Map<String, Object> lowerHand(String userId, String orderId)
            throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);

        orderRef.update(
                "hasRaisedHand", false,
                "handRaisedAt", FieldValue.delete(),
                "updatedAt", now
        ).get();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * Expert promotes a participant to speaker.
     */
    public Map<String, Object> promoteParticipant(
            String expertId,
            String planId,
            String targetUserId,
            String targetOrderId
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        // Verify expert owns the plan
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new SecurityException("Not authorized");
        }

        String interactionMode = planDoc.getString("interactionMode");
        String streamCallId = planDoc.getString("streamCallId");
        if (streamCallId == null) {
            streamCallId = planId;
        }

        // Update participant's order
        DocumentReference orderRef = db.collection("users").document(targetUserId)
                .collection("orders").document(targetOrderId);

        orderRef.update(
                "role", ROLE_SPEAKER,
                "hasRaisedHand", false,
                "handRaisedAt", FieldValue.delete(),
                "updatedAt", now
        ).get();

        // Update role in Stream
        String callType = getStreamCallType(interactionMode != null ? interactionMode : MODE_CLASSROOM);
        boolean updated = streamService.updateMemberRole(callType, streamCallId, targetUserId, ROLE_SPEAKER);

        System.out.println("[SessionService] Promoted " + targetUserId + " to speaker: " + updated);

        Map<String, Object> result = new HashMap<>();
        result.put("success", updated);
        result.put("userId", targetUserId);
        result.put("newRole", ROLE_SPEAKER);
        return result;
    }

    /**
     * Expert demotes a speaker to viewer.
     */
    public Map<String, Object> demoteParticipant(
            String expertId,
            String planId,
            String targetUserId,
            String targetOrderId
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        // Verify expert owns the plan
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new SecurityException("Not authorized");
        }

        String interactionMode = planDoc.getString("interactionMode");
        String streamCallId = planDoc.getString("streamCallId");
        if (streamCallId == null) {
            streamCallId = planId;
        }

        // Update participant's order
        DocumentReference orderRef = db.collection("users").document(targetUserId)
                .collection("orders").document(targetOrderId);

        orderRef.update(
                "role", ROLE_VIEWER,
                "updatedAt", now
        ).get();

        // Update role in Stream
        String callType = getStreamCallType(interactionMode != null ? interactionMode : MODE_CLASSROOM);
        boolean updated = streamService.updateMemberRole(callType, streamCallId, targetUserId, ROLE_VIEWER);

        System.out.println("[SessionService] Demoted " + targetUserId + " to viewer: " + updated);

        Map<String, Object> result = new HashMap<>();
        result.put("success", updated);
        result.put("userId", targetUserId);
        result.put("newRole", ROLE_VIEWER);
        return result;
    }

    /**
     * Expert mutes a participant.
     */
    public Map<String, Object> muteParticipant(
            String expertId,
            String planId,
            String targetUserId,
            String targetOrderId
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        // Verify expert owns the plan
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new SecurityException("Not authorized");
        }

        // Update participant's order
        DocumentReference orderRef = db.collection("users").document(targetUserId)
                .collection("orders").document(targetOrderId);

        orderRef.update(
                "isMuted", true,
                "updatedAt", now
        ).get();

        // TODO: Mute in Stream via API

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("userId", targetUserId);
        return result;
    }

    /**
     * Expert kicks a participant from session.
     */
    public Map<String, Object> kickParticipant(
            String expertId,
            String planId,
            String targetUserId,
            String targetOrderId
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        // Verify expert owns the plan
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new SecurityException("Not authorized");
        }

        // Update participant's order
        DocumentReference orderRef = db.collection("users").document(targetUserId)
                .collection("orders").document(targetOrderId);

        orderRef.update(
                "isKicked", true,
                "role", ROLE_VIEWER,
                "updatedAt", now
        ).get();

        // TODO: Block user in Stream via API

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("userId", targetUserId);
        return result;
    }

    /**
     * Expert toggles gifts on/off during session.
     */
    public Map<String, Object> toggleGifts(String expertId, String planId, Boolean enabled)
            throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        db.collection("users").document(expertId)
                .collection("plans").document(planId)
                .update(
                        "giftsEnabled", enabled,
                        "updatedAt", now
                ).get();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("giftsEnabled", enabled);
        return result;
    }

    /**
     * Get all participants for a session.
     */
    public List<Map<String, Object>> getParticipants(String expertId, String planId)
            throws ExecutionException, InterruptedException {

        QuerySnapshot snapshot = db.collectionGroup("orders")
                .whereEqualTo("expertId", expertId)
                .whereEqualTo("planId", planId)
                .whereEqualTo("type", "SESSION")
                .whereEqualTo("status", "PAID")
                .get().get();

        List<Map<String, Object>> participants = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> participant = new HashMap<>();
            participant.put("orderId", doc.getId());
            participant.put("userId", doc.getString("userId"));
            participant.put("userName", doc.getString("userName"));
            participant.put("userPhotoUrl", doc.getString("userPhotoUrl"));
            participant.put("role", doc.getString("role"));
            participant.put("hasRaisedHand", doc.getBoolean("hasRaisedHand"));
            participant.put("isMuted", doc.getBoolean("isMuted"));
            participant.put("joinedAt", doc.getTimestamp("joinedAt"));
            participants.add(participant);
        }

        return participants;
    }

    /**
     * Get participants with raised hands.
     */
    public List<Map<String, Object>> getRaisedHands(String expertId, String planId)
            throws ExecutionException, InterruptedException {

        QuerySnapshot snapshot = db.collectionGroup("orders")
                .whereEqualTo("expertId", expertId)
                .whereEqualTo("planId", planId)
                .whereEqualTo("hasRaisedHand", true)
                .orderBy("handRaisedAt", Query.Direction.ASCENDING)
                .get().get();

        List<Map<String, Object>> hands = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Map<String, Object> hand = new HashMap<>();
            hand.put("orderId", doc.getId());
            hand.put("userId", doc.getString("userId"));
            hand.put("userName", doc.getString("userName"));
            hand.put("userPhotoUrl", doc.getString("userPhotoUrl"));
            hand.put("handRaisedAt", doc.getTimestamp("handRaisedAt"));
            hands.add(hand);
        }

        return hands;
    }

    /**
     * Get upcoming sessions (scheduled, not yet live).
     */
    public List<Map<String, Object>> getUpcomingSessions(String category, Integer limit)
            throws ExecutionException, InterruptedException {

        Query query = db.collectionGroup("plans")
                .whereEqualTo("type", "SESSION")
                .whereEqualTo("isLive", false)
                .whereGreaterThan("scheduledStartTime", Timestamp.now())
                .orderBy("scheduledStartTime", Query.Direction.ASCENDING)
                .limit(limit != null ? limit : 20);

        if (category != null && !category.isEmpty()) {
            query = query.whereEqualTo("category", category);
        }

        QuerySnapshot snapshot = query.get().get();

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            sessions.add(doc.getData());
        }

        return sessions;
    }

    /**
     * Get currently live sessions.
     */
    public List<Map<String, Object>> getLiveSessions(Integer limit)
            throws ExecutionException, InterruptedException {

        QuerySnapshot snapshot = db.collectionGroup("plans")
                .whereEqualTo("type", "SESSION")
                .whereEqualTo("isLive", true)
                .limit(limit != null ? limit : 20)
                .get().get();

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            sessions.add(doc.getData());
        }

        return sessions;
    }

    /**
     * Send a gift during a live session.
     * Validates user has a PAID order and gifts are enabled.
     */
    public Map<String, Object> sendGift(
            String userId,
            String orderId,
            String expertId,
            String planId,
            String giftId,
            Double giftAmount
    ) throws ExecutionException, InterruptedException {

        Timestamp now = Timestamp.now();

        // Verify user has a valid order
        DocumentSnapshot orderDoc = db.collection("users").document(userId)
                .collection("orders").document(orderId).get().get();

        if (!orderDoc.exists()) {
            throw new IllegalArgumentException("Order not found");
        }

        String orderPlanId = orderDoc.getString("planId");
        String orderExpertId = orderDoc.getString("expertId");
        String orderStatus = orderDoc.getString("status");

        if (!planId.equals(orderPlanId) || !expertId.equals(orderExpertId)) {
            throw new SecurityException("Order does not match this session");
        }

        if (!"PAID".equals(orderStatus)) {
            throw new SecurityException("Order is not paid");
        }

        // Verify session is live and gifts are enabled
        DocumentSnapshot planDoc = db.collection("users").document(expertId)
                .collection("plans").document(planId).get().get();

        if (!planDoc.exists()) {
            throw new IllegalArgumentException("Plan not found");
        }

        Boolean isLive = planDoc.getBoolean("isLive");
        if (!Boolean.TRUE.equals(isLive)) {
            throw new IllegalStateException("Session is not live");
        }

        Boolean giftsEnabled = planDoc.getBoolean("giftsEnabled");
        if (!Boolean.TRUE.equals(giftsEnabled)) {
            throw new IllegalStateException("Gifts are not enabled for this session");
        }

        // Validate gift option
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> giftOptions = (List<Map<String, Object>>) planDoc.get("giftOptions");
        boolean validGift = false;
        Double validatedAmount = giftAmount;

        if (giftOptions != null) {
            for (Map<String, Object> option : giftOptions) {
                if (giftId.equals(option.get("id"))) {
                    validGift = true;
                    // Use the amount from plan config if not provided
                    if (validatedAmount == null) {
                        Object amt = option.get("amount");
                        if (amt instanceof Number) {
                            validatedAmount = ((Number) amt).doubleValue();
                        }
                    }
                    break;
                }
            }
        }

        if (!validGift) {
            throw new IllegalArgumentException("Invalid gift option");
        }

        // Record the gift in a subcollection
        String giftDocId = "gift_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Map<String, Object> giftDoc = new HashMap<>();
        giftDoc.put("giftId", giftId);
        giftDoc.put("amount", validatedAmount);
        giftDoc.put("senderId", userId);
        giftDoc.put("senderOrderId", orderId);
        giftDoc.put("sentAt", now);

        db.collection("users").document(expertId)
                .collection("plans").document(planId)
                .collection("gifts").document(giftDocId)
                .set(giftDoc).get();

        // Update total gifts received
        db.collection("users").document(expertId)
                .collection("plans").document(planId)
                .update("totalGiftsReceived", FieldValue.increment(validatedAmount)).get();

        System.out.println("[SessionService] Gift sent: " + giftId + " ($" + validatedAmount + ") from " + userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("giftId", giftId);
        result.put("amount", validatedAmount);
        return result;
    }

    /**
     * Generate host token for expert.
     */
    private Map<String, Object> generateHostToken(String streamCallId, String expertId, String interactionMode) {
        String callType = getStreamCallType(interactionMode);
        String token = streamService.createUserToken(expertId, ROLE_HOST, streamCallId, callType);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("callType", callType);
        result.put("callId", streamCallId);
        result.put("role", ROLE_HOST);
        result.put("apiKey", streamService.getApiKey());
        result.put("canSpeak", true);
        return result;
    }
}
