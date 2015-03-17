package org.jboss.qa.hornetq.tools.arquillian.extension;

import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.qa.hornetq.HornetQTestCase;

/**
 * Extension which sets property descriptor in HornetQ class which is used in tests and JMSProdider class.
 *
 * @author mnovak@redhat.com
 */
public class ArquillianConfigurationExtension implements RemoteLoadableExtension {

    @Override
    public void register(ExtensionBuilder builder) {
        builder.observer(HornetQTestCase.class);
    }
}
