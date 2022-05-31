package sailpoint.rest.ui.workitems;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.WorkItem;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.workitem.WorkItemUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ListFilterContext for Work Items.
 */
public class WorkItemListFilterContext extends BaseListFilterContext {

    private static final String OWNER_ID = "workitem_owner_id";
    private static final String OWNER_CONTEXT = "Owner";

    private static final String REQUESTER_FILTER = "requester";
    private static final String OWNER_FILTER = "owner";
    private static final String TYPE_FILTER = "workItemType";
    private static final String ASSIGNEE_FILTER = "assignee";
    private static final String PRIORITY_FILTER = "level";
    private static final String ACCESS_REQUEST_ID_FILTER = "identityRequestId";
    private static final String WORKITEM_ID_FILTER = "workitemId";
    private static final String EXPIRES_AFTER_FILTER = "expiresAfter";
    private static final String EXPIRES_BEFORE_FILTER = "expiresBefore";
    private static final String CREATED_AFTER_FILTER = "createdAfter";
    private static final String CREATED_BEFORE_FILTER = "createdBefore";
    private static final String REMINDERS_FILTER = "reminders";
    private static final String ESCALATIONS_FILTER = "escalationCount";
    private static final String NEXT_EVENT_AFTER_FILTER = "nextEventAfter";
    private static final String NEXT_EVENT_BEFORE_FILTER = "nextEventBefore";

    private static final String ACCESS_REQUEST_PROPERTY = "identityRequestId";
    private static final String TYPE_PROPERTY = "type";
    private static final String EXPIRES_PROPERTY = "expiration";
    private static final String CREATED_PROPERTY = "created";
    private static final String NEXT_EVENT_PROPERTY = "wakeUpDate";

    public static final List<WorkItem.Type> excludedTypes =
            Arrays.asList(new WorkItem.Type[] {WorkItem.Type.Test, WorkItem.Type.Generic,
                    WorkItem.Type.Event, WorkItem.Type.ImpactAnalysis});

    /**
     * Constructor
     */
    public WorkItemListFilterContext() {
        super(null);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        Map<String, ListFilterDTO> filters = new LinkedHashMap<String, ListFilterDTO>();
        ListFilterDTO ownerFilter = new ListFilterDTO(OWNER_FILTER, MessageKeys.WORK_ITEM_FLT_OWNER,
                true, ListFilterDTO.DataTypes.Identity, locale);
        ownerFilter.configureSuggest(OWNER_CONTEXT, OWNER_ID, getSuggestUrl());
        filters.put(OWNER_FILTER, ownerFilter);
        ListFilterDTO requesterFilter = new ListFilterDTO(REQUESTER_FILTER, MessageKeys.WORK_ITEM_FLT_REQUESTER,
                false, ListFilterDTO.DataTypes.Identity, locale);
        filters.put(REQUESTER_FILTER, requesterFilter);
        ListFilterDTO assigneeFilter = new ListFilterDTO(ASSIGNEE_FILTER, MessageKeys.WORK_ITEM_FLT_ASSIGNEE,
                false, ListFilterDTO.DataTypes.Identity, locale);
        filters.put(ASSIGNEE_FILTER, assigneeFilter);
        ListFilterDTO typeFilter = new ListFilterDTO(TYPE_FILTER, MessageKeys.WORK_ITEM_FLT_TYPE,
                true, ListFilterDTO.DataTypes.String, locale);
        typeFilter.setAllowedValues(getWorkItemTypes(locale));
        filters.put(TYPE_FILTER, typeFilter);
        ListFilterDTO priorityFilter = new ListFilterDTO(PRIORITY_FILTER, MessageKeys.WORK_ITEM_FLT_PRIORITY,
                false, ListFilterDTO.DataTypes.String, locale);
        priorityFilter.setAllowedValues(getPriorityLevels(locale));
        filters.put(PRIORITY_FILTER, priorityFilter);
        ListFilterDTO accessRequestIdFilter = new ListFilterDTO(ACCESS_REQUEST_ID_FILTER, MessageKeys.WORK_ITEM_FLT_ACCESS_REQUEST_ID,
                false, ListFilterDTO.DataTypes.String, locale);
        filters.put(ACCESS_REQUEST_ID_FILTER, accessRequestIdFilter);
        ListFilterDTO workItemIdFilter = new ListFilterDTO(WORKITEM_ID_FILTER, MessageKeys.WORK_ITEM_FLT_WORK_ITEM_ID,
                false, ListFilterDTO.DataTypes.String, locale);
        filters.put(WORKITEM_ID_FILTER, workItemIdFilter);
        ListFilterDTO expiresAfterFilter = new ListFilterDTO(EXPIRES_AFTER_FILTER, MessageKeys.WORK_ITEM_FLT_EXPIRES_AFTER,
                false, ListFilterDTO.DataTypes.Date, locale);
        filters.put(EXPIRES_AFTER_FILTER, expiresAfterFilter);
        ListFilterDTO expiresBeforeFilter = new ListFilterDTO(EXPIRES_BEFORE_FILTER, MessageKeys.WORK_ITEM_FLT_EXPIRES_BEFORE,
                false, ListFilterDTO.DataTypes.Date, locale);
        filters.put(EXPIRES_BEFORE_FILTER, expiresBeforeFilter);
        ListFilterDTO createdAfterFilter = new ListFilterDTO(CREATED_AFTER_FILTER, MessageKeys.WORK_ITEM_FLT_CREATED_AFTER,
                false, ListFilterDTO.DataTypes.Date, locale);
        filters.put(CREATED_AFTER_FILTER, createdAfterFilter);
        ListFilterDTO createdBeforeFilter = new ListFilterDTO(CREATED_BEFORE_FILTER, MessageKeys.WORK_ITEM_FLT_CREATED_BEFORE,
                false, ListFilterDTO.DataTypes.Date, locale);
        filters.put(CREATED_BEFORE_FILTER, createdBeforeFilter);
        ListFilterDTO remindersFilter = new ListFilterDTO(REMINDERS_FILTER, MessageKeys.WORK_ITEM_FLT_REMINDERS,
                false, ListFilterDTO.DataTypes.Number, locale);
        remindersFilter.setAllowedOperations(getNumericFilterOperations(locale));
        filters.put(REMINDERS_FILTER, remindersFilter);
        ListFilterDTO escalationsFilter = new ListFilterDTO(ESCALATIONS_FILTER, MessageKeys.WORK_ITEM_FLT_ESCALATIONS,
                false, ListFilterDTO.DataTypes.Number, locale);
        escalationsFilter.setAllowedOperations(getNumericFilterOperations(locale));
        filters.put(ESCALATIONS_FILTER, escalationsFilter);
        ListFilterDTO nextEventAfterFilter = new ListFilterDTO(NEXT_EVENT_AFTER_FILTER, MessageKeys.WORK_ITEM_FLT_NEXT_EVENT_AFTER,
                false, ListFilterDTO.DataTypes.Date, locale);
        filters.put(NEXT_EVENT_AFTER_FILTER, nextEventAfterFilter);
        ListFilterDTO nextEventBeforeFilter = new ListFilterDTO(NEXT_EVENT_BEFORE_FILTER, MessageKeys.WORK_ITEM_FLT_NEXT_EVENT_BEFORE,
                false, ListFilterDTO.DataTypes.Date, locale);
        filters.put(NEXT_EVENT_BEFORE_FILTER, nextEventBeforeFilter);

        List<ListFilterDTO> filterDTOList = new ArrayList<ListFilterDTO>();
        List<String> topFilters = getTopFilters(context);

        // add in the top filters in order that they appear
        for(String filterName : topFilters) {
            filterDTOList.add(filters.get(filterName));
            filters.remove(filterName);
        }

        // now add in everything that's left as an 'additional' filter
        for(String filterName : filters.keySet()) {
            ListFilterDTO filter = filters.get(filterName);
            filter.setAdditional(true);
            filterDTOList.add(filter);
        }

        return filterDTOList;
    }

    private List<String> getTopFilters(SailPointContext ctx) throws GeneralException {
        Configuration systemConfig = ctx.getConfiguration();
        List<String> topFilters = new ArrayList<String>();
        String filterAttributeString = systemConfig.getString(Configuration.WORK_ITEM_FILTER_ATTRIBUTES);
        if(Util.isNotNullOrEmpty(filterAttributeString)) {
            String[] filterAttributes = Util.csvToArray(filterAttributeString);
            for(String filterName : filterAttributes) {
                topFilters.add(filterName);
            }
        } else {
            topFilters.add(OWNER_FILTER);
            topFilters.add(REQUESTER_FILTER);
            topFilters.add(ASSIGNEE_FILTER);
            topFilters.add(TYPE_FILTER);
        }
        return topFilters;
    }

    @Override
    public List<ListFilterDTO> getObjectAttributeFilters(SailPointContext context, Locale locale) throws GeneralException {
        return super.getObjectAttributeFilters(context, locale);
    }

    /**
     * Helper method to get the allowed values for the Type filter based on LCM configuration
     */
    private List<ListFilterDTO.SelectItem> getWorkItemTypes(Locale locale) {
        List<ListFilterDTO.SelectItem> types = new ArrayList<>();
        for (WorkItem.Type type : WorkItem.Type.values()) {
            if(!excludedTypes.contains(type)) {
                types.add(new ListFilterDTO.SelectItem(type, locale));
            }
        }
        Collections.sort(types, new Comparator<ListFilterDTO.SelectItem>() {
            @Override
            public int compare(ListFilterDTO.SelectItem o1, ListFilterDTO.SelectItem o2) {
                return o1.getDisplayName().compareTo(o2.getDisplayName());
            }
        });
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
     * Helper method to get the allowed operations for numeric filters.
     */
    private List<ListFilterDTO.SelectItem> getNumericFilterOperations(Locale locale) {
        List<ListFilterDTO.SelectItem> operations = new ArrayList<>();
        operations.add(getSelectItem(MessageKeys.LIST_FILTER_OP_EQUALS, locale, ListFilterValue.Operation.Equals));
        operations.add(getSelectItem(MessageKeys.LIST_FILTER_OP_NOTEQUALS, locale, ListFilterValue.Operation.NotEquals));
        operations.add(getSelectItem(MessageKeys.LIST_FILTER_OP_LESSTHAN, locale, ListFilterValue.Operation.LessThan));
        operations.add(getSelectItem(MessageKeys.LIST_FILTER_OP_GREATERTHAN, locale, ListFilterValue.Operation.GreaterThan));
        return operations;
    }

    private ListFilterDTO.SelectItem getSelectItem(String messageKey, Locale locale, ListFilterValue.Operation operation) {
        return new ListFilterDTO.SelectItem(new Message(messageKey).getLocalizedMessage(locale, null), operation);
    }

    @Override
    protected Filter convertFilterSingleValue(ListFilterDTO filterDTO, String value, ListFilterValue.Operation operation, SailPointContext context) throws GeneralException {
        String property = filterDTO.getProperty();

        switch(property) {
            case WORKITEM_ID_FILTER:
                return WorkItemUtil.getNameAndIdFilter(context, value);

            case ACCESS_REQUEST_ID_FILTER:
                return Filter.eq(ACCESS_REQUEST_PROPERTY, Util.padID(value));

            case TYPE_FILTER:
                return Filter.eq(TYPE_PROPERTY, value);

            case EXPIRES_AFTER_FILTER:
                return getDateFilter(EXPIRES_PROPERTY, value, ListFilterValue.Operation.GreaterThan);

            case EXPIRES_BEFORE_FILTER:
                return getDateFilter(EXPIRES_PROPERTY, value, ListFilterValue.Operation.LessThan);

            case CREATED_AFTER_FILTER:
                return getDateFilter(CREATED_PROPERTY, value, ListFilterValue.Operation.GreaterThan);

            case CREATED_BEFORE_FILTER:
                return getDateFilter(CREATED_PROPERTY, value, ListFilterValue.Operation.LessThan);

            case NEXT_EVENT_AFTER_FILTER:
                return getDateFilter(NEXT_EVENT_PROPERTY, value, ListFilterValue.Operation.GreaterThan);

            case NEXT_EVENT_BEFORE_FILTER:
                return getDateFilter(NEXT_EVENT_PROPERTY, value, ListFilterValue.Operation.LessThan);
        }

        return super.convertFilterSingleValue(filterDTO, value, operation, context);
    }
}
