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
     * @return The currency code (defaults to plan currency or "INR")
     */
    public String getOnDemandCurrency(ServicePlan plan) {
        if (plan == null) return "INR";
        
        String currency = plan.getOnDemandCurrency();
        if (currency != null && !currency.isEmpty()) {
            return currency;
        }
        
        currency = plan.getCurrency();
        return currency != null && !currency.isEmpty() ? currency : "INR";
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
     */
    public void completeOrderInTransaction(Transaction transaction, String userId, String orderId,
                                           Long durationSeconds, Double cost, Double platformFeeAmount,
                                           Double expertEarnings) {
        DocumentReference orderRef = db.collection("users").document(userId)
                .collection("orders").document(orderId);
        
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

    private Map<String, Object> orderToMap(OnDemandConsultationOrder order) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderId", order.getOrderId());
        map.put("userId", order.getUserId());
        map.put("expertId", order.getExpertId());
        map.put("type", order.getType());
        map.put("consultation_type", order.getConsultationType());
        map.put("status", order.getStatus());
        map.put("createdAt", order.getCreatedAt());
        
        if (order.getUserName() != null) map.put("userName", order.getUserName());
        if (order.getExpertName() != null) map.put("expertName", order.getExpertName());
        if (order.getPlanId() != null) map.put("planId", order.getPlanId());
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
        order.setOrderId(doc.getId());
        order.setUserId(doc.getString("userId"));
        order.setUserName(doc.getString("userName"));
        order.setExpertId(doc.getString("expertId"));
        order.setExpertName(doc.getString("expertName"));
        order.setPlanId(doc.getString("planId"));
        order.setType(doc.getString("type"));
        order.setConsultationType(doc.getString("consultation_type"));
        order.setCategory(doc.getString("category"));
        order.setStatus(doc.getString("status"));
        order.setStreamCallCid(doc.getString("stream_call_cid"));
        order.setChatSessionId(doc.getString("chat_session_id"));
        
        if (doc.contains("createdAt")) order.setCreatedAt(doc.getTimestamp("createdAt"));
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
