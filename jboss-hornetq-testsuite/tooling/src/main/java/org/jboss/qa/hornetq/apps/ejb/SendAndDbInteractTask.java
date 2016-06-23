package org.jboss.qa.hornetq.apps.ejb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.ejb.SenderEJBWithDatabaseAccessUsingExecutor;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;

import javax.ejb.SessionContext;
import javax.jms.*;
import javax.sql.DataSource;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class SendAndDbInteractTask implements Runnable {

    private static final Logger log = Logger.getLogger(SenderEJBWithDatabaseAccessUsingExecutor.class.getName());
    private static AtomicInteger numberOfProcessedMessages = new AtomicInteger(0);

    private javax.jms.Connection jmsConnection = null;
    private java.sql.Connection sqlConnection = null;
    private SessionContext sessionContext;
    private DataSource dataSource;
    private ConnectionFactory cf;
    private Random random;
    private Queue queue;
    private UserTransaction userTransaction;

    public SendAndDbInteractTask(SessionContext sessionContext, DataSource dataSource, ConnectionFactory cf, UserTransaction userTransaction) {
        this.sessionContext = sessionContext;
        this.dataSource = dataSource;
        this.cf = cf;
        this.userTransaction = userTransaction;
    }

    @Override
    public void run() {

        try {
            userTransaction.begin();

            sendMessageToOutQueue();

            interactWithDatabase();

            userTransaction.commit();

            int internalCounter = numberOfProcessedMessages.incrementAndGet();

            if (internalCounter % 50 == 0)
                log.info("Finished processing message number" + internalCounter);

        } catch (Exception t) {
            log.error(t.getMessage(), t);
            this.sessionContext.setRollbackOnly();
            try {
                userTransaction.rollback();
            } catch (SystemException e) {
                log.error(e);
            }
        } finally {
            if (jmsConnection != null) {
                try {
                    jmsConnection.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
            if (sqlConnection != null) {
                try {
                    sqlConnection.close();
                    sqlConnection = null;
                } catch (SQLException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
        }
    }

    private void sendMessageToOutQueue() throws JMSException {
        long randomLong;
        if (random == null) {
            random = new Random();
            randomLong = 1;
        }
        randomLong = random.nextLong();

        jmsConnection = cf.createConnection();
        Session session = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        if (queue == null) queue = session.createQueue("InQueue");
        MessageProducer producerToOutServer = session.createProducer(queue);

        ObjectMessage messageToOutServer = session.createObjectMessage(new MessageInfo("name" + randomLong,
                "cool-address" + randomLong, 1));
        messageToOutServer.setStringProperty("inMessageId", messageToOutServer.getJMSMessageID());
        messageToOutServer.setStringProperty("_HQ_DUPL_ID", messageToOutServer.getStringProperty("_HQ_DUPL_ID"));
        producerToOutServer.send(messageToOutServer);
        log.debug("Sending new message to OutServer with inMessageId: " + messageToOutServer.getStringProperty("inMessageId")
                + " and messageId: " + messageToOutServer.getJMSMessageID());
    }

    /**
     * This method performs simple dummy update in db - just to have two data sources EJB interacts with.
     *
     * @throws SQLException
     * @throws JMSException
     */
    private void interactWithDatabase() throws SQLException, JMSException {

        sqlConnection = dataSource.getConnection();
        PreparedStatement ps = sqlConnection.prepareStatement("SELECT COUNT(*) FROM MESSAGE_INFO2");
        ps.execute();
        ps.close();
    }

    public static int getCounter() {
        return numberOfProcessedMessages.get();
    }
}