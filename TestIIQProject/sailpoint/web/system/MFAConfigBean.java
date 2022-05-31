/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Configuration;
import sailpoint.object.DynamicScope;
import sailpoint.object.Filter;
import sailpoint.object.MFAConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Variable;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.BaseBean;
import sailpoint.web.LoginConfigBean;
import sailpoint.web.messages.MessageKeys;

/**
 * MultiFactor configuration bean used to support MFA configuration page.
 *
 */
public class MFAConfigBean extends BaseBean {

    private static final Log log = LogFactory.getLog(MFAConfigBean.class);

    /**
     * MFA population data used to populate the multiselect control on the MFA configuration 
     * page with the name and id of dynamic scopes to be used for MFA workflows.
     */
    public static class MFAPopulationData {
        private String id;
        private String name;

        public MFAPopulationData() {

        }

        public MFAPopulationData(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
    
    /**
     * MFA config DTO used by the MFA configuration page to display configuration information.
     */
    public static class MFAConfigDTO {
        private String workflowName;
        private List<MFAPopulationData> populations;
        private boolean enabled;
        private boolean fullyConfigured;
        
        public MFAConfigDTO() {

        }

        public MFAConfigDTO(MFAConfig mfaConfig) {
            this.workflowName = mfaConfig.getWorkflow().getName();
            this.enabled = mfaConfig.isEnabled();
            this.populations = new ArrayList<MFAPopulationData>();
            for (DynamicScope population : Util.safeIterable(mfaConfig.getPopulations())) {
                populations.add(new MFAPopulationData(population.getName(), population.getId()));
            }

            this.fullyConfigured = true;
            Workflow wf = mfaConfig.getWorkflow();
            //check required variables in workflow
            List<Variable> variables = mfaConfig.getWorkflow().getVariableDefinitions();
            for (Variable var : Util.safeIterable(variables)) {
                if (var.isRequired()) {
                    if (wf.get(var.getName()) == null && var.getInitializer() == null && var.getDefaultValue() == null) {
                        this.fullyConfigured = false;
                        break;
                    }
                }
            }
        }

        public String getWorkflowName() {
            return workflowName;
        }

        public void setWorkflowName(String workflowName) {
            this.workflowName = workflowName;
        }

        public List<MFAPopulationData> getPopulations() {
            return populations;
        }

        public void setPopulations(List<MFAPopulationData> populations) {
            this.populations = populations;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFullyConfigured() {
            return fullyConfigured;
        }

        public void setFullyConfigured(boolean fullyConfigured) {
            this.fullyConfigured = fullyConfigured;
        }

    }

    List<MFAConfig> _mfaConfigList;
    Attributes _previousConfig;

    public MFAConfigBean() throws GeneralException {
        // grab all of the MFA workflows
        Map<String, Workflow> mfaWorkflows = getMFAWorkflows();
        
        // grab the current configuration (if there is one)
        Configuration mfaConfiguration = getMFAConfiguration();
        _mfaConfigList = mfaConfiguration.getList(Configuration.MFA_CONFIG_LIST);
        if(_mfaConfigList == null) {
            _mfaConfigList = new ArrayList<MFAConfig>();
        }

        //Save off previous config for audit purpose. TODO: will this deep copy?
        _previousConfig = new Attributes(mfaConfiguration.getAttributes());
        
        // make a list of configs where the workflow has been deleted
        List<MFAConfig> configsToRemove = new ArrayList();
        for(MFAConfig mfaConfig : _mfaConfigList) {
            Workflow workflow = mfaConfig.getWorkflow();
            String workflowId = (workflow == null) ? null : workflow.getId();
            if(mfaWorkflows.containsKey(workflowId)) {
                mfaWorkflows.remove(workflowId);
            } else {
                configsToRemove.add(mfaConfig);
            }
        }
        
        // remove any configured workflows that no longer exist
        for(MFAConfig mfaConfig : configsToRemove) {
            _mfaConfigList.remove(mfaConfig);
        }
        
        // add any mfa workflows that are not in the configuration
        for(Workflow workflow : Util.safeIterable(mfaWorkflows.values())) {
            MFAConfig mfaConfig = new MFAConfig();
            mfaConfig.setEnabled(false);
            mfaConfig.setWorkflow(workflow);
            _mfaConfigList.add(mfaConfig);
        }
    }

    public String getConfig() {
        return JsonHelper.toJson(getMFAConfigDTOs(_mfaConfigList));
    }

    public void setConfig(String xmlConfig) throws GeneralException {
        List<MFAConfigDTO> mfaConfigDTOList = JsonHelper.listFromJson(MFAConfigDTO.class, xmlConfig);
        _mfaConfigList = getMFAConfigs(mfaConfigDTOList);
    }

    public boolean save(LoginConfigBean loginConfig) throws GeneralException {
        // Validate first.
        if (!validate(loginConfig)) {
            return false;
        }

        Configuration mfaConfiguration = getMFAConfiguration();
        mfaConfiguration.put(Configuration.MFA_CONFIG_LIST, _mfaConfigList);

        getContext().saveObject(mfaConfiguration);
        getContext().commitTransaction();

        auditMFAConfigUpdate(loginConfig);

        return true;
    }

    private void auditMFAConfigUpdate(LoginConfigBean loginConfig) {
        if (loginConfig != null) {
            AuditEvent evt = loginConfig.getAuditEvent();
            if (evt != null) {
                //TODO: How detailed here? -rap
                List<MFAConfig> origConfigs = _previousConfig.getList(Configuration.MFA_CONFIG_LIST);

                if (Util.size(origConfigs) != Util.size(_mfaConfigList)) {
                    evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.MFA + "] " + Configuration.MFA_CONFIG_LIST,
                            new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                    origConfigs != null ? XMLObjectFactory.getInstance().toXml(origConfigs) : "",
                                    _mfaConfigList != null ? XMLObjectFactory.getInstance().toXml(_mfaConfigList) : ""));
                } else if (_mfaConfigList != null && origConfigs != null) {
                    //Same size and non-null
                    if (!_mfaConfigList.containsAll(origConfigs)) {
                        evt.setAttribute(MessageKeys.CONFIGURATION + "[" +Configuration.MFA + "] " + Configuration.MFA_CONFIG_LIST,
                                new Message(Message.Type.Info, MessageKeys.AUDIT_ACTION_LOGIN_CONFIG_VALUE_UPDATE,
                                        XMLObjectFactory.getInstance().toXml(origConfigs),
                                        XMLObjectFactory.getInstance().toXml(_mfaConfigList)));
                    }
                }
            }
        }
    }

    public boolean validate(LoginConfigBean loginConfig) throws GeneralException {
        boolean valid = true;

        for (MFAConfig mfaConfig : Util.safeIterable(_mfaConfigList)) {
            if (mfaConfig.isEnabled()) {
                if (Util.isEmpty(mfaConfig.getPopulations())) {
                    String workflowName = null;
                    Workflow workflow = mfaConfig.getWorkflow();
                    if(workflow != null) {
                        workflowName = workflow.getName();
                    }
                    loginConfig.addMessage(Message.error(Message.info(MessageKeys.MFA_POPULATIONS_REQUIRED, workflowName).
                                    getLocalizedMessage(this.getLocale(), this.getUserTimeZone())));
                    valid = false;
                }
            }
        }

        return valid;
    }

    private List<MFAConfig> getMFAConfigs(List<MFAConfigDTO> mfaConfigDTOList) throws GeneralException {
        List<MFAConfig> mfaConfigList = new ArrayList<MFAConfig>();

        for (MFAConfigDTO dto : mfaConfigDTOList) {
            MFAConfig mfaConfig = new MFAConfig();
            mfaConfig.setWorkflow(getContext().getObjectByName(Workflow.class, dto.getWorkflowName()));
            mfaConfig.setEnabled(dto.isEnabled());
            List<DynamicScope> populations = null;
            for (MFAPopulationData population : dto.getPopulations()) {
                if (populations == null) {
                    populations = new ArrayList<>();
                }
                populations.add(getContext().getObjectByName(DynamicScope.class, population.getName()));
            }
            mfaConfig.setPopulations(populations);
            mfaConfigList.add(mfaConfig);
        }

        return mfaConfigList;
    }

    private List<MFAConfigDTO> getMFAConfigDTOs(List<MFAConfig> mfaConfigList) {
        List<MFAConfigDTO> mfaConfigDTOList = new ArrayList<MFAConfigDTO>();

        for (MFAConfig mfaConfig : Util.safeIterable(mfaConfigList)) {
            mfaConfigDTOList.add(new MFAConfigDTO(mfaConfig));
        }

        return mfaConfigDTOList;
    }

    private Configuration getMFAConfiguration() throws GeneralException {
        Configuration mfaConfiguration = getContext().getObjectByName(Configuration.class, Configuration.MFA);

        if (mfaConfiguration == null) {
            mfaConfiguration = new Configuration();
            mfaConfiguration.setName(Configuration.MFA);
        }

        return mfaConfiguration;
    }
    
    private Map<String, Workflow> getMFAWorkflows() throws GeneralException {
        Map<String, Workflow> mfaWorkflowMap = new HashMap<String, Workflow>();
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("type", Workflow.TYPE_MULTI_FACTOR_AUTHENTICATION));
        List<Workflow> mfaWorkflows = getContext().getObjects(Workflow.class, ops);
        for(Workflow mfaWorkflow : Util.safeIterable(mfaWorkflows)) {
            mfaWorkflowMap.put(mfaWorkflow.getId(), mfaWorkflow);
        }
        return mfaWorkflowMap;
    }
    
}
