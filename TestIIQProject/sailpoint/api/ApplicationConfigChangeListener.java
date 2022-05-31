package sailpoint.api;

import sailpoint.tools.GeneralException;

/**
 * ApplicationConfigChangeListener interface is to be implemented by 
 * classes that are interested in listening to app config changes
 * and update the app into the database
 */
public interface ApplicationConfigChangeListener {
    /**
     * Update app config via listener
     * @throws GeneralException 
     */
    public void updateAppConfig() throws GeneralException;
}
