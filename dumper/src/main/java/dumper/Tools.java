package dumper;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;

/**
 * Created by okalman on 4/15/15.
 */
public class Tools {

    private static final String HOST_ADDR = "127.0.0.1";

    static final String CORE = "core";
    static final String STANDALONE = "standalone";
    static final String VERSION = "version";

    private static final String OP = "operation";
    private static final String OP_ADDR = "address";
    private static final String RECURSIVE = "recursive";
    private static final String OPERATIONS = "operations";
    private static final String PROXIES = "proxies";
    private static final String INHERITED = "inherited";
    private static final String READ_RESOURCE_DESCRIPTION_OPERATION = "read-resource-description";
    private static final String OUTCOME = "outcome";
    private static final String SUCCESS = "success";
    private static final String RESULT = "result";


    static ModelNode getCurrentRunningResourceDefinition(PathAddress pathAddress) throws Exception {
        ModelControllerClient client = ModelControllerClient.Factory.create(HOST_ADDR, 9990);
        try {
            ModelNode op = new ModelNode();
            op.get(OP).set(READ_RESOURCE_DESCRIPTION_OPERATION);
            op.get(OP_ADDR).set(pathAddress.toModelNode());
            op.get(RECURSIVE).set(true);
            op.get(OPERATIONS).set(true);
            op.get(PROXIES).set(false);
            op.get(INHERITED).set(false);

            return Tools.getAndCheckResult(client.execute(op));

        } finally {
            client.close();
        }
    }
    static ModelNode getAndCheckResult(ModelNode result) {
        if (!result.get(OUTCOME).asString().equals(SUCCESS)) {
            //throw new RuntimeException(result.get(FAILURE_DESCRIPTION).toString());
        }
        return result.get(RESULT);
    }
    static File getProjectDirectory() throws URISyntaxException {
        //Try to work around IntilliJ's crappy current directory handling
        return new File("target/");
    }

    static void serializeModelNodeToFile(ModelNode modelNode, File file) throws Exception {
        if (file.exists()) {
            file.delete();
        }
        PrintWriter writer = new PrintWriter(file);
        try {
            modelNode.writeString(writer, false);
            System.out.println("Resource definition for running server written to: " + file.getAbsolutePath());
        } finally {
            writer.close();
        }
    }
    static ModelNode deserializeModelNodeFromFile(File file) throws Exception{
         if (!file.exists()) {
             throw new IllegalArgumentException("file not found");
        }
        ModelNode modelNode;
        InputStream is = null;
        try{
            is = new FileInputStream(file);
            modelNode = ModelNode.fromStream(is);
        }finally{
            if(is != null){
                is.close();
            }
        }
        return modelNode;
    }
    
    

}
