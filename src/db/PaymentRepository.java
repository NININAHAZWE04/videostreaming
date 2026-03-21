package db;

import common.AppLogger;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** DAO pour les paiements cash gérés manuellement par l'admin. */
public final class PaymentRepository {

    private static final String LOG = "PaymentRepository";
    private final DatabaseManager db;

    public record PaymentRow(int id, int userId, String userEmail, String username,
                              double amount, String currency, String plan,
                              int durationDays, String status, String paymentMethod,
                              String proofNote, String adminNote, String approvedBy,
                              LocalDateTime createdAt, LocalDateTime processedAt) {}

    public PaymentRepository() { this.db = DatabaseManager.getInstance(); }

    /** Soumet une demande de paiement cash par l'utilisateur. */
    public int submitCashRequest(int userId, double amount, String currency, String plan, int durationDays, String proofNote) {
        String sql = """
            INSERT INTO payments (user_id, amount, currency, plan, duration_days,
                                  status, payment_method, proof_note)
            VALUES (?,?,?,?,?,'pending','cash',?)
        """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, currency != null ? currency : "USD");
            ps.setString(4, plan);
            ps.setInt(5, durationDays);
            ps.setString(6, proofNote);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            AppLogger.error(LOG, "submitCashRequest: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /** Approuve un paiement : active l'abonnement et marque le paiement. */
    public boolean approve(int paymentId, String adminName, String adminNote) {
        // Get payment details
        Optional<PaymentRow> opt = findById(paymentId);
        if (opt.isEmpty()) return false;
        PaymentRow p = opt.get();

        // Activate subscription
        new SubscriptionRepository().activatePaidPlan(p.userId(), p.plan(), p.durationDays(), "Paiement #" + paymentId + " approuvé");

        // Mark payment approved
        String sql = "UPDATE payments SET status='approved', admin_note=?, approved_by=?, processed_at=CURRENT_TIMESTAMP WHERE id=?";
        exec(sql, adminNote, adminName, paymentId);
        AppLogger.info(LOG, "Paiement #" + paymentId + " approuvé par " + adminName + " → user " + p.userId());
        return true;
    }

    /** Rejette un paiement. */
    public boolean reject(int paymentId, String adminName, String adminNote) {
        String sql = "UPDATE payments SET status='rejected', admin_note=?, approved_by=?, processed_at=CURRENT_TIMESTAMP WHERE id=?";
        exec(sql, adminNote, adminName, paymentId);
        AppLogger.info(LOG, "Paiement #" + paymentId + " rejeté par " + adminName);
        return true;
    }

    public Optional<PaymentRow> findById(int id) {
        String sql = SELECT_SQL + " WHERE p.id=?";
        return queryOne(sql, id);
    }

    public List<PaymentRow> findAll() {
        return queryList(SELECT_SQL + " ORDER BY p.created_at DESC LIMIT 200");
    }

    public List<PaymentRow> findByStatus(String status) {
        return queryList(SELECT_SQL + " WHERE p.status=? ORDER BY p.created_at DESC", status);
    }

    public List<PaymentRow> findByUserId(int userId) {
        return queryList(SELECT_SQL + " WHERE p.user_id=? ORDER BY p.created_at DESC", userId);
    }

    public int countPending() {
        return count("SELECT COUNT(*) FROM payments WHERE status='pending'");
    }

    public double totalApproved() {
        return sumQuery("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='approved'");
    }

    // ─── Private ────────────────────────────────────────────

    private static final String SELECT_SQL = """
        SELECT p.*, u.email, u.username
        FROM payments p JOIN users u ON p.user_id = u.id
    """;

    private Optional<PaymentRow> queryOne(String sql, Object... params) {
        List<PaymentRow> list = queryList(sql, params);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private List<PaymentRow> queryList(String sql, Object... params) {
        List<PaymentRow> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp ca = rs.getTimestamp("created_at");
                Timestamp pa = rs.getTimestamp("processed_at");
                list.add(new PaymentRow(
                    rs.getInt("id"), rs.getInt("user_id"),
                    rs.getString("email"), rs.getString("username"),
                    rs.getDouble("amount"), rs.getString("currency"),
                    rs.getString("plan"), rs.getInt("duration_days"),
                    rs.getString("status"), rs.getString("payment_method"),
                    rs.getString("proof_note"), rs.getString("admin_note"),
                    rs.getString("approved_by"),
                    ca != null ? ca.toLocalDateTime() : null,
                    pa != null ? pa.toLocalDateTime() : null
                ));
            }
        } catch (SQLException e) { AppLogger.error(LOG, "queryList: " + e.getMessage()); }
        return list;
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

    private double sumQuery(String sql) {
        try (Connection c = db.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) { return 0; }
    }
}
