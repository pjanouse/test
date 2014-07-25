package org.jboss.qa.hornetq.apps.clients;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.TransactionManager;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.*;
import javax.transaction.xa.XAResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
* Simple sender with transaction acknowledge session. Able to fail over.
* <p/>
* This class extends Thread class and should be started as a thread using
* start().
*
* @author mnovak@redhat.com
*/
public class XAConsumerTransAck extends Client {

    private static final Logger logger = Logger.getLogger(XAConsumerTransAck.class);

    private int maxRetries = 30;
    private String hostname = "localhost";
    private int port = getJNDIPort();
    private String queueNameJndi = "jms/queue/testQueue0";
    private int commitAfter = 10;
    private FinalTestMessageVerifier messageVerifier;
    private Exception exception = null;
    private boolean stop = false;
    private int receiveTimeout = 3000;
    private Queue queue = null;
    private int counter = 0;
    private List<Message> listOfReceivedMessages = new ArrayList<Message>();
    javax.transaction.TransactionManager txMgr = null;

    /**
     * @param hostname       hostname
     * @param port           port
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public XAConsumerTransAck(String hostname, int port, String queueNameJndi) {
        this(EAP6_CONTAINER, hostname, port, queueNameJndi);
    }

    /**
     * @param hostname       hostname
     * @param port           port
     * @param queueNameJndi  set jndi name of the queue to send messages
     */
    public XAConsumerTransAck(String container, String hostname, int port, String queueNameJndi) {
        super(container);
        this.hostname = hostname;
        this.port = port;
        this.queueNameJndi = queueNameJndi;
    }

    @Override
    public void run() {

        Context context = null;

        XAConnection con = null;

        XASession xaSession = null;

        Session session = null;

        try {

            context = getContext(hostname, port);

            queue = (Queue) context.lookup("jms/queue/testQueue0");

            System.setProperty(JTAEnvironmentBean.class.getSimpleName() + "." + "transactionManagerClassName", TransactionManagerImple.class.getName());
            System.setProperty(JTAEnvironmentBean.class.getSimpleName() + "." + "transactionSynchronizationRegistryClassName", TransactionSynchronizationRegistryImple.class.getName());

            txMgr = TransactionManager.transactionManager();

            XAConnectionFactory cf = (XAConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            con = cf.createXAConnection();

            con.start();

            xaSession = con.createXASession();

            session = xaSession.getSession();

            MessageConsumer consumer = session.createConsumer(queue);

            XAResource xaRes = xaSession.getXAResource();

            while (!stop) {

                receiveMessagesInXATransaction(xaRes, consumer);

            }

            consumer.close();

        } catch (Exception e) {
            exception = e;
            e.printStackTrace();
            System.out.println("Consumer got exception and ended:" + e.getMessage());

        } finally {

            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                }
            }
        }
    }

    private void receiveMessagesInXATransaction(XAResource xaRes, MessageConsumer consumer) throws Exception {

        int numberOfRetries = 0;

        int count = counter;

        while (numberOfRetries < maxRetries) {

            Transaction transaction = null;

            List<Message> receivedMessageWindow = null;

            // try send message window and commit - if fails then rollback and try again. if success then commit xa trans too
            try {
                txMgr.begin();
            } catch (NotSupportedException ex) {
                logger.error(ex);
            } catch (SystemException ex) {
                logger.error(ex);
            }

            try {
                transaction = txMgr.getTransaction();
            } catch (SystemException ex) {
                logger.error(ex);
            }

            try {
                transaction.enlistResource(xaRes);
            } catch (RollbackException ex) {
                logger.error(ex);
                return;
            } catch (java.lang.IllegalStateException ex) {
                logger.error(ex);
            } catch (SystemException ex) {
                logger.error(ex);
            }


            Message message = null;

            receivedMessageWindow = new ArrayList<Message>();
            try {
                while ((message = consumer.receive(receiveTimeout)) != null) {

                    count++;

                    receivedMessageWindow.add(message);

                    System.out.println("Consumer for node: " + hostname
                            + " and queue: " + queue + " Received message: " + count);

                    if (count % commitAfter == 0)   {
                        break;
                    }

                }
            } catch (JMSException ex) {
                logger.error(ex);
            }

            try {
                System.out.println("Commit transaction. Counter is: " + count);
                txMgr.commit();
            } catch (RollbackException ex) {

                return;

            } catch (HeuristicMixedException ex) {
                logger.error(ex);
            } catch (HeuristicRollbackException ex) {
                logger.error(ex);
            } catch (SecurityException ex) {
                logger.error(ex);
            } catch (java.lang.IllegalStateException ex) {
                logger.error(ex);
            } catch (SystemException ex) {
                logger.error(ex);
            }

            listOfReceivedMessages.addAll(receivedMessageWindow);

            counter += receivedMessageWindow.size();

            receivedMessageWindow.clear();

            if (message == null) {
                System.out.println("Setting stop to true");
                stop = true;
            }

            return;


        }

        // maxretry reached then throw exception above
        throw new RuntimeException("FAILURE - MaxRetry reached for consumer for node: " + hostname
                + ". Sent message with property count: " + count);
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the queueNameJndi
     */
    public String getQueueNameJndi() {
        return queueNameJndi;
    }

    /**
     * @param queueNameJndi the queueNameJndi to set
     */
    public void setQueueNameJndi(String queueNameJndi) {
        this.queueNameJndi = queueNameJndi;
    }

    /**
     * @return the messageVerifier
     */
    public FinalTestMessageVerifier getMessageVerifier() {
        return messageVerifier;
    }

    /**
     * @param messageVerifier the messageVerifier to set
     */
    public void setMessageVerifier(FinalTestMessageVerifier messageVerifier) {
        this.messageVerifier = messageVerifier;
    }

    /**
     * @return the exception
     */
    public Exception getException() {
        return exception;
    }

    /**
     * @param exception the exception to set
     */
    public void setException(Exception exception) {
        this.exception = exception;
    }

    /**
     * @return the commitAfter
     */
    public int getCommitAfter() {
        return commitAfter;
    }

    /**
     * Number of messages to be commited at once.
     *
     * @param commitAfter the commitAfter to set
     */
    public void setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
    }

    public static void main(String[] args) throws InterruptedException {

        XAConsumerTransAck consumer = new XAConsumerTransAck("192.168.40.1", getJNDIPort(), "jms/queue/testQueue0");
        consumer.setCommitAfter(1);
        consumer.start();

        consumer.join();
    }

}
