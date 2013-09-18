package org.jboss.qa.hornetq.test.soak.modules;


import java.util.ArrayList;
import java.util.List;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.soak.components.MessageSplliterBean;
import org.jboss.qa.hornetq.test.soak.ClassDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.FileDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.SoakTestModule;
import org.jboss.qa.tools.ContainerInfo;
import org.jboss.qa.tools.JMSOperations;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class DurableSubscriptionsSoakModule extends HornetQTestCase implements SoakTestModule {

    public static final String DURABLE_MESSAGES_TOPIC = "soak.durable.OutTopic";

    public static final String DURABLE_MESSAGES_TOPIC_JNDI = "jms/topic/soak/durable/OutTopic";

    public static final String DURABLE_MESSAGES_QUEUE = "soak.durable.OutQueue";

    public static final String DURABLE_MESSAGES_QUEUE_JNDI = "jms/queue/soak/durable/OutQueue";

    private final ContainerInfo container;


    public DurableSubscriptionsSoakModule() {
        this(CONTAINER1_INFO);
    }


    public DurableSubscriptionsSoakModule(final ContainerInfo container) {
        this.container = container;
    }


    @Override
    public void setUpServers(ContainerController controller) {
        this.prepareDestinations(this.container.getName());
    }


    @Override
    public List<ClassDeploymentDefinition> getRequiredClasses() {
        List<ClassDeploymentDefinition> deployments = new ArrayList<ClassDeploymentDefinition>(1);
        deployments.add(new ClassDeploymentDefinition(MessageSplliterBean.class, this.container.getName()));
        return deployments;
    }


    @Override
    public List<FileDeploymentDefinition> getRequiredAssets() {
        return new ArrayList<FileDeploymentDefinition>();
    }


    private void prepareDestinations(final String containerName) {
        JMSOperations ops = this.getJMSOperations(containerName);
        ops.createTopic(DURABLE_MESSAGES_TOPIC, DURABLE_MESSAGES_TOPIC_JNDI);
        ops.createQueue(DURABLE_MESSAGES_QUEUE, DURABLE_MESSAGES_QUEUE_JNDI);
        ops.close();
    }

}
