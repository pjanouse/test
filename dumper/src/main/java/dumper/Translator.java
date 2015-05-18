/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dumper;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author mstyk
 */
public class Translator {

    Map<String, String> eap6toEap7;
    Map<String, String> eap7toEap6;

    public Translator() {
        initDictionaries();
    }

    private void initDictionaries() {
        eap6toEap7 = new HashMap<String, String>();
        eap7toEap6 = new HashMap<String, String>();

        addToDictionaries("hornetq-server", "server");
        addToDictionaries("param", "params");
        addToDictionaries("connector", "connectors");
        addToDictionaries("discovery-group-name", "discovery-group");
        addToDictionaries("connector-ref", "connector-name");
        addToDictionaries("source-context", "source-context-property");
        addToDictionaries("target-context", "target-context-property");

    }

    private void addToDictionaries(String eap6Value, String eap7Value) {
        eap6toEap7.put(eap6Value, eap7Value);
        eap7toEap6.put(eap7Value, eap6Value);
    }

    public String translateEap6toEap7(String eap6Value) {

        String result = eap6toEap7.get(eap6Value);
        return (result == null) ? eap6Value : result;
    }

    public String translateEap7toEap6(String eap7value) {

        String result = eap7toEap6.get(eap7value);
        return (result == null) ? eap7value : result;
    }
}

