/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import sailpoint.api.SailPointContext;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Comment;
import sailpoint.object.Filter;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.service.ApprovalItemsService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

public class LcmReportCommentHelper {
    private SailPointContext context;
    
    public LcmReportCommentHelper( SailPointContext context ) {
        this.context = context;
    }

    public List<Comment> getCompletionComments( IdentityRequestItem identityRequestItem ) {
        List<ApprovalSummary> approvalSummaries = getApprovalSummaries( identityRequestItem.getIdentityRequest() );
        List<Comment> comments = new ArrayList<Comment>();
        for ( ApprovalSummary approvalSummary : approvalSummaries ) {
            ApprovalSet approvalSet = approvalSummary.getApprovalSet();
            if( isCanceled( identityRequestItem ) ) {
                if (approvalSummary.getComments() != null) {
                    comments.addAll(approvalSummary.getComments());
                }
            }
            if( approvalSet != null ) {
                List<ApprovalItem> approvalItems = approvalSet.getItems();
                for( ApprovalItem approvalItem : approvalItems ) {
                    if( isApprovalItemForIdentityRequestItem( approvalItem, identityRequestItem ) ) {
                      if( approvalItem.getComments() != null ) {
                          comments.addAll( approvalItem.getComments() );
                      }
                      break;
                    }
                }
            }
        }
        return comments;
    }

    private boolean isCanceled( IdentityRequestItem identityRequestItem ) {
        // Do this compare backwards because null state means pending or something
        return WorkItem.State.Canceled.equals( identityRequestItem.getIdentityRequest().getState() );
    }

    public List<Comment> getWorkItemComments( IdentityRequestItem identityRequestItem ) {
        List<Comment> comments = new ArrayList<Comment>(); 
        IdentityRequest identityRequest = identityRequestItem.getIdentityRequest();
        List<ApprovalSummary> approvalSummaries = getApprovalSummaries( identityRequest );
        if( approvalSummaries.size() != 0 ) {
            for ( ApprovalSummary approvalSummary : approvalSummaries ) {
                // Only approvalSummaries with WorkItem.Type.Approval for WorkItem comments.
                if (approvalSummary.getWorkItemType() != WorkItem.Type.Approval)
                    continue;
                // We want to make sure at least one approvalItem matches the IdentityRequestItem.
                boolean matches = false;
                ApprovalSet approvalSet = approvalSummary.getApprovalSet();
                if (approvalSet != null) {
                    for (ApprovalItem approvalItem : approvalSet.getItems()) {
                        if (isApprovalItemForIdentityRequestItem(approvalItem, identityRequestItem)) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    CommentSource commentSource = getApprovalCommentSource( approvalSummary );
                    addComments( comments, commentSource );
                }
            }
        } else {
            CommentSource commentSource = getWorkItemCommentSource( identityRequestItem ); 
            addComments( comments, commentSource );
        }
        return comments;
    }

    private List<ApprovalSummary> getApprovalSummaries( IdentityRequest identityRequest ) {
        List<ApprovalSummary> approvalSummaries = ( List<ApprovalSummary> ) identityRequest.getAttribute( "approvalSummaries" );
        if( approvalSummaries == null ) {
            approvalSummaries = Collections.emptyList();
        }
        return approvalSummaries;
    }
    
    private CommentSource getApprovalCommentSource( ApprovalSummary approvalSummary ) {
        String workItemId = approvalSummary.getWorkItemId();
        if( workItemId != null ) {
            WorkItem workItem = getWorkItemById( workItemId );
            if( workItem != null ) {
                WorkItemCommentSource workItemCommentSource = new WorkItemCommentSource();
                workItemCommentSource.add( workItem );
                return new SortedCommentSource( workItemCommentSource );
            } else {
                WorkItemArchive workItemArchive = getWorkItemArchiveByWorkItemId( workItemId );
                return new SortedCommentSource( new WorkItemArchiveCommentSource( workItemArchive ) );
            }
        }
        return new EmptyCommentSource();
    }
    
    private void addComments( List<Comment> comments, CommentSource commentSource ) {
        comments.addAll( commentSource.getComments() );
    }

    private WorkItem getWorkItemById( String workItemId ) {
        WorkItem workItem = null;
        try {
            workItem = context.getObjectById( WorkItem.class, workItemId );
        } catch ( GeneralException e ) {
            throw new RuntimeException( "Trouble fetching WorkItem with id: " + workItemId, e );
        }
        return workItem;
    }

    private WorkItemArchive getWorkItemArchiveByWorkItemId( String workItemId ) {
        Filter filter = Filter.eq( "workItemId", workItemId );
        QueryOptions options = new QueryOptions( filter );
        Iterator<WorkItemArchive> workItemArchives;
        try {
            workItemArchives = context.search( WorkItemArchive.class, options );
        } catch ( GeneralException e ) {
            throw new RuntimeException( "Trouble fetching WorkItemArchive for WorkItem: " + workItemId, e );
        }
        if( workItemArchives != null ) {
            while( workItemArchives.hasNext() ) {
                return workItemArchives.next();
            }
        }
        return null;
    }

    private CommentSource getWorkItemCommentSource( IdentityRequestItem identityRequestItem ) {
        IdentityRequest identityRequest = identityRequestItem.getIdentityRequest();
        String identityRequestId = identityRequest.getName();
        Filter filter = Filter.eq( "identityRequestId", identityRequestId );
        QueryOptions options = new QueryOptions( filter );
        Iterator<WorkItem> workItems = null;
        try {
            workItems = context.search( WorkItem.class, options );
        } catch ( GeneralException e ) {
            throw new RuntimeException( "Problem when getting WorkItems for IdentityRequest: " + identityRequestId );
        }
        WorkItemCommentSource workItemCommentSource = new WorkItemCommentSource();
        if( workItems != null ) {
            while( workItems.hasNext() ) {
                WorkItem workItem = workItems.next();
                ApprovalSet approvalSet = ( ApprovalSet ) workItem.getAttribute( "approvalSet" );
                if( approvalSet != null ) {
                    for( ApprovalItem approvalItem : approvalSet.getItems() ) {
                        if( isApprovalItemForIdentityRequestItem( approvalItem, identityRequestItem ) ) {
                            workItemCommentSource.add( workItem );
                        }
                    }
                }
            }
        }
        return new SortedCommentSource( workItemCommentSource );
    }
    
    private boolean isApprovalItemForIdentityRequestItem( ApprovalItem approvalItem, IdentityRequestItem identityRequestItem ) {
        String aiApp = approvalItem.getApplication();
        String aiInst = approvalItem.getInstance();
        String aiNi = approvalItem.getNativeIdentity();
        String aiAttr = approvalItem.getName();
        String aiOp = approvalItem.getOperation();
        List<String> aiValue = approvalItem.getValueList();
  
        String iriAppName = identityRequestItem.getApplication();
        String iriNativeIdentity = identityRequestItem.getNativeIdentity();
        String iriInstance = identityRequestItem.getInstance();
        String iriAttrName = identityRequestItem.getName();
        String iriOp = IdentityRequest.opToString(identityRequestItem.getOperation());
        Object iriValue = identityRequestItem.getValue();

        return ( ( Util.isNullOrEmpty(iriAttrName) || ( Util.nullSafeCompareTo(aiAttr, iriAttrName)  == 0 ) ) &&
              ( Util.isNullOrEmpty(iriNativeIdentity) || ( Util.nullSafeCompareTo(aiNi, iriNativeIdentity) == 0) ) &&
             ( Util.nullSafeCompareTo(aiInst, iriInstance)  == 0) &&
             ( Util.nullSafeCompareTo(aiOp, iriOp)  == 0) &&
             ( Util.nullSafeCompareTo(iriAppName, aiApp)  == 0 ) &&
             ( Util.nullSafeContains(aiValue, iriValue) ));
    }

    private interface CommentSource {
        public List<Comment> getComments();
    }

    private class EmptyCommentSource implements CommentSource {
        public List<Comment> getComments() {
            return Collections.emptyList();
        }
    }

    private class SortedCommentSource implements CommentSource {

        public SortedCommentSource( CommentSource commentSource ) {
            this.wrappedCommentSource = commentSource;
        }
        public List<Comment> getComments() {
            List<Comment> wrappedComments = wrappedCommentSource.getComments();
            Collections.sort( wrappedComments, commentComparator );
            return wrappedComments;
        }

        private final CommentSource wrappedCommentSource;

        private final Comparator<Comment> commentComparator = new Comparator<Comment>() {
            public int compare( Comment comment1, Comment comment2 ) {
                if( comment1 == comment2 ) {
                    return 0;
                }
                if( comment1 == null ) {
                    return -1;
                }
                if( comment2 == null ) {
                    return 1;
                }
                return comment1.getDate().compareTo( comment2.getDate() );
            }
        };
    }

    private class WorkItemCommentSource implements CommentSource {
        private List<WorkItem> workItems;
        
        public WorkItemCommentSource() {
            workItems = new ArrayList<WorkItem>();
        }

        public void add( WorkItem workItem ) {
            workItems.add( workItem );
        }

        public List<Comment> getComments() {
            List<Comment> comments = new ArrayList<Comment>();
            for( WorkItem workItem : workItems ) {
                if( workItem != null && workItem.getComments() != null ) {
                    comments.addAll( workItem.getComments() );
                }
            }
            return comments;
        }
    }

    private class WorkItemArchiveCommentSource implements CommentSource {
        public WorkItemArchiveCommentSource( WorkItemArchive workItemArchive ) {
            this.workItemArchive = workItemArchive;
        }

        public List<Comment> getComments() {
            if( workItemArchive != null && workItemArchive.getComments() != null ) {
                return workItemArchive.getComments();
            }
            return Collections.emptyList();
        }

        private final WorkItemArchive workItemArchive;
    }

}