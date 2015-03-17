/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.MessageBuilder;

import javax.jms.Message;
import javax.jms.Session;
import java.util.Random;

/**
 * @author mnovak
 */
public class InfoMessageBuilder implements MessageBuilder {


    private Random r = new Random();

    @Override
    public synchronized Message createMessage(Session session) throws Exception {
        long randomLong = r.nextLong();
        return session.createObjectMessage(new MessageInfo("name" + randomLong,
                "cool-address" + randomLong));
    }

    @Override
    public void setAddDuplicatedHeader(boolean duplHeader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAddDuplicatedHeader() {
        throw new UnsupportedOperationException();
    }

}
