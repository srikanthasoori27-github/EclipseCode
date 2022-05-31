/*
 *  (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.server.upgrade;

import java.util.List;
import java.util.Map;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Module;
import sailpoint.object.Server;
import sailpoint.object.Capability;
import sailpoint.object.RecommenderDefinition;
import sailpoint.object.ServiceDefinition;
import sailpoint.server.ImportExecutor;
import sailpoint.server.MonitoringService;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
    This upgrader will do the following:
        upgrade RecommenderDefinition name
        upgrade Module name
        upgrade SystemConfiguration recommenderSelected entry value
        upgrade WorkGroup name
        upgrade Capability name
        upgrade ServiceDefinition name
 */
public class AIServicesUpgrader extends BaseUpgrader {

    private SailPointContext _context;
    private static final String _AIServices = "AIServices";
    private static final String _IdentityAI = "IdentityAI";

    public void performUpgrade(ImportExecutor.Context context) throws GeneralException {
        _context = context.getContext();

        this.upgradeRecommenderDefinition();
        this.upgradeModule();
        this.upgradeSystemConfiguration();
        this.upgradeWorkGroup();
        this.upgradeCapability();
        this.upgradeServiceDefinition();
    }

    /**
     * Convenience method to change the RecommenderDefinition name
     * It will change the name from IdentityAI to AIServices
     * @throws GeneralException
     */
    private void upgradeRecommenderDefinition() throws GeneralException {
        try {
            // Fetching RecommenderDefinition
            RecommenderDefinition recDef = _context.getObjectByName(RecommenderDefinition.class, _IdentityAI);
            RecommenderDefinition newRecDef = _context.getObjectByName(RecommenderDefinition.class, _AIServices);

            // validate if we have two objects, one with name AIServices and the other with name IdentityAI
            // if this is true, it means customer executed import init-ai.xml before executing patch 8.1p1
            if (recDef != null && newRecDef != null) {
                // Do nothing here, just warn the user about the existence of both objects.
                warn("Could not rename '" + _IdentityAI + "' RecommenderDefinition to '" + _AIServices + "', object already exists. '" + _IdentityAI + "' is no longer in use.");

                return;
            }

            // do nothing in case we don't have a RecommenderDefinition
            if (recDef != null) {
                recDef.setName(_AIServices);
                recDef.setDescription("AI Services Recommender");
                info("Updating RecommenderDefinition Name to '" + _AIServices + "'");

                _context.saveObject(recDef);
                _context.commitTransaction();
                _context.decache();
            }
        } catch (Exception e) {
            log("Exception while updating RecommenderDefinition " + e);
        }
    }

    /**
     * Convenience method to change the Module name
     * It will change the name from IdentityAI to AIServices
     * @throws GeneralException
     */
    private void upgradeModule() throws GeneralException {
        try {
            // Fetching Module
            Module module = _context.getObjectByName(Module.class, _IdentityAI);
            Module newModule = _context.getObjectByName(Module.class, _AIServices);

            // validate if we have two objects, one with name AIServices and the other with name IdentityAI
            // if this is true, it means customer executed import init-ai.xml before executing patch 8.1p1.
            if (module != null && newModule != null) {
                // Do nothing here, just warn the user about the existence of both objects.
                warn("Could not rename '" + _IdentityAI + "' module to '" + _AIServices + "', object already exists. '" + _IdentityAI + "' is no longer in use.");

                return;
            }

            // do nothing in case we don't have a Module
            if (module != null) {
                module.setName(_AIServices);
                info("Updating Module Name to '" + _AIServices + "'");

                _context.saveObject(module);
                _context.commitTransaction();
                _context.decache();
            }
        } catch (Exception e) {
            log("Exception while updating Module " + e);
        }
    }

    /**
     * Convenience method to change the RECOMMENDER_SELECTED value
     * It will change the name from IdentityAI to AIServices
    * @throws GeneralException
    */
    private void upgradeSystemConfiguration() throws GeneralException {
       try {
           // Fetching Configuration
           Configuration config = _context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);

           // do nothing in case we don't have a Configuration or there is no RECOMMENDER_SELECTED attribute
           if (config != null && Util.isNotNullOrEmpty(config.getString(Configuration.RECOMMENDER_SELECTED))) {
               if (config.getString(Configuration.RECOMMENDER_SELECTED).equals(_IdentityAI)) {
                   config.put(Configuration.RECOMMENDER_SELECTED, _AIServices);
                   info("Updating Configuration entry 'recommenderSelected' to '" + _AIServices + "'" );

                   _context.saveObject(config);
                   _context.commitTransaction();
                   _context.decache();
               }
           }
       } catch (Exception e) {
           log("Exception while updating Configuration " + e);
       }
   }


    /**
     * Convenience method to change the WorkGroup name
     * It will change the name from IdentityAI to AIServices
     * @throws GeneralException
     */
    private void upgradeWorkGroup() throws GeneralException {
        try {
            // Fetching WorkGroup "IdentityAI"
            Identity workGroup = _context.getObjectByName(Identity.class, _IdentityAI);
            Identity newWorkGroup = _context.getObjectByName(Identity.class, _AIServices);

            // validate if we have two objects, one with name AIServices and the other with name IdentityAI
            // if this is true, it means customer executed import init-ai.xml before executing patch 8.1p1.
            if (workGroup != null && newWorkGroup != null) {
                // Do nothing here, just warn the user about the existence of both objects.
                warn("Could not rename '" + _IdentityAI + "' workGroup to '" + _AIServices + "', object already exists. '" + _IdentityAI + "' is no longer in use.");

                return;
            }

            // do nothing in case there is no such identity
            if (workGroup != null) {
                workGroup.setName(_AIServices);
                workGroup.setAttribute("displayName", "AI Services");
                info("Updating WorkGroup name to '" + _AIServices + "'");

                _context.saveObject(workGroup);
                _context.commitTransaction();
                _context.decache();
            }
        } catch (Exception e) {
            log("Exception while updating WorkGroup " + e);
        }
    }

    /**
     * Convenience method to change the Capability name
     * It will change the name from IdentityAIAdministrator to AIServicesAdministrator
    * @throws GeneralException
    */
    private void upgradeCapability() throws GeneralException {
       try {
           // Fetching the Monitoring service
           Capability capability = _context.getObjectByName(Capability.class, "IdentityAIAdministrator");
           Capability newCapability = _context.getObjectByName(Capability.class, "AIServicesAdministrator");

           // validate if we have two objects, one with name AIServices and the other with name IdentityAI
           // if this is true, it means customer executed import init-ai.xml before executing patch 8.1p1.
           if (capability != null && newCapability != null) {
               // Do nothing here, just warn the user about the existence of both objects.
               warn("Could not rename 'IdentityAIAdministrator' capability to 'AIServicesAdministrator', object already exists. 'IdentityAIAdministrator' is no longer in use.");

               return;
           }

           // do nothing is case there is no Monitoring service
           if (capability != null) {
               capability.setName("AIServicesAdministrator");
               info("Updating Capability name to AIServicesAdministrator");

               _context.saveObject(capability);
               _context.commitTransaction();
               _context.decache();
           }
       } catch (Exception e) {
           log("Exception while updating Capability " + e);
       }
   }

    /**
     * Convenience method to change the ServiceDefinition name
     * It will change the name from IdentityAI to AIServices
     * @throws GeneralException
     */
    private void upgradeServiceDefinition() throws GeneralException {
        try {
            //Fetching the Monitoring service
            ServiceDefinition service = _context.getObjectByName(ServiceDefinition.class, MonitoringService.NAME);

            //do nothing is case there is no Monitoring service
            if (service != null) {
                Map monitoringConfig = (Map)service.get(Server.ATT_MONITORING_CFG);
                if (monitoringConfig == null) {
                    //do nothing in case monitoringConfig is not present
                    return;
                }

                List<String> monitoredModules = Util.getStringList(monitoringConfig, Server.ATT_MONITORING_CFG_MODULES);
                if (monitoredModules == null) {
                    //do nothing in case monitoredModules have not values in the list
                    return;
                }

                //we need to add the new service name
                String moduleToAdd = _AIServices;

                //we need to remove the previous service name
                String moduleToRemove = _IdentityAI;

                if (monitoredModules.contains(moduleToRemove)) {
                    info("Removing '" + moduleToRemove + "' for monitoring");
                    monitoredModules.remove(moduleToRemove);

                    //This validation will help to avoid to add duplicate module monitoring
                    //This could happen in case import init-ai.xml was run first or
                    //the service was added manually?
                    if (!monitoredModules.contains(moduleToAdd)) {
                        info("Registering '" + moduleToAdd + "' for monitoring");
                        monitoredModules.add(moduleToAdd);
                    }

                    monitoringConfig.put(Server.ATT_MONITORING_CFG_MODULES, monitoredModules);
                    _context.saveObject(service);
                    _context.commitTransaction();
                    _context.decache();
                }
            }
        } catch (Exception e) {
            log("Exception while updating ServiceDefinition." + e);
        }
    }
}