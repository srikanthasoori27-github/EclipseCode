/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *This bean manages account search scopes
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.faces.context.FacesContext;

import sailpoint.object.AccountScopeData;
import sailpoint.object.Application;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.object.Attributes;

public class AccountSearchScopeBean extends BaseEditBean<Application> {
    private static Application _app = null;
    public static List<AccountDataBean> _scopes;

    private static final String ATT_SUBTREE_SCOPE = "SUBTREE";
    private static final String ATT_SEARCH_DNS = "searchDNs";
    private static final String ATT_ACCOUNT_SEARCH_DNS = "account.searchDNs";

    private final String CONFIG_GROUP_MEMBER_FILTER_STRING = "groupMemberFilterString";
    private final String CONFIG_GROUP_MEMBER_SEARCH_DN = "groupMemberSearchDN";
    private final String CONFIG_GROUP_MEMBERSHIP_SEARCH_DN = "groupMembershipSearchDN";
    private final String CONFIG_GROUP_OBJECT_TYPE = "objectType";
    private final String CONFIG_GROUP_OBJECT_TYPE_VALUE = "group";
    private final String CONFIG_SEARCH_DN_KEY = "searchDN";
    private final String CONFIG_ACCOUNT_SEARCH_DNS = "account.searchDNs";
    private final String CONFIG_ACCOUNT_ITERATE_SEARCH_FILTER_KEY = "account.iterateSearchFilter";
    private final String CONFIG_ITERATE_SEARCH_FILTER_KEY = "iterateSearchFilter";
    private final String CONFIG_GROUP_SEARCH_SCOPE_KEY = "searchScope";
    private final String CONFIG_SEARCH_DNS_KEY = "searchDNs";
    private final String CONFIG_GROUP_MEMBERSHIP_SEARCH_SCOPE_KEY = "groupMembershipSearchScope";
    private final String GROUP_MEMBER_SEARCH_DN_SEPRATOR = ";";
    private final String NET_GROUPS = "nisNetgroup";
    private final String POSIXGROUP = "posixgroup";
    private final String CONFIG_LDAP_APPLICATION_NEW_GROUP_DELTA_KEY= "change_saved_for_group";
    private final String CONFIG_LDAP_APPLICATION_OLD_GROUP_DELTA_KEY= "change_saved_for_groups";
    private final String CONFIG_LDAP_APPLICATION_ENABLE_POSIX_GROUPS = "enablePosixGroups";
    private final String CONFIG_LDAP_APPLICATION_ENABLE_NET_GROUPS = "enableNetGroups";
    private final String CONFIG_LDAP_APPLICATION_DELTA_KEY = "deltaAggregation";
    private final String CONFIG_LDAP_APPLICATION_VERSION_KEY = "LDAPApplicationVersion";
    AccountDataBean _accountScope;
    
    private boolean autoPartitioningWarning;

    /**
     * Stores the scopes to be removed
     */
    private Map<String, Boolean> _selectedScopes = new HashMap<String, Boolean>();

    private static Log log = LogFactory.getLog(AccountSearchScopeBean.class);

    @SuppressWarnings("unchecked")
    public AccountSearchScopeBean() {
        _accountScope = new AccountDataBean();
        autoPartitioningWarning = false;
        _scopes = (List<AccountDataBean>) getSessionScope().get(ATT_SEARCH_DNS);
        if(_scopes == null) {
            _scopes = (List<AccountDataBean>) getSessionScope().get(ATT_ACCOUNT_SEARCH_DNS);            
        } 
    }

    public static void reset() {
        AccountSearchScopeBean bean = new AccountSearchScopeBean();
        bean.getSessionScope().remove(ATT_SEARCH_DNS);
        bean.getSessionScope().remove(ATT_ACCOUNT_SEARCH_DNS);
        _scopes = null;
    }
    
    @Override
    public Map getSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
    }
    
    
    public boolean isAutoPartitioningWarning() {
         return autoPartitioningWarning;
    }

    public void setAutoPartitioningWarning(boolean autoPartitioningWarning) {
        this.autoPartitioningWarning = autoPartitioningWarning;
    }

    /**
     * Clears the application object of the class
     */
    public static void clearApplicationObject() {
        _app = null;
    }
    
    public static void setApplicationObject(Application obj) {
        _app = obj;
    }

    public void setScopes(List<AccountDataBean> scopes) {
        _scopes = scopes;
    }

    public AccountDataBean getAccountScope() {
        return _accountScope;
    }

    public void setAccountScope(AccountDataBean accountScope) {
        _accountScope = accountScope;
    }

    public Map<String, Boolean> getSelectedScopes() {
        return _selectedScopes;
    }

    public void setSelectedScopes(Map<String, Boolean> selectedScopes) {
        _selectedScopes = selectedScopes;
    }

    public boolean isStoredOnSession() {
        return true;
    }

    protected Class getScope() {
        return Application.class;
    }

    /**
     * Reads account scope list for Active Directory Application
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<AccountDataBean> getScopes() throws GeneralException {
        if (_scopes == null) {
            if (_app != null) {
                _scopes = (List<AccountDataBean>) _app
                        .getListAttributeValue(ATT_SEARCH_DNS);
                if (_scopes == null)
                    _scopes = (List<AccountDataBean>) _app
                            .getListAttributeValue(ATT_ACCOUNT_SEARCH_DNS);
            }
        }

        // convert list objects to AccountDataBean before returning
        if (!Util.isEmpty(_scopes)) {
            Map data = new HashMap();
            List<AccountDataBean> beans = new ArrayList<AccountDataBean>();
            for (int i = 0; i < _scopes.size(); i++) {
                if (_scopes.get(i) instanceof AccountDataBean)
                    beans.add(_scopes.get(i));
                else {
                    data = (Map) _scopes.get(i);
                    beans.add(new AccountDataBean(data));
                }
            }
            _scopes = beans;
        }
        return _scopes;
    }
       
    
    public void showAutoPartitioningWarningMessage () {
         autoPartitioningWarning = false;
         if (!Util.isEmpty(_scopes)) {
             Map data = new HashMap();
             Set<String> scopeSet = new HashSet<String>();            		 
             for (int i = 0; i < _scopes.size(); i++) {
                 if (_scopes.get(i) instanceof AccountDataBean) {
                     if (Util.isNotNullOrEmpty(_scopes.get(i).getObject().getSearchDN())) {
                         String searchDN =_scopes.get(i).getObject().getSearchDN();
                         autoPartitioningWarning = !scopeSet.add(searchDN);
                    }
                   
             }else {
                     data = (Map) _scopes.get(i);
                     //autoPartitioningWarning = false ;
                 }
             }
            
         }
    }
    /**
     * Reads account scope list for LDAP based application
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<AccountDataBean> getLDAPScopes() throws GeneralException {
        if (_scopes == null) {
            if (_app != null) {
                
                if(Util.isNullOrEmpty(_app.getStringAttributeValue(CONFIG_LDAP_APPLICATION_VERSION_KEY))) {
                    transformLdapAppToMultiGrpApp();
                } else {
                    _scopes = (List<AccountDataBean>) _app
                            .getListAttributeValue(ATT_SEARCH_DNS);
                    if (_scopes == null) {
                        _scopes = (List<AccountDataBean>) _app
                        .getListAttributeValue(ATT_ACCOUNT_SEARCH_DNS);
                    }
                }
            }
        }

        // convert list objects to AccountDataBean before returning
        if (!Util.isEmpty(_scopes)) {
            Map data = new HashMap();
            List<AccountDataBean> beans = new ArrayList<AccountDataBean>();
            for (int i = 0; i < _scopes.size(); i++) {
                if (_scopes.get(i) instanceof AccountDataBean)
                    beans.add(_scopes.get(i));
                else {
                    data = (Map) _scopes.get(i);
                    beans.add(new AccountDataBean(data));
                }
            }
            _scopes = beans;
        }
        return _scopes;
    }
    /**
     * Adds account scopes
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String addAccountScope() throws GeneralException {
        String result = "";

        if (_scopes == null)
            _scopes = new ArrayList();

        String searchDN = _accountScope.getObject().getSearchDN();

        if (Util.isNullOrEmpty(searchDN)) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_SEARCH_DN_REQUIRED), null);
            result = "";
        } else {
            // add default scope
            _accountScope.getObject().setSearchScope(ATT_SUBTREE_SCOPE);
            _scopes.add(_accountScope);
            showAutoPartitioningWarningMessage();
            if(getSessionScope().containsKey(ATT_ACCOUNT_SEARCH_DNS))
                getSessionScope().put(ATT_ACCOUNT_SEARCH_DNS, _scopes);
            else
                getSessionScope().put(ATT_SEARCH_DNS, _scopes);
            _accountScope = new AccountDataBean();
            result = "addedAccountScopeInfo";
        }
        return result;
    }

    /**
     * Removes account scopes
     * @return
     */
    @SuppressWarnings("unchecked")
    public String removeAccountScope() {

        if (_scopes == null) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_ACCOUNT_SCOPES_DEFINED), null);
            return "";
        }

        // create a key set
        Set<String> accountScopes = null;
        if (_selectedScopes != null) {
            accountScopes = _selectedScopes.keySet();
        }
        if ((accountScopes == null) || (accountScopes.size() == 0)) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_ACCOUNT_SCOPE_SELECTED), null);
            return "";
        }

        // store keys to be removed
        Set<String> accountScopesToRemove = new HashSet<String>();
        for (String obj : accountScopes) {
            if (_selectedScopes.get(obj)) {
                accountScopesToRemove.add(obj);
            }
        }

        // remove the selected scopes from the list of scopes
        Iterator<AccountDataBean> i = _scopes.iterator();
        while (i.hasNext()) {
            AccountDataBean currentBean = i.next();
            if (accountScopesToRemove.contains(currentBean.getId())) {
                i.remove();
            }
        }
        if(getSessionScope().containsKey(ATT_ACCOUNT_SEARCH_DNS))
            getSessionScope().put(ATT_ACCOUNT_SEARCH_DNS, _scopes);
        else
            getSessionScope().put(ATT_SEARCH_DNS, _scopes);
        showAutoPartitioningWarningMessage();
        return "removedAccountScopes";
    }
    
    
    /**
     * Transforms Old SUNONE/TIVOLI/OPENLDAP applications to
     * multigroup supported applications
     */
    public void transformLdapAppToMultiGrpApp(){
        
        Attributes<String , Object> _appAttributes = _app.getAttributes();
        String groupMemberFilterString = _app.getStringAttributeValue(CONFIG_GROUP_MEMBER_FILTER_STRING);
        String groupMemberSearchDN = _app.getStringAttributeValue(CONFIG_GROUP_MEMBER_SEARCH_DN);
                                
        List<String> groupMembershipSearchDN = null;
        if (Util.isNotNullOrEmpty(groupMemberSearchDN)) {
            groupMembershipSearchDN = new ArrayList<String>();
            String[] arrGroupMemberSearchDN = groupMemberSearchDN.split(GROUP_MEMBER_SEARCH_DN_SEPRATOR);
            for (String value : arrGroupMemberSearchDN) {
                groupMembershipSearchDN.add(value);
            }
        }
        
        updateAccountDetails(_app,_appAttributes,groupMemberFilterString,groupMembershipSearchDN);
    }
    
    
    /**
     * Converts Old application acccount related details into new multigroup format.
     * @param _app
     * @param _appAttributes
     * @param groupMemberFilterString
     * @param groupMembershipSearchDN
     */
    private boolean updateAccountDetails (Application _app, Attributes<String , Object> _appAttributes,
            String groupMemberFilterString,List<String> groupMembershipSearchDN) {
        boolean update = false;
        List<Map<Object, Object>> searchDNS = null;
        List<AccountDataBean> scopeList = new ArrayList<AccountDataBean>();
        if (_appAttributes.containsKey(CONFIG_ACCOUNT_SEARCH_DNS)) {
            searchDNS = (List<Map<Object,Object>>) _app.getAttributeValue(CONFIG_ACCOUNT_SEARCH_DNS);
        } else {
            if (_appAttributes.containsKey(CONFIG_SEARCH_DNS_KEY)) {
                searchDNS = (List<Map<Object,Object>>) _app.getAttributeValue(CONFIG_SEARCH_DNS_KEY);
            }
        }
        String searchDN = _app.getStringAttributeValue(CONFIG_SEARCH_DN_KEY);
        String searchScope = _app.getStringAttributeValue(CONFIG_GROUP_SEARCH_SCOPE_KEY);
        String iterateSearchFilter = _app.getStringAttributeValue(CONFIG_ACCOUNT_ITERATE_SEARCH_FILTER_KEY);
        if (Util.isNullOrEmpty(iterateSearchFilter)) {
            iterateSearchFilter = _app.getStringAttributeValue(CONFIG_ITERATE_SEARCH_FILTER_KEY);
        }

        if (searchDNS != null) {
            for (Map<Object, Object> val : searchDNS) {
                AccountScopeData data = new AccountScopeData();
                data.setGroupMembershipSearchScope((List<Map<Object,Object>>)addGroupMembershipSearchScope(val,groupMemberFilterString,groupMembershipSearchDN,searchDN, _appAttributes));
                if (Util.isNotNullOrEmpty(searchDN) && !val.containsKey(CONFIG_SEARCH_DN_KEY)) {
                    data.setSearchDN(searchDN);
                } else {
                    data.setSearchDN((String)val.get(CONFIG_SEARCH_DN_KEY));
                }
                
                if (Util.isNotNullOrEmpty(searchScope) && !val.containsKey(CONFIG_GROUP_SEARCH_SCOPE_KEY)) {
                    data.setSearchScope(searchScope);
                } else {
                    data.setSearchScope((String)val.get(CONFIG_GROUP_SEARCH_SCOPE_KEY));
                }
                if (Util.isNotNullOrEmpty(iterateSearchFilter) && !val.containsKey(CONFIG_ITERATE_SEARCH_FILTER_KEY)) {
                    data.setIterateSearchFilter(iterateSearchFilter);
                } else {
                    data.setIterateSearchFilter((String)val.get(CONFIG_ITERATE_SEARCH_FILTER_KEY));
                }
                
                scopeList.add(new AccountDataBean(data));
            }
        } else {
            searchDNS = new ArrayList<Map<Object,Object>>();
            Map<Object, Object> searchDNs = new HashMap<Object, Object>(); 
            AccountScopeData data = new AccountScopeData();
            if (Util.isNotNullOrEmpty(searchDN)) {
                searchDNs.put(CONFIG_SEARCH_DN_KEY, searchDN);
                data.setSearchDN(searchDN);
            }
            if (Util.isNotNullOrEmpty(searchScope)) {
                searchDNs.put(CONFIG_GROUP_SEARCH_SCOPE_KEY, searchScope);
                data.setSearchScope(searchScope);
            }
            if (Util.isNotNullOrEmpty(iterateSearchFilter)) {
                searchDNs.put(CONFIG_ITERATE_SEARCH_FILTER_KEY, iterateSearchFilter);
                data.setIterateSearchFilter(iterateSearchFilter);
            }
            data.setGroupMembershipSearchScope(addGroupMembershipSearchScope(searchDNs,groupMemberFilterString,groupMembershipSearchDN, searchDN, _appAttributes));
            scopeList.add(new AccountDataBean(data));
        }
        
        update = true;
        _scopes = scopeList;
        return update;
    }

    /**
     * Reads Groupmembership related details from old application and converts it to new 
     * multigroup structure
     * @param searchDNs
     * @param groupMemberFilterString
     * @param groupMembershipSearchDN
     * @param searchDN
     * @param _appAttributes
     */
    private List<Map<Object,Object>> addGroupMembershipSearchScope(Map<Object, Object> searchDNs, String groupMemberFilterString, List<String> groupMembershipSearchDN, 
                                               String searchDN, Attributes<String , Object> _appAttributes) {
        List<Map<Object,Object>> groupMembershipSearchScope = new ArrayList<Map<Object,Object>>();
        Map<Object, Object> grpMembershipSearchScope = new HashMap<Object, Object>();
        Map<Object, Object> netMembershipSearchScope = new HashMap<Object, Object>();
        Map<Object, Object> posixMembershipSearchScope = new HashMap<Object, Object>();
        String grpMembSearchScope = (String)searchDNs.get(CONFIG_GROUP_MEMBER_SEARCH_DN);
        String grpMemberFilter = (String)searchDNs.get(CONFIG_GROUP_MEMBER_FILTER_STRING);
        List<String> groupMemberSearchDN = new ArrayList<String>();
        
        if(Util.isNotNullOrEmpty(grpMemberFilter)) {
            grpMembershipSearchScope.put(CONFIG_GROUP_MEMBER_FILTER_STRING, grpMemberFilter); 
            searchDNs.remove(CONFIG_GROUP_MEMBER_FILTER_STRING);            
        }else if (Util.isNotNullOrEmpty(groupMemberFilterString)) {
            grpMembershipSearchScope.put(CONFIG_GROUP_MEMBER_FILTER_STRING, groupMemberFilterString);
        }
        if(Util.isNotNullOrEmpty(grpMembSearchScope)) {            
            String[] arrGroupMemberSearchDN = grpMembSearchScope.split(GROUP_MEMBER_SEARCH_DN_SEPRATOR);
            for (String value : arrGroupMemberSearchDN) {
                groupMemberSearchDN.add(value);
            }
            if(groupMemberSearchDN != null && groupMemberSearchDN.size() > 0) {
                grpMembershipSearchScope.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_DN, groupMemberSearchDN);
                searchDNs.remove(CONFIG_GROUP_MEMBER_SEARCH_DN);
            }

        }else if (groupMembershipSearchDN != null) {
            grpMembershipSearchScope.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_DN, groupMembershipSearchDN);
        }
        else if(Util.isNotNullOrEmpty(searchDN)) {
            String[] arrGroupMemberSearchDN = searchDN.split(GROUP_MEMBER_SEARCH_DN_SEPRATOR);
            for (String value : arrGroupMemberSearchDN) {
                groupMemberSearchDN.add(value);
            }
            if(groupMemberSearchDN != null && groupMemberSearchDN.size() > 0){
                grpMembershipSearchScope.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_DN, groupMemberSearchDN);
            }
        }
        grpMembershipSearchScope.put(CONFIG_GROUP_OBJECT_TYPE,CONFIG_GROUP_OBJECT_TYPE_VALUE);
        groupMembershipSearchScope.add(grpMembershipSearchScope);
        if(_appAttributes.containsKey(CONFIG_LDAP_APPLICATION_ENABLE_NET_GROUPS)){
            boolean isNisGroup = _appAttributes.getBoolean(CONFIG_LDAP_APPLICATION_ENABLE_NET_GROUPS);
            if(isNisGroup == true) {
                if(groupMembershipSearchDN != null && groupMembershipSearchDN.size() > 0){
                    netMembershipSearchScope.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_DN, groupMembershipSearchDN);
                }
                else if(Util.isNotNullOrEmpty(searchDN)) {
                    List<String> lstOfSearchDN = new ArrayList<String>(); 
                    lstOfSearchDN.add(searchDN);
                    netMembershipSearchScope.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_DN, lstOfSearchDN);
                }
                netMembershipSearchScope.put(CONFIG_GROUP_OBJECT_TYPE,NET_GROUPS);
                groupMembershipSearchScope.add(netMembershipSearchScope);
            }
        }
        if(_appAttributes.containsKey(CONFIG_LDAP_APPLICATION_ENABLE_POSIX_GROUPS)) {
            boolean isPosixGroup = _appAttributes.getBoolean(CONFIG_LDAP_APPLICATION_ENABLE_POSIX_GROUPS);
            if(isPosixGroup == true) {
                if(groupMembershipSearchDN != null && groupMembershipSearchDN.size() > 0){
                    posixMembershipSearchScope.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_DN, groupMembershipSearchDN);
                }
                else if(Util.isNotNullOrEmpty(searchDN)) {
                    List<String> lstOfSearchDN = new ArrayList<String>(); 
                    lstOfSearchDN.add(searchDN);
                    posixMembershipSearchScope.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_DN, lstOfSearchDN);
                }
                posixMembershipSearchScope.put(CONFIG_GROUP_OBJECT_TYPE,POSIXGROUP);
                groupMembershipSearchScope.add(posixMembershipSearchScope);
            }
        }
        searchDNs.put(CONFIG_GROUP_MEMBERSHIP_SEARCH_SCOPE_KEY, groupMembershipSearchScope);
        return groupMembershipSearchScope;
    }
    

    public class AccountDataBean extends BaseBean {
        /**
         * The current AccountScopeData we are editing/creating.
         */
        private AccountScopeData object;
        private String _id;

        public AccountDataBean() {
            super();
            _id = "A" + Util.uuid();
            object = new AccountScopeData();
        }

        public AccountDataBean(Map objectMap) {
            super();
            _id = "A" + Util.uuid();
            object = new AccountScopeData(objectMap);
        }

        public AccountDataBean(AccountScopeData source) {
            this();
            object = source;
        }

        public AccountScopeData getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }
    }
}