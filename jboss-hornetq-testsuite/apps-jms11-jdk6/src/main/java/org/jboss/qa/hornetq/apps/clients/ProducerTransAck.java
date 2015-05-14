package org.jboss.qa.hornetq.apps.clients;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProducerTransAck extends Client {

    private static final Logger logger = Logger.getLogger(ProducerTransAck.class);
    private static int maxRetries = 50;

    MessageProducer producer;

    private List<Map<String, String>> listOfSentMessages = new ArrayList<Map<String, String>>();
    private List<Message> listOfMessagesToBeCommited = new ArrayList<Message>();

    private String hostname;
    private int port;
    private int messages;
    private String queueNameJndi;
    private boolean stop = false;
    private Exception exception = null;

    private int commitAfter = 10;

    private FinalTestMessageVerifier messageVerifier;
    private MessageBuilder messageBuilder = new TextMessageBuilder(10);

    /**
     * @deprecated use ProducerTransAck(Container container, String queueNameJndi, int messages)
     *
     * @param hostname      hostname
     * @param port          port
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    @Deprecated
    public ProducerTransAck(String hostname, int port, String queueNameJndi, int messages) {
        this(EAP6_CONTAINER, hostname, port, queueNameJndi, messages);
    }

    /**
     * @param container      container instance
     * @param messages       number of messages to send
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public ProducerTransAck(Container container, String queueNameJndi, int messages) {
        super(container);
        this.hostname = container.getHostname();
        this.port = container.getJNDIPort();
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;

    }

    /**
     * @param container     EAP container
     * @param hostname      hostname
     * @param port          port
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     *
     */
    @Deprecated
    public ProducerTransAck(String container, String hostname, int port, String queueNameJndi, int messages) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.messages = messages;
        this.queueNameJndi = queueNameJndi;
    }

    public void run() {

        final int MESSAGES_COUNT = messages;

        Context context = null;
        Connection con = null;
        Session session = null;

        try {

            context = getContext(hostname, port);

            ConnectionFactory cf = (ConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            Queue queue = (Queue) context.lookup(queueNameJndi);

            con = cf.createConnection();

            session = con.createSession(true, Session.SESSION_TRANSACTED);

            producer = session.createProducer(queue);

            Message msg = null;

            while (counter < MESSAGES_COUNT && !stop) {

                msg = messageBuilder.createMessage(session);
                msg.setIntProperty("count", counter);

                sendMessage(msg);

                listOfMessagesToBeCommited.add(msg);

                Thread.sleep(getTimeout());

                if (counter % commitAfter == 0) {

                    commitSession(session);
                    StringBuilder stringBuilder = new StringBuilder();
                    for (Message m : listOfMessagesToBeCommited) {
                        stringBuilder.append(m.getJMSMessageID());
                    }
                    logger.debug("Adding messages: " + stringBuilder.toString());
                    for (Message m : listOfMessagesToBeCommited) {
                        m = cleanMessage(m);
                        addMessage(listOfSentMessages, m);
                    }

                    logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                            + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty("_HQ_DUPL_ID"));
                    listOfMessagesToBeCommited.clear();

                }
            }

            commitSession(session);

            StringBuilder stringBuilder = new StringBuilder();
            for (Message m : listOfMessagesToBeCommited) {
                stringBuilder.append(m.getJMSMessageID());
            }
            logger.debug("Adding messages: " + stringBuilder.toString());
            for (Message m : listOfMessagesToBeCommited) {
                m = cleanMessage(m);
                addMessage(listOfSentMessages, m);
            }
//                    StringBuilder stringBuilder2 = new StringBuilder();
//                    for (Map<String,String> m : listOfSentMessages) {
//                        stringBuilder2.append("messageId: " + m.get("messageId") + "dupId: " + m.get("_HQ_DUPL_ID") + "##");
//                    }
//                    logger.debug("List of sent messages: " + stringBuilder2.toString());
//                    listOfSentMessages.addAll(listOfMessagesToBeCommited);
            logger.info("COMMIT - session was commited. Last message with property counter: " + counter
                    + ", messageId:" + msg.getJMSMessageID() + ", dupId: " + msg.getStringProperty("_HQ_DUPL_ID"));
            listOfMessagesToBeCommited.clear();

            producer.close();

            if (messageVerifier != null) {
                messageVerifier.addSendMessages(listOfSentMessages);
            }

        } catch (Exception e) {

            logger.error("Producer got exception and ended:", e);
            exception = e;

        } finally {

            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendMessage(Message msg) {
        int numberOfRetries = 0;
        try {
            if (numberOfRetries > maxRetries) {
                try {
                    throw new Exception("Number of retries (" + numberOfRetries + ") is greater than limit (" + maxRetries + ").");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            if (numberOfRetries > 0) {
                logger.info("Retry sent - number of retries: (" + numberOfRetries + ") message: " + msg.getJMSMessageID() + ", counter: " + counter);
            }
            numberOfRetries++;
            producer.send(msg);
            logger.debug("Sent message with property counter: " + counter + ", messageId:" + msg.getJMSMessageID()
                    + " dupId: " + msg.getStringProperty("_HQ_DUPL_ID"));
            counter++;
        } catch (JMSException ex) {
            logger.error("Failed to send message - counter: " + counter, ex);
            sendMessage(msg);
        }
    }

    private void commitSession(Session session) throws Exception {
        int numberOfRetries = 0;
        while (true) {
            try {

                // try commit
                session.commit();

                // if successful -> return
                return;

            } catch (TransactionRolledBackException ex) {
                // if transaction rollback exception -> send messages again and commit
                logger.error("Producer got exception for commit(). Producer counter: " + counter, ex);

                // don't repeat this more than once, this can't happen
                if (numberOfRetries > 2) {
                    throw new Exception("Fatal error. TransactionRolledBackException was thrown more than once for one commit. Message counter: " + counter
                            + " Client will terminate.", ex);
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(m);
                }

                numberOfRetries++;

            } catch (JMSException ex) {
                // if jms exception -> send messages again and commit (in this case server will throw away possible duplicates because dup_id  is set so it's safe)
                ex.printStackTrace();

                // don't repeat this more than once - it's exception because of duplicates
                if (numberOfRetries > 0) {
                    return;
                }

                counter -= listOfMessagesToBeCommited.size();

                for (Message m : listOfMessagesToBeCommited) {
                    sendMessage(m);
                }
                numberOfRetries++;
            }
        }
    }


    public void setMessageVerifier(FinalTestMessageVerifier messageVerifier) {
        this.messageVerifier = messageVerifier;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }

    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }

    public List<Map<String, String>> getListOfSentMessages() {
        logger.debug("listOfSentMessages" + listOfSentMessages.size());
        return listOfSentMessages;
    }

    public void stopSending() {
        this.stop = true;
    }

    /**
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getQueueNameJndi() {
        return queueNameJndi;
    }

    public void setQueueNameJndi(String queueNameJndi) {
        this.queueNameJndi = queueNameJndi;
    }

    @Override
    public int getCount() {
        return counter;
    }

    public static void main(String[] args) throws InterruptedException {

        ProducerTransAck producer = new ProducerTransAck(CONTAINER_TYPE.EAP7_CONTAINER.toString(), "127.0.0.1", 8080, "jms/queue/testQueue", 20);
        MessageBuilder builder = new TextMessageBuilder(14);
        producer.setMessageBuilder(builder);
        producer.setTimeout(0);
        producer.setCommitAfter(10);
        producer.start();
        producer.join();
        System.out.println("Number of sent messages: " + producer.getListOfSentMessages().size());

    }
}
