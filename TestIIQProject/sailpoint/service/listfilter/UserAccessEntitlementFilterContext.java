package sailpoint.service.listfilter;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityFilter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QuickLink;
import sailpoint.object.UIConfig;
import sailpoint.service.useraccess.UserAccessSearchOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.lcm.EntitlementFilters;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of BaseUserAccessFilterContext for Entitlement searching
 */
public class UserAccessEntitlementFilterContext extends BaseUserAccessFilterContext {

    public static final String FILTER_PROPERTY_APPLICATION = "applicationIds";
    public static final String FILTER_PROPERTY_ATTRIBUTE = "attributes";
    public static final String FILTER_PROPERTY_OWNER = "ownerId";
    public static final String FILTER_PROPERTY_VALUE = "value";

    public static final String ENTITLEMENT_DEEPLINK_PARAM = "entitlement";
    public static final String ENTITLEMENT_APPLICATION_DEEPLINK_PARAM = "entitlementApplication";
    public static final String ENTITLEMENT_VALUE_DEEPLINK_PARAM = "entitlementValue";
    public static final String ENTITLEMENT_ATTRIBUTE_DEEPLINK_PARAM = "entitlementAttribute";
    
    public static final String ATTR_REQUESTEE_ID = "requesteeId";
    public static final String ATTR_APPLICATION_ATTRIBUTE_ONLY = "appAttributeOnly";

    private boolean includeValueFilter = false;

    private static int getIndex(String prefix, String paramName) throws GeneralException {
        if (Util.isNullOrEmpty(prefix) || Util.isNullOrEmpty(paramName)) {
            throw new GeneralException("Failed to get index with null prefix or parameter name");
        }
        if (prefix.length() >= paramName.length()) {
            throw new GeneralException("Could not parse index from malformed parameter name " + paramName);
        }
        return Integer.parseInt(paramName.substring(prefix.length()), paramName.length());
    }


    /**
     * Constructor
     * @param requester Identity requesting access. Can be null.
     * @param target Identity targeted for access. Can be null.
     */
    public UserAccessEntitlementFilterContext(Identity requester, Identity target) {
        this(requester, target, false);
    }

    /**
     * Constructor
     * @param requester Identity requesting access. Can be null.
     * @param target Identity targeted for access. Can be null.
     * @param includeValueFilter True to include "value" filter for attributes              
     */
    public UserAccessEntitlementFilterContext(Identity requester, Identity target, boolean includeValueFilter) {
        super(ObjectConfig.getObjectConfig(ManagedAttribute.class),
                UIConfig.LCM_SEARCH_ENTITLEMENT_ATTRIBUTES,
                UserAccessSearchOptions.ObjectTypes.Entitlement,
                requester,
                target);
        this.includeValueFilter = includeValueFilter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOs = new ArrayList<ListFilterDTO>();
        ListFilterDTO appFilter = addLcmAttributes(new ListFilterDTO(
                FILTER_PROPERTY_APPLICATION, MessageKeys.UI_SEARCH_FILTER_APPLICATION, true, ListFilterDTO.DataTypes.Application, locale));
        appFilter.setAttribute(ATTR_APPLICATION_ATTRIBUTE_ONLY, true);
        filterDTOs.add(appFilter);
        filterDTOs.add(addLcmAttributes(new ListFilterDTO(
                FILTER_PROPERTY_ATTRIBUTE, MessageKeys.UI_SEARCH_FILTER_ATTRIBUTE, true, ListFilterDTO.DataTypes.Attribute, locale)));
        if (this.includeValueFilter) {
            filterDTOs.add(new ListFilterDTO(FILTER_PROPERTY_VALUE, MessageKeys.UI_SEARCH_FILTER_VALUE, false, ListFilterDTO.DataTypes.String, locale));
        }
        ListFilterDTO ownerFilter =
                new ListFilterDTO("ownerId", MessageKeys.UI_SEARCH_FILTER_OWNER, false, ListFilterDTO.DataTypes.Identity, locale);
        ownerFilter.configureSuggest(IdentityFilter.CONTEXT_ENTITLEMENT_OWNER, "requestEntitlementsEntitlementOwnerSuggest", getSuggestUrl());
        filterDTOs.add(ownerFilter);

        return filterDTOs;
    }

    /**
     * Add LCM related attribute to the filter.
     * @param filterDTO ListFilterDTO
     * @return ListFilterDTO
     */
    private ListFilterDTO addLcmAttributes(ListFilterDTO filterDTO) {
        filterDTO.setAttribute(ListFilterDTO.ATTR_SUGGEST_IS_LCM, true);
        filterDTO.setAttribute(ATTR_REQUESTEE_ID, (this.target != null) ? this.target.getId() : null);
        filterDTO.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, getSuggestUrl());
        return filterDTO;
    }

    @Override
    public Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context) throws GeneralException{
        Filter filter;
        if (FILTER_PROPERTY_APPLICATION.equals(filterDTO.getProperty())) {
            List<String> applicationIds = getValueStringList(filterValue);
            filter = Filter.in("application.id", applicationIds);
        } else if (FILTER_PROPERTY_ATTRIBUTE.equals(filterDTO.getProperty())) {
            List<Filter> attributeFilters = new ArrayList<Filter>();
            List<String> attributes = getValueStringList(filterValue);
            for(String attribute : attributes) {
                attributeFilters.add(Filter.ignoreCase(Filter.like("attribute", attribute, getMatchMode(context))));
            }
            filter = Filter.or(attributeFilters);
        } else if (FILTER_PROPERTY_VALUE.equals(filterDTO.getProperty())) {
            filter = Filter.ignoreCase(Filter.like("displayableName", getValueString(filterValue), getMatchMode(context)));
        } else if (FILTER_PROPERTY_OWNER.equals(filterDTO.getProperty())) {
            filter = Filter.eq("owner.id", getValueString(filterValue));
        } else if (ListFilterDTO.DataTypes.Identity.equals(filterDTO.getDataType())) {
            // Extended attributes of type Identity for ManagedAttribute objects should
            // not be joined onto Identity because the automatic conversion of the attribute name
            // to the column name in the persistence layer handles Identity attributes more like
            // String attributes for the ManagedAttribute type. In this case we just want to use
            // the eq filter. It looks like the id of the Identity is actually saved when you add
            // a value to the Identity type extend attribute so try to fetch the id if it looks
            // like we have a name.
            String idOrName = getValueString(filterValue);

            if (!ObjectUtil.isUniqueId(idOrName)) {
                Identity identity = context.getObjectByName(Identity.class, idOrName);
                if (identity != null) {
                    idOrName = identity.getId();
                }
            }

            filter = Filter.eq(filterDTO.getProperty(), idOrName);
        } else {
            filter = super.convertFilter(filterDTO, filterValue, context);
        }

        return filter;
    }


    @Override
    public List<Filter> convertDeepLinksFilters(Map<String, ListFilterValue> filterValueMap, SailPointContext context) throws GeneralException {
        // Convert entitlement deep links parameters to filters.
        List<Filter> leafFilters = new ArrayList<Filter>();

        Filter idFilter = convertDeepLinkEntitlementIdFilter(filterValueMap);
        if (idFilter != null) {
            leafFilters.add(idFilter);
        }
        Filter comboFilter = convertDeepLinkEntitlementComboFilters(filterValueMap, context);
        if (comboFilter != null) {
            leafFilters.add(comboFilter);
        }

        Filter filter = createOptionalOr(leafFilters);
        return filter == null ? Collections.<Filter>emptyList() : Arrays.asList(filter);
    }

    /**
     * Convert all entitlement id parameters to filters.
     * @param filterValueMap Filter values
     * @return a filter, or null if no id parameters were found
     * @throws GeneralException
     */
    private Filter convertDeepLinkEntitlementIdFilter(Map<String, ListFilterValue> filterValueMap) throws GeneralException {
        Filter filter = null;
        
        if (filterValueMap.containsKey(ENTITLEMENT_DEEPLINK_PARAM)) {
            ListFilterValue multiValue = filterValueMap.remove(ENTITLEMENT_DEEPLINK_PARAM);
            List<String> entitlements = getValueStringList(multiValue);
            filter = Filter.in("id", entitlements);
        }

        return filter;
    }

    /**
     * Convert all entitlement combo parameters (e.g. "entitlementApplication1", "entitlementAttribute1", 
     * "entitlementValue1", etc) to filters.
     * @param filterValueMap Filter values
     * @return a filter, or null if no entitlement combo parameters were found
     * @throws GeneralException
     */
    private Filter convertDeepLinkEntitlementComboFilters(Map<String, ListFilterValue> filterValueMap, SailPointContext context)
            throws GeneralException {

        // Convert params to a set of EntitlementFilters same as SubmitRequestBean does. (Note, the name is a 
        // little confusing, but these are not the same as query filters.
        EntitlementFilters entitlementFilters = new EntitlementFilters();

        for (Map.Entry<String, ListFilterValue> entry : filterValueMap.entrySet()) {
            String key = entry.getKey();
            String value = getValueString(entry.getValue());

            if (key.startsWith(ENTITLEMENT_APPLICATION_DEEPLINK_PARAM)) {
                int idx = getIndex(ENTITLEMENT_APPLICATION_DEEPLINK_PARAM, key);
                entitlementFilters.setApplication(idx, value);
            } else if (key.startsWith(ENTITLEMENT_VALUE_DEEPLINK_PARAM)) {
                int idx = getIndex(ENTITLEMENT_VALUE_DEEPLINK_PARAM, key);
                entitlementFilters.setValue(idx, value);
            } else if (key.startsWith(ENTITLEMENT_ATTRIBUTE_DEEPLINK_PARAM)) {
                int idx = getIndex(ENTITLEMENT_ATTRIBUTE_DEEPLINK_PARAM, key);
                entitlementFilters.setAttribute(idx, value);
            }
        }

        
        // Now convert the entitlement filters to an actual query filter. This will be an OR of all the individual
        // combo filters (which are each an AND of an individual entitlement's parameters.)
        List<Filter> andFilters = new ArrayList<Filter>();
        for (EntitlementFilters.Filter entitlementFilter : entitlementFilters.getFilters()) {
            String application = entitlementFilter.getApplication();
            if (Util.isNullOrEmpty(application)) {
                throw new GeneralException("Mandatory request parameter " + ENTITLEMENT_APPLICATION_DEEPLINK_PARAM + " not specified");
            }

            // We allow the user to leave entitlement attribute blank and default to the one specified in the
            // application, assuming there is only one. If the user does not specify an attribute and we can't figure
            // one out, throw.
            String attribute = entitlementFilter.getAttribute();
            if (Util.isNullOrEmpty(attribute)) {
                Application app = context.getObjectByName(Application.class, application);
                if (app == null) {
                    throw new GeneralException("Application " + application + " not found");
                }
                List<String> appAttrs = app.getEntitlementAttributeNames();
                if (Util.isEmpty(appAttrs)) {
                    throw new GeneralException("Failed to find entitlement attribute for " + app.getName());
                }
                if (appAttrs.size() > 1) {
                    throw new GeneralException("Entitlement attribute for " + app.getName() + " not specified");
                }
                attribute = appAttrs.get(0);
            }

            String value = entitlementFilter.getValue();
            if (Util.isNullOrEmpty(value)) {
                throw new GeneralException("Mandatory request parameter " + ENTITLEMENT_VALUE_DEEPLINK_PARAM + " not specified");
            }

            List<Filter> comboFilters = new ArrayList<Filter>();
            comboFilters.add(Filter.eq("application.name", application));
            comboFilters.add(Filter.eq("attribute", attribute));
            comboFilters.add(Filter.eq("value", value));
            andFilters.add(Filter.and(comboFilters));
        }

        return createOptionalOr(andFilters);
   }

    @Override
    public boolean hasDeepLinksParams(Map<String, String> queryParameters) throws GeneralException {
        return queryParameters.containsKey(ENTITLEMENT_DEEPLINK_PARAM) ||
                queryParameters.containsKey(ENTITLEMENT_APPLICATION_DEEPLINK_PARAM) ||
                queryParameters.containsKey(ENTITLEMENT_ATTRIBUTE_DEEPLINK_PARAM) ||
                queryParameters.containsKey(ENTITLEMENT_ATTRIBUTE_DEEPLINK_PARAM);
    }
    
    @Override
    protected ListFilterDTO createListFilterDTO(ObjectAttribute attribute, Locale locale) {
        ListFilterDTO listFilterDTO = super.createListFilterDTO(attribute, locale);
        if (ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
            listFilterDTO.configureLcmColumnSuggest(ManagedAttribute.class.getSimpleName(), attribute.getName(),
                    QuickLink.LCM_ACTION_REQUEST_ACCESS, getQuickLink(), getSuggestUrl());
        }
        
        return listFilterDTO;
    }
    
    /**
     * Optionally OR a list of filters if there are more than one.
     * @param filters a list of filters
     * @return if filters is empty: null; if filters size == 1: the single filter; otherwise an OR of the filters
     */
    static private Filter createOptionalOr(List<Filter> filters) {
        if (Util.isEmpty(filters)) {
            return null;
        }
        if (filters.size() == 1) {
            return filters.get(0);
        }
        return Filter.or(filters);
    }

}
