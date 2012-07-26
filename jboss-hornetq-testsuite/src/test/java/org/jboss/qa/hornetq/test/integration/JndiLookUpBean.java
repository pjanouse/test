package org.jboss.qa.hornetq.test.integration;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * @author mnovak@redat.com
 */

@Stateless
@Local(JndiLookUp.class)
public class JndiLookUpBean {

    public ConnectionFactory lookUpConnectionFactory(String jndiName) throws NamingException {

        Context ctx = new InitialContext();

        return (ConnectionFactory) ctx.lookup(jndiName);
    }
}
