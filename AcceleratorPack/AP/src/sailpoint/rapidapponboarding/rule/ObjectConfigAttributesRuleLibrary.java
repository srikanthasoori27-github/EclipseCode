/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.util.List;
import org.apache.log4j.Logger;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;
/**
 * Object Config Util
 * @author rohit.gupta
 *
 */
public class ObjectConfigAttributesRuleLibrary {
	private static String isBusinessApp = null;
	private static String isJoinerBirthright = null;
	private static String isPrivileged = null;
	private static String isLogicalApp = null;
	private static String isManualOverride = null;
	private static String isBusinessApprover = null;
	private static String validatePrivelegedAccount = null;
	private static String skipManagerApprovalPriveleged=null;
	private static String serviceAccountEnabled = null;
	private static String identityTypeEnabled = null;
	private static String personaEnabled = null;
	private static String ticketEnabled = null;
	private static String dependencyEnabled = null;
	private static String passwordSyncEnabled = null;
	private static String aggregationEnabled = null;
	private static String hardPolicyEnabled = null;
	private static String softPolicyEnabled = null;
	private static String privilegedEntAttrExpression = null;
	private static String privilegedAccountAttrExpression = null;
	private static String beforemoverPrimaryaccount = null;
	private static String primarySyncaccounts = null;
	private static String primarypasswordSyncaccounts = null;
	private static String rehirePrimaryaccount = null;
	private static String enablepswexpNotification = null;
	private static String authoritativeNonEmployee=null;
	private static String authoritativeEmployee=null;
	private static String authoritativeServiceAccount=null;
	private static String serviceAccountAttrExpression=null;
	private static String serviceAccountCubeNameAttr=null;
	private static String serviceAccountAppendAppName=null;
	private static String rolePrivileged=null;
	private static String linkPrivilegedAccountType=null;
	private static String rolePrivilegedAccTypes = null;
	private static String entPrivileged=null;
	private static String entPrivilegedAccTypes=null;
	private static String ctrMgr=null;
	private static String ctrExp=null;
	private static String ctrProfid=null;
	private static String ctrDptid=null;
	private static String ctrVendorid=null;
	private static String ctrPrfmgrdpids=null;
	private static String ctrAcctadmdpids=null;
	private static String ctrAcctspndpids=null;
	/**
	 * Sponsor OWNED DEPARTMENT IDS
	 */
	public static final String CTRPSPRDEPIDS = "ctrAcctspndpids";	
	/**
	 * Admin OWNED DEPARTMENT IDS
	 */
	public static final String CTRPADMINDEPIDS = "ctrAcctadmdpids";
	/**
	 * Profile Manager OWNED DEPARTMENT IDS
	 */
	public static final String CTRPROFMGRDEPIDS = "ctrPrfmgrdpids";
	/**
	 * Vendor Constant
	 */
	public static final String CTRVENID = "ctrVendorid";
	/**
	 * Department Constant
	 */
	public static final String CTRDEPID = "ctrDptid";
	/**
	 * Profile Constant
	 */
	public static final String CTRPROFID = "ctrProfid";
	/**
	 * Contract Manager Constant
	 */
	public static final String CTRMGR = "ctrMgr";
	/**
	 * Contract Date Constant
	 */
	public static final String CTREXP = "ctrexpDt";
	/**
	 * Identity Type Constant
	 */
	public static final String IDENTITYTYPE = "type";
	/**
	 * Business Application Extended Attribute Constant
	 */
	public static final String BUSAPP = "busApp";
	/**
	 * Manual Override Extended Attribute Constant
	 */
	private static final String MANUALOVERRIDE = "manualOverride";
	/**
	 * Logical Application Extended Attribute Constant
	 */
	public static final String LOGICALAPPROLE = "appName";
	/**
	 * Logical Application Extended Attribute Constant
	 */
	public static final String LOGICALAPP = "entAppName";
	/**
	 *Link Extended Attribute Constant
	 */
	public static final String LINKACCOUNTTYPE = "apaccountType";
	/**
	 *Bundle Extended Attribute Constant
	 */
	public static final String ROLEPRIVILEGED = "rolePrivileged";
	/**
	 *Bundle Extended Attribute Constant
	 */
	public static final String ROLEPRIVILEGEDACCTYPES = "apaccountType";
	/**
	 *ManagedAttribute Extended Attribute Constant
	 */
	public static final String ENTPRIVILEGED = "entPrivileged";
	/**
	 *ManagedAttribute Extended Attribute Constant
	 */
	public static final String ENTPRIVILEGEDCCTYPES = "apaccountType";
	/**
	 * Joiner Extended Attribute Constant
	 */
	private static final String JOINERBIRTHRIGHT = "isBirthright";
	/**
	 * Privileged Extended Attribute Constant
	 */
	private static final String PRIVILEGED = "psAccount";
	/**
	 * Privileged Extended Attribute Constant
	 */
	public static final String VALIDATEPRIVACCOUNT = "enablepsaValidation";
	/**
	 * Privileged Extended Attribute Constant
	 */
	private static final String SKIPMANAGERAPPROVALPRIV = "omitmanagerPrivgaccess";
	/**
	 * Business Approver Attribute Constant
	 */
	private static final String BUSAPPROVER = "entBusApprovers";
	/**
	 * Service Cube Attribute Constant
	 */
	private static final String SERVICEACCOUNT = "serviceCube";
	/**
	 *Ticket Options Attribute Constant
	 */
	private static final String TICKETOPTION = "afterTicketProvisioningOptions";
	/**
	 *New Account Entitlement Dependency Attribute Constant
	 */
	private static final String DEPENDENCY = "accountCreateEntitlements";
	/**
	 *Password Sync Attribute Constant
	 */
	private static final String PASSWORDSYNC = "targetpasswordSync";
	/**
	 *Aggregation Attribute Constant
	 */
	private static final String AGGREGATION = "accountCorrelationAttrExpression";
	/**
	 *Policy Violation Attribute Constant
	 */
	private static final String SOFT = "softPolicyviolations";
	/**
	 *Policy Violation Attribute Constant
	 */
	private static final String HARD = "hardPolicyviolations";
	/**
	 *Aggregation Entitlement Constant
	 */
	private static final String PRIVILEGEDENTATTREXPRESSION = "privilegedEntAttrExpression";
	/**
	 *Aggregation Account Constant
	 */
	public static final String PRIVILEGEDACCOUNTATTREXPRESSION = "privilegedAccountAttrExpression";
	/**
	 *Mover Constant
	 */
	private static final String BEFOREMOVERPRIMARACCOUNT = "beforemoverPrimaryaccount";
	/**
	 *Attribute Sync Constant
	 */
	private static final String PRIMARYATTRSYNCACCOUNTS = "primarySyncaccounts";
	/**
	 *Password Sync Constant
	 */
	private static final String PRIMARYPASSWORDSYNCACCOUNTS = "primarypasswordSyncaccounts";
	/**
	 *Joiner Constant
	 */
	private static final String REHIREPRIMARYACCOUNT = "rehirePrimaryaccount";
	/**
	 * Privileged Account Password Expirations
	 */
	private static final String PSWDEXPNOTIFICATIONS = "enablepswexpNotification";
	/**
	 * Authoritative Repository Non-Employee Constant
	 */
	private static final String AUTHNONEMPLOYEE="authoritativeNonEmployee";
	/**
	 * Authoritative Repository Employee Constant
	 */
	private static final String AUTHEMPLOYEE="authoritativeEmployee";
	/**
	 * Service Account Expression Constant
	 */
	private static final String SERVICEACCOUNTEXPRESSION="serviceAccountAttrExpression";
	/**
	 *  Service Account Repository Constant
	 */
	private static final String AUTHSERVICEACCOUNT="authoritativeServiceAccount";
	/**
	 * Service Account Cube Name Attribute
	 */
	private static final String SERVICEACCOUNTCUBENAMEATTR = "apServiceAccountUniqueName";
	/**
	 * Service Account Cube Name Append App Name
	 */
	private static final String SERVICEACCOUNTCUBENAMEAPPENDAPPNAME = "apAppendApplicationName";
	/**
	 * Persona
	 */
	private static final String PERSONA = "relationships";
	/**
	 * Check Business Application Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrBusAppEnabled(SailPointContext context)
			throws GeneralException {
		if (isBusinessApp != null) {
			return isBusinessApp;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr.getName().equalsIgnoreCase(
									ObjectConfigAttributesRuleLibrary.BUSAPP)) {
								isBusinessApp = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return isBusinessApp;
	}
	/**
	 * Check Manual Override Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrManualOverrideEnabled(
			SailPointContext context) throws GeneralException {
		if (isManualOverride != null) {
			return isManualOverride;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = ManagedAttribute.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.MANUALOVERRIDE)) {
								isManualOverride = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return isManualOverride;
	}
	/**
	 * Check Logical Application Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrLogicalAppEnabled(SailPointContext context)
			throws GeneralException {
		if (isLogicalApp != null) {
			return isLogicalApp;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = ManagedAttribute.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.LOGICALAPP)) {
								isLogicalApp = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return isLogicalApp;
	}
	/**
	 * Check Joiner Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrJoinerBirthrightAppDisabled(
			SailPointContext context) throws GeneralException {
		if (isJoinerBirthright != null) {
			return isJoinerBirthright;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = ManagedAttribute.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.JOINERBIRTHRIGHT)) {
								isJoinerBirthright = "False";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return isJoinerBirthright;
	}
	/**
	 * Check Privileged Account Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPrivilegedAppEnabled(
			SailPointContext context) throws GeneralException {
		if (isPrivileged != null) {
			return isPrivileged;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Link.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PRIVILEGED)) {
								isPrivileged = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return isPrivileged;
	}
	/**
	 * Check Business Approver Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrBusApproverEnabled(
			SailPointContext context) throws GeneralException {
		if (isBusinessApprover != null) {
			return isBusinessApprover;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = ManagedAttribute.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.BUSAPPROVER)) {
								isBusinessApprover = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return isBusinessApprover;
	}
	/**
	 * Privileged Manager Approval Skip Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrSkipManagerApprovalPrivelegedEnabled(
			SailPointContext context) throws GeneralException {
		if (skipManagerApprovalPriveleged != null) {
			return skipManagerApprovalPriveleged;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.SKIPMANAGERAPPROVALPRIV)) {
								skipManagerApprovalPriveleged = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return skipManagerApprovalPriveleged;
	}
	/**
	 * Check Privileged Account Validation Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPrivAccValEnabled(
			SailPointContext context) throws GeneralException {
		if (validatePrivelegedAccount != null) {
			return validatePrivelegedAccount;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.VALIDATEPRIVACCOUNT)) {
								validatePrivelegedAccount = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return validatePrivelegedAccount;
	}
	/**
	 * Check Identity Contractor Vendor Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrVendorEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrVendorid != null) {
			return ctrVendorid;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTRVENID)) {
								ctrVendorid = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrVendorid;
	}
	/**
	 * Check Identity Contractor Manager Owned Depart Ids Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrMgrDepIdsEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrPrfmgrdpids != null) {
			return ctrPrfmgrdpids;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTRPROFMGRDEPIDS)) {
								ctrPrfmgrdpids = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrPrfmgrdpids;
	}
	/**
	 * Check Identity Contractor Admin Owned Depart Ids Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrAdminDepIdsEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrAcctadmdpids != null) {
			return ctrAcctadmdpids;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTRPADMINDEPIDS)) {
								ctrAcctadmdpids = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrAcctadmdpids;
	}
	/**
	 * Check Identity Contractor Sponsor Owned Depart Ids Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrSponsorDepIdsEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrAcctspndpids != null) {
			return ctrAcctspndpids;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTRPSPRDEPIDS)) {
								ctrAcctspndpids = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrAcctspndpids;
	}
	/**
	 * Check Identity Contractor Department Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrDepartmentEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrDptid != null) {
			return ctrDptid;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTRDEPID)) {
								ctrDptid = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrDptid;
	}
	/**
	 * Check Identity Contractor Profile Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrProfileEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrProfid != null) {
			return ctrProfid;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTRPROFID)) {
								ctrProfid = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrProfid;
	}
	/**
	 * Check Identity Contractor Expiration Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrExpEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrExp != null) {
			return ctrExp;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTREXP)) {
								ctrExp = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrExp;
	}
	/**
	 * Check Identity Contractor Mgr Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrCtrManagerEnabled(
			SailPointContext context) throws GeneralException {
		if (ctrMgr != null) {
			return ctrMgr;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.CTRMGR)) {
								ctrMgr = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ctrMgr;
	}
	/**
	 * Check Identity Type Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrIdentityTypeEnabled(
			SailPointContext context) throws GeneralException {
		if (identityTypeEnabled != null) {
			return identityTypeEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.IDENTITYTYPE)) {
								identityTypeEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return identityTypeEnabled;
	}
	/**
	 * Check Persona Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPersonaEnabled(
			SailPointContext context) throws GeneralException {
		if (personaEnabled != null) {
			return personaEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PERSONA)) {
								personaEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return personaEnabled;
	}
	/**
	 * Check Service Account Validation Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrServiceAccountEnabled(
			SailPointContext context) throws GeneralException {
		if (serviceAccountEnabled != null) {
			return serviceAccountEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Identity.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.SERVICEACCOUNT)) {
								serviceAccountEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return serviceAccountEnabled;
	}
	/**
	 * Check Ticketing Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrTicketEnabled(
			SailPointContext context) throws GeneralException {
		if (ticketEnabled != null) {
			return ticketEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.TICKETOPTION)) {
								ticketEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return ticketEnabled;
	}
	/**
	 * Check New Account Entitlement Dependency Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrEntitlementDependencyEnabled(
			SailPointContext context) throws GeneralException {
		if (dependencyEnabled != null) {
			return dependencyEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.DEPENDENCY)) {
								dependencyEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return dependencyEnabled;
	}
	/**
	 * Check Password Synchronization Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPasswordSyncEnabled(
			SailPointContext context) throws GeneralException {
		if (passwordSyncEnabled != null) {
			return passwordSyncEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PASSWORDSYNC)) {
								passwordSyncEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return passwordSyncEnabled;
	}
	/**
	 * Check Aggregation Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrAggregationEnabled(
			SailPointContext context) throws GeneralException {
		if (aggregationEnabled != null) {
			return aggregationEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.AGGREGATION)) {
								aggregationEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return aggregationEnabled;
	}
	/**
	 * Check Soft Policy Violation Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrSoftPolicyEnabled(
			SailPointContext context) throws GeneralException {
		if (softPolicyEnabled != null) {
			return softPolicyEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.SOFT)) {
								softPolicyEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return softPolicyEnabled;
	}
	/**
	 * Check Hard Policy Violation Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrHardPolicyEnabled(
			SailPointContext context) throws GeneralException {
		if (hardPolicyEnabled != null) {
			return hardPolicyEnabled;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.HARD)) {
								hardPolicyEnabled = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return hardPolicyEnabled;
	}
	/**
	 * Check Before Mover Primary Account Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrBefMoverPrimAcctEnabled(
			SailPointContext context) throws GeneralException {
		if (beforemoverPrimaryaccount != null) {
			return beforemoverPrimaryaccount;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.BEFOREMOVERPRIMARACCOUNT)) {
								beforemoverPrimaryaccount = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return beforemoverPrimaryaccount;
	}
	/**
	 * Check Password Sync Primary Account Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPasswordSyncPrimaryAcctEnabled(
			SailPointContext context) throws GeneralException {
		if (primarypasswordSyncaccounts != null) {
			return primarypasswordSyncaccounts;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PRIMARYPASSWORDSYNCACCOUNTS)) {
								primarypasswordSyncaccounts = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return primarypasswordSyncaccounts;
	}
	/**
	 * Check Attribute Sync Primary Account Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrSyncPrimaryAcctEnabled(
			SailPointContext context) throws GeneralException {
		if (primarySyncaccounts != null) {
			return primarySyncaccounts;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PRIMARYATTRSYNCACCOUNTS)) {
								primarySyncaccounts = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return primarySyncaccounts;
	}
	/**
	 * Joiner Rehire Primary Account Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrJoinerRehirePrimAcctEnabled(
			SailPointContext context) throws GeneralException {
		if (rehirePrimaryaccount != null) {
			return rehirePrimaryaccount;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.REHIREPRIMARYACCOUNT)) {
								rehirePrimaryaccount = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return rehirePrimaryaccount;
	}
	/**
	 * Aggregation Privileged Account Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAggrPrivAcctEnabled(
			SailPointContext context) throws GeneralException {
		if (privilegedAccountAttrExpression != null) {
			return privilegedAccountAttrExpression;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PRIVILEGEDACCOUNTATTREXPRESSION)) {
								privilegedAccountAttrExpression = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return privilegedAccountAttrExpression;
	}
	/**
	 * Aggregation Privileged Entitlement Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAggrPrivEntEnabled(
			SailPointContext context) throws GeneralException {
		if (privilegedEntAttrExpression != null) {
			return privilegedEntAttrExpression;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PRIVILEGEDENTATTREXPRESSION)) {
								privilegedEntAttrExpression = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return privilegedEntAttrExpression;
	}
	/**
	 * Privileged Password Expiration Notifications Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedPrivPwdExpEnabled(
			SailPointContext context) throws GeneralException {
		if (enablepswexpNotification != null) {
			return enablepswexpNotification;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.PSWDEXPNOTIFICATIONS)) {
								enablepswexpNotification = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return enablepswexpNotification;
	}
	/**
	 * Authoritative Non Employee Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrAuthEmployeeEnabled(
			SailPointContext context) throws GeneralException {
		if (authoritativeEmployee != null) {
			return authoritativeEmployee;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.AUTHEMPLOYEE)) {
								authoritativeEmployee = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return authoritativeEmployee;
	}
	/**
	 * Authoritative Non Employee Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrAuthNonEmployeeEnabled(
			SailPointContext context) throws GeneralException {
		if (authoritativeNonEmployee != null) {
			return authoritativeNonEmployee;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.AUTHNONEMPLOYEE)) {
								authoritativeNonEmployee = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return authoritativeNonEmployee;
	}
	/**
	 * Authoritative Service Account Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrAuthServiceAccountEnabled(
			SailPointContext context) throws GeneralException {
		if (authoritativeServiceAccount != null) {
			return authoritativeServiceAccount;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.AUTHSERVICEACCOUNT)) {
								authoritativeServiceAccount = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return authoritativeServiceAccount;
	}
	/**
	 *  Service Account Cube Name Append App Name Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrServiceAccountAppendAppNameAttrEnabled(
			SailPointContext context) throws GeneralException {
		if (serviceAccountAppendAppName != null) {
			return serviceAccountAppendAppName;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.SERVICEACCOUNTCUBENAMEAPPENDAPPNAME)) {
								serviceAccountAppendAppName = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return serviceAccountAppendAppName;
	}
	/**
	 *  Service Account Cube Name Attribute Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrServiceAccountCubeNameAttrEnabled(
			SailPointContext context) throws GeneralException {
		if (serviceAccountCubeNameAttr != null) {
			return serviceAccountCubeNameAttr;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.SERVICEACCOUNTCUBENAMEATTR)) {
								serviceAccountCubeNameAttr = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return serviceAccountCubeNameAttr;
	}
	/**
	 *  Service Account Expression Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrServiceAccountExpressEnabled(
			SailPointContext context) throws GeneralException {
		if (serviceAccountAttrExpression != null) {
			return serviceAccountAttrExpression;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Application.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.SERVICEACCOUNTEXPRESSION)) {
								serviceAccountAttrExpression = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return serviceAccountAttrExpression;
	}
	/**
	 *  Extended Attribute Privileged Entitlement Account Types Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPrivRoleAccTypesEnabled(
			SailPointContext context) throws GeneralException {
		if ( rolePrivilegedAccTypes!= null) {
			return rolePrivilegedAccTypes;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Bundle.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.ROLEPRIVILEGEDACCTYPES)) {
								rolePrivilegedAccTypes = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return rolePrivilegedAccTypes;
	}
	/**
	 *  Extended Attribute Privileged Role Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPrivRoleEnabled(
			SailPointContext context) throws GeneralException {
		if ( rolePrivileged!= null) {
			return rolePrivileged;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Bundle.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.ROLEPRIVILEGED)) {
								rolePrivileged = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return rolePrivileged;
	}
	/**
	 *  Extended Attribute Privileged Link Account Type Enabled
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrLinkPrivAccountTypeEnabled(
			SailPointContext context) throws GeneralException {
		if ( linkPrivilegedAccountType!= null) {
			return linkPrivilegedAccountType;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = Link.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.LINKACCOUNTTYPE)) {
								linkPrivilegedAccountType = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return linkPrivilegedAccountType;
	}
	/**
	 *  Extended Attribute Privileged Entitlement Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPrivEntEnabled(
			SailPointContext context) throws GeneralException {
		if ( entPrivileged!= null) {
			return entPrivileged;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = ManagedAttribute.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.ENTPRIVILEGED)) {
								entPrivileged = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return entPrivileged;
	}
	/**
	 *  Extended Attribute Privileged Entitlement Account Types Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String extendedAttrPrivEntAccTypesEnabled(
			SailPointContext context) throws GeneralException {
		if ( entPrivilegedAccTypes!= null) {
			return entPrivilegedAccTypes;
		}
		ObjectConfig objectConfig = null;
		try {
			objectConfig = ManagedAttribute.getObjectConfig();
			if (objectConfig != null) {
				List<ObjectAttribute> objAttrList = objectConfig
						.getObjectAttributes();
				if (objAttrList != null && objAttrList.size() > 0) {
					for (ObjectAttribute objAttr : objAttrList) {
						if (objAttr != null && objAttr.getName() != null) {
							if (objAttr
									.getName()
									.equalsIgnoreCase(
											ObjectConfigAttributesRuleLibrary.ENTPRIVILEGEDCCTYPES)) {
								entPrivilegedAccTypes = "True";
								break;
							}
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new GeneralException(ex.getMessage());
		}
		return entPrivilegedAccTypes;
	}
	/**
	 *  Extended Attribute Privileged Entitlement and Role Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static boolean  extendedAttrPrivRoleEntEnabled(
			SailPointContext context) throws GeneralException 
	{
		String rolePrivileged=null;
		String entPrivileged=null;
		rolePrivileged=extendedAttrPrivRoleEnabled(context);
		entPrivileged=extendedAttrPrivEntEnabled(context);
		if(rolePrivileged!=null &&  entPrivileged!=null && entPrivileged.equalsIgnoreCase("TRUE") && rolePrivileged.equalsIgnoreCase("TRUE"))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	/**
	 *  Extended Attribute Privileged Entitlement and Role Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static boolean extendedAttrPrivRoleEntAccTypesEnabled(
			SailPointContext context) throws GeneralException 
	{
		String roleAccTypPrivileged=null;
		String entAccTypePrivileged=null;
		roleAccTypPrivileged=extendedAttrPrivRoleAccTypesEnabled(context);
		entAccTypePrivileged=extendedAttrPrivEntAccTypesEnabled(context);
		if(roleAccTypPrivileged!=null &&  entAccTypePrivileged!=null && entAccTypePrivileged.equalsIgnoreCase("TRUE") && roleAccTypPrivileged.equalsIgnoreCase("TRUE"))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	/**
	 *  Extended Attribute Privileged Entitlement and Role Enabled
	 * 
	 * @param context
	 * @throws GeneralException
	 */
	public static String  extendedAttrPrivRoleEntitlementEnabled(
			SailPointContext context) throws GeneralException 
	{
		String rolePrivileged=null;
		String entPrivileged=null;
		rolePrivileged=extendedAttrPrivRoleEnabled(context);
		entPrivileged=extendedAttrPrivEntEnabled(context);
		if(rolePrivileged!=null &&  entPrivileged!=null && entPrivileged.equalsIgnoreCase("TRUE") && rolePrivileged.equalsIgnoreCase("TRUE"))
		{
			return "True";
		}
		else
		{
			return "False";
		}
	}
}
