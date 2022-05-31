/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This class is used to manage the domain data of AD Connector.  
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ExceptionCleaner;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.credential.CredentialRetriever;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.DomainData;
import sailpoint.object.ExchangeData;
import sailpoint.object.ForestData;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class ManageDomainBean extends BaseEditBean<Application> {
    private final static String ATT_IQSERVICE_HOST="IQServiceHost";
    private final static String ATT_IQSERVICE_PORT="IQServicePort";
    private static final String ATT_FOREST_ADMIN = "forestAdmin";
    private static final String ATT_FOREST_ADMIN_PASS = "forestAdminPassword";
    private static final String ATT_FOREST_GC = "forestGC";
    private static final String ATT_FOREST_AUTH_TYPE = "authenticationType";
    private static final String ATT_FOREST_DOMAIN = "forestDomain";
    private static final String FOREST_USE_TLS = "forestUseTLS";
    //identifier (before 7.2) to use TLS for GC connection 
    private static final String USE_SSL_FOR_GC = "useSSLForGC";
    private static final String ATT_EXCHANGE_VERSION = "exchangeversion";
    private static final String ATT_EXCHANGE_SERVERS = "ExchHost";


    private final static String DOMAIN_CONTROLLER="domainController";
    private static Application _app = null;
    private static Log log = LogFactory.getLog(ManageDomainBean.class);

    /**
     * Specifies if discover domain was successful or not
     */
    private boolean _domainSuccess;

    /**
     * Specifies if Forest table operations are successful or not
     */
    private boolean _forestSuccess;

    /**
     * Specifies if exchange table operations are successful or not
     */
    private boolean _exchangeSuccess;

    /**
     * List of the selected domains, used during removal of domains.
     */
    private Map<String, Boolean> _selectedDomains = new HashMap<String, Boolean>();

    /**
     * List of the selected forest, used during removal of forest.
     */
    private Map<String, Boolean> _selectedForest = new HashMap<String, Boolean>();

    /**
     * List of the selected exchange, used during removal of forest.
     */
    private Map<String, Boolean> _selectedExchange = new HashMap<String, Boolean>();

    /**
     * Result of domainDiscover being called on connector
     */
    private String _domainResult;

    /**
     * Result of forestTable operations
     */
    private String _forestTableOperationResult;

    /**
     * Result of exchangeTable operations
     */
    private String _exchangeTableOperationResult;

    /**
     * Specifies if discover servers was successful or not
     */
    private boolean _serverSuccess;
    
    /**
     * Result of discover servers being called on connector
     */
    private String _serverResult;

    /**
     * Attributes required for discovering domains
     */
    private String _forestAdmin;
    private String _forestAdminPassword;
    private String _forestGC;
    /*
     * Attrbutes required for discovering domains
     */
    private String  _selectedForestName;
    private String _selectedDomainName;
    private boolean _manageAllDomainOperation;
    private boolean _useTLSOperation;
   
    private Map<String, Object> _forestConfig;
    private DomainDataBean _domainDataObj;
    public static List<DomainDataBean> _domainDataList;

    public static List<ForestDataBean> _forestDataList;
    private ForestDataBean _forestDataObj;

    public static List<ExchangeDataBean> _exchangeDataList;
    private ExchangeDataBean _exchangeDataObj;

    private static final String ATT_DOMAIN_SETTINGS = "domainSettings";
    private static final String ATT_FOREST_SETTINGS = "forestSettings";
    private static final String ATT_EXCHANGE_SETTINGS = "exchangeSettings";
    
    private static final String ATT_DOMAIN_DN = "domainDN";
    private static final String ATT_EXCHANGE_FOREST_NAME    = "exchangeForest";
    /*
     * Forest Name in domain table is saved against below key
     */
    private static final String ATT_DOMAIN_FOREST_NAME = "forestName";
    private static final String ATT_FOREST_NAME = "forestName";
    private static final String ATT_GC_SERVER = "gcServer";
    
    private boolean _showDomainConfiguration = true;
   
    public static boolean _upgradedSucessfully ;

   @SuppressWarnings("unchecked")
    public ManageDomainBean() {
        super();
        _domainSuccess = false;
        _forestSuccess = false;
        _domainResult = null;
        _useTLSOperation = false;
        _manageAllDomainOperation = false;
        _forestTableOperationResult = null;
        _domainDataObj = new DomainDataBean();
        _domainDataList = (List<DomainDataBean>) getSessionScope().get(
                ATT_DOMAIN_SETTINGS);
        _forestDataList = (List<ForestDataBean>) getSessionScope().get(
                ATT_FOREST_SETTINGS);
        _exchangeDataList = (List<ExchangeDataBean>) getSessionScope().get(
                ATT_EXCHANGE_SETTINGS);
        _forestDataObj = new ForestDataBean();
        _exchangeDataObj = new ExchangeDataBean();
        _serverSuccess = false;
        _serverResult = null;
        _showDomainConfiguration = true;
    }


    public boolean isShowDomainConfiguration() throws GeneralException {
        boolean flag = getHideShowDomainConfigurations();
        if (!flag && Util.isEmpty(_domainDataList)) {
             getSessionScope().put(ATT_DOMAIN_SETTINGS, getDomainDataList());
        }
        return _showDomainConfiguration = flag;
       
    }

     public void setShowDomainConfiguration(boolean _showDomainConfiguration) {
         this._showDomainConfiguration = _showDomainConfiguration;
     }
   
      
     public String getSelectedDomainName() {
         return _selectedDomainName;
     }
     
     public void setSelectedDomainName(String selectedDomainName) {
         this._selectedDomainName = selectedDomainName;
     } 
     
    public String getSelectedForestName() {
        return _selectedForestName;
    }
    
    public void setSelectedForestName(String selectedForestName) {
        this._selectedForestName = selectedForestName;
    }
    
    public boolean isManageAllDomainOperation() {
        return _manageAllDomainOperation;
    }

    public void setManageAllDomainOperation(boolean _manageAllDomainOperation) {
      this._manageAllDomainOperation = _manageAllDomainOperation;
    }
    
    public boolean isUseTLSOperation() {
        return _useTLSOperation;
    }

    public void setUseTLSOperation(boolean _useTLSOperation) {
        this._useTLSOperation = _useTLSOperation;
    }

    /**
     * Clears the application object of the class
     */
    public static void clearApplicationObject() {
        _app = null;
    }
    
    public String getForestAdmin() {
        return _forestAdmin;
    }

    public void setForestAdmin(String forestAdmin) {
        _forestAdmin = forestAdmin;
    }

    public String getForestAdminPassword() {
        return _forestAdminPassword;
    }

    public void setForestAdminPassword(String password) {
        _forestAdminPassword = password;
    }

    public String getForestGC() {
        return _forestGC;
    }

    public void setForestGC(String IP) {
        _forestGC = IP;
    }

    public Map<String, Boolean> getSelectedDomains() {
        return _selectedDomains;
    }

    @Override
    public Map getSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();    	
    }
    
    public void setSelectedDomains(Map<String, Boolean> selectedDomains) {
        _selectedDomains = selectedDomains;
    }

    public Map<String, Boolean> getselectedForest() {
        return _selectedForest;
    }
    public void setselectedForest(Map<String, Boolean> selectedForest) {
        this._selectedForest = selectedForest;
    }

    public Map<String, Boolean> getSelectedExchange() {
        return _selectedExchange;
    }

    public void setSelectedExchange(Map<String, Boolean> _selectedExchange) {
        this._selectedExchange = _selectedExchange;
    }

    public static void setApplicationObject(Application obj) {
        _app = obj;
    }

    public List<ForestDataBean> getforestDataList() throws GeneralException {
        
        if(_forestDataList == null) {
            if (_app != null) {
                Map data = new HashMap();
                _forestDataList = (List<ForestDataBean>) _app
                        .getListAttributeValue(ATT_FOREST_SETTINGS);
                if (_app != null) {
                    List domainDataList = null;
                    domainDataList = (List<DomainDataBean>) _app
                            .getListAttributeValue(ATT_DOMAIN_SETTINGS);
                    if (Util.isEmpty(_forestDataList) &&  !Util.isEmpty(domainDataList)) {
                        upgradeToMultiforestUI ();
                    }
                }
            }
        }
       
        // convert Forest objects to ForestDataBean object before returning
        if (!Util.isEmpty(_forestDataList)) {
            Map data = new HashMap();
            List<ForestDataBean> beans = new ArrayList<ForestDataBean>();
            for (int i = 0; i < _forestDataList.size(); i++) {
                if (_forestDataList.get(i) instanceof ForestDataBean) {
                    beans.add(_forestDataList.get(i));
                } else {
                    data = (Map) _forestDataList.get(i);
                    beans.add(new ForestDataBean(data));
                }
            }
            _forestDataList = beans;
        }
        return _forestDataList;
    }
    
    /**
     * During page load of any old application (before 7.2) we do UI upgrade to transform old AD application into Multi-forest application 
     * Multi-forest application introduced in 7.2 and below function takes care to upgrade into new multiforest UI 
     * Below function getting called if "forestSetting" key not present in application , if this key is not present then it means 
     * application is old application and we need to upgrade it 
     */
    
    public boolean upgradeToMultiforestUI () throws GeneralException {

            _upgradedSucessfully = false ;
            //Read values from old AD application 
            String forestGC = Util.isNotNullOrEmpty(_app.getStringAttributeValue(ATT_FOREST_GC)) ? _app.getStringAttributeValue(ATT_FOREST_GC) : new String();
            String forestAdmin = Util.isNotNullOrEmpty(_app.getStringAttributeValue(ATT_FOREST_ADMIN)) ? _app.getStringAttributeValue(ATT_FOREST_ADMIN) : new String();
            String forestAdminPassword = Util.isNotNullOrEmpty(_app.getStringAttributeValue(ATT_FOREST_ADMIN_PASS)) ? _app.getStringAttributeValue(ATT_FOREST_ADMIN_PASS) : new String();
            boolean useSSLForGC = _app.getBooleanAttributeValue(USE_SSL_FOR_GC);
            String exchangeversion = Util.isNotNullOrEmpty(_app.getStringAttributeValue(ATT_EXCHANGE_VERSION)) ? _app.getStringAttributeValue(ATT_EXCHANGE_VERSION) : new String();
            List<String> _exchangeServers = !Util.isEmpty(_app.getListAttributeValue(ATT_EXCHANGE_SERVERS)) ? _app.getListAttributeValue(ATT_EXCHANGE_SERVERS) : new ArrayList<String>();

             
            if (_forestDataList == null)
                _forestDataList = new ArrayList();
            
            //To Populate _domainDataList we are calling getDomainDataList  which may throw GeneralException
            //We need _domainDataList get populated for further action like doesAllDomainSSLUnabled() and adding "defaultForest" String in old AD domains 
            getDomainDataList();
            
            if (_domainDataList == null)
                _domainDataList = new ArrayList();
            
            if (_exchangeDataList == null)
                _exchangeDataList = new ArrayList();

            if (  Util.isNotNullOrEmpty(exchangeversion) && !exchangeversion.equals("2007") && !exchangeversion.equalsIgnoreCase("DEFAULT") && Util.isEmpty(_exchangeDataList)) {
                ExchangeDataBean exchangeDataBean = new ExchangeDataBean();
                if ( !Util.isEmpty(_exchangeServers)) {
                    exchangeDataBean.getObject().setExchHost(_exchangeServers);
                }
                exchangeDataBean.getObject().setExchangeForest("defaultForest");
                exchangeDataBean.getObject().setAccountForestList(Util.stringToList("defaultForest"));
                _exchangeDataList.add(exchangeDataBean);
            }
            // Move old AD application values to new  Forest Configuration table and domain Configuration table  
             
            if (Util.isNotNullOrEmpty(forestGC) || Util.isNotNullOrEmpty(forestAdmin) || Util.isNotNullOrEmpty(forestAdminPassword)) {
                ForestDataBean forestBean = new ForestDataBean();
                forestBean.getObject().setForestName("defaultForest");
                forestBean.getObject().setGcServer(forestGC);
                forestBean.getObject().setUser(forestAdmin);
                forestBean.getObject().setPassword(forestAdminPassword);
                if (useSSLForGC) {
                    forestBean.getObject().setUseSSL(true);   
                } else {
                    forestBean.getObject().setUseSSL(doesAllDomainSSLUnabled());
                }
                forestBean.getObject().setAuthenticationType("simple");
                _forestDataList.add(forestBean);
                
                for (DomainDataBean bean : _domainDataList) {
                        if (Util.isNullOrEmpty(bean.getObject().getDomainForestName())) {
                            bean.getObject().setDomainForestName("defaultForest");
                            _upgradedSucessfully = true ;
                    }
                }
                
                
            } else { //if DiscoverDomain not configured then insert one default entry in forest table populate same in domain configuration table
                ForestDataBean fbean = new ForestDataBean();
                fbean.getObject().setForestName("defaultForest");
                _forestDataList.add(fbean);
                
                for (DomainDataBean bean : _domainDataList) {
                    if (Util.isNullOrEmpty(bean.getObject().getDomainForestName())) {
                        bean.getObject().setDomainForestName("defaultForest");
                        _upgradedSucessfully = true ;
                    }
                }
                
            }
            
            getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
            getSessionScope().put(ATT_DOMAIN_SETTINGS, _domainDataList);
            getSessionScope().put(ATT_EXCHANGE_SETTINGS, _exchangeDataList);
            /*Remove moved fields from app obj*/
        

        return _upgradedSucessfully;
    }

    public boolean doesAllDomainSSLUnabled () throws GeneralException {
        boolean flag = false;
        if (!Util.isEmpty(_domainDataList)) {
            int count = 0;
            for (DomainDataBean bean : _domainDataList) {
                if (bean.getObject().isUseSSL()) {
                    count ++;
                    if (count == _domainDataList.size()) {
                        flag = true;
                    }
                }
            }
            
        }
        
        return flag;
    }
    
    public  void setforestDataList(List<ForestDataBean> forestDataList) {
        _forestDataList = forestDataList;
    }

    public void setExchangeDataList(List<ExchangeDataBean> exchangeDataList) {
        _exchangeDataList = exchangeDataList;
    }

    public void setDomainDataList(List<DomainDataBean> domainData) {
        _domainDataList = domainData;
    }

    public ForestDataBean getforestDataObj() {
        return _forestDataObj;
    }
    
    public void setforestDataObj(ForestDataBean forestDataObj) {
        _forestDataObj = forestDataObj;
    }
    
    public DomainDataBean getDomainDataObj() {
        return _domainDataObj;
    }

    public ExchangeDataBean getExchangeDataObj() {
        return _exchangeDataObj;
    }


    public void setExchangeDataObj(ExchangeDataBean _exchangeDataObj) {
        this._exchangeDataObj = _exchangeDataObj;
    }


    public void setDomainDataObj(DomainDataBean domainDataObj) {
        _domainDataObj = domainDataObj;
    }

    public boolean isDomainSuccess() {
        return _domainSuccess;
    }
    
    public boolean isForestSuccess() {
        return _forestSuccess;
    }

    public boolean isExchangeSuccess() {
        return _exchangeSuccess;
    }

    public void setExchangeSuccess(boolean _exchangeSuccess) {
        this._exchangeSuccess = _exchangeSuccess;
    }

    public String getDomainResult() {
        return _domainResult;
    }

    public String getForestTableOperationResult() {
        return _forestTableOperationResult;
    }
    
    public String getExchangeTableOperationResult() {
        return _exchangeTableOperationResult;
    }

    public void setExchangeTableOperationResult(String _exchangeTableOperationResult) {
        this._exchangeTableOperationResult = _exchangeTableOperationResult;
    }


    public boolean isStoredOnSession() {
        return true;
    }

    protected Class getScope() {
        return Application.class;
    }

    /**
     * Convenience bean class to make it easy to use any JSON
     * library to serialize server names to JSON like:
     *    [ {"name":"svr1"}, {"name":"srv2"} ]
     */
    static class NamedServer {
        private String name;

        public NamedServer(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public String toString() {
            return getName();
        }
    }

    public String getDiscoverServers() throws ConnectorException {
        try {
            if (!Util.isEmpty(_forestDataList)) {
                for (int k= 0 ; _forestDataList.size() > k ; k++) {
                    String selectedDomainForestId = getRequestParameter("domainForestID");
                    if (Util.isNotNullOrEmpty(selectedDomainForestId) && _forestDataList.get(k).getId().equalsIgnoreCase(selectedDomainForestId)) {
                        if (_app != null) {
                            Attributes attr = _app.getAttributes();
                            String forestAdmin = _forestDataList.get(k).getObject().getUser();
                            String forestAdminPassword = _forestDataList.get(k).getObject().getPassword();
                            String forestGC = _forestDataList.get(k).getObject().getGcServer();
                            boolean forestTLS = _forestDataList.get(k).getObject().isUseSSL();                                    
                            String forestDomain = getRequestParameter("domain");
                            List<String> serverList = new ArrayList<String>();
                            serverList.addAll(Arrays.asList(getRequestParameterValues("servers")));
                            if (Util.isNotNullOrEmpty(forestAdmin) && Util.isNotNullOrEmpty(forestAdminPassword)
                                    && Util.isNotNullOrEmpty(forestGC)) {
                                Connector conn = ConnectorFactory.getConnector(_app, null);
                                _forestConfig = new HashMap<String, Object>();
                                _forestConfig.put(ATT_FOREST_ADMIN, forestAdmin);
                                _forestConfig.put(ATT_FOREST_ADMIN_PASS,
                                        forestAdminPassword);
                                _forestConfig.put(ATT_FOREST_GC, forestGC);
                                _forestConfig.put(ATT_FOREST_DOMAIN, forestDomain);
                                _forestConfig.put(FOREST_USE_TLS, forestTLS);
                                Map<String, Object> domainsFrmForest = new HashMap<String, Object>();
                                if (log.isDebugEnabled()) {
                                    log.debug("Before conn.discoverApplicationAttributes");
                                }
                                domainsFrmForest = conn
                                        .discoverApplicationAttributes(_forestConfig);
                                if (log.isDebugEnabled()) {
                                    log.debug("After conn.discoverApplicationAttributes");
                                }
                                List<String> servers = new ArrayList<String>();
                                servers = (ArrayList<String>) domainsFrmForest
                                        .get(DOMAIN_CONTROLLER);

                                List<NamedServer> namedServers = new ArrayList<NamedServer>();
                                
                                for(int i=0; i<servers.size(); i++) {
                                    namedServers.add( new NamedServer(servers.get(i)));
                                }
            
                                if(!Util.isEmpty(serverList)) {
                                    serverList.removeAll(servers);
                                    String temp = "";
                                    for(int i=0; i<serverList.size(); i++) {
                                        temp = serverList.get(i);
                                        if(Util.isNotNullOrEmpty(temp)) {
                                            namedServers.add( new NamedServer(temp));
                                        }
                                    }
                                }
                                
                                if (log.isDebugEnabled())
                                    log.debug("Servers returned by connector - " + namedServers);
                                return JsonHelper.toJson(namedServers);
                            } else {
                                if (log.isDebugEnabled())
                                    log.debug("Username, Password or GC not provided");
                            }
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            _serverResult = ExceptionCleaner.cleanConnectorException(ex);
            _serverSuccess = false;
            if (log.isDebugEnabled())
                log.debug("Failed to discover servers.", ex);
        }
        return "";
    }

   
    /**
     * Returns flag for hiding and showing DomainConfiguration section in UI
     * 
     * @return
     */
    public boolean getHideShowDomainConfigurations() {
        boolean show = true;
        if (!Util.isEmpty(_forestDataList)) {
                int count = 0 ;
                for (ForestDataBean  forestBean : _forestDataList) { 
                    //even if any single checkbox unchecked then we need to show DomainConfiguration
                     if (forestBean.getObject().isPreviewDomains()) {
                         show = true;
                         break;
                    }
                     if (forestBean.getObject().isManageAllDomain()) {
                         count ++;
                         if (count == _forestDataList.size()) {
                             show = false;
                         }
                     } 
                }
                 
        }       
        return show ;
    }
    
    /*
     * if manage All Domain operation is failed then we will revert back checkbox state to uncheck
     */
    public void revertManageAllDomainState (String selectedForestName) {
        if (_forestDataList == null) {
            _forestDataList = new ArrayList<ForestDataBean>();
        }
          for (int k= 0 ; _forestDataList.size() > k ; k++) {
            if (Util.isNotNullOrEmpty(selectedForestName) && _forestDataList.get(k).getObject().getForestName().equalsIgnoreCase(selectedForestName)) {
             if (_app != null) {
                if (_forestDataList.get(k).getObject().isManageAllDomain()) {
                    _forestDataList.get(k).getObject().setManageAllDomain(false);
                    getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                }
             }
            }
          }
        
    }
    public void removeAllDomainsOfForest(String forestName) {
        Iterator<DomainDataBean> it = _domainDataList.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            Map data = null;
             if (obj instanceof DomainDataBean) {
                 DomainDataBean bean = (DomainDataBean)obj;
                 if (Util.isNotNullOrEmpty(bean.getObject().getDomainForestName()) && bean.getObject().getDomainForestName().equalsIgnoreCase(forestName)){
                     it.remove();
                   }
               }else {
                   data = (Map) obj;
                    String tempDomainForestName = (String) data.get(ATT_DOMAIN_FOREST_NAME);
                     if (tempDomainForestName.equalsIgnoreCase(forestName)) {
                         it.remove();
                    }
              }
        }
        getSessionScope().put(ATT_DOMAIN_SETTINGS, _domainDataList);
    }
    
    @SuppressWarnings("unchecked")
    public String toggleForestAuthenticationType() {
        
        // Get the params we sent to function
        String selectedForestName = getSelectedForestName();
        boolean useTls            = isUseTLSOperation();
        
        // go over all forests configured in app
        for (ForestDataBean forestBean : _forestDataList) {
            // if this is the forest row where operation is performed
            if(forestBean.getObject().getForestName().equalsIgnoreCase(selectedForestName)) {
                // update the bean object
                forestBean.getObject().setAuthEnabled(!useTls);
                forestBean.getObject().setUseSSL(useTls);
                
                String gcServerWithPort = forestBean.getObject().getGcServer();
                
                // If user has filled gc server
                if (Util.isNotNullOrEmpty(gcServerWithPort)) {
                    
                    String gcServerAddressWithoutPort =  gcServerWithPort.indexOf(":") > 0 ? gcServerWithPort.substring(0, gcServerWithPort.indexOf(":")) : gcServerWithPort;
                    String gcPort                     =  gcServerWithPort.indexOf(":") > 0 ? gcServerWithPort.substring(gcServerWithPort.indexOf(":") + 1, gcServerWithPort.length()) : null;
                    
                    // If TLS is checked
                    if (useTls) {
                        if("3268".equals(gcPort) || Util.isNothing(gcPort))
                            gcPort = "3269";
                    }
                    // if TLS is disabled
                    else {
                        if("3269".equals(gcPort) || Util.isNothing(gcPort))
                            gcPort = "3268";
                    }
                    
                    gcServerWithPort = gcServerAddressWithoutPort + ":" + gcPort;
                }
                
                // update in bean
                forestBean.getObject().setGcServer(gcServerWithPort);
                
                break;
            }
        }
        
        // Update the bean in session
        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
        
        return "";
    }
    
    @SuppressWarnings("unchecked")
    public String toggleDomainAuthenticationType() {
        
        // Get the params we sent to function
        String selectedDomainName = getSelectedDomainName();
        boolean useTls            = isUseTLSOperation();
        
        // go over all forests configured in app
        for (DomainDataBean domainBean : _domainDataList) {
            // if this is the forest row where operation is performed
            if(domainBean.getObject().getDomainDN().equalsIgnoreCase(selectedDomainName)) {
                // update the bean object
                domainBean.getObject().setAuthEnabled(!useTls);
                domainBean.getObject().setUseSSL(useTls);
                break;
            }
        }
        
        // Update the bean in session
        getSessionScope().put(ATT_DOMAIN_SETTINGS, _domainDataList);
        
        return "";
    }
    
    boolean hasCredentialConfiguration() {
        CredentialRetriever cr = new CredentialRetriever(getContext());
        return cr.hasConfiguration(_app);
    }
    
    /**
     * Discover domains in the forest and populate the domain list
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public String discoverDomains() {
        if (hasCredentialConfiguration()) {
            _forestTableOperationResult = "Domain Discovery can not be performed when Credential Cycling is enabled.";
            _forestSuccess = false;
            return "";
        }
        
        String currentForestName = new String();

        try {
            if (_domainDataList == null) {
                _domainDataList = new ArrayList<DomainDataBean>();
            }
            if (_forestDataList == null) {
                _forestDataList = new ArrayList<ForestDataBean>();
            }
            boolean domainExistsAlready = false;
            log.info(" ManageAllDomain Operation : " + isManageAllDomainOperation());
            if (!Util.isEmpty(_forestDataList)) {
                for (int k = 0; _forestDataList.size() > k; k++) {

                    String selectedForestName = getSelectedForestName();
                    if (Util.isNotNullOrEmpty(selectedForestName) &&
                            _forestDataList.get(k).getObject().getForestName().equalsIgnoreCase(selectedForestName)) {
                        if (_app != null) {
                            // on useTLS operation update GC port if not there
                            if (isUseTLSOperation() &&
                                    Util.isNotNullOrEmpty(_forestDataList.get(k).getObject().getGcServer())) {
                                String gc = _forestDataList.get(k).getObject().getGcServer();
                                boolean useTLS = _forestDataList.get(k).getObject().isUseSSL();
                                // if useTLS is checked and no port mentioned in gc then append default ssl port
                                if (useTLS) {
                                    if (!gc.contains(":")) {
                                        StringBuilder ForestGCServer = new StringBuilder(gc.trim());
                                        ForestGCServer = ForestGCServer.append(":").append(ForestData.GC_SSL_PORT);
                                        _forestDataList.get(k).getObject().setGcServer(ForestGCServer.toString());
                                        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                                        break;
                                    } else if (gc.contains(":3268")) {
                                        gc = gc.replace(":3268", ":3269");
                                        _forestDataList.get(k).getObject().setGcServer(gc.toString());
                                        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                                        break;
                                    } else {
                                        break;
                                    }
                                } else if (!useTLS) {
                                    if (!gc.contains(":")) {
                                        StringBuilder ForestGCServer = new StringBuilder(gc.trim());
                                        ForestGCServer = ForestGCServer.append(":").append(ForestData.GC_DEFAULT_PORT);
                                        _forestDataList.get(k).getObject().setGcServer(ForestGCServer.toString());
                                        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                                        break;
                                    } else if (gc.contains(":3269")) {
                                        gc = gc.replace(":3269", ":3268");
                                        _forestDataList.get(k).getObject().setGcServer(gc.toString());
                                        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                                        break;
                                    } else {
                                        break;
                                    }
                                }

                            }

                            // If user do uncheck operation on "Manage All Domain" checkbox then remove all domains from
                            // domain Configuration,we add all domains on check opration again
                            if (isManageAllDomainOperation() &&
                                    _forestDataList.get(k).getObject().isManageAllDomain() == false) {
                                _forestDataList.get(k).getObject().setPreviewDomains(false);
                                if (log.isDebugEnabled())
                                    log.debug("removing domains for forest : " + selectedForestName);
                                removeAllDomainsOfForest(selectedForestName);
                                _forestSuccess = true;
                                continue;
                            }
                            String forestAdmin = _forestDataList.get(k).getObject().getUser();
                            String forestAdminPassword = _forestDataList.get(k).getObject().getPassword();
                            String forestGC = _forestDataList.get(k).getObject().getGcServer();
                            currentForestName = _forestDataList.get(k).getObject().getForestName();
                            boolean forestUseTLS = _forestDataList.get(k).getObject().isUseSSL();
                            String forestAuthenticationType = _forestDataList.get(k).getObject()
                                    .getAuthenticationType();
                            if (Util.isNotNullOrEmpty(forestAdmin) && Util.isNotNullOrEmpty(forestAdminPassword) &&
                                    Util.isNotNullOrEmpty(forestGC) && Util.isNotNullOrEmpty(currentForestName)) {

                                // Now that everything is valid, so let us hide / show domain table
                                if (isManageAllDomainOperation()) {
                                    // previewDomain is flag used to render domain data even if "Manage All Domain" is
                                    // checked for that forest
                                    // when preview domain is true we render domains in read only format and we make it
                                    // true when user click on preview button
                                    _forestDataList.get(k).getObject().setPreviewDomains(false);
                                    _showDomainConfiguration = getHideShowDomainConfigurations();
                                }

                                Connector conn = ConnectorFactory.getConnector(_app, null);
                                _forestConfig = new HashMap<String,Object>();
                                _forestConfig.put(ATT_IQSERVICE_HOST, _app.getStringAttributeValue(ATT_IQSERVICE_HOST));
                                _forestConfig.put(ATT_IQSERVICE_PORT, _app.getStringAttributeValue(ATT_IQSERVICE_PORT));
                                _forestConfig.put(ATT_FOREST_ADMIN, forestAdmin);
                                _forestConfig.put(ATT_FOREST_ADMIN_PASS, forestAdminPassword);
                                _forestConfig.put(FOREST_USE_TLS, forestUseTLS);
                                _forestConfig.put(ATT_FOREST_AUTH_TYPE, forestAuthenticationType);
                                // We expect GC:Port , however if its not we need to append port
                                if (forestGC.contains(":")) {
                                    _forestConfig.put(ATT_FOREST_GC, forestGC);
                                } else {
                                    if (_forestDataList.get(k).getObject().isUseSSL()) {
                                        StringBuilder ForestGCServer = new StringBuilder(forestGC.trim());
                                        ForestGCServer = ForestGCServer.append(":").append(ForestData.GC_SSL_PORT);
                                        _forestConfig.put(ATT_FOREST_GC, ForestGCServer.toString());
                                        _forestDataList.get(k).getObject().setGcServer(ForestGCServer.toString());
                                        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                                    } else {
                                        StringBuilder ForestGCServer = new StringBuilder(forestGC.trim());
                                        ForestGCServer = ForestGCServer.append(":").append(ForestData.GC_DEFAULT_PORT);
                                        _forestConfig.put(ATT_FOREST_GC, ForestGCServer.toString());
                                        _forestDataList.get(k).getObject().setGcServer(ForestGCServer.toString());
                                        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                                    }
                                }
                                Map<String,Object> domainsFrmForest = new HashMap<String,Object>();
                                if (log.isDebugEnabled()) {
                                    log.debug("Before conn.discoverApplicationAttributes");
                                }
                                domainsFrmForest = conn.discoverApplicationAttributes(_forestConfig);
                                if (log.isDebugEnabled()) {
                                    log.debug("After conn.discoverApplicationAttributes");
                                }
                                List<String> domains = new ArrayList<String>();
                                domains = (ArrayList<String>) domainsFrmForest.get(ATT_FOREST_DOMAIN);
                                if (log.isDebugEnabled())
                                    log.debug("Domains returned by connector - " + domains);
                                // iterating over the list of domains received from MS
                                for (int i = 0; i < domains.size(); i++) {
                                    domainExistsAlready = false;
                                    DomainDataBean dataObject = new DomainDataBean();
                                    String curDomain = domains.get(i);
                                    String tempDomainDN = "";
                                    Map data = null;
                                    for (int j = 0; j < _domainDataList.size(); j++) { // iterating over the existing domain entries in Domain configuration
                                        if (_domainDataList.get(j) instanceof DomainDataBean) {
                                            tempDomainDN = _domainDataList.get(j).getObject().getDomainDN();
                                        } else {
                                            data = (Map) _domainDataList.get(j);
                                            tempDomainDN = (String) data.get(ATT_DOMAIN_DN);
                                        }

                                        if (tempDomainDN.equalsIgnoreCase(curDomain)) {
                                            domainExistsAlready = true;
                                            _domainDataList.get(j).getObject().setDomainForestName(selectedForestName);
                                            // on "manage all domain" check operation we need to override domain
                                            // credentials and TLS values with forest table values
                                            if (isManageAllDomainOperation() &&
                                                    _forestDataList.get(k).getObject().isManageAllDomain() == true) {
                                                if (log.isDebugEnabled())
                                                    log.debug("overriding credentials of existing domain : " +
                                                            _domainDataList.get(j).getObject().getDomainDN());
                                                _domainDataList.get(j).getObject()
                                                        .setUser(_forestDataList.get(k).getObject().getUser());
                                                _domainDataList.get(j).getObject()
                                                        .setPassword(_forestDataList.get(k).getObject().getPassword());
                                                _domainDataList.get(j).getObject()
                                                        .setUseSSL(_forestDataList.get(k).getObject().isUseSSL());
                                                _domainDataList.get(j).getObject().setAuthenticationType(
                                                        _forestDataList.get(k).getObject().getAuthenticationType());
                                            }

                                            break;
                                        }
                                    }

                                    if (domainExistsAlready) {

                                        // on "manage all domain" check operation if we have domains already listed in
                                        // domain configuration then no need to add domain entry again on domain configuration
                                        if (isManageAllDomainOperation() &&
                                                _forestDataList.get(k).getObject().isManageAllDomain() == true) {
                                            continue;
                                        } else if (!isManageAllDomainOperation()) { // if its Discover domain operation no need to add domain entry again
                                                                                    // on domain configuration
                                            continue;
                                        }
                                    }

                                    dataObject.object.setDomainDN(curDomain);
                                    dataObject.object
                                            .setDomainForestName(_forestDataList.get(k).getObject().getForestName());
                                    dataObject.object.setUseSSL(_forestDataList.get(k).getObject().isUseSSL());
                                    dataObject.object.setAuthenticationType(
                                            _forestDataList.get(k).getObject().getAuthenticationType());
                                    // on "manage all domain" check operation we set default forest credentials to each domain
                                    // however on Discover operation we didn't set default forest credentials to each domain
                                    if (isManageAllDomainOperation() &&
                                            _forestDataList.get(k).getObject().isManageAllDomain() == true) {
                                        dataObject.object.setUser(_forestDataList.get(k).getObject().getUser());
                                        dataObject.object.setPassword(_forestDataList.get(k).getObject().getPassword());
                                    }
                                    if (_forestConfig.containsKey(ATT_FOREST_DOMAIN))
                                        _forestConfig.remove(ATT_FOREST_DOMAIN);
                                    _forestConfig.put(ATT_FOREST_DOMAIN, curDomain);

                                    _domainDataList.add(dataObject);
                                }
                                if (isManageAllDomainOperation()) {
                                    _forestTableOperationResult = "Configuration saved successfully";
                                } else {
                                    _forestTableOperationResult = "Domain Search Successful for forest : " +
                                            _forestDataList.get(k).getObject().getForestName();

                                }
                                _forestSuccess = true;
                                getSessionScope().put(ATT_DOMAIN_SETTINGS, _domainDataList);
                            } else {
                                _forestTableOperationResult = "Forest Name, GC Server, User and Password Details are required.";
                                _forestSuccess = false;

                                // Let us revert the manage all state, as we failed
                                revertManageAllDomainState(currentForestName);
                                // and let us recalculate whether domains should be shown or not
                                _showDomainConfiguration = getHideShowDomainConfigurations();
                                if (log.isDebugEnabled())
                                    log.debug("Forest Name, Username, Password and GCServer details not provided.");
                            }
                        }
                    }
                }
                getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
            }
        } catch (Throwable ex) {
            String error = "Failed to discover domains for : " + currentForestName + " ";
           
            if (isManageAllDomainOperation()) {
                // If manage all domain operation is not successful then remove domains from Domain Configuration of
                // that forest
                removeAllDomainsOfForest(currentForestName);
                // in case manage all domain operation is failed then revert back checkbox state to unchecked
                revertManageAllDomainState(currentForestName);
            }
            if (ex.getCause() != null) {
                
                if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                    error += ex.getCause().getMessage();
                }
            } else {
                error += ex.getClass().getName();
            }
            _forestTableOperationResult = error;
            _forestSuccess = false;
            if (log.isErrorEnabled())
                log.error("Failed to discover domains.", ex);
        }

        return "";
    }
    
    /**
     * Discover domains in the forest and populate the domain list
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public String previewDomains() {
        _showDomainConfiguration = true ;
        String selectedForestName = getSelectedForestName();
        
        if (_forestDataList == null)
            _forestDataList = new ArrayList();
        
        for (int k= 0 ; _forestDataList.size() > k ; k++) {
            if (_forestDataList.get(k).getObject().getForestName().equalsIgnoreCase(selectedForestName)) {
                if (!_forestDataList.get(k).getObject().isPreviewDomains()) {
                    _forestDataList.get(k).getObject().setPreviewDomains(true);
                } else {
                    _forestDataList.get(k).getObject().setPreviewDomains(false);
                }
            }
        }
        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
        getSessionScope().put(ATT_DOMAIN_SETTINGS, _domainDataList);
        return "sucess";
    }
   
    /**
     * Read list of domains
     * 
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<DomainDataBean> getDomainDataList() throws GeneralException {
        if(_domainDataList == null) {
            if (_app != null) {
                _domainDataList = (List<DomainDataBean>) _app
                        .getListAttributeValue(ATT_DOMAIN_SETTINGS);
            }
        }

        // convert domain objects to DomainDataBean object before returning
        if (!Util.isEmpty(_domainDataList)) {
            Map data = new HashMap();
            List<DomainDataBean> beans = new ArrayList<DomainDataBean>();
            for (int i = 0; i < _domainDataList.size(); i++) {
                if (_domainDataList.get(i) instanceof DomainDataBean) {
                    beans.add(_domainDataList.get(i));
                } else {
                    data = (Map) _domainDataList.get(i);
                    beans.add(new DomainDataBean(data));
                }
            }
            _domainDataList = beans;
        }
        return _domainDataList;
    }

    /**
     * Read list of Exchange settings
     *
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<ExchangeDataBean> getExchangeDataList() throws GeneralException {
        if(_exchangeDataList == null) {
            if (_app != null) {
                _exchangeDataList = (List<ExchangeDataBean>) _app
                        .getListAttributeValue(ATT_EXCHANGE_SETTINGS);
            }
        }

        // convert Exchange objects to ExchangeDataBean object before returning
        if (!Util.isEmpty(_exchangeDataList)) {
            Map data = new HashMap();
            List<ExchangeDataBean> beans = new ArrayList<ExchangeDataBean>();
            for (int i = 0; i < _exchangeDataList.size(); i++) {
                if (_exchangeDataList.get(i) instanceof ExchangeDataBean) {
                    beans.add(_exchangeDataList.get(i));
                } else {
                    data = (Map) _exchangeDataList.get(i);
                    beans.add(new ExchangeDataBean(data));
                }
            }
            _exchangeDataList = beans;
        }
        return _exchangeDataList;
    }

    public String addForestData() {
        String _forestresult = "";
        
        if (_forestDataList == null)
            _forestDataList = new ArrayList();
        
        String forestName = _forestDataObj.getObject().getForestName();
        String gcServer = _forestDataObj.getObject().getGcServer();
        
        
        if (Util.isNullOrEmpty(forestName)) {
                _domainResult = "";
                _domainSuccess = false;
                _forestSuccess = false;
                this._forestTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_FORESTNAME_REQUIRED).toString();
                _forestresult = "";
         } else {
            boolean existsAlready = false;
            Map data = null;
            String tempForestName = "";
            String tempGcServer = "";
            for (int i = 0; i < _forestDataList.size(); i++) {
                if (_forestDataList.get(i) instanceof ForestDataBean){
                    tempForestName = _forestDataList.get(i).getObject()
                            .getForestName();
                    tempGcServer = _forestDataList.get(i).getObject().getGcServer();
                }
                else {
                    data =(Map)_forestDataList.get(i);
                    tempForestName = (String)data.get(ATT_FOREST_NAME);
                    tempGcServer = (String)data.get(ATT_GC_SERVER);
                }
                
                if (tempForestName.equalsIgnoreCase(_forestDataObj.getObject().getForestName()) || 
                        (Util.isNotNullOrEmpty(tempGcServer) && tempGcServer.equalsIgnoreCase(_forestDataObj.getObject().getGcServer()))) {
                    existsAlready = true;
                }
            }
            
            if (!existsAlready) {
                
                _forestDataList.add(_forestDataObj);
                getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
                _forestDataObj = new ForestDataBean();
                _forestresult = "addedForestInfo";
                
            } else {
                _domainResult = "";
                _domainSuccess = false;
                _forestSuccess = false;
                this._forestTableOperationResult = new Message(Message.Type.Error,
                        MessageKeys.ERR_FOREST_GCSERVER_EXISTS, forestName,gcServer).toString();
                _forestresult = "";
            }
        }
        
        return _forestresult;
        
    }
    /**
     * Add domain data to list
     * 
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String addDomainData() throws GeneralException {
        String result = "";

        if (_domainDataList == null)
            _domainDataList = new ArrayList();
        
        if (_forestDataList == null)
            _forestDataList = new ArrayList();

        String domainDN = _domainDataObj.getObject().getDomainDN();
        String user = _domainDataObj.getObject().getUser();
        String domainForestName = _domainDataObj.getObject().getDomainForestName();
        boolean isForestNameExist = false;
        Map  map = null;
        
        for (int i = 0; i < _forestDataList.size(); i++) {
            if (_forestDataList.get(i) instanceof ForestDataBean){
                if (_forestDataList.get(i).getObject().getForestName().equalsIgnoreCase(domainForestName)) {
                    isForestNameExist = true ;
                }
            }
            else {
                map =(Map)_forestDataList.get(i);
                String tempForestName = (String)map.get(ATT_FOREST_NAME);
                if (domainForestName.equalsIgnoreCase(tempForestName) );{
                    isForestNameExist = true ;
                }
            }
             
        }
        
        
        if (Util.isNullOrEmpty(domainForestName) || Util.isNullOrEmpty(domainDN) || Util.isNullOrEmpty(user) ) {           
            _forestTableOperationResult = "";
            _forestSuccess = false;
            _domainSuccess = false;
            this._domainResult = new Message(Message.Type.Error, MessageKeys.ERR_FOREST_DOMAIN_USER_PASSWORD_REQUIRED).toString();
            result = "";
        } else if (!isForestNameExist) {
                    _forestTableOperationResult = "";
                    _forestSuccess = false;
                    _domainSuccess = false;
                    this._domainResult = new Message(Message.Type.Error, MessageKeys.ERR_FOREST_ENTRY_REQUIRED,domainForestName).toString();
                    result = "";
                    return result;
              } else {
                // If a domain with the same DN already exists, don't add another
                boolean existsAlready = false;
                Map data = null;
                String tempDomainDN = "";
                for (int i = 0; i < _domainDataList.size(); i++) {
                    if (_domainDataList.get(i) instanceof DomainDataBean)
                        tempDomainDN = _domainDataList.get(i).getObject()
                                .getDomainDN();
                    else {
                        data = (Map) _domainDataList.get(i);
                        tempDomainDN = (String) data.get(ATT_DOMAIN_DN);
                    }
    
                    if (tempDomainDN.equalsIgnoreCase(_domainDataObj.getObject()
                            .getDomainDN()))
                        existsAlready = true;
                }
    
                if (!existsAlready) {
                    String tempPort = _domainDataObj.getObject().getPort();
                    if (_domainDataObj.getObject().isUseSSL()) {
                        if (Util.isNotNullOrEmpty(tempPort) && tempPort.equalsIgnoreCase(DomainData.DEFAULT_PORT)) {
                            _domainDataObj.getObject().setPort(DomainData.SSL_PORT);
                        }
                    }
                    if (!_domainDataObj.getObject().isUseSSL()) {
                        if (Util.isNotNullOrEmpty(tempPort) && tempPort.equalsIgnoreCase(DomainData.SSL_PORT)) {
                            _domainDataObj.getObject().setPort(DomainData.DEFAULT_PORT);
                        }
                    }
                    _domainDataList.add(_domainDataObj);
                    getSessionScope().put(ATT_DOMAIN_SETTINGS, _domainDataList);
                    _domainDataObj = new DomainDataBean();
                    result = "addedDomainInfo";
                } else {
                    _forestTableOperationResult = "";
                    _forestSuccess = false;
                    _domainSuccess = false;
                    this._domainResult = new Message(Message.Type.Error,
                            MessageKeys.ERR_DOMAIN_ALREADY_EXISTS, domainDN).toString();
                    result = "";
                }
        }
        return result;
    }
 
    /**
     * Add exchange data to list
     *
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String addExchangeData() throws GeneralException {
        String result = "";

        if (_exchangeDataList == null)
            _exchangeDataList = new ArrayList();

        List<String> exchangeHost = _exchangeDataObj.getObject().getExchHost();
        String user               = _exchangeDataObj.getObject().getUser();
        String password           = _exchangeDataObj.getObject().getPassword();
        String exchangeForestName = _exchangeDataObj.getObject().getExchangeForest();
        List<String> accountForestList  = _exchangeDataObj.getObject().getAccountForestList();
        Map    map                = null;

        if (Util.isNullOrEmpty(exchangeForestName) || Util.isEmpty(exchangeHost) || Util.isNullOrEmpty(user) || Util.isNullOrEmpty(password) || Util.isEmpty(accountForestList) ) {
            _exchangeTableOperationResult = "";
            _exchangeSuccess = false;
            this._exchangeTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_FOREST_EXCHANGE_HOST_USER_PASSWORD_REQUIRED).toString();
            result = "";
            return result;
        }

        boolean isForestNameExist        = false;
        boolean isAccountForestNameExist = false;
        for (int i = 0; i < _forestDataList.size(); i++) {
            if (_forestDataList.get(i) instanceof ForestDataBean) {
                if (_forestDataList.get(i).getObject().getForestName().equalsIgnoreCase(exchangeForestName)) {
                    isForestNameExist = true ;
                }
            }
            else {
                map =(Map)_forestDataList.get(i);
                String tempForestName = (String)map.get(ATT_FOREST_NAME);
                if (exchangeForestName.equalsIgnoreCase(tempForestName) );{
                    isForestNameExist = true ;
                }
            }
        }

        isAccountForestNameExist = validateExchAccountForestName(accountForestList);
        
        if (!isForestNameExist) {
            _exchangeTableOperationResult = "";
            this._exchangeTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_EXCHANGE_FOREST_INVALID,exchangeForestName).toString();
            result = "";
            return result;
        } else if ( !isAccountForestNameExist && (!accountForestList.isEmpty())) {
            _exchangeTableOperationResult = "";
            this._exchangeTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_EXCHANGE_ACCOUNT_FOREST_INVALID,exchangeForestName).toString();
            result = "";
            return result;
        } else {
            // If a exchange setting with the same forest name already exists, don't add another
            boolean existsAlready = false;
            Map data = null;
            String exchangeForest = "";
            for (int i = 0; i < _exchangeDataList.size(); i++) {
                if (_exchangeDataList.get(i) instanceof ExchangeDataBean)
                    exchangeForest = _exchangeDataList.get(i).getObject()
                    .getExchangeForest();
                else {
                    data = (Map) _domainDataList.get(i);
                    exchangeForest = (String) data.get(ATT_EXCHANGE_FOREST_NAME);
                }

                if (exchangeForest.equalsIgnoreCase(_exchangeDataObj.getObject()
                        .getExchangeForest())) {
                    existsAlready = true;
                }
            }

            if (!existsAlready) {
                _exchangeDataList.add(_exchangeDataObj);
                getSessionScope().put(ATT_EXCHANGE_SETTINGS ,_exchangeDataList);
                _exchangeDataObj = new ExchangeDataBean();
                result = "addedExchangeInfo";
            } else {
                _exchangeTableOperationResult = "";
                this._exchangeTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_EXCHANGE_FOREST_INVALID,exchangeForestName).toString();
                result = "";
                return result;
            }
        }
        return result;
    }


    /**
     * Returns false, if any of the Exchange Account Forest Name doesn't match with
     * the configured forest names under ForestSettings
     * 
     * @param accountForestList
     * @param isAccountForestNameExist
     * @return
     */
    protected static boolean validateExchAccountForestName(List<String> accountForestList) {
        List<String> forestNamesList =  new ArrayList<String>();
        boolean isAccountForestNameExist = true;
        for(Object forrestdata : _forestDataList) {
            String forestName = null;
            if (forrestdata instanceof ForestDataBean) {
                forestName = ((ForestDataBean) forrestdata).getObject().getForestName();
            }else {
                Map mapObj =(Map)forrestdata;
                forestName = (String)mapObj.get(ATT_FOREST_NAME);
            }
            if(Util.isNotNullOrEmpty(forestName)) {
                forestNamesList.add(forestName.toLowerCase());
            }
            
        }
        for(String accountForestName  : Util.safeIterable(accountForestList)) {
            
            if (Util.isNotNullOrEmpty(accountForestName)
                    && !forestNamesList.contains(accountForestName.toLowerCase())) {
                isAccountForestNameExist = false;
            }           
        }
        return isAccountForestNameExist;
    }

    /*
     * During save operation to validate at least one domain is configured for each forest   
     */
    public static boolean isDomainEntryPresentForForest (ForestDataBean forestDataBean) {
        boolean isDomainExist = false; 
        String forestName = forestDataBean.getObject().getForestName();
        
        if (_domainDataList == null) {
            _domainDataList = new ArrayList<DomainDataBean>();
        }
    
        for (DomainDataBean domainBean : _domainDataList ) {
            if (Util.isNotNullOrEmpty(forestName) && Util.isNotNullOrEmpty(domainBean.getObject().getDomainForestName())){
                    if (forestName.equalsIgnoreCase(domainBean.getObject().getDomainForestName())){
                        isDomainExist = true;
                        break;
                    }
            }
        }
    	return isDomainExist;
    }
    
    /*
     * During save operation forest data is synced with domain  
     */
    public static boolean syncForestDataWithDomain (ForestDataBean forestDataBean) {
        boolean isDomainExist = false;
        String forestName = forestDataBean.getObject().getForestName();
        for (DomainDataBean domainBean : _domainDataList ) {
            if (Util.isNotNullOrEmpty(forestName) && Util.isNotNullOrEmpty(domainBean.getObject().getDomainForestName())){
                if (forestName.equalsIgnoreCase(domainBean.getObject().getDomainForestName())){
                    if (forestDataBean.getObject().isManageAllDomain()) {
                        domainBean.getObject().setUser(forestDataBean.getObject().getUser());
                        domainBean.getObject().setPassword(forestDataBean.getObject().getPassword());
                        domainBean.getObject().setUseSSL(forestDataBean.getObject().isUseSSL());
                    }else {
                        domainBean.getObject().setUseSSL(forestDataBean.getObject().isUseSSL());
                    }
                }
            }
        }
        return isDomainExist;
    }
    
    /**
     * Checks if the domain belongs to resource forest domain
     * 
     * @param domainBean
     * @return
     */
    public static boolean isResourceForesrDomain(DomainDataBean domainBean) {
        boolean isResourceForestDomain = false;
        String forestName = domainBean.getObject().getDomainForestName();
        if (Util.isNotNullOrEmpty(forestName)) {
            for (ForestDataBean fbean : Util.safeIterable(_forestDataList)) {
                if (forestName.equalsIgnoreCase(fbean.getObject().getForestName())) {
                    isResourceForestDomain = fbean.getObject().getIsResourceForest();
                }
            }
        }
        return isResourceForestDomain;

    }
    
    public static void reset() {
        ManageDomainBean bean = new ManageDomainBean();
        bean.getSessionScope().remove(ATT_DOMAIN_SETTINGS);
        bean.getSessionScope().remove(ATT_FOREST_SETTINGS);
        bean.getSessionScope().remove(ATT_EXCHANGE_SETTINGS);
        _forestDataList   = null;
        _domainDataList   = null;
        _exchangeDataList = null;
    }

    @SuppressWarnings("unchecked")
    public String removeForestData() {

        if (_forestDataList == null) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_FORESTS_DEFINED),
                    null);
            return "";
        }

        // create a key set
        Set<String> forests = null;
        if (_selectedForest != null) {
            forests = _selectedForest.keySet();
        }

        if ((forests == null) || (forests.size() == 0)) {
            _domainResult = "";
            _domainSuccess = false;
            _forestSuccess = false;
            this._forestTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_NO_FORESTS_SELECTED).toString();
            return "";
        }
   
        // store keys to be removed
        Set<String> forestToRemove = new HashSet<String>();
        for (String forest : forests) {
            if (_selectedForest.get(forest)) {
                forestToRemove.add(forest);
            }
        }

        // remove the selected scopes from the list of scopes
        Iterator<ForestDataBean> i = _forestDataList.iterator();
        while (i.hasNext()) {
            ForestDataBean currentBean = i.next();
            if (forestToRemove.contains(currentBean.getId())) {
                if (isDomainEntryPresentForForest(currentBean)) {
                    _domainResult = "";
                    _domainSuccess = false;
                    _forestSuccess = false;
                    this._forestTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_DELETE_ALL_LINKED_DOMAINS_FIRST_1,currentBean.getObject().getForestName()).toString();
                    if (currentBean.getObject().isManageAllDomain()) {
                        this._forestTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_DELETE_ALL_LINKED_DOMAINS_FIRST_2,currentBean.getObject().getForestName()).toString();
                    }
                    return "";
                } else {
                    i.remove();
                }
            }
        }
        getSessionScope().put(ATT_FOREST_SETTINGS, _forestDataList);
        return "removedForest";
    }

    @SuppressWarnings("unchecked")
    public String removeDomainData() {

        if (_domainDataList == null) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_DOMAINS_DEFINED),
                    null);
            return "";
        }

        // create a key set
        Set<String> domains = null;
        if (_selectedDomains != null) {
            domains = _selectedDomains.keySet();
        }

        if ((domains == null) || (domains.size() == 0)) {
            _forestTableOperationResult = "";
            _forestSuccess = false;
            _domainSuccess = false;
            this._domainResult = new Message(Message.Type.Error, MessageKeys.ERR_NO_DOMAINS_SELECTED).toString();
            return "";
        }

        // store keys to be removed
        Set<String> domainsToRemove = new HashSet<String>();
        for (String domain : domains) {
            if (_selectedDomains.get(domain)) {
                domainsToRemove.add(domain);
            }
        }

        // remove the selected scopes from the list of scopes
        Iterator<DomainDataBean> i = _domainDataList.iterator();
        while (i.hasNext()) {
            DomainDataBean currentBean = i.next();
            if (domainsToRemove.contains(currentBean.getId())) {
                i.remove();
            }
        }
        getSessionScope().put(ATT_DOMAIN_SETTINGS, _domainDataList);
        return "removedDomains";
    }

    public String removeExchangeData() {

        if (_exchangeDataList == null) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_EXCHANGE_FOREST_DEFINED),
                    null);
            return "";
        }

        // create a key set
        Set<String> exchangeList = null;
        if (_selectedExchange != null) {
            exchangeList = _selectedExchange.keySet();
        }

        if ((exchangeList == null) || (exchangeList.size() == 0)) {
            _exchangeTableOperationResult = "";
            _exchangeSuccess = false;
            this._exchangeTableOperationResult = new Message(Message.Type.Error, MessageKeys.ERR_NO_EXCHANGE_FOREST_SELECTED).toString();
            return "";
        }

        // store keys to be removed
        Set<String> exchangeToRemove = new HashSet<String>();
        for (String exchange : exchangeList) {
            if (_selectedExchange.get(exchange)) {
                exchangeToRemove.add(exchange);
            }
        }

        // remove the selected scopes from the list of scopes
        Iterator<ExchangeDataBean> i = _exchangeDataList.iterator();
        while (i.hasNext()) {
            ExchangeDataBean currentBean = i.next();
            if (exchangeToRemove.contains(currentBean.getId())) {
                i.remove();
            }
        }
        getSessionScope().put(ATT_EXCHANGE_SETTINGS, _exchangeDataList);
        return "removedExchange";
    }

    public class ForestDataBean extends BaseBean {

        /**
         * The current Forest data we are editing/creating.
         */
        private ForestData object;

        private String _id;

        public ForestDataBean() {
            super();
            _id = "F" + Util.uuid();
            object = new ForestData();
        }

        public ForestDataBean(Map objectMap) {
            super();
            _id = "F" + Util.uuid();
            object = new ForestData(objectMap);
        }

        public ForestDataBean(ForestData source) {
            this();
            object = source;
        }

        public ForestDataBean(ForestDataBean source) {
            this.object = source.getObject(); 
        }

        public ForestData getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }
    }
    
    public class DomainDataBean extends BaseBean {

        /**
         * The current DominoData we are editing/creating.
         */
        private DomainData object;

        private String _id;

        public DomainDataBean() {
            super();
            _id = "D" + Util.uuid();
            object = new DomainData();
        }

        public DomainDataBean(Map objectMap) {
            super();
            _id = "D" + Util.uuid();
            object = new DomainData(objectMap);
        }

        public DomainDataBean(DomainData source) {
            this();
            object = source;
        }

        public DomainDataBean(DomainDataBean source) {
            this.object = source.getObject();
        }

        public DomainData getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }
    }

    public class ExchangeDataBean extends BaseBean {

        /**
         * The current Exchange data we are editing/creating.
         */
        private ExchangeData object;

        private String _id;

        public ExchangeDataBean() {
            super();
            _id = "E" + Util.uuid();
            object = new ExchangeData();
        }

        public ExchangeDataBean(Map objectMap) {
            super();
            _id = "E" + Util.uuid();
            object = new ExchangeData(objectMap);
        }

        public ExchangeDataBean(ExchangeData source) {
            this();
            object = source;
        }

        public ExchangeDataBean(ExchangeDataBean source) {
            this.object = source.getObject(); 
        }

        public ExchangeData getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }
    }
}