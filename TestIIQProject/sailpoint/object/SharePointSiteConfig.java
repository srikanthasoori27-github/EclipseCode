/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Configuration object that is used to drive unstructured collection 
 * from SharePoint site collections.
 * 
 * A SharePoint target collector config contains a list of these
 * objects. It encapsulates the information necessary to traverse
 * through a tree of sites in a sharepoint environment.
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@XMLClass
public class SharePointSiteConfig implements Cloneable {

    /**
     * URL to the site collection. For example, http://admiral
     */
    private String _site;
 
    /**
     * Account to authenticate as when accessing web services
     */
    private String _user;

    /**
     * Password for user user when accessing MOSS web services.
     */
    private String _password;

    /**
     * Flag to indicate that inherited Permissions should be returned.
     */
    private boolean _includeInheritedListPermissions;
    
    /**
     * The String representation of a Filter string
     * to help filter down the sites scanned for 
     * targets. If this is left null, then all 
     * sites will be scanned.
     */
    private String _siteInclusionFilter;    

    /**
     * The string representation of a Filter that
     * will be used to filter down the lists 
     * returned from the scan.
     */
    private String _listInclusionFilter;
    
    
    
    /* For SharePoint RW - Filter Target Types*/
    private String _targetTypesFilter;
    
    
    /*For SharePoint RW - Filter Type (Exclude or Include)*/
    private String _filterType;
    
    public SharePointSiteConfig() {    
        _site = null;    
        _user = null;
        _password = null;
        _includeInheritedListPermissions = false;
        _siteInclusionFilter = null;
        _listInclusionFilter = null;
        _targetTypesFilter = null;
        _filterType = null;
    }

    @XMLProperty
    public String getSiteCollectionUrl() {
        return _site;
    }

    public void setSiteCollectionUrl(String site) {
        _site = site;
    }
    
    @XMLProperty
    public String getFilterType() {
        return _filterType;
    }

    public void setFilterType(String filterType) {
        _filterType = filterType;
    }
    @XMLProperty
    public String getTargetTypesFilter() {
		return _targetTypesFilter;
    }

    public void setTargetTypesFilter(String targetTypesFilter) {
        _targetTypesFilter = targetTypesFilter;
    }
    
    @XMLProperty
    public String getUser() {
        return _user;
    }

    public void setUser(String user ) {
        _user = user;
    }

    @XMLProperty
    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        _password = password;
    }

    @XMLProperty
    public boolean getIncludeInheritedListPermissions() {
        return _includeInheritedListPermissions;
    }

    public void setIncludeInheritedListPermissions(boolean inherited) {
        _includeInheritedListPermissions = inherited;
    }
    
    @XMLProperty
    public String getSiteInclusionFilter() {
        return _siteInclusionFilter;
    }

    public void setSiteInclusionFilter(String siteInclusionFilter) {
        _siteInclusionFilter = siteInclusionFilter;
    }
    
    @XMLProperty
    public String getListInclusionFilter() {
        return _listInclusionFilter;
    }

    public void setListInclusionFilter(String listInclusionFilter) {
        _listInclusionFilter = listInclusionFilter;
    }
 
    public Object clone() {
        Object buddy =null;
        try {
            buddy = super.clone();
        } catch (CloneNotSupportedException cnfe) { }
        return buddy;
    }
}
