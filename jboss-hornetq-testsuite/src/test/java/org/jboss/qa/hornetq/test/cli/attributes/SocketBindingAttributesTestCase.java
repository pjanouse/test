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
 *
 */
@Category(FunctionalTests.class)
public class SocketBindingAttributesTestCase extends HornetQTestCase {
    private static final Logger logger = Logger.getLogger(SocketBindingAttributesTestCase.class);

    @Test
    @RunAsClient
    @CleanUpBeforeTest
    @RestoreConfigBeforeTest
    public void bindingTest(){
        controller.start(CONTAINER1);

        JMSOperations jmsOperations = getJMSOperations(CONTAINER1);
        String result=jmsOperations.getSocketBindingAtributes("messaging");
        String[]resultArr=result.split(",");
        Assert.assertTrue("Socket bound attribute is false, should be true",resultArr[0].equals("\"bound\" => true"));
        Assert.assertTrue("Socket bound-address should be defined correctly",resultArr[1].equals("\"bound-address\" => \""+getHostname(CONTAINER1)+"\""));



        jmsOperations.close();
        stopServer(CONTAINER1);

    }

}
