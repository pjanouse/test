/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.impl;

import java.util.Random;
import javax.jms.Message;
import javax.jms.Session;
import org.jboss.qa.hornetq.apps.MessageBuilder;

/**
 *
 * @author mnovak
 */
public class InfoMessageBuilder implements MessageBuilder {
    
    
    private Random r = new Random();

    @Override
    public Message createMessage(Session session) throws Exception {
        long randomLong = r.nextLong();
        return session.createObjectMessage(new MessageInfo("name"+randomLong,
                "cool-address"+randomLong));
    }
    
}
