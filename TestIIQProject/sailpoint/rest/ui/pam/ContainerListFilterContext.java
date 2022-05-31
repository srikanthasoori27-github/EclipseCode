/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.pam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.service.listfilter.BaseListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.pam.PamUtil;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

public class ContainerListFilterContext extends BaseListFilterContext {
    private static final String OWNER_FILTER = "owner";
    private static final String OWNER_CONTEXT = "Owner";
    private static final String OWNER_ID = "container_list_owner_id";
    private static final String APP_FILTER = "application";

    public ContainerListFilterContext () {
        super(null);
    }

    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale) throws GeneralException {
        List<ListFilterDTO> filterDTOList = new ArrayList<ListFilterDTO>();

        //Application filter
        ListFilterDTO appFilter = new ListFilterDTO(APP_FILTER, MessageKeys.UI_PAM_CONTAINERS_FILTER_APPLICATION,
                true, ListFilterDTO.DataTypes.Application, locale);
        appFilter.setAttribute(ListFilterDTO.ATTR_SUGGEST_URL, getSuggestUrl());
        appFilter.setAttribute(ListFilterDTO.ATTR_SUGGEST_FILTER_STRING,
                PamUtil.TYPE + " == \"" + PamUtil.PAM_APPLICATION_TYPE + "\"");
        filterDTOList.add(appFilter);

        //Owner filter
        ListFilterDTO ownerFilter = new ListFilterDTO(OWNER_FILTER, MessageKeys.UI_PAM_CONTAINERS_FILTER_OWNER,
                true, ListFilterDTO.DataTypes.Identity, locale);
        ownerFilter.configureSuggest(OWNER_CONTEXT, OWNER_ID, getSuggestUrl());
        filterDTOList.add(ownerFilter);

        return filterDTOList;
    }
}