/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;
import org.jboss.qa.hornetq.apps.MessageBuilder;
import org.jboss.qa.hornetq.apps.MessageCreator;

import javax.jms.Message;
import java.util.Random;
import java.util.UUID;

/**
 * @author mnovak
 */
public class InfoMessageBuilder implements MessageBuilder {


    private Random r = new Random();

    private boolean addDuplicatedHeader = true;

    private int sizeInBytes = 0;

    public InfoMessageBuilder() {

    }

    public InfoMessageBuilder(int sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    @Override
    public synchronized Message createMessage(MessageCreator messageCreator, JMSImplementation jmsImplementation) throws Exception {
        long randomLong = r.nextLong();
        return messageCreator.createObjectMessage(new MessageInfo("name" + randomLong,
                "cool-address" + randomLong));
    }

    /**
     *
     * @return if header for message duplication will be added
     */
    @Override
    public boolean isAddDuplicatedHeader() {
        return addDuplicatedHeader;
    }

    /**
     *
     * if header for message duplication will be added
     *
     * @param addDuplicatedHeader
     */
    @Override
    public void setAddDuplicatedHeader(boolean addDuplicatedHeader) {
        this.addDuplicatedHeader = addDuplicatedHeader;
    }

}
