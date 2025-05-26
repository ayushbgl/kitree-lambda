package in.co.kitree.services;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

public class NotificationService {

    public static void sendNotification(String userToken) {

        System.out.println("Starting sending notification");
        Message message = Message.builder()
                .putData("data", "Hello")
                .putData("type", "test")
                .setToken(userToken)
                .build();

        String response = null;
        try {
            response = FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            e.printStackTrace();
            System.out.println("Some error in sending notification");
        }
        System.out.println("Successfully sent message: " + response);
    }
}
