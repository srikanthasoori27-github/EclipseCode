/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * A Rule contains a script that can be executed to perform
 * custom logic.  Each rule declares which language it uses, the source for the
 * rule, and context about how the rule should be run.
 */
@XMLClass
public class Rule extends SailPointObject implements IXmlEqualable<Rule>
{
    private static final long serialVersionUID = -1151030619555780736L;

    /**
     * @ignore
     * There doesn't seem to be a really good place for this, but here is
     * the constant we use for determining the base path for the script pre-parser.
     */
    public static final String MODEL_BASE_PATH = "modelBasePath";

    ////////////////////////////////////////////////////////////////////////////
    //
    // Rule Types
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Rules may be typed if they are intended for use in certain
     * parts of the system proper use of types is necessary for
     * the rule to appear in selection menus in the UI.
     */
    @XMLClass(xmlname="RuleType")
    public enum Type
    {
        // AccountGroupRefresh has been deprecated; use GroupAggregationRefresh instead
        AccountGroupRefresh(MessageKeys.RULE_TYPE_ACCOUNTGROUPREFRESH),
        AccountSelector(MessageKeys.RULE_TYPE_ACCOUNTSELECTOR),
        ActivityConditionBuilder(MessageKeys.RULE_TYPE_ACTIVITYCONDITIONBUILDER),
        ActivityCorrelation(MessageKeys.RULE_TYPE_ACTIVITYCORRELATION),
        ActivityPositionBuilder(MessageKeys.RULE_TYPE_ACTIVITYPOSITIONBUILDER),
        ActivityTransformer(MessageKeys.RULE_TYPE_ACTIVITYTRANSFORMER),
        AfterProvisioning(MessageKeys.RULE_TYPE_AFTER_PROVISIONING),
        AlertMatch(MessageKeys.RULE_TYPE_ALERT_MATCH),
        AlertCorrelation(MessageKeys.RULE_TYPE_ALERT_CORRELATION),
        AlertCreation(MessageKeys.RULE_TYPE_ALERT_CREATION),
        ApprovalAssignment(MessageKeys.RULE_TYPE_APPROVAL_ASSIGNMENT),
        AttachmentConfig(MessageKeys.RULE_TYPE_ATTACHMENT_CONFIG),
        CommentConfig(MessageKeys.RULE_TYPE_COMMENT_CONFIG),
        ConnectorAfterCreate(MessageKeys.RULE_TYPE_AFTER_CREATE),
        ConnectorAfterModify(MessageKeys.RULE_TYPE_AFTER_MODIFY),
        ConnectorAfterDelete(MessageKeys.RULE_TYPE_AFTER_DELETE),  
        AllowedValues(MessageKeys.RULE_TYPE_ALLOWEDVALUES),
        Approver(MessageKeys.RULE_TYPE_APPROVER),
        ConnectorBeforeCreate(MessageKeys.RULE_TYPE_BEFORE_CREATE),
        ConnectorBeforeModify(MessageKeys.RULE_TYPE_BEFORE_MODIFY),
        ConnectorBeforeDelete(MessageKeys.RULE_TYPE_BEFORE_DELETE),        
        BuildMap(MessageKeys.RULE_TYPE_BUILDMAP),
        BeforeProvisioning(MessageKeys.RULE_TYPE_BEFORE_PROVISIONING),
        CertificationAutomaticClosing(MessageKeys.RULE_TYPE_CERTIFICATIONAUTOMATICCLOSING),
        CertificationEntityCompletion(MessageKeys.RULE_TYPE_CERTIFICATIONENTITYCOMPLETION),
        CertificationEntityCustomization(MessageKeys.RULE_TYPE_CERTIFICATIONENTITYCUSTOMIZATION),
        CertificationEntityRefresh(MessageKeys.RULE_TYPE_CERTIFICATIONENTITYREFRESH),
        CertificationExclusion(MessageKeys.RULE_TYPE_CERTIFICATIONEXCLUSION),
        CertificationItemCompletion(MessageKeys.RULE_TYPE_CERTIFICATIONITEMCOMPLETION),
        CertificationItemCustomization(MessageKeys.RULE_TYPE_CERTIFICATIONITEMCUSTOMIZATION),
        CertificationPreDelegation(MessageKeys.RULE_TYPE_CERTIFICATIONPREDELEGATION),
        //Certification Schedule Entity Rule
        CertificationScheduleEntitySelector(MessageKeys.RULE_TYPE_CERTIFCATION_SCHEDULE_ENTITY_SELECTOR),
        CertificationSignOffApprover(MessageKeys.RULE_TYPE_CERTIFICATIONSIGNOFFAPPROVER),
        CertificationPhaseChange(MessageKeys.RULE_TYPE_CERTIFICATIONPHASECHANGE),
        Certifier(MessageKeys.RULE_TYPE_CERTIFIER),
        ClassificationCustomization(MessageKeys.RULE_TYPE_CLASSIFICATION_CUSTOMIZATION),
        CompositeAccount(MessageKeys.RULE_TYPE_LOGICALACCOUNT),
        CompositeRemediation(MessageKeys.RULE_TYPE_LOGICALPROVISIONING),
        // CompositeTierCorrelation has been deprecated
        CompositeTierCorrelation(MessageKeys.RULE_TYPE_LOGICALTIERCORRELATION),
        Correlation(MessageKeys.RULE_TYPE_CORRELATION),
        EmailRecipient(MessageKeys.RULE_TYPE_EMAIL_RECIPIENT),
        Escalation(MessageKeys.RULE_TYPE_ESCALATION),
        FieldValue(MessageKeys.RULE_TYPE_FIELDVALUE),
        FallbackWorkItemForward(MessageKeys.RULE_TYPE_FALLBACKWORKITEMFORWARD),
        FileParsingRule(MessageKeys.RULE_TYPE_FILEPARSINGRULE),
        GroupAggregationRefresh(MessageKeys.RULE_TYPE_GROUP_AGGREGATION_REFRESH),
        GroupOwner(MessageKeys.RULE_TYPE_GROUPOWNER),
        IdentityAttribute(MessageKeys.RULE_TYPE_IDENTITYATTRIBUTE),
        IdentityAttributeTarget(MessageKeys.RULE_TYPE_IDENTITY_ATTRIBUTE_TARGET),
        IdentityCreation(MessageKeys.RULE_TYPE_IDENTITYCREATION),
        IdentitySelector(MessageKeys.RULE_TYPE_IDENTITYSELECTOR),
        IdentityTrigger(MessageKeys.RULE_TYPE_IDENTITYTRIGGER),
        Integration(MessageKeys.RULE_TYPE_INTEGRATION),
        JDBCProvision(MessageKeys.RULE_TYPE_JDBCPROVISION),
        JDBCOperationProvisioning(MessageKeys.RULE_TYPE_JDBC_OPERATION_PROVISIONING),
        JDBCBuildMap(MessageKeys.RULE_TYPE_JDBCBUILDMAP),
        SapHrProvision(MessageKeys.RULE_TYPE_SAPHRPROVISION),
        SapHrOperationProvisioning(MessageKeys.RULE_TYPE_SAPHR_OPERATION_PROVISIONING),
        SAPHRManagerRule(MessageKeys.RULE_TYPE_SAP_HR_MANAGER_RULE),
        PeopleSoftHRMSProvision(MessageKeys.RULE_TYPE_PEOPLESOFTHRMSPROVISION),
        PeopleSoftHRMSOperationProvisioning(MessageKeys.RULE_TYPE_PEOPLESOFTHRMS_OPERATION_PROVISIONING),
        SuccessFactorsOperationProvisioning(MessageKeys.RULE_TYPE_SUCCESS_FACTORS_OPERATION_PROVISIONING),
        LeaverReassignment(MessageKeys.RULE_TYPE_LEAVER_REASSIGNMENT),
        LeaverAccountRequests(MessageKeys.RULE_TYPE_LEAVER_ACCOUNT_REQUESTS),
        LinkAttribute(MessageKeys.RULE_TYPE_LINKATTRIBUTE),
        Listener(MessageKeys.RULE_TYPE_LISTENER),
        // ManagedAttributeCustomization has been deprecated; use ManagedAttributePromotion instead
        ManagedAttributeCustomization(MessageKeys.RULE_TYPE_MANAGED_ATTRIBUTE_CUSTOMIZATION),
        ManagedAttributePromotion(MessageKeys.RULE_TYPE_MANAGED_ATTRIBUTE_PROMOTION),
        ManagerCorrelation(MessageKeys.RULE_TYPE_MANAGERCORRELATION),
        MergeMaps(MessageKeys.RULE_TYPE_MERGEMAPS),
        Owner(MessageKeys.RULE_TYPE_OWNER),
        Policy(MessageKeys.RULE_TYPE_POLICY),
        PolicyOwner(MessageKeys.RULE_TYPE_POLICY_OWNER),
        PolicyNotification(MessageKeys.RULE_TYPE_POLICYNOTIFICATION),
        PostLifecycle(MessageKeys.RULE_TYPE_POST_LIFECYCLE),
        PostIterate(MessageKeys.RULE_TYPE_POSTITERATE),
        PreIterate(MessageKeys.RULE_TYPE_PREITERATE),
        PeopleSoftHRMSBuildMap(MessageKeys.RULE_TYPE_PEOPLESOFTHRMSBUILDMAP),
        PrivilegedItemSelector(MessageKeys.RULE_TYPE_PRIVILEGED_ITEM_SELECTOR),
        Refresh(MessageKeys.RULE_TYPE_REFRESH),
        RACFPermissionCustomization(MessageKeys.RULE_TYPE_RACF_PERMISSION_CUSTOMIZATION),
        RequestObjectSelector(MessageKeys.RULE_TYPE_REQUEST_OBJECT_SELECTOR),
        ResourceObjectCustomization(MessageKeys.RULE_TYPE_RESOURCEOBJECTCUSTOMIZATION),
        RiskScore(MessageKeys.RULE_TYPE_RISKSCORE),
        SAMLCorrelation(MessageKeys.RULE_TYPE_SAML_CORRELATION),
        SAPBuildMap(MessageKeys.RULE_TYPE_SAPBUILDMAP),
        SSOAuthentication(MessageKeys.RULE_TYPE_SSOAUTHENTICATION),
        SSOValidation(MessageKeys.RULE_TYPE_SSOVALIDATION),
        ScopeCorrelation(MessageKeys.RULE_TYPE_SCOPECORRELATION),
        ScopeSelection(MessageKeys.RULE_TYPE_SCOPESELECTION),
        TargetCorrelation(MessageKeys.RULE_TYPE_TARGETCORRELATION),
        TargetCreation(MessageKeys.RULE_TYPE_TARGETCREATION),
        TargetRefresh(MessageKeys.RULE_TYPE_TARGETREFRESH),
        TargetTransformer(MessageKeys.RULE_TYPE_TARGETTRANSFORMER),
        TaskEventRule(MessageKeys.RULE_TYPE_TASKEVENT),
        TaskCompletion(MessageKeys.RULE_TYPE_TASKCOMPLETION),
        Transformation(MessageKeys.RULE_TYPE_TRANSFORMATION),
        Validation(MessageKeys.RULE_TYPE_VALIDATION),
        Violation(MessageKeys.RULE_TYPE_VIOLATION),
        Workflow(MessageKeys.RULE_TYPE_WORKFLOW),
        WorkItemForward(MessageKeys.RULE_TYPE_WORKITEMFORWARD),
        WebServiceAfterOperationRule(MessageKeys.RULE_TYPE_WEBSERVICEAFTEROPERATION),
        WebServiceBeforeOperationRule(MessageKeys.RULE_TYPE_WEBSERVICEBEFOREOPERATION),
        ReportValidator(MessageKeys.RULE_TYPE_REPORTVALIDATOR),
        ReportParameterQuery(MessageKeys.RULE_TYPE_REPORTPARAMETERQUERY),
        ReportParameterValue(MessageKeys.RULE_TYPE_REPORTPARAMETERVALUE),
        ReportCustomizer(MessageKeys.RULE_TYPE_REPORTCUSTOMIZER);

        private String messageKey;

        private Type(String messageKey)
        {
            this.messageKey = messageKey;
        }

        public String getMessageKey()
        {
            return this.messageKey;
        }
    };


    public static class RuleTypeComparator implements Comparator<Rule.Type>
    {
        public int compare(Rule.Type t1, Rule.Type t2)
        {
            String msg1 = t1.getMessageKey();
            String msg2 = t2.getMessageKey();
            return msg1.compareTo(msg2);
        }
    };


    ////////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A type specifying the context in which the rule can be used.
     * If this is null, it is considered a generic rule.
     */
    private Type type;

    /**
     * The language the rule is written in.
     * The default for internal use and documentation is BeanShell.
     */
    private String language = Script.LANG_BEANSHELL;

    /**
     * The source code of the rule.
     */
    private String source;

    /**
     * A set of rules that are referenced in the source code
     * of this rule.  Allows dependency checking.
     // jsl - how is this set, during compilation?
     */
    private List<Rule> referencedRules;

    /**
     * Metadata describing the inputs and outputs of the rule.
     */
    Signature _signature;

    /**
     * Map of other options for the Rule. Specifically added 
     * to hold some configuration items necessary for 
     * native script execution.
     */
    Attributes<String,Object> _config;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public Rule() {}

    public boolean contentEquals(Rule other) {

        return equals(other);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the language in which the rule is written. Defaults to beanshell.
     *
     * @return The language in which the rule is written.
     */
    @XMLProperty
    public String getLanguage()
    {
        return language;
    }

    /**
     * Set the language in which the rule is written.
     *
     * @param  language  The language in which the rule is written.
     */
    public void setLanguage(String language)
    {
        this.language = language;
    }

    /**
     * Get the source of the rule to be executed (for example - the script).
     *
     * @return The source of the rule to be executed (for example - the script).
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getSource()
    {
        return source;
    }

    /**
     * Set the source of the rule to be executed (for example - the script).
     *
     * @param  source  The source of the rule to be executed (for example - the script).
     */
    public void setSource(String source)
    {
        this.source = source;
    }

    /**
     * Get the type specifying the context in which the rule can be used.
     * If this is null, it is considered a generic rule.
     *
     * @return The possibly-null Type of this Rule.
     */
    @XMLProperty
    public Type getType()
    {
        return type;
    }

    /**
     * Set the Type of this Rule.
     *
     * @param  type  The Type of the Rule.
     */
    public void setType(Type type)
    {
        this.type = type;
    }

    /**
     * Get any Rules that are referenced by this Rule. This allows dependencies
     * on other Rules as libraries.
     *
     * @return Any Rules that are referenced by this Rule.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Rule> getReferencedRules()
    {
        return referencedRules;
    }

    /**
     * Set the Rules that are referenced by this Rule.
     *
     * @param  referencedRules  The rules that are referenced by this Rule.
     */
    public void setReferencedRules(List<Rule> referencedRules)
    {
        this.referencedRules = referencedRules;
    }

    /**
     * Add a Rule that is referenced by this Rule.
     *
     * @param  rule  The Rule that is referenced by this Rule.
     */
    public void addReferencedRule(Rule rule)
    {
        if (null == this.referencedRules)
            this.referencedRules = new ArrayList<Rule>();
        this.referencedRules.add(rule);
    }

    /**
     * Return an object describing the inputs and outputs of the rule.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Signature getSignature() {
        return _signature;
    }

    public void setSignature(Signature s) {
        _signature = s;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Performance experiment to fully load the Rule so the cache can be cleared.
     */
    public void load() {

        getSource();

        if (referencedRules != null) {
            for (Rule r : referencedRules)
                r.load();
        }

        if (_signature != null)
            _signature.load();
    }

    /**
     * Compare Rules by name for sorting purposes.
     *
     * @param o Rule to compare to this Rule
     */
    public int compareTo(Object o) {
        final int result;

        if (o instanceof Rule) {
            Rule otherRule = (Rule) o;
            result = this.getName().compareTo(otherRule.getName());
        } else {
            throw new IllegalArgumentException("Can only compare Rule instances to other Rule instances");
        }

        return result;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitRule(this);
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = super.hashCode();
        result = PRIME * result + ((_signature == null) ? 0 : _signature.hashCode());
        result = PRIME * result + ((language == null) ? 0 : language.hashCode());
        result = PRIME * result + ((referencedRules == null) ? 0 : referencedRules.hashCode());
        result = PRIME * result + ((source == null) ? 0 : source.hashCode());
        result = PRIME * result + ((type == null) ? 0 : type.hashCode());
        result = PRIME * result + ((_config == null) ? 0 : _config.hashCode());
        return result;
    }

    /**
     * A Map of attributes that describe the application configuration.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _config;
    }

    /**
     * Sets the application configuration attributes
     */
    public void setAttributes(Attributes<String, Object> config) {
        _config = config;
    }

    /**
     * Sets a named application configuration attribute.
     * Existing values will be overwritten
     */
    public void setAttribute(String name, Object value) {
        if ( _config == null ) {
            _config = new Attributes<String, Object>();
        }
        _config.put(name,value);
    }

    /**
     * Gets a named application configuration attribute.
     */
    public Object getAttributeValue(String name) {
        return _config != null ? _config.get(name) : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Rule other = (Rule) obj;

        return isContentEquivalent(other);
    }

    /**
     * Returns true if the "contents" of the specified Rule are equal to this, where "contents" means:
     *      signature
     *      language
     *      referencedRules
     *      source
     *      type
     *  but specifically excludes name and id. This method can be used during upgrade to determine if the
     *  contents of two rules are identical; it is also called by the more general equals() method.
     *
     *  @param other the instance to compare to
     *  @return true if contents are equal, false otherwise
     */
    public boolean isContentEquivalent(Rule other)
    {
        if (_signature == null) {
            if (other._signature != null)
                return false;
        }
        else if (!_signature.equals(other._signature))
            return false;

        if (language == null) {
            if (other.language != null)
                return false;
        }
        else if (!language.equals(other.language))
            return false;

        if (referencedRules == null) {
            if (other.referencedRules != null)
                return false;
        }
        else if (!referencedRules.equals(other.referencedRules))
            return false;

        if (source == null) {
            if (other.source != null)
                return false;
        }
        else if (!source.equals(other.source))
            return false;

        if (type == null) {
            if (other.type != null)
                return false;
        }
        else if (!type.equals(other.type))
            return false;

        return true;
    }
}

