import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class ServerLogger {

    // Log file saved in the project folder
    private static final String LOG_FILE = "server_log.txt";

    // Timestamp format matching Section 3 of the project specification
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Static synchronized method so QueueServer and all HandleClient threads
     * can write log lines safely without overlapping text.
     */
    public static synchronized void log(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logEntry = "[" + timestamp + "] " + message;

        // Print to console for live visibility during testing and demo
        System.out.println(logEntry);

        // Open in append mode (true) and flush immediately after writing
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(logEntry);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error writing to server log: " + e.getMessage());
        }
    }
}
