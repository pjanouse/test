package org.jboss.qa.hornetq.test.integration;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.tools.JMSOperations;
import org.jboss.qa.tools.JMSProvider;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.jms.ConnectionFactory;
import javax.jms.XAConnectionFactory;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author mnovak@rehat.com
 */
//@RestoreConfigAfterTest
@RunWith(Arquillian.class)
public class ConnectionFactoryTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(ConnectionFactoryTestCase.class);

    @Inject
    JndiLookUpBean jndiLookUpBean;


    @Test
//    @RestoreConfigAfterTest
    public void testXAConnectionFactoryLookup() throws Exception {

        prepareServer(CONTAINER1);

        controller.start(CONTAINER1);

        deployer.deploy("jndiLookUpBean");

        String connectionFactoryName = "java:/JmsXA";

        try {


            logger.info("Trying to lookup connection factory: " + connectionFactoryName);
            ConnectionFactory cf = jndiLookUpBean.lookUpConnectionFactory(connectionFactoryName);

            Assert.assertNotNull("Connection factory: " + connectionFactoryName + " should not be null.", cf);
            Assert.assertTrue("This should be instance of XAConnectionFactory.", cf instanceof XAConnectionFactory);

        } catch (Exception ex)  {

            logger.error("Exception was thrown during ConnectionFactoryTestCase.testXAConnectionFactoryLookup: ", ex);
            Assert.fail("Exception was thrown during ConnectionFactoryTestCase.testXAConnectionFactoryLookup: " + ex.getMessage());

        } finally {

            deployer.undeploy("jndiLookUpBean");

        }
    }

    @Deployment(testable = true, name = "jndiLookUpBean", managed = true)
    @TargetsContainer(CONTAINER1)
    public static JavaArchive createDeployment() {
        JavaArchive ejb =  ShrinkWrap.create(JavaArchive.class, "jndiLookUpBean.jar")
                .addClass(JndiLookUpBean.class).addClass(JndiLookUp.class);
        ejb.addAsManifestResource(new StringAsset("Manifest-Version: 1.0\n Dependencies: javax.jms, javax.naming \n"), "MANIFEST.MF");

        File target = new File("/tmp/ejb.jar");
        if (target.exists()) {
            target.delete();
        }
        ejb.as(ZipExporter.class).exportTo(target, true);

        return ejb;

    }

    @After
    public void stopAllServers() {

        stopServer(CONTAINER1);

    }

    @Before
    public void startServer() {

        controller.start(CONTAINER1);

    }

    /**
     * Prepares live server for dedicated topology.
     *
     * @param containerName Name of the container - defined in arquillian.xml
     *
     */
    private void prepareServer(String containerName) throws IOException {

//        controller.start(containerName);

        JMSOperations jmsAdminOperations = JMSProvider.getInstance(CONTAINER1);

        jmsAdminOperations.setFactoryRef(true);

        jmsAdminOperations.close();

//        controller.stop(containerName);

    }

}