package db;

import common.AppConfig;
import common.AppLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gestion centralisee des plans d'abonnement (prix, duree, devise).
 */
public final class PlanRepository {

    private static final String LOG = "PlanRepository";
    private final DatabaseManager db;

    public record PlanRow(String plan, double price, int durationDays, String currency, boolean active) {}

    public PlanRepository() {
        this.db = DatabaseManager.getInstance();
    }

    public void seedDefaultsIfEmpty() {
        String countSql = "SELECT COUNT(*) FROM subscription_plans";
        try (Connection c = db.getConnection();
             PreparedStatement count = c.prepareStatement(countSql);
             ResultSet rs = count.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) return;
        } catch (SQLException e) {
            AppLogger.warn(LOG, "seed/count: " + e.getMessage());
            return;
        }

        AppConfig cfg = AppConfig.get();
        upsert("monthly", cfg.getPlanMonthlyPrice(), cfg.getPlanMonthlyDays(), cfg.getPlanCurrency(), true);
        upsert("annual", cfg.getPlanAnnualPrice(), cfg.getPlanAnnualDays(), cfg.getPlanCurrency(), true);
        upsert("trial",  0.0, cfg.getPlanTrialDays(), cfg.getPlanCurrency(), true);
        upsert("free",   0.0, -1, cfg.getPlanCurrency(), true);
    }

    public List<PlanRow> findAll() {
        String sql = "SELECT plan, price, duration_days, currency, is_active FROM subscription_plans ORDER BY plan";
        List<PlanRow> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new PlanRow(
                    rs.getString("plan"),
                    rs.getDouble("price"),
                    rs.getInt("duration_days"),
                    rs.getString("currency"),
                    rs.getBoolean("is_active")
                ));
            }
        } catch (SQLException e) {
            AppLogger.warn(LOG, "findAll: " + e.getMessage());
        }
        return out;
    }

    public Optional<PlanRow> findByPlan(String plan) {
        String sql = "SELECT plan, price, duration_days, currency, is_active FROM subscription_plans WHERE plan = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, plan);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new PlanRow(
                    rs.getString("plan"),
                    rs.getDouble("price"),
                    rs.getInt("duration_days"),
                    rs.getString("currency"),
                    rs.getBoolean("is_active")
                ));
            }
        } catch (SQLException e) {
            AppLogger.warn(LOG, "findByPlan(" + plan + "): " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean upsert(String plan, double price, int durationDays, String currency, boolean active) {
        String sql = "MERGE INTO subscription_plans (plan, price, duration_days, currency, is_active) KEY(plan) VALUES (?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, plan);
            ps.setDouble(2, price);
            ps.setInt(3, durationDays);
            ps.setString(4, currency);
            ps.setBoolean(5, active);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.warn(LOG, "upsert(" + plan + "): " + e.getMessage());
            return false;
        }
    }
}

