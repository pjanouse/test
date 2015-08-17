package org.jboss.qa.hornetq.test.soak.clients;


import java.util.concurrent.Callable;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class SoakProducerCallable implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(SoakProducerCallable.class);

    private static final long MSG_GAP = 100;

    private static final int MAX_RETRIES = 30;

    private final Connection connection;

    private final Queue targetQueue;

    private final MessageBuilder messageBuilder;

    private final long numberOfMessage;

    private boolean stop = false;

    private int counter = 0;


    public SoakProducerCallable(final Connection connection, final Queue targetQueue,
            final MessageBuilder messageBuilder, final long numberOfMessages) {

        this.connection = connection;
        this.targetQueue = targetQueue;
        this.messageBuilder = messageBuilder;
        this.numberOfMessage = numberOfMessages;
    }


    @Override
    public Integer call() throws Exception {
        Session session = null;
        try {
            session = this.connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(this.targetQueue);
            Message msg;

            while (this.counter < this.numberOfMessage && !this.stop) {
                msg = this.messageBuilder.createMessage(new MessageCreator10(session), new HornetqJMSImplementation());
                msg.setIntProperty("counter", ++this.counter);
                this.sendMessage(producer, msg);
                Thread.sleep(MSG_GAP);
            }
            return this.counter;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }


    public void stopSending() {
        this.stop = true;
    }


    private void sendMessage(final MessageProducer producer, final Message msg) throws Exception {
        int numberOfRetries = 0;
        while (numberOfRetries < MAX_RETRIES) {
            try {
                producer.send(msg);
                LOG.info("SENT message with count " + this.counter
                        + " with destination " + msg.getJMSReplyTo().toString());
                return;
            } catch (JMSException ex) {
                LOG.info("SEND RETRY - Sent message with property count: " + this.counter
                        + ", message-counter: " + msg.getStringProperty("counter")
                        + ", messageId:" + msg.getJMSMessageID());

                numberOfRetries++;
            }
        }

        // this is an error - here we should never be because max retrie expired
        throw new Exception("FAILURE - MaxRetry reached for message with property count: " + this.counter
                + ", messageId:" + msg.getJMSMessageID());
    }

}
