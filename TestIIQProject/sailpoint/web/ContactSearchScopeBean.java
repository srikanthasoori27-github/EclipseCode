/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *This bean manages contact search scopes
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

public class ContactSearchScopeBean extends BaseEditBean<Application> {
    private static Application _app = null;

    /**
     * List of contact searchDN oobjects in current session
     */
    public static List<AccountDataBean> _scopes;

    private static final String ATT_SUBTREE_SCOPE = "SUBTREE";
    private static final String ATT_CONTACT_SEARCH_DNS = "contact.searchDNs";

    /**
     * Single searchDN object which is to be added in contact.seachDns list
     */
    AccountDataBean _contactScope;

    /**
     * Set to true to display autoPartitioning warning. AutoPartitioning warning will be displayed whenever there are
     * duplicate searchDN entries in the contact table.
     */
    private boolean autoPartitioningWarning;

    /**
     * Stores the scopes to be removed
     */
    private Map<String, Boolean> _selectedScopes = new HashMap<String, Boolean>();

    private static Log log = LogFactory.getLog(ContactSearchScopeBean.class);

    @SuppressWarnings("unchecked")
    public ContactSearchScopeBean() {
        _contactScope = new AccountDataBean();
        autoPartitioningWarning = false;
        _scopes = (List<AccountDataBean>) getSessionScope().get(ATT_CONTACT_SEARCH_DNS);
    }

    /**
     * Removes contact searchDNs from the current session
     */
    public static void reset() {
        ContactSearchScopeBean bean = new ContactSearchScopeBean();
        bean.getSessionScope().remove(ATT_CONTACT_SEARCH_DNS);
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


    public AccountDataBean getContactScope() {
        return _contactScope;
    }

    public void setContactScope(AccountDataBean _contactScope) {
        this._contactScope = _contactScope;
    }

    /**
     * Returns selected contact searchDNs 
     * @return
     */
    public Map<String, Boolean> getSelectedScopes() {
        return _selectedScopes;
    }

    /**
     * Populates list of selected contact searchDNs
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
     * Reads contact scope list for Active Directory Application
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<AccountDataBean> getScopes() throws GeneralException {
        if (_scopes == null && _app != null) {
            _scopes = (List<AccountDataBean>) _app.getListAttributeValue(ATT_CONTACT_SEARCH_DNS);               
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
     *  Adds new entry in contact.searchDNs
     * @return
     * @throws GeneralException
     */

    @SuppressWarnings("unchecked")
    public String addContactScope() throws GeneralException {
        String result = "";

        if (_scopes == null)
            _scopes = new ArrayList();

        String searchDN = _contactScope.getObject().getSearchDN();

        if (Util.isNullOrEmpty(searchDN)) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_SEARCH_DN_REQUIRED), null);
            result = "";
        } else {
            // add default scope
            _contactScope.getObject().setSearchScope(ATT_SUBTREE_SCOPE);
            _scopes.add(_contactScope);
            showAutoPartitioningWarningMessage();
            getSessionScope().put(ATT_CONTACT_SEARCH_DNS, _scopes);

            _contactScope = new AccountDataBean();
            result = "addedContactScopeInfo";
        }
        return result;
    }

    /**
     * Removes selected entry from contact.SeachDNs
     * @return
     */
    @SuppressWarnings("unchecked")
    public String removeContactScope() {

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
        if ( accountScopes == null || accountScopes.isEmpty() ) {
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
        getSessionScope().put(ATT_CONTACT_SEARCH_DNS, _scopes);

        showAutoPartitioningWarningMessage();
        return "removedContactScopes";
    }


    /**
     * Bean class for the single searchDN object of contact 
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