import com.sun.xml.internal.rngom.parse.host.Base;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
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
                    // Blockchain
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

                    // Heart beat
                    case "hb":
                        handleHeartBeat(Integer.parseInt(tokens[1]), tokens[2]);
                        break;
                    case "si":
                        handleServerInfo(Integer.parseInt(tokens[1]), new ServerInfo(tokens[2], Integer.parseInt(tokens[3])));
                        break;

                    // Catch up
                    case "lb":
                        handleLatestBlock(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]), tokens[3]);
                        break;
                    case "cu":
                        System.out.println(inputLine);
                        handleCatchUp(tokens.length == 1 ? null : tokens[1]);
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

    private void handleLatestBlock(int remotePort, int length, String encodedHash) {

        if (encodedHash == null) return;

        byte[] localHash = blockchain.getHead() == null ? null : blockchain.getHead().calculateHash();
        byte[] remoteHash = Base64.getDecoder().decode(encodedHash);

        if (localHash != null && (blockchain.getLength() > length || (blockchain.getLength() == length && compareHash(localHash, remoteHash) <= 0))) {
            return;
        }

        try {
            // Connect to the peer server
            String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");
            Socket toPeer = new Socket(remoteIP, remotePort);

            ArrayList<Block> blocks = new ArrayList<>();

            PrintWriter writer = new PrintWriter(toPeer.getOutputStream(), true);
            writer.println("cu");
            writer.flush();

            ObjectInputStream reader = new ObjectInputStream(toPeer.getInputStream());
            blocks.add((Block) reader.readObject());

            reader.close();
            writer.close();
            toPeer.close();

            // Read the rest blocks
            String prev = Base64.getEncoder().encodeToString(blocks.get(0).getPreviousHash());

            while (!prev.equals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")) {
                toPeer = new Socket(remoteIP, remotePort);

                writer = new PrintWriter(toPeer.getOutputStream(), true);
                writer.println("cu|" + prev);
                writer.flush();

                reader = new ObjectInputStream(toPeer.getInputStream());
                blocks.add((Block) reader.readObject());

                reader.close();
                writer.close();
                toPeer.close();

                prev = Base64.getEncoder().encodeToString(blocks.get(blocks.size() - 1).getPreviousHash());
            }

            blockchain.setHead(blocks.get(0));
            blockchain.setLength(blocks.size());

            Block curr = blockchain.getHead();

            for (int i = 0; i < blocks.size(); i++) {
                if (i == blocks.size() - 1) {
                    curr.setPreviousBlock(null);
                } else {
                    curr.setPreviousBlock(blocks.get(i + 1));
                }
                curr = curr.getPreviousBlock();
            }
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    private void handleCatchUp(String hash) {

        ObjectOutputStream oos;

        try {
            oos = new ObjectOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            return;
        }

        if (hash == null) {
            try {
                oos.writeObject(blockchain.getHead());
                oos.flush();
                oos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {

            Block curr = blockchain.getHead();

            while (curr != null) {

                if (Base64.getEncoder().encodeToString(curr.calculateHash()).equals(hash)) {

                    try {
                        oos.writeObject(curr);
                        oos.flush();
                        oos.close();
                    } catch (IOException e) {
                    }

                    break;

                } else {
                    curr = curr.getPreviousBlock();
                }
            }
        }
    }

    private int compareHash(byte[] arrayA, byte[] arrayB) {

        if (arrayA.length < arrayB.length) {
            return -1;
        }

        if (arrayA.length > arrayB.length) {
            return 1;
        }

        for (int i = 0; i < arrayA.length; i++) {

            if (arrayA[i] < arrayB[i]) {
                return -1;
            }

            if (arrayA[i] > arrayB[i]) {
                return 1;
            }
        }

        return 0;
    }
}
