package org.jboss.qa.hornetq.test.journalreplication.configuration;

import java.io.File;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.commons.io.FileUtils;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.apps.clients.ProducerTransAck;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.journalreplication.JournalReplicationAbstract;
import org.jboss.qa.hornetq.test.journalreplication.utils.FileUtil;
import org.jboss.qa.hornetq.tools.ControllableProxy;
import org.jboss.qa.hornetq.tools.JMSOperations;
import org.jboss.qa.hornetq.tools.SimpleProxyServer;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class JournalReplicationConfiguration
{

}