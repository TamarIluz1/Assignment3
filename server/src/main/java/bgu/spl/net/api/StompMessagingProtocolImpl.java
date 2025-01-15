package bgu.spl.net.api;

import bgu.spl.net.srv.ConnectionsImpl;
import java.util.Map;
import java.util.HashMap;

//Added this class by Tamar 15/1

public class StompMessagingProtocolImpl<T> implements StompMessagingProtocol<T> {


    @Override
    public void start(int connectionId, ConnectionsImpl<T> connections) {

    }

    @Override
    public void process(T message) {

    }

    @Override
    public boolean shouldTerminate() {
        return false;
    }
}
