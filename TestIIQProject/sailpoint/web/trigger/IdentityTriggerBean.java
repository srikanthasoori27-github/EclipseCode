/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.trigger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import sailpoint.api.AsynchronousWorkflowTriggerHandler;
import sailpoint.api.CertificationTriggerHandler;
import sailpoint.api.WorkflowTriggerHandler;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.QueryOptions;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;


/**
 * A bean used to edit IdentityTriggers.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentityTriggerBean extends BaseObjectBean<IdentityTrigger> implements NavigationHistory.Page {
    private static final Log log = LogFactory.getLog(IdentityTriggerBean.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    private IdentityTriggerDTO trigger;
    private String workflowId;
    
    private List<SelectItem> workflows;
    private List<SelectItem> _processes;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public IdentityTriggerBean() {
        super();
        super.setScope(IdentityTrigger.class);
        super.setStoredOnSession(false);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // BASE OBJECT BEAN OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Create an IdentityTrigger for when we are creating a new object.
     */
    @Override
    public IdentityTrigger createObject() {
        IdentityTrigger trigger = new IdentityTrigger();
        trigger.setType(IdentityTrigger.Type.Create);
        
        try {
            trigger.setOwner(getLoggedInUser());
        }
        catch (GeneralException e) {
            throw new RuntimeException(e);
        }

        // Should this just be a null handler?
        trigger.setHandler(WorkflowTriggerHandler.class.getName());

        return trigger;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public IdentityTriggerDTO getTrigger() throws GeneralException {
        if (null == this.trigger) {
            this.trigger = new IdentityTriggerDTO(getObject());
        }
        return this.trigger;
    }
    
    public void setTrigger(IdentityTriggerDTO trigger) {
        this.trigger = trigger;
    }
    
    public String getWorkflowId() throws GeneralException {
        if (null == this.workflowId) {
            IdentityTrigger trigger = getObject();
            if (null != trigger) {
                Workflow wf = trigger.getWorkflow(getContext());
                if (null != wf) {
                    this.workflowId = wf.getId();
                }
            }
        }
        return this.workflowId;
    }
    
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public List <SelectItem> getWorkflows() throws GeneralException {
        
        if (workflows == null) {

            workflows = new ArrayList<SelectItem>();
            workflows.add(new SelectItem("", getMessage(MessageKeys.SELECT_WORKFLOW)));

            QueryOptions ops = new QueryOptions();
            ops.addOrdering("name", true);
            ops.add(Filter.eq("type", Workflow.TYPE_IDENTITY_LIFECYCLE));
            List<Workflow> flows = getContext().getObjects(Workflow.class, ops);

            for (Workflow wf : flows)
                workflows.add(new SelectItem(wf.getId(), wf.getName()));
        }

        return workflows;
    }

    public List<SelectItem> getProcesses() {
        if (null == _processes) {
            _processes = new ArrayList<>();
            Map<String,Object> bizProcessesMap = RapidSetupConfigUtils.getRapidSetupBusinessProcessConfigurationSection();
            if (!Util.isEmpty(bizProcessesMap)) {
                for(Map.Entry<String,Object> entry : bizProcessesMap.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        String name = entry.getKey();
                        String label = name;

                        //Excluding terminate option to be shown in "RapidSetup Process" under "Lifecycle Event"
                        if (Util.nullSafeCaseInsensitiveEq(Configuration.RAPIDSETUP_CONFIG_TERMINATE, name)) {
                            continue;
                        }
                        Map<String,Object> bizProcessMap = (Map<String,Object>)entry.getValue();
                        if (bizProcessMap != null) {
                            String labelStr = (String)bizProcessMap.get("label");
                            if(Util.isNotNullOrEmpty(labelStr)) {
                                label =  getMessage(labelStr);
                            }
                        }
                        _processes.add(new SelectItem(name, label));
                    }
                }
            }
        }
        return this._processes;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    
    @Override
    public String newAction() throws GeneralException {
        String returnStr = super.newAction();
        NavigationHistory.getInstance().saveHistory(this);
        return returnStr;
    }
    
    @Override
    public String editAction() throws GeneralException {
        String returnStr = super.newAction();
        NavigationHistory.getInstance().saveHistory(this);
        return returnStr;
    }
    /**
     * Action to save the identity trigger.
     */
    @Override
    public String saveAction() throws GeneralException {

        if (!validate( trigger ) ) {
            return "";
        }

        IdentityTrigger trigger = getObject();

        // if the type is RapidSetup, we need to change the handler
        if (this.trigger.getType().equals(IdentityTrigger.Type.RapidSetup)) {
            this.trigger.setHandler(AsynchronousWorkflowTriggerHandler.class.getName());
        }

        // Let the DTO copy its information into the trigger.
        this.trigger.commit(trigger);

        // Set the workflow on the trigger. If it is not a RapidSetup trigger
        if (!this.trigger.getType().equals(IdentityTrigger.Type.RapidSetup)) {
            Workflow wf = null;
            if (null != Util.getString(this.workflowId)) {
                wf = getContext().getObjectById(Workflow.class, this.workflowId);
                trigger.setWorkflow(wf);
            } else {
                addRequiredErrorMessage("triggerWfSelect");
                return "";
            }
        }

        // Save.
        getContext().saveObject(trigger);
        getContext().commitTransaction();

        return "save";
    }
    
    private boolean validate( IdentityTriggerDTO trigger ) {
        boolean response = true;
        response &= trigger.validate();
        response &= isIdentityTriggerNameUnique( trigger );
        response &= isRapidSetupProcessUnique( trigger );
        return response;
    }

    /**
     * Only one IdentityTrigger per RapidSetup Process is allowed.  This method assumes that the IdentityTrigger
     * name has already been checked for uniqueness to prevent denying saving a preexisting trigger that would
     * have the same process as the one being saved (i.e. the same IdentityTrigger)
     * @param trigger
     * @return
     */
    private boolean isRapidSetupProcessUnique( IdentityTriggerDTO trigger ) {
        boolean response = true;
        // This only applies to triggers that are of the RapidSetup type
        if (trigger.getType().equals(IdentityTrigger.Type.RapidSetup)) {
            // Build a query that finds all existing IdentityTriggers that are also a RapidSetup type
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.ignoreCase(Filter.eq("type", IdentityTrigger.Type.RapidSetup)));
            // try and find an existing Identity Trigger that matches hte same process type as the one passed in
            try {
                List<IdentityTrigger> rapidSetupTriggers = getContext().getObjects(IdentityTrigger.class, ops);
                String toSaveProcess = trigger.getProcess();
                if (!Util.isEmpty(rapidSetupTriggers)) {
                    for (IdentityTrigger rapidSetupTrigger : rapidSetupTriggers) {
                        String existingProcess = rapidSetupTrigger.getMatchProcess();
                        if (Util.nullSafeCaseInsensitiveEq(existingProcess, toSaveProcess)) {
                            // Assumption that the triggers name has been checked previously for name uniqueness.
                            // If the processes are the same this checks that this is not an update to the existing
                            // identity trigger that held this process.
                            if (!Util.nullSafeEq(trigger.getName(), rapidSetupTrigger.getName())) {
                                addRequiredErrorMessage("triggerRapidSetupProcess", MessageKeys.ERR_IDENTITY_TRIGGER_RAPIDSETUP_UNIQUE_PER_PROCESS);
                                response = false;
                            }
                        }
                    }
                }
            } catch ( GeneralException e ) {
                addMessage( new Message( Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM ), null );
                response = false;
            }
        }
        return response;
    }

    private boolean isIdentityTriggerNameUnique( IdentityTriggerDTO trigger ) {
        boolean response = true;
        /* Build a query that finds if there is another existing IdentityTrigger 
         * with the same name */
        Filter filter =  Filter.eq( "name", trigger.getName() );
        filter = Filter.and( filter, Filter.ne( "handler", CertificationTriggerHandler.class.getName() ) );
        if( trigger.getPersistentId() != null ) {
            filter = Filter.and( filter, Filter.ne( "id", trigger.getPersistentId() ) );
        }
        QueryOptions ops = new QueryOptions( filter );
        try {
            if ( getContext().countObjects( IdentityTrigger.class, ops ) > 0 ) {
                addRequiredErrorMessage( "identityTriggerName", MessageKeys.ERR_IDENTITY_TRIGGER_UNIQUE_NAME_REQUIRED );
                response = false;
            }
        } catch ( GeneralException e ) {
            addMessage( new Message( Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM ), null );
            response = false;
        }
        
        return response;
    }
    
    // Navigation History methods
    public Object calculatePageState() {
        return null;
    }

    public String getNavigationString() {
        return "triggers";
    }

    public String getPageName() {
        return "Lifecycle Triggers";
    }

    public void restorePageState(Object state) {
    }
}
