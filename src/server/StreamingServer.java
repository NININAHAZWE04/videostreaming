package server;

import diary.Diary;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serveur de streaming d'un fichier vidéo via HTTP (avec support des requêtes Range).
 *
 * Endpoints:
 * - / ou /stream: flux vidéo
 * - /thumbnail: image JPEG extraite du film (nécessite ffmpeg)
 */
public class StreamingServer {
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)", Pattern.CASE_INSENSITIVE);
    private static final int BUFFER_SIZE = 8192;

    private final File videoFile;
    private final String videoTitle;
    private final int streamingPort;
    private final String diaryHost;
    private final int diaryPort;
    private final String streamingHost;
    private final Object thumbnailLock = new Object();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService pool;

    public StreamingServer(
        File videoFile,
        String videoTitle,
        int streamingPort,
        String diaryHost,
        int diaryPort,
        String streamingHost
    ) {
        this.videoFile = videoFile;
        this.videoTitle = normalizeNonEmpty(videoTitle, "videoTitle");
        this.streamingPort = validatePort(streamingPort, "streamingPort");
        this.diaryHost = normalizeNonEmpty(diaryHost, "diaryHost");
        this.diaryPort = validatePort(diaryPort, "diaryPort");
        this.streamingHost = normalizeNonEmpty(streamingHost, "streamingHost");
    }

    public synchronized void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Le serveur est déjà en cours d'exécution");
        }
        validateVideoFile();

        registerInDiary();

        running = true;
        pool = Executors.newCachedThreadPool();

        Thread serverThread = new Thread(this::acceptLoop, "streaming-server-" + streamingPort);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        try {
            unregisterFromDiary();
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de la fermeture du socket serveur: " + e.getMessage());
        } finally {
            stopPool();
            System.out.println("[StreamingServer] Serveur arrêté");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public int getStreamingPort() {
        return streamingPort;
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(streamingPort);
            System.out.println("[StreamingServer] En écoute sur le port " + streamingPort + " pour: " + videoTitle);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    pool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Erreur lors de l'acceptation du client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Erreur lors du démarrage du serveur HTTP: " + e.getMessage());
            }
        } finally {
            stopPool();
        }
    }

    private void handleClient(Socket clientSocket) {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        System.out.println("[StreamingServer] Client connecté: " + clientIp);

        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream out = socket.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                sendError(out, 400, "Bad Request");
                return;
            }

            String[] requestTokens = requestLine.split("\\s+");
            if (requestTokens.length < 3) {
                sendError(out, 400, "Bad Request");
                return;
            }

            String method = requestTokens[0].toUpperCase(Locale.ROOT);
            if (!"GET".equals(method)) {
                sendError(out, 405, "Method Not Allowed");
                return;
            }

            String path = extractPath(requestTokens[1]);

            String rangeHeader = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx <= 0) {
                    continue;
                }
                String headerName = line.substring(0, idx).trim();
                String headerValue = line.substring(idx + 1).trim();
                if ("range".equalsIgnoreCase(headerName)) {
                    rangeHeader = headerValue;
                }
            }

            if ("/thumbnail".equals(path)) {
                serveThumbnail(out);
                return;
            }

            if (!"/".equals(path) && !"/stream".equals(path)) {
                sendError(out, 404, "Not Found");
                return;
            }

            long fileLength = videoFile.length();
            Range range = resolveRange(rangeHeader, fileLength);
            if (range == null) {
                sendRangeNotSatisfiable(out, fileLength);
                return;
            }

            int status = range.isPartial ? 206 : 200;
            out.write(buildHttpHeader(status, fileLength, range.start, range.end).getBytes(StandardCharsets.US_ASCII));
            streamFile(out, range.start, range.end);

            System.out.println("[StreamingServer] Streaming terminé pour " + clientIp + " (" + range.start + "-" + range.end + ")");
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Connection reset by peer") || msg.contains("Broken pipe"))) {
                System.out.println("[StreamingServer] Le client s'est déconnecté (seek/changement de flux).");
            } else {
                System.err.println("Erreur lors du streaming: " + e.getMessage());
            }
        }
    }

    private String extractPath(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return "/";
        }
        String target = rawTarget.trim();
        if (target.startsWith("http://") || target.startsWith("https://")) {
            int slashIdx = target.indexOf('/', target.indexOf("://") + 3);
            target = slashIdx >= 0 ? target.substring(slashIdx) : "/";
        }
        int qIdx = target.indexOf('?');
        if (qIdx >= 0) {
            target = target.substring(0, qIdx);
        }
        if (target.isEmpty()) {
            target = "/";
        }
        return target;
    }

    private void serveThumbnail(OutputStream out) throws IOException {
        File thumbnailFile = ensureThumbnailFile();
        if (thumbnailFile == null || !thumbnailFile.exists() || thumbnailFile.length() == 0) {
            sendError(out, 503, "Thumbnail Unavailable (ffmpeg requis)");
            return;
        }

        String header = "HTTP/1.1 200 OK\r\n"
            + "Content-Type: image/jpeg\r\n"
            + "Cache-Control: public, max-age=300\r\n"
            + "Connection: close\r\n"
            + "Content-Length: " + thumbnailFile.length() + "\r\n\r\n";

        out.write(header.getBytes(StandardCharsets.US_ASCII));

        try (FileInputStream fis = new FileInputStream(thumbnailFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private File ensureThumbnailFile() {
        synchronized (thumbnailLock) {
            File cacheFile = thumbnailCacheFile();

            if (cacheFile.exists() && cacheFile.length() > 0 && cacheFile.lastModified() >= videoFile.lastModified()) {
                return cacheFile;
            }

            File cacheDir = cacheFile.getParentFile();
            if (cacheDir != null && !cacheDir.exists() && !cacheDir.mkdirs()) {
                System.err.println("[StreamingServer] Impossible de créer le dossier thumbnail: " + cacheDir);
                return null;
            }

            boolean extracted = extractFrameWithFfmpeg(cacheFile, "00:00:10")
                || extractFrameWithFfmpeg(cacheFile, "00:00:01");

            if (!extracted) {
                return null;
            }

            return cacheFile;
        }
    }

    private boolean extractFrameWithFfmpeg(File outputFile, String seekTime) {
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-ss",
            seekTime,
            "-i",
            videoFile.getAbsolutePath(),
            "-frames:v",
            "1",
            "-q:v",
            "3",
            outputFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            String ffmpegOut;
            try (InputStream processOut = process.getInputStream()) {
                ffmpegOut = new String(processOut.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.err.println("[StreamingServer] Timeout ffmpeg pour thumbnail");
                return false;
            }

            if (process.exitValue() != 0) {
                if (!ffmpegOut.isBlank()) {
                    System.err.println("[StreamingServer] ffmpeg: " + ffmpegOut.trim());
                }
                return false;
            }

            return outputFile.exists() && outputFile.length() > 0;
        } catch (IOException e) {
            System.err.println("[StreamingServer] ffmpeg indisponible: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private File thumbnailCacheFile() {
        String safeName = videoTitle.replaceAll("[^a-zA-Z0-9._-]", "_");
        String signature = Integer.toHexString((videoFile.getAbsolutePath() + "|" + videoFile.length() + "|" + videoFile.lastModified()).hashCode());
        File cacheDir = new File(System.getProperty("java.io.tmpdir"), "videostreaming-thumbnails");
        return new File(cacheDir, safeName + "-" + signature + ".jpg");
    }

    private void validateVideoFile() {
        if (videoFile == null) {
            throw new IllegalArgumentException("Aucun fichier vidéo fourni");
        }
        if (!videoFile.exists() || !videoFile.isFile()) {
            throw new IllegalArgumentException("Le fichier vidéo n'existe pas: " + videoFile);
        }
        if (!videoFile.canRead()) {
            throw new IllegalArgumentException("Le fichier vidéo est illisible: " + videoFile);
        }
    }

    private static String normalizeNonEmpty(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " ne peut pas être vide");
        }
        return trimmed;
    }

    private static int validatePort(int port, String fieldName) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(fieldName + " doit être compris entre 1 et 65535");
        }
        return port;
    }

    private void stopPool() {
        if (pool == null || pool.isShutdown()) {
            return;
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Range resolveRange(String headerValue, long fileLength) {
        if (headerValue == null || headerValue.isBlank()) {
            return new Range(0, fileLength - 1, false);
        }

        Matcher matcher = RANGE_PATTERN.matcher(headerValue);
        if (!matcher.find()) {
            return new Range(0, fileLength - 1, false);
        }

        String startStr = matcher.group(1);
        String endStr = matcher.group(2);

        try {
            long start;
            long end;

            if (startStr == null || startStr.isEmpty()) {
                long suffixLength = Long.parseLong(endStr);
                if (suffixLength <= 0) {
                    return null;
                }
                suffixLength = Math.min(suffixLength, fileLength);
                start = fileLength - suffixLength;
                end = fileLength - 1;
            } else {
                start = Long.parseLong(startStr);
                if (start >= fileLength || start < 0) {
                    return null;
                }

                if (endStr == null || endStr.isEmpty()) {
                    end = fileLength - 1;
                } else {
                    end = Long.parseLong(endStr);
                    if (end < start) {
                        return null;
                    }
                    end = Math.min(end, fileLength - 1);
                }
            }

            return new Range(start, end, true);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void streamFile(OutputStream out, long start, long end) throws IOException {
        try (FileInputStream fis = new FileInputStream(videoFile)) {
            fis.skipNBytes(start);

            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = end - start + 1;

            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = fis.read(buffer, 0, toRead);
                if (bytesRead < 0) {
                    break;
                }
                out.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            out.flush();
        }
    }

    private String getContentType() {
        String fileName = videoFile.getName().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (fileName.endsWith(".webm")) {
            return "video/webm";
        }
        if (fileName.endsWith(".ogv")) {
            return "video/ogg";
        }
        if (fileName.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (fileName.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        return "application/octet-stream";
    }

    private String buildHttpHeader(int statusCode, long fileLength, long start, long end) {
        String statusMessage = statusCode == 206 ? "Partial Content" : "OK";
        long contentLength = end - start + 1;

        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(statusCode).append(' ').append(statusMessage).append("\r\n");
        header.append("Content-Type: ").append(getContentType()).append("\r\n");
        header.append("Accept-Ranges: bytes\r\n");
        header.append("Connection: close\r\n");
        header.append("Content-Length: ").append(contentLength).append("\r\n");

        if (statusCode == 206) {
            header.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(fileLength).append("\r\n");
        }

        header.append("\r\n");
        return header.toString();
    }

    private void sendError(OutputStream out, int code, String message) throws IOException {
        String body = code + " " + message + "\n";
        String response = "HTTP/1.1 " + code + " " + message + "\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Connection: close\r\n"
            + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
            + body;
        out.write(response.getBytes(StandardCharsets.UTF_8));
    }

    private void sendRangeNotSatisfiable(OutputStream out, long fileLength) throws IOException {
        String response = "HTTP/1.1 416 Range Not Satisfiable\r\n"
            + "Content-Range: bytes */" + fileLength + "\r\n"
            + "Connection: close\r\n"
            + "Content-Length: 0\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
    }

    private void registerInDiary() throws Exception {
        try {
            Registry registry = LocateRegistry.getRegistry(diaryHost, diaryPort);
            Diary diary = (Diary) registry.lookup("Diary");
            diary.registerVideo(videoTitle, streamingHost, streamingPort);
            System.out.println("[StreamingServer] Vidéo enregistrée dans le Diary: " + videoTitle);
        } catch (RemoteException | NotBoundException e) {
            System.err.println("Impossible de contacter le Diary pour enregistrer la vidéo: " + e.getMessage());
            throw e;
        }
    }

    private void unregisterFromDiary() {
        try {
            Registry registry = LocateRegistry.getRegistry(diaryHost, diaryPort);
            Diary diary = (Diary) registry.lookup("Diary");
            diary.unregisterVideo(videoTitle);
            System.out.println("[StreamingServer] Vidéo désenregistrée du Diary: " + videoTitle);
        } catch (NotBoundException | RemoteException e) {
            System.err.println("Erreur lors du désenregistrement Diary: " + e.getMessage());
        }
    }

    private static final class Range {
        private final long start;
        private final long end;
        private final boolean isPartial;

        private Range(long start, long end, boolean isPartial) {
            this.start = start;
            this.end = end;
            this.isPartial = isPartial;
        }
    }
}
