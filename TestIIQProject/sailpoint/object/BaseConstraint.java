/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base class for all Policy constraints, also known as "policy rules" in the UI.
 * 
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */

package sailpoint.object;

import sailpoint.object.Policy.ViolationOwnerType;
import sailpoint.object.PolicyViolation.IPolicyViolationOwnerSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;


/**
 * Base class for all Policy constraints, also known as "policy rules" 
 * in the UI.
 *
 * <code>SailPointObject.name</code> is used as the "short name".
 *
 * <code>SailPointObject.description</code> is used as the "long name".
 *
 * <code>SailPointobject.disabled is used to mark disabled constraints.
 *
 */
public abstract class BaseConstraint extends SailPointObject implements IPolicyViolationOwnerSource {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Configuration option that specifies the name of a rule to
     * post-process the PolicyViolation after it has been built
     * by GenericPolicyExecutor. This can be used in cases where the 
     * violation needs to have certain fields like the description generated
     * from properties of the identity rather than statically defined
     * in the constraint object. GenericPolicyExecutor will first build
     * the PolicyViolation in the default way, then if this option is set
     * and resolves to a rule the rule is evaluated passing the Identity,
     * Policy, GenericConstraint, and PolicyViolation for further 
     * formatting.
     */
    public static final String ARG_VIOLATION_RULE = "violationRule";

    /**
     * The name of a Workflow that will be launched when a violation
     * of this constraint is detected.  This overrides the violation
     * workflow defined in the parent policy if any.
     * Stored as an attribute so Hibernate upgrade does not need to change.
     */
    public static final String ARG_VIOLATION_WORKFLOW = "violationWorkflow";

    //
    // SailPointObject._name is used as the "short name"
    // SailPointObject._description is used as the "long name"
    // SailPointobject._disabled is used to mark disabled constraints
    //

    /**
     * @deprecated This is the pre-3.0 convention for the short name.
     * You should be using the inherited _name field.
     */
    @Deprecated
    private String violationSummary;

    /**
     * The Policy that owns this constraint.
     */
    private Policy policy;

    /**
     * policy violation owner at the constraint level
     */
    private Identity violationOwner;
    private ViolationOwnerType violationOwnerType = ViolationOwnerType.None;
    private Rule violationOwnerRule;
    
    /**
     * The weight a violation of this constraint has when calculating
     * the SOD policy score.
     */
    private int weight;

    /**
     * The description of a compensating control.
     *
     * A text description of some corporate
     * process or policy that is believed to prevent anything bad
     * from happening with this combination of entitlements.
     * It is purely documentation, but might be interesting in reports.
     * Presumably the existence of a compensating control either
     * disables or reduces the severity of this constraint.
     */
    private String compensatingControl;

    /**
     * Text that might be displayed during remediation to help describe
     * the possible actions that can be taken to resolve the violation.
     * This is used in combination with the _remediationBundles list
     * to provide the user with advice on what to do.
     */
    private String remediationAdvice;

    /**
     * Configuration options.
     * These can also be passed into the violation.
     * 
     * @ignore
     * TODO: Do we need to distinguish between arguments intended
     * for the PolicyExecutor and things to include in the PolicyViolation?
     */
    Attributes<String,Object> arguments;

    /**
     * Cached violation formatting rule.
     */
    Rule _violationRule;

    /**
     * Cached violation handling workflow.
     */
    Workflow _violationWorkflow;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public BaseConstraint() {
    }

    public void load() {

        // normally we work top down so this shouldn't be necessary
        if (this.policy != null)
            this.policy.getName();

        if (_violationRule != null)
            _violationRule.load();

        if (_violationWorkflow != null)
            _violationWorkflow.load();
    }

    /**
     * Policy that owns this constraint
     *
     * Note that the policy property is not part of the XML, as the serialization of
     * the Policy includes the constraints.
     *
     * @return Policy associated with this constraint
     */
    public Policy getPolicy() {
        return policy;
    }

    /**
     * Policy that owns this constraint
     *
     * @param p Policy associated with this constraint
     */
    public void setPolicy(Policy p) {
        this.policy = p;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getViolationOwner() {
        return this.violationOwner;
    }
    
    public void setViolationOwner(Identity val) {
        this.violationOwner = val;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getViolationOwnerRule() {
        return this.violationOwnerRule;
    }
    
    public void setViolationOwnerRule(Rule val) {
        this.violationOwnerRule = val;
    }
    
    @XMLProperty
    public ViolationOwnerType getViolationOwnerType() {
        return this.violationOwnerType;
    }
    
    public void setViolationOwnerType(ViolationOwnerType val) {
        this.violationOwnerType = val;
    }
    
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getCompensatingControl() {
        return compensatingControl;
    }

    public void setCompensatingControl(String cc) {
        this.compensatingControl = cc;
    }

    /**
     * Text that might be displayed during remediation to help describe
     * the possible actions that can be taken to resolve the violation.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getRemediationAdvice() {
        return remediationAdvice;
    }

    public void setRemediationAdvice(String s) {
        this.remediationAdvice = s;
    }

    /**
     * The weight a violation of this constraint has when calculating
     * the SOD policy score.
     */
    public int getWeight() {
        return weight;
    }

    @XMLProperty
    public void setWeight(int weight) {
        this.weight = weight;
    }

    /**
     * @exclude
     * @deprecated This should no longer be used. It can be removed
     * after the upgrade to 3.0.
     */
    @Deprecated
    @XMLProperty(mode= SerializationMode.ELEMENT,xmlname="ViolationSummary")
    public String getOldViolationSummary() {
        return null;
    }

    /**
     * @exclude
     * @deprecated This should no longer be used. It can be removed
     * after the upgrade to 3.0.
     */
    @Deprecated
    public void setOldViolationSummary(String s) {
        // Note that this will still be called by Hibernate until we can
        // remove it from the mappings after the upgrade.  Once the 
        // value becomes null ignore it so we don't trash the name.
        if (s != null)
            setName(s);
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getArguments() {
        return arguments;
    }

    public void setArguments(Attributes<String,Object> args) {
        if (args != null) 
            arguments = args;
        else {
            // always keep an empty map for JSF
            // !! is this really necessary, its annoying for the XML
            // serialization
            arguments = new Attributes<String,Object>();
        }
    }

    public String getViolationRule() {
        return getString(ARG_VIOLATION_RULE);
    }
    
    public void setViolationRule(String s) {
        setArgument(ARG_VIOLATION_RULE, s);
    }

    /**
     * Pseudo property to resolve the violation formatting rule.
     */
    public Rule getViolationRuleObject(Resolver r) throws GeneralException {
        if (_violationRule == null) {
            String ruleName = getString(ARG_VIOLATION_RULE);
            if (ruleName != null) 
                _violationRule = r.getObjectByName(Rule.class, ruleName);
        }
        return _violationRule;
    }

    public String getViolationWorkflow() {
        return getString(ARG_VIOLATION_WORKFLOW);
    }
    
    public void setViolationWorkflow(String s) {
        setArgument(ARG_VIOLATION_WORKFLOW, s);
    }

    /**
     * Pseudo property to resolve the violation formatting rule.
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
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return one of several names that can be used to describe
     * this constraint in violations, notifications, the UI, etc.
     * Pick the shortest name available.
     */
    public String getDisplayableName() {

        String name = getName();

        // We have historically tested for empty strings, probably
        // due to the JSF beans not trimming empty strings

        if (name == null || name.length() == 0)
            name = getDescription();

        // the last resort, this should NOT be necessary
        // if we've done a proper upgrade
        if (name == null || name.length() == 0)
            name = violationSummary;

        return name;
    }

    public Object getArgument(String name) {
        return (arguments != null) ? arguments.get(name) : null;
    }
    
    public void setArgument(String name, Object value) {

        if (arguments == null)
            arguments = new Attributes<String,Object>();
        arguments.putClean(name, value);
    }

    public String getString(String name) {
        return (arguments != null) ? arguments.getString(name) : null;
    }
    
}
