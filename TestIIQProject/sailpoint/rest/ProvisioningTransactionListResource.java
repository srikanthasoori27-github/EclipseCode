/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.ProvisioningTransaction.Status;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ProvisioningTransactionListService;
import sailpoint.service.ReportRunnerService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.Sorter;

/**
 * List resource class for Provisioning Transaction UI
 * Extending BaseListResource will get us paging for free
 * @author brian.li
 *
 */
@Path("provisioningTransactions")
public class ProvisioningTransactionListResource extends BaseListResource implements BaseListServiceContext {

    private static final Log log = LogFactory.getLog(ProvisioningTransactionListResource.class);
    protected static final String PROVISIONING_TRANSACTION_REPORT_NAME = "Provisioning Transaction Object Report";
    protected static final String PROVISIONING_TRANSACTION_REPORT_RIGHT = "FullAccessProvisioningTransactionReport";
    protected static final String STATUS_PROPERTY = "status";
    protected static final String TOTAL_STATUS_COUNT = "total";

    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.PROVISIONING_TRANSACTION_COLUMNS;
    }

    /**
     * Method for the UI to run the selected filters as a report
     * @return Response A built Response object wth the id of the TaskResult
     * @throws Exception 
     */
    @GET
    @Path("runReport")
    public Response runReport(@QueryParam("applicationName") List<String> applicationNames,
            @QueryParam("identityName") List<String> identityNames,
            @QueryParam("accountDisplayName") String accountDisplayName,
            @QueryParam("status") String status,
            @QueryParam("type") String type,
            @QueryParam("source") List<String> sources,
            @QueryParam("operation") List<String> operations,
            @QueryParam("startDateRange") String startDateRange,
            @QueryParam("endDateRange") String endDateRange,
            @QueryParam("integration") List<String> integrations,
            @QueryParam("forced") Boolean forced,
            @QueryParam("columns") String columns) throws Exception {
        authorize(new RightAuthorizer(PROVISIONING_TRANSACTION_REPORT_RIGHT));
        Attributes<String, Object> definitionArgs = new Attributes<String, Object>();

        if (!Util.isEmpty(applicationNames)) {
            definitionArgs.put("applications", applicationNames);
        }
        if (!Util.isEmpty(identityNames)) {
            definitionArgs.put("identities", identityNames);
        }
        if (Util.isNotNullOrEmpty(accountDisplayName)) {
            definitionArgs.put("accountDisplayName", accountDisplayName);
        }
        if (Util.isNotNullOrEmpty(status)) {
            ArrayList<String> list = new ArrayList<String>();
            list.add(status);
            definitionArgs.put("status", list);
        }
        if (Util.isNotNullOrEmpty(type)) {
            ArrayList<String> list = new ArrayList<String>();
            list.add(type);
            definitionArgs.put("type", list);
        }
        if (!Util.isEmpty(sources)) {
            definitionArgs.put("source", sources);
        }
        if (!Util.isEmpty(operations)) {
            definitionArgs.put("operation", operations);
        }
        if (null != forced) {
            definitionArgs.put("forced", forced);
        }


        if (Util.isNotNullOrEmpty(startDateRange) || Util.isNotNullOrEmpty(endDateRange)) {
            Attributes<String, Object> dates = new Attributes<String, Object>();
            if (Util.isNotNullOrEmpty(startDateRange)) {
                Date start = getDateFromParam(startDateRange, 0);
                if (start != null) {
                    dates.put("start", start.getTime());
                }
            }
            if (Util.isNotNullOrEmpty(endDateRange)) {
                Date end = getDateFromParam(endDateRange, 1);
                if (end != null) {
                    dates.put("end", end.getTime());
                }
            }
            definitionArgs.put("creationDate", dates);
        }

        if (!Util.isEmpty(integrations)) {
            definitionArgs.put("integration", integrations);
        }

        definitionArgs.put("disableSummary", false);
        definitionArgs.put("disableDetail", false);
        if (Util.isNotNullOrEmpty(columns)) {
            definitionArgs.put("reportColumnOrder", columns);
        }
        List<ColumnConfig> cols = this.getColumns(getColumnKey());
        List<Sorter> sorts = this.getSorters(cols);
        if (!Util.isEmpty(sorts)) {
            Sorter sort = sorts.get(0);
            definitionArgs.put("reportSortBy", sort.getSortProperty());
            definitionArgs.put("reportSortAsc", sort.isAscending());
        }

        ReportRunnerService service = new ReportRunnerService();
        try {
             String result = service.runReport(this, PROVISIONING_TRANSACTION_REPORT_NAME, definitionArgs);
             return Response.ok(result).build();
        }
        catch (GeneralException e) {
            if (log.isWarnEnabled()) {
                log.warn(e);
            }
            return Response.serverError().entity(e.getLocalizedMessage()).build();
        }
    }

    /**
     * Method for the UI to populate the number of PTOs that are on each tab 
     * @return Map that contains the number of PTOs to each status
     * @throws GeneralException
     */
    @GET
    @Path("statusCounts")
    public Map<String, Object> getStatusCount() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessProvisioningTransaction, SPRight.ViewProvisioningTransaction));
        Map<String, Object> result = new HashMap<String, Object>();
        int total = 0;
        for (Status s : ProvisioningTransaction.Status.values()) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq(STATUS_PROPERTY, s));
            int count = countResults(ProvisioningTransaction.class, qo);
            total += count;
            result.put(s.toString().toLowerCase(), count);
        }
        result.put(TOTAL_STATUS_COUNT, total);

        return result;
    }

    @GET
    public ListResult getTransactions(@QueryParam("applicationName") List<String> applicationNames,
                                      @QueryParam("identityName") List<String> identityNames,
                                      @QueryParam("accountDisplayName") String accountDisplayName,
                                      @QueryParam("status") String status,
                                      @QueryParam("type") String type,
                                      @QueryParam("source") List<String> sources,
                                      @QueryParam("operation") List<String> operations,
                                      @QueryParam("startDateRange") String startDateRange,
                                      @QueryParam("endDateRange") String endDateRange,
                                      @QueryParam("integration") List<String> integrations,
                                      @QueryParam("forced") Boolean forced)
                                                  throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessProvisioningTransaction, SPRight.ViewProvisioningTransaction));
        String columnKey = getColumnKey();
        QueryOptions qo = getQueryOptions(columnKey);

        if (!Util.isEmpty(applicationNames)) {
            qo.add(buildMultiValuedFilter(applicationNames, "applicationName"));
        }
        if (!Util.isEmpty(identityNames)) {
            qo.add(buildMultiValuedFilter(identityNames, "identityName"));
        }
        if (Util.isNotNullOrEmpty(accountDisplayName)) {
            qo.add(Filter.ignoreCase(Filter.like("accountDisplayName", accountDisplayName, Filter.MatchMode.START)));
        }
        if (Util.isNotNullOrEmpty(status)) {
            qo.add(Filter.eq("status", status));
        }
        if (Util.isNotNullOrEmpty(type)) {
            qo.add(Filter.eq("type", type));
        }
        if (!Util.isEmpty(sources)) {
            qo.add(buildMultiValuedFilter(sources, "source"));
        }
        if (!Util.isEmpty(operations)) {
            qo.add(buildMultiValuedFilter(operations, "operation"));
        }
        if (Util.isNotNullOrEmpty(startDateRange)) {
            Date start = getDateFromParam(startDateRange, 0);
            if (start != null) {
                qo.add(Filter.ge("created", start));
            }
        }
        if (Util.isNotNullOrEmpty(endDateRange)) {
            Date end = getDateFromParam(endDateRange, 1);
            if (end != null) {
                qo.add(Filter.le("created", end));
            }
        }

        if (!Util.isEmpty(integrations)) {
            qo.add(buildMultiValuedFilter(integrations, "integration"));
        }

        if (null != forced) {
            qo.add(Filter.eq("forced", forced));
        }

        if (Util.isNotNullOrEmpty(query)) {
            qo.add(
                Filter.or(
                    Filter.ignoreCase(Filter.like("identityName", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("identityDisplayName", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("applicationName", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("accountDisplayName", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("nativeIdentity", query, Filter.MatchMode.START)),
                    Filter.eq("name", Util.padID(query))
                )
            );
        }

        BaseListResourceColumnSelector selector = new BaseListResourceColumnSelector(columnKey);
        ProvisioningTransactionListService service = new ProvisioningTransactionListService(getContext(), this, selector);
        
        return service.getTransactions(qo, this);
    }

    /** Sub Resource Methods **/
    @Path("{transactionId}")
    public ProvisioningTransactionResource getTransaction(@PathParam("transactionId") String transactionId)
    throws GeneralException {
        return new ProvisioningTransactionResource(transactionId, this);
    }

    /**
     * Get the SuggestResource for the filters on the transactions
     * @return SuggestResource
     * @throws GeneralException
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessProvisioningTransaction, SPRight.ViewProvisioningTransaction));

        // Since the Filters are built on the client (whyyyyyyy?) we will build this manually.
        // TODO: Build filters on the server
        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext()
                .add(ProvisioningTransaction.class.getSimpleName(), false, "applicationName", "integration")
                .add(Identity.class.getSimpleName());

        return new SuggestResource(this, authorizerContext);
    }

    /**
     * Builds an OR filter from the list of values for the property.
     *
     * @param values The values.
     * @param property The property.
     * @return The or filter.
     */
    private Filter buildMultiValuedFilter(List<String> values, String property) {
        List<Filter> childFilters = new ArrayList<Filter>();
        for (String name : Util.iterate(values)) {
            childFilters.add(Filter.eq(property, name));
        }

        return Filter.or(childFilters);
    }

    /**
     * Gets a date from the string parameter.
     *
     * @param param The string parameter.
     * @param idx The token index to parse.
     * @return The date or null if invalid.
     */
    private Date getDateFromParam(String param, int idx) {
        Date date = null;

        String[] dateTokens = param.split("\\|");
        if (dateTokens.length > idx) {
            Date parsedDate = Util.getDate(dateTokens[idx]);
            if (parsedDate != null) {
                date = parsedDate;
            }
        }

        return date;
    }

}
