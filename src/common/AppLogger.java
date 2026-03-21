package common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Logger structuré simple. Remplace les System.out.println dispersés.
 * Conserve les dernières 500 lignes en mémoire pour les SSE logs.
 */
public final class AppLogger {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_HISTORY = 500;
    private static final ConcurrentLinkedDeque<LogEntry> history = new ConcurrentLinkedDeque<>();

    private static volatile Level minLevel = Level.INFO;
    private static volatile LogListener listener = null;

    public record LogEntry(String timestamp, String level, String component, String message) {
        public String toLine() {
            return "[" + timestamp + "][" + level + "][" + component + "] " + message;
        }
        public String toJson() {
            return "{\"ts\":\"" + esc(timestamp) + "\",\"level\":\"" + level
                + "\",\"component\":\"" + esc(component) + "\",\"message\":\"" + esc(message) + "\"}";
        }
        private static String esc(String v) {
            return v == null ? "" : v.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
        }
    }

    @FunctionalInterface
    public interface LogListener {
        void onLog(LogEntry entry);
    }

    private AppLogger() {}

    public static void setLevel(Level level) { minLevel = level; }
    public static void setListener(LogListener l) { listener = l; }

    public static void debug(String component, String msg) { log(Level.DEBUG, component, msg); }
    public static void info(String component, String msg)  { log(Level.INFO,  component, msg); }
    public static void warn(String component, String msg)  { log(Level.WARN,  component, msg); }
    public static void error(String component, String msg) { log(Level.ERROR, component, msg); }

    private static void log(Level level, String component, String message) {
        if (level.ordinal() < minLevel.ordinal()) return;
        String ts = LocalDateTime.now().format(FMT);
        LogEntry entry = new LogEntry(ts, level.name(), component, message);
        String line = entry.toLine();

        if (level == Level.ERROR) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }

        history.addLast(entry);
        if (history.size() > MAX_HISTORY) history.pollFirst();

        LogListener l = listener;
        if (l != null) {
            try { l.onLog(entry); } catch (Exception ignored) {}
        }
    }

    public static java.util.List<LogEntry> getHistory() {
        return new java.util.ArrayList<>(history);
    }

    public static java.util.List<LogEntry> getHistoryAfter(int skip) {
        java.util.List<LogEntry> all = getHistory();
        if (skip >= all.size()) return java.util.List.of();
        return all.subList(skip, all.size());
    }
}
