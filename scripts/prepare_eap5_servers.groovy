package hornetq.groovy
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 *
 * @author mnovak
 *
 * RUN
 *   groovy -Ddistro.url.old="url" -Ddistro.url.new="url" -Dhornetq.installer.url.new="url" -Dhornetq.installer.url.old="url" -Dnatives.url.old="url" -Dnatives.url.new="url"
 *   
 *   Creates directories:
 *   new:
 *   server1/jboss-eap-5.1
 *   server2/jboss-eap-5.1
 *   server3/jboss-eap-5.1
 *   server4/jboss-eap-5.1
 *   
 *   old:
 *   server1/jboss-eap-5.1
 *   server2/jboss-eap-5.1
 *   server3/jboss-eap-5.1
 *   server4/jboss-eap-5.1
 *   
 * ADDITIONAL PARAMETRES
 *   -Ddev.debug
 *
 */

def __env = System.getenv()
println '============= Env vars ============='
__env.each { k, v ->
    println "$k=$v"
}

def __sys = System.properties
println '============= Sys props ============='
__sys.each { k, v ->
    println "$k=$v"
}


// ------- DEBUG PARAMS --------------------------------------
def debug = (getUniversalProperty("dev.debug") == 'true') // especialy for local testing, see distroStore
def downloadEap = true
// ------- DEBUG PARAMS --------------------------------------


// ------- INITIALIZATION --------------------------------------
def ant = new AntBuilder()
def basedir = getUniversalProperty("workspace")

def osName = System.getProperty("os.name")
def isWindows = (osName ==~ /.*[Ww]indows.*/)
def scriptSuffix = isWindows?'bat':'sh'
def sep = File.separator

def location = 'eap.temp.dir'
def rootDir = ''


// ------- INITIALIZATION --------------------------------------


// ------- INPUT PARAMS --------------------------------------
def distroUrlOld = getUniversalProperty("distro.url.old", '') 
def distroUrlNew = getUniversalProperty("distro.url.new", '') 
def nativesUrlOld = getUniversalProperty("natives.url.old", '') 
def nativesUrlNew = getUniversalProperty("natives.url.new", '') 
def hornetqInstallerUrlNew = getUniversalProperty("hornetq.installer.url.new", '') 
def hornetqInstallerUrlOld = getUniversalProperty("hornetq.installer.url.old", '') 


println "debug: " + debug
println "distroUrlNew: " + distroUrlNew
println "distroUrlOld: " + distroUrlOld
println "nativesUrlOld: " + nativesUrlOld
println "nativesUrlNew: " + nativesUrlNew
println "hornetqInstallerUrlNew: " + hornetqInstallerUrlNew
println "hornetqInstallerUrlOld: " + hornetqInstallerUrlOld

if (downloadEap) {
   
    println "**** DOWNLOADING EAP ****"
    
    ant.delete(dir: new File(basedir, 'new').absolutePath, failonerror: 'false')
    ant.delete(dir: new File(basedir, 'old').absolutePath, failonerror: 'false')
    
    def eapDestNameNew
    ant.delete(dir: location)
    if (distroUrlNew != '') {
        fileName = distroUrlNew.tokenize("/")[-1]
        println fileName
        def file = new FileOutputStream(fileName)
        def out = new BufferedOutputStream(file)
        out << new URL(distroUrlNew).openStream()
        out.close()
        eapDestNameNew = fileName
    } else {
        throw new Exception('EAP new distribution definition error.')
    }
    
    // unzip eap to "new" directory
    def newDir = new File(basedir,"new").absolutePath
    ant.mkdir(dir: newDir)
    ant.unzip(src: new File(basedir, eapDestNameNew).absolutePath, dest: new File(newDir).absolutePath)
    
    // get hornetq installer
    def eapHornetQInstallerDestNameNew
    if (hornetqInstallerUrlNew != '') {
        fileName = hornetqInstallerUrlNew.tokenize("/")[-1]
        println fileName
        def file = new FileOutputStream(fileName)
        def out = new BufferedOutputStream(file)
        out << new URL(hornetqInstallerUrlNew).openStream()
        out.close()
        eapHornetQInstallerDestNameNew = fileName
    } else {
        throw new Exception('EAP new hornetq distribution definition error.')
    }
    // unzip hq installer
    ant.unzip(src: new File(basedir, eapHornetQInstallerDestNameNew).absolutePath, dest: new File(newDir).absolutePath)

    def distributionDirNew = "jboss-eap-5.1"
    if (hornetqInstallerUrlNew.contains("5.2")) {
        distributionDirNew = "jboss-eap-5.2"
    } 
    if (hornetqInstallerUrlNew.contains("5.3")) {
        distributionDirNew = "jboss-eap-5.3"
    }

    // enable guest user
    ant.replaceregexp(file: new File(newDir + sep + distributionDirNew + sep + "jboss-as" + sep +
        "extras" + sep + "hornetq" + sep + "build.xml").absolutePath, match: "#guest", replace: "guest", byline: false)
    
    // install hornetq
    // go to jboss_home/extras/hornetq/ and run ant
    def processBuilder=new ProcessBuilder("ant")
    processBuilder.redirectErrorStream(true)
    processBuilder.directory(new File(newDir + sep + distributionDirNew + sep + "jboss-as" + sep +
        "extras" + sep + "hornetq"))  
    def process = processBuilder.start()
    process.waitFor()  
    // Obtain status and output
    println "return code: ${ process.exitValue()}"
    println "stderr: ${process.err.text}"
    println "stdout: ${process.in.text}" // *out* from the external program is *in* for groovy
    
    // download and put new natives over it
    def eapNativesDestNameNew
    if (nativesUrlNew != '') {
        fileName = nativesUrlNew.tokenize("/")[-1]
        println fileName
        def file = new FileOutputStream(fileName)
        def out = new BufferedOutputStream(file)
        out << new URL(nativesUrlNew).openStream()
        out.close()
        eapNativesDestNameNew = fileName
        // unzip new natives to "new/jboss-eap-5.x"
        ant.unzip(src: new File(basedir, eapNativesDestNameNew).absolutePath, dest: new File(newDir).absolutePath)
    } else {
        println "Natives new not defined. This is ok when on windows or you don't want it."
    }
    
    // copy production profile to default profile
    def productionDir = new File(newDir + sep + distributionDirNew + sep + "jboss-as" + sep +
        "server" + sep + "production").absolutePath
    def defaultDir = new File(newDir + sep + distributionDirNew + sep + "jboss-as" + sep +
        "server" + sep + "default").absolutePath
    ant.delete(dir: new File(defaultDir), failonerror: 'false')
    ant.mkdir(dir: defaultDir)
    ant.copy(todir: defaultDir) {
        fileset(dir : productionDir) {
            include(name:"**/*")
        }
    }
    
    // create server[1..4] directories  and copy eap to them
    def serverDir = new File(newDir, 'server').absolutePath
    for ( i in 1..4 ) {
        ant.delete(dir: serverDir + i, failonerror: 'false')
        ant.mkdir(dir: serverDir + i)
        def serverNormalizedRoot = new File(serverDir + i, 'jboss-eap-5.x').absolutePath
        ant.copy(todir: serverNormalizedRoot) {
            fileset(dir: newDir + sep + distributionDirNew) {
                include(name: "**/*")
            }
        }
    }

    // delete leftover directories
    ant.delete(dir: new File(newDir, 'jboss-eap-5.1').absolutePath, failonerror: 'false')
    ant.delete(dir: new File(newDir, 'jboss-eap-5.2').absolutePath, failonerror: 'false')
    ant.delete(dir: new File(newDir, 'jboss-eap-5.3').absolutePath, failonerror: 'false')
    ant.delete(dir: new File(newDir, 'jboss-ep-5.1').absolutePath, failonerror: 'false')
    ant.delete(dir: new File(newDir, 'jboss-ep-5.2').absolutePath, failonerror: 'false')
    ant.delete(dir: new File(newDir, 'jboss-ep-5.3').absolutePath, failonerror: 'false')
    
    def eapDestNameOld
    ant.delete(dir: location)
    if (distroUrlOld != '') {
        fileName = distroUrlOld.tokenize("/")[-1]
        println fileName
        def file = new FileOutputStream(fileName)
        def out = new BufferedOutputStream(file)
        out << new URL(distroUrlOld).openStream()
        out.close()
        eapDestNameOld = fileName
        
        def oldDir = new File(basedir,"old").absolutePath
        ant.mkdir(dir: oldDir)
        ant.unzip(src: new File(basedir, eapDestNameOld).absolutePath, dest: new File(oldDir).absolutePath)
        
        // get hornetq installer
        def eapHornetQInstallerDestNameOld
        if (hornetqInstallerUrlOld != '') {
            fileName = hornetqInstallerUrlOld.tokenize("/")[-1]
            println fileName
            file = new FileOutputStream(fileName)
            out = new BufferedOutputStream(file)
            out << new URL(hornetqInstallerUrlOld).openStream()
            out.close()
            eapHornetQInstallerDestNameOld = fileName
        } else {
            throw new Exception('EAP new hornetq distribution definition error.')
        }
        // unzip hq installer
        ant.unzip(src: new File(basedir, eapHornetQInstallerDestNameOld).absolutePath, dest: new File(oldDir).absolutePath)
        
        def distributionDirOld = "jboss-eap-5.1"
        if (hornetqInstallerUrlOld.contains("5.2")) {
            distributionDirOld = "jboss-eap-5.2"
        } 
        if (hornetqInstallerUrlOld.contains("5.3")) {
            distributionDirOld = "jboss-eap-5.3"
        } 

        // allow guest user
        ant.replaceregexp(file: new File(oldDir + sep + distributionDirOld + sep + "jboss-as" + sep +
        "extras" + sep + "hornetq" + sep + "build.xml").absolutePath, match: "#guest", replace: "guest", byline: false)
        
        // install hornetq
        // go to jboss_home/extras/hornetq/ and run ant
        def processBuilder2=new ProcessBuilder("ant")
        processBuilder2.redirectErrorStream(true)
        processBuilder2.directory(new File(oldDir + sep + distributionDirOld + sep + "jboss-as" + sep +
        "extras" + sep + "hornetq"))  
        def process2 = processBuilder2.start()
        process2.waitFor()  
        // Obtain status and output
        println "return code: ${process2.exitValue()}"
        println "stderr: ${process2.err.text}"
        println "stdout: ${process2.in.text}" // *out* from the external program is *in* for groovy
        
        // download and put new natives over it
        def eapNativesDestNameOld
        if (nativesUrlOld != '') {
            fileName = nativesUrlOld.tokenize("/")[-1]
            println fileName
            file = new FileOutputStream(fileName)
            out = new BufferedOutputStream(file)
            out << new URL(nativesUrlOld).openStream()
            out.close()
            eapNativesDestNameOld = fileName
            // unzip old natives to old/jboss-eap-5.x"
            ant.unzip(src: new File(basedir, eapNativesDestNameOld).absolutePath, dest: new File(oldDir).absolutePath)
        } else {
            println "EAP old natives not defined."
        }
        
        
        // copy production profile to default profile
        def productionDir2 = new File(oldDir + sep + distributionDirOld + sep + "jboss-as" + sep +
        "server" + sep + "production").absolutePath
        def defaultDir2 = new File(oldDir + sep + distributionDirOld + sep + "jboss-as" + sep +
        "server" + sep + "default").absolutePath
        ant.delete(dir: new File(defaultDir2), failonerror: 'false')
        ant.mkdir(dir: defaultDir2)
        ant.copy(todir: defaultDir2) {
            fileset(dir : productionDir2) {
                include(name:"**/*")
            }
        }
    
        // create server[1..4] directories  and copy eap to them
        serverDir = new File(oldDir, 'server').absolutePath
        for ( i in 1..4 ) {
            ant.delete(dir: serverDir + i, failonerror: 'false')
            ant.mkdir(dir: serverDir + i)
            def serverNormalizedRoot = new File(serverDir + i, 'jboss-eap-5.x').absolutePath
            ant.copy(todir: serverNormalizedRoot) {
                fileset(dir: oldDir + sep + distributionDirOld) {
                    include(name: "**/*")
                }
            }
        }

        // delete leftover directories
        ant.delete(dir: new File(oldDir, 'jboss-eap-5.1').absolutePath, failonerror: 'false')
        ant.delete(dir: new File(oldDir, 'jboss-eap-5.2').absolutePath, failonerror: 'false')
        ant.delete(dir: new File(oldDir, 'jboss-eap-5.3').absolutePath, failonerror: 'false')
        ant.delete(dir: new File(oldDir, 'jboss-ep-5.1').absolutePath, failonerror: 'false')
        ant.delete(dir: new File(oldDir, 'jboss-ep-5.2').absolutePath, failonerror: 'false')
        ant.delete(dir: new File(oldDir, 'jboss-ep-5.3').absolutePath, failonerror: 'false')
    } else {
        println "EAP old distribution not defined. This is ok when this is not backward compatibility PrepareServers."
    }
    
    
    // unzip new dist to "new" directory
    
    
    if (!isWindows) {
        ant.chmod(dir: new File(basedir).absolutePath, verbose: 'true', perm: '755', includes: '**/*.sh')
    }
}

// ------ DOWNLOADING EAP --------------------------------------

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
def getUniversalProperty(String propName) {
    def propName2 = propName.replaceAll('\\.', '_')
    def propName3 = propName.replaceAll('\\.', '_').toUpperCase()
    def propName4 = null
    def sp = propName.split('\\.')
    if (sp.length > 1) {
        propName4 = sp[0] + sp[1..-1].collect{ it.capitalize() }.join()
    }

    def val = System.getProperty(propName) ?: System.getenv(propName)
    if(!val) val = System.getProperty(propName2) ?: System.getenv(propName2)
    if(!val) val = System.getProperty(propName3) ?: System.getenv(propName3)
    if(!val && propName4) val = System.getProperty(propName4) ?: System.getenv(propName4)
    return val
}

def getUniversalProperty(propName, defaultValue) {
    return getUniversalProperty(propName) ?: defaultValue
}

