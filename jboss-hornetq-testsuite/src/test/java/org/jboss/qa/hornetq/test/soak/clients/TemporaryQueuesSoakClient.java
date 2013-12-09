package org.jboss.qa.hornetq.test.soak.clients;


import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.naming.Context;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.soak.modules.TemporaryQueueSoakModule;
import org.jboss.qa.hornetq.tools.ContainerInfo;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class TemporaryQueuesSoakClient extends Client {

    private static final Logger LOG = Logger.getLogger(TemporaryQueuesSoakClient.class);

    private final ExecutorService threadPool;

    private final MessageBuilder messageBuilder = new TextMessageBuilder(1000);

    private final int port;

    private final ContainerInfo container;

    private final long numberOfMessages;

    private final TemporaryQueue[] responseQueues;

    private ProducerThread producerThread;

    private int numberOfSentMessages = 0;

    private int numberOfReceivedMessages = 0;


    public TemporaryQueuesSoakClient(final ContainerInfo container, final long numberOfMessages) {
        this(container, 4447, numberOfMessages, 10);
    }


    public TemporaryQueuesSoakClient(final ContainerInfo container, final int jndiPort,
            final long numberOfMessages, final int numberOfResponseQueues) {

        super(container.getName());
        this.container = container;
        this.port = jndiPort;
        this.numberOfMessages = numberOfMessages;
        this.responseQueues = new TemporaryQueue[numberOfResponseQueues];
        this.threadPool = Executors.newFixedThreadPool(numberOfResponseQueues + 1);
    }


    @Override
    public void run() {
        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = this.getContext(this.container.getIpAddress(), this.port);
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryJndiName());
            Queue outQueue = (Queue) ctx.lookup(TemporaryQueueSoakModule.TEMP_IN_QUEUE_JNDI);

            connection = cf.createConnection();
            connection.start();
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            for (int i = 0; i < this.responseQueues.length; i++) {
                this.responseQueues[i] = session.createTemporaryQueue();
                LOG.info("created temp queue: " + this.responseQueues[i].getQueueName());
            }

            this.producerThread = new ProducerThread(connection, outQueue, this.messageBuilder,
                    this.responseQueues, this.numberOfMessages);
            Future<Integer> sentMessagesFuture = this.threadPool.submit(this.producerThread);

            Future<Integer>[] consumerFutures = new Future[this.responseQueues.length];
            ConsumerThread[] consumerThreads = new ConsumerThread[this.responseQueues.length];
            for (int i = 0; i < this.responseQueues.length; i++) {
                consumerThreads[i] = new ConsumerThread(connection, this.responseQueues[i]);
                consumerFutures[i] = this.threadPool.submit(consumerThreads[i]);
            }

            // wait for execution finish
            this.numberOfSentMessages = sentMessagesFuture.get().intValue();

            for (int i = 0; i < this.responseQueues.length; i++) {
                this.numberOfReceivedMessages += consumerFutures[i].get().intValue();
            }
        } catch (Exception ex) {
            LOG.error("Error while running the clients", ex);
        } finally {
            for (int i = 0; i < this.responseQueues.length; i++) {
                if (this.responseQueues[i] != null) {
                    try {
                        this.responseQueues[i].delete();
                    } catch (JMSException ex) {
                        LOG.error("Error while deleting temporary queue with index " + i, ex);
                    }
                }
            }

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


    private static final class ProducerThread implements Callable<Integer> {

        private static final long MSG_GAP = 100;

        private static final int MAX_RETRIES = 30;

        private final Connection connection;

        private final Queue targetQueue;

        private final MessageBuilder messageBuilder;

        private final long numberOfMessage;

        private final TemporaryQueue[] responseQueues;

        private boolean stop = false;

        private int counter = 0;


        public ProducerThread(final Connection connection, final Queue targetQueue,
                final MessageBuilder messageBuilder, final TemporaryQueue[] responseQueues,
                final long numberOfMessages) {

            this.connection = connection;
            this.targetQueue = targetQueue;
            this.messageBuilder = messageBuilder;
            this.responseQueues = responseQueues;
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
                    msg = this.messageBuilder.createMessage(session);
                    msg.setIntProperty("counter", ++this.counter);
                    msg.setJMSReplyTo(this.responseQueues[this.counter % this.responseQueues.length]);
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


    private static final class ConsumerThread implements Callable<Integer> {

        private static final int MAX_RETRIES = 30;

        private static final long RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

        private final Connection connection;

        private final TemporaryQueue queue;

        private int counter = 0;


        public ConsumerThread(final Connection connection, final TemporaryQueue queue) {
            this(connection, queue, 10);
        }


        public ConsumerThread(final Connection connection, final TemporaryQueue queue, final int ackAfter) {
            this.connection = connection;
            this.queue = queue;
            //this.ackAfter = ackAfter;
        }


        @Override
        public Integer call() throws Exception {
            Session session = null;
            try {
                session = this.connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(this.queue);
                Message msg;

                while ((msg = this.receiveMessage(consumer)) != null) {
                    this.counter++;

                    LOG.debug("Receiver for queue: " + queue.getQueueName()
                            + ". Received message - count: "
                            + this.counter + ", message-counter: " + msg.getStringProperty("counter")
                            + ", messageId:" + msg.getJMSMessageID());
                    msg.acknowledge();
                }

                return this.counter;
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        }


        private Message receiveMessage(final MessageConsumer consumer) throws Exception {
            Message msg;
            int numberOfRetries = 0;

            // receive message with retry
            while (numberOfRetries < MAX_RETRIES) {
                try {
                    msg = consumer.receive(RECEIVE_TIMEOUT);
                    return msg;
                } catch (JMSException ex) {
                    numberOfRetries++;
                    LOG.error("RETRY receive for queue: " + this.queue.getQueueName()
                            + ", trying to receive message with count: " + (this.counter + 1));
                }
            }
            throw new Exception("FAILURE - MaxRetry reached for receiver for queue " + this.queue.getQueueName());
        }

    }

}
