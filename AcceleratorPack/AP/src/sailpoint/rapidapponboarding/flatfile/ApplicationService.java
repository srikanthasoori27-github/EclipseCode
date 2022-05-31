/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.flatfile;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.DelimitedFileConnector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.rapidapponboarding.flatfile.ConnectorService.Connector;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
public class ApplicationService {
	// Logger
	private static final Log applicationSerLogger = LogFactory.getLog("rapidapponboarding.flatfile");
	private static final String thisClassName = ApplicationService.class.getName();
	// Class members
	SailPointContext context = null;
	sailpoint.api.IdentityService apiIdService = null;
	// Constructor
	public ApplicationService(SailPointContext context) {
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"Instantiating " + thisClassName);
		setContext(context);
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"Instantiated " + thisClassName);
	}
	// Accessors
	private void setContext(SailPointContext context) {
		this.context = context;
	}
	private SailPointContext getContext() {
		return this.context;
	}
	/**
	 * Returns an instance of a delimited application with the specified name.
	 * If more than one application is found with the same next, then the first
	 * instance is returned. The first instance is non-deterministic.
	 * 
	 * @param appName
	 *            The name of the application implemented using the delimited
	 *            file connector.
	 * @return The application object.
	 * @throws Exception
	 */
	public String findDelimitedAppByName(String appName)
			throws Exception {
		String thisMethodName = "findDelimitedAppByName(java.lang.String)";
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"Entering " + thisMethodName);
		Iterator iter = null; // Return var
		try {
			// Get the application matching that name and of type delimited
			iter = getContext().search(
					Application.class,
					new QueryOptions().add(
							appAndConnectorFilter(appName,
									Connector.DELIMITED_FILE
											.getConnectorClassName())), "id");
			if (iter != null && !iter.hasNext()) {
				LogEnablement.isLogWarningEnabled(applicationSerLogger,"Could not find an application with name " + appName
						+ " defined as a flat-file connector");
				throw new Exception(
						"Could not find an application with name " + appName
								+ " defined as a flat-file connector");
			} else {
				Object[] obj = (Object[]) iter.next();
				if (obj != null && obj.length == 1 && obj[0] != null)
					return obj[0].toString();
			}
		} catch (GeneralException e) {
			throw new Exception(e);
		} finally {
			LogEnablement.isLogDebugEnabled(applicationSerLogger,"Exiting " + thisMethodName);
			if (null != iter) {
				Util.flushIterator(iter);
			}
		}
		return null;
	}
	/**
	 * A method that gets the column names associated with a delimited connector
	 * application. This overload is easier to call from a rule.
	 * 
	 * @param app
	 *            The application object.
	 * @return
	 * @throws Exception
	 */
	public List<String> getColumnNamesForDelimitedApp(Application app)
			throws Exception {
		String thisMethodName = "getColumnNamesForDelimitedApp(sailpoint.object.Application)";
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"Entering " + thisMethodName);
		if (!app.getConnector().equals(
				ConnectorService.Connector.DELIMITED_FILE
						.getConnectorClassName())) {
			throw new Exception(
					"Specified application is not a delimited file application");
		}
		try {
			return new DelimitedFileConnector(app).getColumnNames(app
					.getAccountSchema());
		} catch (ConnectorException e) {
			throw new Exception(e);
		} finally {
			LogEnablement.isLogDebugEnabled(applicationSerLogger,"Exiting " + thisMethodName);
		}
	}
	/**
	 * Checks to see if the files supplied for a delimited connector application
	 * are valid. The following tests are performed for each object type defined
	 * in the application schema. <li>
	 * <ol>
	 * Presence of identity attribute on the schema
	 * </ol>
	 * <ol>
	 * Presence of data file for the schema, along with at least 1 row of data
	 * with the delimiter specified. These tests are implicit.
	 * </ol>
	 * <ol>
	 * A count to see if they number of columns in the file matches the number
	 * of attributes marked as required in the application.
	 * </ol>
	 * <ol>
	 * If the schema specifies use of headers, then a check is performed to see
	 * if the header row in the file contains at least one column name matching
	 * the identity attribute.
	 * </ol>
	 * </li> Note that by default attributes in the schema are not marked
	 * required, doing so requires the required="true" attribute-value to be
	 * explicitly added to the AttributeDefinition element.
	 * 
	 * @param app
	 *            The application object
	 * @param schema
	 *            The schema for the object in the application
	 * @return null, if the files for the object pass validation A String with
	 *         error message if the files fail validation
	 * @throws Exception
	 *             If identity attribute is not configured, or if the
	 *             application uses regular expressions instead of delimiters to
	 *             parse data. Downstream exceptions are caught and re-thrown as
	 *             well.
	 * @throws GeneralException
	 * 
	 */
	public String validateAppSchema(Application app, Schema schema)
			throws Exception, GeneralException {
		String thisMethodName = "validateAppSchema(sailpoint.object.Application, sailpoint.object.Schema)";
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"Entering " + thisMethodName);
		// Get the instance of the delimited file connector for this app
		DelimitedFileConnector dfConn = new DelimitedFileConnector(app);
		String transport = dfConn
				.getStringAttribute(DelimitedFileConnector.CONFIG_TRANSPORT);
		if ((transport == null)
				|| (DelimitedFileConnector.TRANSPORT_TYPE_LOCAL
						.compareTo(transport) == 0)) {
			try {
				String objectType = schema.getObjectType();
				String identityAttribute = schema.getIdentityAttribute().trim();
				if (identityAttribute == null || identityAttribute.equals("")) {
					return "The application "
							+ app.getName()
							+ " has the schema "
							+ schema.getName()
							+ " incorrectly configured. The identity attribute is missing";
				} else if (dfConn
						.getAttribute(DelimitedFileConnector.CONFIG_REGULAR_EXPRESSION) != null) {
					LogEnablement.isLogWarningEnabled(applicationSerLogger,"Regex is "
							+ dfConn.getAttribute(DelimitedFileConnector.CONFIG_REGULAR_EXPRESSION));
					return "Support for delimited connector applications using regular expressions is currently not available.";
				}
				// Get the object data
				boolean hasHeaders = app
						.getBooleanAttributeValue(DelimitedFileConnector.CONFIG_HAS_HEADER);
				String dataFilePath = dfConn.getFileName(schema);
				List<String> columnNames = dfConn.getColumnNames(schema);
				List<AttributeDefinition> schemaAttrs = schema.getAttributes();
				String delimiter = dfConn
						.getStringAttribute(DelimitedFileConnector.CONFIG_DELIMITER);
				List<String> fileHeaders = FileReader.getHeadersAsList(
						dataFilePath, delimiter, true);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The object type is "
						+ objectType
						+ " with identity attribute "
						+ identityAttribute
						+ " and "
						+ (hasHeaders ? "has headers."
								: "does not have headers."));
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The file path is " + dataFilePath);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The column names are " + columnNames);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The delimiter pattern is " + delimiter);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The file headers are " + fileHeaders);
				// Perform the various tests here
				if (hasHeaders) {
					/*
					 * Test to see if the file supplied is valid for a given
					 * schema by confirming that the headers in the file contain
					 * at least one word that is identical to the schema's
					 * identity attribute.
					 */
					if (fileHeaders!=null && !fileHeaders.contains(identityAttribute.toUpperCase())) {
						return "The schema indicates that the file has headers, but the header is missing the column '"
								+ identityAttribute + "'. Exiting with ERROR!";
					} else {
						LogEnablement.isLogInfoEnabled(applicationSerLogger,"Identity Attribute " + identityAttribute
								+ " is present in file header.");
					}
				}
				/*
				 * Test to see if the number of required schema attributes
				 * matches the number of columns (words) in the file.
				 */
				schemaAttrs = dfConn.getSchemaAttributes(objectType);
				int requiredSchemaAttrs = 0;
				List<String> requiredSchemAttributeNames = new ArrayList();
				for (AttributeDefinition schemaAttr : schemaAttrs) {
					// LogEnablement.isLogDebugEnabled(applicationSerLogger,"Attribute name is " + schemaAttr.getName() +
					// (schemaAttr.isRequired() ? " and is required." :
					// " and is not required"));
					if (schemaAttr.isRequired()) {
						requiredSchemaAttrs++;
						requiredSchemAttributeNames.add(schemaAttr.getName());
					}
				}
				if (hasHeaders && requiredSchemAttributeNames != null
						&& requiredSchemAttributeNames.size() > 0
						&& fileHeaders != null && fileHeaders.size() > 0) {
					for (String requireSttrName : requiredSchemAttributeNames) {
						if (requireSttrName != null) {
							requireSttrName = requireSttrName.toUpperCase();
							if (!fileHeaders.contains(requireSttrName)) {
								return "The required schema attribute "
										+ requireSttrName
										+ " in not present in the file header. Exiting with ERROR!";
							}
						}
					}
				}
				if (fileHeaders!=null && fileHeaders.size() < requiredSchemaAttrs) {
					return "The schema indicates that there are "
							+ requiredSchemaAttrs
							+ " required attributes, but the file only has "
							+ fileHeaders.size()
							+ " columns. Exiting with ERROR!";
				} else {
					LogEnablement.isLogInfoEnabled(applicationSerLogger,"The number of required schema attributes matches or exceeds the number of columns in the file.");
				}
			} catch (ConnectorException e) {
				throw new Exception(e);
			} catch (GeneralException e) {
				throw new Exception(e);
			} finally {
				LogEnablement.isLogDebugEnabled(applicationSerLogger,"Exiting " + thisMethodName);
			}
			return null;
		}
		return null;
	}
	/**
	 * Upper Case Conversion
	 * @param app
	 * @param schema
	 * @return
	 * @throws Exception
	 */
	public BufferedInputStream convertIdAttrInFile(Application app,
			Schema schema) throws Exception {
		String thisMethodName = "convertIdAttrInFile(sailpoint.object.Application, sailpoint.object.Schema)";
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"Entering " + thisMethodName);
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"App Name " + app.getName());
		LogEnablement.isLogDebugEnabled(applicationSerLogger,"Schema Id " + schema.getId());
		String convertedData = ""; // Variable to hold converted data
		try {
			// Get the instance of the delimited file connector for this app
			DelimitedFileConnector dfConn = new DelimitedFileConnector(app);
			String transport = dfConn
					.getStringAttribute(DelimitedFileConnector.CONFIG_TRANSPORT);
			if ((transport == null)
					|| (DelimitedFileConnector.TRANSPORT_TYPE_LOCAL
							.compareTo(transport) == 0)) {
				LogEnablement.isLogDebugEnabled(applicationSerLogger,"dfConn " + dfConn);
				// Get the object data
				String mergeIndexCol = dfConn
						.getStringAttribute(DelimitedFileConnector.CONFIG_INDEX_COLUMN);
				LogEnablement.isLogDebugEnabled(applicationSerLogger,"mergeIndexCol " + mergeIndexCol);
				boolean hasHeaders = app
						.getBooleanAttributeValue(DelimitedFileConnector.CONFIG_HAS_HEADER);
				LogEnablement.isLogDebugEnabled(applicationSerLogger,"hasHeaders " + hasHeaders);
				String dataFilePath = dfConn.getFileName(schema);
				LogEnablement.isLogDebugEnabled(applicationSerLogger,"dataFilePath " + dataFilePath);
				String delimiter = dfConn
						.getStringAttribute(DelimitedFileConnector.CONFIG_DELIMITER);
				LogEnablement.isLogDebugEnabled(applicationSerLogger,"delimiter " + delimiter);
				// The columns, regardless of whether header is specified or not
				List<String> columns = Collections.emptyList();
				if (hasHeaders) {
					columns = FileReader.getHeadersAsList(dataFilePath,
							delimiter, true);
					LogEnablement.isLogDebugEnabled(applicationSerLogger,"columns from file" + columns);
				} else {
					columns = dfConn.getColumnNames(schema);
					LogEnablement.isLogDebugEnabled(applicationSerLogger,"columns from schema" + columns);
				}
				if(columns!=null && columns.size()>0)
				{
				int mergeColNum = columns.indexOf(mergeIndexCol);
				LogEnablement.isLogDebugEnabled(applicationSerLogger,"mergeColNum " + mergeColNum);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The file path is " + dataFilePath);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The column names are " + columns);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The delimiter pattern is " + delimiter);
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The merge index column " + mergeIndexCol
						+ " and is in position " + mergeColNum);
				convertedData = FileReader.capitalizeTokensInDelimitedFile(
						dataFilePath, delimiter.toCharArray()[0],
						columns.indexOf(mergeIndexCol));
				}
				LogEnablement.isLogInfoEnabled(applicationSerLogger,"The converted data is " + convertedData);
			}
		} catch (ConnectorException e) {
			LogEnablement.isLogErrorEnabled(applicationSerLogger,e.getMessage());
			LogEnablement.isLogErrorEnabled(applicationSerLogger,e);
			throw new Exception(e);
		} catch (GeneralException e) {
			LogEnablement.isLogErrorEnabled(applicationSerLogger,e.getMessage());
			LogEnablement.isLogErrorEnabled(applicationSerLogger,e);
			throw new Exception(e);
		} catch (Exception e) {
			LogEnablement.isLogErrorEnabled(applicationSerLogger,e.getMessage());
			LogEnablement.isLogErrorEnabled(applicationSerLogger,e);
			throw new Exception(e);
		}
		return new BufferedInputStream(IOUtils.toInputStream(convertedData));
	}
	/**
	 * Filter for Application
	 * @param appName
	 * @param connectorName
	 * @return
	 */
	public static Filter appAndConnectorFilter(String appName,
			String connectorName) {
		Filter appFilter = Filter.eq("name", appName);
		return Filter.and(appFilter, Filter.eq("connector", connectorName));
	}
}
