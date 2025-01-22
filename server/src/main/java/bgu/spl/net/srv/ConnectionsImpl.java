package bgu.spl.net.srv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.impl.stomp.Frame;

public class ConnectionsImpl<T> implements Connections<T> {
    //TODO: implement this class according to the instructions by Tamar 15/1
    
    // Map connectionId to its ConnectionHandler
    //private ConcurrentMap<Integer, ConnectionHandler<T>> activeConnections; 
    
    // Map topic to set of subscribers (connectionIds)
    //private ConcurrentMap<String, Set<Integer>> topicSubscribers;

    //NEW ARCHITECTURE BY NOAM
    private ConcurrentHashMap<String, User<T>> userDetails; 
    private ConcurrentMap<String, Set<Integer>> channelSubscribers;
    private ConcurrentMap<Integer, ConnectionHandler<T>> ActiveConnectionsToHandler;
    private AtomicInteger msgIdCounter = new AtomicInteger();

    public ConnectionsImpl() {
        userDetails = new ConcurrentHashMap<>();
        channelSubscribers = new ConcurrentHashMap<>();
        ActiveConnectionsToHandler = new ConcurrentHashMap<>();
        msgIdCounter.set(0);
    }



    // Send a regularmessage
    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = ActiveConnectionsToHandler.get(connectionId);
        if (handler != null) {
            handler.send(connectionId,msg);
            return true;
        }
        return false;
    }


    @Override
    public void send(String channel, T msg) {
        Set<Integer> subscribers = channelSubscribers.get(channel);
        if (subscribers != null) {
            for (int connectionId : subscribers) {
                send(connectionId, msg);
            }
        }
    }

    public boolean checkLogin(String username, String password){
        // returns true iff the username exists and the password is correct
        return userDetails.containsKey(username) && userDetails.get(username).equals(password);
    }


    @Override
    public void disconnect(int connectionId) {
        ActiveConnectionsToHandler.remove(connectionId);
        // Remove from all subscribed topics
        for (Set<Integer> subscribers : channelSubscribers.values()) {
            subscribers.remove(connectionId);
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        ActiveConnectionsToHandler.putIfAbsent(connectionId, handler);
    }

    public void subscribe(int connectionId, String channel) {
        channelSubscribers.putIfAbsent(channel, new HashSet<>());
        channelSubscribers.get(channel).add(connectionId);
    }

    public void unsubscribe(int connectionId, String channel) {
        Set<Integer> subscribers = channelSubscribers.get(channel);
        if (subscribers != null) {
            subscribers.remove(connectionId);
            if (subscribers.isEmpty()) {
                channelSubscribers.remove(channel);
            }
        }
    }

    public String getPasswordByUsername(String name){
        return userDetails.get(name).getPassword();
    }

    public int getNewMessageID(){
        return msgIdCounter.getAndIncrement();
    }

    public User getUserDetails(String username){
        return userDetails.get(username);
    }

    public ConcurrentHashMap<String, User<T>> getUsers(){
        return userDetails;
    }

    public Set<Integer> getSubscribers(String channel){
        return channelSubscribers.get(channel);
    }

    public ConnectionHandler getCHbyConnectionID(int connectionId){
        if(!ActiveConnectionsToHandler.containsKey(connectionId)){
            return null;
        }
        return ActiveConnectionsToHandler.get(connectionId);
    }
    
}
