package in.co.kitree.services;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class TranslationServiceTest {

    @Test
    public void testTranslation() {

        String result = TranslationService.translate("coupon.not_enabled", "en");
        assertEquals("This coupon does not exist.", result);

        result = TranslationService.translate("coupon.not_enabled", "hi");
        assertEquals("यह कूपन अभी सक्रिय नहीं है।", result);
    }
}
