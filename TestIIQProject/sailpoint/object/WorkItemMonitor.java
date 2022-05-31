/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Part of the Certification model, but potentialy useful elsewhere.
 * Used by complex objects that need to trigger the generation of
 * a WorkItem for a particular object in a composite, and then track
 * the status of that item.
 *
 * For the Certification model, this is the Base class of 
 * CertificationDelegation, CertificationAction, and CertificationChallenge.
 *
 * There are roughly two sets of fields: those that trigger the generation
 * of a work item, and those that hold the state of an active or
 * completed work item.
 *
 * Most of the trigger fields are not used after the WorkItem has been
 * created.  We could potentially factor those out into a seperate 
 * class like WorkItemTemplate, but there aren't that many of them, and
 * we can overload some of them to hold completion state.  If we get too many
 * more, it probably makes sense just to have the application create a
 * partially fleshed out WorkItem and leave it here for the controller
 * to finish.
 *
 * While the work item is active the _workItem field will contain the
 * internal id of the item.  Note that we do not maintain direct references
 * to the WorkItem so that it may be deleted without violating a Hibernate
 * key constraint.  This is kept around after the work item is completed to
 * help correlate actions to the delegations that they were completed in.
 *
 * Once the WorkItem has been completed, a few bits of completion status
 * are copied from the WorkItem back into the Monitor before the WorkItem
 * is deleted. 
 * 
 * To be more precise, there are three phases the monitor can be in:
 *
 * - Trigger
 *
 * During the trigger phase a WorkItem has not yet been created, and
 * the application specifies the owner and other properties of the
 * new WorkItem.   The WorkItem is created as a side effect of some
 * form of "controller refresh" such as Certificationer.refresh().
 *
 * - Active
 *
 * During the active phase, the WorkItem id set set but the work item
 * completion fields are not.
 *
 * - Complete
 *
 * During the completion phase, the work item completion fields are set.
 * The work item id remains set (even though the WorkItem no longer exists)
 * to help correlate which delegations actions were decided in. 
 * 
 * HIBERNATE ISSUES
 *
 * I decided not to make these SailPointObjects, but since we are 
 * maintaining our own Hibernate table for them that wouldn't be much 
 * of a stretch.  But it doesn't seem very useful to do SailPointContext
 * queries on CertificationDelegation and CertificationAction, in the first
 * case you can search WorkItems, there are no use cases for the second case.
 * 
 * 
 * Author: Jeff
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.Date;


@XMLClass
public class WorkItemMonitor extends SailPointObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Trigger Fields
    //

    /**
     * The name of an Identity or a random email address.
     * 
     * During the initial trigger and active phases, setting this will
     * trigger the generation of a WorkItem or notification.  
     * If the name can be resolved to an Identity, a WorkItem is generated
     * and initially owned by that user. If this is simply an email address,
     * we will send a notification if possible but not open a work item.
     * When set during the active phase, it indicates that the current
     * work item should be deleted and a new one generated.
     *
     * During the complete phase, this will hold the name of the final
     * owner of the work item, which can be retained for historical reporting.
     */
    String _ownerName;

    /**
     * The name of an EmailTemplate object used to format the 
     * notification sent to the owner. If not set, a standard template 
     * from the global configuration is normally used.
     * This field has no meaning once the work item is complete.
     */
    String _emailTemplate;

    /**
     * A longer description of the work item.
     * This can contain arbitrarily long text that describes in detail
     * what the remediator or delegatee is supposed to do.
     * It is not used once a work item has been generated.
     * It can be retained in an object archive if you want to know the
     * original work item comments, or it can be deleted.
     */
    String _comments;

    /**
     * An optional expiration date for the work item.
     * It is not used once a work item has been generated.
     */
    Date _expiration;

    //
    // Active fields
    //

    /**
     * A field that holds the id of the previously generated work item.
     * This should not be included in an archive. This is maintained
     * even after the work item is complete to help correlate actions to
     * the work item in which they were decided.
     */
    String _workItem;

    //
    // Completion Fields
    // NOTE: If we have much more return state from the WorkItem, 
    // then we should consider just leaving the WorkItem around and
    // letting the application go there for info.  We would need to keep    
    // these work items out of the inbox.  
    // Hmm, then again having the local copies is nice for the archive.
    // 

    /**
     * Status of the completed work item.
     * Depending on the context, this might not be set in an archive if
     * the WorkItem has a life cycle that is independent of the object
     * containing the monitor.
     * Once this is set, the action is considered complete, and another
     * work item will not be generated.
     */
    WorkItem.State _completionState;

    /**
     * Completion comments left in the work item.
     * Expected to be used only for WorkItem.State.Returned to indicate
     * why the item was returned. Should be displayed in the UI.
     */
    String _completionComments;
    
    /**
     * Username of the person performing the action required to complete the
     * certification. In a remediation workitem, this would be the person who is logged
     * in when completing the RemediationItem.
     */
    String _completionUser;

    //
    // Context Fields (provide information about who and in what context was
    // the decision made)
    //

    /**
     * The name of the Identity that made the decision. This can be used for
     * auditing, but is also useful for rolling back changes made during a work
     * item that is revoked/rejected and allowing/disallowing editing decisions
     * based identity. This is stored as a name rather than an Identity
     * since this is part of the certification model and needs to be archivable.
     */
    String _actorName;

    /**
     * The ID of the WorkItem in which this decision was made (if the decision
     * was made in the context of a work item). This is used when an item is
     * delegated to support rolling back changes and to determine which
     * decisions are editable in a given context (for example - from within a work item
     * vs. from within the certification). This field can be cleared once the
     * delegation is no longer active.
     */
    String _actingWorkItem;

    private String _actorDisplayName;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public WorkItemMonitor() {
        super();
    }

    public WorkItemMonitor(String workItem, Identity owner) {
        this();

        _workItem = workItem;
        if (null != owner) {
            _ownerName = owner.getName();
        }
    }


    @Override
    public boolean hasName()
    {
        return false;
    }

    //
    // Trigger Properties
    //

// DO NOT USE THESE - WOULD LIKE TO THROW A RUNTIME EXCEPTION HERE TO CATCH ANY
// USES IN THE UNIT TESTS, BUT THIS MESSES UP HIBERNATE.
//    @Override
//    public void setOwner(Identity id) {
//        throw new RuntimeException("Use setOwnerName() instead.");
//    }
//
//    @Override
//    public Identity getOwner() {
//        throw new RuntimeException("Use getOwnerName() instead.");
//    }

    @XMLProperty
    public void setOwnerName(String s) {
        _ownerName = s;
    }

    public String getOwnerName() {
        return _ownerName;
    }

    @XMLProperty
    public void setEmailTemplate(String s) {
        _emailTemplate = s;
    }

    public String getEmailTemplate() {
        return _emailTemplate;
    }

    @XMLProperty
    public void setComments(String s) {
        _comments = s;
    }

    public String getComments() {
        return _comments;
    }

    @XMLProperty
    public void setExpiration(Date d) {
        _expiration = d;
    }

    public Date getExpiration() {
        return _expiration;
    }

    //
    // Active/Completed Status Properties
    //

    @XMLProperty
    public void setWorkItem(String s) {
        _workItem = s;
    }

    public String getWorkItem() {
        return _workItem;
    }

    /**
     * Return the WorkItem for this monitor if there is one and it still exists.
     */
    public WorkItem getWorkItem(Resolver resolver) throws GeneralException {
        WorkItem item = null;
        if (null != _workItem) {
            item = resolver.getObjectById(WorkItem.class, _workItem);
        }
        return item;
    }
    
    //
    // Completed Status Properties
    //

    @XMLProperty
    public void setCompletionState(WorkItem.State s) {
        _completionState = s;
    }

    public WorkItem.State getCompletionState() {
        return _completionState;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setCompletionComments(String s) {
        _completionComments = s;
    }

    public String getCompletionComments() {
        return _completionComments;
    }
    
    @XMLProperty
    public void setCompletionUser(String s) {
        _completionUser = s;
    }
    
    public String getCompletionUser() {
        return _completionUser;
    }
    
    //
    // Context Properties
    //

    @XMLProperty
    public void setActorName(String actorName) {
        _actorName = actorName;
    }

    public String getActorName() {
        return _actorName;
    }
    
    public void setActorDisplayName(String name) {
        _actorDisplayName = name;
    }
    
    @XMLProperty
    public String getActorDisplayName() {
        if (_actorDisplayName == null || _actorDisplayName.length() == 0) {
            return _actorName;
        }
        return _actorDisplayName;
    }

    /**
     * Set the actor Identity of this WorkItemMonitor.
     */
    public void setActor(Identity actor) {
        _actorName = (null != actor) ? actor.getName() : null;
        _actorDisplayName = (null != actor) ? actor.getDisplayName() : null;
    }
    
    /**
     * Return the actor of this WorkItemMonitor using the given Resolver.
     */
    public Identity getActor(Resolver r) throws GeneralException {
        Identity actor = null;
        if (null != _actorName) {
            actor = r.getObjectByName(Identity.class, _actorName);
        }
        return actor;
    }

    @XMLProperty
    public void setActingWorkItem(String workItem) {
        _actingWorkItem = workItem;
    }

    public String getActingWorkItem() {
        return _actingWorkItem;
    }

    //
    // Other Methods
    //

    /**
     * Return whether this work item monitor still has an active work item.
     * 
     * @return True if this work item monitor still has an active work item,
     *         false otherwise.
     */
    public boolean isActive() {
        return (null == _completionState);
    }

    /**
     * Return true if this work item monitor has been returned or expired.
     * 
     * @return True if this work item monitor has been returned or expired,
     *         false otherwise.
     */
    public boolean isReturned() {
        return (WorkItem.State.Returned.equals(_completionState) ||
                WorkItem.State.Expired.equals(_completionState));
    }

    /**
     * Save the context in which this decision is being made.
     * 
     * @param  who       The Identity that is making the decision.
     * @param  workItem  The (possibly null) ID of the work item in which the
     *                   decision is being made.
     */
    protected void saveContext(Identity who, String workItem) {
        setActor(who);
        _actingWorkItem = workItem;
    }

    /**
     * Clear all fields because the decision is being changed. The new decision
     * will provide values for all relevant fields.
     */
    protected void clearData() throws GeneralException {
        _comments = null;
        _completionComments = null;
        _completionState = null;
        _description = null;
        _expiration = null;
        _ownerName = null;
        _actorName = null;
        _actingWorkItem = null;

        // I think it is safe to clear this because
        // CertificationDelegation.clear() will make sure that the work item has
        // been revoked before it is cleared.
        _workItem = null;
    }
}
