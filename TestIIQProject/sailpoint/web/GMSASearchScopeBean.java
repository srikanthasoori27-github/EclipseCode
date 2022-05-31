/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *This bean manages MSA/gMSA search scopes
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AccountScopeData;
import sailpoint.object.Application;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class GMSASearchScopeBean extends BaseEditBean<Application> {
    private static Application _app = null;

    /**
     * List of GMSA searchDN objects in current session
     */
    public static List<AccountDataBean> _scopes;

    private static final String ATT_SUBTREE_SCOPE = "SUBTREE";
    private static final String ATT_GMSA_SEARCH_DNS = "gmsa.searchDNs";

    /**
     * Single searchDN object which is to be added in gmsa.seachDns list
     */
    AccountDataBean _gmsaScope;

    /**
     * Set to true to display autoPartitioning warning. AutoPartitioning warning will be displayed whenever there are
     * duplicate searchDN entries in the gMSA table.
     */
    private boolean autoPartitioningWarning;

    /**
     * Stores the scopes to be removed
     */
    private Map<String, Boolean> _selectedScopes = new HashMap<String, Boolean>();

    private static Log log = LogFactory.getLog(GMSASearchScopeBean.class);

    @SuppressWarnings("unchecked")
    public GMSASearchScopeBean() {
        _gmsaScope = new AccountDataBean();
        autoPartitioningWarning = false;
        _scopes = (List<AccountDataBean>) getSessionScope().get(ATT_GMSA_SEARCH_DNS);
    }

    /**
     * Removes gmsa searchDNs from the current session
     */
    public static void reset() {
        GMSASearchScopeBean bean = new GMSASearchScopeBean();
        bean.getSessionScope().remove(ATT_GMSA_SEARCH_DNS);
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


    public AccountDataBean getGMSAScope() {
        return _gmsaScope;
    }

    public void setGMSAScope(AccountDataBean _gmsaScope) {
        this._gmsaScope = _gmsaScope;
    }

    /**
     * Returns selected GMSA searchDNs 
     * @return
     */
    public Map<String, Boolean> getSelectedScopes() {
        return _selectedScopes;
    }

    /**
     * Populates list of selected GMSA searchDNs
     * @param selectedScopes
     */
    public void setSelectedScopes(Map<String, Boolean> selectedScopes) {
        _selectedScopes = selectedScopes;
    }

    @Override
    public boolean isStoredOnSession() {
        return false;
    }

    protected Class getScope() {
        return Application.class;
    }

    /**
     * Reads gMSA scope list for Active Directory Application
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<AccountDataBean> getScopes() throws GeneralException {
        if (_scopes == null && _app != null) {
            _scopes = (List<AccountDataBean>) _app.getListAttributeValue(ATT_GMSA_SEARCH_DNS);
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
     * Returns true if autoPartitioning warning message to be shown on the display
     */
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
                }
            }
        }
    }

    /**
     *  Adds new entry in gmsa.searchDNs
     * @return
     * @throws GeneralException
     */

    @SuppressWarnings("unchecked")
    public String addGMSAScope() throws GeneralException {
        String result = "";

        if (_scopes == null)
            _scopes = new ArrayList();

        String searchDN = _gmsaScope.getObject().getSearchDN();

        if (Util.isNullOrEmpty(searchDN)) {
            addMessage(new Message(Message.Type.Error,MessageKeys.ERR_SEARCH_DN_REQUIRED), null);
            result = "";
        } else {
            // add default scope
            _gmsaScope.getObject().setSearchScope(ATT_SUBTREE_SCOPE);
            _scopes.add(_gmsaScope);
            showAutoPartitioningWarningMessage();
            getSessionScope().put(ATT_GMSA_SEARCH_DNS, _scopes);

            _gmsaScope = new AccountDataBean();
            result = "addedGMSAScopeInfo";
        }
        return result;
    }

    /**
     * Removes selected entry from gmsa.SeachDNs
     * @return
     */
    @SuppressWarnings("unchecked")
    public String removeGMSAScope() {

        if (_scopes == null) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_ACCOUNT_SCOPES_DEFINED), null);
            return "";
        }

        // create a key set
        Set<String> accountScopes = null;
        if (_selectedScopes != null) {
            accountScopes = _selectedScopes.keySet();
        }
        if ( accountScopes == null || accountScopes.isEmpty() ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_ACCOUNT_SCOPE_SELECTED), null);
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
        getSessionScope().put(ATT_GMSA_SEARCH_DNS, _scopes);
        showAutoPartitioningWarningMessage();
        return "removedGMSAScopes";
    }


    /**
     * Bean class for the single searchDN object of gmsa 
     */

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