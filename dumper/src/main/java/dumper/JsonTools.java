/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dumper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.jboss.dmr.ModelNode;

import org.codehaus.jackson.map.*;
import org.codehaus.jackson.*;

/**
 *
 * @author mstyk
 */
public class JsonTools {

    public static String convertToJson(ModelNode node) {

        String[] type = new String[]{"BOOLEAN", "STRING", "INT", "LONG", "OBJECT", "LIST", "BIG_DECIMAL", "DOUBLE"};

        String nodeString = node.toString();
        String jsonString = nodeString.replace("=>", ":");
        jsonString = jsonString.replace("undefined", "{}");

        String pattern = "(-?\\d+)(L)(,?)";
        jsonString = jsonString.replaceAll(pattern, "$1$3");

        for (String s : type) {
            pattern = "(:) (" + s + ")(,?)";
            jsonString = jsonString.replaceAll(pattern, "$1\"$2\"$3");
        }

        return jsonString;
    }

    public static void serializeModelNodeToJsonFile(ModelNode modelNode, File file) throws Exception {
        if (file.exists()) {
            file.delete();
        }
        PrintWriter writer = new PrintWriter(file);
        try {
            writer.println(convertToJson(modelNode));
            System.out.println("Json written to " + file.getAbsolutePath());
        } finally {
            writer.close();
        }
    }

    public static File getRunningServerDefinitionJsonFile() throws Exception {
        File output = new File("eapdef/eapDefinitionRunning.json");

        List<PathElement> path = new ArrayList<PathElement>();
        path.add(new PathElement("subsystem", "messaging-activemq"));
        ModelNode Eap7ResourceDefinition = Tools.getCurrentRunningResourceDefinition(PathAddress.pathAddress(path));
        serializeModelNodeToJsonFile(Eap7ResourceDefinition, output);
        return output;
    }

    public static void createJsonFromDefinitionFile(File file) throws Exception {
        if (!file.exists()) {
            throw new IllegalArgumentException("file not exists");
        }
        ModelNode eap6def = Tools.deserializeModelNodeFromFile(file);

        File json = new File("eapdef/eap6definition.json");
        serializeModelNodeToJsonFile(eap6def, json);
    }

    public static JsonNode createJsonTree(File file) throws IOException {

        if (file == null) {
            throw new NullPointerException("file");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readValue(file, JsonNode.class);

        return rootNode;
    }

}
