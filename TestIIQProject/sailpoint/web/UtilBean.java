/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A request scoped bean providing several commonly
 * used properties, primarily for populating selection menus.
 *
 * Author: Jeff
 *
 * It sure seemed like this existed somewhere else but I couldn't
 * find it.  Currently several pages are using a completely
 * unrelated bean just to get object lists, we should try to standardize
 * on putting these in a common utility class like this.
 */

package sailpoint.web;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.group.PopulationFilterUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * A request scoped bean providing several commonly
 * used properties, primarily for populating selection menus.
 */
public class UtilBean extends BaseBean
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(UtilBean.class);

    // We'll cache these for request scope

    List<SelectItem> _applications;
    List<SelectItem> _populations;
    List<SelectItem> _workflows;
    List<SelectItem> _selectorRules;
    List<SelectItem> _policyRules;
    List<SelectItem> _policyOwnerRules;
    List<SelectItem> _listenerRules;
    List<SelectItem> _violationRules;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public UtilBean() {
    }

    /**
     * Return the available applications.
     * Note that we only use names since they will be unique and it
     * makes it easier to use this in places that want just the name.
     */
    public List<SelectItem> getApplications() throws GeneralException {

        if (_applications == null) {

            _applications = new ArrayList<SelectItem>();
            _applications.add(new SelectItem("", getMessage(MessageKeys.SELECT_APPLICATION)));
        
            try {
                QueryOptions ops = new QueryOptions();
                ops.setOrderBy("name");

                // Only show apps that are in scope or owned by the logged in user.
                ops.setScopeResults(true);
                ops.addOwnerScope(super.getLoggedInUser());
            
                List<String> props = new ArrayList<String>();
                props.add("name");

                Iterator<Object[]> result = getContext().search(Application.class, ops, props);
                while (result.hasNext()) {
                    Object[] row = result.next();
                    String name = (String)row[0];
                    if (name != null)
                        _applications.add(new SelectItem(name, name));
                }
            }
            catch (GeneralException e) {
                // Does this do anything since this bean isn't what is rendering
                // the page??  Would be really nice if we could fine a way
                // to put messages on the right object - jsl
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE), null);
                log.error("The database is not accessible.", e);
            }
        }

        if (_applications.isEmpty())
            _applications.add(new SelectItem("", getMessage(MessageKeys.NO_UNCONFIGURED_APPS_AVAILABLE)));
        
        return _applications;
    }

    /**
     * @return group definitions that were not created
     * by factories. This list could be large so in practice
     * we need to be using a suggest component.
     */
    public List<SelectItem> getPopulations() throws GeneralException {

        if (_populations == null) {

            _populations = new ArrayList<SelectItem>();

            _populations.add(new SelectItem("", getMessage(MessageKeys.SELECT_POPULATION)));

            QueryOptions ops = new QueryOptions();
            // this filter should look the same as in
            // GroupDefinitionListBean.getQueryOptions() in the onlyIpops if
            // statement
            Identity owningUser = getLoggedInUser();
            PopulationFilterUtil.addPopulationOwnerFiltersToQueryOption( ops, owningUser );
            ops.setScopeResults( true );
            ops.setOrderBy("name");

            List<String> props = new ArrayList<String>();
            props.add("id");
            props.add("name");

            Iterator<Object[]> result = getContext().search(GroupDefinition.class, ops, props);
            while (result.hasNext()) {
                Object[] row = result.next();
                if (row[1] != null)
                    _populations.add(new SelectItem((String)row[0], (String)row[1]));
            }
        }

        return _populations;
    }

    /**
     * Build a select list of workflows.
     * We do not currently have a way of typing workflows so just
     * return all of them.
     * @return a list of all workflows
     */
    public List<SelectItem> getWorkflows() throws GeneralException {

        if (_workflows == null) {

            _workflows = new ArrayList<SelectItem>();

            _workflows.add(new SelectItem("", getMessage(MessageKeys.SELECT_WORKFLOW)));

            QueryOptions ops = new QueryOptions();
            ops.setOrderBy("name");

            List<String> props = new ArrayList<String>();
            props.add("id");
            props.add("name");

            Iterator<Object[]> result = getContext().search(Workflow.class, ops, props);
            while (result.hasNext()) {
                Object[] row = result.next();
                if (row[1] != null)
                    _workflows.add(new SelectItem((String)row[0], (String)row[1]));
            }
        }

        return _workflows;
    }

    /**
     * @return Rules suitable for use in IdentitySelectors.
     * It would be nice to be able to pass args rather than
     * having to have different names for each type.
     */
    public List<SelectItem> getIdentitySelectorRules() throws GeneralException {

        if (_selectorRules == null)
            _selectorRules = WebUtil.getRulesByType(getContext(),
                                                    Rule.Type.IdentitySelector,
                                                    true);

        return _selectorRules;
    }

    /**
     * 
     * @return Rules suitable for use in determining email recipients.
     * @throws GeneralException
     */
    public List<SelectItem> getEmailRecipientRules() throws GeneralException {

        if (_selectorRules == null)
            _selectorRules = WebUtil.getRulesByType(getContext(),
                                                    Rule.Type.EmailRecipient,
                                                    true);

        return _selectorRules;
    }
    
    /**
     * @return Rules suitable for use in advanced polices.
     */
    public List<SelectItem> getPolicyRules() throws GeneralException {

        if (_policyRules == null)
            _policyRules = WebUtil.getRulesByType(getContext(),
                                                    Rule.Type.Policy,
                                                    true);

        return _policyRules;
    }

    /**
     * @return Rules suitable for use in advanced polices.
     */
    public List<SelectItem> getPolicyOwnerRules() throws GeneralException {

        if (_policyOwnerRules == null)
            _policyOwnerRules = WebUtil.getRulesByType(getContext(),
                                                    Rule.Type.PolicyOwner,
                                                    true);

        return _policyOwnerRules;
    }

    /**
     * @return Rules suitable for use as ObjectAttribute change listeners.
     */
    public List<SelectItem> getListenerRules() throws GeneralException {

        if (_listenerRules == null)
            _listenerRules = WebUtil.getRulesByType(getContext(),
                                                    Rule.Type.Listener,
                                                    true);

        return _listenerRules;
    }

    /**
     * 
     * @return Rules suitable for use in policy violations
     * @throws GeneralException
     */
    public List<SelectItem> getViolationRules() throws GeneralException {

        if (_violationRules == null)
            _violationRules = WebUtil.getRulesByType(getContext(),
                                                     Rule.Type.Violation,
                                                     true);

        return _violationRules;
    }
    
    /**
     * 
     * @return list of all rules.
     * @throws GeneralException
     */
    public List<SelectItem> getAllRules() throws GeneralException {
    	return WebUtil.getRulesByType(getContext(), null, true, true);
    }
    
    /**
     * Takes a Request Parameter of Rule Type ("type") and Include Prompt ("prompt")
     * Type represents the type of rules to return
     * Prompt represents whether we add a default entry in the results for message key select_rule. This entry will not
     * have a value associated with it. Simply a place-holder for no selection made.
     * 
     * 
     * @return JSON representation of the rules requested based on request params submitted
     * @throws GeneralException
     */
    public String getRulesJSON() throws GeneralException {
        String type = getRequestParameter("type");
        boolean includePrompt = Boolean.parseBoolean(getRequestParameter("prompt"));

        QueryOptions ops = new QueryOptions();
        ops.setOrderBy("name");
        if (type != null)
            ops.add(Filter.eq("type", type));

        //Add scoping because we cannot authorize per se
        ops.setScopeResults(true);
        
        List<String> props = new ArrayList<String>();
        props.add("id");
        props.add("name");
        
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();
            
            jsonWriter.key("objects");
            List<JSONObject> fieldList = new ArrayList<JSONObject>();
            
            if (includePrompt) {
                Message msg = new Message(MessageKeys.SELECT_RULE);
                Map<String,String> fieldMap = new HashMap<String,String>();
                fieldMap.put("value", "");
                fieldMap.put("name", msg.getMessage());
                fieldList.add(new JSONObject(fieldMap));                
            }
            
            Iterator<Object[]> result = getContext().search(Rule.class, ops, props);
            while (result.hasNext()) {
                Object[] row = result.next();
                if (row[1] != null){
                    Map<String,String> fieldMap = new HashMap<String,String>();
                    fieldMap.put("value", (String)row[1]);
                    fieldMap.put("name", (String)row[1]);
                    fieldList.add(new JSONObject(fieldMap));
                }
            }
            
            jsonWriter.value(fieldList);
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not get JSON for activity data sources");
        }
        
        return jsonString.toString();
    }
    
    /**
     * Takes an optional Request Parameter of Workflow Type ("type") and Include Prompt ("prompt")
     * Type represents the type of workflows to return
     * Prompt represents whether we add a default entry in the results for Message Key select_subprocess. This entry
     * will not have a value associated with it. Simply a place-holder for no selection made.
     *  
     * @return JSON representation of workflows requested based on the request parameters submitted.
     * @throws GeneralException
     */
    public String getSubprocessesJson() throws GeneralException {
        
        String type = getRequestParameter("type");
        boolean includePrompt = Boolean.parseBoolean(getRequestParameter("prompt"));
        
        QueryOptions ops = new QueryOptions();
        ops.setOrderBy("name");
        if (type != null) {ops.add(Filter.eq("type", type));}

        List<String> props = new ArrayList<String>();
        // Using name for both value and name
        props.add("name");
        
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();
            
            jsonWriter.key("objects");
            List<JSONObject> fieldList = new ArrayList<JSONObject>();
            
            if (includePrompt) {
                Message msg = new Message(MessageKeys.SELECT_SUBPROCESS);
                Map<String,String> fieldMap = new HashMap<String,String>();
                fieldMap.put("value", "");
                fieldMap.put("name", msg.getLocalizedMessage());
                fieldList.add(new JSONObject(fieldMap));                
            }
            
            Iterator<Object[]> result = getContext().search(Workflow.class, ops, props);
            while (result.hasNext()) {
                Object[] row = result.next();
                if (row[0] != null){
                    Map<String,String> fieldMap = new HashMap<String,String>();
                    //IIQSAW-2587: StepDTO expects subprocess name instead of ID.
                    fieldMap.put("value", (String)row[0]);
                    fieldMap.put("name", (String)row[0]);
                    fieldList.add(new JSONObject(fieldMap));
                }
            }
            
            jsonWriter.value(fieldList);
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not get JSON for workflow subprocesses");
        }
        
        return jsonString.toString();
    }
    
    /**
     * 
     * @return EmailTemplates ordered by name.
     */
    public List<SelectItem> getEmailTemplates() {
        ArrayList<SelectItem> templateNames = new ArrayList<SelectItem>();

        try {
            // Query the database to get available reminder e-mail templates
            QueryOptions qo = new QueryOptions();
            qo.addOrdering("name", true);
            List<EmailTemplate> templates =
                getContext().getObjects(EmailTemplate.class, qo);

            for (EmailTemplate template : templates) {
                templateNames.add(new SelectItem(template.getId(), template.getName()));
            }
        } catch (GeneralException e) {
            log.error(e);
        }

        return templateNames;
    }
    
    /**
     * 
     * @return Rules suitable for escalation
     * @throws GeneralException
     */
    public List<SelectItem> getEscalationRules() throws GeneralException {
        return 
            WebUtil.getRulesByType(getContext(), Rule.Type.Escalation, true);
    }

    /**
     * Test for existence of file within the root directory.
     *
     * TODO: changed this from public to private for bug 21406#c6, if needed in the future we can open it back up.
     *
     * @param filePath Path to file starting at web root. Cannot contain "../" or "..\".
     * @return true if file exists and is actually a file, false otherwise.
     */
    private boolean doesFileExist(String filePath) {
        if(filePath != null) {
            try {
                // Don't let anyone check files outside the root dir.
                if(filePath.contains("../") || filePath.contains("..\\")) {
                    filePath = filePath.replaceAll("\\.\\./", "").replaceAll("\\.\\.\\\\", "");
                    return doesFileExist(filePath); // catch patterns like ....// with recursion.
                }
                File file = new File( getFacesContext().getExternalContext().getRealPath(filePath) );
                if(file != null && file.isFile()) {
                    return true;
                }
            }
            catch (Exception e) {
                if(log.isInfoEnabled()) {
                    log.info("Error testing for the existence of: " + filePath, e);
                }
            }
        }
        return false;
    }

    /**
     * Check to see if a custom.css exists for CodeMirror.
     *
     * @return true if a custom.css exists for CodeMirror, false if not
     */
    public boolean getCustomCMCSSExists() {
        String path = "";
        try {
            Configuration config = getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            if (config != null) {
                path = config.getString(Configuration.CUSTOM_THEME_CODE_MIRROR);
            }
        }
        catch(Exception e) {
            if (log.isInfoEnabled()) {
                log.info("Error getting system config: " + Configuration.CUSTOM_THEME_CODE_MIRROR, e);
            }
        }
        return doesFileExist(path);
    }
}
