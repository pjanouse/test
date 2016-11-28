package org.jboss.qa.hornetq.tools;


import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Class for dealing with eap version.
 *
 * Stolen from Manu core module's JbossVersion class.
 */
public class EapVersion implements Comparable<EapVersion> {

    private static final Logger LOG = Logger.getLogger(EapVersion.class);

    private static final String VERSION_FILE_NAME = "version.txt";

    private static final String VERSION_INFO_PATTERN =
            "(?i)((Red Hat )?JBoss Enterprise Application Platform - Version )(.+?)(.[a-zA-Z]+[0-9]*)";
    private static final int VERSION_STRING_INDEX = 3;

    private static final Pattern VALIDATION_REGEXP =
            Pattern.compile("\\d+\\.\\d+\\.\\d+(\\.((((CP\\.)?(DR|ER|CR)\\d+)(\\.\\d+)?)|(GA|CP)))?");

    // indices to split version string
    private static final int MAJOR_INDEX = 0;
    private static final int MINOR_INDEX = 1;
    private static final int INCREMENTAL_INDEX = 2;
    private static final int BUILD_INDEX = 3;
    private static final int QUALIFIER_INDEX = 4;

    private static final List<String> ALLOWED_BUILD_VERSIONS = Arrays.asList("DR", "ER", "CR", "GA");

    private final int major;
    private final int minor;
    private final int incremental;
    private final String build; // nullable
    private final Integer buildNumber; // nullable
    private final Integer qualifier; // nullable
    private boolean cpRelease = false;

    /**
     * Parse version from string. Valid formats are:
     *
     * nonCP: MajorVersion.MinorVersion.IncrementalVersion[.BuildNumber[.Qualifier]]
     * CP   : MajorVersion.MinorVersion.IncrementalVersion.CP[.BuildNumber[.Qualifier]]
     *
     * @param version string representing version (e.g. 6.2.0.CR1)
     */
    private EapVersion(final String version) {
        if (version == null) {
            throw new IllegalArgumentException("Product version string must not be null");
        }

        Matcher matcher = VALIDATION_REGEXP.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported product version pattern. Given " + version);
        }

        String[] tokenized = version.split("\\.");
        major = Integer.parseInt(tokenized[MAJOR_INDEX]);
        minor = Integer.parseInt(tokenized[MINOR_INDEX]);
        incremental = Integer.parseInt(tokenized[INCREMENTAL_INDEX]);

        if (tokenized.length == 3) {
            build = null;
            buildNumber = null;
            qualifier = null;
        } else {
            if ("CP".equals(tokenized[BUILD_INDEX]) && tokenized.length > 4) { // non-final CP release
                cpRelease = true;

                String buildString = tokenized[BUILD_INDEX + 1];
                build = buildString.substring(0, 2);
                if (!ALLOWED_BUILD_VERSIONS.contains(build)) {
                    throw new IllegalArgumentException("Unsupported product version pattern. Given " + version);
                }

                if ("DR".equals(build) || "ER".equals(build) || "CR".equals(build)) {
                    buildNumber = Integer.valueOf(buildString.substring(2));
                } else {
                    buildNumber = null;
                }

                if (tokenized.length == 6) {
                    qualifier = Integer.valueOf(tokenized[QUALIFIER_INDEX + 1]);
                } else {
                    qualifier = null;
                }
            } else if ("CP".equals(tokenized[BUILD_INDEX]) && tokenized.length == 4) { // final CP release
                cpRelease = true;
                build = null;
                buildNumber = null;
                qualifier = null;
            } else { // non-CP release
                String buildString = tokenized[BUILD_INDEX];
                build = buildString.substring(0, 2);
                if (!ALLOWED_BUILD_VERSIONS.contains(build)) {
                    throw new IllegalArgumentException("Unsupported product version pattern. Given " + version);
                }

                if ("DR".equals(build) || "ER".equals(build) || "CR".equals(build)) {
                    buildNumber = Integer.valueOf(buildString.substring(2));
                } else {
                    buildNumber = null;
                }

                if (tokenized.length == 5) {
                    qualifier = Integer.valueOf(tokenized[QUALIFIER_INDEX]);
                } else {
                    qualifier = null;
                }
            }
        }

        // Version can be still CP version: if version is GA and incremental version is not 0 and version is greater than 6.2.0
        if (isGa() && !isCpRelease()) {
            if (major >= 6 && minor >= 2 && incremental != 0) {
                cpRelease = true;
            }
        }
    }


    public static EapVersion fromString(final String version) {
        return new EapVersion(version);
    }


    /**
     * Read version from version.txt in EAP_HOME root directory and return a parsed EapVersion instance.
     *
     * @param eapHome EAP root directory
     * @return Parsed version
     */
    public static EapVersion fromEapVersionFile(final String eapHome) throws IOException {
        if (eapHome == null || eapHome.isEmpty()) {
            throw new IllegalArgumentException("EAP home directory cannot be null");
        }

        return fromEapVersionFile(new File(eapHome));
    }


    public static EapVersion fromEapVersionFile(final File eapHome) throws IOException {
        File versionFile = new File(eapHome, VERSION_FILE_NAME);
        Scanner versionScanner = new Scanner(new FileInputStream(versionFile));
        String versionLine = versionScanner.nextLine();

        LOG.debug("Print content of version file: " + versionLine);
        String version = versionLine.replaceAll(VERSION_INFO_PATTERN, "$" + VERSION_STRING_INDEX).trim();
        return new EapVersion(version);
    }


    public int getMajor() {
        return major;
    }


    public int getMinor() {
        return minor;
    }


    public int getIncremental() {
        return incremental;
    }


    public String getBuild() {
        return build;
    }


    public Integer getBuildNumber() {
        return buildNumber;
    }


    public Integer getQualifier() {
        return qualifier;
    }


    public boolean isCpRelease() {
        return cpRelease;
    }


    /**
     * Test if version is GA release. Final CP releases are also considered GA.
     *
     * @return true if version is GA release.
     */
    public boolean isGa() {
        if (isCpRelease()) {
            return buildNumber == null;
        }

        return build == null || "GA".equals(build);
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(major).append(".").append(minor).append(".").append(incremental);

        if (isCpRelease()) {
            builder.append(".CP");
        }

        if (build != null) {
            builder.append(".").append(build);
            if (buildNumber != null) {
                builder.append(buildNumber);
            }

            if (qualifier != null) {
                builder.append(".").append(qualifier);
            }
        }

        return builder.toString();
    }

    @Override
    public boolean equals(final Object version) {
        return version instanceof EapVersion && compareTo((EapVersion) version) == 0;
    }


    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + incremental;
        result = 31 * result + build.hashCode();
        result = 31 * result + buildNumber.hashCode();
        result = 31 * result + qualifier.hashCode();
        result = 31 * result + (cpRelease ? 1 : 0);
        return result;
    }


    @Override
    public int compareTo(final EapVersion other) {
        if (other == null) {
            throw new IllegalArgumentException("Attribute version cannot be null");
        }

        if (major != other.getMajor()) {
            return major - other.getMajor();
        }
        if (minor != other.getMinor()) {
            return minor - other.getMinor();
        }
        if (incremental != other.getIncremental()) {
            return incremental - other.getIncremental();
        }

        if (this.isCpRelease() != other.isCpRelease()) {
            return this.cpRelease ? 1 : -1;
        }

        // 6.0.0 == 6.0.0.GA
        if (isGa() && other.isGa()) {
            return 0;
        }

        // 6.0.0.XXX < 6.0.0
        if (build != null && other.isGa()) {
            return -1;
        }

        // 6.0.0 > 6.0.0.XXX
        if (isGa() && other.getBuild() != null) {
            return 1;
        }

        // 6.0.0.XXX ? 6.0.0.YYY
        if (buildNumber == null || other.getBuildNumber() == null) {
            // GA cases should have been sorted earlier, getting here is internal error
            throw new IllegalStateException("Internal RedHat middleware versions comparator error");
        }

        int thisIndex = ALLOWED_BUILD_VERSIONS.indexOf(build);
        int otherIndex = ALLOWED_BUILD_VERSIONS.indexOf(other.getBuild());
        if (thisIndex != otherIndex) {
            return thisIndex - otherIndex;
        }

        int difference = buildNumber - other.getBuildNumber();
        if (difference != 0) {
            return difference;
        }

        // same build numbers, no qualifiers
        if (qualifier == null && other.getQualifier() == null) {
            return 0;
        }

        // 6.0.0.XXX.Y > 6.0.0.XXX
        if (qualifier != null && other.getQualifier() == null) {
            return 1;
        }

        // 6.0.0.XXX < 6.0.0.XXX.Y
        if (qualifier == null && other.getQualifier() != null) {
            return -1;
        }

        return qualifier - other.getQualifier();
    }

    public int compareToString(final String version) {
        return compareTo(EapVersion.fromString(version));
    }

}
