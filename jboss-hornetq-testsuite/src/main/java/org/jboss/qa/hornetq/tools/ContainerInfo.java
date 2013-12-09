package org.jboss.qa.hornetq.tools;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public final class ContainerInfo {

    private final String name;

    private final String ipAddress;

    private final int bytemanPort;


    public ContainerInfo(final String name, final String ipAddress, final int bytemanPort) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.bytemanPort = bytemanPort;
    }


    public String getName() {
        return name;
    }


    public String getIpAddress() {
        return ipAddress;
    }


    public int getBytemanPort() {
        return bytemanPort;
    }

}
