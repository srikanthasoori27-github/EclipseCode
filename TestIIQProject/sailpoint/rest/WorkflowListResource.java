/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.Version;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.integration.ListResult;
import sailpoint.object.Argument;
import sailpoint.object.SPRight;
import sailpoint.object.Workflow.Step.Icon;
import sailpoint.object.WorkflowRegistry;
import sailpoint.object.WorkflowRegistry.Callable;
import sailpoint.object.WorkflowRegistry.WorkflowType;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;


/**
 * A list resource for Workflows.
 * 
 * Currenlty all t his does is defer to the WorkflowResource
 * so we can launch workflows remotely.
 * 
 * URI : /workflows/$(workflowDefNameOrId)
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@Path(IIQClient.RESOURCE_WORKFLOWS)
public class WorkflowListResource extends BaseResource {
    
    /**
     * Runs a Workflow definition with the supplied 
     * arguments.
     */    
    @Path("{" + IIQClient.PARAM_RESOURCE_WORKFLOW_DEFINITION + "}")
    public WorkflowResource getWorkflow(@PathParam(IIQClient.PARAM_RESOURCE_WORKFLOW_DEFINITION) String workflowDefId) {
        return new WorkflowResource(workflowDefId, this);
    }
    
    /** Returns a list of callable methods from the workflow libraries to the BPE UI
     * for populating the dropdown box on the Script Radios
     * @param type
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("callables/{type}")
    public ListResult getCallables(@PathParam("type") String type) throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.FullAccessWorkflows));
    	
        WorkflowRegistry registry = WorkflowRegistry.getInstance(getContext());
        List<Callable> callables = registry.getCallables(type);
        
        Collections.sort(callables, WorkflowRegistry.CALLABLE_COMPARATOR);
        
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        for(Callable callable : callables) {
            Map<String,Object> row = new HashMap<String,Object>();
            Message message = new Message(callable.getDescriptionKey());
            List<Map<String,String>> arguments = new ArrayList<Map<String,String>>();

            row.put("name", callable.getName());
            row.put("description", message.getLocalizedMessage(getLocale(), getUserTimeZone()));
            
            List<Argument> requiredArguments = callable.getRequiredArguments();
            if(requiredArguments!=null && !requiredArguments.isEmpty()) {
                for(Argument argument : requiredArguments) {
                    Map<String,String> argMap = new HashMap<String,String>();
                    argMap.put("name", argument.getName());
                    argMap.put("description",argument.getDescription());
                    arguments.add(argMap);
                }
            }
            
            row.put("requiredArguments", arguments);
            rows.add(row);
        }
        
        ListResult result = new ListResult(rows, rows.size());
        return result;
    }
    
    /** Returns a list of workflow types to the BPE UI
     * for populating the dropdown box on the Script Radios
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("workflowTypes")
    public ListResult getWorkflowTypes() throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.FullAccessWorkflows));
    	
        WorkflowRegistry registry = WorkflowRegistry.getInstance(getContext());
        List<WorkflowType> types = registry.getTypes();
        if (!Version.isLCMEnabled()) {
            filterLCM(types);
        }

        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        for(WorkflowType type: types) {
            Map<String,Object> row = new HashMap<String,Object>();
            Message help = new Message(type.getHelpKey());
            Message name = new Message(type.getDisplayNameKey());
            row.put("name", name.getLocalizedMessage(getLocale(), getUserTimeZone()));
            row.put("value", type.getName());
            row.put("description", help.getLocalizedMessage(getLocale(), getUserTimeZone()));
            rows.add(row);
        }
        
        ListResult result = new ListResult(rows, rows.size());
        return result;
    }
    
    /** Returns a list of icons to the BPE UI
     * for populating the change icon popup
     * @return a list result containing icon metadata
     * @throws GeneralException
     */
    @GET
    @Path("icons")
    public ListResult getIcons() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessWorkflows));
        
        WorkflowRegistry registry = WorkflowRegistry.getInstance(getContext());
        @SuppressWarnings("unchecked")
        List<Icon> icons = (List<Icon>)registry.getAttribute(WorkflowRegistry.ATTR_ICONS);

        int i = 0;
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        for(Icon icon : icons) {
            i++;
            Map<String, Object> stepMap = new HashMap<String, Object>();
            stepMap.put("id", i);
            stepMap.put("name", icon.getName());
            stepMap.put("iconCls", icon.getStyleClass()+"_sm");
            stepMap.put("text", icon.getText());
            stepMap.put("leaf", true);
            stepMap.put("expanded", false);
            stepMap.put("stepClass", icon.getStyleClass());
            rows.add(stepMap);                
        }
        
        ListResult result = new ListResult(rows, rows.size());
        return result;
    }
    
    @GET
    @Path("approvalModes")
    public ListResult getApprovalModes() throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.FullAccessWorkflows));
    	
        WorkflowRegistry registry = WorkflowRegistry.getInstance(getContext());
        String modeString = (String)registry.getAttribute(WorkflowRegistry.ATTR_APPROVAL_MODES);
        
        List<String> modes = Util.csvToList(modeString);
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        for(String mode: modes) {
            Map<String,Object> row = new HashMap<String,Object>();
            Message help = new Message("help_workflow_approval_mode_"+mode);
            
            row.put("name", mode);
            row.put("description", help.getLocalizedMessage(getLocale(), getUserTimeZone()));
            rows.add(row);
        }
        
        ListResult result = new ListResult(rows, rows.size());
        return result;
    }

    /**
     * If LCM is not enabled we need to not show LCM workflow types.
     * This will iterate over the types and remove the lcm types.
     * @param types all the types.
     * @throws GeneralException
     */
    private void filterLCM(List<WorkflowType> types) throws GeneralException {
        if (Util.isEmpty(types)) {
            return;
        }

        Iterator<WorkflowType> typesIterator = types.iterator();
        while (typesIterator.hasNext()) {
            WorkflowType type = typesIterator.next();
            if (type != null && type.isLCM()) {
                typesIterator.remove();
            }
        }
    }
}
