import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PeriodicServerStatusCheckRunnable implements Runnable {

    HashMap<ServerInfo, Date> serverStatus;

    public PeriodicServerStatusCheckRunnable(HashMap<ServerInfo, Date> serverInfo) {
        this.serverStatus = serverInfo;
    }

    @Override
    public void run() {

        while (true) {

            for (Map.Entry<ServerInfo, Date> entry : serverStatus.entrySet()) {
                if (new Date().getTime() - entry.getValue().getTime() > 4000) {
                    serverStatus.remove(entry);
                }
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
