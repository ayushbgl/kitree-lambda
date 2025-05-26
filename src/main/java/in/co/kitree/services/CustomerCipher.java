package in.co.kitree.services;

public class CustomerCipher {
    private static final Integer shift = 7;

    public static String encryptCaesarCipher(String plaintext) {
        StringBuilder ciphertext = new StringBuilder();
        char ExtractedCharacter;
        for (int i = 0; i < plaintext.length(); i++) {
            ExtractedCharacter = plaintext.charAt(i);

            if (ExtractedCharacter >= 'a' && ExtractedCharacter <= 'z') {
                ExtractedCharacter = (char) (ExtractedCharacter + shift);
                if (ExtractedCharacter > 'z') {
                    ExtractedCharacter = (char) (ExtractedCharacter + 'a' - 'z' - 1);
                }
                ciphertext.append(ExtractedCharacter);

            } else if (ExtractedCharacter >= 'A' && ExtractedCharacter <= 'Z') {
                ExtractedCharacter = (char) (ExtractedCharacter + shift);
                if (ExtractedCharacter > 'Z') {
                    ExtractedCharacter = (char) (ExtractedCharacter + 'A' - 'Z' - 1);
                }
                ciphertext.append(ExtractedCharacter);

            } else {
                ciphertext.append(ExtractedCharacter);
            }
        }
        return ciphertext.toString();
    }

    public static String decryptCaesarCipher(String ciphertext) {
        StringBuilder Decryption = new StringBuilder();
        for (int i = 0; i < ciphertext.length(); i++) {
            char ExtractedCharacter = ciphertext.charAt(i);

            if (ExtractedCharacter >= 'a' && ExtractedCharacter <= 'z') {
                ExtractedCharacter = (char) (ExtractedCharacter - shift);
                if (ExtractedCharacter < 'a') {
                    ExtractedCharacter = (char) (ExtractedCharacter - 'a' + 'z' + 1);
                }
                Decryption.append(ExtractedCharacter);
            } else if (ExtractedCharacter >= 'A' && ExtractedCharacter <= 'Z') {
                ExtractedCharacter = (char) (ExtractedCharacter - shift);
                if (ExtractedCharacter < 'A') {
                    ExtractedCharacter = (char) (ExtractedCharacter - 'A' + 'Z' + 1);
                }
                Decryption.append(ExtractedCharacter);
            } else {
                Decryption.append(ExtractedCharacter);
            }
        }
        return Decryption.toString();
    }
}
