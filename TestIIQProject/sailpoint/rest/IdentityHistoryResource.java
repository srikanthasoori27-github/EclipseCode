/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityHistoryService;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.identity.IdentityHistoryUtil;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workitem.WorkItemUtil;

/**
 * A sub-resource to deal with history on an identity.
 *
 * LOTS of the code in here is shared with the IdentityHistoryExportBean
 * since so much of the search work is common to both.  The result is a
 * lot of static methods
 *
 * @author <a href="mailto:derry.cannon@sailpoint.com">Derry Cannon</a>
 */
public class IdentityHistoryResource extends BaseListResource implements BaseListServiceContext {
    private static final Log log = LogFactory.getLog(IdentityHistoryResource.class);

    private static final String ITEM_ID = "itemId";

    private String identity;

    private BaseListResourceColumnSelector identityHistoryTableByItemColumnSelector =
            new BaseListResourceColumnSelector(UIConfig.IDENTITY_HISTORY_BY_ITEM_TABLE_COLUMNS);

    /**
     * Default constructor
     */
    public IdentityHistoryResource() {
    }


    /**
     * Sub-resource constructor.
     */
    public IdentityHistoryResource(String identity, BaseResource parent) {
        super(parent);
        this.identity = identity;
    }


    /**
     * Return the identity we're operating on.
     */
    private Identity getIdentity() throws GeneralException {
        Identity i = getContext().getObjectById(Identity.class, this.identity);
        if (i == null) {
            throw new ObjectNotFoundException(Identity.class, this.identity);
        }
        return i;
    }


    /**
     * Return the history item with the given id.
     *
     * @return A ListResult with details about the history items.
     */
    @GET
    @Path("item")
    public Map<String, Object> getIdentityHistoryItem(@QueryParam("id") String id) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewIdentity, SPRight.MonitorIdentityHistory));

        if (Util.isNullOrEmpty(id)) {
            throw new InvalidParameterException("id is a required query parameter.");
        }

        // this might look like the long way around, but it gets us
        // the most mileage from existing code
        QueryOptions qo = getQueryOptions();

        qo.add(Filter.eq("id", id));

        List<Map<String, Object>> results = getResults(UIConfig.IDENTITY_HISTORY_TABLE_COLUMNS,
                IdentityHistoryItem.class, qo);


        if (results == null || results.size() < 1) {
            throw new ObjectNotFoundException(IdentityHistoryItem.class, id);
        } else {
            Map<String, Object> map = results.get(0);
            // IIQMAG-1272 check all fields that are are returned (and possibly
            // displayed in a browser) for any xss problems.
            for(String key : map.keySet()) {
                Object value = map.get(key);
                if(value instanceof String) {
                    map.put(key, WebUtil.safeHTML((String)value));
                }
            }
            return map;
        }
    }


    /**
     * Return the history items for the identity, filtering as needed.
     *
     * @return A ListResult with details about the history items.
     */
    @GET
    public ListResult getIdentityHistoryItems() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewIdentity, SPRight.MonitorIdentityHistory));

        QueryOptions qo = this.getQueryOptions();

        qo.add(Filter.eq("identity.id", getIdentity().getId()));

        List<Map<String, Object>> results = getResults(UIConfig.IDENTITY_HISTORY_TABLE_COLUMNS,
                IdentityHistoryItem.class, qo);

        return new ListResult(results, countResults(IdentityHistoryItem.class, qo));
    }


    /**
     * Return the history items for the identity on the given cert item, filtering as needed.
     *
     * @return A ListResult with details about the history items.
     */
    @GET
    @Path("byItem")
    public ListResult getIdentityHistoryByItem(@QueryParam(ITEM_ID) String itemId) throws GeneralException {
        if (Util.isNullOrEmpty(itemId)) {
            throw new InvalidParameterException(ITEM_ID + " is a required query parameter.");
        }

        CertificationItem item = getContext().getObjectById(CertificationItem.class, itemId);
        if (item == null) {
            throw new ObjectNotFoundException(CertificationItem.class, itemId);
        }

        String workItemId = WorkItemUtil.getWorkItemId(getContext(), itemId);

        authorize(new CertificationAuthorizer(item.getCertification(), workItemId));

        // create identity history service
        IdentityHistoryService historyService = new IdentityHistoryService(this, identityHistoryTableByItemColumnSelector);

        return historyService.getIdentityHistory(getIdentity().getId(), item);
    }

    /**
     * An override that specifies a list of projection columns that is
     * different from the list of columns that would be returned by the
     * UIConfig for the given columnsKey.  This lets us leverage the work
     * being done in the superclass without being tied exclusively to
     * the UIConfig.
     *
     * @param columnsKey Key to the table config
     * @return Modified list of column names
     * @throws GeneralException
     */
    @Override
    public List<String> getProjectionColumns(String columnsKey) throws GeneralException {
        List<String> cols = super.getProjectionColumns(columnsKey);

        if (UIConfig.IDENTITY_HISTORY_TABLE_COLUMNS.equals(columnsKey)) {
            cols = IdentityHistoryUtil.supplementProjectionColumns(cols);
        }

        return cols;
    }


    /**
     * Converts the row returned from the db to the format needed by the
     * data store on the UI side.  There's a lot of tweaking and fiddling
     * that needs to take place before it's useful to the UI.
     */
    @Override
    public Map<String, Object> convertRow(Object[] row, List<String> cols, String columnsKey)
            throws GeneralException {
        Map<String, Object> map = super.convertRow(row, cols, columnsKey);

        if (UIConfig.IDENTITY_HISTORY_TABLE_COLUMNS.equals(columnsKey)) {
            return IdentityHistoryUtil.convertRow(map, row, cols, getLocale(), getContext());
        }
        else {
            return IdentityHistoryUtil.convertRow(map, getLocale());
        }
    }


    /**
     * Build the query options based on the request's query parameters.
     *
     * @return QueryOptions object
     * @throws GeneralException
     */
    public QueryOptions getQueryOptions() throws GeneralException {
        // deal with some type conversion issues in using a MultivaluedMap
        Map<String, Object> map = new HashMap<String, Object>();
        map.putAll(this.uriInfo.getQueryParameters());

        return IdentityHistoryUtil.getQueryOptions(map, super.getQueryOptions(UIConfig.IDENTITY_HISTORY_TABLE_COLUMNS),
                getLocale());
    }
}
