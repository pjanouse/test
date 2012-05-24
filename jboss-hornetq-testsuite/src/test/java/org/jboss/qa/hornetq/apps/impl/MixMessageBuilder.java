package org.jboss.qa.hornetq.apps.impl;

import javax.jms.*;
import org.jboss.qa.hornetq.apps.MessageBuilder;


/**
 * Creates new byte JMS messages with mixed size.
 * 
 *
 * @author mnovak@redhat.com
 */
public class MixMessageBuilder implements MessageBuilder {

    // Counter for messages
    private int counter = 0;
    
    private int modulo = 10;
    
    String content = null;

    private byte[] data = null;
    
    // Required size
    private int size;

    public MixMessageBuilder() {
        this.size = 0;
    }

    public MixMessageBuilder(int size) {
        this.size = size;
        content = new String(new char[size]);
        data = new byte[this.size];
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    /**
     * @see {@link MessageBuilder#createMessage(javax.jms.Session)}
     */
    @Override
    public Message createMessage(Session session) throws Exception {
        
        Message message = null;
        
        if (counter % modulo == 0)    { //send large byte messge
            message = session.createBytesMessage();
            message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
            if (this.size > 0) {
                ((BytesMessage) message).writeBytes(data);
            }
            
        } else if (counter % modulo == 1)    { // send lage text message
            message = session.createTextMessage();
            message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
            if (this.size > 0) {
                ((TextMessage) message).setText(content);
            }
            
        } else if (counter % modulo == 2)    { // send lage object message
             message = session.createObjectMessage();
            message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
            if (this.size > 0) {
                ((ObjectMessage)message).setObject(content);
            }
            
        } else { // send normal message
             message = session.createTextMessage();
            message.setIntProperty(MESSAGE_COUNTER_PROPERTY, this.counter++);
            ((TextMessage) message).setText("normal message:" + message.getJMSMessageID());
            
        }
        return message;
    }
}
