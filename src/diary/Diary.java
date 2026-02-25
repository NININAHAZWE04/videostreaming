
package diary;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface RMI pour le service d'annuaire de vidéos
 */
public interface Diary extends Remote {
    
    /**
     * Enregistre une vidéo dans l'annuaire
     * @param title Titre de la vidéo
     * @param host Adresse IP du serveur de streaming
     * @param port Port du serveur de streaming
     * @throws RemoteException
     */
    void registerVideo(String title, String host, int port) throws RemoteException;
    
    /**
     * Supprime une vidéo de l'annuaire
     * @param title Titre de la vidéo à supprimer
     * @throws RemoteException
     */
    void unregisterVideo(String title) throws RemoteException;
    
    /**
     * Récupère les informations d'une vidéo
     * @param title Titre de la vidéo
     * @return VideoInfo ou null si non trouvée
     * @throws RemoteException
     */
    VideoInfo getVideoInfo(String title) throws RemoteException;
    
    /**
     * Liste toutes les vidéos disponibles
     * @return Liste des informations de vidéos
     * @throws RemoteException
     */
    List<VideoInfo> listAllVideos() throws RemoteException;
}