/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * 
 */
package sailpoint.web.workitem;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.BatchRequest;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Comment;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItem.State;
import sailpoint.service.ApprovalItemsService;
import sailpoint.task.BatchRequestTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.PageAuthenticationFilter;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 *
 * A DTO to represent the ApprovalSet model.
 * 
 * It basically just gives some methods to the uitier about the types of items
 * in the ApprovalSet.  Things like hasAttributes, hasAttributeValue, etc so 
 * we can hide certain columns of the approval item table when the entire
 * set is free of a column.
 *
 */
public class ApprovalSetDTO extends BaseDTO {

    private static final Log log = LogFactory.getLog(ApprovalSetDTO.class);

    private static final String JSON_MEMBERS = "members";

    String id;

    String workItemId;

    List<ApprovalItemDTO> _items;

    ApprovalSet _object;

    private List<ColumnConfig> columns;

    private String sortColumn;

    private boolean ascending;

    private String filter;

    private String identityRequestId;

    private List<ApprovalItemDTO> filteredItems;

    public ApprovalSetDTO() {
        this.id = Util.uuid();
        _items = new ArrayList<ApprovalItemDTO>();
    }
    
    public ApprovalSetDTO(ApprovalSet set) {
        this();
        _object = set;
        if (set != null) {
            List<ApprovalItem> items = set.getItems();
            if ( Util.size(items) > 0 ) {
                _items = new ArrayList<ApprovalItemDTO>();
                for ( ApprovalItem item : items  ) {
                    ApprovalItemDTO dto = new ApprovalItemDTO(item);
                    _items.add(dto);
                }
            }
        }
    }

    public boolean getHasDateAssigned() {
        if ( Util.size(_items) > 0 ) {
            for ( ApprovalItemDTO item : _items ) {
                ApprovalItem approvalItem = item.getObject();
                Date start = approvalItem.getStartDate();
                Date end = approvalItem.getEndDate();
                boolean hadStartOrEnd = Util.otob(approvalItem.getAttribute(ApprovalItemsService.ATT_HAD_SUNRISE_SUNSET));
                if(start != null || end != null || hadStartOrEnd) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean getHasAttribute() {
        if ( Util.size(_items) > 0 ) {
            for ( ApprovalItemDTO item : _items ) {
                String name = item.getObject().getName();
                if ( name != null ) 
                    return true;
          
            }
        }
        return false;
    }

    public boolean getHasAttributeValue() {
        if ( Util.size(_items) > 0 ) {
            for ( ApprovalItemDTO item : _items ) {
                Object name = item.getObject().getValue();
                if ( name != null ) 
                    return true;
          
            }
        }
        return false;
    }

    public boolean getHasRequesterComments() {
        if ( Util.size(_items) > 0 ) {
            for ( ApprovalItemDTO item : _items ) {
                String comments = item.getObject().getRequesterComments();
                if ( comments != null ) 
                    return true;
          
            }
        }
        return false;
    }
    
    public boolean isBatchApproval() {
    	if ( Util.size(_items) > 0 ) {
            for ( ApprovalItemDTO item : _items ) {
            	if (item.isBatchApproval()) {
            		return true;
            	}
            }
        }
        return false;
    }
    
    public boolean isShowAccountColumn() throws GeneralException {
        if ( Util.size(_items) > 0 ) {
            for ( ApprovalItemDTO item : _items ) {
                // Show account name if we have a native identity or if this is
                // a create (we show "new account").
                if (!Util.isNullOrEmpty(item.getObject().getNativeIdentity()) ||
                    item.isCreate()) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<ApprovalItemDTO> getItems() {
        return _items;
    }
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public ApprovalSet getObject() {
        return _object;
    }

    public String getMembersGridJson( int start, int end, String sortColumn, boolean ascending, String filter ) throws GeneralException {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        List<Map<String, Object>> members = new ArrayList<Map<String,Object>>(); 
        if( gridParametersChanged( sortColumn, ascending, filter ) ) {
            this.sortColumn = sortColumn;
            this.ascending = ascending;
            this.filter = filter;
            if( sortColumn != null ) {
                Collections.sort( _items, new Comparator<ApprovalItemDTO>() {
                    public int compare( ApprovalItemDTO o1, ApprovalItemDTO o2 ) {
                        String value1 = getComparableValue( o1, ApprovalSetDTO.this.sortColumn );
                        String value2 = getComparableValue( o2, ApprovalSetDTO.this.sortColumn );
                        return value1.compareTo( value2 ) * ( ApprovalSetDTO.this.ascending ? 1 : -1 );
                    }
    
                    private String getComparableValue( ApprovalItemDTO approvalItem, String sortColumn ) {
                        try {
                            if( sortColumn.equals( ApprovalItemGridHelper.JSON_VALUE )) {
                                return getValue( approvalItem ).toString();
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_DECISION ) ) {
                                return getDecision( approvalItem );
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_APPLICATION ) ) {
                                return approvalItem.getApplicationDisplayName();
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_ACCOUNT_NAME ) ) {
                                return approvalItem.getAccountDisplayName(false);
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_OPERATION ) ) {
                                return approvalItem.getOperation();
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_ATTRIBUTE ) ) {
                                return getAttribute( approvalItem );
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_VALUE_TARGET ) ) {
                                return getValueTarget( approvalItem ).toString();
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_ACTIVATION_DATES ) ) {
                                return getAcitvationDates( approvalItem ).toString();
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_REQUESTER_COMMENTS) ) {
                                return getRequesterComments( approvalItem, false );
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_OWNER ) ) {
                                return getOwner( approvalItem );
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_COMPLETION_COMMENTS ) ) {
                                return getCompletionCommentJson( approvalItem ).toString();
                            } else if( sortColumn.equals( ApprovalItemGridHelper.JSON_IS_ROLE ) ) {
                                return Boolean.toString( approvalItem.isRole() );
                            }
                        } catch (GeneralException e) {
                            throw new RuntimeException( "Unable to get " + sortColumn + " for this approval item", e );
                        }
                        return "";
                    }
    
                    private String getDecision( ApprovalItemDTO approvalItem ) {
                        if( approvalItem.getObject().getState() != null ) {
                            return approvalItem.getObject().getState().toString();
                        }
                        return "";
                    }
                });
            }
            filteredItems = filter( _items, new Predicate( this.filter ) );
        }
        List<ApprovalItemDTO> displayedItems = filteredItems.subList( start, Math.min( end, filteredItems.size() ) );
        for( ApprovalItemDTO approvalItem : displayedItems ) {
            members.add( getApprovalItemJson( approvalItem ) );
        }
        jsonMap.put( JSON_MEMBERS, members );
        jsonMap.put( ApprovalItemGridHelper.JSON_TOTAL, getTotalApprovalItems() );
        return JsonHelper.toJson( jsonMap );
    }

    public static String getEmptyMembersGridJson() {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        jsonMap.put( JSON_MEMBERS, new ArrayList<Map<String,Object>>() );
        jsonMap.put( ApprovalItemGridHelper.JSON_TOTAL, 0 );
        return JsonHelper.toJson( jsonMap );
    }

    private boolean gridParametersChanged( String sortColumn,
            boolean ascending, String filter ) {
        return sortColumn != this.sortColumn || ascending != this.ascending || filter != this.filter;
    }

    private int getTotalApprovalItems() {
        if( filteredItems == null ) {
            return 0;
        }
        return filteredItems.size();
    }
    
    private Map<String, Object> getApprovalItemJson( ApprovalItemDTO approvalItem ) throws GeneralException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put( ApprovalItemGridHelper.JSON_ID, approvalItem.getObject().getId() );
        map.put( ApprovalItemGridHelper.JSON_DECISION, approvalItem.getObject().getState() );
        map.put( ApprovalItemGridHelper.JSON_APPLICATION, approvalItem.getApplicationDisplayName() );
        map.put( ApprovalItemGridHelper.JSON_INSTANCE, approvalItem.getObject().getInstance() );
        map.put(ApprovalItemGridHelper.JSON_ACCOUNT_NAME, approvalItem.getAccountDisplayName(true));
        map.put( ApprovalItemGridHelper.JSON_OPERATION, approvalItem.getOperation() );
        map.put( ApprovalItemGridHelper.JSON_ATTRIBUTE, getAttribute( approvalItem ) );
        map.put( ApprovalItemGridHelper.JSON_VALUE, getValue( approvalItem ) );
        map.put( ApprovalItemGridHelper.JSON_VALUE_ID, getValueId( approvalItem ) );
        map.put( ApprovalItemGridHelper.JSON_VALUE_TARGET, getValueTarget( approvalItem ) );
        map.put( ApprovalItemGridHelper.JSON_ACTIVATION_DATES, getAcitvationDates( approvalItem ) );
        map.put( ApprovalItemGridHelper.JSON_REQUESTER_COMMENTS, getRequesterComments( approvalItem, true ) );
        map.put( ApprovalItemGridHelper.JSON_OWNER, getOwner( approvalItem ) );
        map.put( ApprovalItemGridHelper.JSON_COMPLETION_COMMENTS, ( getCompletionCommentJson( approvalItem ) ) );
        map.put( ApprovalItemGridHelper.JSON_IS_ROLE, approvalItem.isRole() );
        map.put( ApprovalItemGridHelper.JSON_ASSIGNMENT_ID, approvalItem.getObject().getAssignmentId() );
        map.put( ApprovalItemGridHelper.JSON_IDENTITY_REQUEST_ID, this.getIdentityRequestId());

        return map;
    }

    private List<Map<String, Object>> getCompletionCommentJson( ApprovalItemDTO approvalItem ) {
        List<Map<String, Object>> comments = new ArrayList<Map<String,Object>>();
        if(hasNoComments( approvalItem ) ) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put( "comment", "None" );
            map.put( "displayName", "" );
            map.put( "date", null );
            comments.add( map );
        } else {
            for( Comment comment : approvalItem.getComments() ) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put( "comment", WebUtil.escapeHTML(comment.getComment(), false) );
                map.put( "author", comment.getAuthor() );
                map.put( "date", comment.getDate() );
                comments.add( map );
            }
        }
        return comments;
    }

    private boolean hasNoComments( ApprovalItemDTO approvalItem ) {
        return approvalItem.getComments() == null || approvalItem.getComments().size() == 0;
    }

    private Object getValueTarget( ApprovalItemDTO approvalItem ) throws GeneralException {
        if (null == workItemId) {
            return null;
        }

        WorkItem item = getContext().getObjectById( WorkItem.class, workItemId ); 
        if ( item != null ) {
            String targetClass = item.getTargetClass();
            // kludge, we normally use simple names but for awhile 
            // at least LoginBean was using package qualifiers
            if (item.isTargetClass(Identity.class) ||
                "sailpoint.object.Identity".equals(targetClass)) {

                String targetName = item.getTargetName();
                String targetId = item.getTargetId();
                Identity id = null;
                if ( targetId != null ) {
                    id = getContext().getObjectById(Identity.class, targetId);
                } else
                if ( targetName != null ) {
                    id = getContext().getObjectByName(Identity.class, targetName);
                }
                if ( id != null ) {
                    return id.getId();
                }
            } 
            else if (item.isTargetClass(BatchRequest.class) 
                    || "sailpoint.object.BatchRequest".equals(targetClass)) {
                String targetId = item.getTargetId();
                return targetId;
            }
        }
        return null;
    }

    private Object getValueId( ApprovalItemDTO approvalItem ) throws GeneralException {
        
        String valueId = "";
        
        if( approvalItem.getValue() == null || approvalItem.getValue().isEmpty() ) {
            return "null";
        }        
        
        /** If this is a detected roles or assigned roles approval item, look up the role by name **/
        if(!Util.isNullOrEmpty(approvalItem.getName()) && 
                (approvalItem.getName().equals(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) || 
                        approvalItem.getName().equals(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES))) {
            valueId = WebUtil.getIdForName( "Bundle", approvalItem.getValue().get( 0 ) );
        }
        return valueId;
    }

    private String getOwner( ApprovalItemDTO approvalItem ) {
        return approvalItem.getObject().getOwner();
    }

    private String getRequesterComments( ApprovalItemDTO approvalItem, boolean escape ) {
        String requesterComments = null;
        if( approvalItem != null && approvalItem.getObject() != null) {
            requesterComments = approvalItem.getObject().getRequesterComments();
            if (escape && requesterComments != null) {
                requesterComments = WebUtil.escapeHTML(requesterComments, false);
            }
        }
        return (requesterComments == null) ? "" : requesterComments;                
    }

    private Map<String,Object> getAcitvationDates( ApprovalItemDTO approvalItem ) {
        Map<String, Object>dates = new HashMap<String, Object>();
        Date startDate = approvalItem.getObject().getStartDate();
        Date endDate = approvalItem.getObject().getEndDate();
        if(startDate != null){
            dates.put("startdate", startDate.getTime());
            dates.put("startdate_pretty", 
                    Util.dateToString(startDate, DateFormat.SHORT, null, getUserTimeZone(), getLocale()));
        }
        if(endDate != null) {
            dates.put("enddate", endDate.getTime());
            dates.put("enddate_pretty",  
                    Util.dateToString(endDate, DateFormat.SHORT, null, getUserTimeZone(), getLocale()));
        }
        return dates;
    }

    private Object getValue( ApprovalItemDTO item ) throws GeneralException {
        ApprovalItem object = item.getObject();
        ApprovalItemsService approvalItemsService = new ApprovalItemsService(getContext(), object);
        List<Map<String, String>> values = new ArrayList<Map<String, String>>();
        if( item.getValue() != null ) {
            for( String value : item.getValue() ) {
                values.add(getValueMap(item.getName(),
                        approvalItemsService.getDisplayValue(value, getLocale()),
                        approvalItemsService.getDescription(value, getLocale())));
            }
        } else {
            Map<String, String> valueMap = getValueMap(item.getName(),
                    (object.getDisplayValue() == null) ? "" : object.getDisplayValue(),
                    null);
            //put role name to the map, it may be different than role display name
            if (Util.size(item.getValue()) == 1 ) {
                String value = item.getValue().get(0);
                valueMap.put("roleName", value);
            }
            values.add(valueMap);
        }

        return JsonHelper.toJson(values);
    }
    
    private Map<String, String> getValueMap(String name, String displayValue, String description) {
        Map<String, String> valueMap = new HashMap<String, String>();
        valueMap.put("name", name);
        valueMap.put("displayValue", displayValue);
        valueMap.put("description", description);
        return valueMap;
    }

    private String getAttribute( ApprovalItemDTO approvalItem ) {
      ApprovalItem object = approvalItem.getObject();
        if( object.getDisplayName() != null ) {
            return object.getDisplayName();
        } else if( object.getName() != null ) {
            return object.getName();
        }
        return "";
    }

   

    public class ApprovalItemDTO extends BaseDTO {

        String id;

        ApprovalItem _object;
        
        List<Comment> _comments;

        /**
         * List that represents each value in the item. Typically one thing, but during 
         * identity edit and update we stick a csv in the value.
         */
        List<String> _valueList;
        
        String _name;
        
        public ApprovalItemDTO(ApprovalItem item) {
            this.id = Util.uuid();
            _object = item;
            _comments = item.getComments();
            _valueList = null;
            _name = item.getName();
        }

        public ApprovalItem getObject() {
            return _object;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<Comment> getComments() {
            return _comments;
        }

        public void setComments(List<Comment> comments) {
            _comments = comments;
        }

        public String getName() {
            return _name;
        }

        public void setName(String _name) {
            this._name = _name;
        }

        public List<String> getValue() {
            if ( _valueList == null ) {
                ApprovalItem item = getObject();
                if ( item != null ) {
                    _valueList = item.getValueList();
                }
            }
            return _valueList;
        }
        
        public void setCompletionComments(String comment) 
            throws GeneralException {

            if ( Util.getString(comment) != null ) {
                String userName = (String)getSessionScope().get(PageAuthenticationFilter.ATT_PRINCIPAL);
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
                        this.getObject().add(new Comment(comment, displayName));
                    }
                }
            }            
        }

        public String getCompletionComments() {
            return null;
        }

        /**
         * These select items drive the contents and values of the Decision radios.
         */
        public List<SelectItem> getStatusChoices() throws GeneralException {
            List<SelectItem> list = new ArrayList<SelectItem>();
            list.add(new SelectItem(WorkItem.State.Finished, "approveRadio"));
            list.add(new SelectItem(WorkItem.State.Rejected, "revokeRadio"));
            return list;
        }

        /**
         * These select items drive the contents and values of the Decision radios
         * when reviewing the violations on a request.
         */
        public List<SelectItem> getViolationReviewStatusChoices() throws GeneralException {
            List<SelectItem> list = new ArrayList<SelectItem>();
            list.add(new SelectItem(WorkItem.State.Rejected, "lcmDeleteRadio"));
            return list;
        }

        /**
         * If displaying an IdentityItem in the UI or auditing an item, this
         * method should be called to get the applicationName.  It maps
         * our internal System name "IIQ" to a friendlier version.
         *
         * @ignore We don't want to expose our internal IIQ appname
         * so this method will help resolve IIQ to IdentityIQ.
         */ 
        public String getApplicationDisplayName() throws GeneralException {
            return getApprovalItemsService().getApplicationDisplayName();
        }
        
        public boolean isRole() throws GeneralException {
            return getApprovalItemsService().isRole();
        }

        public boolean isCreate() throws GeneralException {
            return getApprovalItemsService().isCreate();
        }
        
        public boolean isForceNewAccount() throws GeneralException {
            return getApprovalItemsService().isForceNewAccount();
        }
        
        public boolean isBatchApproval() {
        	 if(_object != null && _object.getAttributes() != null &&
        			 _object.getAttributes().containsKey(BatchRequestTaskExecutor.BATCH_REQUEST)) {
                 return true;
             }
             return false;
        }

        public String getOperation() throws GeneralException{
            String operation = getApprovalItemsService().getOperation(getLocale(), getUserTimeZone());
            if (Util.isNotNullOrEmpty(operation) && getApprovalItemsService().isForceNewAccount()) {
                operation += " (" +
                        getMessage(MessageKeys.LCM_WORKITEM_NEW_ACCOUNT_REQUESTED, getLocale(), getUserTimeZone())
                        + ")";
            }
            return operation;
        }

        private String getAccountDisplayName(boolean escape) throws GeneralException {
            String accountDisplayName = getApprovalItemsService().getAccountDisplayName(null, getLocale(), getUserTimeZone());
            if (escape && accountDisplayName != null) {
                accountDisplayName = WebUtil.escapeHTML(accountDisplayName, false);
            }
            return accountDisplayName;
        }

        private ApprovalItemsService getApprovalItemsService() throws GeneralException {
            return new ApprovalItemsService(getContext(), this.getObject());
        }
    }
    
    
    List<ApprovalItemDTO> filter( List<ApprovalItemDTO> list, Predicate predicate ) {
        List<ApprovalItemDTO> filteredList = new ArrayList<ApprovalItemDTO>();
        if( list != null ) {
            for( ApprovalItemDTO approvalItem : list ) {
                if( predicate.apply(approvalItem ) ) {
                    filteredList.add( approvalItem );
                }
            }
        }
        return filteredList;
    }
    public String getIdentityRequestId() {
        return identityRequestId;
    }

    public void setIdentityRequestId(String identityRequestId) {
        this.identityRequestId = identityRequestId;
    }

    private class Predicate {
        String filter;
        public Predicate( String filter ) {
            this.filter = filter;
        }
        public boolean apply( ApprovalItemDTO approvalItem ) {
            if( filter.equals( "all" ) ) {
                return true;
            }
            State state = approvalItem.getObject().getState();
            if( filter.equals( "rejected" ) ) {
                return WorkItem.State.Rejected == state;
            } else if( filter.equals( "approved" ) ) {
                return WorkItem.State.Finished == state;
            } else if( filter.equals( "decided" ) ) {
                return WorkItem.State.Rejected == state || WorkItem.State.Finished == state;
            } 
            return state == null;
        }
    }

}
