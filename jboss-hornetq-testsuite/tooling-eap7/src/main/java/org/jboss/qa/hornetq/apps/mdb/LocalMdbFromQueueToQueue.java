package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.TextMessage;

@MessageDriven(name = "LocalMdbFromQueueToQueue", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/queue/sourceQueue"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue")
})
public class LocalMdbFromQueueToQueue implements MessageListener {

    private static final Logger logger = Logger.getLogger(LocalMdbFromQueueToQueue.class);

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/queue/targetQueue")
    private Queue queue;

    @Override
    public void onMessage(Message message) {
        try {
            logger.info("Resending " + ((TextMessage) message).getText());
            jmsContext.createProducer().send(queue, message);
        } catch (JMSException e) {
            logger.error(e);
        }
    }
}
