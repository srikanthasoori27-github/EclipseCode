/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

/**
 * Groups collected from different repositories configured in FAM, such as Active Directory, Azure, and NIS.
 */
public class IdentityGroup extends FAMObject {

    String _domain;
    String _applicationId;
    //IdentityCollector
    IdentityCollector _identityCollector;
    String _groupType;
    boolean _createsLoop;
    String _loopPath;
    int _usersCount;
    int _usersCountLessExclusions;
    double _usersPercentage;
    String _uniqueIdentifier;

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

    public IdentityCollector getIdentityCollector() {
        return _identityCollector;
    }

    public void setIdentityCollector(IdentityCollector identityCollector) {
        _identityCollector = identityCollector;
    }

    public String getGroupType() {
        return _groupType;
    }

    public void setGroupType(String groupType) {
        _groupType = groupType;
    }

    public boolean isCreatesLoop() {
        return _createsLoop;
    }

    public void setCreatesLoop(boolean createsLoop) {
        _createsLoop = createsLoop;
    }

    public String getLoopPath() {
        return _loopPath;
    }

    public void setLoopPath(String loopPath) {
        _loopPath = loopPath;
    }

    public int getUsersCount() {
        return _usersCount;
    }

    public void setUsersCount(int usersCount) {
        _usersCount = usersCount;
    }

    public int getUsersCountLessExclusions() {
        return _usersCountLessExclusions;
    }

    public void setUsersCountLessExclusions(int usersCountLessExclusions) {
        _usersCountLessExclusions = usersCountLessExclusions;
    }

    public double getUsersPercentage() {
        return _usersPercentage;
    }

    public void setUsersPercentage(double usersPercentage) {
        _usersPercentage = usersPercentage;
    }

    public String getUniqueIdentifier() {
        return _uniqueIdentifier;
    }

    public void setUniqueIdentifier(String uniqueIdentifier) {
        _uniqueIdentifier = uniqueIdentifier;
    }
}
