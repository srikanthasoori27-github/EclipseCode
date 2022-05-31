/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;

import sailpoint.api.CertificationTriggerHandler;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.Message.Type;
import sailpoint.web.SailPointObjectDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.policy.IdentitySelectorDTO;
import sailpoint.web.util.WebUtil;


/**
 * A DTO for editing IdentityTriggers.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentityTriggerDTO extends SailPointObjectDTO<IdentityTriggerDTO> {

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private transient Identity oldManagerFilter;
    private transient Identity newManagerFilter;

    private IdentityTrigger.Type type;
    private String ruleId;
    private String oldValueFilter;
    private String newValueFilter;
    private String attributeName;
    private IdentitySelectorDTO selector;
    private String handler;
    private String process;
    
    private List<SelectItem> types;
    
    // don't cache the rules - see getter
    //private List<SelectItem> rules;
    
    private List<SelectItem> attributes;
    private String rapidSetupFilterString;

    // Identity Processing Threshold
    private String identityProcessingThreshold;
    private String identityProcessingThresholdType;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public IdentityTriggerDTO(IdentityTrigger trigger) throws GeneralException {

        super(trigger);

        this.type = trigger.getType();

        if (null != trigger.getRule()) {
            this.ruleId = trigger.getRule().getName();
        }

        this.attributeName = trigger.getAttributeName();
        this.oldValueFilter = trigger.getOldValueFilter();
        this.newValueFilter = trigger.getNewValueFilter();
        this.selector =
            new IdentitySelectorDTO(trigger.getSelector(), true);
        this.handler = trigger.getHandler();

        if (IdentityTrigger.Type.ManagerTransfer.equals(trigger.getType())) {
            if (null != this.oldValueFilter) {
                this.oldManagerFilter =
                    getContext().getObjectByName(Identity.class, this.oldValueFilter);
            }
            if (null != this.newValueFilter) {
                this.newManagerFilter =
                    getContext().getObjectByName(Identity.class, this.newValueFilter);
            }
        }

        this.process = trigger.getMatchProcess();

        this.identityProcessingThreshold = trigger.getIdentityProcessingThreshold();
        this.identityProcessingThresholdType = trigger.getIdentityProcessingThresholdType();
    }
 
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // COMMIT AND VALIDATE
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Save the values from the DTO back onto the given trigger.
     */
    public void commit(IdentityTrigger trigger) throws GeneralException {

        super.commit(trigger);
        
        // Set the basic fields.
        trigger.setType(this.type);
        trigger.setHandler(this.handler);
        trigger.setAttributeName(this.attributeName);
        trigger.setOldValueFilter(this.oldValueFilter);
        trigger.setNewValueFilter(this.newValueFilter);

        // Using a select box to populate an actual rule is a pain, so we'll
        // just catch the ID of the trigger's rule and load it.
        Rule rule = null;
        if (null != Util.getString(this.ruleId)) {
            rule = getContext().getObjectByName(Rule.class, this.ruleId);
        }
        trigger.setRule(rule);

        // We already use the oldValueFilter fields for non-manager attribute
        // change detection, so we use some DTO fields for this.
        if (IdentityTrigger.Type.ManagerTransfer.equals(trigger.getType())) {
            String oldName =
                (null != this.oldManagerFilter) ? oldManagerFilter.getName() : null;
            String newName =
                (null != this.newManagerFilter) ? newManagerFilter.getName() : null;
            trigger.setOldValueFilter(oldName);
            trigger.setNewValueFilter(newName);
        }
        
        // Convert the identity selector.
        IdentitySelector selector = null;
        if ((null != this.selector) && !this.selector.isPlaceholder()) {
            selector = this.selector.convert();
        }
        trigger.setSelector(selector);

        // save the process for RapidSetup type
        if (IdentityTrigger.Type.RapidSetup.equals(trigger.getType())) {
            trigger.setMatchProcess(this.process);
        }

        // save the Identity Processing Threshold parameters
        trigger.setIdentityProcessingThreshold(this.identityProcessingThreshold);
        trigger.setIdentityProcessingThresholdType(this.identityProcessingThresholdType);
        
        // Remove any fields from the trigger that aren't relevant for the type.
        scrubTriggerFields(trigger);
    }
    
    /**
     * Null out any unused fields on the trigger according to the type.
     */
    private static void scrubTriggerFields(IdentityTrigger trigger) {

        if (!IdentityTrigger.Type.Rule.equals(trigger.getType())) {
            trigger.setRule(null);
        }
        
        if (!IdentityTrigger.Type.AttributeChange.equals(trigger.getType()) &&
            !IdentityTrigger.Type.ManagerTransfer.equals(trigger.getType())) {
            trigger.setOldValueFilter(null);
            trigger.setNewValueFilter(null);
        }

        if (!IdentityTrigger.Type.AttributeChange.equals(trigger.getType())) {
            trigger.setAttributeName(null);
        }

        if (!IdentityTrigger.Type.RapidSetup.equals(trigger.getType())) {
            trigger.setMatchProcess(null);
        }
    }

    /**
     * Validate that this trigger has all required information.  This will add
     * error messages to the FacesContext and return null if there are any
     * validation problems.
     */
    public boolean validate() {

        boolean valid = true;
        
        // Require name.
        if (null == getName()) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_IDENTITY_TRIGGER_NAME_REQUIRED));
            valid = false;
        }
        
        // Require attribute for attribute change.
        if (IdentityTrigger.Type.AttributeChange.equals(this.type) &&
            (null == this.attributeName)) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_IDENTITY_TRIGGER_ATTR_NAME_REQUIRED));
            valid = false;
        }
        
        // Require rule for rule.
        if (IdentityTrigger.Type.Rule.equals(this.type) &&
            (null == Util.getString(this.ruleId))) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_IDENTITY_TRIGGER_RULE_REQUIRED));
            valid = false;
        }

        // Require process for RapidSetup
        if (IdentityTrigger.Type.RapidSetup.equals(this.type) &&
                (null == this.getProcess())) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_IDENTITY_TRIGGER_PROCESS_REQUIRED));
            valid = false;
        }

        // Validate the selector
        if (this.selector != null && !IdentityTrigger.Type.Alert.equals(this.type)) {
            try {
                this.selector.validate();
            }
            catch (GeneralException e) {
                addMessage(e);
                valid = false;
            }
        }

        // Validate the Identity Processing Threshold values
        if (!Util.isNullOrEmpty(this.identityProcessingThreshold)) {
            // If Threshold is set, must have a threshold type
            if (Util.isNullOrEmpty(this.identityProcessingThresholdType)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.UI_ERR_IDENTITY_TRIGGER_THRESHOLD_MISSING_TYPE));
                valid = false;
            }
            if (!Util.isNumeric(this.identityProcessingThreshold)) {
                addMessage(new Message(Message.Type.Error, MessageKeys.UI_ERR_IDENTITY_TRIGGER_THRESHOLD_NOT_NUMERIC));
                valid = false;
                // Threshold must be positive value
            } else {
                float threshold = Util.atof(this.identityProcessingThreshold);
                if (threshold < 0) {
                    addMessage(new Message(Message.Type.Error, MessageKeys.UI_ERR_IDENTITY_TRIGGER_THRESHOLD_NEGATIVE));
                    valid = false;
                    // If Threshold Type is 'percentage', Threshold must be less
                    // than or equal to 100
                } else if (Util.nullSafeCaseInsensitiveEq(this.identityProcessingThresholdType, "percentage")
                        && threshold > 100) {
                    addMessage(new Message(Message.Type.Error, MessageKeys.UI_ERR_IDENTITY_TRIGGER_THRESHOLD_TOO_HIGH));
                    valid = false;
                }
            }
        }

        return valid;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public IdentityTrigger.Type getType() {
        return this.type;
    }
    
    public void setType(IdentityTrigger.Type type) {
        this.type = type;
    }

    public String getHandler() {
        return this.handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }
    
    public String getRuleId() {
        return this.ruleId;
    }

    public void setRuleId(String triggerRuleId) {
        this.ruleId = triggerRuleId;
    }
    
    public String getAttributeName() {
        return this.attributeName;
    }
    
    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }
    
    public String getOldValueFilter() {
        return this.oldValueFilter;
    }
    
    public void setOldValueFilter(String oldValueFilter) {
        this.oldValueFilter = oldValueFilter;
    }
    
    public String getNewValueFilter() {
        return this.newValueFilter;
    }
    
    public void setNewValueFilter(String newValueFilter) {
        this.newValueFilter = newValueFilter;
    }
    
    public Identity getOldManagerFilter() {
        return this.oldManagerFilter;
    }
    
    public void setOldManagerFilter(Identity oldManagerFilter) {
        this.oldManagerFilter = oldManagerFilter;
    }
    
    public Identity getNewManagerFilter() {
        return this.newManagerFilter;
    }
    
    public void setNewManagerFilter(Identity newManagerFilter) {
        this.newManagerFilter = newManagerFilter;
    }
    
    public IdentitySelectorDTO getSelector() {
        if (null == this.selector) {
            this.selector = new IdentitySelectorDTO(null, true);
        }
        return this.selector;
    }
    
    public void setSelector(IdentitySelectorDTO triggerSelector) {
        this.selector = triggerSelector;
    }

    public String getIdentityProcessingThreshold() {
        return this.identityProcessingThreshold;
    }

    public void setIdentityProcessingThreshold(String threshold) {
        this.identityProcessingThreshold = threshold;
    }

    public String getIdentityProcessingThresholdType() {
        return this.identityProcessingThresholdType;
    }

    public void setIdentityProcessingThresholdType(String thresholdType) {
        this.identityProcessingThresholdType = thresholdType;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the identity trigger types.
     */
    public List<SelectItem> getTypes() {
        
        if (null == this.types) {
            this.types = new ArrayList<SelectItem>();

            Locale l = super.getLocale();
            TimeZone t = super.getUserTimeZone();
            
            types.add(new SelectItem(IdentityTrigger.Type.Create,
                                     IdentityTrigger.Type.Create.getLocalizedMessage(l, t)));
            types.add(new SelectItem(IdentityTrigger.Type.ManagerTransfer,
                                     IdentityTrigger.Type.ManagerTransfer.getLocalizedMessage(l, t)));
            types.add(new SelectItem(IdentityTrigger.Type.AttributeChange,
                                     IdentityTrigger.Type.AttributeChange.getLocalizedMessage(l, t)));
            types.add(new SelectItem(IdentityTrigger.Type.Rule,
                                     IdentityTrigger.Type.Rule.getLocalizedMessage(l, t)));
            types.add(new SelectItem(IdentityTrigger.Type.NativeChange,
                                     IdentityTrigger.Type.NativeChange.getLocalizedMessage(l, t)));
            types.add(new SelectItem(IdentityTrigger.Type.Alert,
                                     IdentityTrigger.Type.Alert.getLocalizedMessage(l, t)));
            if (!getHandler().equals(CertificationTriggerHandler.class.getName())) {
                types.add(new SelectItem(IdentityTrigger.Type.RapidSetup,
                                         IdentityTrigger.Type.RapidSetup.getLocalizedMessage(l, t)));
            }
        }

        return types;
    }

    /**
     * Return the attributes available for an attribute change trigger.
     */
    public List<SelectItem> getAttributes() throws GeneralException {
        
        if (null == this.attributes) {
            ObjectConfig oc = Identity.getObjectConfig();
            List<ObjectAttribute> attrs = oc.getSearchableAttributes();
            Collections.sort(attrs, ObjectAttribute.getByDisplayableNameComparator());
    
            this.attributes = new ArrayList<SelectItem>();
            for (ObjectAttribute attr : attrs) {
                // Don't add the manager attribute since this is handled specially.
                if (!Identity.ATT_MANAGER.equals(attr.getName())) {
                    this.attributes.add(new SelectItem(attr.getName(), attr.getDisplayableName()));
                }
            }
        }

        return this.attributes;
    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String proc) {
        this.process = proc;
    }

    public String getRapidSetupFilterString() {
        return rapidSetupFilterString;
    }



    /**
     * Return the rules for a rule-based trigger.
     * 
     * DO NOT cache the rules list, or you won't pick up new rules added by
     * the rule editor while working with the identity trigger.  See bug 
     * #5901 for details on the rule editor. - DHC
     */
    public List<SelectItem> getRules() throws GeneralException {
        
        return WebUtil.getRulesByType(getContext(), Rule.Type.IdentityTrigger, true);
        
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // selector.xhtml action listeners
    //
    //////////////////////////////////////////////////////////////////////

    public void addSelectorAttribute(ActionEvent e) {
        try {
            if (this.selector != null)
                this.selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Entitlement.name());
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error,ge.getLocalizedMessage()));
        }
    }

    public void addSelectorIdentityAttribute(ActionEvent e) {
        try {
            if (this.selector != null) {
                this.selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                this.selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.IdentityAttribute.name());
            }   
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error,ge.getLocalizedMessage()));
        }
    }
    
    public void addSelectorPermission(ActionEvent e) {
        try {
            if (this.selector != null)
                this.selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Permission.name());
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error,ge.getLocalizedMessage()));
        }
    }

    public void addSelectorRoleAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.RoleAttribute.name());
            }
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error, ge.getLocalizedMessage()));
        }
    }

    public void addSelectorEntitlementAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.EntitlementAttribute.name());
            }
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error, ge.getLocalizedMessage()));
        }
    }


    public void deleteSelectorTerms(ActionEvent e) {
        if (this.selector != null)
            this.selector.deleteSelectedTerms();
    }
    
    public void groupSelectedTerms(ActionEvent e) {
        if (this.selector != null)
            this.selector.groupSelectedTerms();
    }
    
    public void ungroupSelectedTerms(ActionEvent e) {
        if (this.selector != null)
            this.selector.ungroupSelectedTerms();
    }
}
