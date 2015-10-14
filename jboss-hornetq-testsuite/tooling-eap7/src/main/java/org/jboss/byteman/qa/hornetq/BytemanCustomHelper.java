package org.jboss.byteman.qa.hornetq;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

/**
 * Created by okalman on 10/9/15.
 */
public class BytemanCustomHelper extends Helper {
    public BytemanCustomHelper(Rule rule) {
        super(rule);
    }

    public void executeCmd(String cmd){
        try {
            Runtime.getRuntime().exec(cmd);
            System.out.println(cmd + "was successfully executed");
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
