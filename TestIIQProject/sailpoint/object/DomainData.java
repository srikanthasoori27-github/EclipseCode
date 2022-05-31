/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *Data for Domains
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;
import sailpoint.web.ApplicationObjectBean;

public class DomainData {
    String _domainForestName;
    String _domainDN;
    String _netBIOS;
    List<String> _serverList;
    String _user;
    String _password;
    boolean _useSSL;
    String _port = DEFAULT_PORT;
    String _authType = AUTH_TYPE_SIMPLE ;
    String _domainIterateSearchFilter;
    String _authenticationType;
    String _shadowAccountMembershipFilter;
    boolean _tlsEnabled;
    boolean _authEnabled;
    boolean _disableShadowAccountMembership;

    private static final String ATT_DOMAIN_FOREST_NAME = "forestName";
    private static final String ATT_DOMAIN_DN = "domainDN";
    private static final String ATT_USER = "user";
    private static final String ATT_PASSWORD = "password";
    private static final String ATT_SERVERS = "servers";
    private static final String ATT_USE_SSL = "useSSL";
    private static final String ATT_PORT = "port";
    private static final String ATT_AUTH_TYPE = "authorizationType";
    private static final String ATT_AUTHENTICATION_TYPE = "authenticationType";
    private static final String ATT_DOMAIN_ITERATE_SEARCH_FILTER = "domainIterateSearchFilter";
    private static final String ATT_USE_GROUP_MEMBERSHIP_PRELOADING = "useGroupMembershipPreloading";
    private static final String ATT_IS_LEAF_NODE_IN_TREE = "isLeafNodeInTree";
    private static final String ATT_DISABLE_SHADOW_ACCOUNT_MEMBERSHIP   = "disableShadowAccountMembership";
    private static final String ATT_SHADOW_ACCOUNT_MEMBERSHIP_FILTER    = "shadowAccountMembershipFilter";
    public final static String DEFAULT_PORT = "389";
    public final static String SSL_PORT = "636";
    public final static String AUTH_TYPE_SIMPLE = "simple";

    public DomainData() {
        super();
        _domainForestName = null;
        _domainDN = null;
        _netBIOS = null;
        _user = null;
        _password = null;
        _useSSL = false;
        _serverList = new ArrayList<String>();
        _port = DEFAULT_PORT;
        _authType = AUTH_TYPE_SIMPLE;
        _authenticationType = AUTH_TYPE_SIMPLE;
        _domainIterateSearchFilter = null;
        _shadowAccountMembershipFilter = null;
        _tlsEnabled = true;
        _authEnabled = true;
        _disableShadowAccountMembership = false;
    }

    @SuppressWarnings("unchecked")
    public DomainData(Map data) {
        this();
        if(data.get(ATT_DOMAIN_FOREST_NAME) != null)
            _domainForestName = (String) data.get(ATT_DOMAIN_FOREST_NAME);
        if(data.get(ATT_DOMAIN_DN) != null)
            _domainDN = (String) data.get(ATT_DOMAIN_DN);
        if (data.get(ApplicationObjectBean.AD_ATT_DOMAIN_NET_BIOS) != null) {
            _netBIOS = (String) data.get(ApplicationObjectBean.AD_ATT_DOMAIN_NET_BIOS);
        }
        if(data.get(ATT_USER) != null)
            _user = (String) data.get(ATT_USER);
        if(data.get(ATT_PASSWORD) != null)
            _password = (String) data.get(ATT_PASSWORD);
        if(data.get(ATT_SERVERS) != null)
            _serverList = (List<String>) data.get(ATT_SERVERS);
        if(data.get(ATT_USE_SSL) != null)
            setUseSSL((Boolean) data.get(ATT_USE_SSL));
        if(data.get(ATT_PORT) != null)
            _port = (String)data.get(ATT_PORT);
        if(data.get(ATT_AUTH_TYPE) != null)
            _authType = (String)data.get(ATT_AUTH_TYPE);
        if(data.get(ATT_DOMAIN_ITERATE_SEARCH_FILTER) != null)
            _domainIterateSearchFilter = (String)data.get(ATT_DOMAIN_ITERATE_SEARCH_FILTER);
        if(data.get(ATT_AUTHENTICATION_TYPE) != null)
            setAuthenticationType((String)data.get(ATT_AUTHENTICATION_TYPE));
        else
            setAuthenticationType(AUTH_TYPE_SIMPLE);
        if(data.get(ATT_DISABLE_SHADOW_ACCOUNT_MEMBERSHIP) != null)
            setDisableShadowAccountMembership((Boolean) data.get(ATT_DISABLE_SHADOW_ACCOUNT_MEMBERSHIP));
        if (data.get(ATT_SHADOW_ACCOUNT_MEMBERSHIP_FILTER) != null)
            setShadowAccountMembershipFilter((String) data.get(ATT_SHADOW_ACCOUNT_MEMBERSHIP_FILTER));
    }

    public void setDomainForestName(String forestName) {
        this._domainForestName = forestName;
    }
    
    public String getDomainForestName() {
        return _domainForestName;
    }
    
    public void setDomainDN(String domain) {
        _domainDN = domain;
    }

    public String getDomainDN() {
        return _domainDN;
    }

    public void setNetBIOS(String netBIOS) { _netBIOS = netBIOS; }

    public String getNetBIOS() { return _netBIOS; }

    public void setServerList(List serverList) {
        _serverList = serverList;
    }

    public List getServerList() {
        List tempList = new ArrayList();
        if(!Util.isEmpty(_serverList)) {
            String tempServer = null;
            for(int i=0; i<_serverList.size(); i++) {
                tempServer = _serverList.get(i);
                if(Util.isNotNullOrEmpty(tempServer)) {
                    tempList.add(tempServer);
                }
            }
        }
        return tempList;
    }

    public void setUser(String userName) {
        _user = userName;
    }

    public String getUser() {
        return _user;
    }

    public void setPassword(String password) {
        _password = password;
    }

    public String getPassword() {
        return _password;
    }

    public void setUseSSL(boolean useSSL) {
        _useSSL = useSSL;
        
        setAuthEnabled(!useSSL);
    }

    public boolean isUseSSL() {
        return _useSSL;
    }
    
    public void setPort(String port) {
        _port = port;
    }

    public String getPort() {
        return _port;
    }

    public String getAuthType() {
        return _authType;
    }

    public void setAuthType(String authType) {
        _authType = authType;
    }

    public String getAuthenticationType() {
        return _authenticationType;
    }

    public void setAuthenticationType(String authType) {
        _authenticationType = authType;
        
        if(_authenticationType != null) {
            if(_authenticationType.equals(AUTH_TYPE_SIMPLE))
                setTlsEnabled(true);
            else
                setTlsEnabled(false);
        }
    }
    
    public boolean getTlsEnabled() {
        return _tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        _tlsEnabled = tlsEnabled;
    }
    
    public boolean getAuthEnabled() {
        return _authEnabled;
    }

    public void setAuthEnabled(boolean authEnabled) {
        _authEnabled = authEnabled;
    }
    
    public String getDomainIterateSearchFilter() {
        return _domainIterateSearchFilter;
    }

    public void setDomainIterateSearchFilter(String domainIterateSearchFilter) {
        _domainIterateSearchFilter = domainIterateSearchFilter;
    }
    
    public String getShadowAccountMembershipFilter() {
        return _shadowAccountMembershipFilter;
    }

    public void setShadowAccountMembershipFilter(String shadowAccountMembershipFilter) {
        _shadowAccountMembershipFilter = shadowAccountMembershipFilter;
    }
    
    public boolean isDisableShadowAccountMembership() {
        return _disableShadowAccountMembership;
    }

    public void setDisableShadowAccountMembership(boolean _disableShadowAccountMembership) {
        this._disableShadowAccountMembership = _disableShadowAccountMembership;
    }

}