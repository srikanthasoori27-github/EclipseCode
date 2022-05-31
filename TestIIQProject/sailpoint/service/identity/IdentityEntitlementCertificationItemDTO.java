/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.identity;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.service.BaseDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * IdentityEntitlement object contain references to CertificationItem objects so that they can reference
 * current and past certification items that have affected the entitlement.  This DTO is a representation
 * of those certification items
 */
public class IdentityEntitlementCertificationItemDTO extends BaseDTO {

    /**
     * Type of CertificationItem
     */
    private CertificationItem.Type type;

    /**
     * Sub-type of CertificationItem.
     */
    private CertificationItem.SubType subType;

    /**
     * Display name of the object being certified
     */
    private String displayName;

    /**
     * Description of the object being certified
     */
    private String description;

    /**
     * The date of the certification associated with this item
     */
    private Date certificationStartDate;

    /**
     * The date that the certification was finished
     */
    private Date certificationFinishDate;

    /**
     * The date that the mitigations on this item expire
     */
    private Date certificationMitigationExpirationDate;

    /**
     * The name of the certification associated with this item
     */
    private String certificationName;

    /**
     * The name(s) of the identity(s) that ceritifed this item
     */
    private String certifier;

    /**
     * The granularity of the certification
     */
    private Certification.EntitlementGranularity certificationGranularity;

    /**
     * Resolve this from the action, but fall back to the action?
     */
    private String localizedActionStatus;


    /**
     *
     * Create a certification item dto from an existing CertificationItem
     * @param item CertificationItem source of the dto
     * @param context SailPointContext to use for querying
     * @throws GeneralException
     */
    public IdentityEntitlementCertificationItemDTO(CertificationItem item, SailPointContext context, UserContext userContext) throws GeneralException {
        this.type = item.getType();
        this.subType = item.getSubType();
        this.displayName = item.getTargetDisplayName();
        this.description = item.getDescription();

        // Load the certification information including the date, name, and certifiers
        CertificationEntity entity = item.getParent();
        if ( entity != null ) {
            Certification cert = entity.getCertification();
            if (cert != null) {
                this.certificationStartDate = cert.getCreated();
                this.certificationName = cert.getName();
                this.certificationGranularity = cert.getEntitlementGranularity();

                List<String> certifiers = cert.getCertifiers();
                IdentityService identityService = new IdentityService(context);
                if ( Util.size(certifiers) > 0 )  {
                    List<String> certifiersDisplayNames = new ArrayList<String>();
                    for ( String idName : certifiers ) {
                        String certifierDisplayName = identityService.resolveIdentityDisplayName(idName);
                        if ( certifierDisplayName != null ) {
                            certifiersDisplayNames.add(certifierDisplayName);
                        } else {
                            certifiersDisplayNames.add(idName);
                        }
                    }
                    if ( certifiersDisplayNames.size() > 0 )
                        certifier = Util.listToCsv(certifiersDisplayNames);
                }
            }
        }

        AbstractCertificationItem.Status status = item.getSummaryStatus();
        CertificationAction action = item.getAction();
        if ( action != null ) {
            this.certificationFinishDate = action.getDecisionDate();
            this.certificationMitigationExpirationDate = action.getMitigationExpiration();

            CertificationAction.Status actionStatus = action.getStatus();
            if ( action != null ) {
                localizedActionStatus = actionStatus.getMessageKey();
                if ( localizedActionStatus != null ) {
                    Message msg = new Message(localizedActionStatus);
                    localizedActionStatus = msg.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
                }
            }
            if ( localizedActionStatus == null && status != null )
                localizedActionStatus = status.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone());
        }
    }

    public CertificationItem.Type getType() {
        return type;
    }

    public void setType(CertificationItem.Type type) {
        this.type = type;
    }

    public CertificationItem.SubType getSubType() {
        return subType;
    }

    public void setSubType(CertificationItem.SubType subType) {
        this.subType = subType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getCertificationStartDate() {
        return certificationStartDate;
    }

    public void setCertificationStartDate(Date certificationStartDate) {
        this.certificationStartDate = certificationStartDate;
    }

    public Date getCertificationFinishDate() {
        return certificationFinishDate;
    }

    public void setCertificationFinishDate(Date certificationFinishDate) {
        this.certificationFinishDate = certificationFinishDate;
    }

    public Date getCertificationMitigationExpirationDate() {
        return certificationMitigationExpirationDate;
    }

    public void setCertificationMitigationExpirationDate(Date certificationMitigationExpirationDate) {
        this.certificationMitigationExpirationDate = certificationMitigationExpirationDate;
    }

    public String getCertificationName() {
        return certificationName;
    }

    public void setCertificationName(String certificationName) {
        this.certificationName = certificationName;
    }

    public String getCertifier() {
        return certifier;
    }

    public void setCertifier(String certifier) {
        this.certifier = certifier;
    }

    public Certification.EntitlementGranularity getCertificationGranularity() {
        return certificationGranularity;
    }

    public void setCertificationGranularity(Certification.EntitlementGranularity certificationGranularity) {
        this.certificationGranularity = certificationGranularity;
    }

    public String getLocalizedActionStatus() {
        return localizedActionStatus;
    }

    public void setLocalizedActionStatus(String localizedActionStatus) {
        this.localizedActionStatus = localizedActionStatus;
    }
}
