package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

@MessageDriven(name = "SimpleMdbToDbAndRemoteInQueue",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class SimpleMdbToDbAndRemoteInQueue implements MessageListener {

    private static final Logger log = Logger.getLogger(SimpleMdbToDbAndRemoteInQueue.class.getName());
    private Connection connection;
    private DataSource dataSource;
    public static AtomicInteger counter = new AtomicInteger();
    private static final JMSImplementation jmsImplementation = ServiceLoader.load(JMSImplementation.class).iterator().next();

    private Queue queue = null;

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    // used for the transaction rollback
    @Resource
    private MessageDrivenContext context;

    @Resource(name = "lodhDb", mappedName = "java:/jdbc/lodhDS")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onMessage(Message message) {

        javax.jms.Connection con = null;
        Session session = null;

        try {

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            if (queue == null) {
                queue = session.createQueue("InQueue");
            }

            con.start();

            MessageInfo messageInfo = (MessageInfo) ((ObjectMessage) message).getObject();

            MessageProducer sender = session.createProducer(queue);
            Message newMessage = session.createObjectMessage(messageInfo);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            newMessage.setStringProperty(jmsImplementation.getDuplicatedHeader(), message.getStringProperty(jmsImplementation.getDuplicatedHeader()));
            sender.send(newMessage);

            connection = dataSource.getConnection();

            String hqInternalMessageCounter = null;
            try {
                hqInternalMessageCounter = message.getStringProperty("count");
            } catch (Exception e) {
                log.warn("No hqInternalMessageCounter \"count\" property is defined in message");
            }
            int count = counter.incrementAndGet();
            processMessageInfo(message, messageInfo, count);
            log.info("MDB is processing message: " + messageInfo.getName() + ", counter: " + count + ", messageId: " + message.getJMSMessageID()
                    + ", hqInternalMessageCounter: " + hqInternalMessageCounter);

        } catch (JMSException jmse) {
            context.setRollbackOnly();
            try {
                log.error("JMSException thrown during processing of message: " + message.getJMSMessageID(), jmse);
            } catch (JMSException ignore) {
            }
        } catch (SQLException sqle) {
            context.setRollbackOnly();
            try {
                log.error("SQLException thrown during processing of message: " + message.getJMSMessageID(), sqle);
            } catch (JMSException ignore) {
            }
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                    connection = null;
                } catch (SQLException ex) {
                }
            }
            if (con != null) {
                try {
                    con.close();
                    con = null;
                } catch (Exception ex) {
                }
            }
        }
    }

    // This method would use JPA in the real world to persist the data
    private void processMessageInfo(Message message, MessageInfo messageInfo, int count) throws SQLException, JMSException {
        PreparedStatement ps = connection.prepareStatement("INSERT INTO MESSAGE_INFO1"
                + "(MESSAGE_ID, MESSAGE_NAME, MESSAGE_ADDRESS) VALUES  (?, ?, ?)");
        ps.setString(1, message.getJMSMessageID());
        ps.setString(2, messageInfo.getName() + count);
        ps.setString(3, messageInfo.getAddress() + count);
        ps.executeUpdate();
        ps.close();

    }
}