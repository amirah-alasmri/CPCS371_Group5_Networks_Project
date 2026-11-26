import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** TCP client representing one campus service counter. */
public class QueueClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;
    private static final int UPDATE_INTERVAL = 3000;
    private static final int MIN_UPDATES = 20;

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Random random = new Random();
    private final AtomicInteger acceptedUpdates = new AtomicInteger();
    private final int[] testSequence;
    private volatile boolean running = true;
    private String counterId;
    private int sequenceIndex = 0;

    public QueueClient(Socket socket, int[] testSequence) throws IOException {
        this.socket = socket;
        this.testSequence = testSequence;
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = DEFAULT_PORT;
        int[] sequence = null;
        try {
            if (args.length > 3) {
                throw new IllegalArgumentException("Too many arguments");
            }
            if (args.length > 1) {
                port = Integer.parseInt(args[1]);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Port must be between 1 and 65535");
                }
            }
            // Optional fixed values, separated by commas, for reproducible tests.
            if (args.length == 3) {
                String[] values = args[2].split(",", -1);
                sequence = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    sequence[i] = Integer.parseInt(values[i].trim());
                    if (sequence[i] < 0 || sequence[i] > 30) {
                        throw new IllegalArgumentException("Test values must be between 0 and 30");
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            System.out.println("Usage: java QueueClient [host] [port] [testSequence]");
            return;
        }

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to " + host + ":" + port);
            new QueueClient(socket, sequence).runClient();
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    private void runClient() throws IOException {
        // This is the only thread that reads from the server socket.
        String message;
        while ((message = reader.readLine()) != null) {
            System.out.println(message);
            if (message.startsWith("WELCOME ")) {
                counterId = message.substring("WELCOME ".length()).trim();
                break;
            }
        }
        if (counterId == null) {
            System.err.println("Server disconnected before assigning a counter ID.");
            return;
        }

        ScheduledExecutorService updater = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "QueueUpdater");
            thread.setDaemon(true);
            return thread;
        });
        Thread menuThread = new Thread(this::handleMenu, "ConsoleMenu");
        // Neither worker should keep the JVM alive after disconnection.
        menuThread.setDaemon(true);
        updater.scheduleAtFixedRate(this::sendAutomaticUpdate,
                UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
        menuThread.start();

        try {
            // Responses and unsolicited alerts are displayed as soon as they arrive.
            while (running && (message = reader.readLine()) != null) {
                if (message.startsWith("OK UPDATE " + counterId + " ")) {
                    int count = acceptedUpdates.incrementAndGet();
                    System.out.println(message + " (update #" + count + ")");
                    if (count == MIN_UPDATES) {
                        System.out.println("Minimum 20 updates completed. You may now exit.");
                    }
                } else if (message.startsWith("OK ") || message.startsWith("ERROR ")
                        || message.startsWith("ALERT ")) {
                    System.out.println(message);
                } else {
                    System.out.println("Unexpected server message: " + message);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Connection lost: " + e.getMessage());
            }
        } finally {
            disconnect();
            updater.shutdownNow();
            System.out.println("Disconnected. Accepted updates: " + acceptedUpdates.get());
        }
    }

    // Schedule one update at a fixed rate to avoid accumulating execution delays.
    private void sendAutomaticUpdate() {
        if (!running) return;
        try {
            int queueLength;
            if (testSequence == null) {
                queueLength = random.nextInt(31);
            } else {
                queueLength = testSequence[sequenceIndex];
                sequenceIndex = (sequenceIndex + 1) % testSequence.length;
            }
            sendMessage("UPDATE " + queueLength);
        } catch (IOException e) {
            if (running) System.err.println("Update failed: " + e.getMessage());
            disconnect();
        }
    }

    private void handleMenu() {
        Scanner scanner = new Scanner(System.in);
        printMenu();
        try {
            while (running && scanner.hasNextLine()) {
                String choice = scanner.nextLine().trim();
                switch (choice) {
                    case "1":
                    case "STATUS":
                        sendMessage("STATUS");
                        break;
                    case "2":
                    case "TOTAL":
                        sendMessage("TOTAL");
                        break;
                    case "3":
                    case "SHORTEST":
                        sendMessage("SHORTEST");
                        break;
                    case "4":
                    case "RECENT":
                        System.out.println("Enter number of recent updates:");
                        if (!scanner.hasNextLine()) return;
                        sendMessage("RECENT " + scanner.nextLine().trim());
                        break;
                    case "5":
                    case "EXIT":
                        if (acceptedUpdates.get() < MIN_UPDATES) {
                            System.out.println("Please complete at least 20 updates before exiting.");
                        } else {
                            disconnect();
                            return;
                        }
                        break;
                    default:
                        // Also permit direct query input to demonstrate server validation.
                        // UPDATE is reserved for the automatic updater.
                        if (choice.equalsIgnoreCase("UPDATE")
                                || choice.matches("(?i)UPDATE\\s+.*")) {
                            System.out.println("UPDATE is sent automatically.");
                        } else {
                            sendMessage(choice);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            if (running) System.err.println("Request failed: " + e.getMessage());
            disconnect();
        }
    }

    private void printMenu() {
        System.out.println("\n--- Queue Client Menu (" + counterId + ") ---");
        System.out.println("1. STATUS");
        System.out.println("2. TOTAL");
        System.out.println("3. SHORTEST");
        System.out.println("4. RECENT");
        System.out.println("5. EXIT (after at least 20 updates)");
        System.out.println("Enter a menu option or command:");
    }

    // Both worker threads share the writer; serialize complete messages.
    private synchronized void sendMessage(String message) throws IOException {
        if (!running) return;
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    private void disconnect() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // The connection may already be closed.
        }
    }
}
