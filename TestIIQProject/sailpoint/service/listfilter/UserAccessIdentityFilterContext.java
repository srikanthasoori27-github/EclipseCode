package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.IdentityFilter;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of BaseUserAccessFilterContext for Identity based searching
 */
public class UserAccessIdentityFilterContext extends BaseUserAccessFilterContext {
    
    public UserAccessIdentityFilterContext() {
        // Null for everything, we will handle things ourselves.
        super(null, null, null, null, null);
    }
    
    @Override
    public Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        Filter filter;
        if (filterDTO != null && PROPERTY_IDENTITY_IDS.equals(filterDTO.getProperty())) {
            List<String> identityIds = getValueStringList(filterValue);
            filter = Filter.in("id", identityIds);
        } else {
            filter = super.convertFilter(filterDTO, filterValue, context);
        }

        return filter;
    }

    @Override
    public Filter convertFilter(String propertyName, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        Filter filter = super.convertFilter(propertyName, filterValue, context);
        if (filter == null && (IDENTITY_ATTR_PREFIX + PROPERTY_IDENTITY_IDS).equals(propertyName)) {
            filter = Filter.in("Identity.id", getValueStringList(filterValue));
        }
        return filter;        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOs = new ArrayList<ListFilterDTO>();
        ListFilterDTO identityFilter = new ListFilterDTO(PROPERTY_IDENTITY_IDS, null, true, ListFilterDTO.DataTypes.Identity, locale);
        identityFilter.configureSuggest(IdentityFilter.CONTEXT_LCM_POPULATION, "populationSearchGridExpandoIdentityMultiSuggest", getSuggestUrl());
        filterDTOs.add(identityFilter);

        return filterDTOs;
    }
}