import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueueServer {
    private static final int DEFAULT_PORT = 5000;

    private int counterNumber = 1;
    private final Map<String, CounterData> activeCounters = new ConcurrentHashMap<>();
    private final List<QueueUpdate> updateHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<HandleClient> connectedClients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Using default port " + DEFAULT_PORT);
            }
        }

        QueueServer server = new QueueServer();
        server.startServer(port);
    }

    public void startServer(int port) {
        ServerLogger.log("Server started on port " + port);
        System.out.println("QueueServer is running on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();

                String counterId = generateCounterId();
                CounterData counter = new CounterData(counterId);
                activeCounters.put(counterId, counter);

                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                ServerLogger.log(counterId + " connected from " + clientAddress);

                HandleClient clientHandler = new HandleClient(clientSocket, counter, this);
                connectedClients.add(clientHandler);

                Thread clientThread = new Thread(clientHandler);
                clientThread.start();
            }
        } catch (IOException e) {
            ServerLogger.log("Server stopped due to error: " + e.getMessage());
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private synchronized String generateCounterId() {
        String id = "C" + counterNumber;
        counterNumber++;
        return id;
    }

    public void broadcastAlert(String alertMessage) {
        ServerLogger.log("Broadcast " + alertMessage);
        synchronized (connectedClients) {
            for (HandleClient client : connectedClients) {
                client.sendMessage(alertMessage);
            }
        }
    }

    public void removeClient(String counterId, HandleClient clientHandler) {
        activeCounters.remove(counterId);
        connectedClients.remove(clientHandler);
        ServerLogger.log(counterId + " disconnected");
    }

    public void addHistoryRecord(QueueUpdate update) {
        updateHistory.add(update);
    }

    public String getStatus() {
        if (activeCounters.isEmpty()) {
            return "OK STATUS NONE";
        }

        List<CounterData> counters = new ArrayList<>(activeCounters.values());
        counters.sort((c1, c2) -> {
            int id1 = Integer.parseInt(c1.getCounterId().substring(1));
            int id2 = Integer.parseInt(c2.getCounterId().substring(1));
            return Integer.compare(id1, id2);
        });

        StringBuilder response = new StringBuilder("OK STATUS ");
        for (int i = 0; i < counters.size(); i++) {
            CounterData c = counters.get(i);
            String state = c.isPending() ? "PENDING" : String.valueOf(c.getQueueLength());
            response.append(c.getCounterId()).append("=").append(state);

            if (i < counters.size() - 1) {
                response.append("; ");
            }
        }
        return response.toString();
    }

    public String getTotal() {
        int sum = 0;
        for (CounterData c : activeCounters.values()) {
            if (!c.isPending()) {
                sum += c.getQueueLength();
            }
        }
        return "OK TOTAL " + sum;
    }

    public String getShortest() {
        CounterData shortestCounter = null;

        for (CounterData c : activeCounters.values()) {
            if (c.isPending()) {
                continue;
            }

            if (shortestCounter == null) {
                shortestCounter = c;
            } else {
                int currentQueue = c.getQueueLength();
                int shortestQueue = shortestCounter.getQueueLength();

                if (currentQueue < shortestQueue) {
                    shortestCounter = c;
                } else if (currentQueue == shortestQueue) {
                    int currentIdNum = Integer.parseInt(c.getCounterId().substring(1));
                    int shortestIdNum = Integer.parseInt(shortestCounter.getCounterId().substring(1));
                    if (currentIdNum < shortestIdNum) {
                        shortestCounter = c;
                    }
                }
            }
        }

        if (shortestCounter == null) {
            return "OK SHORTEST NONE";
        }
        return "OK SHORTEST " + shortestCounter.getCounterId() + " " + shortestCounter.getQueueLength();
    }

    public String getRecent(int n) {
        synchronized (updateHistory) {
            if (updateHistory.isEmpty()) {
                return "OK RECENT NONE";
            }

            int totalRecords = updateHistory.size();
            int startIndex = Math.max(0, totalRecords - n);

            StringBuilder response = new StringBuilder("OK RECENT ");
            for (int i = startIndex; i < totalRecords; i++) {
                response.append(updateHistory.get(i).toRecentFormat());
                if (i < totalRecords - 1) {
                    response.append("; ");
                }
            }
            return response.toString();
        }
    }
}