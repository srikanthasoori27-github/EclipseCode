package sailpoint.service.listfilter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityFilter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QuickLink;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ListFilterContext implementation for IdentityListResource and RequestPopulationBean
 */
public class RequestAccessIdentitiesFilterContext extends BaseListFilterContext {
    private static Log log = LogFactory.getLog(RequestAccessIdentitiesFilterContext.class);

    public static final String FILTER_PROPERTY_MANAGER = "manager";
    public static final String FILTER_PROPERTY_TYPE = "type";
    public static final String FILTER_PROPERTY_APPLICATION = "links.application.name";
    public static final String FILTER_PROPERTY_ROLE = "bundles.name";
    public static final String FILTER_PROPERTY_ASSIGNED_ROLE = "assignedRoles.name";
    public static final String IDENTITY_DEEPLINK_PARAM = "identity";

    protected String quickLink;

    /**
     * Constructor
     */
    public RequestAccessIdentitiesFilterContext() {
        super(ObjectConfig.getObjectConfig(Identity.class));
    }

    public void setQuickLink(String qlName) { this.quickLink = qlName; }
    public String getQuickLink() { return this.quickLink; }
    /**
     * {@inheritDoc}
     */
    @Override
    public List<ObjectAttribute> getFilterObjectAttributes(SailPointContext context) throws GeneralException {
        return new ArrayList<ObjectAttribute>(getObjectConfig().getExtendedAttributeList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {

        List<ListFilterDTO> filterDTOs = new ArrayList<ListFilterDTO>();

        ListFilterDTO managerFilter =
                new ListFilterDTO(FILTER_PROPERTY_MANAGER, MessageKeys.UI_SEARCH_FILTER_MANAGER, false, ListFilterDTO.DataTypes.Identity, locale);
        managerFilter.configureSuggest(IdentityFilter.CONTEXT_LCM_POPULATION_MANAGER, "suggest_manager", getSuggestUrl());
        filterDTOs.add(managerFilter);

        filterDTOs.add(createColumnListFilterDTO(FILTER_PROPERTY_TYPE, MessageKeys.UI_SEARCH_FILTER_TYPE, locale));
        filterDTOs.add(createColumnListFilterDTO(FILTER_PROPERTY_APPLICATION, MessageKeys.UI_SEARCH_FILTER_APPLICATION, locale));
        filterDTOs.add(createColumnListFilterDTO(FILTER_PROPERTY_ROLE, MessageKeys.UI_SEARCH_FILTER_ROLE, locale));
        filterDTOs.add(createColumnListFilterDTO(FILTER_PROPERTY_ASSIGNED_ROLE, MessageKeys.UI_SEARCH_FILTER_ASSIGNED_ROLE,locale));

        return filterDTOs;
    }

    @Override
    public List<Filter> convertDeepLinksFilters(Map<String, ListFilterValue> filterValueMap, SailPointContext context) throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();
        if (filterValueMap.containsKey(IDENTITY_DEEPLINK_PARAM)) {
            if (log.isDebugEnabled()) {
                for (String key : filterValueMap.keySet()) {
                    log.debug("identity parameter specified; ignoring all other session filter parameters");
                }
            }
            // Create a filter to search for a specific name or id.
            ListFilterValue nameOrId = filterValueMap.remove(IDENTITY_DEEPLINK_PARAM);
            Filter nameFilter = Filter.eq("name", nameOrId);
            Filter idFilter = Filter.eq("id", nameOrId);
            filters.add(Filter.or(nameFilter, idFilter));
        }
        
        return filters;
    }
    
    @Override
    protected ListFilterDTO createListFilterDTO(ObjectAttribute attribute, Locale locale) {
        ListFilterDTO listFilterDTO = super.createListFilterDTO(attribute, locale);
        if (ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
            listFilterDTO.configureLcmColumnSuggest(Identity.class.getSimpleName(), attribute.getName(),
                    QuickLink.LCM_ACTION_REQUEST_ACCESS, getQuickLink(), getSuggestUrl());
        }

        return listFilterDTO;
    }

    private ListFilterDTO createColumnListFilterDTO(String column, String messageKey, Locale locale) {
        ListFilterDTO listFilterDTO = new ListFilterDTO(
                column, messageKey, false, ListFilterDTO.DataTypes.Column, locale);
        listFilterDTO.configureLcmColumnSuggest(Identity.class.getSimpleName(), column,
                QuickLink.LCM_ACTION_REQUEST_ACCESS, getQuickLink(), getSuggestUrl());
        return listFilterDTO;
    }
}
