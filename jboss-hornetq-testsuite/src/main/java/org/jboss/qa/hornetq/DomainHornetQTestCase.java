package org.jboss.qa.hornetq;


import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.shrinkwrap.api.spec.WebArchive;


public class DomainHornetQTestCase extends HornetQTestCase {

    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_GROUP1)
    @TargetsContainer(SERVER_GROUP1)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKillServletServerGroup1() throws Exception {
        return createKillerServlet("killerServletGroup1.war");
    }

    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_GROUP2)
    @TargetsContainer(SERVER_GROUP2)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKillServletServerGroup2() throws Exception {
        return createKillerServlet("killerServletGroup2.war");
    }
    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_GROUP3)
    @TargetsContainer(SERVER_GROUP3)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKillServletServerGroup3() throws Exception {
        return createKillerServlet("killerServletGroup3.war");
    }
    @Deployment(managed = false, testable = false, name = SERVLET_KILLER_GROUP4)
    @TargetsContainer(SERVER_GROUP4)
    @SuppressWarnings("unused")
    public static WebArchive getDeploymentKillServletServerGroup4() throws Exception {
        return createKillerServlet("killerServletGroup4.war");
    }

}
