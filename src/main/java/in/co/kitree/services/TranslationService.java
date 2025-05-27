package in.co.kitree.services;

import java.util.Locale;
import java.util.ResourceBundle;

public class TranslationService {
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String DEFAULT_COUNTRY = "US";

    public static String getTranslation(String key, String language) {
        // Validate inputs
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Translation key cannot be null or empty");
        }
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language cannot be null or empty");
        }

        // Normalize language code
        language = language.toLowerCase();
        String country = DEFAULT_COUNTRY;

        // Set specific country codes for supported languages
        if (language.equals("hi")) {
            country = "IN";
        }

        try {
            // Get the appropriate locale
            Locale locale = new Locale(language, country);
            
            // Load the resource bundle
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
            
            // Try to get the translation
            try {
                return bundle.getString(key);
            } catch (Exception e) {
                // If translation not found, return the key
                return key;
            }
        } catch (Exception e) {
            // If bundle not found, try default language
            try {
                Locale defaultLocale = new Locale(DEFAULT_LANGUAGE, DEFAULT_COUNTRY);
                ResourceBundle defaultBundle = ResourceBundle.getBundle("messages", defaultLocale);
                return defaultBundle.getString(key);
            } catch (Exception ex) {
                // If still not found, return the key
                return key;
            }
        }
    }

    public static String translate(String text, String language) {
        // This method is kept for backward compatibility
        return getTranslation(text, language);
    }
}
