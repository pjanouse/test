package org.jboss.qa.hornetq.test.compatibility;


import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SocketBinding;
import org.jboss.qa.hornetq.tools.XMLManipulation;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Forward compatibility tests for EAP5 HornetQ clients connecting to EAP6 server.
 * <p/>
 * For this test working properly, you need to use arqullian-eap6-legacy.xml descriptor. Your JBOSS_HOME_X
 * properties need to point to EAP6 servers with org.jboss.legacy.jnp module installed. When running
 * this test, use eap5x-backward-compatibility maven profile and set netty.version and hornetq.version
 * maven properties to client libraries versions you want to test with (HornetQ needs to be 2.2.x).
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class Eap5ClientCompatibilityTestCase extends ClientCompatibilityTestBase {

    private static final Logger LOG = Logger.getLogger(Eap5ClientCompatibilityTestCase.class);


    @Override
    protected int getLegacyClientJndiPort() {
        return SocketBinding.LEGACY_JNP.getPort();
    }


    @Override
    protected void prepareContainer(final ContainerInfo container) throws Exception {
        final String discoveryGroupName = "dg-group1";
        final String broadCastGroupName = "bg-group1";
        final String messagingGroupSocketBindingName = "messaging-group";
        final String clusterGroupName = "my-cluster";
        final String connectorName = "netty";

        JMSOperations ops = this.getJMSOperations(container.getName());

        ops.setInetAddress("public", container.getIpAddress());
        ops.setInetAddress("unsecure", container.getIpAddress());
        ops.setInetAddress("management", container.getIpAddress());

        ops.setBindingsDirectory(JOURNAL_DIR);
        ops.setPagingDirectory(JOURNAL_DIR);
        ops.setJournalDirectory(JOURNAL_DIR);
        ops.setLargeMessagesDirectory(JOURNAL_DIR);

        ops.setClustered(false);
        ops.setPersistenceEnabled(true);
        ops.setSharedStore(true);

        ops.removeBroadcastGroup(broadCastGroupName);
        ops.setBroadCastGroup(broadCastGroupName, messagingGroupSocketBindingName, 2000, connectorName,
                "");

        ops.removeDiscoveryGroup(discoveryGroupName);
        ops.setDiscoveryGroup(discoveryGroupName, messagingGroupSocketBindingName, 10000);

        ops.removeClusteringGroup(clusterGroupName);
        ops.setClusterConnections(clusterGroupName, "jms", discoveryGroupName, false, 1, 1000, true,
                connectorName);

        ops.disableSecurity();
        ops.removeAddressSettings("#");
        ops.addAddressSettings("#", "PAGE", 50 * 1024 * 1024, 0, 0, 1024 * 1024);

        ops.addExtension("org.jboss.legacy.jnp");

        ops.close();
        this.controller.stop(container.getName());
        this.controller.start(container.getName());
        ops = this.getJMSOperations(container.getName());

        ops.createSocketBinding(SocketBinding.LEGACY_JNP.getName(), SocketBinding.LEGACY_JNP.getPort());
        ops.createSocketBinding(SocketBinding.LEGACY_RMI.getName(), SocketBinding.LEGACY_RMI.getPort());
        //ops.createSocketBinding(SocketBinding.LEGACY_REMOTING.getName(), SocketBinding.LEGACY_REMOTING.getPort());

        this.deployDestinations(ops);
        ops.close();

        this.activateLegacyJnpModule(container);
        controller.stop(container.getName());
    }


    private void deployDestinations(final JMSOperations ops) {
        for (int destinationNumber = 0; destinationNumber < NUMBER_OF_DESTINATIONS; destinationNumber++) {
            ops.createQueue(QUEUE_NAME_PREFIX + destinationNumber, QUEUE_JNDI_NAME_PREFIX
                    + destinationNumber, true);
            ops.createTopic(TOPIC_NAME_PREFIX + destinationNumber, TOPIC_JNDI_NAME_PREFIX
                    + destinationNumber);
        }
    }


    private void activateLegacyJnpModule(final ContainerInfo container) throws Exception {
        StringBuilder pathToStandaloneXml = new StringBuilder();
        pathToStandaloneXml = pathToStandaloneXml.append(container.getJbossHome())
                .append(File.separator).append("standalone")
                .append(File.separator).append("configuration")
                .append(File.separator).append("standalone-full-ha.xml");
        Document doc = XMLManipulation.getDOMModel(pathToStandaloneXml.toString());

        Element e = doc.createElement("subsystem");
        e.setAttribute("xmlns", "urn:jboss:domain:legacy-jnp:1.0");

        Element entry = doc.createElement("jnp-connector");
        entry.setAttribute("socket-binding", "jnp");
        entry.setAttribute("rmi-socket-binding", "rmi-jnp");
        e.appendChild(entry);

        /*Element entry2 = doc.createElement("remoting");
         entry2.setAttribute("socket-binding", "legacy-remoting");
         e.appendChild(entry2);*/
        XPath xpathInstance = XPathFactory.newInstance().newXPath();
        Node node = (Node) xpathInstance.evaluate("//profile", doc, XPathConstants.NODE);
        node.appendChild(e);

        XMLManipulation.saveDOMModel(doc, pathToStandaloneXml.toString());
    }


    @Test
    @RunAsClient
    @RestoreConfigBeforeTest
    @CleanUpBeforeTest
    public void testJNDILookupTroughLegacyExtension() throws Exception {

        prepareContainer(CONTAINER1_INFO);

        controller.start(CONTAINER1);

        Context ctx = null;

        try {
            ctx = this.getContext(getHostname(CONTAINER1), getLegacyJNDIPort(CONTAINER1));

            List<String> jndiNameToLookup = new ArrayList<String>();

            jndiNameToLookup.add(CONNECTION_FACTORY_JNDI_EAP6);
            jndiNameToLookup.add(CONNECTION_FACTORY_JNDI_EAP6_FULL_NAME);
            jndiNameToLookup.add("jms/queue/" + QUEUE_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jms/queue/" + QUEUE_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jboss/exported/jms/queue/" + QUEUE_NAME_PREFIX + "0");
            jndiNameToLookup.add("jms/topic/" + TOPIC_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jms/topic/" + TOPIC_NAME_PREFIX + "0");
            jndiNameToLookup.add("java:jboss/exported/jms/topic/" + TOPIC_NAME_PREFIX + "0");

            for (String jndiName : jndiNameToLookup) {
                Object o = ctx.lookup(jndiName);
                if (o == null) {
                    Assert.fail("jndiName: " + jndiName + " could not be found.");
                }
            }

        } catch (Exception ex) {
            LOG.error("Error during jndi lookup.", ex);
            throw new Exception(ex);
        } finally {

            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOG.error("Error while closing the naming context", ex);
                }
            }
        }
    }


}
