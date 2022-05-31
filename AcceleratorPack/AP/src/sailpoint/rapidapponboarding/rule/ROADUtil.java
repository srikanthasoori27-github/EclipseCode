/*
Copyright © 2018 SailPoint Technologies, Inc. All Rights Reserved.
All logos, text, content, and works of authorship, including but not limited to underlying code, programming or scripting language, designs, and/or graphics,
that are used and/or depicted herein are protected under United States and international copyright and trademark laws and treaties,
and may not be used or reproduced without the prior express written permission of SailPoint Technologies, Inc.
 */
package sailpoint.rapidapponboarding.rule;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.CertificationScheduler;
import sailpoint.api.IdentityArchiver;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.api.Workflower;
import sailpoint.object.Alert;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.ClassLists;
import sailpoint.object.Comment;
import sailpoint.object.Configuration;
import sailpoint.object.Custom;
import sailpoint.object.Duration;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Form.Section;
import sailpoint.object.Form.Type;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.IdentityExternalAttribute;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.NotificationConfig;
import sailpoint.object.NotificationConfig.ReminderConfig;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.Resolver;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Tag;
import sailpoint.object.Target;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.Template;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.rapidapponboarding.logger.LogEnablement;
import sailpoint.server.Servicer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.workflow.WorkflowContext;
/**
 * Accelerator Pack Util
 * 
 * @author rohit.gupta
 *
 */
public class ROADUtil {
    private static Log roadUtilLogger = LogFactory.getLog("rapidapponboarding.rules");
    private static Custom custom = null;
    private static Custom customGlobal = null;
    private static Custom customMover = null;
    private static Custom customNativeChange = null;
    private static Custom customEpic = null;
    private static final String FEATURE = "featuresString";
    /**
     * Epic Constants
     */
    private static final String EPICSERCUSTOM = "Custom-Epic-SER-Settings";
    /**
     * Ticket Constants
     */
    private static final String TICKETSETTINGS = "TICKET SETTINGS";
    private static final String TICKETCUSTOM = "Custom-Ticket-Settings";
    /**
     * Mover Constants
     */
    private static final String MOVERSETTINGS = "MOVER SETTINGS";
    private static final String MOVERCUSTOM = "Custom-Mover-Settings";
    private static final String MOVERFEATURE = "MOVER FEATURE";
    private static final String MOVERSETTINGMAPPINGS = "certificationSettingsMappings";
    /**
     * Native Change Constants
     */
    private static final String NATIVECHANGESETTINGS = "NATIVE CHANGE SETTINGS";
    private static final String NATIVECHANGECUSTOM = "Custom-Native-Change-Settings";
    private static final String NATIVECHANGEFEATURE = "NATIVE CHANGE DETECTION FEATURE";
    private static final String NATIVECHANGESETTINGMAPPINGS = "certificationSettingsMappings";
    /**
     * Generic
     */
    private static final String DEFAULTFEATURE = "REQUEST MANAGER FEATURE";
    public static final String DEFAULTWORKFLOW = "Workflow-FrameWork-Wrapper";
    public static final String DEFAULTWORKFLOWCREATE = "Workflow-FrameWork-Wrapper-Create";
    public static final String DEFAULTWORKFLOWEDIT = "Workflow-FrameWork-Wrapper-Edit";
    private static final String SNAPSHOT = "requestSnapshot";
    private static final String DISABLED = "IIQDisabled";
    private static final String LOCKED = "IIQLocked";
    static final String REQUESTID = "identityRequestId";
    private static final String APPROVALSUMMARY = "approvalSummaries";
    private static final Object INTERCEPTORPLANRULEKEY = "interceptorPlanRule";
    private static final Object INTERCEPTORPROJECTRULEKEY = "interceptorProjectRule";
    /**
     * Email
     */
    public static final String DISABLESTATICEMAILCONTENT = "apDisableStaticManagerEmailContent";
    /**
     * Persona
     */
    private static List personaList = new ArrayList();
    /**
     * String Comparison for Self Service Friendly Drop Down Operations
     * 
     * @param str
     * @param operation
     * @param strValue
     * @return
     */
    public static int executeStringComparison(Object str, String operation, String strValue) {
        int matches = 0;
        String stStr = (String) str;
        if (stStr != null && stStr.length() > 0 && operation != null && operation.length() > 0 && strValue != null
                && strValue.length() > 0) {
            stStr = stStr.toUpperCase().trim();
            strValue = strValue.toUpperCase().trim();
            if (stStr != null && strValue != null) {
                if (operation.equalsIgnoreCase("STARTSWITH")) {
                    if (stStr.startsWith(strValue)) {
                        matches = matches + 1;
                    }
                } else if (operation.equalsIgnoreCase("CONTAINS")) {
                    if (stStr.contains(strValue)) {
                        matches = matches + 1;
                    }
                } else if (operation.equalsIgnoreCase("ENDSWITH")) {
                    if (stStr.endsWith(strValue)) {
                        matches = matches + 1;
                    }
                } else if (operation.equalsIgnoreCase("EQUALS")) {
                    if (stStr.equalsIgnoreCase(strValue)) {
                        matches = matches + 1;
                    }
                }
            }
        }
        return matches;
    }
    /**
     * Java Regular Expression for Aggregation and Leaver(Exception Entitlements)
     * 
     * @param regex
     * @param text
     * @return
     */
    public static int executeRegex(String regex, Object text) {
        int matches = 0;
        if (regex != null && text != null && text instanceof String) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher((String) text);
            while (matcher.find()) {
                matches++;
            }
        } else if (regex != null && text != null && text instanceof List) {
            List<String> textList = (List) text;
            if (textList.size() > 0) {
                for (String textStr : textList) {
                    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(textStr);
                    while (matcher.find()) {
                        matches++;
                    }
                    if (matches >= 1) {
                        // Let's do it only for one list item, if match found, break
                        break;
                    }
                }
            }
        }
        return matches;
    }
    /**
     * Get Common Name from Distinguished Name
     * 
     * @param saText
     * @return
     */
    public static String getDNFormattedValue(String saText) {
        if (saText != null && saText.contains(",") && saText.contains("=")) {
            String saTextUpper = saText.toUpperCase();
            String[] saTextUpperArr = saTextUpper.split(",");
            for (String saTextUpperSplit : saTextUpperArr) {
                if (saTextUpperSplit.contains("CN=")) {
                    String[] cnSplits = saTextUpperSplit.trim().split("=");
                    if (cnSplits != null && cnSplits.length == 2 && cnSplits[1] != null) {
                        return cnSplits[1].trim();
                    }
                }
            }
        }
        return saText;
    }
    /**
     * Get Global Settings
     * 
     * @return custom
     * @throws GeneralException
     */
    synchronized static Custom getCustomGlobal(SailPointContext context) throws GeneralException {
        // Adding second check to avoid re-initialization when the multiple
        // threads enter into the above if condition and waiting for this to be
        // initialized
        if (null == customGlobal || null == customGlobal.getAttributes()
                || null == customGlobal.getAttributes().get(WrapperRuleLibrary.ACCELERATORPACKGLOBALSETTINGS)
                || !WrapperRuleLibrary.GLOBALROADCUSTOM.equalsIgnoreCase(customGlobal.getName())) {
            customGlobal = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
        } else {
            Date dbModified = Servicer.getModificationDate(context, customGlobal);
            if (Util.nullSafeCompareTo(dbModified, customGlobal.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning updated customGlobal object");
                customGlobal = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning previously initialized customGlobal object");
            }
        }
        return customGlobal;
    }
    /**
     * Get Custom Global Map
     * 
     * @param context
     * @return
     * @throws GeneralException
     */
    public static Map getCustomGlobalMap(SailPointContext context) throws GeneralException {
        // Get the Custom Global Object
        Map map = null;
        customGlobal = getCustomGlobal(context);
        if (customGlobal != null && customGlobal.getAttributes() != null) {
            map = (Map) customGlobal.getAttributes().get(WrapperRuleLibrary.ACCELERATORPACKGLOBALSETTINGS);
        }
        return map;
    }
    /**
     * Get Provisioning SLA's
     * 
     * @param key
     * @return Integer
     * @throws GeneralException
     */
    public static Integer getProvisioningSLA(SailPointContext context, String key) throws GeneralException {
        // Get the Custom Global Object
        customGlobal = getCustomGlobal(context);
        if (customGlobal != null && customGlobal.getAttributes() != null) {
            Map map = (Map) customGlobal.getAttributes().get(WrapperRuleLibrary.ACCELERATORPACKGLOBALSETTINGS);
            if (map != null && map.containsKey(key)) {
                if (map.get(key) != null && map.get(key) instanceof String) {
                    return Integer.valueOf((String) map.get(key));
                } else {
                    return (Integer) map.get(key);
                }
            }
        }
        return null;
    }
    /**
     * Get Global Definition Attribute Value
     * 
     * @param key
     * @return Integer
     * @throws GeneralException
     */
    public static Object getGlobalDefinitionAttribute(SailPointContext context, String key) throws GeneralException {
        // Get the Custom Global Object
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Start getGlobalDefinitionAttribute..");
        customGlobal = getCustomGlobal(context);
        if (customGlobal != null && customGlobal.getAttributes() != null) {
            Map map = (Map) customGlobal.getAttributes().get(WrapperRuleLibrary.ACCELERATORPACKGLOBALSETTINGS);
            if (map != null && map.containsKey(key)) {
                if (map.get(key) != null) {
                    Object value = map.get(key);
                    LogEnablement.isLogDebugEnabled(roadUtilLogger,
                            "End getGlobalDefinitionAttribute..Key.." + key + "..Value..." + value);
                    return value;
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End getGlobalDefinitionAttribute..No Key");
        return null;
    }
    /**
     * Get Common EPIC SER Settings
     * 
     * @return customEpic
     * @throws GeneralException
     */
    synchronized static Custom getCustomEpic(SailPointContext context) throws GeneralException {
        // Adding second check to avoid re-initialization when the multiple
        // threads enter into the above if condition and waiting for this to be
        // initialized
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter getCustomEpic");
        if (null == customEpic || null == customEpic.getAttributes()
                || !ROADUtil.EPICSERCUSTOM.equalsIgnoreCase(customEpic.getName())) {
            customEpic = context.getObjectByName(Custom.class, ROADUtil.EPICSERCUSTOM);
        } else {
            Date dbModified = Servicer.getModificationDate(context, customEpic);
            if (Util.nullSafeCompareTo(dbModified, customEpic.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning updated customEpic object");
                customEpic = context.getObjectByName(Custom.class, ROADUtil.EPICSERCUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning previously initialized customEpic object");
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End getCustomEpic");
        return customEpic;
    }
    /**
     * Get Common Ticket Settings
     * 
     * @return custom
     * @throws GeneralException
     */
    synchronized static Custom getCustom(SailPointContext context) throws GeneralException {
        // Adding second check to avoid re-initialization when the multiple
        // threads enter into the above if condition and waiting for this to be
        // initialized
        if (null == custom || null == custom.getAttributes()
                || null == custom.getAttributes().get(ROADUtil.TICKETSETTINGS)
                || !ROADUtil.TICKETCUSTOM.equalsIgnoreCase(custom.getName())) {
            custom = context.getObjectByName(Custom.class, ROADUtil.TICKETCUSTOM);
        } else {
            Date dbModified = Servicer.getModificationDate(context, custom);
            if (Util.nullSafeCompareTo(dbModified, custom.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning updated custom object");
                custom = context.getObjectByName(Custom.class, ROADUtil.TICKETCUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning previously initialized custom object");
            }
        }
        return custom;
    }
    /**
     * Get Common Mover Settings
     * 
     * @return custom
     * @throws GeneralException
     */
    synchronized static Custom getCustomMover(SailPointContext context) throws GeneralException {
        // Adding second check to avoid re-initialization when the multiple
        // threads enter into the above if condition and waiting for this to be
        // initialized
        if (null == customMover || null == customMover.getAttributes()
                || null == customMover.getAttributes().get(ROADUtil.MOVERSETTINGS)
                || !ROADUtil.MOVERCUSTOM.equalsIgnoreCase(customMover.getName())) {
            customMover = context.getObjectByName(Custom.class, ROADUtil.MOVERCUSTOM);
        } else {
            Date dbModified = Servicer.getModificationDate(context, customMover);
            if (Util.nullSafeCompareTo(dbModified, customMover.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning updated customMover object");
                customMover = context.getObjectByName(Custom.class, ROADUtil.MOVERCUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning previously initialized customMover object");
            }
        }
        return customMover;
    }
    /**
     * Get Common Mover Settings
     * 
     * @return custom
     * @throws GeneralException
     */
    synchronized static Custom getCustomNativeChange(SailPointContext context) throws GeneralException {
        // Adding second check to avoid re-initialization when the multiple
        // threads enter into the above if condition and waiting for this to be
        // initialized
        if (null == customNativeChange || null == customNativeChange.getAttributes()
                || null == customNativeChange.getAttributes().get(ROADUtil.NATIVECHANGESETTINGS)
                || !ROADUtil.NATIVECHANGECUSTOM.equalsIgnoreCase(customNativeChange.getName())) {
            customNativeChange = context.getObjectByName(Custom.class, ROADUtil.NATIVECHANGECUSTOM);
        } else {
            Date dbModified = Servicer.getModificationDate(context, customNativeChange);
            if (Util.nullSafeCompareTo(dbModified, customNativeChange.getModified()) > 0) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning updated customNativeChange object");
                customMover = context.getObjectByName(Custom.class, ROADUtil.MOVERCUSTOM);
            } else {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,"...Returning previously initialized customNativeChange object");
            }
        }
        return customNativeChange;
    }
    /**
     * Force Load All ROADUtil and WrapperRuleLibrary Custom Artifacts
     * 
     * @return custom
     * @throws GeneralException
     */
    public synchronized static void forceLoad(SailPointContext context) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Start Force Load..");
        customNativeChange = context.getObjectByName(Custom.class, ROADUtil.NATIVECHANGECUSTOM);
        customMover = context.getObjectByName(Custom.class, ROADUtil.MOVERCUSTOM);
        customEpic = context.getObjectByName(Custom.class, ROADUtil.EPICSERCUSTOM);
        custom = context.getObjectByName(Custom.class, ROADUtil.TICKETCUSTOM);
        customGlobal = context.getObjectByName(Custom.class, WrapperRuleLibrary.GLOBALROADCUSTOM);
        WrapperRuleLibrary.forceLoad(context);
        EmailNotificationRuleLibrary.forceLoad(context);
        AttributeSyncRuleLibrary.forceLoad(context);
        CertificationRuleLibrary.forceLoad(context);
        TriggersRuleLibrary.forceLoadTriggers(context);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End Force Load..");
    }
    /**
     * Get List of Native Change Settings
     * 
     * @param context
     * @return
     * @throws GeneralException
     */
    public static List getListOfNativeChangeSettings(SailPointContext context) throws GeneralException {
        List nativeChangeSettins = null;
        // Get the Native Custom Artifact
        customNativeChange = getCustomNativeChange(context);

        if (customNativeChange != null) {
            Map parentMap = (Map) customNativeChange.getAttributes().get(ROADUtil.NATIVECHANGESETTINGS);
            if (parentMap != null && parentMap.containsKey(NATIVECHANGESETTINGMAPPINGS)) {
                if (parentMap.get(NATIVECHANGESETTINGMAPPINGS) != null
                        && parentMap.get(NATIVECHANGESETTINGMAPPINGS) instanceof Map) {
                    Map childMap = (Map) parentMap.get(NATIVECHANGESETTINGMAPPINGS);
                    if (childMap != null && childMap.containsKey(NATIVECHANGEFEATURE)) {
                        if (childMap.get(NATIVECHANGEFEATURE) != null
                                && childMap.get(NATIVECHANGEFEATURE) instanceof List) {
                            nativeChangeSettins = (List) childMap.get(NATIVECHANGEFEATURE);
                        }
                    }
                }
            }
        }
        return nativeChangeSettins;
    }
    /**
     * Get List of Mover Settings
     * 
     * @param context
     * @return
     * @throws GeneralException
     */
    public static List getListOfMoverSettings(SailPointContext context) throws GeneralException {
        List moverSettings = null;
        // Get the Mover Custom Artifact
        customMover = getCustomMover(context);

        if (customMover != null) {
            Map parentMap = (Map) customMover.getAttributes().get(ROADUtil.MOVERSETTINGS);
            if (parentMap != null && parentMap.containsKey(MOVERSETTINGMAPPINGS)) {
                if (parentMap.get(MOVERSETTINGMAPPINGS) != null && parentMap.get(MOVERSETTINGMAPPINGS) instanceof Map) {
                    Map childMap = (Map) parentMap.get(MOVERSETTINGMAPPINGS);
                    if (childMap != null && childMap.containsKey(MOVERFEATURE)) {
                        if (childMap.get(MOVERFEATURE) != null && childMap.get(MOVERFEATURE) instanceof List) {
                            moverSettings = (List) childMap.get(MOVERFEATURE);
                        }
                    }
                }
            }
        }
        return moverSettings;
    }
    /**
     * Get Epic SER Attributes
     * 
     * @param key
     * @return Integer
     * @throws GeneralException
     */
    public static Attributes getEpicSERAttributes(SailPointContext context) throws GeneralException {
        // Get the Custom EPIC SER ATTRIBUTES
        customEpic = getCustomEpic(context);

        if (customEpic != null && customEpic.getAttributes() != null) {
            return customEpic.getAttributes();
        }
        return null;
    }
    /**
     * Get Ticket Optimistic Provisioning
     * 
     * @param key
     * @return String
     * @throws GeneralException
     */
    public static String getTicketOptimisticProvision(SailPointContext context, String key) throws GeneralException {
        // Get the Custom Ticket Object
        custom = getCustom(context);

        if (custom != null && custom.getAttributes() != null) {
            Map map = (Map) custom.getAttributes().get(ROADUtil.TICKETSETTINGS);
            if (map != null && map.containsKey(key)) {
                if (map.get(key) != null && map.get(key) instanceof String) {
                    return ((String) map.get(key));
                }
            }
        }
        return null;
    }
    /**
     * Get Ticket SLA's
     * 
     * @param key
     * @return Integer
     * @throws GeneralException
     */
    public static Integer getTicketSLA(SailPointContext context, String key) throws GeneralException {
        // Get the Custom Ticket Object
        custom = getCustom(context);
        if (custom != null && custom.getAttributes() != null) {
            Map map = (Map) custom.getAttributes().get(ROADUtil.TICKETSETTINGS);
            if (map != null && map.containsKey(key)) {
                if (map.get(key) != null && map.get(key) instanceof String) {
                    return Integer.valueOf((String) map.get(key));
                } else {
                    return (Integer) map.get(key);
                }
            }
        }
        return null;
    }
    /**
     * Get All SailPoint Object Classes
     * 
     * @return
     * @throws GeneralException
     */
    public static List getSailPointObjectClasses() throws GeneralException {
        List sailPOintObjectClasses = new ArrayList();
        // Classes defined XMLClasses.MF
        List<Class<?>> xmlClasses = XMLObjectFactory.getAnnotatedClasses();
        for (Class clazz : xmlClasses) {
            if (SailPointObject.class.isAssignableFrom(clazz)) {
                Class spClass = clazz.asSubclass(SailPointObject.class);
                try {
                    SailPointObject obj = (SailPointObject) spClass.newInstance();
                    if (!obj.isXml() && obj.hasAssignedScope()) {
                        sailPOintObjectClasses.add(spClass);
                    }
                } catch (Exception e) {
                    throw new GeneralException(e);
                }
            }
        }
        return sailPOintObjectClasses;
    }
    /**
     * Get All SailPoint Major Classes that Owner Property
     * 
     * @return
     */
    public static List getSailPointObjectClassesOwnedBySomeone() {
        List ownerClasses = new ArrayList();
        // Use Reflection to get Owner Classes
        for (Class clazz : ClassLists.MajorClasses) {
            if (clazz != null) {
                Method[] metClass = clazz.getMethods();
                for (int x = 0; x < metClass.length; x++) {
                    if (metClass[x].getName() == "getOwner") {
                        ownerClasses.add(clazz.getSimpleName());
                    }
                }
            }
        }
        return ownerClasses;
    }
    /**
     * Get All SailPoint Major Class Object
     * 
     * @return
     */
    public static List getSailPointObjectClazz(List classesToCheck) {
        List ownerClasses = new ArrayList();
        // Use Reflection to get Owner Classes
        for (Class clazz : ClassLists.MajorClasses) {
            if (classesToCheck != null && classesToCheck.contains(clazz.getSimpleName())) {
                if (clazz != null) {
                    Method[] metClass = clazz.getMethods();
                    for (int x = 0; x < metClass.length; x++) {
                        if (metClass[x].getName() == "getOwner") {
                            ownerClasses.add(clazz);
                        }
                    }
                }
            }
        }
        return ownerClasses;
    }
    /**
     * Get Clazz From Class Name
     * 
     * @return
     */
    public static Class getSailPointObjectClazzFromClassList(String classToCheck) {
        for (Class clazz : ClassLists.MajorClasses) {
            if (clazz != null) {
                if (classToCheck != null && classToCheck.equalsIgnoreCase(clazz.getSimpleName())) {
                    return clazz;
                }
            }
        }
        return null;
    }
    /**
     * Get Application Schema Attributes
     * 
     * @param appName
     * @param includeMulti
     * @return
     * @throws Exception
     */
    public static List getApplicationSchemaAttributes(String appName, SailPointContext context, boolean includeMulti)
            throws Exception {
        List<String> masterList = new ArrayList();
        if (appName != null) {
            Application app = context.getObjectByName(Application.class, appName);
            if (app != null) {
                Schema schema = app.getAccountSchema();
                if (schema != null) {
                    List<AttributeDefinition> attributeDefinitions = schema.getAttributes();
                    if (attributeDefinitions != null) {
                        for (AttributeDefinition attributeDefinition : attributeDefinitions) {
                            if (includeMulti) {
                                if (attributeDefinition != null && !attributeDefinition.isEntitlement()
                                        && attributeDefinition.getName() != null
                                        && attributeDefinition.getType() != null) {
                                    masterList.add(attributeDefinition.getName());
                                }
                            } else if (attributeDefinition != null && !attributeDefinition.isEntitlement()
                                    && !attributeDefinition.isMulti() && attributeDefinition.getName() != null
                                    && attributeDefinition.getType() != null) {
                                masterList.add(attributeDefinition.getName());
                            }
                        }
                    }
                }
                context.decache(app);
            }
        }
        return masterList;
    }
    /**
     * 
     * Get Identity Cube Attribute Name List
     * 
     * @param context
     * @param includeMulti
     * @param includeStandard
     * @return
     * @throws Exception
     */
    public static List getCubeAttributesList(SailPointContext context, boolean includeMulti, boolean includeStandard,
            boolean displayName, boolean excludeDisplayName) throws Exception {
        List<String> cubeAttrsList = new ArrayList<String>();
        ObjectConfig objectConfig = context.getObjectByName(ObjectConfig.class, "Identity");
        if (objectConfig != null) {
            List<ObjectAttribute> objAttrList = objectConfig.getObjectAttributes();
            if (objAttrList != null) {
                for (ObjectAttribute objAttr : objAttrList) {
                    // Name is Silent and System, We need to include this in All
                    // Drop-Down List
                    // Attribute Sync
                    // Correlation
                    // Provisioning Policies Mining
                    // Password HelpDesk Verification
                    // Ticket Integration Plan Initializer Rule
                    if (objAttr.getName().equalsIgnoreCase("name")) {
                        cubeAttrsList.add(objAttr.getName());
                    }
                    if (includeMulti && includeStandard) {
                        if (objAttr != null && objAttr.getName() != null && !objAttr.isSilent()
                                && !objAttr.isSystem()) {
                            if (displayName && objAttr.getDisplayName() != null) {
                                cubeAttrsList.add(objAttr.getDisplayName());
                            } else {
                                if (excludeDisplayName) {
                                    if (!objAttr.getName().equalsIgnoreCase("displayName")) {
                                        cubeAttrsList.add(objAttr.getName());
                                    }
                                } else {
                                    cubeAttrsList.add(objAttr.getName());
                                }
                            }
                        }
                    } else if (!includeMulti && !includeStandard) {
                        if (objAttr != null && objAttr.getName() != null && !objAttr.isSilent() && !objAttr.isSystem()
                                && !objAttr.isStandard() && !objAttr.isMulti()) {
                            if (displayName && objAttr.getDisplayName() != null) {
                                cubeAttrsList.add(objAttr.getDisplayName());
                            } else {
                                if (excludeDisplayName) {
                                    if (!objAttr.getName().equalsIgnoreCase("displayName")) {
                                        cubeAttrsList.add(objAttr.getName());
                                    }
                                } else {
                                    cubeAttrsList.add(objAttr.getName());
                                }
                            }
                        }
                    } else if (includeMulti && !includeStandard) {
                        if (objAttr != null && objAttr.getName() != null && !objAttr.isSilent() && !objAttr.isSystem()
                                && !objAttr.isStandard()) {
                            if (displayName && objAttr.getDisplayName() != null) {
                                cubeAttrsList.add(objAttr.getDisplayName());
                            } else {
                                if (excludeDisplayName) {
                                    if (!objAttr.getName().equalsIgnoreCase("displayName")) {
                                        cubeAttrsList.add(objAttr.getName());
                                    }
                                } else {
                                    cubeAttrsList.add(objAttr.getName());
                                }
                            }
                        }
                    } else if (!includeMulti && includeStandard) {
                        if (objAttr != null && objAttr.getName() != null && !objAttr.isSilent() && !objAttr.isSystem()
                                && !objAttr.isMulti()) {
                            if (displayName && objAttr.getDisplayName() != null) {
                                cubeAttrsList.add(objAttr.getDisplayName());
                            } else {
                                if (excludeDisplayName) {
                                    if (!objAttr.getName().equalsIgnoreCase("displayName")) {
                                        cubeAttrsList.add(objAttr.getName());
                                    }
                                } else {
                                    cubeAttrsList.add(objAttr.getName());
                                }
                            }
                        }
                    }
                }
            }
            context.decache(objectConfig);
        }
        return cubeAttrsList;
    }
    /**
     * This method returns business application name from role profiles, if not
     * found, infrastructure application name is set
     * 
     * @param requiredBundles
     *            List of Required Bundle Objects
     * @param setAppNames
     *            Set of Application Objects
     * @return String Application Name from Role
     * 
     */
    public static String setRoleAppName(List<Bundle> requiredBundles, Set<Application> setAppNames) {
        String appName = null;
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "setAppNames..." + setAppNames);
        if (setAppNames != null && !setAppNames.isEmpty()) {
            for (Application setValue : setAppNames) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger, "setValue..." + setValue);
                Boolean busApp = (Boolean) setValue.getAttributeValue("busApp");
                if (busApp != null && busApp.booleanValue()) {
                    appName = setValue.getName();
                    break;
                } else {
                    appName = setValue.getName();
                }
            }
        } else {
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Role RequiredBundles  = " + requiredBundles);
            if (requiredBundles != null) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger,
                        "...Role RequiredBundles  Size = " + requiredBundles.size());
                for (Bundle requiredBundle : requiredBundles) {
                    Set<Application> setReqAppNames = requiredBundle.getApplications();
                    LogEnablement.isLogDebugEnabled(roadUtilLogger,
                            "...Role Required Applications  = " + setReqAppNames);
                    if (setReqAppNames != null && !setReqAppNames.isEmpty()) {
                        for (Application setReqValue : setReqAppNames) {
                            roadUtilLogger.debug("setReqValue..." + setReqValue);
                            Boolean busApp = (Boolean) setReqValue.getAttributeValue("busApp");
                            if (busApp != null && busApp.booleanValue()) {
                                appName = setReqValue.getName();
                                break;
                            } else {
                                appName = setReqValue.getName();
                            }
                        }
                    }
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Application name from role: " + appName);
        return appName;
    }
    /**
     * Get WorkGroup Member Names
     * 
     * @param context
     * @param workGroupName
     * @throws GeneralException
     */
    public static List getWorkGroupMemberNames(SailPointContext context, String workGroupName) throws GeneralException {
        List memberNames = new ArrayList();
        Identity workGroup = context.getObjectByName(Identity.class, workGroupName);
        Iterator memberItr = ObjectUtil.getWorkgroupMembers(context, workGroup, Util.csvToList("name"));
        if (memberItr != null) {
            while (memberItr.hasNext()) {
                Object[] arrMember = (Object[]) memberItr.next();
                memberNames.add((String) arrMember[0]);
            }
        }

        return memberNames;
    }
    /**
     * Set Attribute Request
     * 
     * @param attrName
     * @param attrValue
     * @return
     */
    public static AttributeRequest generateSetAttributeRequest(String attrName, Object attrValue) {
        Attributes attrs = new Attributes();
        attrs.put("assignment", "true");
        AttributeRequest newAttrReq = new AttributeRequest();
        newAttrReq.setArguments(attrs);
        newAttrReq.setOp(ProvisioningPlan.Operation.Set);
        if (attrName != null) {
            newAttrReq.setName(attrName);
        }
        if (attrValue != null) {
            newAttrReq.setValue(attrValue);
        }
        return newAttrReq;
    }
    /**
     * Set Attribute Request
     * 
     * @param attrName
     * @param attrValue
     * @return
     */
    public static AttributeRequest generateSetAttributeRequest(String attrName, String attrValue) {
        Attributes attrs = new Attributes();
        attrs.put("assignment", "true");
        AttributeRequest newAttrReq = new AttributeRequest();
        newAttrReq.setArguments(attrs);
        newAttrReq.setOp(ProvisioningPlan.Operation.Set);
        if (attrName != null) {
            newAttrReq.setName(attrName);
        }
        if (attrValue != null) {
            newAttrReq.setValue(attrValue);
        }
        return newAttrReq;
    }
    /**
     * Get Native Id from Application Account Schema
     * 
     * @param context
     * @param appName
     * @param identity
     * @return
     * @throws Exception
     */
    public static String getNativeIdentity(SailPointContext context, String appName, Identity identity)
            throws Exception {
        return getNativeIdentity(context, null, appName, identity);
    }
    /**
     * Get Native Id from Application Account Schema
     * 
     * @param context
     * @param secondaryAccount
     * @param appName
     * @param identity
     * @return
     * @throws Exception
     */
    public static String getNativeIdentity(SailPointContext context, String secondaryAccount, String appName,
            Identity identity) throws Exception {
        return getNativeIdentity(context, secondaryAccount, appName, identity, null);
    }
    /**
     * Get Native Id from Application Account Schema
     * 
     * @param context
     * @param secondaryAccount
     * @param appName
     * @param identity
     * @param link
     * @return
     * @throws Exception
     */
    public static String getNativeIdentity(SailPointContext context, String secondaryAccount, String appName,
            Identity identity, Link lnk) throws Exception {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter getNativeIdentity");
        String nativeId = "";
        String current = null;
        if(lnk != null) {
            current = lnk.getNativeIdentity();
            roadUtilLogger.debug("Passed in Link: " + current);
        }
        if (appName != null && appName.length() > 0 && identity != null) {
            Application app = context.getObjectByName(Application.class, appName);
            if (app != null) {
                Schema schema = app.getAccountSchema();
                String identityAttributeField = schema.getIdentityAttribute();
                List<Template> templates = app.getTemplates();
                Template updateTemp = null;
                if (templates != null && templates.size() > 0) {
                    for (Template temp : templates) {
                        Template.Usage usage = temp.getUsage();
                        if (temp.getSchemaObjectType().equalsIgnoreCase("account")
                                && usage.equals(Template.Usage.Create)) {
                            updateTemp = temp;
                            break;
                        }
                    }
                    if (updateTemp != null) {
                        List<Field> fields = updateTemp.getFields(context);
                        if (fields != null && fields.size() > 0) {
                            for (Field field : fields) {
                                String fieldName = field.getName();
                                String displayName = field.getDisplayName();
                                if (identityAttributeField != null
                                        && identityAttributeField.compareTo(fieldName) == 0) {
                                    HashMap<String, Object> params = new HashMap();
                                    params.put("context", context);
                                    params.put("identity", identity);
                                    params.put("field", field);
                                    params.put("secondaryAccount", secondaryAccount);
                                    params.put("accountRequest", null);
                                    params.put("application", app);
                                    params.put("current", current);
                                    params.put("link", lnk);
                                    params.put("group", null);
                                    params.put("objectRequest", null);
                                    params.put("operation", null);
                                    params.put("project", null);
                                    params.put("role", null);
                                    params.put("template", updateTemp);
                                    Rule rule = field.getFieldRule();
                                    if (rule != null) {
                                        try {
                                            nativeId = (String) context.runRule(rule, params);
                                        } catch (Exception re) {
                                            LogEnablement.isLogErrorEnabled(roadUtilLogger,
                                                    "*** EXCEPTION RUNNING RULE/SCRIPT: " + re.toString());
                                            continue;
                                        }
                                    } else if (field.getScript() != null) {
                                        try {
                                            nativeId = (String) context.runScript(field.getScript(), params);
                                        } catch (Exception re) {
                                            LogEnablement.isLogErrorEnabled(roadUtilLogger,
                                                    "*** EXCEPTION RUNNING SCRIPT: " + re.toString());
                                            continue;
                                        }
                                    } else {
                                        nativeId = (String) field.getValue();
                                    }
                                }
                            }
                        }
                    }
                }
                if (app != null) {
                    context.decache(app);
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit getNativeIdentity = " + nativeId);
        return nativeId;
    }
    /**
     * Get Field Value From Provisioning Forms
     * 
     * @param context
     * @param appName
     * @param identity
     * @param fieldNameToEvalaute
     * @param templateOperation
     * @param link
     * @return
     * @throws Exception
     */
    public static Object getFieldValueFromProvisioningForms(SailPointContext context, String appName, Identity identity,
            String fieldNameToEvalaute, String templateOperation, Link link) throws Exception {
        Object evaluatedValue = "";
        Application app = context.getObjectByName(Application.class, appName);
        List<Template> templates = app.getTemplates();
        Template processTemp = null;
        if (templates != null && templates.size() > 0) {
            for (Template temp : templates) {
                Template.Usage usage = temp.getUsage();
                if (temp.getSchemaObjectType().equalsIgnoreCase("account") && templateOperation != null
                        && templateOperation.equalsIgnoreCase("Create") && usage.equals(Template.Usage.Create)) {
                    processTemp = temp;
                    break;
                } else if (temp.getSchemaObjectType().equalsIgnoreCase("account") && templateOperation != null
                        && templateOperation.equalsIgnoreCase("Update") && usage.equals(Template.Usage.Update)) {
                    processTemp = temp;
                    break;
                } else if (temp.getSchemaObjectType().equalsIgnoreCase("account") && templateOperation != null
                        && templateOperation.equalsIgnoreCase("Enable") && usage.equals(Template.Usage.Enable)) {
                    processTemp = temp;
                    break;
                } else if (temp.getSchemaObjectType().equalsIgnoreCase("account") && templateOperation != null
                        && templateOperation.equalsIgnoreCase("Disable") && usage.equals(Template.Usage.Disable)) {
                    processTemp = temp;
                    break;
                } else if (temp.getSchemaObjectType().equalsIgnoreCase("account") && templateOperation != null
                        && templateOperation.equalsIgnoreCase("Delete") && usage.equals(Template.Usage.Delete)) {
                    processTemp = temp;
                    break;
                }
            }
            if (processTemp != null) {
                List<Field> fields = processTemp.getFields(context);
                if (fields != null && fields.size() > 0) {
                    for (Field field : fields) {
                        String fieldName = field.getName();
                        if (fieldNameToEvalaute != null && fieldNameToEvalaute.compareTo(fieldName) == 0) {
                            HashMap params = new HashMap();
                            params.put("context", context);
                            params.put("identity", identity);
                            params.put("field", field);
                            params.put("link", link);
                            params.put("accountRequest", null);
                            params.put("application", app);
                            if (link != null)
                                params.put("current", link.getAttribute(fieldName));
                            else
                                params.put("current", null);
                            params.put("group", null);
                            params.put("objectRequest", null);
                            params.put("operation", null);
                            params.put("project", null);
                            params.put("role", null);
                            params.put("template", processTemp);
                            Rule rule = field.getFieldRule();
                            if (rule != null) {
                                try {
                                    evaluatedValue =  context.runRule(rule, params);
                                } catch (Exception re) {
                                    LogEnablement.isLogErrorEnabled(roadUtilLogger,
                                            "*** EXCEPTION RUNNING RULE: " + re.toString());
                                    continue;
                                }
                            } else if (field.getScript() != null) {
                                try {
                                    evaluatedValue = context.runScript(field.getScript(), params);
                                } catch (Exception re) {
                                    LogEnablement.isLogErrorEnabled(roadUtilLogger,
                                            "*** EXCEPTION RUNNING SCRIPT: " + re.toString());
                                    continue;
                                }
                            } else {
                                LogEnablement.isLogDebugEnabled(roadUtilLogger, "....No Rule/Script ");
                                evaluatedValue = field.getValue();
                            }
                        }
                    }
                }
            }
        }
        if (app != null) {
            context.decache(app);
        }
        return evaluatedValue;
    }
    /**
     * Launch Just Provisioner
     * 
     * @param plan
     * @throws GeneralException
     */
    public static void launchProvisionerPlan(ProvisioningPlan plan, SailPointContext context) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, ".. Enter launchProvisionerPlan");
        Provisioner provisioner = new Provisioner(context);
        ProvisioningProject project = provisioner.compile(plan);
        provisioner.execute(project);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, ".. End launchProvisionerPlan");
    }
    /**
     * Invoke Extended Rules defined on Applications
     * 
     * @param extendedRule
     * @param appName
     * @param requestType
     * @param spExtAttrs
     * @param extAttributeName
     * @param extAttributeValue
     * @param identityName
     * @param nativeId
     * @param useRuleFromApp
     * @param populationMap
     * @return
     * @throws GeneralException
     */
    public static Object invokeExtendedRuleNoObjectReferences(SailPointContext context, String secondaryAccount,
            String extendedRule, String appName, String requestType, Attributes spExtAttrs, String extAttributeName,
            String extAttributeValue, String identityName, String nativeId, Boolean useRuleFromApp, Map populationMap)
                    throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter invokeExtendedRuleNoObjectReferences");
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter identityName.."+identityName);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter populationMap..." + populationMap);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter requestType..." + requestType);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter extAttributeValue..." + extAttributeValue);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter useRuleFromApp..." + useRuleFromApp);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter spExtAttrs..." + spExtAttrs);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter extendedRule..." + extendedRule);
        AccountRequest acctReq = null;
        Object retVal = new Object();
        Rule rule = null;
        Object obj;
        String actualRule = null;
        if (extendedRule != null && extendedRule.length() > 0 && appName != null && useRuleFromApp) {
            Application app = context.getObjectByName(Application.class, appName);
            if (app != null) {
                Object appObj = app.getAttributeValue(extendedRule);
                if (appObj != null) {
                    actualRule = (String) appObj;
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, " extendedRule.from application.." + actualRule);
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "actualRule " + actualRule);
                }
                context.decache(app);
            }
        } else if (extendedRule != null && extendedRule.length() > 0) {
            actualRule = extendedRule;
            LogEnablement.isLogDebugEnabled(roadUtilLogger, " extendedRule..." + actualRule);
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, " extendedRule..." + actualRule);
        if (requestType != null
                && (requestType.equalsIgnoreCase(LeaverRuleLibrary.LEAVERFEATURE)
                        || requestType.equalsIgnoreCase(LeaverRuleLibrary.IMMEDIATELEAVERFEATURE))
                && populationMap != null
                && populationMap.containsKey(LeaverRuleLibrary.OPTIONSTERMINATIONEXTENDEDRULETOKEN)
                && populationMap.get(LeaverRuleLibrary.OPTIONSTERMINATIONEXTENDEDRULETOKEN) != null) {
            actualRule = (String) populationMap.get(LeaverRuleLibrary.OPTIONSTERMINATIONEXTENDEDRULETOKEN);
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "Override termination extendedRule..." + actualRule);
        } else if (requestType != null && (requestType.equalsIgnoreCase(ReverseLeaverRuleLibrary.REVERSELEAVERFEATURE))
                && populationMap != null
                && populationMap.containsKey(ReverseLeaverRuleLibrary.OPTIONSREVTERMINATIONEXTENDEDRULETOKEN)
                && populationMap.get(ReverseLeaverRuleLibrary.OPTIONSREVTERMINATIONEXTENDEDRULETOKEN) != null) {
            actualRule = (String) populationMap.get(ReverseLeaverRuleLibrary.OPTIONSREVTERMINATIONEXTENDEDRULETOKEN);
            LogEnablement.isLogDebugEnabled(roadUtilLogger,
                    "Override Reverse termination extendedRule..." + actualRule);
        }
        if (actualRule != null) {
            rule = context.getObjectByName(Rule.class, actualRule);
            if (rule != null) {
                HashMap params = new HashMap();
                params.put("context", context);
                params.put("identityName", identityName);
                params.put("appName", appName);
                params.put("nativeId", nativeId);
                params.put("requestType", requestType);
                params.put("spExtAttrs", spExtAttrs);
                params.put("secondaryAccount", secondaryAccount);
                if (extAttributeName != null && extAttributeName.length() > 0) {
                    params.put(extAttributeName, extAttributeValue);
                }
                try {
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Run the rule");
                    obj = context.runRule(rule, params);
                    if (obj != null && obj instanceof List) {
                        retVal = (List) obj;
                    } else if (obj != null && obj instanceof HashMap) {
                        retVal = (HashMap) obj;
                    } else if (obj != null && obj instanceof String) {
                        retVal = (String) obj;
                    }
                    else if (obj != null && obj instanceof Date) {
                        retVal = (Date) obj;
                    }
                    else if (obj != null && obj instanceof Boolean) {
                        retVal = (Boolean) obj;
                    }
                    else if (obj != null ) {
                        retVal = obj;
                    }
                } catch (Exception re) {
                    LogEnablement.isLogErrorEnabled(roadUtilLogger, "...Rule Exception " + re.getMessage());
                    throw new GeneralException("Error During Rule Launch..." + actualRule + " " + re.getMessage());
                }
                context.decache(rule);
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit invokeExtendedRuleNoObjectReferences.."+retVal);
        return retVal;
    }
    /**
     * Invoke Extended Rules defined on Applications
     * 
     * @param extendedRule
     * @param appName
     * @param requestType
     * @param spExtAttrs
     * @param extAttributeName
     * @param extAttributeValue
     * @param identityName
     * @param nativeId
     * @param useRuleFromApp
     * @return
     * @throws GeneralException
     */
    public static Object invokePostExtendedRuleNoObjectReferences(SailPointContext context, String secondaryAccount,
            String extendedRule, String appName, String requestType, Attributes spExtAttrs, String extAttributeName,
            String extAttributeValue, String identityName, String nativeId, ProvisioningProject project)
                    throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter invokeExtendedRuleNoObjectReferences");
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter identityName.."+identityName);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter requestType..." + requestType);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter extAttributeValue..." + extAttributeValue);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter spExtAttrs..." + spExtAttrs);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter extendedRule..." + extendedRule);
        AccountRequest acctReq = null;
        Object retVal = new Object();
        Rule rule = null;
        Object obj;
        String actualRule = null;
        if (extendedRule != null && extendedRule.length() > 0) {
            actualRule = extendedRule;
        }
        if (actualRule != null) {
            rule = context.getObjectByName(Rule.class, actualRule);
            if (rule != null) {
                HashMap params = new HashMap();
                params.put("context", context);
                params.put("identityName", identityName);
                params.put("appName", appName);
                params.put("nativeId", nativeId);
                params.put("requestType", requestType);
                params.put("spExtAttrs", spExtAttrs);
                params.put("secondaryAccount", secondaryAccount);
                params.put("project", project);
                if (extAttributeName != null && extAttributeName.length() > 0) {
                    params.put(extAttributeName, extAttributeValue);
                }
                try {
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Run the rule");
                    obj = context.runRule(rule, params);
                    if (obj != null && obj instanceof List) {
                        retVal = (List) obj;
                    } else if (obj != null && obj instanceof HashMap) {
                        retVal = (HashMap) obj;
                    } else if (obj != null && obj instanceof String) {
                        retVal = (String) obj;
                    }
                    else if (obj != null && obj instanceof Date) {
                        retVal = (Date) obj;
                    }
                    else if (obj != null && obj instanceof Boolean) {
                        retVal = (Boolean) obj;
                    }
                    else if (obj != null ) {
                        retVal = obj;
                    }
                } catch (Exception re) {
                    LogEnablement.isLogErrorEnabled(roadUtilLogger, "...Rule Exception " + re.getMessage());
                    throw new GeneralException("Error During Rule Launch..." + actualRule + " " + re.getMessage());
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit invokeExtendedRuleNoObjectReferences.."+retVal);
        return retVal;
    }
    /**
     * Invoke Extended Rules defined on Applications
     * 
     * @param context
     * @param secondaryAccount
     * @param extendedRule
     * @param appName
     * @param requestType
     * @param spExtAttrs
     * @param extAttributeName
     * @param extAttributeValue
     * @param identityName
     * @param nativeId
     * @param plan
     * @param project
     * @return
     * @throws GeneralException
     */
    public static Object invokePostExtendedRuleNoObjectReferences(SailPointContext context, String secondaryAccount,
            String extendedRule, String appName, String requestType, Attributes spExtAttrs, String extAttributeName,
            String extAttributeValue, String identityName, String nativeId, ProvisioningPlan plan,
            ProvisioningProject project) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter invokeExtendedRuleNoObjectReferences");
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter identityName.."+identityName);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter requestType..." + requestType);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter extAttributeValue..." + extAttributeValue);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter spExtAttrs..." + spExtAttrs);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter extendedRule..." + extendedRule);
        AccountRequest acctReq = null;
        Object retVal = new Object();
        Rule rule = null;
        Object obj;
        String actualRule = null;
        if (extendedRule != null && extendedRule.length() > 0) {
            actualRule = extendedRule;
        }
        if (actualRule != null) {
            rule = context.getObjectByName(Rule.class, actualRule);
            if (rule != null) {
                HashMap params = new HashMap();
                params.put("context", context);
                params.put("identityName", identityName);
                params.put("appName", appName);
                params.put("nativeId", nativeId);
                params.put("requestType", requestType);
                params.put("spExtAttrs", spExtAttrs);
                params.put("secondaryAccount", secondaryAccount);
                params.put("project", project);
                params.put("plan", plan);
                if (extAttributeName != null && extAttributeName.length() > 0) {
                    params.put(extAttributeName, extAttributeValue);
                }
                try {
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Run the rule");
                    obj = context.runRule(rule, params);
                    if (obj != null && obj instanceof List) {
                        retVal = (List) obj;
                    } else if (obj != null && obj instanceof HashMap) {
                        retVal = (HashMap) obj;
                    } else if (obj != null && obj instanceof String) {
                        retVal = (String) obj;
                    }
                    else if (obj != null && obj instanceof Date) {
                        retVal = (Date) obj;
                    }
                    else if (obj != null && obj instanceof Boolean) {
                        retVal = (Boolean) obj;
                    }
                    else if (obj != null ) {
                        retVal = obj;
                    }
                } catch (Exception re) {
                    LogEnablement.isLogErrorEnabled(roadUtilLogger, "...Rule Exception " + re.getMessage());
                    throw new GeneralException("Error During Rule Launch..." + actualRule + " " + re.getMessage());
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit invokeExtendedRuleNoObjectReferences");
        return retVal;
    }
    /**
     * Terminate X Days Pending Request
     * 
     * @param identityName
     * @throws GeneralException
     */
    public static void terminateXDaysPendingRequest(SailPointContext context, String identityName)
            throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Start terminateXDaysPendingRequest");
        Terminator term = new Terminator(context);
        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.like("name", "REQUEST MANAGER FEATURE BY XDAYS " + identityName, MatchMode.START));
        ops.setCloneResults(true);
        Iterator result = context.search(Request.class, ops, "id");
        if (result != null) {
            while (result.hasNext()) {
                Object[] stringsOb = (Object[]) result.next();
                if (stringsOb != null && stringsOb.length == 1) {
                    String requestId = (String) stringsOb[0];
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "Removing Event: " + requestId);
                    if (requestId != null) {
                        term.deleteObject(context.getObjectById(Request.class, requestId));
                    }
                }
            }
        }
        Util.flushIterator(result);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End terminateXDaysPendingRequest");
    }
    /**
     * Use Extended Attribute to "psAccount" find secondary accounts
     * 
     * @param link
     * @return
     */
    public static boolean isSecondaryAccount(Link link) {
        boolean result = false;
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter....isSecondaryAccount");
        // Let's ignore secondary account
        if (link != null && link.getAttribute("psAccount") != null
                && ((String) link.getAttribute("psAccount")).equalsIgnoreCase("TRUE")) {
            result = true;
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End isSecondaryAccount..." + result);
        return result;
    }
    /**
     * Rename LDAP Accounts
     * 
     * @param fullName
     *            CN
     * @param appName
     *            Application Name
     * @param nativeId
     *            Native ID of account
     * @return AttributeRequest
     */
    public static AttributeRequest renameCnFullName(String cnFullName, String appName, String nativeId,
            String comment) {
        String acNewName = cnFullName;
        // New CN Value, no OU
        AttributeRequest newCNAttributeRequest = new AttributeRequest();
        if (comment != null) {
            newCNAttributeRequest.setComments(comment + " " + new Date());
        }
        newCNAttributeRequest.setName("AC_NewName");
        newCNAttributeRequest.setValue(acNewName);
        newCNAttributeRequest.setOperation(ProvisioningPlan.Operation.Set);
        // Add attribute Request
        return newCNAttributeRequest;
    }
    /**
     * Launch Synchronous Workflow
     * 
     * @param plan
     * @param identityName
     * @param comments
     * @param workflowName
     * @param frameworkFeature
     * @param launcher
     * @return
     * @throws GeneralException
     */
    public static boolean launchSynchronousWorkflow(String tracing, SailPointContext context, ProvisioningPlan plan,
            String identityName, String comments, String workflowName, String frameworkFeature, String launcher)
                    throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, ".. Enter launchSynchronousWorkflow.." + identityName);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..  frameworkFeature.." + frameworkFeature);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..  launcher.." + launcher);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..  workflowName.." + workflowName);
        HashMap launchArgsMap = new HashMap();
        if (identityName == null) {
            return false;
        }
        if (workflowName == null) {
            workflowName = ROADUtil.DEFAULTWORKFLOW;
        }
        if (comments == null) {
            comments = "Background Workflow Launch";
        }
        if (frameworkFeature == null) {
            frameworkFeature = ROADUtil.DEFAULTFEATURE;
        }
        if (launcher == null) {
            launcher = "spadmin";
        }
        // Prepare Launch Arguments
        launchArgsMap.put("workItemComments", comments);
        launchArgsMap.put("identityName", identityName);
        Identity identityOb = context.getObjectByName(Identity.class, identityName);
        if (identityOb != null) {
            launchArgsMap.put("identityDisplayName", identityOb.getDisplayName());
            context.decache(identityOb);
        }
        launchArgsMap.put("requestType", frameworkFeature);
        launchArgsMap.put("trace", tracing);
        launchArgsMap.put("launcher", launcher);
        launchArgsMap.put("foregroundProvisioning", "true");
        launchArgsMap.put("plan", plan);
        launchArgsMap.put("flow", "Lifecycle");
        // Create WorkflowLaunch and set values
        WorkflowLaunch wflaunch = new WorkflowLaunch();
        Workflow wf = (Workflow) context.getObjectByName(Workflow.class, workflowName);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..  wf.." + wf);
        wflaunch.setWorkflowName(wf.getName());
        wflaunch.setWorkflowRef(wf.getName());
        wflaunch.setCaseName(workflowName);
        wflaunch.setVariables(launchArgsMap);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..  launchArgsMap.." + launchArgsMap);
        // Create Workflower and launch workflow from WorkflowLaunch
        Workflower workflower = new Workflower(context);
        WorkflowLaunch launch = workflower.launch(wflaunch);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, ".. launch " + launch);
        if (launch != null) {
            String status = launch.getStatus();
            if ((status != null) && (status.compareTo(WorkflowLaunch.STATUS_FAILED) == 0)) {
                TaskResult taskResult = launch.getTaskResult();
                LogEnablement.isLogDebugEnabled(roadUtilLogger, ".. taskResult " + taskResult);
                if (taskResult != null) {
                    List<Message> errors = taskResult.getErrors();
                    if (Util.size(errors) > 0) {
                        StringBuffer sb = new StringBuffer();
                        sb.append("Status : " + status + "\n");
                        for (Message message : errors) {
                            sb.append(message.getLocalizedMessage());
                            sb.append("\n");
                        }
                        status = sb.toString();
                    }
                    LogEnablement.isLogErrorEnabled(roadUtilLogger, "ERROR " + Util.asList(status));
                }
            } else {
                return true;
            }
        }
        return false;
    }
    /**
     * Launch Certification Campaign
     * 
     * @param context
     * @param identityName
     * @param launcher
     * @param certifierName
     * @param feature
     * @param applications
     * @param templateName
     * @param uiTemplate
     * @throws Exception
     */
    public static void launchCertification(SailPointContext context, String identityName, String launcher,
            String certifierName, String feature, List<Application> applications, String templateName,
            boolean uiTemplate) throws Exception {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter launchCertification");
        String r = launcher;
        String certName = "";
        if (identityName != null && certifierName != null) {
            Identity identity = context.getObjectByName(Identity.class, identityName);
            Identity certifier = context.getObjectByName(Identity.class, certifierName);
            if (r == null || r.equalsIgnoreCase("Scheduler") || r.equalsIgnoreCase("RequestHandler")) {
                r = "spadmin";
            }
            String idOrName = feature;
            // Get the Tag and if it does not exist, create it
            Tag tag = context.getObjectByName(Tag.class, idOrName);
            if (null == tag) {
                tag = new Tag(idOrName);
                context.saveObject(tag);
                context.commitTransaction();
            }
            List customMaps = null;
            if (feature != null && feature.equalsIgnoreCase("NATIVE CHANGE DETECTION FEATURE")) {
                customMaps = ROADUtil.getListOfNativeChangeSettings(context);
            } else if (feature != null && feature.equalsIgnoreCase("MOVER FEATURE")) {
                customMaps = ROADUtil.getListOfMoverSettings(context);
            }
            Long activePeriod = new Long("0");
            Long remediationPeriod = new Long("0");
            Long afterStart = new Long("0");
            Long remindFrequency = new Long("0");
            Long allowedDuration = new Long("30");
            Long additionalBeforeEnd = new Long("0");
            String notificationEmail = "";
            String additionalNotificationEmailTemplate = "";
            String additionalNotificationRule = "";
            String exclusionRule = "";
            String certificationNamePrefix = "";
            String certificationNameSuffix = "";
            String certificationActivePhaseEnterRule = "";
            String certificationActivePhaseExitRule = "";
            String certificationEndPhaseEnterRule = "";
            String certificationEndPhaseExitRule = "";
            String certificationRemediationPhaseEnterRule = "";
            String certificationRemediationPhaseExitRule = "";
            String signOffApproverRuleName = "";
            String allowPolicyViolation = "";
            String allowRoles = "";
            String allowAccountRevocation = "";
            String allowBulkAccountRevocation = "";
            String allowBulkItemsRevocation = "";
            String allowBulkApproval = "";
            String allowAdditionalEntitlements = "";
            String allowBulkClearDecisions = "";
            String updateAttributeAssignment = "";
            String listBulkReassign = "";
            String listBulkMitigate = "";
            String listBulkRevoke = "";
            String listBulkRevocation = "";
            String listBulkAccountRevocation = "";
            String listBulkApprove = "";
            String listBulkClearDecisions = "";
            String processRevokesImmediately = "";
            String requireBulkCertify = "";
            String requireReassignmentCompletion = "";
            String automaticSignOffOnAllReassignments = "";
            String displayEntitlementDescription = "";
            String assimilateBulkReassignments = "";
            String allowProvisioningRequirements = "";
            String delegationReviewRequired = "";
            String allowRemediation = "";
            String automaticClosing = "";
            String automaticClosingAction = "";
            String certificationEmail = "";
            String certifyEmptyAccounts = "";
            String certifyAccounts = "";
            String requireElectronicSignature = "";
            String automaticallSignOffWhenNothingToCertify = "";
            String allowException = "";
            String allowExceptionPopup = "";
            String saveExclusions = "";
            String bulkReassignmentEmail = "";
            Long automaticClosingDays = new Long("0");
            String allowRemediationPhase = "";
            String limitReassignments = "";
            String reassignmentLimit = "";
            String suppressEmailWhenNothingToCertify = "";
            String delegationEmail = "";
            String delegationCompletionEmail = "";
            String stagingEnabled = "";
            String allowItemDelegation = "";
            String allowEntityDelegation = "";
            String delegationForwardingDisabled = "";
            String sendPreDelegationCompletionEmails = "";
            String preDelegationRuleName = "";
            String requireMitigationComments = "";
            String requireApprovalComments = "";
            String automaticClosingComments = "";
            if (customMaps != null && customMaps.size() > 0) {
                Map singleMap = (HashMap) customMaps.get(0);
                if (singleMap != null) {
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Pull data from Custom Map");
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...singleMap= " + singleMap);
                    if (singleMap.get("activePeriodDays") != null) {
                        try {
                            activePeriod = Long.parseLong((String) singleMap.get("activePeriodDays"));
                        } catch (NumberFormatException e) {
                            activePeriod = null;
                        }
                    }
                    if (singleMap.get("allowRemediationPhase") != null) {
                        allowRemediation = (String) singleMap.get("allowRemediationPhase");
                    } else {
                        allowRemediation = "false";
                    }
                    if (singleMap.get("remediationPeriodDays") != null) {
                        try {
                            remediationPeriod = Long.parseLong((String) singleMap.get("remediationPeriodDays"));
                        } catch (NumberFormatException e) {
                            remediationPeriod = null;
                        }
                    }
                    if (singleMap.get("notificationDaysAfterStart") != null) {
                        try {
                            afterStart = Long.parseLong((String) singleMap.get("notificationDaysAfterStart"));
                        } catch (NumberFormatException e) {
                            afterStart = null;
                        }
                    }
                    if (singleMap.get("notificationFrequency") != null) {
                        try {
                            remindFrequency = Long.parseLong((String) singleMap.get("notificationFrequency"));
                        } catch (NumberFormatException e) {
                            remindFrequency = null;
                        }
                    }
                    if (singleMap.get("additionalNotificationBeforeEnd") != null) {
                        try {
                            additionalBeforeEnd = Long
                                    .parseLong((String) singleMap.get("additionalNotificationBeforeEnd"));
                        } catch (NumberFormatException e) {
                            additionalBeforeEnd = null;
                        }
                    }
                    if (singleMap.get("notificationEmailTemplate") != null) {
                        notificationEmail = (String) singleMap.get("notificationEmailTemplate");
                    } else {
                        notificationEmail = "";
                    }
                    if (singleMap.get("additionalNotificationEmailTemplate") != null) {
                        additionalNotificationEmailTemplate = (String) singleMap
                                .get("additionalNotificationEmailTemplate");
                    } else {
                        additionalNotificationEmailTemplate = "";
                    }
                    if (singleMap.get("additionalNotificationRule") != null) {
                        additionalNotificationRule = (String) singleMap.get("additionalNotificationRule");
                    }
                    if (singleMap.get("exclusionRule") != null) {
                        exclusionRule = (String) singleMap.get("exclusionRule");
                    }
                    if (singleMap.get("saveExclusions") != null) {
                        saveExclusions = (String) singleMap.get("saveExclusions");
                    }
                    if (singleMap.get("certifyEmptyAccounts") != null) {
                        certifyEmptyAccounts = (String) singleMap.get("certifyEmptyAccounts");
                    }
                    if (singleMap.get("certifyAccounts") != null) {
                        certifyAccounts = (String) singleMap.get("certifyAccounts");
                    }
                    if (singleMap.get("allowExceptionDuration") != null) {
                        try {
                            allowedDuration = Long.parseLong((String) singleMap.get("allowExceptionDuration"));
                        } catch (NumberFormatException e) {
                            allowedDuration = null;
                        }
                    }
                    if (singleMap.get("certificationNamePrefix") != null) {
                        certificationNamePrefix = (String) singleMap.get("certificationNamePrefix");
                    }
                    if (singleMap.get("certificationNameSuffix") != null) {
                        certificationNameSuffix = (String) singleMap.get("certificationNameSuffix");
                    }
                    if (singleMap.get("certificationActivePhaseEnterRule") != null) {
                        certificationActivePhaseEnterRule = (String) singleMap.get("certificationActivePhaseEnterRule");
                    }
                    if (singleMap.get("certificationActivePhaseExitRule") != null) {
                        certificationActivePhaseExitRule = (String) singleMap.get("certificationActivePhaseExitRule");
                    }
                    if (singleMap.get("certificationEndPhaseEnterRule") != null) {
                        certificationEndPhaseEnterRule = (String) singleMap.get("certificationEndPhaseEnterRule");
                    }
                    if (singleMap.get("certificationEndPhaseExitRule") != null) {
                        certificationEndPhaseExitRule = (String) singleMap.get("certificationEndPhaseExitRule");
                    }
                    if (singleMap.get("certificationRemediationPhaseEnterRule") != null) {
                        certificationRemediationPhaseEnterRule = (String) singleMap
                                .get("certificationRemediationPhaseEnterRule");
                    }
                    if (singleMap.get("certificationRemediationPhaseExitRule") != null) {
                        certificationRemediationPhaseExitRule = (String) singleMap
                                .get("certificationRemediationPhaseExitRule");
                    }
                    if (singleMap.get("automaticClosing") != null) {
                        automaticClosing = (String) singleMap.get("automaticClosing");
                        roadUtilLogger.debug("automaticClosing " + automaticClosing);
                    } else {
                        LogEnablement.isLogDebugEnabled(roadUtilLogger, "automaticClosing false");
                        roadUtilLogger.debug("automaticClosing " + automaticClosing);
                    }
                    if (singleMap.get("automaticClosingAction") != null) {
                        automaticClosingAction = (String) singleMap.get("automaticClosingAction");
                    }
                    if (singleMap.get("automaticClosingDays") != null) {
                        try {
                            automaticClosingDays = Long.parseLong((String) singleMap.get("automaticClosingDays"));
                        } catch (NumberFormatException e) {
                            automaticClosingDays = null;
                        }
                    }
                    if (singleMap.get("signOffApproverRuleName") != null) {
                        signOffApproverRuleName = (String) singleMap.get("signOffApproverRuleName");
                    }
                    if (singleMap.get("showPolicyViolation") != null) {
                        allowPolicyViolation = (String) singleMap.get("showPolicyViolation");
                    }
                    if (singleMap.get("allowRoles") != null) {
                        allowRoles = (String) singleMap.get("allowRoles");
                    }
                    if (singleMap.get("allowAccountRevocation") != null) {
                        allowAccountRevocation = (String) singleMap.get("allowAccountRevocation");
                    }
                    if (singleMap.get("allowBulkAccountRevocation") != null) {
                        allowBulkAccountRevocation = (String) singleMap.get("allowBulkAccountRevocation");
                    }
                    if (singleMap.get("allowBulkItemsRevocation") != null) {
                        allowBulkItemsRevocation = (String) singleMap.get("allowBulkItemsRevocation");
                    }
                    if (singleMap.get("allowBulkApproval") != null) {
                        allowBulkApproval = (String) singleMap.get("allowBulkApproval");
                    }
                    if (singleMap.get("allowAdditionalEntitlements") != null) {
                        allowAdditionalEntitlements = (String) singleMap.get("allowAdditionalEntitlements");
                    }
                    if (singleMap.get("allowBulkClearDecisions") != null) {
                        allowBulkClearDecisions = (String) singleMap.get("allowBulkClearDecisions");
                    }
                    if (singleMap.get("updateAttributeAssignment") != null) {
                        updateAttributeAssignment = (String) singleMap.get("updateAttributeAssignment");
                    }
                    if (singleMap.get("listBulkReassign") != null) {
                        listBulkReassign = (String) singleMap.get("listBulkReassign");
                    }
                    if (singleMap.get("listBulkMitigate") != null) {
                        listBulkMitigate = (String) singleMap.get("listBulkMitigate");
                    }
                    if (singleMap.get("listBulkRevoke") != null) {
                        listBulkRevoke = (String) singleMap.get("listBulkRevoke");
                    }
                    if (singleMap.get("listBulkRevocation") != null) {
                        listBulkRevocation = (String) singleMap.get("listBulkRevocation");
                    }
                    if (singleMap.get("listBulkAccountRevocation") != null) {
                        listBulkAccountRevocation = (String) singleMap.get("listBulkAccountRevocation");
                    }
                    if (singleMap.get("listBulkApprove") != null) {
                        listBulkApprove = (String) singleMap.get("listBulkApprove");
                    }
                    if (singleMap.get("listBulkClearDecisions") != null) {
                        listBulkClearDecisions = (String) singleMap.get("listBulkClearDecisions");
                    }
                    if (singleMap.get("processRevokesImmediately") != null) {
                        processRevokesImmediately = (String) singleMap.get("processRevokesImmediately");
                    }
                    if (singleMap.get("requireBulkCertify") != null) {
                        requireBulkCertify = (String) singleMap.get("requireBulkCertify");
                    }
                    if (singleMap.get("requireReassignmentCompletion") != null) {
                        requireReassignmentCompletion = (String) singleMap.get("requireReassignmentCompletion");
                    }
                    if (singleMap.get("automaticSignOffOnAllReassignments") != null) {
                        automaticSignOffOnAllReassignments = (String) singleMap
                                .get("automaticSignOffOnAllReassignments");
                    }
                    if (singleMap.get("displayEntitlementDescription") != null) {
                        displayEntitlementDescription = (String) singleMap.get("displayEntitlementDescription");
                    }
                    if (singleMap.get("assimilateBulkReassignments") != null) {
                        assimilateBulkReassignments = (String) singleMap.get("assimilateBulkReassignments");
                    }
                    if (singleMap.get("allowProvisioningRequirements") != null) {
                        allowProvisioningRequirements = (String) singleMap.get("allowProvisioningRequirements");
                    }
                    if (singleMap.get("delegationReviewRequired") != null) {
                        delegationReviewRequired = (String) singleMap.get("delegationReviewRequired");
                    }
                    if (singleMap.get("certificationEmail") != null) {
                        certificationEmail = (String) singleMap.get("certificationEmail");
                    }
                    if (singleMap.get("bulkReassignmentEmail") != null) {
                        bulkReassignmentEmail = (String) singleMap.get("bulkReassignmentEmail");
                    }
                    if (singleMap.get("allowExceptionPopup") != null) {
                        allowExceptionPopup = (String) singleMap.get("allowExceptionPopup");
                    }
                    if (singleMap.get("allowException") != null) {
                        allowException = (String) singleMap.get("allowException");
                    }
                    if (singleMap.get("requireElectronicSignature") != null) {
                        requireElectronicSignature = (String) singleMap.get("requireElectronicSignature");
                    }
                    if (singleMap.get("automaticallSignOffWhenNothingToCertify") != null) {
                        automaticallSignOffWhenNothingToCertify = (String) singleMap
                                .get("automaticallSignOffWhenNothingToCertify");
                    }
                    if (singleMap.get("suppressEmailWhenNothingToCertify") != null) {
                        suppressEmailWhenNothingToCertify = (String) singleMap.get("suppressEmailWhenNothingToCertify");
                    }
                    if (singleMap.get("limitReassignments") != null) {
                        limitReassignments = (String) singleMap.get("limitReassignments");
                    }
                    if (singleMap.get("reassignmentLimit") != null) {
                        reassignmentLimit = (String) singleMap.get("reassignmentLimit");
                    }
                    if (singleMap.get("delegationEmail") != null) {
                        delegationEmail = (String) singleMap.get("delegationEmail");
                    }
                    if (singleMap.get("delegationCompletionEmail") != null) {
                        delegationCompletionEmail = (String) singleMap.get("delegationCompletionEmail");
                    }
                    if (singleMap.get("stagingEnabled") != null) {
                        stagingEnabled = (String) singleMap.get("stagingEnabled");
                    }
                    if (singleMap.get("allowItemDelegation") != null) {
                        allowItemDelegation = (String) singleMap.get("allowItemDelegation");
                    }
                    if (singleMap.get("allowEntityDelegation") != null) {
                        allowEntityDelegation = (String) singleMap.get("allowEntityDelegation");
                    }
                    if (singleMap.get("delegationForwardingDisabled") != null) {
                        delegationForwardingDisabled = (String) singleMap.get("delegationForwardingDisabled");
                    }
                    if (singleMap.get("sendPreDelegationCompletionEmails") != null) {
                        sendPreDelegationCompletionEmails = (String) singleMap.get("sendPreDelegationCompletionEmails");
                    }
                    if (singleMap.get("preDelegationRuleName") != null) {
                        preDelegationRuleName = (String) singleMap.get("preDelegationRuleName");
                    }
                    if (singleMap.get("requireMitigationComments") != null) {
                        requireMitigationComments = (String) singleMap.get("requireMitigationComments");
                    }
                    if (singleMap.get("requireApprovalComments") != null) {
                        requireApprovalComments = (String) singleMap.get("requireApprovalComments");
                    }
                    if (singleMap.get("automaticClosingComments") != null) {
                        automaticClosingComments = (String) singleMap.get("automaticClosingComments");
                    }
                }
            }
            Identity requestor = context.getObjectByName(Identity.class, r);
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Change requested by " + requestor.getName());
            List identities = new ArrayList();
            identities.add(identityName);
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Set the cert group owner");
            Identity certGroupOwner = context.getObjectByName(Identity.class, "spadmin");
            roadUtilLogger.debug("...Create scheduler & schedule set to run now");
            CertificationScheduler scheduler = new CertificationScheduler(context);
            CertificationDefinition definition = null;
            CertificationSchedule schedule = null;
            if (uiTemplate && templateName != null) {
                /**
                 * Certification Event UI Start
                 */
                CertificationDefinition existingDefinition = context.getObjectByName(CertificationDefinition.class,
                        templateName);
                if (existingDefinition != null) {
                    /**
                     * Derive Api
                     */
                    CertificationDefinition uiDefinitionCopy = (CertificationDefinition) existingDefinition
                            .derive((Resolver) context);
                    uiDefinitionCopy.setType(Certification.Type.Identity);
                    schedule = new CertificationSchedule(context, requestor, uiDefinitionCopy);
                    schedule.setRunNow(true);
                    definition = schedule.getDefinition();
                    definition.setCertifierSelectionType(CertificationDefinition.CertifierSelectionType.Manager);
                }
                /**
                 * Certification Event UI End
                 */
            }
            if (definition == null) {
                schedule = scheduler.initializeScheduleBean(requestor, Certification.Type.Identity);
                schedule.setRunNow(true);
                definition = schedule.getDefinition();
                definition.setCertifierSelectionType(CertificationDefinition.CertifierSelectionType.Manager);
            }
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Configure cert def");
            String moverMessage = "";
            String fieldValue = "";
            String personaEnabled = WrapperRuleLibrary.isPersonaEnabled(context);
            // Partial Leaver Mover Certification Message
            if (personaEnabled != null && personaEnabled.length() > 0 && personaEnabled.equalsIgnoreCase("TRUE")) {
                if (WrapperRuleLibrary.getRelationshipMessagePersona(context, identityName) != null) {
                    moverMessage = (String) WrapperRuleLibrary.getRelationshipMessagePersona(context, identityName);
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...moverMessage=" + moverMessage);
                }
                if (!(moverMessage.equals(""))) {
                    fieldValue = " " + moverMessage + " ";
                } else {
                    fieldValue = " ";
                }
            }
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "...fieldValue=" + fieldValue);
            certName = certificationNamePrefix + identity.getDisplayName() + fieldValue + certificationNameSuffix + " "
                    + certifierName;
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "...certName=" + certName);
            definition.setNameTemplate(certName);
            definition.setShortNameTemplate(certName);
            definition.setName(certName + " [" + new Date().toString() + "]");
            definition.setIdentitiesToCertify(identities);
            definition.setCertifierName(certifierName);
            definition.setOwner(certGroupOwner);
            if (certifier != null) {
                List certiferIds = new ArrayList();
                certiferIds.add(certifier.getId());
                definition.setOwnerIds(certiferIds);
            }
            definition.setCertificationOwner(certGroupOwner);
            definition.setCertificationNameTemplate(certName);
            if (applications != null) {
                // definition.setIncludedApplications(applications);
                List includedApplicationIds = new ArrayList();
                for (Application applicationOb : applications) {
                    includedApplicationIds.add(applicationOb.getId());
                }
                definition.setIncludedApplicationIds(includedApplicationIds);
            }
            // Set Tag
            List tags = new ArrayList();
            tags.add(tag);
            definition.setTags(tags);
            // Set Staging
            if (stagingEnabled != null && stagingEnabled.equalsIgnoreCase("true")) {
                definition.setStagingEnabled(true);
            } else if (stagingEnabled != null && stagingEnabled.equalsIgnoreCase("false")) {
                definition.setStagingEnabled(false);
            }
            // All these properties can be set from custom artifact.
            if (!uiTemplate || templateName == null) {
                definition.setAllowItemDelegation(false);
                definition.setIncludePolicyViolations(Boolean.parseBoolean(allowPolicyViolation));
                definition.setIncludeRoles(Boolean.parseBoolean(allowRoles));
                definition.setAllowAccountRevocation(Boolean.parseBoolean(allowAccountRevocation));
                definition.setAllowEntityBulkAccountRevocation(Boolean.parseBoolean(allowBulkAccountRevocation));
                definition.setAllowEntityBulkRevocation(Boolean.parseBoolean(allowBulkItemsRevocation));
                definition.setAllowEntityBulkApprove(Boolean.parseBoolean(allowBulkApproval));
                definition.setAllowEntityBulkClearDecisions(Boolean.parseBoolean(allowBulkClearDecisions));
                definition.setIncludeAdditionalEntitlements(Boolean.parseBoolean(allowAdditionalEntitlements));
                definition.setDisplayEntitlementDescriptions(Boolean.parseBoolean(displayEntitlementDescription));
                definition.setAssimilateBulkReassignments(Boolean.parseBoolean(assimilateBulkReassignments));
                definition.setAllowProvisioningRequirements(Boolean.parseBoolean(allowProvisioningRequirements));
                definition.setCertificationDelegationReviewRequired(Boolean.parseBoolean(delegationReviewRequired));
                definition.setUpdateAttributeAssignments(Boolean.parseBoolean(updateAttributeAssignment));
                // List Bulk Actions Start
                definition.setAllowListBulkReassign(Boolean.parseBoolean(listBulkReassign));
                definition.setAllowListBulkMitigate(Boolean.parseBoolean(listBulkMitigate));
                definition.setAllowListBulkRevoke(Boolean.parseBoolean(listBulkRevoke));
                definition.setAllowListBulkAccountRevocation(Boolean.parseBoolean(listBulkAccountRevocation));
                definition.setAllowListBulkRevocation(Boolean.parseBoolean(listBulkRevocation));
                definition.setAllowListBulkApprove(Boolean.parseBoolean(listBulkApprove));
                definition.setAllowListBulkClearDecisions(Boolean.parseBoolean(listBulkClearDecisions));
                definition.setRequireBulkCertifyConfirmation(Boolean.parseBoolean(requireBulkCertify));
                // List Bulk Actions End
                definition.setProcessRevokesImmediately(Boolean.parseBoolean(processRevokesImmediately));
                definition.setRequireReassignmentCompletion(Boolean.parseBoolean(requireReassignmentCompletion));
                definition.setAutomateSignOffOnReassignment(Boolean.parseBoolean(automaticSignOffOnAllReassignments));
                definition.setElectronicSignatureRequired(Boolean.parseBoolean(requireElectronicSignature));
                definition.setAutoSignOffWhenNothingToCertify(
                        Boolean.parseBoolean(automaticallSignOffWhenNothingToCertify));
                if (certifyEmptyAccounts != null && certifyEmptyAccounts.equalsIgnoreCase("true")) {
                    definition.setCertifyEmptyAccounts(true);
                } else if (certifyEmptyAccounts != null && certifyEmptyAccounts.equalsIgnoreCase("false")) {
                    definition.setCertifyEmptyAccounts(false);
                } else if (certifyEmptyAccounts == null || certifyEmptyAccounts.length() <= 0) {
                    definition.setCertifyEmptyAccounts(false);
                }
                if (certifyAccounts != null && certifyAccounts.equalsIgnoreCase("true")) {
                    definition.setCertifyAccounts(true);
                } else if (certifyAccounts != null && certifyAccounts.equalsIgnoreCase("false")) {
                    definition.setCertifyAccounts(false);
                } else if (certifyAccounts == null || certifyAccounts.length() <= 0) {
                    definition.setCertifyAccounts(false);
                }
                definition.setItemCustomizationRuleName(null);
                definition.setEmailTemplateNameFor("certificationEmailTemplate", certificationEmail);
                if (exclusionRule != null) {
                    definition.setExclusionRuleName(exclusionRule);
                }
                if (saveExclusions != null && saveExclusions.equalsIgnoreCase("true")) {
                    definition.setSaveExclusions(true);
                } else if (saveExclusions != null && saveExclusions.equalsIgnoreCase("false")) {
                    definition.setSaveExclusions(false);
                } else if (saveExclusions == null || saveExclusions.length() <= 0) {
                    definition.setSaveExclusions(false);
                }
                if (allowException != null && allowException.equalsIgnoreCase("true") && allowedDuration != null) {
                    definition.setAllowExceptions(true);
                    definition.setAllowExceptionDurationAmount(allowedDuration);
                    definition.setAllowExceptionDurationScale(Duration.Scale.Day);
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...allowExceptionPopup.." + allowExceptionPopup);
                    definition.setAllowExceptionPopup(Boolean.parseBoolean(allowExceptionPopup));
                } else {
                    definition.setAllowExceptions(false);
                }
                if (certificationActivePhaseEnterRule != null) {
                    definition.setActivePhaseEnterRuleName(certificationActivePhaseEnterRule);
                }
                if (certificationRemediationPhaseEnterRule != null) {
                    definition.setRemediationPhaseEnterRuleName(certificationRemediationPhaseEnterRule);
                }
                if (certificationRemediationPhaseExitRule != null) {
                    definition.setEndPhaseEnterRuleName(certificationRemediationPhaseExitRule);
                }
                definition.setActivePeriodDurationAmount(activePeriod);
                definition.setActivePeriodDurationScale(Duration.Scale.Day);
                if (allowRemediation != null && allowRemediation.equalsIgnoreCase("true")
                        && remediationPeriod != null) {
                    definition.setRemediationPeriodDurationAmount(activePeriod);
                    definition.setRemediationPeriodDurationScale(Duration.Scale.Day);
                    definition.setRemediationPeriodEnabled(true);
                } else {
                    definition.setProcessRevokesImmediately(false);
                }
                if (automaticClosing != null && automaticClosing.equalsIgnoreCase("true")) {
                    definition.setAutomaticClosingEnabled(true);
                    definition.setAutomaticClosingSigner(certifier);
                    if (automaticClosingAction.equalsIgnoreCase("Remediated")) {
                        definition.setAutomaticClosingAction(CertificationAction.Status.Remediated);
                    } else if (automaticClosingAction.equalsIgnoreCase("Approved")) {
                        definition.setAutomaticClosingAction(CertificationAction.Status.Approved);
                    } else if (automaticClosingAction.equalsIgnoreCase("Mitigated")) {
                        definition.setAutomaticClosingAction(CertificationAction.Status.Mitigated);
                    } else {
                        definition.setAutomaticClosingAction(CertificationAction.Status.Remediated);
                    }
                    definition.setAutomaticClosingIntervalScale(Duration.Scale.Day);
                    definition.setAutomaticClosingInterval(automaticClosingDays);
                    if (automaticClosingComments != null) {
                        definition.setAutomaticClosingComments(automaticClosingComments);
                    }
                } else {
                    definition.setAutomaticClosingEnabled(false);
                }
                if (!notificationEmail.equals("")) {
                    NotificationConfig notificationConfig = new NotificationConfig();
                    notificationConfig.setEnabled(true);
                    notificationConfig.setConfigs(new ArrayList());
                    ReminderConfig reminderConfig = new ReminderConfig();
                    notificationConfig.getConfigs().add(reminderConfig);
                    reminderConfig.setBefore(false);
                    long reminderMillisFromStart = afterStart * Util.MILLI_IN_DAY;
                    reminderConfig.setMillis(reminderMillisFromStart);
                    reminderConfig.setEnabled(true);
                    if (!notificationEmail.equals("")) {
                        reminderConfig.setEmailTemplateName(notificationEmail);
                    }
                    long reminderFrequency = remindFrequency * Util.MILLI_IN_DAY;
                    reminderConfig.setFrequency(reminderFrequency);
                    if (!additionalNotificationEmailTemplate.equals("")) {
                        if (activePeriod != null && additionalBeforeEnd != null) {
                            NotificationConfig.ReminderConfig additionalConfig = new NotificationConfig.ReminderConfig();
                            additionalConfig.setEmailTemplateName(additionalNotificationEmailTemplate);
                            additionalConfig.setEnabled(true);
                            additionalConfig.setOnce(true);
                            additionalConfig.setBefore(true);
                            additionalConfig.setAdditionalRecipientsPresent(true);
                            additionalConfig.setAdditionalRecipientsRuleName(additionalNotificationRule);
                            long reminderMillisBeforeEnd = additionalBeforeEnd * Util.MILLI_IN_DAY;
                            additionalConfig.setMillis(reminderMillisBeforeEnd);
                            notificationConfig.getConfigs().add(additionalConfig);
                        }
                    }
                    definition.setCertificationNotificationConfig(notificationConfig);
                }
                // Set Bulk Reassigment Email
                if (bulkReassignmentEmail != null && bulkReassignmentEmail.length() > 0) {
                    definition.setEmailTemplateNameFor(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE,
                            bulkReassignmentEmail);
                }
                // Set Suppress Email When Nothing to Certify
                if (suppressEmailWhenNothingToCertify != null
                        && suppressEmailWhenNothingToCertify.equalsIgnoreCase("true")) {
                    LogEnablement.isLogDebugEnabled(roadUtilLogger,
                            "suppressEmailWhenNothingToCertify " + suppressEmailWhenNothingToCertify);
                    definition.setSuppressEmailWhenNothingToCertify(true);
                }
                // Set Delegation Stuff
                if (preDelegationRuleName != null && preDelegationRuleName.length() > 0) {
                    definition.setPreDelegationRuleName(preDelegationRuleName);
                }
                if (sendPreDelegationCompletionEmails != null
                        && sendPreDelegationCompletionEmails.equalsIgnoreCase("true")) {
                    definition.setSendPreDelegationCompletionEmails(true);
                } else if (sendPreDelegationCompletionEmails != null
                        && sendPreDelegationCompletionEmails.equalsIgnoreCase("false")) {
                    definition.setSendPreDelegationCompletionEmails(false);
                }
                if (allowEntityDelegation != null && allowEntityDelegation.equalsIgnoreCase("true")) {
                    definition.setAllowEntityDelegation(true);
                } else if (allowEntityDelegation != null && allowEntityDelegation.equalsIgnoreCase("false")) {
                    definition.setAllowEntityDelegation(false);
                }
                if (delegationForwardingDisabled != null && delegationForwardingDisabled.equalsIgnoreCase("true")) {
                    definition.setDelegationForwardingDisabled(true);
                } else if (delegationForwardingDisabled != null
                        && delegationForwardingDisabled.equalsIgnoreCase("false")) {
                    definition.setDelegationForwardingDisabled(false);
                }
                if (allowItemDelegation != null && allowItemDelegation.equalsIgnoreCase("true")) {
                    definition.setAllowItemDelegation(true);
                } else if (allowItemDelegation != null && allowItemDelegation.equalsIgnoreCase("false")) {
                    definition.setAllowItemDelegation(false);
                }
                if (delegationEmail != null && delegationEmail.length() > 0) {
                    definition.setEmailTemplateNameFor(Configuration.DELEGATION_EMAIL_TEMPLATE, delegationEmail);
                }
                if (delegationCompletionEmail != null && delegationCompletionEmail.length() > 0) {
                    definition.setEmailTemplateNameFor(Configuration.DELEGATION_FINISHED_EMAIL_TEMPLATE,
                            delegationCompletionEmail);
                }
                // Set Reassignment
                if (limitReassignments != null && limitReassignments.equalsIgnoreCase("true")) {
                    definition.setLimitReassignments(true);
                } else if (limitReassignments != null && limitReassignments.equalsIgnoreCase("false")) {
                    definition.setLimitReassignments(false);
                }
                // Set Sign Off Rule
                if (signOffApproverRuleName != null && signOffApproverRuleName.length() > 0) {
                    definition.setApproverRuleName(signOffApproverRuleName);
                }
                // Set Require Approval Comments
                if (requireApprovalComments != null && requireApprovalComments.equalsIgnoreCase("true")) {
                    definition.setRequireApprovalComments(true);
                } else if (requireApprovalComments != null && requireApprovalComments.equalsIgnoreCase("false")) {
                    definition.setRequireApprovalComments(false);
                }
                // Set Require Mitigation Comments
                if (requireMitigationComments != null && requireMitigationComments.equalsIgnoreCase("true")) {
                    definition.setRequireMitigationComments(true);
                } else if (requireMitigationComments != null && requireMitigationComments.equalsIgnoreCase("false")) {
                    definition.setRequireMitigationComments(false);
                }
            }
            roadUtilLogger
            .debug("...Schedule task to run, passing in schedule (which has certficiaton defintion attached)");
            TaskSchedule taskSchedule = scheduler.saveSchedule(schedule, false);
            context.decache(identity);
        }
        ProvisioningPlan plan = null;
        AuditRuleLibrary.logRoadAuditEvents(context, plan, null, null, identityName, feature, certName, launcher, false,
                null);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit launchCertification");
    }
    /**
     * 
     * @param context
     * @return
     * @throws GeneralException
     */
    public static List getUniqueRelationships(SailPointContext context, String personaEnabled) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter....getUniqueRelationships");
        String enabled = ObjectConfigAttributesRuleLibrary.extendedAttrPersonaEnabled(context);
        if (personaEnabled != null && personaEnabled.equalsIgnoreCase("true")
                || (enabled != null && enabled.equalsIgnoreCase("True"))) {
            if (personaList != null && personaList.size() > 0) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger, "Existing....getUniqueRelationships");
                LogEnablement.isLogDebugEnabled(roadUtilLogger, "End....getUniqueRelationships");
                return personaList;
            }
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "Querying....getUniqueRelationships");
            QueryOptions ops = new QueryOptions();
            ops.setDistinct(true);
            // ignore case here Oracle Index
            ops.add(Filter.ignoreCase(Filter.eq("attributeName", WrapperRuleLibrary.PERSONARELATIONSHIPS)));
            ops.addFilter(Filter.or(
                    Filter.ignoreCase(Filter.like("value", WrapperRuleLibrary.PERSONAEMPLOYEE, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("value", WrapperRuleLibrary.PERSONASTUDENT, Filter.MatchMode.START)),
                    Filter.ignoreCase(
                            Filter.like("value", WrapperRuleLibrary.PERSONANONEMPLOYEE, Filter.MatchMode.START))));
            ops.addFilter(Filter
                    .ignoreCase(Filter.like("value", WrapperRuleLibrary.PERSONAACTIVE, Filter.MatchMode.ANYWHERE)));
            List<String> props = new ArrayList<String>();
            props.add("value");
            Iterator<Object[]> result = (Iterator<Object[]>) context.search(IdentityExternalAttribute.class, ops,
                    props);
            while (result.hasNext()) {
                String value = (String) (result.next()[0]);
                // Get All Active Relationships and Replace them with Inactive
                // to do startsWith on dropped relationships
                if (value.contains(WrapperRuleLibrary.PERSONAACTIVE)) {
                    value = value.replaceAll("ACTIVE", "INACTIVE");
                }
                if (value.contains("]")) {
                    value = value.substring(0, value.indexOf("]") + 1);
                }
                personaList.add(value);
            }
            if (personaList != null && personaList.size() > 0) {
                Util.removeDuplicates(personaList);
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End....getUniqueRelationships");
        return personaList;
    }
    /**
     * Get Application Schema Attributes
     * 
     * @param appName
     * @return
     * @throws Exception
     */
    public static List getApplicationSchemaAttributes(SailPointContext context, String appName) throws Exception {
        roadUtilLogger.debug("Enter getApplicationSchemaAttributes..");
        List masterList = new ArrayList();
        if (appName != null) {
            Application app = context.getObjectByName(Application.class, appName);
            if (app != null) {
                Schema schema = app.getAccountSchema();
                if (schema != null) {
                    masterList = schema.getAttributeNames();
                }
                context.decache(app);
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit getApplicationSchemaAttributes ");
        return masterList;
    }
    /**
     * Method to check if Lifecycle Event is Enabled or Not
     * 
     * @param context
     * @param triggerName
     * @return
     * @throws GeneralException
     */
    public static boolean roadFeatureDisabled(SailPointContext context, String triggerName) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter roadFeatureDisabled " + triggerName);
        /**
         * Default is Disabled
         */
        boolean result = true;
        IdentityTrigger identityTrigger = null;
        try {
            identityTrigger = context.getObjectByName(IdentityTrigger.class, triggerName);
            if (identityTrigger != null) {
                /**
                 * Not Found Default is Disabled
                 */
                if (!identityTrigger.isDisabled()) {
                    /**
                     * Found and Not Disabled
                     */
                    result = false;
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "Accelerator Pack Feature is Enabled ");
                }
            }
        } catch (Exception ex) {
            throw new GeneralException(ex.getMessage());
        } finally {
            if (identityTrigger != null)
                context.decache(identityTrigger);
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End roadFeatureDisabled ");
        return result;
    }
    /**
     * Method to check if Lifecycle Event is Enabled or Not
     * 
     * @param context
     * @param triggerName
     * @return
     * @throws GeneralException
     */
    public static String roadFeatureDisabledString(SailPointContext context, String triggerName)
            throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter roadFeatureDisabledString ");
        if (roadFeatureDisabled(context, triggerName)) {
            return "True";
        } else {
            return "False";
        }
    }
    /**
     * Check Accelerator Pack Identity Attributes Disabled
     * 
     * @param context
     * @param objectName
     * @param attrName
     * @throws GeneralException
     */
    public static String roadAttributeDisabled(SailPointContext context, String objectName, String attrName)
            throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter roadAttributeDisabled ");
        // By Default, it is disabled
        String result = "True";
        ObjectConfig objectConfig = null;
        try {
            if (objectName != null && objectName.equalsIgnoreCase("Identity")) {
                objectConfig = Identity.getObjectConfig();
            } else if (objectName != null && objectName.equalsIgnoreCase("Bundle")) {
                objectConfig = Bundle.getObjectConfig();
            } else if (objectName != null && objectName.equalsIgnoreCase("Application")) {
                objectConfig = Application.getObjectConfig();
            } else if (objectName != null && objectName.equalsIgnoreCase("Link")) {
                objectConfig = Link.getObjectConfig();
            } else if (objectName != null && objectName.equalsIgnoreCase("ManagedAttribute")) {
                objectConfig = ManagedAttribute.getObjectConfig();
            } else if (objectName != null && objectName.equalsIgnoreCase("Target")) {
                objectConfig = Target.getObjectConfig();
            } else if (objectName != null && objectName.equalsIgnoreCase("Alert")) {
                objectConfig = Alert.getObjectConfig();
            }
            if (objectConfig != null) {
                List<ObjectAttribute> objAttrList = objectConfig.getObjectAttributes();
                if (objAttrList != null && objAttrList.size() > 0) {
                    for (ObjectAttribute objAttr : objAttrList) {
                        if (objAttr != null && objAttr.getName() != null
                                && objAttr.getName().equalsIgnoreCase(attrName)) {
                            // If attribute found, disabled is false and break
                            result = "false";
                            break;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new GeneralException(ex.getMessage());
        }
        return result;
    }
    /**
     * Auto Approve Request
     * 
     * @param approvalSet
     * @param approvedBy
     * @param message
     */
    public static void roadDoAutoApprove(ApprovalSet approvalSet, String approvedBy, String message) {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter doAutoApprove");
        if (approvalSet != null) {
            List<ApprovalItem> items = approvalSet.getItems();
            if (items != null && items.size() > 0) {
                for (ApprovalItem item : items) {
                    item.approve();
                    item.add(new Comment(message, approvedBy));
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit doAutoApprove");
    }
    /**
     * Save Expiration Date on Work Items
     * 
     * @param item
     * @param context
     * @param workflow
     * @param method
     * @param wfVariable
     * @param workItemConfig
     * @param expirationAttribute
     * @throws GeneralException
     */
    public static void roadSaveExpirationDate(WorkItem item, SailPointContext context, Workflow workflow, String method,
            String wfVariable, String workItemConfig, String expirationAttribute) throws GeneralException {
        Date expirationDate = new Date();
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter roadSaveExpirationDate");
        if (method != null && method.equals(Workflow.INTERCEPTOR_OPEN_WORK_ITEM)) {
            if (workflow != null) {
                Attributes configAttrs = (Attributes) workflow.get(workItemConfig);
                if (configAttrs != null) {
                    String hoursTillExpiration = (String) configAttrs.get(expirationAttribute);
                    if (hoursTillExpiration != null) {
                        int hoursTillExpirationInt = Util.atoi(hoursTillExpiration);
                        if (hoursTillExpirationInt > 0) {
                            int minutes = hoursTillExpirationInt * 60;
                            Date dateExp = Util.incrementDateByMinutes(new Date(), minutes);
                            if (item != null && item.getExpirationDate() == null && context != null
                                    && dateExp != null) {
                                item.setExpiration(dateExp);
                                context.saveObject(item);
                                context.commitTransaction();
                            }
                        }
                    }
                }
            }
        }
        if (method != null && method.equals(Workflow.INTERCEPTOR_END_APPROVAL)) {
            if (item != null && item.getState() != null) {
                if (item.getState().equals(WorkItem.State.Expired)) {
                    workflow.put(wfVariable, true);
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit roadSaveExpirationDate");
    }
    /**
     * Auto Reject Work Items
     * 
     * @param approvalSet
     * @param autoRejectedBy
     * @param message
     */
    public static void roadDoAutoReject(ApprovalSet approvalSet, String autoRejectedBy, String message) {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter roadDoAutoReject");
        if (approvalSet != null && message != null && autoRejectedBy != null) {
            List<ApprovalItem> items = approvalSet.getItems();
            if (items != null && items.size() > 0) {
                for (ApprovalItem item : items) {
                    if (item != null) {
                        item.reject();
                        item.setRejecters(autoRejectedBy);
                        List listOfComments = item.getComments();
                        String stringRequesterComments = item.getRequesterComments();
                        item.add(new Comment(message, autoRejectedBy));
                    }
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit roadDoAutoReject");
    }
    /**
     * Check to See IF WorkItem is Expired
     * 
     * @param wfcontext
     * @param context
     * @return
     * @throws GeneralException
     */
    public static boolean isRoadWorkItemExpired(WorkflowContext wfcontext, SailPointContext context)
            throws GeneralException {
        boolean expired = false;
        ApprovalSet approvalSet;
        Attributes args = wfcontext.getArguments();
        String irId = Util.getString(args, ROADUtil.REQUESTID);
        if (context != null && wfcontext != null) {
            if (irId == null) {
                WorkflowContext top = wfcontext.getRootContext();
                irId = (String) top.getVariable(ROADUtil.REQUESTID);
            }
            if (irId != null) {
                IdentityRequest identityRequest = context.getObjectByName(IdentityRequest.class, irId);
                if (identityRequest != null) {
                    List approvalSummaries = (List) identityRequest.getAttribute(ROADUtil.APPROVALSUMMARY);
                    if (approvalSummaries != null && approvalSummaries.size() > 0) {
                        for (int i = 0; i < approvalSummaries.size(); i++) {
                            ApprovalSummary approvalSummary = (ApprovalSummary) approvalSummaries.get(i);
                            if (approvalSummary != null) {
                                if (approvalSummary.getState() != null
                                        && (WorkItem.State.Expired.equals(approvalSummary.getState()))) {
                                    expired = true;
                                    approvalSet = approvalSummary.getApprovalSet();
                                    break;
                                }
                            }
                        }
                    }
                    context.decache(identityRequest);
                }
            }
        }
        return expired;
    }
    /**
     * Creates a Snapshot of the selected Identity
     *
     * @param identityName
     *            String, requestType String
     * @return void
     * @throws GeneralException
     */
    public static void createIdentitySnapshot(SailPointContext context, String identityName, String requestType)
            throws GeneralException {
        try {
            // Validate the request type with allowed values list.
            if (getCommonFrameworkCustomSnapshot(context, requestType)) {
                // Get the Identity Object
                Identity identity = context.getObjectByName(Identity.class, identityName);
                // Create a instance of Identity Archiver to take the Snapshot
                IdentityArchiver identityArchiver = new IdentityArchiver(context);
                // Creating Snapshot Object
                IdentitySnapshot snap = identityArchiver.createSnapshot(identity);
                // Saving Snapshot Object
                context.saveObject(snap);
            }
        } finally {
            context.commitTransaction();
        }
    }
    /**
     * Get SnapShot Settings
     * 
     * @param requestType
     * @return
     * @throws GeneralException
     */
    public static boolean getCommonFrameworkCustomSnapshot(SailPointContext context, String requestType)
            throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter getCommonFrameworkCustomSnapshot");
        boolean returnVal = false;
        List resquestTypeList = new ArrayList();
        Map map = new HashMap();
        map = ROADUtil.getCustomGlobalMap(context);
        if (map != null && map.containsKey(ROADUtil.SNAPSHOT) && map.get(ROADUtil.SNAPSHOT) instanceof List) {
            resquestTypeList = (List) map.get(ROADUtil.SNAPSHOT);
        }
        // Check if list contains key
        if (resquestTypeList.contains(requestType)) {
            returnVal = true;
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit getCommonFrameworkCustomSnapshot");
        return returnVal;
    }
    /**
     * Is Link Disabled
     * 
     * @param link
     * @return
     * @throws GeneralException
     */
    public static String isLinkDisabled(Link link) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter isLinkDisabled");
        String iiqDisabled = "";
        if (link.getAttribute(ROADUtil.DISABLED) != null) {
            if (link.getAttribute(ROADUtil.DISABLED) instanceof Boolean) {
                if ((boolean) link.getAttribute(ROADUtil.DISABLED)) {
                    iiqDisabled = "true";
                } else {
                    iiqDisabled = "false";
                }
            } else if (link.getAttribute(ROADUtil.DISABLED) instanceof String) {
                if (((String) link.getAttribute(ROADUtil.DISABLED)).equalsIgnoreCase("true")) {
                    iiqDisabled = "true";
                } else {
                    iiqDisabled = "false";
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit isLinkDisabled");
        return iiqDisabled;
    }
    /**
     * Is Link Locked
     * 
     * @param link
     * @return
     * @throws GeneralException
     */
    public static String isLinkLocked(Link link) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter isLinkLocked");
        String iiqLocked = "";
        if (link.getAttribute(ROADUtil.LOCKED) != null) {
            if (link.getAttribute(ROADUtil.LOCKED) instanceof Boolean) {
                if ((boolean) link.getAttribute(ROADUtil.LOCKED)) {
                    iiqLocked = "true";
                } else {
                    iiqLocked = "false";
                }
            } else if (link.getAttribute(ROADUtil.LOCKED) instanceof String) {
                if (((String) link.getAttribute(ROADUtil.LOCKED)).equalsIgnoreCase("true")) {
                    iiqLocked = "true";
                } else {
                    iiqLocked = "false";
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit isLinkLocked");
        return iiqLocked;
    }
    /**
     * Is Application Manual
     * 
     * @param context
     * @param appName
     * @return
     * @throws GeneralException
     */
    public static boolean isManualWorkItemApplication(SailPointContext context, String appName)
            throws GeneralException {
        String featureString = Feature.PROVISIONING.toString();
        QueryOptions qo = new QueryOptions(Filter.and(Filter.like(FEATURE, featureString), Filter.eq("name", appName)));
        int count = context.countObjects(Application.class, qo);
        if (count == 1) {
            return false;
        }
        return true;
    }
    /**
     * Is Direct Connector
     * 
     * @param context
     * @param appName
     * @return
     * @throws GeneralException
     */
    public static boolean isDirectConnectorApplication(SailPointContext context, String appName)
            throws GeneralException {
        String featureString = Feature.PROVISIONING.toString();
        QueryOptions qo = new QueryOptions(Filter.and(Filter.like(FEATURE, featureString), Filter.eq("name", appName)));
        int count = context.countObjects(Application.class, qo);
        if (count == 1) {
            return true;
        }
        return false;
    }
    /**
     * Get Authoritative Repositories that supports provisioning
     * 
     * @param context
     * @param repoName
     * @return
     * @throws GeneralException
     */
    public static List getRepositories(SailPointContext context, String repoName) throws GeneralException {
        String featureString = Feature.PROVISIONING.toString();
        List repos = new ArrayList();
        QueryOptions qo = new QueryOptions(
                Filter.and(Filter.like(FEATURE, featureString), Filter.eq("authoritative", true)));
        List propsApp = new ArrayList();
        propsApp.add("id");
        Iterator iterApp = context.search(Application.class, qo, propsApp);
        if (iterApp != null) {
            try {
                while (iterApp.hasNext()) {
                    Object[] rowApp = (Object[]) iterApp.next();
                    if (rowApp != null && rowApp.length == 1) {
                        String appId = (String) rowApp[0];
                        if (appId != null) {
                            LogEnablement.isLogDebugEnabled(roadUtilLogger, "appId.." + appId);
                            Application appObj = context.getObjectById(Application.class, appId);
                            if (appObj != null && appObj.getAttributes() != null) {
                                Attributes attrs = appObj.getAttributes();
                                if (attrs != null && attrs.containsKey(repoName) && attrs.get(repoName) != null
                                        && ((String) attrs.get(repoName)).equalsIgnoreCase("True")) {
                                    repos.add(appObj.getName());
                                }
                                context.decache(appObj);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LogEnablement.isLogErrorEnabled(roadUtilLogger, "...Application Error " + e.getMessage());
            }
            Util.flushIterator(iterApp);
        }
        return repos;
    }
    /**
     * Is Plan Ticket Integration
     * 
     * @param context
     * @param name
     * @return
     * @throws GeneralException
     */
    public static boolean isTicketIntegrationPlan(SailPointContext context, String name) throws GeneralException {
        IntegrationConfig config = context.getObjectByName(IntegrationConfig.class, name);
        if (config != null) {
            return true;
        }
        return false;
    }
    /**
     * Get Recent SailPoint Object
     * 
     * @param context
     * @param filter
     * @param className
     * @param identity
     * @return
     * @throws GeneralException
     */
    public static String getRecentSailPointObjectId(SailPointContext context, Filter filter, String className)
            throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter getRecentSailPointObjectId");
        Class clazz = getSailPointObjectClazzFromClassList(className);
        String id = null;
        if (clazz != null) {
            Object[] result = null;
            Date created = null;
            if (clazz != null) {
                QueryOptions ops = new QueryOptions();
                String[] propertyProjection = new String[] { "id", "created" };
                ops.setOrderBy("created");
                ops.setOrderAscending(false);
                ops.setResultLimit(1);
                ops.add(filter);
                Iterator<Object[]> resutIt = context.search(clazz, ops, Arrays.asList(propertyProjection));
                if (resutIt != null) {
                    while (resutIt.hasNext()) {
                        result = resutIt.next();
                        if (result != null && result.length == 2) {
                            id = (String) result[0];
                            created = (Date) result[1];
                        }
                    }
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End getRecentSailPointObjectId id " + id);
        return id;
    }
    /**
     * Is Form Enabled
     * 
     * @param context
     * @param filter
     * @param className
     * @param identity
     * @return
     * @throws GeneralException
     */
    public static String isFormEnabled(SailPointContext context, String formName) throws GeneralException {
        String id = null;
        Object[] result = null;
        if (formName != null) {
            QueryOptions ops = new QueryOptions();
            String[] propertyProjection = new String[] { "id" };
            ops.add(Filter.eq("name", formName));
            Iterator<Object[]> resutIt = context.search(Form.class, ops, Arrays.asList(propertyProjection));
            if (resutIt != null) {
                while (resutIt.hasNext()) {
                    result = resutIt.next();
                    if (result != null && result.length == 1) {
                        id = (String) result[0];
                    }
                }
            }
        }
        return id;
    }
    /**
     * Is Connector Enabled
     * 
     * @param context
     * @param filter
     * @param className
     * @param identity
     * @return
     * @throws GeneralException
     */
    public static String isConnectorEnabled(SailPointContext context, String connectorName) throws GeneralException {
        String id = null;
        Object[] result = null;
        if (connectorName != null) {
            QueryOptions ops = new QueryOptions();
            String[] propertyProjection = new String[] { "id" };
            ops.add(Filter.eq("connector", connectorName));
            Iterator<Object[]> resutIt = context.search(Application.class, ops, Arrays.asList(propertyProjection));
            if (resutIt != null) {
                while (resutIt.hasNext()) {
                    result = resutIt.next();
                    if (result != null && result.length == 1) {
                        id = (String) result[0];
                    }
                }
            }
        }
        return id;
    }
    /**
     * Return Authoritative Application Names
     * 
     * @param context
     * @return
     * @throws GeneralException
     */
    public static ArrayList getAuthoritativeApplicationNames(SailPointContext context) throws GeneralException {
        ArrayList list = new ArrayList();
        Filter filter = Filter.eq("authoritative", true);
        QueryOptions qo = new QueryOptions();
        qo.addFilter(filter);
        List propsApp = new ArrayList();
        propsApp.add("name");
        Iterator iterApp = context.search(Application.class, qo, propsApp);
        if (iterApp != null) {
            while (iterApp.hasNext()) {
                Object[] rowApp = (Object[]) iterApp.next();
                if (rowApp != null && rowApp.length == 1) {
                    String appName = (String) rowApp[0];
                    if (appName != null) {
                        list.add(appName);
                    }
                }
            }
        }
        return list;
    }
    /**
     * Find If Entitlement is Privileged or Not
     * 
     * @param context
     * @param attrName
     * @param valueStr
     * @param appName
     * @return
     * @throws GeneralException
     */
    public static boolean isEntPrivileged(SailPointContext context, String attrName, String valueStr, String appName)
            throws GeneralException {
        QueryOptions qo = new QueryOptions();
        boolean isPrivilegedAccess = false;
        Filter queryFilter = Filter.and(Filter.eq("attribute", attrName), Filter.eq("value", valueStr),
                Filter.eq("application.name", appName));
        qo.addFilter(queryFilter);
        List properties = new ArrayList();
        properties.add("id");
        properties.add(WrapperRuleLibrary.ENTITLEMENTPRIVILEGED);
        Iterator resultIterator = context.search(ManagedAttribute.class, qo, properties);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Find Managed Attribute resultIterator " + resultIterator);
        if (resultIterator != null) {
            while (resultIterator.hasNext()) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger, "resultIterator.hasNext()");
                Object[] retObjs = (Object[]) resultIterator.next();
                LogEnablement.isLogDebugEnabled(roadUtilLogger, "retObjs " + retObjs);
                LogEnablement.isLogDebugEnabled(roadUtilLogger, "retObjs.length " + retObjs.length);
                if (retObjs != null && retObjs.length == 2) {
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "retObjs[0] " + retObjs[0]);
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "retObjs[1] " + retObjs[1]);
                    if (retObjs[0] != null && retObjs[1] != null) {
                        String id = retObjs[0].toString();
                        String entPrivileged = retObjs[1].toString();
                        LogEnablement.isLogDebugEnabled(roadUtilLogger, "entPrivileged " + entPrivileged);
                        if (entPrivileged != null && entPrivileged.toString().equalsIgnoreCase("TRUE")) {
                            isPrivilegedAccess = true;
                        }
                    } else if (retObjs[0] != null && retObjs[1] == null) {
                        // entPrivileged
                        // has no value
                        isPrivilegedAccess = false;
                    } else if (retObjs[0] == null && retObjs[1] == null) {
                        // Entitlement doesn't exist or it is an expansion item
                        isPrivilegedAccess = false;
                        // Go to Next AttributeRequest
                        LogEnablement.isLogDebugEnabled(roadUtilLogger,
                                "Skip Validation because Entitlement doesn't exist or it is an expansion items");
                        continue;
                    }
                }
            }
        }
        return isPrivilegedAccess;
    }
    /**
     * Invoke Extended Rules defined on Applications
     * 
     * @param extendedRule
     * @param appName
     * @param requestType
     * @param spExtAttrs
     * @param extAttributeName
     * @param extAttributeValue
     * @param identityName
     * @param nativeId
     * @param useRuleFromApp
     * @return
     * @throws GeneralException
     */
    public static Object invokeInterceptorRules(SailPointContext context, Workflow workflow, ProvisioningPlan plan,
            ProvisioningProject project, String identityName, String requestType, String launcher, String source,
            String flow, String ruleName) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter invokeInterceptorRules");
        Object obj = null;
        Rule rule = null;
        if (ruleName != null) {
            rule = context.getObjectByName(Rule.class, ruleName);
            if (rule != null) {
                HashMap params = new HashMap();
                params.put("context", context);
                params.put("identityName", identityName);
                params.put("plan", plan);
                params.put("source", source);
                params.put("requestType", requestType);
                params.put("launcher", launcher);
                params.put("project", project);
                params.put("flow", flow);
                try {
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "...Run the rule");
                    obj = context.runRule(rule, params);
                } catch (Exception re) {
                    LogEnablement.isLogErrorEnabled(roadUtilLogger, "...Rule Exception " + re.getMessage());
                    throw new GeneralException("Error During Rule Launch..." + ruleName + " " + re.getMessage());
                }
                context.decache(rule);
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Exit invokeInterceptorRules");
        return obj;
    }
    /**
     * Execute Interceptor Plan Rule
     * 
     * @param identityName
     * @param project
     * @param requestType
     * @throws GeneralException
     */
    public static Object interceptorPlanRule(SailPointContext context, Workflow workflow, ProvisioningPlan plan,
            ProvisioningProject project, String identityName, String requestType, String launcher, String source,
            String flow) throws GeneralException
    {
        Object obj = null;
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Start  interceptorPlanRule");
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..identityName..." + identityName);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..requestType..." + requestType);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..launcher..." + launcher);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..source..." + source);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..flow..." + flow);
        Map map = new HashMap();
        // Common Configuration
        map = ROADUtil.getCustomGlobalMap(context);
        if (map != null && map.containsKey(ROADUtil.INTERCEPTORPLANRULEKEY)) {
            String ruleName = (String) map.get(ROADUtil.INTERCEPTORPLANRULEKEY);
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "..ruleName..." + ruleName);
            if (ruleName != null && ruleName.length() > 0) {
                obj = invokeInterceptorRules(context, workflow, plan, project, identityName, requestType, launcher,
                        source, flow, ruleName);
            }
        }
        if (obj != null) {
            if (workflow != null) {
                if (obj instanceof ProvisioningPlan) {
                    workflow.put("plan", obj);
                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "End  interceptorPlanRule New Plan");
                    return obj;
                }
            }
            return plan;
        } else {
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "End  interceptorPlanRule Old Plan");
            return plan;
        }
    }
    /**
     * Execute Interceptor Plan Rule
     * 
     * @param identityName
     * @param project
     * @param requestType
     * @throws GeneralException
     */
    public static Object interceptorProjectRule(SailPointContext context, Workflow workflow, ProvisioningPlan plan,
            ProvisioningProject project, String identityName, String requestType, String launcher, String source,
            String flow) throws GeneralException
    {
        Object obj = null;
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Start  interceptorProjectRule");
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..identityName..." + identityName);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..requestType..." + requestType);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..launcher..." + launcher);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..source..." + source);
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "..flow..." + flow);
        Map map = new HashMap();
        // Common Configuration
        map = ROADUtil.getCustomGlobalMap(context);
        if (map != null && map.containsKey(ROADUtil.INTERCEPTORPROJECTRULEKEY)) {
            String ruleName = (String) map.get(ROADUtil.INTERCEPTORPROJECTRULEKEY);
            if (ruleName != null && ruleName.length() > 0) {
                LogEnablement.isLogDebugEnabled(roadUtilLogger, "..ruleName..." + ruleName);
                obj = invokeInterceptorRules(context, workflow, plan, project, identityName, requestType, launcher,
                        source, flow, ruleName);
            }
        }
        if (obj != null) {
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "End  interceptorProjectRule New Project");
            if (workflow != null) {
                if (obj instanceof ProvisioningProject) {
                    workflow.put("project", obj);
                    return obj;
                }
            }
            return project;
        } else {
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "End  interceptorProjectRule Old Project");
            return project;
        }
    }
    /**
     * Get Secret Field Name
     * 
     * @param context
     * @param appName
     * @return
     * @throws GeneralException
     */
    public static String getSecretFieldName(SailPointContext context, String appName) throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Start getSecretFieldName..");
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "appName.." + appName);
        String fieldName = null;
        if (appName != null) {
            Application app = context.getObjectByName(Application.class, appName);
            List<Form> provisioningForms = app.getProvisioningForms();
            if (provisioningForms != null && provisioningForms.size() > 0) {
                for (Form provisioningForm : provisioningForms) {
                    if (provisioningForm.getType() != null && provisioningForm.getType().equals(Type.Create)
                            && provisioningForm.getObjectType() != null
                            && provisioningForm.getObjectType().equals("account")
                            && provisioningForm.getSections() != null) {
                        List<Section> sections = provisioningForm.getSections();
                        if (sections != null && sections.size() > 0) {
                            for (Section section : sections) {
                                LogEnablement.isLogDebugEnabled(roadUtilLogger, "section..");
                                if (section.getFields() != null && section.getFields().size() > 0) {
                                    List<Field> fields = section.getFields();
                                    LogEnablement.isLogDebugEnabled(roadUtilLogger, "fields..");
                                    if (fields != null && fields.size() > 0) {
                                        for (Field field : fields) {
                                            LogEnablement.isLogDebugEnabled(roadUtilLogger,
                                                    "field.getName().." + field.getName());
                                            if (field.getName() != null && field.getType() != null
                                                    && field.getType().equalsIgnoreCase("secret")) {
                                                fieldName = field.getName();
                                                LogEnablement.isLogDebugEnabled(roadUtilLogger,
                                                        "End getSecretFieldName.." + fieldName);
                                                return fieldName;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End getSecretFieldName.." + fieldName);
        return fieldName;
    }
    /**
     * Is Account Privileged
     * 
     * @param context
     * @param appName
     * @param nativeId
     * @return
     * @throws GeneralException
     */
    public static boolean isAccountPrivilegedPerApp(SailPointContext context, String appName, String nativeId)
            throws GeneralException {
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Enter isAccountPrivilegedPerApp");
        Filter privilegedAccountFilter = Filter.ignoreCase(Filter.eq("psAccount", "TRUE"));
        Filter nativeIdFilter = Filter.ignoreCase(Filter.eq("nativeIdentity", nativeId));
        Filter baseFilter = Filter.and(Filter.eq("application.name", appName), privilegedAccountFilter, nativeIdFilter);
        QueryOptions qo = new QueryOptions();
        qo.addFilter(baseFilter);
        boolean result = false;
        // Use a projection query first to return minimal data.
        ArrayList returnCols = new ArrayList();
        returnCols.add("id");
        // Execute the query against the IdentityIQ database.
        Iterator it = context.search(Link.class, qo, returnCols);
        if (it != null) {
            while (it.hasNext()) {
                Object[] retObjs = (Object[]) it.next();
                if (retObjs != null && retObjs.length == 1) {
                    if (retObjs[0] != null) {
                        result = true;
                    }
                }
            }
            Util.flushIterator(it);
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End isAccountPrivilegedPerApp " + result);
        return result;
    }
    /**
     * Get domain container path from distinguishedName
     * 
     * @param distinguishedName
     * @return
     */
    public static String getDomainDNFromDistinguishedDN(String distinguishedName) {
        String domainDN = null;
        if (Util.isNotNullOrEmpty(distinguishedName)) {
            int index = distinguishedName.toLowerCase().indexOf("dc=");
            if (index != -1) {
                domainDN = distinguishedName.toLowerCase().substring(index);
            }
        }
        return domainDN;
    }
    /**
     * Disable Static Email for Managers Flag
     * 
     * @param context
     * @return
     * @throws GeneralException
     */
    public static String getDisableFlagEmailContent(SailPointContext context) throws GeneralException {
        String result = "False";
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "Start  getDisableFlagEmailContent");
        Map map = new HashMap();
        // Common Configuration
        map = ROADUtil.getCustomGlobalMap(context);
        if (map != null && map.containsKey(ROADUtil.DISABLESTATICEMAILCONTENT)) {
            String disable = (String) map.get(ROADUtil.DISABLESTATICEMAILCONTENT);
            LogEnablement.isLogDebugEnabled(roadUtilLogger, "disable.." + disable);
            if (disable != null) {
                disable = disable.trim();
            }
            if (disable != null && disable.length() > 0) {
                result = disable;
            }
        }
        LogEnablement.isLogDebugEnabled(roadUtilLogger, "End  getDisableFlagEmailContent.." + result);
        return result;
    }
    /**
     * Get All Connector Types
     * 
     * @param ctx
     * @return
     * @throws GeneralException
     */
    public static List getConnectorTypes(SailPointContext ctx) throws GeneralException {
        List connectorTypes = new ArrayList();
        Configuration connectorRegistry = null;
        connectorRegistry = ctx.getObject(Configuration.class, Configuration.CONNECTOR_REGISTRY);
        if (connectorRegistry != null) {
            List<Application> apps = (List<Application>) connectorRegistry.getList(Configuration.APPLICATION_TEMPLATES);
            if (apps != null) {
                for (Application app : apps) {
                    Object isDeprecatedConnector = app.getAttributeValue("DeprecatedConnector");
                    if ((null == isDeprecatedConnector || isDeprecatedConnector.toString().equalsIgnoreCase("false"))) {
                        connectorTypes.add(app.getType());
                    }
                }
            }
            ctx.decache(connectorRegistry);
        }
        return connectorTypes;
    }
}
