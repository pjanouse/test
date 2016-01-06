package org.jboss.byteman.qa.hornetq;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

import java.lang.reflect.Field;

/**
 * Created by okalman on 10/9/15.
 */
public class BytemanCustomHelper extends Helper {
    String s;
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

    public void setToNull(Object instance){
        Class<?> c = instance.getClass();
        try {
            Field f = c.getDeclaredField("currentDelivery");
            f.set(instance, null);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

}
