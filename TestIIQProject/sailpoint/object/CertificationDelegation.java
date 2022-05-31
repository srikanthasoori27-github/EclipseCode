/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object holding the delegation state of a certification item.
 *
 * Almost all of the interesting work has been factored out into
 * the WorkItemMonitor class which is also shared by CertificationAction.
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object holding the delegation state of a certification item.
 * When one of these is created and stored on an CertificationIdentity
 * or CertificationItem, it triggers the generation of a WorkItem 
 * for the delegated user. While the work item is active, it maintains
 * a reference to the item for easier tracking. When the work item
 * is completed, it holds a copy of the completion state.
 *
 */
@XMLClass
public class CertificationDelegation extends WorkItemMonitor
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Flag indicating that a post-delegation review is required
     * by the original certifier for any actions performed by the
     * delegated user. Depending on configuration, review might
     * be forced even if this flag is not set, but this provides the
     * ability to have per-user reviews.
     */
    boolean _reviewRequired;

    /**
     * A transient field that is used to communicate to the Certificationer
     * that this delegation was revoked and the work item needs to be removed.
     * This will be set when this delegation is revoked. The Certificationer
     * is responsible for nulling out the delegation request after the work
     * item revocation is completed.
     */
    boolean _revoked;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationDelegation() {
    }

    @XMLProperty 
    public void setReviewRequired(boolean b) {
        _reviewRequired = b;
    }

    /**
     * Flag indicating that a post-delegation review is required
     * by the original certifier for any actions performed by the
     * delegated user. Depending on configuration, review might
     * be forced even if this flag is not set, but this provides the
     * ability to have per-user reviews.
     */
    public boolean isReviewRequired() {
        return _reviewRequired;
    }

    /**
     * @exclude
     * This setter should only be used by the XML serialization framework
     * and hibernate since un-revoking delegations is not allowed.
     * Instead use <code>revoke()</code>.
     *
     * @param  b  Whether this delegation is revoked or not.
     * 
     * @deprecated Use {@link #revoke()} 
     */
    @Deprecated
    @XMLProperty
    public void setRevoked(boolean b) {
        _revoked = b;
    }

    public boolean isRevoked() {
        return _revoked;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Actions on a delegation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Mark this delegation as being revoked. This will trigger the
     * Certificationer to revoke the work item and null out this
     * delegation.
     */
    public void revoke() {
        _revoked = true;
    }

    /**
     * Delegate to the given owner.
     */
    public void delegate(Identity requester, String workItem, String ownerName,
                         String description, String comments)
        throws GeneralException {

        clearData();
        saveContext(requester, workItem);
        _ownerName = ownerName;
        _description = description;
        _comments = comments;
    }

    /**
     * This delegation is about to be reused, so clear any fields that have
     * already been set.
     * 
     * @throws GeneralException  If this delegation is marked as revoked.
     *                           Clearing without first refreshing with the
     *                           Certificationer would cause a dangling work
     *                           item.
     */
    protected void clearData() throws GeneralException {
        if (_revoked) {
            throw new GeneralException("Cannot reset a revoked work item.  This will leave a dangling work item.");
        }

        super.clearData();
        _reviewRequired = false;
    }

    public Identity getOwner(Resolver resolver) throws GeneralException{
        return this.getOwnerName() != null ? resolver.getObjectByName(Identity.class, getOwnerName()) : null;
    }
}
