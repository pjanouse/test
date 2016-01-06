package org.jboss.byteman.qa.hornetq;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

<<<<<<< HEAD
=======
import java.lang.reflect.Field;

>>>>>>> e04e448... Test case for https://bugzilla.redhat.com/show_bug.cgi?id=1288578 ported for artemis
/**
 * Created by okalman on 10/9/15.
 */
public class BytemanCustomHelper extends Helper {
<<<<<<< HEAD
=======
    String s;
>>>>>>> e04e448... Test case for https://bugzilla.redhat.com/show_bug.cgi?id=1288578 ported for artemis
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
<<<<<<< HEAD
=======

    public void setToNull(Object instance){
        Class<?> c = instance.getClass();
        try {
            Field f = c.getDeclaredField("currentDelivery");
            f.set(instance, null);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

>>>>>>> e04e448... Test case for https://bugzilla.redhat.com/show_bug.cgi?id=1288578 ported for artemis
}
