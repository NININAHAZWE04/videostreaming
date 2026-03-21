package server.auth;

import common.AppConfig;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * JWT maison sans dépendance externe.
 * Algorithme : HS256 (HMAC-SHA256).
 *
 * Format : base64url(header).base64url(payload).base64url(signature)
 * Payload : {"sub":"<userId>","email":"<email>","role":"<role>","sub_active":<bool>,"exp":<unix_ts>}
 */
public final class JwtUtil {

    private static final String HEADER  = b64enc("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    private static final long   TTL_SEC = 86_400 * 7; // 7 jours

    private JwtUtil() {}

    /** Génère un token JWT signé. */
    public static String generate(int userId, String email, String role, boolean subActive) {
        long exp = System.currentTimeMillis() / 1000 + TTL_SEC;
        String payload = "{\"sub\":" + userId
            + ",\"email\":\"" + esc(email) + "\""
            + ",\"role\":\"" + esc(role) + "\""
            + ",\"sub_active\":" + subActive
            + ",\"exp\":" + exp + "}";
        String headerPayload = HEADER + "." + b64enc(payload);
        String signature = sign(headerPayload);
        return headerPayload + "." + signature;
    }

    /**
     * Valide et parse un token.
     * @return Claims ou null si invalide/expiré
     */
    public static Claims verify(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        // Re-compute signature
        String expected = sign(parts[0] + "." + parts[1]);
        if (!expected.equals(parts[2])) return null; // tampered

        // Decode payload
        String payloadJson;
        try {
            payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }

        // Parse manually (no JSON lib)
        Integer sub   = parseInt(payloadJson, "sub");
        String  email = parseStr(payloadJson, "email");
        String  role  = parseStr(payloadJson, "role");
        Long    exp   = parseLong(payloadJson, "exp");
        boolean subA  = parseBool(payloadJson, "sub_active");

        if (sub == null || exp == null) return null;
        if (System.currentTimeMillis() / 1000 > exp) return null; // expired

        return new Claims(sub, email, role, subA);
    }

    /** Extrait un Bearer token depuis le header Authorization. */
    public static String extractBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7).trim();
    }

    /** Payload décodé du JWT. */
    public record Claims(int userId, String email, String role, boolean subActive) {
        public boolean isAdmin() { return "admin".equals(role); }
        public boolean isUser()  { return "user".equals(role) || isAdmin(); }
    }

    // ─── Private helpers ────────────────────────────────────

    private static String sign(String data) {
        try {
            String secret = AppConfig.get().getString("jwt.secret");
            if (secret == null || secret.isBlank()) secret = AppConfig.get().getAdminSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new RuntimeException("JWT sign error", e);
        }
    }

    private static String b64enc(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"").replace("\\", "\\\\");
    }

    private static Integer parseInt(String json, String key) {
        try {
            int i = json.indexOf("\"" + key + "\":");
            if (i < 0) return null;
            int start = i + key.length() + 3;
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (Exception e) { return null; }
    }

    private static Long parseLong(String json, String key) {
        try {
            int i = json.indexOf("\"" + key + "\":");
            if (i < 0) return null;
            int start = i + key.length() + 3;
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            return Long.parseLong(json.substring(start, end).trim());
        } catch (Exception e) { return null; }
    }

    private static String parseStr(String json, String key) {
        try {
            int i = json.indexOf("\"" + key + "\":\"");
            if (i < 0) return null;
            int start = i + key.length() + 4;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) { return null; }
    }

    private static boolean parseBool(String json, String key) {
        int i = json.indexOf("\"" + key + "\":true");
        return i >= 0;
    }
}
