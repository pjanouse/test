package org.jboss.qa.hornetq.tools;

import java.security.CodeSource;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class DebugTools
{
	public static String fromWhichJarLoaded(Class<?> clazz)
	{
	    CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();

	    if ( codeSource != null) {
	        return codeSource.getLocation().toString();
	    }
	    
	    return "could not determine location";

	}
}
