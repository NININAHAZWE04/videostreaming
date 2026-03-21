package server.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hachage de mots de passe avec PBKDF2-HmacSHA256.
 * Java natif, zéro dépendance.
 */
public final class PasswordUtil {

    private static final int ITERATIONS  = 310_000;
    private static final int KEY_LENGTH  = 256; // bits
    private static final int SALT_BYTES  = 32;

    private PasswordUtil() {}

    /** Génère un salt aléatoire (base64). */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Hache le mot de passe avec le salt fourni. */
    public static String hash(String password, String saltBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, ITERATIONS, KEY_LENGTH
            );
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash error", e);
        }
    }

    /** Vérifie qu'un mot de passe correspond au hash stocké. */
    public static boolean verify(String password, String storedHash, String storedSalt) {
        String computed = hash(password, storedSalt);
        // Constant-time comparison
        if (computed.length() != storedHash.length()) return false;
        int diff = 0;
        for (int i = 0; i < computed.length(); i++) {
            diff |= computed.charAt(i) ^ storedHash.charAt(i);
        }
        return diff == 0;
    }

    /** Valide la force du mot de passe. Retourne null si OK, message d'erreur sinon. */
    public static String validateStrength(String password) {
        if (password == null || password.length() < 8)
            return "Le mot de passe doit contenir au moins 8 caractères";
        return null;
    }
}
