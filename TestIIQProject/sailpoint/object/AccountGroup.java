/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * NOTE: As of 6.0 this class was merged with ManagedEntitlement.
 * Account groups are modeled as "group" flagged Managed attributes.
 * This class is provided only to facilitate the upgrade process
 * it must no longer be used by system or custom code.
 * <br>
 * 
 * An object representing a "group" defined in an application.
 * Groups in our context are used to assign permissions indirectly
 * to accounts.  Note that the term group is generic, some applications
 * might call the concept something else, like "role".  
 *
 * Account groups are discovered dynamically during identity aggregation.
 * In our model, they are very similar to Link objects, but are not
 * owned by another object.
 *
 * In theory a group can have arbitrary attributes but in practice
 * they are brought over primarily to hold a list of Permission 
 * objects representing the entitlements granted by the group.
 * On the application they logically contain a set of "members" 
 * but modelign that is not attempted here.
 *
 * The SailPointObject._name field is used to hold the "display name"
 * which is an alternative to the nativeIdentity for display.
 * This is not a unique key.  
 *
 * @see sailpoint.object.ManagedAttribute

 * @ignore 
 * It is nice to use the _name for display name
 * since various tools (console, debug page) 
 * show the name if one exists, but they do not know about 
 * subclass _displayNames.  This does, however, make this class 
 * inconsistent with Link which has a _displayName but no _name.
 * Might want to evolve Link.
 * 
 */
@XMLClass
@Deprecated
public class AccountGroup extends AbstractCertifiableEntity
        implements Cloneable {
   
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Cache of property names returned for getUniqueKeyProperties.
     */
    String[] _uniqueKeyProperties;

    /**
     * Application on which the group resides.
     */
    private Application _application;

    /**
     * Optional instance identifier for template applications
     * TODO: Are these needed for account groups? - jsl
     */
    private String _instance;

    /**
     * The attribute of an account on which group memberships are
     * stored.  This is necessary for applications that have more than
     * one group concept.  In retrospect this might be overkill and it
     * complicates the aggregator.
     */
    private String _referenceAttribute;

    /**
     * The "raw" group identity.  For directories this is the DN.
     */
    private String _nativeIdentity;

    /**
     * Selected attribute values from the account.
     */
    private Attributes<String, Object> _attributes;

    /**
     * Entitlements implied by membership.
     * Since these are the primary reason application groups are modeled,
     * IIQ promote these to a top-level field rather than burying it
     * in the attributes map. These permissions are typically returned
     * from connectors as directPermissions and should be over-ridden
     * during aggregation.
     */
    private List<Permission> _permissions;

    /**
     * Unstructured Permissions.
     * These are like _permissions, but are adorned to the group by
     * target aggregation. These should not be over-ridden by
     * group aggregation and are reset during target
     * aggregation runs.
     */
    private List<Permission> _targetPermissions;

    /**
     * The date the account attributes were last refreshed.
     * Set by the aggregator.
     */
    private Date _lastRefresh;

    /**
     * The date target aggregation was last set by the target aggregator.
     */
    Date _lastTargetAggregation;

    /**
     * A special flag set when a value for _referenceAttribute is found
     * but the connector was not able to locate a ResourceObject with
     * that name.  IIQ has the option of persisting these so it does not
     * keep going back to the application every time it sees this value,
     * but the value needs to be marked.
     */
    private boolean _uncorrelated;

    //
    // Correlation keys
    // These are pulled out of the _attributes during aggregation
    // and maintained as Hibernate columns for searching.  Note that
    // this class does not attempt to keep the keys in sync with
    // changes made in the _attributes map.  Only the aggregator should
    // be setting attribute values and keys.
    //

    String _key1;
    String _key2;
    String _key3;
    String _key4;

    /**
     * Optional name of a multi-valued extended Link attribute that is being used
     * to store the group members.
     */
    String _memberAttribute;

    /**
     * A list of account groups that this group inherits
     */
    List<AccountGroup> _inheritance;

    //////////////////////////////////////////////////////////////////////
    //
    // Constuctors
    //
    //////////////////////////////////////////////////////////////////////

    public AccountGroup() {
    }

    /**
     * @exclude
     * Let the PersistenceManager know the name is not unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    /**
     * @exclude
     * IIQ is one of the few that overload this to specify a set
     * of unique key properties to prevent duplication if you load
     * XML files full of AccountGroups that do not have ids.  Without
     * matching of existing objects IIQ would end up with duplicate
     * groups.
     */
    public String[] getUniqueKeyProperties() {
        if (_uniqueKeyProperties == null) {
            _uniqueKeyProperties = new String[3];
            _uniqueKeyProperties[0] = "application";
            _uniqueKeyProperties[1] = "referenceAttribute";
            _uniqueKeyProperties[2] = "nativeIdentity";
        }
        return _uniqueKeyProperties;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Application where the group resides.
     */
    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname = "ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application res) {
        _application = res;
    }

    /**
     * Optional instance identifier for template applications
     */
    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String ins) {
        _instance = ins;
    }

    /**
     * The raw group identity.  For directories this is the DN.
     */
    @XMLProperty(xmlname = "identity")
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    public void setNativeIdentity(String id) {
        _nativeIdentity = id;
    }

    /**
     * The attribute of an account where group memberships are stored.
     */
    @XMLProperty
    public String getReferenceAttribute() {
        return _referenceAttribute;
    }

    public void setReferenceAttribute(String s) {
        _referenceAttribute = s;
    }

    /**
     * The date the account attributes was last refreshed.
     * Set by the aggregator.
     */
    @XMLProperty
    public Date getLastRefresh() {
        return _lastRefresh;
    }

    public void setLastRefresh(Date d) {
        _lastRefresh = d;
    }

    /**
     * A special flag set when a value for <code>referenceAttribute</code> was found
     * but the connector was not able to locate a ResourceObject with
     * that name.  IdentityIQ has the option of persisting these so it does not
     * keep going back to the application every time it see this value,
     * but the value needs to be marked.
     */
    @XMLProperty
    public boolean isUncorrelated() {
        return _uncorrelated;
    }

    public void setUncorrelated(boolean b) {
        _uncorrelated = b;
    }

    /**
     * Selected attribute values from the account.
     */
    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    /**
     * Permissions implied by membership.
     * These permissions are typically returned
     * from connectors as directPermissions and should be over-ridden
     * during aggregation.
     */
    @XMLProperty(mode = SerializationMode.LIST)
     public List<Permission> getPermissions() {
        return _permissions;
    }

    public void setPermissions(List<Permission> perms) {
        _permissions = perms;
    }

    /**
     * Unstructured Permissions.
     * These are like simple permissions, but are adorned to the group by
     * target aggregation. These should not be over-ridden by
     * group aggregation and are reset during our target
     * aggregation runs.
     */
    @XMLProperty(mode = SerializationMode.LIST)
    public List<Permission> getTargetPermissions() {
        return _targetPermissions;
    }

    public void setTargetPermissions(List<Permission> perms) {
        _targetPermissions = perms;
    }

    /**
     * Returns a merged list of all permissions assigned to this group.
     */ 
    public List<Permission> getAllPermissions() {
        List<Permission> merged = new ArrayList<Permission>();
        List<Permission> list = getPermissions();
        if ( list != null ) merged.addAll(list);

        List<Permission> targets = getTargetPermissions();
        if ( targets != null ) merged.addAll(targets);
        return (merged.size()>0) ? merged : null;
    }

    /**
     * The date the target aggregation was last set by the target aggregator.
     */
    @XMLProperty
    public Date getLastTargetAggregation() {
        return _lastTargetAggregation;
    }

    public void setLastTargetAggregation(Date d) {
        _lastTargetAggregation = d;
    }

    /** 
     * The first of four configurable correlation keys.
     * These are pulled out of the attributes map during aggregation
     * and maintained as Hibernate columns for searching.  Note that
     * this class does not attempt to keep the keys in sync with
     * changes made in the attributes map.  Only the aggregator should
     * be setting attribute values and keys.
     */
    @XMLProperty
    public String getKey1() {
        return _key1;
    }

    public void setKey1(String s) {
        _key1 = s;
    }

    @XMLProperty
    public String getKey2() {
        return _key2;
    }

    public void setKey2(String s) {
        _key2 = s;
    }

    @XMLProperty
    public String getKey3() {
        return _key3;
    }

    public void setKey3(String s) {
        _key3 = s;
    }

    @XMLProperty
    public String getKey4() {
        return _key4;
    }

    public void setKey4(String s) {
        _key4 = s;
    }

    /**
     * Optional name of a multi-valued extended Link attribute that is being used
     * to store the group members.
     */
    public String getMemberAttribute() {
        return _memberAttribute;
    }

    public void setMemberAttribute(String attr) {
        _memberAttribute = attr;
    }

    /**
     * A list of account groups that this group inherits from.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<AccountGroup> getInheritance() {
        return _inheritance;
    }

    public void setInheritance(List<AccountGroup> groups) {
        _inheritance = groups;
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitAccountGroup(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    public void setAttribute(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String, Object>();
        _attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void add(Permission p) {
        if (_permissions == null)
            _permissions = new ArrayList<Permission>();
        _permissions.add(p);
    }

    /**
     * Return the name to display in the UI
     */
    public String getDisplayableName() {
        return (_name != null) ? _name : _nativeIdentity;
    }

    /**
     * Gets the entitlement groups for the given list of applications. If the list is
     * empty or null return all entitlement groups.
     *
     * Since an AccountGroup is always assigned to only one application, the returned list
     * contains at most one entitlement group. Since IdentityIQ shares a
     * common interface with Identity, it has to implement the method in
     * this way.
     *
     * @param apps List of applications to retrieve groups for
     * @return List containing 0 or 1 entitlement group. Always non-null.
     *
     * @ignore
     * TODO: Also note that IdentityIQ is not handling template apps with instances
     * in this interface.  If IIQ needed that it would have to pass in another
     * parallel list of instance names. - jsl
     *
     */
    public List<EntitlementGroup> getExceptions(List<Application> apps) {
        
        Application app = null;
        
        if (apps != null && !apps.isEmpty()){
            // Find the app that our account group belongs to 
            // within the provided list of apps
            for (int i = 0; apps.size() > i; i++) {
                if (getApplication().getId().equals(apps.get(i).getId())){
                    app = apps.get(i);
                }
            }
            
            if (null == app) {
                // If, within the provided list of apps, we didn't find
                // the app that the account group is on, return empty list.
                return new ArrayList<EntitlementGroup>();
            }
        } else {
            app = getApplication();
        }
        
        // get the attributes that are marked entitlement on the group schema for the account group.
        Attributes<String, Object> entAttrs = getEntitlementAttributes(app);
                
        return Arrays.asList(
                new EntitlementGroup(app, null, getNativeIdentity(),
                        getDisplayableName(), getAllPermissions(), entAttrs));
    }
    
    /**
     * Finds the attributes that are marked as entitlement in the schema for the provided application.
     * This might be useful outside of AccountGroup, but for now, it will remain private since
     * it is easier to go public later.
     * 
     * @param app The application on which group schema is examined
     * @return Map of attributes on this account group that are marked as
     * entitlement on the application's group schema
     */
    private Attributes<String, Object> getEntitlementAttributes(Application app) {
        Attributes<String, Object> entAttrs = new Attributes<String, Object>();
        
        Schema appSchema = app.getSchema(Application.SCHEMA_GROUP);
        
        if(null != getAttributes()) {
            for (Map.Entry<String, Object> entry : getAttributes().entrySet()) {
                if (null != entry.getValue() &&
                    appSchema.getAttributeDefinition(entry.getKey()) != null &&
                    appSchema.getAttributeDefinition(entry.getKey()).isEntitlement()) {
                    
                    entAttrs.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return entAttrs;
    }

    public String getFullName() {
        return getDisplayableName();
    }

    /**
    * Indicates that you can difference this entity. In some cases,
    * such as AcountGroup objects, you cannot because unlike Identities IIQ does 
    * store a historical snapshots.
    *
    * This flag allows the certificationer to skip the differencing
    * step.
    *
    * @see sailpoint.api.Certificationer#addEntity
    * @see sailpoint.api.Certificationer#renderDifferences
    *
    * @return true if this entity can be differenced
    */
    public boolean isDifferencable(){
       return false;
    }

    /**
    * Returns a UI friendly short name for AccountGroup.
    *
    * Currently this is being used when the Certificationer needs
    * to return a warning like 'no accoutn group permissions to certify'.
    *
    * @param plural Should the type name be plural?
    * @return Entity type short name pluralized if plural flag is true
    */
    public String getTypeName(boolean plural) {
      return "Account Group Permission" + (plural ? "s" : "");
    }

    /**
     * Return a single Permission object that has all the rights
     * for a given target.  SailPoint has never formally
     * stated (and certainly never enforced) that there can be
     * only one Permission object per target.  In cases where
     * aggregate rights list need to be operated upon it is convenient
     * if to collapse multiple perms down to one.
     * There should no information loss provided that the annotations
     * are the same which they should be since they apply to the
     * target, not a collection of rights.
     *
     * There might be cases where the perms are broken up just for
     * scale, since one with too many rights is hard to display.
     * This is being used in the Provisioner now for simulated provisioning
     * so it should not persist.  Need to think about the consequences 
     * if other contexts use this!
     *
     * NOTE: This was copied over from Link, it is used by
     * IIQEvaluator when applying committed ObjectRequests
     * for groups.
     */
    public Permission getSinglePermission(String target) {

        Permission single = null;
        boolean accumulating = false;

        if (target != null) {
            List<Permission> perms = getPermissions();
            if (perms != null) {
                for (Permission perm : perms) {
                    if (target.equals(perm.getTarget())) {
                        if (single == null)
                            single = perm;
                        else {
                            // make a copy to avoid corrupting
                            // the original model
                            if (!accumulating) {
                                single = new Permission(perm);
                                accumulating = true;
                            }
                            single.addRights(perm.getRightsList());
                        }
                    }
                }
            }
        }

        return single;
    }

    /**
     * Put a permission on the list replacing all others with the same
     * target.  Typically this is the permission returned
     * by getSinglePermission with some modifications.
     */
    public void setSinglePermission(Permission single) {

        if (single != null && single.getTarget() != null) {

            // remove what's there now
            List<Permission> perms = getPermissions();
            if (perms != null) {
                ListIterator<Permission> it = perms.listIterator();
                while (it.hasNext()) {
                    Permission p = it.next();
                    if (single.getTarget().equals(p.getTarget()))
                        it.remove();
                }
            }

            // don't add empty permissions
            String rights = single.getRights();
            if (rights != null && rights.length() > 0) {
                if (perms == null)
                    perms = new ArrayList<Permission>();
                perms.add(single);
            }

            _permissions = perms;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("application.name", "Application");
        cols.put("referenceAttribute", "Attribute");
        cols.put("nativeIdentity", "Value");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %-20s %-20s %s\n";
    }

    public static final Comparator<AccountGroup> SP_ACCOUNTGROUP_BY_NAME=
        new Comparator<AccountGroup>() {
            public int compare(AccountGroup ag1, AccountGroup ag2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(ag1.getName(), ag2.getName());
            }
        };

    public static final Comparator<AccountGroup> SP_ACCOUNTGROUP_BY_NATIVE_IDENTITY=
        new Comparator<AccountGroup>() {
            public int compare(AccountGroup ag1, AccountGroup ag2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(ag1.getNativeIdentity(), ag2.getNativeIdentity());
            }
        };

    public static final Comparator<AccountGroup> SP_ACCOUNTGROUP_BY_OWNER=
        new Comparator<AccountGroup>() {
            public int compare(AccountGroup ag1, AccountGroup ag2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                Identity owner1Id = ag1.getOwner();
                Identity owner2Id = ag2.getOwner();

                String owner1 = ( owner1Id != null ) ? owner1Id.getName() : "";
                String owner2 = ( owner2Id != null ) ? owner1Id.getName() : "";
                return collator.compare(owner1, owner2);
            }
        };

    public static final Comparator<AccountGroup> SP_ACCOUNTGROUP_BY_MODIFIED=
        new Comparator<AccountGroup>() {
            public int compare(AccountGroup ag1, AccountGroup ag2) {
                Date date1 = ag1.getModified();
                if (date1 == null ) date1 = new Date();
                Date date2 = ag2.getModified();
                if (date2 == null ) date2 = new Date();
                return date1.compareTo(date2);
            }
        };

   public static class NoMemberAttributeException extends GeneralException {
       private static final long serialVersionUID = 1L;
       public NoMemberAttributeException() {
           super();
       }
       public NoMemberAttributeException(String msg) {
           super(msg);
       }
   }
}
