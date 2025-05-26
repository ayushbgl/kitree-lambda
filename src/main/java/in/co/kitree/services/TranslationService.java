package in.co.kitree.services;

import java.util.Locale;
import java.util.ResourceBundle;

public class TranslationService {

    public static String translate(String text, String language) {
        Locale locale = new Locale(language);
        if (language.equals("en")) {
            locale = new Locale("en", "US");
        } else if (language.equals("hi")) {
            locale = new Locale("hi", "IN");
        } else {
            locale = new Locale("en", "US");
        }
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        return bundle.getString(text);
    }
}
