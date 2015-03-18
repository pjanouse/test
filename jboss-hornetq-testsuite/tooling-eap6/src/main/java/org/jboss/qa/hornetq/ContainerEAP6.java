package org.jboss.qa.hornetq;


import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.ServiceLoader;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.config.descriptor.api.GroupDef;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.qa.hornetq.apps.jmx.JmxNotificationListener;
import org.jboss.qa.hornetq.apps.jmx.JmxUtils;
import org.jboss.qa.hornetq.tools.HornetQAdminOperationsEAP6;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.ProcessIdUtils;
import org.jboss.qa.hornetq.tools.journal.JournalExportImportUtils;
import org.kohsuke.MetaInfServices;


@MetaInfServices
public class ContainerEAP6 implements Container {

    private static final Logger log = Logger.getLogger(ContainerEAP6.class);

    private JmxUtils jmxUtils = null;
    private JournalExportImportUtils journalExportImportUtils = null;

    private int containerIndex = 0;
    private ContainerDef containerDef = null;
    private ContainerController containerController = null;
    private Deployer deployer = null;

    private Integer portOffset = null;


    @Override
    public void init(String containerName, int containerIndex, ArquillianDescriptor arquillianDescriptor,
            ContainerController containerController, Deployer deployer) {

        this.containerIndex = containerIndex;
        this.containerController = containerController;
        this.deployer = deployer;
        this.containerDef = getContainerDefinition(containerName, arquillianDescriptor);
    }


    @Override
    public String getName() {
        return containerDef.getContainerName();
    }


    @Override
    public String getServerHome() {
        // TODO: verifyJbossHome?
        // TODO: env property instead of container property?
        return containerDef.getContainerProperties().get("jbossHome");
    }


    @Override
    public int getPort() {
        return Integer.parseInt(containerDef.getContainerProperties().get("managementPort"));
    }


    @Override
    public int getJNDIPort() {
        return 4447 + getPortOffset();
    }


    @Override
    public int getPortOffset() {
        if (portOffset == null) {
            String tmp = System.getProperty("PORT_OFFSET_" + containerIndex);
            portOffset = (tmp == null ? 0 : Integer.valueOf(tmp));
        }
        return portOffset;
    }


    @Override
    public Context getContext() throws NamingException {
        return JMSTools.getEAP6Context(getHostname(), getPort());
    }


    @Override
    public String getHostname() {
        // TODO: verify IPv6 address
        return containerDef.getContainerProperties().get("managementAddress");
    }


    @Override
    public int getHornetqPort() {
        return HornetQTestCaseConstants.PORT_HORNETQ_DEFAULT + getPortOffset();
    }


    @Override
    public int getHornetqBackupPort() {
        return HornetQTestCaseConstants.PORT_HORNETQ_BACKUP_DEFAULT + getPortOffset();
    }


    @Override
    public int getBytemanPort() {
        return HornetQTestCaseConstants.BYTEMAN_CONTAINER1_PORT;
    }


    @Override
    public HornetQTestCaseConstants.CONTAINER_TYPE getContainerType() {
        return HornetQTestCaseConstants.CONTAINER_TYPE.EAP6_CONTAINER;
    }


    @Override
    public void start() {
        containerController.start(getName());
    }


    @Override
    public void stop() {
        containerController.stop(getName());
    }


    @Override
    public void kill() {

        log.info("Killing server: " + getName());
        try {

            long pid = ProcessIdUtils.getProcessId(this);

            if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("windows"))  { // use taskkill
                Runtime.getRuntime().exec("taskkill /f /pid " + pid);
            } else { // on all other platforms use kill -9
                Runtime.getRuntime().exec("kill -9 " + pid);
            }
        } catch (Exception ex) {
            log.warn("Container " + getName() + " could not be killed. Set debug for logging to see exception stack trace.");
            log.debug(ex);
        } finally {
            log.info("Server: " + getName() + " -- KILLED");
        }
        containerController.kill(getName());

    }

    @Override
    public void restart() {
        stop();
        start();
    }


    @Override
    public synchronized JournalExportImportUtils getExportImportUtil() {
        if (journalExportImportUtils == null) {
            ServiceLoader serviceLoader = ServiceLoader.load(JournalExportImportUtils.class);

            @SuppressWarnings("unchecked")
            Iterator<JournalExportImportUtils> iterator = serviceLoader.iterator();
            if (!iterator.hasNext()) {
                throw new RuntimeException("No implementation found for JournalExportImportUtils.");
            }
            journalExportImportUtils = iterator.next();
        }

        return journalExportImportUtils;
    }

    @Override
    public synchronized JmxUtils getJmsUtils() {
        if (jmxUtils == null) {
            ServiceLoader serviceLoader = ServiceLoader.load(JmxUtils.class);

            @SuppressWarnings("unchecked")
            Iterator<JmxUtils> iterator = serviceLoader.iterator();
            if (!iterator.hasNext()) {
                throw new RuntimeException("No implementation found for JmxUtils.");
            }
            jmxUtils = iterator.next();
        }

        return jmxUtils;
    }


    @Override
    public JMSOperations getJmsOperations() {
        HornetQAdminOperationsEAP6 eap6AdmOps = new HornetQAdminOperationsEAP6();
        eap6AdmOps.setHostname(getHostname());
        eap6AdmOps.setPort(getPort());
        eap6AdmOps.connect();
        return eap6AdmOps;
    }


    @Override
    public synchronized JmxNotificationListener createJmxNotificationListener() {
        // always create new instance
        ServiceLoader serviceLoader = ServiceLoader.load(JmxNotificationListener.class);

        @SuppressWarnings("unchecked")
        Iterator<JmxNotificationListener> iterator = serviceLoader.iterator();
        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for JmxUtils.");
        }
        return iterator.next();
    }


    private ContainerDef getContainerDefinition(String containerName, ArquillianDescriptor descriptor) {
        for (GroupDef groupDef : descriptor.getGroups()) {
            for (ContainerDef containerDef : groupDef.getGroupContainers()) {
                if (containerDef.getContainerName().equalsIgnoreCase(containerName)) {
                    return containerDef;
                }
            }
        }

        throw new RuntimeException("No container with name " + containerName + " found in Arquillian descriptor "
                + descriptor.getDescriptorName());
    }

}
