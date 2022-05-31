package sailpoint.service.listfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QuickLink;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.UIConfig;
import sailpoint.service.listfilter.ListFilterDTO.SelectItem;
import sailpoint.service.useraccess.UserAccessSearchOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleConfig;

/**
 * Implementation of BaseUserAccessFilterContext for Role searching
 */
public class UserAccessRoleFilterContext extends BaseUserAccessFilterContext {

    public static final String FILTER_PROPERTY_TYPE = "type";
    public static final String ROLE_DEEPLINK_PARAM = "role";

    /**
     * Constructor
     * @param requester Identity requesting access. Can be null.
     * @param target Identity targeted for access. Can be null.
     */
    public UserAccessRoleFilterContext(Identity requester, Identity target) {
        super(ObjectConfig.getObjectConfig(Bundle.class), 
                UIConfig.LCM_SEARCH_ROLE_ATTRIBUTES, 
                UserAccessSearchOptions.ObjectTypes.Role, 
                requester, 
                target);
    }

    /**
     * Get the list of supported role type names
     */
    private List<SelectItem> getRoleTypes() {
        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        RoleConfig rc = new RoleConfig();
        List<RoleTypeDefinition> types = rc.getRoleTypeDefinitionsList();
        for (RoleTypeDefinition type : types) {
            if (!type.isNoManualAssignment() || !type.isNoAutoAssignment())
                selectItems.add(new SelectItem(type.getDisplayableName(), type.getName()));
        }

        return selectItems;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOs = new ArrayList<ListFilterDTO>();
        ListFilterDTO typeFilter = new ListFilterDTO(
                FILTER_PROPERTY_TYPE, MessageKeys.UI_SEARCH_FILTER_TYPE, false, ListFilterDTO.DataTypes.String, locale);
        typeFilter.setAllowedValues(getRoleTypes());
        filterDTOs.add(typeFilter);
        return filterDTOs;
    }

    @Override
    public List<Filter> convertDeepLinksFilters(Map<String, ListFilterValue> filterValueMap, SailPointContext context) throws GeneralException {
        // Convert role deep links parameters to filters.
        List<Filter> filters = new ArrayList<Filter>();

        if (filterValueMap.containsKey(ROLE_DEEPLINK_PARAM)) {
            ListFilterValue multiValue = filterValueMap.remove(ROLE_DEEPLINK_PARAM);
            List<String> roles = getValueStringList(multiValue);
            Filter nameFilter = Filter.in("name", roles);
            Filter idFilter = Filter.in("id", roles);
            Filter orFilter = Filter.or(nameFilter, idFilter);
            filters.add(orFilter);
        }

        return filters;
    }

    @Override
    public boolean hasDeepLinksParams(Map<String, String> queryParameters) throws GeneralException {
        return queryParameters.containsKey(ROLE_DEEPLINK_PARAM);
    }

    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        Filter filter;
        if (FILTER_PROPERTY_TYPE.equals(filterDTO.getProperty())) {
            filter = getSimpleFilter(FILTER_PROPERTY_TYPE, value, operation);
        } else {
            filter = super.convertFilterSingleValue(filterDTO, value, operation, context);
        }
        
        return filter;
    }
    
    @Override
    protected ListFilterDTO createListFilterDTO(ObjectAttribute attribute, Locale locale) {
        ListFilterDTO listFilterDTO = super.createListFilterDTO(attribute, locale);
        if (ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
            //Allow the suggest filter to also filter what user access would
            String targetIdentityId = null;
            if (target != null) {
                targetIdentityId = target.getId();            
            }
            listFilterDTO.configureColumnSuggest(Bundle.class.getSimpleName(), attribute.getName(), true, QuickLink.LCM_ACTION_REQUEST_ACCESS, getQuickLink(), targetIdentityId, getSuggestUrl());
        }
        
        return listFilterDTO;
    }
}