/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.tools.arquillian.extension;

import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

/**
 * Simply sets ArquillianDescriptor to ConfigurationLoader.
 *
 * @author mnovak@redhat.com
 */
public class ArquillianConfiguration {

    static ArquillianDescriptor arquillianDescriptor;

    public void setArquillianDescriptor(@Observes BeforeClass event, ArquillianDescriptor descriptor) {
        this.arquillianDescriptor = descriptor;
    }

}
