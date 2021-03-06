// TODO refactor hashmap does NOT hold duplicate keys
package org.jboss.qa.hornetq.apps.impl;

import org.jboss.qa.hornetq.apps.JMSImplementation;

import javax.jms.JMSException;
import javax.jms.Message;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p/>
 * This class is used to store messages into file by JMS org.jboss.qa.hornetq.apps.clients.
 * <p/>
 * Saves each message on separate line in a form of:
 * 1. When HQ_DUP_HEADER is set:
 * MessageID,HQ_DUP_HEADER
 * 2. When it's not set:
 * MessageID,NO_HQ_DUPL_ID
 */
public class MessageStoreImpl {

    protected JMSImplementation jmsImplementation;

    @Deprecated
    public MessageStoreImpl() {
        jmsImplementation = HornetqJMSImplementation.getInstance();
    }

    public MessageStoreImpl(JMSImplementation jmsImplementation) {
        this.jmsImplementation = jmsImplementation;
    }

    /**
     * Save messages from list to a file.
     * 1. When HQ_DUP_HEADER is set:
     * MessageID,HQ_DUP_HEADER
     * 2. When it's not set:
     * MessageID,NO_HQ_DUPL_ID
     *
     * @param messages list of messages
     * @param toFile   file
     */
    public void save(List<Message> messages, File toFile) throws IOException, JMSException {

        if (toFile.exists()) {
            toFile.delete();
        }

        PrintWriter out = null;

        try {
            // Create file
            out = new PrintWriter(toFile);
            StringBuilder strBuilder = new StringBuilder();

            for (Message m : messages) {
                strBuilder.append(m.getJMSMessageID());
                strBuilder.append(",");
                try {
                    strBuilder.append(m.getStringProperty(jmsImplementation.getDuplicatedHeader()) != null ?
                            m.getStringProperty(jmsImplementation.getDuplicatedHeader()) :
                            "NO" + jmsImplementation.getDuplicatedHeader());
                } catch (JMSException ignore) {
                    //ignore
                }
                strBuilder.append("\n");
            }

            out.write(strBuilder.toString());
            out.flush();

        } finally {
            //Close the output stream
            if (out != null) {
                out.close();
            }
        }
    }


    /**
     * Load messages from file
     *
     * @param fromFile from file
     * @return map messageID -> HQ_DUP_ID
     */
    public Map<String, String> load(File fromFile) throws IOException {
        return load(fromFile, 0, Long.MAX_VALUE);
    }

    /**
     * Load messages from file.
     *
     * @param fromFile file from which to read
     * @param fromMessage starts from 0, for example 3 means it starts to read from message on line 4 in a file (4 included)
     * @param numberOfMessages number of lines to read
     * @return map messageID -> HQ_DUP_ID
     */
    public Map<String, String> load(File fromFile, long fromMessage, long numberOfMessages) throws IOException {
        if (!fromFile.exists()) {
            throw new IllegalArgumentException("There is not file with messages list: " + fromFile.getAbsolutePath());
        }

        FileReader in = null;
        Map<String, String> mapOfMesssages = new HashMap<String, String>();

        try {
            // Create file
            in = new FileReader(fromFile);
            BufferedReader reader = new BufferedReader(in);
            String line;
            long skip = 0;
            long i = 0;

            while (skip < fromMessage-1)  {
                reader.readLine();
                skip++;
            }
            while (i < numberOfMessages && (line = reader.readLine()) != null) {

                String[] tokens = line.split(",");
                mapOfMesssages.put(tokens[0], tokens[1]);
                i++;
            }

        } finally {
            //Close the input stream
            if (in != null) {
                in.close();
            }
        }

        return mapOfMesssages;
    }

    public static void main(String[] args) throws Exception, JMSException, IOException {
//        System.out.println("Try to save some messages.");
//        MessageStoreImpl store = new MessageStoreImpl();
//        List<Message> list = new ArrayList<Message>();
//
//        final Properties env = new Properties();
//        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
//        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", "localhost", "4447"));
//        InitialContext context = new InitialContext(env);
//        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
//        Connection conn = cf.createConnection();
//        conn.start();
//        Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
//        Message m;
//
//        MessageProducer p = session.createProducer((Queue) context.lookup("jms/queue/testQueue0"));
//        for (int i = 0; i < 10; i++) {
//            m = session.createTextMessage();
//            m.setStringProperty("_HQ_DUPL_ID", String.valueOf(i));
//            p.send(m);
//            list.add(m);
//        }
//        store.save(list, new File("messageProducer1.txt"));
//
//        Map<String, String> map = store.load(new File("messageProducer1.txt"), 4, 2);
//        for (String key : map.keySet()) {
//            System.out.println("key: " + key + ", value:" + map.get(key));
//        }

        FileMessageVerifier verifier = new FileMessageVerifier();
        verifier.setReceivedMessagesFile(new File("messageConsumer1.txt"));
        verifier.setSentMessagesFile(new File("messageProducer1.txt"));
        verifier.verifyMessages();
    }

}
