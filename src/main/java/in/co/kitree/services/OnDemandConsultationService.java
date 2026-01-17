package in.co.kitree.services;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import in.co.kitree.pojos.OnDemandConsultationOrder;
import in.co.kitree.pojos.PlatformFeeConfig;
import in.co.kitree.pojos.ServicePlan;
import in.co.kitree.pojos.WalletTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Service for handling on-demand consultation operations.
 */
public class OnDemandConsultationService {

    private final Firestore db;
    private final WalletService walletService;

    public OnDemandConsultationService(Firestore db, WalletService walletService) {
        this.db = db;
        this.walletService = walletService;
    }

    /**
     * Constructor without WalletService for simple queries.
     */
    public OnDemandConsultationService(Firestore db) {
        this.db = db;
        this.walletService = null;
    }

    /**
     * Get the on-demand rate for a consultation type from a plan.
     *
     * @param plan            The service plan
     * @param consultationType The consultation type (audio, video, chat)
     * @return The rate per minute, or null if not configured
     */
    public Double getOnDemandRate(ServicePlan plan, String consultationType) {
        if (plan == null) return null;
        
        switch (consultationType.toLowerCase()) {
            case "audio":
                return plan.getOnDemandRatePerMinuteAudio();
            case "video":
                return plan.getOnDemandRatePerMinuteVideo();
            case "chat":
                return plan.getOnDemandRatePerMinuteChat();
            default:
                return null;
        }
    }

    /**
     * Get the currency for on-demand consultations from a plan.
     *
     * @param plan The service plan
     * @return The currency code (defaults to plan currency or WalletService.DEFAULT_CURRENCY)
     */
    public String getOnDemandCurrency(ServicePlan plan) {
        if (plan == null) return WalletService.getDefaultCurrency();
        
        String currency = plan.getOnDemandCurrency();
        if (currency != null && !currency.isEmpty()) {
            return currency;
        }
        
        currency = plan.getCurrency();
        return currency != null && !currency.isEmpty() ? currency : WalletService.getDefaultCurrency();
    }

    /**
     * Create an on-demand consultation order.
     *
     * @param order The order to create
     * @return The order ID
     */
    public String createOrder(OnDemandConsultationOrder order) throws ExecutionException, InterruptedException {
        String orderId = UUID.randomUUID().toString();
        order.setOrderId(orderId);
        order.setCreatedAt(Timestamp.now());
        
        DocumentReference orderRef = db.collection("users").document(order.getUserId())
                .collection("orders").document(orderId);
        
        Map<String, Object> orderData = orderToMap(order);
        orderRef.set(orderData).get();
        
        return orderId;
    }

    /**
     * Create an on-demand consultation order within a Firestore transaction.
     */
    public String createOrderInTransaction(Transaction transaction, OnDemandConsultationOrder order)
            throws ExecutionException, InterruptedException {
        String orderId = UUID.randomUUID().toString();
        order.setOrderId(orderId);
        order.setCreatedAt(Timestamp.now());
        
        DocumentReference orderRef = db.collection("users").document(order.getUserId())
                .collection("orders").document(orderId);
        
        Map<String, Object> orderData = orderToMap(order);
        transaction.set(orderRef, orderData);
        
        return orderId;
    }

    /**
     * Get an on-demand consultation order.
     *
     * @param userId  The user's ID
     * @param orderId The order ID
     * @return The order, or null if not found
     */
    public OnDemandConsultationOrder getOrder(String userId, String orderId) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = db.collection("users").document(userId)
                .collection("orders").document(orderId).get().get();
        
        if (!doc.exists()) return null;
        
        // Verify it's an on-demand consultation order
        String type = doc.getString("type");
        if (!"ON_DEMAND_CONSULTATION".equals(type)) return null;
        
        return docToOrder(doc);
    }

    /**
     * Get an on-demand consultation order within a transaction.
     */
    public OnDemandConsultationOrder getOrderInTransaction(Transaction transaction, String userId, String orderId)
            throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        DocumentSnapshot doc = transaction.get(orderRef).get();
        
        if (!doc.exists()) return null;
        
        // Verify it's an on-demand consultation order
        String type = doc.getString("type");
        if (!"ON_DEMAND_CONSULTATION".equals(type)) return null;
        
        return docToOrder(doc);
    }

    /**
     * Update order status to CONNECTED and set start_time.
     */
    public void connectOrder(String userId, String orderId) throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "CONNECTED");
        updates.put("start_time", Timestamp.now());
        
        orderRef.update(updates).get();
    }

    /**
     * Update order status to CONNECTED and set start_time within a transaction.
     */
    public void connectOrderInTransaction(Transaction transaction, String userId, String orderId) {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "CONNECTED");
        updates.put("start_time", Timestamp.now());
        
        transaction.update(orderRef, updates);
    }

    /**
     * Update max_allowed_duration for mid-consultation recharge.
     */
    public void updateMaxDuration(String userId, String orderId, Long additionalDuration)
            throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        
        orderRef.update("max_allowed_duration", FieldValue.increment(additionalDuration)).get();
    }

    /**
     * Update max_allowed_duration within a transaction.
     */
    public void updateMaxDurationInTransaction(Transaction transaction, String userId, String orderId, Long newMaxDuration) {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        
        transaction.update(orderRef, "max_allowed_duration", newMaxDuration);
    }

    /**
     * Complete an on-demand consultation order within a transaction.
     * Reads the order inside the transaction to ensure optimistic concurrency control.
     * Throws an exception if the order is not in CONNECTED status to prevent double-charging.
     */
    public void completeOrderInTransaction(Transaction transaction, String userId, String orderId,
                                           Long durationSeconds, Double cost, Double platformFeeAmount,
                                           Double expertEarnings) throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        
        // Read the order inside the transaction to enable optimistic concurrency control
        DocumentSnapshot orderDoc = transaction.get(orderRef).get();
        
        if (!orderDoc.exists()) {
            throw new IllegalStateException("Order not found: " + orderId);
        }
        
        // Verify it's an on-demand consultation order
        String type = orderDoc.getString("type");
        if (!"ON_DEMAND_CONSULTATION".equals(type)) {
            throw new IllegalStateException("Order is not an on-demand consultation: " + orderId);
        }
        
        // Check status inside transaction - only proceed if still CONNECTED
        // This prevents double-charging when concurrent requests try to complete the same order
        String currentStatus = orderDoc.getString("status");
        if (!"CONNECTED".equals(currentStatus)) {
            throw new IllegalStateException("Order is not in CONNECTED status (current: " + currentStatus + "): " + orderId);
        }
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "COMPLETED");
        updates.put("end_time", Timestamp.now());
        updates.put("duration_seconds", durationSeconds);
        updates.put("cost", cost);
        updates.put("platform_fee_amount", platformFeeAmount);
        updates.put("expert_earnings", expertEarnings);
        
        transaction.update(orderRef, updates);
    }

    /**
     * Update order with stream call CID.
     */
    public void updateStreamCallCid(String userId, String orderId, String streamCallCid)
            throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        orderRef.update("stream_call_cid", streamCallCid).get();
    }

    /**
     * Mark an order as FAILED within a transaction.
     */
    public void markOrderAsFailedInTransaction(Transaction transaction, String userId, String orderId)
            throws ExecutionException, InterruptedException {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        transaction.update(orderRef, "status", "FAILED");
    }

    /**
     * Calculate remaining seconds for an active consultation.
     */
    public Long calculateRemainingSeconds(OnDemandConsultationOrder order) {
        if (order.getStartTime() == null || order.getMaxAllowedDuration() == null) {
            return 0L;
        }
        
        long startTimeMillis = order.getStartTime().toDate().getTime();
        long nowMillis = System.currentTimeMillis();
        long elapsedSeconds = (nowMillis - startTimeMillis) / 1000;
        
        return Math.max(0, order.getMaxAllowedDuration() - elapsedSeconds);
    }

    /**
     * Calculate elapsed seconds for an active consultation.
     * Uses startTime as the baseline for total call duration.
     */
    public Long calculateElapsedSeconds(OnDemandConsultationOrder order) {
        if (order.getStartTime() == null) {
            return 0L;
        }

        long startTimeMillis = order.getStartTime().toDate().getTime();
        long endTimeMillis = order.getEndTime() != null
                ? order.getEndTime().toDate().getTime()
                : System.currentTimeMillis();

        return (endTimeMillis - startTimeMillis) / 1000;
    }

    /**
     * Calculate billable seconds for an active consultation.
     * Uses bothParticipantsJoinedAt as the start time for billing.
     * Only charges for time when BOTH user and expert were in the call.
     * Falls back to startTime if bothParticipantsJoinedAt is not set.
     */
    public Long calculateBillableSeconds(OnDemandConsultationOrder order) {
        // If we have both_participants_joined_at, use that for billing
        // Otherwise fall back to start_time (legacy behavior)
        Timestamp billingStartTime = order.getBothParticipantsJoinedAt();
        if (billingStartTime == null) {
            billingStartTime = order.getStartTime();
        }

        if (billingStartTime == null) {
            // If neither is set, no billing
            return 0L;
        }

        long startTimeMillis = billingStartTime.toDate().getTime();
        long endTimeMillis = order.getEndTime() != null
                ? order.getEndTime().toDate().getTime()
                : System.currentTimeMillis();

        long billableSeconds = (endTimeMillis - startTimeMillis) / 1000;

        // Ensure non-negative
        return Math.max(0L, billableSeconds);
    }

    /**
     * Calculate cost based on duration and rate.
     */
    public Double calculateCost(Long durationSeconds, Double ratePerMinute) {
        // Use exact seconds for precision
        double durationMinutes = durationSeconds / 60.0;
        double cost = durationMinutes * ratePerMinute;
        // Round to 2 decimal places
        return Math.round(cost * 100.0) / 100.0;
    }

    /**
     * Calculate platform fee amount.
     */
    public Double calculatePlatformFee(Double cost, Double platformFeePercent) {
        double fee = cost * (platformFeePercent / 100.0);
        // Round to 2 decimal places
        return Math.round(fee * 100.0) / 100.0;
    }

    /**
     * Check if there are any other connected consultations for an expert.
     */
    public boolean hasOtherConnectedConsultations(String expertId, String excludeOrderId)
            throws ExecutionException, InterruptedException {
        Query query = db.collectionGroup("orders")
                .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                .whereEqualTo("expertId", expertId)
                .whereEqualTo("status", "CONNECTED");
        
        for (QueryDocumentSnapshot doc : query.get().get().getDocuments()) {
            String orderId = doc.getId();
            if (!orderId.equals(excludeOrderId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there are any other connected consultations for an expert within a transaction.
     * This method queries for connected consultation order IDs first, then reads those specific
     * documents within the transaction to verify they're still CONNECTED atomically.
     * This prevents race conditions where consultations could connect/disconnect between the check
     * and the transaction commit.
     * 
     * @param transaction The Firestore transaction
     * @param expertId The expert ID
     * @param excludeOrderId The order ID to exclude from the check
     * @return true if there are other connected consultations, false otherwise
     */
    public boolean hasOtherConnectedConsultationsInTransaction(Transaction transaction, String expertId, String excludeOrderId)
            throws ExecutionException, InterruptedException {
        // Query for connected consultation order IDs before the transaction
        // (Firestore transactions don't support collection queries)
        Query query = db.collectionGroup("orders")
                .whereEqualTo("type", "ON_DEMAND_CONSULTATION")
                .whereEqualTo("expertId", expertId)
                .whereEqualTo("status", "CONNECTED");
        
        java.util.List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
        
        // Within the transaction, read each document to verify it's still CONNECTED
        // This ensures atomicity - we check status at the same time we update expert status
        for (QueryDocumentSnapshot doc : docs) {
            String orderId = doc.getId();
            if (orderId.equals(excludeOrderId)) {
                continue;
            }
            
            // Get the document reference to read within transaction
            String userId = doc.getString("user_id");
            if (userId == null) {
                continue;
            }
            
            DocumentReference orderRef = db.collection("users").document(userId)
                    .collection("orders").document(orderId);
            
            // Read within transaction to get current status atomically
            DocumentSnapshot orderDoc = transaction.get(orderRef).get();
            if (orderDoc.exists() && "CONNECTED".equals(orderDoc.getString("status"))) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Find any order (on-demand or scheduled) by Stream call CID.
     * Searches across all users' orders using collection group query.
     * 
     * The Stream call CID format is typically: {call_type}:{order_id}
     * e.g., "consultation_video:abc123" or "consultation_audio:xyz789"
     * 
     * @param streamCallCid The Stream call CID to search for
     * @return Map containing order data (orderId, userId, expertId, type, status), or null if not found
     */
    public Map<String, Object> getOrderByStreamCallCid(String streamCallCid) 
            throws ExecutionException, InterruptedException {
        if (streamCallCid == null || streamCallCid.isEmpty()) {
            return null;
        }
        
        // Query across all users' orders for the stream_call_cid
        Query query = db.collectionGroup("orders")
                .whereEqualTo("stream_call_cid", streamCallCid)
                .limit(1);
        
        QuerySnapshot snapshot = query.get().get();
        
        if (snapshot.isEmpty()) {
            // Try deriving from CID format: {call_type}:{order_id}
            // This works when stream_call_cid field isn't set but order_id matches
            String[] parts = streamCallCid.split(":");
            if (parts.length >= 2) {
                String orderId = parts[parts.length - 1];
                System.out.println("[OnDemandConsultationService] Trying to find order by ID from CID: " + orderId);
                
                // Search by orderId across all orders
                Query orderIdQuery = db.collectionGroup("orders")
                        .whereEqualTo("orderId", orderId)
                        .limit(1);
                snapshot = orderIdQuery.get().get();
            }
        }
        
        if (snapshot.isEmpty()) {
            return null;
        }
        
        DocumentSnapshot doc = snapshot.getDocuments().get(0);

        // All fields use snake_case
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("order_id", doc.getId());
        orderData.put("user_id", doc.getString("user_id"));
        orderData.put("expert_id", doc.getString("expert_id"));
        orderData.put("type", doc.getString("type"));
        orderData.put("status", doc.getString("status"));
        orderData.put("stream_call_cid", doc.getString("stream_call_cid"));

        // Include additional fields useful for processing
        if (doc.contains("consultation_type")) {
            orderData.put("consultation_type", doc.getString("consultation_type"));
        }
        if (doc.contains("start_time")) {
            orderData.put("start_time", doc.getTimestamp("start_time"));
        }
        if (doc.contains("expert_rate_per_minute")) {
            orderData.put("expert_rate_per_minute", doc.getDouble("expert_rate_per_minute"));
        }
        if (doc.contains("max_allowed_duration")) {
            orderData.put("max_allowed_duration", doc.getLong("max_allowed_duration"));
        }
        if (doc.contains("platform_fee_percent")) {
            orderData.put("platform_fee_percent", doc.getDouble("platform_fee_percent"));
        }
        if (doc.contains("currency")) {
            orderData.put("currency", doc.getString("currency"));
        }

        return orderData;
    }

    private Map<String, Object> orderToMap(OnDemandConsultationOrder order) {
        Map<String, Object> map = new HashMap<>();
        // All fields use snake_case for consistency
        map.put("order_id", order.getOrderId());
        map.put("user_id", order.getUserId());
        map.put("expert_id", order.getExpertId());
        map.put("type", order.getType());
        map.put("consultation_type", order.getConsultationType());
        map.put("status", order.getStatus());
        map.put("created_at", order.getCreatedAt());

        if (order.getUserName() != null) map.put("user_name", order.getUserName());
        if (order.getExpertName() != null) map.put("expert_name", order.getExpertName());
        if (order.getPlanId() != null) map.put("plan_id", order.getPlanId());
        if (order.getCategory() != null) map.put("category", order.getCategory());
        if (order.getExpertRatePerMinute() != null) map.put("expert_rate_per_minute", order.getExpertRatePerMinute());
        if (order.getCurrency() != null) map.put("currency", order.getCurrency());
        if (order.getMaxAllowedDuration() != null) map.put("max_allowed_duration", order.getMaxAllowedDuration());
        if (order.getPlatformFeePercent() != null) map.put("platform_fee_percent", order.getPlatformFeePercent());
        if (order.getStreamCallCid() != null) map.put("stream_call_cid", order.getStreamCallCid());
        if (order.getChatSessionId() != null) map.put("chat_session_id", order.getChatSessionId());

        return map;
    }

    private OnDemandConsultationOrder docToOrder(DocumentSnapshot doc) {
        OnDemandConsultationOrder order = new OnDemandConsultationOrder();
        // All fields use snake_case for consistency
        order.setOrderId(doc.getId());
        order.setUserId(doc.getString("user_id"));
        order.setUserName(doc.getString("user_name"));
        order.setExpertId(doc.getString("expert_id"));
        order.setExpertName(doc.getString("expert_name"));
        order.setPlanId(doc.getString("plan_id"));
        order.setType(doc.getString("type"));
        order.setConsultationType(doc.getString("consultation_type"));
        order.setCategory(doc.getString("category"));
        order.setStatus(doc.getString("status"));
        order.setStreamCallCid(doc.getString("stream_call_cid"));
        order.setChatSessionId(doc.getString("chat_session_id"));

        if (doc.contains("created_at")) order.setCreatedAt(doc.getTimestamp("created_at"));
        if (doc.contains("start_time")) order.setStartTime(doc.getTimestamp("start_time"));
        if (doc.contains("end_time")) order.setEndTime(doc.getTimestamp("end_time"));
        if (doc.contains("expert_rate_per_minute")) order.setExpertRatePerMinute(doc.getDouble("expert_rate_per_minute"));
        if (doc.contains("currency")) order.setCurrency(doc.getString("currency"));
        if (doc.contains("max_allowed_duration")) order.setMaxAllowedDuration(doc.getLong("max_allowed_duration"));
        if (doc.contains("duration_seconds")) order.setDurationSeconds(doc.getLong("duration_seconds"));
        if (doc.contains("cost")) order.setCost(doc.getDouble("cost"));
        if (doc.contains("platform_fee_percent")) order.setPlatformFeePercent(doc.getDouble("platform_fee_percent"));
        if (doc.contains("platform_fee_amount")) order.setPlatformFeeAmount(doc.getDouble("platform_fee_amount"));
        if (doc.contains("expert_earnings")) order.setExpertEarnings(doc.getDouble("expert_earnings"));

        return order;
    }
}
