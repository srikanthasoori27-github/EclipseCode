/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam.model;

/**
 * Application is the name of the IdentityIQ File Access Manager component that represents the monitored system
 * (such as, Microsoft Outlook, Active Directory, and file servers). IdentityIQ File Access Manager monitors and
 * analyzes permissions of built-in applications.
 */
public class Application extends FAMObject implements SCIMObject {

    static String SCIM_PATH = "applications";

    double _identityCollectorId;
    String _lastPermissiosnRefresh;
    String _firstCrawlTime;
    String _lastCrawlRefresh;
    String _lastDataClassificationRefresh;

    ApplicationType _applicationType;

    public double getIdentityCollectorId() {
        return _identityCollectorId;
    }

    public void setIdentityCollectorId(double identityCollectorId) {
        _identityCollectorId = identityCollectorId;
    }

    public String getLastPermissiosnRefresh() {
        return _lastPermissiosnRefresh;
    }

    public void setLastPermissiosnRefresh(String lastPermissiosnRefresh) {
        _lastPermissiosnRefresh = lastPermissiosnRefresh;
    }

    public String getFirstCrawlTime() {
        return _firstCrawlTime;
    }

    public void setFirstCrawlTime(String firstCrawlTime) {
        _firstCrawlTime = firstCrawlTime;
    }

    public String getLastCrawlRefresh() {
        return _lastCrawlRefresh;
    }

    public void setLastCrawlRefresh(String lastCrawlRefresh) {
        _lastCrawlRefresh = lastCrawlRefresh;
    }

    public String getLastDataClassificationRefresh() {
        return _lastDataClassificationRefresh;
    }

    public void setLastDataClassificationRefresh(String lastDataClassificationRefresh) {
        _lastDataClassificationRefresh = lastDataClassificationRefresh;
    }

    public ApplicationType getApplicationType() {
        return _applicationType;
    }

    public void setApplicationType(ApplicationType applicationType) {
        _applicationType = applicationType;
    }

    @Override
    public String getSCIMPath() {
        return SCIM_PATH;
    }
}
