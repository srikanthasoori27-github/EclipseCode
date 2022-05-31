package sailpoint.web.workitem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.faces.model.SelectItem;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.Identity;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.WorkItemListBean;
import sailpoint.web.messages.MessageKeys;

/**
 * Collection of utilties for Work Items in the UI
 * @author Bernie Margolis
 */
public class WorkItemUtil {
    public static final String WORK_ITEM_ACTIVE_STATE_ARCHIVED = "archived";
    public static final String WORK_ITEM_ACTIVE_STATE_ACTIVE = "active"; 
    public static final String WORK_ITEM_ADMINISTRATOR_CAPABILITY = "WorkItemAdministrator";
    private static final Log log = LogFactory.getLog(WorkItemUtil.class);
    private static final String SEARCH_INPUT_WORK_ITEM = "workItem";
    
    public static List<SelectItem> getPrioritySelectItems(Locale locale, TimeZone timezone) {
        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        WorkItem.Level[] items = WorkItem.Level.values();
        for (int i = 0; i < items.length; ++i) {
            WorkItem.Level priority = items[i];
            selectItems.add(new SelectItem(priority.name(), new Message(priority.getMessageKey()).getLocalizedMessage(locale, timezone)));
        }
        return selectItems;
    }
    
    public static List<SelectItem> getStatusSelectItems(Locale locale, TimeZone timezone) {
        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        selectItems.add(new SelectItem(WORK_ITEM_ACTIVE_STATE_ARCHIVED, new Message(MessageKeys.WORK_ITEM_STATUS_ARCHIVED).getLocalizedMessage(locale, timezone)));
        selectItems.add(new SelectItem(WORK_ITEM_ACTIVE_STATE_ACTIVE, new Message(MessageKeys.WORK_ITEM_STATUS_ACTIVE).getLocalizedMessage(locale, timezone)));
        
        return selectItems;
    }
    
    /**
     * Add a filter to the given QueryOptions to query on name and/or ID.
     * @throws GeneralException 
     */
    private static void addNameAndIdFilter(SailPointContext ctx, QueryOptions qo, String name) throws GeneralException {
        if (null != Util.getString(name)) {
            qo.add(getNameAndIdFilter(ctx, name));
        }
    }

    public static Filter getNameAndIdFilter(SailPointContext ctx, String name) throws GeneralException {
        // Try and find a search input def.  If found, use that.  If not, go with the old 'Descript starts with' business
        SearchInputDefinition input = null;
        if (ctx != null) {
            // new SearchInput hotness
            Configuration cfg = ctx.getConfiguration();
            // we already know the name isn't null or "", so skipping the check
            input = SearchInputDefinition.getInputByName(cfg, SEARCH_INPUT_WORK_ITEM, false);
        }

        Filter filter = null;

        // Bug 18758 - workitem search should always create a compound 'or' filter to search for a
        // specific ID.
        if (input != null) {
            // use the search input definition, if provided
            input.setValue(name);
            filter = input.getFilter(ctx);
        } else {
            // old 'n statically busted
            filter = Filter.ignoreCase(Filter.like("description", name, MatchMode.START));
        }

        // OR'ing the ID filter
        if(Util.isInt(name)) {
            Filter idFilter = Filter.eq("name", Util.padID(name));
            filter = Filter.or(filter, idFilter);
        }

        return filter;
    }
    
    /** Retrieves any passed in filters from the request.  Wraps {@link #getQueryOptionsFromRequest(SailPointContext, QueryOptions, Map, boolean)}
     * for backwards compatibility.
     * @param qo QueryOptions to populate
     * @param params Request parameters
     * @param isArchive archive flag
     * @throws GeneralException
     */
    public static void getQueryOptionsFromRequest(QueryOptions qo, Map<String, String> params, boolean isArchive) throws GeneralException
    {
        getQueryOptionsFromRequest(null, qo, params, isArchive);
    }
    
    /** Retrieves any passed in filters from the request.  Uses the related SearchInputDefinition should the context be provided
     * @param ctx If provided, will attempt to use the SearchInput named {@link #SEARCH_INPUT_WORK_ITEM}.  If not provided, original
     *            behavior of 'description starts-with' will be used
     * @param qo QueryOptions to populate
     * @param params Request parameters
     * @param isArchive archive flag
     * @throws GeneralException
     */
    public static void getQueryOptionsFromRequest(SailPointContext ctx, QueryOptions qo, Map<String, String> params, boolean isArchive) throws GeneralException
    {
        addNameAndIdFilter(ctx, qo, params.get("name"));
        
        if(params.get("expirationStartDate")!=null && !((String)params.get("expirationStartDate")).equals("")) {
            Date startDate = new Date(Long.parseLong(params.get("expirationStartDate")));
            qo.add(Filter.ge("expiration", startDate));
        }
        
        if(params.get("expirationEndDate")!=null && !((String)params.get("expirationEndDate")).equals("")) {
            Date endDate = new Date(Long.parseLong((String)params.get("expirationEndDate")));
            qo.add(Filter.le("expiration", endDate));
        }
        
        if(params.get("creationStartDate")!=null && !((String)params.get("creationStartDate")).equals("")){
            Date startDate = new Date(Long.parseLong((String)params.get("creationStartDate")));
            qo.add(Filter.ge("created", startDate));
        }
        
        if(params.get("creationEndDate")!=null && !((String)params.get("creationEndDate")).equals("")) {
            Date endDate = new Date(Long.parseLong((String)params.get("creationEndDate")));
            qo.add(Filter.le("created", endDate));
        }
        
        if(params.get("modifiedStartDate")!=null && !((String)params.get("modifiedStartDate")).equals("")){
            Date startDate = new Date(Long.parseLong((String)params.get("modifiedStartDate")));
            qo.add(Filter.ge("modified", startDate));
        }
        
        if(params.get("modifiedEndDate")!=null && !((String)params.get("modifiedEndDate")).equals("")) {
            Date endDate = new Date(Long.parseLong((String)params.get("modifiedEndDate")));
            qo.add(Filter.le("modified", endDate));
        }
        
        if(params.get("state")!=null && !((String)params.get("state")).equals("")) {
            String state = (String)params.get("state");
            if(state.equals("Open"))
                qo.add(Filter.isnull("state"));
            else
                qo.add(Filter.eq("state",state ));
        }
        
        if(params.get("level")!=null && !((String)params.get("level")).equals("")) {
            String level = (String)params.get("level");
            WorkItem.Level workItemLevel;
            try {
                workItemLevel = Enum.valueOf(WorkItem.Level.class, level);
            } catch (Exception e) {
                workItemLevel = WorkItem.Level.Normal;
                log.error("A work item grid attempted to filter on an invalid Work Item Prioirity of " + level + ".  Filtering on normal priority instead.", e);
            }
            
            qo.add(Filter.eq("level", workItemLevel));
        }
        
        if(params.get("type")!=null && !((String)params.get("type")).equals("")) {
            /** The names of the types are getting split with a " " for ease of reading on the ui, so
             * we want to strip any of that out for the query.
             */
            String type = (String)params.get("type");
            if(type.contains(",")){
                String[] typeArray = type.split(",");
                ArrayList<Filter> FilterList = new ArrayList<Filter>();
                for(String t : typeArray) {
                    FilterList.add(Filter.eq("type", t));
                }
                qo.add(Filter.or(FilterList));
            }
            else {
                qo.add(Filter.eq("type",type));
            }
        } else if(!isArchive && 
                (Util.isNullOrEmpty(params.get(WorkItemListBean.PARAM_LIST_TYPE)) ||
                    !Arrays.asList(WorkItemListBean.LIST_TYPES_AVOIDING_EVENTS).contains(params.get(WorkItemListBean.PARAM_LIST_TYPE)))) {
            // Plain English: If the params don't have a list type passed or if the list type passed isn't one we've designated as event-free, 
            // then do this:
            // Filter out event type workitems. For lists like our inbox/outbox lists, we have enough other
            // information to avoid this filter and its problematic performance impact
            qo.add(Filter.ne("type", WorkItem.Type.Event.name()));
        }

        if (params.get("ownerName") != null && !"".equals(params.get("ownerName"))) {
            if(!isArchive){
                qo.add(Filter.eq("owner.name", params.get("ownerName")));
            }
            else {
                qo.add(Filter.ignoreCase(Filter.like("ownerName", params.get("ownerName"), MatchMode.START)));
            }
        }
        
        if (params.get("ownerId") != null && !"".equals(params.get("ownerId"))) {
            if(!isArchive){
                qo.add(Filter.eq("owner.id", params.get("ownerId")));
            }
            else {
                qo.add(Filter.ignoreCase(Filter.like("ownerName", params.get("ownerId"), MatchMode.START)));
            }
        }
        
        if (params.get("requestorId") != null && !"".equals(params.get("requestorId"))) {
            // Note the spelling difference between requestOr and requestEr. Both are correct, we should 
            // probably pick one and stick with it throughout.
            if(!isArchive){
                qo.add(Filter.eq("requester.id", params.get("requestorId")));
            }
            else {
                qo.add(Filter.ignoreCase(Filter.like("requester", params.get("requestorId"), MatchMode.START)));
            }
        }
        
        if (params.get("assigneeId") != null && !"".equals(params.get("assigneeId"))) {
            if(!isArchive){
                qo.add(Filter.eq("assignee.id", params.get("assigneeId")));
            }
            else {
                qo.add(Filter.ignoreCase(Filter.like("assignee", params.get("assigneeId"), MatchMode.START)));
            }
        }
        
        if (params.get("reminders") != null && !"".equals(params.get("reminders"))) {
            Filter.LogicalOperation logicOp = Filter.LogicalOperation.EQ;
            if(params.get("remCondition") != null && !"".equals(params.get("remCondition"))) {
                if(params.get("remCondition").equalsIgnoreCase("gt")) {
                    logicOp = Filter.LogicalOperation.GT;
                }
                else if (params.get("remCondition").equalsIgnoreCase("lt")) {
                    logicOp = Filter.LogicalOperation.LT;
                }
            }
            try {
                qo.add(new LeafFilter(logicOp, "reminders", Integer.parseInt((String)params.get("reminders"))));
            }
            catch(NumberFormatException e) {}
        }
        
        if (params.get("escalationCount") != null && !"".equals(params.get("escalationCount"))) {
            //EscCountCondition
            Filter.LogicalOperation logicOp = Filter.LogicalOperation.EQ;
            if(params.get("escCountCondition") != null && !"".equals(params.get("escCountCondition"))) {
                if(params.get("escCountCondition").equalsIgnoreCase("gt")) {
                    logicOp = Filter.LogicalOperation.GT;
                }
                else if (params.get("escCountCondition").equalsIgnoreCase("lt")) {
                    logicOp = Filter.LogicalOperation.LT;
                }
            }
            try {
                qo.add(new LeafFilter(logicOp, "escalationCount", Integer.parseInt((String)params.get("escalationCount"))));
            }
            catch(NumberFormatException e) {}
        }
        
        if (params.get("wakeUpStartDate") != null && !"".equals(params.get("wakeUpStartDate"))) {
            Date wakeUpStartDate = new Date(Long.parseLong((String)params.get("wakeUpStartDate")));
            qo.add(Filter.ge("wakeUpDate", wakeUpStartDate));
        }
        
        if (params.get("wakeUpEndDate") != null && !"".equals(params.get("wakeUpEndDate"))) {
            Date wakeUpEndDate = new Date(Long.parseLong((String)params.get("wakeUpEndDate")));
            // Not sure if setting the time is really necessary, but what the heck...
            Calendar cal = Calendar.getInstance();  
            cal.setTime(wakeUpEndDate);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);

            qo.add(Filter.le("wakeUpDate", cal.getTime()));
        }
        
        if (params.get("identityRequestId") != null && !"".equals(params.get("identityRequestId"))) {
            qo.add(Filter.eq("identityRequestId", Util.padID(params.get("identityRequestId"))));
        }
        
        if (params.get("targetName") != null && !"".equals(params.get("targetName"))) {
            qo.add(Filter.like("targetName", params.get("targetName")));
        }
        
        if (params.get("signed") != null && !"".equals(params.get("signed"))) {
            String signedParm = params.get("signed");
            if (signedParm.equals("true")) {
                qo.add(Filter.eq("signed", true));
            }
            else if (signedParm.equals("false")) {
                qo.add(Filter.eq("signed", false));
            }
        }
        
        if (params.get("completer") != null && !"".equals(params.get("completer"))) {
            qo.add(Filter.eq("completer", params.get("completer")));
        }
    }
    
    public static Map<String, String> convertMultiToSingleMap(MultivaluedMap<String, ?> mmap) {
        Map<String, String> newMap = new HashMap<String, String>();
        Iterator keys = mmap.keySet().iterator();
        String key = "";
        while(keys.hasNext()) {
            key = (String)keys.next();
            newMap.put(key, (String)mmap.getFirst(key));
        }
        return newMap;
    }

    /**
     * @param itemOwner Displayable name of the user who owns the WorkItem that is being edited
     * @param requester Displayable name of the user who requested the Workitem
     * @param editingUser Identity that is attempting to edit the WorkItem's request priority
     * @return true if the specific user is authorized to edit the request priority on the specified WorkItem
     */
    public static boolean isWorkItemPriorityEditingEnabled(String itemOwner, String requester, Identity editingUser) {
        List<Identity> workgroups = editingUser.getWorkgroups();
        Set<String> workgroupNames = new HashSet<String>();
        if (workgroups != null && !workgroups.isEmpty()) {
            for (Identity workgroup : workgroups) {
                workgroupNames.add(workgroup.getName());
            }
        }
        
        CapabilityManager capabilityManager = editingUser.getCapabilityManager();
        
        boolean isEditable = 
                (itemOwner != null && itemOwner.equals(editingUser.getDisplayableName())) ||
                capabilityManager.hasCapability(WORK_ITEM_ADMINISTRATOR_CAPABILITY) ||
                workgroupNames.contains(itemOwner) ||
                (requester != null && requester.equals(editingUser.getDisplayableName()));
        
        isEditable &= Util.otob(Configuration.getSystemConfig().getBoolean(Configuration.WORK_ITEM_PRIORITY_EDITING_ENABLED));

        return isEditable;
    }

    /**
     * Find work item id from list of work items
     *
     * @param items list of work items
     * @param loggedInUser logged in user to use
     * @return {String} workItemID
     * @throws GeneralException
     */
    public static String getWorkItemId(List<WorkItem> items, Identity loggedInUser) throws GeneralException {
        String workItemId = null;

        // if there is only one work item for this cert, then this must be the one to forward independent of the
        // owner matching the logged in user.
        if ( items.size() == 1 ) {
            workItemId = items.get(0).getId();
        } else {
            for (WorkItem item : items) {
                Identity owner = item.getOwner();
                if ((owner.isWorkgroup() && loggedInUser.isInWorkGroup(owner)) || owner.equals(loggedInUser)) {
                    workItemId = item.getId();
                    break;
                }
            }
        }
        return workItemId;
    }


    /**
     * Utility method to find work item id from certification item id or entity id.
     *
     * @param certificationItemId
     * @return {String} work item id
     * @throws GeneralException
     */
    public static String getWorkItemId(SailPointContext context, String certificationItemId) throws GeneralException {
        CertificationItem item = context.getObjectById(CertificationItem.class, certificationItemId);
        String entityId = item.getParent().getId();

        QueryOptions workItemQuery = new QueryOptions(Filter.or(Filter.eq("certificationItem", certificationItemId),
            Filter.eq("certificationEntity", entityId)));

        Iterator<Object[]> workItemIter = context.search(WorkItem.class, workItemQuery, Arrays.asList("id"));

        String workItemId = "";

        if (workItemIter.hasNext()) {
            workItemId = (String)workItemIter.next()[0];
        }
        return workItemId;
    }
}
