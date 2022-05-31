/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The representation for one certification process.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.CertificationStatCounter;
import sailpoint.object.AbstractCertificationItem.ContinuousState;
import sailpoint.tools.BidirectionalCollection;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * The representation for one certification process.
 *
 * This class is different from most in that is "archival",
 * meaning it has a potentially infinite lifespan.  References
 * to object objects such as Identities are made
 * by name rather than through direct references so that they
 * do not introduce foreign key constraints on the referenced
 * objects.
 */
@XMLClass
@Indexes({
	@Index(name="spt_certification_certifiers", column="certifier", table="spt_certifiers")
})
public class Certification
    extends SailPointObject
    implements Phaseable, Cloneable, WorkItemOwnerChangeListener
{
    private static final long serialVersionUID = -6057043257878208066L;
    private static final Log log = LogFactory.getLog(Certification.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Type
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Styles of certification.
     *
     */
    @XMLClass(xmlname="CertificationType")
    public static enum Type implements Localizable {

        Manager(MessageKeys.CERT_TYPE_MGR),
        ApplicationOwner(MessageKeys.CERT_TYPE_APP_OWNER),
        DataOwner(MessageKeys.CERT_TYPE_DATA_OWNER, CertificationEntity.Type.DataOwner),
        Identity(MessageKeys.CERT_TYPE_IDENTITY),
        Group(MessageKeys.CERT_TYPE_ADVANCED),
        // In the UI this is Targeted, but the change was made too close to 7.3 release to justify changing this type
        // in code. Sigh.
        Focused(MessageKeys.CERT_TYPE_FOCUSED),
        BusinessRoleMembership(MessageKeys.CERT_TYPE_ROLE_MEMBER),
        BusinessRoleComposition(MessageKeys.CERT_TYPE_ROLE_COMP, CertificationEntity.Type.BusinessRole),

        // Contains all statuses but challenged as account group entities can't be challenged
        AccountGroupPermissions(MessageKeys.CERT_TYPE_ACCT_GRP_PERMS, CertificationEntity.Type.AccountGroup
                , Arrays.asList(AbstractCertificationItem.Status.Complete
                , AbstractCertificationItem.Status.Delegated, AbstractCertificationItem.Status.Open
                , AbstractCertificationItem.Status.Returned, AbstractCertificationItem.Status.WaitingReview)),

        AccountGroupMembership(MessageKeys.CERT_TYPE_ACCT_GRP_MEMBERSHIP, CertificationEntity.Type.AccountGroup
                , Arrays.asList(AbstractCertificationItem.Status.Complete
                , AbstractCertificationItem.Status.Delegated, AbstractCertificationItem.Status.Open
                , AbstractCertificationItem.Status.Returned, AbstractCertificationItem.Status.WaitingReview)),

        /** @deprecated use {@link #AccountGroupMembership} or {@link #AccountGroupPermissions} */
        @Deprecated
        AccountGroup(MessageKeys.CERT_TYPE_ACT_GRP, CertificationEntity.Type.AccountGroup
                , Arrays.asList(AbstractCertificationItem.Status.Complete
                , AbstractCertificationItem.Status.Delegated, AbstractCertificationItem.Status.Open
                , AbstractCertificationItem.Status.Returned, AbstractCertificationItem.Status.WaitingReview));

        private String messageKey;
        private CertificationEntity.Type entityType;

        // List of allowed entity statuses for this type of certification
        private List<AbstractCertificationItem.Status> allowedEntityStatuses;

        /**
         * Default constructor. Adds all types of AbstractCertificationItem.Status to
         * the allowedEntityStatuses
         *
         * @param messageKey message key for display name
         */
        private Type(String messageKey) {
            // Default to identity.
            this(messageKey, CertificationEntity.Type.Identity);
        }

        /**
         * Constructor that takes a message key and an entity type.
         */
        private Type(String messageKey, CertificationEntity.Type type) {
            this(messageKey, type, Arrays.asList(AbstractCertificationItem.Status.values()));
        }

        /**
         * Constructor used when you need an explicit list of allowedEntityStatuses.
         *
         * @param messageKey message key for display name
         * @param allowedEntityStatuses List of allowed entity statuses for this type of certification
         */
        private Type(String messageKey, CertificationEntity.Type entityType,
                     List<AbstractCertificationItem.Status> allowedEntityStatuses) {
            this.messageKey = messageKey;
            this.entityType = entityType;
            this.allowedEntityStatuses = allowedEntityStatuses;
        }
        
        public static Type getDefaultType() {
            return Type.Manager;
        }

        /**
         * @return message key for display name
         */
        public String getMessageKey() {
            return this.messageKey;
        }

        /**
         * @return  List of allowed entity statuses for this type of certification
         */
        public List<AbstractCertificationItem.Status> getAllowedEntityStatuses() {
            return allowedEntityStatuses;
        }

        /**
         * Returns true if the statuses list is non-null and contains the given status
         *
         * @param status Status to check
         * @return true if the status is allowed
         */
        public boolean isAllowedEntityStatus(AbstractCertificationItem.Status status){
            return allowedEntityStatuses != null && allowedEntityStatuses.contains(status);
        }

        /**
         * Indicates what type of entity is being certified, for example, Identity, Account Group, etc.
         *
         * @return entity type being certified, non-null
         */
        public CertificationEntity.Type getEntityType() {
            return entityType;
        }

        /**
         * Return whether this is an identity type certification.
         */
        public boolean isIdentity() {
            return CertificationEntity.Type.Identity.equals(this.entityType);
        }

        /**
         * Return whether this is an object type certification.
         */
        public boolean isObjectType() {
            return !isIdentity();
        }

        /**
         * Convenience method to compare this type to multiple types.
         * @param types List of types to compare this to
         * @return True if this is contained in the list of given types
         */
        public boolean isType(Certification.Type... types){

            if (types==null)
                return false;

            for (Certification.Type type : types){
                if (type.equals(this)){
                    return true;
                }
            }

            return false;
        }

        /**
         * Check whether this type supports recommendations.
         *
         * @return True if the type supports recommendations.
         */
        public boolean supportsRecommendations() {
            return (this.isIdentity() || this.equals(Type.Manager) || this.equals(Type.ApplicationOwner)
                    || this.equals(Type.Group) || this.equals(Type.BusinessRoleMembership) || this.equals(Type.Focused));
        }

        @Override
        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        @Override
        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Phase
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An enumeration of Phases or States that a certification can be in.
     */
    @XMLClass(xmlname="CertificationPhase")
    public static enum Phase {

        /**
         * The certification has been generated, but is not active in the system.
         */
        Staged(MessageKeys.CERT_PHASE_STAGED),
        
        /**
         * Decisions are actively being made on a certification.
         */
        Active(MessageKeys.CERT_PHASE_ACTIVE),

        /**
         * The certification is in a state where decisions can be challenged,
         * for example, a user can challenge a remediation request claiming that
         * they still need the access that is to be removed.
         */
        Challenge(MessageKeys.CERT_PHASE_CHALLENGE),

        /**
         * The certification remediation requests have been sent and the
         * identities are periodically refreshed to see if the requested
         * remediations have occurred.
         */
        Remediation(MessageKeys.CERT_PHASE_REMEDIATION),

        /**
         * A final phase to transition to after all other phases are complete.
         */
        End(MessageKeys.CERT_PHASE_END);


        private String messageKey;

        private Phase(String messageKey) {
            this.messageKey = messageKey;
        }


        public String getMessageKey() {
            return messageKey;
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // EntitlementGranularity
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Enumeration of granularities at which additional entitlement
     * certification items can be generated. The default is Application.
     */
    @XMLClass
    public static enum EntitlementGranularity {

        /**
         * Generate a certification item per application.
         */
        Application(MessageKeys.ENT_GRANULARITY_APP),

        /**
         * Generate a certification item per attribute or permission.
         */
        Attribute(MessageKeys.ENT_GRANULARITY_ATTR_PERM),

        /**
         * Generate a certification item per value or right of an attribute or
         * permission.
         */
        Value(MessageKeys.ENT_GRANULARITY_ATTR_RIGHT_LEFT);


        private String messageKey;

        private EntitlementGranularity(String messageKey) {
            this.messageKey = messageKey;
        }


        public String getMessageKey() {
            return messageKey;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // SelfCertification Level
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Enumeration of levels of allowing self certification
     */
    @XMLClass
    public static enum SelfCertificationAllowedLevel implements MessageKeyHolder {
        //All Certifiers can self certify
        All(MessageKeys.SELF_CERTIFICATION_ALLOWED_LEVEL_ALL),
        //Certification and System Administrators can self certify
        CertificationAdministrator(MessageKeys.SELF_CERTIFICATION_ALLOWED_LEVEL_CERTIFICATION_ADMINISTRATOR),
        //Only System Administrators can self certify
        SystemAdministrator(MessageKeys.SELF_CERTIFICATION_ALLOWED_LEVEL_SYSTEM_ADMINISTRATOR);

        private String messageKey;

        SelfCertificationAllowedLevel(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The maximium length for a certification name. If the hbm changes, this
     * should also change.
     */
    public static final int NAME_MAX_LENGTH = 256;

    /**
     * The maximum length for a certification short name. If the hbm changes,
     * this should also change.
     */
    public static final int SHORT_NAME_MAX_LENGTH = 255;

    /**
     * The name of the special IdentityIQ application that is used when
     * certifying scopes and capabilities of IdentityIQ users.
     */
    public static final String IIQ_APPLICATION = "IdentityIQ";

    /**
     * The name of the capabilities attribute that is used when certifying
     * capabilities of IdentityIQ users.
     */
    public static final String IIQ_ATTR_CAPABILITIES = "Capabilities";

    /**
     * The name of the scopes attribute that is used when certifying scopes
     * of IdentityIQ users.
     */
    public static final String IIQ_ATTR_SCOPES = "Authorized Scopes";

    /**
     * Key appended to the names of email templates to allow email
     * template customization. For example, if this attribute is
     * set to 'foo', sys config  is checked for
     * 'foo.challengeGenerationEmailTemplate' when sending a
     * challenge generation email.
     */
    public static final String ATTR_EMAIL_TEMPLATE_PREFIX="emailTemplatePrefix";


    /**
     * The short name of the certification. This is an abbreviated version of
     * the actual name.
     * 
     * @ignore
     * TODO: i18n
     */
    String _shortName;

    /**
     * The Identity that created this certification process.
     * Note that this is not necessarily the same as the owner, it
     * will typically be a manager of the owner.
     */
    String _creator;

    /**
     * The Identities that currently owns or have completed this certification.
     */
    List<String> _certifiers;

    /**
     * The date that the certification was signed off on. A null value
     * here specifies that the certification has not yet been signed off
     * on. Consider creating a Signature class if this needs to
     * capture more signature information.
     */
    Date _signed;

    /**
     * A list of sign off history. This is most interesting if an approver
     * rule is specified. Otherwise, the history will only contain a single
     * item.
     */
    List<SignOffHistory> _signOffHistory;


    /**
     * Reference to a rule that should get run when the certification is signed
     * to determine if anyone else needs to approve the certification. This is
     * a reference rather than an actual Rule because of the whole "archival"
     * thing, although you could argue that this is not really needed in an
     * archive.
     */
    Reference _approverRule;

    /**
     * The date that the certification was finished (ie - remediations launched,
     * certification information stored on identity, etc...).  A null value
     * here specifies that the certification has not yet been finished.
     */
    Date _finished;

    /**
     * The date a certification expires, or is due. The UI requires an expiration
     * date, so this value should be non-null. However, it is possible to create a
     * certification with a null expiration date from the console, so the value
     * should be checked.
     */
    Date _expiration;

    /**
     * The date a certification will automatically close. Can be null.
     */
    Date _automaticClosingDate;

    /**
     * Optional comments on the certification process.
     */
    String _comments;

    /**
     * An error from the last push through the Certificationer. This gets
     * cleared when the certification is refreshed again.
     */
    String _error;

    /**
     * Describes what is in this certification.
     * This does not affect the structure, but might be used
     * for information in the UI.
     */
    Type _type;

    /**
     * The ID of the TaskSchedule that created this certification.
     */
    String _taskScheduleId;

    /**
     * The ID of the CertificationDefinition that was used to create this
     * certification. This can become null if the definition is deleted (for example -
     * the certification can still exist after the definition has been deleted).
     * This is stored as a String rather than an reference since this is an
     * archival object.
     */
    String _certificationDefinitionId;

    /**
     * The ID of the IdentityTrigger that created this certification. This
     * can become null if the trigger is deleted (for example - the certification can
     * still exist after the trigger has been deleted). This is stored as a
     * String rather than an reference since this is an archival object.
     */
    String _triggerId;

    /**
     * The configuration for each certification phase. If a certification phase
     * is not present in this list it is assumed to be disabled.
     */
    List<ContinuousStateConfig> _continuousConfig;

    /**
     * The current phase of the certification. A non-null phase signifies that
     * a certification has been started. If useRollingPhases is true, this is
     * not incremented - the phases will be maintained on the individual items.
     */
    Phase _phase;

    /**
     * The configuration for each certification phase. If a certification phase
     * is not present in this list it is assumed to be disabled.
     */
    List<CertificationPhaseConfig> _phaseConfig;

    /**
     * Whether remediation requests should be processed immediately when they
     * are made. If false, revokes are not processed until the certification
     * is signed. If true, this will cause remediations (or challenges) to be
     * launched immediately. Note that if a certification is continuous, this
     * has to be true.
     */
    boolean _processRevokesImmediately;

    /**
     * The date at which the next Phase transition should occur. This gets
     * recalculated any time a phase is transitioned (initially when the
     * certification is started and transitions to the Active phase). A null
     * value signifies that there are no more transitions remaining. This is
     * also null if useRollingPhases is true - the nextPhaseTransition will live
     * on each item.
     */
    Date _nextPhaseTransition;

    /**
     * The date on which the next scan for remediations that have been
     * completed should run. When the remediation phase is entered, this is set. Each
     * time the housekeeper scans for completed remediations, this gets advanced
     * until there are no more incomplete remediations. When there are no more
     * incomplete remediations (or the remediation period is transition out of)
     * this gets nulled out.
     */
    Date _nextRemediationScan;

    /**
     * The date on which the next scan for items that need to be
     * transitioned to the CertificationRequired state should run.  This is managed by the
     * ContinuousCertificationer, and is null for non-continuous certs.
     */
    @Deprecated
    Date _nextCertificationRequiredScan;

    /**
     * The date on which the next scan for items that need to be
     * transitioned to the Overdue state should run.  This is managed by the
     * ContinuousCertificationer, and is null for non-continuous certifications.
     */
    @Deprecated
    Date _nextOverdueScan;

    /**
     * The granularity at which additional entitlements have been generated
     * on this certification.
     */
    EntitlementGranularity _entitlementGranularity;

    /**
     * The ID of the application that is being certified. This property
     * is only non-null if this is an application owner certification.
     * Store the ID rather than a reference to prevent the foreign key
     * since this is an "archival" object.
     */
    String _applicationId;

    /**
     * The name of the manager for which this certification was generated.
     * This property is only non-null if this is an manager certification.
     */
    String _manager;

    /**
     * A reference to the GroupDefinition that defines the population of this
     * certification. This property is only non-null if this is a group
     * certification.
     */
    Reference _groupDefinition;

    /**
     * ID of the group definition used to create this certification.
     */
    String _groupDefinitionId;

    /**
     * Name of the group definition used to create this certification.
     */
    String _groupDefinitionName;

    /**
     * Whether this Certification was generated to service identities that
     * were bulk assigned.
     */
    boolean _bulkReassignment;

    /**
     * True if this certification was a generated reassignment to handle self certification entities and items.
     */
    private boolean _selfCertificationReassignment;

    /**
     * Whether this is a continuous or periodic certification.
     *
     * @deprecated This flag is unused. Continuous certifications are no longer supported.
     */
    @Deprecated
    boolean _continuous;

    /**
     * Whether inactive entities should be excluded from this certification.
     * This is only relevant for periodic (non-continuous) certifications
     * since continuous certifications react to all identity changes by
     * default. This needs to be kept here to be able to tell if there are
     * any periodic certifications that should reactive to "inactive" changes
     * on entities. If later it is decided to start reacting to more changes or keeping
     * the certification schedule around for periodic certifications, this can be
     * removed.
     *
     * @deprecated This flag is unused. Periodic certifications are no longer reactive to changes
     * on inactive identities.
     */
    @Deprecated
    boolean _excludeInactive;

    /**
     * A set of objects containing the certification state for each
     * CertificationEntity included in this certification.
     */
    List<CertificationEntity> _entities;

    /**
     * A set of child Certification objects representing certifications
     * that other users must perform in order for this certification
     * to be considered complete.
     *
     * Unclear if this is a requirement, the use case would be
     * a manager that certifies to their immediate reports, but
     * which delegates certification of second level reports to their
     * respective managers.
     *
     * These are stored inside the parent certification on archive.
     */
    List<Certification> _certifications;

    /**
     * Whether the child certification are stored inlined in this object or
     * as references. This is set to true when Certifications are archived.
     */
    boolean _inlineChildCertifications;

    /**
     * For child certifications, a pointer back to the parent.
     */
    Certification _parent;

    /**
     * List of the IDs of CertificationEntities to refresh.
     */
    Set<String> _entitiesToRefresh;

    /**
     * Set of entities to refresh that is used when the entity does not yet
     * have an ID. This will be flushed to the _entitiesToRefresh list after
     * the entities are persisted.
     */
    transient Set<CertificationEntity> _fullEntitiesToRefresh;

    /**
     * A List of CertificationCommands left on the Certification to kick off
     * activity in the Certificationer. Once executed, the Certificationer
     * will remove a command from the list.
     */
    List<CertificationCommand> _commands;

    /**
     * Set of CertificationCommands that have full items or entities that do not yet have an ID.
     * These should be flushed and merged to the _commands list after the items are persisted or
     * no action will be taken.
     */
    transient List<CertificationCommand> _unpersistedCommands;

    /**
     * A list of entities that are not part of the live operational certification
     * but need to be stored so that they can be included in reports and for
     * historical purposes.
     */
    List<ArchivedCertificationEntity> _archivedEntities;

    /**
     * Whether or not it is required that the certifications along the entire
     * hierarchy should be completed before the current certification can
     * be completed
     */
    boolean _completeHierarchy;

    /**
     * Whether all identities on this certification are complete or not.
     * The Certificationer and UI should not allow an certification to
     * be marked as complete until all of the certificationIdentities
     * have been finished. When this is true, the certification can be
     * signed/finished.
     */
    boolean _complete;

    /**
     * WorkItems associated with this certification.
     */
    List<WorkItem> _workItems;

    public static final String ATTR_ALLOW_PROVISIONING = "allowProvisioningRequirements";
    public static final String ATTR_REQ_APPROVAL_COMMENTS = "requireApprovalComments";
    public static final String ATTR_DISP_ENTS_DESC = "displayEntitlementsDescription";

    public static final String ATT_SIGNATURE_TYPE = "certificationSignatureType";

    /**
     * This is needed for deferred email on bulk reassignment, for example if there is a 
     * staged certification with reassignment through pre-delegation rule. It can be stuck here for lack of
     * anywhere better. 
     */
    public static final String ATT_REASSIGNMENT_DESCRIPTION = "bulkReassignmentDescription";
    
    /**
     * Configuration options which do not need to be in their own column
     */
    private Attributes<String,Object> _attributes;

    /**
     * A list of "tags" for this certification.
     */
    private List<Tag> _tags;

    /**
     * List of CertificationGroups this certification belongs to.
     */
    private List<CertificationGroup> _certificationGroups;

    /**
     * This is the date that the certification is activated. For certifications
     * with Staging enabled, this will be null until the user manually activates 
     * the certification. If staging is not enabled, then this date is equal
     * to the creation date.
     */
    private Date _activated;
    
    /**
     * True if the Certification has any signoffs that were done electronically
     * otherwise false
     */
    boolean _electronicallySigned;
    
    /**
     * Statistics the Certificationer will roll up for easier queries
     * being called properly but should be treated as an
     * approximation only.
     */
    private CertificationStatistics statistics = new CertificationStatistics();

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Certification() {
    }

    /**
     * Copy constructor. Create a copy of the given original certification
     * without any of the contents (entities, certifiers, etc...).
     */
    public Certification(Certification orig) {

        // These will likely get reset on the new certification.
        _name = orig._name;
        _shortName = orig._shortName;
        _creator = orig._creator;

        // Don't set the phase information - needs to start fresh for a new cert.
        //_phase = orig._phase;
        //_nextPhaseTransition = orig._nextPhaseTransition;

        // Set the assigned scope, so the assigned scope path gets set too.
        setAssignedScope(orig.getAssignedScope());

        _approverRule = orig._approverRule;
        _expiration = orig._expiration;
        _automaticClosingDate = orig._automaticClosingDate;
        _comments = orig._comments;
        _type = orig._type;
        _taskScheduleId = orig._taskScheduleId;
        _certificationDefinitionId = orig._certificationDefinitionId;
        _triggerId = orig._triggerId;
        _continuousConfig = orig._continuousConfig;
        _phaseConfig = orig._phaseConfig;
        _processRevokesImmediately = orig._processRevokesImmediately;
        _nextRemediationScan = orig._nextRemediationScan;
        _nextCertificationRequiredScan = orig._nextCertificationRequiredScan;
        _nextOverdueScan = orig._nextOverdueScan;
        _entitlementGranularity = orig._entitlementGranularity;
        _applicationId = orig._applicationId;
        _manager = orig._manager;
        _groupDefinition = orig._groupDefinition;
        _groupDefinitionId = orig._groupDefinitionId;
        _groupDefinitionName = orig._groupDefinitionName;
        _bulkReassignment = orig._bulkReassignment;
        _selfCertificationReassignment = orig._selfCertificationReassignment;
        _continuous = orig._continuous;
        _excludeInactive = orig._excludeInactive;
        _completeHierarchy = orig._completeHierarchy;
        _attributes = (orig._attributes == null) ? null : orig._attributes.mediumClone();

        // Create a copy of the tags list to avoid hibernate "shared references
        // to a collection" errors.
        if (null != orig.getTags()) {
            _tags = new ArrayList<Tag>();
            for (Tag tag : orig.getTags()) {
                _tags.add(tag);
            }
        }

        // Add the certification groups.
        addCertificationGroups(orig.getCertificationGroups());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject Overrides
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public void visit(Visitor v) throws GeneralException {

        v.visitCertification(this);
    }

    /**
     * Certifications do not necessarily have unique names.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    /**
     * Include the id since names are not unique.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("name", "Name");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %s\n";
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // WorkItemOwnerChangeListener
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * If a certification work item owner was changed, reset the certifier.
     *
     * @param  resolver   The Resolver to use.
     * @param  item       The WorkItem that had its owner changed.
     * @param  newOwner   The new owner of the work item.
     * @param  prevOwner  The previous owner of the work item.
     */
    public void workItemOwnerChanged(Resolver resolver, WorkItem item,
                                     Identity newOwner, Identity prevOwner)
        throws GeneralException {

        if ((null != newOwner) && (WorkItem.Type.Certification.equals(item.getType()))) {

            // Replace the previous owner in the certifiers list with the new
            // owner.
            List<String> certifiers = this.getCertifiers();
            if (null == certifiers) {
                certifiers = new ArrayList<String>();
            }

            certifiers.remove(prevOwner.getName());
            certifiers.add(newOwner.getName());
            this.setCertifierNames(certifiers);

            // Don't reset the names of the cert or work item.
            //_name = generateCertificationName(resolver);
            //item.setName(_name);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setShortName(String s) {
        _shortName = s;
    }

    /**
     * The short name of the certification. This is an abbreviated version of
     * the actual name.
     */
    public String getShortName() {
        return _shortName;
    }

    @XMLProperty
    public void setCreator(String s) {
        _creator = s;
    }

    public void setCreator(Identity u) {
        if (u != null)
            _creator = u.getName();
        else
            _creator = null;
    }

    /**
     * The Identity that created this certification process.
     * Note that this is not necessarily the same as the owner, it
     * will typically be a manager of the owner.
     */
    public String getCreator() {
        return _creator;
    }

    public Identity getCreator(Resolver r) throws GeneralException {
        Identity u = null;
        if (r != null && _creator != null)
            u = r.getObjectByName(Identity.class, _creator);
        return u;
    }

    /**
     * @exclude
     * Should only be used by persistence frameworks.  
     * @deprecated use {@link #setCertifierIdentities(java.util.List)} or {@link #setCertifierNames(java.util.List)}
     */
    @Deprecated
    @XMLProperty
    public void setCertifiers(List<String> s) {
        _certifiers = s;
    }

    /**
     * The Identities that currently owns or have completed this certification.
     */
    public List<String> getCertifiers() {
        return _certifiers;
    }

    public void setCertifierIdentities(List<Identity> identities) {
        if (identities != null) {
            _certifiers = new ArrayList<String>();
            for (Identity i : identities) {
                _certifiers.add(i.getName());
            }
        }
        else {
            _certifiers = null;
        }
    }

    public void setCertifierNames(List<String> identities) {
        _certifiers = identities;
    }

    @XMLProperty
    public void setType(Type t) {
        _type = t;
    }

    /**
     * Describes what is in this certification.
     */
    public Type getType() {
        return _type;
    }

    @XMLProperty
    public void setTaskScheduleId(String scheduleId) {
        _taskScheduleId = scheduleId;
    }

    /**
     * The ID of the TaskSchedule that created this certification.
     */
    public String getTaskScheduleId() {
        return _taskScheduleId;
    }

    public TaskSchedule getTaskSchedule(Resolver resolver)
        throws GeneralException {

        return (null != _taskScheduleId) ?
            resolver.getObjectById(TaskSchedule.class, _taskScheduleId) : null;
    }

    @XMLProperty()
    public void setCertificationDefinitionId(String id) {
        _certificationDefinitionId = id;
    }

    /**
     * The ID of the CertificationDefinition that was used to create this
     * certification. This can become null if the definition is deleted.
     */
    public String getCertificationDefinitionId() {
        return _certificationDefinitionId;
    }

    public CertificationDefinition getCertificationDefinition(Resolver resolver)
        throws GeneralException {

        CertificationDefinition def = null;
        if (null != _certificationDefinitionId) {
            def = resolver.getObjectById(CertificationDefinition.class, _certificationDefinitionId);
        }
        return def;
    }

    @XMLProperty()
    public void setTriggerId(String id) {
        _triggerId = id;
    }

    /**
     * The ID of the IdentityTrigger that created this certification. This
     * can become null if the trigger is deleted.
     */
    public String getTriggerId() {
        return _triggerId;
    }

    public IdentityTrigger getIdentityTrigger(Resolver resolver)
        throws GeneralException {

        IdentityTrigger trigger = null;
        if (null != _triggerId) {
            trigger = resolver.getObjectById(IdentityTrigger.class, _triggerId);
        }
        return trigger;
    }

    /**
     * Return the name of the property of a CertificationItem for an
     * identity certification that has the identity name.
     */
    public String getIdentityProperty() {
        String prop = null;
        if (this.isCertifyingIdentities()) {
            prop = "parent.identity";

            // Account group membership and data owner certs store the identity
            // as the target of the item.
            if (Certification.Type.AccountGroupMembership.equals(this.getType()) ||
                Certification.Type.DataOwner.equals(this.getType())) {
                prop = "targetName";
            }
        }
        return prop;
    }

    @XMLProperty
    public void setContinuousConfig(List<ContinuousStateConfig> config) {
        _continuousConfig = config;
    }

    /**
     * The configuration for each certification phase. If a certification phase
     * is not present in this list it is assumed to be disabled.
     */
    public List<ContinuousStateConfig> getContinuousConfig() {
        return _continuousConfig;
    }

    @XMLProperty
    public void setPhase(Phase p) {
        _phase = p;
    }

    /**
     * The current phase of the certification. A non-null phase signifies that
     * a certification has been started. If useRollingPhases is true, this is
     * not incremented - the phases will be maintained on the individual items.
     */
    public Phase getPhase() {
        return _phase;
    }

    @XMLProperty
    public void setPhaseConfig(List<CertificationPhaseConfig> phaseConfig) {
        _phaseConfig = phaseConfig;
    }

    /**
     * The configuration for each certification phase. If a certification phase
     * is not present in this list it is assumed to be disabled.
     */
    public List<CertificationPhaseConfig> getPhaseConfig() {
        return _phaseConfig;
    }

    /**
     * True if remediation requests should be processed immediately when they
     * are made. If false, revokes are not processed until the certification
     * is signed. If true, this will cause remediations (or challenges) to be
     * launched immediately. Note that if a certification is continuous, this
     * has to be true.
     */
    @XMLProperty
    public boolean isProcessRevokesImmediately() {
        return _processRevokesImmediately;
    }

    public void setProcessRevokesImmediately(boolean useRollingPhases) {
        _processRevokesImmediately = useRollingPhases;
    }

    @XMLProperty
    public void setNextPhaseTransition(Date phaseTransition) {
        _nextPhaseTransition = phaseTransition;
    }

    /**
     * The date at which the next Phase transition should occur. This gets
     * recalculated any time a phase is transitioned (initially when the
     * certification is started and transitions to the Active phase). A null
     * value signifies that there are no more transitions remaining. This is
     * also null if useRollingPhases is true - the nextPhaseTransition will live
     * on each item.
     */
    public Date getNextPhaseTransition() {
        return _nextPhaseTransition;
    }

    @XMLProperty
    public void setNextRemediationScan(Date next) {
        _nextRemediationScan = next;
    }

    /**
     * The date on which the next scan for remediations that have been
     * completed should run. When the remediation phase is entered, this is set. Each
     * time the housekeeper scans for completed remediations, this gets advanced
     * until there are no more incomplete remediations. When there are no more
     * incomplete remediations (or the remediation period is ended)
     * this gets nulled out.
     */
    public Date getNextRemediationScan() {
        return _nextRemediationScan;
    }

    @XMLProperty
    @Deprecated
    public void setNextCertificationRequiredScan(Date next) {
        _nextCertificationRequiredScan = next;
    }

    /**
     * The date on which the next scan for items that need to be
     * transitioned to the CertificationRequired state should run. This is managed by the
     * ContinuousCertificationer, and is null for non-continuous certifications.
     */
    @Deprecated
    public Date getNextCertificationRequiredScan() {
        return _nextCertificationRequiredScan;
    }

    @XMLProperty
    @Deprecated
    public void setNextOverdueScan(Date next) {
        _nextOverdueScan = next;
    }

    /**
     * The date on which the next scan for items that need to be
     * transitioned to the Overdue state should run. This is managed by the
     * ContinuousCertificationer, and is null for non-continuous certifications.
     */
    @Deprecated
    public Date getNextOverdueScan() {
        return _nextOverdueScan;
    }

    @XMLProperty
    public void setEntitlementGranularity(EntitlementGranularity eg) {
        _entitlementGranularity = eg;
    }

    /**
     * The granularity at which additional entitlements have been generated
     * on this certification.
     */
    public EntitlementGranularity getEntitlementGranularity() {
        return _entitlementGranularity;
    }

    @XMLProperty
    public void setBulkReassignment(boolean bulkReassignment) {
        _bulkReassignment = bulkReassignment;
    }

    /**
     * True if this Certification was generated to service identities that
     * were bulk assigned.
     */
    public boolean isBulkReassignment() {
        return _bulkReassignment;
    }

    /**
     * True if this certification was a generated reassignment to handle self certification entities and items.
     */
    @XMLProperty
    public boolean isSelfCertificationReassignment() {
        return _selfCertificationReassignment;
    }

    public void setSelfCertificationReassignment(boolean selfCertificationReassignment) {
        _selfCertificationReassignment = selfCertificationReassignment;
    }

    /**
     * True if this is a continuous or periodic certification.
     *
     * @deprecated This flag is unused. Continuous certifications are no longer supported.
     */
    @XMLProperty
    @Deprecated
    public boolean isContinuous() {
        return _continuous;
    }

    @Deprecated
    public void setContinuous(boolean continuous) {
        _continuous = continuous;
    }

    /**
     * Return true if this certification is operating on identities.
     */
    public boolean isCertifyingIdentities() {
        Certification.Type certType = this.getType();
        return CertificationEntity.Type.Identity.equals(certType.getEntityType()) ||
               Certification.Type.AccountGroupMembership.equals(certType) ||
               Certification.Type.DataOwner.equals(certType);
    }
    
    public boolean isCertifyingGroups() {
        Certification.Type certType = this.getType();
        return Certification.Type.AccountGroupPermissions.equals(certType);
    }

    /**
     * True if inactive entities should be excluded from this certification.
     * This is only relevant for periodic (non-continuous) certifications
     * since continuous certifications react to all identity changes by
     * default. This score needs to be kept here to be able to tell if there are
     * any periodic certifications that should reactive to "inactive" changes
     * on entities. If later it is decided to start reacting to more changes or keeping
     * the certification schedule around for periodic certifications, this can be
     * removed.
     *
     * @deprecated This flag is unused. Periodic certifications are no longer reactive to changes
     * on inactive identities.
     */
    @Deprecated
    @XMLProperty
    public boolean isExcludeInactive() {
        return _excludeInactive;
    }

    @Deprecated
    public void setExcludeInactive(boolean exclude) {
        _excludeInactive = exclude;
    }

    @XMLProperty
    public void setApplicationId(String applicationId) {
        _applicationId = applicationId;
    }

    /**
     * The ID of the application that is being certified. This property
     * is only non-null if this is an application owner certification.
     */
    public String getApplicationId() {
        return _applicationId;
    }

    public Application getApplication(Resolver r) throws GeneralException {
        Application a = null;
        if (r != null && _applicationId != null)
            a = r.getObjectById(Application.class, _applicationId);
        return a;
    }

    @XMLProperty
    public void setManager(String manager) {
        _manager = manager;
    }

    public void setManager(Identity manager) {
        _manager = (null != manager) ? manager.getName() : null;
    }

    /**
     * The name of the manager for which this certification was generated.
     * This property is only non-null if this is an manager certification.
     */
    public String getManager() {
        return _manager;
    }

    public Identity getManager(Resolver r) throws GeneralException {
        Identity i = null;
        if (r != null && _manager != null)
            i = r.getObjectByName(Identity.class, _manager);
        return i;
    }

    /**
     * A reference to the GroupDefinition that defines the population of this
     * certification. This property is only non-null if this is a group
     * certification.
     * @deprecated Use {@link #getGroupDefinitionId()} or {@link #getGroupDefinitionName()} 
     */
    @Deprecated
    @XMLProperty(xmlname="GroupDefinitionRef")
    public Reference getGroupDefinition() {
        return _groupDefinition;
    }

    /**
     * @deprecated Use {@link #getGroupDefinitionId()} or {@link #getGroupDefinitionName()} 
     */
    @Deprecated
    public void setGroupDefinition(Reference groupDefinition) {
        _groupDefinition = groupDefinition;
    }

    public void setGroupDefinition(GroupDefinition def) {
        Reference ref = null;
        if (null != def) {
            ref = new Reference(def);
            _groupDefinitionId = def.getId();
            _groupDefinitionName = def.getName();
        } else {
            _groupDefinitionId = null;
            _groupDefinitionName = null;
        }
        _groupDefinition = ref;
    }

    public String getGroupDefinitionId() {
        return _groupDefinitionId;
    }

    @XMLProperty
    public void setGroupDefinitionId(String groupDefinitionId) {
        this._groupDefinitionId = groupDefinitionId;
    }

    public String getGroupDefinitionName() {
        return _groupDefinitionName;
    }

    @XMLProperty
    public void setGroupDefinitionName(String groupDefinitionName) {
        this._groupDefinitionName = groupDefinitionName;
    }

    @XMLProperty
    public void setComplete(boolean b) {
        _complete = b;
    }

    /**
     * True when all identities on this certification are complete.
     * The Certificationer and UI should not allow an certification to
     * be marked as complete until all of the certificationIdentities
     * have been finished. When this is true, the certification can be
     * signed/finished.
     */
    public boolean isComplete() {
        return _complete;
    }

    @XMLProperty
    public void setCompleteHierarchy(boolean b) {
        _completeHierarchy = b;
    }

    /**
     * True if it is required that the certifications along the entire
     * hierarchy should be completed before the current certification can
     * be completed.
     */
    public boolean isCompleteHierarchy() {
        return _completeHierarchy;
    }

    /**
     * Return whether reassignment child certifications must be completed before
     * this certification is completed. This is similar to isCompleteHierarchy(),
     * but applies to reassignments instead of hierarchical manager certifications.
     *
     * @param  dflts  The system configuration defaults.
     */
    public boolean isRequireReassignmentCompletion(Attributes<String,Object> dflts) {

        Object require =
            getAttribute(Configuration.REQUIRE_REASSIGNMENT_COMPLETION, dflts);

        // Lazy upgrade - default to true.
        if (null == require) {
            require = true;
        }

        return Util.otob(require);
    }

   /* Return whether reassignment child certifications must be completed before
    * this certification is completed.  This is similar to isCompleteHierarchy(),
    * but applies to reassignments vs. hierarchical manager certs.
    */
    public boolean isAutoSignoffOnReassignment() {
        Object isAutoSignoff;

        if (null != _attributes)
            isAutoSignoff = _attributes.get(Configuration.AUTOMATE_SIGNOFF_ON_REASSIGNMENT);
        else
            isAutoSignoff = null;

        // Lazy upgrade - default to false.
        if (null == isAutoSignoff) {
            isAutoSignoff = false;
        }

        return Util.otob(isAutoSignoff);
    }

    /**
     * Return whether reassignment certifications should have their contents
     * assimilated into the parent certification and be deleted after being
     * signed.
     *
     * @param  dflts  The system configuration defaults.
     */
    public boolean isAssimilateBulkReassignments(Attributes<String,Object> dflts) {
        return Util.otob(getAttribute(Configuration.ASSIMILATE_BULK_REASSIGNMENTS, dflts));
    }

    /**
     * Check if Limit Reassignments is enabled. Retrieve the value from Certification 
     * Definition/System Configuration
     * @param ctx SailPointContext
     * @throws GeneralException 
     */
    public boolean isLimitReassignments(SailPointContext ctx) throws GeneralException {
        CertificationDefinition certDef = getCertificationDefinition(ctx);
        return (certDef != null) ? certDef.isLimitReassignments(ctx) : ctx.getConfiguration().getBoolean(Configuration.CERTIFICATION_LIMIT_REASSIGNMENTS);
    }

    /**
     * Retrieve the value of Reassignment limit set in Certification 
     * Definition/System Configuration
     * @param ctx SailPointContext
     * @throws GeneralException 
     */
    public int getReassignmentLimit(SailPointContext ctx) throws GeneralException {
        CertificationDefinition certDef = getCertificationDefinition(ctx);
        return (certDef != null) ? certDef.getReassignmentLimit(ctx) : ctx.getConfiguration().getInt(Configuration.CERTIFICATION_REASSIGNMENT_LIMIT);
    }

    /** 
     * Return total count of nodes up in the certification chain. 
     */
    private int getReassignmentCount() {
        int count = 0;
        Certification currentCert = this;
        while (null != currentCert && currentCert.isBulkReassignment()) {
            count++;
            currentCert = currentCert.getParent();
        }
        return count;
    }

    /**
     * Determine if reassignment should be allowed. Compare Reassignment Limit with current count
     * of reassignments
     * @param context SailPointContext
     * @return true if reassignment is not allowed, false otherwise
     * @throws GeneralException
     */
    public boolean limitCertReassignment(SailPointContext context) throws GeneralException {

        return (isLimitReassignments(context) ?
            (getReassignmentCount() >= getReassignmentLimit(context)) : false);
    }

    @XMLProperty
    public void setSigned(Date d) {
        _signed = d;
    }

    /**
     * The date that the certification was signed off. A null value
     * here indicates that signed off is pending.
     */
    public Date getSigned() {
        return _signed;
    }

    /**
     * A list of sign off history. This is most interesting if an approver
     * rule is specified. Otherwise, the history will only contain a single
     * item.
     */
    @XMLProperty
    public List<SignOffHistory> getSignOffHistory() {
        return _signOffHistory;
    }

    /**
     * @exclude
     * This setter is here for the persistence frameworks.
     * @deprecated Use {@link #addSignOffHistory(Identity)} instead.  
     */
    @Deprecated
    public void setSignOffHistory(List<SignOffHistory> history) {
        _signOffHistory = history;
    }

    /**
     * Add an element to the sign off history indicating that the given identity
     * signed off on this certification.
     *
     * @param  signer  The Identity that signed off on the certification.
     */
    public void addSignOffHistory(Identity signer) {
        if (null == _signOffHistory) {
            _signOffHistory = new ArrayList<SignOffHistory>();
        }

        _signOffHistory.add(new SignOffHistory(signer));
    }

    /**
     * Override SailPointObject methods to implement signatures for Certifications
     */
    @Override
    public void addSignOff(SignOffHistory signOff) {
        if(_signOffHistory == null) {
            _signOffHistory = new ArrayList<SignOffHistory>();
        }
        _signOffHistory.add(signOff);
    }
    
    @Override
    public List<SignOffHistory> getSignOffs() {
        return _signOffHistory;
    }
    
    
    public boolean isElectronicallySigned() {
        return _electronicallySigned;
    }

    public void setElectronicallySigned(boolean _electronicallySigned) {
        this._electronicallySigned = _electronicallySigned;
    }

    /**
     * Reference to a rule that should get run when the certification is signed
     * to determine if anyone else needs to approve the certification.
     */
    @XMLProperty
    public Reference getApproverRule() {
        return _approverRule;
    }

    public void setApproverRule(Reference rule) {
        _approverRule = rule;
    }

    public void setApproverRule(Rule rule) {
        Reference ref = null;
        if (null != rule) {
            ref = new Reference(rule);
        }
        _approverRule = ref;
    }

    public Rule getApproverRule(Resolver resolver) throws GeneralException {
        Rule rule = null;
        if (null != _approverRule) {
            rule = (Rule) _approverRule.resolve(resolver);
        }
        return rule;
    }

    @XMLProperty
    public void setFinished(Date d) {
        _finished = d;
    }

    /**
     * The date that the certification was finished. This is after
     * remediations have been launched and certification information
     * has been stored on identity. A null value indicates
     * that the certification has not yet been finished.
     */
    public Date getFinished() {
        return _finished;
    }
    
    @XMLProperty
    public void setActivated(Date activated) {
        _activated = activated;
    }
    
    public Date getActivated() {
        return _activated;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setComments(String s) {
        _comments = s;
    }

    /**
     * Optional comments on the certification process.
     */
    public String getComments() {
        return _comments;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setError(String s) {
        _error = s;
    }

    /**
     * An error from the last push through the Certificationer. This gets
     * cleared when the certification is refreshed again.
     */
    public String getError() {
        return _error;
    }

    /**
     * This is the Hibernate accessor. Trust the back pointer
     * in the entities.
     */
    @BidirectionalCollection(elementClass=CertificationEntity.class, elementProperty="certification")
    public void setEntities(List<CertificationEntity> entities) {
        _entities = entities;
    }

    /**
     * A set of objects containing the certification state for each
     * CertificationEntity included in this certification.
     */
    public List<CertificationEntity> getEntities() {
        return _entities;
    }

    /**
     * @exclude
     * Used for xml serialization
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setXmlEntities(List<CertificationEntity> entities) {
        _entities = entities;
        if (entities != null) {
            for (CertificationEntity entity : entities)
                entity.setCertification(this);
        }
    }

    /**
     * @exclude
     * Used for xml serialization
     */
    public List<CertificationEntity> getXmlEntities() {
        return _entities;
    }

    public void add(CertificationEntity id) {
        if (id != null) {
            if (_entities == null)
                _entities = new ArrayList<CertificationEntity>();
            _entities.add(id);
            id.setCertification(this);
        }
    }

    public void addAll(Collection<CertificationEntity> ids) {
        if (ids != null) {
            for (CertificationEntity id : ids) {
                add(id);
            }
        }
    }

    public void removeEntity(CertificationEntity entity) {
        if (null != _entities) {
            _entities.remove(entity);
        }

        if (null != _entitiesToRefresh) {
            _entitiesToRefresh.remove(entity.getId());
        }

        entity.setCertification(null);
    }

    @BidirectionalCollection(elementClass=Certification.class, elementProperty="parent")
    public void setCertifications(List<Certification> certs) {
        _certifications = certs;
    }

    /**
     * A set of child Certification objects representing certifications
     * that other users must perform in order for this certification
     * to be considered complete.
     *
     * These are stored inside the parent certification on archive.
     */
    public List<Certification> getCertifications() {
        return _certifications;
    }

    /**
     * Get a count of all child certifications. This avoids calling
     * getCertifications().isEmpty() which can bloat the hibernate cache.
     *
     * @param ctx SailPointContext
     * @return count of child certifications
     * @throws GeneralException
     */
    public int getCertificationCount(SailPointContext ctx) throws GeneralException{
        QueryOptions ops = new QueryOptions(Filter.eq("parent.id", this._id));
        ops.setScopeResults(false);
        return ctx.countObjects(Certification.class, ops);
    }


    public void add(Certification cert) {
        if (cert != null) {
            if (_certifications == null)
                _certifications = new ArrayList<Certification>();
            _certifications.add(cert);
            cert.setParent(this);
        }
    }

    /**
     * Indicates whether or not all child certifications have been
     * completed. This uses projection queries so it does not have to
     * walk the certification hierarchy and bloat the hibernate cache
     *
     * @return true if all child certifications have been signed.
     */
    public static boolean isChildCertificationsComplete(SailPointContext ctx, String certId) throws GeneralException{

        QueryOptions ops = new QueryOptions(Filter.eq("parent.id", certId));
        Iterator<Object[]> results = ctx.search(Certification.class, ops, Arrays.asList("id", "signed"));
        if (results != null){
            List<String> children = new ArrayList<String>();
            // make a first pass checking to see if any of the immediate children are un-signed
            while(results.hasNext()){
                Object[]  result = results.next();
                if (result[1] == null)
                    return false;
                children.add(result[0].toString());
            }

            // If we can't find any unsigned immediate children, recurse the hierarchy
            for (String id : children){
                if (!isChildCertificationsComplete(ctx, id))
                    return false;
            }
        }

        return true;
    }

    @XMLProperty
    public void setInlineChildCertifications(boolean inline) {
        _inlineChildCertifications = inline;
    }

    /**
     * True if the child certification are stored inlined in this object or
     * as references. This is set to true when Certifications are archived.
     */
    public boolean isInlineChildCertifications() {
        return _inlineChildCertifications;
    }

    /**
     * @exclude
     * Note that this is the Hibernate property setter, it is assumed that
     * the parent pointer in the child certification is already set so that
     * materialization of the proxy list can be avoided.
     *
     * @deprecated  use {@link #setCertifications(java.util.List)} 
     */
    @Deprecated
    public void setHibernateCertifications(List<Certification> certs) {
        _certifications = certs;
    }

    /**
     * @exclude
     * This should only be used by Hibernate.
     * @deprecated use {@link #getCertifications()}  
     */
    @Deprecated
    public List<Certification> getHibernateCertifications() {
        return _certifications;
    }


    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> attributes) {
        this._attributes = attributes;
    }

    /**
     * Retrieve an attribute from this certification, and if not found on the
     * certification fall back to the value in the given system defaults map.
     *
     * @param  attrName        The name of the attribute to retrieve.
     * @param  systemDefaults  A map of the system default values.
     */
    public Object getAttribute(String attrName,
                               Attributes<String,Object> systemDefaults) {

        Object val = null;

        if (null != _attributes) {
            val = _attributes.get(attrName);
        }

        if ((null == val) && (null != systemDefaults)) {
            val = systemDefaults.get(attrName);
        }

        return val;
    }

    /**
     * Returns the email template prefix to be used when retrieving
     * email templates for this certification. This value can be set by
     * setting a special attribute on the certification. If no
     * attribute is set, the certification type name will be returned.
     * @return Email template prefix string
     */
    public String getEmailTemplatePrefix() {
        return _attributes != null ? _attributes.getString(Certification.ATTR_EMAIL_TEMPLATE_PREFIX) : null;
    }

    public void setEmailTemplatePrefix(String prefix){
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();

        if (prefix != null)
            _attributes.put(Certification.ATTR_EMAIL_TEMPLATE_PREFIX, prefix);
        else if(_attributes.containsKey(Certification.ATTR_EMAIL_TEMPLATE_PREFIX))
            _attributes.remove(Certification.ATTR_EMAIL_TEMPLATE_PREFIX);
    }

    /**
     * @return True if the certifier is allowed to provision missing required roles during an approval.
     */
    public boolean isAllowProvisioningRequirements() {
        return _attributes != null && _attributes.getBoolean(Certification.ATTR_ALLOW_PROVISIONING);
    }

    /**
     * Indicates that the certifier is allowed to provision missing required roles during an approval.
     */
    public void setAllowProvisioningRequirements(boolean allowProvisioningRequirements) {
        // only create the attributes if we have to
        boolean currentValue = _attributes != null && _attributes.getBoolean(ATTR_ALLOW_PROVISIONING);
        if (currentValue != allowProvisioningRequirements){
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(ATTR_ALLOW_PROVISIONING, new Boolean(allowProvisioningRequirements));
        }
    }

    /**
     * Returns true if the certifier is required to enter a comment when a
     * certification item is approved.
     */
    public boolean isRequireApprovalComments() {
        return _attributes != null && _attributes.getBoolean(ATTR_REQ_APPROVAL_COMMENTS);
    }

    /**
     * Sets attribute which indicate if the certifier is required to enter a comment when a
     * certification item is approved
     */
    public void setRequireApprovalComments(boolean requireApprovalComments) {
        // only create the attributes if we have to
        boolean currentValue = _attributes != null && _attributes.getBoolean(ATTR_REQ_APPROVAL_COMMENTS);
        if (currentValue != requireApprovalComments){
            if (_attributes == null)
                _attributes = new Attributes<String,Object>();
            _attributes.put(ATTR_REQ_APPROVAL_COMMENTS, new Boolean(requireApprovalComments));
        }
    }

     /**
     * Returns true if entitlement descriptions should be displayed
     * rather than entitlement names.
     */
    public Boolean getDisplayEntitlementDescription() {
        return _attributes != null && _attributes.containsKey(ATTR_DISP_ENTS_DESC) ?
                (Boolean)_attributes.get(ATTR_DISP_ENTS_DESC) : null;
    }

    /**
     * Sets attribute which indicates whether the entitlement description should be displayed
     * rather than the entitlement name.
     */
    public void setDisplayEntitlementDescription(Boolean displayEntitlementDescription) {
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();
        if (displayEntitlementDescription != null)
            _attributes.put(ATTR_DISP_ENTS_DESC, displayEntitlementDescription);
    }

    /**
     * @exclude
     * Alternate accessor for XML. Set the child list and
     * the parent pointer. This only sets the Certifications if
     * inlineChildCertifications is false.
     *
     * @deprecated  This should only be used by the XML annotation processor.
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST,xmlname="CertificationRefs")
    public void setXmlCertificationRefs(List<Certification> certs) {
        if (!_inlineChildCertifications) {
            _certifications = certs;
            if (_certifications != null) {
                for (Certification a : _certifications)
                    a.setParent(this);
            }
        }
    }

    /**
     * @exclude
     * @deprecated  This should only be used by the XML annotation processor.
     */
    @Deprecated
    public List<Certification> getXmlCertificationRefs() {
        return (!_inlineChildCertifications) ? _certifications : null;
    }

    /**
     * @exclude
     * Set's the certification as inline in the XML. This is used when the
     * certification is archived to get rid of external references.
     *
     * @deprecated  This should only be used by the XML annotation processor.
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setXmlInlineCertifications(List<Certification> certs) {
        if (_inlineChildCertifications) {
            _certifications = certs;
            if (_certifications != null) {
                for (Certification a : _certifications)
                    a.setParent(this);
            }
        }
    }

    /**
     * @exclude
     * @deprecated  This should only be used by the XML annotation processor.
     */
    @Deprecated
    public List<Certification> getXmlInlineCertifications() {
        return (_inlineChildCertifications) ? _certifications : null;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setParent(Certification cert) {
        // do not check the inverse relationship here, let
        // the parent do that
        _parent = cert;
    }

    /**
     * For child certifications, a pointer back to the parent.
     */
    public Certification getParent() {
        return _parent;
    }

    @XMLProperty(mode=SerializationMode.SET)
    public void setEntitiesToRefresh(Set<String> ids) {
        _entitiesToRefresh = ids;
    }

    /**
     * List of the IDs of CertificationEntities to refresh.
     */
    public Set<String> getEntitiesToRefresh() {
        return _entitiesToRefresh;
    }

    /**
     * Set of entities to refresh that is used when the entity does not yet
     * have an ID. This will be flushed to the _entitiesToRefresh list after
     * the entities are persisted.
     */
    public Set<CertificationEntity> getFullEntitiesToRefresh() {
        return _fullEntitiesToRefresh;
    }

    public void addEntityToRefresh(CertificationEntity id) {
        if (null != id) {
            if (null != id.getId()) {
                if (null == _entitiesToRefresh) {
                    _entitiesToRefresh = new HashSet<String>();
                }
                _entitiesToRefresh.add(id.getId());
            }
            else {
                // If we don't have an ID yet, add this to the transient list
                // that will be flushed later.
                if (null == _fullEntitiesToRefresh) {
                    _fullEntitiesToRefresh = new HashSet<CertificationEntity>();
                }
                _fullEntitiesToRefresh.add(id);
            }
        }
    }

    public void clearEntitiesToRefresh() {
        if (null != _entitiesToRefresh) {
            _entitiesToRefresh.clear();
        }
        if (null != _fullEntitiesToRefresh) {
            _fullEntitiesToRefresh.clear();
        }
    }

    /**
     * After the certification entities have been persisted, flush any entities
     * from the fullEntitiesToRefresh field to the entitiesToRefresh field.
     * This is required because entities can be marked for refresh before they
     * are persisted and have an ID.
     *
     * @throws GeneralException If any of the entities in fullEntitiesToRefresh
     *                          don't yet have an ID.
     */
    public void flushFullEntitiesToRefresh() throws GeneralException {

        if ((null != _fullEntitiesToRefresh) && !_fullEntitiesToRefresh.isEmpty()) {
            for (CertificationEntity entity : _fullEntitiesToRefresh) {
                if (null == entity.getId()) {
                    throw new GeneralException("Can only flush full entities to refresh after entity is persisted.");
                }

                addEntityToRefresh(entity);
            }
        }
    }

    /**
     * A List of CertificationCommands left on the Certification to kick off
     * activity in the Certificationer. Once executed, the Certificationer
     * will remove a command from the list.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<CertificationCommand> getCommands() {
        return _commands;
    }

    public void setCommands(List<CertificationCommand> commands) {
        _commands = commands;
    }

    public void addCommand(CertificationCommand command) {
        if (null == _commands) {
            _commands = new ArrayList<CertificationCommand>();
        }
        _commands.add(command);
    }

    public void mergeCommand(CertificationCommand cmd) {
        CertificationCommand found =
            CertificationCommand.findSimilar(cmd, _commands);
        if (null != found) {
            found.mergeItems(cmd.getItemIds());
        }
        else {
            addCommand(cmd);
        }
    }

    public void removeCommand(CertificationCommand cmd) {
        if (null != _commands) {
            _commands.remove(cmd);
        }
    }

    public void clearCommands() {
        if (null != _commands) {
            _commands.clear();
        }
    }

    /**
     * @exclude 
     * This is to be used only by Hibernate.!
     * This causes all the entities to be loaded and can cause OOM exception.
     * A list of entities that are not part of the live operational certification
     * but need to be stored so that they can be included in reports and for
     * historical purposes.
     * 
     * @deprecated Use {@link #fetchArchivedEntities(SailPointContext)} instead.
     */
    @Deprecated
    @XMLProperty
    public List<ArchivedCertificationEntity> getArchivedEntities() {
        return _archivedEntities;
    }

    /**
     * @exclude
     * @deprecated This is to be only used by hibernate.!
     */
    @Deprecated
    public void setArchivedEntities(List<ArchivedCertificationEntity> entities) {
        _archivedEntities = entities;
    }

    /**
     *
     * This method will load the archived entities for the certification from database.
     * Remember to not call this method more than you need. 
     * This is not a getter so save the results to a variable if you need to use it more than once
     * in a method.
     */
    public List<ArchivedCertificationEntity> fetchArchivedEntities(SailPointContext context) throws GeneralException {
        
        return context.getObjects(ArchivedCertificationEntity.class, new QueryOptions(Filter.eq("certification", this)));
    }
    
    public int getArchivedEntityCount(SailPointContext ctx) throws GeneralException{
        QueryOptions ops = new QueryOptions(Filter.eq("certification.id", this._id));
        ops.setScopeResults(false);
        return ctx.countObjects(ArchivedCertificationEntity.class, ops);
    }

    public boolean mergeArchivedEntity(ArchivedCertificationEntity entity, Resolver resolver)
            throws GeneralException {

        boolean found = false;
        
        ArchivedCertificationEntity fromDb = findArchivedEntity(entity.getEntity(), resolver);
        if (null != fromDb) {
            found = true;
            fromDb.getEntity().mergeEntity(entity.getEntity(), false, false);
        }
        
        return found;
    }    
    

    /**
     * Find an ArchivedCertificationEntity in the archived entity list on this
     * certification for the given entity. This returns null if there is more than one
     * matching entity found or there are no matching archived entities.
     *
     * @param  entity    The entity for which to find the archived entity.
     * @param  resolver  The resolver to use
     *
     * @return A matching archived entity if there is a single matching entity
     *         in the archived entities list.
     */
    public ArchivedCertificationEntity findArchivedEntity(CertificationEntity entity,
                                                          Resolver resolver)
        throws GeneralException {

        Filter f = entity.getEqualsFilter(this);
        QueryOptions qo = new QueryOptions();
        qo.add(f);
        List<ArchivedCertificationEntity> matches =
            resolver.getObjects(ArchivedCertificationEntity.class, qo);

        ArchivedCertificationEntity found = null;
        if ((null != matches) && !matches.isEmpty()) {
            found = matches.get(0);
        }

        return found;
    }

    @XMLProperty
    public void setTotalEntities(int i) {
        statistics.setTotalEntities(i);
    }

    /**
     * The total number of certification identities in this report.
     */
    public int getTotalEntities() {
        return statistics.getTotalEntities();
    }

    public int getOpenEntities()
    {
        return statistics.getOpenEntities();
    }

    public int getOpenItems()
    {
        return statistics.getOpenItems();
    }

    @XMLProperty
    public void setCompletedEntities(int i) {
        statistics.setCompletedEntities(i);
    }

    /**
     * The number of entities that have been completed.
     */
    public int getCompletedEntities() {
        return statistics.getCompletedEntities();
    }


    @XMLProperty
    public void setDelegatedEntities(int i) {
        statistics.setDelegatedEntities(i);
    }

    /**
     * The number of entities that have one or more items that
     * have been delegated.
     */
    public int getDelegatedEntities() {
        return statistics.getDelegatedEntities();
    }

    /**
     * The percentage of completion (number of completed certification
     * identities compared to the number of total certification identities) for this
     * certification.
     */
    @XMLProperty
    public int getPercentComplete() {
        return statistics.getPercentComplete();
    }


    public void setPercentComplete(int complete) {
        statistics.setPercentComplete(complete);
    }

    @XMLProperty
    public int getCertifiedEntities() {
        return statistics.getCertifiedEntities();
    }

    public void setCertifiedEntities(int certifiedEntities) {
        statistics.setCertifiedEntities(certifiedEntities);
    }

    @XMLProperty
    public int getCertificationRequiredEntities() {
        return statistics.getCertificationRequiredEntities();
    }

    public void setCertificationRequiredEntities(int certReqEntities) {
        statistics.setCertificationRequiredEntities(certReqEntities);
    }

    @XMLProperty
    public int getOverdueEntities() {
        return statistics.getOverdueEntities();
    }

    public void setOverdueEntities(int overdueEntities) {
        statistics.setOverdueEntities(overdueEntities);
    }

    @XMLProperty
    public int getTotalItems() {
        return statistics.getTotalItems();
    }

    public void setTotalItems(int totalItems) {
        statistics.setTotalItems(totalItems);
    }

    @XMLProperty
    public int getCompletedItems() {
        return statistics.getCompletedItems();
    }

    public void setCompletedItems(int completedItems) {
        statistics.setCompletedItems(completedItems);
    }

    @XMLProperty
    public int getDelegatedItems() {
        return statistics.getDelegatedItems();
    }

    public void setDelegatedItems(int delegatedItems) {
        statistics.setDelegatedItems(delegatedItems);
    }

    @XMLProperty
    public int getItemPercentComplete() {
        return statistics.getItemPercentComplete();
    }

    public void setItemPercentComplete(int itemPercentComplete) {
        statistics.setItemPercentComplete(itemPercentComplete);
    }

    @XMLProperty
    public int getCertifiedItems() {
        return statistics.getCertifiedItems();
    }

    public void setCertifiedItems(int certifiedItems) {
        statistics.setCertifiedItems(certifiedItems);
    }

    @XMLProperty
    public int getCertificationRequiredItems() {
        return statistics.getCertificationRequiredItems();
    }

    public void setCertificationRequiredItems(int certReqItems) {
        statistics.setCertificationRequiredItems(certReqItems);
    }

    @XMLProperty
    public int getOverdueItems() {
        return statistics.getOverdueItems();
    }

    public void setOverdueItems(int overdueItems) {
        statistics.setOverdueItems(overdueItems);
    }

    @XMLProperty
    public void setRemediationsKickedOff(int i) {
        statistics.setRemediationsKickedOff(i);
    }

    /**
     * The total number of items in this certification for which remediation
     * requests been launched. See
     * {@link CertificationAction#remediationKickedOff}.
     */
    public int getRemediationsKickedOff() {
        return statistics.getRemediationsKickedOff();
    }

    @XMLProperty
    public void setRemediationsCompleted(int i) {
        statistics.setRemediationsCompleted(i);
    }

    /**
     * The number of remediations requests that have been completed in this
     * certification. See {@link CertificationAction#remediationCompleted}.
     */
    public int getRemediationsCompleted() {
        return statistics.getRemediationsCompleted();
    }

    /**
     * WorkItems associated with this certification.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST,xmlname="WorkItemRefs")
    public List<WorkItem> getWorkItems() {
        return _workItems;
    }

    public void setWorkItems(List<WorkItem> items) {
        _workItems = items;
    }

    public void addWorkItem(WorkItem item) {
        if (null == _workItems) {
            _workItems = new ArrayList<WorkItem>();
        }
        _workItems.add(item);
    }

    public void removeWorkItem(WorkItem item) {
        if (null != _workItems) {
            _workItems.remove(item);
        }
    }

    /**
     * @return Expiration date (due date) for this certification.
     */
    public Date getExpiration() {
        return _expiration;
    }

    /**
     * @param expiration Expiration date (due date) for this certification.
     */
    @XMLProperty
    public void setExpiration(Date expiration) {
        _expiration = expiration;
    }

    /**
     * @return Automatic closing date for this certification.
     */
    public Date getAutomaticClosingDate() {
        return _automaticClosingDate;
    }

    /**
     * @param automaticClosingDate Automatic closing date for this certification.
     */
    @XMLProperty
    public void setAutomaticClosingDate(Date automaticClosingDate) {
        _automaticClosingDate = automaticClosingDate;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Tag> getTags() {
        return _tags;
    }

    public void setTags(List<Tag> tags) {
        _tags = tags;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<CertificationGroup> getCertificationGroups() {
        return _certificationGroups;
    }

    public void setCertificationGroups(List<CertificationGroup> certificationGroups) {
        this._certificationGroups = certificationGroups;
    }

    public void addCertificationGroup(CertificationGroup group){
        if (_certificationGroups == null)
            _certificationGroups = new ArrayList<CertificationGroup>();
        _certificationGroups.add(group);
    }

    public void addCertificationGroups(List<CertificationGroup> groups){
        if (groups != null){
            for(CertificationGroup group : groups){
                addCertificationGroup(group);
            }
        }
    }

    public List<CertificationGroup> getCertificationGroupsByType(CertificationGroup.Type type){
        List<CertificationGroup> groups = new ArrayList<CertificationGroup>();
        if (_certificationGroups != null && type != null){
            for(CertificationGroup group : _certificationGroups){
                if (type.equals(group.getType())){
                    groups.add(group);
                }
            }
        }
        return groups;
    }

    /**
     * Returns the name of the Certification this access review belongs to.
     * @return name of the certification group
     */
    public String getCertificationName(){
        List<CertificationGroup> groups = getCertificationGroupsByType(CertificationGroup.Type.Certification);
        if (groups != null && !groups.isEmpty())
            return groups.get(0).getName();
        return null;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public CertificationStatistics getStatistics(){
        return statistics;
    }

     public void setStatistics(CertificationStatistics statistics){
        this.statistics =  statistics;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    public void resetStatistics() {
        statistics.reset();
    }

    /**
     * Calculate the item and entity percent complete and store them on this
     * certification.
     */
    public void savePercentComplete() {
         statistics.savePercentComplete();
    }

    public void incCertifiedEntities() {
       statistics.incCertifiedEntities();
    }

    public void decCertifiedEntities() {
        statistics.decCertifiedEntities();
    }

    public void incCertificationRequiredEntities() {
        statistics.incCertificationRequiredEntities();;
    }

    public void decCertificationRequiredEntities() {
        statistics.decCertificationRequiredEntities();
    }

    public void incOverdueEntities() {
        statistics.incOverdueEntities();
    }

    public void decOverdueEntities() {
        statistics.decOverdueEntities();
    }

    public void incCertifiedItems() {
        statistics.incCertifiedItems();
    }

    public void decCertifiedItems() {
        statistics.decCertifiedItems();
    }

    public void incCertificationRequiredItems() {
        statistics.incCertificationRequiredItems();
    }

    public void decCertificationRequiredItems() {
        statistics.decCertificationRequiredItems();
    }

    public void incOverdueItems() {
        statistics.incOverdueItems();
    }

    public void decOverdueItems() {
        statistics.decOverdueItems();
    }
    /**
     * Return whether final sign off has occurred for this certification. This
     * is true if there is a non-null sign off date. This can return false if
     * a certifier has signed off on their certification, but it is still being
     * processed for sign off approval (see the _approvalRule).
     *
     * @ignore
     * Note that this is named hasBeenSigned() rather than isSigned() so the
     * XML annotation serializer doesn't get confused about the getter.
     */
    public boolean hasBeenSigned() {
        return (_signed != null);
    }

    public int getRemainingEntities() {
        return statistics.getTotalEntities() - statistics.getCompletedEntities();
    }


    /**
     * Get the percentage of remediations completed, or 0 if none have been
     * kicked off.
     *
     * @return The percentage of remediations completed, or 0 if none have been
     *         kicked off.
     */
    public float getRemediationPercentComplete()
    {
        if (0 == statistics.getRemediationsKickedOff())
            return 0f;

        return ((float) statistics.getRemediationsCompleted() / statistics.getRemediationsKickedOff()) * 100;
    }

    /**
     * @return True if the certifications was completed before the expiration date. If the
     *  certification has not been signed, true if returned if the expiration date has not passed.
     */
    public boolean isOnTime(){
        if (getExpiration() == null){
            return true;
        }else if (hasBeenSigned()){
            return getSigned().before(getExpiration());
        }else{ // signed == null
            return getExpiration().after(new Date());
        }
    }

    /**
     * @return True if the expiration date has passed. If expiration date is null
     *  returns false.
     */
    public boolean isExpired(){
        if (getExpiration() == null){
            return false;
        }else{
            return getExpiration().before(new Date());
        }
    }

    /**
     * Return whether this certification uses rolling phases or not. If true,
     * each item in the certification progresses through the phases
     * independently. If false, the entire certification progresses through
     * the phases at a timed interval.
     *
     * @return True if this certification uses rolling phases, false otherwise.
     */
    public boolean isUseRollingPhases() {

        // Rolling phases are always used for continuous certs.  They are also
        // used for periodic certs if we process remediations immediately and
        // the challenge or remediation phase is enabled.
        boolean eitherPhaseEnabled =
            isPhaseEnabled(Phase.Challenge) || isPhaseEnabled(Phase.Remediation);

        return _continuous || (_processRevokesImmediately && eitherPhaseEnabled);
    }

    /**
     * Get the CertificationPhaseConfig for the given phase.
     */
    public CertificationPhaseConfig getPhaseConfig(Phase phase) {

        return CertificationStateConfig.getStateConfig(phase, _phaseConfig);
    }

    /**
     * Return whether the given phase is enabled according to the phase config.
     */
    public boolean isPhaseEnabled(Phase phase) {

        return CertificationStateConfig.isEnabled(phase, _phaseConfig);
    }

    /**
     * Convenience method to check if the remediation phase is enabled. This is
     * here so that JSF pages can figure this out easily.
     */
    public boolean isRemediationPhaseEnabled() {
        return this.isPhaseEnabled(Certification.Phase.Remediation);
    }

    /**
     * Return whether this certification is on or past the the remediation
     * phase. This is here so that JSF pages can figure this out easily.
     */
    public boolean isOnOrPastRemediationPhase() {
        boolean onOrPast = false;

        if (null != _phase) {
            onOrPast = (Certification.Phase.Remediation.compareTo(_phase) <= 0);
        }

        return onOrPast;
    }

    /**
     * Get the next enabled Phase (or End if there are no more phases). If the
     * phase is currently null, it is assumed that it is transitioning to the
     * Active or Staged (if enabled).
     *
     * @return The next enabled Phase, or End if there are no more phases.
     */
    public Phase getNextPhase() {
        return CertificationStateConfig.getNextState(_phaseConfig, _phase, getStartPhase(), Phase.End);
    }
    
    /**
     * Gets the starting phase for this certification.
     * @return The starting phase.
     */
    private Phase getStartPhase() {
        for (CertificationPhaseConfig config : Util.safeIterable(_phaseConfig)) {
            if (config.isEnabled() && Phase.Staged.equals(config.getPhase())) {
                return Phase.Staged;
            }
        }  
        
        return Phase.Active;        
    }

    /**
     * Get the previous enabled Phase (or Active (or Staged) if this is the first phase).
     *
     * @return The previous enabled Phase, or Active (or Staged) if this is the first phase.
     */
    public Phase getPreviousPhase() {
        return CertificationStateConfig.getPreviousState(_phaseConfig, _phase);
    }

    /**
     * Required by the Phaseable interface.
     */
    public Certification getCertification() {
        return this;
    }

    /**
     * Calculate the end date for the given phase.
     */
    public Date calculatePhaseEndDate(Phase phase) {
        return CertificationStateConfig.calculateStateEndDate(phase, _phaseConfig, _activated);
    }

    /**
     * The startDate was not being used within the method.
     * Retaining the original signature out of paranoia.
     * @deprecated use {@link #calculateExpirationDate()}
     */
    @Deprecated
    public Date calculateExpirationDate(Date startDate) {
        return calculateExpirationDate();
    }

    /**
     * Calculate the expiration date of this certification based on the duration
     * settings in the state config. This returns an expiration that coincides
     * with the end of the challenge phase (or active phase if the challenge
     * phase is not enabled); returns null for a continuous certification since
     * these have no expiration. Null will also be returned if the certification
     * has not been activated.
     *
     * @return The expiration date of this certification.
     */
    public Date calculateExpirationDate() {

        Date expiration = null;

        if (!_continuous && _activated != null) {
            expiration = calculatePhaseEndDate(Phase.Challenge);
        }

        return expiration;
    }

    /**
     * Calculate the automatic closing date of this certification based on the
     * certification's expiration date and the automatic closing Duration in the certification
     * definition. This returns null for a continuous certification since these
     * cannot be closed automatically.
     *
     * @return The automatic closing date for this certification if configured;
     *         null otherwise.
     * @throws GeneralException
     */
    public Date calculateAutomaticClosingDate(Resolver resolver) throws GeneralException {

        Date autoCloseDate = null;

        if (!_continuous) {
            CertificationDefinition def = getCertificationDefinition(resolver);
            if (null == def) {
                // wow, serious problem if no cert def?
                if (log.isWarnEnabled())
                    log.warn("No certification definition found for cert: " + this.getId());
                
                return null;
            }

            // nothing to do if auto close is not enabled
            if (!def.isAutomaticClosingEnabled())
                return null;

            // we should already have an expiration date, but just in case
            if (null == getExpiration())
                setExpiration(calculateExpirationDate());
            
            // The certification is likely staged..
            if (null == getExpiration())
            	return null;

            // now calculate the auto close date, based on the cert def.  Use the
            // default Duration constructor b/c the auto close interval may be
            // zero if the cert is configured to auto close on its expiration date.
            Duration autoCloseDuration = new Duration();
            autoCloseDuration.setAmount(def.getAutomaticClosingInterval());
            autoCloseDuration.setScale(def.getAutomaticClosingIntervalScale());

            autoCloseDate = autoCloseDuration.addTo(getExpiration());
        }

        return autoCloseDate;
    }


    /**
     * Setup notifications on the given work item using the expiration and
     * notification configuration from this certification.
     *
     * @param  resolver  The Resolver to use.
     * @param  item      The WorkItem on which to setup notifications.
     */
    public void setupWorkItemNotifications(Resolver resolver, WorkItem item)
        throws GeneralException {

        NotificationConfig notifConfig = null;
        CertificationPhaseConfig phaseConfig =
            this.getPhaseConfig(Certification.Phase.Active);
        if (null != phaseConfig) {
            notifConfig = phaseConfig.getNotificationConfig();
        }

        // Setup reminders and escalations for periodic certs.
        // For continuous certs, this is handled per-item.
        // Only set this up if we have not yet set it (for
        // reassignments this should have already been set).
        if (!this.isContinuous() && !item.hasLifecycleInfo()) {
            item.setupNotificationConfig(resolver, this.getExpiration(),
                                         notifConfig);
        }
    }

    /**
     * Return the ContinuousStateConfig for the given continuous state.
     */
    public ContinuousStateConfig getContinuousConfig(ContinuousState state) {
        return CertificationStateConfig.getStateConfig(state, _continuousConfig);
    }

    /**
     * Return the ContinuousStateConfig for the continuous state that comes
     * before the given state.
     */
    public ContinuousState getPreviousContinuousState(ContinuousState current) {
        return CertificationStateConfig.getPreviousState(_continuousConfig, current);
    }

    /**
     * Given the start date of the given state, return when the item will be
     * overdue according to the durations in the continuous config.
     */
    public Date getOverdueDate(ContinuousState startState, Date start) {
        Date overdue = null;
        if (null != start) {
            overdue =
                CertificationStateConfig.calculateStateEndDate(startState, ContinuousState.Overdue,
                                                               _continuousConfig, start);
        }
        return overdue;
    }


    /**
     * Search the certification for the Entity with the given id or name.
     *
     * @param  idOrName  The id or name of the Entity for which to find the
     *             CertificationEntity.
     *
     * @ignore
     * TODO: We're only storing the name right now, probably be storing
     * the unique id so we can handle renames.
     *
     * todo currently this method is being passed both the id and name
     * in two separate cases. Should probly break this into two separate
     * methods and adjust the client calls
     */
    public CertificationEntity getEntity(String idOrName) {

        if (_entities != null && idOrName != null) {
            for (CertificationEntity entity : _entities) {
                if (idOrName.equals(entity.getId())){
                    return entity;
                }else if (idOrName.equals(entity.getIdentity())){
                    return entity;
                }else if (idOrName.equals(entity.getAccountGroup())){
                    return entity;
                }
            }
        }
        return null;
    }

    /**
     * Searches for a certification entity. First it uses the entity's id. If it
     * cannot find that it will search on the entity's name.
     *
     * @param entity  Entity to search for
     * @return CertificationEntity if found, otherwise null.
     */
    public CertificationEntity getEntity(AbstractCertifiableEntity entity) {

        CertificationEntity found = getEntity(entity.getId());

        if (found != null)
            return found;

         if (_entities != null && entity != null && entity.getName() != null) {
            for (CertificationEntity curEntity : _entities) {
                if (entity instanceof Identity &&
                        entity.getName().equals(curEntity.getIdentity())){
                    return curEntity;
                } else if (entity instanceof ManagedAttribute &&
                        entity.getName().equals(curEntity.getAccountGroup())){
                    return curEntity;
                }
            }
        }

        return null;
    }

    /**
     * Traverse through this Certification to find a CertificationItem with the
     * given ID.
     *
     * @param  itemId  The ID of the CertificationItem to find.
     *
     * @return The CertificationItem in this Certification with the given ID, or
     *         null if a CertificationItem with the given ID does not exist in
     *         this certification.
     * @deprecated Use projection query to find CertificationItem
     *             
     *             There are potentially many items here. If there is an ID already
     *             it is better to query for the item directly via a Filter than to 
     *             iterate over all the items in a Certification to get at the one.
     */
    @Deprecated
    public CertificationItem getItem(String itemId) {
        if (null != _entities) {
            for (CertificationEntity entity : _entities) {
                CertificationItem found = entity.getItem(itemId);
                if (null != found) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Traverse through this Certification to find a child Certification with
     * the given ID.
     *
     * @param  certId  The ID of the Certification to find.
     *
     * @return The child Certification in this Certification with the given ID
     *         or null if a Certification with the given ID does not exist in
     *         this certification.
     */
    public Certification getCertification(String certId) {
        if (null != certId) {
            if (certId.equals(_id)) {
                return this;
            }

            if (null != _certifications) {
                for (Certification cert : _certifications) {
                    Certification found = cert.getCertification(certId);
                    if (null != found) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Return whether any of the direct child certifications are bulk
     * reassignments.
     *
     * @return True if any of the direct child certification are bulk
     *         reassignments, false otherwise.
     */
    public boolean hasBulkReassignments() {

        if (null != _certifications) {
            for (Certification cert : _certifications) {
                if (cert.isBulkReassignment()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Bulk re-assign the given list of items.
     *
     * @param  items        The AbstractCertificationItems to reassign.
     * @param  recipient    The delegate.
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     */
    public void bulkReassign(Identity requester,
                             List<AbstractCertificationItem> items,
                             Identity recipient, String description,
                             String comments, Configuration sysConfig)
        throws GeneralException {
        this.bulkReassign(requester, items, recipient, null, description, comments, sysConfig);
    }

    /**
     * Bulk re-assign the given list of items.
     *
     * @param  items        The AbstractCertificationItems to reassign.
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate.
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     */
    public void bulkReassign(Identity requester,
                             List<AbstractCertificationItem> items,
                             Identity recipient, String certName,
                             String description, String comments,
                             Configuration sysConfig)
         throws GeneralException
    
    {
        this.bulkReassign(requester, items, recipient, certName, description, comments, sysConfig, true);
    }

    /**
     * Bulk re-assign the given list of items.
     *
     * @param  items        The AbstractCertificationItems to reassign.
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate.
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @param  checkSelfCertify false to skip check for self certify, if caller has already done it.
     */
    public void bulkReassign(Identity requester,
                             List<AbstractCertificationItem> items,
                             Identity recipient, String certName,
                             String description, String comments,
                             Configuration sysConfig,
                             boolean checkSelfCertify)
         throws GeneralException
    {
        this.bulkReassign(requester, items, recipient, certName, description, comments, sysConfig, checkSelfCertify, true);
    }

    /**
     * 
     * Bulk re-assign the given list of items.
     *
     * @param  items        The AbstractCertificationItems to reassign.
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate.
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @param  sysConfig    Configuration object
     * @param  checkSelfCertify false to skip check for self certify, if caller has already done it.
     * @param  checkLimitReassignment Check if Limit Reassignments is enabled.
     * @throws GeneralException
     */
    public void bulkReassign(Identity requester,
            List<AbstractCertificationItem> items,
            Identity recipient, String certName,
            String description, String comments,
            Configuration sysConfig, boolean checkSelfCertify,
            boolean checkLimitReassignment)
        throws GeneralException {

        // Make sure the caller gave us a non-null recipient
        if (recipient == null) {
            throw new GeneralException("Certification reassignment recipient cannot be null!");
        }

        List<String> certificationItemIds = new ArrayList<String>();
        List<String> certificationEntityIds = new ArrayList<String>();
        CertificationCommand tempCommand = null;
        for (AbstractCertificationItem item : items) {

            // If there is no ID, this item has not been persisted yet.
            // Store a command with them to be flushed later after items are committed
            if (item.getId() == null) {
                if (tempCommand == null) {
                    tempCommand = new CertificationCommand.BulkReassignment(
                            requester, null, null, recipient, certName, description, comments, checkSelfCertify, checkLimitReassignment);
                    if (_unpersistedCommands == null) {
                        _unpersistedCommands = new ArrayList<CertificationCommand>();
                    }
                    _unpersistedCommands.add(tempCommand);
                }
                tempCommand.addUnpersistedItem(item);
            } else {
                //Separate the items from the entities. 
                if (item instanceof CertificationItem) {
                    certificationItemIds.add(item.getId());
                } else if (item instanceof CertificationEntity) {
                    certificationEntityIds.add(item.getId());
                } else {
                    throw new GeneralException("Unknown type: " + item.getClass());
                }
            }
        }
        
        if (certificationItemIds.size() > 0) {
            this.bulkReassignItems(requester, certificationItemIds, recipient, certName, 
                    description, comments, checkSelfCertify, checkLimitReassignment);
        }
        if (certificationEntityIds.size() > 0) {
            this.bulkReassignEntities(requester, certificationEntityIds, recipient, certName, 
                    description, comments, checkSelfCertify, checkLimitReassignment);
        }
    }

    /**
     * Bulk reassign the given entities referred to by the given list of IDs
     * @param  requester    The requester
     * @param  entityIds    The IDs of the entities to reassign 
     * @param  recipient    The delegate.
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @throws GeneralException
     */
    public void bulkReassignEntities(Identity requester,
                             List<String> entityIds,
                             Identity recipient, String description,
                             String comments)
            throws GeneralException {
        this.bulkReassignEntities(requester, entityIds, recipient, null, description, comments, true);
    }

    /**
     * Bulk reassign the given entities referred to by the given list of IDs
     * @param  requester    The requester
     * @param  entityIds    The IDs of the entities to reassign 
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @throws GeneralException
     */
    public void bulkReassignEntities(Identity requester,
                                     List<String> entityIds,
                                     Identity recipient, String certName,
                                     String description, String comments) 
    throws GeneralException {
        this.bulkReassignEntities(requester, entityIds, recipient, certName, description, comments, true);
    }

    /**
     * Bulk reassign the given entities referred to by the given list of IDs
     * @param  requester    The requester
     * @param  entityIds    The IDs of the entities to reassign 
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @param  checkSelfCertify false to skip check for self certify, if caller has already done it.
     * @throws GeneralException
     */
    public void bulkReassignEntities(Identity requester,
                             List<String> entityIds, 
                             Identity recipient, String certName,
                             String description, String comments,
                             boolean checkSelfCertify) 
    throws GeneralException {
        this.bulkReassignEntities(requester, entityIds, recipient, certName, description, comments, checkSelfCertify, true);
    }

    /**
     * Bulk reassign the given entities referred to by the given list of IDs
     * @param  requester    The requester
     * @param  entityIds    The IDs of the entities to reassign 
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @param  checkSelfCertify false to skip check for self certify, if caller has already done it.
     * @param  checkLimitReassignment false to skip check for limiting reassignments, if caller has already done it.
     * @throws GeneralException
     */
    public void bulkReassignEntities(Identity requester,
                             List<String> entityIds, 
                             Identity recipient, String certName,
                             String description, String comments,
                             boolean checkSelfCertify, boolean checkLimitReassignment) 
    throws GeneralException {
        this.bulkReassignInner(requester, CertificationEntity.class, entityIds, recipient, certName, description, comments, checkSelfCertify, checkLimitReassignment);
    }

    /**
     * Bulk reassign the items referred to by the given list of IDs
     * @param  requester    The requester
     * @param  itemIds      The IDs of the items to reassign 
     * @param  recipient    The delegate.
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @throws GeneralException
     */
    public void bulkReassignItems(Identity requester,
                                     List<String> itemIds,
                                     Identity recipient, String description,
                                     String comments)
            throws GeneralException {
        this.bulkReassignItems(requester, itemIds, recipient, null, description, comments, true);
    }

    /**
     * Bulk reassign the items referred to by the given list of IDs
     * @param  requester    The requester
     * @param  itemIds      The IDs of the items to reassign 
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @throws GeneralException
     */
    public void bulkReassignItems(Identity requester,
                                     List<String> itemIds,
                                     Identity recipient, String certName,
                                     String description, String comments) 
    throws GeneralException {
        this.bulkReassignItems(requester, itemIds, recipient, certName, description, comments, true);
    }

    /**
     * Bulk reassign the items referred to by the given list of IDs
     * @param  requester    The requester
     * @param  itemIds      The IDs of the items to reassign 
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @param  checkSelfCertify false to skip check for self certify, if caller has already done it.
     * @throws GeneralException
     */
    public void bulkReassignItems(Identity requester,
                                     List<String> itemIds,
                                     Identity recipient, String certName,
                                     String description, String comments,
                                     boolean checkSelfCertify) 
    throws GeneralException {
        this.bulkReassignItems(requester, itemIds, recipient, certName, description, comments, checkSelfCertify, true);
    }

    /**
     * Bulk reassign the items referred to by the given list of IDs
     * @param  requester    The requester
     * @param  itemIds      The IDs of the items to reassign 
     * @param  recipient    The delegate.
     * @param  certName     The name of the certification to generate
     * @param  description  Descriptive text about the delegation.
     * @param  comments     Comments about the delegation.
     * @param  checkSelfCertify false to skip check for self certify, if caller has already done it.
     * @param  checkLimitReassignment Check if Limit Reassignments is enabled.
     * @throws GeneralException
     */
    public void bulkReassignItems(Identity requester,
                                     List<String> itemIds,
                                     Identity recipient, String certName,
                                     String description, String comments,
                                     boolean checkSelfCertify, boolean checkLimitReassignment) 
    throws GeneralException {
        this.bulkReassignInner(requester, CertificationItem.class, itemIds, recipient, certName, description, comments, checkSelfCertify, checkLimitReassignment);
    }
    
    private void bulkReassignInner(Identity requester,
                                 Class itemClass, List<String> itemIds,
                                 Identity recipient, String certName,
                                 String description, String comments,
                                 boolean checkSelfCertify, boolean checkLimitReassignment) 
    throws GeneralException {

        // Make sure the caller gave us a non-null recipient
        if (recipient == null) {
            throw new GeneralException("Certification reassignment recipient cannot be null!");
        }
        
        mergeCommand(new CertificationCommand.BulkReassignment(requester, itemClass, itemIds,
                recipient, certName, description, comments, checkSelfCertify, checkLimitReassignment));

    }

    /**
     * After the certification entities have been persisted, flush any commands 
     * from the unpersistedCommands field. This is required because an attempt might be made to try 
     * to reassign entities before they are persisted and have an ID.
     *
     * @throws GeneralException If any of the entities in the commands 
     *                          don't yet have an ID.
     */
    public void flushUnpersistedCommands() throws GeneralException {
        if ((null != _unpersistedCommands) && !_unpersistedCommands.isEmpty()) {
            
            for (CertificationCommand command: _unpersistedCommands) {
                command.flushUnpersistedItems();
                mergeCommand(command);
            }
            
            _unpersistedCommands.clear();
            _unpersistedCommands = null;
        }
    }

    /**
     * Find the specified CertificationItem or CertificationEntity that is stored in the
     * unpersisted list of a certificationCommand.
     * @param item The CertificationItem or CertificationEntity you seek.
     * @return the item if found, null otherwise.
     */
    public AbstractCertificationItem findUnpersistedItemInCommands(AbstractCertificationItem item) {
        if (null != item) {
            if ((null != _unpersistedCommands) && !_unpersistedCommands.isEmpty()) {
                for (CertificationCommand command: _unpersistedCommands) {
                    AbstractCertificationItem found  = command.findUnpersistedItem(item);
                    if (null != found) {
                        return found;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Find the CertificationCommand that holds the CertificationItem or CertificationEntity
     * that is unpersisted in any of the CertificationCommands.
     * @param item The CertificationItem or CertificationEntity contained by the CertificationCommand you seek.
     * @return the CertificationCommand that envelops the unpersisted CertificationEntity or CertificationItem.
     */
    public CertificationCommand findCommandForUnpersistedItem(AbstractCertificationItem item) {
        if (null != item) {
            if ((null != _unpersistedCommands) && !_unpersistedCommands.isEmpty()) {
                for (CertificationCommand command: _unpersistedCommands) {
                    AbstractCertificationItem found  = command.findUnpersistedItem(item);
                    if (null != found) {
                        return command;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Gets a list of allowed AbstractCertificationItem.Status for this cert type.
     *
     * @return Non-null List of allowed AbstractCertificationItem.Status.
     */
    public List<AbstractCertificationItem.Status> getAllowedStatuses(){

        List<AbstractCertificationItem.Status> list = new ArrayList<AbstractCertificationItem.Status>();

        for (AbstractCertificationItem.Status status : AbstractCertificationItem.Status.values())
        {
            if (getType() != null && getType().isAllowedEntityStatus(status))
                list.add(status);
        }

        return list;
    }

    /**
     * Mark this certification and all child certifications to store their
     * children inlined rather than as a reference.
     */
    public void inlineChildCertifications() {
        _inlineChildCertifications = true;
        if (null != _certifications) {
            for (Certification cert : _certifications) {
                cert.inlineChildCertifications();
            }
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
        .append("id", getId())
        .append("name", getName())
        .toString();
    }

    /**
     * Hack for the unit tests. Some tests serialize a
     * Certification to XML and compare to a test file.
     * Unfortunately the order of entities is not reliable
     * so a way is needed to put them into a stable order.
     * You obviously do not want to call this for a big cert.
     */
    public void sort() {
        sort(false, false);
    }

    /**
     * This will return true if the cert is locked and actions can
     * be taken on the certification, for example, it has not been signed. 
     * In this case certain actions might be taken like showing warnings. If the certification is not actionable
     * then there is no need to show warnings.
     * 
     * @param context SailPointContext
     * @param id The certification id.
     * @return true if the certification is locked and action can be taken
     * @throws GeneralException
     */
    public static boolean isLockedAndActionable(SailPointContext context, String id) throws GeneralException {

        boolean lockedAndActionable = false;
        if (ObjectUtil.isLockedById(context, Certification.class, id)) {
            Certification cert = context.getObjectById(Certification.class, id);
            if (cert != null && cert.getSigned() == null) {
                lockedAndActionable = true;
            }
        }
        return lockedAndActionable;
    }
    
    /**
     * See note in sort() for explanation. If useComparable is true,
     * the Comparable interface is used when sorting CertificationItems
     * rather than the Comparator returned by getSortComparator. This
     * has a bit more specific ordering with regard to entitlements. This will
     * not use a secondarySort.
     */
    public void sort(boolean useComparable) {
        sort(useComparable, false);
    }
    
    /**
     * See note in sort() for explanation. If secondarySort is true, will sort based on
     * CertificationItem.exceptionAttributeValue if the primarySortKey returns equality
     * with another CertificationItem.
     * @param useComparable uses default Collections.sort if true. False will use the custom
     * comparator in CertificationItem.getSortComparator() to sort CertificationItems.
     * @param secondarySort option passed to the CertificationEntity to sort CertificationItems.
     * @see #sort()
     */
    public void sort(boolean useComparable, boolean secondarySort) {
        if (_entities != null) {
            // first sort entities by identity name
            Collections.sort(_entities, CertificationEntity.getSortComparator());
            // then sort the entity items
            for (CertificationEntity ent : _entities)
                ent.sort(useComparable, secondarySort);
        }
    }

    @XMLClass
    public static class CertificationStatistics{

         /**
         * The total number of certification identities in this report.
         */
        private int totalEntities;

        private int excludedEntities;

        /**
         * The number of entities that have been completed.
         * Do we need to break this down by approved/mitigated/remediated?
         */
        private int completedEntities;

        /**
         * The number of entities that have one or more items that
         * have been delegated.
         */
        private int delegatedEntities;

        /**
         * The percentage of completion (number of completed certification
         * identities compared to the number of total certification identities) for this
         * certification.
         */
        private int percentComplete;

        // Entity-based continuous stats
        private int certifiedEntities;
        private int certificationRequiredEntities;
        private int overdueEntities;

        // Item-based stats
        private int totalItems;
        private int excludedItems;
        private int completedItems;
        private int delegatedItems;
        private int itemPercentComplete;

        // Item-based continuous stats
        private int certifiedItems;
        private int certificationRequiredItems;
        private int overdueItems;

        /**
         * The total number of items in this certification for which remediation
         * requests been launched. See
         * {@link CertificationAction#remediationKickedOff}.
         */
        private int remediationsKickedOff;

        /**
         * The number of remediations requests that have been completed in this
         * certification. See {@link CertificationAction#remediationCompleted}.
         */
        private int remediationsCompleted;

        private int totalViolations;
        private int violationsAllowed;
        private int violationsRemediated;
        private int violationsAcknowledged;

        private int totalRoles;
        private int rolesApproved;
        private int rolesAllowed;
        private int rolesRemediated;

        private int totalExceptions;
        private int exceptionsApproved;
        private int exceptionsAllowed;
        private int exceptionsRemediated;

        private int totalAccounts;
        private int accountsApproved;
        private int accountsAllowed;
        private int accountsRemediated;

        private int totalAccountGroupPermissions;
        private int accountGroupPermissionsApproved;
        private int accountGroupPermissionsRemediated;

        private int totalAccountGroupMemberships;
        private int accountGroupMembershipsApproved;
        private int accountGroupMembershipsRemediated;

        private int totalProfiles;
        private int profilesApproved;
        private int profilesRemediated;

        private int totalScopes;
        private int scopesApproved;
        private int scopesRemediated;

        private int totalCapabilities;
        private int capabilitiesApproved;
        private int capabilitiesRemediated;

        private int totalPermits;
        private int permitsApproved;
        private int permitsRemediated;

        private int totalRequirements;
        private int requirementsApproved;
        private int requirementsRemediated;

        private int totalRoleHierarchies;
        private int roleHierarchiesApproved;
        private int roleHierarchiesRemediated;

        public void reset() {
            totalEntities = 0;
            excludedEntities = 0;
            completedEntities = 0;
            delegatedEntities = 0;
            percentComplete = 0;
            certifiedEntities = 0;
            certificationRequiredEntities = 0;
            overdueEntities = 0;
            totalItems = 0;
            excludedItems = 0;
            completedItems = 0;
            delegatedItems = 0;
            itemPercentComplete = 0;
            certifiedItems = 0;
            certificationRequiredItems = 0;
            overdueItems = 0;
            remediationsKickedOff = 0;
            remediationsCompleted = 0;
            totalViolations = 0;
            violationsAllowed = 0;
            violationsRemediated = 0;
            violationsAcknowledged = 0;
            totalRoles = 0;
            rolesApproved = 0;
            rolesAllowed = 0;
            rolesRemediated = 0;
            totalExceptions = 0;
            exceptionsApproved = 0;
            exceptionsAllowed = 0;
            exceptionsRemediated = 0;
            totalAccountGroupPermissions = 0;
            accountGroupPermissionsApproved = 0;
            accountGroupPermissionsRemediated = 0;
            totalAccountGroupMemberships = 0;
            accountGroupMembershipsApproved = 0;
            accountGroupMembershipsRemediated = 0;
            totalProfiles = 0;
            profilesApproved = 0;
            profilesRemediated = 0;
            totalScopes = 0;
            scopesApproved = 0;
            scopesRemediated = 0;
            totalCapabilities = 0;
            capabilitiesApproved = 0;
            capabilitiesRemediated = 0;
            totalPermits = 0;
            permitsApproved = 0;
            permitsRemediated = 0;
            totalRequirements = 0;
            requirementsApproved = 0;
            requirementsRemediated = 0;
            totalRoleHierarchies = 0;
            roleHierarchiesApproved = 0;
            roleHierarchiesRemediated = 0;
            this.accountsAllowed=0;
            this.accountsApproved=0;
            this.accountsRemediated=0;
            this.totalAccounts=0;
        }

        public int getOpenEntities()
        {
            return totalEntities - delegatedEntities - completedEntities;
        }

        public int getOpenItems()
        {
            return totalItems - delegatedItems - completedItems;
        }

        /**
         * Calculate the item and entity percent complete and store them on this
         * certification.
         */
        public void savePercentComplete() {
            percentComplete = CertificationStatCounter.calculatePercentComplete(completedEntities, totalEntities);
            itemPercentComplete = CertificationStatCounter.calculatePercentComplete(completedItems, totalItems);
        }

        public void incrementDecisionCount(Certification.Type certType, CertificationItem.Type type,
                             CertificationAction.Status status, int count){



            switch(type){
                case PolicyViolation:
                    setTotalViolations(getTotalViolations() + count);
                    if (CertificationAction.Status.Mitigated.equals(status)){
                        setViolationsAllowed(getViolationsAllowed() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setViolationsRemediated(getViolationsRemediated() + count);
                    } else if (CertificationAction.Status.Acknowledged.equals(status)){
                        setViolationsAcknowledged(getViolationsAcknowledged() + count);
                    }
                    break;
                case Bundle:
                    setTotalRoles(getTotalRoles() + count);
                    if (CertificationAction.Status.Approved.equals(status)){
                        setRolesApproved(getRolesApproved() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setRolesRemediated(getRolesRemediated() + count);
                    }  else if (CertificationAction.Status.Mitigated.equals(status)){
                        setRolesAllowed(getRolesAllowed() + count);
                    }
                    break;
                case BusinessRoleHierarchy:
                    setTotalRoleHierarchies(getTotalRoleHierarchies() + count);
                    if (CertificationAction.Status.Approved.equals(status)){
                        setRoleHierarchiesApproved(getRoleHierarchiesApproved() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setRoleHierarchiesRemediated(getRoleHierarchiesRemediated() + count);
                    }
                    break;
                case BusinessRolePermit:
                    setTotalPermits(getTotalPermits() + count);
                    if (CertificationAction.Status.Approved.equals(status)){
                        setPermitsApproved(getPermitsApproved() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setPermitsRemediated(getPermitsRemediated() + count);
                    }
                    break;
                case BusinessRoleRequirement:
                    setTotalRequirements(getTotalRequirements() + count);
                    if (CertificationAction.Status.Approved.equals(status)){
                        setRequirementsApproved(getRequirementsApproved() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setRequirementsRemediated(getRequirementsRemediated() + count);
                    }
                    break;
                case BusinessRoleGrantedCapability:
                    setTotalCapabilities(getTotalCapabilities() + count);
                    if (CertificationAction.Status.Approved.equals(status)){
                        setCapabilitiesApproved(getCapabilitiesApproved() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setCapabilitiesRemediated(getCapabilitiesRemediated() + count);
                    }
                    break;
                case BusinessRoleGrantedScope:
                    setTotalScopes(getTotalScopes() + count);
                    if (CertificationAction.Status.Approved.equals(status)){
                        setScopesApproved(getScopesApproved() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setScopesRemediated(getScopesRemediated() + count);
                    }
                    break;
                case BusinessRoleProfile:
                   setTotalProfiles(getTotalProfiles() + count);
                   if (CertificationAction.Status.Approved.equals(status)){
                       setProfilesApproved(getProfilesApproved() + count);
                   } else if (CertificationAction.Status.Remediated.equals(status)){
                       setProfilesRemediated(getProfilesRemediated() + count);
                   }
                   break;
                case AccountGroupMembership:
                   setTotalAccountGroupMemberships(getTotalAccountGroupMemberships() + count);
                   if (CertificationAction.Status.Approved.equals(status)){
                       setAccountGroupMembershipsApproved(getAccountGroupMembershipsApproved() + count);
                   } else if (CertificationAction.Status.Remediated.equals(status)){
                       setAccountGroupMembershipsRemediated(getAccountGroupMembershipsRemediated() + count);
                   }
                   break;
                case Account:
                    setTotalAccounts(getTotalAccounts() + count);
                    if (CertificationAction.Status.Approved.equals(status)){
                        setAccountsApproved(getAccountsApproved() + count);
                    } else if (CertificationAction.Status.Remediated.equals(status)){
                        setAccountsRemediated(getAccountsRemediated() + count);
                    }else if (CertificationAction.Status.Mitigated.equals(status)){
                        setAccountsAllowed(getAccountsAllowed() + count);
                    }
                    break;
                case DataOwner:   // Same as exception
                case Exception:
                    if (Certification.Type.AccountGroupPermissions.equals(certType)){
                        setTotalAccountGroupPermissions(getTotalAccountGroupPermissions() + count);
                        if (CertificationAction.Status.Approved.equals(status)){
                            setAccountGroupPermissionsApproved(getAccountGroupPermissionsApproved() + count);
                        } else if (CertificationAction.Status.Remediated.equals(status)){
                            setAccountGroupPermissionsRemediated(getAccountGroupPermissionsRemediated() + count);
                        }
                    } else {
                        setTotalExceptions(getTotalExceptions() + count);
                        if (CertificationAction.Status.Approved.equals(status)){
                            setExceptionsApproved(getExceptionsApproved() + count);
                        } else if (CertificationAction.Status.Remediated.equals(status)){
                            setExceptionsRemediated(getExceptionsRemediated() + count);
                        }else if (CertificationAction.Status.Mitigated.equals(status)){
                            setExceptionsAllowed(getExceptionsAllowed() + count);
                        }
                    }
                    break;
                default:
            }
        }


        public void incCertifiedEntities() {
            certifiedEntities++;
        }

        public void decCertifiedEntities() {
            certifiedEntities--;
        }

        public void incCertificationRequiredEntities() {
            certificationRequiredEntities++;
        }

        public void decCertificationRequiredEntities() {
            certificationRequiredEntities--;
        }

        public void incOverdueEntities() {
            overdueEntities++;
        }

        public void decOverdueEntities() {
            overdueEntities--;
        }

        public void incCertifiedItems() {
            certifiedItems++;
        }

        public void decCertifiedItems() {
            certifiedItems--;
        }

        public void incCertificationRequiredItems() {
            certificationRequiredItems++;
        }

        public void decCertificationRequiredItems() {
            certificationRequiredItems--;
        }

        public void incOverdueItems() {
            overdueItems++;
        }

        public void decOverdueItems() {
            overdueItems--;
        }

        @XMLProperty
        public int getTotalEntities() {
            return totalEntities;
        }

        public void setTotalEntities(int totalEntities) {
            this.totalEntities = totalEntities;
        }

        @XMLProperty
        public int getCompletedEntities() {
            return completedEntities;
        }

        public void setCompletedEntities(int completedEntities) {
            this.completedEntities = completedEntities;
        }

        @XMLProperty
        public int getDelegatedEntities() {
            return delegatedEntities;
        }

        public void setDelegatedEntities(int delegatedEntities) {
            this.delegatedEntities = delegatedEntities;
        }

        @XMLProperty
        public int getPercentComplete() {
            return percentComplete;
        }

        public void setPercentComplete(int percentComplete) {
            this.percentComplete = percentComplete;
        }

        @XMLProperty
        public int getCertifiedEntities() {
            return certifiedEntities;
        }

        public void setCertifiedEntities(int certifiedEntities) {
            this.certifiedEntities = certifiedEntities;
        }

        @XMLProperty
        public int getCertificationRequiredEntities() {
            return certificationRequiredEntities;
        }

        public void setCertificationRequiredEntities(int certificationRequiredEntities) {
            this.certificationRequiredEntities = certificationRequiredEntities;
        }

        @XMLProperty
        public int getOverdueEntities() {
            return overdueEntities;
        }

        public void setOverdueEntities(int overdueEntities) {
            this.overdueEntities = overdueEntities;
        }

        @XMLProperty
        public int getTotalItems() {
            return totalItems;
        }

        public void setTotalItems(int totalItems) {
            this.totalItems = totalItems;
        }

        @XMLProperty
        public int getCompletedItems() {
            return completedItems;
        }

        public void setCompletedItems(int completedItems) {
            this.completedItems = completedItems;
        }

        @XMLProperty
        public int getDelegatedItems() {
            return delegatedItems;
        }

        public void setDelegatedItems(int delegatedItems) {
            this.delegatedItems = delegatedItems;
        }

        @XMLProperty
        public int getItemPercentComplete() {
            return itemPercentComplete;
        }

        public void setItemPercentComplete(int itemPercentComplete) {
            this.itemPercentComplete = itemPercentComplete;
        }

        @XMLProperty
        public int getCertifiedItems() {
            return certifiedItems;
        }

        public void setCertifiedItems(int certifiedItems) {
            this.certifiedItems = certifiedItems;
        }

        @XMLProperty
        public int getCertificationRequiredItems() {
            return certificationRequiredItems;
        }

        public void setCertificationRequiredItems(int certificationRequiredItems) {
            this.certificationRequiredItems = certificationRequiredItems;
        }

        @XMLProperty
        public int getOverdueItems() {
            return overdueItems;
        }

        public void setOverdueItems(int overdueItems) {
            this.overdueItems = overdueItems;
        }

        @XMLProperty
        public int getRemediationsKickedOff() {
            return remediationsKickedOff;
        }

        public void setRemediationsKickedOff(int remediationsKickedOff) {
            this.remediationsKickedOff = remediationsKickedOff;
        }

        @XMLProperty
        public int getRemediationsCompleted() {
            return remediationsCompleted;
        }

        public void setRemediationsCompleted(int remediationsCompleted) {
            this.remediationsCompleted = remediationsCompleted;
        }

        @XMLProperty
        public int getRoleHierarchiesRemediated() {
            return roleHierarchiesRemediated;
        }

        public void setRoleHierarchiesRemediated(int roleHierarchiesRemediated) {
            this.roleHierarchiesRemediated = roleHierarchiesRemediated;
        }

        @XMLProperty
        public int getTotalViolations() {
            return totalViolations;
        }

        public void setTotalViolations(int totalViolations) {
            this.totalViolations = totalViolations;
        }

        @XMLProperty
        public int getViolationsAllowed() {
            return violationsAllowed;
        }

        public void setViolationsAllowed(int violationsAllowed) {
            this.violationsAllowed = violationsAllowed;
        }

        @XMLProperty
        public int getViolationsRemediated() {
            return violationsRemediated;
        }

        public void setViolationsRemediated(int violationsRemediated) {
            this.violationsRemediated = violationsRemediated;
        }

        @XMLProperty
        public int getViolationsAcknowledged() {
            return violationsAcknowledged;
        }

        public void setViolationsAcknowledged(int violationsAcknowledged) {
            this.violationsAcknowledged = violationsAcknowledged;
        }

        public int getOpenViolations(){
            return totalViolations - violationsAcknowledged - violationsAllowed -
                    violationsRemediated;
        }

        @XMLProperty
        public int getTotalRoles() {
            return totalRoles;
        }

        public void setTotalRoles(int totalRoles) {
            this.totalRoles = totalRoles;
        }

        @XMLProperty
        public int getRolesApproved() {
            return rolesApproved;
        }

        public void setRolesApproved(int rolesApproved) {
            this.rolesApproved = rolesApproved;
        }

        @XMLProperty
        public int getRolesAllowed() {
            return rolesAllowed;
        }

        public void setRolesAllowed(int rolesAllowed) {
            this.rolesAllowed = rolesAllowed;
        }

        @XMLProperty
        public int getRolesRemediated() {
            return rolesRemediated;
        }

        public void setRolesRemediated(int rolesRemediated) {
            this.rolesRemediated = rolesRemediated;
        }

        public int getOpenRoles(){
            return totalRoles - rolesRemediated - rolesApproved - rolesAllowed;
        }

        @XMLProperty
        public int getTotalExceptions() {
            return totalExceptions;
        }

        public void setTotalExceptions(int totalExceptions) {
            this.totalExceptions = totalExceptions;
        }

        @XMLProperty
        public int getExceptionsApproved() {
            return exceptionsApproved;
        }

        public void setExceptionsApproved(int exceptionsApproved) {
            this.exceptionsApproved = exceptionsApproved;
        }

        @XMLProperty
        public int getExceptionsAllowed() {
            return exceptionsAllowed;
        }

        public void setExceptionsAllowed(int exceptionsAllowed) {
            this.exceptionsAllowed = exceptionsAllowed;
        }

        @XMLProperty
        public int getExceptionsRemediated() {
            return exceptionsRemediated;
        }

        public void setExceptionsRemediated(int exceptionsRemediated) {
            this.exceptionsRemediated = exceptionsRemediated;
        }

        public int getOpenExceptions(){
            return totalExceptions - exceptionsRemediated - exceptionsApproved - exceptionsAllowed;
        }

        @XMLProperty
        public int getTotalAccounts() {
            return totalAccounts;
        }

        public void setTotalAccounts(int totalAccounts) {
            this.totalAccounts = totalAccounts;
        }

        @XMLProperty
        public int getAccountsApproved() {
            return accountsApproved;
        }

        public void setAccountsApproved(int accountsApproved) {
            this.accountsApproved = accountsApproved;
        }

        @XMLProperty
        public int getAccountsAllowed() {
            return accountsAllowed;
        }

        public void setAccountsAllowed(int accountsAllowed) {
            this.accountsAllowed = accountsAllowed;
        }

        @XMLProperty
        public int getAccountsRemediated() {
            return accountsRemediated;
        }

        public void setAccountsRemediated(int accountsRemediated) {
            this.accountsRemediated = accountsRemediated;
        }

        public int getOpenAccounts(){
            return totalAccounts - accountsRemediated - accountsApproved - accountsAllowed;
        }

        @XMLProperty
        public int getTotalAccountGroupPermissions() {
            return totalAccountGroupPermissions;
        }

        public void setTotalAccountGroupPermissions(int totalAccountGroupPermissions) {
            this.totalAccountGroupPermissions = totalAccountGroupPermissions;
        }

        @XMLProperty
        public int getAccountGroupPermissionsApproved() {
            return accountGroupPermissionsApproved;
        }

        public void setAccountGroupPermissionsApproved(int accountGroupPermissionsApproved) {
            this.accountGroupPermissionsApproved = accountGroupPermissionsApproved;
        }

        @XMLProperty
        public int getAccountGroupPermissionsRemediated() {
            return accountGroupPermissionsRemediated;
        }

        public void setAccountGroupPermissionsRemediated(int accountGroupPermissionsRemediated) {
            this.accountGroupPermissionsRemediated = accountGroupPermissionsRemediated;
        }

        public int getOpenAccountGroupPermissions(){
            return totalAccountGroupPermissions - accountGroupPermissionsRemediated - accountGroupPermissionsApproved;
        }

        @XMLProperty
        public int getTotalAccountGroupMemberships() {
            return totalAccountGroupMemberships;
        }

        public void setTotalAccountGroupMemberships(int totalAccountGroupMemberships) {
            this.totalAccountGroupMemberships = totalAccountGroupMemberships;
        }

         @XMLProperty
        public int getAccountGroupMembershipsApproved() {
            return accountGroupMembershipsApproved;
        }

        public void setAccountGroupMembershipsApproved(int accountGroupMembershipsApproved) {
            this.accountGroupMembershipsApproved = accountGroupMembershipsApproved;
        }

         @XMLProperty
        public int getAccountGroupMembershipsRemediated() {
            return accountGroupMembershipsRemediated;
        }

        public void setAccountGroupMembershipsRemediated(int accountGroupMembershipsRemediated) {
            this.accountGroupMembershipsRemediated = accountGroupMembershipsRemediated;
        }

        public int getOpenAccountGroupMemberships(){
            return totalAccountGroupMemberships - accountGroupMembershipsRemediated - accountGroupMembershipsApproved;
        }

        @XMLProperty
        public int getTotalProfiles() {
            return totalProfiles;
        }

        public void setTotalProfiles(int totalProfiles) {
            this.totalProfiles = totalProfiles;
        }

        @XMLProperty
        public int getProfilesApproved() {
            return profilesApproved;
        }

        public void setProfilesApproved(int profilesApproved) {
            this.profilesApproved = profilesApproved;
        }

        @XMLProperty
        public int getProfilesRemediated() {
            return profilesRemediated;
        }

        public void setProfilesRemediated(int profilesRemediated) {
            this.profilesRemediated = profilesRemediated;
        }

        public int getOpenProfiles(){
            return totalProfiles - profilesRemediated - profilesApproved;
        }

        @XMLProperty
        public int getTotalScopes() {
            return totalScopes;
        }

        public void setTotalScopes(int totalScopes) {
            this.totalScopes = totalScopes;
        }

        @XMLProperty
        public int getScopesApproved() {
            return scopesApproved;
        }

        public void setScopesApproved(int scopesApproved) {
            this.scopesApproved = scopesApproved;
        }

        @XMLProperty
        public int getScopesRemediated() {
            return scopesRemediated;
        }

        public void setScopesRemediated(int scopesRemediated) {
            this.scopesRemediated = scopesRemediated;
        }

        public int getOpenScopes() {
            return totalScopes - scopesRemediated - scopesApproved;
        }

        @XMLProperty
        public int getTotalCapabilities() {
            return totalCapabilities;
        }

        public void setTotalCapabilities(int totalCapabilities) {
            this.totalCapabilities = totalCapabilities;
        }

        @XMLProperty
        public int getCapabilitiesApproved() {
            return capabilitiesApproved;
        }

        public void setCapabilitiesApproved(int capabilitiesApproved) {
            this.capabilitiesApproved = capabilitiesApproved;
        }

        @XMLProperty
        public int getCapabilitiesRemediated() {
            return capabilitiesRemediated;
        }

        public void setCapabilitiesRemediated(int capabilitiesRemediated) {
            this.capabilitiesRemediated = capabilitiesRemediated;
        }

        public int getOpenCapabilities() {
            return totalCapabilities - capabilitiesRemediated - capabilitiesApproved;
        }

        @XMLProperty
        public int getTotalPermits() {
            return totalPermits;
        }

        public void setTotalPermits(int totalPermits) {
            this.totalPermits = totalPermits;
        }

        @XMLProperty
        public int getPermitsApproved() {
            return permitsApproved;
        }

        public void setPermitsApproved(int permitsApproved) {
            this.permitsApproved = permitsApproved;
        }

        @XMLProperty
        public int getPermitsRemediated() {
            return permitsRemediated;
        }

        public void setPermitsRemediated(int permitsRemediated) {
            this.permitsRemediated = permitsRemediated;
        }

        public int getOpenPermits() {
            return totalPermits - permitsRemediated - permitsApproved;
        }

        @XMLProperty
        public int getTotalRequirements() {
            return totalRequirements;
        }

        public void setTotalRequirements(int totalRequirements) {
            this.totalRequirements = totalRequirements;
        }

        @XMLProperty
        public int getRequirementsApproved() {
            return requirementsApproved;
        }

        public void setRequirementsApproved(int requirementsApproved) {
            this.requirementsApproved = requirementsApproved;
        }

        @XMLProperty
        public int getRequirementsRemediated() {
            return requirementsRemediated;
        }

        public void setRequirementsRemediated(int requirementsRemediated) {
            this.requirementsRemediated = requirementsRemediated;
        }

        public int getOpenRequirements() {
            return totalRequirements - requirementsRemediated - requirementsApproved;
        }

        @XMLProperty
        public int getTotalRoleHierarchies() {
            return totalRoleHierarchies;
        }

        public void setTotalRoleHierarchies(int totalRoleHierarchies) {
            this.totalRoleHierarchies = totalRoleHierarchies;
        }

        @XMLProperty
        public int getRoleHierarchiesApproved() {
            return roleHierarchiesApproved;
        }

        public void setRoleHierarchiesApproved(int roleHierarchiesApproved) {
            this.roleHierarchiesApproved = roleHierarchiesApproved;
        }

        public int getOpenRoleHierarchies() {
            return totalRoleHierarchies - roleHierarchiesApproved - roleHierarchiesRemediated;
        }

        @XMLProperty
        public int getExcludedEntities() {
            return excludedEntities;
        }

        public void setExcludedEntities(int excludedEntities) {
            this.excludedEntities = excludedEntities;
        }

        @XMLProperty
        public int getExcludedItems() {
            return excludedItems;
        }

        public void setExcludedItems(int excludedItems) {
            this.excludedItems = excludedItems;
        }       
        
    }
}
