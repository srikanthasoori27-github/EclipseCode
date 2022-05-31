/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest;

import java.util.Collections;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;

/**
 * @author danny.feng
 *
 */
@Path("quicklinks")
public class QuickLinkListResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(QuickLinkListResource.class);

    /**
     * Return a ListResult of all QuickLinks.
     *
     * @return A ListResult of QuickLinks.
     */
    @GET
    public ListResult getList() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewQuickLinks));

        String colKey = getColumnKey();
        QueryOptions qo = getQueryOptions(colKey);
        
        qo.addFilter(Filter.eq("hidden", false));
        
        ListResult result = null;

        try {
            result = getListResult(colKey, QuickLink.class, qo);
        } catch (GeneralException e) {
            log.error(e);
            result = new ListResult(Collections.EMPTY_LIST, 0);
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(e.getLocalizedMessage());
        }

        return result;
    }
}
