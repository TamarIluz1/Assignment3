package bgu.spl.net.srv;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsImpl<T> implements Connections<T> {
    //TODO: implement this class according to the instructions by Tamar 15/1
    
    // Map connectionId to its ConnectionHandler
    private ConcurrentMap<Integer, ConnectionHandler<T>> activeConnections; 
    
    // Map topic to set of subscribers (connectionIds)
    private ConcurrentMap<String, Set<Integer>> topicSubscribers;

    //NEW ARCHITECTURE BY NOAM
    private ConcurrentHashMap<String, String> logDetails; 
    private ConcurrentMap<String, Integer> userToConnectionID;
    private ConcurrentMap<Integer, ConnectionHandler<T>> ActiveConnectionToHandler;
    private AtomicInteger msgIdCounter = new AtomicInteger();

    public ConnectionsImpl() {
        activeConnections = new ConcurrentHashMap<>();
        topicSubscribers = new ConcurrentHashMap<>();
        logDetails = new ConcurrentHashMap<>();
    }


    // Send a regularmessage
    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(connectionId,msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        Set<Integer> subscribers = topicSubscribers.get(channel);
        if (subscribers != null) {
            for (int connectionId : subscribers) {
                send(connectionId, msg);
            }
        }
    }

    public void loginUser(String username){

    }


    @Override
    public void disconnect(int connectionId) {
        activeConnections.remove(connectionId);
        // Remove from all subscribed topics
        for (Set<Integer> subscribers : topicSubscribers.values()) {
            subscribers.remove(connectionId);
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    public void subscribe(int connectionId, String channel) {
        topicSubscribers.putIfAbsent(channel, new HashSet<>());
        topicSubscribers.get(channel).add(connectionId);
    }

    public void unsubscribe(int connectionId, String channel) {
        Set<Integer> subscribers = topicSubscribers.get(channel);
        if (subscribers != null) {
            subscribers.remove(connectionId);
            if (subscribers.isEmpty()) {
                topicSubscribers.remove(channel);
            }
        }
    }

    public String getPasswordByUsername(String name){
        return logDetails.get(name);
    }
    
}
