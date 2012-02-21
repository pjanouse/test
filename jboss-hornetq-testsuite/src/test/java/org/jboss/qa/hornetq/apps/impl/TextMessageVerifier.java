/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.qa.hornetq.apps.impl;

import java.util.Observable;
import java.util.Observer;

/**
 *  This class observers jms clients and store their sent and received messages.
 * 
 *  Then it's able to verify for example whether there are no duplicated or lost messages.
 * 
 * @author mnovak@redhat.com
 */
public class TextMessageVerifier implements Observer {
        
    @Override
    public void update(Observable o, Object arg) {
        
        
        
    }
    
    
    
}
