/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.api.certification.CertificationDecisioner;
import sailpoint.api.CertificationService;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Identity;
import sailpoint.object.SignOffHistory;
import sailpoint.object.Tag;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.certification.CertificationBulkActionBean;
import sailpoint.web.certification.CertificationUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.workitem.WorkItemUtil;

/**
 * Certification DTO
 *
 * @author patrick.jeong
 */
public class CertificationDTO {

    private String id;
    private CertificationEntity.Type entityType;
    private String name;
    private Certification.Type type;
    private String shortName;
    private List<String> certifiers = new ArrayList<String>();
    private String creator;
    private Boolean editable = false;
    private Date expiration;
    private Certification.Phase phase;
    private Date nextPhaseTransition;
    private List<String> tags = new ArrayList<String>();
    private List<BulkAction> availableBulkDecisions = new ArrayList<BulkAction>();
    private Integer certificationCount;
    private Integer remediationsCompleted;
    private Integer remediationsStarted;
    private int completedEntities;
    private int totalEntities;
    private CertificationItemStatusCount itemStatusCount = new CertificationItemStatusCount();
    private String signoffBlockedReason;
    private boolean complete;
    private List<CertificationSignoffDTO> signoffs = new ArrayList<CertificationSignoffDTO>();
    private String workItemId;
    private Boolean esigned;
    private String applicationName;
    private int autoApprovedCount;

    /**
     * This is to determine if the certification has been signed off completely. When a certification
     * is created using the Sign Off Approver Rule it allows more than one manager to sign off on
     * the certification. This will return true only after the final signOff is completed.
     */
    private Boolean signOffComplete = false;

    /**
     * Constructor
     *
     * @param certification Certification object
     * @param userContext UserContext
     * @throws GeneralException
     */
    public CertificationDTO(Certification certification, UserContext userContext)
            throws GeneralException {
        id = certification.getId();
        entityType = (certification.getType() != null) ? certification.getType().getEntityType() : null;
        name = certification.getName();
        type = certification.getType();
        shortName = certification.getShortName();
        certifiers = CertificationUtil.getCertifiersDisplayNames(userContext.getContext(),
                certification.getCertifiers());
        Identity creatorIdentity = certification.getCreator(userContext.getContext());
        creator = (creatorIdentity != null) ? creatorIdentity.getDisplayableName() : certification.getCreator();
        expiration = certification.getExpiration();
        phase = certification.getPhase();
        nextPhaseTransition = certification.getNextPhaseTransition();
        certificationCount = certification.getCertificationCount(userContext.getContext());
        remediationsCompleted = certification.getRemediationsCompleted();
        remediationsStarted = certification.getRemediationsKickedOff();
        completedEntities = certification.getCompletedEntities();
        totalEntities = certification.getTotalEntities();
        signOffComplete = certification.getSigned() != null;
        esigned = certification.isElectronicallySigned();

        for (Tag tag : Util.safeIterable(certification.getTags())) {
            tags.add(tag.getName());
        }

        for (SignOffHistory signOffHistory : Util.safeIterable(certification.getSignOffHistory())) {
            signoffs.add(new CertificationSignoffDTO(signOffHistory));
        }

        editable = CertificationUtil.isEditable(userContext, certification);

        if (editable && !signOffComplete) {
            populateAvailableBulkDecisions(userContext, certification);
        }

        itemStatusCount = CertificationUtil.getItemStatusCount(userContext, certification, null);
        autoApprovedCount = CertificationUtil.getAutoApprovalCount(userContext, certification.getId());
        complete = certification.isComplete();
        signoffBlockedReason = new CertificationService(userContext.getContext()).getSignOffBlockedReason(certification);

        List<WorkItem> items = certification.getWorkItems();
        if (null != items) {
            this.workItemId = WorkItemUtil.getWorkItemId(items, userContext.getLoggedInUser());
        }

        Application application = certification.getApplication(userContext.getContext());
        if (application != null) {
            applicationName = application.getName();
        }
    }

    /**
     * Get available bulk decisions for logged in user
     */
    private void populateAvailableBulkDecisions(UserContext userContext, Certification certification) throws GeneralException {
        List<String> bulkDecisions = CertificationUtil.getAvailableBulkDecisions(userContext, certification);

        if (bulkDecisions.contains(CertificationUtil.ALLOW_BULK_APPROVE)) {
            this.availableBulkDecisions.add(new BulkAction(CertificationAction.Status.Approved.name(),
                    MessageKeys.CERT_DECISION_APPROVE));
        }

        if (bulkDecisions.contains(CertificationUtil.ALLOW_BULK_REVOKE)) {
            this.availableBulkDecisions.add(new BulkAction(CertificationAction.Status.Remediated.name(),
                    MessageKeys.CERT_DECISION_REMEDIATE));
        }

        if (bulkDecisions.contains(CertificationUtil.ALLOW_BULK_REVOKE_ACCOUNT)) {
            this.availableBulkDecisions.add(new BulkAction(CertificationAction.Status.RevokeAccount.name(),
                    MessageKeys.CERT_DECISION_REVOKE_ALL_ACCOUNTS));
        }

        if (bulkDecisions.contains(CertificationUtil.ALLOW_BULK_MITIGATE)) {
            this.availableBulkDecisions.add(new BulkAction(CertificationAction.Status.Mitigated.name(),
                    MessageKeys.CERT_DECISION_MITIGATE));
        }

        if (bulkDecisions.contains(CertificationUtil.ALLOW_BULK_REASSIGN)) {
            this.availableBulkDecisions.add(new BulkAction(CertificationDecisioner.STATUS_REASSIGN,
                    MessageKeys.CERT_DECISION_BULK_REASSIGN));
        }

        if (bulkDecisions.contains(CertificationUtil.ALLOW_BULK_SAVE_CUSTOM_ENTITY_FIELDS)) {
            this.availableBulkDecisions.add(new BulkAction(CertificationBulkActionBean.ACTION_SAVE_ENTITY_CUSTOM_FIELDS,
                    MessageKeys.CERT_DECISION_BULK_SAVE_ENTITY_CUSTOM_FIELDS));
        }

        if (bulkDecisions.contains(CertificationUtil.ALLOW_BULK_UNDO)) {
            this.availableBulkDecisions.add(new BulkAction(CertificationDecisioner.STATUS_UNDO,
                    MessageKeys.CERT_DECISION_BULK_UNDO));
        }
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * Bulk decision data holder
     */
    public class BulkAction {
        private String key;
        private String value;

        public BulkAction(String value, String key) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    public String getId() {
        return id;
    }

    public Certification.Type getType() {
        return type;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public List<String> getCertifiers() {
        return certifiers;
    }
    
    public Boolean getEditable() {
        return editable;
    }

    public Date getExpiration() {
        return expiration;
    }

    public Certification.Phase getPhase() {
        return phase;
    }

    public Date getNextPhaseTransition() {
        return nextPhaseTransition;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<BulkAction> getAvailableBulkDecisions() {
        return availableBulkDecisions;
    }

    public Integer getCertificationCount() {
        return certificationCount;
    }

    public Integer getRemediationsCompleted() {
        return remediationsCompleted;
    }

    public Integer getRemediationsStarted() {
        return remediationsStarted;
    }

    public int getCompletedEntities() {
        return completedEntities;
    }

    public int getTotalEntities() {
        return totalEntities;
    }

    public CertificationItemStatusCount getItemStatusCount() {
        return itemStatusCount;
    }

    public String getSignoffBlockedReason() {
        return signoffBlockedReason;
    }
    
    public boolean isComplete() {
        return this.complete;
    }
    
    public List<CertificationSignoffDTO> getSignoffs() {
        return this.signoffs;
    }

    public Boolean getSignOffComplete() {
        return signOffComplete;
    }

    public boolean isEsigned() {
        return esigned;
    }

    public void setEsigned(boolean esigned) {
        this.esigned = esigned;
    }

    public CertificationEntity.Type getEntityType() {
        return entityType;
    }

    public void setEntityType(CertificationEntity.Type entityType) {
        this.entityType = entityType;
    }

    /**
     * @return the application name
     */
    public String getApplicationName() {
        return applicationName;
    }

    public int getAutoApprovedCount() { return autoApprovedCount; }
}
