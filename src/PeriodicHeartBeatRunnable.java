import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class PeriodicHeartBeatRunnable implements Runnable {

    int seq;
    int port;
    HashMap<ServerInfo, Date> serverStatus;

    public PeriodicHeartBeatRunnable(int port, HashMap<ServerInfo, Date> serverStatus) {
        this.seq = 0;
        this.port = port;
        this.serverStatus = serverStatus;
    }

    @Override
    public void run() {

        while (true) {

            try {
                ArrayList<Thread> threads = new ArrayList();

                for (ServerInfo serverInfo : serverStatus.keySet()) {
                    Thread thread = new Thread(new PeriodicClientRunnable(serverInfo, "hb|" + port + "|" + seq));
                    threads.add(thread);
                    thread.start();
                }

                for (Thread thread : threads) {
                    thread.join();
                }

                seq++;

                Thread.sleep(2000);
            } catch (InterruptedException e) {

            }
        }
    }
}
