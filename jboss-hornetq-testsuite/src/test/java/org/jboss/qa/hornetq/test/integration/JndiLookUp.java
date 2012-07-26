package org.jboss.qa.hornetq.test.integration;

import javax.jms.ConnectionFactory;

/**
 * @author mnovak@redhat.com
 */
public interface JndiLookUp {

    public ConnectionFactory lookUpConnectionFactory(String jndiName);

}
