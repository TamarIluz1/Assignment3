package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.User;
import java.util.Map;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

//Added this class by Tamar 15/1

public class StompProtocol implements StompMessagingProtocol<String> {

   private static final Logger logger = Logger.getLogger(StompProtocol.class.getName());

    private int connectionId;
    private ConnectionsImpl<String> connections;
    private Map<String, String> subscriptionsIdtoChannelName = new HashMap<>();
    private Map<String, Set<String>> channeltoSubscriptions = new HashMap<>();
    private Map<String, ConnectionHandler> subscriptionsToHandlers = new HashMap<>();
    private boolean shouldTerminate = false;
    

    @Override
    public void start(int connectionId, ConnectionsImpl<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        logger.info("Started protocol for connection ID: " + connectionId);
    }

    @Override
    public String process(String frame) {
        logger.info("Processing frame: " + frame);
        Frame frame1 = Frame.parseFrame(frame);
        String command = frame1.getCommand();
        logger.info("Command: " + command);
        switch (command) {
            // TODO fix error in report
            case "CONNECT":
                return handleConnect(frame1);
            case "SUBSCRIBE":
                return handleSubscribe(frame1);
            case "UNSUBSCRIBE":
                return handleUnsubscribe(frame1);
            case "SEND":
                return handleSend(frame1);
            case "DISCONNECT":
                return handleDisconnect(frame1);
            default:
                return handleError("Unknown command: " + command);
        }
    }

    private String handleConnect(Frame frame) {
        logger.info("Handling CONNECT frame");
        String acceptVersion = frame.getHeaders().get("accept-version");
        String host = frame.getHeaders().get("host");
        String username = frame.getHeaders().get("login");
        String password = frame.getHeaders().get("passcode");
        if (acceptVersion == null || host == null) {
            return handleError("Missing accept-version or host in CONNECT frame");
        }

        for (User<String> user : connections.getUsers().values()) {
            if (user.isLoggedIn() && user.getConnectionId() == connectionId) {
                return handleError("ERROR\nmessage:The client is already logged in, log out before trying again\n\n^@");
            }
        }

        if (connections.getUserDetails(username).isLoggedIn()) {
            return handleError("ERROR\nmessage:User already logged in\n\n^@");
        }

        Frame connectedFrame = new Frame("CONNECT");

        if (connections.getUsers().containsKey(username) ) {
            // login existing user
            if (connections.checkLogin(username, password)) {
                // login details are valid
                loginExistingUser(username);
                // Send CONNECTED frame back to client
                connectedFrame.addHeader("version", "1.2");
                connectedFrame.setBody(null);
                connections.send(connectionId, connectedFrame.toString());
                logger.info("Sent CONNECTED frame of existing user" + username);
                return connectedFrame.toString();
            }
            
        } else {
            createUser(new User(username, password, (ConnectionHandler)connections.getCHbyConnectionID(connectionId), connectionId));
                connectedFrame.addHeader("version", "1.2");
                connectedFrame.setBody(null);
                connections.send(connectionId, connectedFrame.toString());
            logger.info("Sent CONNECTED frame - new user" + username);
            return connectedFrame.toString();
        }
        return null;

    }

    public void loginExistingUser(String username) {
        User<String> logged = connections.getUserDetails(username);
        logged.setLoggedIn(true);
        logged.setConnectionId(connectionId);
    }

    public void createUser(User u) {
        connections.getUsers().put(u.getUsername(), u);
    }


    private String handleSubscribe(Frame frame) {
        logger.info("Handling SUBSCRIBE frame");
        String destination = frame.getHeaders().get("destination");
        String id = frame.getHeaders().get("id");
        if (destination == null || id == null) {
            return handleError("Missing destination or id in SUBSCRIBE frame");
        }
        subscriptionsIdtoChannelName.put(id, destination);
        if (!channeltoSubscriptions.containsKey(destination)) {
            channeltoSubscriptions.get(destination).add(id);
        }
        else{
            channeltoSubscriptions.put(destination, new HashSet<String>());
            channeltoSubscriptions.get(destination).add(id);
        }
        subscriptionsToHandlers.put(id, connections.getCHbyConnectionID(connectionId));
        
        logger.info("Subscribed to destination: " + destination + " with ID: " + id);
        // Acknowledge subscription
        return null;
    }

    private String handleUnsubscribe(Frame frame) {
        logger.info("Handling UNSUBSCRIBE frame");
        String id = frame.getHeaders().get("id");
        if (id == null || !subscriptionsIdtoChannelName.containsKey(id)) {
            return handleError("Invalid or missing id in UNSUBSCRIBE frame");
        }
        String channelName = subscriptionsIdtoChannelName.get(id);
        subscriptionsIdtoChannelName.remove(id);
        subscriptionsToHandlers.remove(id);
        channeltoSubscriptions.get(channelName).remove(id);
        logger.info("Unsubscribed from ID: " + id);
        return null;
    }

    private String handleSend(Frame frame) {
        logger.info("Handling SEND frame");
        String destination = frame.getHeaders().get("destination");
        if (destination == null || !subscriptionsIdtoChannelName.containsValue(destination)) {
            return handleError("Invalid or missing destination in SEND frame");
        }
        // Broadcast message to all subscribers
        for (String subscriptionID : channeltoSubscriptions.get(destination)) {
            // we send the message to all the subscribers

            Frame messageFrame = new Frame("MESSAGE");
            messageFrame.addHeader("destination", destination);
            messageFrame.addHeader("subscription", subscriptionID);
            messageFrame.addHeader("message-id", ((Integer)connections.getNewMessageID()).toString()); // Add appropriate message id
            messageFrame.setBody(frame.getBody());
            connections.send(destination, messageFrame.toString());
            subscriptionsToHandlers.get(subscriptionID).send(connectionId, destination);
            logger.info("Sent MESSAGE frame to subscriptionId of: " + subscriptionID + destination);
            return messageFrame.toString();

        }
        return null;
    }

    private String handleDisconnect(Frame frame) {
        logger.info("Handling DISCONNECT frame");
        String receiptId = frame.getHeaders().get("receipt");
        if (receiptId != null) {
            Frame receiptFrame = new Frame("RECEIPT");
            receiptFrame.addHeader("receipt-id", receiptId);
            connections.send(connectionId, receiptFrame.toString());
            logger.info("Sent RECEIPT frame with receipt-id: " + receiptId);
            return receiptFrame.toString();
        }
        connections.disconnect(connectionId);
        shouldTerminate = true;
        logger.info("Disconnected connection ID: " + connectionId);
        return null;
    }

    private String handleError(String errorMessage) {
        logger.severe("Handling ERROR frame: " + errorMessage);
        Frame errorFrame = new Frame("ERROR");
        errorFrame.addHeader("message", errorMessage);
        connections.send(connectionId, errorFrame.toString());
        connections.disconnect(connectionId);
        shouldTerminate = true;
        return errorFrame.toString();
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
}
