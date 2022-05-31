/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.*;
import sailpoint.service.WorkItemService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.view.ViewBuilder;
import sailpoint.web.view.ViewEvaluationContext;

import javax.ws.rs.*;

import java.util.*;


/**
 * A resource to return data associated with a work item
 *
 * @author <a href="mailto:derry.cannon@sailpoint.com">Derry Cannon</a>
 */
@Path("workitem")
public class WorkItemResource extends BaseListResource
    {
    private static final Log log = LogFactory.getLog(WorkItemResource.class);

    

    /**
     * Default constructor
     */
    public WorkItemResource() 
        {
        super();
        }


    @POST
    @Path("{workItemId}/approvalComment")
    public RequestResult addApprovalComment(@FormParam("comment") String commentText, @FormParam("approvalItem") String approvalItemId, @PathParam("workItemId") String workItemId) throws GeneralException {        
        WorkItem workItem = getContext().getObjectById( WorkItem.class, workItemId );
        
        authWorkItem(workItem);
        
        RequestResult result = new RequestResult();
        Comment comment = createCompletionComment( commentText );
        
        List<ApprovalItem> approvalItems = getApprovalItems( workItem );
        for( ApprovalItem approvalItem : approvalItems ) {
            if( approvalItem.getId().equals( approvalItemId ) ) { 
                approvalItem.add( comment );
                getContext().saveObject(workItem); 
                getContext().commitTransaction();
                break;
            }
        }
        result.setStatus( RequestResult.STATUS_SUCCESS );
        return result;
    }

    private void authWorkItem(WorkItem workItem) throws GeneralException {
    	authorize(new WorkItemAuthorizer(workItem));
    }

    private Comment createCompletionComment(String comment) throws GeneralException {
          if ( Util.getString(comment) != null ) {
            String userName = getLoggedInUserName();
            if ( userName != null ) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("name", userName));
                Iterator<Object[]> it = getContext().search(Identity.class, ops, Arrays.asList("displayName"));
                if ( it != null ) {
                    String displayName = userName;
                    Object[] row = it.next();
                    if ( row != null ) {
                        displayName = (String)row[0];
                    }
                    return new Comment(comment, displayName);
                }
            }
        }
        throw new GeneralException( "Unable to create completion comment");
    }

    private List<ApprovalItem> getApprovalItems( WorkItem workItem ) {
        Map<String,Object> attributes = workItem.getAttributes();
        List<ApprovalItem> approvalItems = Collections.emptyList();
        if ( attributes != null ) {
            ApprovalSet set = (ApprovalSet)attributes.get(WorkItem.ATT_APPROVAL_SET);
            if (set != null) {
                approvalItems = set.getItems();
            }
        }
        return approvalItems;
    }

    /**
     * Return the policy violations associated with the given work item.
     *
     * @return A ListResult with details about the policy violations.
     */
    @GET 
    @Path("{workItemId}/violations")
    @SuppressWarnings("unchecked")
    public ListResult getViolations(@PathParam("workItemId") String workItemId) throws GeneralException {
        WorkItemService workItemService = new WorkItemService(workItemId, this);
        List<Map<String, Object>> violations = workItemService.getViolations();

        // get the view builder, including any column evaluators
        List<ColumnConfig> columns = getColumns(UIConfig.WORKITEM_VIOLATIONS);
        ViewEvaluationContext viewContext = new ViewEvaluationContext(this, columns);
        ViewBuilder viewBuilder = new ViewBuilder(viewContext, WorkItem.class, columns);

        // run each violation through the view builder so any column
        // evaluators can be called
        List<Map<String, Object>> views = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> violation : Util.safeIterable(violations)) {
            views.add(viewBuilder.getView(violation));
        }

        // get the grid meta data needed by the UI
        GridResponseMetaData metadata = viewBuilder.calculateGridMetaData();
        for (GridColumn col : metadata.getColumns()) {
            String localizedHeader = Internationalizer.getMessage(col.getHeader(), getLocale());
            col.setHeader(localizedHeader != null ? localizedHeader : col.getHeader());
        }

        // now build the results list
        ListResult result = new ListResult(views, views.size());
        result.setMetaData(metadata.asMap());

        return result;
    }
}
