package server.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import common.AppLogger;
import db.DatabaseManager;
import db.VideoMetadata;
import db.VideoRepository;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Optional;

/**
 * Endpoint GET /api/download?token=<token>
 * Sert le fichier vidéo en téléchargement après validation du token.
 */
public final class DownloadHandler implements HttpHandler {

    private static final String LOG = "DownloadHandler";
    private static final int BUFFER = 65536;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        AuthApiServer.addCors(ex);
        if (AuthApiServer.handleOptions(ex)) return;

        String query = ex.getRequestURI().getQuery();
        String token = extractParam(query, "token");

        if (token == null || token.isBlank()) {
            AuthApiServer.sendJson(ex, 400, AuthApiServer.err("Token manquant")); return;
        }

        // Validate token
        int videoId = validateToken(token);
        if (videoId < 0) {
            AuthApiServer.sendJson(ex, 403, AuthApiServer.err("Token invalide ou expiré")); return;
        }

        // Get video file
        Optional<VideoMetadata> vOpt = new VideoRepository().findById(videoId);
        if (vOpt.isEmpty()) {
            AuthApiServer.sendJson(ex, 404, AuthApiServer.err("Vidéo introuvable")); return;
        }
        VideoMetadata video = vOpt.get();
        if (video.getFilePath() == null) {
            AuthApiServer.sendJson(ex, 404, AuthApiServer.err("Fichier non disponible")); return;
        }

        File file = new File(video.getFilePath());
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            AuthApiServer.sendJson(ex, 404, AuthApiServer.err("Fichier introuvable sur le serveur")); return;
        }

        // Mark token as used
        markUsed(token);

        // Increment download count
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE videos SET download_count = download_count + 1 WHERE id=?")) {
            ps.setInt(1, videoId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}

        String filename = safeFilename(video.getTitle(), file);
        String contentType = getContentType(file.getName());

        AppLogger.info(LOG, "Download: " + filename + " (" + video.getFormattedSize() + ")");

        // Stream the file
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ex.getResponseHeaders().set("Content-Length", String.valueOf(file.length()));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, file.length());

        try (OutputStream out = ex.getResponseBody();
             FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[BUFFER];
            int read;
            while ((read = fis.read(buf)) != -1) out.write(buf, 0, read);
            out.flush();
        } catch (IOException e) {
            AppLogger.warn(LOG, "Download interrompu: " + e.getMessage());
        }
    }

    /** Valide le token et retourne le video_id. -1 si invalide. */
    private int validateToken(String token) {
        String sql = """
            SELECT video_id FROM download_tokens
            WHERE token=? AND used=FALSE AND expires_at > CURRENT_TIMESTAMP
        """;
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("video_id") : -1;
        } catch (SQLException e) {
            AppLogger.warn(LOG, "validateToken: " + e.getMessage());
            return -1;
        }
    }

    private void markUsed(String token) {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE download_tokens SET used=TRUE WHERE token=?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return null;
    }

    private String safeFilename(String title, File file) {
        String ext = ".mp4";
        int dot = file.getName().lastIndexOf('.');
        if (dot >= 0) ext = file.getName().substring(dot);
        return title.replaceAll("[^a-zA-Z0-9 _-]", "_") + ext;
    }

    private String getContentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".mp4"))  return "video/mp4";
        if (n.endsWith(".mkv"))  return "video/x-matroska";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".avi"))  return "video/x-msvideo";
        return "application/octet-stream";
    }
}
