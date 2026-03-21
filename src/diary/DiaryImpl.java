package diary;

import common.AppLogger;
import db.DatabaseManager;
import db.VideoMetadata;
import db.VideoRepository;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implémentation du service Diary avec persistance H2.
 * Remplace le ConcurrentHashMap en mémoire par une vraie base de données.
 */
public class DiaryImpl extends UnicastRemoteObject implements Diary {
    private static final long serialVersionUID = 2L;
    private static final String LOG = "DiaryImpl";

    private final VideoRepository repo;

    public DiaryImpl() throws RemoteException {
        super();
        // Initialize DB — creates schema on first run
        DatabaseManager.getInstance();
        this.repo = new VideoRepository();
        AppLogger.info(LOG, "DiaryImpl initialisé avec persistance H2");
    }

    @Override
    public void registerVideo(String title, String host, int port) throws RemoteException {
        String t = normalizeTitle(title);
        validateHost(host);
        validatePort(port);

        boolean exists = repo.findByTitle(t).isPresent();
        repo.updateStreamInfo(t, host.trim(), port);
        AppLogger.info(LOG, (exists ? "Vidéo mise à jour: " : "Vidéo enregistrée: ")
            + t + " → " + host + ":" + port);
    }

    @Override
    public void unregisterVideo(String title) throws RemoteException {
        String t = normalizeTitle(title);
        repo.markStreamStopped(t);
        AppLogger.info(LOG, "Stream arrêté: " + t);
    }

    @Override
    public VideoInfo getVideoInfo(String title) throws RemoteException {
        return repo.findByTitle(normalizeTitle(title))
                   .map(DiaryImpl::toVideoInfo)
                   .orElse(null);
    }

    @Override
    public List<VideoInfo> listAllVideos() throws RemoteException {
        List<VideoInfo> list = new ArrayList<>();
        for (VideoMetadata vm : repo.findActive()) {
            list.add(toVideoInfo(vm));
        }
        list.sort(Comparator.comparing(VideoInfo::getTitle, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Construit un VideoInfo enrichi depuis un VideoMetadata H2 */
    public static VideoInfo toVideoInfo(VideoMetadata vm) {
        return new VideoInfo(
            vm.getTitle(),
            vm.getHost() != null ? vm.getHost() : "localhost",
            vm.getPort(),
            vm.getFormattedDuration(),
            vm.getFormattedSize(),
            vm.getResolution(),
            vm.getQualityLabel(),
            vm.getCodec(),
            vm.getFps(),
            vm.getSynopsis(),
            vm.getCategoryName(),
            vm.getCategoryColor(),
            vm.getTags(),
            vm.getViewCount(),
            vm.getId()
        );
    }

    private static String normalizeTitle(String title) {
        if (title == null) throw new IllegalArgumentException("Le titre ne peut pas être null");
        String t = title.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Le titre ne peut pas être vide");
        return t;
    }

    private static void validateHost(String host) {
        if (host == null || host.trim().isEmpty())
            throw new IllegalArgumentException("L'hôte de streaming ne peut pas être vide");
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65_535)
            throw new IllegalArgumentException("Port invalide: " + port);
    }
}
