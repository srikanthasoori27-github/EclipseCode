/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;

import java.util.List;
import java.util.ArrayList;

/**
 * Archived record of a CertificationItem which has been excluded from a
 * Certification. The full xml CertificationItem is stored in a clob on the parent
 * ArchivedCertificationEntity. The purpose of this record is really to
 * make the archived data searchable.
 *
 * @author jonathan.bryant@sailpoint.com
 */
@Indexes({
	@Index(name="spt_arch_cert_item_apps_name", column="application_name", table="spt_arch_cert_item_apps")
})
public class ArchivedCertificationItem extends SailPointObject{

    private String itemId;
    private ArchivedCertificationEntity parent;
    private List<EntitlementSnapshot> entitlements;
    private CertificationItem.Type type;
    private CertificationItem.SubType subType;

    private String bundle;

    private String exceptionApplication;
    private String exceptionAttributeName;
    private String exceptionAttributeValue;
    private String exceptionPermissionTarget;
    private String exceptionPermissionRight;
    private String exceptionNativeIdentity;

    private String policy;
    private String constraintName;
    private String violationSummary;

    private String targetId;
    private String targetName;
    private String targetDisplayName;

    /**
     * A list of the application names for this certification item.
     */
    List<String> applicationNames;

    public ArchivedCertificationItem() {
    }

    public ArchivedCertificationItem(CertificationItem item, ArchivedCertificationEntity parent) {

        this.parent = parent;

        itemId = item.getId();
        type = item.getType();
        subType = item.getSubType();
        bundle = item.getBundle();
        violationSummary = item.getViolationSummary();

        targetId = item.getTargetId();
        targetName = item.getTargetName();
        targetDisplayName = item.getTargetDisplayName();

        exceptionApplication = item.getExceptionApplication();
        exceptionAttributeName = item.getExceptionAttributeName();
        exceptionAttributeValue = item.getExceptionAttributeValue();
        exceptionPermissionTarget = item.getExceptionPermissionTarget();
        exceptionPermissionRight = item.getExceptionPermissionRight();
        type = item.getType();

        if (item.getExceptionEntitlements() != null)
            exceptionNativeIdentity = item.getExceptionEntitlements().getNativeIdentity();

        if (item.getBundleEntitlements() != null && !item.getBundleEntitlements().isEmpty()){
            entitlements = item.getBundleEntitlements();
        }

        if (item.getExceptionEntitlements() != null){
            if (entitlements == null)
                entitlements = new ArrayList<EntitlementSnapshot>();
            entitlements.add(item.getExceptionEntitlements());
        }

        if (!Util.isEmpty(item.getApplicationNames())){
            applicationNames = new ArrayList<String>(item.getApplicationNames());
        }
    }

    public ArchivedCertificationEntity getParent() {
        return parent;
    }

    public void setParent(ArchivedCertificationEntity parent) {
        this.parent = parent;
    }

    public CertificationItem getCertificationItem(){
        return parent.getEntity().getItem(itemId);
    }

    @XMLProperty
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    @XMLProperty(mode= SerializationMode.LIST,xmlname="Entitlements")
    public List<EntitlementSnapshot> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(List<EntitlementSnapshot> entitlements) {
        this.entitlements = entitlements;
    }

    @XMLProperty
    public CertificationItem.Type getType() {
        return type;
    }

    public void setType(CertificationItem.Type type) {
        this.type = type;
    }

    @XMLProperty
    public CertificationItem.SubType getSubType() {
        return subType;
    }

    public void setSubType(CertificationItem.SubType subType) {
        this.subType = subType;
    }

    @XMLProperty
    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    @XMLProperty
    public String getExceptionApplication() {
        return exceptionApplication;
    }

    public void setExceptionApplication(String exceptionApplication) {
        this.exceptionApplication = exceptionApplication;
    }

    @XMLProperty
    public String getExceptionAttributeName() {
        return exceptionAttributeName;
    }

    public void setExceptionAttributeName(String exceptionAttributeName) {
        this.exceptionAttributeName = exceptionAttributeName;
    }

    @XMLProperty
    public String getExceptionAttributeValue() {
        return exceptionAttributeValue;
    }

    public void setExceptionAttributeValue(String exceptionAttributeValue) {
        this.exceptionAttributeValue = exceptionAttributeValue;
    }

    @XMLProperty
    public String getExceptionPermissionTarget() {
        return exceptionPermissionTarget;
    }

    public void setExceptionPermissionTarget(String exceptionPermissionTarget) {
        this.exceptionPermissionTarget = exceptionPermissionTarget;
    }

    @XMLProperty
    public String getExceptionPermissionRight() {
        return exceptionPermissionRight;
    }

    public void setExceptionPermissionRight(String exceptionPermissionRight) {
        this.exceptionPermissionRight = exceptionPermissionRight;
    }

    @XMLProperty
    public String getExceptionNativeIdentity() {
        return exceptionNativeIdentity;
    }

    public void setExceptionNativeIdentity(String exceptionNativeIdentity) {
        this.exceptionNativeIdentity = exceptionNativeIdentity;
    }

    @XMLProperty
    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    @XMLProperty
    public String getConstraintName() {
        return constraintName;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    @XMLProperty
    public String getViolationSummary() {
        return violationSummary;
    }

    public void setViolationSummary(String violationSummary) {
        this.violationSummary = violationSummary;
    }

    @XMLProperty
    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    @XMLProperty
    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    @XMLProperty
    public String getTargetDisplayName() {
        return targetDisplayName;
    }

    public void setTargetDisplayName(String targetDisplayName) {
        this.targetDisplayName = targetDisplayName;
    }

    public List<String> getApplicationNames() {
        return applicationNames;
    }

    @XMLProperty
    public void setApplicationNames(List<String> applicationNames) {
        this.applicationNames = applicationNames;
    }
    
    @Override
    public boolean hasName() {
        return false;
    }
}
