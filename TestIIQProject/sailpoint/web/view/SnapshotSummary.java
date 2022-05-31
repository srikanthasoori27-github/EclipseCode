package sailpoint.web.view;

import java.util.ArrayList;
import java.util.List;

import sailpoint.api.Iconifier.Icon;
import sailpoint.object.Certification;
import sailpoint.object.EntitlementSnapshot;

/**
 * UI-friendly summary of the EntitlementSnapshot.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class SnapshotSummary {

    private String id;
    private String nativeId;
    private String accountDisplayName;
    private String application;
    private String instance;
    private boolean isIIQ;
    private boolean isNew;
    private List<Icon> accountIcons = new ArrayList<Icon>();
    private List<Entitlement> attributes = new ArrayList<Entitlement>();
    private List<Entitlement> permissions = new ArrayList<Entitlement>();
    private List<String> relatedViolations;

    public SnapshotSummary(EntitlementSnapshot snapshot) {
        id = snapshot.getId();
        nativeId = snapshot.getNativeIdentity();
        accountDisplayName = snapshot.getDisplayName();
        application = snapshot.getApplication();
        instance = snapshot.getInstance();
        isIIQ = Certification.IIQ_APPLICATION.equals(application);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNativeId() {
        return nativeId;
    }

    public void setNativeId(String nativeId) {
        this.nativeId = nativeId;
    }

    public String getAccountDisplayName() {
        return accountDisplayName;
    }

    public void setAccountDisplayName(String accountDisplayName) {
        this.accountDisplayName = accountDisplayName;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public boolean isIIQ() {
        return isIIQ;
    }

    public void setIIQ(boolean IIQ) {
        isIIQ = IIQ;
    }
    
    public boolean getIsNew() {
    	return isNew;
    }
    
    public void setIsNew(boolean isNew) {
    	this.isNew = isNew;
    }

    public List<Icon> getAccountIcons() {
        return accountIcons;
    }

    public void setAccountIcons(List<Icon> accountIcons) {
        this.accountIcons = accountIcons;
    }

    public List<Entitlement> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Entitlement> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(Entitlement attribute){
        attributes.add(attribute);
    }

    public List<Entitlement> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Entitlement> permissions) {
        this.permissions = permissions;
    }

    public void addPermissions(Entitlement permission){
        permissions.add(permission);
    }

    public List<String> getRelatedViolations() {
        return relatedViolations;
    }

    public void setRelatedViolations(List<String> relatedViolations) {
        this.relatedViolations = relatedViolations;
    }

    public static class Entitlement{

        public static String TYPE_ATTR = "attribute";
        public static String TYPE_PERM = "permission";

        private String name;
        private String description;
        private boolean group;
        private String type;
        private List<EntitlementValue> values = new ArrayList<EntitlementValue>();

        public Entitlement(String name, boolean group) {
            this.name = name;
            this.description = null;
            this.group = group;
            this.type = TYPE_ATTR;
        }

        public Entitlement(String name, String description) {
            this.name = name;
            this.description = description;
            this.type = TYPE_PERM;
        }

        public void addValue(String value, String displayValue, String description){
            values.add(new EntitlementValue(value, displayValue, description));
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isGroup() {
            return group;
        }

        public String getType() {
            return type;
        }

        public List<EntitlementValue> getValues() {
            return values;
        }
    }

    public static class EntitlementValue{

        private String value;
        private String displayValue;
        private String description;

        public EntitlementValue(String value, String displayValue, String description) {
            this.value = value;
            this.description = description;
            this.displayValue = displayValue;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }
}
