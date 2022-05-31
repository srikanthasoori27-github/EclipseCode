package sailpoint.web.modeler;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.integration.JsonUtil;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicScope;
import sailpoint.object.IdentitySelector;
import sailpoint.object.QuickLinkOptions;
import sailpoint.service.DynamicScopeDTO;
import sailpoint.service.DynamicScopeService;
import sailpoint.service.ValidationException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.BaseEditBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.policy.IdentitySelectorDTO;

public class DynamicScopeEditorBean extends BaseEditBean<DynamicScope> {

    private static final Log log = LogFactory.getLog(DynamicScopeEditorBean.class);
    
    private static final String DS_CURRENT_DTO = "dsCurrentDTO";
    private static final String DS_IDENTITY_SELECTOR = "dsIdentitySelector";
    private static final String SAVE_OUTCOME = "save";
    private static final String DELETE_OUTCOME = "delete";
    
    private IdentitySelectorDTO selector;
    private String selectedDynamicScopeId;
    private DynamicScopeDTO currentDTO;

    public DynamicScopeEditorBean() {
        super();
        if (Util.otob(getRequestParameter(BaseEditBean.FORCE_LOAD))) {
            clearHttpSession();
        }
        
        this.currentDTO = (DynamicScopeDTO) getEditState(DS_CURRENT_DTO);
        this.selector = (IdentitySelectorDTO) getEditState(DS_IDENTITY_SELECTOR);
    }

    @Override
    public boolean isStoredOnSession() {
        return true;
    }

    @Override
    protected Class<DynamicScope> getScope() {
        return DynamicScope.class;
    }
    
    @Override
    public void clearHttpSession() {
        super.clearHttpSession();
        
        removeEditStateObjects();
    }
    
    protected void removeEditStateObjects() {
        removeEditState(DS_CURRENT_DTO);
        removeEditState(DS_IDENTITY_SELECTOR);
    }
    
    public String getSelectedDynamicScopeId() {
        return selectedDynamicScopeId;
    }

    public void setSelectedDynamicScopeId(String selectedDynamicScopeId) {
        this.selectedDynamicScopeId = selectedDynamicScopeId;
    }

    public void setSelector(IdentitySelectorDTO selector) {
        this.selector = selector;
    }
    
    /**
     * The currentDTO being modified.
     * @return the selected/current DTO that represents the DynamicScope
     * @throws GeneralException
     */
    public DynamicScopeDTO getCurrentDTO() throws GeneralException {
        if (currentDTO != null) {
            addEditState(DS_CURRENT_DTO, currentDTO);
        }
        return currentDTO;
    }

    public void setCurrentDTO(DynamicScopeDTO currentDTO) {
        this.currentDTO = currentDTO;
    }

   /**
    * Returns the IdentitySelectorDTO that is contained within the Dynamic Scope that is being edited
    * @return IdentitySelectorDTO that is new or within the Dynamic Scope
    */
    public IdentitySelectorDTO getSelector()  {
        try {
            DynamicScopeDTO scopeDTO = getCurrentDTO();
            if (selector == null && scopeDTO != null && scopeDTO.getDynamicScope() != null) {
                DynamicScope scope = scopeDTO.getDynamicScope();
                IdentitySelector sel = scope.getSelector();
                //if the current ds is allowing all, then just set the selector to All
                if (scope.isAllowAll()) {
                    selector = new IdentitySelectorDTO();
                    selector.setType(IdentitySelectorDTO.SELECTOR_TYPE_ALL);
                }
                else if (sel != null && !sel.isEmpty()) {
                    selector = new IdentitySelectorDTO(sel, true, true);
                }
                //here we got a null back as the sel object which means it needs to be in the none state
                else {
                    selector = new IdentitySelectorDTO();
                    selector.setType(IdentitySelectorDTO.SELECTOR_TYPE_NONE);
                }
            }
            if (selector != null) {
                selector.setAllowTypeNone(true);
                selector.setAllowTypeAll(true);
                addEditState(DS_IDENTITY_SELECTOR, selector);
            }
        }
        catch (GeneralException e) {
            addMessage(e);
        }

        return selector;
    }

    /**
     * Handles the hidden refresh action when DynamicScope is clicked.
     */
    public void refresh() {
        try {
            if (Util.isNotNullOrEmpty(getSelectedDynamicScopeId())) {
                DynamicScopeService service = new DynamicScopeService(getContext());
                currentDTO = service.getDynamicScope(getSelectedDynamicScopeId());
                //reset selector
                selector = null;
            } else {
                currentDTO = new DynamicScopeDTO(new DynamicScope(), Collections.<QuickLinkOptions> emptyList(), getContext());
                selector = new IdentitySelectorDTO();
                selector.setType(IdentitySelectorDTO.SELECTOR_TYPE_ALL);
            }
        } catch (GeneralException e) {
            addMessage(new Message(Type.Error, e));
        }
    }
    
    /**
     * Save action for this bean for when the user hits the save button. 
     * Note that right now this only will save the selector for the DynamicScope until further development happens.
     * @return JSF outcome status String. "save" for a successful save, null otherwise
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public String saveAction() {
        String outcome = null;
        DynamicScopeDTO scopeDTO = null;
        try {
            DynamicScopeService service = new DynamicScopeService(getContext());
            scopeDTO = getCurrentDTO();
            
            selector.validate();
            
            //set the selector from the currentDTO as of right now, more to set later
            //read the selector.getType() to set the dynamicScope's allowAll if needed
            if (Util.nullSafeEq(this.selector.getType(), IdentitySelectorDTO.SELECTOR_TYPE_ALL)) {
                scopeDTO.getDynamicScope().setAllowAll(true);
                scopeDTO.getDynamicScope().setSelector(null);
            } else {
                scopeDTO.getDynamicScope().setAllowAll(false);
                scopeDTO.getDynamicScope().setSelector(this.selector.convert());
            }
            
            String selectedQLOsJson = getRequestParameter("editForm:selectedQLOsJson");
            Object obj = JsonUtil.parse(selectedQLOsJson);
            List<Map> optionsList = flattenOptions((List<Map>) obj);
            List<QuickLinkOptions> quicklinkOptions = scopeDTO.deserializeQuickLinkOptionsList(
                    optionsList, scopeDTO.getDynamicScope(), getContext());
            scopeDTO.setQuickLinkOptionsList(quicklinkOptions);

            if (Util.isNullOrEmpty(scopeDTO.getId())) {
                service.createDynamicScope(scopeDTO);
                addMessageToSession(new Message(MessageKeys.QLP_EDITOR_CREATE_SUCCESSFUL));
            }
            else {
                service.updateDynamicScope(scopeDTO);
                addMessageToSession(new Message(MessageKeys.QLP_EDITOR_UPDATE_SUCCESSFUL));
            }

            this.removeEditStateObjects();
            outcome = SAVE_OUTCOME;
        } catch (ValidationException e) {
            for (Message m : Util.safeIterable(e.getMessages())) {
                addMessage(m);
            }
        } catch (GeneralException e) {
            addMessage(new Message(Type.Error, e));
            log.error("GeneralException saving DynamicScope: " + scopeDTO, e);
        } catch (Exception e) {
            addMessage(new Message(Type.Error, e));
            log.error("Exception saving DynamicScope: " + scopeDTO, e);
        }
        return outcome;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<Map> flattenOptions(List<Map> list) {
        for (Map map : Util.safeIterable(list)) {
            Map<String, Object> options = (Map)map.get(DynamicScopeDTO.KEY_OPTIONS);
            //move allowBulk, allowOther, allowSelf from options to top level
            map.put(DynamicScopeDTO.KEY_ALLOW_BULK, options.get(DynamicScopeDTO.KEY_ALLOW_BULK));
            options.remove(DynamicScopeDTO.KEY_ALLOW_BULK);
            map.put(DynamicScopeDTO.KEY_ALLOW_OTHER, options.get(DynamicScopeDTO.KEY_ALLOW_OTHER));
            options.remove(DynamicScopeDTO.KEY_ALLOW_OTHER);
            map.put(DynamicScopeDTO.KEY_ALLOW_SELF, options.get(DynamicScopeDTO.KEY_ALLOW_SELF));
            options.remove(DynamicScopeDTO.KEY_ALLOW_SELF);
        }
        return list;
    }

    /**
     * Delete action for this bean.
     * @return JSF outcome status String.
     */
    public String deleteAction() {
        String outcome = null;
        String selectedDS = getSelectedDynamicScopeId();
        if (selectedDS !=null) {
            try {
                DynamicScopeService dsService = new DynamicScopeService(getContext());
                dsService.deleteDynamicScope(selectedDS);
                this.removeEditStateObjects();
                outcome = DELETE_OUTCOME;
                addMessageToSession(new Message(MessageKeys.QLP_EDITOR_DELETE_SUCCESSFUL));
            } catch (GeneralException e) {
                addMessage(new Message(Type.Error, MessageKeys.QLP_EDITOR_DELETE_FAILURE));
                log.error("An error occurred deleting the DynamicScope with id " + selectedDS, e);
            }
        }
        return outcome;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS (READ-ONLY PROPERTIES)
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String getQuicklinksGridJson() throws GeneralException {
        if (currentDTO == null) {
            if (this.selectedDynamicScopeId == null) {
                this.selectedDynamicScopeId = getRequestParameter("selectedDynamicScopeId");
            }
            if (Util.isNotNullOrEmpty(getSelectedDynamicScopeId())) {
                DynamicScopeService service = new DynamicScopeService(getContext());
                currentDTO = service.getDynamicScope(this.selectedDynamicScopeId);
            }
        }
        
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        
        try {
            jsonWriter.object();
            

            JSONArray members = new JSONArray();
            List<QuickLinkOptions> qloList = null;
            if (currentDTO != null) {
                qloList = currentDTO.getQuickLinkOptionsList();
            }
            
            if ( Util.isEmpty(qloList)) {
                jsonWriter.key("count");
                jsonWriter.value(0);
            } else {
                jsonWriter.key("count");
                jsonWriter.value(qloList.size());
                for ( QuickLinkOptions qlo : qloList ) {
                    HashMap<String,Object> memberMap = new HashMap<String,Object>();

                    memberMap.put(DynamicScopeDTO.KEY_QUICK_LINK_ID, qlo.getQuickLink().getId());
                    Map<String, Object> options = qlo.getOptions();
                    if (options == null) {
                        options = new HashMap<String, Object>();
                    }
                    options.put(DynamicScopeDTO.KEY_ALLOW_SELF, qlo.isAllowSelf());
                    options.put(DynamicScopeDTO.KEY_ALLOW_OTHER, qlo.isAllowOther());
                    options.put(DynamicScopeDTO.KEY_ALLOW_BULK, qlo.isAllowBulk());
                    memberMap.put(DynamicScopeDTO.KEY_OPTIONS, options);
                    
                    members.put(memberMap);   
                }
            }

            jsonWriter.key("objects");
            jsonWriter.value(members);
                        
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for getting QuickLinkOptions list", e);
            result = "{}";
        }
        return result;
    }

    public List<SelectItem> getPopulationDefinitionTypes() {
        List<SelectItem> choices = new ArrayList<SelectItem>();
        choices.add(new SelectItem(Configuration.LCM_REQUEST_CONTROLS_ALLOW_ALL, getMessage(MessageKeys.LCM_REQUEST_CONTROLS_ALLOW_ALL)));
        choices.add(new SelectItem(Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY_OR_ALL, getMessage(MessageKeys.LCM_REQUEST_CONTROLS_DEFINE_POPULATION)));
        choices.add(new SelectItem(Configuration.LCM_REQUEST_CONTROLS_MATCH_NONE, getMessage(MessageKeys.LCM_REQUEST_CONTROLS_ALLOW_NONE)));
        return choices;
    }
    
    public List<SelectItem> getMatchAnyOrAllOptions() {
        List<SelectItem> matchAnyOrAllOptions = 
            Arrays.asList(new SelectItem[] { 
                new SelectItem(Configuration.LCM_REQUEST_CONTROLS_MATCH_ANY, getMessage(MessageKeys.LCM_REQUEST_CONTROLS_MATCH_ANY)),
                new SelectItem(Configuration.LCM_REQUEST_CONTROLS_MATCH_ALL, getMessage(MessageKeys.LCM_REQUEST_CONTROLS_MATCH_ALL))
            });
        return matchAnyOrAllOptions;
    }

    public List<SelectItem> getSubordinateChoiceOptions() {
        List<SelectItem> subordinateChoiceOptions = 
            Arrays.asList(new SelectItem[] { 
                new SelectItem(Configuration.LCM_REQUEST_CONTROLS_DIRECT, getMessage(MessageKeys.LCM_REQUEST_CONTROLS_DIRECT)),
                new SelectItem(Configuration.LCM_REQUEST_CONTROLS_DIRECT_OR_INDIRECT, getMessage(MessageKeys.LCM_REQUEST_CONTROLS_DIRECT_OR_INDIRECT))
            });
        return subordinateChoiceOptions;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // selector.xhtml action listeners
    //
    //////////////////////////////////////////////////////////////////////

    public void addSelectorAttribute(ActionEvent e) {
        try {
            if (selector != null)
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Entitlement.name());
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorIdentityAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.IdentityAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorPermission(ActionEvent e) {
        try {
            if (selector != null)
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Permission.name());
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorRoleAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.RoleAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void addSelectorEntitlementAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.EntitlementAttribute.name());
            }
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }


    public void deleteSelectorTerms(ActionEvent e) {
        try {
            if (selector != null)
                selector.deleteSelectedTerms();
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void groupSelectedTerms(ActionEvent e) {
        try {
            if (selector != null)
                selector.groupSelectedTerms();
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

    public void ungroupSelectedTerms(ActionEvent e) {
        try {
            if (selector != null)
                selector.ungroupSelectedTerms();
        }
        catch (Throwable t) {
            addMessage(t);
        }
    }

}