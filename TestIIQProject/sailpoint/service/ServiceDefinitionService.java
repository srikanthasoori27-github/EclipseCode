/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.server.MonitoringService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceDefinitionService extends BaseObjectService<ServiceDefinition> {

    private ServiceDefinition serviceDef;
    private SailPointContext context;
    private UserContext userContext;


    public ServiceDefinitionService(String serviceDefName, UserContext userContext)
        throws GeneralException {

        if (Util.isNullOrEmpty(serviceDefName)) {
            throw new InvalidParameterException("serviceDefName");
        }

        initContext(userContext);
        this.serviceDef = context.getObjectByName(ServiceDefinition.class, serviceDefName);
        if (this.serviceDef == null) {
            throw new ObjectNotFoundException(ServiceDefinition.class, serviceDefName);
        }
    }

    /**
     * Initialize the context
     * @param userContext UserContext
     * @throws GeneralException
     */
    private void initContext(UserContext userContext) throws GeneralException {
        if (userContext == null) {
            throw new InvalidParameterException("userContext");
        }

        this.userContext = userContext;
        this.context = userContext.getContext();

    }

    @Override
    protected ServiceDefinition getObject() {
        return this.serviceDef;
    }

    @Override
    protected SailPointContext getContext() {
        return this.context;
    }

    public static String PATCH_INTERVAL = "interval";
    public static String PATCH_RETENTION = "retention";
    public static String PATCH_MONITORED_STATISTICS = "monitoredStatistics";

    @Override
    protected List<String> getAllowedPatchFields() {
        List<String> fields = new ArrayList<>();
        fields.add(PATCH_INTERVAL);
        fields.add(PATCH_RETENTION);
        fields.add(PATCH_MONITORED_STATISTICS);
        return fields;
    }

    @Override
    protected boolean validateValue(String field, Object value) {
        if (PATCH_INTERVAL.equals(field)) {
            if (Util.otoi(value) > 0) {
                //Must be greater than 0
                return true;
            }
        }

        if (getObject() != null) {
            if (MonitoringService.NAME.equals(getObject().getName())) {
                //Monitoring Statistics are allowed to be patched
                if (PATCH_RETENTION.equals(field)) {
                    if (Util.otoi(value) >= 0) {
                        //Must be >= 0
                        return true;
                    }
                } else if (PATCH_MONITORED_STATISTICS.equals(field)) {
                    if (Util.otol(field) != null) {
                        return true;
                    }
                }
            }
        }


        return false;
    }

    @Override
    protected void patchValue(String field, Object value) {

        if (PATCH_INTERVAL.equals(field)) {
            serviceDef.setInterval(Util.otoi(value));
        } else if (PATCH_RETENTION.equals(field)) {
            Map serviceMonitoringConfig = (Map)serviceDef.get(Server.ATT_MONITORING_CFG);
            if (serviceMonitoringConfig == null) {
                serviceMonitoringConfig = new HashMap<String, Object>();
                serviceDef.put(Server.ATT_MONITORING_CFG, serviceMonitoringConfig);
            }

            serviceMonitoringConfig.put(Server.ATT_MONITORING_CFG_RETENTION, Util.otoi(value));
        } else if (PATCH_MONITORED_STATISTICS.equals(field)) {
            Map serviceMonitoringConfig = (Map)serviceDef.get(Server.ATT_MONITORING_CFG);
            if (serviceMonitoringConfig == null) {
                serviceMonitoringConfig = new HashMap<String, Object>();
                serviceDef.put(Server.ATT_MONITORING_CFG, serviceMonitoringConfig);
            }
            serviceMonitoringConfig.put(Server.ATT_MONITORING_CFG_STATS, Util.otol(value));
        }
    }
}
