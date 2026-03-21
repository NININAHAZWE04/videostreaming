package db;

import common.AppLogger;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** DAO complet pour les utilisateurs avec statut d'abonnement. */
public final class UserRepository {

    private static final String LOG = "UserRepository";
    private final DatabaseManager db;

    private static final String SELECT_WITH_SUB = """
        SELECT u.*,
               s.plan      AS sub_plan,
               s.status    AS sub_status,
               s.ends_at   AS sub_ends_at,
               s.trial_used AS trial_used
        FROM users u
        LEFT JOIN subscriptions s ON s.user_id = u.id
          AND s.status = 'active'
          AND (s.ends_at IS NULL OR s.ends_at > CURRENT_TIMESTAMP)
        """;

    public UserRepository() { this.db = DatabaseManager.getInstance(); }

    /** Crée un utilisateur. Retourne l'ID généré. */
    public int create(String email, String username, String passwordHash, String salt) {
        String sql = "INSERT INTO users (email, username, password_hash, password_salt, role, avatar_color) VALUES (?,?,?,?,?,?)";
        String[] colors = {"#38bdf8","#a78bfa","#34d399","#f472b6","#fb923c","#facc15","#60a5fa","#4ade80"};
        String color = colors[Math.abs(email.hashCode()) % colors.length];
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email.trim().toLowerCase());
            ps.setString(2, username.trim());
            ps.setString(3, passwordHash);
            ps.setString(4, salt);
            ps.setString(5, "user");
            ps.setString(6, color);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            AppLogger.error(LOG, "create error: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    public Optional<User> findById(int id) {
        return queryOne(SELECT_WITH_SUB + " WHERE u.id = ?", id);
    }

    public Optional<User> findByEmail(String email) {
        return queryOne(SELECT_WITH_SUB + " WHERE LOWER(u.email) = LOWER(?)", email.trim());
    }

    public boolean emailExists(String email) {
        return findByEmail(email).isPresent();
    }

    public List<User> findAll() {
        return queryList(SELECT_WITH_SUB + " ORDER BY u.created_at DESC");
    }

    public List<User> findByStatus(String subStatus) {
        return queryList(SELECT_WITH_SUB + " WHERE s.status = ? ORDER BY u.created_at DESC", subStatus);
    }

    public boolean setActive(int userId, boolean active) {
        return exec("UPDATE users SET is_active=? WHERE id=?", active, userId);
    }

    public boolean updateRole(int userId, String role) {
        return exec("UPDATE users SET role=? WHERE id=?", role, userId);
    }

    public boolean updateLastLogin(int userId) {
        return exec("UPDATE users SET last_login_at=CURRENT_TIMESTAMP WHERE id=?", userId);
    }

    public boolean updatePassword(int userId, String hash, String salt) {
        return exec("UPDATE users SET password_hash=?, password_salt=? WHERE id=?", hash, salt, userId);
    }

    public boolean delete(int userId) {
        return exec("DELETE FROM users WHERE id=?", userId);
    }

    public int countTotal() { return count("SELECT COUNT(*) FROM users"); }

    public int countActive() {
        return count("""
            SELECT COUNT(*) FROM users u
            JOIN subscriptions s ON s.user_id=u.id
            WHERE s.status='active' AND (s.ends_at IS NULL OR s.ends_at > CURRENT_TIMESTAMP)
        """);
    }

    public int countTrials() {
        return count("""
            SELECT COUNT(*) FROM subscriptions
            WHERE plan='trial' AND status='active'
            AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP)
        """);
    }

    // ─── Helpers ────────────────────────────────────────────

    private Optional<User> queryOne(String sql, Object... params) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        } catch (SQLException e) {
            AppLogger.error(LOG, "queryOne: " + e.getMessage());
            return Optional.empty();
        }
    }

    private List<User> queryList(String sql, Object... params) {
        List<User> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            AppLogger.error(LOG, "queryList: " + e.getMessage());
        }
        return list;
    }

    private boolean exec(String sql, Object... params) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.error(LOG, "exec: " + e.getMessage());
            return false;
        }
    }

    private int count(String sql) {
        try (Connection c = db.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setEmail(rs.getString("email"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setPasswordSalt(rs.getString("password_salt"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("is_active"));
        u.setAvatarColor(rs.getString("avatar_color"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) u.setCreatedAt(ca.toLocalDateTime());
        Timestamp ll = rs.getTimestamp("last_login_at");
        if (ll != null) u.setLastLoginAt(ll.toLocalDateTime());
        // Subscription fields (may be null from LEFT JOIN)
        String plan = rs.getString("sub_plan");
        u.setSubPlan(plan);
        u.setSubStatus(rs.getString("sub_status"));
        Timestamp ends = rs.getTimestamp("sub_ends_at");
        if (ends != null) u.setSubEndsAt(ends.toLocalDateTime());
        u.setTrialUsed(rs.getBoolean("trial_used"));
        return u;
    }
}
