/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.GeneralException;
import sailpoint.api.SailPointContext;

import java.util.List;
import java.util.ArrayList;


/**
 * Snapshot of the composition of a role. Used for taking snapshots of roles
 * when creating role composition certifications.
 *
 * @author jonathan.bryant@sailpoint.com
 */
@XMLClass
public class RoleSnapshot extends Snapshot{

    /**
     * Role type name
     */
    private String type;
    private String typeDisplayName;
    private String ownerDisplayName;
    private String scopeDisplayName;

    /**
     * Snapshot of the related roles for this role.
     * A simple snapshot of the role
     * because only the name, id and description are needed.
     */
    private List<Snapshot> inheritedRoles;
    private List<Snapshot> permittedRoles;
    private List<Snapshot> requiredRoles;

    private List<Snapshot> grantedCapabilities;

    private List<Snapshot> grantedScopes;

    /**
     * Snapshot of the profiles for this role.
     */
    private List<ProfileSnapshot> profiles;


    public RoleSnapshot() {
        super();
    }

    public RoleSnapshot(Bundle role, SailPointContext context) throws GeneralException {
        super(role);

        this.type = role.getType();

        if (role.getType() != null){
            ObjectConfig roleConfig = context.getObjectByName(ObjectConfig.class, ObjectConfig.ROLE);
            RoleTypeDefinition def = roleConfig.getRoleTypesMap().get(role.getType());
            if (def != null)
                typeDisplayName = def.getDisplayableName();
            if (typeDisplayName==null)
                typeDisplayName = type; // fall back on type if we cant find a display name
        }

        setAttributes(role.getAttributes());

        this.ownerDisplayName = role.getOwner() != null ? role.getOwner().getDisplayName() : null;
        this.scopeDisplayName = role.getAssignedScope() != null ? role.getAssignedScope().getDisplayName() : null;

        if (role.getProfiles() != null){
            this.profiles = new ArrayList<ProfileSnapshot>();
            for(Profile profile : role.getProfiles()){
                profiles.add(new ProfileSnapshot(profile));
            }
        }

        if (role.getInheritance() != null){
            inheritedRoles = new ArrayList<Snapshot>();
            for(Bundle relation : role.getInheritance()){
                inheritedRoles.add(new Snapshot(relation));
            }
        }

        if (role.getPermits() != null){
            permittedRoles = new ArrayList<Snapshot>();
            for(Bundle relation : role.getPermits()){
                permittedRoles.add(new Snapshot(relation));
            }
        }

        if (role.getRequirements() != null){
            requiredRoles = new ArrayList<Snapshot>();
            for(Bundle relation : role.getRequirements()){
                requiredRoles.add(new Snapshot(relation));
            }
        }

        if (role.getProvisioningPlan() != null){
            ProvisioningPlan.AccountRequest iiqRequest =
                    role.getProvisioningPlan().getIIQAccountRequest();
            if (iiqRequest != null){
                
                for(ProvisioningPlan.AttributeRequest attrReq : iiqRequest.getAttributeRequests()){
                    if (Certification.IIQ_ATTR_SCOPES.equals(attrReq.getName()) && attrReq.getValue() != null){
                        List<String> scopes = (List<String>)attrReq.getValue();
                        for(String scopeId : scopes){
                            Scope scope = context.getObjectById(Scope.class, scopeId);
                            if (scope != null)
                                this.addGrantedScope(scope);
                        }
                    } else if (Certification.IIQ_ATTR_CAPABILITIES.equals(attrReq.getName()) &&
                            attrReq.getValue() != null){
                        List<String> caps = (List<String>)attrReq.getValue();
                        for(String cap : caps){
                            //Will this ever work? Looks like we put these into the plan as localized displayablenames? -rap
                            Capability capability = context.getObjectByName(Capability.class, cap);
                            if (capability != null)
                                this.addGrantedCapbility(capability);
                        }
                    }
                }
            }
        }

    }

    @XMLProperty
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XMLProperty
    public String getTypeDisplayName() {
        return typeDisplayName;
    }

    public void setTypeDisplayName(String typeDisplayName) {
        this.typeDisplayName = typeDisplayName;
    }

    @XMLProperty
    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public void setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
    }

    @XMLProperty
    public String getScopeDisplayName() {
        return scopeDisplayName;
    }

    public void setScopeDisplayName(String scopeDisplayName) {
        this.scopeDisplayName = scopeDisplayName;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<ProfileSnapshot> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ProfileSnapshot> profiles) {
        this.profiles = profiles;
    }

    public ProfileSnapshot getProfileSnapshot(String id){
        if (id != null && profiles != null){
            for(ProfileSnapshot snap : profiles){
                if (id.equals(snap.getObjectId()))
                    return snap;
            }
        }

        return null;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Snapshot> getInheritedRoles() {
        return inheritedRoles;
    }

    public void setInheritedRoles(List<Snapshot> inheritedRoles) {
        this.inheritedRoles = inheritedRoles;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Snapshot> getPermittedRoles() {
        return permittedRoles;
    }

    public void setPermittedRoles(List<Snapshot> permittedRoles) {
        this.permittedRoles = permittedRoles;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Snapshot> getRequiredRoles() {
        return requiredRoles;
    }

    public void setRequiredRoles(List<Snapshot> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Snapshot> getGrantedCapabilities() {
        return grantedCapabilities;
    }

    public void setGrantedCapabilities(List<Snapshot> grantedCapabilities) {
        this.grantedCapabilities = grantedCapabilities;
    }

    public void addGrantedCapbility(Capability capability){
        if (grantedCapabilities == null)
            grantedCapabilities = new ArrayList<Snapshot>();
        grantedCapabilities.add(new Snapshot(capability));
    }

    public Snapshot getGrantedCapability(String id){
        if(id != null && grantedCapabilities != null){
            for(Snapshot capability : grantedCapabilities){
                if (id.equals(capability.getObjectId()))
                    return capability;
            }
        }
        return null;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Snapshot> getGrantedScopes() {
        return grantedScopes;
    }

    public void setGrantedScopes(List<Snapshot> grantedScopes) {
        this.grantedScopes = grantedScopes;
    }

    public void addGrantedScope(Scope scope){
        if (grantedScopes == null)
            grantedScopes = new ArrayList<Snapshot>();
        grantedScopes.add(new Snapshot(scope));
    }

    public Snapshot getGrantedScope(String id){
        if(id != null && grantedScopes != null){
            for(Snapshot scope : grantedScopes){
                if (id.equals(scope.getObjectId()))
                    return scope;
            }
        }
        return null;
    }

    /**
     * Returns the Snapshot for the given role if it is
     * found in the required/permitted or inherited roles lists.
     * @param id ID of the related role to retrieve
     * @return Snapshot of the given related role.
     */
    public Snapshot getRelatedRoleSnapshot(String id){

        if (id == null)
            return null;

        if (inheritedRoles != null){
            for(Snapshot snap : inheritedRoles){
                if (id.equals(snap.getObjectId()))
                    return snap;
            }
        }

        if (permittedRoles != null){
            for(Snapshot snap : permittedRoles){
                if (id.equals(snap.getObjectId()))
                    return snap;
            }
        }

        if (requiredRoles != null){
            for(Snapshot snap : requiredRoles){
                if (id.equals(snap.getObjectId()))
                    return snap;
            }
        }

        return null;
    }

    public void getGrantedCapability(){

    }

    /**
     * Snapshot of a profile included in a role.
     */
    @XMLClass
    public static class ProfileSnapshot extends Snapshot{

        private String application;

        private String accountType;

        private List<Filter> constraints;

        private List<Permission> permissions;

        public ProfileSnapshot() {
            super();
        }

        public ProfileSnapshot(Profile profile) {
            super(profile);
            this.application = profile.getApplication().getName();
            this.accountType = profile.getAccountType();
            this.constraints = profile.getConstraints();
            this.permissions = profile.getPermissions();
            setAttributes(profile.getAttributes());

        }

        public String getApplication() {
            return application;
        }

        @XMLProperty
        public void setApplication(String application) {
            this.application = application;
        }

        public String getAccountType() {
            return accountType;
        }

        @XMLProperty
        public void setAccountType(String accountType) {
            this.accountType = accountType;
        }

         @XMLProperty(mode=SerializationMode.LIST)
        public List<Filter> getConstraints() {
            return constraints;
        }

        public void setConstraints(List<Filter> constraints) {
            this.constraints = constraints;
        }

        @XMLProperty(mode=SerializationMode.LIST)
        public List<Permission> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<Permission> permissions) {
            this.permissions = permissions;
        }

    }

}
