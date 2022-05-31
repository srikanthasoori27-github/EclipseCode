package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sailpoint.object.CertificationAction;
import sailpoint.object.IdentitySelector;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class PolicyTreeNode {

    @XMLClass
    public static class PolicyTreeNodeState{

        private String associatedItemId;
        private String associatedEntityId;
        private CertificationAction.Status action;

        public PolicyTreeNodeState(String associatedItemId, String associatedEntityId, CertificationAction.Status action) {
            this.associatedItemId = associatedItemId;
            this.associatedEntityId = associatedEntityId;
            this.action = action;
        }

        public PolicyTreeNodeState() { }

        @XMLProperty
        public String getAssociatedItemId() {
            return associatedItemId;
        }

        public void setAssociatedItemId(String associatedItemId) {
            this.associatedItemId = associatedItemId;
        }

        @XMLProperty
        public String getAssociatedEntityId() {
            return associatedEntityId;
        }

        public void setAssociatedEntityId(String associatedEntityId) {
            this.associatedEntityId = associatedEntityId;
        }

        @XMLProperty
        public CertificationAction.Status getAction() {
            return action;
        }

        public void setAction(CertificationAction.Status action) {
            this.action = action;
        }
    }

    public static String TYPE_APPLICATION = "Application";
    public static String TYPE_TARGET_SOURCE = "TargetSource";

    public PolicyTreeNode() { }


    /**
     * Internal node Constructor
     * @param operator
     */
    public PolicyTreeNode( Operator operator ) {
        this.operator = operator;
        this.application = null;
        this.name = null;
        this.value = null;
        this.displayValue = null;
    }
    
    /**
     * Terminal node Constructor
     * @param application The application name
     * @param name The entitlement name
     * @param value The entitlement value
     */
    public PolicyTreeNode( String application, String name, String value, String applicationId, boolean permission ) {
        this.application = application;
        this.name = name;
        this.value = value;
        this.applicationId = applicationId;
        this.permission = permission;
        this.displayValue = "";
        this.operator = null;
    }

    public PolicyTreeNode(String name, String value, boolean permission, String type,
                          List<IdentitySelector.MatchTerm.ContributingEntitlement> contributingEntitlements) {
        this.name = name;
        this.value = value;
        this.permission = permission;
        this.type = type;
        this.contributingEntitlements = contributingEntitlements;
    }

    /**
     * Terminal node Constructor
     * @param application The application name
     * @param name The entitlement name
     * @param value The entitlement value
     */
    public PolicyTreeNode(String application, String name, String value, String applicationId, boolean permission,
                          String type, List<IdentitySelector.MatchTerm.ContributingEntitlement> contributingEntitlements) {
        this(application, name, value, applicationId, permission);
        this.type = type;
        this.contributingEntitlements = contributingEntitlements;
    }
    
    public boolean isAndOp() {
        return Operator.AND.equals( operator );
    }
    
    public void setSelected( boolean selected ) {
        this.selected = selected;
    }

    @XMLProperty
    public boolean isSelected() {
        return selected;
    }
    
    public List<PolicyTreeNode> getChildren() {
        return children;
    }
    
    public int getChildCount() {
        return children.size();
    }

    /**
     * bug#12724 : insert leaf nodes at end so that it looks pretty in the UI
     * 
     * @param child
     */
    public void add( PolicyTreeNode child ) {
    	if (child.isLeaf()) {
    		children.add(child);
    	}
    	else {
    		children.add(0, child);
    	}
    }
    
    public boolean isLeaf() {
        return operator == null;
    }
  
    @XMLProperty
    public String getApplication() {
        return application;
    }

    @XMLProperty
    public String getValue() {
        return value;
    }

    @XMLProperty
    public String getName() {
        return name;
    }

    @XMLProperty
    public Operator getOperator() {
        return operator;
    }
    
    public void setChildren( List<PolicyTreeNode> children ) {
        this.children = children;
    }

    public void setOperator( Operator operator ) {
        this.operator = operator;
    }

    public void setApplication( String application ) {
        this.application = application;
    }

    public void setValue( String value ) {
        this.value = value;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public void setApplicationId( String applicationId ) {
        this.applicationId = applicationId;
    }

    @XMLProperty
    public String getApplicationId() {
        return applicationId;
    }

    /**
     * Type of the source. With the addition of TargetPermissions, these can now be TargetSource. This will still use
     * application and applicationId to hold the name and ID, but if type == TargetSource, this will map to a TargetSource
     * @param type
     */
    public void setSourceType(String type) { sourceType = type; }

    @XMLProperty
    public String getSourceType() { return sourceType; }

    @XMLProperty
    public boolean isPermission() {
        return permission;
    }
    
    public void setPermission( boolean permission ) {
        this.permission = permission;
    }

    public List<PolicyTreeNodeState> getStatus() {
        return status;
    }

    public void setStatus(List<PolicyTreeNodeState> status) {
        this.status = status;
    }

    public void addStatus(PolicyTreeNodeState neu){

        if (this.status==null)
            this.status = new ArrayList<PolicyTreeNodeState>();

         status.add(neu);
    }

    @XMLProperty
    public Set<String> getApplicationNames(Set<String> apps){

        if (apps == null)
            apps = new HashSet<String>();

        apps.add(this.application);

        for(PolicyTreeNode child : this.getChildren()){
            child.getApplicationNames(apps);
        }

        return apps;
    }

    @XMLProperty
    public String getDisplayValue() {
		return displayValue;
	}

	public void setDisplayValue(String displayValue) {
		this.displayValue = displayValue;
	}

    @XMLProperty
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XMLProperty
    public String getType() { return type; }

    public void setType(String t) { this.type = t; }

    @XMLProperty(mode = SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<IdentitySelector.MatchTerm.ContributingEntitlement> getContributingEntitlements() {
        return contributingEntitlements;
    }

    public void setContributingEntitlements(List<IdentitySelector.MatchTerm.ContributingEntitlement> ents) {
        this.contributingEntitlements = ents;
    }

    public boolean isEffective() {
        return (!Util.isEmpty(this.getContributingEntitlements()));
    }

    @Override
    public boolean equals( Object obj ) {
        if( !( obj instanceof PolicyTreeNode ) )
            return false;
        PolicyTreeNode that = ( PolicyTreeNode ) obj;
        boolean response = true;
        response &= Util.nullSafeEq( this.application, that.getApplication(), true );
        response &= Util.nullSafeEq( this.name, that.getName(), true );
        response &= Util.nullSafeEq( this.value, that.getValue(), true );
        response &= Util.nullSafeEq( this.permission, that.isPermission(), true );
        response &= Util.nullSafeEq( this.isLeaf(), that.isLeaf(), true );
        response &= Util.nullSafeEq(this.getSourceType(), that.getSourceType(), true);
        response &= Util.nullSafeEq(this.getType(), that.getType(), true);

        return response;
    }

    public List<String> getClassificationNames() {
        return classificationNames;
    }

    public void setClassificationNames(List<String> classificationNames) {
        this.classificationNames = classificationNames;
    }

    private List<PolicyTreeNode> children = new ArrayList<PolicyTreeNode>();
    private Operator operator;
    /**
     * Name of the Source. This can be the name of the Application or TargetSource this was derived from
     *
     * @ignore
     * this is still named application due to legacy
     */
    private String application;
    private String value;
    private String name;
    private boolean selected;
    /**
     * ID of the Source. This can be the id of the Application or TargetSource this was derived from
     *
     * @ignore
     * this is still named applicationId due to legacy
     */
    private String applicationId;
    private boolean permission;
    private String displayValue;
    private String description;
    private List<PolicyTreeNodeState> status;
    private String type;
    private String sourceType;
    private List<IdentitySelector.MatchTerm.ContributingEntitlement> contributingEntitlements;
    /**
     * A list of classification categories if it exists for this node.
     */
    private List<String> classificationNames;

}

