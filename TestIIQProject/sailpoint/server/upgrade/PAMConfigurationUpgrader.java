package sailpoint.server.upgrade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.UIConfig;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class PAMConfigurationUpgrader extends BaseUpgrader {

    private SailPointContext _context;

    private static final String PAM_APPLICATION_NAME = "pamApplicationName";
    private static final String PAM_ENABLED = "pamEnabled";
    private static final String pamUiContainerList = "pamUiContainerList";

    @Override
    public void performUpgrade(Context context) throws GeneralException {

        _context = context.getContext();
        Configuration config = _context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);

        //(do we need to execute this upgrader only if "pamEnabled=true" ? )
        //after some thought, it makes more sense to execute this upgrader
        //always that PAM is already setup.

        //do not execute this upgrader in case PAM is not setup
        if (config.containsAttribute(PAM_ENABLED)) {
            this.upgradeSystemConfiguration();
            this.upgradeUIConfiguration();
        } else {
            info("PAM config was not found. Nothing to do here.");
        }
    }

    /**
     * Convenience method to remove pamApplicationName property
    * @throws GeneralException
    */
    private void upgradeSystemConfiguration() throws GeneralException {
       try {

           // Fetching Configuration
           Configuration config = _context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
           if (config == null) {
               warn(Configuration.OBJ_NAME + " not found. SystemConfiguration was not updated.");
               return;
           }

           if (config.containsAttribute(PAM_APPLICATION_NAME)) {

               // need to remove the old entry
               config.remove(PAM_APPLICATION_NAME);

               // We have to save the changes made to the config.
               _context.saveObject(config);
               _context.commitTransaction();
               _context.decache();

               info("The property [" + PAM_APPLICATION_NAME + "] has been removed.");
           } else {
               info("There is no need to update the SystemConfiguration.");
           }

       } catch (Exception e) {
           log("Exception while updating Configuration " + e);
       }
   }

    /**
     * Method to modify column display order for column configs
     * @throws GeneralException
     */
   private void upgradeUIConfiguration() throws GeneralException {
       try {
           SailPointContext spContext = _context.getContext();
           UIConfig uiConfig = spContext.getObjectByName(UIConfig.class, UIConfig.OBJ_NAME);

           //the upgrader is taken a lot of time if it is ran multiple times,
           //we just need to commit data in case there was a change
           boolean save = false;
           if (uiConfig != null && !Util.isEmpty(uiConfig.getAttributes())) {
               List<ColumnConfig> containerListConfig = uiConfig.getAttributes().getList(pamUiContainerList);

               if (Util.isEmpty(containerListConfig)) {
                   //there is a validation on performUpgrade method that will avoid
                   //to hit this area, however it is safer to validate it.
                   warn(pamUiContainerList + " not found. UIConfig was not updated.");
                   return;
               }

               //headerKey with changes
               Map<String, String> map = new HashMap<>();
               map.put("ui_pam_container_list_name", "label_name");
               map.put("ui_pam_container_list_identityTotalCount", "ui_pam_containers_identities_total");
               map.put("ui_pam_container_list_privilegedItemCount", "ui_pam_containers_privileged_items");
               map.put("ui_pam_container_list_groupCount", "ui_pam_containers_groups");

               ColumnConfig applicationNameConfig = null;
               ColumnConfig owner = null;
               int applicationNameIndex = 0;
               boolean updateApplicationName = false;

               //if customer has no change the headerKey then let's change the defaults for the new ones
               for (ColumnConfig columnConfig : Util.safeIterable(containerListConfig)) {
                   if (map.containsKey(columnConfig.getHeaderKey())) {
                       info("HeaderKey \"" + columnConfig.getHeaderKey() + "\" has been updated to \"" +
                               map.get(columnConfig.getHeaderKey()) + "\".");
                       columnConfig.setHeaderKey(map.get(columnConfig.getHeaderKey()));
                       save = true;
                   } else if ("applicationName".equals(columnConfig.getProperty())) {
                       applicationNameConfig = columnConfig;
                       updateApplicationName = applicationNameIndex == 0 ? false : true;
                   } else if ("owner.displayName".equals(columnConfig.getProperty())) {
                       owner = columnConfig;
                   }
                   applicationNameIndex++;
               }

               //We want to make sure the application name is always at the top of the config.
               if (applicationNameConfig != null) {
                   if (updateApplicationName) {
                       containerListConfig.remove(applicationNameConfig);
                       containerListConfig.add(0, applicationNameConfig);
                       info("\"applicationName\" column has been moved to the top of " + pamUiContainerList + ".");
                       save = true;
                   }
               } else {
                   // this should never happen but just in case... add the missing application name config
                   applicationNameConfig = new ColumnConfig();
                   applicationNameConfig.setHeaderKey("ui_pam_application_name");
                   applicationNameConfig.setProperty("applicationName");
                   applicationNameConfig.setDataIndex("applicationName");
                   applicationNameConfig.setHideable(true);
                   containerListConfig.add(0, applicationNameConfig);
                   info("\"applicationName\" column has been added to the top of " + pamUiContainerList + ".");
                   save = true;
               }

               //adding owner to the end of the list.
               if (owner == null) {
                   owner = new ColumnConfig();
                   owner.setHeaderKey("ui_pam_containers_owner");
                   owner.setProperty("owner.displayName");
                   owner.setDataIndex("owner.displayName");
                   owner.setHideable(true);
                   containerListConfig.add(owner);
                   info("\"owner.displayName\" column has been added to " + pamUiContainerList + ".");
                   save = true;
               }
           }

           if (save) {
               _context.saveObject(uiConfig);
               _context.commitTransaction();
               _context.decache();
           }
       } catch (Exception e) {
           log("Exception while updating " + pamUiContainerList + " "+ e);
       }
   }
}