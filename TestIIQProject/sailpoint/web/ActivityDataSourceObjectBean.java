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

import sailpoint.activity.ActivityCollector;
import sailpoint.activity.ActivityCollectorFactory;
import sailpoint.activity.JDBCActivityCollector;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.LogField;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.application.ActivityDataSourceDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 *
 */
public class ActivityDataSourceObjectBean extends BaseBean {
    private static Log log = LogFactory.getLog(ActivityDataSourceObjectBean.class);

    private Map<String, String> _collectorTypes;

    private Map<String, String> _typeToCollectorMap;

    private List<AttributeDefinition> _attrConfig;

    /**
     * Target that is being added to this data source
     */
    private String _addedTarget;

    private List<SelectItem> _transformationRules;
    private List<SelectItem> _transformationRuleNames;
    private List<SelectItem> _correlationRules;
    private List<SelectItem> _positionBuilders;
    private List<SelectItem> _conditionBuilders;

    private Map<String, Boolean> _selectedLogFields = new HashMap<String, Boolean>();
    private Map<String, Boolean> _selectedTargets = new HashMap<String, Boolean>();

    Map<String, Map<String, Boolean>> _selected = new HashMap<String, Map<String, Boolean>>();

    String _selectedTransformationRule;
    String _selectedCorrelationRule;

    private boolean _testSuccess;

    private ActivityDataSourceDTO object;

    /**
     * Result of testConfiguration being called on collector
     */
    private String _testResult;

    private LogField _newLogField;

    
    /**
     * 
     * IQService constants
     */
    
    private static final String ATT_USE_TLS_FOR_IQSERVICE = "useTLSForIQService";
    private final static String ATT_IQSERVICE_HOST = "IQServiceHost";
    private final static String ATT_IQSERVICE_PORT = "IQServicePort";
    private final static String ATT_IQSERVICE_USER = "IQServiceUser";
    private final static String ATT_IQSERVICE_PASSWORD = "IQServicePassword";
    
    public ActivityDataSourceObjectBean(ActivityDataSource ads) {
        super();
        object = new ActivityDataSourceDTO(ads);
        _newLogField = new LogField();
        _newLogField.setTrim(true);
    }


    public ActivityDataSourceObjectBean() {
        super();
        object = new ActivityDataSourceDTO();
        _newLogField = new LogField();
        _newLogField.setTrim(true);
    } // ActivityDataSourceObjectBean()

    public LogField getNewLogField() {
        return _newLogField;
    }

    public void setNewLogField(LogField newLogField) {
        _newLogField = newLogField;
    }

    /**
     * A handler function to auto populate transformation rule and correlation
     * rule on the basis of collector type selected.
     */
    public void handleCollectorTypeChange() {
        String collectorType = getCollectorType();
        if (isCollectorTypeCEFLogFile(collectorType)) {
            getSelectedTransformationRule();
            getSelectedCorrelationRule();
        }
        else {
            _selectedTransformationRule = null;
            _selectedCorrelationRule = null;
            
            //When the Activity Data Source collector type is set to CEF Log file and if the user again
            //try to change it to some other collector type, then Transformation rule & Correlation
            //rule will be set to default values by setting null to them.
            ActivityDataSourceDTO dto = getObject();
            if (dto != null){
                dto.setTransformationRule(_selectedTransformationRule);
                dto.setCorrelationRule(_selectedCorrelationRule);
            }
        }
    }

    public String getSelectedCorrelationRule() {
        if (_selectedCorrelationRule == null) {
            ActivityDataSourceDTO dto = getObject();
            if (dto != null)
                _selectedCorrelationRule = dto.getCorrelationRule();
        }

        return _selectedCorrelationRule;
    }

    public void setSelectedCorrelationRule(String correlationRule) {
        ActivityDataSourceDTO dto = getObject();
        String collectorType = getCollectorType();
        Configuration sysConfig = Configuration.getSystemConfig();

        if ( isCollectorTypeCEFLogFile(collectorType) ) {
            if (sysConfig != null) {
                String correlationRuleName = sysConfig.getString(Configuration.ATT_CEF_LOGFILE_CORRELATION_RULE);
                if (correlationRuleName != null) {
                    _selectedCorrelationRule = correlationRuleName;
                    if (dto != null) {
                        dto.setCorrelationRule(correlationRuleName);
                    }
                }
            }
        }
        else {
            _selectedCorrelationRule = correlationRule;
            if (dto != null) {
                dto.setCorrelationRule(correlationRule);
            }
        }
    }

    public String getSelectedTransformationRule() {
        if (_selectedTransformationRule == null) {
            ActivityDataSourceDTO dto = getObject();
            if (dto != null)
                _selectedTransformationRule = dto.getTransformationRule();
        }

        return _selectedTransformationRule;
    }

    public void setSelectedTransformationRule(String transformationRule) {
        ActivityDataSourceDTO dto = getObject();
        String collectorType = getCollectorType();
        Configuration sysConfig = Configuration.getSystemConfig();

        if ( isCollectorTypeCEFLogFile(collectorType) ) {
            if (sysConfig != null) {
                String transformationRuleName = sysConfig.getString(Configuration.ATT_CEF_LOGFILE_TRANSFORMATION_RULE);
                if (transformationRuleName != null) {
                    _selectedTransformationRule = transformationRuleName;
                    if (dto != null) {
                        dto.setTransformationRule(transformationRuleName);
                    }
                }
            }
        }
        else {
            _selectedTransformationRule = transformationRule;
            if (dto != null) {
                dto.setTransformationRule(transformationRule);
            }
        }
    }

    /**
     * Check Activity Data Source Type for CEF Log File Collector Type.
     * 
     */
    public boolean isCollectorTypeCEFLogFile(String collectorType){
        if (collectorType != null && collectorType.equals(Configuration.CEF_LOG_FILE_COLLECTOR_TYPE)){
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Get name of Activity Data Source Type Collector.
     * 
     */
    public String getCollectorType(){
        return (String) getRequestParam().get("editForm:collectorType");
    }

    /**
     *
     * @return
     */
    public Map<String, String> getCollectorTypes() {
        if (_collectorTypes == null) {
            _collectorTypes = new HashMap<String, String>();
            _typeToCollectorMap = new HashMap<String, String>();

            Configuration collectorRegistry = null;
            try {
                collectorRegistry = getContext().getObjectByName(Configuration.class,
                        "ActivityCollectorRegistry");
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
                        _collectorTypes.put(keyVal, keyVal);
                        _typeToCollectorMap.put(keyVal, attrs.getString(keyVal));
                    }
                }
            }
        } // if _collectorTypes == null

        return _collectorTypes;
    } // getCollectorTypes()

    /**
     * wrapper around Application.getType() that will default in the
     * value based on Application.getConnector() if a type is not set.
     *
     * @return the application type
     */
    public String getCollector() {
        String collector = null;

        ActivityDataSourceDTO activityDS = getObject();

        if (activityDS != null) {
            collector = activityDS.getCollector();
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

    public Map<String, Boolean> getSelectedLogFields() {
        return _selectedLogFields;
    }

    public void setSelectedLogFields(Map<String, Boolean> selectedLogFields) {
        _selectedLogFields = selectedLogFields;
    }

    /**
     *
     * @return
     */
    public List<AttributeDefinition> getAttributeConfig()
            throws GeneralException {
        if (_attrConfig == null) {
            _attrConfig = new ArrayList<AttributeDefinition>();

            ActivityDataSourceDTO activityDS = getObject();
            if (activityDS != null) {
                String collectorClass = activityDS.getCollector();
                if ( collectorClass != null && collectorClass.length() > 0 ) {
                    try {
                        _attrConfig = ActivityCollectorFactory
                                .getDefaultConfigAttributes(collectorClass);
                    } catch (GeneralException ex) {
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.ERR_FINDING_ATTR_CONF, collectorClass), null);
                    }
                }
            }
        }

        return _attrConfig;
    } // getAttributeConfig()

    public String getConfigPage() {
        String configPage = null;

        try {
            ActivityDataSourceDTO activityDS = getObject();
            if (activityDS != null) {
                // This doesn't work because there are two components that reference the same bean value.
                // Never mind that one of them isn't supposed to be rendered; the bean value is overwritten
                // anyways.  This method needs to know the config page so that it can render the proper section,
                // but a4j applies the wrong request parameter.  As a workaround
                // we go straight to the request for an accurate value instead -- Bernie
                // String collectorType = activityDS.getType();
                String collectorType = getCollectorType();
                if (collectorType == null) {
                    // If the type was posted under the id, 'fixedCollectorType', it is OK to use the bean value
                    // because it is current.  It's confusing to use 2 IDs for the same input, but facelets complains
                    // about duplicate component IDs even though only one of them can possibly be rendered at a time
                    collectorType = activityDS.getType();
                }

                Configuration configPageRegistry =
                    getContext().getObjectByName(Configuration.class, "ActivityCollectorConfigPageRegistry");
                configPage = configPageRegistry.getString(collectorType);
            }
        } catch (GeneralException e) {
            log.error("The ActivityCollectrConfigPageRegistry is not available right now.", e);
        }

        if (configPage == null) {
            configPage = "defaultConfig.xhtml";
        }

        return configPage;
    }

    /**
     *
     * @return
     */
    public String changeType() {
        ActivityDataSourceDTO activityDS = getObject();
        activityDS.setCollector(getCollectorTypes().get(activityDS.getType()));
        _attrConfig = null;

        return "";
    } // changeType()

    public ActivityDataSourceDTO getObject() {
        return object;
    }

    /**
     *
     * @return
     */
    public boolean isTestSuccess() {
        return _testSuccess;
    }

    /**
     *
     * @return
     */
    public String getTestResult() {
        return _testResult;
    }

    /**
     *
     */
    @SuppressWarnings("unchecked")
    public String saveAction() {
        boolean error = false;
        ActivityDataSourceDTO activityDSDTO = getObject();

        if ( activityDSDTO != null ) {
            String name = activityDSDTO.getName();
            if ( name == null || name.trim().length() == 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.NAME_REQUIRED), null);
                error = true;
            }

            if (activityDSDTO.getId() == null) {
                try {
                    ActivityDataSource existingActivityDS = getContext().getObjectByName(ActivityDataSource.class, name);
                    if (existingActivityDS != null) {
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.DUPLICATE_ACTIVITY_DATA_SRC_NAME, name), null);
                        error = true;
                    }
                } catch (GeneralException e) {
                    Message errMsg = new Message(Message.Type.Error,
                        MessageKeys.ERR_SAVING_DATA_SRC_OBJ, e);
                    addMessage(errMsg, null);
                    log.error(errMsg.getMessage(), e);
                }
            }

            String type = activityDSDTO.getType();
            if ( type == null || type.trim().length() == 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ACTIVITY_DATA_SRC_REQUIRED), null);
                error = true;
            }

                // make sure that required activity data source attributes have a value
            List<AttributeDefinition> activityDSAttrs = null;
            try {
                activityDSAttrs = getAttributeConfig();
            } catch ( GeneralException ex ) {
                // getAttributeConfig() will add error messages to the context
            }
            if ( activityDSAttrs != null )
            {
                for ( AttributeDefinition attrDef : activityDSAttrs )
                {
                    if ( attrDef != null && attrDef.isRequired() )
                    {
                        Object attrVal = activityDSDTO.getAttributeValue(attrDef.getName());
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
            Attributes<String,Object> configuration = activityDSDTO.getConfiguration();
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

                cleanUpConfig(activityDSDTO);
                Map<String, ActivityDataSourceDTO> editedDSMap =
                    (Map<String, ActivityDataSourceDTO>) appBean.getEditState(ApplicationObjectBean.EDITED_ACTIVITY_DS_MAP);

                if (editedDSMap == null) {
                    editedDSMap = new HashMap<String, ActivityDataSourceDTO>();
                    appBean.addEditState(ApplicationObjectBean.EDITED_ACTIVITY_DS_MAP, editedDSMap);
                }
                
                // check for an existing activity data source and set the already generated id 
                // otherwise the same data source can be added to the map multiple times which 
                // causes problems
                for (String id : editedDSMap.keySet()) {
                    ActivityDataSourceDTO dataSource = editedDSMap.get(id);
                    if (dataSource.getName().equals(activityDSDTO.getName())) {
                        activityDSDTO.setId(id);
                        break;
                    }
                }
                
                if (null == activityDSDTO.getId()) {
                    String tempID = "temp" + new java.rmi.dgc.VMID().toString();
                    activityDSDTO.setId(tempID);
                }
                
                editedDSMap.put(activityDSDTO.getId(), activityDSDTO);

                // Forward on the request params in the session so that we don't lose our state
                String id = (String) getRequestParam().get("editForm:id");
                getSessionScope().put("editForm:id", id);

                // Remove ourselves from the session because our job is done for the time being
                appBean.removeEditState(ApplicationObjectBean.EDITED_ACTIVITY_DS);

                return "saveDataSource";
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
        Map<String, ActivityDataSourceDTO> editedDSMap =
            (Map<String, ActivityDataSourceDTO>) appBean.getEditState(ApplicationObjectBean.EDITED_ACTIVITY_DS_MAP);
        ActivityDataSourceDTO editedDataSource = getObject();
        if (editedDSMap != null) {
            editedDSMap.remove(editedDataSource.getId());
        }

        appBean.removeEditState(ApplicationObjectBean.EDITED_ACTIVITY_DS);

        return "cancelDataSource";
    }

    @SuppressWarnings("unchecked")
    public String addLogField() {
        String result;

        if (_newLogField.getName() == null || _newLogField.getName().trim().length() == 0) {
            addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_LOG_FIELDS_REQUIRED), null);
            result = "";
        } else {
            List<LogField> logFields = (List<LogField>) getObject().getConfiguration().get("fields");

            if (logFields == null) {
                logFields = new ArrayList<LogField>();
                getObject().getConfiguration().put("fields", logFields);
            }

            // If a log field with the same name already exists, don't add another
            boolean existsAlready = false;
            for (LogField existingLogField : logFields) {
                if (existingLogField.getName().equals(_newLogField.getName()))
                    existsAlready = true;
            }

            if (!existsAlready) {
                logFields.add(_newLogField);
                _newLogField = new LogField();
                _newLogField.setTrim(true);
                result = "addedLogField";
            } else {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_DUPLICATE_LOG_FIELD_NAME, _newLogField.getName()), null);
                result = "";
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public String removeLogFields() {
        String result;

        Attributes<String, Object> config = getObject().getConfiguration();

        List<LogField> logFields = (List<LogField>) config.get("fields");
        Set<String> logFieldNames = _selectedLogFields.keySet();
        Set<String> logFieldsToRemove = new HashSet<String>();

        for (String logFieldName : logFieldNames) {
            if (_selectedLogFields.get(logFieldName)) {
                logFieldsToRemove.add(logFieldName);
            }
        }

        Iterator<LogField> i = logFields.iterator();

        while (i.hasNext()) {
            LogField currentLogField = i.next();
            if (logFieldsToRemove.contains(currentLogField.getName())) {
                i.remove();
            }
        }

        result = "removedLogFields";

        return result;
    }

    public String updateTransportSettings() {
        return "";
    }


    public List<SelectItem> getTransformationRules() throws GeneralException {
        // don't cache these, or we won't pick up any new rules
        // added by the rule editor
        return WebUtil.getRulesByType(getContext(),
                                      Rule.Type.ActivityTransformer,
                                      true, false,
                                      getObject().getTransformationRule());
    }

    public List<SelectItem> getTransformationRuleNames() throws GeneralException {
        _transformationRuleNames = getTransformationRules();

        return _transformationRuleNames;
    }

    public List<SelectItem> getCorrelationRules() throws GeneralException {
        // don't cache these, or we won't pick up any new rules
        // added by the rule editor
        return WebUtil.getRulesByType(getContext(),
                                      Rule.Type.ActivityCorrelation,
                                      true, false,
                                      getObject().getCorrelationRule());
    }

    public List<SelectItem> getPositionBuilders() throws GeneralException {
        if (_positionBuilders == null) {
            ActivityDataSourceDTO ads = getObject();
            String ruleName = ads.getConfiguration().getString(JDBCActivityCollector.CONFIG_POSITION_BUILDER);
            Rule selected = (null != ruleName) ? getContext().getObjectByName(Rule.class, ruleName) : null;
            _positionBuilders = WebUtil.getRulesByType(getContext(),
                                                       Rule.Type.ActivityPositionBuilder,
                                                       true, false,
                                                       ((null != selected) ? ruleName : null));
        }

        return _positionBuilders;
    }

    public List<SelectItem> getConditionBuilders() throws GeneralException {
        if (_conditionBuilders == null) {
            ActivityDataSourceDTO ads = getObject();
            String ruleName = ads.getConfiguration().getString(JDBCActivityCollector.CONFIG_CONDITION_BUILDER);
            Rule selected = (null != ruleName) ? getContext().getObjectByName(Rule.class, ruleName) : null;
            _conditionBuilders = WebUtil.getRulesByType(getContext(),
                                                        Rule.Type.ActivityConditionBuilder,
                                                        true, false,
                                                        ((null != selected) ? ruleName : null));
        }

        return _conditionBuilders;
    }

    public String selectType() throws GeneralException {
        ActivityDataSourceDTO dto = getObject();
        if (_typeToCollectorMap == null) {
            getCollectorTypes();
        }

        if (_typeToCollectorMap != null) {
            Configuration sysConfig = Configuration.getSystemConfig();
            String collectorType = dto.getType();
            String collector = _typeToCollectorMap.get(collectorType);
            dto.setCollector(collector);
            
            //Get the CEF Log File name from the System Configuration
            //Read and parse the file and set the configuration fields
            if (isCollectorTypeCEFLogFile(collectorType) && sysConfig != null) {
                String cefActivityFeedDataFieldFile = sysConfig.getString(Configuration.ATT_CEF_LOGFILE_ACTIVITYFEED_DATASOURCE);
                if (cefActivityFeedDataFieldFile != null) {
                    String cefLogFileAttributes = Util.readFile(cefActivityFeedDataFieldFile);
                    if(cefLogFileAttributes != null) {
                        Object cefLogFileAttributesObject = SailPointObject.parseXml(null, cefLogFileAttributes);
                        if (cefLogFileAttributesObject != null && cefLogFileAttributesObject instanceof Attributes) {
                            dto.setConfiguration((Attributes<String, Object>) cefLogFileAttributesObject);
                        }
                    }
                }
            }
            else {
                //When the Activity Data Source collector type is set to CEF Log file and if the user again
                //try to change it to some other collector type, then the configuration fields will be set to
                //default values by setting null.
                dto.setConfiguration(null);
            }
        }

        // Update the selected values so that we don't lose the selections between requests
        dto.setCorrelationRule(getSelectedCorrelationRule());
        dto.setTransformationRule(getSelectedTransformationRule());

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

        ActivityDataSourceDTO ads = getObject();
        List<String> targets = ads.getTargets();
        if (targets != null && !targets.isEmpty())
            retval.addAll(targets);

        return retval;
    }

    public String addTargetAction() {
        ActivityDataSourceDTO ads = getObject();
        ads.addTarget(_addedTarget);

        clearAddedTarget();

        return "";
    }

    public String deleteTargetsAction() {
        ActivityDataSourceDTO adsDTO = getObject();

        Map <String, Boolean> targetsToDelete = getSelectedTargets();

        for (String target : targetsToDelete.keySet()) {
            if (targetsToDelete.get(target)) {
                adsDTO.removeTarget(target);
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
     * djs: We really shouldn't be delegating to the activity datasource
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
    private void cleanUpConfig(ActivityDataSourceDTO activityDS) {
        Attributes<String, Object> config = activityDS.getConfiguration();
        if (config != null) {
            try {
                // ALWAYS preserve these values
                Object fieldMap = config.get("fieldMap");
                Object filters = config.get(ActivityCollector.CONFIG_FILTERS);
                Object allowAll = config.get(ActivityCollector.CONFIG_ALLOW_ALL);
                Object userAttr = config.get(ActivityCollector.CONFIG_USER_ATTRIBUTE);
                ActivityCollector collector =
                    ActivityCollectorFactory.getCollector(activityDS.buildPartialActivityDataSource());
                collector.cleanUpConfig(config);
                // ALWAYS preserve these values
                if ( fieldMap != null )
                    config.put("fieldMap",fieldMap);
                if ( filters != null )
                    config.put(ActivityCollector.CONFIG_FILTERS,filters);
                if ( allowAll != null )
                    config.put(ActivityCollector.CONFIG_ALLOW_ALL,allowAll);
                if ( userAttr != null )
                    config.put(ActivityCollector.CONFIG_USER_ATTRIBUTE,userAttr);
            } catch (Exception e) {
                log.error("Could not find a collector of for this activity data source " + activityDS.getName(), e);
            }
        }
    }
} // class ActivityDataSourceObjectBean
