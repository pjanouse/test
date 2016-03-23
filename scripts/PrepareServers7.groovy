import groovy.xml.XmlUtil

// TODO patch version should be checked when eap_version is not specified
// TODO add patched_eap_zip_url property - will be checked if eapZipUrl is not specified
/**
 * Prepares 4 jboss-eap-6.x servers.
 *
 * Needs property eap.zip.url or eap.version or patch.version to be defined (one of them is enough.
 * f.e. http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/jboss-eap-6.3.0.ER10.zip
 * f.e. 6.3.0.ER10
 * f.e. 6.2.3.CP.CR3
 *
 * if automatic modification of standalone-full-ha.xml, mgmt-groups|mgmt-users.properties is not good for you then
 * specify configuration.dir.url (TODO this is not yet implemented)
 *
 * For backward compatibility tests between EAP 6.x versions set properties (old servers will be in server1,server2 dirs):
 * eap.zip.url.old
 * eap.version.old
 *
 * Disable trace logging at server:
 * Trace logs can be disabled by -DdisableTraceLogs like:
 *      groovy -DEAP_VERSION=6.4.0.DR6 -DdisableTraceLogs PrepareServers.groovy
 *
 * How to install legacy extension (it'll just unzip it and will NOT make any configuration changes):
 *      set -DLEGACY_EXTENSION_URL=file:///home/mnovak/tmp/jboss-as-legacy-naming-dist-1.1.0.redhat-1.zip
 *
 */

public class PrepareServers7 {

    public static String eapDirName = "jboss-eap"
    public static String downloadedEAPZipFileName = 'jboss-eap.zip'
    public static String whereToUnzipEAPDirName = ''

    public static String eapZipUrl = getUniversalProperty('eap.zip.url')
    public static String patchVersion = getUniversalProperty('patch.version')
    public static String eapVersion = getUniversalProperty('eap.version')
    public static String configurationDirUrl = getUniversalProperty('configuration.dir.url')

    public static String eapZipUrlOld = getUniversalProperty('eap.zip.url.old')
    public static String eapVersionOld = getUniversalProperty('eap.version.old')
    public static String configurationDirUrlOld = getUniversalProperty('configuration.dir.url.old')

    public static String disableTraceLogs = getUniversalProperty('disable.trace.logs')

    // if legacy.extension.url is set then it will be installed to eap 6 server
    public static String legacyExtensionUrl = getUniversalProperty('legacy.extension.url')
    public static String whereToDownloadLegacyExtension = 'legacy-extension.zip'

    public PrepareServers7() {

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

    public void printProperties() {
        println "eapZipUrl = " + eapZipUrl
        println "patchVersion = " + patchVersion
        println "eapVersion = " + eapVersion
        println "configurationDirUrl = " + configurationDirUrl

        println "eapZipUrlOld  = " + eapZipUrlOld
        println "eapVersionOld = " + eapVersionOld
        println "configurationDirUrlOld = " + configurationDirUrlOld

        println "legacyExtensionUrl = " + legacyExtensionUrl

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
        locations.add("http://jenkins.mw.lab.eng.bos.redhat.com/hudson/view/EAP7/view/EAP7-Bits/job/eap-7x-testing-binaries/lastSuccessfulBuild/artifact")
        locations.add("http://download.eng.rdu2.redhat.com/released/JBEAP-7/" + eapVersion)
        locations.add("http://download.eng.rdu2.redhat.com/released/JBEAP-7/" + eapVersion + "/zip")

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

        if (urlString.startsWith("file:") && new File(urlString.replaceAll("file://", "")).exists()) {

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
    public String prepareServer(String eapZipUrl, String configurationDirUrl) {

        if (eapZipUrl == null || eapZipUrl == '') {
            throw new IllegalArgumentException("eapZipUrl cannot be empty or null")
        }

        cleanUp()

        // download eap zip to jboss-eap.zip
        downloadFile(eapZipUrl, downloadedEAPZipFileName)

        // unzip jboss-eap.zip -> jboss-eap
        unzip(downloadedEAPZipFileName, whereToUnzipEAPDirName)

        // rename everything with jboss-eap-* to jboss-eap
        renameEAPDir(eapDirName)

        // install legacy extension if legacyExtensionUrl is set
        if (legacyExtensionUrl != null && !''.equals(legacyExtensionUrl)) {
            installLegacyExtension();
        }

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

    public static void installLegacyExtension() {

        println "Legacy extension will be installed. Provided url to legacy extension is: " + legacyExtensionUrl

        downloadFile(legacyExtensionUrl, whereToDownloadLegacyExtension)

        unzip(whereToDownloadLegacyExtension, eapDirName)

    }

    /**
     * Clean ups everything except server{1..x} directories
     */
    public static void cleanUp() {
        AntBuilder ant = new AntBuilder();
        ant.delete(dir: eapDirName, failonerror: 'false')
        ant.delete(dir: downloadedEAPZipFileName, failonerror: 'false')
        ant.delete(failonerror: 'false', includeemptydirs: 'true') {
            fileset(dir: new File(".").absolutePath) {
                include(name: "jboss-eap-*")
                include(name: "*.txt")
                include(name: "*.zip")
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

    /**
     * if disableTraceLogs is set to some value then server then servers will not create trace logs
     * @param jbossHome
     */
    public static void modifyConfiguration(String jbossHome) {

        println "Modifying default configuration in server: " + jbossHome
        File tempFile = new File('tmp.txt')

        // modify standalone-full-ha.xml
//        File standaloneFile = new File(jbossHome + File.separator + 'standalone' + File.separator + 'configuration' + File.separator + 'standalone-full-ha.xml')
//        tempFile.withWriter {
//            w ->
//                standaloneFile.eachLine { line ->
//                    w << line.replaceAll('<journal-min-files>2</journal-min-files>', '<journal-min-files>2</journal-min-files>\n<security-enabled>false</security-enabled>')
//                            .replaceAll('<module-option name="password-stacking" value="useFirstPass"/>'
//                            , '<module-option name="password-stacking" value="useFirstPass"/>\n<module-option name="unauthenticatedIdentity" value="guest"/>')
//                            .replaceAll('<root-logger>',
//                            '<console-handler name="CONSOLE">\n' +
//                                    '                <level name="INFO"/>\n' +
//                                    '                <formatter>\n' +
//                                    '                    <pattern-formatter pattern="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n"/>\n' +
//                                    '                </formatter>\n' +
//                                    '            </console-handler>\n' +
//                                    '            <root-logger>\n')
//                            .replaceAll('<periodic-rotating-file-handler name="FILE" autoflush="true">', '<periodic-rotating-file-handler name="FILE" autoflush="true">\n' +
//                            '                <level name="INFO"/>\n')
//                            .replaceAll('<handler name="FILE"/>', '<handler name="FILE"/>\n' +
//                            '                    <handler name="CONSOLE"/>')
//                            .replaceAll('<formatter name="PATTERN">.*</formatter>', '')
//                            .replaceAll('<formatter>.*</formatter>', '<formatter>\n' +
//                            '                    <pattern-formatter pattern="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n"/>\n' +
//                            '                </formatter>') // for eap 6.1.x it's necessary to add pattern formatter
//                            .replaceAll('NIO', 'ASYNCIO')
//                            .replaceAll('jboss.bind.address.management:127.0.0.1', 'jboss.bind.address:127.0.0.1')
//                            .replaceAll('jboss.bind.address.unsecure:127.0.0.1', 'jboss.bind.address:127.0.0.1')
//                            .concat("\n")
//                }
//        }
//        copyFile(tempFile, standaloneFile)
//        // if disable trace logging is set anyhow then do not set trace server logs
//        if (disableTraceLogs == null) {
//            tempFile.withWriter {
//                w ->
//                    standaloneFile.eachLine { line ->
//                        w << line.replaceAll('<root-logger>',
//                                '            <periodic-rotating-file-handler name="FILE-TRACE" autoflush="true">\n' +
//                                        '                <level name="TRACE"/>\n' +
//                                        '                <formatter>\n' +
//                                        '                   <pattern-formatter pattern="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n"/>\n' +
//                                        '                </formatter>\n' +
//                                        '                <file relative-to="jboss.server.log.dir" path="server-trace.log"/>\n' +
//                                        '                <suffix value=".yyyy-MM-dd"/>\n' +
//                                        '                <append value="true"/>\n' +
//                                        '            </periodic-rotating-file-handler>\n' +
//                                        '            <root-logger>\n')
//
//                                .replaceAll('<handler name="FILE"/>', '<handler name="FILE"/>\n' +
//                                '                    <handler name="FILE-TRACE"/>\n')
//                                .replaceAll('<logger category="com.arjuna">\n' +
//                                '                <level name="WARN"/>',
//                                '<logger category="com.arjuna">\n' +
//                                        '                <level name="TRACE"/>')
//                                .replaceAll('<logger category="com.arjuna">',
//                                '<logger category="org.hornetq">\n' +
//                                        '                <level name="TRACE"/>\n' +
//                                        '            </logger>\n<logger category="com.arjuna">')
//                                .concat("\n")
//                    }
//            }
//            copyFile(tempFile, standaloneFile)
//        } else {
//
//            println "Trace logging will not be set for EAP servers."
//
//        }
        // STANDALONE PROFILE
        def standaloneDirectory = "${jbossHome}${File.separator}standalone${File.separator}configuration"
        def standaloneTemp = new File('standalone-tmp.txt')
        def standaloneFile = new File("${standaloneDirectory}${File.separator}standalone-full-ha.xml")
        def standaloneDocument = new XmlParser().parse(standaloneFile)
        def standaloneProfile = standaloneDocument.profile.get(0)

        disableSecurity(standaloneProfile)

        if (disableTraceLogs == null) {
            setupLogging(standaloneProfile, false)
        } else {
            setupLogging(standaloneProfile, true)
            print "Disabling trace logs"
        }

//        uncomment to enble DEBUG lvl
//        enableDebugConsle(standaloneProfile)

        setupSocketBingingGroup(standaloneDocument)
        parametrizeModCluster(standaloneDocument)

        new XmlNodePrinter(new IndentPrinter(new FileWriter(standaloneTemp))).print(standaloneDocument)
        copyFile(standaloneTemp, standaloneFile)

        // DOMAIN PROFILE
        def domainDirectory = "${jbossHome}${File.separator}domain${File.separator}configuration"

        // modify domain.xml
        def domainTemp = new File('domain-tmp.txt')
        def domainFile = new File("${domainDirectory}${File.separator}domain.xml")
        def domainDocument = new XmlParser().parse(domainFile)

        // copy original full-ha profile and full-ha-sockets 4 times and rename it to full-ha-N / full-ha-sockets-N
        def profiles = domainDocument.profiles.get(0)
        def originalProfile = profiles.profile.findAll { it.@name == 'full-ha' }.get(0)

        disableSecurity(originalProfile)
        enableDebugConsle(originalProfile)
        cloneProfiles(profiles, originalProfile)
        cloneSocketBindings(domainDocument.'socket-binding-groups'.get(0))
        cloneServerGroups(domainDocument.'server-groups'.get(0))

        new XmlNodePrinter(new IndentPrinter(new FileWriter(domainTemp))).print(domainDocument)
        copyFile(domainTemp, domainFile)

        // modify host.xml
        def hostTemp = new File('host-tmp.txt')
        def hostFile = new File("${domainDirectory}${File.separator}host.xml")
        def hostDocument = new XmlParser().parse(hostFile)

        updateInterfacesHostnames(hostDocument)

        def servers = hostDocument.servers.get(0)
        servers.server.each { servers.remove(it) }

        for (i in 1..4) {
            def newServer = servers.appendNode('server', [name: "server-${i}", group: "server-group-${i}", 'auto-start': 'false'])
            if (i != 1) {
                int portOffset = i * 10000
                newServer.appendNode('socket-bindings', ['port-offset': portOffset])
            }
        }

        new XmlNodePrinter(new IndentPrinter(new FileWriter(hostTemp))).print(hostDocument)
        copyFile(hostTemp, hostFile)

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

    private static void disableSecurity(Node profile) {
        Node messagingServer = profile.subsystem.'server'.get(0)
        // messagingServer.appendNode('security', [enabled:"false"])
        messagingServer.children().add(1, new Node(null, 'security', [enabled: "false"]))
        def journalType = messagingServer.get('journal')
        if (journalType != null && journalType.size() > 0) {
            messagingServer.remove(journalType)
        }
        messagingServer.children().add(1, new Node(null, 'journal', [type: "ASYNCIO", 'compact-min-files': '0', 'min-files': "10"]))
        //messagingServer.appendNode('journal ', [type:"ASYNCIO"])
        def securityDomainOther = profile.subsystem.'security-domains'.'security-domain'.find { it.@name == 'other' }
        def remotingSecurity = securityDomainOther.authentication.'login-module'.find { it.@code == 'Remoting' }
        remotingSecurity.appendNode('module-option', [name: 'unauthenticatedIdentity', value: 'guest'])
        Node remotingSubsystem = profile.subsystem.find {
            it.name().getNamespaceURI().startsWith('urn:jboss:domain:remoting:')
        }
        Node httpConnectorOld = remotingSubsystem.get('http-connector').get(0);
        remotingSubsystem.remove(httpConnectorOld);
        remotingSubsystem.appendNode('http-connector', [name: 'http-remoting-connector', 'connector-ref': 'default'])

    }

    private static void enableDebugConsle(Node profile) {
        def logging = profile.subsystem.find { it.name().getNamespaceURI().startsWith('urn:jboss:domain:logging:') }
        logging.'console-handler'.find { it.@name == 'CONSOLE' }.level.each { it.@name = 'DEBUG' }

        //consoleHandler.formatter.'named-formatter'.each{ consoleHandler.formatter.remove(it) }
        //def formatter = consoleHandler.appendNode('formatter')
        //formatter.appendNode('pattern-formatter', [pattern:'%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n'])
    }

    private static void parametrizeModCluster(Node standaloneDoc) {
        def socketBinding = standaloneDoc.'socket-binding-group'.'socket-binding'.find { it.@name == 'modcluster' }
        socketBinding.'@multicast-address' = '${jboss.default.multicast.address:224.0.1.105}'
    }

    private static void cloneProfiles(Node profiles, Node originalProfile) {
        for (i in 1..4) {
            // clone on Node is only in Groovy 2+
            // def newProfile = originalProfile.clone()
            def newProfile = new XmlParser().parseText(XmlUtil.serialize(originalProfile))
            newProfile.@name = "full-ha-${i}"
            profiles.append(newProfile)
        }
        profiles.remove(originalProfile)
    }

    private static void cloneSocketBindings(Node sockets) {
        def originalSockets = sockets.'socket-binding-group'.find { it.@name == 'full-ha-sockets' }
        for (i in 1..4) {
            // clone on Node is only in Groovy 2+
            // def newSockets = originalSockets.clone()
            def newSockets = new XmlParser().parseText(XmlUtil.serialize(originalSockets))
            newSockets.@name = "full-ha-sockets-${i}"
            sockets.append(newSockets)
        }
        sockets.remove(originalSockets)
    }

    private static void cloneServerGroups(Node serverGroups) {
        serverGroups.'server-group'.each { serverGroups.remove(it) }

        for (i in 1..4) {
            def newServerGroup = serverGroups.appendNode('server-group', [name: "server-group-${i}", profile: "full-ha-${i}"])
            def jvm = newServerGroup.appendNode('jvm', [name: 'default'])
            jvm.appendNode('heap', [size: '64m', 'max-size': '788m'])
            jvm.appendNode('permgen', ['max-size': '256m'])
            newServerGroup.appendNode('socket-binding-group', [ref: "full-ha-sockets-${i}"])
        }
    }

    private static void updateInterfacesHostnames(Node host) {
        def interfaces = host.interfaces.get(0)
        def addressList = interfaces.interface.collect { it.'inet-address'.get(0) }

        for (address in addressList) {
            if (address.@value.startsWith('${jboss.bind.address')) {
                address.@value = '${jboss.bind.address:127.0.0.1}'
            }
        }
    }

    private static void copyDomainXml(File config, File target) {
        target.withWriter { w ->
            config.eachLine { line ->
                w << line
                        .replaceAll('jboss.bind.address.management:127.0.0.1', 'jboss.bind.address:127.0.0.1')
                        .replaceAll('jboss.bind.address.unsecure:127.0.0.1', 'jboss.bind.address:127.0.0.1')
                        .concat("\n")
            }
        }
    }

    private static void setupLogging(Node profile, boolean dissableTrace) {
        Node loggingSubsystem = profile.subsystem.find {
            it.name().getNamespaceURI().startsWith('urn:jboss:domain:logging:')
        }
        if (!dissableTrace) {
            setupTraceFileHandler(loggingSubsystem)
            setupActivemqLogger(loggingSubsystem)
            setupRootLogger(loggingSubsystem)
        }
        setupConsoleHandler(loggingSubsystem)
        setupFileHandler(loggingSubsystem)

    }

    private static void setupConsoleHandler(Node loggingSubsystem) {

        def isExistingConsoleHandler = loggingSubsystem.'console-handler'.find { it.@name == 'CONSOLE' }

        if (!isExistingConsoleHandler) {
            Node consoleHandler = new Node(null, 'console-handler', [name: "CONSOLE"])
            consoleHandler.appendNode('level', [name: "INFO"])
            Node formaterParent = new Node(null, 'formatter')
            Node formatterNew = new Node(null, 'pattern-formatter', [pattern: '%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n'])
            consoleHandler.append(formaterParent)
            formaterParent.append(formatterNew)
            loggingSubsystem.append(consoleHandler)
        } else {
            Node consoleHandler = loggingSubsystem.'console-handler'.get(0)
            consoleHandler.remove(consoleHandler.level.get(0))
            consoleHandler.appendNode('level', [name: "INFO"])
            Node formatterOld = consoleHandler.formatter.'named-formatter'.get(0)
            Node formatterNew = new Node(null, 'pattern-formatter', [pattern: '%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n'])
            Node parent = formatterOld.parent();
            parent.remove(formatterOld);
            parent.append(formatterNew);
        }

    }

    private static void setupFileHandler(Node loggingSubsystem) {
        Node fileHandler = loggingSubsystem.'periodic-rotating-file-handler'.find { it.@name == 'FILE' }
        fileHandler.appendNode('level', [name: "INFO"])
        Node formatterOld = fileHandler.formatter.'named-formatter'.get(0)
        Node formatterNew = new Node(null, 'pattern-formatter', [pattern: '%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n'])
        Node parent = formatterOld.parent();
        parent.remove(formatterOld);
        parent.append(formatterNew);
    }

    private static void setupTraceFileHandler(Node loggingSubsystem) {
        Node fileHandlerTrace = new Node(loggingSubsystem, 'size-rotating-file-handler', [name: "FILE-TRACE", autoflush: "true"])
        Node formater = new Node(fileHandlerTrace, 'formatter')
        formater.appendNode('pattern-formatter', [pattern: "%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n"])
        fileHandlerTrace.appendNode('level', [name: "TRACE"])
        fileHandlerTrace.appendNode('rotate-size', [value: "500M"])
        fileHandlerTrace.appendNode('max-backup-index', [value: "500"])
        fileHandlerTrace.appendNode('file', ['relative-to': "jboss.server.log.dir", path: "server-trace.log"])
        fileHandlerTrace.appendNode('append', [value: "true"])
    }

    private static void setupActivemqLogger(Node loggingSubsystem) {
        Node activeMQOldLogger = loggingSubsystem.logger.find { it.@category == 'org.apache.activemq' }
        Node activeMQNewLogger = new Node(null, 'logger', [category: "org.apache.activemq"])
        activeMQNewLogger.appendNode('level', [name: "TRACE"])
        if (activeMQOldLogger != null) {
            Node parent = activeMQOldLogger.parent();
            parent.remove(activeMQOldLogger);
            parent.append(activeMQNewLogger);

        } else {
            loggingSubsystem.append(activeMQNewLogger)
        }

    }

    private static void setupRootLogger(Node loggingSubsystem) {
        Node handlers = loggingSubsystem.'root-logger'.handlers.get(0)
        handlers.appendNode('handler', [name: "FILE-TRACE"])

        def isExistingConsoleHandler = handlers.handler.find { it.@name == 'CONSOLE' }
        if(!isExistingConsoleHandler) handlers.appendNode('handler', [name: "CONSOLE"])
    }

    private static void setupSocketBingingGroup(Node document) {
        Node socketBinging = document.'socket-binding-group'.get(0)
        socketBinging.appendNode('socket-binding', [name: "messaging-group", port: "0", 'multicast-address': '${jboss.messaging.group.address:231.7.7.7}',
                                                    'multicast-port': '${jboss.messaging.group.port:9876}'])

    }

    public static void main(String[] args) {
        //Properties prop = System.getProperties()
        //prop.setProperty("eap.zip.url", 'http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/jboss-eap-6.3.0.ER10.zip')
//        prop.setProperty("eap.version", '6.3.0.ER10')
//        System.setProperties(prop)
//        eapVersion = "6.0.0"
//        eapVersionOld = "6.2.0"
        //def eapZipUrl = 'http://download.eng.rdu2.redhat.com/devel/candidates/JBEAP/JBEAP-6.3.0.ER10/jboss-eap-6.3.0.ER10.zip'
//        eapZipUrl = ' file:///home/mnovak/tmp/jboss-eap-6.3.0.ER10.zip'
        PrepareServers7 p = new PrepareServers7()
        p.prepareServer(eapZipUrl, configurationDirUrl)
        p.copyServers(4)
        if (eapZipUrlOld != null && eapZipUrlOld != '') {
            p.prepareServer(eapZipUrlOld, configurationDirUrlOld);
            p.copyServers(2)
        }
        cleanUp();
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
