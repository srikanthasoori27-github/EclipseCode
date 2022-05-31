/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.List;
import java.util.ArrayList;


/**
 * An ArchivedCertificationEntity is a CertificationEntity that is not a part of
 * the live, operational model of a certification.  These cannot be acted upon
 * (approved, delegated, etc...) but are retained for reporting and
 * historical purposes.
 *
 */
@XMLClass
public class ArchivedCertificationEntity extends SailPointObject {

    /**
     * Enumeration of reasons an entity is archived in a certification.
     */
    @XMLClass
    public static enum Reason {

        /**
         * The entity was excluded by an exclusion rule.
         */
        Excluded(MessageKeys.ARCH_ENTITY_REASON_EXCLUDED),

        /**
         * The AbstractCertifiableEntity referenced by the CertificationEntity
         * has been deleted.
         */
        Deleted(MessageKeys.ARCH_ENTITY_REASON_DELETED),

        /**
         * The AbstractCertifiableEntity referenced by the CertificationEntity
         * is now inactive.
         */
        Inactive(MessageKeys.ARCH_ENTITY_REASON_INACTIVE);

        private String messageKey;


        Reason(String messageKey) {
            this.messageKey = messageKey;
        }


        public String getMessageKey() {
            return messageKey;
        }
    }
    
    /**
     * A back-pointer to the owning certification.
     */
    private Certification certification;

    /**
     * The archived CertificationEntity.
     */
    private CertificationEntity entity;

    /**
     * The reason that this entity is archived.
     */
    private Reason reason;

    /**
     * An optional explanation about an exclusion that can be set by an
     * exclusion rule.
     */
    private String explanation;

    /**
     * The name of the role being certified.
     */
    private String targetName;

    /**
     * Display name of the target object
     */
    private String targetDisplayName;

    // These fields are copied out of the CertificationEntity and are used to
    // uniquely identify an entity within a certification.  These are used by
    // the filter returned by CertificationEntity.getEqualsFilter().
    private String identity;
    private String accountGroup;
    private String application;
    private String nativeIdentity;
    private String referenceAttribute;
    private String schemaObjectType;
    private String targetId;

    private List<ArchivedCertificationItem> items;

    // TODO: Think about adding some fields that can get denormalized out of the
    // entity to allow for easier querying in reports.
    

    /**
     * Default constructor.
     */
    public ArchivedCertificationEntity() {}
    
    /**
     * Constructor.
     */
    public ArchivedCertificationEntity(CertificationEntity entity, Reason reason,
                                       String explanation) {
        this.entity = entity;
        this.reason = reason;
        this.explanation = explanation;

        // Copy search fields out of the entity.
        this.identity = entity.getIdentity();
        this.accountGroup = entity.getAccountGroup();
        this.application = entity.getApplication();
        this.nativeIdentity = entity.getNativeIdentity();
        this.referenceAttribute = entity.getReferenceAttribute();
        this.schemaObjectType = entity.getSchemaObjectType();
        this.targetId = entity.getTargetId();
        this.targetName = entity.getTargetName();
        this.targetDisplayName = entity.getTargetDisplayName();

        if (entity.getItems() != null){
            items = new ArrayList<ArchivedCertificationItem>();
            for(CertificationItem item : entity.getItems()){
                addItem(item);
            }
        }
    }

    @Override
    public boolean hasName() {
        return false;
    }
    
    /**
     * A back-pointer to the owning certification.
     */
    public Certification getCertification() {
        return this.certification;
    }

    public void setCertification(Certification certification) {
        this.certification = certification;
    }

    /**
     * The archived CertificationEntity.
     */
    @XMLProperty
    public CertificationEntity getEntity() {
        return entity;
    }

    public void setEntity(CertificationEntity entity) {
        this.entity = entity;
    }

    @XMLProperty
    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getTargetDisplayName() {
        return targetDisplayName;
    }

    @XMLProperty
    public void setTargetDisplayName(String targetDisplayName) {
        this.targetDisplayName = targetDisplayName;
    }

    /**
     * The reason that this entity is archived.
     */
    @XMLProperty
    public Reason getReason() {
        return reason;
    }

    public void setReason(Reason reason) {
        this.reason = reason;
    }

    /**
     * Return the explanation for an exclusion (provided by the exclusion rule).
     */
    @XMLProperty
    public String getExplanation() {
        return this.explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    

    @XMLProperty
    public String getIdentity() {
        return this.identity;
    }

    public void setIdentity(String name) {
        this.identity = name;
    }

    @XMLProperty
    public String getAccountGroup() {
        return accountGroup;
    }

    public void setAccountGroup(String accountGroup) {
        this.accountGroup = accountGroup;
    }

    @XMLProperty
    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    @XMLProperty
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    @XMLProperty
    public String getReferenceAttribute() {
        return referenceAttribute;
    }

    public void setReferenceAttribute(String referenceAttribute) {
        this.referenceAttribute = referenceAttribute;
    }

    @XMLProperty
    public String getSchemaObjectType() {
        return schemaObjectType;
    }

    public void setSchemaObjectType(String schemaObjectType) {
        this.schemaObjectType = schemaObjectType;
    }

    @XMLProperty
    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public List<ArchivedCertificationItem> getItems() {
        return items;
    }

    public void setItems(List<ArchivedCertificationItem> items) {
        this.items = items;
    }

    public void addItem(CertificationItem item){
        if (items == null)
            items = new ArrayList<ArchivedCertificationItem>();
        // give the items a uid so we can look them up later
        if (item.getId() == null){
            item.setId(Util.uuid());
        }
        items.add(new ArchivedCertificationItem(item, this));
    }

    /**
     * Convenience method used to retrieve name of the
     * entity being certified regardless of type.
     * @return The name of the entity being certified.
     */
    public String getEntityName(){
        if (identity != null){
            return identity;
        } else if (accountGroup != null){
            return accountGroup;
        } else {
            return targetName;
        }
    }
}
