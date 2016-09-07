package org.jboss.qa.hornetq.test.prepares.specific;

import org.jboss.qa.PrepareMethod;
import org.jboss.qa.PrepareUtils;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.test.prepares.PrepareParams;
import org.jboss.qa.hornetq.test.prepares.generic.OneNode;
import org.jboss.qa.hornetq.tools.JMSOperations;

import java.util.Map;

public class DeadLetterPrepare extends OneNode {

    public static final String DEPLOY_DLQ = "DEPLOY_DLQ";

    public static final String EXPIRY_QUEUE_NAME = "test.dlq.ExpiryQueue";

    public static final String DLQ_NAME = "test.dlq.DeadLetterQueue";

    public static final String DLQ_JNDI = "jms/queue" + DLQ_NAME;

    @Override
    @PrepareMethod(value = "DeadLetterPrepare", labels = {"EAP6"})
    public void prepareMethodEAP6(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP6(params);
    }

    @Override
    @PrepareMethod(value = "DeadLetterPrepare", labels = {"EAP7"})
    public void prepareMethodEAP7(Map<String, Object> params) throws Exception {
        super.prepareMethodEAP7(params);
    }

    @Override
    protected void beforePrepare(Map<String, Object> params, Container container) {
        PrepareUtils.requireParam(params, PrepareParams.ADDRESS);
        PrepareUtils.requireParam(params, DEPLOY_DLQ);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.MAX_DELIVERY_ATTEMPTS, 2);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.EXPIRY_QUEUE, "jms.queue." + EXPIRY_QUEUE_NAME);
        PrepareUtils.setIfNotSpecified(params, PrepareParams.DEAD_LETTER_QUEUE, "jms.queue." + DLQ_NAME);

        boolean deployDLQ = PrepareUtils.getBoolean(params, DEPLOY_DLQ);

        JMSOperations jmsOperations = container.getJmsOperations();

        if (deployDLQ) {
            jmsOperations.createQueue(DLQ_NAME, DLQ_JNDI, true);
        }

        jmsOperations.close();
    }
}
