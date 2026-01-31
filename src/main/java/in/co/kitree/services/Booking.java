package in.co.kitree.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import in.co.kitree.services.agora.RtcTokenBuilder2;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Booking {
    private final Firestore db;
    private String expertId;

    public Booking(Firestore db) {
        this.db = db;
    }

    public Map<String, String> makeCall(String userId, String orderId) {
        LoggingService.info("booking_make_call_received", Map.of("orderId", orderId));
        if (!validateIfUserCanMakeCall(userId, orderId)) {
            LoggingService.warn("booking_validation_failed", Map.of("orderId", orderId));
            return null;
        }
        String expertToken = getExpertToken(expertId);
        if(expertToken == null){
            return null;
        }
        String channelName = generateCallChannel(orderId);
        sendNotificationToExpert(expertToken, channelName);

        Map<String, String> response = new HashMap<>();
        response.put("channelName", channelName);
        response.put("agoraToken", new RtcTokenBuilder2().buildTokenWithUid(
                "fb2aa4822d2540e9851da8f9f7e32799",
                "a3d1596ea63f4896b62a9301586bb602",
                channelName,
                2,
                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                21600,
                21600)
        );
        response.put("appId", "fb2aa4822d2540e9851da8f9f7e32799");
        return response;
    }

    private void sendNotificationToExpert(String expertToken, String channelName) {

        LoggingService.info("booking_send_notification_started", Map.of("channelName", channelName));
        Message message = Message.builder().putData("appId", "fb2aa4822d2540e9851da8f9f7e32799")
                .putData("channel", channelName)
                .putData("type", "call")
                .putData("agoraToken", new RtcTokenBuilder2().buildTokenWithUid(
                                "fb2aa4822d2540e9851da8f9f7e32799",
                                "a3d1596ea63f4896b62a9301586bb602",
                                channelName,
                                1,
                                RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                                21600,
                                21600)
                )
                .setToken(expertToken)
                .build();

        String response = null;
        try {
            response = FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            LoggingService.error("booking_notification_send_failed", e);
            return;
        }
        LoggingService.info("booking_notification_sent", Map.of("response", response != null ? response : "null"));
    }

    private String generateCallChannel(String orderId) {
        return orderId;
    }

    private String getExpertToken(String expertId) {
        DocumentReference doc = this.db.collection("users").document(expertId);
        ApiFuture<DocumentSnapshot> ref = doc.get();
        DocumentSnapshot documentSnapshot;
        try {
            documentSnapshot = ref.get();
            if (documentSnapshot.exists()) {
                return Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("fcmToken", null).toString();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private boolean validateIfUserCanMakeCall(String userId, String orderId) {
        DocumentReference doc = this.db.collection("users").document(userId).collection("orders").document(orderId);
        ApiFuture<DocumentSnapshot> ref = doc.get();
        DocumentSnapshot documentSnapshot;
        try {
            documentSnapshot = ref.get();
            if (documentSnapshot.exists()) {
                this.expertId = Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("expertId", "").toString();
                assert !this.expertId.isEmpty();
                return Objects.requireNonNull(documentSnapshot.getData()).getOrDefault("paymentReceivedAt", null) != null && documentSnapshot.getData().getOrDefault("bookingCompletedAt", null) == null;
            }
        } catch (Exception e) {
            LoggingService.error("booking_user_validation_error", e, Map.of("userId", userId, "orderId", orderId));
            return false;
        }
        return false;
    }
}
