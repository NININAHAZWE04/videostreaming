package server.api;

/**
 * Constructeur JSON léger sans dépendance externe.
 */
public final class JsonBuilder {

    private final StringBuilder sb = new StringBuilder();
    private boolean first = true;

    public static JsonBuilder obj() {
        JsonBuilder jb = new JsonBuilder();
        jb.sb.append('{');
        return jb;
    }

    public static String arr(java.util.List<?> items, java.util.function.Function<Object, String> mapper) {
        StringBuilder ab = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) ab.append(',');
            @SuppressWarnings("unchecked")
            String val = mapper.apply((Object) items.get(i));
            ab.append(val);
        }
        ab.append(']');
        return ab.toString();
    }

    public JsonBuilder put(String key, String value) {
        comma();
        sb.append('"').append(esc(key)).append("\":\"").append(esc(value)).append('"');
        return this;
    }

    public JsonBuilder putRaw(String key, String rawValue) {
        comma();
        sb.append('"').append(esc(key)).append("\":").append(rawValue);
        return this;
    }

    public JsonBuilder put(String key, long value) {
        comma();
        sb.append('"').append(esc(key)).append("\":").append(value);
        return this;
    }

    public JsonBuilder put(String key, double value) {
        comma();
        sb.append('"').append(esc(key)).append("\":").append(String.format("%.2f", value));
        return this;
    }

    public JsonBuilder put(String key, boolean value) {
        comma();
        sb.append('"').append(esc(key)).append("\":").append(value);
        return this;
    }

    public JsonBuilder putNullable(String key, String value) {
        comma();
        sb.append('"').append(esc(key)).append("\":");
        if (value == null) sb.append("null");
        else sb.append('"').append(esc(value)).append('"');
        return this;
    }

    public String build() {
        return sb.toString() + '}';
    }

    private void comma() {
        if (!first) sb.append(',');
        first = false;
    }

    public static String esc(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** Sérialise un VideoMetadata en JSON complet */
    public static String videoToJson(db.VideoMetadata vm) {
        return obj()
            .put("id",              vm.getId())
            .put("title",           vm.getTitle())
            .putNullable("filePath",     vm.getFilePath())
            .putNullable("host",         vm.getHost())
            .put("port",            vm.getPort())
            .put("fileSize",        vm.getFileSize())
            .put("durationSec",     vm.getDurationSec())
            .put("formattedDuration", vm.getFormattedDuration())
            .put("formattedSize",   vm.getFormattedSize())
            .putNullable("resolution",   vm.getResolution())
            .putNullable("codec",        vm.getCodec())
            .put("fps",             vm.getFps())
            .put("bitrateKbps",     vm.getBitrateKbps())
            .putNullable("qualityLabel", vm.getQualityLabel())
            .putNullable("synopsis",     vm.getSynopsis())
            .put("categoryId",      vm.getCategoryId())
            .putNullable("categoryName", vm.getCategoryName())
            .putNullable("categoryColor",vm.getCategoryColor())
            .putNullable("tags",         vm.getTags())
            .put("viewCount",       vm.getViewCount())
            .put("downloadCount",   vm.getDownloadCount())
            .put("free",            vm.isFree())
            .put("active",          vm.isActive())
            .putNullable("streamUrl",    vm.getStreamUrl())
            .putNullable("thumbnailUrl", vm.getThumbnailUrl())
            .build();
    }

    /** Sérialise une VideoInfo en JSON */
    public static String videoInfoToJson(diary.VideoInfo v) {
        return obj()
            .put("title",           v.getTitle())
            .put("host",            v.getHost())
            .put("port",            v.getPort())
            .put("url",             v.getStreamUrl())
            .put("thumbnailUrl",    v.getThumbnailUrl())
            .putNullable("formattedDuration", v.getFormattedDuration())
            .putNullable("formattedSize",     v.getFormattedSize())
            .putNullable("resolution",        v.getResolution())
            .putNullable("qualityLabel",      v.getQualityLabel())
            .putNullable("codec",             v.getCodec())
            .put("fps",             v.getFps())
            .putNullable("synopsis",          v.getSynopsis())
            .putNullable("categoryName",      v.getCategoryName())
            .putNullable("categoryColor",     v.getCategoryColor())
            .putNullable("tags",              v.getTags())
            .put("viewCount",       v.getViewCount())
            .put("databaseId",      v.getDatabaseId())
            .build();
    }
}
