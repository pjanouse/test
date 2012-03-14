package org.jboss.qa.hornetq.apps;

import java.util.List;
import javax.jms.JMSException;
import javax.jms.Message;

/**
 *
 * Verifier used in the end of the test. 
 * 
 * @author mnovak@redhat.com
 */
public interface FinalTestMessageVerifier {
    
    public void addReceivedMessages(List<Message> list);
    
    public void addSendMessages(List<Message> list);
    
    public List<Message> getReceivedMessages();
    
    public List<Message> getSentMessages();
    
    public boolean verifyMessages() throws JMSException;
    
}
