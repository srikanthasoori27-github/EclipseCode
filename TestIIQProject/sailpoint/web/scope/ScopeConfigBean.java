/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.scope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.faces.model.SelectItem;

import sailpoint.object.Configuration;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * JSF bean for configuring scopes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ScopeConfigBean extends BaseBean {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private String scopeAttribute;
    private String scopeCorrelationRule;
    private String scopeSelectionRule;
    private boolean unscopedGloballyAccessible;
    private boolean identityControlsAssignedScope;
    private boolean scopingEnabled;
    
    private List<SelectItem> identityAttributes;
    
    /*
    Don't cache any of the rule lists, otherwise new rules created by the
    rule editor while working with the cert schedule can't be detected.
    See bug #5901 for details on the rule editor. - DHC    
    private List<SelectItem> correlationRules;
    private List<SelectItem> selectionRules;
    */

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public ScopeConfigBean() throws GeneralException {
        super();

        Configuration sysConfig = getContext().getConfiguration();
        this.unscopedGloballyAccessible =
            sysConfig.getBoolean(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE, true);
        this.identityControlsAssignedScope =
            sysConfig.getBoolean(Configuration.IDENTITY_CONTROLS_ASSIGNED_SCOPE, false);
        this.scopingEnabled =
            sysConfig.getBoolean(Configuration.SCOPING_ENABLED, false);

        ObjectConfig idConfig =
            getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        this.scopeAttribute = 
            (String) idConfig.get(ObjectConfig.IDENTITY_SCOPE_CORRELATION_ATTR);
        this.scopeCorrelationRule =
            getRuleName(idConfig, ObjectConfig.IDENTITY_SCOPE_CORRELATION_RULE);
        this.scopeSelectionRule =
            getRuleName(idConfig, ObjectConfig.IDENTITY_SCOPE_SELECTION_RULE);
    }

    private String getRuleName(ObjectConfig config, String attrName)
        throws GeneralException {
        String name = (String) config.get(attrName);
        if (null != name) {
            Rule rule = getContext().getObjectByName(Rule.class, name);
            if (null != rule) {
                return rule.getName();
            }
        }

        return null;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getScopeAttribute() {
        return scopeAttribute;
    }

    public void setScopeAttribute(String scopeAttribute) {
        this.scopeAttribute = scopeAttribute;
    }

    public String getScopeCorrelationRule() {
        return scopeCorrelationRule;
    }

    public void setScopeCorrelationRule(String scopeCorrelationRule) {
        this.scopeCorrelationRule = scopeCorrelationRule;
    }

    public String getScopeSelectionRule() {
        return scopeSelectionRule;
    }

    public void setScopeSelectionRule(String scopeSelectionRule) {
        this.scopeSelectionRule = scopeSelectionRule;
    }

    public boolean isUnscopedGloballyAccessible() {
        return unscopedGloballyAccessible;
    }

    public void setUnscopedGloballyAccessible(boolean unscopedGloballyAccessible) {
        this.unscopedGloballyAccessible = unscopedGloballyAccessible;
    }

    public boolean isIdentityControlsAssignedScope() {
        return identityControlsAssignedScope;
    }

    public void setIdentityControlsAssignedScope(boolean b) {
        this.identityControlsAssignedScope = b;
    }

    public List<SelectItem> getIdentityAttributes() throws GeneralException {

        if (null == this.identityAttributes) {
            this.identityAttributes = new ArrayList<SelectItem>();
            
            Set<ObjectAttribute> sortedAttrs =
                new TreeSet<ObjectAttribute>(new Comparator<ObjectAttribute>() {
                    public int compare(ObjectAttribute o1, ObjectAttribute o2) {
                        return o1.getDisplayableName().compareTo(o2.getDisplayableName());
                    }
                });

            ObjectConfig config =
                getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
            if ((null != config) && (null != config.getObjectAttributes())) {
                sortedAttrs.addAll(config.getObjectAttributes());
            }

            this.identityAttributes.add(new SelectItem("", super.getMessage(MessageKeys.SELECT_IDENTITY_ATTR)));
            for (ObjectAttribute attr : sortedAttrs) {
                //We do not want to show System or Standard attributes.
                //The Standard Manager Attribute is an exception.
                if((!attr.isSystem() && !attr.isStandard()) || attr.getName().equals("manager")) {
                    this.identityAttributes.add(new SelectItem(attr.getName(), attr.getDisplayableName()));
                }
            }
        }
        
        return identityAttributes;
    }

    public List<SelectItem> getCorrelationRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.ScopeCorrelation, true);
    }

    public List<SelectItem> getSelectionRules() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.ScopeSelection, true);
    }

    public boolean isScopingEnabled() {
        return scopingEnabled;
    }
    
    public void setScopingEnabled(boolean b) {
        this.scopingEnabled = b;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to save the configuration changes.
     */
    public String save() throws GeneralException {
        
        // Load by name (not from cache) since we are saving.
        Configuration sysConfig =
            getContext().getObjectByName(Configuration.class, Configuration.OBJ_NAME);
        sysConfig.put(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE, this.unscopedGloballyAccessible);
        sysConfig.put(Configuration.IDENTITY_CONTROLS_ASSIGNED_SCOPE, this.identityControlsAssignedScope);
        sysConfig.put(Configuration.SCOPING_ENABLED, this.scopingEnabled);
        getContext().saveObject(sysConfig);

        ObjectConfig idConfig =
            getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        idConfig.put(ObjectConfig.IDENTITY_SCOPE_CORRELATION_ATTR, this.scopeAttribute);

        setRuleName(idConfig, ObjectConfig.IDENTITY_SCOPE_CORRELATION_RULE,
                    this.scopeCorrelationRule);
        setRuleName(idConfig, ObjectConfig.IDENTITY_SCOPE_SELECTION_RULE,
                    this.scopeSelectionRule);
        getContext().saveObject(idConfig);
        getContext().commitTransaction();

        return "configSaved";
    }

    public String cancel() {
        return "cancel";
    }

    private void setRuleName(ObjectConfig idConfig, String attr, String name)
        throws GeneralException {

        String ruleName = null;

        if (null != Util.getString(name)) {
            Rule rule = getContext().getObjectByName(Rule.class, name);
            if (null != rule) {
                ruleName = rule.getName();
            }
        }

        idConfig.put(attr, ruleName);
    }
}
