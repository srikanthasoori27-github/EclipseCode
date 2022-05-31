/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.certification;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.CertificationTriggerHandler;
import sailpoint.api.ObjectUtil;
import sailpoint.api.TaskManager;
import sailpoint.api.certification.CertificationNamer;
import sailpoint.api.certification.SelfCertificationChecker;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.Certification.Type;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.object.CertificationDefinition.FactoryBean;
import sailpoint.object.CertificationDefinition.GroupBean;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.NotificationConfig;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Rule;
import sailpoint.object.SPRight;
import sailpoint.object.Tag;
import sailpoint.object.TaskSchedule;
import sailpoint.recommender.RecommenderUtil;
import sailpoint.service.certification.schedule.CertificationScheduleService;
import sailpoint.task.ApplyCertificationDefinitionChangesTask;
import sailpoint.tools.CronString;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseTaskBean;
import sailpoint.web.ScoreCategoryListBean;
import sailpoint.web.group.PopulationFilterUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.trigger.IdentityTriggerDTO;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 * 
 * JSF bean for scheduling certifications.
 * 
 * @author Peter Holcomb
 * @author Kelly Grizzle
 */
public class CertificationScheduleBean extends BaseTaskBean<TaskSchedule>
{

    // //////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    // //////////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory
            .getLog(CertificationScheduleBean.class);

    private boolean readOnly;
    private String certificationGroupId;

    private List<String> identityIds;
    private List<Identity> selectedIdentities;

    private Map<String, String> identityManagers;

    private List<SelectItem> accessReviewNameParameters;
    private List<SelectItem> certificationNameParameters;
    private List<SelectItem> roleTypeItems;

    private List<SelectItem> escalationRules;
    private List<SelectItem> approverRules;
    private List<SelectItem> certifierRules;
    private List<SelectItem> exclusionRules;
    private List<SelectItem> phaseChangeRules;
    private List<SelectItem> predelegationRules;
    private List<SelectItem> autoClosingRules;

    private List<String> identitiesToRemove;

    private boolean showSchedule;
    private boolean showScheduleAll;
    private boolean runNow;
    private boolean continuousHasLaunched;
    private boolean activePeriodDurationEditable = true;

    private Identity identityToAdd;

    private String groupToAddId;
    private String factoryToAddId;
    private String selectedGroupId;

    private CertificationScheduleDTO editedCertificationSchedule;
    private IdentityTriggerDTO trigger;

    private Certification.Phase latestPhase;

    private CertificationDefinition existingDefinition;
    
    private Boolean triggeredCertGroup = null;

    // Recommendations
    private boolean isRecommenderConfigured;

    private static final String EDITED_CERT_SCHEDULE_KEY = "EDITED_CERT_SCHEDULE";
    private static final String EDITED_CERT_EVENT_ID = "EDITED_CERT_EVENT_ID";
    private static final String CERT_GROUP_ID = "CERT_GROUP_ID";

    // Temporary session key that specifies we're editing an event rather than
    // a schedule. Once we split apart the certification definition and
    // schedule/event UIs, the schedule bean should be split into in two - a
    // schedule bean and a trigger bean. We will no longer need this.
    private static final String IS_EVENT = "IS_EVENT";
    
    // List of properties to fetch and include with JSON for identities to certify
    // Not standard way to get grid json but don't want to overdue it with columnconfig
    // and such since this does not come from BaseListBean.
    private static final List<String> IDENTITIES_JSON_PROPERTIES = 
            Util.csvToList("id,name,firstname,lastname");

    // //////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    // //////////////////////////////////////////////////////////////////////////
    
    @SuppressWarnings("unchecked")
    public CertificationScheduleBean() throws GeneralException
    {
        //Force load, clean old junk out and start so fresh and so clean clean
        if (Util.otob(getRequestParameter(FORCE_LOAD))) {
            cleanSession(getSessionScope());
        }
        
        certificationGroupId = loadCertificationGroupId();
        editedCertificationSchedule = (CertificationScheduleDTO) getSessionScope()
                .get(EDITED_CERT_SCHEDULE_KEY);
        
        if (certificationGroupId != null) {
            readOnly = true;
            
            // Wasn't in the session, so load it up
            if (editedCertificationSchedule == null) {                
                editedCertificationSchedule = loadExistingCertificationSchedule(certificationGroupId);
            }
            
            if (editedCertificationSchedule != null && Certification.Type.Identity.equals(editedCertificationSchedule.getCertificationType())) {
                String triggerId = getCertGroupTriggerId();
                getSessionScope().put(EDITED_CERT_EVENT_ID, triggerId);
                getSessionScope().put(IS_EVENT, triggerId != null);
            }
            
            // Store CertificationScheduleDTO and group ID in the session so we don't reload it every time
            getSessionScope().put(EDITED_CERT_SCHEDULE_KEY, editedCertificationSchedule);
            getSessionScope().put(CERT_GROUP_ID, certificationGroupId);
        } else {
            runNow = false;
        }

        if (editedCertificationSchedule == null) {
            // Assume an individual cert
            CertificationSchedule schedule = new CertificationSchedule(
                    getContext(), getLoggedInUser());
            schedule.getDefinition().setType(Certification.Type.getDefaultType());
            editedCertificationSchedule = new CertificationScheduleDTO(
                    getContext(), schedule);

            // Default certification owner to the current user
            editedCertificationSchedule
                    .setCertificationOwner(getLoggedInUser());
        } else {
            // We are editing an existing schedule.
            editedCertificationSchedule.attach(getContext());

            // Check to see if this is a continuous certification that has
            // already been launched.
            if (CertificationScheduler.FREQ_CONTINUOUS
                    .equals(editedCertificationSchedule.getFrequency())) {
                String taskId = editedCertificationSchedule.getTaskId();
                this.continuousHasLaunched = true;
            }
        }

        existingDefinition = editedCertificationSchedule.getDefinition().createCopy();
        
        setDefaultName();
        setDefaultCertificationNameTemplate();

        this.trigger = new IdentityTriggerDTO(getOrCreateTrigger());


        // Continuous certifications are gone, but we keep the original settings in the CertificationDefinition object.
        // So lets continue to have a warning with some information.
        if (editedCertificationSchedule.getDefinition().isContinuous()) {
            addMessage(new Message(Message.Type.Warn, MessageKeys.CERT_SCHED_CONTINUOUS_DEPRECATION_WARNING));
        }

        this.isRecommenderConfigured = RecommenderUtil.isRecommenderConfigured(getContext());
        if (!this.isRecommenderConfigured && !existingDefinition.getRecommendationsGenerated()) {
            editedCertificationSchedule.setShowRecommendations(false);
        }
    }
    
    private String getCertGroupTriggerId()
        	throws GeneralException
    {
    	QueryOptions options = new QueryOptions();
		options.add(Filter.eq("certificationGroups.id", certificationGroupId));
		options.add(Filter.notnull("triggerId"));
		options.setResultLimit(1);
		
		Iterator<Object[]> results = getContext().search(Certification.class, options, Arrays.asList("triggerId"));
		if (results.hasNext()) {
			Object[] row = results.next();
			return (String)row[0];
		}
		
		return null;
    }
    
    public String getTentativeDateWarning()
    {
    	return WebUtil.localizeMessage(MessageKeys.CERT_SCHED_TENTATIVE_DATES, 
    			editedCertificationSchedule.getActiveStartDate());
    }
    
    public boolean isTentativeActivationDate()
    	throws GeneralException
    {
    	if (isExistingCertGroup()) {
    		return !isPastStagedPhase();
    	} else {
    		return editedCertificationSchedule.isStagingEnabled();
    	}
    }
    
    public boolean isEditable()
    	throws GeneralException
    {

        // FullAccessCertifications can get in to view the options, but they should not be able to edit a schedule in flight
        if (!Authorizer.hasAccess(getLoggedInUserCapabilities(), getLoggedInUserRights(), SPRight.FullAccessCertificationSchedule)) {
            return false;
        }

    	if (CertificationScheduler.FREQ_CONTINUOUS.equals(editedCertificationSchedule.getFrequency())) {
            if (editedCertificationSchedule.getSchedule().getDefinition().getId() != null && editedCertificationSchedule.getTaskId() == null) {
                return false;
            }
    	}

    	return !getContinuousHasLaunched() && !isTriggeredCertGroup();
    }
    
    public boolean isTriggeredCertGroup()
    	throws GeneralException
    {
    	if (triggeredCertGroup == null) {    	
	    	if (isExistingCertGroup()) {
	    		QueryOptions options = new QueryOptions();
	    		options.add(Filter.eq("certificationGroups.id", certificationGroupId));
	    		options.add(Filter.notnull("triggerId"));
	    		
	    		triggeredCertGroup = getContext().countObjects(Certification.class, options) > 0;
	    	} else {
	    		triggeredCertGroup = false;
	    	}
    	}
    	
    	return triggeredCertGroup; 	
    }

    /**
     * Constructs a bean without the normal initialization effort. This allows
     * easy access to a couple of methods that would otherwise have to be
     * duplicated elsewhere.
     * 
     * @param simple
     */
    public CertificationScheduleBean(boolean simple)
    {
        super();
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    // //////////////////////////////////////////////////////////////////////////

    /**
     * Add the selected group to the list of iPOPs to certify.
     */
    public String addGroup() throws GeneralException
    {

        if (null != Util.getString(this.groupToAddId)) {
            GroupBean existing = getSelectedGroup(this.groupToAddId);
            if (null != existing) {
                warnMessage(MessageKeys.POP_ALREADY_BEING_CERTIFIED);
            } else {
                GroupDefinition def = getContext().getObjectById(
                        GroupDefinition.class, this.groupToAddId);
                getEditedCertificationSchedule().addGroup(def);
            }
        }

        this.groupToAddId = null;

        return "groupAddSuccess";
    }

    /**
     * Remove any selected group from the list of iPOPs to certify.
     */
    public String removeGroups() throws GeneralException
    {
        List<GroupBean> remainingGroups = new ArrayList<GroupBean>();
        for (GroupBean groupBean : getEditedCertificationSchedule().getGroups()) {
            if (!groupBean.isChecked()) {
                remainingGroups.add(groupBean);
            }
        }
        getEditedCertificationSchedule().setGroups(remainingGroups);

        return "groupRemoveSuccess";
    }

    private GroupBean getSelectedGroup(String id) throws GeneralException
    {
        CertificationScheduleDTO dto = getEditedCertificationSchedule();
        if (id != null && dto.getGroups() != null) {
            for (CertificationDefinition.GroupBean bean : dto.getGroups()) {
                if (id.equals(bean.getGroupDefinition().getId())) {
                    return bean;
                }
            }
        }
        return null;
    }

    /**
     * Add the selected factory to the list of factories to certify.
     */
    public String addFactory() throws GeneralException
    {

        String fid = Util.getString(this.factoryToAddId);
        if (fid != null) {
            CertificationScheduleDTO dto = getEditedCertificationSchedule();
            List<FactoryBean> factories = dto.getFactories();
            if (factories == null) {
                factories = new ArrayList<FactoryBean>();
                dto.setFactories(factories);
            }

            FactoryBean existing = dto.getFactory(fid);
            if (existing != null) {
                warnMessage(MessageKeys.FACTORY_ALREADY_BEING_CERTIFIED);
            } else {
                GroupFactory f = getContext()
                        .getObjectById(GroupFactory.class, fid);
                getEditedCertificationSchedule().addFactory(f);
            }
        }

        this.factoryToAddId = null;

        return "factoryAddSuccess";
    }

    /**
     * Remove any selected factories from the list to certify.
     */
    public String removeFactories() throws GeneralException
    {
        List<FactoryBean> remainingFactories = new ArrayList<FactoryBean>();
        for (FactoryBean factoryBean : getEditedCertificationSchedule()
                .getFactories()) {
            if (!factoryBean.isChecked()) {
                remainingFactories.add(factoryBean);
            }
        }
        getEditedCertificationSchedule().setFactories(remainingFactories);

        return "factoryRemoveSuccess";
    }

    public CertificationScheduleDTO getEditedCertificationSchedule()
    {
        return editedCertificationSchedule;
    }

    /**
     * Retrieve the IdentityTrigger being edited or create a new one.
     */
    private IdentityTrigger getOrCreateTrigger() throws GeneralException
    {

        IdentityTrigger trigger = null;

        String triggerId = (String) getSessionScope().get(EDITED_CERT_EVENT_ID);
        if (null != triggerId) {
            trigger = getContext().getObjectById(IdentityTrigger.class,
                    triggerId);
        } else {
            // Note: If we split scheduling and events, this should go into
            // createObject().
            trigger = new IdentityTrigger();
            trigger.setType(IdentityTrigger.Type.Create);
            trigger.setAssignedScope(editedCertificationSchedule
                    .getAssignedScope());
            trigger.setOwner(getLoggedInUser());
            trigger.setHandler(CertificationTriggerHandler.class.getName());
        }

        return trigger;
    }

    /**
     * KG - remove this when we split the certification definition, schedules,
     * and events in the UI. See IS_EVENT for more info.
     */
    public boolean isIdentityTrigger()
    {
        return Util.otob(super.getSessionScope().get(IS_EVENT));
    }

    public IdentityTriggerDTO getTrigger()
    {
        return this.trigger;
    }

    public void setTrigger(IdentityTriggerDTO trigger)
    {
        this.trigger = trigger;
    }

    /**
     * Identities to remove when editing Bulk Certifications
     * 
     * @return
     */
    public List<String> getIdentitiesToRemove()
    {
        return identitiesToRemove;
    }

    public void setIdentitiesToRemove(List<String> identitiesToRemove)
    {
        this.identitiesToRemove = identitiesToRemove;
    }

    /**
     * @return read-only list of identities that are being certified
     */
    public Iterator<Object[]> getIdentitiesToCertify()
    {
        List<String> identitiesToCertify = getEditedCertificationSchedule()
                .getIdentitiesToCertify();
        Iterator<Object[]> iterator = null;

        // Preemptively apply the offset and page_size because Oracle can't
        // handle
        // more than 1000 items in a list. Note that this means we are
        // completely
        // dependent on the order that the identities arrived in for our
        // sorting,
        // i.e., we are unable to sort any further by ourselves.
        int start = Util.atoi(getRequestParameter("start"));
        int limit = getResultLimit();

        int lastIndex = start + limit;

        if (identitiesToCertify.size() < lastIndex) {
            lastIndex = identitiesToCertify.size();
        }

        List<String> identitiesToFetch = identitiesToCertify.subList(start, lastIndex);

        if (! Util.isEmpty(identitiesToFetch)) {
            try {
                QueryOptions ops = new QueryOptions(Filter.in("name", identitiesToFetch));
                iterator = getContext().search(Identity.class, ops, IDENTITIES_JSON_PROPERTIES);
                if (Util.isEmpty(iterator)) {
                    // If we don't get any hits, then search the IDs just in case we're 
                    // working with a legacy certification that stored IDs rather than names
                    ops = new QueryOptions(Filter.in("id", identitiesToFetch));
                    iterator = getContext().search(Identity.class, ops, IDENTITIES_JSON_PROPERTIES);
                }
            } catch (GeneralException e) {
                log.error(
                    "Identities could not be fetched because the database is inaccessible",
                    e);
                errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            }
        }
        return iterator;
    }

    public String getIdentitiesToCertifyJson() throws GeneralException {
        Iterator<Object[]> identities = getIdentitiesToCertify();
        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String, Object>> identityRows = new ArrayList<Map<String, Object>>();
        response.put("totalCount", getIdentitiesCount());
        response.put("identities", identityRows);
        while (identities != null && identities.hasNext()) {
            Object[] identity = identities.next();
            Map<String, Object> identityRow = new HashMap<String, Object>();
            int i = 0;
            for (String property : IDENTITIES_JSON_PROPERTIES) {
                identityRow.put(property, identity[i++]);
            }
            identityRows.add(identityRow);
        }
        return JsonHelper.toJson(response);
    }

    /**
     * @return The number of identities being certified in a bulk certification
     */
    public int getIdentitiesCount()
    {
        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();
        final int retval;

        if (certSchedule == null
                || certSchedule.getIdentitiesToCertify() == null) {
            retval = 0;
        } else {
            retval = certSchedule.getIdentitiesToCertify().size();
        }

        return retval;
    }

    /**
     * JSF demands the ability to set values on inputs, but we really don't want
     * to do this for this property. This method is strictly for JSF's benefit.
     * 
     * @param count
     */
    public void setIdentitiesCount(int count)
    {
        // No-op... the count will be retrieved dynamically
    }

    public String showSchedule()
    {

        // Add identities to a list to display on the schedule.
        for (String id : identityIds) {
            try {
                Identity ident = getContext().getObjectById(Identity.class, id);
                if (selectedIdentities == null) {
                    selectedIdentities = new ArrayList<Identity>();
                }
                selectedIdentities.add(ident);
            } catch (GeneralException ge) {
                log.error("Exception: " + ge.getMessage());
                continue;
            }
        }
        showSchedule = true;
        return null;
    }

    /**
     * Allows a user to create a bulk certification of users from the risk
     * scores page
     */
    public String showScheduleAllAction()
    {
        showSchedule = false;
        showScheduleAll = true;
        return null;
    }

    public String addUserToBulkCertification()
    {
        if (identityToAdd == null) {
            return null;
        } else {
            List<String> identitiesToCertify = getEditedCertificationSchedule()
                    .getIdentitiesToCertify();

            if (!identitiesToCertify.contains(identityToAdd.getName())) {
                identitiesToCertify.add(identityToAdd.getName());
            }

            identityToAdd = null;

            return "addUserToBulkCert";
        }
    }

    public String removeUsersFromBulkCertification()
    {
        List<String> identitiesToCertify = getEditedCertificationSchedule()
                .getIdentitiesToCertify();

        if (identitiesToCertify != null) {
            identitiesToCertify.removeAll(identitiesToRemove);
        }

        return "removeUsersFromBulkCert";
    }

    /**
     * Save the certification IdentityTrigger that is being edited or created.
     * 
     * @return JSF nav key, or null if error
     */
    public String saveTrigger()
    {

        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();
        CertificationDefinition def = certSchedule.getDefinition();
        try {
            String name = this.trigger.getName();
            
            if (Util.isNullOrEmpty(def.getName())) {
            	def.setName(name);
            }
            
            def.setOwner(getLoggedInUser());

            infoMessage(MessageKeys.CERT_EVENT_SAVED, name);

            IdentityTrigger trigger = getOrCreateTrigger();
            saveTrigger(trigger, def);
        } catch (GeneralException ge) {
            log.error(
                    "GeneralException encountered: [" + ge.getMessage() + "]",
                    ge);
            errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            return null;
        }
        cleanSession();

        String result = NavigationHistory.getInstance().back();
        if (result == null) {
            result = "backToCertEvents";
        }

        return result;
    }

    /**
     * Save the trigger and given definition.
     */
    private void saveTrigger(IdentityTrigger trigger,
            CertificationDefinition def) throws GeneralException
    {

        // Save the trigger.
        this.trigger.commit(trigger);

        // Now save the definition on the trigger. Save the definition first to
        // make sure we have an ID.
        def.setTriggerId(trigger.getId());
        getContext().saveObject(def);
        trigger.setCertificationDefinition(def);
        getContext().saveObject(trigger);
        getContext().commitTransaction();
    }

    /**
     * Wraps up the call to the different certification creation methods. This
     * removes the need for 5 different submit buttons on the page.
     * 
     * @return JSF nav key, or null if error
     */
    public String scheduleCertification() throws GeneralException
    {

        // Validation is usually done before the form is submitted by an a4j
        // action, however clicking enter sometimes submits the form without
        // going through the appropriate a4j command. Validate again here so
        // that we'll be sure not to save invalid schedules.
        this.validateFields();
        if (getHasErrors()) {
            return null;
        }

        if (isExistingCertGroup()) {
            return saveChangedCertification();
        } else {
            CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();
            CertificationScheduleService scheduleService = new CertificationScheduleService(this);
            CertificationDefinition newDefinition = certSchedule.getDefinition();
            if (scheduleService.isCertificationGroupDefinition(newDefinition)) {
            	CertificationDefinition oldDefinition = newDefinition;
            	
            	newDefinition = scheduleService.cloneCertificationDefinition(oldDefinition);
            	certSchedule.getSchedule().setDefinition(newDefinition);
            	
            	getContext().decache(oldDefinition);
            	
            	getContext().saveObject(newDefinition);
            	getContext().commitTransaction();
            }
            

            // Auto-create any tags in the definition that don't yet exist.
            // After
            // this, getTags() should be used instead of getTagIdsOrNames().
            autoCreateTags(certSchedule);

            saveRemindersAndEscalations();

            if (!newDefinition.getShowRecommendations()) {
                newDefinition.setAutoApprove(false);
            }

            // If we're dealing with a trigger, try to save it.
            if (isIdentityTrigger()) {
                return saveTrigger();
            }

            if (certSchedule.isBusinessRoleCertification()) {
                return scheduleBusinessRoleCertification();
            } else if (certSchedule.isIdentityCertification()) {
                return scheduleIndividualCertification();
            } else if (certSchedule.isAccountGroupCertification()) {
                return scheduleAccountGroupCertification();
            } else if (certSchedule.isAdvancedCertification()) {
                return scheduleAdvancedCertification();
            } else if (certSchedule.isApplicationOwnerCertification()) {
                return scheduleApplicationCertification();
            } else if (certSchedule.isManagerCertification()) {
                return scheduleManagerCertification();
            } else if (certSchedule.isDataOwnerCertification()) {
                return scheduleDataOwnerCertification();
            } else {
                throw new RuntimeException("Unknown certification type '"
                        + certSchedule.getCertificationType() + "'");
            }
        }
    }

    /**
     * Saves changes made to the CertificationDefinition while the certification
     * is active.
     * 
     * @throws GeneralException
     */
    private String saveChangedCertification() throws GeneralException
    {
        CertificationScheduleDTO newCertSchedule = getEditedCertificationSchedule();
        CertificationScheduleService scheduleService = new CertificationScheduleService(this);
        List<Tag> oldTags = newCertSchedule.getDefinition().getTags();
        autoCreateTags(newCertSchedule);

        if (validate(newCertSchedule.getSchedule())) {            
            CertificationDefinition newDefinition = newCertSchedule.getDefinition();
            
            saveRemindersAndEscalations();
            
            if (scheduleService.isSharedCertificationDefinition(newDefinition)) {
            	CertificationDefinition oldDefinition = newDefinition;
            	
            	newDefinition = scheduleService.cloneCertificationDefinition(oldDefinition);
            	oldDefinition.setTags(oldTags);
            	
            	getContext().decache(oldDefinition);
            	
            	CertificationGroup certGroup = getContext().getObjectById(CertificationGroup.class, certificationGroupId);
            	certGroup.setDefinition(newDefinition);
            	
            	getContext().saveObject(newDefinition);
            	getContext().saveObject(certGroup);
            	getContext().commitTransaction();
            } else {            
            	getContext().saveObject(newDefinition);
            	getContext().commitTransaction();
            }
    
            Attributes<String, Object> args = new Attributes<String, Object>();
            args.put(ApplyCertificationDefinitionChangesTask.ARG_CERTIFICATION_GROUP_ID, certificationGroupId);
            
            TaskManager taskManager = new TaskManager(getContext());
            taskManager.run(ApplyCertificationDefinitionChangesTask.TASK_NAME, args);
    
            infoMessage(MessageKeys.CERT_CHANGES_SAVED, ObjectUtil.getName(getContext(), CertificationGroup.class, certificationGroupId));
        
            cleanSession();

            return goBack();
        }
        
        return null;
    }

    /**
     * Validates the changes made by comparing newDefinition with existingDefinition.
     * Validation failure messages will be added to the session.
     * @param newDefinition The new certification definition.
     * @return True if the new definition is valid, false otherwise.
     */
    private boolean validate(CertificationSchedule schedule)
    {
        boolean valid = true;
        
        CertificationDefinition definition = schedule.getDefinition();
        
        if (changed(definition.getActivePeriodDurationAmount(), existingDefinition.getActivePeriodDurationAmount()) ||
            changed(definition.getActivePeriodDurationScale(), existingDefinition.getActivePeriodDurationScale())) {
            
            if (schedule.getActiveEndDate().compareTo(new Date()) < 0) {
                errorMessage(MessageKeys.CERT_ERR_ACTIVE_PERIOD_DATE_PAST, schedule.getActiveEndDate());
                valid = false;
            }
        }
        
        if (definition.isChallengePeriodEnabled() && (changed(definition.getChallengePeriodDurationAmount(), existingDefinition.getChallengePeriodDurationAmount()) ||
            changed(definition.getChallengePeriodDurationScale(), existingDefinition.getChallengePeriodDurationScale()))) {
            
            if (schedule.getChallengeEndDate().compareTo(new Date()) < 0) {
                errorMessage(MessageKeys.CERT_ERR_CHALLENGE_PERIOD_DATE_PAST, schedule.getChallengeEndDate());
                valid = false;
            }
        }
        
        if (definition.isRemediationPeriodEnabled() && (changed(definition.getRemediationPeriodDurationAmount(), existingDefinition.getRemediationPeriodDurationAmount()) ||
                changed(definition.getRemediationPeriodDurationScale(), existingDefinition.getRemediationPeriodDurationScale()))) {
                
            if (schedule.getRemediationEndDate().compareTo(new Date()) < 0) {
                errorMessage(MessageKeys.CERT_ERR_REMEDIATION_PERIOD_DATE_PAST, schedule.getRemediationEndDate());
                valid = false;
            }
        }
        
        return valid;
    }

    private static boolean changed(Object a, Object b) {
        return !Util.nullSafeEq(a, b, true, true);
    }
    
    

    private void saveRemindersAndEscalations() throws GeneralException
    {

        String jsonString = getEditedCertificationSchedule().getCertificationNotificationInfo().getData();
        if (Util.isNullOrEmpty(jsonString)) {
            return;
        }

        NotificationConfig config = NotificationConfig.createFromJsonString(getContext(), jsonString);
        if (config != null) {
            config.setEnabled(getEditedCertificationSchedule().getCertificationNotificationInfo().isEnabled());
            getCertificationDefinition().setCertificationNotificationConfig(config);
        }
        
        CertificationDefinition.NotificationConfig notifConfig = getEditedCertificationSchedule().getRemediationNotificationConfig();
        NotificationConfig configDb = NotificationConfig.createFromJsonObject(getContext(), notifConfig);
        getCertificationDefinition().setRemediationNotificationConfig(configDb);

        notifConfig = getEditedCertificationSchedule().getCertificationRequiredNotificationConfig();
        configDb = NotificationConfig.createFromJsonObject(getContext(), notifConfig);
        getCertificationDefinition().setCertificationRequiredNotificationConfig(configDb);

        notifConfig = getEditedCertificationSchedule().getOverdueNotificationConfig();
        configDb = NotificationConfig.createFromJsonObject(getContext(), notifConfig);
        getCertificationDefinition().setOverdueNotificationConfig(configDb);
    }

    private CertificationDefinition getCertificationDefinition()
            throws GeneralException
    {
        return getEditedCertificationSchedule().getSchedule().getDefinition();
    }

    /**
     * Auto-create any tags in the tagIdsOrNames list that do not already exist.
     * This repopulates the tags property on the schedule with all tags -
     * including those just created.
     */
    private void autoCreateTags(CertificationScheduleDTO schedule)
            throws GeneralException
    {

        List<Tag> tags = new ArrayList<Tag>();
        List<String> tagIdsOrNames = schedule.getTagIdsOrNames();
        if (null != tagIdsOrNames) {
            for (String idOrName : tagIdsOrNames) {
                Tag tag = getContext().getObjectById(Tag.class, idOrName);
                if (null == tag) {
                    // Someone could have clicked the "Add New Tag" button with
                    // an existing tag name. Lookup by name before we try to
                    // create the tag since name is unique. If it is still not
                    // found, we will create a new tag. See bug 5090.
                    // IIQETN-4867 sanitize tags before saving new ones
                    String sanitizedName = WebUtil.safeHTML(idOrName);
                    if (Util.isNotNullOrEmpty(sanitizedName)) {
                        tag = getContext().getObjectByName(Tag.class, sanitizedName);
                        if (null == tag) {
                            tag = new Tag(sanitizedName);
                            getContext().saveObject(tag);
                        }
                    }
                }
                if (tag != null) { // IIQETN-4867 empty sanitized tag names will not create a tag object
                    tags.add(tag);
                }
            }
        }

        schedule.setTags(tags);
    }

    /**
     * Schedules a certification for an individual or list of individuals
     * 
     * @return JSF nav key, or null if error
     */
    private String scheduleIndividualCertification() throws GeneralException
    {
        int size = 0;
        CertificationScheduleDTO dto = getEditedCertificationSchedule();
        List<String> selectedIdentities = dto.getIdentitiesToCertify();
        CertificationSchedule schedule = dto.getSchedule();

        /**
         * If this method was called through the bulk search or bulk risk
         * certification pages, the selected identities will already be
         * configured. So we can just get the size off of them and move on.
         */
        if (selectedIdentities != null)
            size = selectedIdentities.size();

        try {
            String name = getCertificationName().getLocalizedMessage();
            schedule.setName(name);

            infoMessage(MessageKeys.INDIVIDUAL_CERT_SCHEDULED, size);
            save(schedule);
        } catch (GeneralException ge) {
            log.error(
                    "GeneralException encountered: [" + ge.getMessage() + "]",
                    ge);
            errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            return null;
        }
        cleanSession();

        String result = NavigationHistory.getInstance().back();

        if (result == null) {
            result = "backToCertSchedules";
        }

        return result;
    }

    private Message getCertificationName() throws GeneralException
    {

        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();

        Message msg = null;

        if (null != certSchedule.getName()) {
            msg = new Message(certSchedule.getName());
        } else {

            Date firstExec = runNow ? new Date()
                    : getEditedCertificationSchedule().getFirstExecution();

            String certTypeKey = certSchedule.getCertificationType()
                    .getMessageKey();

            msg = new Message(MessageKeys.TASK_SCHED_GLBL_CERT, new Message(
                    certTypeKey), firstExec);

        }

        return msg;
    }

    //
    private String scheduleManagerCertification()
    {

        try {
            CertificationScheduleDTO dto = getEditedCertificationSchedule();
            CertificationSchedule schedule = dto.getSchedule();
            String name = getCertificationName().getLocalizedMessage();
            schedule.setName(name);
            if (schedule.getDefinition().isGlobal()) {
                infoMessage(MessageKeys.GBL_MGR_CERT_SCHEDULED);
            } else {
                Identity certifier = schedule.getDefinition().getCertifier(
                        getContext());
                // If the certifier isn't a manager, return null.
                if (!attestorIsManager(certifier)) {
                    addRequiredErrorMessage("certifyRecipient",
                            MessageKeys.ERR_NOT_A_MANAGER);
                    return null;
                }

                QueryOptions subordinateFilter = new QueryOptions();
                subordinateFilter.add(
                        Filter.and(Filter.eq("manager", certifier)),
                        Filter.eq("managerStatus", true));
                int numManagerSubordinates = getContext().countObjects(
                        Identity.class, subordinateFilter);
                boolean inclSubordinates = schedule.getDefinition()
                        .isSubordinateCertificationEnabled()
                        && numManagerSubordinates > 0;

                if (inclSubordinates) {
                    infoMessage(
                            MessageKeys.MGR_CERT_WITH_SUBORDINATES_SCHEDULED,
                            certifier.getName());
                } else {
                    infoMessage(MessageKeys.MGR_CERT_SCHEDULED,
                            certifier.getName(), "");
                }
            }

            save(schedule);

        } catch (GeneralException ge) {
            log.error("GeneralException encountered: [" + ge.getMessage() + "]");
            errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            return null;
        }

        cleanSession();
        return "scheduleCertificationSuccess";
    }

    private boolean attestorIsManager(Identity attestor)
            throws GeneralException
    {
        return ((attestor != null) && attestor.getManagerStatus());
    }

    private String scheduleAdvancedCertification() throws GeneralException
    {
        CertificationScheduleDTO dto = getEditedCertificationSchedule();
        CertificationSchedule schedule = dto.getSchedule();
        try {
            String name = getCertificationName().getLocalizedMessage();
            schedule.setName(name);

            infoMessage(MessageKeys.ADV_CERT_SCHEDULED);
            save(schedule);
        } catch (GeneralException ge) {
            log.error("GeneralException encountered: [" + ge.getMessage() + "]");
            errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            return null;
        }
        cleanSession();
        return "scheduleCertificationSuccess";
    }

    // Schedules a certification for an individual or list of individuals
    public String scheduleBulkRiskCertification()
    {
        log.warn("Schedule Bulk Risk Certification");
        ValueBinding vb = getFacesContext().getApplication()
                .createValueBinding("#{scoreCategoryList}");
        ScoreCategoryListBean scoreListBean = (ScoreCategoryListBean) vb
                .getValue(getFacesContext());

        try {
            if (null != scoreListBean
                    && getEditedCertificationSchedule().getCertifier().getId() != null
                    && getEditedCertificationSchedule().getCertifier().getId()
                            .trim().length() > 0) {
                List<Identity> identities = scoreListBean.getCategory()
                        .getObjects();

                if (identities != null) {
                    List<String> identityIds = new ArrayList<String>();
                    for (Identity identity : identities) {
                        identityIds.add(identity.getId());
                    }

                    getEditedCertificationSchedule().setIdentitiesToCertify(
                            identityIds);
                    scheduleIndividualCertification();
                }
            }

        } catch (GeneralException ge) {
            log.error("GeneralException encountered: [" + ge.getMessage() + "]");
            errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            return null;
        }
        return "showScores";
    }

    public String scheduleBulkSearchCertification() throws GeneralException
    {
        String returnStr = null;

        String certifier = getEditedCertificationSchedule().getDefinition()
                .getCertifierName();
        if (certifier != null && !certifier.equals("")) {
            returnStr = scheduleIndividualCertification();
        }

        return returnStr;
    }

    private String scheduleApplicationCertification() throws GeneralException
    {
        CertificationScheduleDTO dto = getEditedCertificationSchedule();
        CertificationSchedule schedule = dto.getSchedule();
        try {
            // Copy the app owner specific setting for whether to include
            // policy violations so this will be used when setting common
            // options.
            if (schedule.isGlobalCertification()) {
                String name = getCertificationName().getLocalizedMessage();
                schedule.setName(name);

                infoMessage(MessageKeys.GBL_APP_CERT_SCHEDULED);
                save(schedule);
            } else {
                // validate that at least one application was entered
                if (schedule.getApplicationCount() < 1) {
                    addRequiredErrorMessage("certifyAppNameSuggest",
                            MessageKeys.ERR_APP_EMPTY);
                    return null;
                }

                infoMessage(MessageKeys.APP_CERT_SCHEDULED);
                save(schedule);
            }
        } catch (GeneralException ge) {
            log.error("GeneralException encountered: [" + ge.getMessage() + "]");
            addMessage(ge);
            return null;
        }

        cleanSession();
        return "scheduleCertificationSuccess";
    }

    private String scheduleDataOwnerCertification()
    {
        CertificationScheduleDTO dto = getEditedCertificationSchedule();
        try {
            String name = getCertificationName().getLocalizedMessage();
            CertificationSchedule schedule = dto.getSchedule();
            // todo jfb
            schedule.setName(name);

            if (schedule.isGlobalCertification()) {
                infoMessage(MessageKeys.CERT_SCHEDULED, schedule
                        .getDefinition().getTypeScheduleDescription());
            } else {
                // validate that at least one application was entered
                if (schedule.getApplicationCount() < 1) {
                    addRequiredErrorMessage("certifyAppNameSuggest",
                            MessageKeys.ERR_APP_EMPTY);
                    return null;
                }

                infoMessage(MessageKeys.DATA_OWNER_CERT_SCHEDULED);
            }

            save(schedule);

        } catch (GeneralException ge) {
            log.error("GeneralException encountered: [" + ge.getMessage() + "]");
            addMessage(ge);
            return null;
        }
        cleanSession();
        return "scheduleCertificationSuccess";
    }

    /**
     * Schedule a acount group certification. The specific type of account group
     * certification is determined by the type property of the edited
     * CertificationScheduleDTO.
     * 
     * @return JSF navigation key, null if an error occurred.
     */
    private String scheduleAccountGroupCertification() throws GeneralException
    {

        CertificationScheduleDTO dto = getEditedCertificationSchedule();
        CertificationSchedule schedule = dto.getSchedule();

        try {

            String name = getCertificationName().getLocalizedMessage();
            schedule.setName(name);

            if (schedule.isGlobalCertification()) {
                save(schedule);
                infoMessage(MessageKeys.CERT_SCHEDULED, schedule
                        .getDefinition().getTypeScheduleDescription());
            } else {
                // validate that at least one application was entered
                if (schedule.getApplicationCount() < 1) {
                    addRequiredErrorMessage("certifyAppNameSuggest",
                            MessageKeys.ERR_APP_EMPTY);
                    return null;
                }

                infoMessage(MessageKeys.APP_CERT_SCHEDULED);
                save(schedule);
            }
        } catch (GeneralException ge) {
            log.error("GeneralException encountered: [" + ge.getMessage() + "]");

            if (ge.getMessageInstance() == null)
                errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            else
                addMessage(ge.getMessageInstance(), null);
            return null;
        }

        cleanSession();
        return "scheduleCertificationSuccess";
    }

    private String scheduleBusinessRoleCertification() throws GeneralException
    {
        CertificationScheduleDTO dto = getEditedCertificationSchedule();
        CertificationSchedule schedule = dto.getSchedule();
        try {
            String name = getCertificationName().getLocalizedMessage();
            schedule.setName(name);

            infoMessage(MessageKeys.BIZ_ROLE_CERT_SCHEDULED);
            save(schedule);

        } catch (GeneralException ge) {
            log.error("GeneralException encountered: [" + ge.getMessage() + "]");
            errorMessage(MessageKeys.ERR_FATAL_SYSTEM);

            return null;
        }

        cleanSession();
        return "scheduleCertificationSuccess";
    }

    private void save(CertificationSchedule schedule) throws GeneralException
    {

        CertificationScheduler scheduler = new CertificationScheduler(
                getContext());
        schedule.setRunNow(runNow);
        scheduler.saveSchedule(schedule, this.continuousHasLaunched);
    }

    public String cancelCertification()
    {
        cleanSession();

        return goBack();
    }


    public String goBack() {
        String result = NavigationHistory.getInstance().back();

        if (result == null) {
            result = "backToCertSchedules";
        }

        return result;
    }


    // //////////////////////////////////////////////////////////////////////////
    //
    // Validation
    //
    // //////////////////////////////////////////////////////////////////////////

    /**
     * Wraps the call to the different validation methods so we don't need a
     * separate button for each certification type on the form.
     * 
     * @return Jsf navigation msg
     */
    public String validateFields() throws GeneralException
    {
        boolean valid = true;

        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();
        if (certSchedule.isBusinessRoleCompositionCertification()) {
            valid &= validateBusinessRoleFields();
        } else if (certSchedule.isBusinessRoleMembershipCertification()) {
            valid &= validateBusinessRoleFields();
        } else if (certSchedule.isIdentityCertification()) {
            valid &= validateIdentityFields();
        } else if (certSchedule.isAdvancedCertification()) {
            valid &= validateAdvancedFields();
        } else if (certSchedule.isApplicationOwnerCertification()) {
            valid &= validateApplicationFields();
        } else if (certSchedule.isManagerCertification()) {
            valid &= validateManagerFields();
        } else if (certSchedule.isAccountGroupCertification()) {
            valid &= this.validateAccountGroupFields();
        } else if (certSchedule.isDataOwnerCertification()) {
            valid &= validateDataOwnerFields();
        } else {
            throw new RuntimeException(
                    "Could not validate unknown certification type " + "'"
                            + certSchedule.getCertificationType() + "'");
        }

        // Validate trigger fields if this is a trigger.
        if (isIdentityTrigger()) {
            valid &= this.trigger.validate();
        }

        valid &= validateCommonFields();

        return null;
    }

    private boolean validateDataOwnerFields()
    {

        if (!validateApplicationFields()) {
            return false;
        }

        CertificationScheduleDTO schedule = getEditedCertificationSchedule();
        if (!schedule.isIncludeUnownedData()) {
            return true;
        }
        if (schedule.isAppOwnerIsUnownedOwner()) {
            return true;
        }
        if (schedule.getDefinition().getUnownedDataOwner() == null) {
            addRequiredErrorMessage("certificationUnownedAppOwnerSuggestInput",
                    MessageKeys.ERR_DATA_OWNER_MUST_SELECT_UNOWNED);
            return false;
        }

        return true;
    }

    private boolean validateManagerFields()
    {

        boolean fieldsAreValid = true;
        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();

        // validate that at least one manager name was entered
        if ((null == certSchedule.getDefinition().getCertifierName())
                && !certSchedule.isGlobalCertification()) {
            addRequiredErrorMessage("certifyRecipient",
                    MessageKeys.ERR_MGR_EMPTY);
            fieldsAreValid = false;
        }

        return fieldsAreValid;
    }

    private boolean validateApplicationFields()
    {

        boolean fieldsAreValid = true;
        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();
        // Validate that at least one application was selected
        if (!certSchedule.isGlobalCertification()
                && (certSchedule.getApplicationIds() == null || certSchedule
                        .getApplicationIds().isEmpty())) {
            addRequiredErrorMessage("certifyAppNameSelect",
                    MessageKeys.ERR_APP_CERT_APP_EMPTY);
            fieldsAreValid = false;
        }

        return fieldsAreValid;
    }

    private boolean validateBusinessRoleFields()
    {

        boolean fieldsAreValid = true;
        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();

        // Validate that at least one role was selected
        if (!certSchedule.isGlobalCertification()
                && (certSchedule.getRoleTypes() == null || certSchedule
                        .getRoleTypes().isEmpty())
                && (certSchedule.getBusinessRoleIds() == null || certSchedule
                        .getBusinessRoleIds().isEmpty())) {
            addRequiredErrorMessage("certifyBusinessRoleSelect",
                    MessageKeys.CERT_SCHED_ERR_ROLE_REQUIRED);
            fieldsAreValid = false;
        }

        // if they chose to select certifier manually, require at least on
        // certifier
        if (CertificationDefinition.CertifierSelectionType.Manual
                .equals(certSchedule.getCertifierSelectionType())
                && (certSchedule.getOwnerIds() == null || certSchedule
                        .getOwnerIds().isEmpty())) {
            addRequiredErrorMessage("certifyAppOwners",
                    MessageKeys.CERT_SCHED_ERR_CERTIFIER_REQUIRED);
            fieldsAreValid = false;
        }

        return fieldsAreValid;
    }

    private boolean validateAdvancedFields() throws GeneralException
    {
        CertificationSchedule certSchedule = getEditedCertificationSchedule()
                .getSchedule();

        boolean fieldsAreValid = true;

        // At least one group or factory is required.
        List<GroupBean> groups = certSchedule.getDefinition().getGroupBeans(
                getContext());
        List<FactoryBean> factories = certSchedule.getDefinition()
                .getFactoryBeans(getContext());
        if ((groups == null || groups.isEmpty())
                && (factories == null || factories.isEmpty())) {
            addRequiredErrorMessage("ipopsTable",
                    MessageKeys.ERR_POP_OR_FACT_EMPTY);
            fieldsAreValid = false;
        }

        // Each group must have at least one certifier.
        if (groups != null) {
            for (GroupBean group : groups) {
                if ((null == group.getCertifiers())
                        || group.getCertifiers().isEmpty()) {
                    addRequiredErrorMessage("ipopsTable",
                            MessageKeys.ERR_SELECT_CERTIFIER_FOR_EACH_POP);
                    fieldsAreValid = false;
                }
            }
        }

        // Each factory must have a certifier rule
        if (factories != null) {
            for (FactoryBean f : factories) {
                String rid = f.getCertifierRuleName();
                if (rid == null || rid.length() == 0) {
                    addRequiredErrorMessage(
                            "factoriesTable",
                            MessageKeys.ERR_SELECT_CERTIFIER_FOR_EACH_RULE_OR_FACT);
                    fieldsAreValid = false;
                }
            }
        }

        return fieldsAreValid;
    }

    private boolean validateCommonFields() throws GeneralException
    {
        boolean fieldsAreValid = true;
        CertificationSchedule certSchedule = getEditedCertificationSchedule()
                .getSchedule();

        // Only check the date if we're not running now and this isn't a
        // trigger.
        if (!runNow && !isIdentityTrigger()
                && certSchedule.getFirstExecution() != null
                && Util.isDateAfterToday(certSchedule.getFirstExecution())
                && !isExistingCertGroup()) {
            addRequiredErrorMessage("certifyExecution",
                    MessageKeys.ERR_DATE_PAST);
            fieldsAreValid = false;
        }

        if (certSchedule.getDefinition().getCertificationNameTemplate() == null
                || "".equals(certSchedule.getDefinition()
                        .getCertificationNameTemplate())) {
            addRequiredErrorMessage("certificationTemplateName",
                    MessageKeys.CERT_SCHED_ERR_CERT_NAME_EMPTY);
            fieldsAreValid = false;
        }

        if (certSchedule.getDefinition().getCertificationOwner() == null) {
            addRequiredErrorMessage("certificationOwnerSuggestInput",
                    MessageKeys.CERT_SCHED_ERR_CERT_OWNER_EMPTY);
            fieldsAreValid = false;
        }

        if (!isIdentityTrigger()) {

            try {
                // I need to know if there is an existing schedule with this
                // name, and not with this id.
                QueryOptions ops = null;
                if (null != certSchedule.getTaskId()) {
                    ops = new QueryOptions(Filter.and(Filter.eq("name",
                            certSchedule.getName())), Filter.ne("id",
                            certSchedule.getTaskId()));
                } else {
                    ops = new QueryOptions(Filter.eq("name",
                            certSchedule.getName()));
                }
                if (getContext().countObjects(TaskSchedule.class, ops) > 0) {
                    addRequiredErrorMessage("certifyName",
                            MessageKeys.ERROR_TASK_NAME_EXISTS);
                    fieldsAreValid = false;
                }
            } catch (GeneralException ge) {
                log.error("GeneralException encountered while trying to find existing schedule: ["
                        + ge.getMessage() + "]");
                errorMessage(MessageKeys.ERR_FATAL_SYSTEM);

                fieldsAreValid = false;
                return fieldsAreValid;
            }

        } else {
            fieldsAreValid = isIdentityTriggerNameUnique(trigger);
        }

        if (!certSchedule.getDefinition().isContinuous()) {
            // Require an active period duration.
            if (certSchedule.getDefinition().getActivePeriodDurationAmount() == null
                    || certSchedule.getDefinition()
                            .getActivePeriodDurationAmount() < 1) {
                addRequiredErrorMessage("activeDuration",
                        MessageKeys.ERR_INVALID_ACTIVE_PERIOD_DURATION);
                fieldsAreValid = false;
            }

            if (null == certSchedule.getDefinition()
                    .getActivePeriodDurationScale()) {
                addRequiredErrorMessage("activeDuration",
                        MessageKeys.ERR_ACTIVE_DURATION_SCALE_EMPTY);
                fieldsAreValid = false;
            }

            //TODO: !!!tqm Add Validation
            // for certificaiton_notifications
            
            if (certSchedule.getDefinition().isChallengePeriodEnabled()) {
                if (certSchedule.getDefinition().getChallengePeriodDurationAmount() == null
                        || certSchedule.getDefinition()
                                .getChallengePeriodDurationAmount() < 1) {
                    addRequiredErrorMessage("challengeDuration",
                            MessageKeys.ERR_INVALID_CHALLENGE_PERIOD_DURATION);
                    fieldsAreValid = false;
                }

                if (null == certSchedule.getDefinition()
                        .getChallengePeriodDurationScale()) {
                    addRequiredErrorMessage("challengeDuration",
                            MessageKeys.ERR_CHALLENGE_DURATION_SCALE_EMPTY);
                    fieldsAreValid = false;
                }
            }
            
            if (certSchedule.getDefinition().isRemediationPeriodEnabled()) {
                if (certSchedule.getDefinition().getRemediationPeriodDurationAmount() == null
                        || certSchedule.getDefinition()
                                .getRemediationPeriodDurationAmount() < 1) {
                    addRequiredErrorMessage("remediationDuration",
                            MessageKeys.ERR_INVALID_REMEDIATION_PERIOD_DURATION);
                    fieldsAreValid = false;
                }

                if (null == certSchedule.getDefinition()
                        .getRemediationPeriodDurationScale()) {
                    addRequiredErrorMessage("remediationDuration",
                            MessageKeys.ERR_REMEDIATION_DURATION_SCALE_EMPTY);
                    fieldsAreValid = false;
                }
            }
            
            if (Util.otob(certSchedule.getDefinition().isElectronicSignatureRequired()) &&
            		StringUtils.isBlank(certSchedule.getDefinition().getElectronicSignatureName())) {
            	addRequiredErrorMessage("electronicSignatureName", MessageKeys.ERR_INVALID_ELECTRONIC_SIGNATURE);
            	fieldsAreValid = false;
            }
            
        } else {
            // Require certified and certification required durations.
            if (certSchedule.getDefinition().getCertifiedDurationAmount() < 1) {
                addRequiredErrorMessage("certifiedDuration",
                        MessageKeys.ERR_INVALID_CERTIFIED_DURATION);
                fieldsAreValid = false;
            }
            if (null == certSchedule.getDefinition()
                    .getCertifiedDurationScale()) {
                addRequiredErrorMessage("certifiedDurationScale",
                        MessageKeys.ERR_CERTIFIED_DURATION_SCALE_EMPTY);
                fieldsAreValid = false;
            }

            if (certSchedule.getDefinition()
                    .getCertificationRequiredDurationAmount() < 1) {
                addRequiredErrorMessage("certificationRequiredDuration",
                        MessageKeys.ERR_INVALID_CERT_REQ_DURATION);
                fieldsAreValid = false;
            }
            if (null == certSchedule.getDefinition()
                    .getCertificationRequiredDurationScale()) {
                addRequiredErrorMessage("certificationRequiredDurationScale",
                        MessageKeys.ERR_CERT_REQ_DURATION_SCALE_EMPTY);
                fieldsAreValid = false;
            }

            if (!validateNotification(getEditedCertificationSchedule().getCertificationRequiredNotificationConfig(), "certRequired", false)) {
                fieldsAreValid = false;
            }
            if (!validateNotification(getEditedCertificationSchedule().getOverdueNotificationConfig(), "overdue", false)) {
                fieldsAreValid = false;
            }
        }

        if (!validateNotification(getEditedCertificationSchedule().getRemediationNotificationConfig(), "remed", true)) {
            fieldsAreValid = false;
        }

        if (!validateRemindersAndEscalations()) {
            fieldsAreValid = false;
        }

        if (getEditedCertificationSchedule().isLimitReassignments()) {
            if (getEditedCertificationSchedule().getReassignmentLimit() == null) {
                addRequiredErrorMessage("reassignmentLimit", MessageKeys.SYS_CONFIG_ERR_REASSIGNMENT_LIMIT_EMPTY);
                fieldsAreValid = false;
            } else if (Util.otoi(getEditedCertificationSchedule().getReassignmentLimit()) < 1) {
                addMessage(new Message(Message.Type.Error, MessageKeys.SYS_CONFIG_ERR_REASSIGNMENT_LIMIT_NOT_VALID), null);
                fieldsAreValid = false;
            }

        }

        return fieldsAreValid;
    }

    // TODO: Do server side validation here
    private boolean validateRemindersAndEscalations()
    {

        return true;
    }

    private boolean isIdentityTriggerNameUnique(IdentityTriggerDTO idTrigger)
    {
        boolean response = true;
        /*
         * Build a query that finds if there is another existing IdentityTrigger
         * with the same name
         */
        Filter filter = Filter.eq("name", idTrigger.getName());
        filter = Filter.and(
                filter,
                Filter.eq("handler",
                        CertificationTriggerHandler.class.getName()));
        if (idTrigger.getPersistentId() != null) {
            filter = Filter.and(filter,
                    Filter.ne("id", idTrigger.getPersistentId()));
        }
        QueryOptions ops = new QueryOptions(filter);
        ops.setIgnoreCase(true);
        try {
            if (getContext().countObjects(IdentityTrigger.class, ops) > 0) {
                addRequiredErrorMessage("certifyName",
                        MessageKeys.ERROR_TASK_NAME_EXISTS);
                response = false;
            }
        } catch (GeneralException e) {
            log.error("GeneralException encountered while trying to find existing schedule: ["
                    + e.getMessage() + "]");
            errorMessage(MessageKeys.ERR_FATAL_SYSTEM);
            response = false;
        }

        return response;
    }

    private boolean validateNotification(CertificationDefinition.NotificationConfig notif, String prefix, boolean requireTrigger)
            throws GeneralException {

        boolean fieldsAreValid = true;
        if (notif == null) {
            return true;
        }

        CertificationDefinition.NotificationConfig.ReminderConfig reminderConfig = (CertificationDefinition.NotificationConfig.ReminderConfig) notif.getConfigs().get(0);
        CertificationDefinition.NotificationConfig.EscalationConfig escalationConfig = (CertificationDefinition.NotificationConfig.EscalationConfig) notif.getConfigs().get(1);

        if (escalationConfig.isEnabled()) {
            if (requireTrigger) {
                if (reminderConfig.isEnabled()) {
                    if (escalationConfig.getMaxReminders() < 0) {
                        addRequiredErrorMessage(prefix + "MaxReminders",
                                                new Message(Message.Type.Error, MessageKeys.ERR_NUMBER_LESS_THAN_ZERO,
                                                        escalationConfig.getMaxReminders()));
                                        fieldsAreValid = false;
                    }
                }
                else {
                    if ( escalationConfig.getStartHowManyDays() < 0) {
                        addRequiredErrorMessage(prefix + "EscalateDaysBeforeEnd",
                                                new Message(Message.Type.Error, MessageKeys.ERR_NUMBER_LESS_THAN_ZERO,
                                                        escalationConfig.getStartHowManyDays() < 0));
                                        fieldsAreValid = false;
                    }
                }
            }

            if (Util.getString( escalationConfig.getEscalationRuleId()) == null) {
                addRequiredErrorMessage(prefix + "EscalationRules",
                    MessageKeys.ERR_EMPTY_ESCALATION_RULE);
                fieldsAreValid = false;
            }
        }

        if (reminderConfig.isEnabled()) {
            int start = reminderConfig.getStartHowManyDays();

            if (requireTrigger && (start < 1)) {
                addRequiredErrorMessage(prefix + "ReminderStart",
                        new Message(Message.Type.Error, MessageKeys.ERR_NUMBER_LESS_THAN_ZERO,
                                reminderConfig.getStartHowManyDays()));
                fieldsAreValid = false;
            }

            if (validateReminderFrequency(notif, prefix, requireTrigger) == false) {
                fieldsAreValid = false;
            }

            if (Util.getString(reminderConfig.getEmailTemplateId()) == null) {
                addRequiredErrorMessage(prefix + "EmailTemplates",
                    MessageKeys.ERR_EMPTY_EMAIL_TEMPLATE);
                fieldsAreValid = false;
            }
        }

        return fieldsAreValid;
    }
    
    private boolean validateReminderFrequency(CertificationDefinition.NotificationConfig notif, String prefix, boolean requireTrigger) throws GeneralException {

        CertificationDefinition.NotificationConfig.ReminderConfig reminderConfig = (CertificationDefinition.NotificationConfig.ReminderConfig) notif.getConfigs().get(0);
        if (reminderConfig.isOnce()) {
            return true;
        }
            
        int freqDays = reminderConfig.getOnceEveryHowManyDays();
        if (freqDays < 1) {
            addErrorMessage(prefix + "ReminderFrequencyDays",
                    new Message(Message.Type.Error, MessageKeys.ERR_NUMBER_LESS_THAN_ZERO, reminderConfig.getOnceEveryHowManyDays()),
                    new Message(Message.Type.Error, MessageKeys.ERR_VALIDATION));
            return false;
        }
        return true;
    }

    private boolean validateIdentityFields() throws GeneralException
    {
        boolean valid = true;
        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();
        List<String> certifierNames = null;

        // Require a certifier selection type.
        if (null == certSchedule.getCertifierSelectionType()) {
            addRequiredErrorMessage("triggerRuleSelect",
                    MessageKeys.ERR_CERT_DEF_CERTIFIER_SELECTION_TYPE_REQUIRED);
            valid = false;
        }

        // Require a certifier if "Manager" type is selected.
        if (CertifierSelectionType.Manager.equals(certSchedule
                .getCertifierSelectionType())) {
            if (null == certSchedule.getDefinition().getCertifierName()) {
                addRequiredErrorMessage("defaultCertifierInput",
                        MessageKeys.ERR_CERT_DEF_DEFAULT_CERTIFIER_REQUIRED);
                valid = false;
            } else {
                // bug 26101 - allowing workgroups to be the default certifier for
                // identity certifications where the identity does not have a manager
                Identity identity = ObjectUtil.getIdentityOrWorkgroup(getContext(), certSchedule.getDefinition().getCertifierName());
                String certifierName = identity != null ? identity.getName() : null;
                if (Util.isNullOrEmpty(certifierName)) {
                    addRequiredErrorMessage("defaultCertifierInput",
                            MessageKeys.ERR_CERT_DEF_DEFAULT_CERTIFIER_REQUIRED);
                    valid = false;
                } else {
                    certifierNames = Util.asList(certifierName);
                }
            }
        }

        // Require at least one certifier if "Manual" type is selected.
        List<String> ownerNames = certSchedule.getOwnerNames();
        if (CertifierSelectionType.Manual.equals(certSchedule
                .getCertifierSelectionType())) {

            if ((null == ownerNames) || ownerNames.isEmpty()) {
                addRequiredErrorMessage("certifiersMultiSuggest",
                        MessageKeys.ERR_CERT_DEF_MANUAL_CERTIFIERS_REQUIRED);
                valid = false;
            } else {
                certifierNames = ownerNames;
            }
        }

        // Make sure selected identifier is not in the list of identities to certify, if they
        // are blocked from self-cert
        List<String> identitiesToCertify = getEditedCertificationSchedule().getIdentitiesToCertify();
        // Check if there are identities to certify if this is not a trigger
        if (!isIdentityTrigger() && Util.isEmpty(identitiesToCertify)) {
            addRequiredErrorMessage("identities-display", MessageKeys.CERTIFICATION_HAS_NO_IDENTITIES);
            valid = false;
        }
        for (String ownerName : Util.safeIterable(certifierNames)) {
            if (identitiesToCertify.contains(ownerName)) {
                Identity owner = getContext().getObjectByName(Identity.class, ownerName);
                Certification.SelfCertificationAllowedLevel allowedLevel = getEditedCertificationSchedule().getSelfCertificationAllowedLevel();
                if (owner != null && !SelfCertificationChecker.isSelfCertifyAllowed(owner, allowedLevel)) {
                    addRequiredErrorMessage("defaultCertifierInput",
                            MessageKeys.ERR_CERT_DEF_CERTIFIERS_SELF_CERT);
                    valid = false;
                    break;
                }
            }
        }
        
        return valid;
    }


    private boolean validateAccountGroupFields()
    {

        boolean fieldsAreValid = true;
        CertificationScheduleDTO certSchedule = getEditedCertificationSchedule();
        // Validate that at least one application was selected
        if (!certSchedule.isGlobalCertification()
                && (certSchedule.getApplicationIds() == null || certSchedule
                        .getApplicationIds().isEmpty())) {
            // there are two possible component id's for showing the apps
            // depending on how many apps there are, so add the message to
            // both since only one will be displayed
            addRequiredErrorMessage("certifyAppNameSelect",
                    MessageKeys.ERR_ACT_GRP_CERT_APP_EMPTY);
            fieldsAreValid = false;
        }

        return fieldsAreValid;
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    // //////////////////////////////////////////////////////////////////////////

    /**
     * @return the identityIds
     */
    public List<String> getIdentityIds()
    {
        return identityIds;
    }

    /**
     * @param identityIds
     *            the identityIds to set
     */
    public void setIdentityIds(List<String> identityIds)
    {
        this.identityIds = identityIds;
    }

    /**
     * @return the identityManagers
     */
    public Map<String, String> getIdentityManagers()
    {
        return identityManagers;
    }

    /**
     * @param identityManagers
     *            the identityManagers to set
     */
    public void setIdentityManagers(Map<String, String> identityManagers)
    {
        this.identityManagers = identityManagers;
    }

    /**
     * @return the showSchedule
     */
    public boolean isShowSchedule()
    {
        return showSchedule;
    }

    /**
     * @param showSchedule
     *            the showSchedule to set
     */
    public void setShowSchedule(boolean showSchedule)
    {
        this.showSchedule = showSchedule;
    }

    /**
     * @return the showScheduleAll
     */
    public boolean isShowScheduleAll()
    {
        return showScheduleAll;
    }

    /**
     * @param showScheduleAll
     *            the showScheduleAll to set
     */
    public void setShowScheduleAll(boolean showScheduleAll)
    {
        this.showScheduleAll = showScheduleAll;
    }

    /**
     * Return whether this schedule supports the "exclude inactive" option.
     */
    public boolean getSupportsExcludeInactive()
    {
        // For now, only identity-based certifications support excluding
        // inactive.
        return getCertificationType()
                .isIdentity();
    }

    public boolean getSupportsSelfCertification() {
        Certification.Type certType = getCertificationType();
        return CertificationEntity.Type.Identity.equals(certType.getEntityType()) ||
                Certification.Type.AccountGroupMembership.equals(certType) ||
                Certification.Type.DataOwner.equals(certType);
    }

    public List<SelectItem> getSelfCertificationAllowedLevels()  {
        List<SelectItem> levels = new ArrayList<>();

        for (Certification.SelfCertificationAllowedLevel level : Certification.SelfCertificationAllowedLevel.values()) {
            levels.add(new SelectItem(level, getMessage(level.getMessageKey())));
        }

        return levels;
    }


    public boolean getSupportsSignOffApprover()
    {
        // For now, only identity-based certifications support excluding
        // inactive.
        // Entitlement owner also does this.
        Certification.Type certType = getCertificationType();
        return (certType.isIdentity() || certType.equals(Certification.Type.DataOwner) || certType.equals(Certification.Type.BusinessRoleComposition));
    }

    public boolean getSupportsRecommendations() {
        // IdentityAI only supports certifications with identities as its entities.
        return getCertificationType().supportsRecommendations();
    }

    public boolean getRecommendationsGenerated() {
        return editedCertificationSchedule.getDefinition().getRecommendationsGenerated();
    }

    /**
     * Return whether or not the given certification type supports the option of
     * allowing the certifier to provision missing required roles. available
     * only in certifications that include business roles - role membership
     * certifications and identity certifications.
     * 
     * @return
     */
    public boolean isSupportsProvisioningRequirementsCheckbox()
    {
        Certification.Type certType = getCertificationType();
        return certType.isType(Certification.Type.BusinessRoleMembership)
                || certType.isIdentity();
    }

    /**
     * Returns true if the user may select to exclude the base app accounts
     * which underly a composite account from the certification. This option is
     * only valid for identity certs which cover multiple apps.
     * 
     * @return
     */
    public boolean isSupportsExcludeBaseAppAccountsCheckbox()
    {
        Certification.Type type = getCertificationType();
        return Certification.Type.Manager.equals(type)
                || Certification.Type.Identity.equals(type)
                || Certification.Type.Group.equals(type);
    }

    /**
     * Returns true if the user may select to not update the entitlement
     * trail as this certication goes through its lifecycle.
     * 
     * @return
     */
    public boolean isShowIdentityEntitlementUpdateCheckbox() 
        throws GeneralException   {

        Configuration configuration = getContext().getConfiguration();
        if ( configuration != null ) {
            if ( configuration.getBoolean(Configuration.CERT_SCHEDULE_SHOW_ENTITLEMENT_UPDATE_CHECKBOX) ) {
                Certification.Type type = 
                     getCertificationType();
                return Certification.Type.Manager.equals(type)
                        || Certification.Type.Identity.equals(type)
                        || Certification.Type.ApplicationOwner.equals(type);
            }
        }
        return false;
    }

    private Type getCertificationType() {

        return getEditedCertificationSchedule().getCertificationType();
    }
    
    /**
     * Returns true if the user may select to not update the entitlement
     * trail as this certication goes through its lifecycle.
     * 
     * This is only interestig on Identity type certifications.
     * 
     * @return true if you need to show the assignement checkbox
     */
    public boolean isShowEntitlementAssignmentCheckbox() 
        throws GeneralException   {

        Certification.Type type = 
            getCertificationType();
        return Certification.Type.Manager.equals(type)
               || Certification.Type.Identity.equals(type)
               || Certification.Type.DataOwner.equals(type)
               || Certification.Type.ApplicationOwner.equals(type);
    }

    /**
     * Indicates whether to give users the option to change the default
     * entitlement display mode. This is only relevant in identity certs.
     * 
     * @return
     */
    public boolean isShowDisplayEntitlementDescriptions()
    {
        Certification.Type type = getCertificationType();
        return !Certification.Type.BusinessRoleComposition.equals(type);
    }

    /**
     * Indicates where we should show the 'Approve Account'
     * option for certifications.
     * 
     */
    public boolean isEnableApproveAccount() {
        return Certification.Type.DataOwner != getCertificationType();
    }
    
    /**
     * Indicates where we should show the 'Revoke Account'
     * option for certifications.
     * 
     */
    public boolean isEnableRevokeAccount() {
        return Certification.Type.DataOwner != getCertificationType();
    }

    /**
     * Indicates where we should show the 'Reassign Account'
     * option for certifications.
     *
     */
    public boolean isEnableReassignAccount() {
        return Certification.Type.DataOwner != getCertificationType();
    }

    /**
     * @return true if the schedule will run right away; false otherwise
     */
    public boolean isRunNow()
    {
        return runNow;
    }

    /**
     * @param runNow
     *            true to run the schedule right away; false otherwise
     */
    public void setRunNow(final boolean runNow)
    	throws GeneralException
    {
        this.runNow = runNow;        
        this.editedCertificationSchedule.getSchedule().setRunNow(runNow);
    }

    public boolean getContinuousHasLaunched()
    {
        return this.continuousHasLaunched;
    }

    public void setContinuousHasLaunched(boolean b)
    {
        this.continuousHasLaunched = b;
    }

    /**
     * Get the ID of the GroupDefinition to be added as a certification
     * population source.
     */
    public String getGroupToAddId()
    {
        return this.groupToAddId;
    }

    public void setGroupToAddId(String selectedGroupId)
    {
        this.groupToAddId = selectedGroupId;
    }

    public String getFactoryToAddId()
    {
        return this.factoryToAddId;
    }

    public void setFactoryToAddId(String selectedFactoryId)
    {
        this.factoryToAddId = selectedFactoryId;
    }

    /**
     * Get the ID of the group to which to add a certifier or from which to
     * remove a certifier.
     */
    public String getSelectedGroupId()
    {
        return this.selectedGroupId;
    }

    public void setSelectedGroupId(String selectedGroupId)
    {
        this.selectedGroupId = selectedGroupId;
    }

    public Identity getIdentityToAdd()
    {
        return identityToAdd;
    }

    public void setIdentityToAdd(Identity identityToAdd)
    {
        this.identityToAdd = identityToAdd;
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    // //////////////////////////////////////////////////////////////////////////

    public boolean isReadOnly()
    {
        return readOnly;
    }

    public String getCertificationGroupId()
    {
        return certificationGroupId;
    }

    public boolean isExistingCertGroup()
    {
        return certificationGroupId != null;
    }

    public List<SelectItem> getEmailTemplates()
    {
        ArrayList<SelectItem> templateNames = new ArrayList<SelectItem>();

        try {
            // Query the database to get available reminder e-mail templates
            QueryOptions qo = new QueryOptions();
            qo.addOrdering("name", true);
            List<EmailTemplate> templates = getContext().getObjects(
                    EmailTemplate.class, qo);

            for (EmailTemplate template : templates) {
                templateNames.add(new SelectItem(template.getName()));
            }
        } catch (GeneralException e) {
            log.error(e);
        }

        return templateNames;
    }
    
    public List<SelectItem> getEmailTemplatesNew() {
        ArrayList<SelectItem> templateItems = new ArrayList<SelectItem>();

        try {
            // Query the database to get available reminder e-mail templates
            QueryOptions qo = new QueryOptions();
            qo.addOrdering("name", true);
            List<EmailTemplate> templates =
                getContext().getObjects(EmailTemplate.class, qo);

            for (EmailTemplate template : templates) {
                templateItems.add(new SelectItem(template.getId(), template.getName()));
            }
        } catch (GeneralException e) {
            log.error(e);
        }

        return templateItems;
    }


    /**
     * Populates the auto close action drop down menu with the possible choices,
     * based on the system configuration.
     * 
     * @return List of SelectItems for close action menu
     */
    public List<SelectItem> getAutomaticClosingActions()
    {
        ArrayList<SelectItem> actions = new ArrayList<SelectItem>();

        // only three of the actions are valid for auto-closing
        actions.add(new SelectItem(CertificationAction.Status.Approved,
                getMessage(MessageKeys.CERT_ACTION_LEGEND_APPROVE)));
        actions.add(new SelectItem(CertificationAction.Status.Remediated,
                getMessage(MessageKeys.CERT_ACTION_LEGEND_REVOKE)));
        actions.add(new SelectItem(CertificationAction.Status.Mitigated,
                getMessage(MessageKeys.CERT_ACTION_LEGEND_ALLOW_EXCEPTION)));

        return actions;
    }

    public List<SelectItem> getEscalationRules() throws GeneralException
    {
        if (escalationRules == null) {
            escalationRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.Escalation, true);
        }
        return escalationRules;
    }

    public List<SelectItem> getExclusionRules() throws GeneralException
    {
        if (exclusionRules == null) {
            exclusionRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.CertificationExclusion, true);
        }
        return exclusionRules;
    }

    public List<SelectItem> getAutomaticClosingRules() throws GeneralException
    {
        if (autoClosingRules == null) {
            autoClosingRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.CertificationAutomaticClosing, true);
        }
        return autoClosingRules;
    }

    public List<SelectItem> getPhaseChangeRules() throws GeneralException
    {
        if (phaseChangeRules == null) {
            phaseChangeRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.CertificationPhaseChange, true);
        }
        return phaseChangeRules;
    }

    public List<SelectItem> getPreDelegationRules() throws GeneralException
    {
        if (predelegationRules == null) {
            predelegationRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.CertificationPreDelegation, true);
        }
        return predelegationRules;
    }

    public List<SelectItem> getApproverRules() throws GeneralException
    {
        if (approverRules == null) {
            approverRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.CertificationSignOffApprover, true);
        }
        return approverRules;
    }

    /**
     * List of rules suitable for selecting certifiers, currently used only when
     * scheduling advanced certs on group factories.
     */
    public List<SelectItem> getCertifierRules() throws GeneralException
    {
        if (certifierRules == null) {
            certifierRules = WebUtil.getRulesByType(getContext(),
                    Rule.Type.Certifier, true);
        }
        return certifierRules;
    }

    /**
     * Return select items for parameters that can be inserted into the name
     * template and short name template.
     */
    public List<SelectItem> getAccessReviewNameParameters()
            throws GeneralException
    {

        if (null == this.accessReviewNameParameters) {
            this.accessReviewNameParameters = new ArrayList<SelectItem>();

            // Common options.
            this.accessReviewNameParameters.add(new SelectItem("",
                    getMessage(MessageKeys.NAME_TEMPLATE_SELECT_A_PARAMETER)));
            this.accessReviewNameParameters.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_TYPE + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_TYPE)));
            this.accessReviewNameParameters.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_FULL_DATE + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_FULL_DATE)));
            this.accessReviewNameParameters.add(new SelectItem("$!{"
                    + CertificationNamer.NAME_TEMPLATE_CERTIFIER_PREFIX
                    + CertificationNamer.NAME_TEMPLATE_ID_FULL_NAME_SUFFIX
                    + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_CERTIFIER_FULL_NAME)));

            this.accessReviewNameParameters.addAll(getTypeParameters());
        }

        return this.accessReviewNameParameters;
    }

    private List<SelectItem> getTypeParameters()
    {

        List<SelectItem> items = new ArrayList<SelectItem>();

        // Type-specific options. This is pretty ugly ... we should think
        // about asking the CertificationContext or the Certification.Type
        // to provide us with the name template parameters.
        Certification.Type type = this.editedCertificationSchedule
                .getCertificationType();
        switch (type) {
        case Manager:
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_MANAGER_PREFIX
                    + CertificationNamer.NAME_TEMPLATE_ID_FULL_NAME_SUFFIX
                    + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_MANAGER_FULL_NAME)));
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_GLOBAL + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_GLOBAL)));
            break;

        case ApplicationOwner:
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_APP + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_APPLICATION)));
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_GLOBAL + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_GLOBAL)));
            break;

        case Group:
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_GROUP_NAME + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_GROUP_NAME)));
            items.add(new SelectItem(
                    "$!{" + CertificationNamer.NAME_TEMPLATE_GROUP_FACTORY_NAME
                            + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_GROUP_FACTORY_NAME)));
            break;

        // Nothing interest for these now.
        case Identity:
        case BusinessRoleMembership:
        case BusinessRoleComposition:
            break;

        case AccountGroupPermissions:
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_APP + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_APPLICATION)));
            items.add(new SelectItem("$!{"
                    + CertificationNamer.NAME_TEMPLATE_GROUP_OWNER + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_GROUP_OWNER)));
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_FILTER_BY_OWNER + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_FILTER_BY_OWNER)));
            break;

        case AccountGroup:
        case AccountGroupMembership:
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_APP + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_APPLICATION)));
            items.add(new SelectItem("$!{"
                    + CertificationNamer.NAME_TEMPLATE_GROUP_OWNER + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_GROUP_OWNER)));
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_FILTER_BY_OWNER + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_FILTER_BY_OWNER)));
            break;

        case DataOwner:
            items.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_APP + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_APPLICATION)));
            break;

        }

        return items;
    }

    public List<SelectItem> getCertificationNameParameters()
            throws GeneralException
    {

        if (certificationNameParameters == null) {
            certificationNameParameters = new ArrayList<SelectItem>();

            certificationNameParameters.add(new SelectItem("",
                    getMessage(MessageKeys.NAME_TEMPLATE_SELECT_A_PARAMETER)));
            certificationNameParameters.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_FULL_DATE + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_FULL_DATE)));
            certificationNameParameters.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_DATE_YEAR + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_DATE_YEAR)));
            certificationNameParameters.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_DATE_QUARTER + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_DATE_QUARTER)));
            certificationNameParameters.add(new SelectItem("${"
                    + CertificationNamer.NAME_TEMPLATE_DATE_MONTH + "}",
                    getMessage(MessageKeys.NAME_TEMPLATE_DATE_MONTH)));
        }

        return certificationNameParameters;
    }

    public List<SelectItem> getFrequencies() throws GeneralException {

        List<SelectItem> freqs = new ArrayList<SelectItem>();

        freqs.add(new SelectItem(CronString.FREQ_ONCE,
                getMessage(MessageKeys.FREQUENCY_ONCE)));
        freqs.add(new SelectItem(CronString.FREQ_HOURLY,
                getMessage(MessageKeys.FREQUENCY_HOURLY)));
        freqs.add(new SelectItem(CronString.FREQ_DAILY,
                getMessage(MessageKeys.FREQUENCY_DAILY)));
        freqs.add(new SelectItem(CronString.FREQ_WEEKLY,
                getMessage(MessageKeys.FREQUENCY_WEEKLY)));
        freqs.add(new SelectItem(CronString.FREQ_MONTHLY,
                getMessage(MessageKeys.FREQUENCY_MONTHLY)));
        freqs.add(new SelectItem(CronString.FREQ_QUARTERLY,
                getMessage(MessageKeys.FREQUENCY_QUARTERLY)));
        freqs.add(new SelectItem(CronString.FREQ_ANNUALLY,
                getMessage(MessageKeys.FREQUENCY_ANNUALLY)));

        Certification.Type type = getCertificationType();
        if (isContinuousSupported(type)) {
            freqs.add(new SelectItem(CertificationScheduler.FREQ_CONTINUOUS,
                    getMessage(MessageKeys.FREQUENCY_CONTINUOUS)));
        }

        return freqs;
    }

    private boolean isContinuousSupported(Certification.Type type) throws GeneralException {
        // Continuous certifications are being deprecated, but we need to still include the option for already created ones
        if (!getCertificationDefinition().isContinuous()) {
            return false;
        }

        return (type.isIdentity() && !Certification.Type.BusinessRoleMembership
                .equals(type)) || (type == Certification.Type.DataOwner);
    }

    public List<SelectItem> getEntitlementGranularities()
    {

        List<SelectItem> granularities = new ArrayList<SelectItem>();
        for (Certification.EntitlementGranularity g : Certification.EntitlementGranularity
                .values()) {
            granularities.add(new SelectItem(g, getMessage(g.getMessageKey())));
        }

        return granularities;
    }

    public List<SelectItem> getDurationScales()
    {

        List<SelectItem> scales = new ArrayList<SelectItem>();
        scales.add(new SelectItem(Duration.Scale.Hour,
                getMessage(Duration.Scale.Hour.getMessageKey())));
        scales.add(new SelectItem(Duration.Scale.Day,
                getMessage(Duration.Scale.Day.getMessageKey())));
        scales.add(new SelectItem(Duration.Scale.Week,
                getMessage(Duration.Scale.Week.getMessageKey())));
        scales.add(new SelectItem(Duration.Scale.Month,
                getMessage(Duration.Scale.Month.getMessageKey())));
        return scales;
    }

    public List<SelectItem> getAvailableIPOPs() throws GeneralException
    {

        List<SelectItem> iPOPs = new ArrayList<SelectItem>();

        iPOPs.add(new SelectItem("", getMessage(MessageKeys.SELECT_POPULATION)));

        QueryOptions qo = new QueryOptions();
        // this filter should look the same as in
        // GroupDefinitionListBean.getQueryOptions() in the onlyIpops if
        // statement
        Identity owningUser = getLoggedInUser();
        PopulationFilterUtil.addPopulationOwnerFiltersToQueryOption(qo,
                owningUser);
        qo.setScopeResults(true);
        qo.setOrderBy("name");
        List<GroupDefinition> groups = getContext().getObjects(
                GroupDefinition.class, qo);
        if (null != groups) {
            for (GroupDefinition group : groups) {
                iPOPs.add(new SelectItem(group.getId(), group.getName()));
            }
        }

        return iPOPs;
    }

    public List<SelectItem> getAvailableFactories() throws GeneralException
    {

        List<SelectItem> items = new ArrayList<SelectItem>();

        items.add(new SelectItem("", getMessage(MessageKeys.SELECT_GRP_FACTORY)));

        // TODO: need to filter this list based on capabilities, or
        // more likely the display of factory scheduling at all should
        // be controled by capabilities
        QueryOptions qo = new QueryOptions();
        // qo.add(Filter.or(Filter.eq("owner", getLoggedInUser()),
        // Filter.eq("private", false)));

        qo.setOrderBy("name");
        List<GroupFactory> factories = getContext().getObjects(
                GroupFactory.class, qo);
        if (null != factories) {
            for (GroupFactory factory : factories) {
                items.add(new SelectItem(factory.getId(), factory.getName()));
            }
        }

        return items;
    }
    
	public List<SelectItem> getRoleTypeItems() throws GeneralException
    {

        if (roleTypeItems == null) {
            roleTypeItems = new ArrayList<SelectItem>();
            ObjectConfig roleConfig = getContext().getObjectByName(
                    ObjectConfig.class, ObjectConfig.ROLE);
            for (RoleTypeDefinition type : roleConfig.getRoleTypesList()) {
                roleTypeItems.add(new SelectItem(type.getName(), type
                        .getDisplayableName()));
            }
        }

        return roleTypeItems;
    }

    @Override
    protected void cleanSession()
    {
        super.cleanSession();
        cleanSession(getSessionScope());
    }

    @SuppressWarnings("rawtypes")
    private static void cleanSession(Map session)
    {
        session.remove(CERT_GROUP_ID);
        session.remove(EDITED_CERT_SCHEDULE_KEY);
        session.remove(EDITED_CERT_EVENT_ID);
        session.remove(IS_EVENT);
    }

    /**
     * Gets the latest phase of any certification in this group.
     * 
     * @return The latest certification phase.
     * @throws GeneralException
     */
    public Certification.Phase getLatestPhase() throws GeneralException
    {
        if (latestPhase == null && certificationGroupId != null) {
            latestPhase = new CertificationScheduleService(this).getLatestPhase(certificationGroupId);
        }

        return latestPhase;
    }

    /**
     * Gets whether or not the latest phase of any certification in the
     * certification group is past the staged phase.
     * 
     * @return True if the latest phase is past the staged phase, false
     *         otherwise.
     * @throws GeneralException
     */
    public boolean isPastStagedPhase() throws GeneralException
    {
        return isPastPhase(Certification.Phase.Staged);
    }

    /**
     * Gets whether or not the latest phase of any certification in the
     * certification group is past the active phase.
     * 
     * @return True if the latest phase is past the active phase, false
     *         otherwise.
     * @throws GeneralException
     */
    public boolean isPastActivePhase() throws GeneralException
    {
        return isPastPhase(Certification.Phase.Active);
    }

    /**
     * Gets whether or not the latest phase of any certification in the
     * certification group is past the challenge phase.
     * 
     * @return True if the latest phase is past the challenge phase, false
     *         otherwise.
     * @throws GeneralException
     */
    public boolean isPastChallengePhase() throws GeneralException
    {
        return isPastPhase(Certification.Phase.Challenge);
    }

    /**
     * Gets whether or not the latest phase of any certification in the
     * certification group is past the remediation phase.
     * 
     * @return True if the latest phase is past the remediation phase, false
     *         otherwise.
     * @throws GeneralException
     */
    public boolean isPastRemediationPhase() throws GeneralException
    {
        return isPastPhase(Certification.Phase.Remediation);
    }

    /**
     * Gets whether or not the active period duration can be changed.
     *
     * @return True if the active period duration can be changed, false
     *         otherwise.
     * @throws GeneralException
     */
    public boolean isActivePeriodDurationEditable() throws GeneralException
    {
        // This starts out assumed true, so if it's false that means we've already queried it and cached
        // the value; once it's false it can't go back to true so we don't need to query any more.
        // Also if certificationGroupId is false it's because we're just setting up the cert def so no need to query
        // in that case either.
        if (activePeriodDurationEditable && certificationGroupId != null) {
            activePeriodDurationEditable = new CertificationScheduleService(this).isActivePeriodDurationEditable(certificationGroupId);
        }

        return activePeriodDurationEditable;
    }


    /**
     * Gets the pattern used to format dates displayed for
     * the phase durations.
     * @return The pattern string.
     */
    public String getPhaseDatePattern()
    {
        UIViewRoot viewRoot = null;
        FacesContext fc = FacesContext.getCurrentInstance();

        if (fc != null) {
            viewRoot = fc.getViewRoot();
        }

        Locale locale = viewRoot != null ? viewRoot.getLocale() : Locale.getDefault();
        DateFormat df = Util.getDateFormatForLocale(Internationalizer.IIQ_DEFAULT_DATE_STYLE,
                Internationalizer.IIQ_DEFAULT_TIME_STYLE, locale);
        return ((SimpleDateFormat)df).toPattern();
    }

    /**
     * Gets the status of the Recommender.
     *
     * @return True if a recommender exists & and is configured. Otherwise false.
     */
    public boolean isRecommenderConfigured() { return this.isRecommenderConfigured; }

    /**
     * Gets whether or not the latest phase of any certification in the
     * certification group is past the specified phase.
     * 
     * @param phase
     *            The phase to test.
     * @return True if the latest phase is past the specified phase, false
     *         otherwise.
     * @throws GeneralException
     */
    private boolean isPastPhase(Certification.Phase phase)
        throws GeneralException
    {
        if (getLatestPhase() == null) {
            return false;
        }

        return phase.compareTo(getLatestPhase()) < 0;
    }
    
    /**
     * Loads the certification group id from the request or session.
     * 
     * @return The loaded certification group id.
     */
    private String loadCertificationGroupId()
    {
        String result = getRequestParameter("certGroup");
        if (result == null) {
            result = (String) getSessionScope().get(CERT_GROUP_ID);
        }

        return result;
    }

    /**
     * Gets a CertificationScheduleDTO as it exists in the database. Used for
     * inflight certification modification to check for changes.
     * 
     * @param certificationGroupId
     *            The certification group to use.
     * @return The CertificationScheduleDTO or null if none exists.
     */
    private CertificationScheduleDTO loadExistingCertificationSchedule(
            String certificationGroupId) throws GeneralException
    {
        if (certificationGroupId != null) {
            CertificationGroup certGroup = getContext().getObjectById(
                    CertificationGroup.class, certificationGroupId);
            if (certGroup != null) {
                CertificationDefinition def = certGroup.getDefinition();
                if (def != null) {
                    CertificationSchedule schedule = new CertificationSchedule(
                            getContext(), null, def);

                    CertificationScheduleDTO result = new CertificationScheduleDTO(
                            getContext(), schedule);
                    //bug22926 after we activate the cert from staging we need to use that as the firstExecution
                    Date activeDate = CertificationScheduleService.getActivationDate(certificationGroupId, getContext());
                    Date createDate= certGroup.getCreated();
                    if(createDate != null &&
                            activeDate != null &&
                            createDate.before(activeDate)){
                        result.setFirstExecution(activeDate);
                    } else {
                        result.setFirstExecution(createDate);
                    }
                    result.setActivated(activeDate);
                    if (null != certGroup.getAttribute(CertificationGroup.SCHEDULE_FREQUENCY))
                        result.setFrequency(certGroup.getAttribute(CertificationGroup.SCHEDULE_FREQUENCY).toString());

                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Sets the default name of the edited certification schedule if none is
     * set.
     */
    private void setDefaultName()
    {
        if (null == editedCertificationSchedule.getName()) {
            if (isIdentityTrigger()) {
                editedCertificationSchedule.setName(getLocalizedMessage(
                        MessageKeys.TASK_SCHED_CERT_EVENT, new Date()));
            } else {
                editedCertificationSchedule.setName(getLocalizedMessage(
                        MessageKeys.TASK_SCHED_CERT,
                        editedCertificationSchedule.getTypeDescription(),
                        new Date()));
            }
        }
    }

    /**
     * Sets the default certification name template if none is set.
     */
    private void setDefaultCertificationNameTemplate()
    {
        if (null == editedCertificationSchedule.getCertificationNameTemplate()) {
            String dateToken = "${"
                    + CertificationNamer.NAME_TEMPLATE_FULL_DATE + "}";
            if (isIdentityTrigger()) {
                editedCertificationSchedule
                        .setCertificationNameTemplate(getLocalizedMessage(
                                MessageKeys.TASK_SCHED_CERT_EVENT, dateToken));
            } else {
                editedCertificationSchedule
                        .setCertificationNameTemplate(getLocalizedMessage(
                                MessageKeys.TASK_SCHED_CERT,
                                editedCertificationSchedule
                                        .getTypeDescription(), dateToken));
            }
        }
    }

    /**
     * Gets the localized text for the specified message.
     * 
     * @param messageKey
     *            The message key.
     * @param args
     *            The message arguments.
     * @return The localized message.
     */
    private String getLocalizedMessage(String messageKey, Object... args)
    {
        return new Message(messageKey, args).getLocalizedMessage();
    }

    /**
     * Adds an info message to the session.
     * 
     * @param messageKey
     *            The message key to use.
     * @param args
     *            The message arguments.
     */
    private void infoMessage(String messageKey, Object... args)
    {
        addMessageToSession(new Message(Message.Type.Info, messageKey, args));
    }

    /**
     * Adds a warning message to the FacesContext.
     * 
     * @param messageKey
     *            The message key to use.
     * @param args
     *            The message arguments.
     */
    private void warnMessage(String messageKey, Object... args)
    {
        addMessage(new Message(Message.Type.Warn, messageKey, args), null);
    }

    /**
     * Adds an error message to the FacesContext.
     * 
     * @param messageKey
     *            The message key to use.
     * @param args
     *            The message arguments.
     */
    private void errorMessage(String messageKey, Object... args)
    {
        addMessage(new Message(Message.Type.Error, messageKey, args), null);
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // NAVIGATION HELPERS
    //
    // //////////////////////////////////////////////////////////////////////////

    /**
     * Prepare to create a new schedule using the given DTO.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void newSchedule(Map session, CertificationScheduleDTO dto)
    {
        cleanSession(session);
        session.put(EDITED_CERT_SCHEDULE_KEY, dto);
    }

    /**
     * Prepare to edit the given schedule.
     */
    @SuppressWarnings({ "rawtypes" })
    static void editSchedule(Map session, CertificationScheduleDTO dto)
    {
        newSchedule(session, dto);
    }

    /**
     * Prepare to create a certification event using the given DTO.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void newEvent(Map session, CertificationScheduleDTO dto)
    {
        cleanSession(session);
        session.put(EDITED_CERT_SCHEDULE_KEY, dto);
        session.put(IS_EVENT, true);
    }

    /**
     * Prepare to edit the given event.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void editEvent(Map session, CertificationScheduleDTO dto,
            String triggerId)
    {
        newEvent(session, dto);
        session.put(EDITED_CERT_EVENT_ID, triggerId);
        session.put(IS_EVENT, true);
    }
}
