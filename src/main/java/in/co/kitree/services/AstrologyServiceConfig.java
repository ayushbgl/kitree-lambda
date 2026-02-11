package in.co.kitree.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class AstrologyServiceConfig {

    public static final int HTTP_TIMEOUT_SECONDS = 10;

    private static String cachedApiToken;

    /**
     * Base URL for the astrology lambda.
     * Set via ASTROLOGY_LAMBDA_URL environment variable (injected by SAM template).
     */
    public static String getLambdaBaseUrl() {
        String value = System.getenv("ASTROLOGY_LAMBDA_URL");
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required environment variable ASTROLOGY_LAMBDA_URL is not set. "
                    + "Configure it in template.yaml under Environment.Variables.");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * API token for authenticating with the astrology lambda.
     * Read from secrets.json (same pattern as Razorpay keys).
     */
    public static String getApiToken() {
        if (cachedApiToken != null) {
            return cachedApiToken;
        }
        try {
            JsonNode rootNode = new ObjectMapper().readTree(new File("secrets.json"));
            String token = rootNode.path("ASTROLOGY_API_TOKEN").asText("");
            if (token.isEmpty()) {
                throw new IllegalStateException("ASTROLOGY_API_TOKEN not found in secrets.json");
            }
            cachedApiToken = token;
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read ASTROLOGY_API_TOKEN from secrets.json", e);
        }
    }
}
