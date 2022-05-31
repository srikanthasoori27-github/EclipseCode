/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connector;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Schema;
import sailpoint.object.Application.Feature;

/**
 * 
 */
abstract public class AbstractLogicalConnector
    extends AbstractConnector implements LogicalConnector {


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    private static Log log = LogFactory.getLog(AbstractLogicalConnector.class);

   
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public AbstractLogicalConnector(Application application) {
        super(application);
    }
 
    //////////////////////////////////////////////////////////////////////
    //
    // Connector
    //
    //////////////////////////////////////////////////////////////////////

    abstract public String getConnectorType();

    /**
     * There are no default schemas, they must be fully defined.
     */
    abstract public List<Schema> getDefaultSchemas();
    /**
     * This method should return a List of AttributeDefinitions that describe 
     * the configuration attributes that can be used to configure the behavior 
     * of a connector.
     */
    abstract public List<AttributeDefinition> getDefaultAttributes();

    /**
     * This method should return a List of Feature object that describe the 
     * optional Features that this connector may publish.
     */
    abstract public List<Feature> getSupportedFeatures();
    

    //////////////////////////////////////////////////////////////////////
    //
    // LogicalConnector
    //
    //////////////////////////////////////////////////////////////////////
}
