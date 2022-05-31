package sailpoint.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectConfigService;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.server.MonitoringConfig;
import sailpoint.server.MonitoringService;
import sailpoint.server.ServicerUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Used by HostsResource to GET/PATCH Server objects
 */
public class ServerService extends BaseObjectService<Server> {

    private Server server;
    private List<ServiceDefinition> serviceDefinitions;

    private SailPointContext context;
    private UserContext userContext;

    private static final Log log = LogFactory.getLog(ServerService.class);

    public ServerService(String serverId, UserContext userContext)
        throws GeneralException {

        if (Util.isNullOrEmpty(serverId)) {
            throw new InvalidParameterException("serverId");
        }

        initContext(userContext);

        this.server = context.getObjectById(Server.class, serverId);
        if (this.server == null) {
            throw new ObjectNotFoundException(Server.class, serverId);
        }

        serviceDefinitions = ServicerUtil.getDefinitions(context);
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

    public ServerDTO getServerDTO(Boolean showServices) throws GeneralException {
        return getServerDTO(showServices, null);
    }

    public ServerDTO getServerDTO(Boolean showServices, Boolean includeDefaults) throws GeneralException {
        ServerDTO serverDTO = new ServerDTO(this.server, this.userContext);

        if (showServices != null && showServices) {
            List<ServerDTO.ServiceState> serviceStates = buildServiceStates(context, server);
            serverDTO.setServiceStates(serviceStates);
        }

        if (includeDefaults != null && includeDefaults) {
            try {
                ServiceDefinition serviceDef = context.getObjectByName(ServiceDefinition.class, MonitoringService.NAME);
                if (serviceDef != null) {
                    Map serviceMonitoringConfig = (Map)serviceDef.get(Server.ATT_MONITORING_CFG);

                    serverDTO.setDefaultStatistics(MonitoringConfig.getDefaultMonitoringStatistics(serviceMonitoringConfig));
                    serverDTO.setDefaultPollingInterval(MonitoringConfig.getDefaultPollingInterval(serviceDef));
                    serverDTO.setDefaultRetention(MonitoringConfig.getDefaultRetentionPeriod(serviceMonitoringConfig));
                }
            } catch (Exception e) {
                log.error("Error getting Monitoring config defaults" + e);
            }
        }

        //Build Extended Attributes
        ObjectConfigService objectConfigService = new ObjectConfigService(this.userContext.getContext(), this.userContext.getLocale());
        serverDTO.setExtendedAttributes(objectConfigService.getExtendedAttributeValues(this.server));
        return serverDTO;
    }

    public void deleteServer()  throws GeneralException {
            Terminator terminator = new Terminator(context);
            terminator.deleteObject(server);
    }

    private List<ServerDTO.ServiceState> buildServiceStates(SailPointContext context, Server server)
            throws GeneralException {

        List<ServerDTO.ServiceState> serverStates = new ArrayList<ServerDTO.ServiceState>();
        if (serviceDefinitions == null) {
            return serverStates;
        }

        for(ServiceDefinition serviceDefinition : serviceDefinitions) {
            ServerDTO.ServiceState serviceState = new ServerDTO.ServiceState();
            Boolean allowedByServer = null;
            String serviceName = serviceDefinition.getName();
            try {
                allowedByServer = ServicerUtil.isServiceAllowedByServer(context, serviceDefinition.getName(),server.getName());
            } catch (GeneralException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to fetch Server for local host", e);
                }
            }

            String state = null;
            if (allowedByServer != null) {
                state = allowedByServer ? ServerDTO.ServiceState.STATE_INCLUDE : ServerDTO.ServiceState.STATE_EXCLUDE;
            }
            else {
                // Not included/excluded by Server, so fallback and check if allowed by ServiceDefinition
                boolean inheritedOn = serviceDefinition.isHostAllowed(server.getName());
                state = inheritedOn ? ServerDTO.ServiceState.STATE_DEFER_ON  : ServerDTO.ServiceState.STATE_DEFER_OFF;
            }

            serverStates.add(new ServerDTO.ServiceState(serviceName, state));
        }

        return serverStates;
    }


    @Override
    protected Server getObject() {
        return this.server;
    }

    @Override
    protected SailPointContext getContext() {
        return context;
    }

    public static String PATCH_FIELD_UPDATES = "updates";
    public static String PATCH_FIELD_DELETIONS = "deletions";


    @Override
    protected List<String> getAllowedPatchFields() {
        List<String> fields = new ArrayList();
        fields.add(PATCH_FIELD_UPDATES);
        fields.add(PATCH_FIELD_DELETIONS);
        return fields;
    }

    public static final String OP_INCLUDE="include";
    public static final String OP_EXCLUDE ="exclude";
    public static final String OP_DEFER="defer";

    public static final String DELETEABLE_MON_CFG = "monitoringConfig";

    private static final List<String> DELETEABLES = new ArrayList<String>();
    private static final List<String> ALLOWED_OPS = new ArrayList<String>();

    static {
        ALLOWED_OPS.add(OP_INCLUDE);
        ALLOWED_OPS.add(OP_EXCLUDE);
        ALLOWED_OPS.add(OP_DEFER);

        DELETEABLES.add(DELETEABLE_MON_CFG );
    }


    @Override
    protected boolean validateValue(String field, Object value) {
        boolean isValid = true;

        // See the comments for HostsResource.patchServer for
        // explanation of patch parameters

        if (PATCH_FIELD_UPDATES.equals(field)) {
            if (value != null) {

                if (!(value instanceof ArrayList)) {
                    isValid=false;
                }
                else {
                    for(Object entry : (ArrayList)value) {
                        if (entry != null) {
                            if (!(entry instanceof Map)) {
                                isValid=false;
                            }
                            else {
                                Map update = (Map)entry;
                                if (!update.containsKey("service")) {
                                    isValid=false;
                                }
                                if (!update.containsKey("state")) {
                                    isValid=false;
                                }
                                else {
                                    String op = (String)update.get("state");
                                    if (Util.isNullOrEmpty(op)) {
                                        isValid=false;
                                    }
                                    else {
                                        if (!ALLOWED_OPS.contains(op.toLowerCase())) {
                                            isValid=false;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else if (PATCH_FIELD_DELETIONS.equals(field)) {
            if (value != null) {
                if (!(value instanceof String)) {
                    isValid = false;
                }
                else {
                    if (!DELETEABLES.contains((String)value)) {
                        isValid = false;
                    }
                }
            }
        }

        return isValid;
    }

    @Override
    protected void patchValue(String field, Object value) {
        if (PATCH_FIELD_UPDATES.equals(field)) {
            if (value== null) {
                // do nothing
                return;
            }
            else {
                // perform all of the updates in the list
                ArrayList updates = (ArrayList)value;
                for(Object update : updates) {
                    Map updateMap = (Map)update;
                    String service = (String)updateMap.get("service");
                    String op = (String)updateMap.get("state");
                    performServiceUpdate(service, op.toLowerCase());
                }
            }
        }
        else if (PATCH_FIELD_DELETIONS.equals(field)) {
            if (DELETEABLE_MON_CFG.equals(value)) {
                server.remove(Server.ATT_MONITORING_CFG);
            }
        }
    }

    /**
     * Perform the given update
     * @param service the name of the service that is to be
     * @param op allowed values are "on", "off", "defer"
     *           "on" means to add to includedServices and remove from excludedServices
     *           "off" means to remove from includedServices and add to excludedServices
     *           "defer" means to not override the service at all - remove from both lists
     */
    private void performServiceUpdate(String service, String op) {
        if (op.equals(OP_INCLUDE)) {
            removeServiceFromExcluded(service);
            addServiceToIncluded(service);
        }
        else if (op.equals(OP_EXCLUDE)) {
            addServiceToExcluded(service);
            removeServiceFromIncluded(service);
        }
        else if(op.equals(OP_DEFER)) {
            removeServiceFromExcluded(service);
            removeServiceFromIncluded(service);
        }
    }

    //
    // List utility methods
    //

    private void removeServiceFromExcluded(String service) {
        List<String> excludedServices = (List<String>)server.get(Server.ATT_EXCL_SERVICES);
        if (excludedServices != null) {
            excludedServices.remove(service);
        }
    }

    private void removeServiceFromIncluded(String service) {
        List<String> includedServices = (List<String>)server.get(Server.ATT_INCL_SERVICES);
        if (includedServices != null) {
            includedServices.remove(service);
        }
    }

    private void addServiceToExcluded(String service) {
        List<String> excludedServices = (List<String>)server.get(Server.ATT_EXCL_SERVICES);
        if (excludedServices == null) {
            excludedServices = new ArrayList<String>();
            server.put(Server.ATT_EXCL_SERVICES, excludedServices);
        }
        if (!excludedServices.contains(service)) {
            excludedServices.add(service);
        }
    }

    private void addServiceToIncluded(String service) {
        List<String> includedServices = (List<String>)server.get(Server.ATT_INCL_SERVICES);
        if (includedServices == null) {
            includedServices = new ArrayList<String>();
            server.put(Server.ATT_INCL_SERVICES, includedServices);
        }
        if (!includedServices.contains(service)) {
            includedServices.add(service);
        }
    }


    //
    // Methods for PUT
    //

    public ServerDTO updateServer(ServerDTO dto) throws GeneralException {
        validateDTO(dto);

        merge(dto, server);

        context.saveObject(server);
        context.commitTransaction();

        return getServerDTO(Boolean.FALSE);
    }

    private void merge(ServerDTO dto, Server server) {
        server.put(Server.ATT_EXCL_SERVICES, dto.getExcludedServices());
        server.put(Server.ATT_INCL_SERVICES, dto.getIncludedServices());

        mergeMonitoringConfig(dto, server);
    }

    private void mergeMonitoringConfig(ServerDTO dto, Server server) {

        if (dto.getPollingInterval() == null && dto.getRetention() == null && dto.getEnabledStatistics() == null
                && dto.getMonitoredApplications() == null) {
            server.remove(server.ATT_MONITORING_CFG);
            return;
        }

        // at least one of the config value has a non-null value

        Map monitoringConfig = (Map)server.get(Server.ATT_MONITORING_CFG);
        if (monitoringConfig == null) {
            monitoringConfig = new HashMap();
            server.put(Server.ATT_MONITORING_CFG, monitoringConfig);
        }

        if (dto.getPollingInterval() == null) {
            monitoringConfig.remove(Server.ATT_MONITORING_CFG_POLL_INTERVAL);
        }
        else {
            monitoringConfig.put(Server.ATT_MONITORING_CFG_POLL_INTERVAL, dto.getPollingInterval());
        }

        if (dto.getRetention() == null) {
            monitoringConfig.remove(Server.ATT_MONITORING_CFG_RETENTION);
        }
        else {
            monitoringConfig.put(Server.ATT_MONITORING_CFG_RETENTION, dto.getRetention());
        }

        if (dto.getEnabledStatistics() == null) {
            monitoringConfig.remove(Server.ATT_MONITORING_CFG_STATS);
        }
        else {
            monitoringConfig.put(Server.ATT_MONITORING_CFG_STATS, dto.getEnabledStatistics());
        }

        if (Util.isEmpty(dto.getMonitoredApplications())) {
            monitoringConfig.remove(Server.ATT_MONITORING_CFG_APPS);
        } else {

            // filter out duplicate applications
            Set<String> appSet = new HashSet<>();
            dto.getMonitoredApplications().forEach(d -> appSet.add(d.getName()));

            monitoringConfig.put(Server.ATT_MONITORING_CFG_APPS, new ArrayList<String>(appSet));
        }
    }

    private void validateDTO(ServerDTO dto) throws GeneralException {

        // name in DTO cannot be different than the name in Server we are updating
        if (!server.getName().equals(dto.getName())) {
            throw new GeneralException("Changing Server name is not supported.");
        }

        // a service present in excludedServices must not be present in includedServices
        if (dto.getExcludedServices() != null && dto.getIncludedServices() != null) {
            for (String excludedServiceName : dto.getExcludedServices()) {
                if (dto.getIncludedServices().contains(excludedServiceName)) {
                    throw new GeneralException("Service '" + excludedServiceName + "' is present in both excludedServices and includedServices");
                }
            }
        }
        validateMonitoringConfig(dto, server);
    }

    private void validateMonitoringConfig(ServerDTO dto, Server server) throws GeneralException {
        Integer dtoPollingInterval = dto.getPollingInterval();
        Integer dtoRetention = dto.getRetention();

        if (dtoPollingInterval != null) {
            if (dtoPollingInterval < 0) {
                throw new GeneralException("polling interval must be greater than or equal to 0");
            }
        }

        if (dtoRetention != null) {
            if (dtoRetention < 0) {
                throw new GeneralException("retention must be greater than or equal to 0");
            }
        }


    }

    private Integer getServerPollingInterval(Server svr) {
        Integer pollingInterval = null;
        Map monitoringConfig = (Map)svr.get(Server.ATT_MONITORING_CFG);
        if (monitoringConfig != null) {
            pollingInterval = (Integer)monitoringConfig.get(Server.ATT_MONITORING_CFG_POLL_INTERVAL);
        }
        return pollingInterval;
    }

    private Integer getServerRetention(Server svr) {
        Integer retention = null;
        Map monitoringConfig = (Map)svr.get(Server.ATT_MONITORING_CFG);
        if (monitoringConfig != null) {
            retention = (Integer)monitoringConfig.get(Server.ATT_MONITORING_CFG_RETENTION);
        }
        return retention;
    }

    private List<String> getServerEnabledStatistics(Server svr) {
        List<String> enabledStatistics = null;
        Map monitoringConfig = (Map)svr.get(Server.ATT_MONITORING_CFG);
        if (monitoringConfig != null) {
            enabledStatistics = (List<String>)monitoringConfig.get(Server.ATT_MONITORING_CFG_STATS);
        }
        return enabledStatistics;
    }

    //
    // Console methods
    //

    /**
     * This is expected to be used only by console
     * @param serviceName name of the service to be included/excluded/deferred on the host
     * @param newState "include", "exclude", or "defer"
     * @throws GeneralException
     */
    public void changeServiceState(String serviceName, String newState) throws GeneralException {
        Map<String, Object> patchCmd = new HashMap<String, Object>();
        patchCmd.put("service", serviceName);
        patchCmd.put("state", newState);
        ArrayList patchCmdList = new ArrayList();
        patchCmdList.add(patchCmd);

        patchValue(PATCH_FIELD_UPDATES, patchCmdList);

        context.saveObject(server);
        context.commitTransaction();
    }

}
