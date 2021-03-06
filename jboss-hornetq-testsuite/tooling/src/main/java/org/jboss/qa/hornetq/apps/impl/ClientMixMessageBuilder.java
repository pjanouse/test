package org.jboss.qa.hornetq.apps.impl;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.*;
import java.util.Map;
import java.util.Random;
import java.util.UUID;


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

    private boolean addDuplicatedHeader = true;

    private Random random = new Random();
    private boolean isAddPriorityToMessage = false;
    private Map<String,String> jndiProperties = null;


    /**
     *
     * @return if header for message duplication will be added
     */
    public boolean isAddDuplicatedHeader() {
        return addDuplicatedHeader;
    }

    /**
     *
     * if header for message duplication will be added
     *
     * @param addDuplicatedHeader
     */
    public void setAddDuplicatedHeader(boolean addDuplicatedHeader) {
        this.addDuplicatedHeader = addDuplicatedHeader;
    }

    public void setIsAddPriorityToMessage(boolean isAddPriorityToMessage) {
        this.isAddPriorityToMessage = isAddPriorityToMessage;
    }

    private enum MessageType {
        BYTE, TEXT, OBJECT, MAP, STREAM,
        LARGE_BYTE, LARGE_TEXT, LARGE_OBJECT, LARGE_MAP, STREAM_LARGE
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
     * @param sizeNormal size normal sized messages which will be used for sending (in Kb)
     * @param sizeLarge  size of large message which will be used for sending (in Kb)
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
        if (!(message instanceof MapMessage)) {
            log.error("Message " + message + " is not type of " + MapMessage.class.getName());
            return;
        }

        MapMessage mm = (MapMessage) message;
        String stringContent = new String(new char[1024]); // size of one KB
        mapMessageKey = "a"; // starting with key 'a' on mapped message
        for (int i = 0; i < size; i++) {
            String key = getNextMapKey();
            try {
                mm.setObject(key, stringContent);
            } catch (JMSException jmse) {
                log.error("Can't put key: " + key + " to MapMessage due to exception: ", jmse);
            }
        }
    }

    private String getNextMapKey() {
        if (mapMessageKey == null) {
            mapMessageKey = "a";
        } else {
            int numericValueOfLastCharacter = mapMessageKey.codePointAt(mapMessageKey.length() - 1);
            int numericValueOfZ = (int) 'z';

            if (numericValueOfLastCharacter >= numericValueOfZ) {
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
     * @see {@link MessageBuilder#createMessage(MessageCreator, JMSImplementation)}
     * @param messageCreator
     * @param jmsImplementation
     */
    @Override
    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {
        Message message = null;
        int modulo = MessageType.values().length;
        MessageType whichProcess = MessageType.values()[counter % modulo];

        switch (whichProcess) {
            case BYTE:
                message = messageCreator.createBytesMessage();
                ((BytesMessage) message).writeBytes(data);
                break;
            case TEXT:
                message = messageCreator.createTextMessage();
                ((TextMessage) message).setText(content);
                break;
            case OBJECT:
                message = messageCreator.createObjectMessage();
                ((ObjectMessage) message).setObject(content);
                break;
            case MAP:
                message = messageCreator.createMapMessage();
                fillMapMessage(message, sizeNormalMsg);
                break;
            case LARGE_BYTE:
                message = messageCreator.createBytesMessage();
                ((BytesMessage) message).writeBytes(dataLarge);
                break;
            case STREAM: /* self-defining stream of primitive values */
                message = messageCreator.createStreamMessage();
                ((StreamMessage) message).writeInt(42);
                ((StreamMessage) message).writeString(content);
                break;
            case STREAM_LARGE:
                message = messageCreator.createStreamMessage();
                ((StreamMessage) message).writeInt(42);
                ((StreamMessage) message).writeString(contentLarge);
                break;
            case LARGE_TEXT:
                message = messageCreator.createTextMessage();
                ((TextMessage) message).setText(contentLarge);
                break;
            case LARGE_OBJECT:
                message = messageCreator.createObjectMessage();
                ((ObjectMessage) message).setObject(contentLarge);
                break;
            case LARGE_MAP:
                message = messageCreator.createMapMessage();
                fillMapMessage(message, sizeLargeMsg);
                break;
        }

        message.setIntProperty(MESSAGE_COUNTER_PROPERTY, ++this.counter);
        if (counter % 2 == 0) {
            message.setStringProperty("color", "RED");
        } else {
            message.setStringProperty("color", "GREEN");
        }

        if (isAddDuplicatedHeader())    {
                message.setStringProperty(jmsImplementation.getDuplicatedHeader(), String.valueOf(UUID.randomUUID()) + System.currentTimeMillis());
        }

        if (isAddPriorityToMessage)  {
            int priority = generatePriority();
            message.setJMSPriority(priority);
        }
        if (jndiProperties != null && jndiProperties.size() > 0)    {
            message = MessageUtils.setPropertiesToMessage(jndiProperties, message);
        }

//        message.setStringProperty("_HQ_DUPL_ID", (UUID.randomUUID().toString() + System.currentTimeMillis() + counter));
        if (counter % 100 ==0)  {
            log.info("Sending message with counter: " + this.counter + ", type: " + whichProcess.toString() + ", messageId: " + message.getJMSMessageID() +
                    jmsImplementation.getDuplicatedHeader() + ": " + message.getStringProperty(jmsImplementation.getDuplicatedHeader()));
        } else {
            log.debug("Sending message with counter: " + this.counter + ", type: " + whichProcess.toString() + ", messageId: " + message.getJMSMessageID() +
                    jmsImplementation.getDuplicatedHeader() + ": " + message.getStringProperty(jmsImplementation.getDuplicatedHeader()));
        }
        return message;
    }

    private int generatePriority() {
        return random.nextInt(10);
    }

    public void setJndiProperties(Map<String, String> jndiProperties) {
        this.jndiProperties = jndiProperties;
    }

}
