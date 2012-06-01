package org.jboss.qa.hornetq.apps.impl;

import javax.jms.*;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.MessageBuilder;


/**
 * Creates new JMS messages with mixed size and type.
 *
 * @author mnovak@redhat.com
 * @author ochaloup@redhat.com
 */
public class ClientMixMessageBuilder implements MessageBuilder {
	private static final Logger log = Logger.getLogger(ClientMixMessageBuilder.class);
	
    // Counter of messages
    private int counter = 0;
	
	private enum MessageType {
		BYTE, TEXT, OBJECT, MAP,
		LARGE_BYTE, LARGE_TEXT, LARGE_OBJECT, LARGE_MAP
	}
	
	// Content for Object and Text messages
    String content = null;
    String contentLarge = null;
    // Content for Byte messages
    private byte[] data = null;
    private byte[] dataLarge = null;
    // MapMessage conent
    private String mapMessageKey = null;
    // Sizes of messages for normal and large ones
    private int sizeNormalMsg;
    private int sizeLargeMsg;

    /**
     * It will set default sizes for normal and large messages.
     */
    public ClientMixMessageBuilder() {
        this(20, 120);
    }

    /**
     * Setting size of messages in KiB. For large messages there should be defined number greater than 100 (KiB). 
     * 
     * @param sizeNormal  size normal sized messages which will be used for sending
     * @param sizeLarge  size of large message which will be used for sending
     */
    public ClientMixMessageBuilder(int sizeNormal, int sizeLarge) {
        this.sizeNormalMsg = sizeNormal;
        this.sizeLargeMsg = sizeLarge;
        
        content = new String(new char[sizeNormalMsg * 1024]);
        contentLarge = new String(new char[sizeLargeMsg * 1024]);
        data = new byte[sizeNormalMsg * 1024];
        dataLarge = new byte[sizeLargeMsg * 1024];
    }
    
    /**
	 * Util method to fill map message passed in parameter with some content.
     */
    private void fillMapMessage(Message message, int size) {
    	if(!(message instanceof MapMessage)) {
    		log.error("Message " + message + " is not type of " + MapMessage.class.getName());
    		return;
    	}
    	
    	MapMessage mm = (MapMessage) message;
    	String stringContent = new String(new char[1024]); // size of one KB
    	mapMessageKey = "a"; // starting with key 'a' on mapped message
    	for(int i = 0; i< size; i++) {
    		String key = getNextMapKey();
    		try {
    			mm.setObject(key, stringContent);
    		} catch (JMSException jmse) {
    			log.error("Can't put key: " + key + " to MapMessage due to exception: ", jmse);
    		}
    	}
    }
    
    private String getNextMapKey() {
    	if(mapMessageKey == null) {
    		mapMessageKey = "a";
    	} else {
	    	int numericValueOfLastCharacter = mapMessageKey.codePointAt(mapMessageKey.length()-1);
	    	int numericValueOfZ = (int) 'z';
	    	
	    	if(numericValueOfLastCharacter >= numericValueOfZ) {
	    		mapMessageKey += "a";
	    	} else {
	    		String nextChar = String.valueOf((char) ++numericValueOfLastCharacter);
	    		mapMessageKey = mapMessageKey.replaceFirst("[a-z]$", nextChar);
	    	}
    	}
    	return mapMessageKey;
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
    	int modulo = MessageType.values().length;
    	MessageType whichProcess = MessageType.values()[counter % modulo];
    	
    	switch (whichProcess) {
    		case BYTE:
                message = session.createBytesMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                ((BytesMessage) message).writeBytes(data);
    			break;
    		case TEXT:
                message = session.createTextMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                ((TextMessage) message).setText(content);
    			break;
    		case OBJECT:
                message = session.createObjectMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                ((ObjectMessage)message).setObject(content);
    			break;
    		case MAP:
    			message = session.createMapMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                fillMapMessage(message, sizeNormalMsg);
    			break;
    		case LARGE_BYTE:
                message = session.createBytesMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                ((BytesMessage) message).writeBytes(dataLarge);
    			break;
    		case LARGE_TEXT:
                message = session.createTextMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                ((TextMessage) message).setText(contentLarge);    			
    			break;
    		case LARGE_OBJECT:
                message = session.createObjectMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                ((ObjectMessage)message).setObject(contentLarge);
    			break;
    		case LARGE_MAP:
    			message = session.createMapMessage();
                message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
                fillMapMessage(message, sizeLargeMsg);
    			break;	
    	}
        log.info("Sending message " + whichProcess.toString() + " with counter " + this.counter);
        return message;
    }
}
