/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Resolver;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.service.certification.schedule.CertificationScheduleAdditionalEntitlementsListFilterContext;
import sailpoint.service.certification.schedule.CertificationScheduleIdentityListFilterContext;
import sailpoint.service.certification.schedule.CertificationScheduleRoleListFilterContext;
import sailpoint.service.listfilter.ListFilterContext;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.WorkflowContext;


public class CertificationLauncher {

    private static Log log = LogFactory.getLog(CertificationLauncher.class);

    public static String RAPIDSETUP_CERT_PARAM_NAME_TEMPLATE = "nameTemplate";
    public static String RAPIDSETUP_CERT_PARAM_STAGE_CERT = "enableCertificationStaging";
    public static String RAPIDSETUP_CERT_PARAM_INCLUDE_BIRTHRIGHT_ROLES = "includeBirthrightRoles";
    public static String RAPIDSETUP_CERT_PARAM_BACKUP_CERTIFIER = "backupCertifier";

    public static String RAPIDSETUP_CERT_PARAM_OWNER = "certificationOwner";
    public static String RAPIDSETUP_CERT_PARAM_NEW_CERT_DEF_NAME = "newCertDefName";
    public static String RAPIDSETUP_CERT_PARAM_APPLICATION_PROPERTY = "application";
    public static String RAPIDSETUP_CERT_PARAM_INCLUDE_PREV_MANAGER = "prevManagerIncluded";

    public static String RAPIDSETUP_CERT_PARAM_IDENTITY_NAME = "identityName";
    public static String RAPIDSETUP_CERT_PARAM_TEMPLATE_NAME = "templateName";
    public static String RAPIDSETUP_CERT_PARAM_WAIT_SECONDS = "certGenMaxWaitSeconds";
    private static int DEFAULT_CERT_GEN_MAX_WAIT_SECONDS = 100;
    private static String RAPIDSETUP_CERT_RESULT_NAME = "certGenResultName";

    public boolean launchAndWait(WorkflowContext wfc) throws GeneralException {

        log.debug("Launching certification: Starting from template");

        SailPointContext spCtx = wfc.getSailPointContext();

        Attributes<String, Object> args = wfc.getArguments();

        Map<String, Object> parameters = (Map<String, Object>) args.get("params");
        String identityName = Util.getString(args, RAPIDSETUP_CERT_PARAM_IDENTITY_NAME);
        String templateName = Util.getString(args, RAPIDSETUP_CERT_PARAM_TEMPLATE_NAME);
        int waitSeconds = Util.getInt(args, RAPIDSETUP_CERT_PARAM_WAIT_SECONDS);
        if (waitSeconds <= 0) {
            waitSeconds = DEFAULT_CERT_GEN_MAX_WAIT_SECONDS;
        }
        IdentityChangeEvent changeEvent = (IdentityChangeEvent) Util.get(args, "event");

        CertificationScheduler scheduler = new CertificationScheduler(spCtx);

        String nameTemplate = Util.getString(parameters, RAPIDSETUP_CERT_PARAM_NAME_TEMPLATE);
        Boolean stageCertification = Util.getBoolean(parameters, RAPIDSETUP_CERT_PARAM_STAGE_CERT);
        Boolean includeBirthrightRoles = Util.getBoolean(parameters, RAPIDSETUP_CERT_PARAM_INCLUDE_BIRTHRIGHT_ROLES);
        String backupCertifierName = Util.getString(parameters, RAPIDSETUP_CERT_PARAM_BACKUP_CERTIFIER);
        Boolean prevManagerIncluded = Util.getBoolean(parameters, RAPIDSETUP_CERT_PARAM_INCLUDE_PREV_MANAGER);
        List<String> certifiers = null;

        if(Util.isNullOrEmpty(identityName)) {
            throw new GeneralException("Identity for certification must not be null");
        }

        CertificationSchedule schedule = createCertificationSchedule(spCtx, identityName, templateName, parameters);
        schedule.setRunNow(true);

        CertificationDefinition definition = schedule.getDefinition();
        addEntityFilter(spCtx, definition, identityName);

        if(Util.isNotNullOrEmpty(nameTemplate)) {
            definition.setNameTemplate(nameTemplate);
        }

        if(Util.isNotNullOrEmpty(backupCertifierName)) {
            definition.setBackupCertifierName(backupCertifierName);
        }
        definition.setStagingEnabled(stageCertification);

        if(!includeBirthrightRoles) {
            addBirthrightExclusions(spCtx, definition);
        }

        addEntitlementFilter(spCtx, definition);

        addAppTargetPermissionFilter(spCtx, definition);

        if(prevManagerIncluded) {
            certifiers = calculateCertifiers(changeEvent, definition);
            // If for some reason is not possible to include the previous manager as certifier, we are using the default logic by
            // avoiding to use the attribute "owners" in the certification definition.
            if(!Util.isEmpty(certifiers)) {
                definition.setOwnerIds(certifiers);
                // Modifying the default manager behavior results in a Manual selection type,
                // this is required to know when the certification is created.
                definition.setCertifierSelectionType(CertifierSelectionType.Manual);
            }
        }

        TaskSchedule ts = scheduler.saveSchedule(schedule, false);

        log.debug("Launching certification");

        try {
            log.debug("Waiting for up to " + waitSeconds + " secs for certification generation");
            long startTime = new Date().getTime();

            // wait until it has completed.  This should be quick because it is not partitioned,
            // and only a single identity
            boolean completed = awaitScheduledTask(wfc, ts, waitSeconds);

            long endTime = new Date().getTime();
            log.debug("Waited " + (endTime - startTime) + " millisecs for cert gen to complete");
            if (completed) {
                log.debug("Certification: Done");
            }
            else {
                log.debug("Certification: Still running");
            }
            return completed;
        }
        catch (GeneralException e) {
            throw e;
        }
        catch (Exception e) {
            throw new GeneralException(e);
        }
    }

    /**
     * To be called after the wait period has expired for a Mover cert gen. It will handle any necessary
     * logging or other activities if the cert gen has still not completed.
     * @param wfc workflow context
     * @throws GeneralException
     */
    public void warnIfCertGenNotCompleted(WorkflowContext wfc) throws GeneralException {
        SailPointContext context = wfc.getSailPointContext();
        String certGenResultName = (String)wfc.getVariable(RAPIDSETUP_CERT_RESULT_NAME);
        if (Util.isNotNullOrEmpty(certGenResultName)) {
            context.reconnect();
            TaskResult result = context.getObjectByName(TaskResult.class, certGenResultName);
            if (result == null) {
                log.debug("No TaskResult found yet for " + certGenResultName);
            }
            else if (result.isComplete()) {
                // done!
                log.debug("Certification generation for " + certGenResultName + " has completed");
            }
            else {
                Message msg = new Message(Message.Type.Warn, MessageKeys.MOVER_CERT_GEN_TIMEOUT_MSG);
                log.warn(msg.getLocalizedMessage());
                wfc.getRootWorkflowCase().addMessage(msg);
            }
        }
    }

    /**
     * Retrieves the certifiers for the Mover certification, based on global
     * on-boarding configuration, and the characteristic of the identity change
     * that triggered the Mover processing workflow.
     *
     * @param changeEvent
     *            the identity change event to identify if there was a manager
     *            change.
     * @param definition
     *            the mover certification definition.
     * @return a List with the names of the certifiers.
     */
    private List<String> calculateCertifiers(IdentityChangeEvent changeEvent, CertificationDefinition definition) {

        List<String> certifiers = null;
        Identity prevMgr = changeEvent.getOldObject().getManager();
        Identity currMgr = changeEvent.getNewObject().getManager();

        if (!Util.nullSafeEq(prevMgr, currMgr) && prevMgr != null && !prevMgr.isInactive()
                && prevMgr.getName() != null) {
            certifiers = new ArrayList<>();
            certifiers.add(prevMgr.getName());
            // If Previous Manager is a certifier, then current manager needs to
            // be part of the certifiers collection.
            if (currMgr != null && currMgr.getName() != null) {
                certifiers.add(currMgr.getName());
                Collections.reverse(certifiers);
            }
        }
        return certifiers;
    }

    private CertificationSchedule createCertificationSchedule(
            SailPointContext spCtx, String identityName, String templateName,
            Map<String, Object> parameters) throws GeneralException{
        String ownerName = Util.getString(parameters, RAPIDSETUP_CERT_PARAM_OWNER);
        Identity owner = spCtx.getObjectByName(Identity.class, ownerName);
        String newDefinitionName = Util.getString(parameters, RAPIDSETUP_CERT_PARAM_NEW_CERT_DEF_NAME);

        CertificationDefinition existingDef = spCtx.getObjectByName(CertificationDefinition.class, templateName);
        if(existingDef == null) {
            throw new GeneralException("Unable to find certification definition named '" + templateName + "'");
        }
        CertificationDefinition newCopyDef = (CertificationDefinition) existingDef.derive((Resolver)spCtx);
        CertificationSchedule schedule = new CertificationSchedule(spCtx, owner, newCopyDef);
        CertificationDefinition definition = schedule.getDefinition();
        definition.setCertificationOwner(owner);
        if(Util.isNotNullOrEmpty(newDefinitionName)) {
            definition.setName(newDefinitionName);
        } else {
            definition.setName(existingDef.getName() + " " + identityName + " [" + new Date().toString() + "]");
        }

        return schedule;
    }

    private void addEntityFilter(SailPointContext spCtx, CertificationDefinition definition,
                                          String identityName) throws GeneralException {
        // set filter values (for UI)
        List<Map<String,Object>> entityFilterValues = definition.getEntityFilterValues();
        if(entityFilterValues == null) {
            entityFilterValues = new ArrayList<Map<String, Object>>();
        }
        entityFilterValues.add(getFilterValueMap(ListFilterValue.Operation.Equals,
                "name", identityName));
        definition.setEntityFilterValues(entityFilterValues);

        // set the filter
        definition.setEntityFilter(getFilter(spCtx, entityFilterValues,
                new CertificationScheduleIdentityListFilterContext()));
    }

    private void addEntitlementFilter(
            SailPointContext spCtx, CertificationDefinition definition) throws GeneralException {

        List<String> applicationNames = RapidSetupConfigUtils.getApplicationsConfiguredIncludeEntitlements(Configuration.RAPIDSETUP_CONFIG_MOVER);
        // if no applications are in the list, do nothing ... just use what's in the cert definition
        if(applicationNames.isEmpty()) {
            return;
        }

        // set filter values (for UI)
        definition.setIncludeAdditionalEntitlements(true);
        List<Map<String,Object>> entitlementFilterValues = definition.getEntitlementFilterValues();
        if(entitlementFilterValues == null) {
            entitlementFilterValues = new ArrayList<Map<String, Object>>();
        }

        // If there is already an entitlement filter value that includes application names,
        // update the list of values to add the newly include application names. If not add
        // a new filter value.
        entitlementFilterValues = updateOrAddListFilterValue(
                entitlementFilterValues, RAPIDSETUP_CERT_PARAM_APPLICATION_PROPERTY,
                ListFilterValue.Operation.Equals, applicationNames);
        definition.setEntitlementFilterValues(entitlementFilterValues);

        // set the filter
        definition.setEntitlementFilter(getFilter(spCtx, entitlementFilterValues,
                new CertificationScheduleAdditionalEntitlementsListFilterContext()));
;
    }

    /**
     * Add Application Target Permission for given appName
     * @param spCtx - sailpoint context
     * @param definition - certification definition 
     * @throws GeneralException
     */
    private void addAppTargetPermissionFilter(
            SailPointContext spCtx, CertificationDefinition definition) throws GeneralException {

        List<String> applicationNames = RapidSetupConfigUtils.getApplicationsConfiguredTargetPermission(Configuration.RAPIDSETUP_CONFIG_MOVER);
        // if no applications are in the list, do nothing ... just use what's in the cert definition
        if(applicationNames.isEmpty()) {
            return;
        }

        definition.setIncludeTargetPermissions(true);
        List<Map<String,Object>> targetPermissionFilterValues = definition.getTargetPermissionFilterValues();
        if(targetPermissionFilterValues == null) {
            targetPermissionFilterValues = new ArrayList<Map<String, Object>>();
        }
        
        
        // If there is already an entitlement filter value that includes application names,
        // update the list of values to add the newly include application names. If not add
        // a new filter value.
        targetPermissionFilterValues = updateOrAddListFilterValue(
                targetPermissionFilterValues, RAPIDSETUP_CERT_PARAM_APPLICATION_PROPERTY,
                ListFilterValue.Operation.Equals, applicationNames);
        definition.setTargetPermissionFilterValues(targetPermissionFilterValues);

        // set the target permission application filter this is different than get filters, the property is name, not application
        // We don't want to set other type of target permission here
        definition.setTargetPermissionApplicationFilter(getTargetPermissionApplicationFilter(applicationNames));
    }

    private void addBirthrightExclusions(SailPointContext spCtx,
                                                  CertificationDefinition definition) throws GeneralException {
        String typePropertyName = "type";

        List<String> excludedRoleTypeNames =
                getRoleDefinitionNames(spCtx, rtd -> RapidSetupConfigUtils.isBirthrightRoleType(rtd));
        if(!excludedRoleTypeNames.isEmpty()) {
            // get the existing list of role filters
            List<Map<String,Object>> roleFilterValues = definition.getRoleFilterValues();
            if(roleFilterValues == null) {
                roleFilterValues = new ArrayList<Map<String, Object>>();
            }

            // If there is already a filter value that excludes role types, update
            // the list of values to add the newly excluded roles. If not add a new
            // filter value.
            roleFilterValues = updateOrAddListFilterValue(
                    roleFilterValues, typePropertyName,
                    ListFilterValue.Operation.NotEquals, excludedRoleTypeNames);
            definition.setRoleFilterValues(roleFilterValues);

            // set the filter
            definition.setRoleFilter(getFilter(spCtx, roleFilterValues,
                    new CertificationScheduleRoleListFilterContext()));
        }
    }

    private Map<String, Object> getFilterValueMap(ListFilterValue.Operation operation, String property, String value) {
        Map<String, Object> filterValueMap = new HashMap<>();
        filterValueMap.put(ListFilterValue.FILTER_MAP_OPERATION, operation.name());
        filterValueMap.put(ListFilterValue.FILTER_MAP_PROPERTY, property);
        filterValueMap.put(ListFilterValue.FILTER_MAP_VALUE, value);
        return filterValueMap;
    }

    List<Map<String,Object>> updateOrAddListFilterValue( List<Map<String,Object>> filterValues,
                                                                          String propertyName,
                                                                          ListFilterValue.Operation operation,
                                                                          List additionalValues) {
        boolean valueUpdated = false;

        // see if type is one of them, and if so add the values
        for(Map filterValueMap : filterValues) {
            String mapProperty = (String) filterValueMap.get(ListFilterValue.FILTER_MAP_PROPERTY);
            String mapOperation = (String) filterValueMap.get(ListFilterValue.FILTER_MAP_OPERATION);
            if(propertyName.equalsIgnoreCase(mapProperty) &&
                    operation.toString().equals(mapOperation)) {
                String values = (String) filterValueMap.get(ListFilterValue.FILTER_MAP_VALUE);
                List valueList = Util.csvToList(values, true);
                valueList.addAll(additionalValues);
                filterValueMap.put(ListFilterValue.FILTER_MAP_VALUE, Util.listToCsv(valueList));
                valueUpdated = true;
                break;
            }
        }

        if(!valueUpdated) {
            // if there was no filter value, add one.
            filterValues.add(getFilterValueMap(operation,
                    propertyName, Util.listToCsv(additionalValues)));
        }

        return filterValues;
    }

    private List<String> getRoleDefinitionNames(
            SailPointContext psCtx, Predicate<RoleTypeDefinition> predicate) throws GeneralException {
        ObjectConfig roleConfig = psCtx.getObjectByName(ObjectConfig.class, ObjectConfig.ROLE);
        List<String> includedRoleTypes = new ArrayList<>();
        Map<String, RoleTypeDefinition> roleTypes = roleConfig.getRoleTypesMap();
        roleTypes.values().stream().filter(predicate).forEach(rtd -> includedRoleTypes.add(rtd.getName()));
        return includedRoleTypes;
    }

    private Filter getFilter(SailPointContext spCtx, List<Map<String, Object>> filterValues,
                                      ListFilterContext listFilterContext) throws GeneralException {
        List<ListFilterValue> listFilterValues = new ArrayList<ListFilterValue>();
        for(Map <String, Object> filterValueMap : filterValues) {
            listFilterValues.add(new ListFilterValue(filterValueMap));
        }

        ListFilterService lfService = new ListFilterService(spCtx, Locale.getDefault(), listFilterContext);
        return lfService.getFilter(listFilterValues);
    }

    private Filter getTargetPermissionApplicationFilter(List<String> appNames) {
        List<Filter> filters = new ArrayList<Filter>();
        for (String app : appNames) {
            filters.add(Filter.eq("name", app));
        }
        if (Util.size(filters) == 1) {
            return filters.get(0);
        } else {
            return Util.isEmpty(filters) ? null : Filter.and(filters);
        }
    }

    /**
     * Wait up to maxWaitSeconds for the given taskSchedule to run and complete
     * @param wfc workflow context
     * @param taskSchedule the TaskSchedule that we will wait to run until comnpletion
     * @param maxWaitSeconds how many seconds should we wait for it to complete.
     * @return false if the task is still not completed after maxWaitSeconds. Otherwise, true.
     * @throws GeneralException if a database error occurs
     */
    private boolean awaitScheduledTask(WorkflowContext wfc, TaskSchedule taskSchedule, int maxWaitSeconds)
            throws GeneralException {

        SailPointContext context = wfc.getSailPointContext();

        String resultName = taskSchedule.getArgument(TaskSchedule.ARG_RESULT_NAME);
        if (resultName == null) {
            resultName = taskSchedule.getDefinition().getName();
        }
        if (resultName == null) {
            log.warn("No result name found for cert gen TaskSchedule " + taskSchedule.getName());
            return true;
        }
        if (maxWaitSeconds <= 0) {
            log.warn("Unexpected value of maxWaitSeconds of 0 seconds");
            return true;
        }

        for (int iterations = 0 ; iterations < maxWaitSeconds ; iterations++) {
            context.reconnect();
            TaskResult result = context.getObjectByName(TaskResult.class, resultName);
            if (result == null) {
                log.debug("No TaskResult found yet for " + resultName);
            }
            else if (result.isComplete()) {
                // done!
                log.debug("TaskResult " + resultName + " has completed");
                return true;
            }
            else {
                log.debug("TaskResult " + resultName + " not yet completed");
            }

            try {
                final int waitMs = 1000;
                log.debug("Waiting another "  + waitMs + "ms for TaskResult " + resultName + " to complete");
                Thread.sleep(waitMs );
            }
            catch (InterruptedException e) {
                // ignore
            }
        }

        log.debug("Timeout waiting for foreground cert gen task completion: " + resultName);

        wfc.setVariable(RAPIDSETUP_CERT_RESULT_NAME, resultName);

        return false;
    }

}
