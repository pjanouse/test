package org.jboss.qa.hornetq.apps;

import java.util.List;
import javax.jms.Message;

/**
 * Checks if given messages met required parameters
 *
 * @author pslavice@redhat.com
 */
public interface MessageVerifier {

    void verifyMessage(Message message) throws Exception;
    
    /**
     * 
     * @return true if verification is ok
     * @throws Exception 
     */
    boolean verifyMessages() throws Exception;
    
    public void addReceivedMessages(List<Message> list);
    
    public void addSendMessages(List<Message> list);
    
    public List<Message> getReceivedMessages();
    
    public List<Message> getSentMessages();

}
