/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The state of one Identity within an Certification.
 *
 * Since these are archival objects, we may need a different strategy
 * for maintaining certification histories.  Rather than keeping the
 * fully expanded Hibernate model, the entire root Certification could
 * be XML serialized instead.
 *
 */

package sailpoint.object;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CertificationService;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.api.certification.DataOwnerCertifiableEntity;
import sailpoint.tools.BidirectionalCollection;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;


/**
 * The state of one entity(Identity, AccountGroup/ManagedAttribute, etc) within an Certification.
 */
@XMLClass
@Indexes({
        @Index(name="cert_entity_tdn_ci", property="targetDisplayName", caseSensitive=false)
})
public class CertificationEntity
        extends AbstractCertificationItem
        implements Cloneable {

    private static final Log log = LogFactory.getLog(CertificationEntity.class);

    // Attribute name where the entity snapshot is stored
    // Currently we only do this for role composition certifications
    // since identity snapshots are stored in the DB
    public static final String ATTR_SNAPSHOT = "snapshot";


    /**
     * Entity types.
     *
     */
    @XMLClass(xmlname = "CertificationEntityType")
    public static enum Type {

        Identity(MessageKeys.IDENTITY, true),

        // Account group certs can't be challenged.
        // Note that the class name was changed in 6.0 to be ManagedAttribute
        // but we still keep the old type name
        AccountGroup(MessageKeys.ACCOUNT_GROUP, false, Arrays.asList(Status.Complete, Status.Delegated, Status.Open,
                Status.Returned, Status.WaitingReview)),

        BusinessRole(MessageKeys.BIZ_ROLE, false, Arrays.asList(Status.Complete, Status.Delegated, Status.Open,
                Status.Returned, Status.WaitingReview)),

        DataOwner(MessageKeys.DATA_OWNER, false);

        private String messageKey;
        private List<Status> allowedStatus;
        private boolean historical;

        private Type(String messageKey, boolean historical) {
            this.messageKey = messageKey;
            this.historical = historical;
            allowedStatus = Arrays.asList(Status.values());
        }

        private Type(String displayName, boolean historical, List<Status> allowedStatus) {
            this(displayName, historical);
            this.allowedStatus = allowedStatus;
        }

        public String getMessageKey() {
            return this.messageKey;
        }

        public List<Status> getAllowedStatus() {
            return allowedStatus;
        }

        public boolean isHistorical() {
            return historical;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Type of this certification item.
     */
    Type _type;

    /**
     * Reference to our parent Certification.
     */
    Certification _certification;

    /**
     * The items requiring certification for this entity.
     */
    List<CertificationItem> _items;

    /**
     * A boolean indicating that this entity was bulk certified.
     */
    boolean _bulkCertified;

    /**
     * The name of the associated identity.
     */
    String _identity;

    // name of the assoc account group
    private String _accountGroup;

    // Account group's application. This is only relevant to account groups
    private String _application;

    // Native ID of the account group
    private String _nativeIdentity;

    // reference attribute of the account group
    private String _referenceAttribute;

    // If the entity being certified is a ManagedAttribute
    // we need the application object type.
    private String _schemaObjectType;

    // name of the assoc business role
    // private String _businessRole;

    // List of applications associated with the certification items. this is unrelated to
    // the _application field
    private List<Application> _applications;

    /**
     * The first name of the identity. Stored here to allow searching and
     * sorting without navigating through identity.
     */
    String _firstname;

    /**
     * The last name of the identity. Stored here to allow searching and
     * sorting without navigating through identity.
     */
    String _lastname;

    /**
     * The composite risk score of the identity. Stored here to allow searching and
     * sorting without navigating through identity.
     */
    int _compositeScore;

    /**
     * The unique id of the IdentitySnapshot containing the
     * state of the identity at the time of this certification.
     * Note that IdentityIQ does not own this and the snapshot might no longer
     * exist if this object is being decompressed from an certification archive.
     */
    String _snapshotId;

    /**
     * Changes to attribute and entitlements of this identity
     * since the last certification of this type. This can be
     * saved in the archive, though in theory it could
     * be reproduced as long as the IdentitySnapshots are still available.
     */
    IdentityDifference _differences;

    /**
     * Indicates that this is the first time this user has been certified for the current
     * certification type.
     */
    boolean _newUser;


    Attributes<String, Object> _attributes;

    /**
     * The Hibernate id of the pending Certification owner.
     * This is used by the new cert generator that defers the one-to-many
     * relationship from Certification to CertificationEntity.  During
     * generation we still however have to keep a temporary one-way
     * relationship.  It is a string rather than a formal object reference
     * so we don't have to pay for foreign key index maintenance.
     */
    String _pendingCertification;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationEntity() {
        super();
    }

    public CertificationEntity(Type type) {
        this();
        _type = type;
    }

    public CertificationEntity(AbstractCertifiableEntity entity) {
        if (entity instanceof Identity) {
            Identity identity = (Identity) entity;
            setType(CertificationEntity.Type.Identity);
            setIdentity(identity);
            setFirstname(identity.getFirstname());
            setLastname(identity.getLastname());
            setTargetId(identity.getId());
            setTargetName(identity.getName());
            setTargetDisplayName(identity.getDisplayableName());
            setCompositeScore(identity.getScore());
        }
        else if (entity instanceof ManagedAttribute) {
            ManagedAttribute accountGroup = (ManagedAttribute) entity;
            // continue using the old type name
            setType(CertificationEntity.Type.AccountGroup);
            setAccountGroup(accountGroup);
            setTargetId(accountGroup.getId());
            setTargetName(accountGroup.getName());
        }
        else if (entity instanceof Bundle) {
            Bundle businessRole = (Bundle) entity;
            setType(CertificationEntity.Type.BusinessRole);
            setTargetId(businessRole.getId());
            setTargetName(businessRole.getName());
            setTargetDisplayName(businessRole.getDisplayableName());
        } else if (entity instanceof DataOwnerCertifiableEntity) {
            DataOwnerCertifiableEntity doEntity = (DataOwnerCertifiableEntity) entity;
            setApplication(doEntity.getDataItem().getApplicationName());
            setSchemaObjectType(doEntity.getDataItem().getSchemaObjectType());
            // this is a hack. Because DataOwner is a second class citizen :-(
            // we have to use existing fields
            setTargetId(doEntity.getDataItem().getId());
            setReferenceAttribute(doEntity.getDataItem().getType());
            setTargetName(doEntity.getDataItem().getName());
            setNativeIdentity(doEntity.getDataItem().getValue());
            setTargetDisplayName(doEntity.getDataItem().getDisplayableValue());
            // The identity can get pretty long, depending on the entitlement description.
            // Trim it down to fit in the field when necessary
            String identity = doEntity.getFullName();
            if (identity.length() > 450) {
                identity = identity.substring(0, 450);
            }
            setIdentity(identity);
            setType(CertificationEntity.Type.DataOwner);
        }
        else {
            throw new RuntimeException("Unhandled entity type: " + entity);
        }
    }

    /**
     * Entity cloner used by the new certification generator when we have
     * to split previously built entities based on item ownership.
     * Only worried about identity entities right now since that's all
     * the new generator handles.
     */
    public CertificationEntity(CertificationEntity src) {

        // generic
        setType(src.getType());
        setTargetId(src.getTargetId());
        setTargetName(src.getTargetName());
        setTargetDisplayName(src.getTargetDisplayName());

        // identity
        setIdentity(src.getIdentity());
        setFirstname(src.getFirstname());
        setLastname(src.getLastname());
        setCompositeScore(src.getCompositeScore());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////


    @XMLProperty
    public void setType(Type _type) {
        this._type = _type;
    }

    public Type getType() {
        return _type;
    }


    public void setCertification(Certification cert) {
        _certification = cert;
    }

    /**
     * Reference to the parent Certification.
     */
    public Certification getCertification() {
        return _certification;
    }

    /**
     * @exclude
     * This method exists only for Hibernate.
     *
     * @deprecated use {@link #add(CertificationItem)} 
     */
    @Deprecated
    @BidirectionalCollection(elementClass=CertificationItem.class, elementProperty="parent")
    public void setItems(List<CertificationItem> items) {
        _items = items;
    }

    /**
     * The items requiring certification for this entity.
     */
    @Override
    public List<CertificationItem> getItems() {
        // jsl - formerly wrapped this in
        // Collections.unmodifiableList(_items); but
        // that causes Hibernate to think the original
        // collection has been dereferenced and commits
        // further changes!!
        return _items;
    }

    public boolean hasRoleItem(String roleName){
        if (roleName == null || _items == null)
            return false;

        for (CertificationItem item : _items){
            if (roleName.equals(item.getBundle()))
                return true;
        }

        return false;
    }

    /**
     * Whether this entity contains items that are auto-approved.
     *
     * @return True if items exist that are auto-approved.
     */
    public boolean hasAutoApprovals() {
        for (CertificationItem item : _items) {
            if (item.isActedUpon() && item.getAction().isAutoDecision()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remove the given CertificationItem from this entity.
     *
     * @param  item  The item to remove from this entity.
     */
    public void removeItem(CertificationItem item) {
        if (null != _items) {
            _items.remove(item);
        }
        item.setParent(null);
    }

    /**
     * @exclude
     * This must used by the XML parser so that proper
     * parentage set is received.
     * @deprecated use {@link #add(CertificationItem)} 
     */
    @Deprecated
    @XMLProperty(mode = SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setXmlItems(List<CertificationItem> items) {
        _items = null;
        if (items != null) {
            for (CertificationItem item : items)
                add(item);
        }
    }

    /**
     * @exclude
     * This must used by the XML parser so that proper
     * parentage set is received.
     * @deprecated use {@link #getItems()} 
     */
    @Deprecated
    public List<CertificationItem> getXmlItems() {
        return _items;
    }

    @XMLProperty
    public void setBulkCertified(boolean bulkCertified) {
        _bulkCertified = bulkCertified;
    }

    /**
     * True if this entity was bulk certified.
     */
    public boolean isBulkCertified() {
        return _bulkCertified;
    }


   /* public String getName() {
        if (Type.Identity.equals(this.getType()))
            return getIdentity();
        else if (Type.AccountGroup.equals(this.getType()))
            return getAccountGroup();
        else
            return null;
    }*/

    /**
     * Gets the full name of the entity regardless of the entity type.
     *
     * @return Full name of the given entity.
     *
     * @ignore
     * todo can this be deprecated in favor of calculateDisplayName? DataOwner certs
     * complicate this since in this method we return the identity name, but when
     * creating a display name, we need the entitlement description. jfb
     */
    public String getFullname() {

        if (Type.Identity.equals(this.getType()))
            return Util.getFullname(getFirstname(), getLastname()) != null ? Util.getFullname(getFirstname(), getLastname()) : getIdentity();
        else if (Type.AccountGroup.equals(this.getType()))
            return getAccountGroup();
        else if (Type.BusinessRole.equals(this.getType()))
            return this.getTargetName();
        else if (Type.DataOwner.equals(this.getType())) {
            return this.getIdentity();
        }
        else
            return null;
    }

    /**
     * Calculates a display name for this entity based on type.
     */
    public String calculateDisplayName(SailPointContext ctx, Locale locale) throws GeneralException{
        if (Type.Identity.equals(getType())) {
            return Util.getFullname(getFirstname(), getLastname()) != null
                    ? Util.getFullname(getFirstname(), getLastname()) : getIdentity();
        } else if (Type.AccountGroup.equals(getType())) {
            return getSchemaObjectType() + ": " + getAccountGroup();
        } else if (Type.BusinessRole.equals(getType())) {
            return getTargetName();
        } else if (Type.DataOwner.equals(getType())) {
            DataOwnerCertifiableEntity doe = DataOwnerCertifiableEntity.createFromCertificationEntity(ctx, this);
            return doe.getDisplayName(ctx, locale);
        }
        else {
            return null;
        }
    }

    @XMLProperty
    public void setPendingCertification(String id) {
        _pendingCertification = id;
    }

    public String getPendingCertification() {
        return _pendingCertification;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Identity Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setIdentity(String s) {
        _identity = s;
    }

    public void setIdentity(AbstractCertifiableEntity entity) {
        if (entity != null)
            _identity = entity.getName();
        else
            _identity = null;
    }

    /**
     * The name of the associated identity.
     */
    @Override
    public String getIdentity() {
        return _identity;
    }

    @XMLProperty
    public void setFirstname(String firstname) {
        _firstname = firstname;
    }

    /**
     * The first name of the identity. Stored here to allow searching and
     * sorting without navigating through identity.
     */
    public String getFirstname() {
        return _firstname;
    }

    @XMLProperty
    public void setCompositeScore(int compositeScore) {
        _compositeScore = compositeScore;
    }

    /**
     * The composite risk score of the identity.
     */
    public int getCompositeScore() {
        return _compositeScore;
    }

    @XMLProperty
    public void setLastname(String lastname) {
        _lastname = lastname;
    }

    /**
     * The last name of the identity. Stored here to allow searching and
     * sorting without navigating through identity.
     */
    public String getLastname() {
        return _lastname;
    }

    @XMLProperty
    public void setSnapshotId(String s) {
        _snapshotId = s;
    }

    /**
     * The unique id of the IdentitySnapshot containing the
     * state of the identity at the time of this certification.
     * Note that IdentityIQ does not own this and the snapshot might no longer
     * exist if this object is being decompressed from an certification archive.
     */
    public String getSnapshotId() {
        return _snapshotId;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this._attributes = attributes;
    }

    /**
     * Changes to attribute and entitlements of this identity
     * since the last certification of this type. This might be
     * saved in the archive, though in theory it could
     * be reproduced as long as the IdentitySnapshots are still available.
     */
    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public IdentityDifference getDifferences() {
        return _differences;
    }

    public void setDifferences(IdentityDifference diffs) {
        _differences = diffs;
    }

    /**
     * True if this is the first time this user has been certified for the current
     * certification type.
     */
    public boolean isNewUser() {
        return _newUser;
    }

    @XMLProperty
    public void setNewUser(boolean newUser) {
        this._newUser = newUser;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // AccountGroup Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Gets the name of the account group is this is an
     * AccountGroup entity. Null if Type != AccountGroup.
     *
     * @return AccountGroup name
     */
    public String getAccountGroup() {
        return _accountGroup;
    }

    /**
     * Gets the Name of the account group being certified. Should not
     * be set if Type != AccountGroup.
     *
     * @param accountGroup Name of the account group being certified.
     */
    @XMLProperty
    public void setAccountGroup(String accountGroup) {
        this._accountGroup = accountGroup;
    }

    /**
     * Gets the account group being certified. Null if Type != AccountGroup.
     *
     * @param accountGroup AccountGroup being certified.
     */
    public void setAccountGroup(ManagedAttribute accountGroup) {
        this._accountGroup = accountGroup.getDisplayableName();
        setApplication(accountGroup.getApplication());
        setReferenceAttribute(accountGroup.getAttribute());
        setNativeIdentity(accountGroup.getValue());
        setSchemaObjectType(calculateSchemaObjectType(accountGroup));
    }

    /**
     * Given a managed attribute we need to figure out what to show in the 'Object Type'
     * column in cert list view.
     */
    private String calculateSchemaObjectType(ManagedAttribute managedAttribute) {
        return managedAttribute.getType();
    }

    /**
     * Returns the AccountGroup being certified. Performs
     * a lookup with the given resolver using the account group app name, ref attr and
     * native identity.
     *
     * @param context SailPointContext used to retrieve AccountGroup object.
     * @return account group
     * @throws GeneralException
     */
    public ManagedAttribute getAccountGroup(SailPointContext context) throws GeneralException {
        Application app = getApplication(context);

        if (app == null || context == null || getNativeIdentity() == null || (getTargetDisplayName() == null && getSchemaObjectType() == null)){
            return null;
        }

        return ManagedAttributer.get(
                context,
                app.getId(),
                false,
                getTargetDisplayName(),
                getNativeIdentity(),
                getSchemaObjectType());
    }

    /**
     * Returns the application name of the AccountGroup being certified.
     * Should be Null if Type != AccountGroup.
     *
     * @return Application name
     */
    public String getApplication() {
        return _application;
    }

    /**
     * Sets the application name of the AccountGroup being certified.
     * Should not be set if Type != AccountGroup.
     *
     * @param application Application name
     */
    @XMLProperty
    public void setApplication(String application) {
        this._application = application;
    }

    /**
     * Sets the application name using the given application. This
     * is a convenience method. Should not be set if Type != AccountGroup.
     *
     * @param application Application for this item
     */
    public void setApplication(Application application) {
        this._application = application.getName();
    }

    /**
     * Returns the application of the AccountGroup being certified.
     * Should not be set if Type != AccountGroup.
     *
     * @param resolver Resolver used to look up the application
     * @return Application or null if not found
     * @throws GeneralException
     */
    public Application getApplication(Resolver resolver) throws GeneralException {
        if (getApplication() == null || getApplication().length() == 0)
            return null;

        return resolver.getObjectByName(Application.class, getApplication());
    }

    public String getReferenceAttribute() {
        return _referenceAttribute;
    }

    @XMLProperty
    public void setReferenceAttribute(String referenceAttribute) {
        this._referenceAttribute = referenceAttribute;
    }

    public String getSchemaObjectType() {
        return _schemaObjectType;
    }

    @XMLProperty
    public void setSchemaObjectType(String val) {
        _schemaObjectType = val;
    }

    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    @XMLProperty
    public void setNativeIdentity(String nativeIdentity) {
        this._nativeIdentity = nativeIdentity;
    }

    /**
     * Returns a list of all applications associated with this entity's
     * certification items.
     *
     * @param action CertificationAction to match
     * @param isBulkCertified True if bulk certified
     * @param resolver Resolver instance used to lookup Applications
     * @return Non-null list of Application object.
     */
    public List<Application> getApplications(CertificationAction action, boolean isBulkCertified, Resolver resolver){
        if (_applications == null){
            _applications = new ArrayList<Application>();
            if (getItems() != null){
                HashSet<Application> apps = new HashSet<Application>();
                for(CertificationItem item : getItems()){
                    try {
                        apps.addAll(item.getApplications(action, isBulkCertified, resolver));
                    } catch (GeneralException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Using a list here so we can access the items using array notation in jsf
                _applications.addAll(apps);
            }
        }

        return _applications;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo Properties
    //
    //////////////////////////////////////////////////////////////////////

    public void add(CertificationItem item) {
        if (null != item) {
            if (null == _items)
                _items = new ArrayList<CertificationItem>();
            _items.add(item);
            item.setParent(this);
        }
    }

    /**
     * Merge the items from the given entity into this entity. This method is
     * careful about ordering of items because a specific ordering in
     * parts of the UI is assumed.
     *
     * @param  entity         From which to get the items to merge.
     * @param  authoritative  Whether the given entity is authoritative. If
     *                        true, any item in the given entity that is not
     *                        found on this entity will be removed.
     * @param  markDiffs      If true, items that are added will be marked as
     *                        having differences.
     */
    public List<CertificationItem> mergeEntity(CertificationEntity entity,
                                               boolean authoritative,
                                               boolean markDiffs) {
        return mergeEntity(entity, authoritative, markDiffs, false, false);
    }

    /**
     * Merge the items from the given entity into this entity. This method is
     * careful about ordering of items because a specific ordering in
     * parts of the UI is assumed.
     *
     * @param  entity         From which to get the items to merge.
     * @param  authoritative  Whether the given entity is authoritative.  If
     *                        true, any item in the given entity that is not
     *                        found on this entity will be removed.
     * @param  markDiffs      If true, items that are added will be marked as
     *                        having differences.
     * @param updateOnly      If true, existing items will be updated but new items
     *                        will not be added.
     * @param purgeMergedItems If true, items merged will be purged from the given
     *                         entity.
     */
    public List<CertificationItem> mergeEntity(CertificationEntity entity,
                                               boolean authoritative,
                                               boolean markDiffs,
                                               boolean updateOnly,
                                               boolean purgeMergedItems) {

        List<CertificationItem> removed = new ArrayList<CertificationItem>();

        if (null != entity.getItems()) {

            if (null == _items) {
                _items = new ArrayList<CertificationItem>();
            }

            boolean changed = false;

            // Sort this now so we don't have to sort every time we look for an
            // item in findComparable.  We'll sort again at the end.
            // be safe and filter out any nulls beforehand.
            Collections.sort(_items);

            List<CertificationItem> pruneItems = new ArrayList<CertificationItem>();
            for (CertificationItem item : entity.getItems()) {
                // Only add the item if it is not already in this entity.
                CertificationItem found = Util.findComparable(_items, item, true);
                if (null == found && !updateOnly) {

                    // bug 20412, when new item is added to a completed delegation workitem, 
                    // we want to reset the delegation to generate new workitem
                    if ((this.getDelegation() != null) && (entity.getDelegation()!=null)) {
                        if (this.getDelegation().getWorkItem() != null) {
                            WorkItem.State wstate = this.getDelegation().getCompletionState();
                            if(wstate != null) {
                                if (wstate.toString().equals("Finished")) {
                                    this.setDelegation(entity.getDelegation());
                                }
                            }
                        }
                    }

                    this.add(item);
                    pruneItems.add(item);
                    // Mark any newly added items as having changes.
                    if (markDiffs) {
                        item.setHasDifferences(true);
                    }
                    changed = true;
                }
                else if (null != found) {
                    // We found an item, so merge any ancillary information.
                    found.merge(item);
                    pruneItems.add(item);
                }
            }

            // If the given entity is considered authoritative, prune out any
            // items on this entity that aren't found in the given entity.
            if ((authoritative) && (null != entity.getItems())) {

                // Create a copy of the entity's items and sort them so we don't
                // have to sort every time in containsComparable.
                List<CertificationItem> entityItems = entity.getItems();
                entityItems = new ArrayList<CertificationItem>(entityItems);
                Collections.sort(entityItems);

                for (Iterator<CertificationItem> it=_items.iterator(); it.hasNext(); ) {
                    CertificationItem item = it.next();
                    if (!Util.containsComparable(entityItems, item, true)) {
                        it.remove();
                        removed.add(item);
                        changed = true;
                    }
                }
            }

            // If we added or removed any items, tweak the entity.
            if (changed) {
                // Roll up the "has differences" onto the identity since this
                // changed.
                if (markDiffs) {
                    this.rollUpHasDifferences();
                }

                // Mark this entity for refresh since the contents changed.
                this.markForRefresh();
            }

            // if we want to purge the merge items (so they're not re-merged for a subsequent caller)
            if (purgeMergedItems) {
                for (CertificationItem pruned : pruneItems) {
                    entity.removeItem(pruned);
                }
            }

            Collections.sort(_items);
        }

        return removed;
    }

    public CertificationItem getItem(String itemId) {
        if ((null != itemId) && (null != _items)) {
            for (CertificationItem item : _items) {
                if (itemId.equals(item.getId())) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * Get any items that are on the same account in this entity as the given
     * CertificationItem. This requires that the given item is an Exception.
     */
    public List<CertificationItem> getItemsOnSameAccount(CertificationItem item) {

        boolean isException = isException(item);
        assert (isException) :
                "getItemsOnSameAccount() can only be called on exception item.";

        List<CertificationItem> items = new ArrayList<CertificationItem>();
        if (null != _items) {
            for (CertificationItem current : _items) {
                if ( (current != null) && (current != item) && (onSameAccount(item, current)) ) {
                    items.add(current);
                }
            }
        }

        return items;
    }

    public static Filter getItemsOnSameAccountFilter(CertificationItem item) {
        boolean isException = isException(item);
        assert (isException) :
                "getItemsOnSameAccountFilter() can only be called on exception item.  Attempted to call it on a " + item.getType().toString() + " item.";

        String app = item.getExceptionEntitlements().getApplication();
        String instance = item.getExceptionEntitlements().getInstance();
        String nativeId = item.getExceptionEntitlements().getNativeIdentity();

        Filter sameAccountFilter = Filter.and(
            Filter.eq("exceptionApplication", app), 
            Filter.eq("exceptionEntitlements.instance", instance), 
            Filter.eq("exceptionEntitlements.nativeIdentity", nativeId), 
            Filter.or(
                Filter.eq("type", CertificationItem.Type.Exception.name()),
                Filter.eq("type", CertificationItem.Type.AccountGroupMembership.name())));

        return sameAccountFilter;
    }

    private static boolean isException(CertificationItem item) {
        CertificationItem.Type type = item.getType();
        return ( CertificationItem.Type.Exception == type) ||
               ( CertificationItem.Type.AccountGroupMembership == type);
    }

    private boolean onSameAccount(CertificationItem orig, CertificationItem other) {
        String app = orig.getExceptionEntitlements().getApplication();
        String instance = orig.getExceptionEntitlements().getInstance();
        String nativeId = orig.getExceptionEntitlements().getNativeIdentity();

        if ( ( ( CertificationItem.Type.Exception.equals(other.getType()) ) ||
                ( CertificationItem.Type.AccountGroupMembership.equals(other.getType()) ) ) &&
                ( app.equals(other.getExceptionEntitlements().getApplication()) ) &&
                ( Util.nullSafeEq(instance, other.getExceptionEntitlements().getInstance(), true) ) &&
                ( nativeId.equals(other.getExceptionEntitlements().getNativeIdentity())) ) {
            return true;
        }
        return false;
    }


    
    /**
     * Clear the ID off of this item and all sub-items.
     */
    @Override
    public void clearIds() {
        super.clearIds();

        if (null != _items) {
            for (CertificationItem item : _items) {
                item.clearIds();
            }
        }
    }

    /**
     * Return whether (if the given item is an account revoke) a challenge has
     * already been generated for any other items on this entity that are
     * revoking the same account.
     */
    public boolean isAccountRevokeChallengeGenerated(CertificationItem item) {

        boolean generated = false;

        if ((null != item.getAction()) && item.getAction().isRevokeAccount()) {
            List<CertificationItem> others = this.getItemsOnSameAccount(item);
            for (CertificationItem other : others) {
                if (other.isChallengeGenerated()) {
                    generated = true;
                    break;
                }
            }
        }

        return generated;
    }

    /**
     * Return whether (if the given item is an account revoke) a challenge is
     * still active for any other items on this entity that are
     * revoking the same account.
     */
    public boolean isAccountRevokeChallengeActive(CertificationItem item){

        if ((null != item.getAction()) && item.getAction().isRevokeAccount()) {
            List<CertificationItem> others = this.getItemsOnSameAccount(item);
            for (CertificationItem other : others) {
                if (other.isChallengeGenerated() && other.getChallenge().isActive()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Return whether (if the given item is an account revoke) a challenge is
     * still active for any other items on the entity belonging to the specified revocation
     * item that are revoking the same account.  This version of the method uses the
     * specified resolver to query the database for the answer rather than iterating over 
     * a potentially large item list
     * @param item CertificationItem that is being queried
     * @param resolver Resolver used to make the query (usually a SailPointContext)
     * @return true if there is an active challenge on any of this entity's items
     * @throws GeneralException
     */
    public static boolean isAccountRevokeChallengeActive(CertificationItem item, Resolver resolver) throws GeneralException {
        if (null != item.getAction() && item.getAction().isRevokeAccount()) {
            Filter sameAccountItemsFilter = getItemsOnSameAccountFilter(item);
            Filter isChallengeActiveFilter = Filter.or(
                    Filter.notnull("challenge.workItem"),
                    Filter.and(
                            Filter.eq("challenge.challenged", true),
                            Filter.notnull("challenge.decision"),
                            Filter.eq("challenge.challengeDecisionExpired", false))); 
    
            Filter revokeChallengeActiveFilter = Filter.and(
                sameAccountItemsFilter,
                Filter.notnull("challenge"),
                isChallengeActiveFilter);
            
            return resolver.countObjects(CertificationItem.class, new QueryOptions(revokeChallengeActiveFilter)) > 1;
        } else {
            return false;
        }
    }

    /**
     * Return whether this whole entity (for example - not the sub-items but the
     * identity itself) is delegated.
     *
     * @return True if this whole entity is delegated, false otherwise.
     */
    public boolean isEntityDelegated() {
        return Status.Delegated.equals(getEntityDelegationStatus());
    }

    /**
     * Return the status of delegation on this whole entity (for example - not on the
     * sub-items). If there is no delegation status, null is returned.
     *
     * @return The status of delegation on this whole entity (for example - not on the
     *         sub-items), or null if there is no delegation status.
     */
    public Status getEntityDelegationStatus() {

        Status status = null;

        if (_delegation != null) {
            if (_delegation.isActive())
                status = Status.Delegated;
            else if (_delegation.isReturned())
                status = Status.Returned;
        }

        return status;
    }

    /**
     * Derive a status summary for the entity. This does not refresh the status
     * of the child items - it is assumed that these are already refreshed.
     *
     * Some ambiguity on what "reviewed" means for the entity.
     * If the delegation says reviewRequired, then it seems redundant
     * to require that the entity have an CertificationAction with the reviewed
     * flag set, it can be imply that this is true if all of the child items have been
     * reviewed.
     * @see CertificationService#refreshSummaryStatus(CertificationEntity, Boolean, Boolean)
     */
    public void refreshSummaryStatus(Boolean completeOverride) {

        Status status = Status.Complete;

        boolean isComplete = true;

        if (null != _items) {
            // If any sub-item is incomplete, this entity is incomplete.
            // Calculate status at the same time
            for (CertificationItem item : _items) {
                isComplete &= item.isComplete();
                Status currentStatus = item.getSummaryStatus();

                // Roll up the status of all items - display the most important.
                if ((null != currentStatus) && (currentStatus.compareTo(status) > 0))
                    status = currentStatus;
            }
        }

        applySummaryStatus(completeOverride, status, isComplete);
    }
    
    public void applySummaryStatus(Boolean completeOverride, Status itemsStatus, boolean isComplete) {
        // If the item was deemed complete, allow the completeOverride value to
        // override what the sub-items say.
        if (isComplete && (null != completeOverride) &&
                !completeOverride.booleanValue()) {
            isComplete = false;
        }

        // If we've changed completion states, save the completion date.
        if (isComplete != isComplete()) {
            Date completed = (isComplete) ? new Date() : null;
            setCompleted(completed);

            // If the items are complete but the override says we're not, then
            // force the status to Open.
            if (Status.Complete.equals(itemsStatus) && !isComplete) {
                itemsStatus = Status.Open;
            }
        }

        // Finally, set the summary status on this entity.
        _summaryStatus = itemsStatus;
    }

    /**
     * Roll up the ContinuousState, the nextStateChange date, and the
     * overdueDate from the child items.
     */
    public void refreshContinuousState(Certification cert) {

        if (log.isDebugEnabled()) {
            log.debug("Continuous certification support has been removed");
        }
    }

    /**
     * Perform a null-safe check as to whether d1 is before d2.
     */
    private boolean before(Date d1, Date d2) {

        // d1 is definitely not before d2 if it is null.
        if (null == d1) {
            return false;
        }

        // d1 is before d2 if d2 is null.
        if (null == d2) {
            return true;
        }

        return d1.before(d2);
    }

    /**
     * Refresh whether any action is required for this entity based on whether
     * action is required for any sub-items. Note that this does not refresh
     * the action required of the sub-items but assumes that they have already
     * been refreshed.
     */
    public void refreshActionRequired() {

        // Reset this to false first.
        _actionRequired = false;

        // If any items have action required, set this to true.
        if (null != _items) {
            for (CertificationItem item : _items) {
                if (item.isActionRequired()) {
                    _actionRequired = true;
                    break;
                }
            }
        }
    }

    /**
     * Roll up the "has differences" status from all sub-items onto this entity.
     */
    void rollUpHasDifferences() {

        boolean hasDiffs = false;

        if (null != _items) {
            for (CertificationItem item : _items) {
                hasDiffs |= item.getHasDifferences();
            }
        }

        // Note that - unlike refreshHasDifferences - we're not looking at the
        // IdentityDifference object to find modified/removed entitlements.
        // This is because we're incrementally changing the hasDifferences
        // state of the sub-items which should get rolled up onto this entity,
        // but the modifies/removes will never change in the difference object.
        _hasDifferences = hasDiffs;
    }

    /**
     * Refresh the differences on this entity and all contained items. This
     * returns true if their are any "compliance relevant differences".
     *
     * @param  diff      The IdentityDifference.
     * @param  resolver  The Resolver to use.
     *
     * @return True if any sub-items have differences.
     */
    public boolean refreshHasDifferences(IdentityDifference diff, Resolver resolver)
            throws GeneralException {

        boolean hasDifferences = false;

        // Refresh the sub-items - any sub-item returning true will set
        // hasDifferences to true.
        if (null != _items) {
            for (CertificationItem item : _items) {
                hasDifferences |= item.refreshHasDifferences(diff, resolver);
            }
        }

        // Refreshing the sub-items only finds additions - need to look for
        // removals and modifications also if we haven't yet detected any
        // differences.
        if (!hasDifferences) {
            hasDifferences = hasComplianceDifferences(diff);
        }

        _hasDifferences = hasDifferences;

        return _hasDifferences;
    }

    /**
     * Check whether the given IdentityDifference contains any differences for
     * compliance relevant attributes. This logic could live in
     * IdentityDifference but there is not any compliance-specific logic in
     * there now (it is just a dumb data object), so this is just put here.
     *
     * @param  diff  The IdentityDifference to check for differences.
     *
     * @return True if the given IdentityDifference contains any differences for
     *         compliance relevant attributes, false otherwise.
     */
    private boolean hasComplianceDifferences(IdentityDifference diff) {
        if (null != diff) {
            // Any link or permission attribute should be compliance relevant,
            // so if these are non-empty return true.
            //
            // KG - This isn't true any more since some attributes are
            // entitlements and some are just used for data.  Probably need to
            // look at the schema.
            if ((null != diff.getLinkDifferences()) && !diff.getLinkDifferences().isEmpty())
                return true;
            if ((null != diff.getPermissionDifferences()) && !diff.getPermissionDifferences().isEmpty())
                return true;

            // If there is a difference in the bundles, return true.
            if (null != diff.getBundleDifferences())
                return true;

            // If there is a difference in role assignments, return true
            if (null != diff.getAssignedRoleDifferences())
                return true;

            // If there is a difference in the policy violations, return true.
            if (null != diff.getPolicyViolationDifferences())
                return true;
        }

        // No link, permission, or bundle differences - return false.
        return false;
    }

    public Identity getIdentity(Resolver r) throws GeneralException {
        Identity id = null;
        if (r != null && _identity != null)
            id = r.getObjectByName(Identity.class, _identity);
        return id;
    }

    public IdentitySnapshot getIdentitySnapshot(Resolver r) throws GeneralException {

        IdentitySnapshot snap = null;
        if (r != null && _snapshotId != null)
            snap = r.getObjectById(IdentitySnapshot.class, _snapshotId);
        return snap;
    }

    public RoleSnapshot getRoleSnapshot(){
        if (_attributes == null || !_attributes.containsKey(ATTR_SNAPSHOT))
            return null;

        return (RoleSnapshot)_attributes.get(ATTR_SNAPSHOT);
    }

    public void setRoleSnapshot(RoleSnapshot snapshot){

        if (snapshot == null){
            if (_attributes != null && _attributes.containsKey(ATTR_SNAPSHOT))
                _attributes.remove(ATTR_SNAPSHOT);
        } else {
            if (_attributes == null)
                _attributes = new Attributes<String, Object>();

            _attributes.put(ATTR_SNAPSHOT, snapshot);
        }
    }

    @Override
    public CertificationEntity getCertificationEntity() {
        return this;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions on CertificationEntities
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void markForRefresh() {
        try {
            markForRefresh(null);
        } catch (GeneralException ge) {
            // this should never happen since the context isn't used in this case.
            // but, log anyway just in case.
            log.debug("Error while marking certification entity for refresh", ge);
        }
    }

    // Version of markForRefresh that takes a SailPointContext so that the
    // certification can be saved after modification.
    public void markForRefresh(SailPointContext context) throws GeneralException {
        if (null != _certification) {
            _certification.addEntityToRefresh(this);

            if (null != context) {
                context.saveObject(_certification);
            }
        }
    }

    /**
     * Mark this item as needing a continuous flush.
     */
    @Override
    public void markForContinuousFlush() {
        if (null != _items) {
            for (CertificationItem item : _items) {
                item.markForContinuousFlush();
            }
        }
    }

    /**
     * Overridden to descend into children.
     */
    @Override
    void removeReturnedDelegations() {
        if (null != _items) {
            for (CertificationItem item : _items) {
                item.removeReturnedDelegations();
            }
        }
    }

    @Override
    public void removeAutoApprovals(SailPointContext context, Identity decider, String workItemId) throws GeneralException {
        if (null != _items) {
            for (CertificationItem item : _items) {
                if (item.isActedUpon() && item.getAction().isAutoDecision()) {
                    item.clearDecision(context, decider, workItemId);

                    if (item.isCleared()) {
                        context.removeObject(item.getAction());
                        item.setAction(null);
                    }
                }
            }
        }
    }

    /**
     * Overridden to descend into children.
     */
    @Override
    public boolean rollbackChanges(String workItemId) {

        // Let the super class do it's thing first.
        boolean rolledBack = super.rollbackChanges(workItemId);

        // Descend into sub-items and rollback.
        if (null != getItems()) {
            for (CertificationItem item : getItems()) {
                rolledBack |= item.rollbackChanges(workItemId);
            }
        }

        // Mark for refresh if we've rolled back.
        if (rolledBack) {
            markForRefresh();
        }

        return rolledBack;
    }

    /**
     * Bulk certify this certification identity with the status and parameters
     * from the given certification action. If the entity is delegated and the
     * request is not coming from a delegated work item (for example - workItem is null),
     * the delegation is revoked.
     *
     * @param who        The Identity performing the bulk certification.
     * @param workItem   The ID of the work item in which the bulk certify is
     *                   taking place.
     * @param bulkAction The bulk action that contains the status and
     *                   parameters to use to bulk certify this identity.
     * @param selector   The selector used to determine which items are to be
     *                   bulk certified. If null, all bulk certifiable items
     *                   are bulk certified.
     * @param force      Whether to force bulk certification on the children,
     *                   even if they do not usually allow it. THIS SHOULD
     *                   ONLY BE USED FOR TESTING!
     * @return A possibly empty list of CertificationItems that were not bulk
     *         certified.
     */
    public List<CertificationItem> bulkCertify(Identity who, String workItem,
                                               CertificationAction bulkAction,
                                               CertificationItemSelector selector,
                                               boolean force)
            throws GeneralException {

        List<CertificationItem> notApproved = new ArrayList<CertificationItem>();

        // Revoke delegation if delegated (and this is not being bulk certified
        // from a delegated entity.
        if ((null != _delegation) && (null == workItem))
            this.revokeDelegation();

        List<CertificationItem> items = this.getItems();
        if (null != items) {
            for (CertificationItem item : items) {
                notApproved.addAll(item.bulkCertify(who, workItem, bulkAction, selector, force));
            }
        }

        // Mark this identity as needing to be refreshed by the Certificationer.
        markForRefresh();

        return notApproved;
    }

    public Identity getDefaultRemediator(Resolver resolver) throws GeneralException {
        return null;
    }

    /**
     * Filter that will find a matching entity in the given certification.
     * Note that this matches by property values rather than ID.
     *
     * @param  cert  The Certification in which to search for a matching entity.
     *
     * @return A Filter that will find a matching entity in the given certification.
     */
    public Filter getEqualsFilter(Certification cert) {

        // IMPORTANT!!  Any fields used by these filters must also exist in
        // ArchivedCertificationEntity so that we can search for archived
        // entities on a certification in the same way that we search for
        // regular entities.

        Filter f = Filter.eq("certification", cert);

        switch (_type) {
            case Identity:
                f = Filter.and(f, Filter.eq("identity", _identity));
                break;
            case AccountGroup:
                // jsl - note that the property names here are the old
                // AccountGroup names, they have not been upgraded to match
                // the ManagedAttribute property names
                List<Filter> filters = new ArrayList<Filter>();
                filters.add(f);
                filters.add(Filter.eq("accountGroup", _accountGroup));
                filters.add(Filter.eq("application", _application));
                filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", _nativeIdentity)));
                filters.add(Filter.eq("referenceAttribute", _referenceAttribute));
                f = Filter.and(filters);
                break;
            case DataOwner:
                f = Filter.and(
                        f,
                        Filter.eq("referenceAttribute", _referenceAttribute),
                        Filter.eq("targetId", _targetId),
                        Filter.eq("targetName", _targetName),
                        Filter.eq("nativeIdentity", _nativeIdentity));
                break;
            case BusinessRole:
                f = Filter.and(f, Filter.eq("targetId", this.getTargetId()));
                break;
            default:
                throw new RuntimeException("Unknown type: " + _type);
        }

        return f;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Sorting
    //
    // Hack for the unit tests to get a reliable order when
    // comparing XML files.
    //
    //////////////////////////////////////////////////////////////////////

    private static Comparator<CertificationEntity> SortComparator;

    public static Comparator<CertificationEntity> getSortComparator() {
        if (SortComparator == null) {
            SortComparator =
                    new Comparator<CertificationEntity>() {
                        public int compare(CertificationEntity p1, CertificationEntity p2) {
                            Collator collator = Collator.getInstance();
                            collator.setStrength(Collator.PRIMARY);
                            return collator.compare(p1.getIdentity(), p2.getIdentity());
                        }
                    };
        }
        return SortComparator;
    }

    /**
     * @see sailpoint.object.Certification#sort(boolean, boolean)
     */
    void sort(boolean useComparable, boolean secondarySort) {
        if (_items != null) {
            if (useComparable) {
                Collections.sort(_items);
            }
            else {
                Collections.sort(_items, CertificationItem.getSortComparator(secondarySort));
            }

            for (CertificationItem item : _items) {
                item.sort();
            }
        }
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitCertificationEntity(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Object overrides
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object o) {

        boolean eq = false;

        if (!this.getClass().isInstance(o))
            return false;
        if (this == o)
            return true;

        CertificationEntity entity = (CertificationEntity) o;

        // If we have an ID, use it.
        if (getId() != null) {
            eq = getId().equals(entity.getId());
        }
        else {
            // No ID yet, use the identifying traits of this entity.  We could
            // go deeper, but this is probably enough.
            eq = new EqualsBuilder().append(_type, entity._type)
                    .append(_identity, entity._identity)
                    .append(_accountGroup, entity._accountGroup)
                    .append(_application, entity._application)
                    .append(_nativeIdentity, entity._nativeIdentity)
                    .append(_referenceAttribute, entity._referenceAttribute)
                    .append(_targetId, entity._targetId)
                    .isEquals();
        }

        return eq;
    }

    @Override
    public int hashCode() {

        if (null != _id) {
            return _id.hashCode();
        }

        return new HashCodeBuilder().append(_type)
                .append(_identity)
                .append(_accountGroup)
                .append(_application)
                .append(_nativeIdentity)
                .append(_referenceAttribute)
                .append(_targetId)
                .toHashCode();
    }
}
