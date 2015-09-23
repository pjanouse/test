package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;
import org.jboss.qa.hornetq.constants.Constants;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A MdbToDBAndRemoteInOutQueue used for lodh tests with network failure
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue" and database.
 *
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb1",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbToDBAndRemoteInOutQueue implements MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbToDBAndRemoteInOutQueue.class.getName());
    private Queue queue = null;
    private DataSource dataSource;
    private java.sql.Connection sqlConnection = null;
    private javax.jms.Connection jmsConnection = null;
    public static AtomicInteger numberOfProcessedMessages = new AtomicInteger();

    @Resource(mappedName = Constants.TO_OUT_SERVER_CONNECTION_FACTORY_JNDI_NAME)
    private ConnectionFactory cf;

    @Resource(name = "lodhDb", mappedName = "java:/jdbc/lodhDS")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Resource
    private MessageDrivenContext context;

    @Override
    public void onMessage(Message message) {

        try {

            int internalCounter = numberOfProcessedMessages.incrementAndGet();

            sendMessageToOutQueue(message);

            insertMessageToDatabase(message, internalCounter);

            if (internalCounter % 100 == 0)
                log.info("Processed message: " + message);
        } catch (Exception t) {
            log.error(t.getMessage(), t);
            this.context.setRollbackOnly();
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
                } catch (SQLException ex) {
                }
            }
        }
    }

    private void sendMessageToOutQueue(Message message) throws JMSException {

        jmsConnection = cf.createConnection();
        Session session = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        if (queue == null) queue = session.createQueue("OutQueue");
        String text = message.getJMSMessageID() + " processed by: " + hashCode();
        MessageProducer producerToOutServer = session.createProducer(queue);
        TextMessage messageToOutServer = session.createTextMessage(text);
        messageToOutServer.setStringProperty("inMessageId", message.getJMSMessageID());
        messageToOutServer.setStringProperty("_HQ_DUPL_ID", message.getStringProperty("_HQ_DUPL_ID"));
        producerToOutServer.send(messageToOutServer);
        log.debug("Sending new message to OutServer with inMessageId: " + messageToOutServer.getStringProperty("inMessageId")
                + " and messageId: " + messageToOutServer.getJMSMessageID());

    }

    // This method would use JPA in the real world to persist the data
    private void insertMessageToDatabase(Message message, int count) throws SQLException, JMSException {

        MessageInfo messageInfoForDB = (MessageInfo) ((ObjectMessage) message).getObject();

        sqlConnection = dataSource.getConnection();
        PreparedStatement ps = sqlConnection.prepareStatement("INSERT INTO MESSAGE_INFO2"
                + "(MESSAGE_ID, MESSAGE_NAME, MESSAGE_ADDRESS) VALUES  (?, ?, ?)");
        ps.setString(1, message.getJMSMessageID());
        ps.setString(2, messageInfoForDB.getName() + count);
        ps.setString(3, messageInfoForDB.getAddress() + count);
        ps.executeUpdate();
        ps.close();

    }
}
