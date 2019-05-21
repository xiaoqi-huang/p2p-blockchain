import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PeriodicClientRunnable implements Runnable {

    ServerInfo serverInfo;
    String message;

    public PeriodicClientRunnable(ServerInfo serverInfo, String message) {
        this.serverInfo = serverInfo;
        this.message = message;
    }

    @Override
    public void run() {
        try {
            Socket toServer = new Socket();
            toServer.connect(new InetSocketAddress(serverInfo.getHost(), serverInfo.getPort()), 2000);
            PrintWriter writer = new PrintWriter(toServer.getOutputStream(), true);

//            System.out.println("sent: " + message);
            writer.println(message);
            writer.flush();

            writer.close();
            toServer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
