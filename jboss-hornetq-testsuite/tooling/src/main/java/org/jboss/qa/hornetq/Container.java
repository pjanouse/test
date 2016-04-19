package org.jboss.qa.hornetq;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import javax.naming.Context;
import javax.naming.NamingException;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.constants.Constants;
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
    String getConnectionFactoryName();

    Context getContext(Constants.JNDI_CONTEXT_TYPE contextType) throws NamingException;

    String getHostname();
    int getHornetqPort();
    int getHornetqBackupPort();
    int getBytemanPort();
    Constants.CONTAINER_TYPE getContainerType();

    int getHttpPort();
    String getUsername();
    String getPassword();

    int getProcessId();

    void fail(Constants.FAILURE_TYPE failureType);

    String getServerVersion() throws FileNotFoundException;

    // cleanup operations
    void deleteDataFolder() throws IOException;

    void suspend() throws IOException;

    void resume() throws IOException;

    // ContainerController delegates
    void start();
    void start(Map<String,String> containerProperties);
    void startAdminOnly();

    int getJGroupsTcpPort();

    void stop();
    void kill();
    void waitForKill();
    void waitForKill(long timeout);
    void restart();
    void deploy(Archive archive);

    void undeploy(Archive archive);

    void undeploy(String archiveName);

    JournalExportImportUtils getExportImportUtil();
    JmxUtils getJmxUtils();
    JmxNotificationListener createJmxNotificationListener();
    JMSOperations getJmsOperations();

    void update(ContainerController controller);

    ContainerDef getContainerDefinition();
    PrintJournal getPrintJournal();

}
