package in.co.kitree.services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

import java.util.Map;

public class NotificationService {

    public static void sendNotification(String userToken) {

        LoggingService.info("notification_send_started");
        Message message = Message.builder()
                .putData("data", "Hello")
                .putData("type", "test")
                .setToken(userToken)
                .build();

        String response = null;
        try {
            response = FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            LoggingService.error("notification_send_failed", e);
            return;
        }
        LoggingService.info("notification_sent_successfully", Map.of("response", response != null ? response : "null"));
    }
}
