package db;

import common.AppLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stockage de configuration dynamique administrable depuis le panel.
 */
public final class SettingsRepository {

    private static final String LOG = "SettingsRepository";
    private final DatabaseManager db;

    public SettingsRepository() {
        this.db = DatabaseManager.getInstance();
    }

    public Map<String, String> findAll() {
        Map<String, String> out = new LinkedHashMap<>();
        String sql = "SELECT setting_key, setting_value FROM app_settings ORDER BY setting_key";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            AppLogger.warn(LOG, "findAll: " + e.getMessage());
        }
        return out;
    }

    public String get(String key, String fallback) {
        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(1);
        } catch (SQLException e) {
            AppLogger.warn(LOG, "get(" + key + "): " + e.getMessage());
        }
        return fallback;
    }

    public int getInt(String key, int fallback) {
        String val = get(key, null);
        if (val == null || val.isBlank()) return fallback;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean upsert(String key, String value) {
        String sql = "MERGE INTO app_settings (setting_key, setting_value) KEY(setting_key) VALUES (?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.warn(LOG, "upsert(" + key + "): " + e.getMessage());
            return false;
        }
    }
}

