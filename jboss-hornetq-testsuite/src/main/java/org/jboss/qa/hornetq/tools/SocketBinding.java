package org.jboss.qa.hornetq.tools;


/**
 * Enum with default socket binding informations for EAP.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public enum SocketBinding {

    LEGACY_JNP("jnp", 1099),
    LEGACY_RMI("rmi-jnp", 5599),
    LEGACY_REMOTING("legacy-remoting", 4873);

    private final String bindingName;

    private final int bindingPort;


    private SocketBinding(final String name, final int port) {
        this.bindingName = name;
        this.bindingPort = port;
    }


    public String getName() {
        return this.bindingName;
    }


    public int getPort() {
        return this.bindingPort;
    }

}
