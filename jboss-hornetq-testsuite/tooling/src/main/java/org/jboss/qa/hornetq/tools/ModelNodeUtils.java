package org.jboss.qa.hornetq.tools;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.impl.ClientConfigurationImpl;
import org.jboss.dmr.ModelNode;
import org.jboss.qa.hornetq.exception.ModelNodeOperationException;
import org.jboss.threads.JBossThreadFactory;

public final class ModelNodeUtils {

    private final static int DEFAULT_TIMEOUT = 30000;

    private ModelNodeUtils() {
        // visibility
    }

    public static ModelNode applyOperation(String hostname, int managementPort, ModelNode operation) throws IOException,
            ModelNodeOperationException {

        ModelControllerClient client = null;

        try {
            client = createClient(hostname, managementPort);
            return applyOperation(client, operation);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    public static ModelNode applyOperation(ModelControllerClient client, ModelNode operation) throws IOException,
            ModelNodeOperationException {

        ModelNode result = client.execute(operation);

        if (result.hasDefined(ClientConstants.OUTCOME)
                && ClientConstants.SUCCESS.equals(result.get(ClientConstants.OUTCOME).asString())) {
            return result;
        } else if (result.hasDefined(ClientConstants.FAILURE_DESCRIPTION)) {
            final String failureDesc = result.get(ClientConstants.FAILURE_DESCRIPTION).toString();
            throw new ModelNodeOperationException(failureDesc);
        } else {
            throw new ModelNodeOperationException(String.format("Operation not successful; outcome = '%s'",
                    result.get(ClientConstants.OUTCOME)));
        }
    }

    public static ModelControllerClient createClient(String hostname, int managementPort) throws UnknownHostException {
        return createClient(hostname, managementPort, DEFAULT_TIMEOUT);
    }

    public static ModelControllerClient createClient(String hostname, int managementPort, int timeout)
            throws UnknownHostException {

        AtomicInteger executorCount = new AtomicInteger(0);
        ThreadGroup group = new ThreadGroup("management-client-thread");
        ThreadFactory threadFactory = new JBossThreadFactory(group, Boolean.FALSE, null, "%G "
                + executorCount.incrementAndGet() + "-%t", null, null, AccessController.getContext());
        ExecutorService executorService = new ThreadPoolExecutor(2, 6, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), threadFactory);

//        return ModelControllerClient.Factory.create(ClientConfigurationImpl.create(hostname, managementPort, null, null,
//                timeout));
        return ModelControllerClient.Factory.create(hostname, managementPort);
    }

}
