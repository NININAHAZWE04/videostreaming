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
        props.setProperty("videos.directory", "./videos");

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
    public String getVideosDirectory() { return getString("videos.directory"); }
}
