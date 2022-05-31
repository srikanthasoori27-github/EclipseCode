/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
*/
package sailpoint.rapidapponboarding.reports;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.LiveReport;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.reporting.datasource.JavaDataSource;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
/**
 * Role Members Report
 * @author rohit.gupta
 *
 */
public class RoleMembersReport implements JavaDataSource {
	private QueryOptions baseQueryOptions;
	private SailPointContext context;
	private Integer startRow;
	private Integer pageSize;
	private Monitor monitor;
	private List<String> applicationList;
	private String appName = null;
	private String rolePrivileged = null;
	private boolean disablePsA = true;
	private boolean disableJoiner = true;
	private boolean disableLogicalApp = true;
	private boolean disableBusinessApprovers = true;
	private boolean disable2ndBusinessApprovers = true;
	private boolean disableServiceCube = true;
	private boolean disableRelationships = true;
	private boolean disableManagerRelationships = true;
	private String roleStatus = null;
	private List<String> roleTypes = null;
	private List<String> owners = null;
	private List<Map<String, Object>> objectList = new ArrayList();
	private Iterator<Map<String, Object>> objects;
	private Map<String, Object> object;
	private String separator = "|";
	private static final Log roleMemberLogger = LogFactory
			.getLog(AppHealthCheckReport.class);
	@Override
	public void initialize(SailPointContext context, LiveReport report,
			Attributes<String, Object> arguments, String groupBy,
			List<Sort> sort) throws GeneralException {
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Initializing Role Members Report");
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Initializing Role Members Report with arguments="
				+ arguments);
		this.context = context;
		baseQueryOptions = new QueryOptions();
		this.appName = (String) arguments.get("appName");
		this.rolePrivileged = (String) arguments.get("rolePrivileged");
		/**
		 * See IF Accelerator Pack Features are Enabled
		 */
		roadFeaturesEnabled();
		joinerEnabled();
		this.roleStatus = (String) arguments.get("disabled");
		this.roleTypes = arguments.getList("type");
		this.owners = arguments.getList("owners");
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Initializing Role Members Report with  this.roleStatus="
				+ this.roleStatus);
		try {
			this.applicationList = arguments.getList("applications");
			prepare();
		} catch (SQLException e) {
			LogEnablement.isLogDebugEnabled(roleMemberLogger,"SQLException : " + e.getMessage());
		}
	}
	/**
	 * This is the method which prepares data for the report.
	 *
	 */
	private void prepare() throws GeneralException, SQLException {
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Entering prepare method..");
		Iterator<Object[]> bundleMemberIterator = null;
		try {
			Map<String, Object> itemMap = null;
			QueryOptions opsBundle = new QueryOptions();
			// <Parameter argument="applications"
			// property="profiles.application.id"/>
			if (null != this.applicationList && !this.applicationList.isEmpty())
				opsBundle.add(Filter.in("profiles.application.id",
						this.applicationList));
			// Iterate through the Roles and check for the members
			List listProperties = new ArrayList();
			listProperties.add("name");
			listProperties.add("disabled");
			listProperties.add("displayName");
			listProperties.add("type");
			listProperties.add("owner.id");
			bundleMemberIterator = context.search(Bundle.class, opsBundle,
					listProperties);
			if (null != bundleMemberIterator) {
				LogEnablement.isLogDebugEnabled(roleMemberLogger,"bundleMemberIterator=" + bundleMemberIterator);
				while (bundleMemberIterator.hasNext()) {
					Bundle bundle = null;
					Identity roleOwner = null;
					try {
						String bundleBusAppName = null;
						String bundleRoleStatus = null;
						String bundleDisplayName = null;
						String bundleDescription = null;
						String bundleRoleType = null;
						String bundleRoleOwner = null;
						String bundleBirthRight = null;
						String bundle1stLevelApprovers = null;
						String bundle2ndLevelApprovers = null;
						String bundleRolePrivileged = null;
						itemMap = new HashMap<String, Object>();
						Object[] rowBundle = bundleMemberIterator.next();
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"rowBundle=" + rowBundle);
						// Role Name
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"Bundle Details rowBundle[0]="
								+ rowBundle[0]);
						itemMap.put("name", rowBundle[0]);
						// Role Status
						bundleRoleStatus = String.valueOf(rowBundle[1]);
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"Role Disabled Flag from Bundle="
								+ bundleRoleStatus);
						itemMap.put("rolestatus", bundleRoleStatus);
						// Role Display Name
						bundleDisplayName = (String) rowBundle[2];
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"Bundle Display Name=" + bundleDisplayName);
						itemMap.put("roledisplayname", bundleDisplayName);
						// Role Type
						bundleRoleType = (String) rowBundle[3];
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"Role Type from Bundle=" + bundleRoleType);
						itemMap.put("roletype", bundleRoleType);
						// Role Owner
						bundleRoleOwner = (String) rowBundle[4];
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"Role Owner from Bundle="
								+ bundleRoleOwner);
						roleOwner = context.getObjectById(Identity.class,
								bundleRoleOwner);
						itemMap.put("roleowner", roleOwner.getName());
						if (!disableServiceCube && roleOwner != null) {
							itemMap.put("roleownerservicecube",
									roleOwner.getAttribute("serviceCube"));
						}
						// Get Bundle Object to get Description
						bundle = context.getObjectByName(Bundle.class,
								(String) rowBundle[0]);
						if (null != bundle) {
							// Role Description
							bundleDescription = bundle.getDescription("en_US");
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"Bundle Description="
									+ bundleDescription);
							itemMap.put("roledescription", bundleDescription);
							if (!disableLogicalApp) {
								bundleBusAppName = (String) bundle
										.getAttribute("appName");
								LogEnablement.isLogDebugEnabled(roleMemberLogger,"Business Application Name="
										+ bundleBusAppName);
								itemMap.put("businessapplication",
										bundleBusAppName);
							}
							if (!disableJoiner) {
								bundleBirthRight = (String) bundle
										.getAttribute("isBirthright");
								LogEnablement.isLogDebugEnabled(roleMemberLogger,"Bundle BirthRight="
										+ bundleBirthRight);
								itemMap.put("bundlebirthright",
										bundleBirthRight);
							}
							if (!disableBusinessApprovers) {
								bundle1stLevelApprovers = (String) bundle
										.getAttribute("roleBusApprovers");
								LogEnablement.isLogDebugEnabled(roleMemberLogger,"Bundle 1st Level Approvers="
										+ bundle1stLevelApprovers);
								itemMap.put("bundle1stlevelapprovers",
										bundle1stLevelApprovers);
							}
							if (!disable2ndBusinessApprovers) {
								bundle2ndLevelApprovers = (String) bundle
										.getAttribute("additionalRoleBusApprovers");
								LogEnablement.isLogDebugEnabled(roleMemberLogger,"Bundle 2nd Level Approvers="
										+ bundle2ndLevelApprovers);
								itemMap.put("bundle2ndlevelapprovers",
										bundle2ndLevelApprovers);
							}
							if (!disablePsA) {
								bundleRolePrivileged = (String) bundle
										.getAttribute("rolePrivileged");
								LogEnablement.isLogDebugEnabled(roleMemberLogger,"Role Privileged="
										+ bundleRolePrivileged);
								itemMap.put("rolePriv", bundleRolePrivileged);
							}
						}
						// Filter based on Business Application Name, Role
						// Status, Role Type
						boolean busAppNameMatch = true;
						boolean roleStatusMatch = true;
						boolean roleTypeMatch = true;
						boolean roleOwnerMatch = true;
						boolean rolePrivMatch = true;
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"Application Name Filter=" + this.appName);
						if (!disableLogicalApp) {
							if (null != this.appName && !this.appName.isEmpty()) {
								if (!this.appName
										.equalsIgnoreCase(bundleBusAppName)) {
									busAppNameMatch = false;
								}
							}
						}
						if (!disablePsA) {
							if (null != this.rolePrivileged
									&& !this.rolePrivileged.isEmpty()) {
								if (!this.rolePrivileged
										.equalsIgnoreCase(bundleRolePrivileged)) {
									rolePrivMatch = false;
								}
							}
						}
						if (null != this.roleStatus
								&& !this.roleStatus.isEmpty()) {
							if (!this.roleStatus
									.equalsIgnoreCase(bundleRoleStatus)) {
								roleStatusMatch = false;
							}
						}
						if (null != this.roleTypes && !this.roleTypes.isEmpty()) {
							if (!this.roleTypes.contains(bundleRoleType)) {
								roleTypeMatch = false;
							}
						}
						if (null != this.owners && !this.owners.isEmpty()) {
							if (!this.owners.contains(bundleRoleOwner)) {
								roleOwnerMatch = false;
							}
						}
						QueryOptions ops = new QueryOptions();
						ops.add(Filter.eq("value", rowBundle[0]));
						ops.add(Filter.or(Filter.eq("name", "detectedRoles"),
								Filter.eq("name", "assignedRoles")));
						LogEnablement.isLogDebugEnabled(roleMemberLogger,"Query Options=" + ops);
						Iterator<Object[]> memberIterator = null;
						try {
							memberIterator = context.search(
									IdentityEntitlement.class, ops, Arrays
											.asList("value",
													"identity.firstname",
													"identity.lastname",
													"identity.name", "name"));
							if (null != memberIterator
									&& memberIterator.hasNext()) {
								while (memberIterator.hasNext()) {
									Object[] row = memberIterator.next();
									LogEnablement.isLogDebugEnabled(roleMemberLogger,"row=" + row);
									itemMap.put("firstname", row[1]);
									itemMap.put("lastname", row[2]);
									itemMap.put("identity", row[3]);
									LogEnablement.isLogDebugEnabled(roleMemberLogger,"row=" + row[4]);
									if (((String) row[4])
											.equalsIgnoreCase("detectedRoles")) {
										itemMap.put("detectedorassigned",
												"Detected");
									} else {
										itemMap.put("detectedorassigned",
												"Assigned");
									}
									QueryOptions options = new QueryOptions();
									options.add(Filter.eq("name", row[0]));
									Identity identity = null;
									try {
										identity = context
												.getObjectByName(
														Identity.class,
														(String) row[3]);
										// TODO Remove the below
										LogEnablement.isLogDebugEnabled(roleMemberLogger,"Identity=" + identity);
										if (null != identity) {
											itemMap.put(
													"displayname",
													identity.getStringAttribute("displayName"));
											itemMap.put(
													"email",
													identity.getStringAttribute("email"));
											if (!disableServiceCube) {
												itemMap.put(
														"rolememberservicecube",
														identity.getStringAttribute("serviceCube"));
											}
											if (!disableRelationships) {
												itemMap.put(
														"rolememberrelationships",
														identity.getStringAttribute("relationships"));
											}
											if (!disableManagerRelationships) {
												itemMap.put(
														"rolemembermanagerrelationships",
														identity.getStringAttribute("mgrrelationships"));
											}
										}
										LogEnablement.isLogDebugEnabled(roleMemberLogger,"Item Map=" + itemMap);
									} finally {
										if (null != identity)
											context.decache(identity);
									}
									if (busAppNameMatch && roleStatusMatch
											&& roleTypeMatch && roleOwnerMatch
											&& rolePrivMatch) {
										LogEnablement.isLogDebugEnabled(roleMemberLogger,"Found a match with filter hence adding to the list");
										objectList.add(itemMap);
									} else {
										LogEnablement.isLogDebugEnabled(roleMemberLogger,"Did not match with filter hence not adding to the list");
									}
								}
							} else {
								// TODO Put into a seperate method
								if (busAppNameMatch && roleStatusMatch
										&& roleTypeMatch && roleOwnerMatch
										&& rolePrivMatch) {
									LogEnablement.isLogDebugEnabled(roleMemberLogger,"Found a match with filter hence adding to the list");
									objectList.add(itemMap);
								} else {
									LogEnablement.isLogDebugEnabled(roleMemberLogger,"Did not match with filter hence not adding to the list");
								}
							}
						} finally {
							if (null != memberIterator)
								Util.flushIterator(memberIterator);
						}
						objects = objectList.iterator();
					} finally {
						if (null != bundle)
							context.decache(bundle);
						if (null != roleOwner)
							context.decache(roleOwner);
					}
				}
			}
		} catch (Exception e) {
			LogEnablement.isLogDebugEnabled(roleMemberLogger,"SQLException : " + e.getMessage());
		} finally {
			if (null != bundleMemberIterator)
				Util.flushIterator(bundleMemberIterator);
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
		// TODO Remove the below
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Inside getFieldValue=" + fieldName);
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Inside getFieldValue entry set=" + this.object.entrySet());
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Inside getFieldValue key set=" + this.object.keySet());
		if (null != this.object)
			LogEnablement.isLogDebugEnabled(roleMemberLogger,"Inside getFieldValue=" + this.object.get(fieldName));
		return this.object != null ? this.object.get(fieldName) : null;
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
	/* Get Joiner Feature Trigger */
	private void joinerEnabled() throws GeneralException {
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Enter joinerEnabled ");
		boolean result = false;
		IdentityTrigger identityTrigger = null;
		try {
			identityTrigger = context.getObjectByName(IdentityTrigger.class,
					"JOINER FEATURE");
			if (identityTrigger != null) {
				if (!identityTrigger.isDisabled()) {
					this.disableJoiner = false;
					LogEnablement.isLogDebugEnabled(roleMemberLogger,"Joiner Enabled ");
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		} finally {
			if (identityTrigger != null)
				context.decache(identityTrigger);
		}
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"End joinerEnabled ");
	}
	/**
	 * Check Accelerator Pack Features are Enabled or Not
	 * 
	 * @throws GeneralException
	 */
	private void roadFeaturesEnabled() throws GeneralException {
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"Enter roadFeaturesEnabled ");
		boolean result = false;
		ObjectConfig objectConfig = null;
		ObjectConfig identityObjectConfig = null;
		try {
			identityObjectConfig = context.getObjectByName(ObjectConfig.class,
					"Identity");
			if (identityObjectConfig != null) {
				List<ObjectAttribute> objAttrList = identityObjectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null
								&& objAttr.getName() != null
								&& objAttr.getName().equalsIgnoreCase(
										"serviceCube")) {
							this.disableServiceCube = false;
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"Service Cube Enabled ");
						}
						if (objAttr != null
								&& objAttr.getName() != null
								&& objAttr.getName().equalsIgnoreCase(
										"relationships")) {
							this.disableRelationships = false;
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"Relationships Enabled ");
						}
						if (objAttr != null
								&& objAttr.getName() != null
								&& objAttr.getName().equalsIgnoreCase(
										"mgrrelationships")) {
							this.disableManagerRelationships = false;
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"Manager Relationships Enabled ");
						}
					}
				}
			}
			objectConfig = context
					.getObjectByName(ObjectConfig.class, "Bundle");
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null
								&& objAttr.getName() != null
								&& objAttr.getName().equalsIgnoreCase(
										"rolePrivileged")) {
							this.disablePsA = false;
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"PSA Enabled ");
						}
						if (objAttr != null
								&& objAttr.getName() != null
								&& objAttr.getName()
										.equalsIgnoreCase("appName")) {
							this.disableLogicalApp = false;
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"Logical App Enabled ");
						}
						if (objAttr != null
								&& objAttr.getName() != null
								&& objAttr.getName().equalsIgnoreCase(
										"roleBusApprovers")) {
							this.disableBusinessApprovers = false;
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"Business Approvers 1st Level Enabled ");
						}
						if (objAttr != null
								&& objAttr.getName() != null
								&& objAttr.getName().equalsIgnoreCase(
										"additionalRoleBusApprovers")) {
							this.disable2ndBusinessApprovers = false;
							LogEnablement.isLogDebugEnabled(roleMemberLogger,"Business Approvers 2nd Level Enabled ");
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		} finally {
			if (objectConfig != null)
				context.decache(objectConfig);
			if (identityObjectConfig != null)
				context.decache(identityObjectConfig);
		}
		LogEnablement.isLogDebugEnabled(roleMemberLogger,"End roadFeaturesEnabled ");
	}
	@Override
	public void setLimit(int startRow, int pageSize) {
		this.startRow = startRow;
		this.pageSize = pageSize;
	}
}
