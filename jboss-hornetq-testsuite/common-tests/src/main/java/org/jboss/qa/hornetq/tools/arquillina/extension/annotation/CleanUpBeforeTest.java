package org.jboss.qa.hornetq.tools.arquillina.extension.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for deleting data, tmp, log directory before test
 *
 * @author mnovak@redhat.com
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CleanUpBeforeTest {

}

