package org.jboss.qa.hornetq.tools;


import java.util.Random;


/**
 * Simple utility class to generate random alphanumeric strings of requested length.
 */
public final class RandomStringGenerator {

    private static final Random RND = new Random();

    private static final char[] SYMBOLS;

    static {
        StringBuilder tmp = new StringBuilder();
        for (char ch = '0'; ch <= '9'; ch++) {
            tmp.append(ch);
        }
        for (char ch = 'a'; ch <= 'z'; ch++) {
            tmp.append(ch);
        }
        for (char ch = 'A'; ch <= 'Z'; ch++) {
            tmp.append(ch);
        }
        SYMBOLS = tmp.toString().toCharArray();
    }

    private RandomStringGenerator() {
        // lower visibility
    }

    public static String generateString(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Cannot generate string of requested length " + length);
        }

        char[] buffer = new char[length];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = SYMBOLS[RND.nextInt(SYMBOLS.length)];
        }

        return new String(buffer);
    }

}
