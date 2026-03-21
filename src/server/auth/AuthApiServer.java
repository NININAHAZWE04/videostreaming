package server.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import common.AppConfig;
import common.AppLogger;
import db.*;
import server.api.AdminApiServer;
import server.api.JsonBuilder;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * API d'authentification et d'abonnement côté client.
 *
 *  POST /api/auth/register          — créer un compte
 *  POST /api/auth/login             — connexion → JWT
 *  GET  /api/auth/me                — infos utilisateur connecté
 *  POST /api/auth/logout            — révocation côté client (stateless)
 *  POST /api/auth/trial             — démarrer le trial 14j
 *  POST /api/auth/payment/request   — soumettre demande paiement cash
 *  GET  /api/auth/payment/status    — statut de mes paiements
 *  POST /api/auth/download-token    — obtenir un token de téléchargement
 *
 * Ces endpoints sont montés sur le même serveur que AdminApiServer (port 8081).
 * Cette classe est appelée depuis AdminApiServer.main() pour enregistrer les contextes.
 */
public final class AuthApiServer {

    private static final String LOG = "AuthApiServer";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AuthApiServer() {}

    /** Enregistre tous les contextes auth sur un serveur existant. */
    public static void registerContexts(HttpServer server) {
        server.createContext("/api/auth/register",        new RegisterHandler());
        server.createContext("/api/auth/login",           new LoginHandler());
        server.createContext("/api/auth/me",              new MeHandler());
        server.createContext("/api/auth/trial",           new TrialHandler());
        server.createContext("/api/auth/payment/request", new PaymentRequestHandler());
        server.createContext("/api/auth/payment/status",  new PaymentStatusHandler());
        server.createContext("/api/auth/download-token",  new DownloadTokenHandler());
        server.createContext("/api/auth/plans",           new PlansHandler());
        AppLogger.info(LOG, "Contextes auth enregistrés");
    }

    // ═══ POST /api/auth/register ════════════════════════════════

    static final class RegisterHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isMethod(ex,"POST")) { sendJson(ex,405,err("Method Not Allowed")); return; }

            Map<String,String> body = parseBody(ex);
            String email    = body.get("email");
            String username = body.get("username");
            String password = body.get("password");

            if (email==null||email.isBlank()||username==null||username.isBlank()||password==null) {
                sendJson(ex, 400, err("email, username et password sont requis")); return;
            }
            if (!email.contains("@")) { sendJson(ex, 400, err("Email invalide")); return; }
            String pwErr = PasswordUtil.validateStrength(password);
            if (pwErr != null) { sendJson(ex, 400, err(pwErr)); return; }

            UserRepository users = new UserRepository();
            if (users.emailExists(email)) { sendJson(ex, 409, err("Cet email est déjà utilisé")); return; }

            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hash(password, salt);

            try {
                int userId = users.create(email.trim().toLowerCase(), username.trim(), hash, salt);
                User user = users.findById(userId).orElseThrow();

                // Propose trial automatiquement (démarrer seulement si accepté)
                String token = JwtUtil.generate(userId, user.getEmail(), user.getRole(), false);
                AppLogger.info(LOG, "Nouveau compte: " + email);

                sendJson(ex, 201, JsonBuilder.obj()
                    .put("token", token)
                    .putRaw("user", userJson(user))
                    .put("canStartTrial", true)
                    .put("message", "Compte créé ! Vous bénéficiez d'un essai gratuit de 14 jours.")
                    .build());
            } catch (Exception e) {
                sendJson(ex, 500, err("Erreur création compte: " + e.getMessage()));
            }
        }
    }

    // ═══ POST /api/auth/login ════════════════════════════════════

    static final class LoginHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isMethod(ex,"POST")) { sendJson(ex,405,err("Method Not Allowed")); return; }

            Map<String,String> body = parseBody(ex);
            String email    = body.get("email");
            String password = body.get("password");

            if (email==null||password==null) { sendJson(ex,400,err("email et password requis")); return; }

            UserRepository users = new UserRepository();
            Optional<User> opt = users.findByEmail(email);
            if (opt.isEmpty()) { sendJson(ex,401,err("Email ou mot de passe incorrect")); return; }

            User user = opt.get();
            if (!user.isActive()) { sendJson(ex,403,err("Compte suspendu. Contactez l'administrateur.")); return; }
            if (!PasswordUtil.verify(password, user.getPasswordHash(), user.getPasswordSalt())) {
                sendJson(ex,401,err("Email ou mot de passe incorrect")); return;
            }

            // Expire overdue subscriptions on login
            new SubscriptionRepository().expireOverdue();
            // Reload user with fresh sub status
            user = users.findById(user.getId()).orElse(user);
            users.updateLastLogin(user.getId());

            String token = JwtUtil.generate(user.getId(), user.getEmail(), user.getRole(), user.hasActiveSubscription());
            AppLogger.info(LOG, "Login: " + email);

            sendJson(ex, 200, JsonBuilder.obj()
                .put("token", token)
                .putRaw("user", userJson(user))
                .build());
        }
    }

    // ═══ GET /api/auth/me ════════════════════════════════════════

    static final class MeHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            JwtUtil.Claims claims = requireAuth(ex); if (claims == null) return;

            new SubscriptionRepository().expireOverdue();
            UserRepository users = new UserRepository();
            Optional<User> opt = users.findById(claims.userId());
            if (opt.isEmpty()) { sendJson(ex,404,err("Utilisateur introuvable")); return; }

            User user = opt.get();
            sendJson(ex, 200, userJson(user));
        }
    }

    // ═══ POST /api/auth/trial ════════════════════════════════════

    static final class TrialHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            JwtUtil.Claims claims = requireAuth(ex); if (claims == null) return;

            UserRepository users = new UserRepository();
            User user = users.findById(claims.userId()).orElse(null);
            if (user == null) { sendJson(ex,404,err("Utilisateur introuvable")); return; }
            if (!user.canStartTrial()) { sendJson(ex,409,err("Essai gratuit déjà utilisé")); return; }

            try {
                new SubscriptionRepository().startTrial(claims.userId());
                user = users.findById(claims.userId()).orElse(user);
                String newToken = JwtUtil.generate(user.getId(), user.getEmail(), user.getRole(), true);
                AppLogger.info(LOG, "Trial démarré: " + user.getEmail());
                sendJson(ex, 200, JsonBuilder.obj()
                    .put("token", newToken)
                    .putRaw("user", userJson(user))
                    .put("message", "Essai de 14 jours activé ! Profitez de tous les contenus.")
                    .build());
            } catch (Exception e) {
                sendJson(ex,409,err(e.getMessage()));
            }
        }
    }

    // ═══ POST /api/auth/payment/request ══════════════════════════

    static final class PaymentRequestHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            JwtUtil.Claims claims = requireAuth(ex); if (claims == null) return;

            Map<String,String> body = parseBody(ex);
            String plan      = body.get("plan");
            String proof     = body.get("proofNote");
            if (plan == null || plan.isBlank()) { sendJson(ex,400,err("plan requis")); return; }

            PlanRepository planRepo = new PlanRepository();
            Optional<PlanRepository.PlanRow> planRow = planRepo.findByPlan(plan.trim().toLowerCase(Locale.ROOT));
            if (planRow.isEmpty() || !planRow.get().active()) {
                sendJson(ex, 400, err("Plan invalide ou inactif"));
                return;
            }

            double amount = planRow.get().price();
            int days = planRow.get().durationDays();
            String currency = planRow.get().currency();

            PaymentRepository payments = new PaymentRepository();
            int id = payments.submitCashRequest(claims.userId(), amount, currency, plan, days, proof);
            AppLogger.info(LOG, "Demande paiement cash #" + id + " — user " + claims.email() + " plan=" + plan);

            sendJson(ex, 201, JsonBuilder.obj()
                .put("paymentId", id)
                .put("status", "pending")
                .put("message", "Demande envoyée. L'admin validera votre paiement sous 24h.")
                .build());
        }
    }

    // ═══ GET /api/auth/plans ═══════════════════════════════════

    static final class PlansHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isMethod(ex, "GET")) { sendJson(ex,405,err("Method Not Allowed")); return; }

            List<PlanRepository.PlanRow> plans = new PlanRepository().findAll();
            StringBuilder sb = new StringBuilder("[");
            int written = 0;
            for (PlanRepository.PlanRow p : plans) {
                if (!p.active()) continue;
                if (written++ > 0) sb.append(',');
                sb.append(JsonBuilder.obj()
                    .put("id", p.plan())
                    .put("price", p.price())
                    .put("durationDays", p.durationDays())
                    .put("currency", p.currency())
                    .build());
            }
            sb.append(']');
            sendJson(ex, 200, sb.toString());
        }
    }

    // ═══ GET /api/auth/payment/status ════════════════════════════

    static final class PaymentStatusHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            JwtUtil.Claims claims = requireAuth(ex); if (claims == null) return;

            List<PaymentRepository.PaymentRow> list = new PaymentRepository().findByUserId(claims.userId());
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                PaymentRepository.PaymentRow p = list.get(i);
                sb.append(JsonBuilder.obj()
                    .put("id", p.id())
                    .put("plan", p.plan())
                    .put("amount", p.amount())
                    .put("currency", p.currency())
                    .put("status", p.status())
                    .put("durationDays", p.durationDays())
                    .put("createdAt", p.createdAt() != null ? p.createdAt().format(FMT) : "")
                    .put("adminNote", p.adminNote() != null ? p.adminNote() : "")
                    .build());
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ═══ POST /api/auth/download-token ═══════════════════════════

    static final class DownloadTokenHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isMethod(ex,"POST")) { sendJson(ex,405,err("Method Not Allowed")); return; }
            JwtUtil.Claims claims = requireAuth(ex); if (claims == null) return;

            // Verify subscription is active
            UserRepository users = new UserRepository();
            User user = users.findById(claims.userId()).orElse(null);
            if (user == null || !user.hasActiveSubscription()) {
                sendJson(ex, 403, err("Abonnement actif requis pour télécharger")); return;
            }

            Map<String,String> body = parseBody(ex);
            String vidIdStr = body.get("videoId");
            if (vidIdStr == null) { sendJson(ex,400,err("videoId requis")); return; }

            int videoId;
            try { videoId = Integer.parseInt(vidIdStr); }
            catch (NumberFormatException e) { sendJson(ex,400,err("videoId invalide")); return; }

            // Verify video exists
            Optional<VideoMetadata> vOpt = new VideoRepository().findById(videoId);
            if (vOpt.isEmpty()) { sendJson(ex,404,err("Vidéo introuvable")); return; }
            VideoMetadata video = vOpt.get();
            if (video.getFilePath() == null || video.getFilePath().isBlank()) {
                sendJson(ex,404,err("Fichier vidéo non disponible")); return;
            }

            // Generate token
            String token = generateDownloadToken();
            LocalDateTime expires = LocalDateTime.now().plusHours(2);

            String sql = "INSERT INTO download_tokens (token, user_id, video_id, expires_at) VALUES (?,?,?,?)";
            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, token);
                ps.setInt(2, claims.userId());
                ps.setInt(3, videoId);
                ps.setTimestamp(4, Timestamp.valueOf(expires));
                ps.executeUpdate();
            } catch (SQLException e) {
                sendJson(ex, 500, err("Erreur génération token")); return;
            }

            String host = ex.getRequestHeaders().getFirst("Host");
            if (host == null || host.isBlank()) {
                host = "localhost:" + AppConfig.get().getAdminApiPort();
            }
            String downloadUrl = "http://" + host + "/api/download?token=" + token;
            AppLogger.info(LOG, "Token download généré: user=" + claims.email() + " video=" + video.getTitle());

            sendJson(ex, 200, JsonBuilder.obj()
                .put("token", token)
                .put("downloadUrl", downloadUrl)
                .put("filename", safeFilename(video.getTitle(), video.getFilePath()))
                .put("expiresIn", "2 heures")
                .build());
        }
    }

    // ─── Static helpers ─────────────────────────────────────────

    public static String userJson(User u) {
        String subLabel = null;
        int daysLeft = 0;
        if (u.hasActiveSubscription()) {
            subLabel = u.getSubPlan();
            daysLeft = u.daysRemaining();
        }
        return JsonBuilder.obj()
            .put("id",             u.getId())
            .put("email",          u.getEmail())
            .put("username",       u.getUsername())
            .put("role",           u.getRole())
            .put("active",         u.isActive())
            .put("avatarColor",    u.getAvatarColor())
            .put("initials",       u.initials())
            .put("hasSubscription",u.hasActiveSubscription())
            .putNullable("subPlan",subLabel)
            .put("daysRemaining",  daysLeft)
            .put("trialUsed",      u.isTrialUsed())
            .put("canStartTrial",  u.canStartTrial())
            .put("createdAt",      u.getCreatedAt() != null ? u.getCreatedAt().format(FMT) : "")
            .build();
    }

    static JwtUtil.Claims requireAuth(HttpExchange ex) throws IOException {
        String auth  = ex.getRequestHeaders().getFirst("Authorization");
        String token = JwtUtil.extractBearer(auth);
        JwtUtil.Claims claims = JwtUtil.verify(token);
        if (claims == null) {
            sendJson(ex, 401, err("Authentification requise"));
            return null;
        }
        return claims;
    }

    static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        addCors(ex);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static boolean handleOptions(HttpExchange ex) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) return false;
        addCors(ex); ex.sendResponseHeaders(204,-1); ex.close(); return true;
    }

    static boolean isMethod(HttpExchange ex, String method) {
        return method.equalsIgnoreCase(ex.getRequestMethod());
    }

    static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,PUT,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type,Authorization");
    }

    static String err(String msg) {
        return "{\"error\":\"" + JsonBuilder.esc(msg) + "\"}";
    }

    static Map<String,String> parseBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        return parseJsonRaw(new String(bytes, StandardCharsets.UTF_8));
    }

    static Map<String,String> parseJsonRaw(String json) {
        Map<String,String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank() || !json.trim().startsWith("{")) return map;
        json = json.trim().substring(1, json.trim().length() - 1).trim();
        int i = 0;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length() || json.charAt(i) != '"') break;
            i++;
            int ks = i;
            while (i < json.length() && json.charAt(i) != '"') { if (json.charAt(i)=='\\') i++; i++; }
            String key = unesc(json.substring(ks, i)); i++;
            while (i < json.length() && json.charAt(i) != ':') i++; i++;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            String value = null;
            if (i < json.length() && json.charAt(i) == '"') {
                i++; int vs = i;
                while (i < json.length() && json.charAt(i) != '"') { if (json.charAt(i)=='\\') i++; i++; }
                value = unesc(json.substring(vs, i)); i++;
            } else if (i < json.length()) {
                int vs = i;
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                value = json.substring(vs, i).trim();
                if ("null".equals(value)) value = null;
            }
            map.put(key, value);
            while (i < json.length() && (json.charAt(i)==',' || Character.isWhitespace(json.charAt(i)))) i++;
        }
        return map;
    }

    private static String unesc(String s) {
        return s.replace("\\\"","\"").replace("\\n","\n").replace("\\r","\r")
                .replace("\\t","\t").replace("\\\\","\\");
    }

    private static String generateDownloadToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safeFilename(String title, String filePath) {
        String ext = ".mp4";
        if (filePath != null) {
            int dot = filePath.lastIndexOf('.');
            if (dot >= 0) ext = filePath.substring(dot);
        }
        return title.replaceAll("[^a-zA-Z0-9 _-]", "_") + ext;
    }
}
