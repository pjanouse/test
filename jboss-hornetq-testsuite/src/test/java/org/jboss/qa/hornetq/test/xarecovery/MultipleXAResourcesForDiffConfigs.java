package org.jboss.qa.hornetq.test.xarecovery;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * TODO
 */
@RunWith(Arquillian.class)
public class MultipleXAResourcesForDiffConfigs extends HornetQTestCase {

    // Logger
    protected static final Logger logger = Logger.getLogger(MultipleXAResourcesForDiffConfigs.class);

    @ArquillianResource
    private Deployer deployer;

    /**
     * SOAK test implementation
     *
     * @throws Exception if something goes wrong
     */
    @Test
    @RunAsClient
    public void soakTest() throws Exception {
        // cluster A
        controller.start(CONTAINER1);
        Thread.sleep(180 * 60000);

        stopServer(CONTAINER1);
    }
}
