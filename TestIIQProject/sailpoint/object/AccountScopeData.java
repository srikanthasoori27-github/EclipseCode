/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *Data for Account Scope
 */
package sailpoint.object;

import java.util.List;
import java.util.Map;

public class AccountScopeData {
    private String _searchDN;
    private String _searchScope;
    private String _iterateSearchFilter;
    private String _primaryGroupSearchDN;
    private String _groupMembershipSearchDN;
    private String _groupMemberFilterString;
    private List<Map<Object,Object>> _groupMembershipSearchScope;

    private static final String ATT_SEARCH_DN = "searchDN";
    private static final String ATT_GROUP_MEMBERSHIP_SEARCH_DN = "groupMembershipSearchDN";
    private static final String ATT_PRIMARY_GROUP_SEARCH_DN = "primaryGroupSearchDN";
    private static final String ATT_SEARCH_SCOPE = "searchScope";
    private static final String ATT_ITERATE_SEARCH_FILTER = "iterateSearchFilter";
    private static final String ATT_GROUP_MEMBER_FILTER_STRING = "groupMemberFilterString";
    private static final String ATT_GROUP_MEMBERSHIP_SEARCH_SCOPE ="groupMembershipSearchScope";

    public AccountScopeData() {
        _searchDN = null;
        _searchScope = null;
        _iterateSearchFilter = null;
        _primaryGroupSearchDN = null;
        _groupMembershipSearchDN = null;
        _groupMemberFilterString = null;
        _groupMembershipSearchScope = null;
    }

    public AccountScopeData(Map data) {
        super();
        if (data.get(ATT_SEARCH_DN) != null)
            _searchDN = (String) data.get(ATT_SEARCH_DN);
        if (data.get(ATT_SEARCH_SCOPE) != null)
            _searchScope = (String) data.get(ATT_SEARCH_SCOPE);
        if (data.get(ATT_ITERATE_SEARCH_FILTER) != null)
            _iterateSearchFilter = (String) data.get(ATT_ITERATE_SEARCH_FILTER);
        if (data.get(ATT_PRIMARY_GROUP_SEARCH_DN) != null)
            _primaryGroupSearchDN = (String) data
                    .get(ATT_PRIMARY_GROUP_SEARCH_DN);
        if (data.get(ATT_GROUP_MEMBERSHIP_SEARCH_DN) != null)
            _groupMembershipSearchDN = (String) data
                    .get(ATT_GROUP_MEMBERSHIP_SEARCH_DN);
        if (data.get(ATT_GROUP_MEMBER_FILTER_STRING) != null)
            _groupMemberFilterString = (String) data
                    .get(ATT_GROUP_MEMBER_FILTER_STRING);
        if(data.get(ATT_GROUP_MEMBERSHIP_SEARCH_SCOPE) != null)
            _groupMembershipSearchScope = (List<Map<Object,Object>>) data.get(ATT_GROUP_MEMBERSHIP_SEARCH_SCOPE);
    }

    public void setSearchDN(String searchDN) {
        _searchDN = searchDN;
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

    public void setSearchScope(String searchScope) {
        _searchScope = searchScope;
    }

    public String getSearchScope() {
        return _searchScope;
    }

    public void setPrimaryGroupSearchDN(String primaryGroupSearchDN) {
        _primaryGroupSearchDN = primaryGroupSearchDN;
    }

    public String getPrimaryGroupSearchDN() {
        return _primaryGroupSearchDN;
    }

    public void setGroupMembershipSearchDN(String groupMembershipSearchDN) {
        _groupMembershipSearchDN = groupMembershipSearchDN;
    }

    public String getGroupMembershipSearchDN() {
        return _groupMembershipSearchDN;
    }

    public String getGroupMemberFilterString() {
        return _groupMemberFilterString;
    }

    public void setGroupMemberFilterString(String groupMemberFilterString) {
        _groupMemberFilterString = groupMemberFilterString;
    }

       public List<Map<Object,Object>> getGroupMembershipSearchScope() {
        return _groupMembershipSearchScope;
    }

    public void setGroupMembershipSearchScope(List<Map<Object,Object>> groupMembershipSearchScope) {
        this._groupMembershipSearchScope = groupMembershipSearchScope;
    }
}