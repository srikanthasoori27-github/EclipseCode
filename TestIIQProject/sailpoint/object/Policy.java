/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class encapsulating the definition of a policy.
 *
 * Author: Jeff
 * 
 * These are structured a bit like TaskDefinitions, in that 
 * we have a generic policy definition model (Policy) that
 * references a class that implements the policy logic 
 * (PolicyExecutor).  This makes the implementation of policies
 * slightly more complicated, but makes management of policy
 * artifacts like PolicyViolation easier because we have only
 * one class to deal with.
 *
 * Policies are a bit like Rules except they have more metadata
 * surrounding them.  Possibly policies should become just another
 * form of Rule executor, but it feels  like we will want to put
 * other things here like exclusion lists, enable/disable status, 
 * expiration dates etc,
 * 
 * The unfortunate side effect of using a generic model is that
 * extra data for the policy executor either has to be stuffed
 * into an XML blob, or we have to have executor specific fields
 * in the Policy class.  The later violates encapsulation but 
 * is relatively painless if we don't have too many policies and 
 * they aren't customer extensible.  An alternative is to define
 * concrete subclasses and use union-subclass in the Hibernate mappings
 * so we can search over all subclasses with one Hibernate request, but
 * this causes other complications.
 *
 * As of 2.5 a policy may have a Signature that can be used to 
 * automatically generate a configuration form with the values
 * left in the attribute map.  This makes it easier to build
 * custom policies without always having to write UI config pages.
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Interrogator;
import sailpoint.api.SailPointContext;
import sailpoint.object.PolicyViolation.IPolicyViolationOwnerSource;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Localizable;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class encapsulating the definition of a policy.
 *
 * There are several types of policy but the same model is
 * used to simplify persistence. Some properties defined
 * in this class are only used with certain policy types.
 */
@XMLClass
public class Policy extends SailPointObject implements IPolicyViolationOwnerSource, Describable
{
    private static final long serialVersionUID = 3951316707421111764L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Policy.class);

    // jsl - Why is this here?  Do we need a registry of default policy names?
    // We don't have this for any of the other polices...

    public static final String POLICY_NAME_ACTIVITY = "Activity Policy";

    /**
     * The name of an optional policy attribute whose value is 
     * used by the PolicyScorer as the default risk weight for
     * violations of this policy. This is used with custom policies
     * that do not have GenericConstraints which is where weights 
     * usually live.  
     * 
     * @ignore
     * I decided to make this a policy attribute rather than a 
     * member field because I'm sick of Hibernate schema upgrades
     * and this makes it easier to deal with in the generated
     * Signature form.
     */
    public static final String ARG_RISK_WEIGHT = "riskWeight";

    /**
     * The plugin name argument.
     */
    public static final String ARG_PLUGIN_NAME = "pluginName";

    /**
     * The name of an optional policy attribute, is used in AdvancedPolicy to 
     * indicate it needs to check effective for all MatchTerms.
     */
    public static final String ARG_CHECK_EFFECTIVE = "checkEffective";

    ///////////////////////////////////////////////////////////////////////////
    //
    // Enumerations
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Constants for the default policy types.
     * @ignore
     * Type names are extensible so we no longer use an enumeration.
     */
    public static final String TYPE_GENERIC = "Generic";
    public static final String TYPE_SOD = "SOD";
    public static final String TYPE_ENTITLEMENT_SOD = "EntitlementSOD";
    public static final String TYPE_EFFECTIVE_ENTITLEMENT_SOD = "EffectiveEntitlementSOD";
    public static final String TYPE_ACTIVITY = "Activity";
    public static final String TYPE_RISK = "Risk";
    public static final String TYPE_ACCOUNT = "Account";
    public static final String TYPE_ADVANCED = "Advanced";
    public static final String TYPE_CUSTOM = "Custom";

    /**
     * @deprecated  This is deprecated and only used during the upgrade from 2.5 to 3.0.
     * Moving away from a Type enumeration and just representing
     * the various fields as Policy fields to make policy types extensible.
     * The Hibernate column was changed to a string so the
     * enumeration can be made private to weed out any stray references.
     */
    @Deprecated
    private enum Type {

        Generic(GenericConstraint.class, 
                Arrays.asList(CertificationAction.Status.Mitigated,
                              CertificationAction.Status.Delegated), 
                null, "policy_type_generic"),

        SOD(SODConstraint.class,"sodpolicy.xhtml","policy_type_sod"),

        Activity(ActivityConstraint.class, 
                 Arrays.asList(CertificationAction.Status.Mitigated,
                               CertificationAction.Status.Delegated), 
                 "activitypolicy.xhtml", "policy_type_activity"),

        Risk(GenericConstraint.class, "genericpolicy.xhtml", "policy_type_risk");

        private Class<? extends BaseConstraint> constraintClass;
        private List<CertificationAction.Status> allowedStatus;
        private String configPage;
        private String messageKey;

        /**
         * @param constraintClass Class of constraint making up this policy
         * @param configPage Page used to configure this policy type.
         * @param messageKey Key for this type's display name.
         */
        Type(Class<? extends BaseConstraint> constraintClass, String configPage, String messageKey) {
            this(constraintClass, Arrays.asList(CertificationAction.Status.values()),configPage, messageKey);
        }

        /**
         * Convenience so Lists which are easy to build can be assigned to policy type.
         * The list is converted to a set.
         *
         * @param constraintClass Class of constraint making up this policy
         * @param allowedStatus Statuses allowed for policies of this type.
         * @param configPage Page used to configure this policy type.
         * @param messageKey Key for this type's display name.
         */
        Type(Class<? extends BaseConstraint> constraintClass, List<CertificationAction.Status> allowedStatus,
                String configPage, String messageKey) {
            this.configPage = configPage;
            this.messageKey = messageKey;
            this.constraintClass = constraintClass;
            this.allowedStatus = allowedStatus;
        }

        /**
         * @param status The status to check
         * @return True if this policy type allows the given status
         */
        public boolean isStatusAllowed(CertificationAction.Status status){
            return allowedStatus!=null ? allowedStatus.contains(status) : false;
        }

        /**
         * @return Class of constraint making up this policy
         */
        public Class<? extends BaseConstraint> getConstraintClass() {
            return constraintClass;
        }

        public String getConfigPage() {
            return configPage;
        }

        public void setConfigPage(String configPage) {
            this.configPage = configPage;
        }

        /**
         * @return Key for this type's display name.
         */
        public String getMessageKey() {
            return messageKey;
        }

    };

    /**
     * An enumeration of states a policy can be in.
     * @ignore
     * Making this an enum rather than a boolean so we can extend it
     * later if necessary, also avoids the silly null value upgrade
     * problem in Hibernate.
     */
    @XMLClass(xmlname="PolicyState")
    public enum State implements Localizable {

        Inactive("policy_state_inactive"),
        Active("policy_state_active");

        private String messageKey;

        State(String messageKey) {
            this.messageKey = messageKey;
        }


        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault()); 
        }

        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            return Internationalizer.getMessage(messageKey, locale);
        }
    };

    /**
     * @ignore
     * Make this an enum so that it can be 
     * extended later to include workflow etc
     */
    @XMLClass
    public static enum ViolationOwnerType {
        None,
        Identity,
        Manager,
        Rule;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * True if this is a template policy. These will not be displayed
     * in the policy table, but they are used to build the select list
     * of policy types when creating new policies. Formerly this was
     * done using the Type enumeration.
     */
    boolean _template;

    /**
     * Transient field indicating that this policy should not be cached.
     * This is used to mark advanced policies containing inline Script objects
     * which are not yet thread safe. Until 19100 is fixed, 
     * Policy objects will be inspected when they are loaded into the cache and this flag
     * will be set if any Scripts are found. When this Policy is requested the cache
     * manager will always load a fresh one from disk.
     */
    boolean _noCache;

    /**
     * The type name.
     * Prior to 3.0 this was an enumeration, now it can be anything
     * for custom types.
     */
    String _type;

    /**
     * The catalog key for localizing the type name.
     * This is set only for built-in types.
     */
    String _typeKey;

    /**
     * The fully qualified class name to the <code>PolicyExecutor</code> implementation.
     */
    String _executor;

    /**
     * The URL fragment of a JSF page used to configure this policy.
     * When set, this is expected to be a path to a JSF page relative to
     * web/define/policy that will allow a user to configure a policy.
     * If not set it defaults to genericpolicy.xhtml.
     */
    String _configPage;

    /**
     * The allowed certification actions for violations of this policy.
     * This is a CSV, if the value is null then all actions are allowed.
     * The allowed actions are being encoded using CertificationStatus
     * names, so the tense is technically wrong, if "Approved" is
     * on this list, then you are allowed to take the "Approve" action
     * on this violation.
     */
    String _certificationActions;

    /**
     * Global policies are enabled by setting this flag.
     * @ignore
     * Is a flag enough or do we need a state enumeration?
     */
    State _state = State.Inactive;

    /**
     * Optional arguments to the executor. When descriptions
     * are set on the Policy they are maintained in the 
     * "sysDescriptions" argument.
     */
    Attributes<String,Object> _arguments;

    /**
     * Optional alert configuration.
     */
    PolicyAlert _alert;

    /**
     * Cached resolved executor instance.
     */
    PolicyExecutor _executorInstance;

    //
    // Type specific fields
    // I don't like these, but we don't have that many and I want to avoid
    // the complexity of Hibernate subclassing.
    // 
    
    /**
     * For policies that are generic, this is a list of constraints in the policy
     * Basically identical to the BaseConstraint.  
     * 
     * @ignore
     * We tend to only hold one 
     * constraint at this point in time, but this is modelled as a list in case
     * we want to extend this in the future.
     */
    List<GenericConstraint> _genericConstraints;

    /**
     * For policies of type SOD, the set of constraints in the policy.
     */
    List<SODConstraint> _SODConstraints;
    
    /**
     * For policies of type Activity, the set of constraints in the policy.
     */
    List<ActivityConstraint> _activityConstraints;
    
    /**
     * For simple or custom policies, an optional signature that defines
     * the configuration form for the policy. This will be rendered
     * automatically by the policy pages, the results are left
     * in the attributes map.
     */
    Signature _signature;

    /**
     * Runtime cache used to lookup constraints by id.
     */ 
    Map<String,BaseConstraint> _constraintMap;

    /**
     * Cached violation formatting rule.
     */
    Rule _violationRule;

    /**
     * Cached violation handling workflow.
     */
    Workflow _violationWorkflow;
    
    /**
     * default owner for policy violations for 
     * this policy.
     * The owner will be either the violationOwner
     * Or, if the field managerViolationOwner is set then
     * the manager of the identity becomes the owner of the
     * policy violation.
     * Or, the identity found using the violationOwnerRule
     * In that order of preference.
     */
    ViolationOwnerType _violationOwnerType = ViolationOwnerType.Identity;
    
    /**
     * Statically defined owner for all violations.
     */
    Identity _violationOwner;

    /**
     * Rule to calculate violation owner.
     */
    Rule _violationOwnerRule;

    ///////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public Policy() {
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitPolicy(this);
    }

    /**
     * Fully load the object so the cache can be cleared.
     */
    public void load() {

        if (_arguments != null)
            _arguments.get("foo");

        if (_alert != null)
            _alert.load();

        // don't need much of this
        if (_violationOwner != null)
            _violationOwner.getName();

        if (_violationOwnerRule != null)
            _violationOwnerRule.load();

        if (_genericConstraints != null) {
            for (GenericConstraint c : _genericConstraints)
                c.load();
        }
        
        if (_SODConstraints != null) {
            for (SODConstraint c : _SODConstraints)
                c.load();
        }
        
        if (_activityConstraints != null) {
            for (ActivityConstraint c : _activityConstraints)
                c.load();
        }

        if (getOwner() != null) {
            getOwner().load();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * True if this is a template policy. These will not be displayed
     * in the policy table, but they are used to build the select list
     * of policy types when creating new policies.
     */
    @XMLProperty
    public boolean isTemplate()
    {
        return _template;
    }

    public void setTemplate(boolean b)
    {
        _template = b;
    }

    @XMLProperty
    public boolean isNoCache()
    {
        return _noCache;
    }

    public void setNoCache(boolean b)
    {
        _noCache = b;
    }

    /**
     * Get the possibly-null Type of this Policy.
     * 
     * @return The possibly-null Type of this Policy.
     */
    @XMLProperty
    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    /**
     * The catalog key for localizing the type name.
     * This is set only for built-in types.
     */
    @XMLProperty
    public String getTypeKey()
    {
        return _typeKey;
    }

    public void setTypeKey(String key)
    {
        _typeKey = key;
    }

    /**
     * The URL fragment of a JSF page used to configure this policy.
     * When set, this is expected to be a path to a JSF page relative to
     * web/define/policy that will allow a user to configure a policy.
     * If not set it defaults to genericpolicy.xhtml.
     */
    @XMLProperty
    public String getConfigPage()
    {
        return _configPage;
    }

    public void setConfigPage(String url)
    {
        _configPage = url;
    }

    /**
     * The allowed certification actions for violations of this policy.
     * This is a CSV, if the value is null then all actions are allowed.
     * The allowed actions are being encoded using CertificationStatus
     * names, so the tense is technically wrong, if "Approved" is
     * on this list, then you are allowed to take the "Approve" action
     * on this violation.
     */
    @XMLProperty
    public String getCertificationActions()
    {
        return _certificationActions;
    }

    public void setCertificationActions(String actions)
    {
        _certificationActions = actions;
    }

    /**
     * The fully qualified class name to the PolicyExecutor implementation.
     */
    @XMLProperty
    public String getExecutor() {
        return _executor;
    }

    public void setExecutor(String s) {
        _executor = s;
        _executorInstance = null;
    }

    public void setExecutor(Class cls) {
        
        setExecutor((cls != null) ? cls.getName() : null);
    }

    public void setExecutor(PolicyExecutor pe) {

        if (pe == null)
            setExecutor((String)null);
        else {
            setExecutor(pe.getClass());
            _executorInstance = pe;
        }
    }

    /**
     * The state of the policy. There are currently only two states:
     * Active and Inactive. A policy must be in the Active state for
     * it to be processed.
     */
    @XMLProperty
    public State getState() {
        if (_state == null) _state = State.Inactive;
        return _state;
    }

    public void setState(State s) {
        if (s == null)
            _state = State.Inactive;
        else
            _state = s;
    }

    // !! crap, this will serialize as <Attributes> but we
    // want <Arguments>, not sure this is worth messing with
    // another serialization mode, or Attributes subclass, should
    // we just rename the property "attributes" and be done with it?

    /**
     * Extensible policy configuration.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Attributes<String,Object> args) {
        if (args != null) 
            _arguments = args;
        else {
            // always keep an empty map for JSF
            // !! is this really necessary, its annoying for the XML
            // serialization
            _arguments = new Attributes<String,Object>();
        }
    }

    public Object getArgument(String name) {
        return (_arguments != null) ? _arguments.get(name) : null;
    }

    public void setArgument(String name, Object value) {

        if (_arguments == null)
            _arguments = new Attributes<String,Object>();
        _arguments.putClean(name, value);
    }

    public Map<String,String> getDescriptions() {
        Map<String,String> map = null;
        Object o = getArgument(ATT_DESCRIPTIONS);
        if (o instanceof Map)
            map = (Map<String,String>)o;
        return map;
    }

    public void setDescriptions(Map<String,String> map) {
        setArgument(ATT_DESCRIPTIONS, map);
    }

    /**
     * Incrementally add one description.
     */
    public void addDescription(String locale, String desc) {
        new DescribableObject<Policy>(this).addDescription(locale, desc);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(String locale) {
        return new DescribableObject<Policy>(this).getDescription(locale);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(Locale locale) {
        return new DescribableObject<Policy>(this).getDescription(locale);
    }    
    
    /**
     * Optional alert configuration.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public PolicyAlert getAlert() {
        return _alert;
    }

    public void setAlert(PolicyAlert alert) {
        _alert = alert;
    }

    /**
     * For simple or custom policies, an optional signature that defines
     * the configuration form for the policy. This will be rendered
     * automatically by the policy pages, the results are left
     * in the attributes map.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Signature getSignature() {
        return _signature;
    }

    public void setSignature(Signature s) {
        _signature = s;
    }

    /**
     * Return the name of a rule that will be called to 
     * format each new violation of this policy.
     */
    public String getViolationRule() {
        return getString(BaseConstraint.ARG_VIOLATION_RULE);
    }
    
    public void setViolationRule(String s) {
        setArgument(BaseConstraint.ARG_VIOLATION_RULE, s);
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getViolationOwner() {
        return _violationOwner;
    }
    
    public void setViolationOwner(Identity val) {
        _violationOwner = val;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getViolationOwnerRule() {
        return _violationOwnerRule;
    }
    
    public void setViolationOwnerRule(Rule val) {
        _violationOwnerRule = val;
    }
    
    @XMLProperty
    public ViolationOwnerType getViolationOwnerType() {
        return _violationOwnerType;
    }
    
    public void setViolationOwnerType(ViolationOwnerType val) {
        _violationOwnerType = val;
    }
    
    /**
     * Resolve the violation formatting rule.
     */
    public Rule getViolationRuleObject(Resolver r) throws GeneralException {
        if (_violationRule == null) {
            String ruleName = getString(BaseConstraint.ARG_VIOLATION_RULE);
            if (ruleName != null) 
                _violationRule = r.getObjectByName(Rule.class, ruleName);
        }
        return _violationRule;
    }

    /**
     * Return the name of a workflow to be launched for
     * each violation of this policy.
     */
    public String getViolationWorkflow() {
        return getString(BaseConstraint.ARG_VIOLATION_WORKFLOW);
    }
    
    public void setViolationWorkflow(String s) {
        setArgument(BaseConstraint.ARG_VIOLATION_WORKFLOW, s);
    }

    /**
     * Resolve the violation formatting rule.
     */
    public Workflow getViolationWorkflow(Resolver r) throws GeneralException {
        if (_violationWorkflow == null) {
            String name = getViolationWorkflow();
            if (name != null) 
                _violationWorkflow = r.getObjectByName(Workflow.class, name);
        }
        return _violationWorkflow;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constraints
    //
    //////////////////////////////////////////////////////////////////////

    //
    // This first set of accessors is intended for use only by Hibernate.
    // They do not manage the reverse pointer from the constraint to the policy
    // to avoid marking the object dirty when Hibernate rehydrates it.
    //

    /**
     * Return the list of policy constraints.
     * This is only relevant for policies that are NOT of types
     * TYPE_SOD and TYPE_ACTIVITY. 
     */
    public List<GenericConstraint> getGenericConstraints() {
        return _genericConstraints;
    }

    /**
     * @exclude
     * This does not set the owner on the objects in the List and
     *             should only be used by the persistence mechanisms.
     * @deprecated use {@link #addConstraint(BaseConstraint)} 
     */
    @Deprecated
    public void setGenericConstraints(List<GenericConstraint> constraints) {
        _genericConstraints = constraints;
    }

    /**
     * Return the list of role SOD constraints.
     * This is only relevant for policies of type TYPE_SOD.
     */
    public List<SODConstraint> getSODConstraints() {
        return _SODConstraints;
    }

    /**
     * @exclude
     * This does not set the owner on the objects in the List and
     *             should only be used by the persistence mechanisms.
     * @deprecated use {@link #addConstraint(BaseConstraint)}
     */
    public void setSODConstraints(List<SODConstraint> cons) {
        _SODConstraints = cons;
    }

    /**
     * Return the list of activity constraints.
     * This is only relevant for policies of type <code>TYPE_ACTIVITY</code>.
     */
    @Deprecated
    public List<ActivityConstraint> getActivityConstraints() {
        return _activityConstraints;
    }

    /**
     * @exclude
     * This does not set the owner on the objects in the List and
     *             should only be used by the persistence mechanisms.
     * @deprecated use {@link #addConstraint(BaseConstraint)}
     */
    @Deprecated
    public void setActivityConstraints(List<ActivityConstraint> constraints) {
        _activityConstraints = constraints;
    }

    /**
     * Return one of the three constraint lists.
     * A policy cannot have more than one list.
     */
    public List getConstraints() {
        List cons = _SODConstraints;
        if (Util.isEmpty(cons))
            cons = _activityConstraints;
        if (Util.isEmpty(cons))
            cons = _genericConstraints;
        return cons;
    }

    //
    // This set of accessors is intended for the XML serializer, they
    // ensure that ownership is maintained
    //

    /**
     * @exclude
     * @deprecated use {@link #getConstraints()} 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST,xmlname="GenericConstraints")
    public List<GenericConstraint> getXmlGenericConstraints() {
        return _genericConstraints;
    }

    /**
     * @exclude
     * @deprecated use {@link #addConstraint(BaseConstraint)}  
     */
    @Deprecated
    public void setXmlGenericConstraints(List<GenericConstraint> cons) {
        assignConstraints(cons);
    }

    /**
     * @exclude
     * @deprecated use {@link #getConstraints()} 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST,xmlname="SODConstraints")
    public List<SODConstraint> getXmlSODConstraints() {
        return _SODConstraints;
    }
    
    /**
     * @exclude
     * @deprecated use {@link #addConstraint(BaseConstraint)}  
     */
    @Deprecated
    public void setXmlSODConstraints(List<SODConstraint> cons) {
        assignConstraints(cons);
    }

    /**
     * @exclude
     * @deprecated use {@link #getConstraints()} 
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST,xmlname="ActivityConstraints")
    public List<ActivityConstraint> getXmlActivityConstraints() {
        return _activityConstraints;
    }

    /**
     * @exclude
     * @deprecated use {@link #addConstraint(BaseConstraint)}  
     */
    @Deprecated
    public void setXmlActivityConstraints(List<ActivityConstraint> cons) {
        assignConstraints(cons);
    }
    
    /**
     * Return the object description. Descriptions are generally
     * longer than the name and are intended for display in
     * a multi-line text area.
     * @deprecated Use #getDescription(String locale) instead
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.ELEMENT, legacy=true)
    public String getDescription() {
        return _description;
    }

    /**
     *  @deprecated Use #addDescription(String locale, String description)
     */
    @Deprecated
    public void setDescription(String s) {
        // Since there is no longer a corresponding column for this property in Polilcy's table
        // this value will not be persisted in this form.  The method only remains so that
        // we can import legacy Applications from XML.  The PostImportVisitor will 
        // properly add the description to the descriptions map when necessary.
        _description = s;
        new DescribableObject<Policy>(this).logDeprecationWarning(s);
    }

    //
    // This set of accessors manages all three lists and maintains ownership
    // This is what most applications should use.
    //

    /**
     * Add a new constraint to the appropriate list.
     */
    public <T extends BaseConstraint> void assignConstraints(List<T> cons) {

        // we don't support mixed constraints
        _genericConstraints = null;
        _SODConstraints = null;
        _activityConstraints = null;

        if (cons != null) {
            for (BaseConstraint con : cons)
                addConstraint(con);
        }
    }

    /**
     * Add a constraint to the appropriate list.
     */
    public void addConstraint(BaseConstraint con) {
        if (con != null) {
            con.setPolicy(this);
            if (con instanceof GenericConstraint) {
                if (_genericConstraints == null)
                    _genericConstraints = new ArrayList<GenericConstraint>();
                _genericConstraints.add((GenericConstraint)con);
            }
            else if (con instanceof SODConstraint) {
                if (_SODConstraints == null)
                    _SODConstraints = new ArrayList<SODConstraint>();
                _SODConstraints.add((SODConstraint)con);
            }
            else if (con instanceof ActivityConstraint) {
                if (_activityConstraints == null)
                    _activityConstraints = new ArrayList<ActivityConstraint>();
                _activityConstraints.add((ActivityConstraint)con);
            }
        }
    }
    
    /**
     * Remove a constraint from the appropriate list.
     */
    public void removeConstraint(BaseConstraint con) {
        if (con instanceof GenericConstraint) {
            if (_genericConstraints != null)
                _genericConstraints.remove((GenericConstraint)con);
        }
        else if (con instanceof SODConstraint) {
            if (_SODConstraints != null)
                _SODConstraints.remove((SODConstraint)con);
        }
        else if (con instanceof ActivityConstraint) {
            if (_activityConstraints != null)
                _activityConstraints.remove((ActivityConstraint)con);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the constraint for simple policies that will
     * only have one constraint.
     */
    public GenericConstraint getConstraint() {
        GenericConstraint con = null;
        if (_genericConstraints != null && _genericConstraints.size() > 0)
            con = _genericConstraints.get(0);
        return con;
    }

    /**
     * @exclude
     * Helper for the 2.5 to 3.0 policy upgrade process.
     * This allows us to keep Type private so we can catch stray
     * references.
     */
    public void upgrade() {

        // Try to avoid upgrading twice just in case they want
        // to change something in the template.  Assume that policies
        // with a typeKey have already been upgraded.

        if (_type != null && _typeKey == null) {
            // this may throw for the newer types that didn't
            // have an enum
            Type t = null;
            try {
                t = Enum.valueOf(Type.class, _type);
            }
            catch (java.lang.IllegalArgumentException e) {
            }

            if (t != null) {
                _typeKey = t.getMessageKey();
                _configPage = t.getConfigPage();

                // we don't need this any more
                // _constraintClass = t.getConstraintClass();

                // allowedStatus we'll cheat on since we're changing the structure
                if (t == Type.Generic )
                    _certificationActions = "Mitigated,Delegated";
                else if (t == Type.Activity)
                    _certificationActions = "Acknowledged,Mitigated,Delegated";
                else if (t == Type.SOD)
                    _certificationActions = "Remediated,Mitigated,Delegated";
            }
        }
    }

    /**
     * Derive a new policy from a template.
     * This object is expected to be a template policy.
     * It is cloned and some unnecessary fields are removed.
     * NOTE: The template must have been fully loaded.
     */
    public Policy derive(Resolver r) throws GeneralException {

        Policy p = (Policy)super.derive(r);
        p.setTemplate(false);

        // objects that have owned children must null out the
        // child ids, otherwise the child will be stolen from the template

        if (_genericConstraints != null) {
            for (GenericConstraint c : _genericConstraints)
                c.clearPersistentIdentity();
        }
        
        if (_SODConstraints != null) {
            for (SODConstraint c : _SODConstraints)
                c.clearPersistentIdentity();
        }
        
        if (_activityConstraints != null) {
            for (ActivityConstraint c : _activityConstraints)
                c.clearPersistentIdentity();
        }

        return p;
    }

    /**
     * @exclude
     * Convenience accessor to resolve the executor.
     * @deprecated  Executor instances are now managed by <code>Interrogator</code>
     */
    @Deprecated
    public PolicyExecutor getExecutorInstance() throws GeneralException {


        if (_executorInstance == null && _executor != null) {
            // Delegate the real work to Interrogator which understands how
            // to deal with plugin classloading, if needed.
            String policyName = getName();
            String pluginName = getString(Policy.ARG_PLUGIN_NAME);
            _executorInstance = Interrogator.buildPolicyExecutor(policyName, _executor, pluginName);
        }
        return _executorInstance;
    }

    /**
     * Return true if the policy types are the same.
     *
     * @param type Policy type
     * @return false if policy type is not equal, or type param is null.
     */
    public boolean isType(String type) {
        return (type != null) ? type.equals(_type) : false;
    }

    /**
     * Return true if the given certification action can be taken
     * on violations of this policy.
     */
    public boolean isActionAllowed(CertificationAction.Status status) {

        // if not specified all are allowed
        return (_certificationActions == null ||
                _certificationActions.indexOf(status.toString()) >= 0);
    }

    /**
     * For JSF provide a property "attributes" as an alternative
     * to "arguments" so we can use some common signature edting includes.
     */
    public Attributes<String,Object> getAttributes() {
        return _arguments;
    }

    public void setAttributes(Attributes<String,Object> atts) {
        _arguments = atts;
    }

    /**
     * Common argument accessor with type coercion.
     */
    public String getString(String name) {

        return Util.otoa(getArgument(name));
    }

    /**
     * Common argument accessor with type coercion.
     */
    public int getInt(String name) {

        return Util.otoi(getArgument(name));
    }
   
    /**
     * Lookup the constraint associated with a violation.
     * @ignore
     * Formerly we saved the Class in the Type enumeration and
     * queried the database for an object of that class with the
     * id or name from the PolicyViolation.  Now we assume that
     * the Policy is fully loaded and we just search within 
     * ourselves.  This is faster and avoids the need
     * for the silly constraintClass property in the type definition.
     */
    public BaseConstraint getConstraint(PolicyViolation v){

        BaseConstraint found = null;
        String id = v.getConstraintId();
        Map<String,BaseConstraint> map = getConstraintMap();

        if (id != null) {
            found = map.get(id);
        }
        else {
            // some of the unit tests contrive violations without ids
            // this will be a slow search but assume we're only
            // here for the unit tests
            String name = v.getConstraintName();
            if (name != null) {
                Collection<BaseConstraint> cons = map.values();
                if (cons != null) {
                    for (BaseConstraint con : cons) {
                        if (name.equals(con.getName())) {
                            found = con;
                            break;
                        }
                    }
                }
            }
        }
        return found;
    }
    
    public BaseConstraint getConstraint(String constraintId, String constraintName)
            throws GeneralException {

            BaseConstraint found = null;
            String id = constraintId;
            Map<String,BaseConstraint> map = getConstraintMap();

            if (id != null) {
                found = map.get(id);
            }
            else {
                // some of the unit tests contrive violations without ids
                // this will be a slow search but assume we're only
                // here for the unit tests
                String name = constraintName;
                if (name != null) {
                    Collection<BaseConstraint> cons = map.values();
                    if (cons != null) {
                        for (BaseConstraint con : cons) {
                            if (name.equals(con.getName())) {
                                found = con;
                                break;
                            }
                        }
                    }
                }
            }
            return found;
        }
    
    public Identity getViolationOwnerForIdentity(SailPointContext context, Identity identity) {

        return getViolationOwnerForIdentity(context, identity, null);
    }
    
    public Identity getViolationOwnerForIdentity(SailPointContext context, Identity identity, BaseConstraint constraint) {

        if (constraint != null) {
            Identity owner = getViolationOwner(context, identity, constraint);
            if (owner != null) {
                return owner;
            }
        }
        
        return getViolationOwner(context, identity, this);
    }
    
    //TODO: Method looks complicated. Too many if then else stuff... Refactor
    private Identity getViolationOwner(SailPointContext context, Identity identity, IPolicyViolationOwnerSource violationOwnerSource) {
     
        if (violationOwnerSource.getViolationOwnerType() == ViolationOwnerType.None) {
            return null;
        }
        
        if (violationOwnerSource.getViolationOwnerType() == null) {
            // if here, it means for some odd reason it is not set, assume identity
            violationOwnerSource.setViolationOwnerType(ViolationOwnerType.Identity);
        }
        
        if (violationOwnerSource.getViolationOwnerType() == ViolationOwnerType.Identity) {
            return violationOwnerSource.getViolationOwner();
        } else if (violationOwnerSource.getViolationOwnerType() == ViolationOwnerType.Manager) {
            // first try the manager
            Identity violationOwner = identity.getManager();
            if (violationOwner == null) {
                // then try the policy owner
                violationOwner = getOwner();
                if (violationOwner == null) {
                    try {
                        // if all else fails fall back to the spadmin
                        String adminName = BrandingServiceFactory.getService().getAdminUserName();
                        violationOwner = context.getObjectByName(Identity.class, adminName);
                    } catch (GeneralException ex) {
                        Policy.log.error(ex.getMessage(), ex);
                        
                        return null;
                    }
                }
            }
            return violationOwner;
        } else if (violationOwnerSource.getViolationOwnerType() == ViolationOwnerType.Rule) {
            try {
                if (violationOwnerSource.getViolationOwnerRule() == null) {
                    return null;
                }
                
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("identity", identity);
                //if we have the base constraint we pass it as well as the policy
                if (violationOwnerSource instanceof BaseConstraint) {
                    BaseConstraint bc = (BaseConstraint)violationOwnerSource;
                    Policy policy = bc.getPolicy();
                    params.put("policy", policy);
                    params.put("constraint", bc);
                //if we don't have the constraint object we pass it as null
                }else if (violationOwnerSource instanceof Policy) {
                    Policy policy = (Policy)violationOwnerSource;
                    params.put("policy", policy);
                    params.put("constraint", null);
                }

                // Fetch the rule to prevent a LazyInitializationException in the rule runner
                Rule violationOwnerRule = context.getObjectById(Rule.class, violationOwnerSource.getViolationOwnerRule().getId());
                Object ownerObjectFromRule = context.runRule(violationOwnerRule, params);
                if (ownerObjectFromRule instanceof String) {
                    //TODO: Need to document this should return name now -rap
                    return context.getObjectByName(Identity.class, (String)ownerObjectFromRule);
                } else if (ownerObjectFromRule instanceof  Identity) {
                    return (Identity) ownerObjectFromRule;
                } else {
                    log.warn("Unexpected return from rule: " + ownerObjectFromRule);
                    return null;
                }
            } catch (GeneralException ex) {
                Policy.log.error(ex.getMessage(), ex);
                
                return null;
            }
        } else {
            throw new IllegalStateException("Unknown ViolationOwnerType: " + violationOwnerSource.getViolationOwnerType());
        }
    }

    private Map<String,BaseConstraint> getConstraintMap() {
        
        if (_constraintMap == null) {
            _constraintMap = new HashMap<String,BaseConstraint>();

            // should only have one of these
            buildConstraintMap(_constraintMap, _genericConstraints);
            buildConstraintMap(_constraintMap, _SODConstraints);
            buildConstraintMap(_constraintMap, _activityConstraints);
        }

        return _constraintMap;
    }

    private void buildConstraintMap(Map<String,BaseConstraint> map,
                                    List constraints) {

        if (constraints != null) {
            for (Object o : constraints) {
                if (o instanceof BaseConstraint) {
                    BaseConstraint bc = (BaseConstraint)o;
                    String id = bc.getId();
                    if (id != null)
                        map.put(id, bc);
                    else {
                        //While simulating a rule that is not yet saved the constraint is in the session only and hence doesnt have an id as yet
                        // so using the Name instead
                        map.put(bc.getName(), bc);
                    }
                }
            }
        }
    }

    /**
     * Check if policy has constraints
     * @return true if this policy has constraints associated with it; false otherwise
     */
    public boolean hasConstraints() {
        return (_SODConstraints != null && !_SODConstraints.isEmpty()) || 
               (_activityConstraints != null && !_activityConstraints.isEmpty()) || 
               (_genericConstraints != null && !_genericConstraints.isEmpty());
    }
    

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = super.hashCode();
        result = PRIME * result + ((_arguments == null) ? 0 : _arguments.hashCode());
        result = PRIME * result + ((_SODConstraints == null) ? 0 : _SODConstraints.hashCode());
        result = PRIME * result + ((_activityConstraints == null) ? 0 : _activityConstraints.hashCode());
        result = PRIME * result + ((_genericConstraints == null) ? 0 : _genericConstraints.hashCode());
        result = PRIME * result + ((_description == null) ? 0 : _description.hashCode());
        result = PRIME * result + ((_executor == null) ? 0 : _executor.hashCode());
        result = PRIME * result + ((_executorInstance == null) ? 0 : _executorInstance.hashCode());
        result = PRIME * result + ((_state == null) ? 0 : _state.hashCode());
        result = PRIME * result + ((_type == null) ? 0 : _type.hashCode());
        return result;
    }

    // jsl - can't most of this be done with the default
    // shallow comparison?  If we have to do something this
    // complex it may belong over in Differencer

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Policy other = (Policy) obj;
        if (_arguments == null) {
            if (other._arguments != null)
                return false;
        } else if (!_arguments.equals(other._arguments))
            return false;
        if (_SODConstraints == null) {
            if (other._SODConstraints != null)
                return false;
        } else if (!_SODConstraints.equals(other._SODConstraints))
            return false;
        if (_activityConstraints == null) {
            if (other._activityConstraints != null)
                return false;
        } else if (!_activityConstraints.equals(other._activityConstraints))
            return false;
        if (_genericConstraints == null) {
            if (other._genericConstraints != null)
                return false;
        } else if (!_genericConstraints.equals(other._genericConstraints))
            return false;
        if (_description == null) {
            if (other._description != null)
                return false;
        } else if (!_description.equals(other._description))
            return false;
        if (_executor == null) {
            if (other._executor != null)
                return false;
        } else if (!_executor.equals(other._executor))
            return false;
        if (_executorInstance == null) {
            if (other._executorInstance != null)
                return false;
        } else if (!_executorInstance.equals(other._executorInstance))
            return false;
        if (_state == null) {
            if (other._state != null)
                return false;
        } else if (!_state.equals(other._state))
            return false;
        if (_type == null) {
            if (other._type != null)
                return false;
        } else if (!_type.equals(other._type))
            return false;
        return true;
    }


}

