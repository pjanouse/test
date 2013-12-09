package org.jboss.qa.hornetq.apps;

import javax.jms.JMSException;
import java.util.List;
import java.util.Map;

/**
 * Verifier used in the end of the test.
 *
 * @author mnovak@redhat.com
 */
public interface FinalTestMessageVerifier {

    public void addReceivedMessages(List<Map<String,String>> list);

    public void addSendMessages(List<Map<String,String>> list);

    public List<Map<String,String>> getReceivedMessages();

    public List<Map<String,String>> getSentMessages();

    public boolean verifyMessages() throws JMSException;

}
