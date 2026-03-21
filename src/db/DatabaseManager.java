package db;

import common.AppConfig;
import common.AppLogger;
import java.sql.*;

/**
 * Gestionnaire de connexion H2 embedded.
 * Initialise le schéma au premier démarrage et gère les migrations.
 */
public final class DatabaseManager {

    private static final String COMPONENT = "DatabaseManager";
    private static DatabaseManager instance;

    private final String url;
    private final String user;
    private final String password;

    private DatabaseManager(String url, String user, String password) throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        initSchema();
        AppLogger.info(COMPONENT, "H2 initialisé: " + url);
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            AppConfig cfg = AppConfig.get();
            try {
                // Create data directory if needed
                java.io.File dataDir = new java.io.File("data");
                if (!dataDir.exists()) dataDir.mkdirs();
                instance = new DatabaseManager(cfg.getDbUrl(), cfg.getDbUser(), cfg.getDbPassword());
            } catch (SQLException e) {
                throw new RuntimeException("Impossible d'initialiser la base H2: " + e.getMessage(), e);
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public static void shutdown() {
        if (instance != null) {
            try (Connection c = instance.getConnection(); Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
                AppLogger.info(COMPONENT, "H2 arrêtée proprement");
            } catch (Exception e) {
                AppLogger.warn(COMPONENT, "Erreur shutdown H2: " + e.getMessage());
            }
        }
    }

    private void initSchema() throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             Statement s = c.createStatement()) {

            // CATEGORIES
            s.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id         INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    name       VARCHAR(100) NOT NULL UNIQUE,
                    color      VARCHAR(20)  DEFAULT '#6366f1',
                    icon       VARCHAR(50)  DEFAULT 'film',
                    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // VIDEOS
            s.execute("""
                CREATE TABLE IF NOT EXISTS videos (
                    id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    title            VARCHAR(255) NOT NULL UNIQUE,
                    file_path        VARCHAR(1024),
                    host             VARCHAR(255),
                    port             INTEGER,
                    file_size        BIGINT       DEFAULT 0,
                    duration_sec     INTEGER      DEFAULT 0,
                    resolution       VARCHAR(20),
                    codec            VARCHAR(50),
                    fps              FLOAT        DEFAULT 0,
                    bitrate_kbps     INTEGER      DEFAULT 0,
                    quality_label    VARCHAR(20),
                    synopsis         CLOB,
                    category_id      INTEGER      REFERENCES categories(id) ON DELETE SET NULL,
                    tags             VARCHAR(500),
                    view_count       INTEGER      DEFAULT 0,
                    is_active        BOOLEAN      DEFAULT TRUE,
                    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    last_streamed_at TIMESTAMP
                )
            """);

            // VIEW_EVENTS
            s.execute("""
                CREATE TABLE IF NOT EXISTS view_events (
                    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    video_id      INTEGER NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
                    client_ip_hash VARCHAR(64),
                    bytes_served  BIGINT  DEFAULT 0,
                    viewed_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // SCHEMA_VERSION for future migrations
            s.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version    INTEGER PRIMARY KEY,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // APP SETTINGS (runtime config managed by admin)
            s.execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    setting_key   VARCHAR(120) PRIMARY KEY,
                    setting_value VARCHAR(1000) NOT NULL,
                    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // SUBSCRIPTION PLANS (prices and durations)
            s.execute("""
                CREATE TABLE IF NOT EXISTS subscription_plans (
                    plan          VARCHAR(30) PRIMARY KEY,
                    price         DECIMAL(10,2) NOT NULL,
                    duration_days INTEGER      NOT NULL,
                    currency      VARCHAR(10)  DEFAULT 'USD',
                    is_active     BOOLEAN      DEFAULT TRUE,
                    updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """);

            seedDefaultCategories(c);
            seedDefaultSettings(c);

            // USERS
            s.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    email         VARCHAR(255) NOT NULL UNIQUE,
                    username      VARCHAR(100) NOT NULL,
                    password_hash VARCHAR(512) NOT NULL,
                    password_salt VARCHAR(128) NOT NULL,
                    role          VARCHAR(20)  DEFAULT 'user',
                    is_active     BOOLEAN      DEFAULT TRUE,
                    avatar_color  VARCHAR(20)  DEFAULT '#38bdf8',
                    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    last_login_at TIMESTAMP
                )
            """);

            // SUBSCRIPTIONS
            s.execute("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    plan        VARCHAR(30)  NOT NULL DEFAULT 'trial',
                    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
                    starts_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    ends_at     TIMESTAMP,
                    trial_used  BOOLEAN      DEFAULT FALSE,
                    notes       VARCHAR(500),
                    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // PAYMENTS (cash gérés par admin)
            s.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id        INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    amount         DECIMAL(10,2) NOT NULL,
                    currency       VARCHAR(10)   DEFAULT 'USD',
                    plan           VARCHAR(30)   NOT NULL,
                    duration_days  INTEGER       DEFAULT 30,
                    status         VARCHAR(20)   DEFAULT 'pending',
                    payment_method VARCHAR(50)   DEFAULT 'cash',
                    proof_note     VARCHAR(1000),
                    admin_note     VARCHAR(500),
                    approved_by    VARCHAR(100),
                    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                    processed_at   TIMESTAMP
                )
            """);

            // DOWNLOAD_TOKENS (tokens à usage unique)
            s.execute("""
                CREATE TABLE IF NOT EXISTS download_tokens (
                    token      VARCHAR(64) PRIMARY KEY,
                    user_id    INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    video_id   INTEGER     NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
                    expires_at TIMESTAMP   NOT NULL,
                    used       BOOLEAN     DEFAULT FALSE,
                    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Migrate videos: add is_free + download_count if missing (safe ALTER)
            try { s.execute("ALTER TABLE videos ADD COLUMN is_free BOOLEAN DEFAULT FALSE"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE videos ADD COLUMN download_count INTEGER DEFAULT 0"); }
            catch (SQLException ignored) {}

            // Mark schema v2
            s.execute("MERGE INTO schema_version (version) KEY(version) VALUES (1)");
            s.execute("MERGE INTO schema_version (version) KEY(version) VALUES (2)");
            s.execute("MERGE INTO schema_version (version) KEY(version) VALUES (3)");
            seedDefaultPlans(c);
            AppLogger.info(COMPONENT, "Schéma v3 OK (settings, plans, users, subscriptions, payments, download_tokens)");
        }
    }

    private void seedDefaultPlans(Connection c) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM subscription_plans";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }

        AppConfig cfg = AppConfig.get();
        String[][] defaults = {
            {"monthly", String.valueOf(cfg.getPlanMonthlyPrice()), String.valueOf(cfg.getPlanMonthlyDays()), cfg.getPlanCurrency(), "true"},
            {"annual",  String.valueOf(cfg.getPlanAnnualPrice()),  String.valueOf(cfg.getPlanAnnualDays()),  cfg.getPlanCurrency(), "true"},
            {"trial",   "0.0",                                     String.valueOf(cfg.getPlanTrialDays()),   cfg.getPlanCurrency(), "true"},
            {"free",    "0.0",                                     "-1",                                     cfg.getPlanCurrency(), "true"}
        };

        String merge = "MERGE INTO subscription_plans (plan, price, duration_days, currency, is_active) KEY(plan) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(merge)) {
            for (String[] p : defaults) {
                ps.setString(1, p[0]);
                ps.setDouble(2, Double.parseDouble(p[1]));
                ps.setInt(3, Integer.parseInt(p[2]));
                ps.setString(4, p[3]);
                ps.setBoolean(5, Boolean.parseBoolean(p[4]));
                ps.executeUpdate();
            }
        }
    }

    private void seedDefaultSettings(Connection c) throws SQLException {
        AppConfig cfg = AppConfig.get();
        String[][] defaults = {
            {"streaming.max.connections.per.ip", String.valueOf(cfg.getMaxConnectionsPerIp())},
            {"streaming.max.concurrent.clients", String.valueOf(cfg.getMaxConcurrentClients())},
            {"plan.currency", cfg.getPlanCurrency()}
        };

        try (PreparedStatement ps = c.prepareStatement(
            "MERGE INTO app_settings (setting_key, setting_value) KEY(setting_key) VALUES (?, ?)"
        )) {
            for (String[] kv : defaults) {
                ps.setString(1, kv[0]);
                ps.setString(2, kv[1]);
                ps.executeUpdate();
            }
        }
    }

    private void seedDefaultCategories(Connection c) throws SQLException {
        String[][] defaults = {
            {"Action", "#ef4444", "zap"},
            {"Comedie", "#f59e0b", "smile"},
            {"Drame", "#8b5cf6", "heart"},
            {"Documentaire", "#3b82f6", "book-open"},
            {"Animation", "#10b981", "sparkles"},
            {"Science-Fiction", "#06b6d4", "rocket"},
            {"Horreur", "#6b7280", "skull"},
            {"Thriller", "#f43f5e", "flame"},
            {"Romance", "#ec4899", "heart"},
            {"Famille", "#22c55e", "users"},
            {"Sport", "#14b8a6", "activity"},
            {"Autres", "#9ca3af", "more-horizontal"}
        };

        int inserted = 0;
        String sql = """
            INSERT INTO categories (name, color, icon)
            SELECT ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ?)
        """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String[] cat : defaults) {
                ps.setString(1, cat[0]);
                ps.setString(2, cat[1]);
                ps.setString(3, cat[2]);
                ps.setString(4, cat[0]);
                inserted += ps.executeUpdate();
            }
        }

        if (inserted > 0) {
            AppLogger.info(COMPONENT, "Categories par defaut completees: +" + inserted);
        }
    }
}
