package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityFilter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QuickLink;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;

import java.util.Locale;

/**
 * Implementation of BaseUserAccessFilterContext for population based searching
 */
public class UserAccessPopulationFilterContext extends BaseUserAccessFilterContext {

    public static final String FILTER_PROPERTY_MANAGER = "manager";
    
    public UserAccessPopulationFilterContext() {
        // Null for ObjectTypes, requester and target will make this always enabled, just as we hoped.
        super(ObjectConfig.getObjectConfig(Identity.class), UIConfig.LCM_SEARCH_IDENTITY_ATTRIBUTES, null, null, null);
    }

    public Filter convertFilter(String propertyName, ListFilterValue filterValue, SailPointContext context) throws GeneralException {
        Filter filter = super.convertFilter(propertyName, filterValue, context);
        // For Identity searches, we will get the Identity join filter so need
        // to match it to an ObjectAttribute and fudge a ListFilterDTO for it.
        if (filter == null && propertyName.startsWith(IDENTITY_ATTR_PREFIX)) {
            ListFilterDTO filterDTO = getFilterDTO(propertyName.substring(IDENTITY_ATTR_PREFIX.length()));
            if (filterDTO != null) {
                // Put the Identity prefix back on
                filterDTO.setProperty(propertyName);
                filter = convertFilter(filterDTO, filterValue, context);
            }
        }
        
        return filter;
    }
    
    public ListFilterDTO createListFilterDTO(ObjectAttribute attribute, Locale locale) {
        ListFilterDTO listFilterDTO = super.createListFilterDTO(attribute, locale);
        if (ListFilterDTO.DataTypes.Identity.equals(listFilterDTO.getDataType())) {
            String context = FILTER_PROPERTY_MANAGER.equals(listFilterDTO.getProperty()) ?
                    IdentityFilter.CONTEXT_LCM_POPULATION_MANAGER : IdentityFilter.CONTEXT_LCM_POPULATION;
            listFilterDTO.configureSuggest(context, "suggest_" + listFilterDTO.getProperty(), getSuggestUrl());
        } else if (ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
            listFilterDTO.configureLcmColumnSuggest(Identity.class.getSimpleName(), attribute.getName(),
                    QuickLink.LCM_ACTION_REQUEST_ACCESS, getQuickLink(), getSuggestUrl());
        }
        return listFilterDTO;
    }
}