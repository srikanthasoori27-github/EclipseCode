/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FileTools
{
    private static final Log log = LogFactory.getLog(FileTools.class);
    
	// ========================================
	//
	// methods: exists
	//
	// ========================================

	public static boolean doesFileExist(String pathname)
	{
		File file = new File(pathname);
		return file.exists();
	}

	// ========================================

	public static boolean doesDirectoryExist(String pathname)
	{
		File file = new File(pathname);
		if (file.exists() && file.isDirectory())
		{
			return true;
		}
		return false;
	}

	// ========================================
	//
	// methods: lists
	//
	// ========================================

	public static File[] listFiles(String pathname) throws FileNotFoundException
	{
		File file = new File(pathname);
		if (!file.exists())
		{
			throw new FileNotFoundException();
		}

		File files[] = file.listFiles(new FileFilter()
		{
			public boolean accept(File pathname)
			{
				if (pathname.exists() && pathname.isFile())
				{
					return true;
				}
				return false;
			}
		});

		return files;
	}

	// ========================================

	public static File[] listFiles(String pathname, int limit) throws FileNotFoundException
	{
		File file = new File(pathname);
		if (!file.exists())
		{
			throw new FileNotFoundException();
		}

		final int countLimit = limit;

		File files[] = file.listFiles(new FileFilter()
		{
			int count = 0;

			public boolean accept(File pathname)
			{
				if (pathname.exists() && pathname.isFile() && count < countLimit)
				{
					count++;
					return true;
				}
				return false;
			}
		});

		return files;
	}

	// ========================================

	public static File[] listDirectories(String pathname) throws FileNotFoundException
	{
		File file = new File(pathname);
		if (!file.exists())
		{
			throw new FileNotFoundException();
		}

		File files[] = file.listFiles(
		// anonymous class
				new FileFilter()
				{
					public boolean accept(File pathname)
					{
						if (pathname.exists() && pathname.isDirectory())
						{
							return true;
						}
						return false;
					}
				});

		return files;
	}

	// ========================================
	//
	// methods: write
	//
	// ========================================

	public static void writeToFile(String filename, String string)
	{
		try
		{
			FileWriter fw = new FileWriter(filename);
			fw.write(string);
			fw.close();
		}
		catch (Exception e)
		{
		    if (log.isErrorEnabled())
		        log.error(e.getMessage(), e);
		}
	}

	// ========================================

	public static void writeToFile(String filename, byte[] byteArray)
	{
		try
		{
			FileOutputStream fos = new FileOutputStream(filename);
			fos.write(byteArray);
			fos.close();
		}
		catch (Exception e)
		{
		    if (log.isErrorEnabled())
		        log.error(e.getMessage(), e);
		}
	}

	// ========================================
	//
	// methods: read
	//
	// ========================================

	public static String readFromFileAsString(String filename) throws java.io.FileNotFoundException,
			java.io.IOException
	{
		FileReader fr = new FileReader(filename);
		BufferedReader br = new BufferedReader(fr);
		StringBuilder sb = new StringBuilder();

		String line = null;
		while ((line = br.readLine()) != null)
		{
			sb.append(line);
			sb.append("\n");
		}

		br.close();
		fr.close();

		return sb.toString();
	}

	// ========================================

	public static ArrayList readFromFileAsStringArrayList(String filename) throws java.io.FileNotFoundException,
			java.io.IOException
	{
		FileReader fr = new FileReader(filename);
		BufferedReader br = new BufferedReader(fr);
		ArrayList<String> stringArrayList = new ArrayList<String>();

		String line = null;
		while ((line = br.readLine()) != null)
		{
			stringArrayList.add(line);
		}

		br.close();
		fr.close();

		return stringArrayList;
	}

	// ========================================

    public static String getExtension(String file) {
        if (Util.isNullOrEmpty(file) || !file.contains(".") || file.length() == 1) {
            return file;
        }

        return file.substring(file.lastIndexOf(".") + 1);
    }

}
