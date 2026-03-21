package db;

import common.AppLogger;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO pour la table categories.
 */
public final class CategoryRepository {

    private static final String COMPONENT = "CategoryRepository";
    private final DatabaseManager db;

    public record Category(int id, String name, String color, String icon, int videoCount) {}

    public CategoryRepository() {
        this.db = DatabaseManager.getInstance();
    }

    public List<Category> findAll() {
        String sql = """
            SELECT c.*, COUNT(v.id) as video_count
            FROM categories c
            LEFT JOIN videos v ON v.category_id = c.id
            GROUP BY c.id, c.name, c.color, c.icon, c.created_at
            ORDER BY c.name
        """;
        List<Category> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Category(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("color"),
                    rs.getString("icon"),
                    rs.getInt("video_count")
                ));
            }
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findAll error: " + e.getMessage());
        }
        return list;
    }

    public Optional<Category> findById(int id) {
        String sql = "SELECT c.*, 0 as video_count FROM categories c WHERE c.id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new Category(
                    rs.getInt("id"), rs.getString("name"),
                    rs.getString("color"), rs.getString("icon"), 0
                ));
            }
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "findById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public int create(String name, String color, String icon) {
        String sql = "INSERT INTO categories (name, color, icon) VALUES (?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.setString(2, color == null ? "#6366f1" : color);
            ps.setString(3, icon == null ? "film" : icon);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "create error: " + e.getMessage());
            throw new RuntimeException("Erreur création catégorie: " + e.getMessage());
        }
    }

    public boolean update(int id, String name, String color, String icon) {
        String sql = "UPDATE categories SET name=?, color=?, icon=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, color);
            ps.setString(3, icon);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "update error: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(int id) {
        // videos.category_id SET NULL via FK constraint
        String sql = "DELETE FROM categories WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.error(COMPONENT, "delete error: " + e.getMessage());
            return false;
        }
    }
}
