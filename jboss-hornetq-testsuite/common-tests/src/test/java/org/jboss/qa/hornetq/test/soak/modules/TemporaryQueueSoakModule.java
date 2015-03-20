package org.jboss.qa.hornetq.test.soak.modules;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.qa.hornetq.Container;
import org.jboss.qa.hornetq.HornetQTestCase;
import org.jboss.qa.hornetq.test.soak.components.TemporaryQueueBean;
import org.jboss.qa.hornetq.test.soak.ClassDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.FileDeploymentDefinition;
import org.jboss.qa.hornetq.test.soak.SoakTestModule;
import org.jboss.qa.hornetq.tools.JMSOperations;


/**
 * @author Martin Svehla &lt;msvehla@redhat.com&gt;
 */
public class TemporaryQueueSoakModule extends HornetQTestCase implements SoakTestModule {

    public static final String TEMP_IN_QUEUE = "soak.temporary.InQueue";

    public static final String TEMP_IN_QUEUE_JNDI = "jms/queue/soak/temporary/InQueue";

    public static final String TEMP_QUEUE_PREFIX = "jms/queue/soak/temporary/TempQueue";

    private Container container;


    @Override
    public void setUpServers() {
        this.container = container(1);
        JMSOperations ops = container.getJmsOperations();
        ops.createQueue(TEMP_IN_QUEUE, TEMP_IN_QUEUE_JNDI);
        ops.close();
    }


    @Override
    public List<ClassDeploymentDefinition> getRequiredClasses() {
        return Arrays.asList(new ClassDeploymentDefinition(TemporaryQueueBean.class, this.container.getName()));
    }


    @Override
    public List<FileDeploymentDefinition> getRequiredAssets() {
        return new ArrayList<FileDeploymentDefinition>();
    }

}
