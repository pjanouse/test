package org.jboss.qa.hornetq.tools;

import org.jboss.logging.Logger;

import java.lang.management.ManagementFactory;
import java.security.CodeSource;
import java.util.Map;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 *
 */
public class DebugTools
{
	private static final Logger log = Logger.getLogger(DebugTools.class);

	public static String fromWhichJarLoaded(Class<?> clazz)
	{
	    CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();

	    if ( codeSource != null) {
	        return codeSource.getLocation().toString();
	    }
	    
	    return "could not determine location";

	}

	public static void printThreadDump() {
		Map<Thread, StackTraceElement[]> mst = Thread.getAllStackTraces();
		StringBuilder stacks = new StringBuilder("Stack traces of all threads:");
		for (Thread t : mst.keySet()) {
			stacks.append("Stack trace of thread: ").append(t.toString()).append("\n");
			StackTraceElement[] elements = mst.get(t);
			for (StackTraceElement e : elements) {
				stacks.append("---").append(e).append("\n");
			}
			stacks.append("---------------------------------------------\n");
		}
		log.error(stacks);
	}

	public static void printThreadDump(Thread thread) {
		StackTraceElement[] st = thread.getStackTrace();
		StringBuilder stack = new StringBuilder();

		stack.append("Stack trace of thread: ").append(thread.toString()).append("\n");
		for (StackTraceElement e : st) {
			stack.append("---").append(e).append("\n");
		}
		stack.append("---------------------------------------------\n");
		log.error(stack);
	}

	public static int getSurefirePid(){
		String name = ManagementFactory.getRuntimeMXBean().getName();
		return Integer.parseInt(name.split("@")[0]);
	}
}
