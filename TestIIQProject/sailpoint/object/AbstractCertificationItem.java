/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * 
 */

package sailpoint.object;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.WatchableMap;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * Abstract superclass for a certifiable item in a certification.
 *
 * In some customer situations, customers will want to add additional behavior
 * and information to the certification model.  The "custom" fields in
 * CertificationItem can be used to store this information.  Certificationer
 * provides hooks during generation, refresh, etc... to insert custom behavior.
 * 
 */
@XMLClass
public abstract class AbstractCertificationItem
    extends SailPointObject
    implements Cloneable, WorkItemOwnerChangeListener,
               WatchableMap.Watcher<String, Object>
{
    private static final Log log = LogFactory.getLog(AbstractCertificationItem.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Status
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An enumeration of possible statuses for an AbstractCertificationItem.
     * @ignore
     * Note that the order of declaration of the statuses is important because
     * this is used in the compareTo() method.
     */
    @XMLClass
    public static enum Status implements Localizable, MessageKeyHolder {
    
        /**
         * The certification item has been fully acted upon.
         */
        Complete(MessageKeys.CERT_ITEM_STAT_COMPLETE, true),
    
        /**
         * The certification item has not been acted upon.
         */
        Open(MessageKeys.CERT_ITEM_STAT_OPEN),

        /**
         * The certification item has been delegated.
         */
        Delegated(MessageKeys.CERT_ITEM_STAT_DELEGATED),

        /**
         * The certification item was completed by a delegate, but has not yet
         * been reviewed by the owner.
         */
        WaitingReview(MessageKeys.CERT_ITEM_STAT_REVIEW),
    
        /**
         * The certification item has been returned ("bounced back") from a
         * delegate that has chosen not to work on the item.
         */
        Returned(MessageKeys.CERT_ITEM_STAT_RETURNED),
    
        /**
         * The certification item has been challenged.
         */
        Challenged(MessageKeys.CERT_ITEM_STAT_CHALLENGED, true);
    
        private String messageKey;
        private boolean complete;
    
        private Status(String messageKey) {
            this(messageKey, false);
        }

        private Status(String messageKey, boolean complete) {
            this.messageKey = messageKey;
            this.complete = complete;
        }

        public String getMessageKey() {
            return this.messageKey;
        }

        /**
         * Return whether this state is considered complete.
         */
        public boolean isComplete() {
            return this.complete;
        }

        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // ContinuousState
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An enumeration of possible continuous certification states that indicate
     * the timeliness of certification for an AbstractCertificationItem.
     * 
     * @ignore
     * Note that the order of declaration of the states is important because
     * this is used in the compareTo() method.
     */
    @XMLClass
    public static enum ContinuousState implements Localizable {
    
        /**
         * The item has been certified and does not currently need
         * recertification.
         */
        Certified(MessageKeys.CERT_ITEM_STATE_CERTIFIED) {
            @Override
            public void leaveItem(Certification cert) { cert.decCertifiedItems(); }
            @Override
            public void enterItem(Certification cert) { cert.incCertifiedItems(); }
            @Override
            public void leaveEntity(Certification cert) { cert.decCertifiedEntities(); }
            @Override
            public void enterEntity(Certification cert) { cert.incCertifiedEntities(); }
        },
    
        /**
         * The item is in need recertification soon.
         */
        CertificationRequired(MessageKeys.CERT_ITEM_STATE_CERTIFICATION_REQUIRED) {
            @Override
            public void leaveItem(Certification cert) { cert.decCertificationRequiredItems(); }
            @Override
            public void enterItem(Certification cert) { cert.incCertificationRequiredItems(); }
            @Override
            public void leaveEntity(Certification cert) { cert.decCertificationRequiredEntities(); }
            @Override
            public void enterEntity(Certification cert) { cert.incCertificationRequiredEntities(); }
        },
    
        /**
         * The item is overdue for recertification.
         */
        Overdue(MessageKeys.CERT_ITEM_STATE_OVERDUE) {
            @Override
            public void leaveItem(Certification cert) { cert.decOverdueItems(); }
            @Override
            public void enterItem(Certification cert) { cert.incOverdueItems(); }
            @Override
            public void leaveEntity(Certification cert) { cert.decOverdueEntities(); }
            @Override
            public void enterEntity(Certification cert) { cert.incOverdueEntities(); }
        };
    
        private String messageKey;
    
        private ContinuousState(String messageKey) {
            this.messageKey = messageKey;
        }
    
        public String getMessageKey() {
            return this.messageKey;
        }

        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }

        /**
         * Tell the given certification that an item is transitioning to this
         * state from the possibly null given state.
         */
        public void transitionItemTo(ContinuousState from, Certification cert) {
            if (null != from) {
                from.leaveItem(cert);
            }

            this.enterItem(cert);
        }

        /**
         * Tell the given certification that an entity is transitioning to this
         * state from the possibly null given state.
         */
        public void transitionEntityTo(ContinuousState from, Certification cert) {
            if (null != from) {
                from.leaveEntity(cert);
            }

            this.enterEntity(cert);
        }
        
        /**
         * Tell the given Certification that this state is being entered for an
         * item.
         */
        protected abstract void enterItem(Certification cert);

        /**
         * Tell the given Certification that this state is being left for an
         * item.
         */
        protected abstract void leaveItem(Certification cert);

        /**
         * Tell the given Certification that this state is being entered for an
         * entity.
         */
        protected abstract void enterEntity(Certification cert);

        /**
         * Tell the given Certification that this state is being left for an
         * entity.
         */
        protected abstract void leaveEntity(Certification cert);
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the Identity that certified to this item.
     * If null, assumed to be the same as the user that certified
     * to the parent CertificationIdentity.
     *
     * TODO: Do we need both this and the WorkItemMonitor._actor fields?
     */
    String _certifier;
    
    /**
     * The certification decision made for this item.
     * This must be set for the item to be considered complete.
     */
    CertificationAction _action;

    /**
     * Information about item delegation.
     */
    CertificationDelegation _delegation;

    /**
     * Date this certification was completed.
     * A non-null value implies completion.
     * The Certificationer and UI should not set this until all
     * CertificationItems have been marked completed.
     * 
     * @ignore
     * TODO: Do we need a date here or just a flag?
     */
    Date _completed;

    /**
     * The overall status of the item.  This is based on the action, delegation,
     * challenge, etc...
     */
    Status _summaryStatus = Status.Open;

    /**
     * The continuous certification state for this item.  This is null for
     * periodic (non-continuous) certifications. 
     */
    ContinuousState _continuousState;
    
    /**
     * The date at which the last decision was committed for a continuous
     * certification.  For CertificationEntities, this holds the most recent
     * last decision.
     */
    Date _lastDecision;
    
    /**
     * The date at which the continuous certification state will next be
     * incremented.  This is null for periodic (non-continuous)
     * certifications, or if the state is Overdue.  For CertificationEntities,
     * this holds the nearest state change of all items.
     */
    Date _nextContinuousStateChange;

    /**
     * The date at which this item will be (or was) overdue.  This is null
     * for periodic (non-continuous) certifications.  For
     * CertificationEntities, this is the lowest date of all items.
     */
    Date _overdueDate;
    
    /**
     * A boolean indicating whether this certification item has
     * compliance-relevant differences since the last certification.
     * This is maintained in a separate field from actual differences to
     * allow hibernate sorting.
     */
    boolean _hasDifferences;

    /**
     * A boolean indication of whether this item needs further action.  Currently,
     * this indicates that either a challenge needs to be addressed or a
     * delegation decision needs to be reviewed.  This could be calculated
     * dynamically, but is rolled up to allow for searching, sorting, and for
     * better performance when displayed in a grid.
     */
    boolean _actionRequired;
    
    /**
     * The ID of the entity being certified.
     *
     * Note: The ID might reference an entity that no longer exists, such as
     * in an archived cert. In those cases the targetName should be used to
     * provide the user with some context.
     */
    String _targetId;

    /*
     * The name of the entity being certified.
     * 
     * Note: The entity name may have been changed since the certification
     * was created
     */
    String _targetName;
    
    /**
     * The display name of the entity being certified.
     */
    String _targetDisplayName;

    /**
     * Custom field that can be used to extend the certification model.
     */
    String _custom1;

    /**
     * Custom field that can be used to extend the certification model.
     */
    String _custom2;

    /**
     * A Map that contains arbitrary information that can be used to create
     * custom extensions to the certification model.  If you need to be able to
     * search on the custom field use one of the _extendedXXX fields instead.
     */
    Map<String, Object> _customMap;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////

    public AbstractCertificationItem()
    {
        super();
    }

    /**
     * These do not have names.
     */
    @Override
    public boolean hasName()
    {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Returns the ID of the entity being certified.
     *
     * Note: The ID might reference an entity which no longer exists, such as
     * in an archived certification. In those cases the targetName should be used to
     * provide the user with some context.
     *
     * @return The ID of the entity being certified. Can be null.
     */
    public String getTargetId() {
        return _targetId;
    }

    /**
     * Set the ID of the entity being certified.
     */
    @XMLProperty
    public void setTargetId(String targetId) {
        this._targetId = targetId;
    }


    /**
     * Returns the name of the entity being certified.
     *
     * Note: The entity name might have been changed since the certification
     * was created
     * 
     * @return The name of the entity being certified. Can be null. Can
     *  reference an entity which no longer exists.
     */
    public String getTargetName() {
        return _targetName;
    }

    /**
     * Sets the name of the entity being certified.
     *
     * @param targetName The name of the entity being certified. Can be
     *  null.
     */
    @XMLProperty
    public void setTargetName(String targetName) {
        this._targetName = targetName;
    }
    
    /**
     * Gets the display name of the entity being certified.
     * 
     * @return The display name.
     */
    @XMLProperty
    public String getTargetDisplayName() {
        return _targetDisplayName;
    }
    
    /**
     * Sets the display name of the entity being certified.
     * 
     * @param targetDisplayName The display name.
     */
    public void setTargetDisplayName(String targetDisplayName) {
        _targetDisplayName = targetDisplayName;
    }

    /**
     * Return the name of the Identity that this item is certifying.
     * 
     * @return The name of the Identity that this item is certifying.
     *
     * @ignore
     * TODO: This is not safe now that there are CertificationEntities of
     * different types (Identities, Account Groups, Roles, etc...).  If this is
     * being used to load an object by name, it should probably be returning a
     * Reference that can load the affected AbstractCertifiableEntity.
     */
    public abstract String getIdentity();

    /**
     * Get the list of CertificationItems being certified.  By default, this
     * returns null.
     * 
     * @return The list of CertificationItems being certified
     */
    public List<CertificationItem> getItems() {
        return null;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setAction(CertificationAction a) {
        _action = a;
    }

    /**
     * Get the certification decision made for this item.
     * This must be set for the item to be considered complete.
     */
    public CertificationAction getAction() {
        return _action;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setDelegation(CertificationDelegation a) {
        _delegation = a;
    }

    public CertificationDelegation getDelegation() {
        return _delegation;
    }

    @XMLProperty
    public void setCompleted(Date d) {
        _completed = d;
    }

    /**
     * Get the date this certification was completed.
     * A non-null value implies completion.
     */
    public Date getCompleted() {
        return _completed;
    }

    @XMLProperty
    public void setSummaryStatus(Status summaryStatus) {
        _summaryStatus = summaryStatus;
    }

    /**
     * Get the overall status of the item.  This is based on the action, delegation,
     * challenge, etc...
     */
    public Status getSummaryStatus() {
        return _summaryStatus;
    }

    @XMLProperty
    public void setContinuousState(ContinuousState state) {
        _continuousState = state;
    }
    
    /**
     * The continuous certification state for this item.  This will be null for
     * periodic (non-continuous) certifications. 
     */
    public ContinuousState getContinuousState() {
        return _continuousState;
    }
    
    @XMLProperty
    public void setLastDecision(Date lastDecision) {
        _lastDecision = lastDecision;
    }

    /**
     * The date at which the last decision was committed for a continuous
     * certification.  For CertificationEntities, this holds the most recent
     * decision.
     */
    public Date getLastDecision() {
        return _lastDecision;
    }

    @XMLProperty
    public void setNextContinuousStateChange(Date nextChange) {
        _nextContinuousStateChange = nextChange;
    }

    /**
     * The date at which the continuous certification state will next be
     * incremented.  This is null for periodic (non-continuous)
     * certifications, or if the state is Overdue.  For CertificationEntities,
     * this holds the nearest state change of all items.
     */
    public Date getNextContinuousStateChange() {
        return _nextContinuousStateChange;
    }

    @XMLProperty
    public void setOverdueDate(Date overdueDate) {
        _overdueDate = overdueDate;
    }

    /**
     * The date at which this item will be (or was) overdue.  This is null
     * for periodic (non-continuous) certifications.  For
     * CertificationEntities, this is the lowest date of all items.
     */
    public Date getOverdueDate() {
        return _overdueDate;
    }

    /**
     * A boolean indicating if this certification item has
     * compliance-relevant differences since the last certification.
     */
    @XMLProperty
    public boolean getHasDifferences() {
        return _hasDifferences;
    }

    public void setHasDifferences(boolean hasDifferences) {
        _hasDifferences = hasDifferences;
    }

    /**
     * A boolean indication if this item needs further action.  Currently,
     * this indicates that either a challenge needs to be addressed or a
     * delegation decision needs to be reviewed.  
     */
    @XMLProperty
    public boolean isActionRequired() {
        return _actionRequired;
    }

    public void setActionRequired(boolean actionRequired) {
        _actionRequired = actionRequired;
    }
    
    /**
     * @deprecated Only for use by hibernate - use {@link #setCustom1(String)}
     *             instead.
     */
    @XMLProperty(xmlname="custom1")
    public void setHibernateCustom1(String s) {
        _custom1 = s;
    }

    /**
     * @deprecated Only for use by hibernate - use {@link #getCustom1()}
     *             instead.
     */
    public String getHibernateCustom1() {
        return _custom1;
    }

    /**
     * @deprecated Only for use by hibernate - use {@link #setCustom2(String)}
     *             instead.
     */
    @XMLProperty(xmlname="custom2")
    public void setHibernateCustom2(String s) {
        _custom2 = s;
    }

    /**
     * @deprecated Only for use by hibernate - use {@link #getCustom2()}
     *             instead.
     */
    public String getHibernateCustom2() {
        return _custom2 ;
    }

    /**
     * @deprecated Only for use by hibernate - use {@link #setCustomMap(Map)}
     *             instead.
     */
    @XMLProperty(xmlname="CustomMap")
    public void setHibernateCustomMap(Map<String, Object> m) {
        _customMap = m;
    }

    /**
     * @deprecated Only for use by hibernate - use {@link #getCustomMap()}
     *             instead.
     */
    public Map<String, Object> getHibernateCustomMap() {
        return _customMap;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Pseudo-properties
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the custom1 custom property.
     */
    public String getCustom1() {
        return _custom1;
    }

    /**
     * Set the custom1 custom property - also mark this item for refresh.
     */
    public void setCustom1(String s) {
        _custom1 = s;
        this.markForRefresh();
    }

    /**
     * Get the custom2 custom property.
     */
    public String getCustom2() {
        return _custom2;
    }

    /**
     * Set the custom2 custom property - also mark this item for refresh.
     */
    public void setCustom2(String s) {
        _custom2 = s;
        this.markForRefresh();
    }

    /**
     * Get the extendedMap custom property.  Changes to this map will mark this
     * item for refresh.
     */
    public Map<String, Object> getCustomMap() {
        if (null == _customMap) {
            _customMap = new HashMap<String,Object>();
        }
        return new WatchableMap<String, Object>(_customMap, this);
    }

    /**
     * Set the extendedMap custom property - also mark this item for refresh.
     */
    public void setCustomMap(Map<String, Object> m) {
        // Pull the delegate out of the WatchableMap if we wrapped it.
        if (m instanceof WatchableMap) {
            _customMap = ((WatchableMap<String, Object>) m).getDelegate();
        }
        else {
            _customMap = m;
        }

        this.markForRefresh();
    }

    /**
     * WatchableMap.Watcher interface - the extendedMap was changed, so mark
     * this item for refresh.
     */
    public void mapChanged(Map<String, Object> m) {
        this.markForRefresh();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Helper methods
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Clear the ID off of this item and any associated objects.
     */
    public void clearIds() {
        _id = null;
        
        if (null != _action) {
            _action.setId(null);
        }

        if (null != _delegation) {
            _delegation.setId(null);
        }
    }
    

    public abstract Identity getDefaultRemediator(Resolver resolver) throws GeneralException;

    /**
     * Return the Certification that this item is a part of.
     */
    public abstract Certification getCertification();

    /**
     * Return the CertificationEntity for this item.
     */
    public abstract CertificationEntity getCertificationEntity();

    /**
     * Refresh the summary status of this item - this sets the summary status.
     */
    public void refreshSummaryStatus() {
        this.refreshSummaryStatus(null);
    }

    /**
     * Refresh the summary status of this item - this sets the summary status.
     * 
     * @param  completeOverride  A possibly-null boolean value to override the
     *                           completion status.  This allows a rule to be
     *                           run to prevent completion of items.
     */
    public abstract void refreshSummaryStatus(Boolean completeOverride);

    /**
     * Refresh if this item has difference or not - this sets the
     * hasDifferences flag.  This is currently specific to identities - 
     * should generalize to support differences on other certifiable entities.
     * 
     * @param  diff      The IdentityDifference object.
     * @param  resolver  The Resolver to use.
     * 
     * @return If this item has differences or not.
     */
    public abstract boolean refreshHasDifferences(IdentityDifference diff,
                                                  Resolver resolver)
        throws GeneralException;

    /**
     * Refresh if action is required on this item (for example - a challenge needs
     * a decision or a delegated decision needs to be reviewed).
     */
    public abstract void refreshActionRequired();

    /**
     * Returns a list of all applications associated with this entity's
     * certification items.
     *
     * @param  action   The action being executed.  This method only returns
     *                  information for remediation requests.
     * @param  isBulkCertified If action is bulk certified or not
     * @param  resolver Resolver instance used to lookup Applications
     * @return Non-null list of Application object.
     */
    public abstract List<Application> getApplications(CertificationAction action,
                                                      boolean isBulkCertified,
                                                      Resolver resolver)
        throws GeneralException;

    /**
     * Notify this item that a work item owner has been changed.  This resets
     * some state in the WorkItemMonitor if the monitor created the work item.
     * 
     * @param  resolver   The Resolver to use.
     * @param  item       The WorkItem that was forwarded.
     * @param  newOwner   The new owner for the work item.
     * @param  prevOwner  The previous owner of the work item.
     */
    public void workItemOwnerChanged(Resolver resolver, WorkItem item,
                                     Identity newOwner, Identity prevOwner) {
        WorkItemMonitor monitor = getWorkItemMonitor(item);
        if (null != monitor) {
            monitor.setOwnerName(newOwner.getName());
        }
    }

    /**
     * Return the WorkItemMonitor on this certification item that corresponds
     * to the given work item.
     * 
     * @param  item  The WorkItem for which to retrieve the monitor.
     * 
     * @return The WorkItemMonitor on this certification item that corresponds
     *         to the given work item, or null if there is no matching monitor
     *         for the given work item.
     */
    WorkItemMonitor getWorkItemMonitor(WorkItem item) {

        WorkItemMonitor monitor = null;
        if (isMonitorForWorkItem(_action, item)) {
            monitor = _action;
        }
        else if (isMonitorForWorkItem(_delegation, item)) {
            monitor = _delegation;
        }

        return monitor;
    }

    /**
     * Check if the given monitor was used to generate the given work item.
     * 
     * @param monitor WorkItemMonitor to match
     * @param workItem WorkItem to match
     *                 
     * @return True if work item monitor is used for the work item, otherwise false                
     */
    boolean isMonitorForWorkItem(WorkItemMonitor monitor, WorkItem workItem) {
        return (null != monitor) && (null != workItem.getId()) &&
               (workItem.getId().equals(monitor.getWorkItem()));
    }

    public boolean isComplete() {

        return _completed != null;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions on an AbstractCertificationItem
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Mark this item as needing to be refreshed the next time the certification
     * is refreshed.
     */
    public abstract void markForRefresh();

    /**
     * Mark this item as needing a continuous flush.
     */
    public abstract void markForContinuousFlush();
    
    /**
     * Bulk certify this item with the status and parameters from the given
     * certification action.  If the item is delegated and the request is not
     * coming from a delegated work item (for example, the workItem is null), the delegation
     * should be revoked.
     *
     * @param who        The Identity performing the bulk certification.
     * @param workItem   The ID of the work item in which the bulk certify is
     *                   taking place.
     * @param bulkAction The bulk action that contains the status and
     *                   parameters to use to bulk certify this identity.
     * @param selector   The selector used to determine which items are to be
     *                   bulk certified.  If null, all bulk certifiable items
     *                   are bulk certified.
     * @param force      If bulk certification should be forced on the children,
     *                   even if they do not usually allow it.  THIS SHOULD
     *                   ONLY BE USED FOR TESTING!
     *
     * @return A possibly empty list of CertificationItems that were not bulk
     *         certified.
     */
    public abstract List<CertificationItem> bulkCertify(Identity who, String workItem,
                                                        CertificationAction bulkAction,
                                                        CertificationItemSelector selector,
                                                        boolean force)
        throws GeneralException;

    /**
     * Delegate this item to the given recipient.  This also clears any action
     * that has already be set.
     * 
     * @param  requester    The Identity requesting the delegation.
     * @param  workItem     The (possibly null) ID of the work item in which the
     *                      request is being made.
     * @param  recipient    The name of the recipient for the delegation.
     * @param  description  The description for the delegation.
     * @param  comments     The comments for the recipient of the delegation.
     * 
     * @throws GeneralException  If there is already an active delegation for
     *                           this item.  The caller is responsible for
     *                           revoking the delegation and refreshing before
     *                           calling this.
     */
    public void delegate(Identity requester, String workItem, String recipient,
                         String description, String comments)
        throws GeneralException {

        // If there was a previous delegation for this item we have a couple of
        // options.  We could either throw an exception or implicitly revoke the
        // delegation.  Revoking is tricky because this method will reset the
        // delegation, which is storing the revocation information to be picked
        // up by the Certificationer.  To make our lives easier, we'll throw and
        // make it a precondition that the delegation has been revoked.
        if ((null != _delegation) && _delegation.isActive())
            throw new GeneralException(MessageKeys.ERR_ITEM_ALREADY_DELEGATED);

        // Clear the action and set delegation.  This saves the context of the
        // delegation in case it needs to be rolled back.  This will happen if
        // an identity is delegated and an item from that identity is
        // subsequently delegated.
        _action = null;
        _delegation = new CertificationDelegation();
        _delegation.delegate(requester, workItem, recipient, description, comments);

        // Bug 608 - If we're delegating an identity, clear an returned
        // delegations on the sub-items since the identity delegation will
        // override this.  If delegating an item, this will also clear the
        // returned delegation on the parent identity.
        removeReturnedDelegations();

        // Mark this item as needing to be refreshed by the Certificationer.
        markForRefresh();
    }

    /**
     * Clear the delegation on this item and its sub-items if it is returned.
     */
    void removeReturnedDelegations() {
        if ((null != _delegation) && _delegation.isReturned()) {
            _delegation = null;
        }
    }

    /**
     * Clear any approvals made automatically, typically before delegating an item.
     *
     * @param context SailPointContext
     * @param decider The Identity clearing the approvals
     * @param workItemId The work item associated to these removals
     * @throws GeneralException
     */
    void removeAutoApprovals(SailPointContext context, Identity decider, String workItemId) throws GeneralException {
        // By default this is a no op, but overriders can choose to remove auto-approvals here if needed.
    }

    /**
     * Revoke the delegation for this item and roll-back any changes made while
     * delegated.
     */
    public void revokeDelegation() {

        if (null == _delegation) {
            if (log.isWarnEnabled())
                log.warn("Item is not delegated - cannot revoke:" + this);
            
            return;
        }

        // Rollback changes made during delegation and mark it as being revoked.
        // The Certificationer will handle the rest upon refresh.
        rollbackChanges(_delegation.getWorkItem());
        _delegation.revoke();

        // Mark this item as needing to be refreshed by the Certificationer.
        markForRefresh();
    }

    /**
     * Roll-back any changes made in the context of the given work item.
     * 
     * @param  workItemId  The ID of the work item from which to roll-back
     *                     changes.
     *
     * @return True if the roll-back caused any changes, false otherwise.
     */
    public boolean rollbackChanges(String workItemId) {

        // KPG: Right now we're not allowing modifying decisions that have
        // already been made when in a work item.  If we start allowing this,
        // we'll have to store the previous state of the action (before
        // delegation) so that it can be rolled back.

        boolean rolledBack = false;

        // If the action was acted upon within this work item, null it out.
        if ((null != _action) && (null != _action.getActingWorkItem()) &&
            _action.getActingWorkItem().equals(workItemId)) {
            _action = null;
            rolledBack = true;
        }

        // If this item was delegated within a delegation (only possible when
        // delegating part of a delegated identity), revoke the delegation.
        if ((null != _delegation) && (null != _delegation.getActingWorkItem()) &&
            _delegation.getActingWorkItem().equals(workItemId)) {
            this.revokeDelegation();
            rolledBack = true;
        }

        // Mark this item as needing to be refreshed by the Certificationer if
        // the rollback occurred.
        if (rolledBack) {
            markForRefresh();
        }

        return rolledBack;
    }
}
