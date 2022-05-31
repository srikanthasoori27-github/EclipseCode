/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.flatfile;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;
public class FileReader {
	// Logger
	private static final Log fileReaderLogger = LogFactory
			.getLog("rapidapponboarding.flatfile");
	/**
	 * This method is used only to get first line of file Need to close file and
	 * stream in the calling method This method is compatible with Java 7 and
	 * Java 8
	 */
	private static String[] getHeadersAsArray(String filePath, String regex,
			boolean convertToUpperCase) throws GeneralException {
		LogEnablement.isLogDebugEnabled(fileReaderLogger,"Start getHeadersAsArray ");
		LogEnablement.isLogDebugEnabled(fileReaderLogger,"The filePath is " + filePath);
		Stream streamFromReader = null;
		BufferedReader reader = null;
		InputStream stream = null;
		File file = null;
		String[] headerArray = null;
		try {
			if (filePath != null) {
				/**
				 * "By default the classes in the java.io package always resolve
				 * relative pathnames against the current user directory. This
				 * directory is named by the system property user.dir, and is
				 * typically the directory in which the Java virtual machine was
				 * invoked"
				 */
				file = new File(filePath);
				if ( !file.exists() )  {
					LogEnablement.isLogDebugEnabled(fileReaderLogger,"File Not Exists");
					if ( !file.isAbsolute() ) {
						String appHome = Util.getApplicationHome();
						LogEnablement.isLogDebugEnabled(fileReaderLogger,"appHome.."+appHome);
						if ( appHome != null ) {
							file = new File(appHome+File.separator+filePath);
							if ( !file.exists() ) {
								file = new File(filePath);
							}
						}
					}
				}
				if (file.exists()) {
					LogEnablement.isLogDebugEnabled(fileReaderLogger,"File Exists ");
					stream = new BufferedInputStream(new FileInputStream(file));
					if (stream != null) {
						reader = new BufferedReader(new InputStreamReader(
								stream));
						if (reader != null) {
							String line;
							streamFromReader = reader.lines();
							while ((line = reader.readLine()) != null)
							{
								LogEnablement.isLogDebugEnabled(fileReaderLogger,"convertToUpperCase "+convertToUpperCase);
								if (convertToUpperCase && line != null) 
								{
									line = line.toUpperCase();
								}
								if (line != null) 
								{
									headerArray = line.split(Pattern.quote(regex));
									LogEnablement.isLogDebugEnabled(fileReaderLogger,"headerArray "+headerArray);
								}
								break;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			throw new GeneralException(e);
		} finally {
			if (streamFromReader != null) {
				streamFromReader.close();
			}
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		LogEnablement.isLogDebugEnabled(fileReaderLogger,"End getHeadersAsArray " + headerArray);
		return headerArray;
	}
	/**
	 * Get the words in the first line of a file as a String array. Not
	 * Compatible with Java 7
	 * 
	 * @param path
	 *            The file to read.
	 * @param regex
	 *            The regular expression the delimits the words.
	 * @param convertToUpperCase
	 *            true, if we want the results in UPPER CASE
	 * @return
	 * @throws ToolsException
	 */
	/*
	 * public static String[] getHeadersAsArray(String path, String regex,
	 * boolean convertToUpperCase) throws ToolsException { String thisMethodName
	 * = "getHeadersAsArray(java.lang.String)"; LogEnablement.isLogDebugEnabled(fileReaderLogger,"Entering " +
	 * thisMethodName);
	 * 
	 * String[] headerArray = null; Stream fileStream = null;
	 * 
	 * try {
	 * 
	 * if (convertToUpperCase) { fileStream = Files.lines(Paths.get(path)).map(s
	 * -> s.toUpperCase().split(Pattern.quote(regex))); headerArray = (String[])
	 * fileStream.findFirst().get(); } else { fileStream =
	 * Files.lines(Paths.get(path)).map(s -> s.split(Pattern.quote(regex)));
	 * headerArray = (String[]) fileStream.findFirst().get(); } } catch
	 * (IOException e) { throw new ToolsException(e); } finally {
	 * 
	 * if(fileStream != null) { LogEnablement.isLogDebugEnabled(fileReaderLogger,"Closing file stream: " + path);
	 * fileStream.close(); } LogEnablement.isLogDebugEnabled(fileReaderLogger,"Exiting " + thisMethodName); }
	 * 
	 * return headerArray; }
	 */
	/**
	 * Get the words in the first line of a file as a List.
	 * 
	 * @param path
	 *            The file to read.
	 * @param regex
	 *            The regular expression the delimits the words.
	 * @param convertToUpperCase
	 *            true, if we want the results in UPPER CASE
	 * @return
	 * @throws ToolsException
	 * @throws GeneralException
	 */
	public static List<String> getHeadersAsList(String path, String regex,
			boolean convertToUpperCase) throws  GeneralException {
		String thisMethodName = "getHeadersAsList(java.lang.String)";
		LogEnablement.isLogDebugEnabled(fileReaderLogger,"Entering " + thisMethodName);
		try {
			String[] headerAsArray = getHeadersAsArray(path, regex,convertToUpperCase);
			if(headerAsArray!=null)
			{
				LogEnablement.isLogDebugEnabled(fileReaderLogger,"Exiting " + thisMethodName);
				return Arrays.asList(getHeadersAsArray(path, regex,
						convertToUpperCase));
			}
			else
			{
				LogEnablement.isLogDebugEnabled(fileReaderLogger,"Exiting " + thisMethodName);
				return null;
			}
		} finally {
			LogEnablement.isLogDebugEnabled(fileReaderLogger,"Exiting " + thisMethodName);
		}
	}
	/**
	 * This method will convert all occurences of a derived token in each line
	 * of a given file to UPPER CASE. The token is derived by splitting each
	 * line of data using the specified delimiter and using the token specified
	 * by the column index. For example, if a file has a line <br/>
	 * Person ID | First Name | Last Name | First Name<br/>
	 * then for a pipe delimiter and column index of 1, the output is Person ID
	 * | FIRST NAME | Last Name | FIRST NAME. Note that the entire contents of
	 * the file is returned as a String making this inefficient for large files.
	 * 
	 * @param filePath
	 *            The fully qualified location of the file.
	 * @param delimiter
	 *            The delimiter used to split each string of the file into
	 *            tokens. Delimiter values are automtically escaped to the
	 *            appropriate regular expression using the Pattern.quote(String)
	 *            method.
	 * @param colIdx
	 *            The index of the column in the line for which the conversion
	 *            occurs.
	 * @return The entire file, as a String, after converting every occurence of
	 *         the token in each line to UPPER CASE
	 * @throws ToolsException
	 *             A wrapper for underlying java.io.IOException
	 */
	public static String capitalizeTokensInDelimitedFile(String filePath,
			char delimiter, int colIdx) throws GeneralException,Exception {
		String thisMethodName = "capitalizeTokensInDelimitedFile(java.util.String, java.util.String, int)";
		LogEnablement.isLogDebugEnabled(fileReaderLogger,"Entering " + thisMethodName + filePath);
		// A constant for the double quote character
		String doubleQuoteChar = "\"";
		StringBuffer convertedData = new StringBuffer();
		try {
			File file=new File(filePath);
			if ( !file.exists() )  {
				LogEnablement.isLogDebugEnabled(fileReaderLogger,"File Not Exists");
				if ( !file.isAbsolute() ) {
					String appHome = Util.getApplicationHome();
					LogEnablement.isLogDebugEnabled(fileReaderLogger,"appHome.."+appHome);
					if ( appHome != null ) {
						file = new File(appHome+File.separator+filePath);
						if ( !file.exists() ) {
							file = new File(filePath);
						}
					}
				}
			}
		   if (file.exists()) 
			{
				LogEnablement.isLogDebugEnabled(fileReaderLogger,"File Exists ");
				for (LineIterator lineItr = FileUtils.lineIterator(file, "UTF-8"); lineItr.hasNext();) {
					String tmpData = lineItr.nextLine();
					boolean isQuotesPresent = false;
					if (tmpData != null && tmpData.trim().length() > 0) {
						if (tmpData.contains(doubleQuoteChar)) {
							isQuotesPresent = true;
						}
						fileReaderLogger.info("Parsing line " + tmpData);
						List<String> words = new RFC4180LineParser(delimiter)
								.parseLine(tmpData);
						fileReaderLogger.info("The words from the line are : " + words);
						// Convert the array back to a String, but change the word
						// at
						// the colIdx to upper case
						for (int i = 0; i < words.size(); i++) {
							if (isQuotesPresent)
								convertedData.append(doubleQuoteChar);
							if (i == colIdx) {
								convertedData.append(words.get(i) == null ? ""
										: words.get(i).toUpperCase().trim());
							} else {
								convertedData.append(words.get(i) == null ? ""
										: words.get(i).trim());
							}
							if (isQuotesPresent)
								convertedData.append(doubleQuoteChar);
							if (i != (words.size() - 1))
								convertedData.append(delimiter);
						}
						convertedData.append("\n"); // add a UTF-8 new line
						// character
					} // after each line
				}
			}
		} catch (IOException e) {
			LogEnablement.isLogErrorEnabled(fileReaderLogger,e.getMessage());
			LogEnablement.isLogErrorEnabled(fileReaderLogger,e);
			throw new Exception(e);
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(fileReaderLogger,e.getMessage());
			LogEnablement.isLogErrorEnabled(fileReaderLogger,e);
			throw new Exception(e);
		}
		LogEnablement.isLogDebugEnabled(fileReaderLogger,"The converted data is " + convertedData);
		return convertedData.toString();
	}
}
