package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.User;
import java.util.Map;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;

//Added this class by Tamar 15/1

public class StompProtocol implements StompMessagingProtocol<String> {

   private static final Logger logger = Logger.getLogger(StompProtocol.class.getName());

    private int connectionId;
    private ConnectionsImpl<String> connections;
    private Map<String, String> subscriptionsIdtoChannelName = new HashMap<>();
    private Map<String, Set<String>> channeltoSubscriptions = new HashMap<>();
<<<<<<< HEAD
    private ConcurrentHashMap<String, Integer> subscriptionIdToConnectionId = new ConcurrentHashMap();
    //private Map<String, Map<String, String>> subsByChannel = new HashMap<>(); //channel -> connectionId -> subscriptionId
    private Map<String, ConnectionHandler<String>> subscriptionsIDToHandlers = new HashMap<>();
=======
    private Map<String, ConnectionHandler<String>> subscriptionsIDToHandlers = new HashMap<>();
    
>>>>>>> 652ec8cc85b7d36380900ed55be2020ade3cda8b
    private boolean shouldTerminate = false;
    // TODO add field of subscription to
    

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

        if (connections.getUserDetails(username) != null && connections.getUserDetails(username).isLoggedIn()) {
            return handleError("ERROR\nmessage:User already logged in\n\n^@");
        }

        Frame connectedFrame = new Frame("CONNECTED");
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
            logger.info("Sent CONNECTED frame - new user" + username + "CH is null? " + connections.getCHbyConnectionID(connectionId));
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
        String subsID = frame.getHeaders().get("id");
        if (destination == null || subsID == null) {
            return handleError("Missing destination or id in SUBSCRIBE frame");
        }
<<<<<<< HEAD
        if (channeltoSubscriptions.containsKey(destination)){
            logger.info("Checking if already subscribed to this channel");
            for (String existingSubscription : channeltoSubscriptions.get(destination)){
                // for every subscriptionId, well check its not the of the current connection.
                logger.info("existing checked:" + connectionId + "compared to " + subscriptionIdToConnectionId.get(existingSubscription));
                if (subscriptionIdToConnectionId.get(existingSubscription).equals(connectionId)){
                    return handleError("ERROR\nmessage:Already subscribed to this channel\n\n^@");
                }
            }
        }

        

        // if (connections.getCHbyConnectionID(connectionId))
        subscriptionsIdtoChannelName.put(subsID, destination);
  
=======
        //need to subscribe to the connections 22.1
        connections.subscribe(connectionId, destination);

        subscriptionsIdtoChannelName.put(id, destination);
>>>>>>> 652ec8cc85b7d36380900ed55be2020ade3cda8b
        if (channeltoSubscriptions.containsKey(destination)) {
            channeltoSubscriptions.get(destination).add(subsID);
        }
        else{
            logger.info("Creating new channel: " + destination);
            channeltoSubscriptions.put(destination, new HashSet<String>());
            channeltoSubscriptions.get(destination).add(subsID);
        }
<<<<<<< HEAD
        subscriptionsIDToHandlers.put(subsID, connections.getCHbyConnectionID(connectionId));
        subscriptionIdToConnectionId.put(subsID, connectionId);
        logger.info("Adding to subscriptionsIDToHandlers: " + subsID + " -> " + connections.getCHbyConnectionID(connectionId));

        logger.info("Subscribed to destination: " + destination + " with ID: " + subsID);
=======
        ConnectionHandler<String> ch = connections.getCHbyConnectionID(connectionId);
        if(ch != null){
            subscriptionsIDToHandlers.put(id, ch);
        }
        else{

            throw new NullPointerException("ConnectionHandler is null");
        }        
        logger.info("Subscribed to destination: " + destination + " with ID: " + id);
>>>>>>> 652ec8cc85b7d36380900ed55be2020ade3cda8b
        // Acknowledge subscription
        return null;
    }

    private String handleUnsubscribe(Frame frame) {
        logger.info("Handling UNSUBSCRIBE frame");
        String subsId = frame.getHeaders().get("id");
        if (subsId == null || !subscriptionsIdtoChannelName.containsKey(subsId)) {
            return handleError("Invalid or missing id in UNSUBSCRIBE frame");
        }
<<<<<<< HEAD
        String channelName = subscriptionsIdtoChannelName.get(subsId);
        subscriptionsIdtoChannelName.remove(subsId);
        subscriptionsIDToHandlers.remove(subsId);
        channeltoSubscriptions.get(channelName).remove(subsId);
        subscriptionIdToConnectionId.remove(subsId);
        logger.info("Unsubscribed from ID: " + subsId);
=======
        String channelName = subscriptionsIdtoChannelName.get(id);

        //need to unsubscribe to the connections 22.1
        connections.unsubscribe(connectionId, channelName);

        subscriptionsIdtoChannelName.remove(id);
        subscriptionsIDToHandlers.remove(id);
        channeltoSubscriptions.get(channelName).remove(id);
        logger.info("Unsubscribed from ID: " + id);
>>>>>>> 652ec8cc85b7d36380900ed55be2020ade3cda8b
        return null;
    }

    private String handleSend(Frame frame) {
        logger.info("Handling SEND frame");
        String destination = frame.getHeaders().get("destination");
        logger.info("DESTINATION" + destination);
        if (destination == null || !subscriptionsIdtoChannelName.containsValue(destination.substring(1))) {
            return handleError("Invalid or missing destination in SEND frame");
        }
        if (destination.charAt(0) == '/') {
            destination = destination.substring(1); // removing slash if exists
        }
        // Broadcast message to all subscribers
        for (String subscriptionID : channeltoSubscriptions.get(destination)) {
            // we send the message to all the subscribers

            Frame messageFrame = new Frame("MESSAGE");
<<<<<<< HEAD
            messageFrame.addHeader("destination", destination);
=======
            
>>>>>>> 652ec8cc85b7d36380900ed55be2020ade3cda8b
            messageFrame.addHeader("subscription", subscriptionID);
            messageFrame.addHeader("message-id", ((Integer)connections.getNewMessageID()).toString()); // Add appropriate message id
            messageFrame.addHeader("destination", destWithoutSlash);
            messageFrame.setBody(frame.getBody());
<<<<<<< HEAD
            connections.send(destination, messageFrame.toString());
            logger.info("Sent MESSAGE frame to subscription: " + subscriptionID);
            subscriptionsIDToHandlers.get(subscriptionID).send(connectionId, destination);
            logger.info("Sent MESSAGE frame to subscriptionId of: " + subscriptionID + destination);
=======
            if(subscriptionsIDToHandlers.get(subscriptionID) != null){
                subscriptionsIDToHandlers.get(subscriptionID).send(connectionId, messageFrame.toString());
            }
            else{
                if (connections.getCHbyConnectionID(connectionId) != null){
                    subscriptionsIDToHandlers.putIfAbsent(subscriptionID, connections.getCHbyConnectionID(connectionId));
                    subscriptionsIDToHandlers.get(subscriptionID).send(connectionId, messageFrame.toString());

                }
                else{
                    
                }
            }
            logger.info("Sent MESSAGE frame to subscriptionId of: " + subscriptionID + destWithoutSlash);
            connections.send(destWithoutSlash, messageFrame.toString());
>>>>>>> 652ec8cc85b7d36380900ed55be2020ade3cda8b
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

    public int getConnectionId() {
        return connectionId;
    }
}
