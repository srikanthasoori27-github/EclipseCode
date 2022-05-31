/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.TaskManager;
import sailpoint.object.Application;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.Argument;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Configuration.EmailNotify;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.JasperResult;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Scope;
import sailpoint.object.Signature;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskDefinition.ResultAction;
import sailpoint.object.TaskExecutor;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskItemDefinition.ProgressMode;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemConfig;
import sailpoint.reporting.JasperExecutor;
import sailpoint.search.SelectItemComparator;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseTaskBean;
import sailpoint.web.WorkItemConfigBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.workitem.WorkItemUtil;

/*
 * Things on the session:
 *  ("isNew");
 *  ("editForm:argForm");
 *  ("editForm:templateDef");
 *  ("editForm:taskResultName");
 *
 *
 */

public class TaskDefinitionBean extends BaseTaskBean<TaskDefinition> {

    private static Log log = LogFactory.getLog(TaskDefinitionBean.class);

    private static String TASK_DEF_USER_REPORT_FORM = "/analyze/reports/userreportargs.xhtml";
    private static String TASK_DEF_ACCOUNT_ATTRIBUTES_REPORT_FORM = "/analyze/reports/userAccountAttributesReportArgs.xhtml";
    private static String TASK_DEF_PRIV_USER_REPORT_FORM="/analyze/reports/privilegedaccountreportargs.xhtml";
    protected static String TASK_DEF_REMEDIATION_REPORT = "Remediation Progress Report";
    private static String IDENTITY_ATTRIBUTE_INACTIVE = "inactive";
    public static final String ATT_SYNC_RESULT = "syncTaskResult";
    public static final String ATT_SYNC_ARGUMENTS = "syncArguments";
    public static final String ATT_SYNC_MONITOR = "syncMonitor";
    public static final String ATT_SYNC_EXECUTOR = "syncExecutor";
    /* Mapping of the argument */
    List<ArgValue> _defArgs;
    private boolean _renderHeaderMsg;
    private boolean _taskValidated;
    List<ObjectAttribute> _identityAttributes;
    WorkItemConfigBean _signoff;
    
    /** A flag to tell the bean whether or not to transition to the next page once the validation
     * and save has finished **/
    boolean transition;

    /**
     * Map used by the default rendering when there is 
     * not a custom a form specified. Also used for 
     * some of the header attributes.
     */
    Map<String,ArgValue> _defArgMap;

    /**
     * Map used by custom forms.
     */
    Map<String,Object> _customArgMap;

    /**
     * Cached items for the ResultAction selector.
     */
    static SelectItem[] _actionItems;

    /**
     * Cached items for the emailNotification selector.
     */
    static SelectItem[] _emailNotifyOptions;

    /**
     * Stores email notification type and template 
     */
    private String _emailNotify;
    private String _emailTemplate;
    private String _emailRecipients;

    /**
     * Flag indiciating that run length statistics have been reset.
     * These are stored in the arguments map but they're not editable so
     * we throw them away every time because FORCE_LOAD is always set.
     * Keep a flag so we know when to trust the map.
     */
    boolean _runStatsReset;

    /**
     * Monitor that can be handed to reporting
     * tasks to give progress while executing 
     * reports synchronously.
     */
    ReportExportMonitor _syncReportMonitor;
    
    /**
     * Used to hold the browser generated tab identifier
     */
    private String _tabId;

    @SuppressWarnings("unchecked")
    public TaskDefinitionBean() throws GeneralException {
        super();

        String isNew = getRequestOrSessionParameter("isNew");
        if ( ( isNew != null ) && ( "true".compareTo(isNew) == 0 ) ) {
            getSessionScope().remove("isNew");
            getSessionScope().remove("editForm:argForm");
        }

        // always force load the object from the DB, this is to make sure we have the most
        // up to date info such as number of runs that can change while the object
        // is still in the session.
        getSessionScope().put(FORCE_LOAD, true);
        setScope(TaskDefinition.class);
        setStoredOnSession(true);
        _syncReportMonitor = (ReportExportMonitor) getSessionScope().get(ATT_SYNC_MONITOR + getTabId());
        
        //
        // We have to load this object so the parent stuff 
        // is available after we detach
        //
        setLoadOnFetch(true);

        // mlh - reports need to be browser tab aware.  Pull in the browser tabId
        // and if it exists, use it to set/get the session data.
        String tabId = getRequestParameter("editForm:tabId");
        if(tabId == null){
            tabId = getRequestParameter("tabId"); // this one comes from the URL
        }
        tabId = (tabId == null) ? "" : tabId; // null is no good.
        setTabId(tabId); // for JSF (not sure this is necessary)

    }

    @Override
    public TaskDefinition createObject() {
        String id = (String)getSessionScope().get("editForm:templateDef");
        TaskDefinition def = new TaskDefinition();

        if ( id != null ) {
            TaskDefinition template = null;
            try {
                template = getContext().getObjectById(TaskDefinition.class,id);
                if (  template == null ) {
                    throw new GeneralException("Unable to load base template "
                                               + "task definition :" + id);
                } else {
                   // load the definition or we won't be
                   // able to get back at the def's 
                   // signature               	
                	
                   template.load();
                   def = assimilateDef(template);
                   def.setOwner(getLoggedInUser());
                   /*If this task has a parent, use the parent as the template, not this task */
                   if(template.getParentRoot()!=null) {
                	   def.setParent((TaskDefinition)template.getParentRoot());
                   }
                   getContext().decache(template);
                }
            } catch(GeneralException e) {
                addExceptionToContext(e);
            }
        }

        return def;
    }

    /**
     * Override this so we can deal with the incredibly annoying
     * Hibernate "stale id from the last transaction" problem.
     */
    public void attachObject(TaskDefinition def) throws GeneralException {

        WorkItemConfig signoff = def.getSignoffConfig();
        // ugh, try to avoid using EditSession and guess by
        // looking at the create date
        if (signoff.getCreated() == null)
            signoff.setId(null);

        // superclass will handle the main object
        super.attachObject(def);
    }

    public static boolean isGridReport(TaskDefinition def ) {
        TaskItemDefinition.Type defType = def.getEffectiveType();
        if ( TaskItemDefinition.Type.GridReport.equals(defType) ) {
            return true;
        }
        return false;
    }

    /**
     * Determines whether we should show the "Report Type" options to the user when they configure their report
     */
    public boolean isShowTypeOptions() {
    	try {
        	TaskItemDefinition def = (TaskItemDefinition)getObject();
        	if(def==null)
        		return false;

        	TaskItemDefinition parent = def.getParentRoot();
        	return (parent!=null && parent.getEffectiveType().equals(TaskItemDefinition.Type.Report));

        	} catch(GeneralException ge) {
        		log.info("Exception caught while trying to determine if this task is a report. Exception: " + ge.getMessage());
        	}
        	return false;
    }

    public static TaskDefinition assimilateDef(TaskDefinition temp) {

        // jsl - Rather than copying the signature, we just
        // leave it empty and fall back to the parent signature.
        TaskDefinition def = new TaskDefinition();
        def.setTemplate(false);
        def.setParent(temp);
        def.setType(temp.getEffectiveType());
        def.setSubType(temp.getSubType());
        def.setFormPath(temp.getFormPath());
        def.setOwner(temp.getOwner());
        def.setDescription(temp.getDescription());
        def.setType(temp.getType());
        def.setSubType(temp.getSubType());

        if (temp.getArguments() != null){
            for(String key : temp.getArguments().keySet()){
                def.setArgument(key, temp.getArgument(key));
            }
        }

        // sigh, these aren't forwarded to the parent, need to copy
        // them, or do a full clone of the parent
        def.setResultAction(temp.getResultAction());
        def.setConcurrent(temp.isConcurrent());
        def.setResultExpiration(temp.getResultExpiration());

        return def;
    }

    /**
     * Get the name of the TaskDefinition that is providing
     * the siguature we use to build the arguments form.
     * This is used in the "Options for..." header above the form.
     * This could actually be confusing if the templates don't
     * have obvious names.
     */
    public String getSignatureName() throws GeneralException {

        String name = null;

        // Sigh, because of the way we copy the signature, go back
        // until we hit a task with a signature *and* a name.

        TaskItemDefinition def = (TaskItemDefinition)getObject();
        while (def != null &&
                (def.getName() == null || def.getSignature() == null))
            def = def.getParent();

        if (def != null)
            name = def.getName();

        return name;
    }

    public List<SelectItem> getApplicationActivityActions()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        for (ApplicationActivity.Action action : ApplicationActivity.Action.values())
        {
             list.add(new SelectItem(action.name(), getMessage(action.getMessageKey())));
        }
        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));

        return list;
    }

    public List<SelectItem> getApplicationActivityResults()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        for (ApplicationActivity.Result result : ApplicationActivity.Result.values())
        {
            list.add(new SelectItem(result.name(), getMessage(result.getMessageKey())));
        }
        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));

        return list;
    }

    public List<SelectItem> getWorkItemTypes()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        for (WorkItem.Type type : WorkItem.Type.values())
        {
            if (type != WorkItem.Type.Generic && 
                type != WorkItem.Type.Test &&
                type != WorkItem.Type.Event) 
            {
                list.add(new SelectItem(type.name(), getMessage(type.getMessageKey())));
            }
        }
        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));

        return list;
    }
    
    public List<SelectItem> getWorkItemPriorities()
    {
        return WorkItemUtil.getPrioritySelectItems(getLocale(), getUserTimeZone());
    }

    public List<SelectItem> getWorkItemStates()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("Open", super.getMessage(MessageKeys.CERT_ACTION_OPEN)));
        for (WorkItem.State state : WorkItem.State.values())
        {
            // Skip Canceled because WorkItems are not persisted with that state 
            if( state.equals( WorkItem.State.Canceled ) ) {
                continue;
            }
            list.add(new SelectItem(state.name(), getMessage(state.getMessageKey())));
        }
        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));

        return list;
    }
    
    public List<SelectItem> getWorkItemStatusOptions() {
        return WorkItemUtil.getStatusSelectItems(getLocale(), getUserTimeZone());
    }

    public List<SelectItem> getLogicalOpSelectItems() {
        List<SelectItem> items = new ArrayList<SelectItem>();
        items.add(new SelectItem(Filter.LogicalOperation.EQ.name(), Filter.LogicalOperation.EQ.getDisplayName()));
        items.add(new SelectItem(Filter.LogicalOperation.LT.name(), Filter.LogicalOperation.LT.getDisplayName()));
        items.add(new SelectItem(Filter.LogicalOperation.GT.name(), Filter.LogicalOperation.GT.getDisplayName()));
        return items;
    }

    /**
     * Return a list of ObjectAttribute objects representing the attributes
     * we can use in search filters.
     *
     * This is used by the userreportargs.xhtml which is an extended form
     * for the tasks that run user reports.  It is unfortunate that we have
     * to do something this task-specific in TaskDefinitionBean.  Since we're
     * including a configurable page fragment, we should arguably have this
     * logic in a custom backing bean just for that fragment.  But this is
     * kind of a pain so we'll leave it here.  This is a bigger issue for
     * custom reports however.  Essentially what we need is something
     * similar to the Wavest FormUtil class, a general purpose backing bean
     * that can do data analysis and massaging.
     */
    public List<ObjectAttribute> getSearchableIdentityAttributes()
    throws GeneralException {

        if (_identityAttributes == null) {
            _identityAttributes = new ArrayList<ObjectAttribute>();

            ObjectConfig config = Identity.getObjectConfig();
            if (config != null) {
                List<ObjectAttribute> atts = config.getSearchableAttributes();
                if (atts != null) {
                    // we have been filtering inactive, why?
                    for (ObjectAttribute att : atts) {
                        if (!att.getName().equals(IDENTITY_ATTRIBUTE_INACTIVE))
                            _identityAttributes.add(att);
                    }
                }
            }
        }

        return _identityAttributes;
    }

    List<ObjectAttribute> _multiIdentityAttributes;
    public List<ObjectAttribute> getMultiIdentityAttributes()
        throws GeneralException {

        if (_multiIdentityAttributes == null) {
            _multiIdentityAttributes = new ArrayList<ObjectAttribute>();

            ObjectConfig config = Identity.getObjectConfig();
            if (config != null) {
                _multiIdentityAttributes = config.getMultiAttributeList();
            }
        }

        return _multiIdentityAttributes;
    }

    List<ObjectAttribute> _accountAttributes;
    public List<ObjectAttribute> getAccountAttributes()
        throws GeneralException {

        if (_accountAttributes == null) {
            _accountAttributes = new ArrayList<ObjectAttribute>();

            ObjectConfig config = Link.getObjectConfig();
            if (config != null) {
                // all link attributes are searchable
                _accountAttributes = config.getObjectAttributes();
            }
        }
        /** Need to initialize dates in the Custom Arg Map **/
        if(_accountAttributes!=null && getCustomArgMap()!=null) {
            for(ObjectAttribute attr : _accountAttributes) {
                if(attr.getType().equals("date") && getCustomArgMap().get(attr.getName())==null) {
                    getCustomArgMap().put(attr.getName(), new Date());
                }
            }
        }
        return _accountAttributes;
    }

    /**
     * Overload this to do post-processing on the object after it has
     * Prune out XML padding in the description.  Ideally this should just
     * be done by the XML parser, but need to have a way to declare that.
     */
    @Override
    public void fixObject() throws GeneralException{

        if (getObject() != null) {
            TaskDefinition def = (TaskDefinition)getObject();
            String desc = def.getDescription();
            String fixed = Util.unxml(desc);
            if (!Util.nullSafeEq(desc, fixed))
                getObject().setDescription(fixed);
            
            //fix deleted application
            Object appId = def.getArgument("application");
            if (appId != null) {
                Object app = getContext().getObjectById(Application.class, (String)appId);
                if (app == null) {
                    def.setArgument("application", null);
                }
            }

            // Propagate the assigned scope to the taskScope argument
            Scope assignedScope = def.getAssignedScope();
            if (assignedScope != null) {
                def.setArgument(TaskDefinition.ARG_TASK_SCOPE, assignedScope.getName());
            }
        }
    }

    public Map<String,Object> getCustomArgMap() {
        if ( _customArgMap == null ) {
            try {
               TaskDefinition def = (TaskDefinition)getObject();
               _customArgMap  = def.getEffectiveArguments();
            } catch (Exception e) {
                log.info("Unable to load argvals.  Exception: " + e.getMessage());
            }
        }
        return _customArgMap;
    }

    public Map<String,ArgValue> getArgMap() {
        if ( _defArgMap == null ) {
           try {
               List<ArgValue> argVals = getTaskDefArgs();
               if ( argVals != null ) {
                   _defArgMap = new HashMap<String,ArgValue>();
                   for ( ArgValue argValue : argVals ) {
                       String name = argValue.getName();
                       _defArgMap.put(name, argValue);
                   }
               }
           } catch (Exception e) {
               log.info("Unable to load argvals.  Exception: " + e.getMessage());
           }

        }
        return _defArgMap;
    }

    public List<ArgValue> getTaskDefArgs() throws GeneralException {
        if (_defArgs == null) {

            _defArgs = new ArrayList<ArgValue>();

            TaskDefinition def = (TaskDefinition)getObject();
            Signature signature = def.getEffectiveSignature();

            // build a map of argValues
            if ( signature != null ) {
                Map<String,Object> argMap = def.getEffectiveArguments();
                List<Argument> args = signature.getArguments();
                for (Argument argument : args) {
                    ArgValue argValue = new ArgValue();
                    argValue.setArgument(argument);
                    String argName = argument.getName();
                    Object value = argMap.get(argName);

                    if (!argMap.containsKey(argName)) {
                        value = argument.getDefaultValue();
                    }
                  
                    if ( value != null ) {
                        argValue.setObjectValue(value);
                        // only need this for dates, a kludge
                        // until we get the Extjs date picker
                        // able to set null dates
                        argValue.setBound(true);
                    }

                    // jsl - if we're dealing with a SailPointObject
                    // reference, be sure to resolve name references
                    // to ids
                    // dc - this SHOULD now be unnecessary w/ the new suggest components
                   // argValue.resolve(getContext());
                    _defArgs.add(argValue);
                }

                // 
                //  djs: this can be removed once the form starts using the customArgMap vs argMap
                // 
            /** If this is a user report task definition, we need to get the extended
             * attributes out of the identity configuration object and build args for it.
             */
                if(TASK_DEF_USER_REPORT_FORM.equals(def.getEffectiveFormPath()) ||
                        TASK_DEF_PRIV_USER_REPORT_FORM.equals(def.getEffectiveFormPath()) ||
                        TASK_DEF_ACCOUNT_ATTRIBUTES_REPORT_FORM.equals(def.getEffectiveFormPath())) {
                    List<ObjectAttribute> attributes = getSearchableIdentityAttributes();
                    if(attributes!=null) {
                        for(ObjectAttribute attr : attributes) {
                            /** Make sure the list of args does not already contain this arg. **/
                            ArgValue argValue = new ArgValue();
                            Argument argument = new Argument();
                            argument.setType(Argument.TYPE_STRING);
                            argument.setName(attr.getName());
                            if(argMap.get(attr.getName())!=null) {
                                argValue.setValue((argMap.get(attr.getName())).toString());
                            }
                            argValue.setArgument(argument);
                            _defArgs.add(argValue);
                        }
                    }
                }
            }

            // Add task scoping if it's enabled
            if (isShowAssignedScopeControl()) {
                ArgValue scopeValue = new ArgValue();
                Argument scopeArgument = new Argument();
                scopeArgument.setName(TaskDefinition.ARG_TASK_SCOPE);
                scopeArgument.setType(Field.TYPE_SCOPE);
                // Don't show scope in the form because it is already rendered as a standard attribute
                scopeArgument.setHidden(true);
                scopeValue.setArgument(scopeArgument);
                String scopeName = Util.otos(def.getArgument(TaskDefinition.ARG_TASK_SCOPE));
                scopeValue.setNullSafeValue(scopeName);
                scopeValue.resolve(getContext());
                _defArgs.add(scopeValue);
            }
            
        }
        return _defArgs;
    }

    /**
     * Similar to {@link #getTaskDefArgs()}, but exclude the arguments that are hidden
     * @return
     * @throws GeneralException
     */
    public List<ArgValue> getTaskDefFormArgs() throws GeneralException {
        List<ArgValue> taskDefArgs = getTaskDefArgs();
        List<ArgValue> formArgs = new ArrayList<ArgValue>();
        for (ArgValue argValue : Util.iterate(taskDefArgs)) {
            if (!argValue.getArgument().isHidden()) {
                formArgs.add(argValue);
            }
        }
        return formArgs;
    }

    /**
     * Kludge for conditional rendering, since some combination of
     * JSF, Facelets, and el is too stupid to know what
     * taskDefinition.taskDefArgs.size means.
     */
    public boolean getDisplayArguments() throws GeneralException {
        List<ArgValue> args = getTaskDefFormArgs();
        return (args != null && args.size() > 0);
    }

    /**
     * Return labels for a ResultAction selector.
     * Geez, this is painful isn't there an easier way to deal
     * with enumerations in JSF?
     */
    public SelectItem[] getResultActionItems() throws GeneralException {

        if (_actionItems == null) {
            _actionItems = new SelectItem[4];
            _actionItems[0] = new SelectItem(ResultAction.Delete.toString(),
            getMessage(MessageKeys.TASK_RESULT_ACTION_DELETE));
            _actionItems[1] = new SelectItem(ResultAction.Rename.toString(),
            getMessage(MessageKeys.TASK_RESULT_ACTION_RENAME_OLD));
            _actionItems[2] = new SelectItem(ResultAction.RenameNew.toString(),
            getMessage(MessageKeys.TASK_RESULT_ACTION_RENAME_NEW));
            _actionItems[3] = new SelectItem(ResultAction.Cancel.toString(),
            getMessage(MessageKeys.TASK_RESULT_ACTION_CANCEL));
        }

        return _actionItems;
    }

    public String getResultAction() throws GeneralException {
        TaskDefinition d = (TaskDefinition)getObject();
        ResultAction action = d.getResultAction();
        if (action == null)
            action = ResultAction.Delete;
        return action.toString();
    }

    public void setResultAction(String s) throws GeneralException {
        if (s != null) {
            TaskDefinition d = (TaskDefinition)getObject();
            ResultAction action = Enum.valueOf(ResultAction.class, s);
            if (action != null)
                d.setResultAction(action);
        }
    }

    /**
     * Return labels for a Email Notify selector.
     */
    public SelectItem[] getEmailNotifyOptions() throws GeneralException {

        if (_emailNotifyOptions == null) {
            _emailNotifyOptions = new SelectItem[4];
            _emailNotifyOptions[0] = new SelectItem(EmailNotify.Disabled.toString(), getMessage(MessageKeys.NOTIFY_OPTION_DISABLED));
            _emailNotifyOptions[1] = new SelectItem(EmailNotify.Warning.toString(), getMessage(MessageKeys.NOTIFY_OPTION_WARNING));
            _emailNotifyOptions[2] = new SelectItem(EmailNotify.Failure.toString(), getMessage(MessageKeys.NOTIFY_OPTION_FAILURE));
            _emailNotifyOptions[3] = new SelectItem(EmailNotify.Always.toString(), getMessage(MessageKeys.NOTIFY_OPTION_ALWAYS));
        }
        return _emailNotifyOptions;
    }

    public String getEmailNotify() throws GeneralException {
        // jsl - why are we doing it this way, setting the member fields
        // as a side effect of the accessor?  this assumes we can never
        // refresh the page, else we'll lose previous posted values
        _emailNotify = Configuration.EmailNotify.Disabled.toString();
        TaskDefinition def = (TaskDefinition)getObject();
        if (def != null) {
            String tempNotify = def.getString(Configuration.TASK_COMPLETION_EMAIL_NOTIFY);
            if (tempNotify != null) {
                _emailNotify = tempNotify;
            }
        }
        return _emailNotify;
    }

    public void setEmailNotify(String emailNotify) throws GeneralException {
        _emailNotify = emailNotify;
    }

    public String getEmailTemplate() throws GeneralException {
        TaskDefinition def = (TaskDefinition)getObject();
        if (def != null) {
            _emailTemplate = def.getString(Configuration.TASK_COMPLETION_EMAIL_TEMPLATE);
        }
        return _emailTemplate;
    }

    public void setEmailTemplate(String emailTemplate) throws GeneralException {
        _emailTemplate = emailTemplate;
    }

    public List<String> getRecipients() throws GeneralException{
        TaskDefinition def = (TaskDefinition)getObject();
        if (def != null) {
            // jsl - no reason why this must be a string, should support
            // both styles
            _emailRecipients = def.getString(Configuration.TASK_COMPLETION_RECIPIENTS);
        }
        return(Util.csvToList(_emailRecipients)); 
    }

    public void setRecipients(List<String> recipients)throws GeneralException {
        _emailRecipients = Util.listToCsv(recipients);
    }

    /**
     * Bootstrap a signoff config if we don't have one yet.
     * This will be cleaned up during save if we don't need it.
     * Sigh, now that we have to have a wrapper bean it wouldn't
     * be that much of a stretch to make it into a DTO to avoid
     * the funky boostrapping.
     */
    public WorkItemConfigBean getSignoff() throws GeneralException {

        if (_signoff == null) {
            TaskDefinition d = (TaskDefinition)getObject();
            WorkItemConfig config = d.getSignoffConfig();
            if (config == null) {
                // NOTE: See attachObject for why life sucks when
                // you add a new child object like this
                config = new WorkItemConfig();
                d.setSignoffConfig(config);

                // note that these start off disabled, you have to
                // ask for it like you mean it!
                config.setDisabled(true);
            }

            _signoff = new WorkItemConfigBean(config);
        }
        return _signoff;
    }

    /**
     * Return the average run length fomratted as hh:mm:ss
     */
    public String getRunLengthAverage() throws GeneralException {
        String result;
        if (!_runStatsReset) {
            TaskDefinition def = (TaskDefinition)getObject();
            result = Util.durationToString(def.getRunLengthAverage());
        }
        else {
            // formatting 0:00:00 looks better than just empty
            result = Util.durationToString(0);
        }
        return result;
    }

    public int getRuns() throws GeneralException {
        int result = 0;
        if (!_runStatsReset) {
            TaskDefinition def = (TaskDefinition)getObject();
            result = def.getRuns();
        }
        return result;
    }

    /**
     * Action handler for the reset run statistics button.
     * These are stored in the arguments map but since they
     * are not in the Signature, they won't be in the getTaskDefArgs
     * list so when we merge, it will preserve the values in the 
     * TaskDefinition object.
     */
    public String resetRunStatistics() {
        _runStatsReset = true;
        return "";
    }

    /**
     * We don't use a DTO and FORCE_LOAD is always on for some reason so we can't
     * save state in the TaskDefinition, everything has to be posted.  This is a
     * hidden form field where we maintain state.
     */
    public boolean getRunStatsReset() {
        return _runStatsReset;
    }

    public void setRunStatsReset(boolean b) {
        _runStatsReset = b;
    }

    /**
     * Validation method that has to be called when doing sync
     * execution before we launch the task. This is also called
     * during the normal save pattern.
     */
    public String validateName() {
        //Check to see if the saved name is the same as the effective task definition name.
        try {
            TaskDefinition def = (TaskDefinition)getObject();
            if (null == def.getName() || def.getName().trim().length() == 0) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EMPTY_TASK_NAME), null);
                _taskValidated = false;
                return null;
            }
        } catch (GeneralException ge) {
            log.error("Unable to validate task definition: " + ge.getMessage());
            _taskValidated = false;
            return null;
        }
        _taskValidated = true;
        return "";
    }

    /** this is a helper method to allow the taskdefinition edit page to use
     * a4j actions.  We need to know if the form validated before we submit
     * the task for a save and an execution.  This allows us to do see
     * that the form got through to the action.  PH
     */
    public String validateAndSave() {
        try {
            String validateResult = validateName();
            if ( validateResult == null ) {
                _taskValidated = false;
                return null;
            }
            TaskDefinition def = (TaskDefinition)getObject();
            if(null != def.getParent() && def.getEffectiveDefinitionName().equals(def.getName())) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_TASK_NAME_MATCHES_PARENT), null);
                log.error("Can not save a task with the same name as its parent");
                _taskValidated = false;
                return null;
            }

            // Check for report with existing name if new object
            TaskDefinition existingTask = getContext().getObjectByName(TaskDefinition.class, def.getName());
            //If this is a new object, and a task exists with that name already, reject it.
            if (getObjectId()==null && null != existingTask) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_TASK_NAME_EXISTS, existingTask.getName()), null);
            	_taskValidated = false;
            	return null;
            //If this is an existing object, and a task exists with that name already
            //but doesn't have the same id, reject it.                
            } else if(getObjectId()!=null && existingTask!=null && !getObjectId().equals(existingTask.getId())) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_TASK_NAME_EXISTS, existingTask.getName()), null);
                _taskValidated = false;
                return null;
            }

            // validation we share with save() and saveAndExecute()
            if (!validateExtra()) {
                // messages are already set
                _taskValidated = false;
                return null;
            }
            
        } catch (GeneralException ge) {
            log.error("Unable to validate task definition: " + ge.getMessage());
            _taskValidated = false;
            return null;
        }

        _taskValidated = true;
        cleanup();
        String transition = saveIt(false);
        if(isTransition()) 
            return transition;
        else 
            return "";
    }

    /**
     * Do additional validation that can't be done with JSF validation tags.
     * Return false and add error messages if there are validation errors.
     */
    public boolean validateExtra() {

        boolean valid = true;

        if (_signoff != null) {
            // first let the WorkitemConfigBean do it's thing
            // error messages are loaded by the validate() method
            valid = _signoff.validate();

            // prevent a conflicting combination
            if (valid && _signoff.isEnabled()) {
                try {
                    TaskDefinition def = (TaskDefinition)getObject();
                    ResultAction action = def.getResultAction();
                    if (action == null || action.equals(ResultAction.Delete)) {
                        addMessage(
                            new Message(Message.Type.Error, MessageKeys.ERR_DELETE_INVALID_IF_SIGNOFF_ENABLED),
                            new Message(Message.Type.Error, MessageKeys.ERR_VALIDATION));
                        valid = false;
                    }
                }
                catch (Exception e) {
                    // severe error accessing TaskDefinition
                    addMessage(
                        new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e),
                        new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM)
                    );
                }
            }
        }

        return valid;
    }

    private void cleanup() {
        getSessionScope().remove("editForm:cancelViewResult");
        getSessionScope().remove("editForm:executedTaskResultName");
        getSessionScope().remove("editForm:currentPage");
    }

    /**
     * jsl - unlike saveAndExecute we've been relying on JSF to do
     * field validation so the validate() method won't have been called
     * and I guess is redundant since we've done field validation??
     * One thing that still needs to be done though is the WorkItemConfig
     * validation which can't be done in JSF.
     */
    public String save() {

        String transition = null;
        boolean valid = validateExtra();
        if (valid)
            transition = saveIt(false);

        return transition;
    }

    public String saveIt(boolean execute) {

        String returnStr = "ok";
        boolean error = false;
        // clear any state from async executions
        getSessionScope().remove(ATT_SYNC_RESULT + getTabId());

        try {
            TaskDefinition def = (TaskDefinition)getObject();

            // populate argument map, the executor is put in
            // by default, so start with the definitions map
            Attributes<String, Object> argMap = mergeArgs(def.getArguments());
            def.setArguments(argMap);

            // reset statistics held in the harguments map
            if (_runStatsReset)
                def.resetRunStatistics();
            
            if (isTaskDefinition()) {
                def.setArgument(Configuration.TASK_COMPLETION_EMAIL_NOTIFY, _emailNotify);
                def.setArgument(Configuration.TASK_COMPLETION_EMAIL_TEMPLATE, _emailTemplate);
                def.setArgument(Configuration.TASK_COMPLETION_RECIPIENTS, _emailRecipients);
                //if email notification is disabled identities should be null
                // jsl - not really necessary, might want to keep them so they
                // can temporarily disable without losing this, if we're going
                // to do this for recipients, why not the template as well?
                if (_emailNotify.equals(Configuration.EmailNotify.Disabled.toString())) {
                    def.setArgument(Configuration.TASK_COMPLETION_RECIPIENTS, null);
                }
                String taskScopeName = Util.otos(def.getArgument(TaskDefinition.ARG_TASK_SCOPE)); 
                Scope taskScope = null;
                if (!Util.isNullOrEmpty(taskScopeName)) {
                    taskScope = getContext().getObjectByName(Scope.class, taskScopeName); 
                }
                if (taskScope != null) {
                    def.setAssignedScope(taskScope);
                }
            }

            if ( isReportDef(def) ) {

                // add user locale so report text can be localized
                def.getArguments().put(JasperExecutor.OP_LOCALE, getLocale().toString());
                def.getArguments().put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());

                // check for the argument that tells us this
                // we should mark this a grid report
                if ( isGridReport(def) ) {
                    def.setType(TaskItemDefinition.Type.GridReport);
                } else {
                    def.setType(TaskItemDefinition.Type.Report);
                }
            }

            String defId  = def.getId();
            if ( BaseTaskBean.isEmpty(defId)) {
                def.setId(null);
            }

            // this will validate again, sigh
            if (_signoff != null)
                _signoff.commit(true);

            // If they left the signoff in the disabled state
            // delete it since it is a first-class Hibernate object.
            WorkItemConfig signoff = def.getSignoffConfig();
            if (signoff != null && signoff.isDisabled()) {
                def.setSignoffConfig(null);
                getContext().removeObject(signoff);
            }
            else if (signoff != null) {
                // have to make sure this gets flushed too in case
                // there were no other changes to the parent
                // oh how I wish we had left this an XMLObject
                getContext().saveObject(signoff);
            }
            
            //Cascade scope from the existing parent if it exists.
            TaskDefinition.cascadeScopeToObject(def, def);

            ObjectUtil.checkIllegalRename(getContext(), def);
            getContext().saveObject(def);
            getContext().commitTransaction();

            // have to remove our object state as well because
            // forceLoad doesn't seem to be passed from the new table
            clearHttpSession();

            if ( execute ) {
                executeTask(def);
            }

        } catch ( GeneralException e ) {
            addMessage(new Message(Message.Type.Error, e.getMessage()));
            log.error(e.getMessage(), e);
            setRenderHeaderMsg(true);
            error = true;
            returnStr = null;
        } finally {
            if ( ( !error ) && (!execute) ) cleanSession();
        }
        return returnStr;
    }

    /**
     * Note that this must follow the same conventions as TaskDefinitionListBean
     * for saving state on the session. - jsl
     */
    @SuppressWarnings("unchecked")
    public void executeTask(TaskDefinition def) {

        String tabId = getTabId();
        
        getSessionScope().remove(TaskDefinitionListBean.ATT_RESULT_ID + tabId);
        getSessionScope().remove(TaskDefinitionListBean.ATT_LAUNCH_ERROR + tabId);
        getSessionScope().remove(ATT_SYNC_RESULT + tabId);
        getSessionScope().remove(ATT_SYNC_ARGUMENTS + tabId);

        def.getArguments().put(JasperExecutor.OP_LOCALE, getLocale().toString());
        def.getArguments().put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());

        TaskResult result = null;
        String launchError = null;
        try {
            TaskManager tm = new TaskManager(getContext());
            tm.setLauncher(getLoggedInUserName());
            result = tm.runSync(def,null);
        }
        catch (GeneralException ge) {
            // usually this is a concurrent task error, but we don't
            // have a way to distinguish that!
            log.error("GeneralException: [" + ge.getMessage() + "]", ge);
            // because the task was launched with an Ajax command
            // button, adding the error here will have no effect since
            // the editform will not be refreshed.  Have to save it
            // for later.
            launchError = ge.getMessage();
        }

        String resultId;
        if (result != null)
            resultId = result.getId();
        else {
            // There was a problem launching the task, normally
            // this will have thrown and we will have added an
            // error message in the faces context.  So we know not
            // to continue thinking the result is pending, store
            // a special value so the status popup terminates
            // immediately.
            resultId = TaskDefinitionListBean.ERROR_RESULT_ID;
        }

        getSessionScope().put(TaskDefinitionListBean.ATT_RESULT_ID + tabId, resultId);
        getSessionScope().put(TaskDefinitionListBean.ATT_LAUNCH_ERROR + tabId, launchError);
    }

    @SuppressWarnings("unchecked")
    public String getArgumentForm() throws GeneralException {
        String argForm = (String)getSessionScope().get("editForm:argForm");

        String oid = getRequestOrSessionParameter("editForm:id");
        if ( ( oid != null ) && !( "new".compareTo(oid) == 0 ) ) {
        	getSessionScope().put(FORCE_LOAD, true);
        }

        TaskDefinition def = (TaskDefinition)getObject();
        String customForm = getEffectiveForm(def);
        if ( ( argForm == null ) || ( customForm == null ) ||
                ( argForm.compareTo(customForm) != 0 ) ) {
            if ( customForm != null )
                argForm = customForm;
            else
                argForm = "arguments.xhtml";
            getSessionScope().put("editForm:argForm", argForm);
        }
        return argForm;
    }

    private String getEffectiveForm(TaskDefinition def) {
        String customForm = null;
        if ( def != null )
            customForm = def.getFormPath();

        if ( customForm == null ) {
            TaskItemDefinition parent = def.getParent();
            while ( parent != null ) {
                customForm = parent.getFormPath();
                if ( customForm != null ) {
                    break;
                }
                parent = parent.getParent();
            }
        }
        return customForm;
    }

    /**
     * @return the _renderHeaderMsg
     */
    public boolean isRenderHeaderMsg() {
        return _renderHeaderMsg;
    }

    public boolean isReportTask() {
    	try {
    	TaskItemDefinition def = (TaskItemDefinition)getObject();
    	if(def==null || def.getEffectiveType()==null)
    		return false;

    	return (def.getEffectiveType().equals(TaskDefinition.Type.Report) ||
    		def.getEffectiveType().equals(TaskDefinition.Type.GridReport));
    	} catch(GeneralException ge) {
    		log.info("Exception caught while trying to determine if this task is a report. Exception: " + ge.getMessage());
    	}
    	return false;
    }
    
    public boolean isTaskDefinition() throws GeneralException {
        TaskItemDefinition definition = (TaskItemDefinition) getObject();
        if (definition == null || definition.getEffectiveType() == null) {
            return true;
        }

        if (definition.getEffectiveType().equals(TaskDefinition.Type.Report) ||
            definition.getEffectiveType().equals(TaskDefinition.Type.GridReport)) {
                return false;
        }
        
        return true;
    }

    /**
     * From the ui display perspective currently only if the progress mode
     * is Percentage it displays with the progress percentage bar. 
     * It displays the execution time otherwise.
     * The progress mode depends on the parent mode if its there.
     * This method eventually calls the 
     * @see TaskItemDefinition#getEffectiveProgressMode() method
     * for the 'Save and execute' in Reports/monitor tasks and the 'Execute' button in reports
     * @return String progress mode
     */
    public String getProgressMode() {
    	String progressMode = ProgressMode.None.toString();
    	try {
    		TaskItemDefinition def = (TaskItemDefinition)getObject();
    		if(def != null) {
    			ProgressMode mode = def.getEffectiveProgressMode();
    			if (mode != null && mode == ProgressMode.Percentage)
    			{
    				progressMode = ProgressMode.Percentage.toString();
    			}    		
    		}
    	} catch(GeneralException ge) {
			log.info("Exception caught while trying to determine progress mode. Exception: " + ge.getMessage());
		}
       return progressMode;
    }

    /**
     * @param headerMsg the _renderHeaderMsg to set
     */
    public void setRenderHeaderMsg(boolean headerMsg) {
        _renderHeaderMsg = headerMsg;
    }

    /**
     * @return the _taskValidated
     */
    public boolean isTaskValidated() {
        return _taskValidated;
    }

    /**
     * @param validated the _taskValitaed to set
     */
    public void setTaskValidated(boolean validated) {
        _taskValidated = validated;
    }

    protected static boolean isReportDef(TaskDefinition def) {
       if ( def != null ) {
           TaskDefinition.Type effectiveType = def.getEffectiveType();
           if ( effectiveType != null ) {
               if (effectiveType.equals(TaskDefinition.Type.Report) ||
                       effectiveType.equals(TaskDefinition.Type.GridReport) ||
                       effectiveType.equals(TaskDefinition.Type.LiveReport)) {
                   return true;
               }
           }
       }
       return false;
    }

    /**
     * Merge the arguments from the task 
     */
    private Attributes<String,Object> mergeArgs(Map<String,Object> defArgs)
        throws GeneralException {

        Attributes<String,Object> args = null;
        if ( defArgs != null ) {
            args = new Attributes<String,Object>(defArgs);
        } else {
            args = new Attributes<String,Object>();
        }
        // Merge attributes coming from a custom form 
        mergeCustomAttributes(args); 
        
        Map<String,ArgValue> parameterMap = getArgMap();
        if (parameterMap != null) {
           Iterator<String> it = parameterMap.keySet().iterator();
           while (it.hasNext()) {
               String key = it.next();
               ArgValue value = parameterMap.get(key);
               if ((key != null) && (value != null)) {
                   Argument arg = value.getArgument();
                   Object v = value.getObjectValue();
                   
                   // jsl - try to be smarter about collapsing
                   // unecessary values, also need to handle
                   // the null date kludge
                   if (v instanceof String && ((String) v).length() == 0)
                       v = null;

                   if (v != null && arg != null) {
                       if (arg.isType(Argument.TYPE_BOOLEAN)) {
                           boolean b = Util.otob(v);
                           // Don't try to reduce clutter here.  There are executors which
                           // rely on task arguments to override defaults.  If we don't explicitly
                           // set a false here then the argument won't be propagated.
                       } else if (arg.isType(Argument.TYPE_INT)) {
                           v = Util.otoa(v);
                       } else if (arg.isType(Argument.TYPE_DATE)) {
                           // leave this as a Date
                           if (!value.isBound())
                               v = null;
                       } else {
                           //convert SailPoint object ids to names
                           Class<?> clazz = loadClass(arg.getType());
                           if (clazz != null) {
                               v = ObjectUtil.convertIdsToNames(getContext(), clazz, v);
                           }
                       }
                   }
                   if (v == null)
                       args.remove(key);
                   else
                       args.put(key, v);
               }
           }
        }                

        
        return args;
    }

    /**
     * This method is used to merge in attributes that were posted
     * from a custom form which uses the getCustomArgs method.
     * The difference between custom and "normal" operations is 
     * the map returned the custom map just a map and 
     * the attribute name is not required to be in the 
     * Signature.
     */ 
    private void mergeCustomAttributes(Attributes<String,Object> current) {
        if ( ( _customArgMap != null ) && ( _customArgMap.size() > 0 ) ) {
            Iterator<String> keys = _customArgMap.keySet().iterator();
            while ( keys.hasNext() ) {
                String key = keys.next();
                Object v = _customArgMap.get(key);
                if ( v != null ) {
                    if (v instanceof String && ((String) v).length() == 0)
                        v = null;
                    if ( v instanceof Boolean ) {
                        boolean b = Util.otob(v);
                        v = (b) ? "true" : null;
                    } else 
                    if ( v instanceof Integer ) {
                        int ival = Util.otoi(v);
                        v = (ival > 0) ? Util.itoa(ival) : null;
                    } 
                }
                if ( v != null ) {
                    current.put(key, v);
                } else {
                    current.remove(key);
                }
            }
        }
    }

    // 
    // Synchronous Report execution 
    // Currently applies only to reports because we don't want
    // to save the result in the db.
    // 
    //  TODO : refactor with some of the export stuff in the
    //         list bean.
    // 
    public String executeSync() {
        try {
            TaskDefinition def = (TaskDefinition)getObject();
            executeReportSync(def);
        }
        catch(GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
            log.error(e.getMessage(), e);
            setRenderHeaderMsg(true);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void executeReportSync(TaskDefinition def) {

        String tabId = getTabId();
        getSessionScope().remove(TaskDefinitionListBean.ATT_RESULT_ID + tabId);
        getSessionScope().remove(TaskDefinitionListBean.ATT_LAUNCH_ERROR + tabId);
        getSessionScope().remove(ATT_SYNC_RESULT + tabId);
        getSessionScope().remove(ATT_SYNC_ARGUMENTS + tabId);
        
        TaskResult result = null;
        String launchError = null;

        try {

            String user = getLoggedInUserName();

            Attributes<String,Object> attrs = def.getArguments();
            if ( attrs == null ) 
                attrs = new Attributes<String,Object>();
            attrs = mergeArgs(attrs);

            attrs.put(JasperExecutor.OP_LOCALE, getLocale().toString());
            attrs.put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());

            JasperExecutor jasperExecutor = null;
            ReportExportMonitor taskMonitor = new ReportExportMonitor();
            TaskManager tm = new TaskManager(getContext());
            TaskExecutor executor = tm.getTaskExecutor(def);
            if ( executor instanceof JasperExecutor ) {
                jasperExecutor = (JasperExecutor)executor;
                jasperExecutor.setMonitor(taskMonitor);
                // put it on the session so it can be used progress dialogs
                getSessionScope().put(ATT_SYNC_MONITOR + tabId, taskMonitor);
            } else {
                throw new GeneralException("Only report executors support sync execution.");
            }
            // store this on the session so we can terminate 
            getSessionScope().put(ATT_SYNC_EXECUTOR + tabId, jasperExecutor);
            // Build up a result object we can stick in the 
            // session for rendering.
            // Fake an id.. Since the result Bean requires it..
            result = new TaskResult();
            result.setId("#SYNCID#"+new Date().getTime());
            result.setType(def.getEffectiveType());
            result.setLaunched(new Date());
            result.setLauncher(user);

            String reportName = def.getName();
            if ( ( reportName == null ) || ( reportName.length() == 0 ) ) {
                reportName = def.getId(); 
            }
            result.setName(reportName);
            result.setDefinition(def);
            //
            // Call directly down to the JasperExecutor's special buildResult
            // method that will not persist the result. 
            //
            JasperResult jasper = 
                jasperExecutor.buildResult(def, attrs, getContext());
            result.setReport(jasper);
            result.setCompleted(new Date());
            result.setCompletionStatus(result.calculateCompletionStatus());
            
            getSessionScope().put(ATT_SYNC_ARGUMENTS + tabId, attrs);

        } catch (GeneralException ge) {
            // usually this is a concurrent task error, but we don't
            // have a way to distinguish that!
            log.error("GeneralException: [" + ge.getMessage() + "]", ge);
            // because the task was launched with an Ajax command
            // button, adding the error here will have no effect since
            // the editform will not be refreshed.  Have to save it
            // for later.
            launchError = ge.getMessage();
        }
        getSessionScope().put(ATT_SYNC_RESULT + tabId, result);
        getSessionScope().put(TaskDefinitionListBean.ATT_LAUNCH_ERROR + tabId, launchError);
    }

    public void terminateSync() {
        String tabId = getTabId();
        JasperExecutor executor = (JasperExecutor)getSessionScope().get(ATT_SYNC_EXECUTOR + tabId);
        if ( executor != null ) {
            executor.terminate();
        }
        getSessionScope().remove(ATT_SYNC_MONITOR + tabId);
        getSessionScope().remove(ATT_SYNC_EXECUTOR + tabId);
        getSessionScope().remove(ATT_SYNC_RESULT + tabId);
        _syncReportMonitor = null;
    }

    public String getSyncStatus() {
        String progress = null;
        if(_syncReportMonitor == null){
            _syncReportMonitor = (ReportExportMonitor) getSessionScope().get(ATT_SYNC_MONITOR + getTabId());
        }
        if (_syncReportMonitor != null) {
            if (_syncReportMonitor.hasCompleted()) {
                progress = "done";
            } else if (_syncReportMonitor.getProgress() != null) {
                progress = _syncReportMonitor.getProgress();
            }
        }
        return (progress == null) ? "" : progress;
    }
    
    public void setSyncStatus(String status) { }

    public int getSyncPercentComplete() {
        int percent = 0;
        if(_syncReportMonitor == null){
            _syncReportMonitor = (ReportExportMonitor) getSessionScope().get(ATT_SYNC_MONITOR + getTabId());
        }
        if (_syncReportMonitor != null) {
            percent = _syncReportMonitor.getPercentComplete();
        }
        return percent;
    }
    
    public void setSyncPercentComplete(int t) { }

    public String gotoViewResult() {
        String nav = null;
        String resultId = (String) getSessionScope().get(
                TaskDefinitionListBean.ATT_RESULT_ID + getTabId());
        if (resultId != null) {
            getSessionScope().put("editForm:id", resultId);
            cleanup();
//            nav = "runNow";
        }
//        TaskResult result = (TaskResult) getSessionScope().get(ATT_SYNC_RESULT + getTabId());
//        if (result != null) {
//            nav = "runNow";
//        }
        return nav;
    }

    public boolean isTransition() {
        return transition;
    }

    public void setTransition(boolean transition) {
        this.transition = transition;
    }
    
    /** Prevents the error: "The authenticated user does not have any controlled scopes and 
     * will not be able to view this object after it is created." from appearing **/
    @Override
    public boolean showScopeError() {
        return false;
    }
    
    public String getTabId() {
        if(_tabId == null){
            _tabId = "";
        }
        return _tabId;
    }
    
    public void setTabId(String tabId) {
        _tabId = tabId;
    }
}
