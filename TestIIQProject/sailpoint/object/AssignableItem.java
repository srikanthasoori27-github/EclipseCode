package sailpoint.object;

/**
 * Simple interface used on WorkItems and RemediationItems
 * to handle both classes using the same Workflower
 * methods.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public interface AssignableItem {

    String getId();

    void setAssignee(Identity assignee);

    Identity getAssignee();

    Identity getOwner();

}
