package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;

public interface MessagingProtocol<T> {
    
    void start(int connectionId, Connections<T> connections);//changed fron Connections<T> to ConnectionsImpl<T> by Tamar 15/1
    /**
     * process the given message 
     * @param msg the received message
     * @return the response to send or null if no response is expected by the client
     */
    T process(T msg);
 
    /**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
 
}