/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *Data for Group Scope
 */
package sailpoint.object;

import java.util.Map;

public class GroupScopeData {
    private String _searchDN;
    private String _searchScope;
    private String _iterateSearchFilter;
    private String _objectType;
    

    private static final String ATT_SEARCH_DN = "searchDN";
    private static final String ATT_SEARCH_SCOPE = "searchScope";
    private static final String ATT_ITERATE_SEARCH_FILTER = "iterateSearchFilter";
    private static final String ATT_OBJECT_TYPE = "objectType";

    public GroupScopeData() {
        _searchDN = null;
        _iterateSearchFilter = null;
    }

    public GroupScopeData(Map data) {
        super();
        if(data.get(ATT_SEARCH_DN) != null)
            _searchDN = (String) data.get(ATT_SEARCH_DN);
        if(data.get(ATT_SEARCH_SCOPE) != null)
            _searchScope = (String) data.get(ATT_SEARCH_SCOPE);
        if(data.get(ATT_ITERATE_SEARCH_FILTER) != null)
            _iterateSearchFilter = (String) data.get(ATT_ITERATE_SEARCH_FILTER);
        if(data.get(ATT_OBJECT_TYPE) != null)
        	_objectType = (String) data.get(ATT_OBJECT_TYPE);
    }

    public void setSearchDN(String searchDN) {
        _searchDN = searchDN;
    }

    public void setSearchScope(String searchScope) {
        _searchScope = searchScope;
    }

    public String getSearchScope() {
        return _searchScope;
    }

    public String getSearchDN() {
        return _searchDN;
    }

    public void setIterateSearchFilter(String iterateSearchFilter) {
        _iterateSearchFilter = iterateSearchFilter;
    }

    public String getIterateSearchFilter() {
        return _iterateSearchFilter;
    }
       
    public String getObjectType() {
		return _objectType;
	}

	public void setObjectType(String objectType) {
		this._objectType = objectType;
	}
}