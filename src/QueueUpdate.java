import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/**
 * Represents a single queue update from a counter.
 * Each update stores the counter ID, queue length, and server timestamp.
 * These records are used to maintain the update history and display recent updates.
 */
public class QueueUpdate {
    private final String counterID;
    private final int queueLength;
    private final LocalDateTime timestamp; // The server date and time when the update was recorded.


    public QueueUpdate(String counterID, int queueLength) {
    // Reject queue lengths outside the allowed range (0 to 30).
        if (queueLength < 0 || queueLength > 30) {
            throw new IllegalArgumentException("Queue length must be between 0 and 30");
        }

        this.counterID = counterID;
        this.queueLength = queueLength;
        this.timestamp = LocalDateTime.now();
    }
    // getters
    public String getCounterId() {
        return counterID;
    }

    public int getQueueLength() {
        return queueLength;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    // Converts the update into RECENT response format.
    // return a formatted String containing the Counter ID, Queue Length, and Server time.
    public String toRecentFormat() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        return counterID + "," + queueLength + "," + timestamp.format(formatter);
    }   
}
