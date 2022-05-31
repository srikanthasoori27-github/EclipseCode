/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identityrequest;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.IdentityFilter;
import sailpoint.object.IdentityRequest;
import sailpoint.object.WorkItem;
import sailpoint.service.LCMConfigService;
import sailpoint.service.RequestAccessService;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of ListFilterContext to handle filtering on identity request list page
 */
public class IdentityRequestListFilterContext extends BaseListFilterContext {

    // These are legacy names from classic grid advanced search, keep them around in case others have configured them
    private static final String REQUESTER_SUGGEST_ID = "accessRequests_requester";
    private static final String REQUESTEE_SUGGEST_ID = "accessRequests_requestee";

    static final String REQUESTER_PROPERTY = "requesterId";
    static final String REQUESTEE_PROPERTY = "targetId";
    static final String CREATED_PROPERTY = "created";
    static final String TYPE_PROPERTY = "type";
    static final String PRIORITY_PROPERTY = "priority";
    static final String STATUS_PROPERTY = "IIQ_requestStatus";

    static final String EXECUTION_STATUS_PROPERTY = "executionStatus";
    static final String COMPLETION_STATUS_PROPERTY = "completionStatus";

    /**
     * Enumeration to use for the "status" filter, which does not correspond exactly to CompletionStatus.
     */
    protected enum RequestStatus implements MessageKeyHolder {
        Pending(MessageKeys.UI_IDENTITY_REQUEST_FILTER_STATUS_PENDING),
        Complete(MessageKeys.UI_IDENTITY_REQUEST_FILTER_STATUS_COMPLETE),
        Canceled(MessageKeys.UI_IDENTITY_REQUEST_FILTER_STATUS_CANCELED);

        private String messageKey;

        RequestStatus(String messageKey) {
            this.messageKey = messageKey;
        }

        @Override
        public String getMessageKey() {
            return this.messageKey;
        }
    }

    /**
     * Constructor
     */
    public IdentityRequestListFilterContext() {
        super(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) {

        ListFilterDTO requesterFilter = new ListFilterDTO(REQUESTER_PROPERTY, MessageKeys.UI_IDENTITY_REQUEST_FILTER_REQUESTER, true, ListFilterDTO.DataTypes.Identity, locale);
        requesterFilter.configureSuggest(IdentityFilter.INCLUDE_WORKGROUPS_FILTER, REQUESTER_SUGGEST_ID, getSuggestUrl());

        ListFilterDTO requesteeFilter = new ListFilterDTO(REQUESTEE_PROPERTY, MessageKeys.UI_IDENTITY_REQUEST_FILTER_REQUESTEE, true, ListFilterDTO.DataTypes.Identity, locale);
        requesteeFilter.configureSuggest(IdentityFilter.GLOBAL_FILTER, REQUESTEE_SUGGEST_ID, getSuggestUrl());

        ListFilterDTO typeFilter = new ListFilterDTO(TYPE_PROPERTY, MessageKeys.UI_IDENTITY_REQUEST_FILTER_TYPE, true, ListFilterDTO.DataTypes.String, locale);
        typeFilter.setAllowedValues(getRequestTypes(context, locale));

        ListFilterDTO dateRangeFilter = new ListFilterDTO(CREATED_PROPERTY, MessageKeys.UI_IDENTITY_REQUEST_FILTER_CREATED_DATE, false, ListFilterDTO.DataTypes.DateRange, locale);

        ListFilterDTO priorityFilter = new ListFilterDTO(PRIORITY_PROPERTY, MessageKeys.UI_IDENTITY_REQUEST_FILTER_PRIORITY, false, ListFilterDTO.DataTypes.String, locale);
        priorityFilter.setAllowedValues(getPriorityLevels(locale));

        ListFilterDTO statusFilter = new ListFilterDTO(STATUS_PROPERTY, MessageKeys.UI_IDENTITY_REQUEST_FILTER_STATUS, false, ListFilterDTO.DataTypes.String, locale);
        statusFilter.setAllowedValues(getStatusValues(locale));

        return Arrays.asList(requesterFilter, requesteeFilter, dateRangeFilter, typeFilter, priorityFilter, statusFilter);
    }

    /**
     * Helper method to get the allowed values for the Type filter based on LCM configuration
     */
    private List<ListFilterDTO.SelectItem> getRequestTypes(SailPointContext context, Locale locale) {
        List<ListFilterDTO.SelectItem> types = new ArrayList<>();
        Configuration systemConfig = Configuration.getSystemConfig();
        LCMConfigService lcmConfigService = new LCMConfigService(context);
        List<String> requestTypes = systemConfig.getStringList(Configuration.ACCESS_REQUEST_TYPES);
        for (String requestType : Util.iterate(requestTypes)) {
            types.add(new ListFilterDTO.SelectItem(lcmConfigService.getRequestTypeMessage(requestType, locale), requestType));
        }
        return types;
    }

    /**
     * Helper method to get the allowed values for the Priority filter from the WorkItem.Level enum
     */
    private List<ListFilterDTO.SelectItem> getPriorityLevels(Locale locale) {
        List<ListFilterDTO.SelectItem> levels = new ArrayList<>();
        for (WorkItem.Level level : WorkItem.Level.values()) {
            levels.add(new ListFilterDTO.SelectItem(level, locale));
        }
        return levels;
    }

    /**
     * Helper method to get the allowed values for the Status filter from the RequestStatus enum
     */
    private List<ListFilterDTO.SelectItem> getStatusValues(Locale locale) {
        List<ListFilterDTO.SelectItem> statuses = new ArrayList<>();
        for (RequestStatus status : RequestStatus.values()) {
            statuses.add(new ListFilterDTO.SelectItem(status, locale));
        }
        return statuses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        Filter filter = null;
        String property = filterDTO.getProperty();
        if (REQUESTEE_PROPERTY.equals(property) || REQUESTER_PROPERTY.equals(property)) {
            // Do not create a standard identity filter for these guys, just a simple equality filter on the ID value
            filter = getSimpleFilter(property, value, operation);
        } else if (TYPE_PROPERTY.equals(property) && RequestAccessService.FLOW_CONFIG_NAME.equals(value)) {
            // Legacy. Need to filter on the old flow names if newer AccessRequest is the value
            filter = Filter.in(property, Arrays.asList(RequestAccessService.FLOW_CONFIG_NAME, IdentityRequest.ROLES_REQUEST_FLOW_CONFIG_NAME, IdentityRequest.ENTITLEMENTS_REQUEST_FLOW_CONFIG_NAME));
        } else if (PRIORITY_PROPERTY.equals(property) && WorkItem.Level.Normal.name().equals(value)) {
            // If filtering on "Normal" priority, need to also include no priority at all, which is Normal too
            filter = Filter.or(Filter.isnull(property), getSimpleFilter(property, value, operation));
        } else if (STATUS_PROPERTY.equals(property)) {
            // Our status filter is not a direct correlation to execution/completion status, so have to create this filters manually.
            switch (RequestStatus.valueOf(value)) {
                case Complete:
                    filter = Filter.and(
                            Filter.notnull(COMPLETION_STATUS_PROPERTY),
                            Filter.ne(COMPLETION_STATUS_PROPERTY, IdentityRequest.CompletionStatus.Pending),
                            Filter.ne(EXECUTION_STATUS_PROPERTY, IdentityRequest.ExecutionStatus.Terminated)
                    );
                    break;
                case Pending:
                    filter = Filter.and(
                            Filter.or(Filter.isnull(COMPLETION_STATUS_PROPERTY), Filter.eq(COMPLETION_STATUS_PROPERTY, IdentityRequest.CompletionStatus.Pending)),
                            Filter.ne(EXECUTION_STATUS_PROPERTY, IdentityRequest.ExecutionStatus.Terminated)
                    );
                    break;
                case Canceled:
                    filter = Filter.eq(EXECUTION_STATUS_PROPERTY, IdentityRequest.ExecutionStatus.Terminated);
                    break;
            }
        }

        if (filter == null) {
            filter = super.convertFilterSingleValue(filterDTO, value, operation, context);
        }

        return filter;
    }
}
