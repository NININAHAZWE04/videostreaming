package db;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Modèle complet d'une vidéo avec toutes les métadonnées.
 * Utilisé par la couche DB et l'API REST.
 */
public final class VideoMetadata implements Serializable {
    @Serial private static final long serialVersionUID = 2L;

    private int id;
    private String title;
    private String filePath;
    private String host;
    private int port;
    private long fileSize;         // bytes
    private int durationSec;       // secondes
    private String resolution;     // ex: "1920x1080"
    private String codec;          // ex: "h264"
    private float fps;
    private int bitrateKbps;
    private String qualityLabel;   // "480p","720p","1080p","4K"
    private String synopsis;
    private int categoryId;
    private String categoryName;
    private String categoryColor;
    private String tags;
    private int viewCount;
    private boolean active;
    private boolean free;           // accès gratuit sans abonnement
    private int downloadCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastStreamedAt;
    private String thumbnailUrl;

    public VideoMetadata() {}

    // ─── Getters / Setters ─────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public int getDurationSec() { return durationSec; }
    public void setDurationSec(int durationSec) { this.durationSec = durationSec; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }

    public float getFps() { return fps; }
    public void setFps(float fps) { this.fps = fps; }

    public int getBitrateKbps() { return bitrateKbps; }
    public void setBitrateKbps(int bitrateKbps) { this.bitrateKbps = bitrateKbps; }

    public String getQualityLabel() { return qualityLabel; }
    public void setQualityLabel(String qualityLabel) { this.qualityLabel = qualityLabel; }

    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryColor() { return categoryColor; }
    public void setCategoryColor(String categoryColor) { this.categoryColor = categoryColor; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastStreamedAt() { return lastStreamedAt; }
    public void setLastStreamedAt(LocalDateTime lastStreamedAt) { this.lastStreamedAt = lastStreamedAt; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    // ─── Computed helpers ──────────────────────────────────────────────────

    /** Durée formatée ex: "1:32:45" ou "45:12" */
    public String getFormattedDuration() {
        if (durationSec <= 0) return "--:--";
        int h = durationSec / 3600;
        int m = (durationSec % 3600) / 60;
        int s = durationSec % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    /** Taille formatée ex: "1.4 Go", "842 Mo" */
    public String getFormattedSize() {
        if (fileSize <= 0) return "—";
        if (fileSize >= 1_073_741_824L) return String.format("%.1f Go", fileSize / 1_073_741_824.0);
        if (fileSize >= 1_048_576L)     return String.format("%.0f Mo", fileSize / 1_048_576.0);
        if (fileSize >= 1024L)          return String.format("%.0f Ko", fileSize / 1024.0);
        return fileSize + " o";
    }

    /** Déduit qualityLabel depuis la résolution */
    public static String deriveQualityLabel(String resolution) {
        if (resolution == null || resolution.isBlank()) return null;
        String[] parts = resolution.split("[x×]");
        if (parts.length < 2) return null;
        try {
            int height = Integer.parseInt(parts[1].trim());
            if (height >= 2160) return "4K";
            if (height >= 1080) return "1080p";
            if (height >= 720)  return "720p";
            if (height >= 480)  return "480p";
            return "SD";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getStreamUrl() {
        if (host == null || port <= 0) return null;
        return "http://" + host + ":" + port;
    }
}
