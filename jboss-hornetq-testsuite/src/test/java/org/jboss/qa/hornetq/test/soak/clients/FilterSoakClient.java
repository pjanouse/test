package org.jboss.qa.hornetq.test.soak.clients;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;
import org.jboss.qa.hornetq.test.soak.modules.EjbSoakModule;
import org.jboss.qa.tools.ContainerInfo;
import org.jboss.qa.tools.jms.ClientUtils;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class FilterSoakClient extends Client {

    private static final Logger LOG = Logger.getLogger(FilterSoakClient.class);

    private final ExecutorService threadPool = Executors.newFixedThreadPool(2);

    private final MessageBuilder messageBuilder = new TextMessageBuilder(1000);

    private final ContainerInfo container;

    private final int port;

    private final int numberOfMessages;

    private ProducerThread producerThread;

    private int numberOfSentMessages;

    private int numberOfReceivedMessages;

    private List<String> sentMessages = new ArrayList<String>();

    private List<String> receivedMessages = new ArrayList<String>();


    public FilterSoakClient(final ContainerInfo container, final int numberOfMessages) {
        this(container, 4447, numberOfMessages);
    }


    public FilterSoakClient(final ContainerInfo container, final int jndiPort, final int numberOfMessages) {
        super(container.getName());
        this.container = container;
        this.port = jndiPort;
        this.numberOfMessages = numberOfMessages;
    }


    @Override
    public void run() {
        Context ctx = null;
        Connection connection = null;
        Session session = null;

        try {
            ctx = this.getContext(this.container.getIpAddress(), this.port);
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(this.getConnectionFactoryJndiName());
            Queue queue = (Queue) ctx.lookup(EjbSoakModule.EJB_IN_QUEUE_JNDI);
            Queue resultQueue = (Queue) ctx.lookup(EjbSoakModule.EJB_OUT_QUEUE_JNDI);

            connection = cf.createConnection();
            connection.start();
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            this.producerThread = new ProducerThread(connection, queue, this.messageBuilder,
                    this.numberOfMessages);
            Future<Integer> sentMessagesFuture = this.threadPool.submit(this.producerThread);

            ConsumerThread consumerThread = new ConsumerThread(connection, resultQueue);
            Future<Integer> receivedMessagesFuture = this.threadPool.submit(consumerThread);

            // wait for execution finish
            this.numberOfSentMessages = sentMessagesFuture.get().intValue();
            this.sentMessages.addAll(this.producerThread.getListOfMessages());

            this.numberOfReceivedMessages = receivedMessagesFuture.get().intValue();
            this.receivedMessages.addAll(consumerThread.getListOfMessages());
        } catch (Exception ex) {
            LOG.error("Error while running the clients", ex);
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

        private final List<String> listOfSentMessages = new LinkedList<String>();

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
                    msg = this.messageBuilder.createMessage(session);
                    msg.setIntProperty("counter", ++this.counter);
                    msg.setIntProperty("filterProperty", this.counter % 2);
                    this.listOfSentMessages.add(ClientUtils.sendMessage(producer, msg));
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


        public List<String> getListOfMessages() {
            return this.listOfSentMessages;
        }

    }


    private static final class ConsumerThread implements Callable<Integer> {

        private final Connection connection;

        private final Queue queue;

        private final List<String> receivedMessages = new LinkedList<String>();

        private int counter = 0;


        public ConsumerThread(final Connection connection, final Queue queue) {
            this.connection = connection;
            this.queue = queue;
        }


        @Override
        public Integer call() throws Exception {
            Session session = null;
            try {
                session = this.connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(this.queue, "filterProperty = 0");
                Message msg;

                while ((msg = ClientUtils.receiveMessage(consumer, this.counter + 1)) != null) {
                    this.counter++;

                    LOG.debug("Receiver for queue: " + this.queue.getQueueName()
                            + ". Received message - count: "
                            + this.counter + ", message-counter: " + msg.getStringProperty("counter")
                            + ", messageId:" + msg.getJMSMessageID());
                    msg.acknowledge();
                    this.receivedMessages.add(msg.getStringProperty("_HQ_DUPL_ID"));
                }

                return this.counter;
            } finally {
                if (session != null) {
                    session.close();
                }
            }
        }


        public List<String> getListOfMessages() {
            return this.receivedMessages;
        }

    }

}
