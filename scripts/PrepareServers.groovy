/**
 * Prepares 4 jboss-eap-6.x servers.
 *
 * Needs property eap.zip.url or eap.version or patch.version to be defined (one of them is enough.
 * f.e. http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/jboss-eap-6.3.0.ER10.zip
 * f.e. 6.3.0.ER10
 * f.e. 6.2.3.CP.CR3
 *
 * if natives are at common location then the is optional property natives.url which defines them.
 * f.e. http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/natives/jboss-eap-native-6.3.0.ER10-RHEL6-x86_64.zip
 *
 * if automatic modification of standalone-full-ha.xml, mgmt-groups|mgmt-users.properties is not good for you then
 * specify configuration.dir.url (TODO this is not yet implemented)
 *
 * For backward compatibility tests between EAP 6.x versions set properties (old servers will be in server1,server2 dirs):
 * eap.zip.url.old
 * eap.version.old
 * optionally natives.url.old, configuration.dir.url.old
 *
 */

public class PrepareServers {

    public static String eapDirName = "jboss-eap"
    public static String downloadedEAPZipFileName = 'jboss-eap.zip'
    public static String whereToUnzipEAPDirName = ''
    public static String downloadedNativeZipFileName = "jboss-eap-native.zip"
    // this is the same as natives must be unzipped into JBOSS_HOME
    public static String whereToUnzipNativeDirName = whereToUnzipEAPDirName

    public static String eapZipUrl = getUniversalProperty('eap.zip.url')
    public static String patchVersion = getUniversalProperty('patch.version')
    public static String eapVersion = getUniversalProperty('eap.version')
    public static String nativesUrl = getUniversalProperty('natives.url')
    public static String configurationDirUrl = getUniversalProperty('configuration.dir.url')

    public static String eapZipUrlOld = getUniversalProperty('eap.zip.url.old')
    public static String eapVersionOld = getUniversalProperty('eap.version.old')
    public static String nativesUrlOld = getUniversalProperty('natives.url.old')
    public static String configurationDirUrlOld = getUniversalProperty('configuration.dir.url.old')

    public PrepareServers() {

        if (eapZipUrl == null || eapZipUrl == '' && patchVersion != null) {
            eapZipUrl = patchVersion
        }

        // if eapZipUrl is not defined but eapVersion is then build eapZipUrl from eapVersion
        if ((eapZipUrl == null || eapZipUrl == '') && (eapVersion != null && eapVersion != '')) {
            eapZipUrl = buildEapZipUrlFromEapVersion(eapVersion)
        }

        if ((eapZipUrlOld == null || eapZipUrlOld == '') && (eapVersionOld != null && eapVersionOld != '')) {
            eapZipUrlOld = buildEapZipUrlFromEapVersion(eapVersionOld)
        }

        printProperties()

    }

    public void printProperties()   {
        println "eapZipUrl = " +  eap.zip.url
        println "patchVersion = " + patch.version
        println "eapVersion = " +  eap.version
        println "nativesUrl = " +  natives.url
        println "configurationDirUrl = " + configuration.dir.url

        println "eapZipUrlOld  = " +  eap.zip.url.old
        println "eapVersionOld = " +  eap.version.old
        println "nativesUrlOld = " +  natives.url.old
        println "configurationDirUrlOld = " + configuration.dir.url.old
    }

    /**
     * Try to build url to EAP zip.
     * @param eapVersion
     * @return url to EAP zip
     */
    public static String buildEapZipUrlFromEapVersion(String eapVersion) {

        // paths to default locations of EAP zip
        List<String> locations = new ArrayList<String>()
        locations.add("file:///home/hudson/static_build_env/eap/" + eapVersion)
        locations.add("http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-" + eapVersion)
        locations.add("http://download.eng.rdu2.redhat.com/released/JBEAP-6/" + eapVersion)
        locations.add("http://download.eng.rdu2.redhat.com/released/JBEAP-6/" + eapVersion + "/zip")

        String eapZipFileName = "/jboss-eap-" + eapVersion + ".zip" // jboss-eap-6.3.0.ER10.zip

        for (String location : locations) {
            if (validateThatUrlExists(location + eapZipFileName)) {
                return location + eapZipFileName
            }
        }
        throw new Exception("Specify correct eap.version or eap.zip.url.")
    }

    public static boolean validateThatUrlExists(String urlString) {
        println "Validating Url " + urlString

        if (urlString.startsWith("file:") && new File(urlString).exists()) {

            return true;

        } else if (urlString.startsWith("http:")) {

            final URL url = new URL(urlString);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("HEAD");
            if (huc.getResponseCode() == 200) {
                println "Url " + urlString + " exists."
                return true
            }
        }

        println "Url " + urlString + " does not exists."
        return false
    }

/**
 * Prepares server in current directory
 *
 * @return absolute path to EAP server
 */
    public String prepareServer(String eapZipUrl, String nativesUrl, String configurationDirUrl) {

        if (eapZipUrl == null || eapZipUrl == '') {
            throw new IllegalArgumentException("eapZipUrl cannot be empty or null")
        }

        cleanUp()

        // download eap zip to jboss-eap.zip
        downloadFile(eapZipUrl, downloadedEAPZipFileName)

        // unzip jboss-eap.zip -> jboss-eap
        unzip(downloadedEAPZipFileName, whereToUnzipEAPDirName)

        // download native zip to jboss-eap-native.zip //////////////
        if (new Platform().isRHEL()) {
            if (nativesUrl == null || nativesUrl == '') { // get zip from eapZipUrl
                downloadNativeZipBasedOnEapZipUrl(eapZipUrl)
            } else if (nativesUrl.endsWith('.zip')) {     // else if zip then download it
                downloadFile(nativesUrl, downloadedNativeZipFileName)
            } else { //else if it's directory then get platform and download the correct zip     todo
                throw new UnsupportedOperationException("natives url cannot be a directory, this was not yet implemented")
            }
            // unzip jboss-eap-native.zip over jboss-eap
            unzip(downloadedNativeZipFileName, whereToUnzipNativeDirName)
        }
        ///////////////////////////////////////////////////////

        // rename everything with jboss-eap-* to jboss-eap
        renameEAPDir(eapDirName)

        // modify configuration
        // if configuration file url specified then download it and copy to standalone/configuration
        if (configurationDirUrl != null && configurationDirUrl != '') {
            throw new UnsupportedOperationException("You have specified configuration directory. This was not implemented yet.")
            // todo
        } else {
            modifyConfiguration(new File(eapDirName).absolutePath)
        }

        return new File(eapDirName).absolutePath
    }

    public static void cleanUp() {
        AntBuilder ant = new AntBuilder();
        ant.delete(dir: eapDirName, failonerror: 'false')
        ant.delete(dir: downloadedEAPZipFileName, failonerror: 'false')
        ant.delete(dir: downloadedNativeZipFileName, failonerror: 'false')
        ant.delete(failonerror: 'false', includeemptydirs: 'true') {
            fileset(dir: new File(".").absolutePath) {
                include(name: "jboss-eap-*")
            }
        }
    }

/** Create server{1..4} and copy jboss-eap into them
 *
 * @param numberOfCopies how many times to copy the server
 */
    public void copyServers(int numberOfCopies) {
        AntBuilder ant = new AntBuilder()
        String serverDir = 'server'

        for (i in 1..numberOfCopies) {
            ant.delete(dir: serverDir + i, failonerror: 'false')
            //ant.mkdir(dir: serverDir + i)
            ant.copy(todir: serverDir + i) {
                fileset(dir: new File(".").absolutePath) {
                    include(name: eapDirName + "/**")
                }
            }
        }
    }

/**
 * This is expecting our QA Lab conventions that directory "natives" (so "natives/jboss-eap-native-*.zip")
 * is in the same directory as eap zip
 */
    public static void downloadNativeZipBasedOnEapZipUrl(String eapZipUrl) {

        println "Trying to get native zip based on eap.zip.url: " + eapZipUrl

        // parse eapZipUrl and get path to baseDir
        String eapZipFileName = eapZipUrl.tokenize("/")[-1]
        String baseDir = eapZipUrl.replaceAll(eapZipFileName, "")

        println " - base dir is: " + baseDir + ", eap zip file name is: " + eapZipFileName

        // build path to native zip file - jboss-eap-6.3.0.ER10.zip
        StringBuilder nativeFileNameBuilder = new StringBuilder(eapZipFileName);
        int indexWhereToPlacePlatform = nativeFileNameBuilder.indexOf(".zip")
        //println "index is: " + indexWhereToPlacePlatform
        nativeFileNameBuilder.insert(indexWhereToPlacePlatform, getPlatformVersion())
        nativeFileNameBuilder.insert(10, "native-")

        String nativeFileName = nativeFileNameBuilder.toString()

        // download this zip
        try {
            downloadFile(baseDir + "natives" + "/" + nativeFileName, downloadedNativeZipFileName)
        } catch (FileNotFoundException ex) {
            ex.printStackTrace()
            println("Try native instead of natives.")
            downloadFile(baseDir + "native" + "/" + nativeFileName, downloadedNativeZipFileName)
        }

    }

    public static String getPlatformVersion() {
        def fn = "-"
        def p = new Platform()

        if (p.isRHEL()) {
            if (p.isRHEL4()) fn += 'RHEL4-'
            else if (p.isRHEL5()) fn += 'RHEL5-'
            else if (p.isRHEL7()) fn += 'RHEL7-'
            else fn += 'RHEL6-'

            if (p.isX86()) {
                fn += 'i386'
            } else if (p.isX64()) {
                fn += 'x86_64'
            } else {
                fn += 'ppc64'
            }

        }
        print "Native platform version is " + fn
        return fn
    }

    public void renameEAPDir(String directoryWithNewEAPDir) {

        String originalEAPDir = null;

        for (File f : new File('.').listFiles()) {
            if (f.isDirectory() && f.getName().contains("jboss-eap-")) {
                originalEAPDir = f.getName()
                break
            }
        }

        println "Renaming directory " + originalEAPDir + " to " + directoryWithNewEAPDir

        AntBuilder ant = new AntBuilder()
        // find everything with jboss-eap-* and rename to jboss-eap
        ant.move(overwrite: true, todir: new File(directoryWithNewEAPDir).absolutePath) {
            fileset(dir: new File(originalEAPDir).absolutePath) {
                include(name: "**/*")
            }
        }

    }

    public static void modifyConfiguration(String jbossHome) {

        println "Modifying default configuration in server: " + jbossHome
        File tempFile = new File('tmp.txt')

        // modify standalone-full-ha.xml
        File standaloneFile = new File(jbossHome + File.separator + 'standalone' + File.separator + 'configuration' + File.separator + 'standalone-full-ha.xml')
        tempFile.withWriter {
            w ->
                standaloneFile.eachLine { line ->
                    w << line.replaceAll('<journal-min-files>2</journal-min-files>', '<journal-min-files>2</journal-min-files>\n<security-enabled>false</security-enabled>')
                            .replaceAll('<module-option name="password-stacking" value="useFirstPass"/>'
                            , '<module-option name="password-stacking" value="useFirstPass"/>\n<module-option name="unauthenticatedIdentity" value="guest"/>')
                            .replaceAll('<root-logger>',
                            '            <console-handler name="CONSOLE">\n' +
                                    '                <level name="DEBUG"/>\n' +
                                    '                <formatter>\n' +
                                    '                    <pattern-formatter pattern="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n"/>\n' +
                                    '                </formatter>\n' +
                                    '            </console-handler>\n' +
                                    '<root-logger>\n')
                            .replaceAll('<handler name="FILE"/>', '<handler name="FILE"/>\n<handler name="CONSOLE"/>')
                            .replaceAll('NIO', 'ASYNCIO')
                            .replaceAll('jboss.bind.address.management:127.0.0.1', 'jboss.bind.address:127.0.0.1')
                            .replaceAll('jboss.bind.address.unsecure:127.0.0.1', 'jboss.bind.address:127.0.0.1')
                            .concat("\n")
                }
        }
        copyFile(tempFile, standaloneFile)

        // modify mgmt-groups.properties and mgmt-users.properties
        File managementGroupsFile = new File(jbossHome + File.separator + 'standalone' + File.separator + 'configuration' + File.separator + 'mgmt-groups.properties')
        if (managementGroupsFile.exists()) {
            tempFile.withWriter {
                w ->
                    managementGroupsFile.eachLine { line ->
                        w << line.replaceAll('#admin=PowerUser,BillingAdmin,', 'admin=admin')
                    }
            }
            copyFile(tempFile, managementGroupsFile)
        }

        File managementUsersFile = new File(jbossHome + File.separator + 'standalone' + File.separator + 'configuration' + File.separator + 'mgmt-users.properties')
        if (managementUsersFile.exists()) {

            tempFile.withWriter {
                w ->
                    managementUsersFile.eachLine { line ->
                        w << line.replaceAll('#admin=2a0923285184943425d1f53ddd58ec7a', 'admin=873c8bce2336514bd5253059f8b2a167')
                                .concat("\n")
                    }
            }
            copyFile(tempFile, managementUsersFile)
        }
    }

    public static void downloadFile(String address, String outputFileName) {
        println "Starting download " + address + " to " + outputFileName
        def file = new FileOutputStream(outputFileName)
        def out = new BufferedOutputStream(file)
        out << new URL(address).openStream()
        out.close()
        println "Downloaded file " + address + " to " + outputFileName
    }

    public static void unzip(String srcFile, String dstFile) {
        println "Unzip " + srcFile + " into '" + dstFile + "'"
        AntBuilder ant = new AntBuilder()
        ant.unzip(src: srcFile, dest: dstFile)
        println srcFile + " was unzipped into '" + dstFile + "'"
    }

/**
 * Get property from system properties and environment variables.
 * Input is of form my.great.property. Following properties
 * will be tested (the first not null value will be returned):
 * * 'my.great.property' in system properties
 * * 'my.great.property' in environment variables
 * * 'my_great_property' in system properties
 * * 'my_great_property' in environment variables
 * * 'MY_GREAT_PROPERTY' in environment variables
 * * 'myGreatProperty'   in system properties
 */
    public static String getUniversalProperty(String propName) {
        String propName2 = propName.replaceAll('\\.', '_')
        String propName3 = propName.replaceAll('\\.', '_').toUpperCase()
        String propName4 = null
        String[] sp = propName.split('\\.')
        if (sp.length > 1) {
            propName4 = sp[0] + sp[1..-1].collect { it.capitalize() }.join()
        }

        String val = System.getProperty(propName) ?: System.getenv(propName)
        if (!val) val = System.getProperty(propName2) ?: System.getenv(propName2)
        if (!val) val = System.getProperty(propName3) ?: System.getenv(propName3)
        if (!val && propName4) val = System.getProperty(propName4) ?: System.getenv(propName4)
        return val
    }

    public static copyFile(File sourceFile, File toFile) {
        println "Copying file " + sourceFile.getAbsoluteFile() + " to file " + toFile.getAbsoluteFile()
        AntBuilder ant = new AntBuilder()
        ant.copy(overwrite: true, file: sourceFile.absolutePath,
                tofile: toFile.absolutePath)
    }

    public static void main(String[] args) {
        //Properties prop = System.getProperties()
        //prop.setProperty("eap.zip.url", 'http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/jboss-eap-6.3.0.ER10.zip')
//        prop.setProperty("eap.version", '6.3.0.ER10')
//        System.setProperties(prop)
//        eapVersion = "6.0.0"
//        eapVersionOld = "6.2.0"
        //def eapZipUrl = 'http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/jboss-eap-6.3.0.ER10.zip'
        //def eapZipUrl = ' file:///home/mnovak/tmp/jboss-eap-6.3.0.ER10.zip'
        //def nativesUrl = 'http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/natives/jboss-eap-native-6.3.0.ER10-RHEL6-x86_64.zip'
        PrepareServers p = new PrepareServers()
        p.prepareServer(eapZipUrl, nativesUrl, configurationDirUrl)
        p.copyServers(4)
        if (eapZipUrlOld != null && eapZipUrlOld != '') {
            p.prepareServer(eapZipUrlOld, nativesUrlOld, configurationDirUrlOld);
            p.copyServers(2)
        }
    }

}

class Platform {

    def osName
    def osArch
    def osVersion
    def archModel

    def Platform(osName, osArch, osVersion) {
        this.osName = osName
        this.osArch = osArch
        this.osVersion = osVersion
        this.archModel = isX86() ? '32' : '64'
    }

    def Platform() {
        this.osName = System.getProperty('os.name')
        this.osArch = System.getProperty('os.arch')
        this.osVersion = System.getProperty('os.version')
        this.archModel = System.getProperty('sun.arch.data.model')
    }

    def String toString() {
        "${osName} ${osVersion} ${osArch}"
    }

    def isWindows() {
        return (osName ==~ /.*[Ww]indows.*/)
    }

    def isRHEL() {
        return (osName ==~ /[Ll]inux.*/)
    }

    def isSolaris() {
        return (osName == 'SunOS')
    }

    def isHP() {
        return (osName == 'HP-UX')
    }

    def isX64() {
        return (osArch == 'amd64')
    }

    def isX86() {
        return (osArch == 'x386') || (osArch == 'x86') || (osArch == 'i386')
    }

    def isSparc() {
        return (osArch == 'sparc')
    }

    def isSparc64() {
        return (osArch == 'sparc' && archModel == '64')
    }

    def isRHEL4() {
        return isRHEL() && (osVersion ==~ /.*EL[^56][a-zA-Z]*/)
    }

    def isRHEL5() {
        return isRHEL() && (osVersion ==~ /.*el5.*/)
    }

    def isRHEL6() {
        return isRHEL() && (osVersion ==~ /.*el6.*/)
    }

    def isRHEL7() {
        return isRHEL() && (osVersion ==~ /.*el7.*/)
    }

    def isSolaris11() {
        return isSolaris() && (osVersion ==~ /5\.11/)
    }

    def isSolaris10() {
        return isSolaris() && (osVersion ==~ /5\.10/)
    }

    def isSolaris9() {
        return isSolaris() && (osVersion ==~ /5\.9/)
    }

    def isHP11() {
        return isHP() && (osName ==~ /.*11.*/)
    }

    def getScriptSuffix() {
        return isWindows() ? 'bat' : 'sh'
    }

}