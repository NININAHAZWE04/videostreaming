package server.sse;

import common.AppLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bus d'événements SSE (Server-Sent Events).
 * Remplace le polling toutes les 5s par un push temps réel.
 *
 * Événements émis :
 *  - video_added    : une vidéo a été ajoutée en base
 *  - video_removed  : une vidéo supprimée
 *  - stream_started : un stream HTTP a démarré
 *  - stream_stopped : un stream arrêté
 *  - stats_update   : stats dashboard rafraîchies
 *  - log_entry      : ligne de log (pour le terminal admin)
 */
public final class SseEventBus {

    private static final SseEventBus INSTANCE = new SseEventBus();
    private static final String LOG = "SseEventBus";

    private final List<SseClient> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private SseEventBus() {
        // Send keepalive comment every 20s to prevent proxy timeouts
        heartbeat.scheduleAtFixedRate(() -> broadcast("comment", ":keepalive"), 20, 20, TimeUnit.SECONDS);
    }

    public static SseEventBus get() { return INSTANCE; }

    /** Enregistre un client SSE connecté */
    public void addClient(SseClient client) {
        clients.add(client);
        AppLogger.info(LOG, "Client SSE connecté. Total: " + clients.size());
    }

    /** Retire un client déconnecté */
    public void removeClient(SseClient client) {
        clients.remove(client);
        AppLogger.info(LOG, "Client SSE déconnecté. Total: " + clients.size());
    }

    public int getClientCount() { return clients.size(); }

    /** Publie un événement à tous les clients connectés */
    public void publish(String eventType, String jsonData) {
        broadcast(eventType, jsonData);
    }

    // Convenience methods
    public void publishVideoAdded(int videoId, String title)   { publish("video_added",    "{\"id\":" + videoId + ",\"title\":\"" + esc(title) + "\"}"); }
    public void publishVideoRemoved(int videoId, String title) { publish("video_removed",  "{\"id\":" + videoId + ",\"title\":\"" + esc(title) + "\"}"); }
    public void publishStreamStarted(String title, String url) { publish("stream_started", "{\"title\":\"" + esc(title) + "\",\"url\":\"" + esc(url) + "\"}"); }
    public void publishStreamStopped(String title)             { publish("stream_stopped", "{\"title\":\"" + esc(title) + "\"}"); }
    public void publishStatsUpdate(String statsJson)           { publish("stats_update",   statsJson); }
    public void publishLogEntry(String logJson)                { publish("log_entry",      logJson); }

    private void broadcast(String eventType, String data) {
        if (clients.isEmpty()) return;
        String msg = "event: " + eventType + "\ndata: " + data + "\n\n";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);

        clients.removeIf(client -> {
            try {
                client.send(bytes);
                return false; // keep
            } catch (IOException e) {
                client.close();
                return true; // remove
            }
        });
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Représente une connexion SSE individuelle */
    public static final class SseClient {
        private final OutputStream out;
        private volatile boolean closed = false;

        public SseClient(OutputStream out) {
            this.out = out;
        }

        public synchronized void send(byte[] data) throws IOException {
            if (closed) throw new IOException("client closed");
            out.write(data);
            out.flush();
        }

        public void close() {
            closed = true;
            try { out.close(); } catch (IOException ignored) {}
        }

        public boolean isClosed() { return closed; }
    }
}
