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

import java.lang.reflect.Method;
import org.jboss.qa.tools.byteman.annotation.BMRule;
import org.jboss.qa.tools.byteman.annotation.BMRules;

/**
 * ExtractScriptUtil
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public final class ExtractScriptUtil
{

    public static String extract(Method method)
    {
        BMRule rule = method.getAnnotation(BMRule.class);
        BMRules rules = method.getAnnotation(BMRules.class);

        return createRules(rule, rules);
    }

    private static String createRules(BMRule rule, BMRules rules)
    {
        if(rule != null || rules != null)
        {
           return GenerateScriptUtil.constructScriptText(toRuleArray(rule, rules));
        }
        return null;
    }

    private static BMRule[] toRuleArray(BMRule rule, BMRules rules)
    {
       if(rule != null)
       {
          return new BMRule[] {rule};
       }
       return rules.value();
    }
}
