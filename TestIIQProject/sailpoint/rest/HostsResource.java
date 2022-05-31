/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.Server;
import sailpoint.object.UIConfig;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.service.ServerDTO;
import sailpoint.service.ServerService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.util.Sorter;


/**
 * A resource to get the information for active IdentityIQ hosts.
 *
 * @author <a href="mailto:colin.hume@sailpoint.com">Colin Hume</a>
 */
@Path("hosts")
public class HostsResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(HostsResource.class);

    @Override
    protected String getColumnKey() {
        String key = super.getColumnKey();
        return key != null ? key : UIConfig.SERVER_COLUMNS;
    }

    /**
     * Return a list of the Server objects
     */
    @GET
    public ListResult getHosts(@QueryParam("active") Boolean filterByActive,
            @QueryParam("services") Boolean showServices,
            @QueryParam("defaults") Boolean includeDefaults) throws GeneralException {

        //Used in ApplicationSelector, RequestAccessSelectAccountPanel, and Environment Monitoring
    	authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

    	ManualSortUtil sortUtil = new ManualSortUtil(getSortBy());
    	if (sortUtil.isManualSort()) {
            sortUtil.adjustOptions();
        }

    	QueryOptions qo = getQueryOptions(getColumnKey());

    	if (Util.isNotNullOrEmpty(query)) {
                qo.add(Filter.ignoreCase(Filter.like("name", this.query, Filter.MatchMode.START)));
        }

        if (filterByActive != null) {
            qo.addFilter(Filter.eq("inactive", !filterByActive.booleanValue()));
        }

        int count = getContext().countObjects(Server.class, qo);
        if(count>0) {
            log.debug( "You Have "+count+" IIQ instances running");
        }else{
            log.debug( "No server instances found");
        }

        //TODO: Could implement BaseListServiceContext and use BaseListService.
        //These objects are small, and shouldn't be too many, so skipping for now -rap
        List<Server> servers = getContext().getObjects(Server.class, qo);

        if (sortUtil.isManualSort()) {
            servers = sortUtil.sortAndTrim(servers);
        }

        List<ServerDTO> results = new ArrayList<>();
        for(Server server : servers){
            ServerService svc = getService(server.getId());
            results.add(svc.getServerDTO(showServices));
        }

        ListResult lr = new ListResult(results, count);
        return lr;
    }

    @Path("{serverId}")
    @GET
    public ServerDTO getServer(@PathParam("serverId") String serverId,
                               @QueryParam("services") Boolean showServices,
                               @QueryParam("defaults") Boolean includeDefaults)
            throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

        ServerService svc = getService(serverId);
        return svc.getServerDTO(showServices, includeDefaults);
    }

    @Path("{serverId}")
    @DELETE
    public Response deleteServer(@PathParam("serverId") String serverId)
            throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring));

        ServerService svc = getService(serverId);
        svc.deleteServer();

        // If we get this far without throwing, return OK
        return Response.ok().build();
    }

    @Path("{serverId}")
    @PUT
    public void updateServer(@PathParam("serverId") String serverId, Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring));

        try {
            String json = JsonHelper.toJson(data);
            ServerDTO serverDto = JsonHelper.fromJson(ServerDTO.class, json);
            ServerService svc = getService(serverId);
            svc.updateServer(serverDto);
        } catch(Exception e) {
            log.warn("Unable to Update Server: " + data, e);
            throw e;
        }
    }

    // To use PATCH, pass a parameter named "updates" with a value
    // described below:
    //
    // To turn a single service on:
    //   [ { "service":"myservice", "state":"include" } ]
    //
    // To turn a single service off:
    //   [ { "service":"myservice", "state":"exclude" } ]
    //
    // To defer startup logic to the service definition and
    // various other properties:
    //   [ { "service":"myservice", "state":"defer" } ]
    //
    // To perform multiple operations:
    //   [ { "service":"apple", "state":"include" },
    //     { "service":"grape", "state":"exclude" }
    //   ]
    //
    @Path("{serverId}")
    @PATCH
    public void patchServer(@PathParam("serverId") String serverId, Map<String, Object> values)
            throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring));


        if (Util.isEmpty(values)) {
            throw new InvalidParameterException("values");
        }

        ServerService svc = getService(serverId);
        svc.patch(values);
    }

    private ServerService getService(String serverId) throws GeneralException {
        return new ServerService(serverId, this);
    }

    /**
     * Utility class which encapsulates the nastiness of sorting by fields which are not database columns
     */
    class ManualSortUtil {

        final List<String> MANUAL_SORT_COLS = new ArrayList<String>
                (Arrays.asList(
                        Server.ATT_CPU_USAGE,
                        Server.ATT_MEMORY_USAGE,
                        Server.ATT_MEMORY_USAGE_PERCENTAGE,
                        Server.ATT_DATABASE_RESPONSE_TIME,
                        Server.ATT_REQUEST_THREADS,
                        Server.ATT_TASK_THREADS,
                        Server.ATT_OPEN_FILE_COUNT));

        String origSortByString;
        int origStart;
        int origLimit;

        Sorter manualSorter = null;


        ManualSortUtil(String sortByString) throws GeneralException {

            // hold onto the original sort string and paging controls
            origSortByString = sortByString;
            origStart = getStart();
            origLimit = getLimit();

            // Separate the Sorters into the the 2 houses: manual and normal.
            // There should be only a single Sorter in manual, or all in normal.
            List<Sorter> allSorters = parseSorters();

            List<Sorter> manualSorters = new ArrayList<Sorter>();
            List<Sorter> normalSorters = new ArrayList<Sorter>();

            if (allSorters != null) {
                for (Sorter sorter : allSorters) {
                    if (MANUAL_SORT_COLS.contains(sorter.getProperty())) {
                        manualSorters.add(sorter);
                    } else {
                        normalSorters.add(sorter);
                    }
                }

                // make sure that this is do-able
                validate(manualSorters, normalSorters);

                // set our sorter
                if (manualSorters.size() == 1) {
                    manualSorter = manualSorters.get(0);
                }
            }
        }

        boolean isManualSort() {
            return manualSorter != null;
        }

        void adjustOptions() {
            if (isManualSort()) {
                // disable paging and sorting
                setLimit(0);
                setStart(0);
                setSortBy(null);
            }
        }

        /**
         * ky - pretty restrictive currently.  If you ask to sort manually, there can only be a single
         * sort column.
         * @throws GeneralException
         */
        void validate(List<Sorter> manualSorters, List<Sorter> normalSorters) throws GeneralException {
            if (manualSorters.size() > 1 ||
                    (manualSorters.size() == 1 && normalSorters.size() > 0)) {
                throw new GeneralException("Invalid sort '" + origSortByString + "'");
            }
        }

        List<Server> sortAndTrim(List<Server> origServers) {
            if (Util.isEmpty(origServers) || !isManualSort()) {
                return origServers;
            }
            else {
                String propertyName = manualSorter.getProperty();
                boolean isAscending = manualSorter.isAscending();

                // sort
                Comparator<Server> comparator =
                        new ServerStatisticComparatorBigDecimal(propertyName);
                if (!isAscending) {
                    // reverse the comparator
                    comparator = Collections.reverseOrder(comparator);
                }
                Server[] serverArray = origServers.toArray(new Server[0]);
                Arrays.sort(serverArray, comparator);

                // trim
                ArrayList<Server> result = new ArrayList<Server>();
                for(int i=origStart; i < serverArray.length && (origLimit <= 0 || i < origLimit); i++) {
                    result.add(serverArray[i]);
                }

                return result;
            }
        }

        /**
         * Comparator used to sort Server objects by a given attribute
         */
        class ServerStatisticComparatorBigDecimal implements Comparator<Server> {
            String _attributeName;

            ServerStatisticComparatorBigDecimal(String attributeName) {
                _attributeName = attributeName;
            }

            @Override
            public int compare(Server server1, Server server2) {
                Object field1 = server1.get(_attributeName);
                Object field2 = server2.get(_attributeName);
                BigDecimal val1 = null;
                BigDecimal val2 = null;

                if (field1 != null) {
                    val1 = new BigDecimal(field1.toString());
                }
                if (field2 != null) {
                    val2 = new BigDecimal(field2.toString());
                }

                if (val1 == null) {
                    return -1;
                }
                else if (val2 == null) {
                    return 1;
                }
                else {
                    int cmp = val1.compareTo(val2);
                    return cmp;
                }
            }

        }

    }
    
}
