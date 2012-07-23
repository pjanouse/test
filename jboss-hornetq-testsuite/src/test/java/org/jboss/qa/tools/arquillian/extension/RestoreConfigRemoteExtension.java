
package org.jboss.qa.tools.arquillian.extension;

import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;

/**
 * Just register extension classes to arquillian.
 * 
 * @author mnovak
 */
public class RestoreConfigRemoteExtension implements RemoteLoadableExtension {

    @Override
    public void register(ExtensionBuilder builder) {
        builder.observer(RestoreConfig.class);
    }
}
