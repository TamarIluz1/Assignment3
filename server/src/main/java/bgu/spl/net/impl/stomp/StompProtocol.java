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
                return handleError("Unknown command: " + command,-1);
        }
    }

    private String handleConnect(Frame frame) {
        logger.info("Handling CONNECT frame");
        String acceptVersion = frame.getHeaders().get("accept-version");
        String host = frame.getHeaders().get("host");
        String username = frame.getHeaders().get("login");
        String password = frame.getHeaders().get("passcode");
        if (acceptVersion == null || host == null) {
            return handleError("Missing accept-version or host in CONNECT frame",-1);
        }

        for (User<String> user : connections.getUsers().values()) {
            if (user.isLoggedIn() && user.getConnectionId() == connectionId) {
                return handleError("The client is already logged in, log out before trying again\n\n^@",-1);
            }
        }
        

        if (connections.getUserDetails(username) != null && connections.getUserDetails(username).isLoggedIn()) {
            if (!connections.checkLogin(username, password)) {
                return handleError("Wrong password\n\n^@",-1);
            }
            return handleError("User already logged in\n\n^@",-1);
        }

        Frame connectedFrame = new Frame("CONNECTED");
        if (connections.getUsers().containsKey(username) ) {
            // login existing user
            logger.info("PASSWORD " + password);
            if (!connections.checkLogin(username, password)) {
                return handleError("Wrong password\n\n^@",-1);
            }
            // login details are valid
            loginExistingUser(username);
            // Send CONNECTED frame back to client
            connectedFrame.addHeader("version", "1.2");
            connectedFrame.setBody(null);
            connections.addUserConnection(connectionId, username);
            logger.info("Sent CONNECTED frame of existing user" + username);
            return connectedFrame.toString();
            
            
        } else {
            createUser(new User<String>(username, password, (ConnectionHandler)connections.getCHbyConnectionID(connectionId), connectionId));
                connectedFrame.addHeader("version", "1.2");
                connectedFrame.setBody(null);
            connections.addUserConnection(connectionId, username);
            logger.info("Sent CONNECTED frame - new user" + username  + connections.getCHbyConnectionID(connectionId));
            return connectedFrame.toString();
            
        }

    }

    public void loginExistingUser(String username) {
        User<String> logged = connections.getUserDetails(username);
        logged.setLoggedIn(true);
        logged.setConnectionId(connectionId);
    }

    public void createUser(User u) {
        connections.getUsers().put(u.getUsername(), u);
        logger.info("Adding user of username "+ u.getUsername());
        connections.addUserConnection(connectionId, u.getUsername());
    }


    private String handleSubscribe(Frame frame) {

        logger.info("Handling SUBSCRIBE frame");
        String destination = frame.getHeaders().get("destination");
        Integer subscriptionID = Integer.parseInt(frame.getHeaders().get("id"));
        String receiptId = frame.getHeaders().get("receipt");
        Frame receiptFrame = new Frame("RECEIPT");
        receiptFrame.addHeader("receipt-id", receiptId);
        receiptFrame.setBody(null);

        if (destination == null || subscriptionID == null) {
            return handleError("Missing destination or id in SUBSCRIBE frame",-1);
        }

        if (connections.getChanneltoSubscriptions().containsKey(destination)){
            logger.info("Checking if already subscribed to this channel");
            for (String userSubscribed : connections.getChanneltoSubscriptions().get(destination)){
                // for every subscriptionId, well check its not the of the current connection.
                if (connections.getUserByConnectionId(connectionId).equals(userSubscribed)){
                    return handleError("Already subscribed to this channel\n\n^@",Integer.parseInt(receiptId));
                }
            }
        }

        logger.info("subscribing user  of connectionId: " + connectionId + " to channel: " + destination + " with ID: " + subscriptionID);
        //need to subscribe to the connections 22.1
        connections.subscribe(connectionId, destination, subscriptionID);

        //connections.getSubscriptionsIdtoChannelName().put(subscriptionID, destination);
        String currUser = connections.getUserByConnectionId(connectionId);
        if (connections.getChanneltoSubscriptions().containsKey(destination)) {
            connections.getChanneltoSubscriptions().get(destination).add(currUser);
        }
        else{
            logger.info("Creating new channel: " + destination);
            connections.getChanneltoSubscriptions().put(destination, new HashSet<String>());
            connections.getChanneltoSubscriptions().get(destination).add(currUser);
        }

     
        logger.info("Adding to subscriptionsIDToHandlers: " + subscriptionID + " -> " + connections.getCHbyConnectionID(connectionId));

        logger.info("Subscribed to destination: " + destination + " with ID: " + subscriptionID);
        // Acknowledge subscription
        return receiptFrame.toString();
    }

    private String handleUnsubscribe(Frame frame) {
        User user = connections.getUserDetails(connections.getUserByConnectionId(connectionId));
        logger.info("Handling UNSUBSCRIBE frame" + frame.getHeaders().get("id"));
        String subscriptionID = frame.getHeaders().get("id");
        String receiptId = frame.getHeaders().get("receipt");
        Frame receiptFrame = new Frame("RECEIPT");
        receiptFrame.addHeader("receipt-id", receiptId);
        receiptFrame.setBody(null);
        if (subscriptionID == null || !user.isSubscriptionExist(Integer.parseInt(subscriptionID))) {
            return handleError("not subscribed to the chanel to exit",Integer.parseInt(receiptId));
        }
        Integer sId = Integer.parseInt(subscriptionID);
        String channelName = user.getChannelBySubscriptionId(sId);
        logger.info("Unsubscribed from ID: " + sId);
        
        //need to unsubscribe to the connections 22.1
        connections.unsubscribe(connectionId, channelName, sId);
        return receiptFrame.toString();
    }

    private String handleSend(Frame frame) {
        logger.info("Handling SEND frame");
        String destination = frame.getHeaders().get("destination");
        logger.info("DESTINATION" + destination);

        if (destination == null){
            return handleError("missing destination in SEND frame",-1);
        } 
        if (destination.charAt(0) == '/') {
            destination = destination.substring(1); // removing slash if exists
        }
        User user = connections.getUserDetails(connections.getUserByConnectionId(connectionId));    
        if (!user.isRegistedToChannel(destination)) {
            return handleError("User is not subscribed to channel: " + destination,-1);
        }

        String bigMessage = "";
        // Broadcast message to all subscribers
        for (String userSubbed : connections.getChanneltoSubscriptions().get(destination)) {
            // we send the message to all the subscribers
            User userSubscribed = connections.getUserDetails(userSubbed);
            Frame messageFrame = new Frame("MESSAGE");
            messageFrame.addHeader("subscription", connections.getUserDetails(userSubbed).getSubscriptionIdByChannel(destination).toString());
            messageFrame.addHeader("message-id", (connections.getNewMessageID()).toString()); 
            messageFrame.addHeader("destination", "/"+destination);
            messageFrame.setBody(frame.getBody());
            //connections.send(Integer.parseInt(subscriptionID), messageFrame.toString());
            bigMessage = bigMessage + messageFrame.toString();
            // connections.getSubscriptionsIDToHandlers().get(subscriptionID).send(connectionId, messageFrame.toString());
            connections.getUserDetails(userSubbed).getConnectionHandler().send(userSubscribed.getConnectionId() ,messageFrame.toString()); // i dont like this
            logger.info("Sent MESSAGE frame to user of: " + userSubbed + destination + "by connectionID " + userSubscribed.getConnectionId());
        }
        return null;
    }

    private String handleDisconnect(Frame frame) {
        // TODO: handle DISCONNECT frame BY TAMAR 15/1
        logger.info("Handling DISCONNECT frame");
        String receiptId = frame.getHeaders().get("receipt");
        if (receiptId != null) {
            Frame receiptFrame = new Frame("RECEIPT");
            User user = connections.getUserDetails(connections.getUserByConnectionId(connectionId));
            if(user != null){
                user.setLoggedIn(false);
                user.setConnectionId(-1);
            }
            connections.disconnect(connectionId);
            receiptFrame.addHeader("receipt-id", receiptId);
            logger.info("Sent RECEIPT frame with receipt-id: " + receiptId);
            return receiptFrame.toString();
        }
        User user = connections.getUserDetails(connections.getUserByConnectionId(connectionId));
        if(user != null){
            user.setLoggedIn(false);
            user.setConnectionId(-1);
        }
        connections.disconnect(connectionId);
        shouldTerminate = true;
        logger.info("Disconnected connection ID: " + connectionId);
        return null;
    }

    private String handleError(String errorMessage,int receiptId) {
        if(receiptId != -1){
            logger.severe("Handling ERROR frame: " + errorMessage);
            Frame errorFrame = new Frame("ERROR");
            errorFrame.addHeader("receipt-id",receiptId+"");
            errorFrame.addHeader("message", errorMessage);
            User user = connections.getUserDetails(connections.getUserByConnectionId(connectionId));
            if(user != null){
                user.setLoggedIn(false);
                user.setConnectionId(-1);

            }
            connections.disconnect(connectionId);
            shouldTerminate = true;
            return errorFrame.toString();
        }
        else{
            Frame errorFrame = new Frame("ERROR");
            errorFrame.addHeader("receipt-id","massage-"+connections.getNewMessageID().toString());
            errorFrame.addHeader("message", errorMessage);
            User user = connections.getUserDetails(connections.getUserByConnectionId(connectionId));
            if(user != null){
                user.setLoggedIn(false);
                user.setConnectionId(-1);
            }
            connections.disconnect(connectionId);
            shouldTerminate = true;
            return errorFrame.toString();

        }
        
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public int getConnectionId() {
        return connectionId;
    }
}
