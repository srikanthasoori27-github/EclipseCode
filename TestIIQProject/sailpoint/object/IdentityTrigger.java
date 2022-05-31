/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.rapidsetup.constraint.TriggerPredicate;
import sailpoint.api.SailPointContext;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.tools.ConvertTools;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * An identity trigger is used used to determine when certain events happen to
 * an identity. These are attached to some sort of action that will be executed
 * when an identity meets the criteria of the trigger.
 */
@XMLClass
public class IdentityTrigger extends SailPointObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    public static final String PARAM_CERT_DEF_ID = "certificationDefinitionId";
    public static final String PARAM_WORKFLOW = "workflow";

    public static final String MATCH_PARAM_PROCESS = "businessProcess";
    public static final String IDENTITY_PROCESSING_THRESHOLD = "identityProcessingThreshold";
    public static final String IDENTITY_PROCESSING_THRESHOLD_TYPE = "identityProcessingThresholdType";

    ////////////////////////////////////////////////////////////////////////////
    //
    // TYPE
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * used to declare the method parameters required for the inactiveChecker lambda
     */
    public interface InactiveCheck {
        boolean isInactive(IdentityTrigger trigger);
    }

    /**
     * default inactiveCheck for types that only support isDisabled().
     * @param trigger
     * @return false always
     */
    public static boolean defaultInactiveCheck(IdentityTrigger trigger) {
        return false;
    }

    /**
     * Enumeration of the possible event types.
     */
    @XMLClass(xmlname="IdentityTriggerType")
    public static enum Type implements Localizable {
        Create(MessageKeys.IDENTITY_TRIGGER_TYPE_CREATE, false, false, true),
        Delete(MessageKeys.IDENTITY_TRIGGER_TYPE_DELETE, false, true, false),
        AttributeChange(MessageKeys.IDENTITY_TRIGGER_TYPE_ATTRIBUTE_CHANGE, true, true, true),
        Rule(MessageKeys.IDENTITY_TRIGGER_TYPE_RULE, true, true, true),
        // This is a specialization of the attribute change type, but is
        // interesting enough that we'll let it have its own type.
        ManagerTransfer(MessageKeys.IDENTITY_TRIGGER_TYPE_MANAGER_TRANSFER, true, true, true),
        NativeChange(MessageKeys.IDENTITY_TRIGGER_TYPE_NATIVE_CHANGE, true, true, true),
        Alert(MessageKeys.IDENTITY_TRIGGER_TYPE_ALERT, false, false, false),
        // RapidSetup type also supports inactive check
        RapidSetup(MessageKeys.IDENTITY_TRIGGER_TYPE_RAPIDSETUP, true, true, true, RapidSetupConfigUtils::isTriggerInactive);

        private String messageKey;
        private boolean comparesTwoIdentities;
        private boolean needsPreviousIdentity;
        private boolean needsNewIdentity;
        private InactiveCheck inactiveCheck;

        private Type(String messageKey, boolean comparesTwo, boolean needsPreviousIdentity, boolean needsNewIdentity) {
            this.messageKey = messageKey;
            this.comparesTwoIdentities = comparesTwo;
            this.needsPreviousIdentity = needsPreviousIdentity;
            this.needsNewIdentity = needsNewIdentity;
            this.inactiveCheck = IdentityTrigger::defaultInactiveCheck;
        }

        private Type(String messageKey, boolean comparesTwo, boolean needsPreviousIdentity, boolean needsNewIdentity, InactiveCheck checker) {
            this(messageKey, comparesTwo, needsPreviousIdentity, needsNewIdentity);
            inactiveCheck = checker;
        }


        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(messageKey);
            return msg.getLocalizedMessage(locale, timezone);
        }

        /**
         * Return whether a previous and current identity are both needed to
         * determine whether a trigger of this type is matched.
         */
        public boolean isComparesTwoIdentities() {
            return this.comparesTwoIdentities;
        }

        /**
         * Is a previous identity needed for the trigger match?
         */
        public boolean isNeedsPreviousIdentity() { return needsPreviousIdentity;}

        /**
         * Is a new identity needed for the trigger match?
         */
        public boolean isNeedsNewIdentity() { return needsNewIdentity; }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private Type type;

    // The rule to run for an Rule trigger.
    private Rule rule;
    
    // Only used for attribute change triggers.
    private String attributeName;
    // Consider using full-blown filters here.  This gives more power but is
    // harder to configure.  We could put this in a rule if we need that much
    // power.
    private String oldValueFilter;
    private String newValueFilter;

    private IdentitySelector selector;
    
    // The fully-qualified name of the IdentityTriggerHandler class.
    private String handler;

    // The parameters to pass to the IdentityTriggerHandler.
    private Attributes<String,Object> parameters;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor - required for persistence.
     */
    public IdentityTrigger() {
        super();
    }

    public void load() { 

        if (this.rule != null)
            this.rule.load();

        if (this.selector != null)
            this.selector.load();

        if (getOwner() != null)
            getOwner().load();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    @XMLProperty
    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @XMLProperty(xmlname="TriggerRule",mode=SerializationMode.REFERENCE)
    public Rule getRule() {
        return this.rule;
    }

    public void setRule(Rule rule) {
        this.rule = rule;
    }

    @XMLProperty
    public String getAttributeName() {
        return this.attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    @XMLProperty
    public IdentitySelector getSelector() {
        return this.selector;
    }

    public void setSelector(IdentitySelector selector) {
        this.selector = selector;
    }

    @XMLProperty
    public String getOldValueFilter() {
        return this.oldValueFilter;
    }

    public void setOldValueFilter(String oldValueFilter) {
        this.oldValueFilter = oldValueFilter;
    }

    @XMLProperty
    public String getNewValueFilter() {
        return this.newValueFilter;
    }

    public void setNewValueFilter(String newValueFilter) {
        this.newValueFilter = newValueFilter;
    }

    @XMLProperty
    public String getHandler() {
        return this.handler;
    }
    
    public void setHandler(String handler) {
        this.handler = handler;
    }
    
    @XMLProperty(xmlname="HandlerParameters")
    public Attributes<String, Object> getParameters() {
        return this.parameters;
    }
    
    public void setParameters(Attributes<String, Object> parameters) {
        this.parameters = parameters;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // PSEUDO GETTER AND SETTERS for handler parameters (TRIGGER SPECIFIC)
    //
    ////////////////////////////////////////////////////////////////////////////

    public void setAttribute(String attrName, Object value) {
        if (null == this.parameters) {
            this.parameters = new Attributes<String,Object>();
        }
        this.parameters.put(attrName, value);
    }

    public void setCertificationDefinition(CertificationDefinition def) {
        this.setAttribute(PARAM_CERT_DEF_ID, def.getId());
    }

    public CertificationDefinition getCertificationDefinition(Resolver r)
        throws GeneralException {

        CertificationDefinition def = null;
        if (null != this.parameters) {
            String id = this.parameters.getString(PARAM_CERT_DEF_ID);
            if (null != id) {
                def = r.getObjectById(CertificationDefinition.class, id);
            }
        }
        return def;
    }

    public void setWorkflow(Workflow workflow) {
        if (null != workflow) {
            this.setAttribute(PARAM_WORKFLOW, workflow.getName());
        }
        else if (null != this.parameters) {
            this.parameters.remove(PARAM_WORKFLOW);
        }
    }
    
    public Workflow getWorkflow(Resolver r) throws GeneralException {
        
        Workflow flow = null;
        if (null != this.parameters) {
            String name = this.parameters.getString(PARAM_WORKFLOW);
            if (null != name) {
                flow = r.getObjectByName(Workflow.class, name);
            }
        }
        return flow;
    }

    public String getWorkflowName() {
        return (parameters != null) ? parameters.getString(PARAM_WORKFLOW) : null;
    }

    public void setWorkflowName(String s) {
        setAttribute(PARAM_WORKFLOW, s);
    }

    public void setMatchProcess(String process) {
        setAttribute(MATCH_PARAM_PROCESS, process);
    }

    public String getMatchProcess() {
        String process = null;
        if (null != this.parameters) {
            process = (String)this.parameters.get(MATCH_PARAM_PROCESS);
        }
        return process;
    }

    public void setIdentityProcessingThreshold(String value) {
        setAttribute(IDENTITY_PROCESSING_THRESHOLD, value);
    }

    public String getIdentityProcessingThreshold() {
        return Util.getString(this.parameters, IDENTITY_PROCESSING_THRESHOLD);
    }

    public void setIdentityProcessingThresholdType(String type) {
        setAttribute(IDENTITY_PROCESSING_THRESHOLD_TYPE, type);
    }

    public String getIdentityProcessingThresholdType(){
        String thresholdType = null;
        if (null != this.parameters) {
            thresholdType = Util.getString(this.parameters, IDENTITY_PROCESSING_THRESHOLD_TYPE);
        }
        return thresholdType;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitIdentityTrigger(this);
    }

    /**
     * Provides a dynamic way to check if a trigger is active.  Note
     * that this is separate from the isDisabled() method, which just
     * checks the disabled boolean.
     *
     * Only RapidSetup currently supports isInactive() -- all other
     * types will just return false.
     *
     * @return the value of the trigger's type's inactiveCheck lambda
     */
    public boolean isInactive() {
        boolean inactive = false;
        Type type = getType();
        if (type != null) {
            inactive = type.inactiveCheck.isInactive(this);
        }
        return inactive;
    }

    /**
     * Return whether the given previous and new identity states match this
     * trigger. Note that a null previous identity indicates a creation and a
     * null new identity indicates a deletion.
     * 
     * @param  prevIdentity  The previous identity (null for a creation).
     * @param  newIdentity   The new identity (null for a deletion).
     * @param  matcher       The matcher to use to evaluate the selector.
     * @param  spContext     SailPointContext in case of Rule or RapidSetup type
     * 
     * @return True if the trigger is matched by the given identities, false
     *         otherwise.
     */
    public boolean matches(Identity prevIdentity, Identity newIdentity,
                           IdentityMatcher matcher, SailPointContext spContext)
        throws GeneralException {
        
        boolean match = false;

        // If there is a selector, first make sure that the new identity
        // matches it.
        Identity toMatch = (null != newIdentity) ? newIdentity : prevIdentity;
        if ((null != this.selector) && !matcher.isMatch(this.selector, toMatch)) {
            if (Type.RapidSetup.equals(getType())) {
                TriggerPredicate.logSelectorFail(this, toMatch);
            }
            return false;
        }

        // If we got here, match based on the type.  Could move this onto Type.
        String attrName = this.attributeName;
        switch (this.type) {
        case Create:
            if (newIdentity != null) {
                match = newIdentity.needsCreateProcessing(Identity.CreateProcessingStep.Trigger);
            }
            break;

        case Delete:
            match = (null == newIdentity);
            break;

        case ManagerTransfer:
            // Manager transfer is an attribute change on ATT_MANAGER.
            attrName = Identity.ATT_MANAGER;
        case AttributeChange:
            if ((null != prevIdentity) && (null != newIdentity)) {
                Object oldVal = prevIdentity.getAttribute(attrName);
                Object newVal = newIdentity.getAttribute(attrName);

                match = !Util.nullSafeEq(oldVal, newVal, true) &&
                        matchesValueFilters(oldVal, newVal);
            }
            break;

        case Rule:
            // Considered only running rules if both previous and new are
            // non-null so that rule writers don't have to worry about
            // null/void.  I have capitulated.
            // jsl - not sure what the previous comments mean, but 14556
            // asks for passing null so rule writers don't have to worry
            // about void, they can consistently check for null
            Map<String,Object> params = new HashMap<String,Object>();
            params.put("newIdentity", newIdentity);
            params.put("previousIdentity", prevIdentity);
            // jsl - added trigger itself so the rule can be shared and
            // have usage sensitive behavior, also for unit tests
            params.put("trigger", this);
            Object result = spContext.runRule(this.rule, params);
            match = Util.otob(result);
            break;            
            
        case NativeChange:
            // djs: all we care about in this case is if the filtering
            // of identity above worked.  We call this methjod 
            // during aggregation to figure out if we should 
            // store the native change detections and again durin
            // refresh when they are fired.
            // Technically in the second case it would be nice to 
            // check if the identity had stored change detections 
            // before returning true.
            match = true;
            break;
        case Alert:
            //Never want to process these with Identity Processing.
            //Only used for events
            match = false;
            break;

        case RapidSetup:
            TriggerPredicate condition = new TriggerPredicate();
            match = condition.evaluate(this, spContext, newIdentity, prevIdentity);
            break;

        default:
            throw new GeneralException("Unhandled type for match: " + this.type);
        }

        return match;
    }

    /**
     * Return whether the old and new value filters are matched (if defined).
     */
    private boolean matchesValueFilters(Object oldVal, Object newVal) {
        
        boolean matchesOld = true;
        boolean matchesNew = true;
        
        // Only check the value filters if they are specified.
        if (null != Util.getString(this.oldValueFilter)) {
            // Convert the filter value to match the value we're matching against.
            Object converted =
                (null != oldVal) ? ConvertTools.convert(this.oldValueFilter, oldVal.getClass())
                                 : this.oldValueFilter;
            matchesOld = Util.nullSafeEq(converted, oldVal, true);
        }

        if (null != Util.getString(this.newValueFilter)) {
            // Convert the filter value to match the value we're matching against.
            Object converted =
                (null != newVal) ? ConvertTools.convert(this.newValueFilter, newVal.getClass())
                                 : this.newValueFilter;
            matchesNew = Util.nullSafeEq(converted, newVal, true);
        }

        return matchesOld && matchesNew;
    }

    /**
     * Create an identity change event for this trigger given the previous and
     * new identities. This should only be called if matches() returns true.
     * 
     * Build an event based on the neu
     * object and fall back to the previous if null. 
     * 
     */
    public IdentityChangeEvent createEvent(Identity prev, Identity neu) 
        throws GeneralException {

        IdentityChangeEvent event = null;

        switch (this.type) {
        case Create:
            event = new IdentityChangeEvent(neu);
            break;
        case Delete:
            event = new IdentityChangeEvent(prev.getName());
            break;
        case AttributeChange:
        case ManagerTransfer:
            // Do we want the changed values in the event?
            event = new IdentityChangeEvent(prev, neu);
            break;
        case Rule:
            event = new IdentityChangeEvent(prev, neu);
            break;
        case RapidSetup:
            event = new IdentityChangeEvent(prev, neu);
            break;
        case NativeChange :
            if ( neu != null ) {
                List<NativeChangeDetection> nativeChanges = neu.getNativeChangeDetections();
                if ( Util.size(nativeChanges) > 0  ) 
                    event = new IdentityChangeEvent(neu.getName(), nativeChanges);
            } else 
            if ( prev != null ) {
                List<NativeChangeDetection> nativeChanges = prev.getNativeChangeDetections();
                if ( Util.size(nativeChanges) > 0 ) 
                    event = new IdentityChangeEvent(prev.getName(), nativeChanges);
            }
            break;
        case Alert:
            //Do nothing
            break;
            
        default:
            throw new GeneralException("Unhandled type for match: " + this.type);
        }

        if ( event != null)
            // Set the source for the event.
            event.setTrigger(this);
        
        return event;
    }

    /**
     * Return a formatted message that describes what caused the given event.
     * 
     * @ignore
     * This could return Messages that could be localized, but is currently just
     * being stuck into an audit record so localization isn't so important.
     */
    public String formatCause(IdentityChangeEvent event)
        throws RuntimeException {

        String cause = null;
        String attrName = this.attributeName;
        
        switch (this.type) {
        case Create: cause = "Identity created"; break;
        case Delete: cause = "Identity deleted"; break;
        case ManagerTransfer:
            attrName = Identity.ATT_MANAGER;
        case AttributeChange:
            Object oldVal = event.getOldObject().getAttribute(attrName);
            Object newVal = event.getNewObject().getAttribute(attrName);
            cause = "Attribute '" + attrName + "' changed from " + oldVal + " to " + newVal;
            break;
        case Rule: cause = "Rule '" + this.rule.getName() + "' matched"; break;

        case RapidSetup: cause = "RapidSetup trigger matched for " + this.getMatchProcess(); break;
        
        case NativeChange :
            List<String> attrNames = new ArrayList<String>();
            List<String> ops  = new ArrayList<String>();
            List<String> changeStrings = new ArrayList<String>();

            List<NativeChangeDetection> nativeChanges = event.getNativeChanges();
            if ( Util.size(nativeChanges) > 0 ) {
                for ( NativeChangeDetection changes : nativeChanges )  {
                    if ( changes == null ) continue;
                    
                    AccountRequest.Operation op = changes.getOperation();
                    String opString = null;
                    if ( op == null ) 
                        opString = AccountRequest.Operation.Modify.toString();
                    else
                        opString = op.toString();
                    
                    if ( !ops.contains(opString) ) {
                        ops.add(opString);
                    }
                    List<Difference> diffs = changes.getDifferences();
                    if ( Util.size(diffs) > 0 ) {
                        for ( Difference diff : diffs ) {
                            String diffAttr = diff.getAttribute();
                            if ( diffAttr != null ) {
                                if ( !attrNames.contains(diffAttr)  )
                                    attrNames.add(diffAttr);
                            }
                            String added = diff.getAddedValuesCsv();
                            String removed = diff.getRemovedValuesCsv();  
                            if ( added != null || removed != null ) {
                                //
                                // MemberOf(Added=foo,Removed=Bar);
                                //
                                String changeString = diffAttr + "(";
                                if ( added != null ) {
                                    changeString += "Added=[" + added +"]";
                                }
                                if ( removed != null ) {
                                    if ( added != null )
                                        changeString += ",";
                                    changeString += "Removed=[" + removed + "]";
                                }
                                changeString += ")";
                                
                                if ( !changeStrings.contains(changeString) ) { 
                                    changeStrings.add(changeString);
                                }
                            //IIQETN-4864 Handling attribute modification
                            } else if ( null != diff ) {
                                String changeString = diffAttr + "(";
                                changeString += "Modified[" + diff.getOldValue() +"] ";
                                changeString += "to [" + diff.getNewValue() + "])";

                                if ( !changeStrings.contains(changeString) ) {
                                    changeStrings.add(changeString);
                                }
                            }
                        }
                    }
                }
            }
            if ( Util.size(attrNames) > 0 ) {
                cause = "Native ["+ Util.listToCsv(ops)  + "] detected. Summary of changes  [" + Util.listToCsv(changeStrings) + "]";
            } else {
                cause = "Native changes were detected.";
            }            
            break;
        case Alert:
            cause = "Alert Matched";
            break;
        default:
            throw new RuntimeException("Unhandled type: " + this.type);
        }
        return cause;
    }
}
