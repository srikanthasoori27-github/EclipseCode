package sailpoint.service.certification.schedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.CertificationService;
import sailpoint.api.CertificationTriggerHandler;
import sailpoint.api.Notary;
import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.api.TaskManager;
import sailpoint.api.certification.CertificationNamer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Phase;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.ESignatureType;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.NotificationConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.object.Scope;
import sailpoint.object.Tag;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.service.listfilter.ListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.suggest.SuggestHelper;
import sailpoint.task.ApplyCertificationDefinitionChangesTask;
import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * UI Service for the new Certification Schedule interface
 * @author brian.li
 *
 */
public class CertificationScheduleService {

    /**
     * Flag to indicate SignOff Approval Rule is selected.
     */
    public static final String USE_APPROVAL_RULE = "useApprovalRule";

    /**
     * Simple inner class to hold some related things about filter values so we can use static configuration
     */
    private static class FilterValueConfig {
        /**
         * Key of the filter values in the attributes map
         */
        private String filterValueKey;

        /**
         * Key of the filter in the attributes map
         */
        private String filterKey;

        /**
         * ListFilterContext to use for manipulation of filters and values.
         */
        private ListFilterContext listFilterContext;

        private FilterValueConfig(String filterValuesKey, String filterKey, ListFilterContext listFilterContext) {
            this.filterValueKey = filterValuesKey;
            this.filterKey = filterKey;
            this.listFilterContext = listFilterContext;
        }
    }

    /**
     * Inner map of attributes that will come in as suggest objects but should be stored as simple names.
     * The Class here is what is used to regenerate the suggest object when the definition is loaded.
     */
    private static final Map<String, Class> SUGGEST_ATTRIBUTES_MAP = new HashMap<String, Class>() {{
        put(CertificationDefinition.ARG_ENTITY_RULE, Rule.class);
        put(CertificationDefinition.ARG_ENTITY_POPULATION, GroupDefinition.class);
        put(CertificationDefinition.ARG_CERTIFIER, Identity.class);
        put(CertificationDefinition.ARG_BACKUP_CERTIFIER, Identity.class);
        put(CertificationDefinition.ARG_CERTIFIER_RULE, Rule.class);
        put(CertificationDefinition.ARG_PRE_DELEGATION_RULE_NAME, Rule.class);
        put(CertificationDefinition.ARG_CERT_OWNER, Identity.class);
        put(CertificationDefinition.ARG_SIGN_OFF_APPROVER_RULE_NAME, Rule.class);
        put(Configuration.CERT_SIGN_OFF_APPROVAL_EMAIL_TEMPLATE, EmailTemplate.class);
        put(CertificationDefinition.ARG_ACTIVE_PHASE_ENTER_RULE_NAME, Rule.class);
        put(CertificationDefinition.ARG_REMEDIATION_PHASE_ENTER_RULE_NAME, Rule.class);
        put(CertificationDefinition.ARG_FINISH_PHASE_ENTER_RULE_NAME, Rule.class);
        put(CertificationDefinition.ARG_CHALLENGE_PHASE_ENTER_RULE_NAME, Rule.class);
        put(CertificationDefinition.ARG_AUTOMATIC_CLOSING_RULE_NAME, Rule.class);
        put(CertificationDefinition.ARG_AUTOMATIC_CLOSING_SIGNER, Identity.class);
        put(Configuration.SELF_CERTIFICATION_VIOLATION_OWNER, Identity.class);
        put(Configuration.CERTIFICATION_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE, EmailTemplate.class);
        put(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE, EmailTemplate.class);
    }};

    /**
     * Array of statics that need conversions from Integer to long
     * These attributes get converted from their JSON number format into an Integer,
     * but the back end is expecting Longs.
     */
    private static final String[] LONG_ATTRIBUTES = {
        Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT,
        CertificationDefinition.ARG_ACTIVE_PERIOD_DURATION_AMOUNT,
        CertificationDefinition.ARG_REMEDIATION_PERIOD_DURATION_AMOUNT,
        CertificationDefinition.ARG_CHALLENGE_PERIOD_DURATION_AMOUNT,
        CertificationDefinition.ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT
    };

    /**
     * Inner list of filter values configurations to handle. These will be generically handled to
     * collapse suggest objects to name string, regenerate suggests on definition load, and set the filter
     * based on the values.
     */
    private static final List<FilterValueConfig> FILTER_VALUES_CONFIG = new ArrayList<FilterValueConfig>() {{
        add(new FilterValueConfig(CertificationDefinition.ARG_ENTITY_FILTER_VALUES,
                CertificationDefinition.ARG_ENTITY_FILTER,
                new CertificationScheduleIdentityListFilterContext()));
        add(new FilterValueConfig(CertificationDefinition.ARG_ROLE_FILTER_VALUES,
                CertificationDefinition.ARG_ROLE_FILTER,
                new CertificationScheduleRoleListFilterContext()));
        add(new FilterValueConfig(CertificationDefinition.ARG_ENTITLEMENT_FILTER_VALUES,
                CertificationDefinition.ARG_ENTITLEMENT_FILTER,
                new CertificationScheduleAdditionalEntitlementsListFilterContext()));
        add(new FilterValueConfig(CertificationDefinition.ARG_ACCOUNT_FILTER_VALUES,
                CertificationDefinition.ARG_ACCOUNT_FILTER,
                new CertificationScheduleAccountListFilterContext()));
        add(new FilterValueConfig(CertificationDefinition.ARG_TARGET_PERMISSION_FILTER_VALUES,
                CertificationDefinition.ARG_TARGET_PERMISSION_FILTER,
                new CertificationScheduleTargetPermissionListFilterContext()));
    }};

    /**
     * List of notification attributes that we need to handle.
     */
    static final String[] NOTIFICATION_ATTRIBUTES = {
        CertificationDefinition.CERTIFICATION_NOTIF_PREFIX + CertificationDefinition.REMINDERS_AND_ESCALATIONS_KEY,
        CertificationDefinition.REMEDIATION_NOTIF_PREFIX + CertificationDefinition.REMINDERS_AND_ESCALATIONS_KEY
    };

    /**
     * Map keying an enabled/disabled flag to the list of attributes that should be removed on save, and defaults
     * included on load
     */
    static final Map<String, List<String>> DEPENDENT_ATTRIBUTES = new HashMap<String, List<String>>() {{
        put(CertificationDefinition.ARG_REMEDIATION_PERIOD_ENABLED, Arrays.asList(
                CertificationDefinition.ARG_REMEDIATION_PHASE_ENTER_RULE_NAME,
                CertificationDefinition.ARG_REMEDIATION_PERIOD_DURATION_AMOUNT,
                CertificationDefinition.ARG_REMEDIATION_PERIOD_DURATION_SCALE));
        put(CertificationDefinition.ARG_CHALLENGE_PERIOD_ENABLED, Arrays.asList(
                CertificationDefinition.ARG_CHALLENGE_PHASE_ENTER_RULE_NAME,
                CertificationDefinition.ARG_CHALLENGE_PERIOD_DURATION_AMOUNT,
                CertificationDefinition.ARG_CHALLENGE_PERIOD_DURATION_SCALE,
                Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE,
                Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE,
                Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE,
                Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE,
                Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE,
                Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE,
                Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE,
                Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE));
        put(CertificationDefinition.ARG_AUTOMATIC_CLOSING_ENABLED, Arrays.asList(
                CertificationDefinition.ARG_AUTOMATIC_CLOSING_RULE_NAME,
                CertificationDefinition.ARG_AUTOMATIC_CLOSING_SIGNER,
                CertificationDefinition.ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT,
                CertificationDefinition.ARG_AUTOMATIC_CLOSING_DURATION_SCALE,
                Configuration.AUTOMATIC_CLOSING_ACTION,
                Configuration.AUTOMATIC_CLOSING_COMMENTS));
    }};

    /**
     * Format string for the template parameters used by CertificationNamer
     */
    private static final String CERTIFICATION_NAMER_TEMPLATE_FORMAT = "${%s}";
    protected static final String METADATA_CERTIFICATION_NAME_PARAMETERS = "certificationNameParameters";
    protected static final String METADATA_ACCESS_REVIEW_NAME_PARAMETERS = "accessReviewNameParameters";
    protected static final String METADATA_ESIGNATURES = "electronicSignatures";
    protected static final String METADATA_DURATION_SCALES = "durationScales";
    protected static final String METADATA_FREQUENCY = "frequency";
    protected static final String METADATA_AUTO_CLOSING_OPTIONS = "automaticClosingOptions";
    protected static final String METADATA_DEFAULT_REMINDER = "defaultReminderConfig";
    protected static final String METADATA_DEFAULT_ESCALATION = "defaultEscalationConfig";
    protected static final String METADATA_SELF_CERT_LEVELS = "selfCertificationLevels";
    protected static final String METADATA_EDITABLE_ATTRIBUTES = "editableAttributes";
    protected static final String METADATA_READ_ONLY = "readOnly";
    protected static final String METADATA_INCLUDE_SCOPE = "includeScope";
    protected static final String METADATA_RECOMMENDER_CONFIGURED = "recommenderConfigured";

    static final String ATTR_TAGS = "tags";

    private UserContext userContext;

    public CertificationScheduleService(UserContext userContext) {
        this.userContext = userContext;
    }

    private SailPointContext getContext() {
        return this.userContext.getContext();
    }

    private Locale getLocale() {
        return this.userContext.getLocale();
    }

    /**
     * Gets the activation date of the earliest certification in the group. This should be
     * one that was set up for the initial setting
     * @param certificationGroupId The certification group id.
     * @return The activation date, or null if none exists.
     * @throws GeneralException
     */
    public static Date getActivationDate(String certificationGroupId, SailPointContext context)
            throws GeneralException
    {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certificationGroups.id", certificationGroupId));
        queryOptions.setResultLimit(1);
        queryOptions.addOrdering("activated", true);

        Iterator<Object[]> rows = context.search(Certification.class, queryOptions, Arrays.asList("activated"));

        if (rows.hasNext()) {
            Object[] row = rows.next();
            return (Date)row[0];
        }

        return null;
    }

    /**
     * Grabs a certification schedule via taskSchedule ID.
     * When a CertificationSchedule is made, a TaskSchedule is made and then a
     * CertificationScheduler is used to grab the schedule itself.
     * Then transform the object to the DTO.
     *
     * @param taskScheduleId ID of the TaskSchedule
     * @return The CertificationScheduleDTO object to be returned
     * @throws GeneralException
     */
    public CertificationScheduleDTO getCertificationScheduleDTO(String taskScheduleId) throws GeneralException {
        TaskSchedule taskSchedule = getContext().getObjectById(TaskSchedule.class, taskScheduleId);
        if (taskSchedule == null) {
            throw new ObjectNotFoundException(new Message(MessageKeys.ERR_CERTIFICATION_SCHEDULE_TASK_SCHEDULE_NOT_FOUND, taskScheduleId));
        }

        CertificationScheduler scheduler = new CertificationScheduler(getContext());
        CertificationSchedule schedule = scheduler.getCertificationSchedule(taskSchedule);

        restoreDependentDefaults(schedule.getDefinition());
        return createScheduleDTO(schedule);
    }

    /**
     * Create and return a default CertificationScheduleDTO for when we want to create a new Schedule
     * filled with default values.
     * @param identity Identity to be used for the Cert Owner
     * @return The CertificationScheduleDTO to be returned
     * @throws GeneralException
     */
    public CertificationScheduleDTO getDefaultCertificationScheduleDTO(Identity identity, String certificationGroupId) throws GeneralException {
        CertificationSchedule schedule = new CertificationSchedule(getContext(), identity);
        schedule.getDefinition().setType(Certification.Type.Focused);

        // Copy the definition from the given certification group if provided, clearing out identifiers
        if (!Util.isNullOrEmpty(certificationGroupId)) {
            CertificationDefinition newDefinition = loadCertificationDefinition(certificationGroupId).createCopy();
            newDefinition.setId(null);
            newDefinition.setCreated(new Date());
            newDefinition.setName(null);
            newDefinition.setOwner(identity);

            restoreDependentDefaults(newDefinition);
            schedule.setDefinition(newDefinition);
        }

        return createScheduleDTO(schedule);
    }

    /**
     * Grabs a certification schedule via certification group id.
     *
     * @param certificationGroupId ID of the certification group to load the schedule from
     * @return The CertificationScheduleDTO object to be returned
     * @throws GeneralException
     */
    public CertificationScheduleDTO getCertificationScheduleDTOFromCertificationGroup(String certificationGroupId) throws GeneralException {
        CertificationGroup certificationGroup = loadCertificationGroup(certificationGroupId);
        CertificationSchedule schedule = new CertificationSchedule(getContext(), null, loadCertificationDefinition(certificationGroupId));
        restoreDependentDefaults(schedule.getDefinition());
        CertificationScheduleDTO dto = createScheduleDTO(schedule);
        dto.setCertificationGroupId(certificationGroupId);
        dto.setCertificationGroupName(certificationGroup.getName());

        // Certs have two dates, create date and active date.
        // Create happens when the user schedules the cert, and it's saved in the database.
        // Active is when the cert actually enters its active phase.
        // The cert's first execution is usually the created date UNLESS the cert is staged.
        // If the cert is staged, it's not active until the user explicitly activates the cert, which can
        // be any length of time after it's created.
        Date activeDate = getActivationDate(certificationGroupId, getContext());
        Date createDate = certificationGroup.getCreated();
        if (createDate != null && activeDate != null && createDate.before(activeDate)) {
            dto.setFirstExecution(activeDate);
        } else {
            dto.setFirstExecution(createDate);
        }
        dto.setActivated(activeDate);

        dto.setFrequency((String)certificationGroup.getAttribute(CertificationGroup.SCHEDULE_FREQUENCY));
        return dto;
    }

    /**
     * Helper method to load the certification definition from a group
     * @param certificationGroupId ID of the certification group
     * @return CertificationDefinition
     * @throws GeneralException
     */
    private CertificationDefinition loadCertificationDefinition(String certificationGroupId) throws GeneralException {
        CertificationDefinition definition = loadCertificationGroup(certificationGroupId).getDefinition();
        if (definition == null) {
            // WHAT HAPPENED?
            throw new GeneralException("CertificationDefinition not found on certification group!");
        }

        return definition;
    }

    /**
     * Helper method to load the certification group
     * @param certificationGroupId ID of the certification group
     * @return CertificationGroup
     * @throws GeneralException
     */
    private CertificationGroup loadCertificationGroup(String certificationGroupId) throws GeneralException {
        CertificationGroup certificationGroup = getContext().getObjectById(CertificationGroup.class, certificationGroupId);
        if (certificationGroup == null) {
            throw new ObjectNotFoundException(CertificationGroup.class, certificationGroupId);
        }

        return certificationGroup;
    }

    /**
     * Helper method to create a DTO from the CertificationSchedule, including attribute manipulation
     * @param schedule The CertificationSchedule
     * @return DTO based on the CertificationSchedule
     * @throws GeneralException
     */
    private CertificationScheduleDTO createScheduleDTO(CertificationSchedule schedule) throws GeneralException {
        CertificationScheduleDTO dto = new CertificationScheduleDTO(schedule);
        enhanceAttributes(dto.getDefinition(), schedule.getDefinition());
        checkDefaults(dto.getDefinition(), schedule.getDefinition());

        if (schedule.getDefinition().getAssignedScope() != null) {
            dto.getDefinition().setAssignedScope(SuggestHelper.getSuggestObject(Scope.class, schedule.getDefinition().getAssignedScope().getId(), getContext()));
        }

        return dto;
    }

    /**
     * Saves a CertificationScheduleDTO either by creating a new schedule or editing an existing schedule
     * @param dto CertificationScheduleDTO to save
     * @return Map of error messages if any, keyed by attribute ID
     * @throws GeneralException
     */
    public Map<String, List<String>> saveCertificationSchedule(CertificationScheduleDTO dto) throws GeneralException {
        if (isReadOnly()) {
            throw new UnauthorizedAccessException();
        }

        CertificationSchedule schedule = new CertificationSchedule();
        schedule.setDefinition(getCertificationDefinitionForSave(schedule, dto));

        mergeToSchedule(dto, schedule);
        String certificationGroupId = dto.getCertificationGroupId();
        boolean hasCertGroupId = Util.isNotNullOrEmpty(certificationGroupId);

        Map<String, List<String>> errors = validate(schedule, hasCertGroupId);
        if (!Util.isEmpty(errors)) {
            return errors;
        }

        if (hasCertGroupId) {
            // We are editing a definition for a generated certification group. In this case we will not go through the scheduler,
            // but will save the modified objects directly and kick off the task to apply the changes to the certifications in the group.
            getContext().saveObject(schedule.getDefinition());
            if (!Util.nullSafeEq(schedule.getDefinition().getId(), dto.getDefinition().getId())) {
                // If the ID of the definition we are saving does not match that of the definition on the group, we have had to clone it,
                // so set the definition directly and save the group object.
                CertificationGroup certificationGroup = loadCertificationGroup(certificationGroupId);
                certificationGroup.setDefinition(schedule.getDefinition());
                getContext().saveObject(certificationGroup);
            }

            getContext().commitTransaction();

            // This task applies the changes we made in the definition to the generated certifications.
            Attributes<String, Object> args = new Attributes<String, Object>();
            args.put(ApplyCertificationDefinitionChangesTask.ARG_CERTIFICATION_GROUP_ID, certificationGroupId);

            TaskManager taskManager = new TaskManager(getContext());
            taskManager.run(ApplyCertificationDefinitionChangesTask.TASK_NAME, args);
        } else {
            // We are saving a new schedule or an existing schedule, in either case go through the scheduler
            CertificationScheduler scheduler = new CertificationScheduler(getContext());
            scheduler.saveSchedule(schedule, false);
        }

        return null;
    }

    /**
     * Gets the certification definition to use for saving. If this definition is already saved, fetch it, and if it is shared
     * with a schedule or another certification group, create a new cloned copy to avoid collisions.
     * @param schedule CertificationSchedule
     * @param dto CertificationScheduleDTO
     * @return CertificationDefinition
     * @throws GeneralException
     */
    private CertificationDefinition getCertificationDefinitionForSave(CertificationSchedule schedule, CertificationScheduleDTO dto)
        throws GeneralException {
        CertificationDefinition definition = schedule.getDefinition();
        // We are making changes to an existing definition, so check if it is shared.
        if (dto.getDefinition().getId() != null) {
            definition = getContext().getObjectById(CertificationDefinition.class, dto.getDefinition().getId());
            if (definition == null) {
                throw new ObjectNotFoundException(CertificationDefinition.class, dto.getDefinition().getId());
            }

            // If we are saving changes to a certification group, we need to see if this is shared with more than one certification
            // group or some schedules. Otherwise, we only need to clone if a group has been generated from this same schedule.
            boolean isShared = (dto.getCertificationGroupId() == null) ? isCertificationGroupDefinition(definition) :
                    isSharedCertificationDefinition(definition);

            // We are shared, so in order to maintain pre-existing options in the other definition, clone it and decache.
            if (isShared) {
                CertificationDefinition oldDefinition = definition;
                definition = cloneCertificationDefinition(definition);
                getContext().decache(oldDefinition);
            }

            List<String> editableAttributes = (dto.getCertificationGroupId() == null) ? null : getEditableAttributes(dto.getCertificationGroupId());
            removeMissingAttributes(definition, dto.getDefinition(), editableAttributes);
        }

        // This should be set unless this is a brand new schedule, but just in case set it whenever it is null. We should always have an owner.
        if (definition.getOwner() == null) {
            definition.setOwner(this.userContext.getLoggedInUser());
        }

        return definition;
    }

    /**
     * Helper method to merge the dto into a CertificationSchedule Object
     * @param dto DTO that contains the information
     * @param schedule CertificationSchedule to copy the information into
     */
    private void mergeToSchedule(CertificationScheduleDTO dto,
            CertificationSchedule schedule) throws GeneralException {

        CertificationDefinition def = schedule.getDefinition();
        CertificationDefinitionDTO defDto = dto.getDefinition();

        // If a name is already set in the definition, keep it. This can happen for existing or cloned definition.
        if (Util.isNullOrEmpty(def.getName())) {
            def.setName(defDto.getName());
        }

        setScheduleVariables(dto, schedule);

        if (defDto.getAssignedScope() != null) {
            String assignedScopeNameOrId = SuggestHelper.extractNameOrIdFromSuggestObject(defDto.getAssignedScope());
            //Scope does not have unique name, must be by ID
            Scope assignedScope = getContext().getObjectById(Scope.class, assignedScopeNameOrId);
            schedule.getDefinition().setAssignedScope(assignedScope);
        }

        scrubAttributes(defDto);
        schedule.getDefinition().mergeAttributes(defDto.getAttributes());

        // Then call the setters on the CertificationDefinition object with the attribute values,
        // converting the attributes on the real definition back to the appropriate storage
        defDto.getAttributes().setOnObject(schedule.getDefinition(), CertificationDefinition.class);

        // Convert the tags here since they are not attributes
        schedule.getDefinition().setTags(getTagList(dto.getDefinition()));
    }

    /**
     * Remove attributes from the definition that are not found on the DTO. This is to handle the case of a cleared value
     * when saving an existing definition.
     *
     * This also will remove any non-editable attributes from the DTO
     */
    private void removeMissingAttributes(CertificationDefinition definition, CertificationDefinitionDTO definitionDTO, List<String> editableAttributes) {
        for (String key : definition.getAttributes().getKeys()) {
            boolean isEditable = editableAttributes == null || editableAttributes.contains(key);
            if (isEditable && !definitionDTO.getAttributes().containsKey(key)) {
                definition.getAttributes().remove(key);
            } else if (!isEditable) {
                definitionDTO.getAttributes().remove(key);
            }
        }
    }

    /**
     * Helper to set the first level variables on the schedule object
     * @param dto CertificationScheduleDTO from the front end
     * @param schedule CertificationSchedule to merge to
     */
    private void setScheduleVariables(CertificationScheduleDTO dto,
            CertificationSchedule schedule) {

        schedule.setFirstExecution(dto.getFirstExecution());
        schedule.setFrequency(dto.getFrequency());
        schedule.setRunNow(dto.isRunNow());
        schedule.setTaskId(dto.getTaskId());
        schedule.setName(dto.getName());
    }

    /**
     * Given the list of tags in the DTO, convert to a list of real Tag objects,
     * creating new ones as needed.
     * @param definitionDTO The CertificationDefinition DTO
     * @return List of Tag objects, or null if empty
     * @throws GeneralException
     */
    private List<Tag> getTagList(CertificationDefinitionDTO definitionDTO) throws GeneralException {
        if (Util.isEmpty(definitionDTO.getTags())) {
            return null;
        }

        List<Tag> tags = new ArrayList<>();
        for (String dtoTag: definitionDTO.getTags()) {
            String sanitizedTag = WebUtil.stripHTML(dtoTag);
            Tag tag = getContext().getObjectByName(Tag.class, sanitizedTag);
            if (tag == null) {
                tag = new Tag(sanitizedTag);
                getContext().saveObject(tag);
            }

            tags.add(tag);
        }

        return tags;
    }

    /**
     * Helper method to clean the data from the DTO attributes before assigning back to the real definition
     * @param definition The CertificationDefinitionDTO
     * @throws GeneralException
     */
    private void scrubAttributes(CertificationDefinitionDTO definition) throws GeneralException {
        Attributes<String, Object> attributes = definition.getAttributes();

        // Convert top level suggest objects to strings for storage
        readSuggestObjects(attributes);

        // Convert filter values to filters and store both
        readFilterValues(attributes);

        // Convert integer values from JSON to long values
        readIntegerValues(attributes);

        // Convert notification DTOs to actual NotificationConfig objects
        readNotificationConfigs(attributes);
    }

    /**
     * Helper method to enhance the data before sending the DTO to the client. Does things like build suggest
     * objects, convert types, etc.
     * @param definition The CertificationDefinitionDTO
     * @throws GeneralException
     */
    private void enhanceAttributes(CertificationDefinitionDTO dto, CertificationDefinition definition) throws GeneralException {
        // This will call the getters on the CertificationDefinition object to convert the attribute values from strings (or whatever is stored)
        // to a more useful real type, such as boolean or long or whatnot.
        dto.setAttributes(dto.getAttributes().getFromObject(definition, CertificationDefinition.class, getContext()));

        // Creates the sugggest objects for necessary values
        createSuggestObjects(dto.getAttributes());

        // Expands filter values to objects expected by the UI layer
        expandFilterValues(dto.getAttributes());

        // Convert notification attributes to DTO objects.
        for (String attr : NOTIFICATION_ATTRIBUTES) {
            if (dto.getAttributes().containsKey(attr)) {
                Object config = dto.getAttributes().get(attr);
                if (config instanceof NotificationConfig) {
                    dto.setAttribute(attr,
                            new NotificationConfigDTO((NotificationConfig) config, getContext()));
                }

            }
        }
    }

    /**
     * When loading a DTO to send to the client, we need to populate the defaults for values that are removed
     * based on disabling an attribute. This will create an initialized default definition and pull the values into
     * the DTO as needed.
     */
    private void restoreDependentDefaults(CertificationDefinition definition) throws GeneralException {
        CertificationDefinition defaultDefinition = new CertificationDefinition();
        defaultDefinition.initialize(getContext());

        for (Map.Entry<String, List<String>> dependantEntry : DEPENDENT_ATTRIBUTES.entrySet()) {
            if (!Util.otob(definition.getAttribute(dependantEntry.getKey()))) {
                for (String dependantAtttribute : dependantEntry.getValue()) {
                    definition.getAttributes().put(dependantAtttribute, defaultDefinition.getAttributes().get(dependantAtttribute));
                }
            }
        }
    }

    /**
     * Helper method to check any defaults that are not set from the CertificationDefinition object
     * @param dto The CertificationDefinitionDTO
     * @param definition The CertificationDefinition
     */
    private void checkDefaults(CertificationDefinitionDTO dto, CertificationDefinition definition) {
        if (dto.getName() == null) {
            dto.setName(new Message(MessageKeys.TASK_SCHED_CERT, definition.getType().getMessageKey(), new Date())
                    .getLocalizedMessage(getLocale(), this.userContext.getUserTimeZone()));
        }

        if (dto.getAttributes().get(CertificationDefinition.ARG_CERT_NAME_TEMPLATE) == null) {
            Message defaultCertNameTemplate = new Message(MessageKeys.TASK_SCHED_CERT,
                    definition.getType().getMessageKey(),
                    String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_FULL_DATE));
            dto.getAttributes().put(CertificationDefinition.ARG_CERT_NAME_TEMPLATE, defaultCertNameTemplate.getLocalizedMessage(getLocale(), null));
        }
        
        if (Util.isNotNullOrEmpty(definition.getApproverRuleName())) {
            dto.setAttribute(USE_APPROVAL_RULE, true);
        } else {
            dto.setAttribute(USE_APPROVAL_RULE, false);
        }

        if (!dto.getAttributes().containsKey(CertificationDefinition.ARG_ENABLE_PARTITIONING)) {
            dto.setAttribute(CertificationDefinition.ARG_ENABLE_PARTITIONING, true);
        }

        // UPGRADE: Target Permissions flag was added in 8.1, if we are opening up a schedule from before then we should make it match the additional entitlements flag
        if (!dto.getAttributes().containsKey(CertificationDefinition.ARG_INCLUDE_TARGET_PERMISSIONS)) {
            dto.setAttribute(CertificationDefinition.ARG_INCLUDE_TARGET_PERMISSIONS, dto.getAttributes().get(CertificationDefinition.ARG_INCLUDE_ADDITIONAL_ENTITLEMENTS));
        }
    }

    /**
     * Validates the input data for CertificationSchedule.
     * 
     * @param schedule The CertificationSchedule to validate against.
     * @param hasCertGroupId True if the schedule has a certification group id
     * @return Map of error messages if any, keyed by attribute ID
     * @throws GeneralException 
     */
    private Map<String, List<String>> validate(CertificationSchedule schedule, boolean hasCertGroupId) throws GeneralException {
        Map<String, List<String>> errors = new HashMap<String, List<String>>();

        CertificationDefinition definition = schedule.getDefinition();

        if (definition.getType() == null) {
            addError(errors, CertificationDefinition.ARG_CERTIFICATION_TYPE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_TYPE);
        }

        String entityType = definition.getEntitySelectionType();
        if (Util.isNullOrEmpty(entityType)) {
            addError(errors, CertificationDefinition.ARG_ENTITY_SELECTION_TYPE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ENTITY_ERROR_ENTITY_TYPE);
        } else if (CertificationDefinition.ENTITY_SELECTION_TYPE_POPULATION.equals(entityType)) {
            definition.setEntityFilter(null);
            definition.setEntityFilterValues(null);
            definition.setEntityRule(null);
            if (Util.isNullOrEmpty(definition.getEntityPopulation())) {
                addError(errors, CertificationDefinition.ARG_ENTITY_POPULATION, MessageKeys.UI_CERTIFICATION_SCHEDULE_ENTITY_ERROR_POPULATION);
            }
        } else if (CertificationDefinition.ENTITY_SELECTION_TYPE_RULE.equals(entityType)) {
            definition.setEntityFilter(null);
            definition.setEntityFilterValues(null);
            definition.setEntityPopulation(null);
            if (Util.isNullOrEmpty(definition.getEntityRule())) {
                addError(errors, CertificationDefinition.ARG_ENTITY_RULE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ENTITY_ERROR_RULE);
            }
        } else if (CertificationDefinition.ENTITY_SELECTION_TYPE_FILTER.equals(entityType)) {
            definition.setEntityRule(null);
            definition.setEntityPopulation(null);
        }  else {
            addError(errors, CertificationDefinition.ARG_ENTITY_SELECTION_TYPE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ENTITY_ERROR_UNKNOWN_ENTITY_TYPE);
        }

        //validate certifier section
        CertifierSelectionType certifierType = definition.getCertifierSelectionType();
        if (certifierType == null) {
            addError(errors, CertificationDefinition.ARG_CERTIFIER_TYPE, MessageKeys.UI_CERTIFICATION_SCHEDULE_CERTIFIER_TYPE_ERROR);
       } else if (CertifierSelectionType.Manual.equals(certifierType)) {
            definition.setCertifierRule(null);
            if (Util.isNullOrEmpty(definition.getCertifierName())) {
                addError(errors, CertificationDefinition.ARG_CERTIFIER, MessageKeys.UI_CERTIFICATION_SCHEDULE_CERTIFIER_ERROR_SINGLE_CERTIFIER);
            }
        } else if (CertifierSelectionType.Rule.equals(certifierType)) {
            definition.setCertifierName(null);
            if (Util.isNullOrEmpty(definition.getCertifierRule())) {
                addError(errors, CertificationDefinition.ARG_CERTIFIER_RULE, MessageKeys.UI_CERTIFICATION_SCHEDULE_CERTIFIER_ERROR_RULE);
            }
        }
        //backup certifier is not required for single certifier, but required for all others. 
        if (!CertifierSelectionType.Manual.equals(certifierType) && Util.isNullOrEmpty(definition.getBackupCertifierName())) {
            addError(errors, CertificationDefinition.ARG_BACKUP_CERTIFIER, MessageKeys.UI_CERTIFICATION_SCHEDULE_CERTIFIER_ERROR_BACKUP_CERTIFIER);
        }
        if (Util.isNullOrEmpty(definition.getPreDelegationRuleName())) {
            definition.setSendPreDelegationCompletionEmails(false);
        }
        if (Util.otob(definition.isElectronicSignatureRequired()) && definition.getElectronicSignatureName() == null) {
            addError(errors, Certification.ATT_SIGNATURE_TYPE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_ELECTRONIC_SIGNATURE);
        }
        if (definition.isLimitReassignments()) {
            Integer limit = definition.getReassignmentLimit();
            if (limit == null || limit < 1) {
                addError(errors, Configuration.CERTIFICATION_REASSIGNMENT_LIMIT, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_REASSIGNMENT_LIMIT);
            }
        }
        if (Util.otob(definition.getAttribute(USE_APPROVAL_RULE)) && Util.isNullOrEmpty(definition.getApproverRuleName())) {
            addError(errors, CertificationDefinition.ARG_SIGN_OFF_APPROVER_RULE_NAME, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_APPROVAL_RULE);
        }

        // Schedule Certifications validation
        Date firstExecution = schedule.getFirstExecution();
        if (!schedule.isRunNow() && firstExecution == null) {
            addError(errors, CertificationSchedule.ARG_FIRST_EXECUTION, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_START_DATE_MISSING);
        }
        // We don't want to validate against a date in the past if we are editing a generated cert,
        // or if Run Now is selected.
        else if (firstExecution != null && !hasCertGroupId && !schedule.isRunNow()) {
            Date now = new Date();
            if (now.after(firstExecution)) {
                addError(errors, CertificationSchedule.ARG_FIRST_EXECUTION, MessageKeys.ERR_DATE_PAST);
            }
        }
        checkDurationAmount(CertificationDefinition.ARG_ACTIVE_PERIOD_DURATION_AMOUNT, 
                definition.getActivePeriodDurationAmount(),
                MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_ACTIVE_PERIOD_DURATION, errors);

        if (definition.isRemediationPeriodEnabled()) {
            checkDurationAmount(CertificationDefinition.ARG_REMEDIATION_PERIOD_DURATION_AMOUNT,
                    definition.getRemediationPeriodDurationAmount(),
                    MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_REVOCATION_PERIOD_DURATION, errors);
        }
        if (definition.isChallengePeriodEnabled()) {
            checkDurationAmount(CertificationDefinition.ARG_CHALLENGE_PERIOD_DURATION_AMOUNT,
                    definition.getChallengePeriodDurationAmount(),
                    MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_CHALLENGE_PERIOD_DURATION, errors);
        }
        if (definition.isAutomaticClosingEnabled()) {
            checkDurationAmount(CertificationDefinition.ARG_AUTOMATIC_CLOSING_DURATION_AMOUNT,
                    definition.getAutomaticClosingInterval(),
                    MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_AUTOMATIC_CLOSING_EXPIRATION_DURATION, errors);
        }

        // Additional Options validation
        if (Util.isNullOrEmpty(definition.getCertificationOwner())) {
            addError(errors, CertificationDefinition.ARG_CERT_OWNER, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_OWNER);
        }

        if (Util.isNullOrEmpty(definition.getCertificationNameTemplate())) {
            addError(errors, CertificationDefinition.ARG_CERT_NAME_TEMPLATE, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_NAME);
        }

        checkDurationAmount(Configuration.CERTIFICATION_MITIGATION_DURATION_AMOUNT, 
                definition.getAllowExceptionDurationAmount(getContext()),
                MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_DEFAULT_DURATION_EXCEPTION, errors, 0);

        if (definition.isEnablePartitioning()) {
            Integer limit = definition.getPartitionCount();
            if (limit != null && limit < 0) {
                addError(errors, CertificationDefinition.ARG_PARTITION_COUNT, MessageKeys.UI_CERTIFICATION_SCHEDULE_ERROR_PARTITION_COUNT);
            }
        }

        // Only override the auto approve flag if the cert hasn't been generated
        if (!definition.getShowRecommendations() && !hasCertGroupId) {
            definition.setAutoApprove(false);
        }

        return errors;
    }

    /**
     * Add the error message to the error map with attribute id as the key.
     * 
     * @param errorMap Map of error messages keyed by attribute ID
     * @param attributeId the key in the error map
     * @param messageKey message key for the error
     */
    private void addError(Map<String, List<String>> errorMap,
                          String attributeId,
                          String messageKey) {
        List<String> errors = errorMap.get(attributeId);
        if (errors == null) {
            errors = new ArrayList<String>();
            errorMap.put(attributeId, errors);
        }
        errors.add(Message.localize(messageKey).getLocalizedMessage(getLocale(), null));
    }

    /**
     * Reads the attributes that come from the client as suggest objects and pull the name out of them,
     * updating the attributes map for saving
     * @param attributes Attributes map
     */
    private void readSuggestObjects(Attributes<String, Object> attributes) {
        // TODO: Support multi suggests
        for (Map.Entry<String, Class> suggestEntry : SUGGEST_ATTRIBUTES_MAP.entrySet()) {
            String attributeName = suggestEntry.getKey();
            if (attributes.containsKey(attributeName)) {
                Object value = attributes.get(attributeName);
                String newValue = SuggestHelper.extractNameOrIdFromSuggestObject(value);
                attributes.put(attributeName, newValue);
            }
        }
    }

    /**
     * Creates suggest objects in attributes map for any values that require those on the client. Uses
     * Uses SUGGEST_ATTRIBUTES_MAP to get the list and classes.
     * @param attributes Attributes map to update
     * @throws GeneralException
     */
    private void createSuggestObjects(Attributes<String, Object> attributes) throws GeneralException {
        // TODO: Support multi suggests
        for (Map.Entry<String, Class> suggestEntry : SUGGEST_ATTRIBUTES_MAP.entrySet()) {
            String attributeName = suggestEntry.getKey();
            if (attributes.containsKey(attributeName)) {
                String nameOrId = attributes.getString(attributeName);
                attributes.put(attributeName, SuggestHelper.getSuggestObject(suggestEntry.getValue(), nameOrId, getContext()));
            }

        }
    }

    /**
     * Reads the filter values from the client, cleans them up and creates the associated filter in the attributes map.
     * Uses FILTER_VALUES_CONFIG.
     * @param attributes Attributes map to update
     * @throws GeneralException
     */
    private void readFilterValues(Attributes<String, Object> attributes) throws GeneralException {
        for (FilterValueConfig filterValueConfig : FILTER_VALUES_CONFIG) {
            // Need some special handling to get app filter from target permission list filter context
            CertificationScheduleTargetPermissionListFilterContext tpListFilterContext = null;
            if (filterValueConfig.listFilterContext instanceof CertificationScheduleTargetPermissionListFilterContext) {
                tpListFilterContext = (CertificationScheduleTargetPermissionListFilterContext)filterValueConfig.listFilterContext;
            }
            if (attributes.containsKey(filterValueConfig.filterValueKey)) {
                List<ListFilterValue> filterValues = ListFilterService.getFilterValues(attributes, filterValueConfig.filterValueKey);
                ListFilterService listFilterService = new ListFilterService(this.getContext(), getLocale(), filterValueConfig.listFilterContext);
                listFilterService.collapseFilterValues(filterValues);
                if (tpListFilterContext != null) {
                    tpListFilterContext.reset();
                    attributes.put(filterValueConfig.filterKey, listFilterService.getFilter(filterValues));
                    attributes.put(CertificationDefinition.ARG_TARGET_PERMISSION_APP_FILTER, tpListFilterContext.getApplicationFilter());
                } else {
                    attributes.put(filterValueConfig.filterKey, listFilterService.getFilter(filterValues));
                }
                List<Map> filterValueMaps = new ArrayList<>();
                for (ListFilterValue filterValue : filterValues) {
                    filterValueMaps.add(filterValue.toMap());
                }
                if (Util.isEmpty(filterValueMaps)) {
                    filterValueMaps = null;
                }
                attributes.put(filterValueConfig.filterValueKey, filterValueMaps);
            } else {
                attributes.put(filterValueConfig.filterKey, null);
                if (tpListFilterContext != null) {
                    attributes.put(CertificationDefinition.ARG_TARGET_PERMISSION_APP_FILTER, null);
                }
            }
        }
    }

    /**
     * Expand the filter values to include suggests and whatnot as required by client. Uses FILTER_VALUES_CONFIG.
     * @param attributes Attributes map to update
     * @throws GeneralException
     */
    private void expandFilterValues(Attributes<String, Object> attributes) throws GeneralException {
        for (FilterValueConfig filterValueConfig : FILTER_VALUES_CONFIG) {
            if (attributes.containsKey(filterValueConfig.filterValueKey)) {
                List<ListFilterValue> filterValues = ListFilterService.getFilterValues(attributes, filterValueConfig.filterValueKey);
                ListFilterService listFilterService = new ListFilterService(this.getContext(), getLocale(), filterValueConfig.listFilterContext);
                listFilterService.expandFilterValues(filterValues);
                attributes.putClean(filterValueConfig.filterValueKey, filterValues);
            }
        }
    }

    /**
     * Create a meta data map to send to the client with the CertificationScheduleDTO with presentation and configuration
     * related values
     * @param schedule Initialized CertificationScheduleDTO for the metadata to apply to
     * @return Map of metadata
     */
    public Map<String, Object> getMetaData(CertificationScheduleDTO schedule) throws GeneralException {
        Map<String, Object> metaData = new HashMap<>();
        List<ListFilterDTO.SelectItem> certificationNameParams = new ArrayList<ListFilterDTO.SelectItem>() {{
            add(new ListFilterDTO.SelectItem(MessageKeys.NAME_TEMPLATE_FULL_DATE, String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_FULL_DATE)));
            add(new ListFilterDTO.SelectItem(MessageKeys.NAME_TEMPLATE_DATE_YEAR, String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_DATE_YEAR)));
            add(new ListFilterDTO.SelectItem(MessageKeys.NAME_TEMPLATE_DATE_QUARTER, String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_DATE_QUARTER)));
            add(new ListFilterDTO.SelectItem(MessageKeys.NAME_TEMPLATE_DATE_MONTH, String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_DATE_MONTH)));
        }};
        metaData.put(METADATA_CERTIFICATION_NAME_PARAMETERS, certificationNameParams);

        List<ListFilterDTO.SelectItem> accessReviewNameParameters = new ArrayList<ListFilterDTO.SelectItem>() {{
            add(new ListFilterDTO.SelectItem(MessageKeys.NAME_TEMPLATE_TYPE, String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_TYPE)));
            add(new ListFilterDTO.SelectItem(MessageKeys.NAME_TEMPLATE_FULL_DATE, String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_FULL_DATE)));
            add(new ListFilterDTO.SelectItem(MessageKeys.NAME_TEMPLATE_CERTIFIER_FULL_NAME, String.format(CERTIFICATION_NAMER_TEMPLATE_FORMAT, CertificationNamer.NAME_TEMPLATE_CERTIFIER_PREFIX + CertificationNamer.NAME_TEMPLATE_ID_FULL_NAME_SUFFIX)));
        }};
        metaData.put(METADATA_ACCESS_REVIEW_NAME_PARAMETERS, accessReviewNameParameters);

        List<ESignatureType> eSignatureTypes = new Notary(getContext(), getLocale()).getESignatureTypes();
        List<ListFilterDTO.SelectItem> esignatureSelectItems = new ArrayList<>();
        for (ESignatureType eSignatureType : Util.iterate(eSignatureTypes)) {
            esignatureSelectItems.add(new ListFilterDTO.SelectItem(eSignatureType.getDisplayableName(), eSignatureType.getName()));
        }
        metaData.put(METADATA_ESIGNATURES, esignatureSelectItems);

        List<ListFilterDTO.SelectItem> durationScaleItems = new ArrayList<ListFilterDTO.SelectItem>() {{
            add(new ListFilterDTO.SelectItem(Duration.Scale.Month, getLocale()));
            add(new ListFilterDTO.SelectItem(Duration.Scale.Week, getLocale()));
            add(new ListFilterDTO.SelectItem(Duration.Scale.Day, getLocale()));
            add(new ListFilterDTO.SelectItem(Duration.Scale.Hour, getLocale()));
        }};
        metaData.put(METADATA_DURATION_SCALES, durationScaleItems);

        List<ListFilterDTO.SelectItem> frequencyItems = new ArrayList<ListFilterDTO.SelectItem>() {{
            add(new ListFilterDTO.SelectItem(MessageKeys.FREQUENCY_ONCE, CronString.FREQ_ONCE));
            add(new ListFilterDTO.SelectItem(MessageKeys.FREQUENCY_HOURLY, CronString.FREQ_HOURLY));
            add(new ListFilterDTO.SelectItem(MessageKeys.FREQUENCY_DAILY, CronString.FREQ_DAILY));
            add(new ListFilterDTO.SelectItem(MessageKeys.FREQUENCY_WEEKLY, CronString.FREQ_WEEKLY));
            add(new ListFilterDTO.SelectItem(MessageKeys.FREQUENCY_MONTHLY, CronString.FREQ_MONTHLY));
            add(new ListFilterDTO.SelectItem(MessageKeys.FREQUENCY_QUARTERLY, CronString.FREQ_QUARTERLY));
            add(new ListFilterDTO.SelectItem(MessageKeys.FREQUENCY_ANNUALLY, CronString.FREQ_ANNUALLY));
        }};
        metaData.put(METADATA_FREQUENCY, frequencyItems);

        List<ListFilterDTO.SelectItem> autoClosingOptionItems = new ArrayList<ListFilterDTO.SelectItem>() {{
            add(new ListFilterDTO.SelectItem(MessageKeys.CERT_ACTION_LEGEND_REVOKE, CertificationAction.Status.Remediated));
            add(new ListFilterDTO.SelectItem(MessageKeys.CERT_ACTION_LEGEND_APPROVE, CertificationAction.Status.Approved));
            add(new ListFilterDTO.SelectItem(MessageKeys.CERT_ACTION_LEGEND_ALLOW_EXCEPTION, CertificationAction.Status.Mitigated));
        }};
        metaData.put(METADATA_AUTO_CLOSING_OPTIONS, autoClosingOptionItems);

        List<ListFilterDTO.SelectItem> selfCertificationLevels = new ArrayList<ListFilterDTO.SelectItem>() {{
            add(new ListFilterDTO.SelectItem(Certification.SelfCertificationAllowedLevel.All, getLocale()));
            add(new ListFilterDTO.SelectItem(Certification.SelfCertificationAllowedLevel.CertificationAdministrator, getLocale()));
            add(new ListFilterDTO.SelectItem(Certification.SelfCertificationAllowedLevel.SystemAdministrator, getLocale()));
        }};
        metaData.put(METADATA_SELF_CERT_LEVELS, selfCertificationLevels);

        // Provide default reminder & escalation values to the front end.
        NotificationConfigDTO.ReminderConfigDTO defaultReminderConfig = NotificationConfigDTO.ReminderConfigDTO.getDefault(getContext());
        metaData.put(METADATA_DEFAULT_REMINDER, defaultReminderConfig);

        NotificationConfigDTO.EscalationConfigDTO defaultEscalationConfig = NotificationConfigDTO.EscalationConfigDTO.getDefault(getContext());
        metaData.put(METADATA_DEFAULT_ESCALATION, defaultEscalationConfig);

        if (schedule.getCertificationGroupId() != null) {
            metaData.put(METADATA_EDITABLE_ATTRIBUTES, getEditableAttributes(schedule.getCertificationGroupId()));
        }
        metaData.put(METADATA_READ_ONLY, isReadOnly());
        metaData.put(METADATA_INCLUDE_SCOPE, new ScopeService(getContext()).isScopingEnabled());
        metaData.put(METADATA_RECOMMENDER_CONFIGURED, RecommenderUtil.isRecommenderConfigured(getContext()));

        return metaData;
    }

    /**
     * Helper to convert the specified known list of Integer values to longs
     * These attributes are Integers from being converted from JSON. Cast them to longs
     * which is what the API expects
     * @param attributes
     */
    private void readIntegerValues(Attributes<String, Object> attributes) {
        for (String attr : LONG_ATTRIBUTES) {
            if (attributes.containsKey(attr)) {
                attributes.put(attr, Util.atol(attributes.getString(attr)));
            }
        }

    }

    /**
     * Helper method to iterate through the notification attributes and convert the
     * DTOs to actual NotificationConfig objects for save.
     * @param attributes Attributes map that has notification DTOs to convert
     */
    private void readNotificationConfigs(Attributes<String, Object> attributes) {
        for (String attr : NOTIFICATION_ATTRIBUTES) {
            if (attributes.containsKey(attr)) {
                Map<String, Object> dto = (Map<String, Object>)attributes.get(attr);

                attributes.put(attr, new NotificationConfig(dto));
            }
        }
    }

    /**
     * If a duration is invalid, flag it as an error during validation. Assumes minimum duration value is 1.
     * @param attributeId the ID of the attribute
     * @param amount The amount to check
     * @param messageKey The error message key to use if there is an error
     * @param errors Map of errors to add to
     */
    private void checkDurationAmount(String attributeId, Long amount, String messageKey, Map<String, List<String>> errors) {
        checkDurationAmount(attributeId, amount, messageKey, errors, 1);
    }

    /**
    * If a duration is invalid, flag it as an error during validation. Allows for a custom minimum duration amount.
    * @param attributeId the ID of the attribute
    * @param amount The amount to check
    * @param messageKey The error message key to use if there is an error
    * @param errors Map of errors to add to
    * @param min Lowest value allowed for this duration's amount
    */
   private void checkDurationAmount(String attributeId, Long amount, String messageKey, Map<String, List<String>> errors, int min) {
       if (amount == null || amount < min) {
           addError(errors, attributeId, messageKey);
       }
   }

    /**
     * Gets whether or not the specified definition is used by more than one certification group or is on a schedule.
     * @param definition The definition to check.
     * @return True if the definition is shared, false otherwise.
     */
    public boolean isSharedCertificationDefinition(CertificationDefinition definition)
            throws GeneralException {
        return getNumCertificationGroups(definition.getId()) > 1 ||
                getNumCertificationSchedules(definition.getId(), definition.getName()) > 0 ||
                getNumIdentityTriggers(definition.getId()) > 0;
    }

    /**
     * Gets whether or not the specified definition is used by at least one certification group.
     * @param definition The definition to check.
     * @return True if the definition is part of a certification group, false otherwise.
     */
    public boolean isCertificationGroupDefinition(CertificationDefinition definition) throws GeneralException {
        return getNumCertificationGroups(definition.getId()) > 0;
    }

    /**
     * Gets the number of identity triggers that link to the specified certification definition.
     * @param certificationDefinitionId The id of the certification definition to look for.
     * @return The number of identity triggers.
     */
    private int getNumIdentityTriggers(String certificationDefinitionId)
            throws GeneralException {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("handler", CertificationTriggerHandler.class.getName()));

        Iterator<IdentityTrigger> identityTriggers = getContext().search(IdentityTrigger.class, queryOptions);

        int result = 0;

        while (identityTriggers.hasNext()) {
            IdentityTrigger identityTrigger = identityTriggers.next();

            if (identityTrigger.getParameters() != null) {
                Object triggerCertDefinitionId = identityTrigger.getParameters().get(IdentityTrigger.PARAM_CERT_DEF_ID);

                if (certificationDefinitionId.equals(triggerCertDefinitionId)) {
                    ++result;
                }
            }
        }

        return result;
    }

    /**
     * Gets the number of certification schedules with the specified definition.
     * @param certificationDefinitionId The id of the certification definition to look for.
     * @param certificationDefinitionName The name of the certification definition to look for
     * @return The number of certification schedules.
     */
    private int getNumCertificationSchedules(String certificationDefinitionId, String certificationDefinitionName)
            throws GeneralException {
        QueryOptions queryOptions = new QueryOptions();

        Iterator<TaskSchedule> schedules = getContext().search(TaskSchedule.class, queryOptions);

        int result = 0;

        while (schedules.hasNext()) {
            TaskSchedule schedule = schedules.next();
            TaskDefinition taskDefinition = schedule.getDefinition(getContext());

            if (TaskManager.IMMEDIATE_SCHEDULE.equals(schedule.getDescription())) {
                continue;
            }

            if (null == taskDefinition || !TaskDefinition.Type.Certification.equals(taskDefinition.getEffectiveType())) {
                continue;
            }

            String scheduleCertificationDefinitionId = schedule.getArgument(CertificationSchedule.ARG_CERTIFICATION_DEFINITION_ID);
            if (!Util.nullSafeEq(certificationDefinitionId, scheduleCertificationDefinitionId) &&
                    !Util.nullSafeEq(certificationDefinitionName, scheduleCertificationDefinitionId)) {
                continue;
            }

            ++result;
        }

        return result;
    }

    /**
     * Gets the number of certification groups with the specified definition.
     * @param certificationDefinitionId The id of the certification definition to look for.
     * @return The number of groups.
     */
    private int getNumCertificationGroups(String certificationDefinitionId)
            throws GeneralException
    {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("definition.id", certificationDefinitionId));

        return getContext().countObjects(CertificationGroup.class, queryOptions);
    }

    /**
     * Clones the specified definition, creating a new object with the same attributes but not
     * managed by hibernate and no id.
     * @param definition The source definition.
     * @return The newly created definition.
     */
    public CertificationDefinition cloneCertificationDefinition(CertificationDefinition definition) {
        CertificationDefinition result = definition.createCopy();
        result.setId(null);
        // Let's not just arbitrarily tag 'Modified 1234546' endlessly.
        // Replace it if there's already one
        String modifiedSuffix = " Modified ";
        Pattern p = Pattern.compile("^(.*" + modifiedSuffix + ")(\\d*)$");
        Matcher m = p.matcher(definition.getName());
        String newName = null;
        if (m.matches()) {
            newName = m.group(1) + new Date().getTime();
        } else {
            // no match
            newName = definition.getName() + modifiedSuffix + new Date().getTime();
        }
        result.setName(newName);

        return result;
    }

    /**
     * Get the latest phase of any certification in the given certification group
     * @param certificationGroupId ID of the CertificationGroup
     * @return Latest phase of any certification in the group.
     * @throws GeneralException
     */
    public Certification.Phase getLatestPhase(String certificationGroupId) throws GeneralException {
        Certification.Phase latestPhase = null;
        if (certificationGroupId != null) {
            QueryOptions queryOptions = new QueryOptions();
            queryOptions.add(Filter.eq("certificationGroups.id", certificationGroupId));
            queryOptions.setDistinct(true);

            Iterator<Object[]> phases = getContext().search(Certification.class, queryOptions, "phase");
            while (phases.hasNext()) {
                Object[] row = phases.next();

                Certification.Phase phase = (Certification.Phase) row[0];

                if (phase != null) {
                    if (latestPhase == null) {
                        latestPhase = phase;
                    } else if (latestPhase.ordinal() < phase.ordinal()) {
                        latestPhase = phase;
                    }
                }
            }

        }

        return latestPhase;
    }

    /**
     * Active period duration has special logic for when it is editable or not, this is kind of wonky but
     * evolved over time with some customer complaints so leaving it as-is.
     * @param certificationGroupId ID of the certification group we are editing
     * @return True if active period duration should be editable, otherwise false.
     * @throws GeneralException
     */
    public boolean isActivePeriodDurationEditable(String certificationGroupId) throws GeneralException {
        // We want to find out if any certs in this group are out of the Active phase but not signed off.
        // If any exist, the active period duration is no longer editable.
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certificationGroups.id", certificationGroupId));
        queryOptions.add(
                Filter.not(
                        Filter.or(
                                Filter.isnull("phase"),
                                Filter.eq("phase", Certification.Phase.Active),
                                Filter.eq("phase", Certification.Phase.Staged),
                                Filter.notnull("signed"))));
        queryOptions.setDistinct(true);

        int numRows = getContext().countObjects(Certification.class, queryOptions);

        boolean activePeriodDurationEditable = numRows == 0;
        //bug#21262, we need to take into consideration that if
        // all certs have been signed we need to set the editable to false
        if (activePeriodDurationEditable && allSigned(certificationGroupId)) {
            activePeriodDurationEditable = false;
        }

        return activePeriodDurationEditable;
    }

    /**
     * Helper method to determine whether all the certs have been signed for the certifictionDefinition
     */
    private boolean allSigned(String certificationGroupId) throws GeneralException {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certificationGroups.id", certificationGroupId));
        queryOptions.add(Filter.isnull("signed"));
        queryOptions.setDistinct(true);

        return (getContext().countObjects(Certification.class, queryOptions) == 0);
    }

    /**
     * Helper method to get the editable attribute list for the given certification group.
     * @return Null if everything is editable, otherwise an array containing editable attributes. Empty array indicates nothing is editable.
     */
    private List<String> getEditableAttributes(String certificationGroupId) throws GeneralException {
        // if cert group is empty or we are in read only mode, no need to check phases.
        if (isGroupEmpty(certificationGroupId) || isReadOnly()) {
            return new ArrayList<>();
        }

        Phase latestPhase = getLatestPhase(certificationGroupId);

        // We want to keep it an empty list during our end phase so that everything is read only
        if (latestPhase == Certification.Phase.End) {
            return new ArrayList<>();
        }

        List<String> editableAttributes = CertificationDefinition.getEditableAttributesForPhase(latestPhase);
        if (Util.isEmpty(editableAttributes)) {
            // Setting to null will imply everything is editable
            editableAttributes = null;
        } else {
            // SPECIAL CASE: Active period durations can be editable based on criteria different from latest phase
            if ((!editableAttributes.contains(CertificationDefinition.ARG_ACTIVE_PERIOD_DURATION_AMOUNT) ||
                    !editableAttributes.contains(CertificationDefinition.ARG_ACTIVE_PERIOD_DURATION_SCALE)) &&
                    isActivePeriodDurationEditable(certificationGroupId)) {
                editableAttributes.add(CertificationDefinition.ARG_ACTIVE_PERIOD_DURATION_AMOUNT);
                editableAttributes.add(CertificationDefinition.ARG_ACTIVE_PERIOD_DURATION_SCALE);
            }

            // SPECIAL CASE: Tags are not attributes, but go ahead and pretend they are. They are editable until End phase,
            // so just add them since End phase is handled above.
            editableAttributes.add(ATTR_TAGS);
        }

        return editableAttributes;
    }

    /**
     * Check if the certification group is empty
     * @param certificationGroupId ID of the certification group. Optional. If not defined, always returns false.
     * @return true if empty, otherwise false.
     */
    private boolean isGroupEmpty(String certificationGroupId) throws GeneralException {
        if (!Util.isNothing(certificationGroupId)) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("certificationGroups.id", certificationGroupId));
            CertificationGroup group = getContext().getObjectById(CertificationGroup.class, certificationGroupId);
            CertificationService.filterEmptyCerts(getContext(), ops, group);
            return (getContext().countObjects(Certification.class, ops) == 0);
        }

        return false;
    }
    /**
     * Some people, such as CertificationAdministrators, can view options used for existing certifications, however they are not authorized
     * to change or save anything.
     */
    private boolean isReadOnly() throws GeneralException {
        return !RightAuthorizer.isAuthorized(this.userContext, SPRight.FullAccessCertificationSchedule);
    }
}
