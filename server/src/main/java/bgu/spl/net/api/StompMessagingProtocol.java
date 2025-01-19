package bgu.spl.net.api;

import bgu.spl.net.srv.ConnectionsImpl;// Added the refrence by Tamar 15/1 

public interface StompMessagingProtocol<T>  {
	/**
	 * Used to initiate the current client protocol with it's personal connection ID and the connections implementation
	**/
    void start(int connectionId, ConnectionsImpl<T> connections);//changed fron Connections<T> to ConnectionsImpl<T> by Tamar 15/1
    
    T process(T message);
	
	/**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
}
