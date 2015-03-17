package org.jboss.qa.hornetq;

import javax.naming.Context;
import javax.naming.NamingException;

/**
 * Provides instance of the context for server
 */
public interface ContextProvider {

    /**
     * Returns context for server 1
     *
     * @return instance of {@link Context}
     * @throws NamingException if something goes wrong
     */
    Context getContext() throws NamingException;

    /**
     * Returns context for server 1
     *
     * @return instance of {@link Context}
     * @throws NamingException if something goes wrong
     */
    Context getContextContainer1() throws NamingException;

    /**
     * Returns context for server 2
     *
     * @return instance of {@link Context}
     * @throws NamingException if something goes wrong
     */
    Context getContextContainer2() throws NamingException;
}
