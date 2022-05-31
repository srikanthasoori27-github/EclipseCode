package sailpoint.web.group;

import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Identity.CapabilityManager;

public final class PopulationFilterUtil {
    /**
     * A method to encapsulate the slightly bizarre population owner filters
     * @param qo The QueryOptions to add the filtering to
     * @param owningUser The owning Identity
     */
    public static void addPopulationOwnerFiltersToQueryOption( QueryOptions qo, Identity owningUser ) {
        qo.add( FACTORY_FILTER );
        CapabilityManager ownerCapabilityManager = owningUser.getCapabilityManager();
        if( !ownerCapabilityManager.hasCapability( Capability.SYSTEM_ADMINISTRATOR ) ) {
            addNonAdminOwnerFiltersToQueryOption( qo, owningUser );
        }  
    }

    private static void addNonAdminOwnerFiltersToQueryOption( QueryOptions qo, Identity owningUser ) {
        Filter ownerScopeFilter = QueryOptions.getOwnerScopeFilter( owningUser, "owner" );
        qo.add( Filter.or( ownerScopeFilter, PRIVATE_FILTER ) );
        qo.addOwnerScope( owningUser );
    }
    
    private static final Filter PRIVATE_FILTER = Filter.eq( "private", false );
    private static final Filter FACTORY_FILTER = Filter.isnull( "factory" );
}
