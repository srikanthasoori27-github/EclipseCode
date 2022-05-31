package sailpoint.object;

/**
 * Utility table for helping with bulk IN queries. Sqlserver IN query parameters maxes out at 2100 so we need to
 * move the IN params into a join table and use a subquery to join the params.
 * For example, Filter.in("manager.id", managerIds) would be converted to
 * Filter.subquery("manager.id", BulkJoinId.class, "joinId", Filter.eq("joinProperty", "manager.id")
 * The list of manager ids needs to be loaded into the BulkJoinId table first.
 * Its possible to have multiple IN filters but not recommended for performance reasons.
 * Currently, this is only being used by the LCMConfigService but could be generalized into the persistence manager.
 */
public class BulkIdJoin extends SailPointObject {
    private String joinId;

    private String joinProperty;

    private String userId;

    // Constructor
    public BulkIdJoin() {
    }

    public BulkIdJoin(String joinProperty, String joinId, String userId) {
        this.joinId = joinId;
        this.joinProperty = joinProperty;
        this.userId = userId;
    }

    public boolean hasName() {
        return false;
    }

    public String getJoinId() {
        return joinId;
    }

    public void setJoinId(String joinId) {
        this.joinId = joinId;
    }

    public String getJoinProperty() {
        return joinProperty;
    }

    public void setJoinProperty(String joinProperty) {
        this.joinProperty = joinProperty;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
