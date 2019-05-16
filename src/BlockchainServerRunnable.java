import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;

public class BlockchainServerRunnable implements Runnable{

    private Socket clientSocket;
    private Blockchain blockchain;
    private HashMap<ServerInfo, Date> serverStatus;

    public BlockchainServerRunnable(Socket clientSocket, Blockchain blockchain, HashMap<ServerInfo, Date> serverStatus) {
        this.clientSocket = clientSocket;
        this.blockchain = blockchain;
        this.serverStatus = serverStatus;
    }

    public void run() {
        try {
            serverHandler(clientSocket.getInputStream(), clientSocket.getOutputStream());
            clientSocket.close();
        } catch (IOException e) {
        }
    }

    public void serverHandler(InputStream clientInputStream, OutputStream clientOutputStream) {

        BufferedReader inputReader = new BufferedReader(
                new InputStreamReader(clientInputStream));
        PrintWriter outWriter = new PrintWriter(clientOutputStream, true);

        try {
            while (true) {
                String inputLine = inputReader.readLine();
                if (inputLine == null) {
                    break;
                }

                String[] tokens = inputLine.split("\\|");
                switch (tokens[0]) {
                    case "tx":
                        if (blockchain.addTransaction(inputLine))
                            outWriter.print("Accepted\n\n");
                        else
                            outWriter.print("Rejected\n\n");
                        outWriter.flush();
                        break;
                    case "pb":
                        outWriter.print(blockchain.toString() + "\n");
                        outWriter.flush();
                        break;
                    case "hb":
                        handleHeartBeat(Integer.parseInt(tokens[1]), tokens[2]);
                        break;
                    case "si":
                        handleServerInfo(Integer.parseInt(tokens[1]), new ServerInfo(tokens[2], Integer.parseInt(tokens[3])));
                        break;
                    case "cc":
                        return;
                    default:
                        outWriter.print("Error\n\n");
                        outWriter.flush();
                }
            }
        } catch (IOException e) {
        }
    }

    private void handleHeartBeat(int remotePort, String seqNum) {

        String localIP = clientSocket.getLocalAddress().toString();
        int localPort = clientSocket.getLocalPort();
        String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");

        ServerInfo localServerInfo = new ServerInfo(localIP, localPort);
        ServerInfo remoteServerInfo = new ServerInfo(remoteIP, remotePort);

        serverStatus.put(remoteServerInfo, new Date());

        if (seqNum.equals("0")) {

            ArrayList<Thread> threads = new ArrayList<>();

            for (ServerInfo serverInfo : serverStatus.keySet()) {
                if (serverInfo.equals(remoteServerInfo) || serverInfo.equals(localServerInfo)) {
                    continue;
                }
                Thread thread = new Thread(new PeriodicClientRunnable(serverInfo, "si|" + localPort + "|" + remoteIP + "|" + remotePort));
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

    private void handleServerInfo(int remotePort, ServerInfo newServerInfo) {
        if (!serverStatus.keySet().contains(newServerInfo)) {
            serverStatus.put(newServerInfo, new Date());

            String localIP = clientSocket.getLocalAddress().toString();
            int localPort = clientSocket.getLocalPort();
            String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");

            ServerInfo localServerInfo = new ServerInfo(localIP, localPort);
            ServerInfo remoteServerInfo = new ServerInfo(remoteIP, remotePort);

            ArrayList<Thread> threads = new ArrayList<>();

            for (ServerInfo serverInfo : serverStatus.keySet()) {

                if (serverInfo.equals(newServerInfo) || serverInfo.equals(remoteServerInfo) || serverInfo.equals(localServerInfo)) {
                    continue;
                }

                Thread thread = new Thread(new PeriodicClientRunnable(serverInfo, "si|" + localPort + "|" + newServerInfo.getHost() + "|" + newServerInfo.getPort()));
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
}
