package org.jboss.qa.management.cli;

/**
 * @author rhatlapa (rhatlapa@redhat.com)
 */
public class CliConfiguration {
    private String host = "127.0.0.1";
    private int port = 9999;
    private String user = "";
    private char[] password = "".toCharArray();

    public CliConfiguration() {
    }

    public CliConfiguration(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public CliConfiguration(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password.toCharArray();
    }

    public CliConfiguration(String user, String password) {
        this.user = user;
        this.password = password.toCharArray();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    public char[] getPassword() {
        return password;
    }
}
