// TODO finish this class
package org.jboss.qa.hornetq.apps.impl;

import javax.jms.*;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.*;
import java.util.*;

/**
 * <p/>
 * This class is used to store messages into file by JMS clients.
 * <p/>
 * Saves each message on separate line in a form of:
 * 1. When HQ_DUP_HEADER is set:
 * MessageID,HQ_DUP_HEADER
 * 2. When it's not set:
 * MessageID,NO_HQ_DUPL_ID
 */
public class MessageStoreImpl {

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
                    strBuilder.append(m.getStringProperty("_HQ_DUPL_ID") != null ? m.getStringProperty("_HQ_DUPL_ID") : "NO_HQ_DUPL_ID");
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
     * @param fromFile
     * @return
     */
    public Map<String, String> load(File fromFile) throws IOException {
        return load(fromFile, 0, Long.MAX_VALUE);
    }

    /**
     * Load messages from file.
     *
     * @param fromFile
     * @param fromMessage starts from 0, for example 3 means it starts to read from message on live 4 in a file (4 included)
     * @param numberOfMessages number of lines to read
     * @return
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
            String line = null;
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

    public static void main(String[] args) throws NamingException, JMSException, IOException {
        System.out.println("Try to save some messages.");
        MessageStoreImpl store = new MessageStoreImpl();
        List<Message> list = new ArrayList<Message>();

        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        env.put(Context.PROVIDER_URL, String.format("remote://%s:%s", "localhost", "4447"));
        InitialContext context = new InitialContext(env);
        ConnectionFactory cf = (ConnectionFactory) context.lookup("jms/RemoteConnectionFactory");
        Connection conn = (Connection) cf.createConnection();
        conn.start();
        Session session = (Session) conn.createSession(true, Session.SESSION_TRANSACTED);
        Message m = null;

        MessageProducer p = session.createProducer((Queue) context.lookup("jms/queue/testQueue0"));
        for (int i = 0; i < 10; i++) {
            m = session.createTextMessage();
            m.setStringProperty("_HQ_DUPL_ID", String.valueOf(i));
            p.send(m);
            list.add(m);
        }
//        store.save(list, new File("messageProducer1.txt"));

        Map<String, String> map = store.load(new File("messageProducer1.txt"), 4, 2);
        for (String key : map.keySet()) {
            System.out.println("key: " + key + ", value:" + map.get(key));
        }
    }

}
