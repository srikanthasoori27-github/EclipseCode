/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseObjectSuggestBean;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * JSF bean for group suggest component.
 *
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
public class GroupDefinitionSuggestBean extends BaseObjectSuggestBean<GroupDefinition> {

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {

        QueryOptions qo = new QueryOptions();        
        getSortOrdering(qo);

        String group = (String)super.getRequestParam().get("group");
        if ((null != group && !group.equals(""))) {
            
            if(GroupFactoryListBean.ATT_POPULATIONS.equals(group)) {
                Identity owningUser = getLoggedInUser();
                PopulationFilterUtil.addPopulationOwnerFiltersToQueryOption( qo, owningUser );
            } else {
                qo.add(Filter.eq("factory.name", group));                
            }
            
        } else {
            qo.add(Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START)));
            qo.add(Filter.or(Filter.eq("private", false),Filter.eq("owner", getLoggedInUser())));
        }

        if(!Util.isNullOrEmpty(query)) {
            qo.add(Filter.ignoreCase(Filter.like("name", query, Filter.MatchMode.START)));
        }
        qo.setScopeResults(true);

        return qo;
    }

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();
        cols.add("id");
        cols.add("name");
        cols.add("factory.name");
        // do we need the path of the scope to differentiate?
        return cols;
    }

    @Override
    public Map<String,Object> convertRow(Object[] row, List cols)
            throws GeneralException {

        Map<String,Object> converted = super.convertRow(row, cols);
        Object factoryName = converted.get("factory.name");
        //If there is no factory name, this is for Populations
        converted.put("factory", (factoryName == null) ? getMessage(MessageKeys.POPULATIONS) : factoryName);

        return converted;
    }
}
