package org.jboss.qa.tools;

import org.apache.log4j.Logger;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * This proxy routes multicast from one multicast group for example 233.1.2.99 to another
 * multicast group for example 233.1.2.1.
 * <p/>
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

    private LinkedList<byte[]> sendPackets = new LinkedList<byte[]>();

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

                log.debug("Packet received from source: " + sourceMulticastGroup + ":" + sourceMulticastPort
                        + " content: " + line + " dest host:port - "
                        + pkt.getAddress());

                // if we sent this packet before then don't send it again to prevent multicast packet flooding
                for (byte[] content : sendPackets)  {
                    if (Arrays.equals(content, line)) {
                        sendPackets.remove(content);
                        log.debug("Packet received from source: " + sourceMulticastGroup + ":" + sourceMulticastPort
                                + " content: " + line + " and destination host:port - "
                                + pkt.getAddress() + " is DUPLICATE - DON'T SEND IT");
                        break;
                    }
                }

                sendPackets.add(line);

                DatagramPacket pkt1 = new DatagramPacket(line, line.length,
                        InetAddress.getByName(destinationMulticastGroup), destinationMulticastPort);

                destSocket.send(pkt1);

                log.debug("Packet received from source: " + sourceMulticastGroup + ":" + sourceMulticastPort
                        + " content: " + pkt1.getData().length + " dest host:port - "
                        + pkt1.getAddress());

                // if linked list is too big then remove first
                while (sendPackets.size() > 100)    {
                    log.info("Remove first packets from list. Sent packets size: " + sendPackets.size());
                    sendPackets.removeFirst();
                }

            } while (!stop);

            sourceMulticastSocket.close();
            destSocket.close();
            log.info("Proxy from: " + sourceMulticastGroup + ":" + sourceMulticastPort
                    + " to: " + destinationMulticastGroup + ":" + destinationMulticastPort + " was stopped");
        } catch (Exception err) {
            log.error("Multicast proxy got critical error: ", err);
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

    public static void main(String[] args) throws InterruptedException, RemoteException {

//        ControllableProxy proxy1 = new SimpleProxyServer("192.168.1.1", 5445, 43821);
//        ControllableProxy proxy2 = new SimpleProxyServer("192.168.1.2", 5445, 43812);
//        proxy1.start();
//        proxy2.start();

        MulticastProxy mp12 = new MulticastProxy("233.1.2.1", 9876, "233.1.2.4", 9876);
        mp12.start();
        MulticastProxy mp21 = new MulticastProxy("233.1.2.2", 9876, "233.1.2.3", 9876);
        mp21.start();

        ControllableProxy proxy1 = new SimpleProxyServer("192.168.40.2", 5445, 43812);
        ControllableProxy proxy2 = new SimpleProxyServer("10.34.3.115", 5445, 43821);
        proxy1.start();
        proxy2.start();

        mp21.join();
        mp12.join();


    }
}