package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class User<T> {
    private final String username;
    private String password;
    private boolean isLoggedIn;
    private ConnectionHandler<T> connectionHandler;
    private int connectionId;
    private final ConcurrentHashMap<Integer, String> channels; 
 
    public User(String username, String password, ConnectionHandler<T> connectionHandler, int connectionId) {
        this.username = username;
        this.password = password;
        this.isLoggedIn = true;
        this.connectionHandler = connectionHandler;
        this.connectionId = connectionId;
        this.channels = new ConcurrentHashMap<>();
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
}
