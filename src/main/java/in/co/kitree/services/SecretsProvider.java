package in.co.kitree.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Centralized, cached provider for secrets.json values.
 * Reads the file once on first access (thread-safe via double-checked locking)
 * and caches all key-value pairs in memory.
 *
 * Package-private — only accessible within the services package.
 */
class SecretsProvider {

    private static volatile Map<String, String> cache;

    private SecretsProvider() {}

    /**
     * Returns the value for the given key from secrets.json.
     * Returns empty string if the key is not found.
     */
    static String getString(String key) {
        if (cache == null) {
            synchronized (SecretsProvider.class) {
                if (cache == null) {
                    cache = loadSecrets();
                }
            }
        }
        return cache.getOrDefault(key, "");
    }

    private static Map<String, String> loadSecrets() {
        Map<String, String> map = new HashMap<>();
        try {
            JsonNode rootNode = new ObjectMapper().readTree(new File("secrets.json"));
            Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                map.put(entry.getKey(), entry.getValue().asText(""));
            }
        } catch (Exception e) {
            LoggingService.error("secrets_provider_load_failed", e);
        }
        return map;
    }
}
