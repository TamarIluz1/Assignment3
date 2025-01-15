package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;// Added the refrence by Tamar 15/1

public interface MessagingProtocol<T> {
    
    void start(int connectionId, ConnectionsImpl<T> connections);//changed fron Connections<T> to ConnectionsImpl<T> by Tamar 15/1
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