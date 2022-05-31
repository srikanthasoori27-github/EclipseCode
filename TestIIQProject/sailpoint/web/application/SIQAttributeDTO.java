/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.application;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ExceptionCleaner;
import sailpoint.connector.Connector;
import sailpoint.connector.SecurityIQConnector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Schema;
import sailpoint.object.TargetHostConfig;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;

import javax.faces.model.SelectItem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 5/25/17.
 */
public class SIQAttributeDTO extends AttributeDTO {

    private Log log = LogFactory.getLog(SIQAttributeDTO.class);
    private static final String SIQ_COLLECTOR = "sailpoint.unstructured.SecurityIQTargetCollector";


    private String _url = "jdbc:sqlserver://localhost:1433;databaseName=SecurityIQDB";
    private String _userName = "SecurityIQ_User";
    private String _password;
    //Default driverClass
    private String _driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    //List of hostNames
    List<String> _targetHosts;
    //List of TargetHostConfig
    private List<TargetHostConfigDTO> _targetHostConfigDtos;
    private String _schemaName = "whiteops";
    private boolean _aggregateInherited;
    //Do we want this as an option? -rap
    private boolean _aggregateDeny;
    private String _referencedApplications;



    //Variable to hold status of connection test
    private String _testResult;
    private boolean _testError;

    private List<TargetHostConfigDTO> _availableHosts;

    public SIQAttributeDTO(SchemaDTO dto) {
        super(dto);
        if (Schema.TYPE_UNSTRUCTURED.equals(dto.getObjectType())) {
            Attributes<String, Object> atts = dto.getConfig();
            if (Util.isNotNullOrEmpty(atts.getString(JdbcUtil.ARG_URL))) {
                _url = atts.getString(JdbcUtil.ARG_URL);
            }
            if (Util.isNotNullOrEmpty(atts.getString(JdbcUtil.ARG_USER))) {
                _userName = atts.getString(JdbcUtil.ARG_USER);
            }
            _password = atts.getString(JdbcUtil.ARG_PASSWORD);
            if (Util.isNotNullOrEmpty(atts.getString(JdbcUtil.ARG_DRIVER_CLASS))) {
                _driverClass = atts.getString(JdbcUtil.ARG_DRIVER_CLASS);
            }
            if (Util.isNotNullOrEmpty(atts.getString(SecurityIQConnector.SIQTargetConnector.ARG_SCHEMA_NAME))) {
                _schemaName = atts.getString(SecurityIQConnector.SIQTargetConnector.ARG_SCHEMA_NAME);
            }
            //Initialize the _targetHostConfigDtos
            createTargetHostConfigBeans(atts.getList(SecurityIQConnector.SIQTargetConnector.INCLUDED_TARGET_HOSTS));
            if (!Util.isEmpty(_targetHostConfigDtos)) {
                //Initialize _availableHosts to the configured _targetHostConfigDtos
                //Can call Discover, but that may be unneeded overhead
                for (TargetHostConfigDTO thc: _targetHostConfigDtos) {
                    if (_availableHosts == null) {
                        _availableHosts = new ArrayList<TargetHostConfigDTO>();
                    }
                    _availableHosts.add(thc);
                }
            }
            _aggregateInherited = atts.getBoolean(SecurityIQConnector.SIQTargetConnector.CONFIG_AGG_INHERITED);
        }

    }

    //Create a list of TargetHostConfig from the given Map
    public void createTargetHostConfigBeans(List<TargetHostConfig> targetHostConfigs) {

        if (!Util.isEmpty(targetHostConfigs)) {
            for (TargetHostConfig thc : targetHostConfigs) {
                addTargetHost(thc);
            }
        }
    }

    //Create/Delete TargetHostConfigs to reflect what has been selected in the multiselect
    public void updateTargetHostConfigs() {
        if (Util.isEmpty(_targetHosts)) {
            //Clear all configs as well
            if (_targetHostConfigDtos != null) {
                _targetHostConfigDtos.clear();
            }
        } else {
            if (_targetHostConfigDtos != null) {
                for (Iterator it = _targetHostConfigDtos.iterator(); it.hasNext();) {
                    TargetHostConfigDTO thcb = (TargetHostConfigDTO)it.next();
                    if (!_targetHosts.contains(thcb.getHostName())) {
                        it.remove();
                    }
                }
            }
            for (String s : Util.safeIterable(_targetHosts)) {
                if (_targetHostConfigDtos == null) {
                    _targetHostConfigDtos = new ArrayList<TargetHostConfigDTO>();
                }
                boolean found = false;
                for (TargetHostConfigDTO thcb : Util.safeIterable(_targetHostConfigDtos)) {
                    if (thcb.getHostName().equals(s)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    //Create a configBean for the targetHost
                    _targetHostConfigDtos.add(getAvailableConfigBean(s));
                }
            }
        }
    }

    /**
     * Return the TargetHostConfigBean from the
     * @see #_availableHosts with the given hostName
     * @param hostName - hostName of the configBean
     * @return
     */
    public TargetHostConfigDTO getAvailableConfigBean(String hostName) {
        for (TargetHostConfigDTO b : Util.safeIterable(_availableHosts)) {
            if (Util.nullSafeEq(b.getHostName(), hostName)) {
                return b;
            }
        }
        return null;
    }

    public void addTargetHost(TargetHostConfig hostConfig) {
        if (_targetHostConfigDtos == null) {
            _targetHostConfigDtos = new ArrayList<TargetHostConfigDTO>();
        }
        _targetHostConfigDtos.add(new TargetHostConfigDTO(hostConfig));
        if (_targetHosts == null) {
            _targetHosts = new ArrayList<String>();
        }
        _targetHosts.add(hostConfig.getHostName());
    }

    @Override
    public void saveAttributeData() {
            if (_schemaDTO.getObjectType().equals(Schema.TYPE_UNSTRUCTURED)) {
                //Only need to modify the Unstructured for time being. Alert is self sufficient
                _schemaDTO.addConfig(JdbcUtil.ARG_URL, _url);
                _schemaDTO.addConfig(JdbcUtil.ARG_USER, _userName);
                _schemaDTO.addConfig(JdbcUtil.ARG_PASSWORD, _password);
                _schemaDTO.addConfig(JdbcUtil.ARG_DRIVER_CLASS, _driverClass);
                _schemaDTO.addConfig(SecurityIQConnector.SIQTargetConnector.ARG_SCHEMA_NAME, _schemaName);
                _schemaDTO.addConfig(SecurityIQConnector.SIQTargetConnector.INCLUDED_TARGET_HOSTS, getTargetHostConfigs());
                _schemaDTO.addConfig(SecurityIQConnector.SIQTargetConnector.CONFIG_AGG_INHERITED, _aggregateInherited);
            }
    }


    public List<TargetHostConfig> getTargetHostConfigs() {
        List<TargetHostConfig> configs = new ArrayList<>();
        for (TargetHostConfigDTO bean : Util.safeIterable(_targetHostConfigDtos)) {
            configs.add(bean.clone());
        }
        return configs;
    }

    public List<TargetHostConfigDTO> getTargetHostConfigDtos() {
        return _targetHostConfigDtos;
    }

    public String testConfiguration() {
        boolean success = true;
        try {
            _testResult = "Test Successful";
            _testError = false;
            Connector connector = getTargetConnector();
            //This will throw exception if misconfigured
            connector.testConfiguration();

        } catch (Exception ge) {
            if (log.isWarnEnabled()) {
                log.warn("Exception testing config." + ge);
            }
            _testResult = ExceptionCleaner.cleanConnectorException(ge);
            _testError = true;
        }
        return "";
    }

    public String discoverTargetHosts() {
        if (Schema.TYPE_UNSTRUCTURED.equals(_schemaDTO.getObjectType())) {
            try {

                List<Map<String, Object>> configs = getTargetConnector().getTargetHosts();
                createAvailableHostConfigs(configs);

            } catch (Exception ge) {
                if (log.isErrorEnabled()) {
                    log.error("Exception getting available target hosts." + ge);
                }
                addMessage(ge);
            }
        } else {
            addMessage("No Unstructured Schema found");
        }

        return "";
    }

    /**
     * Create an instance of the SIQTargetConnector to get the available TargetHosts
     * @return
     * @throws Exception
     */
    private SecurityIQConnector.SIQTargetConnector getTargetConnector() throws Exception {
        return new SecurityIQConnector(getPartialApp()).getTargetConnector();
    }


    /**
     * Create a partial App with enough config to fetch the TargetHosts
     * @return
     */
    protected Application getPartialApp() {
        Application app = new Application();
        Schema s = new Schema();
        s.setObjectType(Schema.TYPE_UNSTRUCTURED);
        _schemaDTO.populate(s);
        //Connector init wants an associationschema
        Schema assoc = new Schema();
        assoc.setObjectType("placeHolder");
        s.setAssociationSchemaName("placeHolder");

        app.setSchema(s);
        app.setSchema(assoc);
        //Mock up AssociationSchema

        return app;
    }



    public void createAvailableHostConfigs(List<Map<String, Object>> configs) {
        if (_availableHosts == null) {
            _availableHosts = new ArrayList<TargetHostConfigDTO>();
        } else {
            _availableHosts.clear();
        }
        for (Map m : Util.safeIterable(configs)) {
            _availableHosts.add(createConfig(m));
        }
    }

    public TargetHostConfigDTO createConfig(Map<String, Object> m) {
        TargetHostConfigDTO b = new TargetHostConfigDTO();
        b.setHostId(Util.getString(m, SecurityIQConnector.SIQTargetConnector.TARGET_HOST_BAM_ID));
        b.setHostName(Util.getString(m, SecurityIQConnector.SIQTargetConnector.TARGET_HOST_BAM_NAME));
        b.setCaseSensitive(Util.getBoolean(m, SecurityIQConnector.SIQTargetConnector.TARGET_HOST_CASE_SENSITIVE));

        return b;
    }

    /**
     * Return a JSON representation of available BAM_names from the SIQCollector
     * @return
     */
    public List<SelectItem> getTargetHostSelectItems() {

        List<SelectItem> items = new ArrayList<SelectItem>();
        for(TargetHostConfigDTO s : Util.safeIterable(_availableHosts)) {
            items.add(new SelectItem(s.getHostName(), s.getHostName()));
        }

        return items;
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

    public void setTargetHostConfigs(List<TargetHostConfigDTO> configs) {
        _targetHostConfigDtos = configs;
    }

    public void setTargetHosts(List _targetHosts) {
        this._targetHosts = _targetHosts;
    }

    public List getTargetHosts() {
        return _targetHosts;
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

    public String getReferencedApplications() { return _referencedApplications; }

    public void setReferencedApplications(String apps) { _referencedApplications = apps; }

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


    public class TargetHostConfigDTO {

        public String _hostName;
        public List<String> _paths;
        public String _pathsCSV;
        public String _hostId;
        public boolean _caseSensitive;

        public TargetHostConfigDTO() { }

        public TargetHostConfigDTO(TargetHostConfig thc) {
            _hostName = thc.getHostName();
            _paths = thc.getPaths();
            _pathsCSV = Util.listToCsv(_paths);
            _hostId = thc.getHostId();
            _caseSensitive = thc.isPathCaseSensitive();
        }

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


}
