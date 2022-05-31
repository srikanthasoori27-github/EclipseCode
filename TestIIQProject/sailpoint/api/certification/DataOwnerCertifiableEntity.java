package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationEntity;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class DataOwnerCertifiableEntity extends AbstractCertifiableEntity {

    private static final long serialVersionUID = 1L;
    private static final Log log = LogFactory.getLog(DataOwnerCertifiableEntity.class);
    private final DataItem dataItem;

    public static class DataItem {

        private String applicationName;
        private String id;
        private String schemaObjectType;
        private String type;
        private String name;
        private String value;
        private String displayableValue;

        public DataItem() {

        }

        public String getId() {
            return this.id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getApplicationName() {
            return this.applicationName;
        }

        public void setApplicationName(String val) {
            this.applicationName = val;
        }

        public String getType() {
            return this.type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSchemaObjectType() {
            return schemaObjectType;
        }

        public void setSchemaObjectType(String schemaObjectType) {
            this.schemaObjectType = schemaObjectType;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return this.value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDisplayableValue() {
            if (null != displayableValue) {
                return this.displayableValue;
            }
            return this.value;
        }

        public void setDisplayableValue(String displayableValue) {
            this.displayableValue = displayableValue;
        }

        @Override
        public String toString() {
            return
                    ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    public DataOwnerCertifiableEntity(DataItem dataItem) {

        this.dataItem = dataItem;

        // just for debug purpose
        setName(String.format("%s:%s", dataItem.getName(), dataItem.getValue()));
    }

    public static DataOwnerCertifiableEntity createFromCertificationEntity(CertificationEntity certEntity)
            throws GeneralException {
        return createFromCertificationEntity(SailPointFactory.getCurrentContext(), certEntity);
    }

    public static DataOwnerCertifiableEntity createFromCertificationEntity(SailPointContext context, CertificationEntity certEntity)
            throws GeneralException {
        //MEH 16785, certEntity can be null
        if (certEntity != null) {
            DataItem item = new DataItem();
            item.setApplicationName(certEntity.getApplication());
            item.setId(certEntity.getTargetId());
            item.setSchemaObjectType(certEntity.getSchemaObjectType());
            item.setType(certEntity.getReferenceAttribute());
            item.setName(certEntity.getTargetName());
            item.setValue(certEntity.getNativeIdentity());
            if (null != context) {
                ManagedAttribute ma = certEntity.getAccountGroup(context);
                if (null != ma) {
                    item.setDisplayableValue(ma.getDisplayableName());
                }
            }

            return new DataOwnerCertifiableEntity(item);
        } else {
            return null;
        }
    }

    public Filter createCertificationEntityFilter()
            throws GeneralException {

        return Filter.and(
                Filter.eq("application", this.dataItem.getApplicationName()),
                Filter.eq("targetId", this.dataItem.getId()),
                Filter.eq("referenceAttribute", this.dataItem.getType().toString()),
                Filter.eq("targetName", this.dataItem.getName()),
                Filter.eq("nativeIdentity", this.dataItem.getValue()));
    }

    public boolean matches(CertificationEntity certEntity)
            throws GeneralException {

        //MEH 16785 watch out for a null certEntity in case of exclusion rules on continuous dataowner certs
        if (certEntity != null)
            return createFromCertificationEntity(certEntity).equals(this);

        return false;
    }

    public DataItem getDataItem() {
        return this.dataItem;
    }

    public String getDisplayName(SailPointContext context, Locale locale)
            throws GeneralException {

        String messageKey = null;
        if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(this.dataItem.getType())) {
            messageKey = MessageKeys.ENT_SNAP_PERM_VAL_SUMMARY;
        } else {
            messageKey = MessageKeys.ENT_SNAP_ATTR_VAL_SUMMARY;
        }

        return new Message(messageKey,
                "",
                this.dataItem.getDisplayableValue(),
                this.dataItem.getName())
                .getLocalizedMessage(locale, null);
    }

    public String getDisplayDescription(SailPointContext context, Locale locale)
            throws GeneralException {

        String messageKey = null;
        String description = null;

        Application app = context.getObjectByName(Application.class, this.dataItem.getApplicationName());

        if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(this.dataItem.getType())) {
            messageKey = MessageKeys.ENT_SNAP_PERM_VAL_SUMMARY;
            description = Explanator.getPermissionDescription(app, this.dataItem.getName(), locale);
        } else {
            messageKey = MessageKeys.ENT_SNAP_ATTR_VAL_SUMMARY;
            description = Explanator.getDescription(app, this.dataItem.getName(), this.dataItem.getDisplayableValue(), locale);
        }

        if (Util.isNullOrEmpty(description)) {
            description = getDisplayName(context, locale);
        }

        return new Message(messageKey,
                "",
                description,
                this.dataItem.getName())
                .getLocalizedMessage(locale, null);

    }

    // one item with an entitlement, or permission
    public EntitlementSnapshot createEntitlement() {

        EntitlementSnapshot snapshot = new EntitlementSnapshot();

        snapshot.setApplication(this.dataItem.getApplicationName());
        if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(this.dataItem.getType())) {
            List<Permission> permissions = new ArrayList<Permission>();
            Permission p = new Permission(this.dataItem.getValue(), this.dataItem.getName());
            permissions.add(p);
            snapshot.setPermissions(permissions);
        } else {
            Attributes<String, Object> attrs = new Attributes<String, Object>();
            attrs.put(this.dataItem.getName(), this.dataItem.getValue());
            snapshot.setAttributes(attrs);
        }

        return snapshot;
    }

    @Override
    public String getFullName() {

        try {
            EntitlementSnapshot snapshot = createEntitlement();
            Message msg = EntitlementDescriber.summarize(snapshot);
            return msg != null ? msg.getLocalizedMessage(Locale.getDefault(), null) : null;
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);

            return "Error getting fullname";
        }
    }

    @Override
    public String getTypeName(boolean plural) {
        return "Data Owner Certification" + (plural ? "s" : "");
    }

    @Override
    public boolean isDifferencable() {
        return false;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (o instanceof DataOwnerCertifiableEntity) {
            DataOwnerCertifiableEntity other = (DataOwnerCertifiableEntity) o;
            EqualsBuilder builder = new EqualsBuilder();
            builder.append(getDataItem().getApplicationName(), other.getDataItem().getApplicationName());
            builder.append(getDataItem().getId(), other.getDataItem().getId());
            builder.append(getDataItem().getType(), other.getDataItem().getType());
            builder.append(getDataItem().getName(), other.getDataItem().getName());
            builder.append(getDataItem().getValue(), other.getDataItem().getValue());
            return builder.isEquals();
        }

        return false;
    }

    @Override
    public int hashCode() {

        return new HashCodeBuilder()
                .append(getDataItem().getApplicationName())
                .append(getDataItem().getId())
                .append(getDataItem().getType())
                .append(getDataItem().getName())
                .append(getDataItem().getValue())
                .toHashCode();
    }

}
