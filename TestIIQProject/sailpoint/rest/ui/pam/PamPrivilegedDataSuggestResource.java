/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.pam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.Target;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.suggest.BaseSuggestResource;
import sailpoint.service.pam.ContainerService;
import sailpoint.service.pam.PamPrivilegedDataSuggestService;
import sailpoint.service.pam.PamUtil;
import sailpoint.service.suggest.SuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;

import static sailpoint.tools.Util.otoi;

public class PamPrivilegedDataSuggestResource extends BaseSuggestResource {

    private static Log log = LogFactory.getLog(PamPrivilegedDataSuggestResource.class);

    private static final String PD_VALUE = "privilegedData.value";

    String containerId;

    public PamPrivilegedDataSuggestResource(String containerId, BaseResource parent, SuggestAuthorizerContext authorizerContext) {
        super(parent, authorizerContext);
        this.containerId = containerId;
    }

    /**
     * Get the list of privileged data managed attributes for the application that is associated with the containerId
     * @param inputs
     * @return
     * @throws GeneralException
     */
    @POST
    @Path("object/{class}")
    public ListResult getSuggestViaHttpPost(@PathParam("class") String suggestClass, Map<String, Object> inputs)
            throws GeneralException {
        List<Map<String, Object>> results = new ArrayList<>();
        if (ManagedAttribute.class.getSimpleName().equals(suggestClass)) {

            PamPrivilegedDataSuggestService suggestService =
                    new PamPrivilegedDataSuggestService(getContext(), getLoggedInUser(), containerId, inputs);
            int count = suggestService.getPrivilegedItemsForContainerCount();
            Iterator<ManagedAttribute> searchResults = suggestService.getPrivilegedItemsForContainer();
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
        return new ListResult(results, 0);
    }

}
