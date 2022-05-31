/*
 *  (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.listfilter;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ServerStatistic;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ServerStatisticFilterContext extends BaseListFilterContext {

    public ServerStatisticFilterContext() {
        super(ObjectConfig.getObjectConfig(ServerStatistic.class));
    }

    private static final String PROPERTY_SERVER_STAT_SERVER = "server";
    private static final String PROPERTY_SERVER_STAT_STATS = "stats";
    private static final String PROPERTY_SERVER_STAT_SNAPSHOT = "snapshotName";


    @Override
    public List<ListFilterDTO> getDefaultFilters(SailPointContext context, Locale locale)
        throws GeneralException {

        List<ListFilterDTO> filters = new ArrayList<>();

        ListFilterDTO serverFilter = new ListFilterDTO("server", MessageKeys.UI_SERVER_STAT_FILTER_SERVER, false, ListFilterDTO.DataTypes.String, locale);
        filters.add(serverFilter);

        ListFilterDTO statFilter = new ListFilterDTO("stats", MessageKeys.UI_SERVER_STAT_FILTER_STATS, true, ListFilterDTO.DataTypes.String, locale);
        filters.add(statFilter);

        ListFilterDTO snapshotFilter = new ListFilterDTO("snapshotName", MessageKeys.UI_SERVER_STAT_FILTER_SNAPSHOT, false, ListFilterDTO.DataTypes.String, locale);
        filters.add(snapshotFilter);

        return filters;
    }


    @Override
    public Filter convertFilter(ListFilterDTO filterDTO, ListFilterValue filterValue, SailPointContext context)
        throws GeneralException {
        Filter filter = null;

        if (filterDTO != null && PROPERTY_SERVER_STAT_SERVER.equals(filterDTO.getProperty())) {
            if (filterValue.getValue() != null) {
                filter = Filter.eq("host.name", getValueString(filterValue));
            }
        } else if (filterDTO != null && PROPERTY_SERVER_STAT_STATS.equals(filterDTO.getProperty())) {
            if (filterValue.getValue() != null) {
                filter = Filter.in("name", getValueStringList(filterValue));
            }
        } else if (filterDTO != null && PROPERTY_SERVER_STAT_SNAPSHOT.equals(filterDTO.getProperty())) {
            if (filterValue.getValue() != null) {
                filter = Filter.eq("snapshotName", getValueString(filterValue));
            }
        } else {
            filter = super.convertFilter(filterDTO, filterValue, context);
        }

        return filter;
    }

}
