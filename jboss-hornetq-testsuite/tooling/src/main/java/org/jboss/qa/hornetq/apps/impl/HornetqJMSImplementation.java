package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;

import java.util.logging.Logger;

/**
 * Created by eduda on 3.8.2015.
 */
public class HornetqJMSImplementation implements JMSImplementation {

    private static HornetqJMSImplementation instance;

    public static HornetqJMSImplementation getInstance() {
        if (instance == null) {
            instance = new HornetqJMSImplementation();
        }
        return instance;
    }

    private HornetqJMSImplementation() {

    }

    @Override
    public String getDuplicatedHeader() {
        return "_HQ_DUPL_ID";
    }

    @Override
    public String getScheduledDeliveryTimeHeader() {
        return "_HQ_SCHED_DELIVERY";
    }

}
