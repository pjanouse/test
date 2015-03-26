package org.jboss.qa.hornetq.test.cli.attributes;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.categories.FunctionalTests;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.CleanUpBeforeTest;
import org.jboss.qa.hornetq.tools.arquillina.extension.annotation.RestoreConfigBeforeTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by okalman on 12/3/14.
 */
@Category(FunctionalTests.class)
public class SocketBindingAttributesTestCase extends HornetQTestCase {

    private static final Logger logger = Logger.getLogger(SocketBindingAttributesTestCase.class);

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void bindingTest() {

        container(1).start();
        JMSOperations jmsOperations = container(1).getJmsOperations();
        String result = jmsOperations.getSocketBindingAtributes("messaging");
        String[] resultArr = result.split(",");
        Assert.assertTrue("Socket bound attribute is false, should be true", resultArr[0].equals("\"bound\" => true"));

        if (container(1).getHostname().contains(":")) {
            //for IPv6
            Assert.assertTrue("Socket bound-address should be defined correctly", resultArr[1].contains(container(1).getHostname().substring(1, container(1).getHostname().length() - 2)));
        } else {
            //for IPv4
            Assert.assertTrue("Socket bound-address should be defined correctly", resultArr[1].equals("\"bound-address\" => \"" + container(1).getHostname() + "\""));
        }
        jmsOperations.close();
        container(1).stop();
    }

}
