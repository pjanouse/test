package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

@MessageDriven(name = "SimpleMdbToDb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class SimpleMdbToDb implements MessageListener {

    private static final Logger log = Logger.getLogger(SimpleMdbToDb.class.getName());
    private Connection connection;
    private DataSource dataSource;
    public static AtomicInteger counter = new AtomicInteger();
    // used for the transaction rollback
    @Resource
    private MessageDrivenContext context;

    @Resource(name = "lodhDb", mappedName = "java:/jdbc/lodhDS")
//    @Resource(name = "lodhDb", mappedName = "java:/jdbc/mysqlDS")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
//        try {
//            connection = dataSource.getConnection();
////            connection.setAutoCommit(false);
//        } catch (SQLException sqle) {
//            sqle.printStackTrace();
//        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            connection.close();
            connection = null;
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
    }

    @Override
    public void onMessage(Message message) {
        try {
            connection = dataSource.getConnection();
            MessageInfo messageInfo = (MessageInfo) ((ObjectMessage) message).getObject();
            int count = counter.incrementAndGet();
            log.info("messageInfo " + messageInfo.getName() + ", counter: " + count);
            processMessageInfo(message, messageInfo, count);

        } catch (JMSException jmse) {
            jmse.printStackTrace();
            context.setRollbackOnly();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
            context.setRollbackOnly();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                    connection = null;
                } catch (SQLException ex) {
                }
            }
        }
    }

    // This method would use JPA in the real world to persist the data
    private void processMessageInfo(Message message, MessageInfo messageInfo, int count) throws SQLException, JMSException {
        PreparedStatement ps = (PreparedStatement) connection.prepareStatement("INSERT INTO MESSAGE_INFO2"
                + "(MESSAGE_ID, MESSAGE_NAME, MESSAGE_ADDRESS) VALUES  (?, ?, ?)");
        ps.setString(1, message.getJMSMessageID());
        ps.setString(2, messageInfo.getName() + count);
        ps.setString(3, messageInfo.getAddress() + count);
        ps.executeUpdate();
        ps.close();

    }
}