package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.ejb.*;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * A MdbWithRemoteOutQueueToContaniner1 used for lodh tests. Used in RemoteJcaTestCase.
 * <p/>
 * This mdb reads messages from queue "InQueue" and sends to queue "OutQueue".
 *
 * @author <a href="pslavice@jboss.com">Pavel Slavicek</a>
 * @author <a href="mnovak@redhat.com">Miroslav Novak</a>
 * @version $Revision: 1.1 $
 */
@MessageDriven(name = "mdb1",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue")})
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)
public class MdbWithRemoteOutQueueToContaniner1 implements MessageDrivenBean, MessageListener {

    private static final long serialVersionUID = 2770941392406343837L;
    private static final Logger log = Logger.getLogger(MdbWithRemoteOutQueueToContaniner1.class.getName());
    private MessageDrivenContext context = null;
    private static String hostname;
    private static InitialContext ctx = null;
    private static Queue queue = null;
    private static ConnectionFactory cf = null;

    static {
        try {
            Properties prop = new Properties();
            String jbossHome = System.getProperty("jboss.home.dir") != null ? System.getProperty("jboss.home.dir") : "";
            File propFile = new File(jbossHome + File.separator + "mdb1.properties");
            log.info("Location of property file (mdb1) is: " + propFile.getAbsolutePath());
            prop.load(new FileInputStream(propFile));
            hostname = prop.getProperty("remote-jms-server");
            log.info("Hostname of remote jms server is: " + hostname);

            ctx = new InitialContext();
            // i want connection factory configured here
            cf = (ConnectionFactory) ctx.lookup("java:/JmsXA");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public MdbWithRemoteOutQueueToContaniner1() {
        super();
    }

    @Override
    public void setMessageDrivenContext(MessageDrivenContext ctx) {
        this.context = ctx;
    }

    public void ejbCreate() {
    }

    @Override
    public void ejbRemove() {

        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException e) {
                log.log(Level.FATAL, e.getMessage(), e);
            }
        }
    }

    @Override
    public void onMessage(Message message) {

        Connection con = null;
        Session session = null;

        try {

            long time = System.currentTimeMillis();
            int counter = 0;
            try {
                counter = message.getIntProperty("count");
            } catch (Exception e) {
                log.log(Level.ERROR, e.getMessage(), e);
            }
            String messageInfo = message.getJMSMessageID() + ", count:" + counter;
            log.log(Level.INFO, " Start of message:" + messageInfo);

            for (int i = 0; i < (5 + 5 * Math.random()); i++) {
                try {
                    Thread.sleep((int) (10 + 10 * Math.random()));
                } catch (InterruptedException ex) {
                }
            }

            if (cf == null) {
                ctx = new InitialContext();
                cf = (ConnectionFactory) ctx.lookup("java:/JmsXA");
            }

            con = cf.createConnection();

            session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

            queue = session.createQueue("OutQueue");

            con.start();

            String text = message.getJMSMessageID() + " processed by: " + hashCode();
            MessageProducer sender = session.createProducer(queue);
            TextMessage newMessage = session.createTextMessage(text);
            newMessage.setStringProperty("inMessageId", message.getJMSMessageID());
            sender.send(newMessage);

            log.log(Level.INFO, " End of " + messageInfo + " in " + (System.currentTimeMillis() - time) + " ms");

        } catch (Exception t) {

            t.printStackTrace();
            log.log(Level.FATAL, t.getMessage(), t);

            this.context.setRollbackOnly();
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (JMSException e) {
                    log.log(Level.FATAL, e.getMessage(), e);
                }
            }


        }
    }
}