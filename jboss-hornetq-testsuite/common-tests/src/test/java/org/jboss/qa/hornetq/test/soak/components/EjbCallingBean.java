package org.jboss.qa.hornetq.test.soak.components;


import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.jboss.logging.Logger;
import org.jboss.qa.hornetq.test.soak.modules.EjbSoakModule;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
@MessageDriven(name = "ejb-calling-bean", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination",
                              propertyValue = "java:/" + EjbSoakModule.EJB_IN_QUEUE_JNDI)
})
@TransactionManagement(TransactionManagementType.CONTAINER)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class EjbCallingBean implements MessageListener {

    private static final Logger LOG = Logger.getLogger(EjbCallingBean.class);

    @EJB
    private MessagesToTopicBean ejb;


    @Override
    public void onMessage(Message msg) {
        this.ejb.resendMessage(msg);
    }

}
