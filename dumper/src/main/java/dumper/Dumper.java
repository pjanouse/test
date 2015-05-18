package dumper;

import java.io.File;
import org.codehaus.jackson.JsonNode;

/**
 * Created by okalman on 4/15/15, edited by mstyk on 5/14/15.
 */
public class Dumper {
    
    // option to add whole subtree of different node to final result
    public static final boolean ADD_WHOLE_DIFFERENT_SUBTREES = true;

    public static void main(String[] args) throws Exception {
     
        File Eap6DefJson = new File("eapdef/eap6definition.json");
        File Eap7DefJson = JsonTools.getRunningServerDefinitionJsonFile();

        JsonNode eap6Node = JsonTools.createJsonTree(Eap6DefJson);
        JsonNode eap7Node = JsonTools.createJsonTree(Eap7DefJson);

        CompareTools ct = new CompareTools(eap6Node, eap7Node,ADD_WHOLE_DIFFERENT_SUBTREES);
        ct.compareNodes();
        ct.printAliasDifferenceResult(new File("Result_renamed.txt"));
        ct.printDifferenceResult(new File("Result_differences.txt"));
        ct.printResult(new File("Result_all.txt"));

    }
}
