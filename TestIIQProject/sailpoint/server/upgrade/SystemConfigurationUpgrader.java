/*
 *  (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.server.upgrade;

import sailpoint.api.SailPointContext;
import sailpoint.api.passwordConstraints.PasswordConstraintHistory;
import sailpoint.object.Configuration;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.tools.GeneralException;

/**
 * Upgrade SystemConfiguration for items that can't be upgraded via upgradeObjects.xml
 */
public class SystemConfigurationUpgrader extends BaseUpgrader {

    SailPointContext _context;
    Configuration _config;
    boolean _needsUpdate;

    private int PASSWORD_HISTORY_MAX_DEFAULT = 20;

    @Override
    public void performUpgrade(Context context) throws GeneralException {

        _context = context.getContext();
        _config = _context.getObjectByName(Configuration.class, Configuration.OBJ_NAME);

        upgradePasswordHistoryLengthMaxValue();

        if (_needsUpdate) {
            _context.saveObject(_config);
            _context.commitTransaction();
        }
    }

    /**
     * Add the password history length max system config.
     * If password history length is not defined then just set the max to the default, 20.
     */
    public void upgradePasswordHistoryLengthMaxValue() {
        int historyLength = _config.getInt(PasswordConstraintHistory.HISTORY);
        int historyLengthMax = _config.getInt(PasswordConstraintHistory.HISTORY_MAX);

        // if there is already a system config value for passwordHistoryMax leave it alone.
        if (historyLengthMax > 0) {
            log("SystemConfiguration passwordHistoryMax value is already set.");
            return;
        }

        // if historyLength is not set then set max to default
        historyLengthMax = PASSWORD_HISTORY_MAX_DEFAULT;

        // if history length is set and its more than the default 20 use that value
        if (historyLength > PASSWORD_HISTORY_MAX_DEFAULT) {
            historyLengthMax = historyLength;
        }

        try {
            _config.put(PasswordConstraintHistory.HISTORY_MAX, historyLengthMax);
            _needsUpdate = true;
            log("Setting SystemConfiguration passwordHistoryMax value");
        } catch (Exception e) {
            log("Exception while setting SystemConfiguration passwordHistoryMax value." + e);
        }

    }

}
