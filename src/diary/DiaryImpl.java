package diary;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation du service Diary.
 */
public class DiaryImpl extends UnicastRemoteObject implements Diary {
    private static final long serialVersionUID = 1L;

    private final Map<String, VideoInfo> videos;

    public DiaryImpl() throws RemoteException {
        super();
        this.videos = new ConcurrentHashMap<>();
    }

    @Override
    public void registerVideo(String title, String host, int port) throws RemoteException {
        String normalizedTitle = normalizeTitle(title);
        validateHost(host);
        validatePort(port);

        VideoInfo videoInfo = new VideoInfo(normalizedTitle, host.trim(), port);
        VideoInfo previous = videos.put(normalizedTitle, videoInfo);

        if (previous == null) {
            System.out.println("[Diary] Vidéo enregistrée: " + normalizedTitle + " sur " + host + ":" + port);
        } else {
            System.out.println("[Diary] Vidéo mise à jour: " + normalizedTitle + " sur " + host + ":" + port);
        }
    }

    @Override
    public void unregisterVideo(String title) throws RemoteException {
        String normalizedTitle = normalizeTitle(title);
        VideoInfo removed = videos.remove(normalizedTitle);
        if (removed != null) {
            System.out.println("[Diary] Vidéo supprimée: " + normalizedTitle);
        }
    }

    @Override
    public VideoInfo getVideoInfo(String title) throws RemoteException {
        String normalizedTitle = normalizeTitle(title);
        return videos.get(normalizedTitle);
    }

    @Override
    public List<VideoInfo> listAllVideos() throws RemoteException {
        List<VideoInfo> list = new ArrayList<>(videos.values());
        list.sort(Comparator.comparing(VideoInfo::getTitle, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("Le titre de la vidéo ne peut pas être null");
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Le titre de la vidéo ne peut pas être vide");
        }
        return trimmed;
    }

    private static void validateHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("L'hôte de streaming ne peut pas être vide");
        }
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Le port de streaming doit être compris entre 1 et 65535");
        }
    }
}
