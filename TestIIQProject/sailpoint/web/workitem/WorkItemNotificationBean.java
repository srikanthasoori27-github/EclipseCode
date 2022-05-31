package sailpoint.web.workitem;

import sailpoint.object.WorkItem;
import sailpoint.rest.WorkItemNotificationsResource;
import sailpoint.service.WorkItemNotificationService;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Simple bean to retrieve values from session for use with Notifications bell menu item.
 * Following session values are set in WorkItemNotificationResource, "lastCheckedTime", 
 * "lastResetTime", "newItemCount".
 * 
 * See notificationsMenuItem.xhtml/notificationsMenuItem.js for usage.
 */
public class WorkItemNotificationBean extends BaseBean {
    
    
    private String menuItemName;
        
    public WorkItemNotificationBean() {
        super();
    }

    public long getLastCheckedTime() {
        if (getSessionScope().containsKey(WorkItemNotificationsResource.LAST_CHECKED_TIME)) {
            return (Long)getSessionScope().get(WorkItemNotificationsResource.LAST_CHECKED_TIME);
        } else {
            return 0;
        }
    }


    public long getLastResetTime() {
        if (getSessionScope().containsKey(WorkItemNotificationsResource.LAST_RESET_TIME)) {
            return (Long)getSessionScope().get(WorkItemNotificationsResource.LAST_RESET_TIME);
        } else {
            return 0;
        }
    }

    public int getNewItemCount() {
        if (getSessionScope().containsKey(WorkItemNotificationsResource.NEW_ITEM_COUNT)) {
            return (Integer)getSessionScope().get(WorkItemNotificationsResource.NEW_ITEM_COUNT);
        } else {
            return 0;
        }
    }
    
    public String getMenuItemName() {
        return this.menuItemName;
    }
    
    public void setMenuItemName(String menuItemName) {
        this.menuItemName = menuItemName;
    }

    /**
     * @return List of NotificationMenuItems for menu
     */
    public List<WorkItemNotificationService.NotificationMenuItems> getMenuItems() {
        return Arrays.asList(WorkItemNotificationService.NotificationMenuItems.values());
    }

    /**
     * Navigate to the Manage Work Items page with the desired type as query parameters
     */
    public String goToWorkItems() {
        WorkItemNotificationService.NotificationMenuItems menuItem =
                WorkItemNotificationService.NotificationMenuItems.valueOf(this.menuItemName);
        
        List<WorkItem.Type> types = WorkItemNotificationService.getTypesForMenuItem(menuItem);

        // Build query params for the navigation string with the work item types
        StringBuilder navigationSb = new StringBuilder("manageWorkItems#/workItems");
        
        // Navigate to approvals page instead work items
        if(this.menuItemName.equals(WorkItemNotificationService.NotificationMenuItems.Approvals.toString())) {
            navigationSb = new StringBuilder("viewResponsiveApproval#/approvals");
            return navigationSb.toString();
        }

        if (!Util.isEmpty(types)) {
            navigationSb.append("?");
            Iterator<WorkItem.Type> typeIterator = types.iterator();
            while (typeIterator.hasNext()) {
                navigationSb.append("workItemType=" + typeIterator.next());
                if (typeIterator.hasNext()) {
                    navigationSb.append("&");
                }
            }
        }

        return navigationSb.toString();
    }
}