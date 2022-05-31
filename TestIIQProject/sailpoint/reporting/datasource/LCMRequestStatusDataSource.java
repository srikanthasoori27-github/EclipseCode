/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.object.ApprovalItem;
import sailpoint.object.AuditEvent;
import sailpoint.object.Comment;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.reporting.LCMRequestStatusReport;
import sailpoint.reporting.ReportParameterUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class LCMRequestStatusDataSource extends SailPointDataSource {

    private static final Log log = LogFactory.getLog(LCMRequestStatusDataSource.class);

    private static final String FIELD_COMP_DATE_STRING = "completionDateString";
    private static final String FIELD_APPROVERS = "approvers";
    private static final String FIELD_REJECTERS = "rejecters";
    private static final String FIELD_STATUS = "status";

    // Calculated status used for in-memory filtering
    private static final String FILTER_STATUS = "filterStatus";
    private static final String FILTER_COMP_DATE = "filterCompletionDate";

    private Iterator<AuditEvent> events;
    private int cacheCnt;

    private Map currentRow;

    // parameters
    private List<String> types;
    private List<String> applications;
    private List<String> approvers;
    private List<String> requestors;
    private List<String> targetIdentities;
    private List<IdentityItem> entitlements;
    private List<String> roleNames;
    private String status;
    private Date requestDateStart;
    private Date requestDateEnd;
    private Date completionDateStart;
    private Date completionDateEnd;

    public LCMRequestStatusDataSource(Locale locale, TimeZone timezone) {
        super(locale, timezone);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for active LCM workflows");
        events = new IncrementalObjectIterator<AuditEvent>(getContext(), AuditEvent.class, getQueryOptions());
    }

    /**
     * Gets the given field from the current report row.
     *
     * @param jrField The field from the jasper template to retrieve
     * @return Field value
     * @throws net.sf.jasperreports.engine.JRException
     *
     */
    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();

        if (fieldName == null)
            throw new RuntimeException("No field specified");

        if (DataSourceUtil.CURRENT_BEAN_FIELD_NAME.equals(fieldName))
            return currentRow;

        Object val = currentRow.get(fieldName);

        // Add some spaces ifnull so we don't get any blank cells. This seems to
        // happen randonly on the first row on the page. This hack 'fixes' the problem.
        return val == null || val.toString().length() == 0 ? "  " : val;
    }

     /**
     * Gets the next item on the list.
     *
     * @return True if there are more items in the datasource
     * @throws JRException
     */
    public boolean internalNext() throws JRException {
         currentRow = null;
         if(events != null){
             try {
                 // Get next row. Check matchesCompletionFilter to perform any filtering
                 // that can't be done in the db query
                 while(events.hasNext() && currentRow == null){
                    currentRow = getCurrentRow();
                    if (!matchesCompletionFilter(currentRow))
                        currentRow = null;
                 }
             } catch (GeneralException e) {
                 log.error("Failed to get LCM request audit event.", e);
                 throw new JRException(e);
             }
         }

         return currentRow != null;
    }

    /**
     * Converts an AuditEvent record into a Map which can be
     * easily consumed by the JasperTemplate
     * @return
     * @throws GeneralException
     */
    private Map<String, Object> getCurrentRow() throws GeneralException{
        Map<String, Object> row = new HashMap<String, Object>();

        AuditEvent event = events.next();

        row.put("requester", getIdentityDisplayName(event.getSource()));
        row.put("requestedFor", getIdentityDisplayName(event.getTarget()));
        row.put("created", Internationalizer.getLocalizedDate(event.getCreated(), getLocale(), getTimezone()));

        row.put("operation", getOperationDescription(event));

        row.put("account", event.getAccountName());
        row.put("application", event.getApplication());
        row.put("attribute", event.getAttributeName());
        row.put("attributeValue", getAttributeValue(event));
        row.put("role", event.getAttributeValue());
        row.put( "action", event.getAction() );
        
        String cause = "";
        String action = event.getAction();
        /* These value are synchronized across:
         *   - init-lcm.xml
         *   - PasswordManagermentReportArgs.xhtml
         *   - LCMRequestStatusDataSource.java
         * This seems overly fragile...
         */
        if( action != null ) {
            if( action.equals( "ExpirePasswordStart" ) ) {
                cause = getMessage( MessageKeys.REPT_PASSWORD_MANAGEMENT_EXPIRE_PASSWORD_START );
            } else if( action.equals( "ForgotPasswordStart" ) ) {
                cause = getMessage( MessageKeys.REPT_PASSWORD_MANAGEMENT_FORGOT_PASSWORD_START );
            } else if( action.equals( "PasswordsRequestStart" ) ) {
                cause = getMessage( MessageKeys.REPT_PASSWORD_MANAGEMENT_PASSWORD_REQUEST_START );
            }
        }
            
        row.put( "cause", cause );

        row.put(FILTER_STATUS, LCMRequestStatusReport.ARG_STATUS_PENDING);

        List<AuditEvent> relatedEvents = getRelatedEvents(event.getTrackingId());
        if (relatedEvents != null && !relatedEvents.isEmpty()){
            List<String> approvers = new ArrayList<String>();
            List<String> rejecters = new ArrayList<String>();
            List<Comment> comments = new ArrayList<Comment>();
            Date completionDate = null;
            Date rejectionDate = null;
            String status = null;


            // check to see if a cancellation event exists, if so we can ignore all
            // other events.
            AuditEvent failureEvent = checkForFailure(relatedEvents);
            AuditEvent cancelEvent = checkForCancellation(relatedEvents);
            if (cancelEvent != null){
                completionDate = cancelEvent.getCreated();
                status =  LCMRequestStatusReport.ARG_STATUS_CANCELLED;
                if (cancelEvent.getAttribute("completionComments") != null)
                    comments.addAll((List<Comment>)cancelEvent.getAttribute("completionComments"));
            } else 
            if ( failureEvent != null) {
                completionDate = failureEvent.getCreated();
                status = LCMRequestStatusReport.ARG_STATUS_FAILED;              
            }            
            else {

                // Check for a completion event. If so this will determine the final status
                AuditEvent completionEvent = checkForCompletionEvent(relatedEvents, event);
                if (completionEvent != null){
                    completionDate = completionEvent.getCreated();
                    row.put(FIELD_STATUS, getAuditActionName(completionEvent.getAction()));
                    status = LCMRequestStatusReport.ARG_STATUS_APPROVED;
                    
                  //** If the value has changed, update the row **/
                    Object requestedValue = row.get("attributeValue");
                    Object newValue = getAttributeValue(completionEvent);
                    if(!requestedValue.equals(newValue)){
                        row.put("attributeValue", getMessage(MessageKeys.REPT_LCM_REQUEST_OLD_NEW_VAL, requestedValue, newValue));
                    }
                }

                // Iterate thru all events collecting comments, approver/rejecter names
                // In the event that we dont have a completion event yet, try and determine the
                // status by examining the workflow summary if it still exists
                for(AuditEvent relatedEvent : relatedEvents){
                    if (relatedEvent != completionEvent && correlateEvent(event, relatedEvent)){
                        if (relatedEvent.getAttribute("completionComments") != null)
                            comments.addAll((List<Comment>)relatedEvent.getAttribute("completionComments"));

                        if (AuditEvent.ActionApproveLineItem.equals(relatedEvent.getAction())) {
                            approvers.add(relatedEvent.getSource());
                        } else if (AuditEvent.ActionRejectLineItem.equals(relatedEvent.getAction())){
                            rejecters.add(relatedEvent.getSource());
                            // keep track of the most recent rejection date since rejections will
                            // not have a completion audit event
                            if (rejectionDate == null || relatedEvent.getCreated().after(rejectionDate))
                                rejectionDate  = relatedEvent.getCreated();
                        }

                        if (status == null)
                            status = getStatus(relatedEvent);
                    }
                }
            }

            // If we found at least one rejection and there is no completion event,
            // and we could not get a status from the TaskResult, then we can assume
            // the item was rejected
            if (status == null && rejectionDate != null){
                status = LCMRequestStatusReport.ARG_STATUS_REJECTED;
                completionDate = rejectionDate;
            } else if (status == null){
                // default status
                status = LCMRequestStatusReport.ARG_STATUS_PENDING;
            }

            if (LCMRequestStatusReport.ARG_STATUS_REJECTED.equals(status)){
                row.put(FIELD_STATUS, getMessage(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_REJECTED));
            } else if (LCMRequestStatusReport.ARG_STATUS_APPROVED.equals(status)) {
                row.put(FIELD_STATUS, getMessage(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_APPROVED));
            } else if (LCMRequestStatusReport.ARG_STATUS_PENDING.equals(status)) {
                row.put(FIELD_STATUS, getMessage(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_PENDING));
            }  else if (LCMRequestStatusReport.ARG_STATUS_CANCELLED.equals(status)) {
                row.put(FIELD_STATUS, getMessage(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_CANCELLED));
            } else if (LCMRequestStatusReport.ARG_STATUS_FAILED.equals(status)) {
                row.put(FIELD_STATUS, getMessage(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_FAILED));
            }

            // dump all the data we've collected into the row
            row.put(FILTER_STATUS, status);
            if (completionDate != null){
                row.put(FILTER_COMP_DATE, completionDate);
                row.put(FIELD_COMP_DATE_STRING,
                            Internationalizer.getLocalizedDate(completionDate, getLocale(), getTimezone()));
            }
            row.put(FIELD_APPROVERS, getIdentityDisplayNameList(approvers));
            row.put(FIELD_REJECTERS, getIdentityDisplayNameList(rejecters));
            row.put("comments", formatComments(comments));

        } else {
            row.put(FIELD_STATUS, getMessage(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_PENDING));
        }

        if (cacheCnt > 0 && cacheCnt % 100 == 0)
            getContext().decache();

        cacheCnt++;

        return row;
    }

    /**
     * Check for a cancellation event, if found return it.
     * @param events
     * @return
     */
    private AuditEvent checkForCancellation(List<AuditEvent> events){

        if (events == null)
            return null;

        for(AuditEvent event : events){
            if (AuditEvent.CancelWorkflow.equals(event.getAction()))
                return event;
        }

        return null;
    }
    
    private AuditEvent checkForFailure(List<AuditEvent> events) {
        if ( Util.size(events) > 0 ) { 
            for (AuditEvent event : events) {
                if (AuditEvent.ProvisioningFailure.equals(event.getAction())) {
                    return event;
                }
            }
        }
        return null;
    }

    /**
     * Check related events for a completion audit event. This event tells us
     * that the access request was successfully provisioned.
     * @param events
     * @param startEvent
     * @return
     */
    private AuditEvent checkForCompletionEvent(List<AuditEvent> events, AuditEvent startEvent){

        if (events == null)
            return null;

        for(AuditEvent event : events){
            if (event.getAction().equals(startEvent.getAttribute("operation")) && correlateEvent(startEvent, event))
                return event;
        }

        return null;
    }

    /**
     * Returns true if request for the given AuditEvent can be found in the ApprovalSummary.
     * @param summary
     * @param event
     * @return
     * @throws GeneralException
     */
    private boolean checkforCorrelatingEvent(List<WorkflowSummary.ApprovalSummary> summary, AuditEvent event) throws GeneralException{
        if (summary == null)
            return false;
        for(WorkflowSummary.ApprovalSummary approval : summary){
           if (approval.getApprovalSet() != null && approval.getApprovalSet().getItems() != null){
               for(ApprovalItem item : approval.getApprovalSet().getItems()){
                   if (correlateEvent(event, item))
                       return true;
               }
           }
        }
        return false;
    }

    /**
     * Check the WorkflowSummary to see if the status of an request can be determined
     * @param event
     * @return
     * @throws GeneralException
     */
    private String getStatus(AuditEvent event) throws GeneralException{
        String taskResultId = (String)event.getAttribute("taskResultId");
        if (taskResultId != null){
           TaskResult result = getContext().getObjectById(TaskResult.class, taskResultId);
           if (result != null && result.getAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY) != null){
               WorkflowSummary workflowSummary = (WorkflowSummary)result.getAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY);
               if (checkforCorrelatingEvent(workflowSummary.getApprovals(), event))
                   return LCMRequestStatusReport.ARG_STATUS_APPROVED;
               if (checkforCorrelatingEvent(workflowSummary.getRejections(), event))
                   return LCMRequestStatusReport.ARG_STATUS_REJECTED;
               if (checkforCorrelatingEvent(workflowSummary.getPending(), event))
                   return LCMRequestStatusReport.ARG_STATUS_PENDING;
           }
        }

        return null;
    }

    /**
     * Format comment objects into nice displayable paragraphs.
     * @param comments
     * @return
     */
    private String formatComments(List<Comment> comments){
        String commentsStr = "";
        if (comments != null && !comments.isEmpty()){
            Collections.sort(comments, new Comparator<Comment>(){
                public int compare(Comment a, Comment b){
                    if (a == null){
                        return -1;
                    } else if (b == null){
                        return 1;
                    } else if (a.equals(b)){
                        return 0;
                    } else if (a.getDate() == null){
                        return b.getDate() != null ? -1 : 1;
                    } else{
                        return a.getDate().compareTo(b.getDate());                      
                    }
                }
            });
        
            for(Comment comment : comments){
                commentsStr += comment.getLocalizedMessage(getLocale(), getTimezone());
                commentsStr += "\n\n";
            }
        }
        return commentsStr;
    }

    /**
     * Gets attribute value for event, performing any special handling required for
     * different event types.
     * @param event
     * @return
     */
    private String getAttributeValue(AuditEvent event){

        if ("ManageIdentityAttributesStart".equals(event.getAction())){
            Message msg = Message.info(MessageKeys.REPT_LCM_REQUEST_STATUS_ATTR_SUMMARY_TEXT, event.getAttributeValue());
            return msg.getLocalizedMessage(getLocale(), getTimezone());
        }

        return event.getAttributeValue();
    }

    private String getIdentityDisplayNameList(List<String> names) throws GeneralException{
        if (names == null || names.isEmpty())
            return "";

        List<String> cols = Arrays.asList("displayName");
        List<String> displayNames = new ArrayList<String>();
        for(String name : names){
            String displayName = name;
            Iterator<Object[]> rows =
                    getContext().search(Identity.class, new QueryOptions(Filter.eq("name", name)), cols);
            if (rows.hasNext()){
                String val = (String)rows.next()[0];
                if (val != null)
                    displayName = val;
            }

            displayNames.add(displayName);
        }

        return !displayNames.isEmpty() ? Util.listToCsv(displayNames) : "";
    }

    /**
     * Get related events records to the given tracking ID.
     * This would include things such as approval, rejection or completion
     * audit events.
     * @param trackingId
     * @return
     * @throws GeneralException
     */
    private List<AuditEvent> getRelatedEvents(String trackingId) throws GeneralException{
        if (trackingId == null)
            return null;
        QueryOptions relatedEventOps = new QueryOptions();
        relatedEventOps.add(Filter.eq("trackingId", trackingId));
        relatedEventOps.add(Filter.not(Filter.in("action", types)));
        return getContext().getObjects(AuditEvent.class, relatedEventOps);
    }

    /**
     * Get display name for an identity
     * @param name
     * @return
     * @throws GeneralException
     */
    private String getIdentityDisplayName(String name) throws GeneralException{
        if (name==null)
            return "";

        Iterator<Object[]> results = getContext().search(Identity.class,
                new QueryOptions(Filter.eq("name", name)), Arrays.asList("displayName"));
        if (results != null && results.hasNext()){
            String displayName = (String)results.next()[0];
            return displayName != null ? displayName : name;
        }

        return name;
    }

    private boolean correlateEvent(AuditEvent event, AuditEvent otherEvent){

        if (event == null || otherEvent == null)
            return false;

        if (!Util.nullSafeEq(event.getApplication(), otherEvent.getApplication(), true))
            return false;
        if (!Util.nullSafeEq(event.getAttributeName(), otherEvent.getAttributeName(), true))
            return false;
        if (!Util.nullSafeEq(event.getInstance(), otherEvent.getInstance(), true))
            return false;
        if (!Util.nullSafeEq(event.getAccountName(), otherEvent.getAccountName(), true))
            return false;

        return true;
    }

    private boolean correlateEvent(AuditEvent event, ApprovalItem item){

        if (event == null || item == null)
            return false;

        if (!Util.nullSafeEq(event.getApplication(), item.getApplication(), true))
            return false;
        if (!Util.nullSafeEq(event.getAttributeName(), item.getName(), true))
            return false;
        if (!Util.nullSafeEq(event.getInstance(), item.getInstance(), true))
            return false;
        if (!Util.nullSafeEq(event.getAccountName(), item.getNativeIdentity(), true))
            return false;

        return true;
    }

    /**
     * Get displayable name for an Audit action
     * @param op Operation
     * @return
     * @throws GeneralException
     */
    private String getAuditActionName(String op) throws GeneralException{

        String key = null;
        if ("RoleAdd".equals(op))
           key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_ADD_ROLE;
        else if ("RoleRemove".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_REM_ROLE;
        else if ("EntitlementAdd".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_ADD_ENTITLEMENT;
        else if ("EntitlementRemove".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_REM_ENTITLEMENT;
        else if ("Modify".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_UPDATE_IDENTITY;
        else if ("Delete".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_REM_ACCOUNT;
        else if ("Unlock".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_UNLOCK_ACCOUNT;
        else if ("Disable".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_DISABLE_ACCOUNT;
        else if ("Create".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_COMPLETE_OP_CREATE_IDENTITY;

        if (key != null)
            return getMessage(key);
        else
            return getMessage(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_COMPLETED);

    }

    private String getOperationDescription(AuditEvent event){
        if (event == null)
            return "";

        String op = (String)event.getAttribute("operation");
        String key = null;
        if ("RoleAdd".equals(op))
           key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_ADD_ROLE;
        else if ("RoleRemove".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_REM_ROLE;
        else if ("EntitlementAdd".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_ADD_ENTITLEMENT;
        else if ("EntitlementRemove".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_REM_ENTITLEMENT;
        else if ("Modify".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_UPDATE_IDENTITY;
        else if ("Delete".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_REM_ACCOUNT;
        else if ("Disable".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_DISABLE_ACCOUNT;
        else if ("Unlock".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_UNLOCK_ACCOUNT;

        String value = op;
        if (key != null) {
            value = getMessage(key);
        }

        // If the user explictily requested an additional account, show this
        // with the operation.  This is consistent with how we display approvals,
        // etc...
        boolean forceCreate =
            Util.otob(event.getAttribute(ProvisioningPlan.ARG_FORCE_NEW_ACCOUNT));
        if (forceCreate) {
            value += " (" + getMessage(MessageKeys.LCM_WORKITEM_NEW_ACCOUNT_REQUESTED) + ")";
        }
        return value;
    }

    private QueryOptions getQueryOptions(){
        QueryOptions ops = new QueryOptions();

        ops.add(Filter.in("action", types));

        // Filter on create date. If no create date filter exists we can also try to filter on completionDate
        // completionDate filtering is done in memory, so we can avoid unecessarily retrieving records if the
        // creation date occurs after the specified completion date range.
        Filter createFilter = ReportParameterUtil.getDateRangeFilter("created", requestDateStart, requestDateEnd);
        if (createFilter != null){
            ops.add(createFilter);
        } else if (completionDateEnd != null){
            ops.add(ReportParameterUtil.getDateRangeFilter("created", null, completionDateEnd));
        } else if (completionDateStart != null){
            ops.add(ReportParameterUtil.getDateRangeFilter("created", null, completionDateStart));
        }

        if (applications != null && !applications.isEmpty()){
            ops.add(Filter.in("application", applications));
        }

        if (targetIdentities != null && !targetIdentities.isEmpty()){
            ops.add(Filter.in("target", targetIdentities));
        }

        if (requestors != null && !requestors.isEmpty()){
            ops.add(Filter.in("source", requestors));
        }

        if (roleNames != null && !roleNames.isEmpty()){
            ops.add(Filter.in("attributeValue", roleNames));
        }

        if (entitlements != null){
            List<Filter> entFilterList = new ArrayList<Filter>();
            // for each entitlement create an AND filter which includes app,attr, attrValue
            for(IdentityItem ent : entitlements){
                List<Filter> entFilter = new ArrayList<Filter>();
                String valuesList = Util.listToCsv(ent.getValueList());
                entFilter.add(Filter.eq("application", ent.getApplication()));
                entFilter.add(Filter.eq("attributeName", ent.getName()));
                if (valuesList != null && !"".equals(valuesList) )
                    entFilter.add(Filter.eq("attributeValue", valuesList));
                entFilterList.add(Filter.and(entFilter));
            }

            // Combine all the AND'd filters into one big OR filter so we match
            // any or all of the app/attr/value combinations
            if (!entFilterList.isEmpty())
                ops.add(Filter.or(entFilterList));
        }

        List<Filter> relatedEventFilters = new ArrayList<Filter>();

        // if an approver is specified
        if (approvers != null && !approvers.isEmpty()){
            Filter actionFilter = Filter.or(Filter.eq("action", AuditEvent.ActionApproveLineItem),
                    Filter.eq("action", AuditEvent.ActionRejectLineItem));
            relatedEventFilters.add(Filter.and(Filter.in("source", approvers), actionFilter));
        }

        if (!relatedEventFilters.isEmpty()){
            // exclude start events
            relatedEventFilters.add(Filter.and(Filter.ne("action", AuditEvent.EntitlementsRequestStart),
                    Filter.ne("action", AuditEvent.RolesRequestStart),
                    Filter.ne("action", AuditEvent.IdentityCreateRequestStart),
                    Filter.ne("action", AuditEvent.IdentityEditRequestStart),
                    Filter.ne("action", AuditEvent.EntitlementsRequestStart)));     
            ops.add(Filter.subquery("trackingId", AuditEvent.class, "trackingId", Filter.and(relatedEventFilters)));
        }

        return ops;
    }

    /**
     * Used for in-memory filter of rows in cases where the database
     * cant perform all filtering for us.
     * @param row
     * @return
     */
    private boolean matchesCompletionFilter(Map row){

        if (this.status != null){

            String rowStatus = (String)row.get(FILTER_STATUS);

            if (rowStatus == null)
                rowStatus =  LCMRequestStatusReport.ARG_STATUS_PENDING;
            if( status.equals( LCMRequestStatusReport.ARG_STATUS_COMPLETED ) ) {
                if( !( rowStatus.equals( LCMRequestStatusReport.ARG_STATUS_COMPLETED ) ||
                       rowStatus.equals( LCMRequestStatusReport.ARG_STATUS_REJECTED ) ||
                       rowStatus.equals( LCMRequestStatusReport.ARG_STATUS_APPROVED ) ) ) {
                    return false;
                }
            } else if (!rowStatus.equals(this.status)) {
                return false;
            }
        }

        Date completionDate = (Date)row.get(FILTER_COMP_DATE);
        if (completionDateStart != null && (completionDate == null || completionDate.before(completionDateStart)))
            return false;

        if (completionDateEnd != null && (completionDate == null || completionDate.after(completionDateEnd)))
            return false;


        return true;
    }

    /////////////////////////////////////////////////////////////////////////////
    //
    //  Filter parameter mutators
    //
    /////////////////////////////////////////////////////////////////////////////

    public List<String> getApplications() {
        return applications;
    }

    public void setApplications(List<String> applications) {
        this.applications = applications;
    }

    public List<String> getApprovers() {
        return approvers;
    }

    public void setApprovers(List<String> approvers) {
        this.approvers = approvers;
    }

    public List<String> getRequestors() {
        return requestors;
    }

    public void setRequestors(List<String> requestors) {
        this.requestors = requestors;
    }

    public List<String> getTargetIdentities() {
        return targetIdentities;
    }

    public void setTargetIdentities(List<String> targetIdentities) {
        this.targetIdentities = targetIdentities;
    }

    public List<IdentityItem> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(List<IdentityItem> entitlements) {
        this.entitlements = entitlements;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public void setRoleNames(List<String> roleNames) {
        this.roleNames = roleNames;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getRequestDateStart() {
        return requestDateStart;
    }

    public void setRequestDateStart(Date requestDateStart) {
        this.requestDateStart = requestDateStart;
    }

    public Date getRequestDateEnd() {
        return requestDateEnd;
    }

    public void setRequestDateEnd(Date requestDateEnd) {
        this.requestDateEnd = requestDateEnd;
    }

    public Date getCompletionDateStart() {
        return completionDateStart;
    }

    public void setCompletionDateStart(Date completionDateStart) {
        this.completionDateStart = completionDateStart;
    }

    public Date getCompletionDateEnd() {
        return completionDateEnd;
    }

    public void setCompletionDateEnd(Date completionDateEnd) {
        this.completionDateEnd = completionDateEnd;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }
}
