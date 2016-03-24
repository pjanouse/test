// TODO initialize client by container
package org.jboss.qa.hornetq.apps.clients;


import com.arjuna.ats.arjuna.common.*;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import com.arjuna.ats.jta.TransactionManager;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.FinalTestMessageVerifier;
import org.jboss.qa.hornetq.tools.ContainerUtils;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a JMS consumer which receives messages in XA transaction. JMS consumer is able to failover.
 * <p/>
 * Messages are received in XA transaction - 2 phase commit if forced even with one xaresource
 * <p/>
 * <p/>
 * This class extends Thread class and should be started as a thread using
 * start().
 *
 * @author mnovak@redhat.com
 */
public class XAConsumerTransAck extends Client {

    private static final Logger logger = Logger.getLogger(XAConsumerTransAck.class);

    public String nodeIdentifierAndObjectStoreDir = "ObjectStore";

    protected static javax.transaction.TransactionManager txMgr = null;
    protected static final Object txMgrLock = new Object();

    // this lock is used so we can synchronize recovery scans among XaConsumers, access to recovery manager
    protected static RecoveryManager recoveryManager = null;
    protected static final Object recoveryManagerLock = new Object();

    // only the first consumer can create transaction manager and recovery manager, also only the last can close transaction manager and recovery manager
    private static AtomicInteger numberOfConsumers = new AtomicInteger(0);

    public String consumerIdentifier = null;

    private String hostname;

    // port for JNDI lookup of JMS administration objects
    private int port;

    // JNDI name of the queue
    private String queueNameJndi;

    // after how many messages XA transaction is committed
    private int commitAfter = 10;

    // message verifier which
    private FinalTestMessageVerifier messageVerifier;

    // holds last exception which was thrown
    private Exception exception = null;

    // whether consumer should stop consuming messages
    private boolean stop = false;

    // consumer.receive(receiveTimeout)
    private int receiveTimeout = 3000;

    private Queue queue = null;

    protected int howManyTimesToRecoverTransactionsAfterClientFinishes = 3;

    // list of received messages which were received and committed
    private List<Map<String, String>> listOfReceivedMessages = new ArrayList<Map<String, String>>();

    private Container liveServer;
    private Container backupServer;

    /**
     * @param containerToConnect container to which the client must connect
     * @param queueNameJndi      set jndi name of the queue to send messages
     * @param liveServer         live server for recovery manager
     * @param backupServer       backup server for recovery manager
     */
    public XAConsumerTransAck(Container containerToConnect, String queueNameJndi, Container liveServer, Container backupServer) {
        super(containerToConnect);
        this.hostname = containerToConnect.getHostname();
        this.port = containerToConnect.getJNDIPort();
        this.queueNameJndi = queueNameJndi;
        consumerIdentifier = "consumer-" + numberOfConsumers.incrementAndGet();
        this.liveServer = liveServer;
        this.backupServer = backupServer;
    }



    @Override
    public void run() {

        Context context = null;

        XAConnection con = null;

        XASession xaSession;

        XAResource xaRes;

        Session session = null;

        try {

            context = getContext(hostname, port);

            queue = (Queue) context.lookup(queueNameJndi);

            synchronized (txMgrLock) {
                if (txMgr == null) {
                    System.setProperty(JTAEnvironmentBean.class.getSimpleName() + "." + "transactionManagerClassName", TransactionManagerImple.class.getName());
                    System.setProperty(JTAEnvironmentBean.class.getSimpleName() + "." + "transactionSynchronizationRegistryClassName", TransactionSynchronizationRegistryImple.class.getName());

                    // this is necessary - because without prepare all this is bad idea
                    // TODO this is a bad idea to force two phase commmit, but without it client does not have deterministic way, what to do with messages from failed transaction when failover occurs
                    arjPropertyManager.getCoordinatorEnvironmentBean().setCommitOnePhase(false);

                    arjPropertyManager.getCoreEnvironmentBean().setNodeIdentifier(nodeIdentifierAndObjectStoreDir);
                    arjPropertyManager.getObjectStoreEnvironmentBean().setObjectStoreDir(nodeIdentifierAndObjectStoreDir);
                    // it might happen that many instances of xa xaconsumer are started at the same time,
                    txMgr = TransactionManager.transactionManager();
                }
            }

            XAConnectionFactory cf = (XAConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            con = cf.createXAConnection();

            con.start();

            xaSession = con.createXASession();

            session = xaSession.getSession();

            MessageConsumer consumer = session.createConsumer(queue);

            // got and start any recovery which is necessary
            startRecovery();

            // receive messages from queue in XA transaction - commits XA transaction every "commitAfter" message
            while (!stop) {

                try {

                    xaRes = xaSession.getXAResource();

                    // for any exception we throw away uncommitted messages and will try again
                    receiveMessagesInXATransaction(xaRes, consumer);

                } catch (Exception ex) {
                    // in case of failover we have to recreate all JMS objects
                    logger.warn("Exception was thrown to xa consumer - " + consumerIdentifier + ": ", ex);
                    logger.warn("Recreating all JMS objects.");
                    // we have to recreate all objects so failover is successful TODO check whether it's ok behaviour
                    // it's necessary to close old sessions so no more messages are scheduled for it TODO check whether it's ok behaviour
//                    session.close();
//                    xaSession.close();
//
//                    xaSession = con.createXASession();
//                    session = xaSession.getSession();
//                    consumer = session.createConsumer(queue);

                    // run recovery of failed transactions -- this just to commit already prepared transactions
                    runRecoveryScan();

                }
            }

            // we need at least 2 recovery scans
            for (int i = 0; i < howManyTimesToRecoverTransactionsAfterClientFinishes; i++) {
                runRecoveryScan();
            }

            consumer.close();

        } catch (Exception e) {

            exception = e;
            logger.error("Consumer " + consumerIdentifier + " got exception and ended:", e);

        } finally {

            // clean up resources
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException ignored) {
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException ignored) {
                }
            }
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                }
            }
            if (messageVerifier != null) {
                messageVerifier.addReceivedMessages(listOfReceivedMessages);
            }

            // we have to decrement number of consumer so stop recovery is aware of it and can stop
            numberOfConsumers.decrementAndGet();
            // sleep some time to stop recovery manager
            stopRecovery();
        }
    }

    /*
     * Create and begin transaction
     * Enlist xaResource to transaction
     * Receive x messages (message window)
     * Commit transaction
     *
     * Exception is thrown when something bad happens, usually during failover. We don't care about failed transactions
     * as TM should be in charge of it.
     */
    private void receiveMessagesInXATransaction(XAResource xaRes, MessageConsumer consumer) throws Exception {

        // counter contains number of received messages which are already commited
        // numberOfReceivedMessages contains number of messages which are commited + not yet commited
        int numberOfReceivedMessages = counter;

        Transaction transaction = null;

        try {
            // start transaction
            txMgr.begin();

            transaction = txMgr.getTransaction();

            // enlist xa resource to transaction
            transaction.enlistResource(xaRes);
//            transaction.enlistResource(new DummyXAResource());

            Message message;

            // list of messages which is received in this transaction
            List<Message> receivedMessageWindow = new ArrayList<Message>();

            // while message not null, receive messages
            while ((message = consumer.receive(receiveTimeout)) != null) {

                numberOfReceivedMessages++;

                receivedMessageWindow.add(message);

                logger.info("Consumer for node: " + hostname
                        + " and queue: " + queue.getQueueName() + " received message: " + message.getJMSMessageID()
                        + " . Number of received commited and uncommitted messages: "
                        + numberOfReceivedMessages + ", number of commited messages: " + counter);

                // if "commitAfter" messages is received break cycle and commit transaction
                if (numberOfReceivedMessages % commitAfter == 0) {
                    break;
                }

            }

            logger.info("Going to commit XA transaction. Number of received commited and uncommitted messages will be:: " + numberOfReceivedMessages);
            logger.warn("#####################################");
            logger.warn("Transaction status is: " + getTransactionStatus(transaction));
            logger.warn("#####################################");

            txMgr.commit();

            logger.warn("#####################################");
            logger.warn("Transaction status after commit is: " + getTransactionStatus(transaction));
            logger.warn("#####################################");

            // add committed messages to list of received messages
            addMessages(listOfReceivedMessages, receivedMessageWindow);

            // increase number of received messages
            counter += receivedMessageWindow.size();

            // clear messages window
            receivedMessageWindow.clear();

            // if null was received then queue is empty and stop the consumer
            if (message == null) {
                logger.info("Setting stop to true");
                stop = true;
            }

        } finally {
            logger.warn("#####################################");
            logger.warn("Transaction status in finally block (is: " + getTransactionStatus(transaction));
            logger.warn("#####################################");
        }

    }

    private String getTransactionStatus(Transaction transaction) throws SystemException {
        int status = transaction.getStatus();
        if (status == Status.STATUS_ACTIVE) {
            return "STATUS_ACTIVE";
        } else if (status == Status.STATUS_MARKED_ROLLBACK) {
            return "STATUS_MARKED_ROLLBACK";
        } else if (status == Status.STATUS_PREPARED) {
            return "STATUS_PREPARED";
        } else if (status == Status.STATUS_COMMITTED) {
            return "STATUS_COMMITTED";
        } else if (status == Status.STATUS_ROLLEDBACK) {
            return "STATUS_ROLLEDBACK";
        } else if (status == Status.STATUS_UNKNOWN) {
            return "STATUS_UNKNOWN";
        } else if (status == Status.STATUS_NO_TRANSACTION) {
            return "STATUS_NO_TRANSACTION";
        } else if (status == Status.STATUS_PREPARING) {
            return "STATUS_PREPARING";
        } else if (status == Status.STATUS_COMMITTING) {
            return "STATUS_COMMITTING";
        } else if (status == Status.STATUS_ROLLING_BACK) {
            return "STATUS_ROLLING_BACK";
        } else {
            return "This status is not known to JTA.";
        }
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

    public List<Map<String, String>> getListOfReceivedMessages() {
        return listOfReceivedMessages;
    }

    public void setListOfReceivedMessages(List<Map<String, String>> listOfReceivedMessages) {
        this.listOfReceivedMessages = listOfReceivedMessages;
    }


    public void startRecovery() throws Exception {

        synchronized (recoveryManagerLock) {
            if (recoveryManager == null) {
                if (ContainerUtils.isEAP7(container)) {
                    recoveryManager = createRecoveryManagerEAP7();
                } else {
                    recoveryManager = createRecoveryManagerEAP6();
                }
            }
        }
    }

    protected RecoveryManager createRecoveryManagerEAP6() throws CoreEnvironmentBeanException {
        String resourceRecoveryClass = "org.hornetq.jms.server.recovery.HornetQXAResourceRecovery";
        //org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=localhost,port=5445;org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=localhost1,port=5446"
        String remoteResourceRecoveryOpts = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory," +
                "guest,guest,host=" + liveServer.getHostname() + ",port=" + liveServer.getHornetqPort()
                + ";org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host="
                + backupServer.getHostname() + " ,port=" + backupServer.getHornetqPort();

        logger.info("Start recovery with configuration: " + remoteResourceRecoveryOpts);
//                String remoteResourceRecoveryOpts = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory," +
//                        "guest,guest,host=127.0.0.1,port=5445;org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=127.0.0.1,port=7445";

        List<String> recoveryClassNames = new ArrayList<String>();
        recoveryClassNames.add(resourceRecoveryClass + ";" + remoteResourceRecoveryOpts);

        // this appears that it does not work
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreDir(nodeIdentifierAndObjectStoreDir);
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore").setObjectStoreDir(nodeIdentifierAndObjectStoreDir);
        BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier(nodeIdentifierAndObjectStoreDir);

        BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class).setXaResourceRecoveryClassNames(recoveryClassNames);
        BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class).setRecoveryBackoffPeriod(1);

        RecoveryManager.delayRecoveryManagerThread();

        RecoveryManager rm = RecoveryManager.manager();
        rm.initialize();
        return rm;
    }

    protected RecoveryManager createRecoveryManagerEAP7() throws CoreEnvironmentBeanException {
        String resourceRecoveryClass = "org.jboss.activemq.artemis.wildfly.integration.recovery.WildFlyActiveMQXAResourceRecovery";
        String remoteResourceRecoveryOpts = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory," +
                "guest,guest,host=" + liveServer.getHostname() + ",port=5445"
                + ";org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host="
                + backupServer.getHostname() + " ,port=" + (5445 + backupServer.getPortOffset());

        logger.info("Start recovery with configuration: " + remoteResourceRecoveryOpts);
//                String remoteResourceRecoveryOpts = "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory," +
//                        "guest,guest,host=127.0.0.1,port=5445;org.hornetq.core.remoting.impl.netty.NettyConnectorFactory,guest,guest,host=127.0.0.1,port=7445";

        List<String> recoveryClassNames = new ArrayList<String>();
        recoveryClassNames.add(resourceRecoveryClass + ";" + remoteResourceRecoveryOpts);

        // this appears that it does not work
        BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreDir(nodeIdentifierAndObjectStoreDir);
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore").setObjectStoreDir(nodeIdentifierAndObjectStoreDir);
        BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class).setNodeIdentifier(nodeIdentifierAndObjectStoreDir);

        BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class).setXaResourceRecoveryClassNames(recoveryClassNames);
        BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class).setRecoveryBackoffPeriod(1);

        RecoveryManager.delayRecoveryManagerThread();

        RecoveryManager rm = RecoveryManager.manager();
        rm.initialize();
        return rm;
    }

    /**
     * Only the last consumer can stop recovery manager
     */
    public void stopRecovery() {
        try { // we can afford to synchronize this even when this should be called when no recover scan is in progress - we're just being safe here
            synchronized (recoveryManagerLock) {
                if (numberOfConsumers.get() == 0) {
                    recoveryManager.terminate();
                    recoveryManager = null;
                    logger.info("Recovery manager was stopped.");
                }
            }
        } catch (Exception ex) {
            logger.error("Exception when calling stopRecovery. Consumer: " + printInfoAboutTMOfConsumer(), ex);
        }
    }

    public void runRecoveryScan() {
        try {
            synchronized (recoveryManagerLock) {
                recoveryManager.scan();
            }
        } catch (Exception ex) {
            logger.error("Exception when calling recovery scan. " + printInfoAboutTMOfConsumer(), ex);
        }
    }

    public String printInfoAboutTMOfConsumer() {
        return "Consumer: " + consumerIdentifier + " with txManager object store: " + nodeIdentifierAndObjectStoreDir;
    }

    public static void main(String[] args) throws Exception {
//        XAConsumerTransAck xaConsumer = new XAConsumerTransAck("127.0.0.1", 4447, "jms/queue/testQueue0");
//        xaConsumer.startRecovery();
//        for (int i = 0; i < 20; i++) {
//            xaConsumer.runRecoveryScan();
//            Thread.sleep(1000);
//        }
//        xaConsumer.stopRecovery();
    }


}
