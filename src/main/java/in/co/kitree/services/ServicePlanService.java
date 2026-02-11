package in.co.kitree.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import in.co.kitree.pojos.ServicePlan;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Reads ServicePlan documents from Firestore.
 * Shared by ServiceHandler and ConsultationHandler to avoid duplicating
 * the ~40-line plan-parsing logic.
 */
public class ServicePlanService {

    private final Firestore db;

    public ServicePlanService(Firestore db) {
        this.db = db;
    }

    /**
     * Load a ServicePlan from users/{expertId}/plans/{planId}.
     * Returns null if the document does not exist.
     */
    public ServicePlan getPlanDetails(String planId, String expertId) throws ExecutionException, InterruptedException {
        DocumentReference doc = db.collection("users").document(expertId).collection("plans").document(planId);
        ApiFuture<DocumentSnapshot> ref = doc.get();
        DocumentSnapshot documentSnapshot = ref.get();

        if (!documentSnapshot.exists()) {
            return null;
        }

        Map<String, Object> data = Objects.requireNonNull(documentSnapshot.getData());
        ServicePlan plan = new ServicePlan();
        plan.setPlanId(planId);

        plan.setAmount(((Long) data.getOrDefault("amount", 0L)).doubleValue());
        plan.setCurrency((String) data.getOrDefault("currency", ""));
        plan.setSubscription((Boolean) data.getOrDefault("isSubscription", false));
        plan.setVideo((Boolean) data.getOrDefault("isVideo", false));
        plan.setRazorpayId((String) data.getOrDefault("razorpayId", ""));
        plan.setType((String) data.getOrDefault("type", ""));
        plan.setSubtype((String) data.getOrDefault("subtype", ""));
        plan.setCategory((String) data.getOrDefault("category", ""));

        Object durationObj = data.getOrDefault("duration", 30L);
        Long duration;
        if (durationObj instanceof Integer) {
            duration = ((Integer) durationObj).longValue();
        } else if (durationObj instanceof Long) {
            duration = (Long) durationObj;
        } else {
            duration = 30L;
        }
        plan.setDuration(duration);
        plan.setDurationUnit((String) data.getOrDefault("durationUnit", "MINUTES"));

        if (documentSnapshot.contains("date")) {
            plan.setDate((com.google.cloud.Timestamp) documentSnapshot.get("date"));
        }
        if (documentSnapshot.contains("sessionStartedAt")) {
            plan.setSessionStartedAt((com.google.cloud.Timestamp) documentSnapshot.get("sessionStartedAt"));
        }
        if (documentSnapshot.contains("sessionCompletedAt")) {
            plan.setSessionCompletedAt((com.google.cloud.Timestamp) documentSnapshot.get("sessionCompletedAt"));
        }
        plan.setTitle((String) data.getOrDefault("title", ""));

        // On-demand consultation rate fields
        if (data.containsKey("onDemandRatePerMinuteAudio")) {
            Object audioRateObj = data.get("onDemandRatePerMinuteAudio");
            if (audioRateObj != null) {
                plan.setOnDemandRatePerMinuteAudio(convertToDouble(audioRateObj));
            }
        }
        if (data.containsKey("onDemandRatePerMinuteVideo")) {
            Object videoRateObj = data.get("onDemandRatePerMinuteVideo");
            if (videoRateObj != null) {
                plan.setOnDemandRatePerMinuteVideo(convertToDouble(videoRateObj));
            }
        }
        if (data.containsKey("onDemandRatePerMinuteChat")) {
            Object chatRateObj = data.get("onDemandRatePerMinuteChat");
            if (chatRateObj != null) {
                plan.setOnDemandRatePerMinuteChat(convertToDouble(chatRateObj));
            }
        }
        if (data.containsKey("onDemandCurrency")) {
            plan.setOnDemandCurrency((String) data.get("onDemandCurrency"));
        }

        return plan;
    }

    private static Double convertToDouble(Object obj) {
        if (obj instanceof Double) {
            return (Double) obj;
        } else if (obj instanceof Integer) {
            return ((Integer) obj).doubleValue();
        } else if (obj instanceof Long) {
            return ((Long) obj).doubleValue();
        }
        return null;
    }
}
