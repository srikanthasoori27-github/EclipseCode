package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Differencer;
import sailpoint.api.ScopeService;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 *
 * IdentityDTO delegates to this class for all scope
 * related stuff under the View Identity -> User Rights tab
 *
 */
public class ScopeHelper {

    private static final Log log = LogFactory.getLog(ScopeHelper.class);
    
    private IdentityDTO parent;
    // Looks like it could be
    // "default", "true" or "false"
    private LazyLoad<String> controlsAssignedScopeLoader;
    private LazyLoad<List<String>> controlledScopesInfo;
    private LazyLoad<Scope> assignedScopeLoader;
    
    
    public ScopeHelper(IdentityDTO parent) {
        
        if (log.isInfoEnabled()) {
            log.info("ScopeHelper()");
        }
        this.parent = parent;
        this.controlsAssignedScopeLoader = new LazyLoad<>(this::fetchControlsAssignedScope);
        this.controlledScopesInfo = new LazyLoad<>(this::fetchControlledScopes);
        this.assignedScopeLoader = new LazyLoad<>(this::fetchAssignedScope);
    }

    public Scope getAssignedScope() throws GeneralException {
        return this.assignedScopeLoader.getValue();
    }
    
    public void setAssignedScope(Scope scp) {
        // Noop, required for JSF inputHidden tag
    }
    
    public String getControlsAssignedScope() throws GeneralException {
        if (this.parent.getState().getControlsAssignedScope() != null) {
            return this.parent.getState().getControlsAssignedScope();
        }

        return this.controlsAssignedScopeLoader.getValue();
    }
    
    public void setControlsAssignedScope(String val) {
        this.parent.getState().setControlsAssignedScope(val);
    }
    
    public List<String> getControlledScopes() throws GeneralException {
        if (this.parent.getState().getControlledScopes() != null) {
            return this.parent.getState().getControlledScopes();
        }

        return this.controlledScopesInfo.getValue();
    }
    
    public void setControlledScopes(List<String> val) throws GeneralException {
        this.parent.getState().setControlledScopes(val);
    }
    
    public String getControlledScopesJson() throws GeneralException {
        return WebUtil.referencedJSONData(Scope.class.getSimpleName(), Util.listToCsv(getControlledScopes()));
    }

    public void setControlledScopesJson(String json) throws GeneralException {
        // Noop, required for JSF inputHidden tag
    }
    
    public List<SelectItem> getControlsAssignedScopeOptions() {
        List<SelectItem> options = new ArrayList<SelectItem>();
        boolean defaultValue;
        
        try {
            defaultValue = this.parent.getContext().getConfiguration().getBoolean(Configuration.IDENTITY_CONTROLS_ASSIGNED_SCOPE, false);
        } catch (GeneralException e) {
            defaultValue = false;
            log.error("Assigned scope settings are not accessible because the system configuration is missing.");
        }
        
        options.add(new SelectItem("default", this.parent.getMessage(MessageKeys.ID_ASSIGNED_SCOPE_DEFAULT, this.parent.getMessage("txt_" + Boolean.toString(defaultValue).toLowerCase()))));
        options.add(new SelectItem(Boolean.toString(true), this.parent.getMessage(MessageKeys.TXT_TRUE)));
        options.add(new SelectItem(Boolean.toString(false), this.parent.getMessage(MessageKeys.TXT_FALSE)));
        
        return options;
    }
    
    public boolean isShowScopeControls() {
        ScopeService scopeSvc = new ScopeService(this.parent.getContext());
        return scopeSvc.isScopingEnabled();
    }

    private Scope fetchAssignedScope() throws GeneralException {
        
        Identity id = this.parent.getObject();
        if ( id != null ) 
            return id.getAssignedScope();
        else 
            return null;
    }
    
    private String fetchControlsAssignedScope() throws GeneralException {
        
        Identity id = this.parent.getObject();
        if ( id != null ) {
            if (null == id.getControlsAssignedScope()) {
                return "default";
            } else {
                return Boolean.toString(id.getControlsAssignedScope());   
            }
        } else {
            return "default";
        }
    }
    
    private List<String> fetchControlledScopes() throws GeneralException {

        List<Scope> controlledScopes = null;
        Identity identity = this.parent.getObject();
        if ( identity != null )
             controlledScopes = identity.getControlledScopes();

        return (controlledScopes == null) ? null : WebUtil.objectListToIds(controlledScopes);
    }

    void addControlsAssignedScopeInfoToRequest(Identity identity, AccountRequest account) 
        throws GeneralException {

        if (this.parent.getState().getControlsAssignedScope() != null) {
            Boolean controlsAssignedScope = isControlsAssignedScope();
            if (controlsAssignedScope != identity.getControlsAssignedScope()) {
                AttributeRequest req = new AttributeRequest();
                req.setName(ProvisioningPlan.ATT_IIQ_CONTROLS_ASSIGNED_SCOPE);
                req.setOperation(ProvisioningPlan.Operation.Set);
                req.setValue(controlsAssignedScope);
                account.add(req);
            }
        }
    }
    
    void addControlledScopesInfoToRequest(Identity identity, AccountRequest account) {
        List<String> newScopes = this.parent.getState().getControlledScopes();
        // When diffing scopes, try not to assume everything is from
        // the same Hibernate session.
        // tqm: we can't do name comparison because names are only unique per
        // container.
        List<String> oldScopes = identity.getControlledScopes().stream().map(Scope::getId).collect(Collectors.toList());

        if (!Differencer.equal(oldScopes, newScopes)) {
            AttributeRequest req = new AttributeRequest();
            req.setName(ProvisioningPlan.ATT_IIQ_CONTROLLED_SCOPES);
            req.setOperation(ProvisioningPlan.Operation.Set);
            req.setValue(newScopes);
            account.add(req);
        }
    }
    
    Boolean isControlsAssignedScope() throws GeneralException {
        
        Boolean currentAssignedScope = false;
        if ("default".equals(this.parent.getState().getControlsAssignedScope())) {
            currentAssignedScope = null;
        } else {
            currentAssignedScope = Util.otob(this.parent.getState().getControlsAssignedScope());
        }
        return currentAssignedScope;
    }
    
}

