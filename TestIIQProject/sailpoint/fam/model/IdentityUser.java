/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

import java.util.List;

/**
 * Identities collected from different identity repositories configured in FAM, such as Active Directory, Azure, and NIS.
 */
public class IdentityUser extends FAMObject implements SCIMObject {

    public static final String SCIM_PATH = "identityusers";

    String _domain;
    //TODO: API docs say this is a double? -rap
    String _applicationId;
    boolean _deleted;
    String _userPrincipalName;
    String _email;
    String _fullName;
    String _displayName;
    String _userType;
    String _uniqueIdentifier;

    IdentityCollector _identityCollector;
    List<BusinessResource> _ownedResources;


    public String getDomain() {
        return _domain;
    }

    public void setDomain(String domain) {
        _domain = domain;
    }

    public String getApplicationId() {
        return _applicationId;
    }

    public void setApplicationId(String applicationId) {
        _applicationId = applicationId;
    }

    public boolean isDeleted() {
        return _deleted;
    }

    public void setDeleted(boolean deleted) {
        _deleted = deleted;
    }

    public String getUserPrincipalName() {
        return _userPrincipalName;
    }

    public void setUserPrincipalName(String userPrincipalName) {
        _userPrincipalName = userPrincipalName;
    }

    public String getEmail() {
        return _email;
    }

    public void setEmail(String email) {
        _email = email;
    }

    public String getFullName() {
        return _fullName;
    }

    public void setFullName(String fullName) {
        _fullName = fullName;
    }

    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    public String getUserType() {
        return _userType;
    }

    public void setUserType(String userType) {
        _userType = userType;
    }

    public String getUniqueIdentifier() {
        return _uniqueIdentifier;
    }

    public void setUniqueIdentifier(String uniqueIdentifier) {
        _uniqueIdentifier = uniqueIdentifier;
    }

    public IdentityCollector getIdentityCollector() {
        return _identityCollector;
    }

    public void setIdentityCollector(IdentityCollector identityCollector) {
        _identityCollector = identityCollector;
    }

    public List<BusinessResource> getOwnedResources() {
        return _ownedResources;
    }

    public void setOwnedResources(List<BusinessResource> ownedResources) {
        _ownedResources = ownedResources;
    }

    @Override
    public String getSCIMPath() {
        return SCIM_PATH;
    }
}
