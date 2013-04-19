package org.jboss.qa.hornetq.test.integration;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.apps.servlets.HornetQTestServlet;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.HttpRequest;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.arquillina.extension.annotation.RestoreConfigAfterTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This is only EAP5 related test.
 * This test case deploy servlet which tries whether java:/JmsXA is XAConnectionFactory when preferFactoryRef is enabled.
 *
 * @author mnovak@rehat.com
 */
@RestoreConfigAfterTest
@RunWith(Arquillian.class)
public class ConnectionFactoryTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ConnectionFactoryTestCase.class);


    @Test
    @RunAsClient
    @RestoreConfigAfterTest
    public void testXAConnectionFactoryEnabled() throws Exception {
        testXAConnectionFactoryLookup(true);
    }

    private void testXAConnectionFactoryLookup(boolean preferFactoryRef) throws Exception {

        prepareServer(CONTAINER1, preferFactoryRef);

        controller.start(CONTAINER1);

        String connectionFactoryName = "java:/JmsXA";

        deployer.deploy("hornetQTestServlet");

        try {


            logger.info("Trying to lookup connection factory: " + connectionFactoryName);

            try {
                deployer.undeploy("hornetQTestServlet");
            } catch (Exception ex) {
                logger.debug("HornetQTestServlet servlet was not deployed and it ");
            }
            deployer.deploy("hornetQTestServlet");
            String response = HttpRequest.get("http://" + CONTAINER1_IP + ":8080/HornetQTestServlet/HornetQTestServlet" +
                    "?op=testConnectionFactoryType&jndiName=" + connectionFactoryName, 4, TimeUnit.SECONDS);
            logger.info("Response from server is: " + response);
            Assert.assertTrue("This should be instance of XAConnectionFactory. Response is: " + response, preferFactoryRef == Boolean.valueOf(response.trim()));

        } catch (Exception ex) {

            logger.error("Exception was thrown during ConnectionFactoryTestCase.testXAConnectionFactoryLookup: ", ex);
            Assert.fail("Exception was thrown during ConnectionFactoryTestCase.testXAConnectionFactoryLookup: " + ex.getMessage());

        }

        deployer.undeploy("hornetQTestServlet");

        stopServer(CONTAINER1);
    }

    @After
    public void stopAllServers() {

        try {
            deployer.undeploy("hornetQTestServlet");
        } catch (Exception ignore)  {}

        stopServer(CONTAINER1);

    }

    @Deployment(testable = true, name = "hornetQTestServlet", managed = false)
    @TargetsContainer(CONTAINER1)
    public static WebArchive createHornetQTestServlet() {
        final WebArchive hornetqTestServlet = ShrinkWrap.create(WebArchive.class, "hornetQTestServlet.war");
        StringBuilder webXml = new StringBuilder();
        webXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> ");
        webXml.append("<web-app version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" \n");
        webXml.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
        webXml.append("         xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee \n");
        webXml.append("                              http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n");
        webXml.append(" <servlet>\n");
        webXml.append("   <servlet-name>HornetQTestServlet</servlet-name>\n");
        webXml.append("   <servlet-class>org.jboss.qa.hornetq.apps.servlets.HornetQTestServlet</servlet-class>\n");
        webXml.append(" </servlet>\n");
        webXml.append("\n");
        webXml.append(" <servlet-mapping>\n");
        webXml.append("   <servlet-name>HornetQTestServlet</servlet-name>\n");
        webXml.append("   <url-pattern>/HornetQTestServlet</url-pattern>\n");
        webXml.append(" </servlet-mapping>\n");
        webXml.append("</web-app>\n");
        webXml.append("\n");

        StringBuilder jbossWebXml = new StringBuilder();
        jbossWebXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n");
        jbossWebXml.append("<jboss-web> \n");
        jbossWebXml.append("  <context-root>/HornetQTestServlet</context-root> \n");
        jbossWebXml.append("</jboss-web> \n");
        webXml.append("\n");

        hornetqTestServlet.addAsWebInfResource(new StringAsset(webXml.toString()), "web.xml");
        hornetqTestServlet.addAsWebInfResource(new StringAsset(jbossWebXml.toString()), "jboss-web.xml");
        hornetqTestServlet.addClass(HornetQTestServlet.class);
        if (logger.isTraceEnabled()) {
            logger.trace(webXml.toString());
            logger.trace(jbossWebXml.toString());
            logger.trace(hornetqTestServlet.toString(true));
        }

        //      Uncomment when you want to see what's in the servlet
//        File target = new File("/tmp/servlet.jar");
//        if (target.exists()) {
//            target.delete();
//        }
//        hornetqTestServlet.as(ZipExporter.class).exportTo(target, true);

        return hornetqTestServlet;
    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     */
    private void prepareServer(String containerName, boolean preferFactoryRef) {

        JMSOperations jmsAdminOperations = this.getJMSOperations(CONTAINER1);

        jmsAdminOperations.setFactoryRef(preferFactoryRef);

        jmsAdminOperations.close();

    }

}