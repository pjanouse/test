package org.jboss.qa.hornetq.test.journalreplication.utils;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.jboss.qa.hornetq.tools.arquillian.extension.RestoreConfig;

/**
 * @author <a href="dpogrebn@redhat.com">Dmytro Pogrebniuk</a>
 * 
 */
public class FileUtil
{
	private static final Logger log = Logger.getLogger(FileUtil.class);

	/**
	 * @param original
	 * @param destination
	 */
	public static void copyFile(File original, File destination)
	{
		RestoreConfig copyrator = new RestoreConfig();

		try
		{
			copyrator.copyFile(original, destination);
		} catch (IOException copyException)
		{
			log.error("Exception while copying: " + original.getAbsolutePath() + " ->" + destination.getAbsolutePath(),
					copyException);
			throw new RuntimeException(copyException);
		}

		log.info("Files are copied: " + original.getAbsolutePath() + " ->" + destination.getAbsolutePath());
	}

	/**
	 * Deletes given folder and all sub folders
	 * 
	 * @param path
	 *            folder which should be deleted
	 * @return true if operation was successful, false otherwise
	 */
	public static boolean deleteFolder(File path)
	{
		if (log.isDebugEnabled())
		{
			log.debug(String.format("Removing folder '%s'", path));
		}
		boolean successful = true;
		if (path.exists())
		{
			File[] files = path.listFiles();
			if (files != null)
			{
				for (File file : files)
				{
					successful = successful && ((file.isDirectory()) ? deleteFolder(file) : file.delete());
				}
			}
		}
		return successful && (path.delete());
	}
}
