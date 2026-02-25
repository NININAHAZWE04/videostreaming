package diary;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Informations de publication d'une vidéo dans le Diary.
 */
public final class VideoInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String title;
    private final String host;
    private final int port;

    public VideoInfo(String title, String host, int port) {
        this.title = Objects.requireNonNull(title, "title");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
    }

    public String getTitle() {
        return title;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return title + " (" + host + ":" + port + ")";
    }
}
