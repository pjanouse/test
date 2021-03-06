package org.jboss.qa.hornetq.test.remote.jca;

import org.jboss.qa.hornetq.tools.JMSOperations;

/**
 * Created by mstyk on 24/5/16.
 */
public class RemoteJcaWithSocketExhaustionTestCaseBlockMode extends RemoteJcaWithSocketExhaustionAbstract {

    @Override
    protected  void setAddressSettings(JMSOperations jmsAdminOperations) {
        jmsAdminOperations.removeAddressSettings("#");
        jmsAdminOperations.addAddressSettings("default", "#", "BLOCK", RemoteJcaLoadTestBase.MAX_SIZE_BYTES_DEFAULT, 60000, 2000, RemoteJcaLoadTestBase.PAGE_SIZE_BYTES_DEFAULT, "jms.queue.DLQ", "jms.queue.ExpiryQueue", 10);
    }
}
