package db;

import common.AppLogger;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la table videos avec toutes les métadonnées.
 */
public final class VideoRepository {

    private static final String COMPONENT = "VideoRepository";
    private final DatabaseManager db;

    private static final String SELECT_ALL = """
        SELECT v.*, c.name as cat_name, c.color as cat_color
        FROM videos v
        LEFT JOIN categories c ON v.category_id = c.id
    """;

    public VideoRepository() {
        this.db = DatabaseManager.getInstance();
    }

    /** Crée ou met à jour une vidéo par son titre. Retourne l'ID. */
    public int upsert(VideoMetadata vm) {
        String sql = """
            MERGE INTO videos (title, file_path, host, port, file_size, duration_sec,
                resolution, codec, fps, bitrate_kbps, quality_label, synopsis,
                category_id, tags, is_active)
            KEY (title)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, vm.getTitle());
            ps.setString(2, vm.getFilePath());
            ps.setString(3, vm.getHost());
            ps.setInt(4, vm.getPort());
            ps.setLong(5, vm.getFileSize());
            ps.setInt(6, vm.getDurationSec());
            ps.setString(7, vm.getResolution());
            ps.setString(8, vm.getCodec());
            ps.setFloat(9, vm.getFps());
            ps.setInt(10, vm.getBitrateKbps());
            ps.setString(11, vm.getQualityLabel());
            ps.setString(12, vm.getSynopsis());
            if (vm.getCategoryId() > 0) ps.setInt(13, vm.getCategoryId()); else ps.setNull(13, Types.INTEGER);
            ps.setString(14, vm.getTags());
            ps.setBoolean(15, vm.isActive());
            ps.executeUpdate();

            // Get ID - MERGE may not return generated keys in H2 cleanly, so we query
            Optional<VideoMetadata> existing = findByTitle(vm.getTitle());
            return existing.map(VideoMetadata::getId).orElse(-1);
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "Erreur upsert vidéo: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /** Met à jour host+port quand un stream démarre */
    public void updateStreamInfo(String title, String host, int port) {
        String sql = "UPDATE videos SET host=?, port=?, is_active=TRUE, last_streamed_at=CURRENT_TIMESTAMP WHERE title=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, host);
            ps.setInt(2, port);
            ps.setString(3, title.trim());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                // Video not yet in DB — insert minimal entry
                VideoMetadata vm = new VideoMetadata();
                vm.setTitle(title.trim());
                vm.setHost(host);
                vm.setPort(port);
                vm.setActive(true);
                upsert(vm);
            }
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "Erreur updateStreamInfo: " + e.getMessage());
        }
    }

    /** Marque un stream comme arrêté */
    public void markStreamStopped(String title) {
        String sql = "UPDATE videos SET is_active=FALSE, host=NULL, port=NULL WHERE title=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "Erreur markStreamStopped: " + e.getMessage());
        }
    }

    /** Met à jour les métadonnées (synopsis, catégorie, tags) */
    public void updateMetadata(int id, String synopsis, Integer categoryId, String tags) {
        String sql = "UPDATE videos SET synopsis=?, category_id=?, tags=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, synopsis);
            if (categoryId != null && categoryId > 0) ps.setInt(2, categoryId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, tags);
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "Erreur updateMetadata: " + e.getMessage());
        }
    }

    public boolean setActive(int id, boolean active) {
        String sql = "UPDATE videos SET is_active=?, last_streamed_at=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            if (active) ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            else ps.setNull(2, Types.TIMESTAMP);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "Erreur setActive: " + e.getMessage());
            return false;
        }
    }

    /** Incrémente le compteur de vues */
    public void incrementViewCount(int id) {
        String sql = "UPDATE videos SET view_count = view_count + 1 WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            AppLogger.warn(COMPONENT, "Erreur incrementViewCount: " + e.getMessage());
        }
    }

    public Optional<VideoMetadata> findByTitle(String title) {
        String sql = SELECT_ALL + " WHERE LOWER(v.title) = LOWER(?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findByTitle error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<VideoMetadata> findById(int id) {
        String sql = SELECT_ALL + " WHERE v.id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<VideoMetadata> findAll() {
        return query(SELECT_ALL + " ORDER BY LOWER(v.title)");
    }

    public List<VideoMetadata> findActive() {
        return query(SELECT_ALL + " WHERE v.is_active = TRUE ORDER BY LOWER(v.title)");
    }

    public List<VideoMetadata> findByCategory(int categoryId) {
        String sql = SELECT_ALL + " WHERE v.category_id = ? ORDER BY LOWER(v.title)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            ResultSet rs = ps.executeQuery();
            return mapAll(rs);
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findByCategory error: " + e.getMessage());
            return List.of();
        }
    }

    public List<VideoMetadata> search(String query) {
        String pattern = "%" + query.toLowerCase() + "%";
        String sql = SELECT_ALL + " WHERE LOWER(v.title) LIKE ? OR LOWER(v.synopsis) LIKE ? OR LOWER(v.tags) LIKE ? ORDER BY v.view_count DESC";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            return mapAll(ps.executeQuery());
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "search error: " + e.getMessage());
            return List.of();
        }
    }

    public List<VideoMetadata> findTopViewed(int limit) {
        String sql = SELECT_ALL + " ORDER BY v.view_count DESC LIMIT ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return mapAll(ps.executeQuery());
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findTopViewed error: " + e.getMessage());
            return List.of();
        }
    }

    public List<VideoMetadata> findInactiveLimit(int limit) {
        String sql = SELECT_ALL + " WHERE v.is_active = FALSE ORDER BY v.created_at DESC LIMIT ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return mapAll(ps.executeQuery());
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findInactiveLimit error: " + e.getMessage());
            return List.of();
        }
    }

    public List<VideoMetadata> findMostViewedSinceDays(int days, int limit) {
        String sql = """
            SELECT v.*, c.name as cat_name, c.color as cat_color
            FROM videos v
            LEFT JOIN categories c ON v.category_id = c.id
            WHERE v.is_active = TRUE
            ORDER BY (
                SELECT COUNT(*) FROM view_events ve
                WHERE ve.video_id = v.id
                  AND ve.viewed_at >= DATEADD('DAY', ?, CURRENT_TIMESTAMP)
            ) DESC, v.view_count DESC, v.created_at DESC
            LIMIT ?
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, -Math.abs(days));
            ps.setInt(2, limit);
            return mapAll(ps.executeQuery());
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findMostViewedSinceDays error: " + e.getMessage());
            return List.of();
        }
    }

    public boolean deleteById(int id) {
        String sql = "DELETE FROM videos WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "delete error: " + e.getMessage());
            return false;
        }
    }

    public int countActive() {
        return countQuery("SELECT COUNT(*) FROM videos WHERE is_active=TRUE");
    }

    public int countTotal() {
        return countQuery("SELECT COUNT(*) FROM videos");
    }

    public long totalViewsToday() {
        String sql = "SELECT COUNT(*) FROM view_events WHERE CAST(viewed_at AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)";
        return countQuery(sql);
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private List<VideoMetadata> query(String sql) {
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return mapAll(rs);
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "query error: " + e.getMessage());
            return List.of();
        }
    }

    private List<VideoMetadata> mapAll(ResultSet rs) throws SQLException {
        List<VideoMetadata> list = new ArrayList<>();
        while (rs.next()) list.add(map(rs));
        return list;
    }

    private int countQuery(String sql) {
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "countQuery error: " + e.getMessage());
            return 0;
        }
    }

    private VideoMetadata map(ResultSet rs) throws SQLException {
        VideoMetadata vm = new VideoMetadata();
        vm.setId(rs.getInt("id"));
        vm.setTitle(rs.getString("title"));
        vm.setFilePath(rs.getString("file_path"));
        vm.setHost(rs.getString("host"));
        vm.setPort(rs.getInt("port"));
        vm.setFileSize(rs.getLong("file_size"));
        vm.setDurationSec(rs.getInt("duration_sec"));
        vm.setResolution(rs.getString("resolution"));
        vm.setCodec(rs.getString("codec"));
        vm.setFps(rs.getFloat("fps"));
        vm.setBitrateKbps(rs.getInt("bitrate_kbps"));
        vm.setQualityLabel(rs.getString("quality_label"));
        vm.setSynopsis(rs.getString("synopsis"));
        int catId = rs.getInt("category_id");
        vm.setCategoryId(rs.wasNull() ? 0 : catId);
        vm.setCategoryName(rs.getString("cat_name"));
        vm.setCategoryColor(rs.getString("cat_color"));
        vm.setTags(rs.getString("tags"));
        vm.setViewCount(rs.getInt("view_count"));
        vm.setFree(rs.getBoolean("is_free"));
        int dlc = 0; try { dlc = rs.getInt("download_count"); } catch (SQLException ignored) {}
        vm.setDownloadCount(dlc);
        vm.setActive(rs.getBoolean("is_active"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) vm.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp lastStreamed = rs.getTimestamp("last_streamed_at");
        if (lastStreamed != null) vm.setLastStreamedAt(lastStreamed.toLocalDateTime());

        // Build thumbnail URL
        String host = vm.getHost();
        int port = vm.getPort();
        if (host != null && port > 0) {
            vm.setThumbnailUrl("http://" + host + ":" + port + "/thumbnail");
        }
        return vm;
    }
}
