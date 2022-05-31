/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.acceleratorpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Schema;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;

/**
 * ListFilterContext implementation for returning application account attribute filters.
 */
public class ApplicationOnboardApplicationAttributeListFilterContext extends BaseListFilterContext {

    String applicationId;

    public ApplicationOnboardApplicationAttributeListFilterContext(String applicationId) {
        super(null);
        this.applicationId = applicationId;
    }

    /**
     * Override this to handle making object attribute filters ourselves, since we need additional operators
     */
    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale)
            throws GeneralException {

        if (this.applicationId == null) {
            throw new GeneralException("No application specified to retrieve attributes");
        }
        return addAllowedOperations(
                getSchema(context).getAttributes(),
                locale);
    }

    /**
     * Convenience method to get the application schema.
     */
    public Schema getSchema(SailPointContext context) throws GeneralException {
        Application app = context.getObjectById(Application.class, this.applicationId);
        return app.getSchema(Schema.TYPE_ACCOUNT);
    }

    /**
     * Helper method to add the AllowedOperations for each schema attribute.
     */
    public List<ListFilterDTO> addAllowedOperations(List<AttributeDefinition> attributes, Locale locale)
            throws GeneralException {

        if (attributes == null) {
            throw new GeneralException("There are no attributes in the Schema");
        }

        List<ListFilterDTO> result = new ArrayList<>();
        for (AttributeDefinition attribute : attributes) {
            ListFilterDTO listFilterDTO = new ListFilterDTO(attribute.getName(), attribute.getDisplayableName(locale),
                    false, ListFilterDTO.DataTypes.String, locale);
            listFilterDTO.configureSuggest(
                    "Global",
                    String.format("attributeSuggest_%s", attribute.getName()),
                    getSuggestUrl());

            // All ListFilterDTO objects get the equals operation, add in the others needed here.
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.StartsWith, locale);
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.EndsWith, locale);
            listFilterDTO.addAllowedOperation(ListFilterValue.Operation.Contains, locale);

            result.add(listFilterDTO);
        }

        return result;
    }
}
