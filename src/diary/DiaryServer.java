package diary;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Serveur Diary - lance le service RMI.
 * Usage: java -cp bin diary.DiaryServer <host> <port>
 */
public final class DiaryServer {
    private DiaryServer() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java -cp bin diary.DiaryServer <host> <port>");
            System.exit(1);
        }

        String host = args[0] == null ? "" : args[0].trim();
        if (host.isEmpty()) {
            System.err.println("Erreur: l'hôte ne peut pas être vide");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("Le port doit être compris entre 1 et 65535");
            }

            System.setProperty("java.rmi.server.hostname", host);

            Registry registry = LocateRegistry.createRegistry(port);
            DiaryImpl diary = new DiaryImpl();
            registry.rebind("Diary", diary);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    registry.unbind("Diary");
                    UnicastRemoteObject.unexportObject(diary, true);
                } catch (Exception ignored) {
                }
            }, "diary-shutdown"));

            System.out.println("===========================================");
            System.out.println("    DIARY SERVICE STARTED");
            System.out.println("===========================================");
            System.out.println("Host: " + host);
            System.out.println("Port: " + port);
            System.out.println("Service prêt à accepter les connexions...");
            System.out.println("===========================================");

        } catch (NumberFormatException e) {
            System.err.println("Erreur: le port doit être un nombre");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur: " + e.getMessage());
            System.exit(1);
        } catch (RemoteException e) {
            System.err.println("Erreur lors du démarrage du serveur Diary: " + e.getMessage());
            System.exit(1);
        }
    }
}
