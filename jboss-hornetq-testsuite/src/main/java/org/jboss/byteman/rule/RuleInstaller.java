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
package org.jboss.byteman.rule;

import java.lang.reflect.Method;



/**
 * MethodRuleInstaller
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class RuleInstaller
{
    public static final String CLASS_KEY_PREFIX = "Class:";
    public static final String METHOD_KEY_PREFIX = "Method:";

    /**
     * 
     * This will install rule which is described in annotation of caller method.
     * 
     * @param testClass 
     * 
     */
    public static void installRule(Class testClass) {
        try {
        RuleInstaller ruleInstaller = new RuleInstaller();
        
        // get name of caller method - of the test with byteman annotation
        Throwable t = new Throwable();
        
        StackTraceElement[] elements = t.getStackTrace();
        
        String callerMethodName = elements[1].getMethodName();
        
        System.out.println("CallerClassName=" + testClass.getName() + " , Caller method name: " + callerMethodName);
        
        ruleInstaller.installMethod(testClass.getMethod(callerMethodName, null));
        
        } catch (NoSuchMethodException ex)  {
            ex.printStackTrace();
        }
        
    }


    public void installMethod(Method method)
    {
        String script = ExtractScriptUtil.extract(method);
        System.out.println("mnovak: install method called");
        if(script != null)
        {
            SubmitUtil.install(generateKey(METHOD_KEY_PREFIX), script);
        }
    }

    public void uninstallMethod(Method method)
    {
        String script = ExtractScriptUtil.extract(method);
        if(script != null)
        {
            SubmitUtil.uninstall(generateKey(METHOD_KEY_PREFIX), script);
        }
    }

    private String generateKey(String prefix)
    {
        return prefix + Thread.currentThread().getName();
    }
}
