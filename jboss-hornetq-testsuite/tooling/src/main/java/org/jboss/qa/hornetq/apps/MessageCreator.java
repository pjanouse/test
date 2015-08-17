package org.jboss.qa.hornetq.apps;

import javax.jms.*;
import java.io.Serializable;

/**
 * Created by eduda on 3.8.2015.
 */
public interface MessageCreator {

    /**
     * Creates a ByteMessage object
     * @return Message object
     */
    BytesMessage createBytesMessage();

    /**
     * Creates a MapMessage object
     * @return Message object
     */
    MapMessage createMapMessage();

    /**
     * Creates a ObjectMessage object
     * @return Message object
     */
    ObjectMessage createObjectMessage();

    /**
     * Creates a ObjectMessage object
     * @return Message object
     */
    ObjectMessage createObjectMessage(Serializable object);

    /**
     * Creates a StreamMessage object
     * @return Message object
     */
    StreamMessage createStreamMessage();

    /**
     * Creates a TextMessage object
     * @return Message object
     */
    TextMessage createTextMessage();

    /**
     * Creates a TextMessage object
     * @return Message object
     */
    TextMessage createTextMessage(String text);

}
