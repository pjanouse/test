package org.jboss.qa.hornetq.tools;

import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Created by mnovak on 3/24/15.
 */
public class MulticastAddressUtils {

    private static final Logger log = Logger.getLogger(MulticastAddressUtils.class);

    private static String generateMulticastAddress()  {
        return new StringBuilder().append(randInt(224, 239)).append(".").append(randInt(1, 254)).append(".")
                .append(randInt(1, 254)).append(".").append(randInt(1, 254)).toString();
    }

    /**
     * Checks environment variable  MCAST_ADDR if it's set. If not then it generates IPv4 multicast ddress.
     * @return multicast address
     */
    public static String getMulticastAddress()  {
        String tmpMultiCastAddress = System.getProperty("MCAST_ADDR");
        if (tmpMultiCastAddress == null)    {
            tmpMultiCastAddress = generateMulticastAddress();
        }
        return checkMulticastAddress(tmpMultiCastAddress);
    }

    /**
     * Verify that multicast address is a multicast address.
     *
     * @param multiCastAddress multicast address
     * @return
     */
    private static String checkMulticastAddress(String multiCastAddress) {
        if (multiCastAddress == null) {
            log.error("Environment variable for MCAST_ADDR is empty (see above), please setup correct MCAST_ADDR variable." +
                    " Stopping test suite by throwing runtime exception.");
            throw new RuntimeException("MCAST_ADDR is null, please setup correct MCAST_ADDR");
        }

        InetAddress ia = null;

        try {
            ia = InetAddress.getByName(multiCastAddress);
            if (!ia.isMulticastAddress())   {
                log.error("Address: " + multiCastAddress + " cannot be found. Double check your MCAST_ADDR environment variable.");
                throw new RuntimeException("Address: " + multiCastAddress + " cannot be found. Double check your  properties whether they're correct.");
            }
        } catch (UnknownHostException e) {
            log.error("Address: " + multiCastAddress + " cannot be found. Double check your properties whether they're correct.", e);
        }
        return multiCastAddress;
    }

    /**
     * Returns a psuedo-random number between min and max, inclusive.
     * The difference between min and max can be at most
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param min Minimim value
     * @param max Maximim value.  Must be greater than min.
     * @return Integer between min and max, inclusive.
     * @see java.util.Random#nextInt(int)
     */
    private static int randInt(int min, int max) {

        // Usually this can be a field rather than a method variable
        Random rand = new Random();

        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive
        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }
}
