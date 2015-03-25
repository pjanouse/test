package org.jboss.qa.hornetq;


import java.io.FileNotFoundException;
import java.io.IOException;
import javax.naming.Context;
import javax.naming.NamingException;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.MulticastAddressUtils;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.jboss.shrinkwrap.api.Archive;


public interface Container {

    // multicast address is shared across all containers
    public static String MCAST_ADDRESS = MulticastAddressUtils.getMulticastAddress();

    void init(String containerName, int containerIndex, ArquillianDescriptor arquillianDescriptor,
            ContainerController containerController);

    // basic container info
    String getName();
    String getServerHome();
    int getPort();
    int getJNDIPort();
    int getPortOffset();
    Context getContext() throws NamingException;
    String getHostname();
    int getHornetqPort();
    int getHornetqBackupPort();
    int getBytemanPort();
    HornetQTestCaseConstants.CONTAINER_TYPE getContainerType();

    int getHttpPort();
    String getUsername();
    String getPassword();

    String getServerVersion() throws FileNotFoundException;

    // cleanup operations
    void deleteDataFolder() throws IOException;

    // ContainerController delegates
    void start();
    void stop();
    void kill();
    void restart();
    void deploy(Archive archive);

    void undeploy(Archive archive);

    void undeploy(String archiveName);

    JournalExportImportUtils getExportImportUtil();
    JmxUtils getJmsUtils();
    JmxNotificationListener createJmxNotificationListener();
    JMSOperations getJmsOperations();

    void update(ContainerController controller, Deployer deployer);
}
