package db;

import common.AppLogger;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO pour les statistiques de visionnage et le dashboard.
 */
public final class StatsRepository {

    private static final String COMPONENT = "StatsRepository";
    private final DatabaseManager db;

    public record HourlyStats(int hour, int viewCount) {}
    public record DashboardStats(int totalVideos, int activeStreams, long viewsToday,
                                  long totalViews, double totalBandwidthMb, List<VideoMetadata> topVideos) {}

    public StatsRepository() {
        this.db = DatabaseManager.getInstance();
    }

    /** Enregistre un événement de visionnage */
    public void recordView(int videoId, String clientIpHash, long bytesServed) {
        String sql = "INSERT INTO view_events (video_id, client_ip_hash, bytes_served) VALUES (?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, videoId);
            ps.setString(2, clientIpHash);
            ps.setLong(3, bytesServed);
            ps.executeUpdate();
        } catch (SQLException e) {
            AppLogger.warn(COMPONENT, "recordView error: " + e.getMessage());
        }
    }

    /** Vues par heure des dernières 24h (24 entrées) */
    public List<HourlyStats> viewsLast24h() {
        String sql = """
            SELECT EXTRACT(HOUR FROM viewed_at) as hr, COUNT(*) as cnt
            FROM view_events
            WHERE viewed_at >= DATEADD('HOUR', -24, CURRENT_TIMESTAMP)
            GROUP BY EXTRACT(HOUR FROM viewed_at)
            ORDER BY hr
        """;
        List<HourlyStats> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new HourlyStats(rs.getInt("hr"), rs.getInt("cnt")));
            }
        } catch (SQLException e) {
            AppLogger.warn(COMPONENT, "viewsLast24h error: " + e.getMessage());
        }
        return list;
    }

    /** Stats complètes pour le dashboard */
    public DashboardStats getDashboard(int activeStreamCount) {
        VideoRepository vr = new VideoRepository();
        int totalVideos = vr.countTotal();
        long viewsToday = vr.totalViewsToday();
        long totalViews = countQuery("SELECT COUNT(*) FROM view_events");
        double totalBandwidthMb = sumQuery("SELECT SUM(bytes_served) / 1048576.0 FROM view_events");
        List<VideoMetadata> top5 = vr.findTopViewed(5);
        return new DashboardStats(totalVideos, activeStreamCount, viewsToday, totalViews, totalBandwidthMb, top5);
    }

    /** Bande passante totale en Mo transmis aujourd'hui */
    public double bandwidthTodayMb() {
        return sumQuery("""
            SELECT COALESCE(SUM(bytes_served),0) / 1048576.0 FROM view_events
            WHERE CAST(viewed_at AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE)
        """);
    }

    private long countQuery(String sql) {
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            AppLogger.warn(COMPONENT, "countQuery error: " + e.getMessage());
            return 0;
        }
    }

    private double sumQuery(String sql) {
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            AppLogger.warn(COMPONENT, "sumQuery error: " + e.getMessage());
            return 0.0;
        }
    }
}
