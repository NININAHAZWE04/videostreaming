package server.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import diary.Diary;
import diary.VideoInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * API HTTP minimale pour exposer le contenu du Diary à un frontend web.
 */
public final class DiaryApiServer {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DiaryApiServer() {
    }

    public static void main(String[] args) throws Exception {
        String diaryHost = args.length > 0 ? args[0].trim() : "localhost";
        int diaryPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
        int apiPort = args.length > 2 ? Integer.parseInt(args[2]) : 8080;

        validatePort(diaryPort, "diaryPort");
        validatePort(apiPort, "apiPort");

        HttpServer server = HttpServer.create(new InetSocketAddress(apiPort), 0);
        server.createContext("/api/health", new HealthHandler(diaryHost, diaryPort));
        server.createContext("/api/videos", new VideosHandler(diaryHost, diaryPort));
        server.setExecutor(Executors.newCachedThreadPool());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Arrêt de l'API web...");
            server.stop(1);
        }, "web-api-shutdown"));

        log("API web démarrée sur http://localhost:" + apiPort);
        log("Diary cible: " + diaryHost + ":" + diaryPort);
        server.start();
    }

    private static void validatePort(int port, String name) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(name + " doit être compris entre 1 et 65535");
        }
    }

    private static Diary lookupDiary(String diaryHost, int diaryPort) throws Exception {
        Registry registry = LocateRegistry.getRegistry(diaryHost, diaryPort);
        return (Diary) registry.lookup("Diary");
    }

    private static final class HealthHandler implements HttpHandler {
        private final String diaryHost;
        private final int diaryPort;

        private HealthHandler(String diaryHost, int diaryPort) {
            this.diaryHost = diaryHost;
            this.diaryPort = diaryPort;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String body = "{"
                + "\"status\":\"ok\"," 
                + "\"timestamp\":\"" + escapeJson(LocalDateTime.now().toString()) + "\","
                + "\"diaryHost\":\"" + escapeJson(diaryHost) + "\","
                + "\"diaryPort\":" + diaryPort
                + "}";
            sendJson(exchange, 200, body);
        }
    }

    private static final class VideosHandler implements HttpHandler {
        private final String diaryHost;
        private final int diaryPort;

        private VideosHandler(String diaryHost, int diaryPort) {
            this.diaryHost = diaryHost;
            this.diaryPort = diaryPort;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOptions(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Diary diary = lookupDiary(diaryHost, diaryPort);
                List<VideoInfo> videos = diary.listAllVideos();

                StringBuilder sb = new StringBuilder();
                sb.append("{\"count\":").append(videos.size()).append(",\"videos\":[");
                for (int i = 0; i < videos.size(); i++) {
                    VideoInfo v = videos.get(i);
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append('{')
                        .append("\"title\":\"").append(escapeJson(v.getTitle())).append("\",")
                        .append("\"host\":\"").append(escapeJson(v.getHost())).append("\",")
                        .append("\"port\":").append(v.getPort()).append(',')
                        .append("\"url\":\"http://").append(escapeJson(v.getHost())).append(':').append(v.getPort()).append("\",")
                        .append("\"thumbnailUrl\":\"http://").append(escapeJson(v.getHost())).append(':').append(v.getPort()).append("/thumbnail\"")
                        .append('}');
                }
                sb.append("]}");

                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                log("Erreur API /api/videos: " + e.getMessage());
                String body = "{\"error\":\"Diary unavailable\",\"details\":\"" + escapeJson(e.getMessage()) + "\"}";
                sendJson(exchange, 502, body);
            }
        }
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return false;
        }
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private static void log(String message) {
        System.out.println("[DiaryApiServer][" + LocalDateTime.now().format(TS) + "] " + message);
    }
}
