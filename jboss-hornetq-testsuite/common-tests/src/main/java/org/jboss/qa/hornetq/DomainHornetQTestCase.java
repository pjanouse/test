package org.jboss.qa.hornetq;


import org.jboss.logging.Logger;

import java.util.Iterator;
import java.util.ServiceLoader;


public class DomainHornetQTestCase extends HornetQTestCase {

    private static final Logger LOG = Logger.getLogger(DomainHornetQTestCase.class);

    private DomainContainer domainContainer = null;

    public DomainContainer domainContainer() {
        if (domainContainer == null) {
            domainContainer = createContainer("cluster");
            LOG.info("Domain container done");
        }
        //domainContainer.update(controller);
        LOG.info("Domain container updated");
        LOG.info("Container:");
        LOG.info("  " + domainContainer.getHostname());
        LOG.info("  " + domainContainer.getPort());
        return domainContainer;
    }

    private DomainContainer createContainer(String name) {
        ServiceLoader<DomainContainer> loader = ServiceLoader.load(DomainContainer.class);
        Iterator<DomainContainer> iterator = loader.iterator();
        if (!iterator.hasNext()) {
            throw new RuntimeException("No implementation found for Container");
        }

        DomainContainer c = iterator.next();
        LOG.info("Container implementation found");
        c.init(getArquillianDescriptor(), controller);
        LOG.info("Container init done");
        return c;
    }

}
