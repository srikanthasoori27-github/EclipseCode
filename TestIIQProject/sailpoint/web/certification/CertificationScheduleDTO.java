/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.CertificationScheduler;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDefinition.ApplicationGroup;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Configuration;
import sailpoint.object.Duration;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.NotificationConfig;
import sailpoint.object.Scope;
import sailpoint.object.Tag;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.certification.CertificationScheduleDTO.NotificationInfo.IDataLoader;
import sailpoint.web.extjs.ApplicationSchemaSerializer;
import sailpoint.web.identity.IdentitySuggestItem;

/**
 * This DTO contains the data needed to schedule the various types of certifications.
 * Ideally we would want to subclass the various types of certifications, but that wouldn't
 * jive well with our JSF pages, who are major consumers of this bean.  As a result,
 * this class contains all the fields for all types of certifications, but unused fields
 * will simply be null.  The comments in the source code indicate which fields go with which types.
 * @author Bernie Margolis
 */
public class CertificationScheduleDTO implements Serializable {
    private static final long serialVersionUID = -8554708308063755173L;

    private transient SailPointContext context;

    private CertificationSchedule schedule;

    // Transient values
    private List<String> tagIdsOrNames;
    private String assignedScopeId;
    private Identity certOwner;
    private List<CertificationDefinition.GroupBean> groups;
    private List<CertificationDefinition.FactoryBean> factories;
    private List<String> identitiesToCertify;

    private NotificationInfo certificationNotificationInfo;
    private CertificationDefinition.NotificationConfig remediationNotificationConfig;
    private CertificationDefinition.NotificationConfig certificationRequiredNotificationConfig;
    private CertificationDefinition.NotificationConfig overdueNotificationConfig;
    
    public CertificationScheduleDTO(SailPointContext ctx, CertificationSchedule schedule) throws GeneralException{
        attach(ctx);

        this.schedule = schedule;
        if (schedule.getDefinition() != null){
            groups = schedule.getDefinition().getGroupBeans(ctx);
            factories = schedule.getDefinition().getFactoryBeans(ctx);
            certOwner = schedule.getDefinition().getCertificationOwner(ctx);
            identitiesToCertify = schedule.getDefinition().getIdentitiesToCertify();
            if (schedule.getDefinition().getTags() != null){
                tagIdsOrNames = new ArrayList<String>();
                for(Tag tag : schedule.getDefinition().getTags()){
                    tagIdsOrNames.add(tag.getId());
                }
            }

            this.assignedScopeId = schedule.getDefinition().getAssignedScope() != null ?
                    schedule.getDefinition().getAssignedScope().getId() : null;
        }
    }

    /**
     * Attach a SailPointContext to this DTO - this should be called after the
     * DTO has been restored from the session.
     */
    void attach(SailPointContext ctx) {
        this.context = ctx;
    }

    /**
     * Returns the CertificationSchedule, updated with any changes made on the DTO.
     * @return
     */
    public CertificationSchedule getSchedule() throws GeneralException{

        // copy any transient properties back onto the definition
        if (schedule != null){
            schedule.getDefinition().setCertificationOwner(certOwner);
            if (getGroups().isEmpty())
                schedule.getDefinition().setGroupBeans(null);
            else
                schedule.getDefinition().setGroupBeans(getGroups());
            if (getFactories().isEmpty())
                schedule.getDefinition().setFactoryBeans(null);
            else
                schedule.getDefinition().setFactoryBeans(getFactories());
            if (getIdentitiesToCertify().isEmpty()){
                schedule.getDefinition().setIdentitiesToCertify(null);
            } else{
                schedule.getDefinition().setIdentitiesToCertify(getIdentitiesToCertify());
            }

            if (this.assignedScopeId != null){
                Scope scope = context.getObjectById(Scope.class, assignedScopeId);
                schedule.getDefinition().setAssignedScope(scope);
            }
        }

        return schedule;
    }

    /**
     * Common setting indicating the certification's name
     * @return
     */
    public String getName() {
        return schedule.getName();
    }
    public void setName(String name) {
        this.schedule.setName(name);
    }

    /**
     * Common setting indicating the certification's task ID
     * @return
     */
    public String getTaskId() {
        return schedule.getTaskId();
    }

    /*public void setTaskId(String taskId) {
        this. = taskId;
    }*/

    /**
     * Return the ID of the associated CertificationDefinition.
     */
    public String getCertificationDefinitionId() {
        return this.schedule.getDefinition().getId();
    }

    public CertificationDefinition getDefinition(){
        return schedule.getDefinition();
    }

    /**
     * Setter for the string value of the Certification.Type enum.
     *
     * @deprecated  Consider getting rid of this and just adding a setter for
     *              the certificationType property that uses a converter.
     */
    public String getType() {
        return schedule.getDefinition().getType() != null ? schedule.getDefinition().getType().toString() : null;
    }
    public void setType(String typeStr) {
        Certification.Type type = null;
        if (typeStr != null)
            type = Certification.Type.valueOf(typeStr);
        schedule.getDefinition().setType(type);
    }

    /**
     * @return Message key describing certification type
     */
    public String getTypeDescription() {
        String description = null;
        if (null != schedule.getDefinition().getType()) {
            description = schedule.getDefinition().getType().getMessageKey();
        }
        return description;
    }

    /**
     * Return the Certification.Type for the certification being scheduled.
     */
    public Certification.Type getCertificationType() {
        return schedule.getDefinition().getType();
    }

    public Identity getCertificationOwner() throws GeneralException{
        return certOwner;
    }

    public void setCertificationOwner(Identity certificationOwner) {
        certOwner = certificationOwner;
    }

    public String getCertificationNameTemplate() {
        return schedule.getDefinition().getCertificationNameTemplate();
    }

    public void setCertificationNameTemplate(String certificationNameTemplate) {
        this.schedule.getDefinition().setCertificationNameTemplate(certificationNameTemplate);
    }

    /**
     * Common setting indicating whether or not the certification is global
     * @return
     */
    public boolean isGlobalCertification() {
        return schedule.getDefinition().isGlobal();
    }
    public void setGlobalCertification(boolean globalCertification) {
        this.schedule.getDefinition().setGlobal(globalCertification);
    }

    /**
     * Common setting indicating how frequently a certification should be scheduled
     * @return
     */
    public String getFrequency() {
        // When we save a continuous cert, we convert its frequency to ONCE.
        // We need to convert back from ONCE to CONTINUOUS when pulling it out again.
        if (schedule.getDefinition().isContinuous())
            return CertificationScheduler.FREQ_CONTINUOUS;
        else
            return schedule.getFrequency();
    }

    public void setFrequency(String frequency) {
        this.schedule.setFrequency(frequency);
    }

    /**
     * Common setting indicating the date of the scheduled certification's first execution
     * @return
     */
    public Date getFirstExecution() {
        if(schedule.getFirstExecution() == null) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE) + 5);
            setFirstExecution(cal.getTime());
        }

        return schedule.getFirstExecution();
    }
    public void setFirstExecution(Date firstExecution) {
        this.schedule.setFirstExecution(firstExecution);
    }
    
    /**
     * The date the schedule was activated.
     */
    public Date getActivated() {
        return schedule.getActivated();
    }
    
    public void setActivated(Date activated) {
        schedule.setActivated(activated);
    }

    /**
     * Length of the active period.
     */
    public Long getActivePeriodDurationAmount() {
        return schedule.getDefinition().getActivePeriodDurationAmount();
    }
    public void setActivePeriodDurationAmount(Long amount) {
        this.schedule.getDefinition().setActivePeriodDurationAmount(amount);
    }

    /**
     * Scale of the active period duration.
     */
    public Duration.Scale getActivePeriodDurationScale() {
        return this.schedule.getDefinition().getActivePeriodDurationScale();
    }
    public void setActivePeriodDurationScale(Duration.Scale scale) {
        this.schedule.getDefinition().setActivePeriodDurationScale(scale);
    }
    
    public Date getActiveStartDate() {
        return schedule.getActiveStartDate();
    }

    /**
     * This is a calculated property that is based off the active duration
     * and the certification start.
     */
    public Date getActiveEndDate() {

        return schedule.getActiveEndDate();
    }

    public boolean isChallengePeriodEnabled() {
        return schedule.getDefinition().isChallengePeriodEnabled();
    }

    public void setChallengePeriodEnabled(boolean enabled) {
        this.schedule.getDefinition().setChallengePeriodEnabled(enabled);
    }

    public Long getChallengePeriodDurationAmount() {
        return schedule.getDefinition().getChallengePeriodDurationAmount();
    }

    public void setChallengePeriodDurationAmount(Long amount) {
        this.schedule.getDefinition().setChallengePeriodDurationAmount(amount);
    }

    public Duration.Scale getChallengePeriodDurationScale() {
        return schedule.getDefinition().getChallengePeriodDurationScale();
    }

    public void setChallengePeriodDurationScale(Duration.Scale scale) {
        this.schedule.getDefinition().setChallengePeriodDurationScale(scale);
    }

    public boolean isAllowExceptionPopup() throws GeneralException{
        return schedule.getDefinition().isAllowExceptionPopup(context);
    }

    public void setAllowExceptionPopup(boolean allow) {
        schedule.getDefinition().setAllowExceptionPopup(allow);
    }

    public Long getAllowExceptionDurationAmount() throws GeneralException {
        return schedule.getDefinition().getAllowExceptionDurationAmount(context);
    }

    public void setAllowExceptionDurationAmount(Long amount) {
        schedule.getDefinition().setAllowExceptionDurationAmount(amount);
    }

    public Duration.Scale getAllowExceptionDurationScale() throws GeneralException {
        return schedule.getDefinition().getAllowExceptionDurationScale(context);
    }

    public void setAllowExceptionDurationScale(Duration.Scale scale) {
        schedule.getDefinition().setAllowExceptionDurationScale(scale);
    }

    public boolean isAllowEntityDelegation() throws GeneralException {
        return schedule.getDefinition().isAllowEntityDelegation(context);
    }

    public void setAllowEntityDelegation(boolean allow) {
        schedule.getDefinition().setAllowEntityDelegation(allow);
    }

    public boolean isDelegationForwardingDisabled() throws GeneralException {
        return schedule.getDefinition().isDelegationForwardingDisabled();
    }

    public void setDelegationForwardingDisabled(boolean allow) {
        schedule.getDefinition().setDelegationForwardingDisabled(allow);
    }

    public boolean isLimitReassignments() throws GeneralException {
        return schedule.getDefinition().isLimitReassignments();
    }

    public void setLimitReassignments(boolean allow) {
        schedule.getDefinition().setLimitReassignments(allow);
    }

    public Integer getReassignmentLimit() throws GeneralException {
        return schedule.getDefinition().getReassignmentLimit();
    }

    public void setReassignmentLimit(Integer value) {
        schedule.getDefinition().setReassignmentLimit(value);
    }

    public boolean isRequireBulkCertifyConfirmation() throws GeneralException {
        return schedule.getDefinition().isRequireBulkCertifyConfirmation(context);
    }

    public void setRequireBulkCertifyConfirmation(boolean require) {
        schedule.getDefinition().setRequireBulkCertifyConfirmation(require);
    }

    public boolean isAllowItemDelegation()  throws GeneralException {
        return schedule.getDefinition().isAllowItemDelegation(context);
    }

    public void setAllowItemDelegation(boolean allow) {
        schedule.getDefinition().setAllowItemDelegation(allow);
    }

    /**
     * This is a calculated property that is based off the challenge duration
     * and the certification end.
     */
    public Date getChallengeEndDate() {
        return schedule.getChallengeEndDate();
    }

    public boolean isAutomaticClosingEnabled() {
        return schedule.getDefinition().isAutomaticClosingEnabled();
    }

    public void setAutomaticClosingEnabled(boolean enabled) {
        this.schedule.getDefinition().setAutomaticClosingEnabled(enabled);
    }

    public Long getAutomaticClosingInterval() {
        return schedule.getDefinition().getAutomaticClosingInterval();
    }

    public void setAutomaticClosingInterval(Long amount) {
        this.schedule.getDefinition().setAutomaticClosingInterval(amount);
    }

    public Duration.Scale getAutomaticClosingIntervalScale() {
        return schedule.getDefinition().getAutomaticClosingIntervalScale();
    }

    public void setAutomaticClosingIntervalScale(Duration.Scale scale) {
        this.getDefinition().setAutomaticClosingIntervalScale(scale);
    }

    public Date getAutomaticClosingDate() {
        return schedule.getAutomaticClosingDate();
    }

    public CertificationAction.Status getAutomaticClosingAction() {
        return schedule.getDefinition().getAutomaticClosingAction();
    }

    public void setAutomaticClosingAction(CertificationAction.Status action) {
        schedule.getDefinition().setAutomaticClosingAction(action);
    }

    public String getAutomaticClosingComments() {
        return schedule.getDefinition().getAutomaticClosingComments();
    }

    public void setAutomaticClosingComments(String comments) {
        schedule.getDefinition().setAutomaticClosingComments(comments);
    }

    public Identity getAutomaticClosingSigner() throws GeneralException {
        return schedule.getDefinition().getAutomaticClosingSigner(context);
    }

    public void setAutomaticClosingSigner(Identity signer) {
        schedule.getDefinition().setAutomaticClosingSignerName(signer != null ? signer.getName() : null);
    }
    
    public boolean isStagingEnabled() {
        return schedule.getDefinition().isStagingEnabled();
    }
    
    public void setStagingEnabled(boolean stagingEnabled) {
        schedule.getDefinition().setStagingEnabled(stagingEnabled);
    }

    public boolean isRemediationPeriodEnabled() {
        return schedule.getDefinition().isRemediationPeriodEnabled();
    }

    public void setRemediationPeriodEnabled(boolean enabled) {
        this.schedule.getDefinition().setRemediationPeriodEnabled(enabled);
    }

    public Long getRemediationPeriodDurationAmount() {
        return schedule.getDefinition().getRemediationPeriodDurationAmount();
    }

    public void setRemediationPeriodDurationAmount(Long amount) {
        this.schedule.getDefinition().setRemediationPeriodDurationAmount(amount);
    }

    public Duration.Scale getRemediationPeriodDurationScale() {
        return schedule.getDefinition().getRemediationPeriodDurationScale();
    }

    public void setRemediationPeriodDurationScale(Duration.Scale scale) {
        this.getDefinition().setRemediationPeriodDurationScale(scale);
    }

    public Date getRemediationStartDate() {
        return schedule.getRemediationStartDate();
    }

    public Date getRemediationEndDate() {
        return schedule.getRemediationEndDate();
    }

    public boolean isProcessRevokesImmediately() {
        return this.schedule.getDefinition().isProcessRevokesImmediately();
    }

    public void setProcessRevokesImmediately(boolean immediately) {
        this.schedule.getDefinition().setProcessRevokesImmediately(immediately);
    }

    public boolean isContinuous() {
        return this.schedule.getDefinition().isContinuous();
    }

    public void setContinuous(boolean continuous) {
        this.schedule.getDefinition().setContinuous(continuous);
    }

    public Long getCertifiedDurationAmount() {
        return schedule.getDefinition().getCertifiedDurationAmount();
    }

    public void setCertifiedDurationAmount(Long amount) {
        this.schedule.getDefinition().setCertifiedDurationAmount(amount);
    }

    public Duration.Scale getCertifiedDurationScale() {
        return schedule.getDefinition().getCertifiedDurationScale();
    }

    public void setCertifiedDurationScale(Duration.Scale scale) {
        this.schedule.getDefinition().setCertifiedDurationScale(scale);
    }

    public Duration getCertifiedDuration() {
        return createDuration(this.getCertifiedDurationAmount(),
                              this.getCertifiedDurationScale());
    }

    public Long getCertificationRequiredDurationAmount() {
        return schedule.getDefinition().getCertificationRequiredDurationAmount();
    }

    public void setCertificationRequiredDurationAmount(Long amount) {
        this.schedule.getDefinition().setCertificationRequiredDurationAmount(amount);
    }

    public Duration.Scale getCertificationRequiredDurationScale() {
        return schedule.getDefinition().getCertificationRequiredDurationScale();
    }

    public void setCertificationRequiredDurationScale(Duration.Scale scale) {
        this.getDefinition().setCertificationRequiredDurationScale(scale);
    }

    public Duration getCertificationRequiredDuration() {
        return createDuration(this.schedule.getDefinition().getCertificationRequiredDurationAmount(),
                              this.schedule.getDefinition().getCertificationRequiredDurationScale());
    }

    /**
     * Null-safe method to construct a Duration.
     */
    private Duration createDuration(Long amount, Duration.Scale scale) {
        return (null != scale && amount != null) ? new Duration(amount, scale) : null;
    }

    public CertificationDefinition.NotificationConfig getCertificationRequiredNotificationConfig() throws GeneralException {
        if (certificationRequiredNotificationConfig == null) {
            certificationRequiredNotificationConfig = schedule.getDefinition().getCertificationRequiredNotificationConfig(context);
        }
        return certificationRequiredNotificationConfig;
    }

    public CertificationDefinition.NotificationConfig getOverdueNotificationConfig() throws GeneralException {
        if (overdueNotificationConfig == null) {
            overdueNotificationConfig = schedule.getDefinition().getOverdueNotificationConfig(context);
        }
        return overdueNotificationConfig;
    }
    
    public CertificationDefinition.NotificationConfig getRemediationNotificationConfig() throws GeneralException {
        if (remediationNotificationConfig == null) {
            remediationNotificationConfig = schedule.getDefinition().getRemediationNotificationConfig(context);
        }
        return remediationNotificationConfig;
    }

    public String getNameTemplate() {
        return this.schedule.getDefinition().getNameTemplate();
    }

    public void setNameTemplate(String nameTemplate) {
        this.schedule.getDefinition().setNameTemplate(nameTemplate);
    }

    public String getShortNameTemplate() {
        return this.schedule.getDefinition().getShortNameTemplate();
    }

    public void setShortNameTemplate(String shortNameTemplate) {
        this.schedule.getDefinition().setShortNameTemplate(shortNameTemplate);
    }

    /**
     * Common setting
     * @return
     */
    public String getDescription() {
        return schedule.getDefinition().getDescription();
    }
    public void setDescription(String description) {
        this.schedule.getDefinition().setDescription(description);
    }

    public Scope getAssignedScope() throws GeneralException {
        // Use the SailPointContext to load the assigned scope.  Previously, I
        // tried storing the actual Scope here, but saw problems when this bean
        // was restored from the session with a "closed hibernate session" error.
        // I tried reattaching the Scope when restoring from the session, but
        // would occassionally get an "associated with two sessions" error if
        // an AJAX call was still completing when saving the schedule.  To get
        // around this I'm just loading the scope by ID with the attached
        // context every time we need it.
        Scope assignedScope = null;
        if (null != assignedScopeId) {
            assignedScope = this.context.getObjectById(Scope.class, this.assignedScopeId);
        }
        return assignedScope;
    }

    public void setAssignedScope(Scope scope) {
        this.assignedScopeId = (null != scope) ? scope.getId() : null;
    }

    /**
     * Common setting indicating if certifications should be generated for the certifier's subordinate certifiers as well
     * @return
     */
    public String getIsSubordinateCertificationEnabled() {
        return String.valueOf(schedule.getDefinition().isSubordinateCertificationEnabled());
    }
    public void setIsSubordinateCertificationEnabled(String subordinateCertificationEnabled) {
        Boolean b = null;
        if (subordinateCertificationEnabled != null)
            b = Util.atob(subordinateCertificationEnabled);
        this.schedule.getDefinition().setIsSubordinateCertificationEnabled(b);
    }

    /**
     * Manager Certification setting indicating if all employees reporting to subordinate certifiers should also be included in the
     * certification
     * @return
     */
    public boolean isFlattenManagerCertificationHierarchy() {
        return schedule.getDefinition().isFlattenManagerCertificationHierarchy();
    }
    public void setFlattenManagerCertificationHierarchy(boolean flattenManagerCertificationHierarchy) {
        this.schedule.getDefinition().setFlattenManagerCertificationHierarchy(flattenManagerCertificationHierarchy);
    }

    /**
     * Manager Certification setting
     * @return
     */
    public boolean isCompleteCertificationHierarchy() {
        return schedule.getDefinition().isCompleteCertificationHierarchy();
    }
    public void setCompleteCertificationHierarchy(boolean completeCertificationHierarchy) {
        this.schedule.getDefinition().setCompleteCertificationHierarchy(completeCertificationHierarchy);
    }

    /**
     * Common setting that specifies the granularity at which additional entitlements should be certified
     * @return
     */
    public Certification.EntitlementGranularity getEntitlementGranularity() {
        return schedule.getDefinition().getEntitlementGranularity();
    }
    public void setEntitlementGranularity(
                    Certification.EntitlementGranularity entitlementGranularity) {
        this.schedule.getDefinition().setEntitlementGranularity(entitlementGranularity);
    }

    /**
    * Gets the granularity, which may differ from the the user selecteced,
    * depending on other parameters.
    */
    public Certification.EntitlementGranularity getEffectiveEntitlementGranularity() {
      return this.isCertifyAccounts() ? Certification.EntitlementGranularity.Application :
              this.getEntitlementGranularity();
    }

    /**
    * If we're certifying accounts, then the granularity select should be disabled.
    * When a use has chosen to certify accounts, granularity is not longer really applicable
    * since we will be certifying accounts not additional entitlements.
    * @return
    */
    public boolean isEntitlementGranularityEditable(){
      return !this.isCertifyAccounts();
    }

    public String getCertificationDetailedView() throws GeneralException {
        return schedule.getDefinition().getCertificationDetailedView(context);
    }

    public void setCertificationDetailedView(String certificationDetailedView) {
        this.schedule.getDefinition().setCertificationDetailedView(certificationDetailedView);
    }

    public String getCertPageListItems() throws GeneralException {
        return new Boolean(schedule.getDefinition().getCertPageListItems(context)).toString();
    }

    public void setCertPageListItems(String certPageListItems) {
        this.schedule.getDefinition().setCertPageListItems(Util.atob(certPageListItems));
    }

    public boolean isAllowExceptions() throws GeneralException{
        return this.schedule.getDefinition().isAllowExceptions(context);
    }

    public void setAllowExceptions(boolean allowExceptions) {
        this.schedule.getDefinition().setAllowExceptions(allowExceptions);
    }

    public boolean isMitigationDeprovisionEnabled() throws GeneralException{
        return this.schedule.getDefinition().isMitigationDeprovisionEnabled(context);
    }

    public void setMitigationDeprovisionEnabled(boolean mitigationDeprovisionEnabled) {
        this.schedule.getDefinition().setMitigationDeprovisionEnabled(mitigationDeprovisionEnabled);
    }

    public String getExclusionRuleName() {
        return schedule.getDefinition().getExclusionRuleName();
    }

    public void setExclusionRuleName(String ruleName) {
        this.schedule.getDefinition().setExclusionRuleName(ruleName);
    }

    public String getActivePhaseEnterRuleName() {
        return schedule.getDefinition().getActivePhaseEnterRuleName();
    }

    public void setActivePhaseEnterRuleName(String activePhaseEnterRuleName) {
        this.schedule.getDefinition().setActivePhaseEnterRuleName(activePhaseEnterRuleName);
    }

    public String getChallengePhaseEnterRuleName() {
        return schedule.getDefinition().getChallengePhaseEnterRuleName();
    }

    public void setChallengePhaseEnterRuleName(String challengePhaseEnterRuleName) {
        this.schedule.getDefinition().setChallengePhaseEnterRuleName(challengePhaseEnterRuleName);
    }

    public String getAutomaticClosingRuleName() {
        return schedule.getDefinition().getAutomaticClosingRuleName();
    }

    public void setAutomaticClosingRuleName(String automaticClosingRuleName) {
        this.schedule.getDefinition().setAutomaticClosingRuleName(automaticClosingRuleName);
    }

    public String getRemediationPhaseEnterRuleName() {
        return schedule.getDefinition().getRemediationPhaseEnterRuleName();
    }

    public void setRemediationPhaseEnterRuleName(String remediationPhaseEnterRuleName) {
        this.schedule.getDefinition().setRemediationPhaseEnterRuleName(remediationPhaseEnterRuleName);
    }

    public String getEndPhaseEnterRuleName() {
        return schedule.getDefinition().getEndPhaseEnterRuleName();
    }

    public void setEndPhaseEnterRuleName(String val) {
        this.schedule.getDefinition().setEndPhaseEnterRuleName(val);
    }

    public boolean getSaveExclusions() {
        return schedule.getDefinition().getSaveExclusions();
    }

    public void setSaveExclusions(boolean saveExclusions) {
        this.schedule.getDefinition().setSaveExclusions(saveExclusions);
    }

    public boolean isExcludeInactive() {
        return schedule.getDefinition().isExcludeInactive();
    }

    public void setExcludeInactive(boolean exclude) {
        this.schedule.getDefinition().setExcludeInactive(exclude);
    }

    public boolean isEnablePartitioning() {
        return schedule.getDefinition().isEnablePartitioning();
    }
    
    public void setEnablePartitioning(boolean val) {
        schedule.getDefinition().setEnablePartitioning(val);
    }
    
    public String getPreDelegationRuleName() {
        return schedule.getDefinition().getPreDelegationRuleName();
    }

    public void setPreDelegationRuleName(String ruleName) {
        this.schedule.getDefinition().setPreDelegationRuleName(ruleName);
    }

    public String getApproverRuleName() {
        return schedule.getDefinition().getApproverRuleName();
    }

    public void setApproverRuleName(String approverRuleName) {
        this.schedule.getDefinition().setApproverRuleName(approverRuleName);
    }

    public boolean isAllowProvisioningRequirements() {
        return schedule.getDefinition().isAllowProvisioningRequirements();
    }

    public void setAllowProvisioningRequirements(boolean allowProvisioningRequirements) {
        this.schedule.getDefinition().setAllowProvisioningRequirements(allowProvisioningRequirements);
    }

    public boolean isRequireApprovalComments() throws GeneralException {
        return schedule.getDefinition().isRequireApprovalComments();
    }

    public void setRequireApprovalComments(boolean requireApprovalComments) {
        this.schedule.getDefinition().setRequireApprovalComments(requireApprovalComments);
    }

    public boolean isRequireMitigationComments() throws GeneralException {
        return schedule.getDefinition().isRequireMitigationComments(context);
    }

    public void setRequireMitigationComments(boolean requireMitigationComments) {
        this.schedule.getDefinition().setRequireMitigationComments(requireMitigationComments);
    }

    public boolean isRequireRemediationComments() throws GeneralException {
        return schedule.getDefinition().isRequireRemediationComments(context);
    }

    public void setRequireRemediationComments(boolean requireRemediationComments) {
        this.schedule.getDefinition().setRequireRemediationComments(requireRemediationComments);
    }

    public boolean isExcludeBaseAppAccounts() {
        return schedule.getDefinition().isExcludeBaseAppAccounts();
    }

    public void setExcludeBaseAppAccounts(boolean excludeBaseAppAccounts) {
        this.schedule.getDefinition().setExcludeBaseAppAccounts(excludeBaseAppAccounts);
    }

    public boolean isUpdateIdentityEntitlements() {
        return schedule.getDefinition().isUpdateIdentityEntitlements();
    }

    public void setUpdateIdentityEntitlements(boolean updateEntitlements) {
        this.schedule.getDefinition().setUpdateIdentityEntitlements(updateEntitlements);
    }
    
    public boolean isUpdateAttributeAssignments() {
        return schedule.getDefinition().isUpdateAttributeAssignments();
    }

    public void setUpdateAttributeAssignments(boolean updateAssignments) {
        this.schedule.getDefinition().setUpdateAttributeAssignments(updateAssignments);
    }
    
    public Attributes<String,Object> getAttributes() {
        return schedule.getDefinition().getAttributes();
    }

    public void setAttributes(Attributes<String,Object> attributes) {
        this.schedule.getDefinition().setAttributes(attributes);
    }

    public boolean isIncludeRoles() {
        return schedule.getDefinition().isIncludeRoles();
    }

    public void setIncludeRoles(boolean includeRoles) {
        this.schedule.getDefinition().setIncludeRoles(includeRoles);
    }

    public boolean isIncludeAdditionalEntitlements() {
        return schedule.getDefinition().isIncludeAdditionalEntitlements();
    }

    public void setIncludeAdditionalEntitlements(boolean b) {
        this.schedule.getDefinition().setIncludeAdditionalEntitlements(b);
    }

    public boolean isIncludeTargetPermissions() {
        return schedule.getDefinition().isIncludeTargetPermissions();
    }

    public void setIncludeTargetPermissions(boolean b) {
        this.schedule.getDefinition().setIncludeTargetPermissions(b);
    }

    public boolean isCertifyEmptyAccounts() {
        return schedule.getDefinition().isCertifyEmptyAccounts();
    }

    public void setCertifyEmptyAccounts(boolean certifyEmptyAccounts) {
        this.schedule.getDefinition().setCertifyEmptyAccounts(certifyEmptyAccounts);
    }

    public boolean isCertifyAccounts() {
        return schedule.getDefinition().isCertifyAccounts();
    }

    public void setCertifyAccounts(boolean certifyAccounts) {
        this.schedule.getDefinition().setCertifyAccounts(certifyAccounts);
    }

    /**
     * Common Setting
     * @return
     */
    public boolean isIncludePolicyViolations() {
        return schedule.getDefinition().isIncludePolicyViolations();
    }
    public void setIncludePolicyViolations(boolean includePolicyViolations) {
        this.schedule.getDefinition().setIncludePolicyViolations(includePolicyViolations);
    }

    public boolean isIncludeCapabilities() {
        return schedule.getDefinition().isIncludeCapabilities();
    }

    public void setIncludeCapabilities(boolean includeCapabilities) {
        this.schedule.getDefinition().setIncludeCapabilities(includeCapabilities);
    }

    public boolean isIncludeScopes() {
        return this.schedule.getDefinition().isIncludeScopes();
    }

    public void setIncludeScopes(boolean includeScopes) {
        this.schedule.getDefinition().setIncludeScopes(includeScopes);
    }

    /**
     * Common setting that indicates the applications to include in the certification
     * @return
     */
    public List<String> getIncludedApplicationIds() {
        return ObjectUtil.convertToIds(context, Application.class, schedule.getDefinition().getIncludedApplicationIds());
    }
    public void setIncludedApplicationIds(List<String> includedApplicationIds) {
        this.schedule.getDefinition().setIncludedApplicationIds(ObjectUtil.convertToNames(context, Application.class, includedApplicationIds));
    }

    /**
     * NOTE: THIS METHOD IS SOLELY A PART OF THE DTO AND SHOULD ONLY BE USED BE
     * THE WEB TIER TO GET THE TAGS AND ALLOW TAG AUTO-CREATION.
     *
     * A list of the names or IDs of the tags for this schedule.getDefinition().  We will
     * auto-create new Tags for any names that we find in this list when the
     * definition is saved.
     *
     * @return A list of IDs or names of the tags for this schedule.getDefinition().
     */
    public List<String> getTagIdsOrNames() throws GeneralException{
        if (null == this.tagIdsOrNames) {
            this.tagIdsOrNames = new ArrayList<String>();
            // if we've pulled the object off the session we may need
            // to reattach.
            if (schedule.getDefinition().getId() != null){
                context.attach(schedule.getDefinition());
            }
            if (null != this.schedule.getDefinition().getTags()) {
                for (Tag tag : this.schedule.getDefinition().getTags()) {
                    this.tagIdsOrNames.add(tag.getId());
                }
            }
        }
        return this.tagIdsOrNames;
    }

    public void setTagIdsOrNames(List<String> tagIdsOrNames) {
    	if (tagIdsOrNames == null) {
    		this.tagIdsOrNames = new ArrayList<String>();
    	} else {
    		this.tagIdsOrNames = tagIdsOrNames;
    	}
    }

    public List<Tag> getTags() {
        return this.schedule.getDefinition().getTags();
    }

    public void setTags(List<Tag> tags) {
        this.schedule.getDefinition().setTags(tags);
    }

    /**
     * Application Certification setting that indicates the IDs of the owners for the
     * application(s) being certified
     * @return
     */
    public List<String> getOwnerIds() {
        return ObjectUtil.convertToIds(context, Identity.class, schedule.getDefinition().getOwnerIds());
    }
    public void setOwnerIds(List<String> ownerIds) {
        this.schedule.getDefinition().setOwnerIds(ObjectUtil.convertToNames(context, Identity.class, ownerIds));
    }

    public List<String> getOwnerNames() {
        return ObjectUtil.convertToNames(context, Identity.class, schedule.getDefinition().getOwnerIds());
    }

    /**
     * Manager Certification setting that indicates the certifier
     * @return
     */
    public Identity getCertifier() throws GeneralException{
        return schedule.getDefinition().getCertifier(context);
    }
    public void setCertifier(Identity certifier) {
        this.schedule.getDefinition().setCertifierName(certifier != null ? certifier.getName() : null);
    }

    /**
     * Application Certification setting that indicates the applications being certified
     * @return
     */
    public List<String> getApplicationIds() {
        return ObjectUtil.convertToIds(context, Application.class, schedule.getDefinition().getApplicationIds());
    }
    public void setApplicationIds(List<String> applicationIds) {
        this.schedule.getDefinition().setApplicationIds(ObjectUtil.convertToNames(context, Application.class, applicationIds));
    }
    
    public String getApplicationGroupsJson() throws GeneralException {
        List<ApplicationGroup> appGroups = this.schedule.getDefinition().getApplicationGroups();
        
        if (Util.isEmpty(appGroups)) {
            return "[]";
        }
        
        Map<String, List<String>> appMap = new LinkedHashMap<String, List<String>>();
        // transform the app groups to a local map we can serialize
        for (ApplicationGroup ag : appGroups) {
            
            if ( !appMap.containsKey(ag.getApplicationName()) ) {
                appMap.put(ag.getApplicationName(), new ArrayList<String>());
            }
            
            List<String> schemaObjectTypes = appMap.get(ag.getApplicationName());
            // yes we should be using a Set here, however AppSchemaSerializer isn't expecting a Set
            if ( !schemaObjectTypes.contains(ag.getSchemaObjectType()) ) {
                schemaObjectTypes.add(ag.getSchemaObjectType());
            }
        }

        return ApplicationSchemaSerializer.serialize(appMap, context);
    }
    public void setApplicationGroupsJson(String appGroupsJson) throws GeneralException {
        Map<String, List<String>> appMap = (Map<String, List<String>>) ApplicationSchemaSerializer.deserialize(appGroupsJson, context);
        
        // transform to application group
        List<ApplicationGroup> val = new ArrayList<ApplicationGroup>();
        for (Map.Entry<String,List<String>> item : Util.safeIterable(appMap.entrySet())) {
            for (String schema : Util.safeIterable(item.getValue())) {
                ApplicationGroup valItem = new ApplicationGroup();
                val.add(valItem);
                valItem.setApplicationName(item.getKey());
                valItem.setSchemaObjectType(schema);
            }
        }
        
        this.schedule.getDefinition().setApplicationGroups(val);
    }

    /**
     * Advanced Certification setting
     * @return
     */
    public List<CertificationDefinition.GroupBean> getGroups() throws GeneralException{
        return groups != null ? groups : new ArrayList<CertificationDefinition.GroupBean>();
    }
    public void setGroups(List<CertificationDefinition.GroupBean> groupBeans) {
        groups = groupBeans;
    }

    public void addGroup(GroupDefinition group) throws GeneralException {
        CertificationDefinition.GroupBean groupBean = new CertificationDefinition.GroupBean(group);
        getGroups().add(groupBean);
    }

    public List<CertificationDefinition.FactoryBean> getFactories() throws GeneralException {
        return factories != null ? factories : new ArrayList<CertificationDefinition.FactoryBean>();
    }
    public void setFactories(List<CertificationDefinition.FactoryBean> factoryBeans) {
        this.factories = factoryBeans;
    }

    public CertificationDefinition.FactoryBean getFactory(String id) throws GeneralException{
        if (id != null && getFactories() != null){
            for(CertificationDefinition.FactoryBean bean : getFactories()){
                if (id.equals(bean.getId())){
                    return bean;
                }
            }
        }
        return null;
    }

    public void addFactory(GroupFactory f) throws GeneralException{
        CertificationDefinition.FactoryBean factoryBean =
                new CertificationDefinition.FactoryBean(f.getId(), f.getName());
        getFactories().add(factoryBean);
    }


    public List<String> getRoleTypes() {
        return schedule.getDefinition().getRoleTypes();
    }

    public void setRoleTypes(List<String> roleTypes) {
        this.schedule.getDefinition().setRoleTypes(roleTypes);
    }

    /**
     * Bulk/Individual certification setting that specifies the identities being certified
     * @return
     */
    public List<String> getIdentitiesToCertify() {
        if (identitiesToCertify == null)
            identitiesToCertify = new ArrayList<String>();
        return identitiesToCertify;
    }
    public void setIdentitiesToCertify(List<String> identitiesToCertify) {
        this.identitiesToCertify = identitiesToCertify;
    }

    /**
     * @return List of business roles selected for certification
     */
    public List<String> getBusinessRoleIds() {
        return ObjectUtil.convertToIds(context, Bundle.class, this.schedule.getDefinition().getBusinessRoleIds());
    }

    public void setBusinessRoleIds(List<String> businessRoleIds) {
        this.schedule.getDefinition().setBusinessRoleIds(ObjectUtil.convertToNames(context, Bundle.class, businessRoleIds));
    }

    public CertificationDefinition.CertifierSelectionType getCertifierSelectionType() {
        CertificationDefinition.CertifierSelectionType certifierSelectionType =
                this.schedule.getDefinition().getCertifierSelectionType();
        if (certifierSelectionType==null && isBusinessRoleMembershipCertification()){
            return CertificationDefinition.CertifierSelectionType.Manager;
        }else if (certifierSelectionType==null && this.isBusinessRoleCompositionCertification()){
            return CertificationDefinition.CertifierSelectionType.Owner;
        }else if (certifierSelectionType==null){
            return CertificationDefinition.CertifierSelectionType.Manual;
        }
        return certifierSelectionType;
    }

    public void setCertifierSelectionType(CertificationDefinition.CertifierSelectionType certifierSelectionType) {
        this.schedule.getDefinition().setCertifierSelectionType(certifierSelectionType);
    }

    /**
     * @return True if the user has elected to include all the children of selected nodes
     * in the certification.
     */
    public boolean isIncludeRoleHierarchy() {
        return schedule.getDefinition().isIncludeRoleHierarchy();
    }

    public void setIncludeRoleHierarchy(boolean includeRoleHierarchy) {
        this.schedule.getDefinition().setIncludeRoleHierarchy(includeRoleHierarchy);
    }

    public String getDisplayEntitlementDescriptions() throws GeneralException {
        return schedule.getDefinition().isDisplayEntitlementDescriptions(context).toString();
    }

    public void setDisplayEntitlementDescriptions(String displayEntitlementDescriptions) {
        this.schedule.getDefinition().setDisplayEntitlementDescriptions(Util.atob(displayEntitlementDescriptions));
    }

    public boolean isSendPreDelegationCompletionEmails() {
        return this.schedule.getDefinition().isSendPreDelegationCompletionEmails();
    }

    public void setSendPreDelegationCompletionEmails(boolean val) {
        this.schedule.getDefinition().setSendPreDelegationCompletionEmails(val);
    }

    public boolean isAutomateSignoffPopup() throws GeneralException {
        return schedule.getDefinition().isAutomateSignoffPopup(context);
    }
    public void setAutomateSignoffPopup(boolean automateSignoffPopup) {
        this.schedule.getDefinition().setAutomateSignoffPopup(automateSignoffPopup);
    }
    
    public boolean isElectronicSignatureRequired() throws GeneralException {
    	return schedule.getDefinition().isElectronicSignatureRequired(context);
    }
    public void setElectronicSignatureRequired(boolean electronicSignatureRequired) {
    	schedule.getDefinition().setElectronicSignatureRequired(electronicSignatureRequired);
    }
    
    public String getElectronicSignatureName() throws GeneralException {
    	return schedule.getDefinition().getElectronicSignatureName(context);
    }
    public void setElectronicSignatureName(String electronicSignatureName) {
    	schedule.getDefinition().setElectronicSignatureName(electronicSignatureName);
    }

    /*
        Configuration to set whether roles that require other roles should be included
        in a Role Membership cert.
     */
    public boolean getIncludeRequiredRoles() {
        return schedule.getDefinition().getIncludeRequiredRoles();
    }
    public void setIncludeRequiredRoles(boolean value) {
        schedule.getDefinition().setIncludeRequiredRoles(value);
    }
    
    
    /* *********************************************************************
   *
   * Convenience methods for retrieving certification type
   *
   ********************************************************************** */

	public boolean isShowEntitlementOptions(){
        return !isAccountGroupCertification() && !isBusinessRoleCertification() && !isDataOwnerCertification();
    }

    public boolean isAccountGroupCertification(){
        return isAccountGroupMembershipCertification() || isAccountGroupPermissionsCertification();
    }

    public boolean isAccountGroupMembershipCertification(){
        return Certification.Type.AccountGroupMembership.equals(getCertificationType());
    }

    public boolean isAccountGroupPermissionsCertification(){
        return Certification.Type.AccountGroupPermissions.equals(getCertificationType());
    }

    public boolean isApplicationOwnerCertification(){
        return Certification.Type.ApplicationOwner.equals(getCertificationType());
    }

    public boolean isDataOwnerCertification() {
        return Certification.Type.DataOwner.equals(getCertificationType());
    }

    public boolean isManagerCertification(){
        return Certification.Type.Manager.equals(getCertificationType());
    }

    /**
     * Determines if this is a responsive certification type
     * @return True if this is a responsive type, False otherwise.
     */
    public boolean isResponsiveCertType() {

        return true;

    }

    public boolean isIdentityCertification(){
        return Certification.Type.Identity.equals(getCertificationType());
    }

    public boolean isAdvancedCertification(){
        return Certification.Type.Group.equals(getCertificationType());
    }

    public boolean isBusinessRoleCertification(){
        return isBusinessRoleCompositionCertification() ||
                isBusinessRoleMembershipCertification();
    }

    public boolean isBusinessRoleCompositionCertification(){
        return Certification.Type.BusinessRoleComposition.equals(getCertificationType());
    }

    public boolean isBusinessRoleMembershipCertification(){
        return Certification.Type.BusinessRoleMembership.equals(getCertificationType());
    }


    /* *********************************************************************

        Properties which turn on/off UI components depending on
        certification type

    ********************************************************************* */

    /**
     * @return True if global checkbox should be displayed
     */
    public boolean isShowGlobal(){
        return !isAdvancedCertification() && !isIdentityCertification();
    }

    /**
     * @return True if the application select box should be displayed.
     */
    public boolean isShowApplicationSelection(){
        return isAccountGroupCertification() || isApplicationOwnerCertification() || isDataOwnerCertification();
    }

    /**
     * true if the multiple certifiers control should be displayed
     *
     * @return True if multiple certifiers select box should be displayed.
     */
    public boolean isShowMultiCertifiers(){
        return (isAccountGroupCertification() ||
                isApplicationOwnerCertification() || isBusinessRoleCertification() || isDataOwnerCertification());
    }


    /**
     * @return True if the 'Remediation Period' check box should be displayed
     */
    public boolean isShowRemediationPeriod(){
        //Everyone has this, but keep the check for future use. 
        return true;
    }

    /**
     * @return True if the 'Challenge Period' check box should be displayed
     */
    public boolean isShowChallengePeriod(){
        return !isBusinessRoleCertification();
    }

    /**
     * @return True if the 'Automatic Closing' check box should be displayed
     */
    public boolean isShowAutomaticClosing(){
        return !isContinuous();
    }

    /**
     * @return True if the 'Subordinate Completion' check box should be displayed
     */
    public boolean isShowSubordinateCompletion(){
        return !isAccountGroupCertification() && !isBusinessRoleCertification();
    }

    /**
     * @return True if the 'Include Policy Violations' check box should be displayed
     */
    public boolean isShowIncludePolicyViolations(){
        return !isAccountGroupCertification() && !isBusinessRoleCertification() && !isDataOwnerCertification();
    }

    /**
     * @return True if the 'Include Additional Entitlements' check box should be displayed
     */
    public boolean isShowIncludeAdditionalEntitlements() {
        // Account group certs *only* have entitlements.  Business role certs
        // *never* have entitlements.  All others can have entitlements.
        return !isAccountGroupCertification() && !isBusinessRoleCertification() && !isDataOwnerCertification();
    }

    /**
     * @return True if the 'Include Roles' check box should be displayed
     */
    public boolean isShowIncludeRoles() {
        // Account group certs *never* have roles.  Business role membership
        // certs always have roles.  Business role composition certs have roles
        // for inheritance, but these are used differently.  All others can have
        // roles.
        return !isAccountGroupCertification() && !isBusinessRoleCertification() && !isDataOwnerCertification();
    }

    /**
     * @return True if the 'Include Additional Entitlements' check box should be displayed
     */
    public boolean isShowIncludeTargetPermissions() {
        // Account group certs *only* have entitlements.  Business role certs
        // *never* have entitlements.  All others can have entitlements.
        return !isAccountGroupCertification() && !isBusinessRoleCertification() && !isDataOwnerCertification();
    }

    /**
     * @return True if the 'Show Classifications' option should be displayed
     */
    public boolean isDisplayShowClassifications() {
        return schedule.getDefinition().shouldIncludeClassifications();
    }

    /**
     * @return True if the 'Entitlement Granularity' select box should be displayed
     */
    public boolean isShowEntitlementGranularity(){
        return !isAccountGroupMembershipCertification() && !isBusinessRoleCertification()
                && !isDataOwnerCertification();
    }

    /**
     * @return True if the 'Included Applications' select box should be displayed
     */
    public boolean isShowIncludedApplications(){
        return !isAccountGroupCertification() && !isApplicationOwnerCertification() &&
                !isBusinessRoleCertification() && !isDataOwnerCertification();
    }

    /**
     * @return True if the user is allowed to include the entire hierarchy of
     * the selected business roles.
     */
    public boolean isShowBusinessRoleHierarchy(){
        return Certification.Type.BusinessRoleComposition.equals(getCertificationType());
    }

    public boolean isShowIncludeIIQEntitlements() {
        // Allow certifying IIQ entitlements for all identity-based certs except
        // for app owner (since these are rendered to only include the entitlements
        // on the app being certified).
        return isManagerCertification() || isIdentityCertification() || isAdvancedCertification();
    }

    public boolean isShowIncludeIIQScopes() {
        // Only show scopes when we're showing IIQ entitlements and scoping is enabled
        ScopeService scopeSvc = new ScopeService(context);
        return isShowIncludeIIQEntitlements() && scopeSvc.isScopingEnabled();
    }
    
    /**
     * Return whether to show the "Notify Remediations" option.
     */
    public boolean isShowNotifyRemediation() {
        return getCertificationType().isIdentity() ||
               Certification.Type.AccountGroupMembership.equals(getCertificationType()) ||
               Certification.Type.DataOwner.equals(getCertificationType());
    }

    public boolean isIncludeUnownedData() {
        return this.schedule.getDefinition().isIncludeUnownedData();
    }

    public void setIncludeUnownedData(boolean includeUnownedData) {
        this.schedule.getDefinition().setIncludeUnownedData(includeUnownedData);
    }
    
    public boolean isIncludeEntitlementsGrantedByRoles() {
        return this.schedule.getDefinition().isIncludeEntitlementsGrantedByRoles();
    }
    
    public void setIncludeEntitlementsGrantedByRoles(boolean val) {
        this.schedule.getDefinition().setIncludeEntitlementsGrantedByRoles(val);
    }

    public boolean isAppOwnerIsUnownedOwner() {
        return schedule.getDefinition().isAppOwnerIsUnownedOwner();
    }

    public void setAppOwnerIsUnownedOwner(boolean appOwnerIsUnownedOwner) {
        this.schedule.getDefinition().setAppOwnerIsUnownedOwner(appOwnerIsUnownedOwner);
    }

    public IdentitySuggestItem getUnownedDataOwner() throws GeneralException{
        Identity identity = this.schedule.getDefinition().getUnownedDataOwner(context);
        IdentitySuggestItem item = null;
        if (identity != null){
            item = new IdentitySuggestItem();
            item.setId(identity.getId());
            item.setName(identity.getName());
            item.setDisplayName(identity.getDisplayableName());
        }
        return item;
    }

    public void setUnownedDataOwner(IdentitySuggestItem val) {
        this.schedule.getDefinition().setUnownedDataOwner(val != null ? val.getName() : null);
    }

    public boolean isShowAutomateSignoffPopup() {
        // Hide Prompt for Signoff option for continuous or identity related certs
        return !(isContinuous() || isIdentityCertification() || isManagerCertification() || isAdvancedCertification() || isApplicationOwnerCertification());
    }

    /*************************************
     *
     * Bulk Action Properties
     * @throws GeneralException
     *
     *************************************/

    // APPROVE
    public boolean isAllowEntityBulkApprove()  throws GeneralException {
        return schedule.getDefinition().isAllowEntityBulkApprove(context);
    }

    public void setAllowEntityBulkApprove(boolean approve) {
        schedule.getDefinition().setAllowEntityBulkApprove(approve);
    }

    public boolean isAllowListBulkApprove()  throws GeneralException {
        return schedule.getDefinition().isAllowListBulkApprove(context);
    }

    public void setAllowListBulkApprove(boolean approve) {
        schedule.getDefinition().setAllowListBulkApprove(approve);
    }

    // REVOCATION
    public boolean isAllowEntityBulkRevocation() throws GeneralException {
        return schedule.getDefinition().isAllowEntityBulkRevocation(context);
    }

    public void setAllowEntityBulkRevocation(boolean allow) {
        schedule.getDefinition().setAllowEntityBulkRevocation(allow);
    }

    // List view bulk revoke
    public boolean isAllowListBulkRevocation() throws GeneralException {
        return schedule.getDefinition().isAllowListBulkRevocation(context);
    }

    public void setAllowListBulkRevocation(boolean allow) {
        schedule.getDefinition().setAllowListBulkRevocation(allow);
    }

    // List view bulk account revoke
    public boolean isAllowListBulkAccountRevocation() throws GeneralException {
        return schedule.getDefinition().isAllowListBulkAccountRevocation(context);
    }

    public void setAllowListBulkAccountRevocation(boolean allow) {
        schedule.getDefinition().setAllowListBulkAccountRevocation(allow);
    }

    // allow overriding default violation remediator
    
    public boolean isEnableOverrideViolationDefaultRemediator() throws GeneralException {
    	return schedule.getDefinition().isEnableOverrideViolationDefaultRemediator(context);
    }
    
    public void setEnableOverrideViolationDefaultRemediator(boolean allow) {
    	schedule.getDefinition().setEnableOverrideViaolationDefaultRemediator(allow);
    }
    
    // details view bulk account revoke
    public boolean isAllowEntityBulkAccountRevocation() throws GeneralException {
        return schedule.getDefinition().isAllowEntityBulkAccountRevocation(context);
    }

    public void setAllowEntityBulkAccountRevocation(boolean allow) {
        schedule.getDefinition().setAllowEntityBulkAccountRevocation(allow);
    }

    public boolean isAllowEntityBulkClearDecisions() throws GeneralException {
        return schedule.getDefinition().isAllowEntityBulkClearDecisions(context);
    }

    public void setAllowEntityBulkClearDecisions(boolean allow) {
        schedule.getDefinition().setAllowEntityBulkClearDecisions(allow);
    }
    
    public boolean isAllowListBulkClearDecisions() throws GeneralException {
        return schedule.getDefinition().isAllowListBulkClearDecisions(context);
    }

    public void setAllowListBulkClearDecisions(boolean allow) {
        schedule.getDefinition().setAllowListBulkClearDecisions(allow);
    }
    
    public boolean isAllowApproveAccounts() throws GeneralException {
        return schedule.getDefinition().isAllowApproveAccounts(context);
    }

    public void setAllowApproveAccounts(boolean allow) {
        schedule.getDefinition().setAllowApproveAccounts(allow);
    }

    public boolean isAllowAccountRevocation() throws GeneralException {
        return schedule.getDefinition().isAllowAccountRevocation(context);
    }

    public void setAllowAccountRevocation(boolean allow) {
        schedule.getDefinition().setAllowAccountRevocation(allow);
    }
    
    public boolean isEnableReassignAccount() throws GeneralException {
        return schedule.getDefinition().isEnableReassignAccount(context);
    }
    
    public void setEnableReassignAccount(boolean enable) {
        schedule.getDefinition().setEnableReassignAccount(enable);
    }
    
    public Identity getDefaultAssignee() throws GeneralException {
        return schedule.getDefinition().getDefaultAssignee(context);
    }
    
    public void setDefaultAssignee(Identity defaultAssignee) {
        schedule.getDefinition().setDefaultAssignee(defaultAssignee);
    }

    // MITIGATION / ALLOW EXCEPTION
    public boolean isAllowListBulkMitigate() throws GeneralException {
        return schedule.getDefinition().isAllowListBulkMitigate(context);
    }

    public void setAllowListBulkMitigate(boolean allow) {
        schedule.getDefinition().setAllowListBulkMitigate(allow);
    }

    // REASSIGN
    public boolean isAllowListBulkReassign() throws GeneralException {
        return schedule.getDefinition().isAllowListBulkReassign(context);
    }

    public void setAllowListBulkReassign(boolean allow) {
        schedule.getDefinition().setAllowListBulkReassign(allow);
    }

    public boolean isRequireReassignmentCompletion() throws GeneralException {
        return schedule.getDefinition().isRequireReassignmentCompletion(context);
    }

    public void setRequireReassignmentCompletion(boolean require) {
        schedule.getDefinition().setRequireReassignmentCompletion(require);
    }

    public boolean isAssimilateBulkReassignments() throws GeneralException {
        return schedule.getDefinition().isAssimilateBulkReassignments(context);
    }

    public void setAssimilateBulkReassignments(boolean assimilate) {
        schedule.getDefinition().setAssimilateBulkReassignments(assimilate);
    }

    public boolean isAutomateSignOffOnReassignment() throws GeneralException {
        return schedule.getDefinition().isAutomateSignOffOnReassignment(context);
    }

    public void setAutomateSignOffOnReassignment(boolean automate) {
        schedule.getDefinition().setAutomateSignOffOnReassignment(automate);
    }

    public boolean isIncludeClassifications() throws GeneralException {
        return schedule.getDefinition().isIncludeClassifications(context);
    }

    public void setIncludeClassifications(boolean includeClassifications) {
        schedule.getDefinition().setIncludeClassifications(includeClassifications);
    }

    public boolean isCertificationDelegationReviewRequired() throws GeneralException {
        return schedule.getDefinition().isCertificationDelegationReviewRequired(context);
    }

    public void setCertificationDelegationReviewRequired(boolean required) {
        schedule.getDefinition().setCertificationDelegationReviewRequired(required);
    }

    // Suppress initial notification email
    public boolean isSuppressInitialNotification() throws GeneralException {
        return schedule.getDefinition().isSuppressInitialNotification(context);
    }

    public void setSuppressInitialNotification(boolean suppress) {
        schedule.getDefinition().setSuppressInitialNotification(suppress);
    }

    public String getCertificationEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CERTIFICATION_EMAIL_TEMPLATE);
    }

    public void setCertificationEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CERTIFICATION_EMAIL_TEMPLATE, template);
    }

    public String getMitigationExpirationEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.MITIGATION_EXPIRATION_EMAIL_TEMPLATE);
    }

    public void setMitigationExpirationEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.MITIGATION_EXPIRATION_EMAIL_TEMPLATE, template);
    }

    public String getChallengePeriodStartEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE);
    }

    public void setChallengePeriodStartEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_START_EMAIL_TEMPLATE, template);
    }

    public String getChallengePeriodEndEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE);
    }

    public void setChallengePeriodEndEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CHALLENGE_PERIOD_END_EMAIL_TEMPLATE, template);
    }

    public String getChallengeGenerationEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE);
    }

    public void setChallengeGenerationEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CHALLENGE_GENERATION_EMAIL_TEMPLATE, template);
    }

    public String getCertificationDecisionChallengedEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE);
    }

    public void setCertificationDecisionChallengedEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CERT_DECISION_CHALLENGED_EMAIL_TEMPLATE, template);
    }

    public String getChallengeExpirationEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE);
    }

    public void setChallengeExpirationEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CHALLENGE_EXPIRED_EMAIL_TEMPLATE, template);
    }

    public String getChallengeDecisionExpirationEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE);
    }

    public void setChallengeDecisionExpirationEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CHALLENGE_DECISION_EXPIRED_EMAIL_TEMPLATE, template);
    }

    public String getChallengeAcceptedEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE);
    }

    public void setChallengeAcceptedEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CHALLENGE_ACCEPTED_EMAIL_TEMPLATE, template);
    }

    public String getChallengeRejectedEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE);
    }

    public void setChallengeRejectedEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CHALLENGE_REJECTED_EMAIL_TEMPLATE, template);
    }

    public String getCertificationSignOffApprovalEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.CERT_SIGN_OFF_APPROVAL_EMAIL_TEMPLATE);
    }

    public void setCertificationSignOffApprovalEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.CERT_SIGN_OFF_APPROVAL_EMAIL_TEMPLATE, template);
    }

    public String getBulkReassignmentEmailTemplate() {
        return schedule.getDefinition().getEmailTemplateNameFor(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE);
    }

    public void setBulkReassignmentEmailTemplate(String template) {
        schedule.getDefinition().setEmailTemplateNameFor(Configuration.BULK_REASSIGNMENT_EMAIL_TEMPLATE, template);
    }
    
    public boolean isAutoSignOffWhenNothingToCertify() {
        return schedule.getDefinition().isAutoSignOffWhenNothingToCertify();
    }

    public void setAutoSignOffWhenNothingToCertify( boolean autoSignOffWhenNothingToCertify ) {
        schedule.getDefinition().setAutoSignOffWhenNothingToCertify( autoSignOffWhenNothingToCertify );
    }

    public boolean isSuppressEmailWhenNothingToCertify() {
        return schedule.getDefinition().isSuppressEmailWhenNothingToCertify();
    }

    public void setSuppressEmailWhenNothingToCertify( boolean suppressEmailWhenNothingToCertify ) {
        schedule.getDefinition().setSuppressEmailWhenNothingToCertify( suppressEmailWhenNothingToCertify );
    }

    public Certification.SelfCertificationAllowedLevel getSelfCertificationAllowedLevel() throws GeneralException {
        return schedule.getDefinition().getSelfCertificationAllowedLevel(context);
    }

    public void setSelfCertificationAllowedLevel(Certification.SelfCertificationAllowedLevel level) {
        schedule.getDefinition().setSelfCertificationAllowedLevel(level);
    }

    public Identity getSelfCertificationViolationOwner() throws GeneralException {
        return schedule.getDefinition().getSelfCertificationViolationOwner(context);
    }

    public void setSelfCertificationViolationOwner(Identity owner) {
        schedule.getDefinition().setSelfCertificationViolationsOwner(owner);
    }

    public boolean getShowRecommendations() {
        return Util.nullsafeBoolean(schedule.getDefinition().getShowRecommendations());
    }

    public void setShowRecommendations(boolean showRecommendations) {
        schedule.getDefinition().setShowRecommendations(showRecommendations);
    }

    public boolean isAutoApprove() {
        return Util.nullsafeBoolean(schedule.getDefinition().isAutoApprove());
    }

    public void setAutoApprove(boolean autoApprove) {
        schedule.getDefinition().setAutoApprove(autoApprove);
    }

    /**
     * This class encapsulates information for 
     * new Notifications. This provides the 
     * date in a json format which can be 
     * decoded by the component.
     *
     */
    public static class NotificationInfo {
        private Boolean enabled = null;
        private String data;
        private IDataLoader loader;
        
        public interface IDataLoader {
            void load() throws GeneralException;
            String getData() throws GeneralException;
            Date getStartDate();
            Date getEndDate();
            boolean isEnabled();
        }
        
        public NotificationInfo(IDataLoader loader) throws GeneralException {
            this.loader = loader;
            this.loader.load();
        }
        
        public String getData() throws GeneralException {
            if (data == null) {
                data = loader.getData();
            }
            return data;
        }
        
        public void setData(String val) {
            this.data = val;
        }
        
        public Date getStartDate() {
            return loader.getStartDate();
        }
        
        public void setStartDate(Date val) {
        }
        
        public Date getEndDate() {
            return loader.getEndDate();
        }
        
        public void setEndDate(Date val) {
        }
        
        public boolean isEnabled() {
            if (enabled == null ) {
                enabled = loader.isEnabled();
            }
            return enabled;
        }
        
        public void setEnabled(boolean val) {
            enabled = val;
        }
    }
    
    public NotificationInfo getCertificationNotificationInfo() throws GeneralException {
        
        if (certificationNotificationInfo == null) {
            certificationNotificationInfo = new NotificationInfo(new IDataLoader() {

                private sailpoint.object.NotificationConfig config;
                
                public void load() throws GeneralException {
                    config =  schedule.getDefinition().getCertificationNotificationConfig(context);
                }
                
                public String getData() throws GeneralException {

                    String val = NotificationConfig.createJsonString(context, config);
                    
                    if (val == null) {
                        // so that it doesn't get loaded multiple times
                        val = "";
                    }
                    
                    return val;
                }
                
                public Date getStartDate() {
                    return getFirstExecution();
                }
                
                public Date getEndDate() {
                    if (isChallengePeriodEnabled()) {
                        return getChallengeEndDate();
                    } else {
                        return getActiveEndDate();
                    }
                }
                
                public boolean isEnabled() {
                    
                    if (config != null) {
                        return config.isEnabled();
                    } else {
                        return false;
                    }
                }
            });
        }
        
        return certificationNotificationInfo;
    }
}
