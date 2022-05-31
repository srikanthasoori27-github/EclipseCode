/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Optional interface Connector writers may implement if they
 * want to generate composite accounts from existing accounts
 * rather than actually bring in accounts from a resource.
 * A somewhat experimental feature added for JPMC.  
 *
 * It is too bad that composite connectors have to implement
 * the entire Connector interface, but I don't see a good way
 * around that without complicating the Application UI.
 * 
 * Author: Jeff
 *
 */

package sailpoint.connector;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.tools.GeneralException;

public interface LogicalConnector {

    /**
     * Look at an Identity and return a list of Links
     * representing composite accounts that can be derived
     * from the existing account links on an identity.
     *
     * Hmm, I don't like passing a SailPointContext in here
     * but we need it to resolve rules.  Debating on whether to let
     * these use SailPointFactory so we can keep the interface clean.
     */
    public List<Link> getCompositeAccounts(SailPointContext context,
                                           Identity id)
        throws GeneralException, ConnectorException;


    /**
     * Convert an abstract provisioning plan against the composite
     * application into a concrete plan targeting the component applications.
     */
    public ProvisioningPlan compileProvisioningPlan(SailPointContext context,
                                                    Identity id,
                                                    ProvisioningPlan src)
        throws GeneralException;
    
}
