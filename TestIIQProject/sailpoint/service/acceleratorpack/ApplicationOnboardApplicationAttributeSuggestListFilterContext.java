/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.service.acceleratorpack;

import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;

/**
 * ListFilterContext implementation for returning application account attribute filters with column suggests.
 */
public class ApplicationOnboardApplicationAttributeSuggestListFilterContext extends ApplicationOnboardApplicationAttributeListFilterContext {

    public ApplicationOnboardApplicationAttributeSuggestListFilterContext(String applicationId) {
        super(applicationId);
    }

    /**
     * Override this to handle making object attribute filters ourselves, since we need to configure the suggests
     */
    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale)
            throws GeneralException {

        if (this.applicationId == null) {
            throw new GeneralException("No application specified to retrieve attributes");
        }

        List<ListFilterDTO> result = null;
        result = addAllowedOperations(getSchema(context).getEntitlementAttributes(), locale);

        return convertToColumnSuggest(result);

    }

    private List<ListFilterDTO> convertToColumnSuggest(List<ListFilterDTO> applicationAttributeFilters)
            throws GeneralException {

        if (applicationAttributeFilters == null) {
            throw new GeneralException("There are no attributes in the Schema");
        }

        for (ListFilterDTO filter : applicationAttributeFilters) {
            // If string type, configure as suggest
            if (ListFilterDTO.DataTypes.String.equals(filter.getDataType())) {
                filter.setMultiValued(true);
                filter.setEditable(true);
                String filterString = Filter.eq("attribute", filter.getProperty()).toString();

                configureColumnSuggest(filter, ManagedAttribute.class.getSimpleName(), "value", filterString);
            }
        }

        return applicationAttributeFilters;
    }
}
