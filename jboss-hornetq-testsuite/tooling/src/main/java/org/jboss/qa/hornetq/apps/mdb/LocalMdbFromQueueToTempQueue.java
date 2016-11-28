package org.jboss.qa.hornetq.apps.mdb;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.*;

/**
 * Created by okalman on 8/13/14.
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "jms/queue/InQueue"),
        })
@TransactionManagement(TransactionManagementType.BEAN)
public class LocalMdbFromQueueToTempQueue implements MessageDrivenBean, MessageListener {

    private static final Logger log = Logger.getLogger(LocalMdbFromQueueToTempQueue.class.getName());

    @Resource(mappedName = "java:/JmsXA")
    private QueueConnectionFactory connectionFactory;

    @Resource(mappedName = "java:/jms/queue/OutQueue")
    private Queue queue;

    @Override
    public void onMessage(Message message) {
        QueueConnection queueConnection=null;
        try{
            queueConnection = connectionFactory.createQueueConnection();
            QueueSession session = queueConnection.createQueueSession(false, QueueSession.AUTO_ACKNOWLEDGE);
            QueueSender queueSender = session.createSender(queue);
            queueSender.send(message);
            System.out.println(((TextMessage) message).getText());
            TemporaryQueue tmpQueue= (TemporaryQueue)message.getJMSReplyTo();
            queueSender = session.createSender(tmpQueue);
            try {
                if (message.propertyExists("ttl")) {
                    queueSender.setTimeToLive(message.getIntProperty("ttl"));
                }
            }catch(Exception e){
                //never mind... messages will not expire
            }

            Message response= session.createTextMessage("Response to: "+((TextMessage) message).getText());
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
