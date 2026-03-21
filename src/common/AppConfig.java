package common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration centralisée chargée depuis application.properties.
 * Valeurs par défaut intégrées pour un démarrage sans configuration.
 */
public final class AppConfig {

    private static final AppConfig INSTANCE = new AppConfig();
    private final Properties props = new Properties();

    private AppConfig() {
        // Defaults
        props.setProperty("diary.host", "localhost");
        props.setProperty("diary.port", "12999");
        props.setProperty("api.port", "18080");
        props.setProperty("admin.api.port", "18081");
        props.setProperty("admin.secret", "changeme-admin-secret");
        props.setProperty("db.url", "jdbc:h2:./data/videostreaming;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");
        props.setProperty("db.user", "sa");
        props.setProperty("db.password", "");
        props.setProperty("h2.console.enabled", "true");
        props.setProperty("h2.console.port", "18082");
        props.setProperty("log.level", "INFO");
        props.setProperty("streaming.max.connections.per.ip", "5");
        props.setProperty("streaming.max.concurrent.clients", "150");
        props.setProperty("videos.directory", "./videos");
        props.setProperty("plan.monthly.price", "9.99");
        props.setProperty("plan.monthly.days", "30");
        props.setProperty("plan.annual.price", "79.99");
        props.setProperty("plan.annual.days", "365");
        props.setProperty("plan.trial.days", "14");
        props.setProperty("plan.currency", "USD");

        // Try to load from file
        File configFile = new File("application.properties");
        if (!configFile.exists()) {
            configFile = new File("../application.properties");
        }
        if (configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                props.load(in);
                System.out.println("[AppConfig] Chargé: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[AppConfig] Impossible de charger application.properties: " + e.getMessage());
            }
        }

        // Environment variable overrides (useful in Docker and CI)
        applyEnv("DIARY_HOST", "diary.host");
        applyEnv("DIARY_PORT", "diary.port");
        applyEnv("API_PORT", "api.port");
        applyEnv("ADMIN_API_PORT", "admin.api.port");
        applyEnv("ADMIN_SECRET", "admin.secret");
        applyEnv("JWT_SECRET", "jwt.secret");
        applyEnv("DB_URL", "db.url");
        applyEnv("DB_USER", "db.user");
        applyEnv("DB_PASSWORD", "db.password");
        applyEnv("H2_CONSOLE_ENABLED", "h2.console.enabled");
        applyEnv("H2_CONSOLE_PORT", "h2.console.port");
        applyEnv("LOG_LEVEL", "log.level");
        applyEnv("STREAMING_MAX_CONNECTIONS_PER_IP", "streaming.max.connections.per.ip");
        applyEnv("STREAMING_MAX_CONCURRENT_CLIENTS", "streaming.max.concurrent.clients");
        applyEnv("VIDEOS_DIRECTORY", "videos.directory");
        applyEnv("PLAN_MONTHLY_PRICE", "plan.monthly.price");
        applyEnv("PLAN_MONTHLY_DAYS", "plan.monthly.days");
        applyEnv("PLAN_ANNUAL_PRICE", "plan.annual.price");
        applyEnv("PLAN_ANNUAL_DAYS", "plan.annual.days");
        applyEnv("PLAN_TRIAL_DAYS", "plan.trial.days");
        applyEnv("PLAN_CURRENCY", "plan.currency");
    }

    private void applyEnv(String envKey, String propKey) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) {
            props.setProperty(propKey, v.trim());
        }
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    public String getString(String key) {
        return props.getProperty(key);
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(props.getProperty(key, "0").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Configuration invalide pour la clé '" + key + "': " + props.getProperty(key));
        }
    }

    public boolean getBoolean(String key) {
        return "true".equalsIgnoreCase(props.getProperty(key, "false").trim());
    }

    public double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String getDiaryHost()     { return getString("diary.host"); }
    public int    getDiaryPort()     { return getInt("diary.port"); }
    public int    getApiPort()       { return getInt("api.port"); }
    public int    getAdminApiPort()  { return getInt("admin.api.port"); }
    public String getAdminSecret()   { return getString("admin.secret"); }
    public String getJwtSecret()     {
        String s = getString("jwt.secret");
        // Fallback to admin.secret if jwt.secret not configured
        return (s != null && !s.isBlank()) ? s : getAdminSecret();
    }
    public String getDbUrl()         { return getString("db.url"); }
    public String getDbUser()        { return getString("db.user"); }
    public String getDbPassword()    { return getString("db.password"); }
    public boolean isH2ConsoleEnabled() { return getBoolean("h2.console.enabled"); }
    public int    getH2ConsolePort() { return getInt("h2.console.port"); }
    public int    getMaxConnectionsPerIp() { return getInt("streaming.max.connections.per.ip"); }
    public int    getMaxConcurrentClients() { return getInt("streaming.max.concurrent.clients"); }
    public String getVideosDirectory() { return getString("videos.directory"); }
    public double getPlanMonthlyPrice() { return getDouble("plan.monthly.price", 9.99); }
    public int    getPlanMonthlyDays()  { return getInt("plan.monthly.days"); }
    public double getPlanAnnualPrice()  { return getDouble("plan.annual.price", 79.99); }
    public int    getPlanAnnualDays()   { return getInt("plan.annual.days"); }
    public int    getPlanTrialDays()    { return getInt("plan.trial.days"); }
    public String getPlanCurrency()     { return getString("plan.currency"); }
}
