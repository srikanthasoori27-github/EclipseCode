/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *This bean manages group scopes
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

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.object.GroupScopeData;

public class GroupSearchScopeBean extends BaseEditBean<Application> {
    private static Application _app = null;
    private GroupDataBean _groupScope;
    public static List<GroupDataBean> _scopes;

    private final String ATT_SUBTREE_SCOPE = "SUBTREE";
    private static final String ATT_GROUP_SEARCH_DNS = "group.searchDNs";
    private final String ATT_GROUP_SEARCH_DN = "group.searchDN";
    private final String ATT_GROUP_SEARCH_FILTER = "group.iterateSearchFilter";
    private final String ATT_GROUP = "group";
    private final String CONFIG_LDAP_APPLICATION_VERSION_KEY = "LDAPApplicationVersion";
    
    private final String CONFIG_GROUP_ITERATE_SEARCH_FILTER = "group.iterateSearchFilter";
    private final String CONFIG_GROUP_SEARCH_SCOPE = "group.searchScope";
    private final String CONFIG_GROUP_OBJECT_TYPE_VALUE = "group";
    private final String CONFIG_SEARCH_DN_KEY = "searchDN";
    private final String CONFIG_ITERATE_SEARCH_FILTER_KEY = "iterateSearchFilter";
    private final String CONFIG_GROUP_SEARCH_SCOPE_KEY = "searchScope";
    private final String CONFIG_SEARCH_DNS_KEY = "searchDNs";
    private final String CONFIG_SEARCH_DNS_SUFFIX = ".searchDNs";

    private Map<String, Boolean> _selectedScopes = new HashMap<String, Boolean>();

    private static Log log = LogFactory.getLog(GroupSearchScopeBean.class);

    @SuppressWarnings("unchecked")
    public GroupSearchScopeBean() {
        _groupScope = new GroupDataBean();
        _scopes = (List<GroupDataBean>) getSessionScope().get(ATT_GROUP_SEARCH_DNS);
    }

    public static void reset() {
        GroupSearchScopeBean bean = new GroupSearchScopeBean();
        bean.getSessionScope().remove(ATT_GROUP_SEARCH_DNS);
        _scopes = null;
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

    public void setScopes(List<GroupDataBean> scopes) {
        _scopes = scopes;
    }

    public GroupDataBean getGroupScope() {
        return _groupScope;
    }

    public void setGroupScope(GroupDataBean groupScope) {
        _groupScope = groupScope;
    }

    public Map<String, Boolean> getSelectedScopes() {
        return _selectedScopes;
    }

    public void setSelectedScopes(Map<String, Boolean> selectedScopes) {
        _selectedScopes = selectedScopes;
    }

    @Override
    public boolean isStoredOnSession() {
        return true;
    }

    @Override
    protected Class getScope() {
        return Application.class;
    }

    @Override
    public Map getSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();    	
    }
    
    /**
     * Adds group scopes
     * 
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String addGroupScope() throws GeneralException {
        String result = "";

        if (_scopes == null)
            _scopes = new ArrayList();

        String searchDN = _groupScope.getObject().getSearchDN();

        if (Util.isNullOrEmpty(searchDN)) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_SEARCH_DN_REQUIRED), null);
            result = "";
        } else {
            // add deafult scope
            _groupScope.getObject().setSearchScope(ATT_SUBTREE_SCOPE);
            _scopes.add(_groupScope);
            getSessionScope().put(ATT_GROUP_SEARCH_DNS, _scopes);
            _groupScope = new GroupDataBean();
            result = "addedgroupScopeInfo";
        }
        return result;
    }

    /**
     * Reads group scope lists for Active Directory based application
     * 
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<GroupDataBean> getScopes() throws GeneralException {       
        if (_scopes == null) {
            if (_app != null) {
                _scopes = (List<GroupDataBean>) _app
                        .getListAttributeValue(ATT_GROUP_SEARCH_DNS);
            }
        }

        if (!Util.isEmpty(_scopes)) {
            Map data = new HashMap();
            List<GroupDataBean> beans = new ArrayList<GroupDataBean>();
            for (int i = 0; i < _scopes.size(); i++) {
                if (_scopes.get(i) instanceof GroupDataBean)
                    beans.add(_scopes.get(i));
                else {
                    data = (Map) _scopes.get(i);
                    beans.add(new GroupDataBean(data));
                }
            }
            _scopes = beans;
        }
        return _scopes;
    }


    /**
     * Reads group scope lists  LDAP based application.
     * 
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<GroupDataBean> getLDAPScopes() throws GeneralException {
        if (_scopes == null) {
            if (_app != null) {
                if (Util.isNullOrEmpty(_app.getStringAttributeValue(CONFIG_LDAP_APPLICATION_VERSION_KEY))) {
                    updateGroupDetails(_app, _app.getAttributes());
                } else {
                    List<Schema>grpSchemas = _app.getGroupSchemas();
                    List<String> objectTypeList = new ArrayList<String>();
                    Attributes<String,Object> appAttributes = _app.getAttributes();
                    List<GroupDataBean> grpDataList = new ArrayList<GroupDataBean>();
                   
                    for (Schema s : grpSchemas) {
                        objectTypeList.add(s.getObjectType());
                    }
                    
                    for (String grpType : objectTypeList) {
                        if (appAttributes.containsKey(grpType+CONFIG_SEARCH_DNS_SUFFIX)) {
                            if (_app.getAttributeValue(grpType+CONFIG_SEARCH_DNS_SUFFIX) instanceof  Map) {
                                Map data = (Map) _app.getAttributeValue(grpType+CONFIG_SEARCH_DNS_SUFFIX);
                                GroupDataBean groupDataBean = new GroupDataBean(data);
                                grpDataList.add(groupDataBean);
                            } else if (_app.getAttributeValue(grpType+CONFIG_SEARCH_DNS_SUFFIX) instanceof List) {
                                List<Map>  groupData = (List<Map>) _app.getAttributeValue(grpType+CONFIG_SEARCH_DNS_SUFFIX);
                                for (Map data : groupData) {
                                    GroupDataBean groupDataBean = new GroupDataBean(data);
                                    grpDataList.add(groupDataBean);
                                }
                            }
                        }
                    }
                    _scopes = grpDataList;
                
                    if (_scopes == null) {
                        GroupScopeData data = new GroupScopeData();
                        data.setSearchDN((String)_app.getAttributeValue(ATT_GROUP_SEARCH_DN));
                        data.setIterateSearchFilter((String)_app.getAttributeValue(ATT_GROUP_SEARCH_FILTER));
                        data.setObjectType(ATT_GROUP);
                        GroupDataBean bean = new GroupDataBean(data);
                        List<GroupDataBean> scopeList = new ArrayList<GroupDataBean>();
                        scopeList.add(bean);
                        _scopes = scopeList;
                    }
                }
            }
        }

        if (!Util.isEmpty(_scopes)) {
            Map data = new HashMap();
            List<GroupDataBean> beans = new ArrayList<GroupDataBean>();
            for (int i = 0; i < _scopes.size(); i++) {
                if (_scopes.get(i) instanceof GroupDataBean)
                    beans.add(_scopes.get(i));
                else {
                    data = (Map) _scopes.get(i);
                    beans.add(new GroupDataBean(data));
                }
            }
            _scopes = beans;
        }
        return _scopes;
    }
    /**
     * Removes group scopes from list
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public String removeGroupScope() {

        if (_scopes == null) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_GROUP_SCOPES_DEFINED), null);
            return "";
        }

        // create a key set
        Set<String> groupScopes = null;
        if (_selectedScopes != null) {
            groupScopes = _selectedScopes.keySet();
        }

        if ((groupScopes == null) || (groupScopes.size() == 0)) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_GROUP_SCOPE_SELECTED), null);
            return "";
        }

        // store keys to be removed
        Set<String> groupScopesToRemove = new HashSet<String>();
        for (String obj : groupScopes) {
            if (_selectedScopes.get(obj)) {
                groupScopesToRemove.add(obj);
            }
        }

        // remove the selected scopes from the list of scopes
        Iterator<GroupDataBean> i = _scopes.iterator();
        while (i.hasNext()) {
            GroupDataBean currentBean = i.next();
            if (groupScopesToRemove.contains(currentBean.getId())) {
                i.remove();
            }
        }
        
        
        getSessionScope().put(ATT_GROUP_SEARCH_DNS, _scopes);
        return "removedgroupScopes";
    }
    
    
    /**
     * Converts Old application group related details into new multigroup format.
     * @param app
     * @param appAttributes
     */
    @SuppressWarnings("unchecked")
    private boolean updateGroupDetails (Application app, Attributes<String , Object> appAttributes) {
        boolean update = false;
        List<Map<String, Object>> groupSearchDNS = null;
        List<GroupDataBean> scopeList = new ArrayList<GroupDataBean>();

        //Check for group.searchDNs entry in application configuration
        if (appAttributes.containsKey(ATT_GROUP_SEARCH_DNS)) {
            groupSearchDNS = (List<Map<String,Object>>) app.getAttributeValue(ATT_GROUP_SEARCH_DNS);
        }
        
      //Check for groupsearchDNs entry in application configuration
        if (groupSearchDNS == null && appAttributes.containsKey(ATT_GROUP+CONFIG_SEARCH_DNS_KEY)) {
            groupSearchDNS = (List<Map<String,Object>>) app.getAttributeValue(ATT_GROUP+CONFIG_SEARCH_DNS_KEY);
        }
        
        
        String iterateSearchFilter = app.getStringAttributeValue(CONFIG_GROUP_ITERATE_SEARCH_FILTER);
        
        //Get the value of group.searchDN
        String grpSearchDN = app.getStringAttributeValue(ATT_GROUP_SEARCH_DN);
        String grpSearchScope = app.getStringAttributeValue(CONFIG_GROUP_SEARCH_SCOPE);
        
        //if groupSearchDNS  & grpSearchDN is null then go for groupSearchDN retrieval process.
        if (groupSearchDNS == null && Util.isNullOrEmpty(grpSearchDN)) {
            grpSearchDN = app.getStringAttributeValue(ATT_GROUP+CONFIG_SEARCH_DN_KEY);
        }
        
      //if groupSearchDNS  & grpSearchDN is null then go for searchDNs retrieval process.
        if (Util.isNullOrEmpty(grpSearchDN) && groupSearchDNS == null && appAttributes.containsKey(CONFIG_SEARCH_DNS_KEY)) {
            List<Map<String,Object>> searchDNS = (List<Map<String, Object>>)app.getAttributeValue(CONFIG_SEARCH_DNS_KEY);
            if(!Util.isEmpty(searchDNS)) {
                groupSearchDNS = new ArrayList<Map<String,Object>>();
                for (Map<String,Object> searchDNs : searchDNS) {
                    Map<String,Object> map = new HashMap<String,Object>();
                    for(String entryKey : searchDNs.keySet()) {
                        map.put(entryKey,searchDNs.get(entryKey));
                    }
                    groupSearchDNS.add(map);
                }
            }
        }

        //Check if only searchDN or SearchDNs is configured, then there is no need to processed further for the group
        if(groupSearchDNS == null &&(Util.isNullOrEmpty(iterateSearchFilter) && Util.isNullOrEmpty(grpSearchDN))) {
            return true;
        }
        if (Util.isNullOrEmpty(iterateSearchFilter)) {
            iterateSearchFilter = app.getStringAttributeValue(CONFIG_ITERATE_SEARCH_FILTER_KEY);
        }
        //If grpSearchDN is null or empty, then go for top level searchDN entry
        if(Util.isNullOrEmpty(grpSearchDN)) {
            grpSearchDN = app.getStringAttributeValue(CONFIG_SEARCH_DN_KEY);
        }      
        if(Util.isNullOrEmpty(grpSearchScope)){
            grpSearchScope = app.getStringAttributeValue(CONFIG_GROUP_SEARCH_SCOPE_KEY);
        }

        //If multiple grouSearcheDNS configured, then iterate each entry and insert the others top level keys
        if (!Util.isEmpty(groupSearchDNS)) {
            for (Map<String,Object> val : groupSearchDNS) {
                GroupScopeData grpData = new GroupScopeData();
                if(!val.containsKey(CONFIG_ITERATE_SEARCH_FILTER_KEY) && Util.isNotNullOrEmpty(iterateSearchFilter)){
                    grpData.setIterateSearchFilter(iterateSearchFilter);
                } else {
                    grpData.setIterateSearchFilter((String)val.get(CONFIG_ITERATE_SEARCH_FILTER_KEY));
                }
                if(!val.containsKey(CONFIG_SEARCH_DN_KEY) && Util.isNotNullOrEmpty(grpSearchDN)){
                    grpData.setSearchDN(grpSearchDN);
                } else {
                    grpData.setSearchDN((String)val.get(CONFIG_SEARCH_DN_KEY));
                }
                if(!val.containsKey(CONFIG_GROUP_SEARCH_SCOPE_KEY) && Util.isNotNullOrEmpty(grpSearchScope)){
                    grpData.setSearchScope(grpSearchScope);
                } else {
                    grpData.setSearchScope((String)val.get(CONFIG_GROUP_SEARCH_SCOPE_KEY));
                }
                grpData.setObjectType(CONFIG_GROUP_OBJECT_TYPE_VALUE);
                GroupDataBean grpDataBean = new GroupDataBean(grpData);
                scopeList.add(grpDataBean);
            }
        } else {
            groupSearchDNS = new ArrayList<Map<String,Object>>();
            Map<String,Object> grpSearchDNs = new HashMap<String,Object>();
            GroupScopeData grpData = new GroupScopeData();
            if (Util.isNotNullOrEmpty(grpSearchDN)) {
                grpData.setSearchDN(grpSearchDN);
            }          
            if (Util.isNotNullOrEmpty(iterateSearchFilter)) {
                grpData.setIterateSearchFilter(iterateSearchFilter);
            }

            if(Util.isNullOrEmpty(grpSearchScope)){
                grpSearchScope = app.getStringAttributeValue(CONFIG_GROUP_SEARCH_SCOPE_KEY);
            }
            if (Util.isNotNullOrEmpty(grpSearchScope)) {
                grpData.setSearchScope(grpSearchScope);
            }
            grpData.setObjectType(CONFIG_GROUP_OBJECT_TYPE_VALUE);
            GroupDataBean grpDataBean = new GroupDataBean(grpData);
            scopeList.add(grpDataBean);
        }
       
        _scopes = scopeList;
        update = true;
        return update;
    }

    public class GroupDataBean extends BaseBean {
        /**
         * The current GroupScopeData we are editing/creating.
         */
        private GroupScopeData object;

        private String _id;

        public GroupDataBean() {
            super();
            _id = "G" + Util.uuid();
            object = new GroupScopeData();
        }

        public GroupDataBean(Map objectMap) {
            super();
            _id = "G" + Util.uuid();
            object = new GroupScopeData(objectMap);
        }

        public GroupDataBean(GroupScopeData source) {
            this();
            object = source;
        }

        public GroupScopeData getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }
    }
}