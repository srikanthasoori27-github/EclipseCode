package sailpoint.service;

import java.util.List;

import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;
import sailpoint.web.util.Sorter;

/**
 * Interface for common options for paging lists to be passed from a resource to a service layer object.
 */
public interface BaseListServiceContext extends UserContext {

    int getStart();

    int getLimit();

    String getQuery();

    List<Sorter> getSorters(List<ColumnConfig> columnConfigs) throws GeneralException;
    
    String getGroupBy();

    List<Filter> getFilters();
}
