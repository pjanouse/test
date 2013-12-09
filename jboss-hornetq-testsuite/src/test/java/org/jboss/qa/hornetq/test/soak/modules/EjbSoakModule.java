package org.jboss.qa.hornetq.test.soak.modules;


import java.util.ArrayList;
import java.util.List;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.test.HornetQTestCase;
import org.jboss.qa.hornetq.test.soak.ClassDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.FileDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.SoakTestModule;
import org.jboss.qa.hornetq.test.soak.components.EjbCallingBean;
import org.jboss.qa.hornetq.test.soak.components.MessagesToTopicBean;
import org.jboss.qa.hornetq.tools.ContainerInfo;
import org.jboss.qa.hornetq.tools.JMSOperations;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class EjbSoakModule extends HornetQTestCase implements SoakTestModule {

    public static final String EJB_IN_QUEUE = "soak.ejb.InQueue";

    public static final String EJB_IN_QUEUE_JNDI = "jms/queue/soak/ejb/InQueue";

    public static final String EJB_OUT_QUEUE = "soak.ejb.OutQueue";

    public static final String EJB_OUT_QUEUE_JNDI = "jms/queue/soak/ejb/OutQueue";

    public static final String EJB_OUT_TOPIC = "soak.ejb.OutTopic";

    public static final String EJB_OUT_TOPIC_JNDI = "jms/topic/soak/ejb/OutTopic";

    private final ContainerInfo container;


    public EjbSoakModule() {
        this(CONTAINER1_INFO);
    }


    public EjbSoakModule(final ContainerInfo container) {
        this.container = container;
    }


    @Override
    public void setUpServers(ContainerController controller) {
        JMSOperations ops = this.getJMSOperations(this.container.getName());
        ops.createQueue(EJB_IN_QUEUE, EJB_IN_QUEUE_JNDI);
        ops.createQueue(EJB_OUT_QUEUE, EJB_OUT_QUEUE_JNDI);
        ops.createTopic(EJB_OUT_TOPIC, EJB_OUT_TOPIC_JNDI);
        ops.close();
    }


    @Override
    public List<ClassDeploymentDefinition> getRequiredClasses() {
        List<ClassDeploymentDefinition> deployment = new ArrayList<ClassDeploymentDefinition>(2);
        deployment.add(new ClassDeploymentDefinition(EjbCallingBean.class, this.container.getName()));
        deployment.add(new ClassDeploymentDefinition(MessagesToTopicBean.class, this.container.getName()));
        return deployment;
    }


    @Override
    public List<FileDeploymentDefinition> getRequiredAssets() {
        /*List<FileDeploymentDefinition> deployment = new ArrayList<FileDeploymentDefinition>(1);
         Asset contents = EmptyAsset.INSTANCE;
         deployment.add(new FileDeploymentDefinition(contents, "beans.xml", this.container.getName()));
         return deployment;*/
        return new ArrayList<FileDeploymentDefinition>();
    }

}
