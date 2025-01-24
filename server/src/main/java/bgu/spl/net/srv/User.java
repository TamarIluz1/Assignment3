package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class User<T> {
    private final String username;
    private String password;
    private boolean isLoggedIn;
    private ConnectionHandler<T> connectionHandler;
    private int connectionId;
    private ConcurrentHashMap<Integer, String> channels; // subscriptionId -> channelName
    private ConcurrentHashMap<String, Integer> channelToSubscriptionId; // channelName -> subscriptionId
 
    public User(String username, String password, ConnectionHandler<T> connectionHandler, int connectionId) {
        this.username = username;
        this.password = password;
        this.isLoggedIn = true;
        this.connectionHandler = connectionHandler;
        this.connectionId = connectionId;
        this.channels = new ConcurrentHashMap<>();
        this.channelToSubscriptionId = new ConcurrentHashMap<>();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        isLoggedIn = loggedIn;
    }

    public void setCH(ConnectionHandler<T> connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    public void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
    }

    public Integer getConnectionId(){
        return connectionId;
    }

    public void addSubscription(int subscriptionId, String channelName) {
        channels.put(subscriptionId, channelName);
        channelToSubscriptionId.put(channelName, subscriptionId);
    }

    public ConnectionHandler<T> getConnectionHandler() {
        return connectionHandler;
    }

    public void removeSubscription(int subscriptionId, String channelName) {
        channels.remove(subscriptionId);
        channelToSubscriptionId.remove(channelName);
    }

    public boolean isSubscriptionExist(int subscriptionId){
        return channels.containsKey(subscriptionId);
    }

    public String getChannelBySubscriptionId(int subscriptionId){
        return channels.get(subscriptionId);
    }

    public Integer getSubscriptionIdByChannel(String channel){
        return channelToSubscriptionId.get(channel);
    }

    public void logout(){
        isLoggedIn = false;
        channels = new ConcurrentHashMap<>();
        channelToSubscriptionId = new ConcurrentHashMap<>();
        setConnectionId(-1);
        setCH(null);
    }
}
