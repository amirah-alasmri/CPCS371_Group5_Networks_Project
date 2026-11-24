import java.io.*;
import java.net.Socket;


public class HandleClient implements Runnable {

    private  Socket clientSocket;
    private  CounterData counterData;
    private  QueueServer server;

    private BufferedReader reader;
    private BufferedWriter writer;

   
    public HandleClient(Socket clientSocket, CounterData counterData, QueueServer server) {
        this.clientSocket = clientSocket;
        this.counterData = counterData;
        this.server = server;
    }


    public synchronized void sendMessage(String message) {
        if (writer == null || clientSocket.isClosed()) {
            return;
        }
        try {
            writer.write(message);
            writer.newLine(); // Terminate every message with a newline
            writer.flush();   // Flush the message.
        } catch (IOException e) {
            // Client likely disconnected; cleanup in finally block will handle removal
        }
    }

    @Override
    public void run() {
        String counterId = counterData.getCounterId();

        try {
            // These variables will handle the input and output stream from clients
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

        // sends WELCOME <counterID> to the client and log it
            String welcomeMsg = "WELCOME " + counterId;
            sendMessage(welcomeMsg);
            ServerLogger.log(counterId + " sent " + welcomeMsg);

            // It will read the clients lines 
            String line;
            while ((line = reader.readLine()) != null) {
                validateCommand(line);
            }

        } catch (IOException e) {
            // Normal when client closes terminal or loses connection
        } finally {
            // 4. Always clean up when client disconnects
            cleanup();
        }
    }

    /**
     * Parses and validates one incoming command line.
     */
    private void validateCommand(String rawLine) {
        String counterId = counterData.getCounterId();
        ServerLogger.log(counterId + " received " + rawLine);
// the command sent was empty, send an error message.
        if (rawLine == null || rawLine.trim().isEmpty()) {
            sendError(rawLine, "Empty command");
            return;
        }

        // Split by spaces (case-sensitive: lowercase commands must be rejected)
        String[] parts = rawLine.trim().split("\\s+");
        String command = parts[0];

        switch (command) {
            case "UPDATE":
                handleUpdate(rawLine, parts);
                break;

            case "STATUS":
                if (parts.length != 1) {//if the command has aruguments, send an error message
                    sendError(rawLine, "STATUS takes no arguments");
                    return;
                }
                sendOkResponse(server.getStatus());//if the command is valid, send the status to the client
                break;

            case "TOTAL":
                if (parts.length != 1) {//if the command has aruguments, send an error message
                    sendError(rawLine, "TOTAL takes no arguments");
                    return;
                }
                sendOkResponse(server.getTotal());//if the command is valid, send the status to the client
                break;

            case "SHORTEST":
                if (parts.length != 1) {//if the command has aruguments, send an error message
                    sendError(rawLine, "SHORTEST takes no arguments");
                    return;
                }
                sendOkResponse(server.getShortest());//if the command is valid, send the status to the client
                break;

            case "RECENT":
                handleRecent(rawLine, parts);
                break;

            default:
                sendError(rawLine, "Unknown command");//this defualt will handle any command that is not in the list of valid commands, and send an error message to the client.
                break;
        }
    }

  
    private void handleUpdate(String rawLine, String[] parts) {
        String counterId = counterData.getCounterId();

        // if the command has more or less than 1 argument, send an error message
        if (parts.length != 2) {
            sendError(rawLine, "UPDATE requires exactly one integer argument");
            return;
        }

        // must be a valid integer
        int newQueue;
        try {
            newQueue = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendError(rawLine, "Queue length must be an integer");
            return;
        }

        // Must be in range 0..30
        if (newQueue < 0 || newQueue > 30) {
            sendError(rawLine, "Queue length must be between 0 and 30");
            return;
        }

        // Synchronize on counterData so concurrent reads (STATUS/TOTAL/SHORTEST) see consistent state
        boolean shouldAlert = false;
        synchronized (counterData) {
            counterData.setQueueLength(newQueue);

            // Alert rule: trigger only when crossing from <=20 (or PENDING) to >20
            if (newQueue > 20 && !counterData.isAlertActive()) {
                counterData.setAlertActive(true);
                shouldAlert = true;
            } else if (newQueue <= 20) {
                // Reset alert state when queue drops back to 20 or below
                counterData.setAlertActive(false);
            }
        }

        // Append to ordered session history and log acceptance
        server.addHistoryRecord(new QueueUpdate(counterId, newQueue));
        ServerLogger.log(counterId + " accepted queue = " + newQueue);

        // Send acknowledgment response BEFORE broadcasting alert (per Section 3)
        sendOkResponse("OK UPDATE " + counterId + " " + newQueue);

        // Broadcast congestion alert to ALL connected clients if triggered
        if (shouldAlert) {
            String alertMsg = "ALERT " + counterId + " QUEUE " + newQueue + " EXCEEDS 20";
            server.broadcastAlert(alertMsg);
        }
    }

    /**
     * Validates and executes RECENT <n> where n >= 1.
     */
    private void handleRecent(String rawLine, String[] parts) {
        if (parts.length != 2) {
            sendError(rawLine, "RECENT requires exactly one integer argument");
            return;
        }

        int n;
        try {
            n = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendError(rawLine, "RECENT count must be an integer");
            return;
        }

        if (n < 1) {
            sendError(rawLine, "RECENT count must be at least 1");
            return;
        }

        sendOkResponse(server.getRecent(n));
    }

    // Sends a valid OK response and logs it
    private void sendOkResponse(String response) {
        sendMessage(response);
        ServerLogger.log(counterData.getCounterId() + " sent " + response);
    }

    // Rejects invalid input without closing the socket
    private void sendError(String rawCommand, String reason) {
        String counterId = counterData.getCounterId();
        String errorMsg = "ERROR " + reason;

        ServerLogger.log(counterId + " rejected " + rawCommand + " (" + reason + ")");
        sendMessage(errorMsg);
        ServerLogger.log(counterId + " sent " + errorMsg);
    }

    // Removes client from server active lists and closes socket/streams
    private void cleanup() {
        server.removeClient(counterData.getCounterId(), this);

        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {}

        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}

        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException ignored) {}
    }
}
