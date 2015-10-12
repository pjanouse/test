package org.jboss.qa.hornetq.apps;

/**
 * Created by eduda on 3.8.2015.
 */
public interface JMSImplementation {

    /**
     * Returns message header id for duplicate detection
     * @return
     */
    String getDuplicatedHeader();

    /**
     * Returns message header id for scheduled delivery
     * @return
     */
    String getScheduledDeliveryTimeHeader();

}
