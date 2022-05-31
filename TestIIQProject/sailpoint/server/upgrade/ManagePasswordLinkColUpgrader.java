/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server.upgrade;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.ColumnConfig;
import sailpoint.object.UIConfig;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * The purpose of this upgrader is to add the passwordChangeDate ColumnConfig to managePasswordLinkColConfig
 * and remove the lastRefresh ColumnConfig for managePasswordLinkColConfig
 */
public class ManagePasswordLinkColUpgrader extends BaseUpgrader {

    private static final String uiConfigEntry = "managePasswordLinkColConfig";

    @Override
    public void performUpgrade(Context context) throws GeneralException {
        SailPointContext spContext = context.getContext();

        UIConfig uiConfig = spContext.getObjectByName(UIConfig.class, UIConfig.OBJ_NAME);
        if (uiConfig != null && !Util.isEmpty(uiConfig.getAttributes())) {
            @SuppressWarnings("unchecked")
            List<ColumnConfig> configs = uiConfig.getAttributes().getList(uiConfigEntry);
            if (Util.size(configs) > 0) {
                upgradeColumnConfigs(configs);
                spContext.saveObject(uiConfig);
                spContext.commitTransaction();
            } else {
                // It's a problem if there is no legit managePasswordLinkColConfig
                warn("No configuration for " + uiConfigEntry + ". Exiting Upgrader.");
            }
        } else {
            warn("UIConfig Configuration object not found. Exiting Upgrader.");
        }
    }

    private void upgradeColumnConfigs(List<ColumnConfig> colConfigs) {
        // index of where the status column is, assume -1 to be not found
        int statusIndex = -1;
        boolean colAlreadyExists = false;

        ColumnConfig neuColCfg = new ColumnConfig();
        neuColCfg.setDataIndex("passwordChangeDate");
        neuColCfg.setRenderer("spLinkPasswordChangeDate");
        neuColCfg.setHeaderKey("ui_manage_passwords_password_change_date");
        neuColCfg.setDateStyle("short");
        neuColCfg.setSortable(true);
        neuColCfg.setStateId("passwordChangeDate");

        // Iterating through the ColumnConfigs for managePasswordLinkColConfig
        for (int i = 0; i < colConfigs.size(); i++) {
            ColumnConfig colCfg = colConfigs.get(i);
            // I don't see how this could happen, but to prevent an NPE...
            if (colCfg == null) {
                continue;
            }

            // Checking to see if passwordChangeDate ColumnConfig already exists
            if (neuColCfg.getDataIndex().equals(colCfg.getDataIndex())) {
                colAlreadyExists = true;
                boolean updated = false;

                if (!neuColCfg.getRenderer().equals(colCfg.getRenderer())) {
                    colCfg.setRenderer(neuColCfg.getRenderer());
                    info("Password Request Date ColumnConfig renderer updated in UIConfig entry: {0}", uiConfigEntry);
                    updated = true;
                }
                if (!neuColCfg.getHeaderKey().equals(colCfg.getHeaderKey())) {
                    colCfg.setHeaderKey(neuColCfg.getHeaderKey());
                    info("Password Request Date ColumnConfig headerKey updated in UIConfig entry: {0}", uiConfigEntry);
                    updated = true;
                }
                if (!neuColCfg.getDateStyle().equals(colCfg.getDateStyle())) {
                    colCfg.setDateStyle(neuColCfg.getDateStyle());
                    info("Password Request Date ColumnConfig dateStyle updated in UIConfig entry: {0}", uiConfigEntry);
                    updated = true;
                }
                if (neuColCfg.isSortable() != colCfg.isSortable()) {
                    colCfg.setSortable(neuColCfg.isSortable());
                    info("Password Request Date ColumnConfig sortable updated in UIConfig entry: {0}", uiConfigEntry);
                    updated = true;
                }
                if (!neuColCfg.getStateId().equals(colCfg.getStateId())) {
                    colCfg.setStateId(neuColCfg.getStateId());
                    info("Password Request Date ColumnConfig stateId updated in UIConfig entry: {0}", uiConfigEntry);
                    updated = true;
                }

                if (!updated) {
                    info("Password Request Date ColumnConfig already found in UIConfig entry: {0}", uiConfigEntry);
                }
            } else if ("status".equals(colCfg.getDataIndex())) {
                // Found our "status" ColumnConfig, after which the new ColumnConfig will be inserted,
                statusIndex = i;
            } else if ("lastRefresh".equals(colCfg.getDataIndex())) {
                // remove lastRefresh ColumnConfig - it makes no sense for Manage Passwords
                colConfigs.remove(colCfg);
            }
        }

        // If passwordChangeDate ColumnConfig doesn't already exist, add it.
        if (!colAlreadyExists) {
            // We have a status column at index statusIndex
            if (statusIndex > -1) {
                // Insert the passwordChangeDate column right after the status column
                colConfigs.add(statusIndex + 1, neuColCfg);
                info(uiConfigEntry + " entry in UIConfig has been upgraded to include" + 
                        " the ColumnConfig for the password request date.");
            } else {
                // Add passwordChangeDate to the end. It can always be adjusted manually in UIConfig.
                colConfigs.add(neuColCfg);

                info("status ColumnConfig for managePasswordLinkColConfig does not exist - "
                        + "inserting passwordChangeDate ColumnConfig at the end of the UIConfig entry");
            }
        }
    }
}
