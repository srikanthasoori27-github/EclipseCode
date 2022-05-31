package sailpoint.rest;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.service.WorkItemNotificationService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import java.util.Map;

/**
 * Resource to retrieve counts associated with work item notification bell in menu.
 * This also updates the session values associated with notification.
 * 
 */
@Path("workItemNotifications")
public class WorkItemNotificationsResource extends BaseResource {

    /**
     * Session key to store the last time when notification was checked.
     * This should be updated whenever count() is called.
     */
    public static final String LAST_CHECKED_TIME = "notificationLastCheckedTime";
    
    /**
     * Session key to store the number of total notifications since last reset.
     * This should be updated after getting notification count; 
     * and reset to 0 after retrieving notification details.
     */
    public static final String NEW_ITEM_COUNT = "notificationNewItemCount";
    
    /**
     * Session key to store the last time when notification was reset.
     * This should be updated after retrieving notification details.
     */
    public static final String LAST_RESET_TIME = "notificationLastResetTime";

    /**
     * Returns a map of counts of various types of pending work items for the logged in user.
     * This also updates LAST_RESET_TIME and resets NEW_ITEM_COUNT in the session.
     *
     * @return map of counts
     * @throws sailpoint.tools.GeneralException
     */
    @GET
    public Map<String, Object> getWorkItemNotifications() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        
        setLastResetTime(System.currentTimeMillis());
        setNewItemCount(0);
        
        return new WorkItemNotificationService(this).getWorkItemNotifications();
    }

    /**
     * Returns the count of new work items created or forwarded to logged in user since 
     * the start time. If start time is not defined, count all work items owned by logged in user.
     * This also updates LAST_CHECKED_TIME and NEW_ITEM_COUNT in the session.
     * 
     * @return Integer count of work items
     * @throws GeneralException
     */
    @GET
    @Path("count")
    public int getWorkItemNotificationsCount() throws GeneralException {
        authorize(new AllowAllAuthorizer());

        setLastCheckedTime(System.currentTimeMillis());

        long startTime = getLastResetTime();
        int count = new WorkItemNotificationService(this).getNewWorkItemCount(startTime);

        setNewItemCount(count);
        
        return count;
    }
    
    /**
     * Updates LAST_CHECKED_TIME for work items notification.
     * 
     */
    protected void setLastCheckedTime(long lastCheckedTime) {
        getSession().setAttribute(LAST_CHECKED_TIME, lastCheckedTime);
    }
    
    /**
     * Updates LAST_RESET_TIME for work items notification.
     * 
     */
    protected void setLastResetTime(long lastResetTime) {
        getSession().setAttribute(LAST_RESET_TIME, lastResetTime);
    }
    
    /**
     * Retrieves LAST_RESET_TIME of work items notification.  
     * This returns 0 if it is not defined in the session.
     */
    protected long getLastResetTime() {
        if (getSession().getAttribute(LAST_RESET_TIME) != null) {
            return  (Long)getSession().getAttribute(LAST_RESET_TIME);
        } else {
            return 0;
        }
    }

    /**
     * Updates NEW_ITEM_COUNT of the work items notification.
     */
    protected void setNewItemCount(int newItemCount) {
        getSession().setAttribute(NEW_ITEM_COUNT, newItemCount);
    }
    

}