package org.jboss.qa.hornetq.apps.impl;

import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.Session;

/**
 * Created by mnovak on 9/24/14.
 */
public class AllHeadersClientMixMessageBuilder extends ClientMixMessageBuilder {

    /*JMSDestination
        - JMSDeliveryMode
		- JMSMessageID
		- JMSTimestamp - system current time
		- JMSCorrelationID
		- JMSReplyTo - needs queue/topic instance
		- JMSRedelivered
		- JMSType
		- JMSExpiration
		- JMSPriority
		-

		JMSXUserID String Provider on The identity of the user sending the
        JMSXAppID String Provider on The identity of the application

        JMSXDeliveryCount int Provider on The number of message delivery
                              Receive attempts; the first is 1, the second 2,...
        JMSXGroupID String Client The identity of the message group
                                 this message is part of
        JMSXGroupSeq int Client The sequence number of this
                               message within the group; the first
                              message is 1, the second 2,...
        JMSXProducerTXID String Provider on The transaction identifier of the
                                Send transaction within which this
                                    message was produced
        JMSXConsumerTXID String Provider on The transaction identifier of the
                                Receive transaction within which this
                                       message was consumed
        JMSXRcvTimestamp long Provider on The time JMS delivered the message
                              Receive to the consumer
        JMSXState int Provider Assume there exists a message
                              warehouse that contains a separate
                             copy of each message sent to each
                            consumer and that these copies exist
                           from the time the original message
                          was sent.
                         Each copy’s state is one of:
                          1(waiting), 2(ready), 3(expired) or
                         4(retained).
                        Since state is of no interest to
                       producers and consumers, it is not
                      provided to either. It is only relevant
                     to messages looked up in a
                    warehouse, and JMS provides no API

		*/

    private int JMSDeliveryMode = DeliveryMode.PERSISTENT;
    private String JMSCorrelationID = "ID:JMSCorrelationID"; // can be anything
    private String JMSType = "JMSType"; // can be anything
    private long JMSExpiration = 120000000;
    private int JMSPriority = 6;    // 0..9

    private String JMSXUserID = "testUser";
    private String JMSXAppID = "testingApplication";
    private String JMSXGroupID = "testGroupId";

    public AllHeadersClientMixMessageBuilder(int sizeNormal, int sizeLarge) {
        super(sizeNormal, sizeLarge);
    }

    @Override
    public Message createMessage(Session session) throws Exception {
        Message msg = super.createMessage(session);

        msg.setJMSDeliveryMode(JMSDeliveryMode);
        msg.setJMSCorrelationID(JMSCorrelationID);
        msg.setJMSType(JMSType);
        msg.setJMSExpiration(JMSExpiration);
        msg.setJMSPriority(JMSPriority);

        msg.setStringProperty("JMSXUserID", JMSXUserID);
        msg.setStringProperty("JMSXAppID", JMSXAppID);
        msg.setStringProperty("JMSXGroupID", JMSXGroupID);

        return msg;
    }
}
