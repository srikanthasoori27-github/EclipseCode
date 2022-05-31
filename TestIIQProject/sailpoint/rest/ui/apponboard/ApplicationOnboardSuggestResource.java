/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.apponboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;
import static sailpoint.tools.Util.otoi;

public class ApplicationOnboardSuggestResource extends SuggestResource {

    String applicationId;

    public ApplicationOnboardSuggestResource (String applicationId, BaseResource parent, SuggestAuthorizerContext authorizerContext) {
        super(parent, authorizerContext);
        this.applicationId = applicationId;
    }


    /**
     * This method overrides the default getColumnSuggestViaHttpPost method in SuggestResource. There is special
     * logic in the specific case that the object is ManagedAttribute and the column is value
     * @param suggestClass SailPoint object simple class name
     * @param suggestColumn Column/property name on the class
     * @param inputs the post data may contain start, limit, query, sortBy, sortDirection, and filterString.
     * @return
     * @throws GeneralException
     * @throws ClassNotFoundException
     */

    @POST
    @Path("column/{class}/{column}")
    @Override
    public ListResult getColumnSuggestViaHttpPost(@PathParam("class") String suggestClass, @PathParam("column") String suggestColumn, Map<String, Object> inputs)
            throws GeneralException, ClassNotFoundException {
        if (ManagedAttribute.class.getSimpleName().equals(suggestClass) && "value".equals(suggestColumn)) {
            // IIQMAG-3037 We want to show the display name for the value column in the UI to make it easier for
            // end users to read. The default column suggest logic doesn't support this so we will perform special
            // logic when the requested column suggest is for ManagedAttribute.value
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("application.id", applicationId));
            if (Util.isNotNullOrEmpty((String)inputs.get("filterString"))) {
                qo.add(Filter.compile((String) inputs.get("filterString")));
            }
            if (Util.isNotNullOrEmpty((String)inputs.get("query"))) {
                qo.add(Filter.like("displayableName", inputs.get("query"), Filter.MatchMode.START));
            }

            qo.setResultLimit(WebUtil.getResultLimit(
                    otoi(inputs.get(PARAM_LIMIT))));

            qo.setFirstRow(otoi(inputs.get(PARAM_START)));

            qo.setDistinct(true);
            qo.add(Filter.notnull("value"));
            int count = getContext().countObjects(ManagedAttribute.class, qo);
            qo.addOrdering("displayableName", true, true);
            List<Map<String, Object>> results = new ArrayList<>();

            Iterator<ManagedAttribute> searchResults = getContext().search(ManagedAttribute.class, qo);
            while (searchResults.hasNext()) {
                ManagedAttribute searchResult = searchResults.next();
                HashMap<String, Object> result = new HashMap<>();
                result.put("id", searchResult.getValue());
                String displayName = searchResult.getDisplayableName();
                result.put("displayName", displayName);
                results.add(result);
            }
            return new ListResult(results, count);
        }
        else {
            return super.getColumnSuggestViaHttpPost(suggestClass,suggestColumn,inputs);
        }
    }
}
