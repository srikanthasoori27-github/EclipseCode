/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
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
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ExceptionCleaner;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.PE2SiteConfig;
import sailpoint.object.Rule;
import sailpoint.object.SharePointSiteConfig;
import sailpoint.object.TargetHostConfig;
import sailpoint.object.TargetSource;
import sailpoint.object.WindowsShare;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import sailpoint.unstructured.SecurityIQTargetCollector;
import sailpoint.unstructured.TargetCollector;
import sailpoint.unstructured.TargetCollectorFactory;
import sailpoint.unstructured.TargetCollectorProxy;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

import static sailpoint.object.TargetSource.*;

/**
 * A bean that contains all the details necessary when
 * configuring a TargetSource. This bean is used
 * by the ApplicationObjectBean and never used directly.
 * The main idea being to centralize all of the TargetSource
 * configuration stuff here in this bean to avoid complicating
 * the ApplicationObjectBean.
 *
 * This bean basically servers to different pages, one that
 * configures the AD file shares and the other which configures
 * the SharePoint target colleciton configuration.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class TargetSourceBean extends BaseBean {

    private static Log log = LogFactory.getLog(TargetSourceBean.class);
    private Map<String, String> _collectorTypes;

    private Map<String, String> _typeToCollectorMap;
    private Map<String, String> _typeToConfigPageMapMap;

    private List<AttributeDefinition> _attrConfig;

    /**
     * Target that is being added to this data source
     */
    private String _addedTarget;
    
    private Map<String, Boolean> _selectedTargets = new HashMap<String, Boolean>();

    Map<String, Map<String, Boolean>> _selected = new HashMap<String, Map<String, Boolean>>();
    
    /**
     * Name of the session attribute where the share list is stored.
     */
    private String ATT_SHARE_LIST = "shares";


    private String ATT_SITE_LIST = "siteCollections";
    private String ATT_PE2_SITE_LIST = "PE2Collections";

    /**
     * Cached list of transformation rules.
     */
    private List<SelectItem> _creationRules;

    /**
     * Currently selected transformation rule.
     */
    private String _selectedCreationRule;

    /**
     * Cached list of refresh rules
     */
    private List<SelectItem> _refreshRules;

    /**
     * Currently selected refresh rule.
     */
    private String _selectedRefreshRule;

    /**
     * Cached list of correlation rules.
     */
    private List<SelectItem> _correlationRules;

    /**
     * Currently selected Correlation rule
     */
    private String _selectedCorrelationRule;

    /**
     *  The current TargetSource we are editing/creating.
     */
    private TargetSourceDTO object = new TargetSourceDTO();

    /**
     * List of the selected shares, used during removal of
     * shares.
     */
    private Map<String, Boolean> _selectedShares = new HashMap<String, Boolean>();

    /**
     * The new bean that will be added when add is pushed.
     */
    private WindowsShareBean _newShare;

    /**
     * List of share beans.
     */
    List<WindowsShareBean> _shares;

    /**
     * A new site used only when editing a SharePoint configuration.
     */
    SiteConfigBean _newSite;

    /**
     * List of site beans, used only when editing a SharePoint configuration.
     */
    List<SiteConfigBean> _sites;
    
    /* List of PE2 target data used only for PE2 based connectors like RACF, TSS etc... */
    PE2ConfigBean newpe2config;   

	/*List of PE2 configurations*/
    List<PE2ConfigBean> pe2configs;

    /**
     * Bean to hold securityIQConfig and provide SIQ specific functionality
     */
    private SIQConfigBean _siqConfig;

    /**
     * Used to populate the target sources depend the Feature and application type selected.
     */
    Application _app;

    /**
     * List of Provisioning target collectors used for Provisioning.
     */
    private List<SelectItem> _targetCollectors;

    /**
     *  The flag to toggle the above mentioned flag for overriding the default provisioning.
     */
    private boolean _overrideProvisioningToggle;

    
    /**
     * 
     * IQService constants
     */
    
    private static final String ATT_USE_TLS_FOR_IQSERVICE = "useTLSForIQService";
    private final static String ATT_IQSERVICE_HOST = "IQServiceHost";
    private final static String ATT_IQSERVICE_PORT = "IQServicePort";
    private final static String ATT_IQSERVICE_USER = "IQServiceUser";
    private final static String ATT_IQSERVICE_PASSWORD = "IQServicePassword";
    
    
    
    @SuppressWarnings("unchecked")
    public TargetSourceBean() {
        super();
        //object = new TargetSourceDTO();
        _newShare = new WindowsShareBean();
        _newSite = new SiteConfigBean();
        newpe2config = new PE2ConfigBean();

        _shares = (List<WindowsShareBean>)getSessionScope().get(ATT_SHARE_LIST);
        getSessionScope().remove(ATT_SHARE_LIST);

        _sites= (List<SiteConfigBean>)getSessionScope().get(ATT_SITE_LIST);
        getSessionScope().remove(ATT_SITE_LIST);
        
        pe2configs = (List<PE2ConfigBean>) getSessionScope().get(ATT_PE2_SITE_LIST);
        getSessionScope().remove(ATT_PE2_SITE_LIST);

        _overrideProvisioningToggle = false;
    }

    @SuppressWarnings("unchecked")
    public TargetSourceBean(TargetSource source) {
        this();
        object = new TargetSourceDTO(source);
        Attributes<String,Object> config = object.getConfiguration();

        if ( (_shares == null ) && ( config != null ) )  {
            List<WindowsShare> infos =
                (List<WindowsShare>)config.get(ATT_SHARE_LIST);
            if ( ( infos != null ) && ( infos.size() > 0 ) ) {
                List<WindowsShareBean> beans = new ArrayList<WindowsShareBean>();
                for ( WindowsShare info : infos ) {
                    beans.add(new WindowsShareBean(info));
                }
                _shares = beans;
            }
        }

        if ( (_sites == null ) && ( config != null ) )  {
            List<SharePointSiteConfig> sites =
                (List<SharePointSiteConfig>)config.get(ATT_SITE_LIST);
            if ( Util.size(sites) > 0 ) {
                _sites = new ArrayList<SiteConfigBean>();
                for ( SharePointSiteConfig site : sites ) {
                    _sites.add(new SiteConfigBean(site));
                }
            }
        }

        if ( (pe2configs == null ) && ( config != null ) )  {
            List<PE2SiteConfig> PE2Configs =
                (List<PE2SiteConfig>)config.get(ATT_PE2_SITE_LIST);
            if ( Util.size(PE2Configs) > 0 ) {
            	pe2configs = new ArrayList<PE2ConfigBean>();
                for ( PE2SiteConfig PE2Config : PE2Configs ) {
                	pe2configs.add(new PE2ConfigBean(PE2Config));
                }
            }
        }

        if (Util.nullSafeEq(source.getCollector(), SIQConfigBean.SIQ_COLLECTOR)) {
            _siqConfig = new SIQConfigBean(config);
        }


    }

    /**
     * Silly method that can be used to seed a select box value
     * with MAX_VALUE. Used for the default value of the depth
     * of a share.
     */
    public String getMaxInt() {
        return Integer.toString(Integer.MAX_VALUE);
    }

    public void reset() {
        getSessionScope().remove(ATT_SHARE_LIST);
        getSessionScope().remove(ATT_SITE_LIST);
        getSessionScope().remove(ATT_PE2_SITE_LIST);
    }

    public List<WindowsShareBean> getShares() {
         return _shares;
    }

    public void setShares(List<WindowsShareBean> shares) {
        _shares = shares;
    }

    public List<SiteConfigBean> getSites() {
         return _sites;
    }

    public void setSites(List<SiteConfigBean> sites) {
        _sites = sites;
    }

    public void setNewShare(WindowsShareBean newShare) {
        _newShare = newShare;
    }

    public Map<String, Boolean> getSelectedShares() {
        return _selectedShares;
    }

    public void setApplicationObject(Application obj) {
        _app = obj;
    }

    public Application getApplicationObject() {
        return _app;
    }

    public TargetSourceDTO getObject() {               
        try {
            Attributes<String,Object> config = object.getConfiguration();
            if ( config == null ) {
                config = new Attributes<String,Object>();
                object.setConfiguration(config);
            }
            if ( Util.size(_shares) > 0 ) {
                List<WindowsShare> infos =
                     new ArrayList<WindowsShare>();
                for (WindowsShareBean share : _shares) {
                    infos.add(share.getObject());
                }
                config.put(ATT_SHARE_LIST, infos);
            } else {
                config.remove(ATT_SHARE_LIST);
            }

            if ( Util.size(_sites) > 0 ) {
                List<SharePointSiteConfig> configs =
                     new ArrayList<SharePointSiteConfig>();
                for (SiteConfigBean site : _sites) {
                    configs.add(site.getObject());
                }
                config.put(ATT_SITE_LIST, configs);
            } else {
                config.remove(ATT_SITE_LIST);
                _sites = null;
            }
            if ( Util.size(pe2configs) > 0 ) {
                List<PE2SiteConfig> configs =
                     new ArrayList<PE2SiteConfig>();
                for (PE2ConfigBean PE2Config : pe2configs) {
                    configs.add(PE2Config.getObject());
                }
                config.put(ATT_PE2_SITE_LIST, configs);
            } else {
                config.remove(ATT_PE2_SITE_LIST);
                pe2configs = null;
            }

            if (_siqConfig != null) {
                config.putAll(_siqConfig.getConfigMap());
            }


            String correlation = _selectedCorrelationRule;
            if ( correlation != null ) {
                //Rule rule = getContext().getObject(Rule.class, correlation);
                object.setCorrelationRule(getSelectedCorrelationRule());
            }

            String creation = _selectedCreationRule;
            if ( creation != null ) {
                //Rule rule = getContext().getObject(Rule.class, transformation);
                //String strCreationRule = getSelectedCreationRule();
                object.setCreationRule(getSelectedCreationRule());
            }

            String refresh = _selectedRefreshRule;
            if ( refresh != null ) {
                object.setRefreshRule(getSelectedRefreshRule());
            }
        } catch(Exception e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e), null);
        }
        return object;
        
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Rules
    //
    ///////////////////////////////////////////////////////////////////////////

    public List<SelectItem> getCreationRules() throws GeneralException {
        _creationRules = WebUtil.getRulesByType(getContext(),
                                                Rule.Type.TargetCreation,
                                                true, false,
                                                getObject().getCreationRule());

        return _creationRules;
    }

    public List<SelectItem> getRefreshRules() throws GeneralException {
        _refreshRules = WebUtil.getRulesByType(getContext(),
                Rule.Type.TargetRefresh,
                true, false,
                getObject().getRefreshRule());

        return _refreshRules;
    }

    public List<SelectItem> getCorrelationRules() throws GeneralException {
        _correlationRules = WebUtil.getRulesByType(getContext(),
                                                   Rule.Type.TargetCorrelation,
                                                   true, false,
                                                   getObject().getCorrelationRule());
        return _correlationRules;
    }

    public String getSelectedCorrelationRule() {
        if (_selectedCorrelationRule == null) {
            //Rule correlationRule = getObject().getCorrelationRule();
            if (getObject().getCorrelationRule() != null)
                _selectedCorrelationRule = getObject().getCorrelationRule();//correlationRule.getName();
        }
        return _selectedCorrelationRule;
    }

    public void setSelectedCorrelationRule(String correlationRule) {
        _selectedCorrelationRule = correlationRule;
    }

    public String getSelectedCreationRule() {
        if (_selectedCreationRule == null) {
            TargetSourceDTO object = getObject();
            if ( object != null ) {
                //Rule creationRule = getObject().getCreationRule();
                if (getObject().getCreationRule() != null) {
                    _selectedCreationRule = getObject().getCreationRule();//creationRule.getName();
                }
            }
        }

        return _selectedCreationRule;
    }

    public void setSelectedCreationRule(String creationRule) {
        _selectedCreationRule = creationRule;
    }

    public String getSelectedRefreshRule() {
        if (_selectedRefreshRule == null) {
            TargetSourceDTO object = getObject();
            if ( object != null ) {
                if (getObject().getRefreshRule() != null) {
                    _selectedRefreshRule = getObject().getRefreshRule();
                }
            }
        }

        return _selectedRefreshRule;
    }

    public void setSelectedRefreshRule(String refreshRule) {
        _selectedRefreshRule = refreshRule;
    }

    private boolean configHasValues(Map<String,Object> config) {
        Iterator<String> keys = config.keySet().iterator();
        if ( keys != null ) {
            while ( keys.hasNext() ) {
                String key = keys.next();
                Object o = config.get(key);
                if ( o != null ) {
                    String s = Util.otos(o);
                    if ( Util.getString(s) != null ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Map<String, String> getCollectorTypes() {
        if (_collectorTypes == null) {
            _collectorTypes = new HashMap<String, String>();
            _typeToCollectorMap = new HashMap<String, String>();
            _typeToConfigPageMapMap = new HashMap<String, String>();;
            Configuration collectorRegistry = null;
            try {
                collectorRegistry = getContext().getObjectByName(Configuration.class,
                        "TargetCollectorRegistry");
            } catch (GeneralException ex) {
                Message errMsg = new Message(Message.Type.Error,
                        MessageKeys.ERR_FINDING_COL_REGISTRY);
                log.error(errMsg.getMessage(), ex);
                addMessage(errMsg, null);
            }

            if (collectorRegistry != null) {
                Attributes attrs = collectorRegistry.getAttributes();
                if (attrs != null) {
                    for (Object key : attrs.keySet()) {
                        String keyVal = key.toString();
                        HashMap<String, Object> colConfigMap =(HashMap<String, Object>) attrs.get(key);
                        for (Object keyType : colConfigMap.keySet()) {
                            if(keyType.toString().equalsIgnoreCase("class")) {
                                _typeToCollectorMap.put(keyVal, (String) colConfigMap.get(keyType));                            
                            }
                            else if(keyType.toString().equalsIgnoreCase("page")){
                                _typeToConfigPageMapMap.put(keyVal, (String) colConfigMap.get(keyType));
                            }
                        }                            
                    }
                }
            }
            _collectorTypes = getCollectorTypesbyAppType(_typeToCollectorMap);
        } // if _collectorTypes == null

        return _collectorTypes;
    } // getCollectorTypes()

    /**
     * Get the List of Target Collectors by Application Type.
     */
    Map<String, String> getCollectorTypesbyAppType(Map<String,String> attrs) {
        Map<String, String> collectorTypes = new HashMap<String, String>();
        if(_app != null) {
            String appType = _app.getType();
            //Set the App type to Config, will be used by SharePointRWCollector to get the domain groups if
            //app type is Active Directory
            TargetSourceDTO tgtDS = getObject();
            tgtDS.setAppType(appType);

            boolean bUnstructuredTargetsFeature = _app.supportsFeature(Application.Feature.UNSTRUCTURED_TARGETS);
            if (bUnstructuredTargetsFeature) {
                if (log.isInfoEnabled()) {
                    log.info("Reading object of Target Collectors by Application Type from Configuration.");
                }
                Configuration appTypeCollectorRegistry = null;
                try {
                    // Read object of Target Collectors by Application Type from Configuration.
                    appTypeCollectorRegistry = getContext().getObjectByName(Configuration.class, "ApplicationTargetCollectorRegistry");
                } catch (GeneralException ex) {
                    Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_FINDING_COL_REGISTRY);
                    log.error(errMsg.getMessage(), ex);
                    addMessage(errMsg, null);
                }
                if (log.isInfoEnabled()) {
                    log.info("Object of Target Collectors by Application Type successfully read.");
                }

                if (appTypeCollectorRegistry != null) {
                    Attributes attributess = appTypeCollectorRegistry.getAttributes();
                    if (attributess != null) {
                        for (Object key : attributess.keySet()) {
                            List<String> appTypeCollectorList = null;
                            String keyVal = key.toString();
                            if (keyVal.equals(appType)) {
                                Object collectorObject = attributess.get(key);
                                if (collectorObject instanceof List) {
                                    appTypeCollectorList = (List<String>) collectorObject;
                                    if (!Util.isEmpty(appTypeCollectorList)) {
                                        for (String targetCollector: appTypeCollectorList) {
                                            collectorTypes.put(targetCollector,targetCollector);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
         }

        return collectorTypes;
    }

    /**
     * Get the List of Provisioning Target Collectors used for provisioning.
     */
    public List<SelectItem> getTargetCollectors() throws GeneralException {
        _targetCollectors = new ArrayList<SelectItem>();

        boolean supportsProv = !getObject().isProvisioningDisabled();
        //Only allow ManualWorkItem if the Collector Type does not support provisioning
        if (supportsProv) {
            // Get the List of Provisioning Target Collectors by Application Type.
            Map<String,String> provisioningTargetCollectorMap = getProvisioningTargetCollectorsbyAppType();

            if (!Util.isEmpty(provisioningTargetCollectorMap)) {
                // Get list of all Configured Target Collectors
                List<TargetSource> provisioningTargetCollectors = getContext().getObjects(TargetSource.class);

                // Add the entry to Provisioning Action drop down list to create Manual work item for
                // Non Provisioning Target Collectors.
                if (getOverrideProvisioningToggle()) {
                    _targetCollectors.add(new SelectItem(ARG_MANUAL_WORK_ITEM, ARG_MANUAL_WORK_ITEM));
                }

                if (!Util.isEmpty(provisioningTargetCollectors)) {
                    // Get Provisioning Target Collectors that can be used for provisioning for this
                    // target configuration.
                    for (TargetSource provisioningTargetCollector : provisioningTargetCollectors) {
                        String provisiongTargetCollectorName = provisioningTargetCollector.getName();
                        String collector = provisioningTargetCollector.getCollector();
                        if (Util.isNotNullOrEmpty(provisioningTargetCollectorMap.get(collector))) {
                            _targetCollectors.add(new SelectItem(provisiongTargetCollectorName,provisiongTargetCollectorName));
                        }
                    }
                }
            }
        }

        // Add the entry to Provisioning Action drop down list to create Manual work item for
        // Provisioning Target Collectors.
        if (!getOverrideProvisioningToggle() || !supportsProv) {
            _targetCollectors.add(new SelectItem(ARG_MANUAL_WORK_ITEM, ARG_MANUAL_WORK_ITEM));
        }


        return _targetCollectors;
    }

    /**
     * Get the List of Provisioning Target Collectors by Application Type.
     */
    public Map<String,String> getProvisioningTargetCollectorsbyAppType(){
        List<String> provisioningCollectorList = null;
        Map<String,String> provisioningTargetCollectorMap = new HashMap<String, String>();

        if(_app != null) {
            String appType = _app.getType();

            if(log.isInfoEnabled()){
                log.info("Reading object of Configurable Provisioning Target Collectors from Configuration.");
            }
            Configuration collectorRegistry = null;
            try {
                // Read object of Configurable Provisioning Target Collectors from Configuration
                collectorRegistry = getContext().getObjectByName(Configuration.class, "ProvisioningTargetCollectorRegistry");
            } catch (GeneralException ex) {
                Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_FINDING_COL_REGISTRY);
                log.error(errMsg.getMessage(), ex);
                addMessage(errMsg, null);
            }

            if(log.isInfoEnabled()){
                log.info("Configurable Provisioning Target Collectors Object successfully read.");
            }

            if (collectorRegistry != null) {
                Attributes attributess = collectorRegistry.getAttributes();
                if (attributess != null) {
                    for (Object key : attributess.keySet()) {
                        String keyVal = key.toString();
                        if(keyVal.equals(appType)){
                            Object collectorObject = attributess.get(key);
                            if(collectorObject instanceof List)
                                provisioningCollectorList = (List<String>) collectorObject;
                        }
                    }
                }
            }
        }

        if(!Util.isEmpty(provisioningCollectorList)){
            for(String targetCollector: provisioningCollectorList){
                provisioningTargetCollectorMap.put(targetCollector,targetCollector);
            }
        }
        return provisioningTargetCollectorMap;
    }

     /**
      *  Get the flag to toggle the override default Provisioning flag.
      */
     public boolean getOverrideProvisioningToggle() {
         TargetSourceDTO object = getObject();
         // Here we are ensuring that in case of edit target configuration we do not want to
         // show the override provisioning check box for non provisioning
         // target collectors.
         if (Util.isNotNullOrEmpty(object.getId())) {
             String collector = object.getCollector();
             if (Util.isNotNullOrEmpty(collector)) {
                 Map<String,String> provisioningTargetCollectors = getProvisioningTargetCollectorsbyAppType();
                 if (!Util.isEmpty(provisioningTargetCollectors)) {
                     if (Util.isNullOrEmpty(provisioningTargetCollectors.get(collector))) {
                         _overrideProvisioningToggle = true;
                     }
                 }
             }
         }

         return _overrideProvisioningToggle;
     }

     /**
      * A handler function to list the target collectors that are relevant to the 
      * context of the application and the target collector, for provisioning.
      */
     public void handleOverrideProvisioningAction(TargetSourceDTO object) throws GeneralException {
         // Clear previous selections in Provisioning Action drop Down List
         _overrideProvisioningToggle = false;
         Attributes<String,Object> config = object.getConfiguration();
         config.remove(ATT_TARGET_COLLECTOR);
         config.remove(ATT_PROVISIONING_OVERRIDDEN);

         // In case of non provisioning target collectors, set Override Default Provisioning checkbox
         // to true by deafult and do not show it on UI.
         String collector = object.getCollector();
         if (Util.isNotNullOrEmpty(collector)) {
             if (object.isProvisioningDisabled()) {
                 //provisioning disabled on TargetSource
                 _overrideProvisioningToggle = true;
                 object.setProvisioningOverridden(true);
             } else {
                 Map<String,String> provisioningTargetCollectors = getProvisioningTargetCollectorsbyAppType();
                 if (!Util.isEmpty(provisioningTargetCollectors)) {
                     if (Util.isNullOrEmpty(provisioningTargetCollectors.get(collector))) {
                         _overrideProvisioningToggle = true;
                         object.setProvisioningOverridden(true);
                     }
                 }
             }
         }
     }

    /**
     * wrapper around Application.getType() that will default in the
     * value based on Application.getConnector() if a type is not set.
     *
     * @return the application type
     */
    public String getCollector() {
        String collector = null;

        TargetSourceDTO targetDS = getObject();

        if (targetDS != null) {
            collector = targetDS.getCollector();
        }

        return collector;
    }

    /**
     *
     * @return
     */
    public Map<String, Map<String, Boolean>> getSelected() {
        return _selected;
    }

    /**
     *
     * @param selected
     */
    public void setSelected(Map<String, Map<String, Boolean>> selected) {
        _selected = selected;
    }

    /**
     *
     * @return
     */
    public List<AttributeDefinition> getAttributeConfig()
            throws GeneralException {
        if (_attrConfig == null) {
            _attrConfig = new ArrayList<AttributeDefinition>();

//            TargetSourceDTO activityDS = getObject();
//            if (activityDS != null) {
//                String collectorClass = activityDS.getCollector();
//                if ( collectorClass != null && collectorClass.length() > 0 ) {
//                    try {
//                        _attrConfig = ActivityCollectorFactory
//                                .getDefaultConfigAttributes(collectorClass);
//                    } catch (GeneralException ex) {
//                        addMessage(new Message(Message.Type.Error,
//                                MessageKeys.ERR_FINDING_ATTR_CONF, collectorClass), null);
//                    }
//                }
//            }
        }

        return _attrConfig;
    } // getAttributeConfig()

    public String getConfigPage() {
        String configPage = null;
        String collectorType = null;
        try {
            TargetSourceDTO targetDS = getObject();
            if (targetDS != null) {
                // This doesn't work because there are two components that reference the same bean value.
                // Never mind that one of them isn't supposed to be rendered; the bean value is overwritten
                // anyways.  This method needs to know the config page so that it can render the proper section,
                // but a4j applies the wrong request parameter.  As a workaround
                // we go straight to the request for an accurate value instead -- Bernie
                // String collectorType = targetDS.getType();
                collectorType = (String) getRequestParam().get("editForm:collectorType");
                if (Util.isNullOrEmpty(collectorType)) {
                    // If the type was posted under the id, 'fixedCollectorType', it is OK to use the bean value
                    // because it is current.  It's confusing to use 2 IDs for the same input, but facelets complains
                    // about duplicate component IDs even though only one of them can possibly be rendered at a time
                    collectorType = targetDS.getType();
                }
                if(_typeToConfigPageMapMap != null)
                    configPage = _typeToConfigPageMapMap.get(collectorType);
                else {
                    Configuration configPageRegistry = 
                        SailPointFactory.getCurrentContext().getObjectByName(Configuration.class, "TargetCollectorRegistry");
                    if (configPageRegistry != null) {
                        Attributes attrs = configPageRegistry.getAttributes();                        
                        if (attrs != null) {
                            for (Object key : attrs.keySet()) {
                                String keyVal = key.toString();
                                HashMap<String, Object> colConfigMap =(HashMap<String, Object>) attrs.get(key);
                                for (Object keyType : colConfigMap.keySet()) {
                                    if(keyType.toString().equalsIgnoreCase("page")){
                                        if(keyVal.equalsIgnoreCase(collectorType)) {
                                            configPage = colConfigMap.get(keyType).toString();
                                            break;
                                        }
                                    }
                                }
                                if(configPage != null)
                                    break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("The TargetCollectorRegistry is not available right now.", e);
        }

        if (collectorType != null && configPage == null) {
            log.error("Could not get config page for this collector type.");
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_NO_CONFIG_PAGE, collectorType), null);            
        }

        return configPage;
    }

    /**
     *
     * @return
     */
    public String changeType() {
        TargetSourceDTO targetDS = getObject();
        targetDS.setCollector(getCollectorTypes().get(targetDS.getType()));
        _attrConfig = null;

        return "";
    } // changeType()

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public String saveAction() {
        boolean error = false;
        TargetSourceDTO targetDSDTO = getObject();

        if ( targetDSDTO != null ) {
            String name = targetDSDTO.getName();
            if ( name == null || name.trim().length() == 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.NAME_REQUIRED), null);
                error = true;
            }

            if (targetDSDTO.getId() == null) {
                try {
                    TargetSource existingTargetDS = getContext().getObjectByName(TargetSource.class, name);
                    if (existingTargetDS != null) {
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.DUPLICATE_UNSTRUCTURED_TARGET_SRC_NAME, name), null);
                        error = true;
                    }
                } catch (GeneralException e) {
                    Message errMsg = new Message(Message.Type.Error,
                        MessageKeys.ERR_SAVING_DATA_SRC_OBJ, e);
                    addMessage(errMsg, null);
                    log.error(errMsg.getMessage(), e);
                }
            }

            String type = targetDSDTO.getType();
            if ( type == null || type.trim().length() == 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.UNSTRUCTURED_TARGET_SRC_REQUIRED), null);
                error = true;
            }

                // make sure that required Target data source attributes have a value
            List<AttributeDefinition> targetDSAttrs = null;
            try {
                targetDSAttrs = getAttributeConfig();
            } catch ( GeneralException ex ) {
                // getAttributeConfig() will add error messages to the context
            }
            if ( targetDSAttrs != null )
            {
                for ( AttributeDefinition attrDef : targetDSAttrs )
                {
                    if ( attrDef != null && attrDef.isRequired() )
                    {
                        Object attrVal = targetDSDTO.getAttributeValue(attrDef.getName());
                        if ( attrVal == null || attrVal.toString().length() == 0 )
                        {
                            addMessage(new Message(Message.Type.Error,
                                    MessageKeys.ACTIVITY_DATA_SRC_ATTR_VALUE_REQUIRED, attrDef.getName()), null);
                            error = true;
                        }
                    }
                }
            }  // if appAttrs != null
            
         // validate IQServiceConfiguration here for TLS.
            Attributes<String,Object> configuration = targetDSDTO.getConfiguration();
            if(null != configuration && configuration.containsKey(ATT_USE_TLS_FOR_IQSERVICE)) {
                boolean useTLS = Util.otob(configuration.get(ATT_USE_TLS_FOR_IQSERVICE));
                if(useTLS) {
                    String iqServiceUser = Util.otos(configuration.get(ATT_IQSERVICE_USER));
                    if(null == iqServiceUser || ("").equals(iqServiceUser)) {
                        error = true;
                        addMessage(new Message(Message.Type.Error, MessageKeys.ERR_IQSERVICE_USER_REQUIRED));
                    }
                    String iqServicePassword = Util.otos(configuration.get(ATT_IQSERVICE_PASSWORD));
                    if(null == iqServicePassword || ("").equals(iqServicePassword)) {
                        error = true;
                        addMessage(new Message(Message.Type.Error, MessageKeys.ERR_IQSERVICE_PASSWORD_REQUIRED));
                    }
                }
            }
            
            
            
            // Update the edit info on the app bean
            if (!error) {
                ApplicationObjectBean appBean =
                    (ApplicationObjectBean) getFacesContext().getApplication().createValueBinding("#{applicationObject}").getValue(getFacesContext());

                cleanUpConfig(targetDSDTO);
                Map<String, TargetSourceDTO> editedDSMap =
                    (Map<String, TargetSourceDTO>) appBean.getEditState(ApplicationObjectBean.EDITED_TARGET_DS_MAP);

                if (editedDSMap == null) {
                    editedDSMap = new HashMap<String, TargetSourceDTO>();
                    appBean.addEditState(ApplicationObjectBean.EDITED_TARGET_DS_MAP, editedDSMap);
                }
                
                // check for an existing target data source and set the already generated id 
                // otherwise the same data source can be added to the map multiple times which 
                // causes problems
                for (String id : editedDSMap.keySet()) {
                    TargetSourceDTO dataSource = editedDSMap.get(id);
                    if (dataSource.getName().equals(targetDSDTO.getName())) {
                        targetDSDTO.setId(id);
                        break;
                    }
                }
                
                if (null == targetDSDTO.getId()) {
                    String tempID = "temp" + new java.rmi.dgc.VMID().toString();
                    targetDSDTO.setId(tempID);
                }
                
                editedDSMap.put(targetDSDTO.getId(), targetDSDTO);

                // Forward on the request params in the session so that we don't lose our state
                String id = (String) getRequestParam().get("editForm:id");
                getSessionScope().put("editForm:id", id);

                // Remove ourselves from the session because our job is done for the time being
                appBean.removeEditState(ApplicationObjectBean.EDITED_TARGET_DS);
                //Clean up session state. Not sure why all this is on the session?!? -rap
                reset();

                return "saveTargetSource";
            }

        }  // if _object != null

        // If we get to this point, an error occurred
        return "";
    }

    @SuppressWarnings("unchecked")
    public String cancelAction() {
        // Forward on the request params in the session so that we don't lose our state
        String id = (String) getRequestParam().get("editForm:id");
        getSessionScope().put("editForm:id", id);

        // Cancel our changes
        ApplicationObjectBean appBean =
            (ApplicationObjectBean) getFacesContext().getApplication().createValueBinding("#{applicationObject}").getValue(getFacesContext());
        appBean.removeEditState(ApplicationObjectBean.EDITED_TARGET_DS);

        // reset session params
        this.reset();

        return "cancelTargetSource";
    }
    
    public String selectType() throws GeneralException {
        TargetSourceDTO object = getObject();

        if (_typeToCollectorMap == null) {
            getCollectorTypes();
        }

        if (_typeToCollectorMap != null) {
            // Get currently selected Target Source Type
            String collector = _typeToCollectorMap.get(object.getType());
            object.setCollector(collector);
        }

        // Update the selected values so that we don't lose the selections between requests
        object.setCorrelationRule(getSelectedCorrelationRule());
        object.setCreationRule(getSelectedCreationRule());
        object.setRefreshRule(getSelectedRefreshRule());

        // Clear the previous selections and list appropriate target collectors in 
        // Provisioning Action drop down list
        handleOverrideProvisioningAction(object);

        return "selectDataSourceType";
    }

    public void setAddedTarget(final String target) {
        _addedTarget = target;
    }

    public String getAddedTarget() {
        if (_addedTarget == null) {
            _addedTarget = "";
        }

        return _addedTarget;
    }

    public List<String> getTargets() {
        final List<String> retval = new ArrayList<String>();

        TargetSourceDTO targetDSDTO = getObject();
        List<String> targets = targetDSDTO.getTargets();
        if (targets != null && !targets.isEmpty())
            retval.addAll(targets);

        return retval;
    }

    public String addTargetAction() {
        TargetSourceDTO targetDSDTO = getObject();
        targetDSDTO.addTarget(_addedTarget);

        clearAddedTarget();

        return "";
    }

    public String deleteTargetsAction() {
        TargetSourceDTO targetDSDTO = getObject();

        Map <String, Boolean> targetsToDelete = getSelectedTargets();

        for (String target : targetsToDelete.keySet()) {
            if (targetsToDelete.get(target)) {
                targetDSDTO.removeTarget(target);
                targetsToDelete.put(target, false);
            }
        }

        return "";
    }

    private void clearAddedTarget() {
        _addedTarget = "";
    }

    public Map<String, Boolean> getSelectedTargets() {
        return _selectedTargets;
    }

    public void setSelectedTargets(Map<String, Boolean> targets) {
        _selectedTargets = targets;
    }

    // Helper methods
    /**
     * We need to override getContext() in this class because it is not a true
     * stand-alone bean.  It is kept in the session and reused by the ApplicationObjectBean,
     * so it does not maintain an up-to-date copy of the context.  The only reason that
     * it extends BaseBean is so that it can take advantage of the buildRuleList() methods.
     * I was tempted to refactor those in a utility class, but I didn't think that prudent
     * given that 3.0 is only 2 weeks away --Bernie
     *
     * The buildRuleList() methods in BaseBean were refactored to use the static
     * WebUtil.getRulesByType() methods instead.  Perhaps refactoring this bean
     * is now an option? - DHC 07/23/10
     */
    @Override
    public SailPointContext getContext() {
        SailPointContext context;
        try {
            context = SailPointFactory.getCurrentContext();
        } catch (GeneralException e) {
            context = super.getContext();
        }

        return context;
    }

    /**
     * Overriding this for the same reason that we overrode getContext()
     */
    @Override
    public Map getSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
    }

    /**
     * Overriding this for the same reason that we overrode getContext()
     */
    @Override
    public FacesContext getFacesContext() {
        return FacesContext.getCurrentInstance();
    }

    /**
     * Overriding this for the same reason that we overrode getContext()
     */
    @Override
    public Map getRequestParam() {
        return FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
    }

    /**
     * djs: We really shouldn't be delegating to the target datasource
     * about which of the elements in the configuration map should be
     * removed/persisted.
     *
     * Since we have a form per type here, it would be cleaner if
     * the form would null out values not entered
     * then have this method cleanup the config and remove nulls
     * AND "" values.
     *
     * What we loose with this approach is the ability for
     * field engineers to inject custom configuration attributes without also
     * having to modify the Collector's implementation. The Collectors
     * all have some number of un-published attributes to tweek behavior.
     *
     * At some point we need to clean up the forms and removing
     * the cleanupConfig method from the ActivtyCollector interface.
     *
     */
    private void cleanUpConfig(TargetSourceDTO targetDS) {
        Attributes<String, Object> config = targetDS.getConfiguration();
        if (config != null) {
            try {
                // ALWAYS preserve these values
//                Object fieldMap = config.get("fieldMap");
//                Object filters = config.get(ActivityCollector.CONFIG_FILTERS);
//                Object allowAll = config.get(ActivityCollector.CONFIG_ALLOW_ALL);
//                Object userAttr = config.get(ActivityCollector.CONFIG_USER_ATTRIBUTE);
//                ActivityCollector collector =
//                    ActivityCollectorFactory.getCollector(activityDS.buildPartialActivityDataSource());
//                collector.cleanUpConfig(config);
//                // ALWAYS preserve these values
//                if ( fieldMap != null )
//                    config.put("fieldMap",fieldMap);
//                if ( filters != null )    
//                    config.put(ActivityCollector.CONFIG_FILTERS,filters);
//                if ( allowAll != null )
//                    config.put(ActivityCollector.CONFIG_ALLOW_ALL,allowAll);
//                if ( userAttr != null )
//                    config.put(ActivityCollector.CONFIG_USER_ATTRIBUTE,userAttr);
            } catch (Exception e) {
                log.error("Could not find a collector of for this target data source " + targetDS.getName(), e);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // File  Shares
    //
    ///////////////////////////////////////////////////////////////////////////

    public WindowsShareBean getNewShare() {
        return _newShare;
    }

    public void setSelectedShares(Map<String, Boolean> selectedShares) {
        _selectedShares = selectedShares;
    }

    @SuppressWarnings("unchecked")
    public String addShare() {
        String result = "";

        if ( ( _newShare.getObject().getPath() == null ) ||
             ( _newShare.getObject().getPath().trim().length() == 0) ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SHARE_PATHS_BLANK), null);
            result = "";
        } else {
            if ( _shares == null ) {
                _shares = new ArrayList<WindowsShareBean>();
            }
            // If a share with the same path already exists, don't add another
            boolean existsAlready = false;
            for (WindowsShareBean existingWindowsFileShareInfo : _shares) {
                if (existingWindowsFileShareInfo.getObject().getPath().equals(_newShare.getObject().getPath()))
                    existsAlready = true;
            }

            if (!existsAlready) {
                _shares.add(_newShare);
                _newShare = new WindowsShareBean();
                result = "addedWindowsFileShareInfo";
            } else {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DUPLICATE_SHARE_PATH,
                        _newShare.getObject().getPath()), null);
                result = "";
            }

            getSessionScope().put(ATT_SHARE_LIST, _shares);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public String removeShares() {

        if ( _shares == null ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_SHARES_DEFINED), null);
            return "";
        }

        Set<String> sharePaths = null;
        if ( _selectedShares != null ) {
            sharePaths = _selectedShares.keySet();
        }
        if ( ( sharePaths == null ) || ( sharePaths.size() == 0 ) ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_SHARES_SELECTED), null);
            return "";
        }

        Set<String> sharesToRemove = new HashSet<String>();
        for (String sharePath : sharePaths) {
            if (_selectedShares.get(sharePath)) {
                sharesToRemove.add(sharePath);
            }
        }
        Iterator<WindowsShareBean> i = _shares.iterator();
        while (i.hasNext()) {
            WindowsShareBean currentBean = i.next();
            if (sharesToRemove.contains(currentBean.getId())) {
                i.remove();
            }
        }
        getSessionScope().put(ATT_SHARE_LIST, _shares);
        return "removedWindowsFileShareInfos";
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // SharePoint Sites - Used in configuration of SharePoint target collection
    //
    ///////////////////////////////////////////////////////////////////////////

    public SiteConfigBean getNewSite() {
        return _newSite;
    }

    public void setNewSite(SiteConfigBean newShare) {
        _newSite = newShare;
    }

    @SuppressWarnings("unchecked")
    public String addSite() {
        String result = "";

        String currentUrl = Util.getString(_newSite.getObject().getSiteCollectionUrl());
        if ( currentUrl == null ) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SITES_BLANK), null);
            result = "";
        } else {
            if ( _sites == null ) {
                _sites = new ArrayList<SiteConfigBean>();
            }
            // If a share with the same path already exists, don't add another
            boolean existsAlready = false;
            for (SiteConfigBean existingWindowsFileShareInfo : _sites) {
                if (existingWindowsFileShareInfo.getObject().getSiteCollectionUrl().equals(currentUrl))
                    existsAlready = true;
            }

            if (!existsAlready) {
                _sites.add(_newSite);
                _newSite = new SiteConfigBean();
                result = "addedWindowsFileShareInfo";
            } else {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DUPLICATE_SITE,
                        _newSite.getObject().getSiteCollectionUrl()), null);
                result = "";
            }
            getSessionScope().put(ATT_SITE_LIST, _sites);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public String removeSites() {

        if ( _sites == null ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_SITES_DEFINED), null);
            return "";
        }

        Set<String> sites = null;
        if ( _selectedShares != null ) {
            sites = _selectedShares.keySet();
        }
        if ( Util.size(sites) == 0 ) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_NO_SITES_SELECTED), null);
            return "";
        }

        Set<String> sitesToRemove = new HashSet<String>();
        for (String siteId : sites ) {
            if (_selectedShares.get(siteId)) {
                sitesToRemove.add(siteId);
            }
        }
        Iterator<SiteConfigBean> i = _sites.iterator();
        while (i.hasNext()) {
            SiteConfigBean currentBean = i.next();
            if (sitesToRemove.contains(currentBean.getId())) {
                i.remove();
            }
        }
        if ( _sites.isEmpty() ) {
            // value other then null to indicate we
            // shouldn't repopulate the site list
            _sites = new ArrayList<SiteConfigBean>();
        }
        getSessionScope().put(ATT_SITE_LIST, _sites);
        return "removedWindowsFileShareInfos";
    }

    //////////////////////////////////////////////////////////////////
    //
    // PE2 Sites - Used in configuration of PE2 target collection
    //
    ///////////////////////////////////////////////////////////////////////////

    
	public List<PE2ConfigBean> getPe2configs() {
		return pe2configs;
	}

	public void setPe2configs(List<PE2ConfigBean> pe2configs) {
		this.pe2configs = pe2configs;
	}
	
	public PE2ConfigBean getNewpe2config() {
		return newpe2config;
	}

	public void setNewpe2config(PE2ConfigBean newpe2config) {
		this.newpe2config = newpe2config;
	}
	 @SuppressWarnings("unchecked")
	    public String addPE2Config() {
	        String result = "";

	        String generic = Util.getString(newpe2config.getObject().getGeneric());
	        String targetName = Util.getString(newpe2config.getObject().getTargetName());
	        String targetType = Util.getString(newpe2config.getObject().getTargetType());
	        if ( targetName == null || targetType == null ) {
	            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_PE2CONFIG_BLANK), null);
	            result = "";
	        } else {
	            if ( pe2configs == null ) {
	            	pe2configs = new ArrayList<PE2ConfigBean>();
	            }
	            // If a share with the same path already exists, don't add another
	            boolean existsAlready = false;
	            for (PE2ConfigBean existingTargetName : pe2configs) {
	            	if (Util.isNotNullOrEmpty(generic)) {
	            		if (existingTargetName.getObject().getTargetName().equals(targetName) && existingTargetName.getObject().getTargetType().equals(targetType) && 
	            				existingTargetName.getObject().getGeneric().equals(generic))
	                    existsAlready = true;
	            	} else if (existingTargetName.getObject().getTargetName().equals(targetName) && existingTargetName.getObject().getTargetType().equals(targetType)) {
	                    existsAlready = true;
	            	}
	            }

	            if (!existsAlready) {
	            	pe2configs.add(newpe2config);
	            	newpe2config = new PE2ConfigBean();
	                result = "addedTargetInfo";
	            } else {
	                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DUPLICATE_PE2CONFIG,
	                		newpe2config.getObject().getTargetName(), newpe2config.getObject().getTargetType()), null);
	                result = "";
	            }
	            getSessionScope().put(ATT_PE2_SITE_LIST, pe2configs);
	        }
	        return result;
	    }

	    @SuppressWarnings("unchecked")
	    public String removePE2Config() {

	        if ( pe2configs == null ) {
	            addMessage(new Message(Message.Type.Error,
	                    MessageKeys.ERR_NO_PE2CONFIG_DEFINED), null);
	            return "";
	        }

	        Set<String> PE2Configs = null;
	        if ( _selectedShares != null ) {
	        	PE2Configs = _selectedShares.keySet();
	        }
	        if ( Util.size(PE2Configs) == 0 ) {
	            addMessage(new Message(Message.Type.Error,
	                    MessageKeys.ERR_NO_SITES_SELECTED), null);
	            return "";
	        }

	        Set<String> PE2ConfigssToRemove = new HashSet<String>();
	        for (String siteId : PE2Configs ) {
	            if (_selectedShares.get(siteId)) {
	            	PE2ConfigssToRemove.add(siteId);
	            }
	        }
	        Iterator<PE2ConfigBean> i = pe2configs.iterator();
	        while (i.hasNext()) {
	            PE2ConfigBean currentBean = i.next();
	            if (PE2ConfigssToRemove.contains(currentBean.getId())) {
	                i.remove();
	            }
	        }
	        if ( pe2configs.isEmpty() ) {
	            // value other then null to indicate we
	            // shouldn't repopulate the site list
	        	pe2configs = new ArrayList<PE2ConfigBean>();
	        }
	        getSessionScope().put(ATT_PE2_SITE_LIST, pe2configs);
	        return "removeTargetInfo";
	    }
	// End of PE2 configurations


    //////////////////////////////////////////////////////////////////
    //
    // SecurityIQ TargetCollector Configuration
    //
    ///////////////////////////////////////////////////////////////////////////

    public SIQConfigBean getSiqConfig() {
        if (_siqConfig == null) {
            _siqConfig = new SIQConfigBean();
        }
        return _siqConfig;
    }

    public void setSiqConfig(SIQConfigBean bean) {
        _siqConfig = bean;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // DTOs
    //
    ///////////////////////////////////////////////////////////////////////////

    public class WindowsShareBean extends BaseBean {

        /**
         *  The current TargetSource we are editing/creating.
         */
        private WindowsShare object;

        private String _id;

        public WindowsShareBean() {
            super();
            _id = Util.uuid();
            object = new WindowsShare();
            object.setIncludeExplicitPermissions(true);
        }

        public WindowsShareBean(WindowsShare source) {
            this();
            object = source;
        }

        public WindowsShare getObject() {
            return object;
        }

        public String getDepthStr() {
            String depthStr = Integer.toString(object.getDepth());
            return depthStr;
        }
        public void setDepthStr(String depthStr) {
            int depth = Util.atoi(depthStr);
            object.setDepth(depth);
        }

        public String getPath() {
            String path = object.getPath();
            if ( path != null ) {
                path = path.replace("\\\\","\\");
            }
            return path;
        }

        public void setPath(String path) {
            if ( path != null ) {
                path = path.replace("\\","\\\\");
                object.setPath(path);
            }
        }

        public String getId() {
            return _id;
        }
    }

    /**
     * DTO basically that just helps us strip out
     * empty strings and holds a unique id for our
     * row handling.
     */
    public class SiteConfigBean extends BaseBean {

        /**
         *  The current site config we are editing/creating.
         */
        private SharePointSiteConfig object;

        private String _id;

        public SiteConfigBean() {
            super();
            _id = Util.uuid();
            object = new SharePointSiteConfig();
        }

        public SiteConfigBean(SharePointSiteConfig source) {
            this();
            object = source;
        }

        public SharePointSiteConfig getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }

        public void setSiteInclusionFilter(String incomming) {
            String filter = Util.getString(incomming);
            object.setSiteInclusionFilter(filter);
        }

        public String getSiteInclusionFilter() {
            return (object != null) ? object.getSiteInclusionFilter() : null;
        }

        public void setListInclusionFilter(String incomming) {
            String filter = Util.getString(incomming);
            object.setListInclusionFilter(filter);
        }

        public String getListInclusionFilter() {
            return (object != null) ? object.getListInclusionFilter() : null;
        }

    }
    
    public class PE2ConfigBean extends BaseBean {

        /**
         *  The current site config we are editing/creating.
         */
        private PE2SiteConfig object;

        private String _id;

        public PE2ConfigBean() {
            super();
            _id = Util.uuid();
            object = new PE2SiteConfig();
        }

        public PE2ConfigBean(PE2SiteConfig source) {
            this();
            object = source;
        }

        public PE2SiteConfig getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }        
    }

    public class SIQConfigBean extends BaseBean {

        private static final String SIQ_COLLECTOR = "sailpoint.unstructured.SecurityIQTargetCollector";


    private String _url = "jdbc:sqlserver://localhost:1433;databaseName=SecurityIQDB";
        private String _userName = "SecurityIQ_User";
        private String _password;
        //Default driverClass
        private String _driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        //List of hostNames
        List<String> _targetHosts;
        //List of TargetHostConfig
        private List<TargetHostConfigBean> _targetHostConfigs;
        private String _schemaName = "whiteops";
        private boolean _aggregateInherited;
        //Do we want this as an option? -rap
        private boolean _aggregateDeny;



        //Variable to hold status of connection test
        private String _testResult;
        private boolean _testError;

        private List<TargetHostConfigBean> _availableHosts;


        public SIQConfigBean() { }

        public SIQConfigBean(Map<String, Object> config) {
            if (config != null) {
                _url = Util.getString(config, SecurityIQTargetCollector.CONFIG_DBURL);
                _userName = Util.getString(config, SecurityIQTargetCollector.CONFIG_DB_USER);
                _password = Util.getString(config, SecurityIQTargetCollector.CONFIG_DB_PASS);
                _driverClass = Util.getString(config, SecurityIQTargetCollector.CONFIG_DRIVER_CLASS);
                _schemaName = Util.getString(config, SecurityIQTargetCollector.CONFIG_SCHEMA_NAME);
                //Initialize the _targetHostConfigs
                createTargetHostConfigBeans((List) config.get(SecurityIQTargetCollector.INCLUDED_TARGET_HOSTS));
                if (!Util.isEmpty(_targetHostConfigs)) {
                    //Initialize _availableHosts to the configured _targetHostConfigs
                    //Can call Discover, but that may be unneeded overhead
                    for (TargetHostConfigBean thc: _targetHostConfigs) {
                        if (_availableHosts == null) {
                            _availableHosts = new ArrayList<TargetHostConfigBean>();
                        }
                        _availableHosts.add(thc);
                    }
                }
                _aggregateInherited = Boolean.valueOf(Util.getString(config, SecurityIQTargetCollector.CONFIG_AGG_INHERITED));
            }
        }

        /**
         * Test the Collector Configuration
         * @return
         */
        public String testConfiguration() {

            boolean success = true;
            try {
                _testResult = "Test Successful";
                _testError = false;
                TargetCollector collector = getTargetCollector();
                //This will throw exception if misconfigured
                collector.testConfiguration();

            } catch (GeneralException ge) {
                if (log.isWarnEnabled()) {
                    log.warn("Exception testing config." + ge);
                }
                _testResult = ExceptionCleaner.cleanConnectorException(ge);
                _testError = true;
            }
            return "";
        }

        public String discoverTargetHosts() {
            //Clear the testResults so they won't show on postback
            _testResult = null;
            _testError = false;
            try {
                SecurityIQTargetCollector collector;
                TargetCollectorProxy proxy = (TargetCollectorProxy)getTargetCollector();
                collector = (SecurityIQTargetCollector)proxy.getCollector();
                List<Map<String, Object>> configs = collector.getTargetHosts();
                createAvailableHostConfigs(configs);

            } catch(GeneralException ge) {
                if (log.isErrorEnabled()) {
                    log.error("Exception getting available target hosts." + ge);
                }
                addMessage(ge);
            }

            return "";
        }

        public void createAvailableHostConfigs(List<Map<String, Object>> configs) {
            if (_availableHosts == null) {
                _availableHosts = new ArrayList<TargetHostConfigBean>();
            } else {
                _availableHosts.clear();
            }
            for (Map m : Util.safeIterable(configs)) {
                _availableHosts.add(createConfig(m));
            }
        }

        public TargetHostConfigBean createConfig(Map<String, Object> m) {
            TargetHostConfigBean b = new TargetHostConfigBean();
            b.setHostId(Util.getString(m, SecurityIQTargetCollector.TARGET_HOST_BAM_ID));
            b.setHostName(Util.getString(m, SecurityIQTargetCollector.TARGET_HOST_BAM_NAME));
            b.setCaseSensitive(Util.getBoolean(m, SecurityIQTargetCollector.TARGET_HOST_CASE_SENSITIVE));

            return b;
        }

        /**
         * Return a JSON representation of available BAM_names from the SIQCollector
         * @return
         */
        public List<SelectItem> getTargetHostSelectItems() {

            List<SelectItem> items = new ArrayList<SelectItem>();
            for(TargetHostConfigBean s : Util.safeIterable(_availableHosts)) {
                items.add(new SelectItem(s.getHostName(), s.getHostName()));
            }

            return items;
        }

        protected TargetCollector getTargetCollector() throws GeneralException {
            //Mock Up a TargetSource
            TargetSource ts = getObject().buildPartialTargetSource();
            //Get Collector from factory
            return TargetCollectorFactory.getTargetCollector(ts);
        }

        public Map<String, Object> getConfigMap() throws GeneralException {
            Map<String, Object> config = new HashMap<String, Object>();
            config.put(SecurityIQTargetCollector.CONFIG_DRIVER_CLASS, _driverClass);
            config.put(SecurityIQTargetCollector.CONFIG_DBURL, _url);
            config.put(SecurityIQTargetCollector.CONFIG_DB_USER, _userName);
            config.put(SecurityIQTargetCollector.CONFIG_DB_PASS, _password);
            config.put(SecurityIQTargetCollector.INCLUDED_TARGET_HOSTS, createHostConfigs());
            config.put(SecurityIQTargetCollector.CONFIG_SCHEMA_NAME, _schemaName);
            config.put(SecurityIQTargetCollector.CONFIG_AGG_INHERITED, _aggregateInherited);
            //Disable provisioning for SecurityIQ Collectors for now
            config.put(ATT_PROVISIONING_DISABLED, true);
            return config;
        }


        public String getUrl() {
            return _url;
        }

        public void setUrl(String _url) {
            this._url = _url;
        }

        public String getUserName() {
            return _userName;
        }

        public void setUserName(String _userName) {
            this._userName = _userName;
        }

        public String getPassword() {
            return _password;
        }

        public void setPassword(String _password) {
            this._password = _password;
        }

        public String getDriverClass() {
            return _driverClass;
        }

        public void setDriverClass(String _driverClass) {
            this._driverClass = _driverClass;
        }

        public List getTargetHostConfigs() {
            return _targetHostConfigs;
        }

        public void setTargetHostConfigs(List<TargetHostConfigBean> configs) {
            _targetHostConfigs = configs;
        }

        public void setTargetHosts(List _targetHosts) {
            this._targetHosts = _targetHosts;
        }

        public List getTargetHosts() {
            return _targetHosts;
        }

        public void addTargetHost(TargetHostConfig hostConfig) {
            if (_targetHostConfigs == null) {
                _targetHostConfigs = new ArrayList<TargetHostConfigBean>();
            }
            _targetHostConfigs.add(new TargetHostConfigBean(hostConfig));
            if (_targetHosts == null) {
                _targetHosts = new ArrayList<String>();
            }
            _targetHosts.add(hostConfig.getHostName());
        }

        public String getSchemaName() {
            return _schemaName;
        }

        public void setSchemaName(String _schemaName) {
            this._schemaName = _schemaName;
        }

        public boolean isAggregateInherited() {
            return _aggregateInherited;
        }

        public void setAggregateInherited(boolean _aggregateInherited) {
            this._aggregateInherited = _aggregateInherited;
        }

        public boolean isAggregateDeny() {
            return _aggregateDeny;
        }

        public void setAggregateDeny(boolean _aggregateDeny) {
            this._aggregateDeny = _aggregateDeny;
        }

        public String getTestResult() {
            return _testResult;
        }

        public void setTestResult(String res) {
            _testResult = res;
        }

        public boolean getTestError() { return _testError; }

        public void setTestError(boolean b) { _testError = b; }

        public List getAvailableHosts() { return _availableHosts; }

        public void setAvailableHosts(List hosts) {
            _availableHosts = hosts;
        }

        //Create a list of TargetHostConfig from the given Map
        public void createTargetHostConfigBeans(List<TargetHostConfig> targetHostConfigs) {

            if (!Util.isEmpty(targetHostConfigs)) {
                for (TargetHostConfig thc : targetHostConfigs) {
                    addTargetHost(thc);
                }
            }
        } 
        public List<TargetHostConfig> createHostConfigs() {
            List<TargetHostConfig> configs = null;

            for (TargetHostConfigBean bean : Util.safeIterable(_targetHostConfigs)) {
                if (configs == null) {
                    configs = new ArrayList<TargetHostConfig>();
                }
                configs.add(bean.clone());
            }
            return configs;
        }

        /**
         * Return the TargetHostConfigBean from the
         * @see #_availableHosts with the given hostName
         * @param hostName - hostName of the configBean
         * @return
         */
        public TargetHostConfigBean getAvailableConfigBean(String hostName) {
            for (TargetHostConfigBean b : Util.safeIterable(_availableHosts)) {
                if (Util.nullSafeEq(b.getHostName(), hostName)) {
                    return b;
                }
            }
            return null;
        }

        //Create/Delete TargetHostConfigs to reflect what has been selected in the multiselect
        public void updateTargetHostConfigs() {
            if (Util.isEmpty(_targetHosts)) {
                //Clear all configs as well
                if (_targetHostConfigs != null) {
                    _targetHostConfigs.clear();
                }
            } else {
                if (_targetHostConfigs != null) {
                    for (Iterator it = _targetHostConfigs.iterator(); it.hasNext();) {
                        TargetHostConfigBean thcb = (TargetHostConfigBean)it.next();
                        if (!_targetHosts.contains(thcb.getHostName())) {
                            it.remove();
                        }
                    }
                }
                for (String s : Util.safeIterable(_targetHosts)) {
                    if (_targetHostConfigs == null) {
                        _targetHostConfigs = new ArrayList<TargetHostConfigBean>();
                    }
                    boolean found = false;
                    for (TargetHostConfigBean thcb : Util.safeIterable(_targetHostConfigs)) {
                        if (thcb.getHostName().equals(s)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        //Create a configBean for the targetHost
                        _targetHostConfigs.add(getAvailableConfigBean(s));
                    }
                }
            }
        }

    }


    /**
     * Provides configuration for a given TargetHost
     */
    public static class TargetHostConfigBean {

        public TargetHostConfigBean() { }

        public TargetHostConfigBean(TargetHostConfig thc) {
            _hostName = thc.getHostName();
            _paths = thc.getPaths();
            _pathsCSV = Util.listToCsv(_paths);
            _hostId = thc.getHostId();
            _caseSensitive = thc.isPathCaseSensitive();
        }

        public TargetHostConfigBean(String hostName, String hostId, boolean caseSensitive) {
            _hostName = hostName;
            _hostId = hostId;
            _caseSensitive = caseSensitive;
        }

        //Name of the TargetHost
        String _hostName;
        //List of paths for the given targetHost
        List<String> _paths;

        String _pathsCSV;

        boolean _caseSensitive;

        String _hostId;

        public String getHostName() {
            return _hostName;
        }

        public void setHostName(String host) {
            _hostName = host;
        }

        public List getPaths() {
            return _paths;
        }

        public void setPaths(List paths) {
            _paths = paths;
        }

        public void addPath(String path) {
            if (_paths == null) {
                _paths = new ArrayList<String>();
            }

            _paths.add(path);
        }

        public String getPathsCSV() {
            return _pathsCSV;
        }

        public void setPathsCSV(String s) {
            _pathsCSV = s;
            _paths = Util.csvToList(_pathsCSV);
        }

        public boolean isCaseSensitive() {
            return _caseSensitive;
        }

        public void setCaseSensitive(boolean b ) {
            _caseSensitive = b;
        }

        public String getHostId() {
            return _hostId;
        }

        public void setHostId(String s) {
            _hostId = s;
        }

        public TargetHostConfig clone() {
            TargetHostConfig thc = new TargetHostConfig();
            thc.setHostName(_hostName);
            thc.setPaths(_paths);
            thc.setHostId(_hostId);
            thc.setPathCaseSensitive(_caseSensitive);
            return thc;
        }
    }




} // class TargetSourceBean
