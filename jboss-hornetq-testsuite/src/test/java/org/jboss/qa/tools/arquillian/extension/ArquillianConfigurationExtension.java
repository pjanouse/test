package org.jboss.qa.tools.arquillian.extension;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.qa.hornetq.test.HornetQTestCase;

/**
 * Extension which sets property descriptor in ConfigurationLoader class which is used in tests and JMSProdider class.
 *
 * @author mnovak@redhat.com
 */
public class ArquillianConfigurationExtension implements RemoteLoadableExtension {

    @Override
    public void register(ExtensionBuilder builder) {
        builder.observer(HornetQTestCase.class);
    }
}
