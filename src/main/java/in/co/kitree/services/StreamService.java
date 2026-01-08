package in.co.kitree.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Service for interacting with Stream Video API and verifying webhooks.
 * Handles webhook signature verification and Stream API calls.
 */
public class StreamService {
    
    private final boolean isTest;
    private final String apiKey;
    private final String apiSecret;
    
    /**
     * Creates a new StreamService instance.
     * Loads API credentials from secrets.json based on environment.
     * 
     * Expected secrets.json format:
     * {
     *   "STREAM_API_KEY": "...",
     *   "STREAM_API_SECRET": "...",
     *   "STREAM_API_KEY_TEST": "...",
     *   "STREAM_API_SECRET_TEST": "..."
     * }
     * 
     * @param isTest true for test environment, false for production
     */
    public StreamService(boolean isTest) {
        this.isTest = isTest;
        
        String key = "";
        String secret = "";
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(new File("secrets.json"));
            
            if (isTest) {
                key = rootNode.path("STREAM_API_KEY_TEST").asText("");
                secret = rootNode.path("STREAM_API_SECRET_TEST").asText("");
            } else {
                key = rootNode.path("STREAM_API_KEY").asText("");
                secret = rootNode.path("STREAM_API_SECRET").asText("");
            }
            
            System.out.println("[StreamService] Initialized for " + (isTest ? "TEST" : "PROD") + 
                " environment, API key loaded: " + !key.isEmpty());
            
        } catch (IOException e) {
            System.err.println("[StreamService] Error reading secrets.json: " + e.getMessage());
        }
        
        this.apiKey = key;
        this.apiSecret = secret;
    }
    
    /**
     * Verifies the webhook signature from Stream.
     * Stream uses HMAC-SHA256 with the API secret to sign webhook payloads.
     * 
     * @param body The raw webhook body
     * @param signature The signature from the X-Webhook-Signature or X-Signature header
     * @return true if signature is valid, false otherwise
     */
    public boolean verifyWebhookSignature(String body, String signature) {
        if (signature == null || signature.isEmpty()) {
            System.out.println("[StreamService] No signature provided for verification");
            return false;
        }
        
        if (apiSecret == null || apiSecret.isEmpty()) {
            System.out.println("[StreamService] No API secret configured, skipping signature verification");
            // In development, you might want to return true here
            // For production, this should return false
            return false;
        }
        
        try {
            String computedSignature = computeHmacSha256(body, apiSecret);
            boolean isValid = computedSignature.equals(signature);
            
            System.out.println("[StreamService] Signature verification: " + (isValid ? "VALID" : "INVALID"));
            if (!isValid) {
                System.out.println("[StreamService] Expected: " + computedSignature);
                System.out.println("[StreamService] Received: " + signature);
            }
            
            return isValid;
        } catch (Exception e) {
            System.err.println("[StreamService] Error verifying signature: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Computes HMAC-SHA256 signature for the given message.
     * 
     * @param message The message to sign
     * @param secret The secret key
     * @return The hex-encoded signature
     */
    private String computeHmacSha256(String message, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        sha256Hmac.init(secretKeySpec);
        byte[] signedBytes = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        
        // Convert to hex string
        StringBuilder sb = new StringBuilder();
        for (byte b : signedBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Get the API key for this environment.
     * 
     * @return The Stream API key
     */
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * Check if the service is properly configured.
     * 
     * @return true if API key and secret are configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() 
            && apiSecret != null && !apiSecret.isEmpty();
    }
    
    /**
     * Check if running in test mode.
     * 
     * @return true if test environment
     */
    public boolean isTest() {
        return isTest;
    }
}
