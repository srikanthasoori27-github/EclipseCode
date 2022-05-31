/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectSuggestBean;


/**
 * JSF bean for scope suggest component.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ScopesSuggestBean extends BaseObjectSuggestBean<Scope> {
	
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "displayName";
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {

        QueryOptions qo = new QueryOptions();        
        getSortOrdering(qo);

        if (null != Util.getString(query)) {
            qo.add(Filter.ignoreCase(Filter.like("displayName", query, Filter.MatchMode.START)));
        }

        qo.setScopeResults(true);

        return qo;
    }

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();
        cols.add("id");
        cols.add("displayName");
        // do we need the path of the scope to differentiate?
        return cols;
    }

    @Override
    public Map<String,Object> convertRow(Object[] row, List cols)
        throws GeneralException {

        Map<String,Object> converted = super.convertRow(row, cols);

        // Brute force a path conversion ... 
        Scope scope =
            getContext().getObjectById(Scope.class, (String) converted.get("id"));
        
        // Think about not including "scope" in the path.  In other words, just
        // show the ancestors.
        String path = Util.truncateFront(scope.getDisplayablePath(), 24);
        converted.put("IIQ_path", path);
        
        return converted;
    }
}
