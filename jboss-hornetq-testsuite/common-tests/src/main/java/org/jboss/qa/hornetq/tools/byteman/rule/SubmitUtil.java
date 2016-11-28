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
package org.jboss.qa.hornetq.tools.byteman.rule;

import org.jboss.byteman.agent.submit.ScriptText;
import org.jboss.byteman.agent.submit.Submit;
import org.jboss.logging.Logger;
import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SumitUtil
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class SubmitUtil {

    public static String host = "localhost";
    public static int port = 9091;

    // Logger
    private static final Logger log = Logger.getLogger(SubmitUtil.class);

    public static void install(final String key, final String script) {

        final Submit submit = new Submit(host, port);

        // this can hang on some machines because agent does not sent a response
        // adding timeout 60 s
        Thread submitThread = new Thread() {
            public void run() {
                try {
                    submit.addScripts(Arrays.asList(new ScriptText(key, script)));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        };
        submitThread.run();
        try {
            submitThread.join(60000);
        } catch (InterruptedException e) {
            //ignore
        }
        if (submitThread.isAlive()) {
            Assert.fail("Could not install byteman rule because byteman agent did not answer when rule was deployed. For this " +
                    "reason submit.addScripts() method did not returned and hangs.");
        }
    }

//    public static void installFromStream(InputStream rules) {
//        try {
//            Submit submit = new Submit(host, port);
//            submit.addRulesFromResources(Arrays.asList(new InputStream[]{rules}));
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//            throw new SubmitException("Could not install scripts from resource", e);
//        }
//    }

    public static void installFromFiles(String[] filesWithRules) {
        try {
            Submit submit = new Submit(host, port);

            File ruleDir = new File("src" + File.separator + "test" + File.separator + "resources" + File.separator);
            File file;
            List<String> listPathToFilesWithRules = new ArrayList<String>();
            for (String ruleFile : filesWithRules) {
                file = new File(ruleDir, ruleFile);
                listPathToFilesWithRules.add(file.getAbsolutePath());
            }
            submit.addRulesFromFiles(listPathToFilesWithRules);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new SubmitException("Could not install scripts from resource", e);
        }
    }

    public static void uninstall(String key, String script) {
        try {
            Submit submit = new Submit();
            submit.deleteScripts(Arrays.asList(new ScriptText(key, script)));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new SubmitException("Could not uninstall script from file", e);
        }
    }

    public static void uninstallAll() {
        try {
            Submit submit = new Submit(host, port);
            submit.deleteAllRules();
        } catch (Exception e) {
            log.error("Error on uninstalling Byteman rules from server", e);
            throw new SubmitException("Could not delete rules", e);
        }
    }
}
