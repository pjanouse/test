//package org.jboss.qa.hornetq.apps.clients;
//
//import org.apache.log4j.Logger;
//import org.hornetq.core.transaction.impl.XidImpl;
//import org.hornetq.utils.UUIDGenerator;
//import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
//
//import javax.jms.*;
//import javax.naming.Context;
//import javax.naming.InitialContext;
//import javax.naming.NamingException;
//import javax.transaction.xa.XAException;
//import javax.transaction.xa.XAResource;
//import javax.transaction.xa.Xid;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Properties;
//import java.util.Random;
//
///**
// * This class extends Thread class and should be started as a thread using
// * start().
// *
// * @author mnovak@redhat.com
// */
//public class XAConsumer extends Client {
//
//    private static final Logger logger = Logger.getLogger(XAConsumer.class);
//
//    private int maxRetries = 30;
//    private String hostname = "localhost";
//    private int port = 4447;
//    private String queueNameJndi = "jms/queue/testQueue0";
//    private int commitAfter = 10;
//    private FinalTestMessageVerifier messageVerifier;
//    private Exception exception = null;
//    private boolean stop = false;
//    private int receiveTimeout = 3000;
//    private Queue queue = null;
//    private int counter = 0;
//    private List<Message> listOfReceivedMessages = new ArrayList<Message>();
//    Random r = new Random();
//
//    /**
//     * @param hostname       hostname
//     * @param port           port
//     * @param queueNameJndi  set jndi name of the queue to send messages
//     */
//    public XAConsumer(String hostname, int port, String queueNameJndi) {
//        this(EAP6_CONTAINER, hostname, port, queueNameJndi);
//    }
//
//    /**
//     * @param container     container
//     * @param hostname       hostname
//     * @param port           port
//     * @param queueNameJndi  set jndi name of the queue to send messages
//     */
//    public XAConsumer(String container, String hostname, int port, String queueNameJndi) {
//        super(container);
//        this.hostname = hostname;
//        this.port = port;
//        this.queueNameJndi = queueNameJndi;
//    }
//
//    @Override
//    public void run() {
//
//        Context context = null;
//
//        XAConnection con = null;
//
//        XASession xaSession = null;
//
//        Session session = null;
//
//        try {
//
//            context = getContext(hostname, port);
//
//            queue = (Queue) context.lookup("jms/queue/testQueue0");
//
//            XAConnectionFactory cf = (XAConnectionFactory) context.lookup(getConnectionFactoryJndiName());
//
//            con = cf.createXAConnection();
//
//            con.start();
//
//            xaSession = con.createXASession();
//
//            session = xaSession.getSession();
//
//            MessageConsumer consumer = session.createConsumer(queue);
//
//            while (!stop) {
//
//                receiveMessagesInXATransaction(consumer, xaSession);
//
//            }
//
//            if (messageVerifier != null) {
//                messageVerifier.addReceivedMessages(listOfReceivedMessages);
//            }
//
//        } catch (Exception e) {
//
//            exception = e;
//            logger.error("Consumer got exception and ended:" + e.getMessage(), e);
//
//        } finally {
//
//            if (con != null) {
//                try {
//                    con.close();
//                } catch (JMSException e) {
//                }
//            }
//            if (context != null) {
//                try {
//                    context.close();
//                } catch (NamingException e) {
//                }
//            }
//        }
//    }
//
//    private void receiveMessagesInXATransaction(MessageConsumer consumer, XASession xaSession) throws Exception {
//
//        XAResource xaRes = xaSession.getXAResource();
//
//        int count = counter;
//
//        Xid xid = new XidImpl(("xa-example1" + r.nextInt()).getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
//
//        List<Message> receivedMessageWindow = null;
//
//        try {
//
//            xaRes.start(xid, XAResource.TMNOFLAGS);
//
//            Message message = null;
//
//            receivedMessageWindow = new ArrayList<Message>();
//
//            while ((message = consumer.receive(receiveTimeout)) != null && (count - counter) < commitAfter) {
//
//                count++;
//
//                receivedMessageWindow.add(message);
//
//                System.out.println("Consumer for node: " + hostname
//                        + " and queue: " + queue + " Received message with counter: " + count);
//
//            }
//
//            xaRes.end(xid, XAResource.TMSUCCESS);
//
//            xaRes.commit(xid, true);
//
//            if (message == null) {
//                stop = true;
//            }
//
//        } catch (XAException ex) {
//
//            logger.error("Exception: ", ex);
//
//            tryCommitAgain(xid, xaRes);
//
//        } finally {
//
//            listOfReceivedMessages.addAll(receivedMessageWindow);
//
//            counter += receivedMessageWindow.size();
//
//            receivedMessageWindow.clear();
//
//        }
//    }
//
//    /**
//     * @return the hostname
//     */
//    public String getHostname() {
//        return hostname;
//    }
//
//    /**
//     * @param hostname the hostname to set
//     */
//    public void setHostname(String hostname) {
//        this.hostname = hostname;
//    }
//
//    /**
//     * @return the port
//     */
//    public int getPort() {
//        return port;
//    }
//
//    /**
//     * @param port the port to set
//     */
//    public void setPort(int port) {
//        this.port = port;
//    }
//
//    /**
//     * @return the queueNameJndi
//     */
//    public String getQueueNameJndi() {
//        return queueNameJndi;
//    }
//
//    /**
//     * @param queueNameJndi the queueNameJndi to set
//     */
//    public void setQueueNameJndi(String queueNameJndi) {
//        this.queueNameJndi = queueNameJndi;
//    }
//
//    /**
//     * @return the messageVerifier
//     */
//    public FinalTestMessageVerifier getMessageVerifier() {
//        return messageVerifier;
//    }
//
//    /**
//     * @param messageVerifier the messageVerifier to set
//     */
//    public void setMessageVerifier(FinalTestMessageVerifier messageVerifier) {
//        this.messageVerifier = messageVerifier;
//    }
//
//    /**
//     * @return the exception
//     */
//    public Exception getException() {
//        return exception;
//    }
//
//    /**
//     * @param exception the exception to set
//     */
//    public void setException(Exception exception) {
//        this.exception = exception;
//    }
//
//    /**
//     * @return the commitAfter
//     */
//    public int getCommitAfter() {
//        return commitAfter;
//    }
//
//    /**
//     * Number of messages to be commited at once.
//     *
//     * @param commitAfter the commitAfter to set
//     */
//    public void setCommitAfter(int commitAfter) {
//        this.commitAfter = commitAfter;
//    }
//
//    public static void main(String[] args) throws InterruptedException {
//
//        XAConsumer consumer = new XAConsumer("192.168.1.1", 4447, "jms/queue/testQueue0");
//
//        consumer.start();
//
//        consumer.join();
//    }
//
//    private void tryCommitAgain(Xid xid, XAResource xaRes) throws Exception {
//
//        int numberOfTries = 0;
//        while (numberOfTries < maxRetries) {
//            try {
//
//                Thread.sleep(3000);
//
//                xaRes.commit(xid, false);
//
//                return;
//
//            } catch (XAException ex) {
//
//                numberOfTries++;
//                if (ex.errorCode == XAException.XAER_NOTA) {
//                    return;
//                } else if (ex.errorCode == XAException.XA_RETRY) {
//                    System.out.println("Exception during commit, try: " + numberOfTries);
//                    ex.printStackTrace();
//                }
//
//            }
//        }
//        throw new Exception("Retrying commit failed. MaxRetries: " + maxRetries + " Stopping consumer.");
//    }
//
//}
