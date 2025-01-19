package bgu.spl.net.srv;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionsImpl<T> implements Connections<T> {
    //TODO: implement this class according to the instructions by Tamar 15/1
    
    // Map connectionId to its ConnectionHandler
    private ConcurrentMap<Integer, ConnectionHandler<T>> activeConnections;
    
    // Map topic to set of subscribers (connectionIds)
    private ConcurrentMap<String, Set<Integer>> topicSubscribers;

    public ConnectionsImpl() {
        activeConnections = new ConcurrentHashMap<>();
        topicSubscribers = new ConcurrentHashMap<>();
    }

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
    
}
