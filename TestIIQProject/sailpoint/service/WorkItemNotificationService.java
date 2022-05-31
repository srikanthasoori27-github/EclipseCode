package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * WorkItemNotificationService provides methods mapping WorkItem.Types to NotificationMenuItems and
 * information about available notifications
 */
public class WorkItemNotificationService {

    private final SailPointContext context;
    private final UserContext userContext ;

    /**
     * Mapping work item types to their notification menu item
     */
    private static final Map<WorkItem.Type, NotificationMenuItems> typeToMenuItemMap;

    static {
        Map<WorkItem.Type, NotificationMenuItems> tmpMap = new HashMap<WorkItem.Type, NotificationMenuItems>();
        tmpMap.put(WorkItem.Type.Approval, NotificationMenuItems.Approvals);
        tmpMap.put(WorkItem.Type.Form, NotificationMenuItems.Forms);
        tmpMap.put(WorkItem.Type.PolicyViolation, NotificationMenuItems.Violations);
        tmpMap.put(WorkItem.Type.ViolationReview, NotificationMenuItems.Violations);

        for (WorkItem.Type type : WorkItem.Type.values()) {
            if (!tmpMap.containsKey(type)) {
                // We have historically excluded these types, so do it here too
                // First seen in WorkItemBean.getTypes
                if (!WorkItem.Type.Test.equals(type) &&
                        !WorkItem.Type.Event.equals(type) &&
                        !WorkItem.Type.ImpactAnalysis.equals(type) &&
                        !WorkItem.Type.Generic.equals(type)) {
                    tmpMap.put(type, NotificationMenuItems.Other);
                }
            }
        }
        typeToMenuItemMap = Collections.unmodifiableMap(tmpMap);
    }

    /**
     * Get the notification menu item that includes the work item type
     * @param type WorkItem type
     * @return NotificationMenuItems value
     */
    public static NotificationMenuItems getMenuItemForType(WorkItem.Type type) {
        return typeToMenuItemMap.get(type);
    }

    /**
     * Get all the work item types included in the notification menu item
     * @param menuItem NotificationMenuItems
     * @return List of WorkItem types
     */
    public static List<WorkItem.Type> getTypesForMenuItem(NotificationMenuItems menuItem) {
        List<WorkItem.Type> types = new ArrayList<WorkItem.Type>();
        for (Map.Entry<WorkItem.Type, NotificationMenuItems> typeEntry : typeToMenuItemMap.entrySet()) {
            if (menuItem.equals(typeEntry.getValue())) {
                types.add(typeEntry.getKey());
            }
        }

        return types;
    }

    /**
     * Menu Items in the Work Item Notifications menu
     */
    public static enum NotificationMenuItems {
        Approvals(MessageKeys.MENU_NOTIFICATIONS_ITEM_APPROVALS),
        Forms(MessageKeys.MENU_NOTIFICATIONS_ITEM_FORMS),
        Violations(MessageKeys.MENU_NOTIFICATIONS_ITEM_VIOLATIONS),
        Other(MessageKeys.MENU_NOTIFICATIONS_ITEM_OTHER);

        private String messageKey;

        NotificationMenuItems(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }

    public WorkItemNotificationService(UserContext userContext) {
        this.userContext = userContext;
        this.context = userContext.getContext();
    }

    /**
     * Returns a map of work item notification counts.
     * Map will be keyed by menu item NOTIFICATIONS_MENU_ITEM_* entries. "Violations" includes both PolicyViolation and
     * ViolationReview. "Other" includes all types not included in other entries
     * @return a map of counts
     */
    public Map<String, Object> getWorkItemNotifications() throws GeneralException
    {
        QueryOptions ops = new QueryOptions();
        ops.add(QueryOptions.getOwnerScopeFilter(userContext.getLoggedInUser(), "owner"));
        ops.addGroupBy("type");

        List<String> cols = new ArrayList<String>();
        cols.add("type");
        cols.add("count(*)"); // could also be count(id)

        Map<String, Object> result = new HashMap<String, Object>();

        // Create a map with counts for the types that exist, and also sum the "other" count.
        Iterator<Object[]> rows = context.search(WorkItem.class, ops, cols);
        if (rows != null) {
            while (rows.hasNext()) {
                Object[] row = rows.next();
                WorkItem.Type type = (WorkItem.Type) row[0];
                Long count = (Long) row[1];
                addResult(result, type, count);
            }
        }

        // Fill in missing counts with 0
        for (WorkItemNotificationService.NotificationMenuItems notificationMenuItem : WorkItemNotificationService.NotificationMenuItems.values()) {
            if (!result.containsKey(notificationMenuItem.toString())) {
                result.put(notificationMenuItem.toString(), 0L);
            }
        }

        return result;
    }

    /**
     * Add the count to the menu item for the work type, keeping tally if already exists
     */
    private void addResult(Map<String, Object> result, WorkItem.Type workItemType, Long count) {
        WorkItemNotificationService.NotificationMenuItems menuItem = WorkItemNotificationService.getMenuItemForType(workItemType);
        if (menuItem != null) {
            String key = menuItem.toString();
            if (!result.containsKey(key)) {
                result.put(key, count);
            } else if (count > 0) {
                result.put(key, (Long)result.get(key) + count);
            }
        }
    }

    /**
     * Get the count of new work items owned by the user, optionally limited to ones created or changing ownership
     * after the given startTime
     * @param startTime Date to limit "new" work items. Optional.
     * @return Count of work items
     * @throws GeneralException
     */
    public int getNewWorkItemCount(Long startTime) throws GeneralException {
        Date startDate = (startTime != null && startTime > 0) ? new Date(startTime) : null;

        // First get ones created after start time
        QueryOptions ops = new QueryOptions();
        ops.add(QueryOptions.getOwnerScopeFilter(userContext.getLoggedInUser(), "owner"));
        if (startDate != null) {
            ops.add(Filter.gt("created", startDate));
        }

        int count = this.context.countObjects(WorkItem.class, ops);

        // Then if startTime is defined, check forwarding history
        if (startDate != null) {
            ops = new QueryOptions();
            ops.add(QueryOptions.getOwnerScopeFilter(userContext.getLoggedInUser(), "owner"));
            ops.add(Filter.gt("modified", startDate));
            // Don't want to get the ones we already counted from first query.
            ops.add(Filter.le("created", startDate));
            
            Iterator<Object[]> workItems = this.context.search(WorkItem.class, ops, "ownerHistory");
            while (workItems.hasNext()) {
                Object[] workItem = workItems.next();
                @SuppressWarnings("unchecked")
                List<WorkItem.OwnerHistory> ownerHistories = (List<WorkItem.OwnerHistory>) workItem[0];
                if (!Util.isEmpty(ownerHistories)) {
                    WorkItem.OwnerHistory lastOwnerHistory = ownerHistories.get(ownerHistories.size() - 1);
                    if (Util.nullSafeCompareTo(lastOwnerHistory.getStartDate(), startDate) > 0) {
                        count++;
                    }
                }
            }
        }

        return count;
    }


}
