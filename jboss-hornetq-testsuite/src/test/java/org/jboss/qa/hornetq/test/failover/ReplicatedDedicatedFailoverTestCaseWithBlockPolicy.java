package org.jboss.qa.hornetq.test.failover;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.qa.tools.JMSOperations;
import org.junit.runner.RunWith;

/**
* Failover tests just with replicated journal with BLOCK policy.
*/
@RunWith(Arquillian.class)
public class ReplicatedDedicatedFailoverTestCaseWithBlockPolicy extends ReplicatedDedicatedFailoverTestCase {


    protected void setAddressSettings(JMSOperations jmsAdminOperations) {
        setAddressSettings("default", jmsAdminOperations);
    }

    protected void setAddressSettings(String serverName, JMSOperations jmsAdminOperations) {
        jmsAdminOperations.addAddressSettings(serverName, "#", "BLOCK", 100 * 1024 * 1024, 0, 0, 512 * 1024);
    }
}
