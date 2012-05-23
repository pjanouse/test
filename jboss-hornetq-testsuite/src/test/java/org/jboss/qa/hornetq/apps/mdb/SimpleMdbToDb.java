package org.jboss.qa.hornetq.apps.mdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.sql.DataSource;
import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.apps.impl.MessageInfo;

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
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        try {
            connection = dataSource.getConnection();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }
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

    public void onMessage(Message message) {
        try {

            MessageInfo messageInfo = (MessageInfo) ((ObjectMessage) message).getObject();
            log.info("messageInfo " + messageInfo.getName() + ", counter: " + counter.incrementAndGet());
            processMessageInfo(messageInfo);
            countAll();

        } catch (JMSException jmse) {
            jmse.printStackTrace();
            context.setRollbackOnly();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
            context.setRollbackOnly();
        }
    }

    // This method would use JPA in the real world to persist the data
    private void processMessageInfo(MessageInfo messageInfo) throws SQLException {
        String sql = "INSERT INTO MessageInfo VALUES (" + counter + ", " + messageInfo.getName() + ", " + messageInfo.getAddress() + ");";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);

        log.info(sql);
        preparedStatement.executeUpdate();
        preparedStatement.close();
    }

    public long countAll() {
        long result = 0;
        try {
            log.info("Count all record in database:" + result);
            PreparedStatement ps = (PreparedStatement) connection.prepareStatement("SELECT COUNT(*) FROM MessageInfo");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = rs.getLong(1);
            }
            rs.close();
            log.info("Records in DB :" + result);
            ps.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

}