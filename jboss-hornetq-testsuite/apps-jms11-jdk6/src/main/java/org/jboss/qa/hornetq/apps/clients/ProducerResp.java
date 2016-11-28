package org.jboss.qa.hornetq.apps.clients;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.impl.MessageCreator10;

import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.TemporaryQueue;
import javax.naming.Context;

/**
 * Simple sender with auto acknowledge session, receiving responses to temporary queue
 * <p/>
 * This class extends Thread class and should be started as a thread using
 * start().
 * <p/>
 * /**
 * Created by okalman on 8/13/14.
 */
public class ProducerResp extends Producer11 {
    private static final Logger logger = Logger.getLogger(ProducerAutoAck.class);

    private volatile TemporaryQueue tempQueue = null;

    private int waitBeforeReceive = 0;
    private boolean largeMessage = false;
    private boolean skipReceive = false;
    private String largeString="";
     private int receiveCount=0;

    /**
     * @param container     EAP container
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     */
    public ProducerResp(Container container, String queueNameJndi, int messages){
        this(container,queueNameJndi,messages, 0);
    }


    /**
     * @param container     EAP container
     * @param messages      number of messages to send
     * @param queueNameJndi set jndi name of the queue to send messages
     * @param waitBeforeReceive sets delay before receiving replies with may cause expiration of replies with some mdbs (LocalMdbFromQueueToTempQueue)
     */
    public ProducerResp(Container container,String queueNameJndi, int messages, int waitBeforeReceive){
        super(container, queueNameJndi, messages);
        this.waitBeforeReceive = waitBeforeReceive;
    }

    public void run()  {
        Context context = null;

        QueueConnection queueConnection = null;

        QueueSession session = null;

        String sMessage = new String();

        try {
            context = getContext(hostname, port);


            Queue queue = (Queue) context.lookup(destinationNameJndi);

            QueueConnectionFactory cf = (QueueConnectionFactory) context.lookup(getConnectionFactoryJndiName());

            queueConnection = cf.createQueueConnection();

            session = queueConnection.createQueueSession(false, QueueSession.AUTO_ACKNOWLEDGE);

            tempQueue = session.createTemporaryQueue();


            MessageProducer producer = session.createProducer(queue);

            Message msg;
            while (!stopSending.get() && counter < messages) {

                msg = messageBuilder.createMessage(new MessageCreator10(session), jmsImplementation);
                // send message in while cycle
                if(largeMessage) {
                    msg.setStringProperty("largeContent", largeString);

                }
                msg.setJMSReplyTo(tempQueue);
                if (waitBeforeReceive > 0) {
                    //ttl of replies will be same as thread sleep before receiving starts, message may expire
                    msg.setIntProperty("ttl", waitBeforeReceive);
                }
                sendMessage(producer, msg);
                msg = cleanMessage(msg);
                addMessage(listOfSentMessages, msg);

                Thread.sleep(getTimeout());

                logger.debug("Producer for node: " + hostname + ". Sent message with property count: " + counter + ", messageId:" + msg.getJMSMessageID());

            }

            QueueReceiver receiver = session.createReceiver(tempQueue);
            queueConnection.start();
            Message response;
            if(waitBeforeReceive!=0){
                Thread.sleep(waitBeforeReceive+60000);
            }

            if(skipReceive==false) {
                while ((response = receiver.receive(10000)) != null) {
                   countRecievedIncrement();
                    Thread.sleep(getTimeout() + 200);
                }
            }

        } catch (Exception e) {
           setException(e);
        } finally {
            try {
                queueConnection.close();
            } catch (Exception e) {
                logger.error(e);
            }
        }


    }


    /**
     * @return tempQueue ist initialized after client starts
     */
    public TemporaryQueue getTempQueue() {
        return tempQueue;
    }

    public synchronized void countRecievedIncrement() {
        receiveCount++;
    }

    public synchronized int getRecievedCount() {
        return receiveCount;
    }

    /**
     * If not set, default value is false
     * @param b
     */
    public void setUseLargeMessage(boolean b){
        largeMessage=b;
        StringBuilder sb = new StringBuilder("");
        if(b=true && largeString.length()<1) {
            while (sb.length() < 200000) {
                sb.append("some really interesting text ");
            }
            largeString=sb.toString();
        }

    }

    /**
     * If not set default value is false
     * @param b if true receiver will not try to get responses from temporary queue
     */
    public void setSkipReceive(boolean b){
        skipReceive=b;
    }


}
