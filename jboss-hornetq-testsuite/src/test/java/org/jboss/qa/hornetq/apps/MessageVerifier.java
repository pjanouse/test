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
    
}
