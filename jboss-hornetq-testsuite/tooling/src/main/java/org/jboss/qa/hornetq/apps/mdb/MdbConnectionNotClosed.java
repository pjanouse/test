package org.jboss.qa.hornetq.apps.mdb;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenContext;
import javax.jms.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.impl.JMSMessageProperties;

/**
 * Created by tomr on 20/12/13.
 */

@MessageDriven(name = "MdbConnectionNotClosed", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
        @ActivationConfigProperty(propertyName = "useJNDI", propertyValue = "true"),
        @ActivationConfigProperty(propertyName = "hA", propertyValue = "true")
}, mappedName = "java:jboss/exported/jms/queue/inQueue")

public class MdbConnectionNotClosed implements MessageListener {
    public static final String MESSAGE_THROW_EXCEPTION = "ThrowException";

    private static final Logger log = Logger.getLogger(MdbConnectionNotClosed.class);
    private static AtomicInteger mdbCnt = new AtomicInteger(0);
    private static String hostName = null;
    private int msgCnt = 0;
    private int mdbID = 0;
    @Resource
    private MessageDrivenContext ctx;
    private TextMessage txtMsg = null;
    private ObjectMessage objMsg = null;

    
    private Queue outQueue;

    @Resource(name = "java:/JmsXA")
    private QueueConnectionFactory qcf;
    private QueueConnection queueConnection = null;
    private QueueSession queueSession = null;
    private QueueSender queueSender = null;

    private int totalMsgCnt = 0;
    private boolean throwException = false;

    public MdbConnectionNotClosed() {
        mdbCnt.incrementAndGet();
        mdbID = mdbCnt.get();
    }

    public void onMessage(Message message) {

        try {

            if (log.isDebugEnabled()) {
                log.infof("MDB[%d] Got message - '%s'.", mdbID, message.toString());
            }

            if (message instanceof TextMessage) {
                txtMsg = (TextMessage) message;
                log.infof("MDB[%d] Got text message '%s'.", mdbID, txtMsg.getText());
                queueSender.send(message);
                if (log.isDebugEnabled()) {
                    log.infof("MDB[%d] Sent message '%s' to destination '%s'.", mdbID, message, outQueue.getQueueName());
                }
                throwException = txtMsg.getBooleanProperty(MESSAGE_THROW_EXCEPTION);
                if (throwException) {

                    log.infof("MDB[%d] This message asked to throw RuntimeException.", mdbID);

                    throw new RuntimeException("This is a dummy exception thrown from onMessage() method.");
                }

                if (log.isDebugEnabled()) {
                    log.infof("MDB[%d] The unique value = %d.", mdbID, txtMsg.getLongProperty(JMSMessageProperties.UNIQUE_VALUE));
                }

                int redeliveryCount = txtMsg.getIntProperty(JMSMessageProperties.JMSX_DELIVERY_COUNT);

                if (redeliveryCount != 1 && (log.isDebugEnabled() || log.isTraceEnabled())) {

                    log.infof("MDB[%d] Message redelivery count is %d.", mdbID, redeliveryCount);

                }

                long cunsumerDelay = txtMsg.getLongProperty(JMSMessageProperties.MESSAGE_CONSUMER_DELAY);

                if (cunsumerDelay != 0 && redeliveryCount < 3) {

                    log.infof("MDB[%d] Going to sleep for %d milliseconds.", mdbID, cunsumerDelay);

                    try {

                        Thread.sleep(cunsumerDelay);

                    } catch (InterruptedException interruptedException) {

                        log.warnf("MDB[%d] Thread interrupted.", mdbID);
                    }

                }

                msgCnt++;

            } else if (message instanceof ObjectMessage) {

                objMsg = (ObjectMessage) message;

                log.infof("MDB[%d] Got object message of class '%s'.", mdbID, objMsg.getObject().getClass().getName());

            } else {

                log.warnf("MDB[%d] Unknown message type.", mdbID);

            }

        } catch (JMSException jmsEx) {

            ctx.setRollbackOnly();

            log.errorf("MDB[%d] JMS Error %s", mdbID, jmsEx);

        } finally {


        }
    }

    @PostConstruct
    public void init() {
        log.infof("MDB[%d] Creating MDB.", mdbID);

        if (hostName == null) {

            try {

                hostName = InetAddress.getLocalHost().getHostName();

            } catch (UnknownHostException e) {

                log.warnf("MDB[%d] Problem obtaining host name, setting hostName to 'unknown'.", mdbID);

                hostName = "unknown";
            }
        }

        try {

            queueConnection = qcf.createQueueConnection();

            queueSession = queueConnection.createQueueSession(true, Session.SESSION_TRANSACTED);

            if (outQueue == null)   {
                outQueue = queueSession.createQueue("OutQueue");
            }

            queueSender = queueSession.createSender(outQueue);

        } catch (JMSException jmsException) {

            log.errorf("MDB[%d] Caught exception while creating JMS resources %s.", mdbID, jmsException);

        }
    }

    @PreDestroy
    public void cleanUp() {
        log.infof("MDB[%d] Processed '%d' messages.", mdbID, msgCnt);
        log.infof("MDB[%d] Closing MDB.");

        try {

            if (queueSender != null) {

                queueSession.close();

                log.debug("MDB[%d] qSender closed.");
            }

            if (queueSession != null) {

                queueSession.close();

                log.debug("MDB[%d] qSession closed.");
            }

            if (queueConnection != null) {

                queueConnection.close();

                log.debug("MDB[%d] qConnection closed.");
            }

        } catch (JMSException jmsEx) {

            log.warn("MDB[%d] Problem while cleaning jms resources.", jmsEx);

        }

        log.infof("MDB[%d] Closed.");

    }

}

