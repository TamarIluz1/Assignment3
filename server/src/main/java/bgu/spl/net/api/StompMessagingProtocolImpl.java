package bgu.spl.net.api;

import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {
    private int connectionId;
    private ConnectionsImpl<String> connections;
    private boolean shouldTerminate = false;

    @Override
    public void start(int connectionId, ConnectionsImpl<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
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

    private void handleConnect(String message) {
        // Handle CONNECT logic and respond with CONNECTED frame
        connections.send(connectionId, "CONNECTED\nversion:1.2\n\n");
    }

    private void handleSubscribe(String message) {
        // Handle SUBSCRIBE logic
    }

    private void handleSend(String message) {
        // Handle SEND logic (publish to channels)
    }

    private void handleDisconnect(String message) {
        // Handle DISCONNECT logic and terminate the connection
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private String extractCommand(String message) {
        return message.split("\n")[0]; // Assume first line is the command
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
