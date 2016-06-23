package org.jboss.qa.hornetq.apps.ejb;

import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.constants.Constants;
import org.jboss.shrinkwrap.api.Archive;

import javax.naming.Context;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by mstyk on 6/22/16.
 */
public class EjbProducer extends Thread {

    private SimpleSendEJB bean;
    private volatile boolean sendingActive;
    private Container container;

    private int maxNumberOfMessages;
    private int timeout = 100;


    /**
     * EJB needs to be deployed on server before this producer is created
     *
     * @param container with deployed EJB
     * @param lookupString expression used to lookup EJB
     * @param maxNumberOfMessages maximum number of msgs this producer sends. It can be interrupted by calling stopSendind() method
     */
    public EjbProducer(Container container, String lookupString, int maxNumberOfMessages) {
        this.container = container;
        this.maxNumberOfMessages = maxNumberOfMessages;

        try {
            Context ctx = container.getContext(Constants.JNDI_CONTEXT_TYPE.EJB_CONTEXT);
            this.bean = (SimpleSendEJB) ctx.lookup(lookupString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot initialize ejb on container " + container + ". Lookup expression " + lookupString, e);
        }
    }

    /**
     * EJB needs to be deployed on server before this producer is created
     *
     * @param simpleSendEJB SimpleSendEJB instance
     * @param maxNumberOfMessages maximum number of msgs this producer sends. It can be interrupted by calling stopSendind() method
     */
    public EjbProducer(SimpleSendEJB simpleSendEJB, int maxNumberOfMessages) {
        if (simpleSendEJB == null) {
            throw new IllegalArgumentException("simpleSendEJB is not initialized");
        }
        this.bean = simpleSendEJB;
        this.maxNumberOfMessages = maxNumberOfMessages;
    }

    public void stopSending() {
        sendingActive = false;
    }

    public int getSendMessagesCount() {
        return bean.sendCount();
    }

    @Override
    public void run() {
        //sending
        sendingActive = true;
        bean.createConnection();
        while (sendingActive) {
            bean.sendMessage();

            try {
                Thread.sleep(timeout);
            } catch (Exception e) {
            }
            if (getSendMessagesCount() > maxNumberOfMessages) break;
        }
        bean.closeConnection();
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
