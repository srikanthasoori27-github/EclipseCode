/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for editing workflows.
 *
 * Author: Jeff
 *
 * Maintains a DTO model of the Workflow object similar
 * to what is done for Policy editing.  
 * 
 */

package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import sailpoint.api.Notary;
import sailpoint.object.ESignatureType;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.WorkItemConfig;
import sailpoint.object.Workflow;
import sailpoint.service.form.renderer.extjs.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.StateDiagram;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.FormBean;
import sailpoint.web.FormHandler;
import sailpoint.web.WorkItemConfigBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class WorkflowBean extends BaseObjectBean<Workflow> implements FormHandler.FormStore, FormBean {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(WorkflowBean.class);

    /**
     * HttpSession attribute where we store the WorkflowDTO between requests.
     */
    public static final String ATT_WORKFLOW = "WorkflowBeanSession";
    public static final String ATT_OBJECT_ID = "WorkflowId";
    
    
    /** When we want to send the serialized workflow back to the back-end from the gui in order to run the 
     * autolayout, we post it under "WorkflowJSON";
     */
    public static final String ATT_WORKFLOW_JSON = "workflowJSON";
    
    /** Sent on the request to indicate the type of workflow to obtain available steps from the registry
     */
    public static final String TYPE = "type";

    /**
     * The editing state saved on the HttpSession.
     * Unlike older pages we're going to forego a "session"
     * object and just put everything into the root DTO.
     */
    WorkflowDTO _workflowDTO;
    
    /**
     * The JSON representation of the workflow passed from the UI Workflow Editor
     */
    String _workflowJSON;
    
    /**
     * This is a generic workItemConfigBean object used for getting select lists like
     * emails and escalations for driving the options offered in the ui.
     */
    WorkItemConfigBean _workItemConfig;
    
    /** The id of the workflow that we want to delete **/
    String _workflowId;
    
    /**
     * FormBean that is used during workflow configuration
     * The Bean is initialized via ajax while editing either process variables or step configurations.
     */
    FormRenderer _formBean;
    
    /**
     * name of the configuration form 
     */
    String _configFormName;
    
    /**
     * Holds the Serialized representation of the arguments in which the form will edit
     * 
     */
    String _formArgJSON;
    
    /**
     * id of the panel that the form is being rendered to.
     * This will help generate a unique id to store the form on the session
     */
    String _configFormPanelId;
    
    /**
     * Id's of all forms stored on the session
     * This will help clear the session
     */
    Set<String> _sessionFormNames;
    
    /**
     * UI Context with which the form is rendering to
     *
     */
    public static enum ConfigFormContext {
        Workflow,
        Step
    }
    ConfigFormContext _configFormContext;
    
    public static class CurrentForm {
        
        /**
         * Name of the form
         */
        public String _formName;
        
        /**
         * If we are in context of a step, we will hold the step name here
         */
        
        public String _formStepName;
        /**
         * Context of the workflow Form
         * @see "sailpoint.web.workflow.WorkflowBean.ConfigFormContext"
         */
        
        public Enum formContext;
        
        /**
         * JSON representation of form arguments
         */
        public String _formArgJSON;
        
        /**
         * Id of the step/workflow that the form belongs
         * Helps generate a unique id if multiple steps use the same form
         */
        public String _formPanelId;
        
        public CurrentForm(String name, Enum context, String stepName, String argJSON, String formPanelId) {
            this._formName = name;
            this.formContext = context;
            this._formStepName = stepName;
            this._formArgJSON = argJSON;
            this._formPanelId = formPanelId;
        }

        /**
         * May want to use sailpoint.web.workflow.WorkflowBean.getFormNameFromSession()
         * Using getFormNameFromSession will take care of null checks in the getCurrentConfigFOrmFromSession
         * @return
         */
        public String getFormName() {
            return _formName;
        }

        public void setFormName(String _formName) {
            this._formName = _formName;
        }
        
        public Enum getFormContext() {
            return formContext;
        }
        
        public void setFormContext(Enum context) {
            this.formContext = context;
        }
        
        public String getFormStepName() {
            return this._formStepName;
        }
        
        public void setFormStepName(String name) {
            this._formStepName = name;
        }

        public String getFormArgJSON() {
            return _formArgJSON;
        }

        public void setFormArgJSON(String _formArgJSON) {
            this._formArgJSON = _formArgJSON;
        }
        
        /**
         * This is used to keep a unique name for the session Form
         */
        public String getVarPanelName() {
            return this._formPanelId;
        }
    }

    /**
     * The name of the step which is being edited. 
     */
    String _configFormStepName;

    // Session attributes that hold the master and expanded form.
    public static final String ATT_MASTER_FORM = "WorkflowBean.masterForm.";
    public static final String ATT_EXPANDED_FORM = "WorkflowBean.expandedForm.";
    public static final String ATT_CURRENT_FORM_NAME = "WorkflowBean.currentForm";
    public static final String ATT_WORKFLOW_SESSION_FORMS = "WorkflowBean.sessionForms";    

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public WorkflowBean() {
        super();
        setScope(Workflow.class);
        _workItemConfig = new WorkItemConfigBean(new WorkItemConfig());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Session Management
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * BaseObjectBean subclasses traditionally use foo.object to 
     * get to the real SailPointObject being edited.  Not sure
     * it's wise to overload that so we have a different accessor
     * for the DTO.
     * 
     * Hmm, does workflow.dto look better than workflow.workflow?
     */
    public WorkflowDTO getSession() {
        if (_workflowDTO == null) {
            try {
                Map hsession = getSessionScope();
                _workflowDTO = (WorkflowDTO)hsession.get(ATT_WORKFLOW);
                if (_workflowDTO == null) {
                    Workflow workflow = getObject();
                    // subclass must overload this
                    _workflowDTO = new WorkflowDTO(workflow);
                    

                    saveSession();
                }
            } 
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error("Unable to restore policy session. Exception: " + t.getMessage(), t);
            }
        }
        return _workflowDTO;
    }

    public void saveSession() {
        // BaseObjectBean should handle the root object
        Map ses = getSessionScope();
        ses.put(ATT_OBJECT_ID, getObjectId());
        ses.put(ATT_WORKFLOW, _workflowDTO);
    }
    
    public void cancelSession() {
        // BaseObjectBean should handle the root object
        Map ses = getSessionScope();
        ses.remove(ATT_WORKFLOW);
        ses.remove(ATT_OBJECT_ID);
    }
    
    protected void restoreObjectId() {

        Map ses = getSessionScope();

        // list page will set this initially, thereafter we keep it
        // refreshed as we transition among our pages
        String id = (String)ses.get(ATT_OBJECT_ID);
        if (id != null) {
            setObjectId(id);
        }
        
    }
    
    /**
     * Overload from BaseObjectBean called to create a new object.
     */
    @Override
    public Workflow createObject() {

        return new Workflow();
    }
    
    /**
     * @see #getFormBean()
     * @see sailpoint.web.FormBean#getFormRenderer(java.lang.String)
     */
    @Override
    public FormRenderer getFormRenderer(String formId) throws GeneralException {
        //Because we have the current form stored on the session, go ahead and use this
        return getFormBean(getFormNameFromSession());
    }

    /**
     * Fetches the arguments from the workflowDTO object. A _configFormContext
     * must be set on the bean to determine if the arguments are being fetched 
     * from a workflow or step.
     * 
     * @return the key, value pair of arguments to assign to the form
     * @see sailpoint.web.FormBean#getFormArguments()
     */
    @Override
    public Map<String,Object> getFormArguments() throws GeneralException {
        Map<String, Object> returnMap = new HashMap<String, Object>();
        
        if (_workflowDTO == null) {
            // error condition do nothing
            return returnMap;
        }
        
        returnMap.put("workflowType", _workflowDTO.getType());
        returnMap.put("workflowName", _workflowDTO.getName());
        returnMap.put("workflowDescription",  _workflowDTO.getDescription());
        returnMap.put("workflowTaskType", _workflowDTO.getTaskType());
        returnMap.put("workflowOwner", _workflowDTO.getOwner());
        // djs: put this lower, what about timezone...
        returnMap.put("locale", getLocale());        
        
        //Possibly use the workflowBean variable if available?
        if (getCurrentConfigFormFromSession() != null) {
            
            CurrentForm currentForm = getCurrentConfigFormFromSession();
            
            switch ((ConfigFormContext)currentForm.getFormContext()) {
            case Workflow:
                
                //Test to see if CurrentForm.argJSON is present
                //If so use this, otherwise fall back to server side variables
                if(currentForm.getFormArgJSON() != null) {
                    //Use the serialized args sent from the client to populate
                    List<Map<String, Object>> args = JsonHelper.listOfMapsFromJson(String.class, Object.class, currentForm.getFormArgJSON());
                    for(Map arg : args) {
                        String source = normalizeScriptSource("initializerSource", "initializerMethod", arg);
                        if (source != null) {
                            returnMap.put((String)arg.get("name"), source);
                        }
                    }
                }else {
                    log.warn("Unable to find form workflow arguments json on session. Falling back to server side representation");
                    List<VariableDTO> variables = _workflowDTO.getVariables();
                    for (VariableDTO varDTO : Util.safeIterable(variables)) {                        
                        if (varDTO != null) {
                            ScriptDTO scriptDTO = varDTO.getInitializer();
                            String source = normalizeScriptSource(scriptDTO);
                            if (source != null) { 
                                returnMap.put(varDTO.getName(), source);
                            }
                        }
                        
                    }
                }
                break;
            case Step:
                // Put a copy of the step DTO into the args so the config form fields can 
                // use it if necessary.
                StepDTO stepDTO = getStepDTO();
                if ( stepDTO != null ) {
                    StepDTO copy = new StepDTO(stepDTO);
                    returnMap.put("stepDTO", copy );
                } else {
                    StepDTO empty = new StepDTO();
                    returnMap.put("stepDTO",empty);
                }
                
                if (Util.isNotNullOrEmpty(currentForm.getFormStepName())) {
                    if(currentForm.getFormArgJSON() != null) {
                        //Use serialized arg representation sent from client to populate form
                        List<Map<String, Object>> args = JsonHelper.listOfMapsFromJson(String.class, Object.class, currentForm.getFormArgJSON());
                        for(Map arg : args) {
                            String source = normalizeScriptSource("valueSource", "valueMethod", arg);
                            if (source != null) {
                                returnMap.put((String)arg.get("name"), source);
                            }
                        }
                    } else {
                        log.warn("Unable to find form step arguments json on session. Falling back to server side representation");
                        if (stepDTO != null) {
                            List<ArgDTO> args = stepDTO.getArgs();
                            for (ArgDTO argDTO : Util.safeIterable(args)) {
                                if (argDTO != null) {
                                    ScriptDTO scriptDTO = argDTO.getValue();
                                    String source = normalizeScriptSource(scriptDTO);
                                    if (source != null) {
                                        returnMap.put(argDTO.getName(), source);
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
        log.debug("returning map : " + returnMap);
        return returnMap;
    }
    
    /**
     * Iterates through the keys of the map, if a matching field in the form is multi-valued 
     * and the format is not CSV then convert the csv string into a list.  It becomes very important
     * when expanding the form that the value is a list. Formicator.shouldExpand(Field) depends heavily
     * on the previousValue and the value of the field being equal.  Thus let's convert this to a list and push it 
     * along the form expansion highway.
     * @param form we need to derive if field definitions are multi from somewhere
     * @param map the map with which we are converting values
     */
    private void csvToList(Form form, Map<String, Object> map) {
        if (form != null && map != null) {
            Set<String> keys = map.keySet();
            for (String key : Util.safeIterable(keys)) {
                Field field = form.getField(key);
                if (field != null && field.isMulti() && !Field.FORMAT_CSV.equals(field.getFormat())) {
                    Object value = map.get(key);
                    if (value instanceof String) {
                        map.put(key, Util.csvToList((String)value));
                    }
                }
            }
        }
    }
   
    private String normalizeScriptSource(String sourceKey, String methodKey, Map from) {
        String source = Util.otos(from.get(sourceKey));
        if (source != null) {
            String method = Util.otos(from.get(methodKey));
            // variables by default are string, if it is not a string then add the method
            if (method != null && !"string".equalsIgnoreCase(method)) {
                source = method + ":" + source;
            }
            //co-erce empty to null
            if(source.isEmpty())
                return null;
        }
        return source;
    }

    //This should probably be in the ScriptDTO itself. ScriptDTO.getScriptlet is very similar
    private String normalizeScriptSource(ScriptDTO scriptDTO) {
        String source = null;
        if (scriptDTO != null) {
            source = scriptDTO.getSource();
            if (source != null) {
                String method = scriptDTO.getMethod();
                if (!scriptDTO.isLiteral()) {
                    source = method + ":" + source;
                    if (scriptDTO.isNegate()) {
                        source = "!" + source;
                    }
                }
            }
        }
        return source;
    }

    /** Not implemented
     * @see sailpoint.web.FormBean#getFormBeanState()
     */
    @Override
    public Map<String,Object> getFormBeanState() {
        return null;
    }
    
    public Set<String> getSessionFormNames() {
        return (Set<String>)super.getSessionScope().get(ATT_WORKFLOW_SESSION_FORMS);
    }
    
    /**
     * Add a formName to the list of formNames we have stored on the session.
     * We use this list to clear the session upon changing workflows in the BPE
     */
    public void addFormNameToSession(String name) {
        Set<String> sessForm;
        if(getSessionFormNames() != null) {
            sessForm = getSessionFormNames();
            sessForm.add(name);
        } else {
            sessForm = new HashSet<String>();
            sessForm.add(name);
        }
        setSessionFormNames(sessForm);
    }
    
    public void removeFormNameFromSession(String name) {
        if (getSessionFormNames() != null) {
            Set<String> sessForm = getSessionFormNames();
            sessForm.remove(name);
            setSessionFormNames(sessForm);
        }
    }
    
    public void setSessionFormNames(Set<String> formNames) {
        clearSessionFormNames();
        super.getSessionScope().put(ATT_WORKFLOW_SESSION_FORMS, formNames);
    }
    
    public void clearSessionFormNames() {
        super.getSessionScope().remove(ATT_WORKFLOW_SESSION_FORMS);
    }
    
    public void clearFormsFromSession() {
        Set<String> sessForms = getSessionFormNames();
        if(sessForms != null) {
            for(String s : sessForms) {
                super.getSessionScope().remove(s);
            }
            clearSessionFormNames();
        }
    }

    /** Store the master form on the session. Because we are storing SailPoint Objects here, 
     * we need to try and remove  before we put. This is a limitation in the JSF 2.1 SessionMap. 
     * SessionMap.put checks that the new object is not equal to the old object before putting. Because
     * our SailPointObject overrides equals, and only uses id/name to check equals, this is failing.
     * @see sailpoint.web.FormHandler.FormStore#storeMasterForm(sailpoint.object.Form)
     */
    @Override
    public void storeMasterForm(Form form) {
        String sessionFormId = generateSessionFormName(FormHandler.FormTypes.MASTER.toString(), form.getName(), getVarPanelFromSession());
        super.getSessionScope().remove(sessionFormId);
        super.getSessionScope().put(sessionFormId, form);
        addFormNameToSession(sessionFormId);
    }

    /**
     * Get the master form from the session based on the CurrentForm stored on the session
     * @see sailpoint.web.workflow.WorkflowBean#retrieveMasterForm(String)}
     */
    @Override
    public Form retrieveMasterForm() {
        return retrieveMasterForm(getFormNameFromSession());
    }
    
    public Form retrieveMasterForm(String formId) {
        String sessionFormId = generateSessionFormName(FormHandler.FormTypes.MASTER.toString(), formId, getVarPanelFromSession());
        return (Form)super.getSessionScope().get(sessionFormId);
    }

    /** Clear the Master Form that we have stored in the CurrentForm from the session.
     * @see sailpoint.web.FormHandler.FormStore#clearMasterForm()
     */
    @Override
    public void clearMasterForm() {
        String currForm = generateSessionFormName(FormHandler.FormTypes.MASTER.toString(), getFormNameFromSession(), getVarPanelFromSession());
        super.getSessionScope().remove(currForm);
    }

    /** Store the Expanded Form on the session
     * This Generates a unique key for the session map based on The form name and the context in which the form is displayed in the BPE
     * @see sailpoint.web.FormHandler.FormStore#storeExpandedForm(sailpoint.object.Form)
     */
    @Override
    public void storeExpandedForm(Form form) {
        String sessionFormId = generateSessionFormName(FormHandler.FormTypes.EXPANDED.toString(), form.getName(), getVarPanelFromSession());
        super.getSessionScope().remove(sessionFormId);
        super.getSessionScope().put(sessionFormId, form);
        addFormNameToSession(sessionFormId);
    }

    /**
     * Get the expanded form from the session based on the CurrentForm stored on the session
     * @see sailpoint.web.FormHandler.FormStore#retrieveExpandedForm()
     */
    @Override
    public Form retrieveExpandedForm() {
        return retrieveExpandedForm(getFormNameFromSession());
    }

    public Form retrieveExpandedForm(String formId) {
        String sessionFormId = generateSessionFormName(FormHandler.FormTypes.EXPANDED.toString(), formId, getVarPanelFromSession());
        return (Form)super.getSessionScope().get(sessionFormId);
    }

    /** Clear the expanded form from the session. This uses information stored in the CurrentForm on the session to 
     * generate the formId to be removed
     * @see sailpoint.web.FormHandler.FormStore#clearExpandedForm()
     */
    @Override
    public void clearExpandedForm() {
        String currForm = generateSessionFormName(FormHandler.FormTypes.EXPANDED.toString(), getFormNameFromSession(), getVarPanelFromSession());
        super.getSessionScope().remove(currForm);
    }
    
    /**
     * Stores the current form information on the session for retrieval upon form action
     * @param name - Name of the current form being displayed
     * @param configFormContext - Context of the current form @see this.ConfigFormContext
     * @param stepName - In the case of step context, this will hold the name of the step the form is associated
     * @param argJSON - The serialized arguments of the current form/workflow sent from the client to be used as formArgs
     * @param formPanelId - The id of the step/workflow that this form is being used.
     */
    public void storeCurrentConfigFormOnSession(String name, ConfigFormContext configFormContext, String stepName, String argJSON, String formPanelId) {
        clearCurrentConfigForm();
        getSessionScope().put(WorkflowBean.ATT_CURRENT_FORM_NAME, new CurrentForm(name, configFormContext, stepName, argJSON, formPanelId));
    }
    
    public CurrentForm getCurrentConfigFormFromSession() {
        return (CurrentForm)getSessionScope().get(WorkflowBean.ATT_CURRENT_FORM_NAME);
    }
    
    public void clearCurrentConfigForm() {
        getSessionScope().remove(WorkflowBean.ATT_CURRENT_FORM_NAME);
    }
    
    protected void clearConfigForm() {
        String sessionFormName = getFormNameFromSession();
        String masterFormName = generateSessionFormName(FormHandler.FormTypes.MASTER.toString(), sessionFormName, getVarPanelFromSession());
        String expandedFormName = generateSessionFormName(FormHandler.FormTypes.EXPANDED.toString(), sessionFormName, getVarPanelFromSession());
        this.clearExpandedForm();
        this.clearMasterForm();
        this.removeFormNameFromSession(expandedFormName);
        this.removeFormNameFromSession(masterFormName);
        // order is of removing session variables is important.  clearMaster,clearExpanded references CurrentConfigForm so we need to clearCurrentConfigForm last
        this.clearCurrentConfigForm();
    }
    
    /**
     * Convenience method to get the form name from the session map entry holding the CurrentForm
     * If there is not CurrentForm on the session, return empty string
     * 
     * @return Name of the Form stored in the CurrentForm on the session
     */
    public String getFormNameFromSession() {
        CurrentForm cf = getCurrentConfigFormFromSession();
        if(cf != null) {
            return cf.getFormName();
        }
        return "";
    }
    
    /**
     * Convenience method to get the panel name from the session map holding the Current Form
     * If there is not CurrentForm on the session, return emptyString
     * @return
     */
    public String getVarPanelFromSession() {
        CurrentForm cf = getCurrentConfigFormFromSession();
        if(cf != null) {
            return cf.getVarPanelName();
        }
        return "";
    }
    
    public String getStepsJson() {
        String json = null;
        try {
            List<WorkflowDTO> templates = new ArrayList<WorkflowDTO>();
            
            String templateId = getRequestParameter(TYPE);

            templates.addAll(WorkflowUtil.resolveStepLibrary(templateId, getContext()));
            
            json = WorkflowUtil.getStepOptionsJson(templates, getContext());
        } catch(GeneralException ge) {
            if (log.isErrorEnabled())
                log.error(ge.getMessage(), ge);

            json = "{}";
        } catch(JSONException e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);

            json = "{}";
        }
        return json;
    }
    
    /** On the workflow editor, we load the workflow as json so we can instantiate the editor
     * for that workflow
     * @return
     */
    public String getJson() {
        String json = null;
        if(getRequestParameter("forceLoad")!=null && Util.atob(getRequestParameter("forceLoad"))){
            cancelSession();
        }
        try {
            if (getSession() != null)
                json = WorkflowUtil.getWorkflowJson(getSession(), getContext());
        } catch(GeneralException ge) {
            if (log.isErrorEnabled())
                log.error(ge.getMessage(), ge);
            
            json = "{}";
        } catch (JSONException e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
            
            json = "{}";
        }
        saveSession();
        return json;
    }
    
    /**
     * Same as getJson, but needed a way to determine this is coming from restoreFromHistory or from BPE
     * @return
     */
    public String getJsfJson() {
        //Clear any forms we may have stored on the session
        clearFormsFromSession();
        //Clear the reference to the currentForm
        clearCurrentConfigForm();
        return getJson();
    }
    
    /** Takes the workflow json from the gui, builds a workflow, lays it out using a state diagram, then sends it back to the ui **/
    public String getAutoLayoutJson() {
        String json = getRequestParameter(ATT_WORKFLOW_JSON);
        
        String updatedJson = null;
        
        if(json!=null) {
            try {
                WorkflowDTO dto = WorkflowUtil.updateWorkflowFromJSON(json, getSession());
                XMLObjectFactory factory = XMLObjectFactory.getInstance();
                
                if(dto!=null) {
                    Workflow workflow = new Workflow();
                    dto.commit(workflow);
                    if(log.isInfoEnabled())
                    	log.info("Before: " +factory.toXml(workflow));
                    StateDiagram diagram = workflow.getStateDiagram();
                    diagram.setNodeWidth(30);
                    diagram.setNodeHeight(100);
                    diagram.setIncludeLabels(true);
                    workflow.layout(diagram);

                    if(log.isInfoEnabled())
                    	log.info("After: " +factory.toXml(workflow));
                    
                    WorkflowDTO updatedDto = new WorkflowDTO(workflow);
                    
                    //Restore step IDs so we can match them back client side
                    restoreStepIds(dto.getSteps(), updatedDto);
                    
                    updatedJson = WorkflowUtil.getWorkflowJson(updatedDto, getContext());
                }
                
            }   catch (Throwable t) {
                // probably not recoverable but stay
                if (log.isErrorEnabled())
                    log.error(t.getMessage(), t);
                
                addMessage(new Message("err_fatal_system"));
            }
        }
        return updatedJson;
    }
    
    public String deleteWorkflowAction() throws GeneralException {
        if(getWorkflowId()!=null) {
            Workflow workflow = getContext().getObjectById(Workflow.class, _workflowId);
            if(workflow!=null) {
                getContext().removeObject(workflow);
                getContext().commitTransaction();
                clearWorkflowAction();
            }
        }
        return "";
    }
   
    
    public String clearWorkflowAction() {
        cancelSession();
        String key = getSessionKey();
        getSessionScope().remove(key);
        return "";
    }
    
    public String saveWorkflowAction() {
        restoreObjectId();
        try {

            if (log.isDebugEnabled())
                log.debug(getWorkflowJSON());

            _workflowDTO = WorkflowUtil.updateWorkflowFromJSON(getWorkflowJSON(), getSession());
            Workflow dest = getObject();
            _workflowDTO.commit(dest);
            // Ensure no errors exist in Workflow before trying to save
            if (!verifySaveWorkflowAction()) {
                // This duplicate name checking
                super.saveAction();

                if (log.isDebugEnabled()) {
                    XMLObjectFactory factory = XMLObjectFactory.getInstance();
                    log.debug(factory.toXml(dest));
                }
                saveSession();
            }

        } catch (Throwable t) {
            // probably not recoverable but stay
            if (log.isErrorEnabled())
                log.error(t.getMessage(), t);

            addMessage(new Message("err_fatal_system"));
        }
        return "";
    }
    
    public String saveAsWorkflowAction() {
        restoreObjectId();
        
        try {

            if (log.isDebugEnabled())
                log.debug(getWorkflowJSON());
            
            // Like save, we'll start with the original object and then
            // merge the values back into the cloned version
            Workflow existing = getObject();
            // Do a deep clone, remove the persistent things and name
            Workflow saveAs = (Workflow)existing.derive(getContext());            
            
            // create a new DTO to persist the changes
            _workflowDTO = WorkflowUtil.updateWorkflowFromJSON(getWorkflowJSON(), getSession());            
            // commit UI object to new saveAs object
            _workflowDTO.commit(saveAs);            
            // ensure the new object is committed to the session
            setObject(saveAs);

            // Ensure no errors exist in Workflow before trying to save
            if (!verifySaveWorkflowAction()) {
                // This duplicate name checking
                super.saveAction();
                // set the object id so save Session will store the new object id to the session
                setObjectId(saveAs.getId());

                if (log.isDebugEnabled()) {
                    XMLObjectFactory factory = XMLObjectFactory.getInstance();
                    log.debug(factory.toXml(saveAs));
                }
                saveSession();
            }

        } catch (Exception t) {
            // probably not recoverable but stay
            if (log.isErrorEnabled())
                log.error(t.getMessage(), t);

            addMessage(new Message("err_fatal_system"));
        }
        return "";
    }
    
    /**
     * reads the _configFormName and initializes the form bean.  This action
     * is dynamic and changes based on the BPE context if editing
     * a step or a workflow. 
     * @return empty string, at this time do not forward anywhere
     * @throws GeneralException
     */
    public String updateConfigFormAction() throws GeneralException {
        if (Util.isNotNullOrEmpty(getConfigFormName())) {
            // switching from advanced to basic view now calls updateConfigForm, so let's clear
            // the existing expanded and master form so it may be regenerated based off posted data, i.e. formArgJSON
            clearExpandedForm();
            clearMasterForm();
            //Because this is a request scoped bean, the configFormName will be lost upon next submit
            storeCurrentConfigFormOnSession(getConfigFormName(), getConfigFormContext(), getConfigFormStepName(), getFormArgJSON(), getConfigFormPanelId());
            getFormBean(getConfigFormName());
        }
        
        return null;
    }
    
    public String getConfigFormName() {
        return _configFormName;
    }

    public void setConfigFormName(String configFormName) {
        this._configFormName = configFormName;
    }
    
    public ConfigFormContext getConfigFormContext() {
        return _configFormContext;
    }

    public void setConfigFormContext(ConfigFormContext configFormContext) {
        this._configFormContext = configFormContext;
    }

    public String getConfigFormStepName() {
        return _configFormStepName;
    }

    public void setConfigFormStepName(String configFormStepName) {
        this._configFormStepName = configFormStepName;
    }

    public String getConfigFormPanelId() {
        return _configFormPanelId;
    }

    public void setConfigFormPanelId(String _configFormPanelId) {
        this._configFormPanelId = _configFormPanelId;
    }

    public FormRenderer getFormBean() {
            try {
                if (null == _formBean || _formBean.getForm() == null) {
                
                    String formName = getFormNameFromSession();       
                    // First, try to retrieve the expanded form.
                    Form expanded = this.retrieveExpandedForm(formName);
                    
                    // If the expanded form could not be retrieved, we need to
                    // create it from the master.
                    if (null == expanded) {
                        // Try to grab the master form if we already have one.
                        Form master = this.retrieveMasterForm(formName);
        
                        // No master in the session, let's build one and store it
                        // in the session.
                        if (null == master) {
                            //Create a form bean without an associated form. Will need to call init at a later time
                            _formBean = new FormRenderer(this, getLocale(),
                                                             getContext(), getUserTimeZone());
                            return _formBean;
                        }
        
                        // Use the form handler to build the expanded form from the
                        // master.  This also stores the expanded form in the FormStore.
                        FormHandler handler = new FormHandler(getContext(), this);
                        expanded = handler.initializeForm(master, true, getFormArguments());
                        
                        
                    }
                    
                    // Now create the renderer using the expanded form.
                    _formBean = new FormRenderer(expanded, this, getLocale(),
                                                     getContext(), getUserTimeZone());
                }
        } catch (GeneralException e) {
            handleFormException(e);
        }

        return _formBean;
    }

    /**
     * Get the JSF backing FormBean for the current Form.
     * @param formName - Name of the form in which we are instantiating this formBean for
     * @return
     * @throws GeneralException
     */
    public FormRenderer getFormBean(String formName) {
        
        try {
            // First, try to retrieve the expanded form.
            Form expanded = this.retrieveExpandedForm(formName);
            
            // If the expanded form could not be retrieved, we need to
            // create it from the master.
            if (null == expanded) {
                // Try to grab the master form if we already have one.
                Form master = this.retrieveMasterForm(formName);

                // No master in the session, let's build one and store it
                // in the session.
                if (null == master) {
                    master = createMasterForm(formName);
                    //Because these forms are persisted client side, we want to include the hidden fields instead
                    //of stripping them out of the form in the Formicator
                    master.setIncludeHiddenFields(true);
                }

                // Use the form handler to build the expanded form from the
                // master.  This also stores the expanded form in the FormStore.
                FormHandler handler = new FormHandler(getContext(), this);
                Map<String, Object> formArgMap = getFormArguments();
                csvToList(master, formArgMap);
                expanded = handler.initializeForm(master, true, formArgMap);
            }
            
            // Now create the renderer using the expanded form.
            _formBean = new FormRenderer(expanded, this, getLocale(),
                                             getContext(), getUserTimeZone());
        } catch (GeneralException e) {
            handleFormException(e);
        }

        return _formBean;
    }
    
    /**
     * Adds a message to the faces context, log the exception and clear the config form from the session. Clearing the form from the session allows the user 
     * to edit the form from the debug page and upon new visit to the form,  rerender the form instead of pulling the form from the session. 
     */
    private void handleFormException(GeneralException e) {
        Message formError = new Message(Message.Type.Error, MessageKeys.WORKFLOW_ERROR_FORM_GENERATION_FAILED);
        addMessage(formError);
        log.error(formError.getLocalizedMessage(getLocale(), getUserTimeZone()), e);
        this.clearConfigForm();
    }

    public String getFormArgJSON() {
        return _formArgJSON;
    }

    public void setFormArgJSON(String _formArgJSON) {
        this._formArgJSON = _formArgJSON;
    }

    /**
     * Get the master form and populate it with data from the object being
     * edited.
     * 
     * Note: this should only be called once for the lifespan of this form
     * handling session.  Subsequently, the master form should be accessed
     * using the retrieveMasterForm() method.
     */
    private Form createMasterForm(String formName) throws GeneralException {
        
        // The first step is to retrieve the master form from where it is
        // stored.  This may be a top-level Form object, a Form built
        // on-the-fly by code, a Form stored within a WorkItem, etc...
        Form form = getContext().getObjectByName(Form.class, formName);
        
        if (null == form) {
            addMessage(new Message(Message.Type.Error, MessageKeys.WORKFLOW_ERROR_FORM_NOT_FOUND, formName));
            // return a new form object so we don't formicate all over ourselves 
            return new Form();
        }
        
        // Always clone the form because it is going to be modified.
        form = (Form) form.deepCopy((XMLReferenceResolver) getContext());

        return form;
    }

    /**
     * Validate the Workflow Object before trying to save. This will prevent
     * any invalid data from making to the backend.
     * 
     * @return Return false if there are no errors, true if errors exist
     * @throws Exception
     */
    public boolean verifySaveWorkflowAction() throws Exception {
        boolean error = false;
        if ( getObject() != null ) {
            //Check for empty name
            String value = getObject().getName();
            if ( value == null || value.trim().length() == 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.NAME_REQUIRED), null);
                error = true;
            }
            
            // check for safe name
            if (!WebUtil.isSafeValue(value)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.NAME_UNSAFE), null);
                error = true;
            }

            if (!verifyVariables()) {
                error = true;
            }
            
            //If creating new workflow, check for existing workflow with the same name
            if (getObject().getId() == null) {
                try {
                    Workflow existingWf = getContext().getObjectByName(Workflow.class, value);
                    if (existingWf != null) {
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.WORKFLOW_NONUNIQUE_NAME, value), null);
                        error = true;
                    }
                } catch (GeneralException e) {
                    addMessage(new Message(Message.Type.Error,
                            MessageKeys.ERR_FATAL_SYSTEM, e), null);
                    log.error("Unable to save workflow object", e);
                }
            }
        }
        return error;
    }

    private boolean verifyVariables() throws GeneralException {
        boolean valid = true;

        Workflow workflow = getObject();
        if (workflow != null) {
            List<Workflow.Variable> variableDefinitions = workflow.getVariableDefinitions();
            if (variableDefinitions != null) {
                for (Workflow.Variable variableDefinition : variableDefinitions) {
                    valid = verifyVariable(variableDefinition) && valid;
                }
            }
        }

        return valid;
    }
    
    private boolean verifyVariable(Workflow.Variable variable) throws GeneralException {
        boolean valid = true;

        if (variable != null) {
            // check for safe name
            if (!WebUtil.isSafeValue(variable.getName())) {
                addMessage(new Message(Message.Type.Error, MessageKeys.NAME_UNSAFE), null);
                valid = false;
            }
        }

        return valid;
    }
    
    public String getWorkflowJSON() {
        return _workflowJSON;
    }

    public void setWorkflowJSON(String _workflowjson) {
        _workflowJSON = _workflowjson;
    }
    
    public List<String> getApprovalModeOptions() {
        List<String> approvalModes = new ArrayList<String>();
        approvalModes.add(Workflow.ApprovalModeSerial);
        approvalModes.add(Workflow.ApprovalModeSerialPoll);
        approvalModes.add(Workflow.ApprovalModeParallel);
        approvalModes.add(Workflow.ApprovalModeParallelPoll);
        approvalModes.add(Workflow.ApprovalModeAny);
        return approvalModes;
    }


    public WorkItemConfigBean getWorkItemConfig() {
        return _workItemConfig;
    }

    public void setWorkItemConfig(WorkItemConfigBean itemConfig) {
        _workItemConfig = itemConfig;
    }    

    public String getWorkflowId() {
        return _workflowId;
    }

    public void setWorkflowId(String workflowId) {
        _workflowId = workflowId;
    }

    /**
     * This nonsense is necessary to support edits from the search pages
     */
    @Override
    public void restoreFromHistory() {
        super.restoreFromHistory();
        Map session = getSessionScope();
        boolean forceLoad = Util.otob(getRequestParam().get(FORCE_LOAD));
        if (!forceLoad) {
            forceLoad = Util.otob(session.get(FORCE_LOAD));
        }
        
        if (!forceLoad) {
            restoreObjectId();
            setScope(Workflow.class);
            String sessionKey = getSessionKey();
            if (sessionKey != null) {
                _workflowJSON = getJson();
            }
        }
    }
    
    /**
     * Sets the step ids in a workflow to that of the original workflow.
     * This is used primarily for autoLayout so that client side can match
     * the new workflow steps to the workflow steps before calling auto layout
     * 
     * @param origSteps StepDTOs from the original workflowDTO before calling layout
     * @param newWF The new workflowDTO created for autoLayout
     */
    private void restoreStepIds(List<StepDTO> origSteps, WorkflowDTO newWF) {
        List<StepDTO> newSteps = newWF.getSteps();
        for(StepDTO step : origSteps) {
            for(StepDTO newStep : newSteps) {
                if(newStep.getName().equals(step.getName())) {
                    newStep.setUid(step.getUid());
                    break;
                }
            }
            
        }
    }
    
    /**
     * Get a list of the Esignatures defined in the system.  These 
     * are displayed under the WorkItemConfig panel and allows
     * users to select a signature for use on the approval step.
     *
     * @return
     * @throws GeneralException
     */
    public List<SelectItem> getEsignatures() throws GeneralException {
    
        List<SelectItem> items = new ArrayList<SelectItem>();
        	
    	Notary notary = new Notary(getContext(), getLocale());
        List<ESignatureType> types = notary.getESignatureTypes();

        for ( ESignatureType type : types  ) {
            items.add(new SelectItem(type.getName(), type.getDisplayableName()));
        }
        
        return items;
    }
    
    
    public String submit() throws GeneralException {
        String transition = null;
        
        FormRenderer formBean = getFormBean();
        String action = formBean.getAction();
        
        FormHandler handler = new FormHandler(getContext(), this);
        
        // Switch based on the action from the POST.
        if (Form.ACTION_REFRESH.equals(action)) {
            
            // Let the handler refresh the master form with the data that
            // was posted.  This will cause the expanded form to be cleared
            // so that it will be regenerated when the page is re-rendered.
            handler.refresh(this.retrieveMasterForm(formBean.getForm().getName()), formBean, getFormArguments());
            
            // Go back to this page.
            transition = null;
        }
        else if (Form.ACTION_NEXT.equals(action) || Form.ACTION_BACK.equals(action)) {

            boolean valid =
                handler.submit(this.retrieveMasterForm(formBean.getForm().getName()), formBean, getFormArguments());
            if (!valid) {
                // Just return to the page and redisplay the existing form with
                // the error messages.
                transition = null;
                return transition;
            }
            //FormHandler clears the Expanded Form. Go ahead and clear the master as well since we are done with this form for now
            //The master and expanded will be rebuilt if the user decides to edit again
            //this.clearMasterForm();
        }
        else if (Form.ACTION_CANCEL.equals(action)) {
            this.clearConfigForm();
        }
        else {
            // This is some unknown action.  Log a warning if the action is not known.
            log.warn("Unknown form action: " + action);

            // Clear everything and bail!
            this.clearExpandedForm();
            this.clearMasterForm();
            transition = NAV_OUTCOME_HOME;
        }
        
        return transition;
    }
    
    /**
     * Generate a unique key to store the form on the session
     * @param type - Type of the Form @see "sailpoint.web.FormHandler.FormTypes"
     * @param formName - Name of the form
     * @param id - id of the step/workflow containing the form. Have to use this in case
     *              multiple steps reference the same form
     * @return
     */
    protected String generateSessionFormName(String type, String formName, String id) {
        String sessionFormName;
        if(type.equals(FormHandler.FormTypes.MASTER.toString())) {
            sessionFormName = WorkflowBean.ATT_MASTER_FORM + formName + id;
        } else {
            sessionFormName = WorkflowBean.ATT_EXPANDED_FORM + formName + id;
        }
        return sessionFormName;
    }
    
    /**
     * Find the DTO for the current form step so it can be used in the
     * configuration form.
     * 
     * @return the StepDTO that matches the current step config form
     * 
     * @throws GeneralException
     */
    private StepDTO getStepDTO() throws GeneralException {        
        List<StepDTO> steps = _workflowDTO.getSteps();
        for (StepDTO stepDTO : Util.safeIterable(steps)) {
            if (stepDTO != null && getCurrentConfigFormFromSession().getFormStepName().equals(stepDTO.getName())) {
                return new StepDTO(stepDTO);
            }
        }
        return null;
    }   
    
}
