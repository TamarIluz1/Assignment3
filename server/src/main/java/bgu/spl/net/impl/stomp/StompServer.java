package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this

        // Added all this main code by Tamar 15/1

        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <tpc/reactor>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String mode = args[1];

        if (mode.equals("tpc")) {
            startTPCServer(port);
        } else if (mode.equals("reactor")) {
            startReactorServer(port);
        } else {
            System.out.println("Unknown mode: " + mode);
            System.out.println("Supported modes: tpc, reactor");
        }
    }


    private static void startTPCServer(int port) {
        //TODO: implement this added func by Tamar 15/1
        
    }


    private static void startReactorServer(int port) {
        //TODO: implement this added func by Tamar 15/1
    }
    




}
