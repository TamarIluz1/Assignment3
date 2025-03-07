package bgu.spl.net.impl.rci;

import bgu.spl.net.api.MessagingProtocol;
import java.io.Serializable;
import bgu.spl.net.srv.Connections;


public class RemoteCommandInvocationProtocol<T> implements MessagingProtocol<Serializable> {

    private T arg;

    @Override
    public void start(int connectionId, Connections<Serializable> connections) {
        // Do nothing
    }

    public RemoteCommandInvocationProtocol(T arg) {
        this.arg = arg;
    }

    @Override
    public Serializable process(Serializable msg) {
        return ((Command) msg).execute(arg);
    }

    @Override
    public boolean shouldTerminate() {
        return false;
    }

}
