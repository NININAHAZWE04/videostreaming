package diary;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Informations de publication d'une vidéo dans le Diary.
 * Enrichi avec les métadonnées complètes pour l'API et les clients.
 */
public final class VideoInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    private final String title;
    private final String host;
    private final int port;

    // Métadonnées enrichies (optionnelles)
    private final String formattedDuration;
    private final String formattedSize;
    private final String resolution;
    private final String qualityLabel;
    private final String codec;
    private final float fps;
    private final String synopsis;
    private final String categoryName;
    private final String categoryColor;
    private final String tags;
    private final int viewCount;
    private final int databaseId;

    /** Constructeur minimal — rétrocompatibilité */
    public VideoInfo(String title, String host, int port) {
        this(title, host, port, null, null, null, null, null, 0, null, null, null, null, 0, -1);
    }

    /** Constructeur complet avec toutes les métadonnées */
    public VideoInfo(String title, String host, int port,
                     String formattedDuration, String formattedSize,
                     String resolution, String qualityLabel, String codec,
                     float fps, String synopsis, String categoryName,
                     String categoryColor, String tags, int viewCount, int databaseId) {
        this.title         = Objects.requireNonNull(title, "title");
        this.host          = Objects.requireNonNull(host, "host");
        this.port          = port;
        this.formattedDuration = formattedDuration;
        this.formattedSize = formattedSize;
        this.resolution    = resolution;
        this.qualityLabel  = qualityLabel;
        this.codec         = codec;
        this.fps           = fps;
        this.synopsis      = synopsis;
        this.categoryName  = categoryName;
        this.categoryColor = categoryColor;
        this.tags          = tags;
        this.viewCount     = viewCount;
        this.databaseId    = databaseId;
    }

    public String getTitle()             { return title; }
    public String getHost()              { return host; }
    public int    getPort()              { return port; }
    public String getFormattedDuration() { return formattedDuration; }
    public String getFormattedSize()     { return formattedSize; }
    public String getResolution()        { return resolution; }
    public String getQualityLabel()      { return qualityLabel; }
    public String getCodec()             { return codec; }
    public float  getFps()               { return fps; }
    public String getSynopsis()          { return synopsis; }
    public String getCategoryName()      { return categoryName; }
    public String getCategoryColor()     { return categoryColor; }
    public String getTags()              { return tags; }
    public int    getViewCount()         { return viewCount; }
    public int    getDatabaseId()        { return databaseId; }
    public String getStreamUrl()         { return "http://" + host + ":" + port; }
    public String getThumbnailUrl()      { return "http://" + host + ":" + port + "/thumbnail"; }

    @Override
    public String toString() {
        String q = qualityLabel != null ? " [" + qualityLabel + "]" : "";
        return title + q + " (" + host + ":" + port + ")";
    }
}
