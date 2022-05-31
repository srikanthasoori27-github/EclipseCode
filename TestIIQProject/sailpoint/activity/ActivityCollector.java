/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.activity;

import java.util.List;
import java.util.Map;

import openconnector.ConnectorServices;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;

/**
 * The ActivityCollector interface.
 */
public interface ActivityCollector {

    ///////////////////////////////////////////////////////////////////////////
    //
    // 
    //
    ///////////////////////////////////////////////////////////////////////////

    public final String CONFIG_USER_ATTRIBUTE = "userFieldAttributeName";

    public final String CONFIG_FILTERS = "filters";

    public final String CONFIG_ALLOW_ALL = "allowAll";

    /**
     * Test the configuration to the activity information.
     * Throw and exception ( as detailed as possible ) when there is a problem
     * during the test;
     * @param newParam TODO
     */
    public void testConfiguration(Map<String, Object> options) 
        throws GeneralException;

    /**
     * Returns an iterator over ApplicationActivity objects.
     * @param datasource Datasource where data will be read from
     * @param options Map of options
     * 
     * TODO: List out the known options 
     */
    public CloseableIterator<ApplicationActivity> iterate(Map<String, Object> options)
        throws GeneralException;

    /**
     * Returns a list of the attributes definitions that makeup the 
     * the settings that are neccessary for the setup of this 
     * ActivityCollector.
     */
    public List<AttributeDefinition> getDefaultConfiguration();

    /**
     * Set the application associated with this object.
     * <p>
     * This method is implemented by AbstractActivityCollector
     * </p>
     * @param datasource The ActivityDataSoruce used by this collector
     */
    public void setDataSource(ActivityDataSource datasource);

    /**
     * Returns the ActivityDataSource associated with this object.
     */
    public ActivityDataSource getDataSource();

    /**
     * Returns the attributes that make up the collectors configuration.
     */
    public Attributes<String,Object> getAttributes();

    /**
     * Return a Map that contains information that can be handed back
     * to a collector so it can be instructed where to start within   
     * a dataset.  
     */
    public Map<String,Object> getPositionConfig();

    public void setPositionConfig(Map<String,Object> config);
    
    /**
     * Cleans the unused attributes out of the config that is passed in
     * @param config Attributes object that needs to be cleaned up
     */
    public void cleanUpConfig(Attributes<String,Object> config);

    /**
     * Sets the ConnectorServices object.
     */
    void setConnectorServices(ConnectorServices conServices);

    /**
     * @return the ConnectorServices object.
     */
    ConnectorServices getConnectorServices();
}
