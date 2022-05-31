package sailpoint.web.mining;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.object.Filter;
import sailpoint.role.MiningService;
import sailpoint.web.util.WebUtil;

public class ItRoleMiningTemplate {
    private String ownerName;
    public static enum FilterType {
        ATTRIBUTE,
        POPULATION
    }
    
    /* Boring accessors and mutators */
    public String getId() { return getNonNullString( id ); }
    public void setId( String id ) { this.id = id; }
    
    public FilterType getFilterType() {
        if( filterType == null ) {
            filterType = FilterType.ATTRIBUTE;
        }
        return filterType; 
    }
    public void setFilterType( FilterType filterType ) { this.filterType = filterType; }
    
    public int getMaximumRoles() { 
        if( maxRoles == null )
            maxRoles = DEFAULT_MAX_ROLES;
        return maxRoles; 
    }
    public void setMaximumRoles( int maximumRoles ) { this.maxRoles = maximumRoles; }

    public int getMinimumEntitlementsPerRole() { return minEntitlementsPerRole; }
    public void setMinimumEntitlementsPerRole( int minimumEntitlementsPerRole ) { this.minEntitlementsPerRole = minimumEntitlementsPerRole; }
    
    public int getMinimumIdentitiesPerRole() { return minIdentitiesPerRole; }
    public void setMinimumIdentitiesPerRole( int minimumIdentitiesPerRole ) { this.minIdentitiesPerRole = minimumIdentitiesPerRole; }
    
    public String getName() { return getNonNullString( name ); }

    public void setName( String name ) {
        //IIQTC-90 :-XSS vulnerability when adding a name
        this.name = WebUtil.escapeHTML(name, false);
    }
    
    public String getPopulationName() { return getNonNullString( populationName ); }
    public void setPopulationName( String populationName ) { this.populationName = populationName;};
    
    /* Lazy instantiation of entitlement list */
    public Set<ITRoleMiningEntitlement> getEntitlements() {
        if( entitlements == null ) {
            entitlements = new HashSet<ITRoleMiningEntitlement>(); 
        }
        return entitlements;
    }
    public void setEntitlements( Set<ITRoleMiningEntitlement> entitlements ) { this.entitlements = entitlements; }

    public String getOwnerId() { return getNonNullString( ownerId ); }
    public void setOwnerId( String ownerId ) { this.ownerId = ownerId; }
    
    public String getOwnerName() { return getNonNullString( ownerName ); }
    public void setOwnerName( String ownerName ) { this.ownerName = ownerName;}

    public List<String> getApplicationIds() {
        if( applicationIds == null ) {
            applicationIds = new LinkedList<String>();
        }
        return applicationIds;
    }
    
    public List<Filter> getIdentityFilters() {
        if( identityFilters == null ) {
            identityFilters = new LinkedList<Filter>();
        }
        return identityFilters;
    }
    
    /**
     * This method grabs the filter values out of the identity filters that were specified in the 
     * template.  This is necessary to recreate the UI from a persisted template
     * @return Map<String, String> attribute values for the identity filter
     */
    public Map<String, String> getAttributeValuesForIdentityFilters() {
        Map<String, String> valuesForIdentityFilters;
        
        // If this isn't an attribute filter, there are no attribute values to get
        if (getFilterType() == ItRoleMiningTemplate.FilterType.ATTRIBUTE && identityFilters != null && !identityFilters.isEmpty()) {
            valuesForIdentityFilters = MiningService.convertIdentityFiltersToAttributes(identityFilters);
        } else {
            valuesForIdentityFilters = new MiningService.SafeMap();
        }
        
        return valuesForIdentityFilters;
    }

    public void setIdentityFilters( List<Filter> filters ) {
        this.identityFilters = filters;
    }
    
    public Set<ITRoleMiningEntitlement> getExcludedEntitlementsForApplication( String applicationId ) {
        Set<ITRoleMiningEntitlement> response = new HashSet<ITRoleMiningEntitlement>();
        Set<ITRoleMiningEntitlement> excludedEntitlements = getEntitlements();
        response.addAll( excludedEntitlements );
        return response;
    }

    public boolean addApplication( String applicationId ) {
        boolean response = false;
        List<String> applicationIds = getApplicationIds();
        if( !applicationIds.contains( applicationId ) ) {
            response = applicationIds.add( applicationId );
        }
        return response;
    }

    public void removeApplication( String applicationId ) {
        boolean removed = applicationIds.remove( applicationId );
        /* Remove identity items for application we just removed */
        if( removed ) {
            LinkedList<ITRoleMiningEntitlement> removableItems = new LinkedList<ITRoleMiningEntitlement>();
            for( ITRoleMiningEntitlement item : entitlements ) {
                if( item.getApplication().equals( applicationId ) ) {
                    removableItems.add( item );
                }
            }
            entitlements.removeAll( removableItems );
        }
    }
    
    public void addEntitlement( ITRoleMiningEntitlement attributeItem ) {
        getEntitlements().add( attributeItem );
    }

    public void addEntitlements( Collection<ITRoleMiningEntitlement> entitlements) {
        getEntitlements().addAll(entitlements);
    }
    
    /* Protected methods */
    protected void setApplicationIds( List<String> applicationIds ) {
        if( applicationIds != null ) {
            this.applicationIds = applicationIds;
        }
    }
    
    /* Properties */
    private String id;
    private FilterType filterType;
    private Integer maxRoles;
    private int minEntitlementsPerRole;
    private int minIdentitiesPerRole;
    private String name;
    private String ownerId;
    private String populationName;
    private Set<ITRoleMiningEntitlement> entitlements;
    private List<String> applicationIds;
    private List<Filter> identityFilters;
    /* Private Methods */
    private static final String getNonNullString( String s ) {
        return ( s == null ? "" : s );
    }
    /* Private Constants */
    private static final Integer DEFAULT_MAX_ROLES = Integer.valueOf( 1000 );
}
