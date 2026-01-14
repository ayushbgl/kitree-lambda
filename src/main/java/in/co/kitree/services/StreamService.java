package in.co.kitree.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/**
 * Service for interacting with Stream Video API and verifying webhooks.
 * Handles webhook signature verification and Stream API calls.
 */
public class StreamService {
    
    private static final String STREAM_API_BASE_URL = "https://video.stream-io-api.com/api/v2/video";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
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
     * The signature is sent in the X-SIGNATURE header.
     * 
     * @param body The raw webhook body
     * @param signature The signature from the X-SIGNATURE header
     * @return true if signature is valid, false otherwise
     */
    public boolean verifyWebhookSignature(String body, String signature) {
        if (signature == null || signature.isEmpty()) {
            System.out.println("[StreamService] No signature provided for verification");
            return false;
        }
        
        if (apiSecret == null || apiSecret.isEmpty()) {
            System.out.println("[StreamService] No API secret configured, skipping signature verification");
            return false;
        }
        
        try {
            // Compute HMAC-SHA256 signature using API secret
            String computedSignatureHex = computeHmacSha256(body, apiSecret);
            
            // Stream may send signature in different formats, try to match:
            // 1. Direct hex comparison
            boolean isValid = computedSignatureHex.equalsIgnoreCase(signature);
            
            // 2. If not matching, try with "sha256=" prefix (some webhook implementations use this)
            if (!isValid && signature.startsWith("sha256=")) {
                isValid = computedSignatureHex.equalsIgnoreCase(signature.substring(7));
            }
            
            // 3. Try base64 encoded comparison
            if (!isValid) {
                String computedSignatureBase64 = computeHmacSha256Base64(body, apiSecret);
                isValid = computedSignatureBase64.equals(signature);
            }
            
            System.out.println("[StreamService] Signature verification: " + (isValid ? "VALID" : "INVALID"));
            if (!isValid) {
                System.out.println("[StreamService] Computed (hex): " + computedSignatureHex);
                System.out.println("[StreamService] Received: " + signature);
            }
            
            return isValid;
        } catch (Exception e) {
            System.err.println("[StreamService] Error verifying signature: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Computes HMAC-SHA256 signature and returns as Base64 string.
     */
    private String computeHmacSha256Base64(String message, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        sha256Hmac.init(secretKeySpec);
        byte[] signedBytes = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signedBytes);
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
    
    /**
     * End a Stream video call.
     * This is used to programmatically end a call when finalizing a consultation.
     * 
     * @param callType The call type (e.g., "consultation_video", "consultation_audio")
     * @param callId The call ID
     * @return true if the call was ended successfully (or was already ended)
     */
    public boolean endCall(String callType, String callId) {
        if (callType == null || callId == null) {
            System.err.println("[StreamService] Cannot end call: missing callType or callId");
            return false;
        }
        
        if (!isConfigured()) {
            System.err.println("[StreamService] Cannot end call: service not configured");
            return false;
        }
        
        try {
            // Stream Video API endpoint to end a call
            // POST /video/call/{type}/{id}/end
            String url = String.format("%s/call/%s/%s/end?api_key=%s", 
                STREAM_API_BASE_URL, callType, callId, apiKey);
            
            // Create JWT token for API authentication
            String authToken = createServerAuthToken();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", authToken)
                    .header("stream-auth-type", "jwt")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            System.out.println("[StreamService] End call response: " + statusCode + " - " + response.body());
            
            // 200 = success, 404 = call not found (already ended), both are OK
            if (statusCode == 200 || statusCode == 404) {
                return true;
            }
            
            System.err.println("[StreamService] Failed to end call: HTTP " + statusCode);
            return false;
            
        } catch (Exception e) {
            System.err.println("[StreamService] Error ending call: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create a server-side JWT token for Stream API authentication.
     * This is a simplified version - for production, use proper JWT library.
     */
    private String createServerAuthToken() {
        try {
            // Create a simple JWT for server authentication
            // Header
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            
            // Payload with server flag
            long now = System.currentTimeMillis() / 1000;
            long exp = now + 3600; // 1 hour expiry
            String payload = String.format(
                "{\"user_id\":\"server\",\"iat\":%d,\"exp\":%d}", now, exp);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.getBytes(StandardCharsets.UTF_8));
            
            // Signature
            String signatureInput = header + "." + encodedPayload;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            byte[] signatureBytes = sha256Hmac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            
            return header + "." + encodedPayload + "." + signature;
            
        } catch (Exception e) {
            System.err.println("[StreamService] Error creating auth token: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Parse a call CID into type and id components.
     * Call CID format: {type}:{id}
     * 
     * @param callCid The full call CID (e.g., "consultation_video:abc123")
     * @return Array with [type, id] or null if invalid format
     */
    public static String[] parseCallCid(String callCid) {
        if (callCid == null || !callCid.contains(":")) {
            return null;
        }
        String[] parts = callCid.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return parts;
    }
}
