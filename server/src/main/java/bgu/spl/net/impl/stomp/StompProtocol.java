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
                logger.info("Sent CONNECTED frame of existing user" + username);
                return connectedFrame.toString();
            }
            
        } else {
            createUser(new User(username, password, (ConnectionHandler)connections.getCHbyConnectionID(connectionId), connectionId));
                connectedFrame.addHeader("version", "1.2");
                connectedFrame.setBody(null);
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
        String receiptId = frame.getHeaders().get("receipt");
        Frame receiptFrame = new Frame("RECEIPT");
        receiptFrame.addHeader("recipt", receiptId);
        receiptFrame.setBody(null);

        if (destination == null || id == null) {
            return handleError("Missing destination or id in SUBSCRIBE frame");
        }

        if (connections.getChanneltoSubscriptions().containsKey(destination)){
            logger.info("Checking if already subscribed to this channel");
            for (String existingSubscription : connections.getChanneltoSubscriptions().get(destination)){
                // for every subscriptionId, well check its not the of the current connection.
                logger.info("existing checked:" + connectionId + "compared to " +connections.getSubscriptionIdToConnectionId().get(existingSubscription));
                if (connections.getSubscriptionIdToConnectionId().get(existingSubscription).equals(connectionId)){
                    return handleError("ERROR\nmessage:Already subscribed to this channel\n\n^@");
                }
            }
        }

        // Set<Integer> subscribers = connections.getSubscribers(destination);
        // if(subscribers != null){
        //     for (Integer sub : subscribers) {
        //         if (sub == connectionId) {
        //             return handleError("ERROR\nmessage:Already subscribed to this channel\n\n^@");
        //         }
        //     }
        // }
        

        //need to subscribe to the connections 22.1
        connections.subscribe(connectionId, destination);

        connections.getSubscriptionsIdtoChannelName().put(id, destination);
        if (connections.getChanneltoSubscriptions().containsKey(destination)) {
            connections.getChanneltoSubscriptions().get(destination).add(id);
        }
        else{
            logger.info("Creating new channel: " + destination);
            connections.getChanneltoSubscriptions().put(destination, new HashSet<String>());
            connections.getChanneltoSubscriptions().get(destination).add(id);
        }
        connections.getSubscriptionsIDToHandlers().put(id, connections.getCHbyConnectionID(connectionId));
        connections.getSubscriptionIdToConnectionId().put(id, connectionId);

     
        logger.info("Adding to subscriptionsIDToHandlers: " + id + " -> " + connections.getCHbyConnectionID(connectionId));

        logger.info("Subscribed to destination: " + destination + " with ID: " + id);
        // Acknowledge subscription
        return receiptFrame.toString();
    }

    private String handleUnsubscribe(Frame frame) {
        logger.info("Handling UNSUBSCRIBE frame");
        String id = frame.getHeaders().get("id");
        String receiptId = frame.getHeaders().get("receipt");
        Frame receiptFrame = new Frame("RECEIPT");
        receiptFrame.addHeader("recipt", receiptId);
        receiptFrame.setBody(null);
        if (id == null || !connections.getSubscriptionsIdtoChannelName().containsKey(id)) {
            return handleError("Invalid or missing id in UNSUBSCRIBE frame");
        }
        String channelName = connections.getSubscriptionsIdtoChannelName().get(id);
        connections.getSubscriptionsIdtoChannelName().remove(id);
        connections.getSubscriptionsIDToHandlers().remove(id);
        connections.getChanneltoSubscriptions().get(channelName).remove(id);
        connections.getSubscriptionIdToConnectionId().remove(id);
        logger.info("Unsubscribed from ID: " + id);

        //need to unsubscribe to the connections 22.1
        connections.unsubscribe(connectionId, channelName);

        connections.getSubscriptionsIdtoChannelName().remove(id);
        logger.info("Unsubscribed from ID: " + id);
        return receiptFrame.toString();
    }

    private String handleSend(Frame frame) {
        logger.info("Handling SEND frame");
        String destination = frame.getHeaders().get("destination");
        logger.info("DESTINATION" + destination);

        if (destination == null || !connections.getSubscriptionsIdtoChannelName().containsValue(destination.substring(1))) {
            return handleError("Invalid or missing destination in SEND frame");
        }
        // String destWithoutSlash = destination.substring(1);
        // // Broadcast message to all subscribers

        // Frame messageFrame = new Frame("MESSAGE");
            
        // messageFrame.addHeader("subscription", ""+connectionId);
        // messageFrame.addHeader("message-id", ((Integer)connections.getNewMessageID()).toString()); // Add appropriate message id
        // messageFrame.addHeader("destination", destination);
        // messageFrame.setBody(frame.getBody());
        // logger.info("Sent MESSAGE frame to subscriptionId of: " + connectionId + destWithoutSlash);
        // connections.send(destWithoutSlash,  messageFrame.toString());

        if (destination.charAt(0) == '/') {
            destination = destination.substring(1); // removing slash if exists
        }
        // Broadcast message to all subscribers
        for (String subscriptionID : connections.getChanneltoSubscriptions().get(destination)) {
            // we send the message to all the subscribers

            Frame messageFrame = new Frame("MESSAGE");
            messageFrame.addHeader("subscription", subscriptionID);
            messageFrame.addHeader("message-id", ((Integer)connections.getNewMessageID()).toString()); // Add appropriate message id
            messageFrame.addHeader("destination", "/"+destination);
            messageFrame.setBody(frame.getBody());
            //connections.send(Integer.parseInt(subscriptionID), messageFrame.toString());
            logger.info("Sent MESSAGE frame to subscription: " + subscriptionID);
            connections.getSubscriptionsIDToHandlers().get(subscriptionID).send(connectionId, messageFrame.toString());
            logger.info("Sent MESSAGE frame to subscriptionId of: " + subscriptionID + destination);

        }
        return null;
    }

    private String handleDisconnect(Frame frame) {
        // TODO: handle DISCONNECT frame BY TAMAR 15/1
        logger.info("Handling DISCONNECT frame");
        String receiptId = frame.getHeaders().get("receipt");
        if (receiptId != null) {
            Frame receiptFrame = new Frame("RECEIPT");
            connections.disconnect(connectionId);
            receiptFrame.addHeader("receipt-id", receiptId);
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
