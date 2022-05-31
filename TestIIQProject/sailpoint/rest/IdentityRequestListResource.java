/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.IdentityRequestAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequest.ExecutionStatus;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem.Level;
import sailpoint.service.RequestAccessService;
import sailpoint.service.identityrequest.IdentityRequestService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.AccessRequestSearchSettings;
import sailpoint.web.Authorizer;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.Sorter;
import sailpoint.web.util.WebUtil;

/**
 * Rest resource for IdentityRequest lists
 *
 * @author: patrick.jeong@sailpoint.com
 */
@Path("identityRequests")
public class IdentityRequestListResource extends BaseListResource{

    private static final Log log = LogFactory.getLog(IdentityRequestListResource.class);

    private Date startDate;
    private Date endDate;
    private String status;
    private String requester;
    private String type;
    private String priority;
    private String requestId;
    private String identity;
    private String identityName;
    private String requestType;
    private List<String> groups;
    private boolean isDashboardWidget;
    private String externalTicketId;

    /**
     * Returns list of task results matching the given query parameters.
     * @param type
     * @return
     * @throws GeneralException 
     */
    @POST
    public ListResult list(@FormParam("start") int startParm,@FormParam("requestType") String requestType,@FormParam("isDashboardWidget") String isDashboardWidget,
            @FormParam("limit") int limitParm, @FormParam("identity") String requesteeParm, @FormParam("identityName") String identityNameParm,
            @FormParam("sort") String sortFieldParm, @FormParam("dir") String sortDirParm,@FormParam("status") String requestStatus,
            @FormParam("statusParm") String statusParm, @FormParam("groups") String groupsParm,
            @FormParam("startDate") long startDateParm, @FormParam("endDate") long endDateParm,
            @FormParam("requester") String requester, @FormParam("type") String type,
            @FormParam("priority") String priorityParm,@FormParam("requestId") String requestIdParm, 
            @FormParam("saveSearch") String saveSearch, @FormParam("extTicketId") String extTicketIdParm) throws GeneralException{

        try {
            authorize(new IdentityRequestAuthorizer(requester, requesteeParm));
        }
        catch(UnauthorizedAccessException unauth) {
            ListResult result =  new ListResult(new ArrayList(), 0);
            result.addError(unauth.getLocalizedMessage());
            return result;
        }

        // Need to store search fields in session so we can remember then the next time we come back to this view
        HttpSession session = getRequest().getSession();
        AccessRequestSearchSettings settings = new AccessRequestSearchSettings();
        if (saveSearch != null) {
            settings.setRequestType(requestType);
            settings.setRequestStatus(statusParm);
            settings.setRequestId(requestIdParm);
            settings.setRequestPriority(priorityParm);
            settings.setStartDate(startDateParm);
            settings.setEndDate(endDateParm);
            if (requesteeParm != null) 
                settings.setRequestee(getContext().getObjectById(Identity.class, requesteeParm));
            if (requester != null)
                settings.setRequester(getContext().getObjectById(Identity.class, requester));
            settings.setGroups(groupsParm);
            settings.setExternalTicketId(extTicketIdParm);
            session.setAttribute("arSearchSettings", settings);
        }

        // todo need to look at updating BaseListResource so that we can handle post as well as get
        this.start = startParm;
        this.limit = WebUtil.getResultLimit(limitParm);
        this.sortBy = sortFieldParm;
        this.sortDirection = sortDirParm;
        this.identity = requesteeParm;
        this.requester = requester;
        this.requestType = requestType;
        this.priority = priorityParm;
        this.requestId = requestIdParm;
        this.identityName = identityNameParm;
        this.isDashboardWidget = Boolean.valueOf(isDashboardWidget);
        this.type = type;
        this.externalTicketId = extTicketIdParm;

        /** In case the request includes an empty sorty string **/
        if(Util.isNullOrEmpty(sortBy)) {
            sortBy = "id";
        }

        if (groupsParm != null)
            groups = Util.csvToList(groupsParm);


        if (startDateParm != 0)
            this.startDate = new Date(startDateParm);
        if (endDateParm != 0){
            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date(endDateParm));
            cal.add(Calendar.DAY_OF_YEAR, 1);
            this.endDate = cal.getTime();
        }

        status = requestStatus;

        if (statusParm != null && statusParm.length() > 0)
            status = statusParm;

        List<Map> results = new ArrayList<Map>();
        List<IdentityRequest> identityRequests = null;
        String error = null;

        int totalCount = 0;

        try {
            // try converting requester to id 
            if (requester != null){
                QueryOptions idOps = new QueryOptions(Filter.eq("name", requester));
                Iterator<Object[]> iter =
                        getContext().search(Identity.class, idOps, Arrays.asList("id"));
                if (iter.hasNext())
                    this.requester = (String)iter.next()[0];
            }

            QueryOptions ops = getQueryOptions();

            totalCount = getContext().countObjects(IdentityRequest.class, ops);

            identityRequests = getContext().getObjects(IdentityRequest.class, ops);

            if (identityRequests != null){
                for(IdentityRequest request : identityRequests){
                    HashMap<String, String> row = new HashMap<String, String>();
                    row.put("id", request.getId());
                    row.put("name", Util.stripLeadingChar(request.getName(), '0'));
                    row.put("priority", convertPriority(request.getPriority()));
                    row.put("type", request.getUserFriendlyType(getLocale(), getUserTimeZone()));

                    String requesterIdentity = request.getRequesterDisplayName();                    
                    String reqType = request.getType();
                    if ( Util.nullSafeEq(reqType, IdentityRequest.FORGOT_PASSWORD_FLOW) ||
                            Util.nullSafeEq(reqType, IdentityRequest.EXPIRED_PASSWORD_FLOW) ) {
                        // in the case of forgotpassword or expirepassword the requester name
                        // is something like 'sailpointcontextrequestfilter'. since this is a self service request
                        // replace this with the targetdisplayname
                        requesterIdentity = request.getTargetDisplayName();
                    }
                    row.put("requesterDisplayName", requesterIdentity);

                    String dateStr = Internationalizer.getLocalizedDate(request.getCreated(), getLocale(),
                            getUserTimeZone());
                    row.put("created", dateStr);
                    row.put("targetDisplayName", request.getTargetDisplayName());
                    row.put("state", request.getState());

                    String executionStatus = null;
                    ExecutionStatus status = request.getExecutionStatus();
                    if ( status != null ) {
                        executionStatus = localize(status.getMessageKey());
                    }
                    row.put("executionStatus", executionStatus);
                    row.put("completionStatus", request.getCompletionStatus() == null ? localize(MessageKeys.IDENTITY_REQUEST_COMP_PENDING) : localize(request.getCompletionStatus().getMessageKey()));

                    String verifiedDate = Internationalizer.getLocalizedDate(request.getVerified(), getLocale(), getUserTimeZone());
                    row.put("verified", verifiedDate);

                    String completeDate = "";
                    if (request.getEndDate() != null){
                        completeDate =
                                Internationalizer.getLocalizedDate(request.getEndDate(), getLocale(), getUserTimeZone());
                    }

                    row.put("endDate", completeDate);
                    row.put("externalTicketId", request.getExternalTicketId());
                    results.add(row);
                }
            }
        } catch (Throwable t) {
            log.error(t);
            error = t.getMessage();
        }


        ListResult result = null;
        if (error != null){
            result =  new ListResult(new ArrayList(), 0);
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(error);
        } else {
            result = new ListResult(results, totalCount);
        }

        return result;
    }

    /**
     * Simple conversion for priority
     * 
     * @param level
     * @return
     */
    private String convertPriority(Level level) {
        String key = null;
        if (level == null) {
            key = Level.Normal.getMessageKey();
        }
        else {
            key = level.getMessageKey();
        }
        return localize(key);
    }

    @SuppressWarnings("unchecked")
    protected QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = new QueryOptions();

        if (start > 0)
            qo.setFirstRow(start);

        if (limit > 0)
            qo.setResultLimit(limit);

        if (this.sortBy != null) {
            // if it starts with a bracket, we can assume it is a JSON array of sorters, ExtJS Store style.
            if(this.sortBy.startsWith("[")) {
                List<Sorter> sorters = JsonHelper.listFromJson(Sorter.class, this.sortBy);
                for(Sorter sorter : sorters) {
                    qo.addOrdering(sorter.getProperty(), sorter.isAscending());
                }
            } else {
                qo.addOrdering(this.sortBy, "ASC".equals(this.sortDirection));
            }
        } else {
            qo.addOrdering("created", false);
        }

        if (this.type != null) {
            qo.add(Filter.eq("source", this.type));
        }

        if (this.requestType != null && this.requestType.length() > 0) {
            if (requestType.equals(RequestAccessService.FLOW_CONFIG_NAME)) {
                String[] types = {"RolesRequest", "EntitlementsRequest", RequestAccessService.FLOW_CONFIG_NAME};
                qo.add(Filter.in("type", Arrays.asList(types)));  
            }
            else {
                qo.add(Filter.eq("type", this.requestType));
            }
        }

        List<Filter> identityFilters = new ArrayList<Filter>();
        if (identity != null && identity.length() > 0) {
            qo.add(Filter.eq("targetId", identity));
        } else if (groups != null && groups.size() > 0) {
            Iterator<Object[]> groupIter = getContext().search(
                    GroupDefinition.class,
                    new QueryOptions(Filter.in("id", groups)),
                    Arrays.asList("filter"));
            while (groupIter != null && groupIter.hasNext()) {
                Object[] row = groupIter.next();
                Filter filter = (Filter) row[0];
                if (filter != null)
                    identityFilters.add(filter);
            }
            if (!identityFilters.isEmpty())
                qo.add(Filter.subquery("targetId", Identity.class, "id", Filter.and(identityFilters)));
        }

        // allow non restricted search if user can view all results
        if (canViewAllResults() && !isDashboardWidget) {
            if (requester != null && requester.length() > 0) {
                qo.add(Filter.eq("requesterId", requester));
            }

            if (identity != null && identity.length() > 0) {
                qo.add(Filter.eq("targetId", identity));
            }

            if (identityName != null && identityName.length() > 0) {
                qo.add(Filter.subquery("targetId", Identity.class, "id", Filter.ignoreCase(Filter.like("name", identityName, Filter.MatchMode.START))));
            }
        } else {
            // if user has restricted access. restrict search to requests made
            // by or for user.
            if (requester != null && requester.length() > 0) {
                qo.add(Filter.eq("requesterId", requester));
                if (!requester.equals(getLoggedInUser().getId()))
                    qo.add(Filter.eq("targetId", getLoggedInUser().getId()));
            }

            if (identity != null && identity.length() > 0) {
                qo.add(Filter.eq("targetId", identity));

                if (!identity.equals(getLoggedInUser().getId()))
                    qo.add(Filter.eq("requesterId", getLoggedInUser().getId()));
            }

            if (identityName != null && identityName.length() > 0) {
                qo.add(Filter.subquery("targetId", Identity.class, "id", Filter.ignoreCase(Filter.like("name", identityName, Filter.MatchMode.START)))); 
                qo.add(Filter.eq("requesterId", getLoggedInUser().getId()));
            }

            //When all is null, use the logged in user's info
            if ((requester == null || requester.length() == 0)
                    && (identity == null || identity.length() == 0)
                    && (identityName == null || identityName.length() == 0)) {
                
                //Do a quick search of the request items' reference to identity requests
                //so we can prevent table scans.
                QueryOptions localQo =  new QueryOptions();
                localQo.add(Filter.or(Filter.eq("ownerName", getLoggedInUserName()),
                        Filter.eq("owner.id", getLoggedInUser().getId())));
                List<String> props = Util.csvToList("identityRequest.id");
                Iterator<Object[]> result = getContext().search(IdentityRequestItem.class, localQo, props);
                Set<String> identityRequestIds = new HashSet<String>();
                while (result != null && result.hasNext()) {
                    Object[] row = result.next();
                    identityRequestIds.add((String)row[0]);
                }
                
                if (!Util.isEmpty(identityRequestIds)) {
                    qo.add(Filter.or(Filter.eq("targetId", getLoggedInUser().getId()),
                                     Filter.eq("requesterId", getLoggedInUser().getId()),
                                     //Just in case the user name changed since the request was made
                                     Filter.eq("owner.id", getLoggedInUser().getId()),
                                     Filter.in("id", identityRequestIds)));
                } else {
                    qo.add(Filter.or(Filter.eq("targetId", getLoggedInUser().getId()),
                                     Filter.eq("requesterId", getLoggedInUser().getId()),
                                     //Just in case the user name changed since the request was made
                                     Filter.eq("owner.id", getLoggedInUser().getId())));
                }

            }
        }

        if (startDate != null)
            qo.add(Filter.ge("created", startDate));
        if (endDate != null)
            qo.add(Filter.lt("created", endDate));

        if ("complete".equals(status)) {
            qo.add(Filter.notnull("completionStatus"));
        } else if ("pending".equals(status)) {
            qo.add(Filter.isnull("completionStatus"));
            qo.add(Filter.ne("executionStatus", "Terminated"));
        } else if ("canceled".equals(status)) {
            qo.add(Filter.eq("executionStatus", "Terminated"));
        }

        if ("low".equals(priority)) {
            qo.add(Filter.eq("priority", "Low"));
        } else if ("high".equals(priority)) {
            qo.add(Filter.eq("priority", "High"));
        } else if ("normal".equals(priority)) {
            qo.add(Filter.or(Filter.isnull("priority"),
                    Filter.eq("priority", "Normal")));
        }

        if (requestId != null && requestId.length() > 0) {
            String formattedName = requestId;
            if (!requestId.startsWith("0")) {
                // make requestId 10 char integer left padded with zeros
                formattedName = String.format("%010d", Util.atoi(requestId));
            }
            // make sure requestid is number
            qo.add(Filter.eq("name", formattedName));
        }

        if(externalTicketId != null && externalTicketId.length() > 0) {
            qo.add(Filter.ignoreCase(Filter.eq("externalTicketId", externalTicketId)));
        }

        return qo;
    }

    /**
     * Cancels the workflow associated with the task,
     * adding an AuditEvent with the given comments.
     * @param comments Comments to include when auditing the cancel event
     * @return RequestResult with status and any error messages
     * @throws GeneralException
     */
    @POST
    @Path("cancelWorkflow")
    public RequestResult cancelWorkflow(@FormParam("requestId") String requestId, @FormParam("comments") String comments) throws GeneralException {
        IdentityRequest request = null;

        if (requestId != null) {
            request = getContext().getObjectById(IdentityRequest.class, requestId);
        }

        if (request == null) {
            RequestResult result = new RequestResult();
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(Message.error(MessageKeys.IDENTITY_REQUEST_NOT_FOUND).getLocalizedMessage(getLocale(), getUserTimeZone()));
            return result;
        }

        return new IdentityRequestService(getContext(), request).cancelWorkflow(this, comments);
    }

    /**
     * Returns true if the current user can view the given IdentityRequest object
     */
    private boolean canViewAllResults(){
        if (Capability.hasSystemAdministrator(getLoggedInUserCapabilities()))
            return true;

        if (Authorizer.hasAccess(getLoggedInUserCapabilities(), getLoggedInUserRights(),  SPRight.FullAccessIdentityRequest))
            return true;

        return false;
    }

}
