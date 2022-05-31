/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identityrequest;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.WorkItem;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * List Filter Context for IdentittyRequestItemListService. These filters are not displayed to user but instead
 * to define the contents of tables on details page based on compilation status and approval state
 */
public class IdentityRequestItemListFilterContext extends BaseListFilterContext{

    public static final String COMPILATION_STATUS_PROPERTY = "compilationStatus";
    public static final String APPROVAL_STATE_PROPERTY= "approvalState";

    /**
     * Constructor
     */
    public IdentityRequestItemListFilterContext() {
        super(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) {
        ListFilterDTO compilationStatusFilter = new ListFilterDTO(COMPILATION_STATUS_PROPERTY, "", true, ListFilterDTO.DataTypes.String, locale);
        compilationStatusFilter.setAllowedValues(getCompilationStatusValues(locale));

        ListFilterDTO approvalStateFilter = new ListFilterDTO(APPROVAL_STATE_PROPERTY, "", true, ListFilterDTO.DataTypes.String, locale);
        approvalStateFilter.setAllowedValues(getApprovalStates(locale));

        return Arrays.asList(compilationStatusFilter, approvalStateFilter);
    }

    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        Filter filter = super.convertFilterSingleValue(filterDTO, value, operation, context);
        // Handle NotEquals to include a null filter
        if (ListFilterValue.Operation.NotEquals.equals(operation)) {
            filter = Filter.or(Filter.isnull(filterDTO.getProperty()), filter);
        }

        return filter;
    }

    /**
     * Helper method to get the allowed values for the compilation status filter
     */
    private List<ListFilterDTO.SelectItem> getCompilationStatusValues(Locale locale) {
        List<ListFilterDTO.SelectItem> compilationStatuses = new ArrayList<>();
        for (IdentityRequestItem.CompilationStatus status : IdentityRequestItem.CompilationStatus.values()) {
            compilationStatuses.add(new ListFilterDTO.SelectItem(status, locale));
        }
        return compilationStatuses;
    }

    /**
     * Helper method to get the allowed values for the approval state filter
     */
    private List<ListFilterDTO.SelectItem> getApprovalStates(Locale locale) {
        List<ListFilterDTO.SelectItem> approvalStates = new ArrayList<>();
        for (WorkItem.State state : WorkItem.State.values()) {
            approvalStates.add(new ListFilterDTO.SelectItem(state, locale));
        }
        return approvalStates;
    }
}
