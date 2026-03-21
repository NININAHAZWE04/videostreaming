package db;

import common.AppLogger;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** DAO pour les abonnements : trial, monthly, annual, free. */
public final class SubscriptionRepository {

    private static final String LOG = "SubscriptionRepo";
    private final DatabaseManager db;

    // Plans disponibles : name -> durée en jours
    public static final Map<String, Integer> PLAN_DURATIONS = Map.of(
        "trial",   14,
        "monthly", 30,
        "annual",  365,
        "free",    -1   // -1 = illimité (accès free seulement)
    );

    public record SubscriptionRow(int id, int userId, String userEmail, String username,
                                   String plan, String status,
                                   LocalDateTime startsAt, LocalDateTime endsAt,
                                   boolean trialUsed, String notes, LocalDateTime createdAt) {}

    public SubscriptionRepository() { this.db = DatabaseManager.getInstance(); }

    /** Démarre un trial de 14 jours. Lève une exception si déjà utilisé. */
    public int startTrial(int userId) {
        // Check trial not already used
        String check = "SELECT COUNT(*) FROM subscriptions WHERE user_id=? AND trial_used=TRUE";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(check)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) throw new RuntimeException("Trial déjà utilisé");
        } catch (SQLException e) { throw new RuntimeException(e); }

        // Cancel any existing active subscription
        cancelAll(userId);

        int trialDays = new PlanRepository().findByPlan("trial").map(PlanRepository.PlanRow::durationDays).orElse(14);
        LocalDateTime ends = LocalDateTime.now().plusDays(Math.max(1, trialDays));
        return insert(userId, "trial", "active", ends, true, "Trial 14 jours gratuit");
    }

    /** Active un abonnement payant après approbation du paiement. */
    public int activatePaidPlan(int userId, String plan, int durationDays, String notes) {
        cancelAll(userId);
        LocalDateTime ends = durationDays > 0 ? LocalDateTime.now().plusDays(durationDays) : null;
        return insert(userId, plan, "active", ends, true, notes);
    }

    /** Active un abonnement gratuit (accès content free seulement). */
    public int activateFree(int userId) {
        cancelAll(userId);
        return insert(userId, "free", "active", null, false, "Compte gratuit");
    }

    /** Expire manuellement tous les abonnements actifs d'un utilisateur. */
    public void cancelAll(int userId) {
        String sql = "UPDATE subscriptions SET status='cancelled' WHERE user_id=? AND status='active'";
        exec(sql, userId);
    }

    /** Expire les abonnements dont la date de fin est passée. Retourne le nombre expiré. */
    public int expireOverdue() {
        String sql = "UPDATE subscriptions SET status='expired' WHERE status='active' AND ends_at IS NOT NULL AND ends_at < CURRENT_TIMESTAMP";
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            int n = s.executeUpdate(sql);
            if (n > 0) AppLogger.info(LOG, n + " abonnement(s) expiré(s)");
            return n;
        } catch (SQLException e) { AppLogger.warn(LOG, "expireOverdue: " + e.getMessage()); return 0; }
    }

    public List<SubscriptionRow> findAll() {
        String sql = """
            SELECT s.*, u.email, u.username
            FROM subscriptions s JOIN users u ON s.user_id = u.id
            ORDER BY s.created_at DESC LIMIT 200
        """;
        return queryList(sql);
    }

    public List<SubscriptionRow> findByStatus(String status) {
        String sql = """
            SELECT s.*, u.email, u.username
            FROM subscriptions s JOIN users u ON s.user_id = u.id
            WHERE s.status = ? ORDER BY s.created_at DESC
        """;
        return queryList(sql, status);
    }

    public List<SubscriptionRow> findByUserId(int userId) {
        String sql = """
            SELECT s.*, u.email, u.username
            FROM subscriptions s JOIN users u ON s.user_id = u.id
            WHERE s.user_id = ? ORDER BY s.created_at DESC
        """;
        return queryList(sql, userId);
    }

    public int countActive() {
        return count("SELECT COUNT(*) FROM subscriptions WHERE status='active' AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP)");
    }

    public int countTrials() {
        return count("SELECT COUNT(*) FROM subscriptions WHERE plan='trial' AND status='active' AND ends_at > CURRENT_TIMESTAMP");
    }

    // ─── Private helpers ────────────────────────────────────

    private int insert(int userId, String plan, String status, LocalDateTime endsAt, boolean trialUsed, String notes) {
        String sql = "INSERT INTO subscriptions (user_id, plan, status, ends_at, trial_used, notes) VALUES (?,?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, plan);
            ps.setString(3, status);
            if (endsAt != null) ps.setTimestamp(4, Timestamp.valueOf(endsAt)); else ps.setNull(4, Types.TIMESTAMP);
            ps.setBoolean(5, trialUsed);
            ps.setString(6, notes);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            AppLogger.error(LOG, "insert error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void exec(String sql, Object... params) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) { AppLogger.warn(LOG, "exec: " + e.getMessage()); }
    }

    private int count(String sql) {
        try (Connection c = db.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private List<SubscriptionRow> queryList(String sql, Object... params) {
        List<SubscriptionRow> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp sa = rs.getTimestamp("starts_at");
                Timestamp ea = rs.getTimestamp("ends_at");
                Timestamp ca = rs.getTimestamp("created_at");
                list.add(new SubscriptionRow(
                    rs.getInt("id"), rs.getInt("user_id"),
                    rs.getString("email"), rs.getString("username"),
                    rs.getString("plan"), rs.getString("status"),
                    sa != null ? sa.toLocalDateTime() : null,
                    ea != null ? ea.toLocalDateTime() : null,
                    rs.getBoolean("trial_used"),
                    rs.getString("notes"),
                    ca != null ? ca.toLocalDateTime() : null
                ));
            }
        } catch (SQLException e) { AppLogger.error(LOG, "queryList: " + e.getMessage()); }
        return list;
    }
}
