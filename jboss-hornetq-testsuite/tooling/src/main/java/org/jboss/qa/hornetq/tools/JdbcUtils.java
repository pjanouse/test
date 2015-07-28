package org.jboss.qa.hornetq.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.util.Scanner;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.Container;

public final class JdbcUtils {

    private static final Logger LOG = Logger.getLogger(JdbcUtils.class);

    private static final String META_INF_TXT = "meta-inf.txt";

    private static final String URL_JDBC_DRIVER_MASK =
            "http://www.qa.jboss.com/jdbc-drivers-products/EAP/%s/%s/jdbc4/%s";

    private static final String DEPLOYMENTS = "standalone" + File.separator + "deployments";


    private JdbcUtils() {
        // visibility
    }


    /**
     * Download jdbc driver for given EAP container into deployments directory.
     *
     * @param container  Test EAP container
     * @param database   Database name (eg oracle12c,mssql2012)
     * @return JDBC driver file name
     *
     * @throws IOException if the driver cannot be downloaded
     */
    public static String downloadJdbcDriver(Container container, String database) throws IOException {
        String eapVersion = getDriverVersion(container.getServerVersion());
        URL metaInfUrl = new URL(String.format(URL_JDBC_DRIVER_MASK, eapVersion, database, META_INF_TXT));
        LOG.info("Print meta-inf url: " + metaInfUrl);

        ReadableByteChannel rbc = Channels.newChannel(metaInfUrl.openStream());
        File targetDirDeployments = new File(container.getServerHome(), DEPLOYMENTS + File.separator + META_INF_TXT);

        FileOutputStream fos = new FileOutputStream(targetDirDeployments);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        Scanner scanner = new Scanner(new FileInputStream(targetDirDeployments));
        String jdbcFileName = scanner.nextLine();
        LOG.info("Print jdbc file name: " + jdbcFileName);

        URL jdbcUrl = new URL(String.format(URL_JDBC_DRIVER_MASK, eapVersion, database, jdbcFileName));
        ReadableByteChannel rbc2 = Channels.newChannel(jdbcUrl.openStream());
        File targetDirDeploymentsForJdbc = new File(container.getServerHome(), DEPLOYMENTS + File.separator + jdbcFileName);
        FileOutputStream fos2 = new FileOutputStream(targetDirDeploymentsForJdbc);
        fos2.getChannel().transferFrom(rbc2, 0, Long.MAX_VALUE);

        fos.close();
        fos2.close();
        rbc.close();
        rbc2.close();

        return jdbcFileName;
    }


    /**
     * JDBC driver version for given EAP version.
     *
     * That's not always the same thing, eg. drivers for 6.1.2+ are always 6.1.1.
     *
     * @param eapVersion EAP server version
     * @return EAP version which the JDBC drivers should come for.
     */
    private static String getDriverVersion(String eapVersion) {
        String[] versionArray = eapVersion.split("\\.");
        int major = Integer.valueOf(versionArray[0]);
        int minor = Integer.valueOf(versionArray[1]);
        int micro = Integer.valueOf(versionArray[2]);

        switch (major) {
            case 5:
                break;
            case 6:
                switch (minor) {
                    case 0:
                        break;
                    case 1:
                        if (micro > 1) {
                            // for 6.1.2+, use driver for version 6.1.1
                            micro = 1;
                        }
                        break;
                    default:
                        // for version 6.2.0+ always use driver for '0' micro version
                        micro = 0;
                        break;
                }
                break;
            case 7 :
                break;
            default:
                throw new IllegalArgumentException("Given container is not EAP5, EAP6 or EAP7! It says its major version is "
                        + major);
        }

        return String.format("%d.%d.%d", major, minor, micro);
    }

    public static String installJdbcDriverModule(Container container, String database) throws IOException {
        String eapVersion = getDriverVersion(container.getServerVersion());
        URL metaInfUrl = new URL(String.format(URL_JDBC_DRIVER_MASK, eapVersion, database, META_INF_TXT));
        LOG.info("Print meta-inf url: " + metaInfUrl);

        ReadableByteChannel rbc = Channels.newChannel(metaInfUrl.openStream());
        File targetDirDeployments = new File(container.getServerHome(), DEPLOYMENTS + File.separator + META_INF_TXT);

        FileOutputStream fos = new FileOutputStream(targetDirDeployments);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        Scanner scanner = new Scanner(new FileInputStream(targetDirDeployments));
        String jdbcFileName = scanner.nextLine();
        LOG.info("Print jdbc file name: " + jdbcFileName);

        URL jdbcUrl = new URL(String.format(URL_JDBC_DRIVER_MASK, eapVersion, database, jdbcFileName));
        ReadableByteChannel rbc2 = Channels.newChannel(jdbcUrl.openStream());
        File moduleFolder = new File(container.getServerHome() + File.separator + "modules" + File.separator + "system" + File.separator
                + "layers" + File.separator + "base" + File.separator + "com" + File.separator + "mylodhdb" + File.separator + "main");
        moduleFolder.mkdirs();
        
        FileOutputStream fos2 = new FileOutputStream(moduleFolder + File.separator + jdbcFileName);
        fos2.getChannel().transferFrom(rbc2, 0, Long.MAX_VALUE);


        PrintWriter writer = new PrintWriter(moduleFolder.getAbsolutePath() + File.separator + "module.xml", "UTF-8");
        writer.print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "        <module xmlns=\"urn:jboss:module:1.1\" name=\"com.mylodhdb\">\n"
                + "            <resources>\n"
                + "                <resource-root path=\"" + jdbcFileName + "\"/>\n"
                + "            </resources>\n"
                + "            <dependencies>\n"
                + "                <module name=\"javax.api\"/>\n"
                + "                <module name=\"javax.transaction.api\"/>\n"
                + "            </dependencies>\n"
                + "        </module>");
        writer.close();

        fos.close();
        fos2.close();
        rbc.close();
        rbc2.close();

        return jdbcFileName;
    }
}
