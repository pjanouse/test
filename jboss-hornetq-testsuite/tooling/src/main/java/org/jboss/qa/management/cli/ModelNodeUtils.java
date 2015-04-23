package org.jboss.qa.management.cli;

import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * @author Petr Kremensky <pkremens@redhat,com>
 */
public class ModelNodeUtils {

    // TODO just simple sample methods.!

    /**
     * Extract result from given ModelNode.
     *
     * @param response Response of CLI operation in form of ModelNode.
     * @return Result extracted from ModelNode response
     */
    public static String getResult(ModelNode response) {
        return response.get(RESULT).asString();
    }

    /**
     * Extract outcome from given ModelNode.
     *
     * @param response Response of CLI operation in form of ModelNode.
     * @return <code>true</code>, if response represented by given ModelNode was successful.
     */
    public static boolean isSuccessful(ModelNode response) {
        return response.get(OUTCOME).asString().equals(SUCCESS);
    }

}