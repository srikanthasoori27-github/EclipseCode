package sailpoint.web.accessrequest;

import sailpoint.api.SailPointContext;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningTarget;
import sailpoint.tools.GeneralException;

import java.util.Map;

/**
 * Class used by AccessRequest to represent a requested entitlement
 */
public class RequestedEntitlement extends RequestedAccessItem {

    /**
     * Constructor.
     * @param data Map of properties.
     * @throws GeneralException
     */
    public RequestedEntitlement(Map<String, Object> data) throws GeneralException {
        super(data);
    }

    @Override
    protected ProvisioningTarget initializeProvisioningTarget(SailPointContext context) throws GeneralException {
        ManagedAttribute entitlement = context.getObjectById(ManagedAttribute.class, getId());
        return new ProvisioningTarget(entitlement.getApplication(), entitlement.getAttribute(), entitlement.getValue());
    }
}