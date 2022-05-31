package sailpoint.service.certification.schedule;

import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.IdentityFilter;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Common FilterContext class that uses shared code among the specific implementations
 * for the Certification Schedule UI.
 * @author brian.li
 *
 */
public class CertificationScheduleBaseListFilterContext extends
        BaseListFilterContext {

    /**
     * Class that the implemented class it is represented.
     * Currently used to set the suggestClass when setting it up as a column for String types
     */
    private Class<?> clazz;

    /**
     * Static map to translate our back end class names to what it is being called
     * to users on the front end.
     */
    public static final Map<String, String> CLASS_NAMES;
    static {
        CLASS_NAMES = new HashMap<String, String>();
        CLASS_NAMES.put(Bundle.class.getSimpleName(), "Role");
        CLASS_NAMES.put(ManagedAttribute.class.getSimpleName(), "AdditionalEntitlements");
        CLASS_NAMES.put(Link.class.getSimpleName(), "Account");
    }

    /**
     * SuggestId formatted string to use for suggests.
     * Should format to "certificationScheduleAdditionalEntitlements_owner"
     */
    private static final String SUGGEST_ID = "certificationSchedule%s_%s";

    public CertificationScheduleBaseListFilterContext(Class<?> clazz) {
        super(ObjectConfig.getObjectConfig(clazz));
        this.clazz = clazz;
    }

    @Override
    protected ListFilterDTO createListFilterDTO(ObjectAttribute attribute, Locale locale) {
        ListFilterDTO listFilterDTO = super.createListFilterDTO(attribute, locale);
        addSpecialFilterOptions(listFilterDTO, locale);
        return listFilterDTO;
    }

    /**
     * Clear out the filter's operations, and set them up based on type 
     * @param listFilterDTO Filter to set operations to
     * @param locale The locale to use
     */
    protected void addFilterOperations(ListFilterDTO listFilterDTO, Locale locale) {
        listFilterDTO.getAllowedOperations().clear();
        // All types will have Equals and all but booleans have NotEquals
        listFilterDTO.addAllowedOperation(ListFilterValue.Operation.Equals, locale);
        if (!ListFilterDTO.DataTypes.Boolean.equals(listFilterDTO.getDataType())) {
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.NotEquals, locale);
        }

        // Applications and Identity have the required operations at this point
        if (ListFilterDTO.DataTypes.Column.equals(listFilterDTO.getDataType())) {
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.StartsWith, locale);
        } else if (ListFilterDTO.DataTypes.Number.equals(listFilterDTO.getDataType())) {
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.LessThan, locale);
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.GreaterThan, locale);
        } else if (ListFilterDTO.DataTypes.Date.equals(listFilterDTO.getDataType())) {
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.LessThan, locale);
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.GreaterThan, locale);
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.Between, locale);
        }

    }

    /**
     * Loop through and add the special options for these default filters to be consistent with the extended attrs
     * Ignore date filters since they are already configured before this point.
     * @param filters The list of filters to apply options to
     */
    protected void addSpecialFilterOptions(List<ListFilterDTO> filters, Locale locale) {
        for (ListFilterDTO filter : Util.iterate(filters)) {
            addSpecialFilterOptions(filter, locale);
        }
    }
    
    /**
     * Add special case options for the CertSchedule filters
     * @param listFilterDTO The ListFilterDTO to add options to
     * @param locale The locale to use
     */
    protected void addSpecialFilterOptions(ListFilterDTO listFilterDTO, Locale locale) {
        if (ListFilterDTO.DataTypes.Identity.equals(listFilterDTO.getDataType()) ||
                ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
            listFilterDTO.setMultiValued(true);
        }

        if (ListFilterDTO.DataTypes.Identity.equals(listFilterDTO.getDataType())) {
            // The context for Identity type should already be set to
            // either GLOBAL_FILTER or Manager, so we will just fetch it
            // from the listFilterDTO
            String context = (String)listFilterDTO.getAttribute(ListFilterDTO.ATTR_SUGGEST_CONTEXT);
            // set the context to Global, if null
            if (context == null) {
                context = IdentityFilter.GLOBAL_FILTER;
            }
            //Populate identity suggests with what type of filter it is trying to create
            listFilterDTO.configureSuggest(
                context,
                String.format(SUGGEST_ID, CLASS_NAMES.get(this.clazz.getSimpleName()), listFilterDTO.getProperty()),
                getSuggestUrl());
        }

        if (ListFilterDTO.DataTypes.Application.equals(listFilterDTO.getDataType())) {
            listFilterDTO.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, getSuggestUrl());
        }

        // If string type, configure as suggest and add StartsWith Operation
        if (ListFilterDTO.DataTypes.String.equals(listFilterDTO.getDataType())) {
            configureColumnSuggest(listFilterDTO, clazz.getSimpleName(), listFilterDTO.getProperty(), null);
        }

        // Configure the appropriate filter operations per type
        addFilterOperations(listFilterDTO, locale);
    }

    @Override
    protected Filter getIdentityFilter(String property, String value, ListFilterValue.Operation operation) {
        return getSimpleFilter(property + ".name", value, operation);
    }

    @Override
    public Comparator<ListFilterDTO> getFilterComparator(Locale locale) {
        return ListFilterDTO.getLabelComparator(locale);
    }

    protected Filter getApplicationFilter(String property, String value, ListFilterValue.Operation operation) {
        return getSimpleFilter(property + ".name", value, operation);
    }

    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        String property = filterDTO.getProperty();

        switch(filterDTO.getDataType()) {
            case Application:
                return getApplicationFilter(property, value, operation);
        }

        return super.convertFilterSingleValue(filterDTO, value, operation, context);
    }

}
