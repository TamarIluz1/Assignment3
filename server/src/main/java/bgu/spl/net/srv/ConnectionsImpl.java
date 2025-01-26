package bgu.spl.net.srv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import bgu.spl.net.impl.stomp.Frame;

public class ConnectionsImpl<T> implements Connections<T> {
    //TODO: implement this class according to the instructions by Tamar 15/1
    
    // Map connectionId to its ConnectionHandler
    //private ConcurrentMap<Integer, ConnectionHandler<T>> activeConnections; 
    
    // Map topic to set of subscribers (connectionIds)
    //private ConcurrentMap<String, Set<Integer>> topicSubscribers;

    //NEW ARCHITECTURE BY NOAM
    private ConcurrentHashMap<String, User<T>> userDetails; 
    private ConcurrentMap<String, Set<String>> channelSubscribers; // stored with USERNAME SET VALUE
    private ConcurrentMap<Integer, ConnectionHandler<T>> ActiveConnectionsToHandler; // connectionId -> ConnectionHandler
    private ConcurrentHashMap<Integer, String> connectionIdToUsername = new ConcurrentHashMap<>();
    Logger logger = Logger.getLogger("ConnectionsImpl");
    private AtomicInteger msgIdCounter = new AtomicInteger();

    //private ConcurrentHashMap<String, String> subscriptionsIdtoChannelName = new ConcurrentHashMap<>();
    //private ConcurrentHashMap<String, Set<String>> channeltoSubscriptions = new ConcurrentHashMap<>();
    //private ConcurrentHashMap<String, ConnectionHandler<String>> subscriptionsIDToHandlers = new ConcurrentHashMap<>(); //COMPLETELY WRONG
    //private ConcurrentHashMap<String, Integer> subscriptionIdToConnectionId = new ConcurrentHashMap();
    //private ConcurrentHashMap<String, Map<String, String>> connectionIdToMapsubsIDtoChannel = new ConcurrentHashMap();


    // public Map<String, ConnectionHandler<String>> getSubscriptionsIDToHandlers() {
    //     return subscriptionsIDToHandlers;
    // }
    // public Map<String, String> getSubscriptionsIdtoChannelName() {
    //     return subscriptionsIdtoChannelName;
    // }
    public Map<String, Set<String>> getChanneltoSubscriptions() {
        return channelSubscribers;
    }
    // public ConcurrentHashMap<String, Integer> getSubscriptionIdToConnectionId() {
    //     return subscriptionIdToConnectionId;
    // }

 
    public ConnectionsImpl() {
        userDetails = new ConcurrentHashMap<>();
        channelSubscribers = new ConcurrentHashMap<>();
        ActiveConnectionsToHandler = new ConcurrentHashMap<>();
        msgIdCounter.set(0);
    }



    // Send a regularmessage
    @Override
    public boolean send(int connectionId, T msg) {
        if(!connectionIdToUsername.containsKey(connectionId)){
            return false;
        }
        ConnectionHandler<T> handler = userDetails.get(connectionIdToUsername.get(connectionId)).getConnectionHandler();
        if (handler != null) {
            handler.send(connectionId,msg);
            return true;
        }
        System.out.println("handler is null");
        return false;
    }


    @Override
    public void send(String channel, T msg) {
        Set<String> subscribers = channelSubscribers.get(channel);
        if (subscribers != null) {
            for (String username : subscribers) {
                send(userDetails.get(username).getConnectionId(), msg);
            }
        }
    }

    public boolean checkLogin(String username, String password){
        // returns true iff the username exists and the password is correct
        return userDetails.containsKey(username) && userDetails.get(username).getPassword().equals(password);
    }


    @Override
    public void disconnect(int connectionId){
        synchronized(connectionIdToUsername){
            String usernameLoggedOut = connectionIdToUsername.get(connectionId);
            if (usernameLoggedOut == null) {
                return;
            }
            logger.info("Disconnecting " + usernameLoggedOut);
            User user = userDetails.get(usernameLoggedOut);
            synchronized(user){
            user.logout();
            }
            connectionIdToUsername.remove(connectionId);
            // Remove from all subscribed topics
            for (Set<String> subscribers : channelSubscribers.values()) {
                subscribers.remove(usernameLoggedOut);
            }
            

        }

        
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        ActiveConnectionsToHandler.putIfAbsent(connectionId, handler);
    }

    public void addUserConnection(int connectionId, String username) {
        connectionIdToUsername.put(connectionId, username);
        userDetails.get(username).setConnectionId(connectionId);
        userDetails.get(username).setCH(ActiveConnectionsToHandler.get(connectionId));
    }

    public void subscribe(int connectionId, String channel, Integer subscriptionId) {
        String usernameRegistered = connectionIdToUsername.get(connectionId);
        logger.info("Subscribing " + usernameRegistered + " to channel " + channel);
        User currUser = userDetails.get(usernameRegistered);

        channelSubscribers.putIfAbsent(channel, new HashSet<>());
        channelSubscribers.get(channel).add(usernameRegistered);
        logger.info("Subscribed logg " + subscriptionId + " to channel " + channel);
        currUser.addSubscription(subscriptionId, channel);
    }

    public void unsubscribe(int connectionId, String channel, Integer subscriptionId) {
        Set<String> subscribers = channelSubscribers.get(channel);
        User currUser = userDetails.get(connectionIdToUsername.get(connectionId));
        if (subscribers != null) { // TODO when it will be null?
            subscribers.remove(connectionIdToUsername.get(connectionId));
            if (subscribers.isEmpty()) {
                channelSubscribers.remove(channel);
            }
        }
        
        currUser.removeSubscription(subscriptionId, channel);
    }

    public String getPasswordByUsername(String name){
        return userDetails.get(name).getPassword();
    }

    public Integer getNewMessageID(){
        return msgIdCounter.getAndIncrement();
    }

    public User<T> getUserDetails(String username){
        if(username == null || !userDetails.containsKey(username)){
            return null;
        }
        return userDetails.get(username);
    }

    public ConcurrentHashMap<String, User<T>> getUsers(){
        return userDetails;
    }

    public Set<String> getSubscribers(String channel){
        return channelSubscribers.get(channel);
    }

    public ConnectionHandler getCHbyConnectionID(int connectionId){
        if(!ActiveConnectionsToHandler.containsKey(connectionId)){
            return null;
        }
        return ActiveConnectionsToHandler.get(connectionId);
    }

    public String getUserByConnectionId(int connectionId){
        return connectionIdToUsername.get(connectionId);
    }


    
}
