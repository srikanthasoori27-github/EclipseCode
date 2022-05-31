/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *Data for Domains
 */
package sailpoint.object;

import java.util.Map;


public class ForestData {
    String _forestName;
    String _gcServer;
    String _user;
    String _password;
    boolean _useSSL;
    boolean _manageAllDomain;
    boolean previewDomains;
    String _useGroupMembershipPreloading;
    String _authenticationType;
    boolean _tlsEnabled;
    boolean authEnabled;
    boolean _isResourceForest;


    String _authType = AUTH_TYPE_SIMPLE ;

    private static final String ATT_FOREST_NAME = "forestName";
    private static final String ATT_GC_SERVER = "gcServer";
    private static final String ATT_USER = "user";
    private static final String ATT_PASSWORD = "password";
    private static final String ATT_AUTHENTICATION_TYPE = "authenticationType";
    private static final String ATT_USE_SSL = "useSSL";
    private static final String ATT_MANAGE_ALL_DOMAINS = "manageAllDomains";
    private static final String ATT_AUTH_TYPE = "authorizationType";
    public final static String GC_SSL_PORT = "3269";
    public final static String GC_DEFAULT_PORT = "3268";
    public final static String AUTH_TYPE_SIMPLE = "simple";
    private static final String ATT_USE_GROUP_MEMBERSHIP_PRELOADING = "useGroupMembershipPreloading";
    private static final String ATT_IS_RESOURCE_FOREST = "isResourceForest";

    public ForestData() {
        super();
        _forestName = null;
        _gcServer = null;
        _user = null;
        _password = null;
        _useSSL = false;
        _manageAllDomain = false;
         previewDomains = false;
        _authType = AUTH_TYPE_SIMPLE;
        _authenticationType = AUTH_TYPE_SIMPLE;
        _tlsEnabled = true;
        authEnabled = true;
    }

    @SuppressWarnings("unchecked")
    public ForestData(Map data) {
        this();
        if(data.get(ATT_FOREST_NAME) != null)
            _forestName = (String) data.get(ATT_FOREST_NAME);
        if (data.get(ATT_GC_SERVER) != null) {
            _gcServer = (String) data.get(ATT_GC_SERVER);
        }
        if(data.get(ATT_USER) != null)
            _user = (String) data.get(ATT_USER);
        if(data.get(ATT_PASSWORD) != null)
            _password = (String) data.get(ATT_PASSWORD);
        if(data.get(ATT_USE_SSL) != null)
            setUseSSL((Boolean) data.get(ATT_USE_SSL));
        if(data.get(ATT_MANAGE_ALL_DOMAINS) != null)
            _manageAllDomain = (Boolean) data.get(ATT_MANAGE_ALL_DOMAINS);
        if(data.get(ATT_AUTH_TYPE) != null)
            _authType = (String)data.get(ATT_AUTH_TYPE);
        if (data.get(ATT_USE_GROUP_MEMBERSHIP_PRELOADING) != null)
            this._useGroupMembershipPreloading = ((String)data.get(ATT_USE_GROUP_MEMBERSHIP_PRELOADING));
        if(data.get(ATT_AUTHENTICATION_TYPE) != null) {
            setAuthenticationType(((String)data.get(ATT_AUTHENTICATION_TYPE)));
        }
        else {
            setAuthenticationType(AUTH_TYPE_SIMPLE);
        }
        if(data.get(ATT_IS_RESOURCE_FOREST) != null)
            setIsResourceForest((Boolean) data.get(ATT_IS_RESOURCE_FOREST));
    }
 
     public boolean isPreviewDomains() {
        return previewDomains;
     }

     public void setPreviewDomains(boolean previewDomains) {
       this.previewDomains = previewDomains;
     }
    
    public String getForestName() {
        return _forestName;
    }

    public void setForestName(String forestName) {
        this._forestName = forestName;
    }

    public String getGcServer() {
        return _gcServer;
    }

    public void setGcServer(String gcServer) {
        this._gcServer = gcServer;
    }
    
    public void setUser(String userName) {
        this._user = userName;
    }

    public String getUser() {
        return _user;
    }

    public void setPassword(String password) {
        this._password = password;
    }

    public String getPassword() {
        return _password;
    }

    public void setUseSSL(boolean useSSL) {
        this._useSSL = useSSL;
        
        setAuthEnabled(!useSSL);
    }

    public boolean isUseSSL() {
        return _useSSL;
    }
    
    public boolean isManageAllDomain() {
        return _manageAllDomain;
    }

    public void setManageAllDomain(boolean manageAllDomain) {
        this._manageAllDomain = manageAllDomain;
    }
    
    public String getAuthType() {
        return _authType;
    }

    public void setAuthType(String authType) {
        this._authType = authType;
    }
    public String getUseGroupMembershipPreloading() {
        return this._useGroupMembershipPreloading; 
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
        return authEnabled;
    }
    
    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }
    
    public void setUseGroupMembershipPreloading(String useGroupMembershipPreloading) {
        this._useGroupMembershipPreloading = useGroupMembershipPreloading;
    }
    
    public boolean getIsResourceForest() {
        return _isResourceForest;
    }

    public void setIsResourceForest(boolean _isResourceForest) {
        this._isResourceForest = _isResourceForest;
    }
}