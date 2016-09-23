package org.jboss.qa.hornetq.tools.arquillian.extension;


import org.jboss.arquillian.config.descriptor.api.ContainerDef;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class JbossConfigFiles {

    private static final String STANDALONE_CONF_DIR = "standalone" + File.separator + "configuration";
    private static final String DOMAIN_CONF_DIR = "domain" + File.separator + "configuration";

    private static final String BACKUP_EXTENSION = ".backup";

    private static final String DEFAULT_STANDALONE_CONFIG = "standalone-full-ha.xml";
    private static final String DEFAULT_DOMAIN_CONFIG = "domain.xml";
    private static final String DEFAULT_HOST_CONFIG = "host.xml";

    private final ContainerDef container;

    public static JbossConfigFiles forContainer(ContainerDef container) {
        return new JbossConfigFiles(container);
    }

    private JbossConfigFiles(ContainerDef container) {
        this.container = container;
    }

    public File getStandaloneXml() {
        String standaloneXmlName = container.getContainerProperties().get("serverConfig");
        if (standaloneXmlName == null || standaloneXmlName.isEmpty()) {
            standaloneXmlName = DEFAULT_STANDALONE_CONFIG;
        }
        return new File(getStandaloneConfigDir(), standaloneXmlName);
    }

    public File getStandaloneXmlBackup() {
        String standaloneXmlName = container.getContainerProperties().get("serverConfig");
        if (standaloneXmlName == null || standaloneXmlName.isEmpty()) {
            standaloneXmlName = DEFAULT_STANDALONE_CONFIG;
        }
        return new File(getStandaloneConfigDir(), standaloneXmlName + BACKUP_EXTENSION);
    }

    public List<File> getAllStandaloneXmls(){
        List<File> configFiles = new ArrayList<File>();
        File[] files = (getStandaloneConfigDir().listFiles());
        if (files == null) {
            throw new NullPointerException("no files in config directory");
        }
        for (int i = 0; i < files.length; i++) {
            String fileName = files[i].getName();
            if (fileName.endsWith(".xml") && fileName.contains("standalone")){
                configFiles.add(files[i]);
            }
        }

        // profile requested bz arquillian descriptor
        String standaloneXmlName = container.getContainerProperties().get("serverConfig");
        if (standaloneXmlName == null || standaloneXmlName.isEmpty()) {
            standaloneXmlName = DEFAULT_STANDALONE_CONFIG;
        }
        File arquillianConfig = new File(getStandaloneConfigDir(), standaloneXmlName);

        if (!configFiles.contains(arquillianConfig)) {
            throw new RuntimeException(arquillianConfig + " not found in config directory " + getStandaloneConfigDir());
        }

        return configFiles;
    }

    public File getStandaloneXmlBackup(File original) {
        return new File(original.getAbsolutePath() + BACKUP_EXTENSION);
    }

    public File getDomainXml() {
        String domainXmlName = container.getContainerProperties().get("domainConfig");
        if (domainXmlName == null || domainXmlName.isEmpty()) {
            domainXmlName = DEFAULT_DOMAIN_CONFIG;
        }
        return new File(getDomainConfigDir(), domainXmlName);
    }

    public File getDomainXmlBackup() {
        String domainXmlName = container.getContainerProperties().get("domainConfig");
        if (domainXmlName == null || domainXmlName.isEmpty()) {
            domainXmlName = DEFAULT_DOMAIN_CONFIG;
        }
        return new File(getDomainConfigDir(), domainXmlName + BACKUP_EXTENSION);
    }

    public File getHostXml() {
        String hostXmlName = container.getContainerProperties().get("hostConfig");
        if (hostXmlName == null || hostXmlName.isEmpty()) {
            hostXmlName = DEFAULT_HOST_CONFIG;
        }
        return new File(getDomainConfigDir(), hostXmlName);
    }

    public File getHostXmlBackup() {
        String hostXmlName = container.getContainerProperties().get("hostConfig");
        if (hostXmlName == null || hostXmlName.isEmpty()) {
            hostXmlName = DEFAULT_HOST_CONFIG;
        }
        return new File(getDomainConfigDir(), hostXmlName + BACKUP_EXTENSION);
    }

    public File getStandaloneConfigDir() {
        return new File(getJbossHome(), STANDALONE_CONF_DIR);
    }

    public File getDomainConfigDir() {
        return new File(getJbossHome(), DOMAIN_CONF_DIR);
    }

    public File getJbossHome() {
        String jbossHomeString = container.getContainerProperties().get("jbossHome");
        return new File(jbossHomeString);
    }

}
