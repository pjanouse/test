package org.jboss.qa.hornetq.tools;

/**
 * Created by okalman on 11/11/15.
 */
public class PiCalc {
    public double calculate(double timeoutMilis){
        double act=0;
        long counter=1;
        double start = System.currentTimeMillis();
        while(true){
            act=act+(4.0/(counter));
            act=act-(4.0/(counter+2));
            counter=counter+4;
            if(System.currentTimeMillis()-start>timeoutMilis){
                break;
            }
        }
        return act;
    }


}
