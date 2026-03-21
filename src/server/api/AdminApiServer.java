package server.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import common.AppConfig;
import common.AppLogger;
import db.*;
import server.FfprobeExtractor;
import server.auth.AuthApiServer;
import server.sse.SseEventBus;
import server.sse.SseEventBus.SseClient;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * API REST Admin complète.
 *
 * Endpoints publics :
 *   GET  /api/videos                — liste toutes les vidéos actives (enrichie)
 *   GET  /api/videos/search?q=...   — recherche full-text
 *   GET  /api/categories            — liste des catégories
 *   GET  /api/health                — santé de l'application
 *   GET  /api/events                — SSE stream temps réel
 *   GET  /api/logs                  — SSE stream des logs
 *
 * Endpoints admin (Authorization: Bearer <secret>) :
 *   GET    /api/admin/videos         — toutes les vidéos (actives + inactives)
 *   POST   /api/admin/videos         — créer/mettre à jour une vidéo
 *   PUT    /api/admin/videos/{id}    — modifier les métadonnées
 *   DELETE /api/admin/videos/{id}    — supprimer
 *   GET    /api/admin/categories     — liste + compteurs
 *   POST   /api/admin/categories     — créer une catégorie
 *   PUT    /api/admin/categories/{id}
 *   DELETE /api/admin/categories/{id}
 *   GET    /api/admin/stats          — dashboard stats
 *   GET    /api/admin/stats/hourly   — vues dernières 24h
 */
public final class AdminApiServer {

    private static final String LOG = "AdminApiServer";
    private static volatile int activeStreamCount = 0;

    private AdminApiServer() {}

    public static void setActiveStreamCount(int n) { activeStreamCount = n; }

    public static void main(String[] args) throws Exception {
        AppConfig cfg = AppConfig.get();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : cfg.getAdminApiPort();

        // Init DB
        DatabaseManager.getInstance();

        // Wire AppLogger → SSE log stream
        AppLogger.setListener(entry -> SseEventBus.get().publishLogEntry(entry.toJson()));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // ── Public endpoints ───────────────────────────────────────────────
        server.createContext("/api/videos",     new VideosHandler());
        server.createContext("/api/categories", new CategoriesHandler());
        server.createContext("/api/health",     new HealthHandler());
        server.createContext("/api/media",      new MediaHandler());
        server.createContext("/api/events",     new SseEventsHandler());
        server.createContext("/api/logs",       new SseLogsHandler());

        // ── Auth & client endpoints ────────────────────────────────────────
        AuthApiServer.registerContexts(server);
        server.createContext("/api/download", new server.auth.DownloadHandler());

        // ── Admin endpoints ────────────────────────────────────────────────
        server.createContext("/api/admin/videos",        new AdminVideosHandler());
        server.createContext("/api/admin/categories",    new AdminCategoriesHandler());
        server.createContext("/api/admin/stats",         new AdminStatsHandler());
        server.createContext("/api/admin/users",         new AdminUsersHandler());
        server.createContext("/api/admin/subscriptions", new AdminSubscriptionsHandler());
        server.createContext("/api/admin/payments",      new AdminPaymentsHandler());

        server.setExecutor(Executors.newCachedThreadPool());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            AppLogger.info(LOG, "Arrêt AdminApiServer...");
            server.stop(1);
        }, "admin-api-shutdown"));

        AppLogger.info(LOG, "Admin API démarrée sur http://localhost:" + port);
        server.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC — /api/videos
    // ═══════════════════════════════════════════════════════════════════════

    static final class VideosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isGet(ex)) { sendJson(ex, 405, err("Method Not Allowed")); return; }

            String query = queryParam(ex.getRequestURI(), "q");
            String catStr = queryParam(ex.getRequestURI(), "category");

            VideoRepository repo = new VideoRepository();
            List<VideoMetadata> videos;

            if (query != null && !query.isBlank()) {
                videos = repo.search(query);
            } else if (catStr != null) {
                try { videos = repo.findByCategory(Integer.parseInt(catStr)); }
                catch (NumberFormatException e) { videos = repo.findActive(); }
            } else {
                videos = repo.findActive();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"count\":").append(videos.size()).append(",\"videos\":[");
            for (int i = 0; i < videos.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(JsonBuilder.videoToJson(videos.get(i)));
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC — /api/categories
    // ═══════════════════════════════════════════════════════════════════════

    static final class CategoriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isGet(ex)) { sendJson(ex, 405, err("Method Not Allowed")); return; }
            CategoryRepository repo = new CategoryRepository();
            List<CategoryRepository.Category> cats = repo.findAll();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < cats.size(); i++) {
                if (i > 0) sb.append(',');
                CategoryRepository.Category c = cats.get(i);
                sb.append(JsonBuilder.obj()
                    .put("id", c.id()).put("name", c.name())
                    .put("color", c.color()).put("icon", c.icon())
                    .put("videoCount", c.videoCount()).build());
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC — /api/health
    // ═══════════════════════════════════════════════════════════════════════

    static final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            VideoRepository repo = new VideoRepository();
            Runtime rt = Runtime.getRuntime();
            long usedMb  = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
            long totalMb = rt.totalMemory() / 1_048_576;
            String body = JsonBuilder.obj()
                .put("status", "ok")
                .put("timestamp", LocalDateTime.now().toString())
                .put("totalVideos", repo.countTotal())
                .put("activeStreams", activeStreamCount)
                .put("jvmUsedMb", usedMb)
                .put("jvmTotalMb", totalMb)
                .put("sseClients", SseEventBus.get().getClientCount())
                .build();
            sendJson(ex, 200, body);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SSE — /api/events
    // ═══════════════════════════════════════════════════════════════════════

    static final class SseEventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            addCors(ex);
            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.sendResponseHeaders(200, 0);

            SseClient client = new SseClient(ex.getResponseBody());
            SseEventBus.get().addClient(client);
            // Send initial connected event
            try {
                client.send("event: connected\ndata: {\"status\":\"ok\"}\n\n".getBytes(StandardCharsets.UTF_8));
                // Block until client disconnects
                while (!client.isClosed()) {
                    Thread.sleep(5000);
                }
            } catch (Exception ignored) {
            } finally {
                SseEventBus.get().removeClient(client);
                client.close();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SSE — /api/logs
    // ═══════════════════════════════════════════════════════════════════════

    static final class SseLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            addCors(ex);
            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, 0);

            SseClient client = new SseClient(ex.getResponseBody());
            // Send log history first
            try {
                for (AppLogger.LogEntry entry : AppLogger.getHistory()) {
                    client.send(("event: log_entry\ndata: " + entry.toJson() + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                }
                // Register for future logs
                AppLogger.setListener(e -> SseEventBus.get().publishLogEntry(e.toJson()));
                SseEventBus.get().addClient(client);
                while (!client.isClosed()) Thread.sleep(5000);
            } catch (Exception ignored) {
            } finally {
                SseEventBus.get().removeClient(client);
                client.close();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — /api/admin/videos
    // ═══════════════════════════════════════════════════════════════════════

    static final class AdminVideosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isAdmin(ex)) return;

            String method = ex.getRequestMethod().toUpperCase();
            String path = ex.getRequestURI().getPath();
            VideoRepository repo = new VideoRepository();

            if (method.equals("POST") && path.endsWith("/upload")) {
                handleUpload(ex, repo);
                return;
            }

            // DELETE /api/admin/videos/{id}
            if (method.equals("DELETE")) {
                int id = extractId(path);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Optional<VideoMetadata> existing = repo.findById(id);
                if (existing.isEmpty()) { sendJson(ex, 404, err("Vidéo introuvable")); return; }
                boolean ok = repo.deleteById(id);
                if (ok) {
                    SseEventBus.get().publishVideoRemoved(id, existing.get().getTitle());
                    sendJson(ex, 200, "{\"deleted\":true}");
                } else {
                    sendJson(ex, 500, err("Suppression échouée"));
                }
                return;
            }

            if (method.equals("POST") && path.endsWith("/stream")) {
                int id = extractIdFromPath(path, -2);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Optional<VideoMetadata> existing = repo.findById(id);
                if (existing.isEmpty()) { sendJson(ex, 404, err("Vidéo introuvable")); return; }
                VideoMetadata vm = existing.get();
                if (vm.getFilePath() == null || vm.getFilePath().isBlank()) {
                    sendJson(ex, 400, err("Aucun fichier associé à cette vidéo"));
                    return;
                }
                if (!Files.exists(Path.of(vm.getFilePath()))) {
                    sendJson(ex, 404, err("Fichier vidéo introuvable sur le serveur"));
                    return;
                }
                boolean ok = repo.setActive(id, true);
                Optional<VideoMetadata> updated = repo.findById(id);
                if (ok && updated.isPresent()) {
                    SseEventBus.get().publishStreamStarted(updated.get().getTitle(), "/api/media/" + id + "/stream");
                    sendJson(ex, 200, JsonBuilder.videoToJson(updated.get()));
                } else {
                    sendJson(ex, 500, err("Impossible d'activer le stream"));
                }
                return;
            }

            if (method.equals("POST") && path.endsWith("/stop")) {
                int id = extractIdFromPath(path, -2);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Optional<VideoMetadata> existing = repo.findById(id);
                if (existing.isEmpty()) { sendJson(ex, 404, err("Vidéo introuvable")); return; }
                boolean ok = repo.setActive(id, false);
                Optional<VideoMetadata> updated = repo.findById(id);
                if (ok && updated.isPresent()) {
                    SseEventBus.get().publishStreamStopped(updated.get().getTitle());
                    sendJson(ex, 200, JsonBuilder.videoToJson(updated.get()));
                } else {
                    sendJson(ex, 500, err("Impossible d'arrêter le stream"));
                }
                return;
            }

            // PUT /api/admin/videos/{id}
            if (method.equals("PUT")) {
                int id = extractId(path);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Map<String, String> body = parseJsonBody(ex);
                String synopsis = body.get("synopsis");
                String catStr   = body.get("categoryId");
                String tags     = body.get("tags");
                String isFreeStr= body.get("isFree");

                Integer catId = null;
                if (catStr != null && !catStr.isBlank()) {
                    try { catId = Integer.parseInt(catStr); } catch (NumberFormatException ignored) {}
                }
                repo.updateMetadata(id, synopsis, catId, tags);
                if (isFreeStr != null) persistVideoFlags(id, "true".equalsIgnoreCase(isFreeStr), null);

                Optional<VideoMetadata> updated = repo.findById(id);
                if (updated.isEmpty()) { sendJson(ex, 404, err("Non trouvé")); return; }
                SseEventBus.get().publishVideoAdded(id, updated.get().getTitle());
                sendJson(ex, 200, JsonBuilder.videoToJson(updated.get()));
                return;
            }

            // GET /api/admin/videos
            if (method.equals("GET")) {
                List<VideoMetadata> all = repo.findAll();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < all.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(JsonBuilder.videoToJson(all.get(i)));
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
                return;
            }

            // POST /api/admin/videos — create/upsert from metadata
            if (method.equals("POST")) {
                Map<String, String> body = parseJsonBody(ex);
                String titleVal = body.get("title");
                if (titleVal == null || titleVal.isBlank()) { sendJson(ex, 400, err("title requis")); return; }

                VideoMetadata vm = new VideoMetadata();
                vm.setTitle(titleVal.trim());
                vm.setFilePath(body.get("filePath"));
                vm.setSynopsis(body.get("synopsis"));
                vm.setTags(body.get("tags"));
                vm.setActive(false);
                String catStr = body.get("categoryId");
                String isFreeStr = body.get("isFree");
                if (catStr != null && !catStr.isBlank()) {
                    try { vm.setCategoryId(Integer.parseInt(catStr)); } catch (NumberFormatException ignored) {}
                }
                int id = repo.upsert(vm);
                if (isFreeStr != null) persistVideoFlags(id, "true".equalsIgnoreCase(isFreeStr), false);
                Optional<VideoMetadata> created = repo.findById(id);
                if (created.isPresent()) {
                    SseEventBus.get().publishVideoAdded(id, titleVal);
                    sendJson(ex, 201, JsonBuilder.videoToJson(created.get()));
                } else {
                    sendJson(ex, 500, err("Création échouée"));
                }
                return;
            }

            sendJson(ex, 405, err("Method Not Allowed"));
        }

        private void handleUpload(HttpExchange ex, VideoRepository repo) throws IOException {
            String uploadedName = ex.getRequestHeaders().getFirst("X-Upload-Filename");
            if (uploadedName == null || uploadedName.isBlank()) {
                sendJson(ex, 400, err("Nom de fichier manquant"));
                return;
            }

            Map<String, String> meta = decodeMeta(ex.getRequestHeaders().getFirst("X-Video-Meta"));
            String title = meta.get("title");
            if (title == null || title.isBlank()) title = stripExtension(uploadedName);

            Path videosDir = Path.of(AppConfig.get().getVideosDirectory()).toAbsolutePath().normalize();
            try {
                Files.createDirectories(videosDir);
            } catch (IOException e) {
                sendJson(ex, 500, err("Impossible de preparer le dossier videos"));
                return;
            }

            Path target = uniqueTarget(videosDir, sanitizeFilename(uploadedName));
            long copied;
            try (InputStream in = ex.getRequestBody()) {
                copied = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                sendJson(ex, 500, err("Echec de l'upload video"));
                return;
            }

            if (copied <= 0) {
                Files.deleteIfExists(target);
                sendJson(ex, 400, err("Fichier video vide"));
                return;
            }

            VideoMetadata vm = new VideoMetadata();
            vm.setTitle(title.trim());
            vm.setFilePath(target.toString());
            vm.setSynopsis(meta.get("synopsis"));
            vm.setTags(meta.get("tags"));
            vm.setActive(false);

            String catStr = meta.get("categoryId");
            if (catStr != null && !catStr.isBlank()) {
                try { vm.setCategoryId(Integer.parseInt(catStr)); } catch (NumberFormatException ignored) {}
            }

            FfprobeExtractor.extract(target.toFile(), vm);

            try {
                int id = repo.upsert(vm);
                String isFreeStr = meta.get("isFree");
                if (isFreeStr != null) persistVideoFlags(id, "true".equalsIgnoreCase(isFreeStr), false);
                Optional<VideoMetadata> created = repo.findById(id);
                if (created.isPresent()) {
                    SseEventBus.get().publishVideoAdded(id, created.get().getTitle());
                    sendJson(ex, 201, JsonBuilder.videoToJson(created.get()));
                } else {
                    sendJson(ex, 500, err("Upload enregistre mais video introuvable"));
                }
            } catch (RuntimeException e) {
                try { Files.deleteIfExists(target); } catch (IOException ignored) {}
                sendJson(ex, 500, err("Erreur de persistance de la video uploadée"));
            }
        }
    }

    static final class MediaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isGet(ex)) { sendJson(ex, 405, err("Method Not Allowed")); return; }

            String path = ex.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 5) { sendJson(ex, 404, err("Media introuvable")); return; }

            int id;
            try { id = Integer.parseInt(parts[3]); }
            catch (NumberFormatException e) { sendJson(ex, 400, err("ID invalide")); return; }

            String action = parts[4];
            Optional<VideoMetadata> opt = new VideoRepository().findById(id);
            if (opt.isEmpty()) { sendJson(ex, 404, err("Vidéo introuvable")); return; }

            VideoMetadata vm = opt.get();
            if (vm.getFilePath() == null || vm.getFilePath().isBlank()) { sendJson(ex, 404, err("Fichier absent")); return; }
            Path file = Path.of(vm.getFilePath());
            if (!Files.exists(file) || !Files.isRegularFile(file)) { sendJson(ex, 404, err("Fichier absent")); return; }

            if ("stream".equals(action)) {
                serveVideoFile(ex, file, vm);
                return;
            }
            if ("thumbnail".equals(action)) {
                sendJson(ex, 404, err("Thumbnail indisponible"));
                return;
            }

            sendJson(ex, 404, err("Media introuvable"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — /api/admin/categories
    // ═══════════════════════════════════════════════════════════════════════

    static final class AdminCategoriesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isAdmin(ex)) return;

            String method = ex.getRequestMethod().toUpperCase();
            String path = ex.getRequestURI().getPath();
            CategoryRepository repo = new CategoryRepository();

            if (method.equals("GET")) {
                List<CategoryRepository.Category> cats = repo.findAll();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < cats.size(); i++) {
                    if (i > 0) sb.append(',');
                    CategoryRepository.Category c = cats.get(i);
                    sb.append(JsonBuilder.obj()
                        .put("id", c.id()).put("name", c.name())
                        .put("color", c.color()).put("icon", c.icon())
                        .put("videoCount", c.videoCount()).build());
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
                return;
            }

            if (method.equals("POST")) {
                Map<String, String> body = parseJsonBody(ex);
                String name = body.get("name");
                if (name == null || name.isBlank()) { sendJson(ex, 400, err("name requis")); return; }
                try {
                    int id = repo.create(name, body.get("color"), body.get("icon"));
                    sendJson(ex, 201, "{\"id\":" + id + "}");
                } catch (Exception e) {
                    sendJson(ex, 409, err(e.getMessage()));
                }
                return;
            }

            if (method.equals("PUT")) {
                int id = extractId(path);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Map<String, String> body = parseJsonBody(ex);
                String name = body.get("name");
                if (name == null || name.isBlank()) { sendJson(ex, 400, err("name requis")); return; }
                boolean ok = repo.update(id, name, body.get("color"), body.get("icon"));
                sendJson(ex, ok ? 200 : 404, ok ? "{\"updated\":true}" : err("Non trouvé"));
                return;
            }

            if (method.equals("DELETE")) {
                int id = extractId(path);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                boolean ok = repo.delete(id);
                sendJson(ex, ok ? 200 : 404, ok ? "{\"deleted\":true}" : err("Non trouvé"));
                return;
            }

            sendJson(ex, 405, err("Method Not Allowed"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — /api/admin/stats
    // ═══════════════════════════════════════════════════════════════════════

    static final class AdminStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isAdmin(ex)) return;
            if (!isGet(ex)) { sendJson(ex, 405, err("Method Not Allowed")); return; }

            String path = ex.getRequestURI().getPath();
            StatsRepository stats = new StatsRepository();

            if (path.endsWith("/hourly")) {
                List<StatsRepository.HourlyStats> hs = stats.viewsLast24h();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < hs.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append("{\"hour\":").append(hs.get(i).hour())
                      .append(",\"views\":").append(hs.get(i).viewCount()).append('}');
                }
                sb.append(']');
                sendJson(ex, 200, sb.toString());
                return;
            }

            // Dashboard stats
            StatsRepository.DashboardStats ds = stats.getDashboard(activeStreamCount);
            VideoRepository vr = new VideoRepository();

            StringBuilder topSb = new StringBuilder("[");
            List<VideoMetadata> top = ds.topVideos();
            for (int i = 0; i < top.size(); i++) {
                if (i > 0) topSb.append(',');
                VideoMetadata v = top.get(i);
                topSb.append(JsonBuilder.obj()
                    .put("id", v.getId())
                    .put("title", v.getTitle())
                    .put("viewCount", v.getViewCount())
                    .putNullable("qualityLabel", v.getQualityLabel())
                    .build());
            }
            topSb.append(']');

            String body = JsonBuilder.obj()
                .put("totalVideos",    ds.totalVideos())
                .put("activeStreams",  ds.activeStreams())
                .put("viewsToday",     ds.viewsToday())
                .put("totalViews",     ds.totalViews())
                .put("bandwidthMb",    ds.totalBandwidthMb())
                .put("sseClients",     SseEventBus.get().getClientCount())
                .putRaw("topVideos",   topSb.toString())
                .build();
            sendJson(ex, 200, body);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static boolean isAdmin(HttpExchange ex) throws IOException {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String secret = AppConfig.get().getAdminSecret();
        if (auth != null && auth.equals("Bearer " + secret)) return true;
        sendJson(ex, 401, err("Unauthorized — Bearer token requis"));
        return false;
    }

    private static boolean isGet(HttpExchange ex) {
        return "GET".equalsIgnoreCase(ex.getRequestMethod());
    }

    private static boolean handleOptions(HttpExchange ex) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) return false;
        addCors(ex);
        ex.sendResponseHeaders(204, -1);
        ex.close();
        return true;
    }

    static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        addCors(ex);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Upload-Filename, X-Video-Meta");
    }

    private static String err(String msg) {
        return "{\"error\":\"" + JsonBuilder.esc(msg) + "\"}";
    }

    private static int extractId(String path) {
        String[] parts = path.split("/");
        try { return Integer.parseInt(parts[parts.length - 1]); }
        catch (NumberFormatException e) { return -1; }
    }

    private static String queryParam(URI uri, String key) {
        String q = uri.getQuery();
        if (q == null) return null;
        for (String p : q.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return null;
    }

    /** Minimal JSON body parser — handles flat {"key":"value"} objects */
    public static Map<String, String> parseJsonBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readAllBytes();
        return parseJson(new String(bytes, StandardCharsets.UTF_8).trim());
    }

    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json.isEmpty() || !json.startsWith("{")) return map;
        // Remove braces
        json = json.substring(1, json.length() - 1).trim();
        // Simple tokenizer
        int i = 0;
        while (i < json.length()) {
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length() || json.charAt(i) != '"') break;
            // Read key
            i++;
            int ks = i;
            while (i < json.length() && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\') i++;
                i++;
            }
            String key = unescape(json.substring(ks, i));
            i++; // closing "
            // Skip :
            while (i < json.length() && json.charAt(i) != ':') i++;
            i++;
            // Skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            // Read value
            String value = null;
            if (i < json.length() && json.charAt(i) == '"') {
                i++;
                int vs = i;
                while (i < json.length() && json.charAt(i) != '"') {
                    if (json.charAt(i) == '\\') i++;
                    i++;
                }
                value = unescape(json.substring(vs, i));
                i++; // closing "
            } else if (i < json.length()) {
                int vs = i;
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                value = json.substring(vs, i).trim();
                if (value.equals("null")) value = null;
            }
            map.put(key, value);
            // Skip ,
            while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
        }
        return map;
    }

    private static Map<String, String> decodeMeta(String encoded) {
        if (encoded == null || encoded.isBlank()) return new LinkedHashMap<>();
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return parseJson(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return new LinkedHashMap<>();
        }
    }

    private static void persistVideoFlags(int id, Boolean isFree, Boolean active) {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE videos SET is_free = COALESCE(?, is_free), is_active = COALESCE(?, is_active) WHERE id = ?")) {
            if (isFree != null) ps.setBoolean(1, isFree); else ps.setNull(1, java.sql.Types.BOOLEAN);
            if (active != null) ps.setBoolean(2, active); else ps.setNull(2, java.sql.Types.BOOLEAN);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            AppLogger.warn(LOG, "persistVideoFlags error: " + e.getMessage());
        }
    }

    private static String sanitizeFilename(String name) {
        String cleaned = name == null ? "video.mp4" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "video.mp4" : cleaned;
    }

    private static String stripExtension(String name) {
        if (name == null || name.isBlank()) return "Nouvelle video";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Path uniqueTarget(Path dir, String fileName) {
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) return dir.resolve("video-upload.mp4");
        if (!Files.exists(target)) return target;

        String base = stripExtension(fileName);
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) ext = fileName.substring(dot);
        for (int i = 1; i < 10_000; i++) {
            Path candidate = dir.resolve(base + "-" + i + ext).normalize();
            if (candidate.startsWith(dir) && !Files.exists(candidate)) return candidate;
        }
        return dir.resolve(System.currentTimeMillis() + "-" + fileName).normalize();
    }

    private static void serveVideoFile(HttpExchange ex, Path file, VideoMetadata vm) throws IOException {
        addCors(ex);
        long fileLength = Files.size(file);
        String rangeHeader = ex.getRequestHeaders().getFirst("Range");
        long start = 0;
        long end = fileLength - 1;
        boolean partial = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring(6).split("-", 2);
            try {
                if (!parts[0].isBlank()) start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isBlank()) end = Math.min(Long.parseLong(parts[1]), fileLength - 1);
                else end = fileLength - 1;
                if (start < 0 || start >= fileLength || end < start) {
                    ex.getResponseHeaders().set("Content-Range", "bytes */" + fileLength);
                    ex.sendResponseHeaders(416, -1);
                    ex.close();
                    return;
                }
                partial = true;
            } catch (NumberFormatException e) {
                ex.sendResponseHeaders(416, -1);
                ex.close();
                return;
            }
        }

        long contentLength = end - start + 1;
        ex.getResponseHeaders().set("Content-Type", contentTypeFor(file.getFileName().toString()));
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if (partial) {
            ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        }
        ex.sendResponseHeaders(partial ? 206 : 200, contentLength);

        try (var raf = new RandomAccessFile(file.toFile(), "r"); OutputStream os = ex.getResponseBody()) {
            raf.seek(start);
            byte[] buffer = new byte[64 * 1024];
            long remaining = contentLength;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                os.write(buffer, 0, read);
                remaining -= read;
            }
        }

        new VideoRepository().incrementViewCount(vm.getId());
    }

    private static String contentTypeFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ogv")) return "video/ogg";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".m4v")) return "video/x-m4v";
        return "application/octet-stream";
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r")
                .replace("\\t", "\t").replace("\\\\", "\\");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — /api/admin/users
    // ═══════════════════════════════════════════════════════════════════════

    static final class AdminUsersHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isAdmin(ex)) return;
            String method = ex.getRequestMethod().toUpperCase();
            String path   = ex.getRequestURI().getPath();
            UserRepository repo = new UserRepository();

            if (method.equals("GET")) {
                List<User> users = repo.findAll();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < users.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(server.auth.AuthApiServer.userJson(users.get(i)));
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
                return;
            }

            // PUT /api/admin/users/{id}
            if (method.equals("PUT")) {
                int id = extractId(path);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Map<String,String> body = parseJsonBody(ex);
                String role   = body.get("role");
                String active = body.get("active");
                if (role   != null) repo.updateRole(id, role);
                if (active != null) repo.setActive(id, "true".equals(active));
                Optional<User> u = repo.findById(id);
                sendJson(ex, u.isPresent() ? 200 : 404, u.map(server.auth.AuthApiServer::userJson).orElse(err("Non trouvé")));
                return;
            }

            // DELETE /api/admin/users/{id}
            if (method.equals("DELETE")) {
                int id = extractId(path);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                sendJson(ex, repo.delete(id) ? 200 : 404, repo.delete(id) ? "{\"deleted\":true}" : err("Non trouvé"));
                return;
            }

            // POST /api/admin/users/{id}/subscription — grant subscription
            if (method.equals("POST") && path.endsWith("/subscription")) {
                int id = extractIdFromPath(path, -2);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Map<String,String> body = parseJsonBody(ex);
                String plan = body.getOrDefault("plan","monthly");
                int days    = SubscriptionRepository.PLAN_DURATIONS.getOrDefault(plan, 30);
                String dStr = body.get("days");
                if (dStr != null) { try { days = Integer.parseInt(dStr); } catch(NumberFormatException ignored) {} }
                new SubscriptionRepository().activatePaidPlan(id, plan, days, "Accordé par admin");
                AppLogger.info(LOG, "Abonnement accordé: userId=" + id + " plan=" + plan);
                sendJson(ex, 200, "{\"granted\":true,\"plan\":\"" + plan + "\",\"days\":" + days + "}");
                return;
            }

            // POST /api/admin/users/{id}/revoke — cancel subscription
            if (method.equals("POST") && path.endsWith("/revoke")) {
                int id = extractIdFromPath(path, -2);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                new SubscriptionRepository().cancelAll(id);
                AppLogger.info(LOG, "Abonnement révoqué: userId=" + id);
                sendJson(ex, 200, "{\"revoked\":true}");
                return;
            }

            sendJson(ex, 405, err("Method Not Allowed"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — /api/admin/subscriptions
    // ═══════════════════════════════════════════════════════════════════════

    static final class AdminSubscriptionsHandler implements HttpHandler {
        private static final DateTimeFormatter SUB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isAdmin(ex)) return;
            if (!isGet(ex)) { sendJson(ex, 405, err("Method Not Allowed")); return; }

            String filter = queryParam(ex.getRequestURI(), "status");
            SubscriptionRepository repo = new SubscriptionRepository();
            List<SubscriptionRepository.SubscriptionRow> list =
                filter != null ? repo.findByStatus(filter) : repo.findAll();

            StringBuilder sb = new StringBuilder("{\"count\":");
            sb.append(list.size()).append(",\"subscriptions\":[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                SubscriptionRepository.SubscriptionRow r = list.get(i);
                sb.append(JsonBuilder.obj()
                    .put("id",         r.id())
                    .put("userId",     r.userId())
                    .put("userEmail",  r.userEmail())
                    .put("username",   r.username())
                    .put("plan",       r.plan())
                    .put("status",     r.status())
                    .put("endsAt",     r.endsAt() != null ? r.endsAt().format(SUB_FMT) : "illimité")
                    .put("trialUsed",  r.trialUsed())
                    .put("createdAt",  r.createdAt() != null ? r.createdAt().format(SUB_FMT) : "")
                    .build());
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — /api/admin/payments
    // ═══════════════════════════════════════════════════════════════════════

    static final class AdminPaymentsHandler implements HttpHandler {
        private static final DateTimeFormatter PAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        @Override public void handle(HttpExchange ex) throws IOException {
            if (handleOptions(ex)) return;
            if (!isAdmin(ex)) return;
            String method = ex.getRequestMethod().toUpperCase();
            String path   = ex.getRequestURI().getPath();

            if (method.equals("GET")) {
                String filter = queryParam(ex.getRequestURI(), "status");
                PaymentRepository repo = new PaymentRepository();
                List<PaymentRepository.PaymentRow> list =
                    filter != null ? repo.findByStatus(filter) : repo.findAll();

                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(',');
                    PaymentRepository.PaymentRow p = list.get(i);
                    sb.append(JsonBuilder.obj()
                        .put("id",           p.id())
                        .put("userId",       p.userId())
                        .put("userEmail",    p.userEmail())
                        .put("username",     p.username())
                        .put("amount",       p.amount())
                        .put("currency",     p.currency())
                        .put("plan",         p.plan())
                        .put("durationDays", p.durationDays())
                        .put("status",       p.status())
                        .put("paymentMethod",p.paymentMethod())
                        .put("proofNote",    p.proofNote() != null ? p.proofNote() : "")
                        .put("adminNote",    p.adminNote() != null ? p.adminNote() : "")
                        .put("approvedBy",   p.approvedBy() != null ? p.approvedBy() : "")
                        .put("createdAt",    p.createdAt() != null ? p.createdAt().format(PAY_FMT) : "")
                        .put("processedAt",  p.processedAt() != null ? p.processedAt().format(PAY_FMT) : "")
                        .build());
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
                return;
            }

            // POST /api/admin/payments/{id}/approve
            if (method.equals("POST") && path.endsWith("/approve")) {
                int id = extractIdFromPath(path, -2);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Map<String,String> body = parseJsonBody(ex);
                String adminNote = body.getOrDefault("adminNote", "");
                String approvedBy = body.getOrDefault("approvedBy", "admin");
                boolean ok = new PaymentRepository().approve(id, approvedBy, adminNote);
                SseEventBus.get().publish("payment_approved", "{\"paymentId\":" + id + "}");
                sendJson(ex, ok ? 200 : 404, ok ? "{\"approved\":true}" : err("Paiement introuvable"));
                return;
            }

            // POST /api/admin/payments/{id}/reject
            if (method.equals("POST") && path.endsWith("/reject")) {
                int id = extractIdFromPath(path, -2);
                if (id < 0) { sendJson(ex, 400, err("ID invalide")); return; }
                Map<String,String> body = parseJsonBody(ex);
                String adminNote = body.getOrDefault("adminNote","");
                String approvedBy = body.getOrDefault("approvedBy","admin");
                boolean ok = new PaymentRepository().reject(id, approvedBy, adminNote);
                sendJson(ex, ok ? 200 : 404, ok ? "{\"rejected\":true}" : err("Paiement introuvable"));
                return;
            }

            sendJson(ex, 405, err("Method Not Allowed"));
        }
    }

    // ─── Extra helper: extract ID from path segment ─────────────────────────
    private static int extractIdFromPath(String path, int segment) {
        String[] parts = path.split("/");
        try {
            int idx = segment < 0 ? parts.length + segment : segment;
            return Integer.parseInt(parts[idx]);
        } catch (Exception e) { return -1; }
    }
}
