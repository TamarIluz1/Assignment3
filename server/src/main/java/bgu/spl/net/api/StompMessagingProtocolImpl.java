package bgu.spl.net.api;

import bgu.spl.net.srv.ConnectionsImpl;
import java.util.Map;
import java.util.HashMap;

//Added this class by Tamar 15/1

public class StompMessagingProtocolImpl<T> implements StompMessagingProtocol<T> {
    private int connectionId;
    private ConnectionsImpl<T> connections;
    private boolean shouldTerminate = false;
    private Map<String, String> subscriptions = new HashMap<>();

    @Override
    public void start(int connectionId, ConnectionsImpl<T> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(T message) {
        // Parse the STOMP frame and execute corresponding actions
        String command = extractCommand(message);
        switch (command) {
            case "CONNECT":
                handleConnect(message);
                break;
            case "SUBSCRIBE":
                handleSubscribe(message);
                break;
            case "SEND":
                handleSend(message);
                break;
            case "DISCONNECT":
                handleDisconnect(message);
                break;
            default:
                connections.send(connectionId, "ERROR\nmessage: Unknown command\n\n");
        }
    }

    private String extractCommand(T message) {
        // Extract the command from the message
        String msgStr = message.toString();
        int endOfCommand = msgStr.indexOf('\n');
        return msgStr.substring(0, endOfCommand).trim();
    }

    private void handleConnect(T message) {
        // Handle CONNECT logic and respond with CONNECTED frame
        connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
    }

    private void handleSubscribe(T message) {
        // Handle SUBSCRIBE logic
        String msgStr = message.toString();
        String[] lines = msgStr.split("\n");
        String destination = null;
        for (String line : lines) {
            if (line.startsWith("destination:")) {
                destination = line.substring("destination:".length()).trim();
                break;
            }
        }
        if (destination != null) {
            subscriptions.put(destination, Integer.toString(connectionId));
            connections.send(connectionId, "RECEIPT\nreceipt-id:sub-" + connectionId + "\n\n");
        } else {
            connections.send(connectionId, "ERROR\nmessage: Missing destination header\n\n");
        }
    }

    private void handleSend(T message) {
        // Handle SEND logic (publish to channels)
        String msgStr = message.toString();
        String[] lines = msgStr.split("\n");
        String destination = null;
        StringBuilder body = new StringBuilder();
        boolean bodyStarted = false;
        for (String line : lines) {
            if (line.startsWith("destination:")) {
                destination = line.substring("destination:".length()).trim();
            } else if (line.isEmpty()) {
                bodyStarted = true;
            } else if (bodyStarted) {
                body.append(line).append("\n");
            }
        }
        if (destination != null) {
            connections.send(destination, body.toString());
        } else {
            connections.send(connectionId, "ERROR\nmessage: Missing destination header\n\n");
        }
    }

    private void handleDisconnect(T message) {
        // Handle DISCONNECT logic and terminate the connection
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
