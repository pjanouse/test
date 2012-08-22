/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.tools.arquillian.extension;

import org.apache.log4j.Logger;
import org.jboss.arquillian.config.descriptor.api.ArquillianDescriptor;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.arquillian.test.spi.event.suite.Before;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;

/**
 * Simply sets ArquillianDescriptor.
 *
 * @author mnovak@redhat.com
 */
public class ArquillianConfiguration {

    private static final Logger logger = Logger.getLogger(ArquillianConfigurationExtension.class);

    static ArquillianDescriptor arquillianDescriptor;

    public void setArquillianDescriptor(@Observes BeforeClass event, ArquillianDescriptor descriptor) {
        this.arquillianDescriptor = descriptor;
    }

    public void describeTestStart(@Observes Before event) {
        logger.info("Start test -------------------------------- " + event.getTestClass().getName() + "." + event.getTestMethod().getName());
    }

    public void describeTestStop(@Observes After event) {
        logger.info("Stop test -------------------------------- " + event.getTestClass().getName() + "." + event.getTestMethod().getName());
    }

}
