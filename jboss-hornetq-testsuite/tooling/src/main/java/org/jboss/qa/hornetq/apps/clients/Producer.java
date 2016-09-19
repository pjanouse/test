package org.jboss.qa.hornetq.apps.clients;

import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.impl.TextMessageBuilder;

import javax.jms.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Producer extends Client {

    protected volatile AtomicBoolean stopSending = new AtomicBoolean(false);
    protected int messages = 1000;
    protected MessageBuilder messageBuilder = new TextMessageBuilder(1000);
    protected List<Map<String,String>> listOfSentMessages = new ArrayList<Map<String,String>>();
    protected List<Message> listOfMessagesToBeCommited = new ArrayList<Message>();

    public Producer(Container container, String destinationNameJndi, int messages, int maxRetries) {
        super(container, destinationNameJndi, maxRetries);
        this.messages = messages;
    }

    @Deprecated
    public Producer(String container, String hostname, int jndiPort, String destinationNameJndi, int messages) {
        super(container, hostname, jndiPort, destinationNameJndi, 1000);
        this.messages = messages;
    }

    public void stopSending() {
        stopSending.set(true);
    }

    @Override
    public void forcedStop() {
        stopSending();
        super.forcedStop();
    }

    public int getMessages() {
        return messages;
    }

    public void setMessages(int messages) {
        this.messages = messages;
    }

    public List<Map<String, String>> getListOfSentMessages() {
        return listOfSentMessages;
    }

    public void setListOfSentMessages(List<Map<String, String>> listOfSentMessages) {
        this.listOfSentMessages = listOfSentMessages;
    }

    public MessageBuilder getMessageBuilder() {
        return messageBuilder;
    }

    public void setMessageBuilder(MessageBuilder messageBuilder) {
        this.messageBuilder = messageBuilder;
    }


}
