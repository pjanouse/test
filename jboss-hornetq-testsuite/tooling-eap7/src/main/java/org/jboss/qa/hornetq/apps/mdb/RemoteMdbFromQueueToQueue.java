package org.jboss.qa.hornetq.apps.mdb;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;

/**
 * Created by okalman on 8/24/15.
 */
@MessageDriven(name = "mdb",
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "destination", propertyValue = "queues/InQueue"),
                @ActivationConfigProperty(propertyName="userName", propertyValue="user"),
                @ActivationConfigProperty(propertyName="password", propertyValue="user")
        })
@TransactionManagement(value = TransactionManagementType.CONTAINER)
@TransactionAttribute(value = TransactionAttributeType.REQUIRED)

public class RemoteMdbFromQueueToQueue implements MessageListener{

    private static final Logger log = Logger.getLogger(RemoteMdbFromQueueToQueue.class.getName());


    @Resource(mappedName = "java:/jms/CF")
    private ConnectionFactory cf;

    @Resource(lookup = "java:global/remoteContext")
    private Context context1;


    @Override
    public void onMessage(Message message) {
        Connection connection =null;
        try{

            connection = cf.createConnection();
            Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            System.out.println("MESSAGE RECEIVED");
            Queue queue = (Queue) context1.lookup("queues/OutQueue");
            MessageProducer sender = session.createProducer(queue);
            sender.send(message);
        }catch(Exception e){
            log.log(Level.FATAL, e.getMessage(), e);
        }finally {
            try {
                if(connection!=null){
                    connection.close();
                }
            }catch (Exception e){
                log.log(Level.FATAL, e.getMessage(), e);
            }
        }

    }



}
