package sailpoint.object;

import java.util.List;
import java.util.Locale;

import sailpoint.tools.GeneralException;


/**
 * This interface is implemented by SailPointObjects that contain information that represents entitlements.
 * @author Bernie Margolis
 */
public interface EntitlementDataSource {
    List<Entitlement> getEntitlements(Locale locale, String attributeOrPermissionFilter) throws GeneralException;
}
