package org.jboss.qa.hornetq.test.security;

import java.io.File;
import java.security.Provider;
import java.security.Security;

import org.apache.commons.lang3.StringUtils;

/**
 * Methods for handling PKCS#11 security providers.
 *
 * @author Josef Cacek
 */
public class PKCS11Utils {

    /**
     * Tries to register the sun.security.pkcs11.SunPKCS11 provider with
     * configuration provided in the given file.
     *
     * @param configPath
     * path to PKCS#11 provider configuration file
     * @return newly registered PKCS#11 provider name if provider successfully
     * registered; <code>null</code> otherwise
     */
    public static String registerProvider(final String configPath) {
        if (StringUtils.isEmpty(configPath)) {
            return null;
        }
        final File cfgFile = new File(configPath);
        final String absolutePath = cfgFile.getAbsolutePath();
        if (cfgFile.isFile()) {
            try {
                Provider pkcs11Provider = (Provider) Class.forName("sun.security.pkcs11.SunPKCS11")
                        .getConstructor(String.class).newInstance(absolutePath);
                Security.addProvider(pkcs11Provider);
                return pkcs11Provider.getName();
            } catch (Exception e) {
                System.err.println("Unable to register SunPKCS11 security provider.");
                e.printStackTrace();
            }
        } else {
            System.err.println("The PKCS#11 provider is not registered. Configuration file doesn't exist: "
                    + absolutePath);
        }
        return null;
    }
}