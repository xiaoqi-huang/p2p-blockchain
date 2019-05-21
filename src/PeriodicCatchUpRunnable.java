import java.util.*;

public class PeriodicCatchUpRunnable implements Runnable {

    int port;
    HashMap<ServerInfo, Date> serverStatus;
    Blockchain blockchain;

    public PeriodicCatchUpRunnable(int port, HashMap<ServerInfo, Date> serverStatus, Blockchain blockchain) {
        this.port = port;
        this.serverStatus = serverStatus;
        this.blockchain = blockchain;
    }

    @Override
    public void run() {

        while (true) {

            if (blockchain.getHead() == null) continue;

            ArrayList<ServerInfo> targets = getRandomPeers(serverStatus.keySet());

            multicast(targets, getMessage());

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private ArrayList<ServerInfo> getRandomPeers(Set<ServerInfo> serverInfoSet) {

        // Return all the peers if there are no more than 4 peers
        if (serverInfoSet.size() < 5) {
            return new ArrayList<>(serverInfoSet);
        }

        // Shuffle the serverInfo list and add the first one to the targets list; Repeat for 5 times
        ArrayList<ServerInfo> targets = new ArrayList<>();
        ArrayList<ServerInfo> allPeers = new ArrayList<>(serverInfoSet);

        for (int i = 0; i < 5; i++) {
            Collections.shuffle(allPeers);
            targets.add(allPeers.remove(0));
        }

        return targets;
    }

    private String getMessage() {
        return "lb|" + port + "|" + blockchain.getLength() + "|" + Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash());
    }

    private void multicast(ArrayList<ServerInfo> targets, String message) {

        ArrayList<Thread> threads = new ArrayList<>();

        for (ServerInfo target : targets) {
            Thread thread = new Thread(new PeriodicClientRunnable(target, message));
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
