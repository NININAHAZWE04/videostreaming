package server;

import common.AppLogger;
import db.VideoMetadata;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Extraction automatique des métadonnées vidéo via ffprobe.
 * Durée, résolution, codec, fps, bitrate, qualité — tout automatique.
 */
public final class FfprobeExtractor {

    private static final String COMPONENT = "FfprobeExtractor";
    private static final int TIMEOUT_SECONDS = 30;

    private FfprobeExtractor() {}

    /**
     * Extrait les métadonnées et les applique sur un VideoMetadata existant.
     * @return true si ffprobe a réussi
     */
    public static boolean extract(File videoFile, VideoMetadata vm) {
        if (videoFile == null || !videoFile.exists()) return false;

        // Always set file size from Java (no ffprobe needed)
        vm.setFileSize(videoFile.length());

        // Try ffprobe
        if (!isFfprobeAvailable()) {
            AppLogger.warn(COMPONENT, "ffprobe indisponible — métadonnées limitées pour: " + videoFile.getName());
            return false;
        }

        try {
            String[] cmd = {
                "ffprobe",
                "-v", "quiet",
                "-print_format", "flat",
                "-show_streams",
                "-show_format",
                videoFile.getAbsolutePath()
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            String output;
            try (InputStream is = proc.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                AppLogger.warn(COMPONENT, "ffprobe timeout pour: " + videoFile.getName());
                return false;
            }

            if (proc.exitValue() != 0) {
                AppLogger.warn(COMPONENT, "ffprobe a échoué pour: " + videoFile.getName());
                return false;
            }

            parseOutput(output, vm);
            AppLogger.info(COMPONENT, "Métadonnées extraites: " + vm.getTitle()
                + " | " + vm.getFormattedDuration()
                + " | " + vm.getResolution()
                + " | " + vm.getQualityLabel()
                + " | " + vm.getFormattedSize());
            return true;

        } catch (Exception e) {
            AppLogger.warn(COMPONENT, "Erreur ffprobe: " + e.getMessage());
            return false;
        }
    }

    private static void parseOutput(String output, VideoMetadata vm) {
        // Duration from format section: format.duration="123.456"
        String duration = extractValue(output, "format.duration");
        if (duration != null) {
            try {
                double d = Double.parseDouble(duration.replace("\"", ""));
                vm.setDurationSec((int) Math.round(d));
            } catch (NumberFormatException ignored) {}
        }

        // Bitrate: format.bit_rate="2048000"
        String bitrate = extractValue(output, "format.bit_rate");
        if (bitrate != null) {
            try {
                long bps = Long.parseLong(bitrate.replace("\"", ""));
                vm.setBitrateKbps((int) (bps / 1000));
            } catch (NumberFormatException ignored) {}
        }

        // Video stream — find first video stream index
        String resolution = null;
        String codec = null;
        float fps = 0;

        for (int i = 0; i <= 5; i++) {
            String prefix = "streams.stream." + i;
            String codecType = extractValue(output, prefix + ".codec_type");
            if (codecType == null || !codecType.contains("video")) continue;

            // Codec name
            String codecName = extractValue(output, prefix + ".codec_name");
            if (codecName != null) codec = codecName.replace("\"", "");

            // Resolution
            String width = extractValue(output, prefix + ".width");
            String height = extractValue(output, prefix + ".height");
            if (width != null && height != null) {
                try {
                    int w = Integer.parseInt(width.replace("\"", ""));
                    int h = Integer.parseInt(height.replace("\"", ""));
                    resolution = w + "x" + h;
                } catch (NumberFormatException ignored) {}
            }

            // FPS: r_frame_rate="30/1" or avg_frame_rate="30000/1001"
            String fpsStr = extractValue(output, prefix + ".r_frame_rate");
            if (fpsStr == null) fpsStr = extractValue(output, prefix + ".avg_frame_rate");
            if (fpsStr != null) {
                fps = parseFraction(fpsStr.replace("\"", ""));
            }

            break; // Found video stream
        }

        if (resolution != null) {
            vm.setResolution(resolution);
            String quality = VideoMetadata.deriveQualityLabel(resolution);
            if (quality != null) vm.setQualityLabel(quality);
        }
        if (codec != null) vm.setCodec(codec);
        if (fps > 0) vm.setFps(Math.round(fps * 100f) / 100f);
    }

    private static String extractValue(String output, String key) {
        String search = key + "=";
        int idx = output.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = output.indexOf('\n', start);
        if (end < 0) end = output.length();
        return output.substring(start, end).trim();
    }

    private static float parseFraction(String fraction) {
        // "30/1" or "30000/1001"
        if (fraction == null || fraction.isBlank()) return 0;
        String[] parts = fraction.split("/");
        if (parts.length == 1) {
            try { return Float.parseFloat(parts[0]); } catch (NumberFormatException e) { return 0; }
        }
        if (parts.length == 2) {
            try {
                float num = Float.parseFloat(parts[0]);
                float den = Float.parseFloat(parts[1]);
                return den == 0 ? 0 : num / den;
            } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    public static boolean isFfprobeAvailable() {
        try {
            Process p = new ProcessBuilder("ffprobe", "-version").start();
            p.waitFor(5, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
