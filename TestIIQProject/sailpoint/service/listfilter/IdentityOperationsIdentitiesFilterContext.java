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
public class IdentityOperationsIdentitiesFilterContext extends BaseListFilterContext {
    private static Log log = LogFactory.getLog(IdentityOperationsIdentitiesFilterContext.class);

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
    public IdentityOperationsIdentitiesFilterContext() {
        super(ObjectConfig.getObjectConfig(Identity.class));
    }

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
    protected ListFilterDTO createListFilterDTO(ObjectAttribute attribute, Locale locale) {
        ListFilterDTO listFilterDTO = super.createListFilterDTO(attribute, locale);
        if (ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
            listFilterDTO.configureColumnSuggest(Identity.class.getSimpleName(), attribute.getName(),
                    null, getSuggestUrl());
        }

        return listFilterDTO;
    }

    private ListFilterDTO createColumnListFilterDTO(String column, String messageKey, Locale locale) {
        ListFilterDTO listFilterDTO = new ListFilterDTO(
                column, messageKey, false, ListFilterDTO.DataTypes.Column, locale);
        listFilterDTO.configureColumnSuggest(Identity.class.getSimpleName(), column,
                null, getSuggestUrl());
        return listFilterDTO;
    }
}
