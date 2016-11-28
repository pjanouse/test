package org.jboss.qa.hornetq.apps.ejb;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.constants.Constants;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.sql.DataSource;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EJB sending messages to InQueue, and performing access to DB in UserTransaction
 */
@Stateless
@Remote(SimpleSendEJB.class)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
@TransactionManagement(TransactionManagementType.BEAN)
public class SenderEJBWithDatabaseAccess implements SimpleSendEJB {

    private static final Logger log = Logger.getLogger(SenderEJBWithDatabaseAccess.class.getName());
    private static AtomicInteger numberOfProcessedMessages = new AtomicInteger(0);

    @Resource(mappedName = Constants.POOLED_CONNECTION_FACTORY_JNDI_EAP7)
    private ConnectionFactory cf;

    @Resource(name = "lodhDb", mappedName = "java:/jdbc/lodhDS")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Resource
    SessionContext sessionContext;

    @Resource
    UserTransaction userTransaction;

    private DataSource dataSource;
    private javax.jms.Connection jmsConnection = null ;
    private java.sql.Connection sqlConnection = null;
    private Random random;
    private Queue queue;


    @Override
    public void createConnection() {
    }

    @Override
    public void closeConnection() {
    }

    @Override
    public void sendMessage() {
        try {
            userTransaction.begin();

            sendMessageToOutQueue();

            interactWithDatabase();

            userTransaction.commit();

            int internalCounter = numberOfProcessedMessages.incrementAndGet();

            if (internalCounter % 50 == 0)
                log.info("Finished processing message number" + internalCounter);

        }catch(Exception t){
            log.error(t.getMessage(), t);
            this.sessionContext.setRollbackOnly();
            try {
                userTransaction.rollback();
            } catch (SystemException e) {
                log.error(e);
            }
        }finally{
            if (jmsConnection != null) {
                try {
                    jmsConnection.close();
                } catch (JMSException e) {
                    log.fatal(e.getMessage(), e);
                }
            }
            if (sqlConnection != null) {
                try {
                    sqlConnection.close();
                    sqlConnection = null;
                } catch (SQLException e) {
                    log.fatal(e.getMessage(), e);
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
     * This method performs simple dummy select in db - just to have two data sources EJB interacts with.
     */
    private void interactWithDatabase() throws SQLException, JMSException {

        sqlConnection = dataSource.getConnection();
        PreparedStatement ps = sqlConnection.prepareStatement("SELECT COUNT(*) FROM MESSAGE_INFO2");
        ps.execute();
        ps.close();
    }

    public int sendCount() {
        return numberOfProcessedMessages.get();
    }

}



