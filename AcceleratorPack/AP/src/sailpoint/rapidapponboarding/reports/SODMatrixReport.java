/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.reports;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.LiveReport;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.rapidapponboarding.rule.PolicyViolationsRuleLibrary;
import sailpoint.reporting.datasource.JavaDataSource;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Policy Violation Feature Repor
 * @author rohit.gupta
 *
 */
public class SODMatrixReport implements JavaDataSource {
	private QueryOptions baseQueryOptions;
	private SailPointContext context;
	private Integer startRow;
	private Integer pageSize;
	private Monitor monitor;
	private List<String> applicationList;
	private List<Map<String, Object>> objectList = new ArrayList();
	private Iterator<Map<String, Object>> objects;
	private Map<String, Object> object;
	private String separator = "|";
	private static final Log sodMatrixLogger = LogFactory
			.getLog(AppHealthCheckReport.class);
	@Override
	public void initialize(SailPointContext context, LiveReport report,
			Attributes<String, Object> arguments, String groupBy,
			List<Sort> sort) throws GeneralException {
		this.context = context;
		baseQueryOptions = new QueryOptions();
		try {
			// Check if the use all apps checkbox was selected
			if (arguments.getBoolean("selectAllApplications")) {
				this.applicationList = PolicyViolationsRuleLibrary
						.getPVAppList(context);
			} else {
				// If not, then use the applications that were manually input
				this.applicationList = arguments.getList("applications");
			}
			prepare();
		} catch (SQLException e) {
			LogEnablement.isLogDebugEnabled(sodMatrixLogger,"SQLException : " + e.getMessage());
		}
	}
	/**
	 * This is the method which prepares data for the report.
	 *
	 */
	private void prepare() throws GeneralException, SQLException {
		try {
			/*
			 * Frames the appropriate query based on the input parameter values
			 * passed
			 */
			if (this.applicationList != null && this.applicationList.size() > 0) {
				Map<String, Object> itemMap = null;
				List singleEntAppList = null;
				singleEntAppList = PolicyViolationsRuleLibrary
						.getSinglePVAppList(context);
				for (String appName : this.applicationList) {
					if (Util.isNotNullOrEmpty(appName)) {
						// Check if this application only supports one
						// entitlement per account
						if (singleEntAppList != null
								&& singleEntAppList.contains(appName)) {
							itemMap = new HashMap<String, Object>();
							itemMap.put("ApplicationName", appName);
							itemMap.put("GroupId", "NA");
							itemMap.put("Entitlements", "NA");
							itemMap.put("Description",
									"This application only permits 1 entitlement per account");
							objectList.add(itemMap);
						} else {
							Application appSearchObj = context.getObjectByName(
									Application.class, appName);
							Custom customObj = null;
							String customName = (String) appSearchObj
									.getAttributeValue("txCombinationName");
							LogEnablement.isLogDebugEnabled(sodMatrixLogger,"customName ->" + customName);
							if (customName != null) {
								customObj = context.getObjectByName(
										Custom.class, customName);
								LogEnablement.isLogDebugEnabled(sodMatrixLogger,"customObj ->" + customObj);
							}
							if (appSearchObj != null)
								context.decache(appSearchObj);
							Iterator entitlementIterator = null;
							try {
								// check if this application has an SoD Custom
								// object
								if (customObj != null) {
									Map violationsMap = (Map) customObj
											.get("ViolationsMap");
									// Sort the map keys
									Map<String, String> sortedViolationsMap = new TreeMap<String, String>(
											sodKeyComparator);
									sortedViolationsMap.putAll(violationsMap);
									for (Map.Entry<String, String> entry : sortedViolationsMap
											.entrySet()) {
										itemMap = new HashMap<String, Object>();
										itemMap.put("ApplicationName", appName);
										itemMap.put("GroupId", entry.getKey());
										// Separate each entitlement (by comma)
										// so that we can look up its
										// description and append it
										String[] entitlementNames = entry
												.getValue().split(",");
										String entStr = "";
										int count = 0;
										String entitlementSeparator = "";
										for (String entitlementName : entitlementNames) {
											QueryOptions ops = new QueryOptions();
											ops.addFilter(Filter.and(Filter
													.eq("application.name",
															appName), Filter
													.eq("value",
															entitlementName
																	.trim())));
											entitlementIterator = context
													.search(ManagedAttribute.class,
															ops, "attributes");
											if (count < 1) {
												entitlementSeparator = "";
											} else {
												entitlementSeparator = " "
														+ this.separator + " ";
											}
											if (entitlementIterator != null) {
												String description = null;
												while (entitlementIterator
														.hasNext()) {
													Object[] entAttr = (Object[]) entitlementIterator
															.next();
													if (entAttr != null
															&& entAttr.length == 1
															&& entAttr[0] != null) {
														Attributes attrs = (Attributes) entAttr[0];
														if (attrs != null) {
															Map map = (Map) attrs
																	.get("sysDescriptions");
															if (map != null) {
																description = (String) map
																		.get("en_US");
															}
														}
													}
												}
												if (Util.isNotNullOrEmpty(description)) {
													entStr += entitlementSeparator
															+ entitlementName
															+ " ("
															+ description + ")";
												} else {
													entStr += entitlementSeparator
															+ entitlementName;
												}
											} else {
												entStr += entitlementSeparator
														+ entitlementName;
											}
											count++;
										}
										itemMap.put("Entitlements", entStr);
										itemMap.put("Description",
												"These entitlements cannot be assigned together per account");
										objectList.add(itemMap);
									}
									context.decache(customObj);
								} else {
									itemMap = new HashMap<String, Object>();
									itemMap.put("ApplicationName", appName);
									itemMap.put("GroupId", "NA");
									itemMap.put("Entitlements", "NA");
									itemMap.put(
											"Description",
											"This application allows multiple entitlements, but may have a conflicting entitlements/roles matrix defined using OOTB policy violations");
									objectList.add(itemMap);
								}
							} finally {
								if (null != entitlementIterator) {
									Util.flushIterator(entitlementIterator);
								}
							}
						}
					}
				}
			} else {
				LogEnablement.isLogDebugEnabled(sodMatrixLogger,"Application list is null or empty");
			}
			objects = objectList.iterator();
		} catch (Exception e) {
			LogEnablement.isLogDebugEnabled(sodMatrixLogger,"SQLException : " + e.getMessage());
		}
	}
	public QueryOptions getBaseQueryOptions() {
		return baseQueryOptions;
	}
	@Override
	public String getBaseHql() {
		return null;
	}
	@Override
	public Object getFieldValue(String fieldName) throws GeneralException {
		if (fieldName.equals("ApplicationName")) {
			return this.object.get("ApplicationName");
		} else if (fieldName.equals("GroupId")) {
			return this.object.get("GroupId");
		} else if (fieldName.equals("Entitlements")) {
			return this.object.get("Entitlements");
		} else if (fieldName.equals("Description")) {
			return this.object.get("Description");
		} else {
			throw new GeneralException("Unknown column '" + fieldName + "'");
		}
	}
	@Override
	public int getSizeEstimate() throws GeneralException {
		if (this.applicationList != null) {
			return this.applicationList.size();
		} else {
			return 0;
		}
	}
	@Override
	public void close() {
	}
	@Override
	public void setMonitor(Monitor monitor) {
		this.monitor = monitor;
	}
	@Override
	public Object getFieldValue(JRField jrField) throws JRException {
		String fieldName = jrField.getName();
		try {
			return getFieldValue(fieldName);
		} catch (GeneralException e) {
			throw new JRException(e);
		}
	}
	@Override
	public boolean next() throws JRException {
		boolean hasMore = false;
		if (this.objects != null) {
			hasMore = this.objects.hasNext();
			if (hasMore) {
				this.object = this.objects.next();
			} else {
				this.object = null;
			}
		}
		return hasMore;
	}
	@Override
	public void setLimit(int startRow, int pageSize) {
		this.startRow = startRow;
		this.pageSize = pageSize;
	}
	public static Comparator sodKeyComparator = new Comparator() {
		public int compare(Object key1, Object key2) {
			int key1Val = 0;
			int key2Val = 0;
			try {
				key1Val = Integer.parseInt(key1.toString());
				key2Val = Integer.parseInt(key2.toString());
			} catch (NumberFormatException nfe) {
				LogEnablement.isLogErrorEnabled(sodMatrixLogger,"Cannot convert values to int: " + key1 + ", "
						+ key2);
				return 0;
			}
			return Integer.compare(key1Val, key2Val);
		}
	};
}
