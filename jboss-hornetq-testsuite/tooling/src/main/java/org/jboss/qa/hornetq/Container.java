package org.jboss.qa.hornetq;


import javax.naming.Context;
import javax.naming.NamingException;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;


public interface Container {

    void init(String containerName, int containerIndex, ArquillianDescriptor arquillianDescriptor,
            ContainerController containerController, Deployer deployer);

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

    // ContainerController delegates
    void start();
    void stop();
    void kill();

    // Deployer delegates
    void deploy(String deployment);
    void undeploy(String deployment);

    JournalExportImportUtils getExportImportUtil();
    JmxUtils getJmsUtils();
    JmxNotificationListener createJmxNotificationListener();

}