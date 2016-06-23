package org.jboss.qa.hornetq.apps.ejb;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.constants.Constants;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.jms.ConnectionFactory;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;

/**
 * EJB sending messages to InQueue, and performing access to DB in UserTransaction
 * Uses managed executor service
 * @see SendAndDbInteractTask
 **/
@Stateless
@Remote(SimpleSendEJB.class)
@TransactionAttribute(TransactionAttributeType.REQUIRED)
@TransactionManagement(TransactionManagementType.BEAN)
public class SenderEJBWithDatabaseAccessUsingExecutor implements SimpleSendEJB {

    private static final Logger log = Logger.getLogger(SenderEJBWithDatabaseAccessUsingExecutor.class.getName());


    @Resource(mappedName = Constants.POOLED_CONNECTION_FACTORY_JNDI_EAP7)
    private ConnectionFactory cf;

    @Resource
    private ManagedExecutorService executorService;

    @Resource(name = "lodhDb", mappedName = "java:/jdbc/lodhDS")
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Resource
    SessionContext sessionContext;

    @Resource
    UserTransaction userTransaction;

    private DataSource dataSource;


    @Override
    public void createConnection() {
    }

    @Override
    public void closeConnection() {
    }

    @Override
    public void sendMessage() {
        executorService.submit(new SendAndDbInteractTask(sessionContext, dataSource, cf, userTransaction));
    }

    @Override
    public int sendCount() {
        return SendAndDbInteractTask.getCounter();
    }

}



