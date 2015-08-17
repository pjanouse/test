package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;

/**
 * Created by eduda on 3.8.2015.
 */
public class ArtemisJMSImplementation implements JMSImplementation {

    @Override
    public String getDuplicatedHeader() {
        return "_AMQ_DUPL_ID";
    }

    @Override
    public String getScheduledDeliveryTimeHeader() {
        return "_AMQ_SCHED_DELIVERY";
    }
}
