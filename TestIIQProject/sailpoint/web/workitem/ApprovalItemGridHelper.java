package sailpoint.web.workitem;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.ColumnConfig;
import sailpoint.tools.GeneralException;

public class ApprovalItemGridHelper {
    
    private static final String COMPLETION_COMMENTS = "completionComments";
    private static final String REQUESTER_COMMENTS = "requesterComments";
    private static final String ACTIVATION_DATES = "activationDates";
    private static final String VALUES = "values";
    private static final String ATTRIBUTE = "attribute";
    private static final String OPERATION = "operation";
    private static final String ACCOUNT_NAME = "accountName";
    private static final String APPLICATION = "application";
    private static final String DECISION = "decision";
    private static final ColumnConfig DESCISION_COLUMN;
    private static final ColumnConfig REVIEW_DECISION_COLUMN;
    private static final ColumnConfig APPLICATION_COLUMN;
    private static final ColumnConfig ACCOUNT_COLUMN;
    private static final ColumnConfig OPERATION_COLUMN;
    private static final ColumnConfig ATTRIBUTE_COLUMN;
    private static final ColumnConfig VALUES_COLUMN;
    private static final ColumnConfig ACTIVATION_DATE_COLUMN;
    private static final ColumnConfig REQUESTER_COMMENTS_COLUMN;
    private static final ColumnConfig OWNER_COLUMN;
    private static final ColumnConfig COMPLETION_COMMENTS_COLUMN;
    private static final ColumnConfig BATCH_FILE_COLUMN;
    public static final String JSON_ID = "id";
    public static final String JSON_DECISION = "decision";   
    public static final String JSON_APPLICATION = "application";
    public static final String JSON_INSTANCE = "instance";
    public static final String JSON_ACCOUNT_NAME = "accountName";
    public static final String JSON_OPERATION = "operation";
    public static final String JSON_ATTRIBUTE = "attribute";
    public static final String JSON_VALUE = "values";
    public static final String JSON_VALUE_ID = "valueIds";
    public static final String JSON_VALUE_TARGET = "valueTargetId";
    public static final String JSON_ACTIVATION_DATES = "activationDates";
    public static final String JSON_REQUESTER_COMMENTS = "requesterComments";
    public static final String JSON_OWNER = "owner";
    public static final String JSON_COMPLETION_COMMENTS = "completionComments";
    public static final String JSON_TOTAL = "totalCount";
    public static final String JSON_IS_ROLE = "isRole";
    public static final String JSON_IDENTITY_REQUEST_ID = "identityRequestId";
    public static final String JSON_ASSIGNMENT_ID = "assignmentId";
    
    private final ApprovalSetDTO approvalSet;
    private final boolean showOwner;

    
    static {
        DESCISION_COLUMN = new ColumnConfig( "approvalitem_decision", DECISION );
        DESCISION_COLUMN.setRenderer( "SailPoint.workitem.ApprovalItemGrid.renderDecisionColumn" );
        REVIEW_DECISION_COLUMN = new ColumnConfig( "", DECISION );
        APPLICATION_COLUMN = new ColumnConfig( APPLICATION, APPLICATION );
        APPLICATION_COLUMN.setRenderer( "SailPoint.workitem.ApprovalItemGrid.renderApplicationColumn" ); 
        APPLICATION_COLUMN.setFlex(1);
        ACCOUNT_COLUMN = new ColumnConfig( "approvalitem_account_name", ACCOUNT_NAME);
        ACCOUNT_COLUMN.setRenderer("SailPoint.grid.Util.wordWrapRenderer");
        ACCOUNT_COLUMN.setFlex(1);
        OPERATION_COLUMN = new ColumnConfig( "label_operation", OPERATION );
        ATTRIBUTE_COLUMN = new ColumnConfig( ATTRIBUTE, ATTRIBUTE );
        VALUES_COLUMN = new ColumnConfig( "approvalitem_values", VALUES );
        VALUES_COLUMN.setRenderer( "SailPoint.workitem.ApprovalItemGrid.renderValueLink" );
        VALUES_COLUMN.setFlex(1);
        ACTIVATION_DATE_COLUMN = new ColumnConfig( "approvalitem_activation_dates", ACTIVATION_DATES );
        ACTIVATION_DATE_COLUMN.setRenderer( "SailPoint.workitem.ApprovalItemGrid.renderActivationDatesColumn" ); 
        ACTIVATION_DATE_COLUMN.setFlex((float) 1.2);
        REQUESTER_COMMENTS_COLUMN = new ColumnConfig( "approvalitem_requester_comments", REQUESTER_COMMENTS);
        REQUESTER_COMMENTS_COLUMN.setRenderer("SailPoint.grid.Util.wordWrapRenderer");
        REQUESTER_COMMENTS_COLUMN.setFlex(2);
        OWNER_COLUMN = new ColumnConfig( "approvalitem_owner", "owner" );
        COMPLETION_COMMENTS_COLUMN = new ColumnConfig( "approvalitem_completion_comments", COMPLETION_COMMENTS);
        COMPLETION_COMMENTS_COLUMN.setRenderer("SailPoint.workitem.ApprovalItemGrid.renderCompletionComments");
        COMPLETION_COMMENTS_COLUMN.setFlex(2);
        BATCH_FILE_COLUMN = new ColumnConfig( "approvalitem_batchfile", VALUES );
        BATCH_FILE_COLUMN.setRenderer( "SailPoint.workitem.ApprovalItemGrid.renderBatchItemsLink" );
    }

    /**
     * We don't want to show the link if the user doesn't have FullAccessBatchRequest. Show just the file name instead.
     */
    public static void removeBatchItemsLink() {
        BATCH_FILE_COLUMN.setRenderer("SailPoint.workitem.ApprovalItemGrid.renderBatchFileName");
    }
    
    public ApprovalItemGridHelper( ApprovalSetDTO approvalSet, boolean showOwner ) {
        this.approvalSet = approvalSet;
        this.showOwner = showOwner;
    }

    public List<ColumnConfig> getApprovalItemHeaders( boolean disableDescisionColumn ) throws GeneralException {
        List<ColumnConfig> columns = new ArrayList<ColumnConfig>();
    	// special headers for batch approval
    	if (approvalSet != null && approvalSet.isBatchApproval()) {
    		columns.add(DESCISION_COLUMN);
            columns.add(BATCH_FILE_COLUMN );
            columns.add( COMPLETION_COMMENTS_COLUMN );
    		return columns;
    	}
        if( !disableDescisionColumn ) {
            columns.add( DESCISION_COLUMN );
        }
        addCommonColumns( columns );
        return columns;
    }
    
    public String getStoreFieldsJson() {
        StringBuilder response = new StringBuilder( "{ \"fields\":[" + "\"" + JSON_ID + "\"" );
        response.append( ",\"" + JSON_DECISION + "\"" );
        response.append( ",\"" + JSON_APPLICATION + "\"" );
        response.append( ",\"" + JSON_INSTANCE + "\"" );
        response.append( ",\"" + JSON_ACCOUNT_NAME + "\"" );
        response.append( ",\"" + JSON_OPERATION + "\"" );
        response.append( ",\"" + JSON_ATTRIBUTE + "\"" );
        response.append( ",\"" + JSON_VALUE + "\"" );
        response.append( ",\"" + JSON_VALUE_ID + "\"" );
        response.append( ",\"" + JSON_VALUE_TARGET + "\"" );
        response.append( ",\"" + JSON_ACTIVATION_DATES + "\"" );
        response.append( ",\"" + JSON_REQUESTER_COMMENTS + "\"" );
        response.append( ",\"" + JSON_OWNER + "\"" );
        response.append( ",\"" + JSON_COMPLETION_COMMENTS + "\"" );
        response.append( ",\"" + JSON_IS_ROLE + "\"" );
        response.append( ",\"" + JSON_IDENTITY_REQUEST_ID + "\"" );
        response.append( ",\"" + JSON_TOTAL + "\"" );
        response.append( "] }" );
        return response.toString();

    }
    
    public List<ColumnConfig> getViolationReviewHeaders() throws GeneralException {
        List<ColumnConfig> columns = new ArrayList<ColumnConfig>();
        columns.add( REVIEW_DECISION_COLUMN );
        addCommonColumns( columns );
        return columns;
    }

    private void addCommonColumns( List<ColumnConfig> columns ) throws GeneralException {
        columns.add( APPLICATION_COLUMN );
        if (approvalSet != null) {
            if( approvalSet.isShowAccountColumn() ) {
                columns.add( ACCOUNT_COLUMN );
            }
            columns.add( OPERATION_COLUMN );
            if( approvalSet.getHasAttribute() ) {
                columns.add( ATTRIBUTE_COLUMN );
            }
            if( approvalSet.getHasAttributeValue() ) {
                columns.add( VALUES_COLUMN );
            }
            if( approvalSet.getHasDateAssigned() ) {
                columns.add( ACTIVATION_DATE_COLUMN );
            }
            if( approvalSet.getHasRequesterComments() ) {
                columns.add( REQUESTER_COMMENTS_COLUMN );
            }
        }
        if( showOwner ) {
            columns.add( OWNER_COLUMN );
        }
        columns.add( COMPLETION_COMMENTS_COLUMN );
    }
}
