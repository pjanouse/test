package org.jboss.qa.tools;

import java.net.*;
import org.apache.log4j.Logger;

/**
 * This proxy routes multicast from one multicast group for example 233.1.2.99 to another
 * multicast group for example 233.1.2.1.
 * 
 * The goal is that one server sends multicast packets to one group and listen on another.
 * In this was a cluster can be disconnected.
 * 
 * @author mnovak@redhat.com
 */
public class MulticastProxy extends Thread {

    
    // Logger
    private static final Logger log = Logger.getLogger(MulticastProxy.class);
    
    private String sourceMulticastGroup;
    private int sourceMulticastPort;
    private String destinationMulticastGroup;
    private int destinationMulticastPort;
    private boolean stop = false;

    public MulticastProxy(String sourceMulticastGroup, int sourceMulticastPort, String destinationMulticastGroup,
            int destinationMulticastPort) {

        this.sourceMulticastGroup = sourceMulticastGroup;
        this.sourceMulticastPort = sourceMulticastPort;
        this.destinationMulticastGroup = destinationMulticastGroup;
        this.destinationMulticastPort = destinationMulticastPort;

    }

    public void run() {
        try {

            MulticastSocket sourceMulticastSocket = new MulticastSocket(sourceMulticastPort);  // Create socket
            sourceMulticastSocket.joinGroup(InetAddress.getByName(sourceMulticastGroup));
                       
            DatagramSocket destSocket = new DatagramSocket();
            destSocket.setSoTimeout(500);
            
            log.info("Proxy from: " + sourceMulticastGroup + ":" + sourceMulticastPort
                        + " to: " + destinationMulticastGroup + ":" + destinationMulticastPort + " was created");
            
            do {
                byte[] line = new byte[4096];

                DatagramPacket pkt = new DatagramPacket(line, line.length,
                        InetAddress.getByName(destinationMulticastGroup), destinationMulticastPort);
                
                sourceMulticastSocket.receive(pkt);
                
                log.info("Packet received from source: " + sourceMulticastGroup + ":" + sourceMulticastPort
                        + " content: " + line + " dest host:port - "
                        + pkt.getAddress());

                DatagramPacket pkt1 = new DatagramPacket(line, line.length,
                        InetAddress.getByName(destinationMulticastGroup), destinationMulticastPort);
                
                destSocket.send(pkt1);

                log.info("Packet received from source: " + sourceMulticastGroup + ":" + sourceMulticastPort
                        + " content: " + pkt1.getData().length + " dest host:port - "
                        + pkt1.getAddress());

            } while (!stop);

            sourceMulticastSocket.close();
            destSocket.close();
        } catch (Exception err) {
            log.error("Multicast proxy got critical error: " , err);
        }
    }

    /**
     * @return the stop
     */
    public boolean isStop() {
        return stop;
    }

    /**
     * @param stop the stop to set
     */
    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public static void main(String[] args) throws InterruptedException {

        MulticastProxy mp12 = new MulticastProxy("233.1.2.99", 9876, "233.1.2.1", 9876);
        MulticastProxy mp21 = new MulticastProxy("233.1.2.99", 9876, "233.1.2.2", 9876);
        mp12.start();
        mp21.start();
        mp12.join();
        mp21.join();

    }
}