package org.jboss.byteman.qa.hornetq;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

import java.lang.reflect.Field;
import java.net.InetAddress;

/**
 * Created by okalman on 10/9/15.
 */
public class BytemanCustomHelper extends Helper {
    public BytemanCustomHelper(Rule rule) {
        super(rule);
    }

    public void executeCmd(String cmd){
        try {
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            System.out.println(cmd + "was successfully executed");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void waitUntilNetworkFails(String host){
        long TIMEOUT = 30000;
        try {
            InetAddress address = InetAddress.getByName(host);
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < TIMEOUT) {
                if(!address.isReachable(500)){
                    System.out.println("Host: " + host + "is down");
                    break;
                }else{
                    System.out.println("Waiting for: " + host + "to fail");
                    Thread.sleep(1000);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void sleep(long millis){
        try{
            Thread.sleep(millis);
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    public void setToNull(Object instance){
        Class<?> c = instance.getClass();
        try {
            Field f = c.getDeclaredField("currentDelivery");
            f.setAccessible(true);
            f.set(instance, null);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
