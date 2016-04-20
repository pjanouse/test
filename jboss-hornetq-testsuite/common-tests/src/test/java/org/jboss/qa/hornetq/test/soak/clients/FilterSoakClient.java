package org.jboss.qa.hornetq.test.soak.clients;


import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.HornetqJMSImplementation;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.soak.modules.EjbSoakModule;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.jms.ClientUtils;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class FilterSoakClient extends Client {

    private static final Logger LOG = Logger.getLogger(FilterSoakClient.class);

    private final ExecutorService threadPool = Executors.newFixedThreadPool(3);

    private final MessageBuilder messageBuilder = new TextMessageBuilder(1000);

    private final Container container;

    private final int port;

    private final int numberOfMessages;

    private ProducerThread producerThread;

    private int numberOfSentMessages;

    private int numberOfReceivedMessages;

    private List<String> sentMessages = new ArrayList<String>();

    private List<String> receivedMessages = new ArrayList<String>();

    public FilterSoakClient(final Container container, final int numberOfMessages) {
        super(container.getName());
        this.container = container;
        this.port = container.getJNDIPort();
        this.numberOfMessages = numberOfMessages;
    }


    @Override
    public void run() {
        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = container.getContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryJndiName());
            Queue queue = (Queue) ctx.lookup(EjbSoakModule.EJB_IN_QUEUE_JNDI);
            Queue resultQueue = (Queue) ctx.lookup(EjbSoakModule.EJB_OUT_QUEUE_JNDI);

            connection = cf.createConnection();
            connection.start();
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            this.producerThread = new ProducerThread(connection, queue, this.messageBuilder,
                    this.numberOfMessages);
            Future<Integer> sentMessagesFuture = this.threadPool.submit(this.producerThread);

            ConsumerThread consumerThread = new ConsumerThread(connection, resultQueue, "filterProperty = 0");
            ConsumerThread consumerThread1 = new ConsumerThread(connection, resultQueue, "filterProperty = 1");
            Future<Integer> receivedMessagesFuture = this.threadPool.submit(consumerThread);
            Future<Integer> receivedMessagesFuture1 = this.threadPool.submit(consumerThread1);

            // wait for execution finish
            this.numberOfSentMessages = sentMessagesFuture.get().intValue();
            this.numberOfReceivedMessages = receivedMessagesFuture.get().intValue() + receivedMessagesFuture1.get().intValue();

        } catch (Exception ex) {
            LOG.error("Error while running the org.jboss.qa.hornetq.apps.clients", ex);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing the session", ex);
                }
            }

            if (connection != null) {
                try {
                    connection.stop();
                    connection.close();
                } catch (JMSException ex) {
                    LOG.error("Error while closing the connection", ex);
                }
            }

            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    LOG.error("Error while closing the naming context", ex);
                }
            }
        }
    }


    public void stopSending() {
        if (this.producerThread != null) {
            this.producerThread.stopSending();
        }
    }


    public int getNumberOfSentMessages() {
        return this.numberOfSentMessages;
    }


    public int getNumberOfReceivedMessages() {
        return this.numberOfReceivedMessages;
    }


    public List<String> getSentMessages() {
        return this.sentMessages;
    }


    public List<String> getReceivedMessages() {
        return this.receivedMessages;
    }


    private static final class ProducerThread implements Callable<Integer> {

        private static final long MSG_GAP = 100;

        private final Connection connection;

        private final Queue queue;

        private final MessageBuilder messageBuilder;

        private final int numberOfMessages;

        private boolean stop = false;

        private int counter = 0;


        public ProducerThread(final Connection connection, final Queue queue,
                final MessageBuilder messageBuilder, final int numberOfMessages) {

            this.connection = connection;
            this.queue = queue;
            this.messageBuilder = messageBuilder;
            this.numberOfMessages = numberOfMessages;
        }


        @Override
        public Integer call() throws Exception {
            Session session = null;
            try {
                session = this.connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                MessageProducer producer = session.createProducer(this.queue);
                Message msg;

                while (this.counter < this.numberOfMessages && !this.stop) {
                    msg = this.messageBuilder.createMessage(new MessageCreator10(session), HornetqJMSImplementation.getInstance());
                    msg.setIntProperty("counter", ++this.counter);
                    msg.setIntProperty("filterProperty", this.counter % 2);
                    sendMessage(producer, msg, 30);
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

        private String sendMessage(final MessageProducer producer, final Message msg,
                                          final int maxRetries) throws JMSException {

            int numberOfRetries = 0;
            Integer msgCounter;
            try {
                msgCounter = msg.getIntProperty("counter");
            } catch (JMSException ex) {
                msgCounter = null;
            }

            while (numberOfRetries < maxRetries) {
                try {
                    producer.send(msg);

                    if (msgCounter == null) {
                        LOG.info("SENT message with id " + msg.getJMSMessageID());
                    } else {
                        if(msgCounter % 100 == 0) {
                            LOG.info("SENT message with counter " + msgCounter
                                    + " and id " + msg.getJMSMessageID());
                        }
                    }

                    if (msg.getStringProperty("_HQ_DUPL_ID") != null) {
                        return msg.getStringProperty("_HQ_DUPL_ID");
                    } else {
                        return null;
                    }
                } catch (JMSException ex) {
                    if (msgCounter == null) {
                        LOG.info("SEND RETRY - Sent message with id " + msg.getJMSMessageID());
                    } else {
                        LOG.info("SEND RETRY - Sent message with counter " + msgCounter
                                + " and id " + msg.getJMSMessageID());
                    }
                    numberOfRetries++;
                }
            }

            // this is an error - here we should never be because max retrie expired
            if (msgCounter == null) {
                throw new JMSException("FAILURE - MaxRetry reached for message with id"
                        + msg.getJMSMessageID());
            } else {
                throw new JMSException("FAILURE - MaxRetry reached for message counter " + msgCounter
                        + " and id " + msg.getJMSMessageID());
            }
        }

    }


    private static final class ConsumerThread implements Callable<Integer> {

        private final Connection connection;

        private final Queue queue;

        private int counter = 0;

        private String selector = null;


        public ConsumerThread(final Connection connection, final Queue queue, String selector) {
            this.connection = connection;
            this.queue = queue;
            this.selector = selector;
        }


        @Override
        public Integer call() throws Exception {
            Session session = null;
            try {
                session = this.connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                MessageConsumer consumer = null;
                if (selector != null) {
                    consumer = session.createConsumer(this.queue, selector);
                } else {
                    consumer = session.createConsumer(this.queue);
                }
                Message msg;

                while ((msg = ClientUtils.receiveMessage(consumer, this.counter + 1)) != null) {
                    this.counter++;

                    LOG.debug("Receiver for queue: " + this.queue.getQueueName()
                            + ". Received message - count: "
                            + this.counter + ", message-counter: " + msg.getStringProperty("counter")
                            + ", messageId:" + msg.getJMSMessageID());
                    msg.acknowledge();
                }

                LOG.error("Filter soak consumer ended - received NULL - number of received messages: " + counter);

                return this.counter;
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        }

    }

}
