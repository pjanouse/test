/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.tools.arquillian.extension;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.jboss.arquillian.test.spi.event.suite.Test;

/**
 * Simply sets ArquillianDescriptor to ConfigurationLoader.
 *
 * @author mnovak@redhat.com
 */
public class ArquillianConfiguration {

    private static final Logger logger = Logger.getLogger(ArquillianConfigurationExtension.class);

    static ArquillianDescriptor arquillianDescriptor;

    public void setArquillianDescriptor(@Observes BeforeClass event, ArquillianDescriptor descriptor) {
        this.arquillianDescriptor = descriptor;
    }

    public void describeTestTest(@Observes Test event) {
        logger.info("Starting test -------------------------------- " + event.getTestClass().getName() + "." + event.getTestMethod().getName());
    }

}
