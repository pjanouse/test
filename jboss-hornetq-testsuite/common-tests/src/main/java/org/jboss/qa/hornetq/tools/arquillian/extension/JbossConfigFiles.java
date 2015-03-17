package org.jboss.qa.hornetq.tools.arquillian.extension;


import java.io.File;

import org.jboss.arquillian.config.descriptor.api.ContainerDef;

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
