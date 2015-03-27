package org.jboss.qa.hornetq.tools.jms;


import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.Clients;
import org.jboss.qa.hornetq.apps.clients.Client;
import org.junit.Assert;


/**
 * Helper methods for sending and receiving messages in JMS org.jboss.qa.hornetq.apps.clients.
 *
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class ClientUtils {

    private static final Logger log = Logger.getLogger(ClientUtils.class);

    private static final int DEFAULT_MAX_RETRIES = 30;

    private static final long DEFAULT_RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(120);


    public static String sendMessage(final MessageProducer producer, final Message msg)
            throws JMSException {

        return sendMessage(producer, msg, DEFAULT_MAX_RETRIES);
    }


    public static String sendMessage(final MessageProducer producer, final Message msg,
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
                    log.info("SENT message with id " + msg.getJMSMessageID());
                } else {
                    log.info("SENT message with counter " + msgCounter
                            + " and id " + msg.getJMSMessageID());
                }

                if (msg.getStringProperty("_HQ_DUPL_ID") != null) {
                    return msg.getStringProperty("_HQ_DUPL_ID");
                } else {
                    return null;
                }
            } catch (JMSException ex) {
                if (msgCounter == null) {
                    log.info("SEND RETRY - Sent message with id " + msg.getJMSMessageID());
                } else {
                    log.info("SEND RETRY - Sent message with counter " + msgCounter
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


    public static Message receiveMessage(final MessageConsumer consumer, final int counter)
            throws JMSException {

        return receiveMessage(consumer, counter, DEFAULT_MAX_RETRIES, DEFAULT_RECEIVE_TIMEOUT);
    }


    public static Message receiveMessage(final MessageConsumer consumer, final int counter,
            final int maxRetries, final long timeout) throws JMSException {

        Message msg;
        int numberOfRetries = 0;

        while (numberOfRetries < maxRetries) {
            try {
                msg = consumer.receive(timeout);
                return msg;
            } catch (JMSException ex) {
                numberOfRetries++;
                log.error("RETRY receive for message with counter " + counter);
            }
        }
        throw new JMSException("FAILURE - MaxRetry reached for message with counter " + counter);
    }

    /**
     * Method blocks until all receivers gets the numberOfMessages or timeout expires
     * <p/>
     * This is NOT sum{receivers.getCount()}. Each receiver must have numberOfMessages.
     *
     * @param receivers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    public static void waitForReceiversUntil(List<Client> receivers, int numberOfMessages, long timeout) {
        long startTimeInMillis = System.currentTimeMillis();

        for (Client c : receivers) {
            while (c.getCount() < numberOfMessages) {
                if ((System.currentTimeMillis() - startTimeInMillis) > timeout) {
                    Assert.fail("Client: " + c + " did not receive " + numberOfMessages + " in timeout: " + timeout);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     */
    public static void waitForClientsToFinish(Clients clients) {
        waitForClientsToFinish(clients, 600000);
    }

    /**
     *
     * Waits for the org.jboss.qa.hornetq.apps.clients to finish. If they do not finish in the specified time out then it fails the test.
     *
     * @param clients org.jboss.qa.hornetq.apps.clients
     * @param timeout timeout
     */
    public static void waitForClientsToFinish(Clients clients, long timeout) {
        long startTime = System.currentTimeMillis();
        try {
            while (!clients.isFinished()) {
                Thread.sleep(1000);
                if (System.currentTimeMillis() - startTime > timeout) {
                    Map<Thread, StackTraceElement[]> mst = Thread.getAllStackTraces();
                    StringBuilder stacks = new StringBuilder("Stack traces of all threads:");
                    for (Thread t : mst.keySet()) {
                        stacks.append("Stack trace of thread: ").append(t.toString()).append("\n");
                        StackTraceElement[] elements = mst.get(t);
                        for (StackTraceElement e : elements) {
                            stacks.append("---").append(e).append("\n");
                        }
                        stacks.append("---------------------------------------------\n");
                    }
                    log.error(stacks);
                    for (Client c : clients.getConsumers()) {
                        c.interrupt();
                    }
                    for (Client c : clients.getProducers()) {
                        c.interrupt();
                    }
                    Assert.fail("Clients did not stop in : " + timeout + "ms. Failing the test and trying to kill them all. Print all stacktraces:" + stacks);
                }
            }
        } catch (InterruptedException e) {
            log.error("waitForClientsToFinish failed: ", e);
        }
    }

    /**
     * Method blocks until all receivers gets the numberOfMessages or timeout expires
     *
     * @param producers        receivers
     * @param numberOfMessages numberOfMessages
     * @param timeout          timeout
     */
    public static void waitForProducersUntil(List<Client> producers, int numberOfMessages, long timeout) {
        long startTimeInMillis = System.currentTimeMillis();

        for (Client c : producers) {
            while (c.getCount() < numberOfMessages) {
                if ((System.currentTimeMillis() - startTimeInMillis) > timeout) {
                    Assert.fail("Client: " + c + " did not send " + numberOfMessages + " in timeout: " + timeout);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
