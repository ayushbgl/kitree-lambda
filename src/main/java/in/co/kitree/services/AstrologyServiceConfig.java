package in.co.kitree.services;

public class AstrologyServiceConfig {

    public static final int HTTP_TIMEOUT_SECONDS = 10;

    /**
     * Base URL for the astrology lambda.
     * Must be set via ASTROLOGY_LAMBDA_URL environment variable in template.yaml.
     * Example: https://xxxx.lambda-url.ap-south-1.on.aws
     */
    public static String getLambdaBaseUrl() {
        return getRequiredEnv("ASTROLOGY_LAMBDA_URL");
    }

    /**
     * API token for authenticating with the astrology lambda.
     * Must be set via ASTROLOGY_API_TOKEN environment variable in template.yaml.
     */
    public static String getApiToken() {
        return getRequiredEnv("ASTROLOGY_API_TOKEN");
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required environment variable " + name + " is not set. "
                    + "Configure it in template.yaml under Environment.Variables.");
        }
        // Remove trailing slash for consistent usage
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
