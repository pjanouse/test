package org.jboss.qa.hornetq.tools.arquillian.extension;

import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;

/**
 * Just register extension classes to arquillian.
 *
 * @author mnovak
 */
public class CleanUpAfterTestExtension implements RemoteLoadableExtension {

    @Override
    public void register(ExtensionBuilder builder) {
        builder.observer(CleanUp.class);
    }
}
