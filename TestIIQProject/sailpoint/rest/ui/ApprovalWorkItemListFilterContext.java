/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

public class ApprovalWorkItemListFilterContext extends BaseListFilterContext {

    private static final String OWNER_ID = "workitem_owner_id";
    private static final String OWNER_CONTEXT = "SelfWithWorkgroups";
    private static final String OWNER_FILTER = "owner";
    private static final String REQUESTER_FILTER = "requester";
    private static final String ASSIGNEE_FILTER = "assignee";

    /**
     * Constructor
     */
    public ApprovalWorkItemListFilterContext() {
        super(null);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOList = new ArrayList<ListFilterDTO>();
        ListFilterDTO ownerFilter = new ListFilterDTO(OWNER_FILTER, MessageKeys.WORK_ITEM_FLT_OWNER,
                true, ListFilterDTO.DataTypes.Identity, locale);
        ownerFilter.configureSuggest(OWNER_CONTEXT, OWNER_ID, getSuggestUrl());
        ListFilterDTO requesterFilter = new ListFilterDTO(REQUESTER_FILTER, MessageKeys.WORK_ITEM_FLT_REQUESTER,
                false, ListFilterDTO.DataTypes.Identity, locale);
        ListFilterDTO assigneeFilter = new ListFilterDTO(ASSIGNEE_FILTER, MessageKeys.WORK_ITEM_FLT_ASSIGNEE,
                false, ListFilterDTO.DataTypes.Identity, locale);
        filterDTOList.add(ownerFilter);
        filterDTOList.add(requesterFilter);
        filterDTOList.add(assigneeFilter);
        return filterDTOList;
    }
}
