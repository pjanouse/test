/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.qa.tools.byteman.rule;


import org.apache.log4j.Logger;

import java.lang.reflect.Method;

/**
 * MethodRuleInstaller
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class RuleInstaller {

    // Logger
    private static final Logger log = Logger.getLogger(RuleInstaller.class);

    public static final String CLASS_KEY_PREFIX = "Class:";
    public static final String METHOD_KEY_PREFIX = "Method:";

    /**
     * This will install rule which is described in annotation of caller method.
     *
     * @param testClass class with test
     */
    public static void installRule(Class testClass) {
        installRule(testClass, "localhost", 9091);
    }

    /**
     * This will install rule which is described in annotation of caller method.
     *
     * @param testClass class with test
     * @param host      hostname where byteman listen to
     * @param port      port where byteman listen to
     */
    public static void installRule(Class testClass, String host, int port) {

        SubmitUtil.host = host;
        SubmitUtil.port = port;

        RuleInstaller ruleInstaller = new RuleInstaller();
        Throwable t = new Throwable();
        StackTraceElement[] elements = t.getStackTrace();
        String callerMethodName = null;
        boolean installed = false;
        for (int level = 1; level < elements.length; level++) {
            try {
                // climb up the stack trace and add all BM rules as long as they're on methods of the test class
//                if (!elements[level].getClassName().equals(testClass.getName())) {
//                    installed = true;
//                    break;
//                }

                callerMethodName = elements[level].getMethodName();
                log.info(String.format("CallerClassName='%s', caller method name='%s'", testClass.getName(), callerMethodName));
                ruleInstaller.installMethod(testClass.getMethod(callerMethodName));
                installed = true;
                break;
            } catch (Exception ex) {

                // this means that method has parameters -> testClass.getMethod(...) thrown exception
                // problem is that we don't know parameters here
                // we need another way how to get the method
                // so try to get all methods from test class and compare it with names from stacktrace
                // and try to find byteman rules
                Method[] testClassMethods = testClass.getMethods();
                for (int i = 0; i < testClassMethods.length; i++) {
                    if (callerMethodName != null) {
                        if (callerMethodName.equalsIgnoreCase(testClassMethods[i].getName())) {
                            ruleInstaller.installMethod(testClassMethods[i]);
                        }
                    }
                }
            }

        }
        if (!installed) {
            log.error("Cannot find corresponding annotations on stack trace methods");
        }
    }

    public static void uninstallAllRules(final String host, final int port) {
        SubmitUtil.host = host;
        SubmitUtil.port = port;

        log.info(String.format("Deleting all Byteman rules from host %s (%d)", host, port));
        SubmitUtil.uninstallAll();
    }

    public void installMethod(Method method) {
        String script = ExtractScriptUtil.extract(method);
        if (script != null) {
            SubmitUtil.install(generateKey(METHOD_KEY_PREFIX), script);
        }
    }

    public void uninstallMethod(Method method) {
        String script = ExtractScriptUtil.extract(method);
        if (script != null) {
            SubmitUtil.uninstall(generateKey(METHOD_KEY_PREFIX), script);
        }
    }

    private String generateKey(String prefix) {
        return prefix + Thread.currentThread().getName();
    }
}
