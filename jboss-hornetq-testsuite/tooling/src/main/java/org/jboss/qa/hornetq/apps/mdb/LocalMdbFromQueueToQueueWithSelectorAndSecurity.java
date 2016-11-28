package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;

/**
 * Created by okalman on 9/2/14.
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
                @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "color = 'RED'" ),
                @ActivationConfigProperty(propertyName = "username", propertyValue = "user"),
                @ActivationConfigProperty(propertyName = "password", propertyValue = "user.1234")
        })
public class LocalMdbFromQueueToQueueWithSelectorAndSecurity implements MessageDrivenBean, MessageListener{
    private static final Logger log = Logger.getLogger(LocalMdbFromQueueToQueueWithSelectorAndSecurity.class.getName());
    private String username="user";
    private String password="user.1234";
    @Resource(mappedName = "java:/JmsXA")
    private QueueConnectionFactory connectionFactory;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private Queue queue;

    public LocalMdbFromQueueToQueueWithSelectorAndSecurity(){};
    public LocalMdbFromQueueToQueueWithSelectorAndSecurity(String username, String password){
        this.username=username;
        this. password=password;
    }

    @Override
    public void onMessage(Message message) {
        QueueConnection queueConnection=null;
        try{
            log.trace("MDB received message "+ message.getStringProperty("_HQ_DUPL_ID"));
            queueConnection = connectionFactory.createQueueConnection(username,password);
            QueueSession session = queueConnection.createQueueSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            QueueSender queueSender = session.createSender(queue);
            Message response= message;
            queueSender.send(response);
            Thread.sleep(100);
        }catch(Exception e){
            log.error(e);
        }finally{
            try {
                queueConnection.close();
            }catch(Exception e){
                log.fatal(e.getMessage(), e);
            }
        }

    }

    @Override
    public void setMessageDrivenContext(MessageDrivenContext messageDrivenContext) throws EJBException {

    }

    @Override
    public void ejbRemove() throws EJBException {

    }
}
