package in.co.kitree.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TranslationServiceUnitTest {

    @BeforeEach
    void setUp() {
        // Any setup if needed
    }

    @Test
    void testGetTranslation_ValidKey() {
        // Arrange
        String key = "welcome_message";
        String language = "hi"; // Hindi

        // Act
        String translation = TranslationService.getTranslation(key, language);

        // Assert
        assertNotNull(translation);
        assertFalse(translation.isEmpty());
        assertEquals(key, translation); // Fallback is the key itself
    }

    @Test
    void testGetTranslation_InvalidKey() {
        // Arrange
        String key = "non_existent_key";
        String language = "en";

        // Act
        String translation = TranslationService.getTranslation(key, language);

        // Assert
        assertEquals(key, translation); // Should return the key if translation doesn't exist
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "hi", "mr", "gu", "bn", "ta", "te", "ml", "kn"})
    void testGetTranslation_AllSupportedLanguages(String language) {
        // Arrange
        String key = "welcome_message";

        // Act
        String translation = TranslationService.getTranslation(key, language);

        // Assert
        assertNotNull(translation);
        assertFalse(translation.isEmpty());
    }

    @Test
    void testGetTranslation_UnsupportedLanguage() {
        // Arrange
        String key = "welcome_message";
        String language = "fr"; // French (unsupported)

        // Act
        String translation = TranslationService.getTranslation(key, language);

        // Assert
        assertEquals(key, translation); // Should return the key for unsupported languages
    }

    @ParameterizedTest
    @CsvSource({
        "welcome_message, en, welcome_message",
        "welcome_message, hi, welcome_message",
        "error_invalid_coupon, en, error_invalid_coupon",
        "error_invalid_coupon, hi, error_invalid_coupon"
    })
    void testGetTranslation_SpecificTranslations(String key, String language, String expectedTranslation) {
        // Act
        String translation = TranslationService.getTranslation(key, language);

        // Assert
        assertEquals(expectedTranslation, translation);
    }

    @Test
    void testGetTranslation_NullKey() {
        // Arrange
        String language = "en";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            TranslationService.getTranslation(null, language);
        });
    }

    @Test
    void testGetTranslation_NullLanguage() {
        // Arrange
        String key = "welcome_message";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            TranslationService.getTranslation(key, null);
        });
    }

    @Test
    void testGetTranslation_EmptyKey() {
        // Arrange
        String key = "";
        String language = "en";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            TranslationService.getTranslation(key, language);
        });
    }

    @Test
    void testGetTranslation_EmptyLanguage() {
        // Arrange
        String key = "welcome_message";
        String language = "";

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            TranslationService.getTranslation(key, language);
        });
    }
}
