/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Common base class for all SailPoint objects with identity
 * in the persistent store.  
 *
 * Author: Jeff and a cast of thousands
 * 
 * We define a _name field since most objects need one, though for
 * some name less classes like ApplicationActivity it does not have
 * to be included in the Hibernate mapping file.  If a subclass
 * does not have a unique name, it should implement the
 * hasName() and isNameUnique() methods to return false as appropriate.
 *
 * Starting with 3.0, all SailPointObjects support extensible
 * attributes.  These are typically stored in an Attributes map,
 * but with values duplicated in a set of special SailPointObject
 * properties which will be stored in columns in the Hibernate table
 * for searching.  This class includes property methods for 20
 * extended attributes.  The subclass must manage the Attributes map
 * and select the desired number of attributes in it's Hibernate mapping file.
 * 
 * The copying of map attributes into the extended properties is currently
 * under the control of the subclass, and for Identity and Link
 * the Identitizer.  Need to make this more automatic, it would be
 * best to have HibernatePeristenceManager take care of it.
 * 
 */

package sailpoint.object;

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.tools.xml.XMLReferenceTarget;

/**
 * Common base class for all SailPoint objects with identity
 * in the persistent store.  
 */
@Indexes({@Index(property="assignedScopePath",subClasses=true)})
@XMLClass
public abstract class SailPointObject extends AbstractXmlObject
  implements XMLReferenceTarget, Serializable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Maximum number of extended attributes that can be mapped
     * onto columns in the Hibernate table. This is the absolute maximum, 
     * the configured maximum can be less and must be derived from the
     * ObjectConfig for this class.
     *
     * In 6.1 this is only the limit on the number of numeric extended
     * attribute columns (extended1, extended2, etc.)  You can have
     * any number of symbolic attribute columns provided you define
     * them properly in the .hbm.xml file and use ExtendedPropertyAccessor
     * as the "access" class.
     */
    public static final int MAX_EXTENDED_ATTRIBUTES = 20;
    
    /**
     * Maximum number of extended attributes which are of type identity.
     * Read comments for {@link #MAX_EXTENDED_ATTRIBUTES} for more details.
     */
    public static final int MAX_EXTENDED_IDENTITY_ATTRIBUTES = 5;

    /**
     * System attribute in the extended attributes map that
     * contains the localized descriptions. The value
     * will be a Map<String,String>.
     *
     * Add a prefix to avoid conflicts with user defined 
     * extended attributes.  
     */
    public static final String ATT_DESCRIPTIONS = "sysDescriptions";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	static private Log log = LogFactory.getLog(SailPointObject.class);

    /**
     * A transient unique identifier that can be assigned
     * to an object when it is being edited in the UI. This is used
     * with new objects that have not yet been saved and might not
     * yet have a unique name.
     * Currently this is only used for some types of Policy editing,
     * should try to remove this.
     */
    String _uid;

    /**
     * The database id. This will be automatically set by the persistent
     * store upon saving.
     */
    String _id;

    /**
     * Objects normally have unique user-defined names, though some like
     * AuditRecord do not have names at all, and others like SODConstraint
     * have names that might not be unique. The subclasses will overload the
     * hasName and isUniqueName methods to indicate whether the name
     * is a unique search key.
     */
    String _name;
    
    /**
     * Field containing lock information. If this is non-null
     * it means a lock was acquired, though it might be expired.
     * The LockInfo class provides methods to parse and generate this
     * string.
     * 
     * @ignore
     * This could be an XML type but 
     * this is so fundamental, and it's nice to 
     * have it searchable.
     */
    String _lock;

    /**
     * Transient field set when the object is considered dirty and needs
     * to be flushed to the database. This is used only if the 
     * SailPointContext is set to use the 6.1 explicit save option.
     */
    boolean _dirty;

    /**
     * Owner of this object
     */
    private Identity _owner;

    /**
     * Verbose comments.
     */
    String _description;

    /**
     * Date the object was created in the persistent store.
     */
    Date _created;

    /**
     * Date the object was last modified in the persistent store.
     * 
     * @ignore
     * Since there are not really multiple administrators,
     * the last modifier does not need to be remembered.
     */
    Date _modified;

    /**
     * True if the object has been electronically signed
     * If true, this object should not be modified
    */
    boolean _immutable;
     

    /**
     * The scope in which this object lives. This is not required, but if
     * set it can restrict access to this object to the owner or people that
     * have control over this scope.
     */
    Scope _assignedScope;
    
    /**
     * The path of the assignedScope. This is denormalized from the assigned
     * scope onto each object so that scoping queries do not always require
     * joining to the scope table.
     */
    String _assignedScopePath;

    /**
     * Set when there is an workflow pending for a modification
     * to this object. There can only be one workflow pending at a time.
     *
     * This is now a first-class reference rather than an id
     * so some interesting joins can be done in Hibernate.  This does
     * mean that the reference must be pruned before the work item can
     * be deleted, but this can now be done cleanly by the WorkItemHandler.
     *
     * @ignore
     * NOTE: This field must be explicitly mapped by the subclass, not
     * all SailPointObjects will support approvals.  We put it here so that
     * pending approvals can be dealt with in a generic way in the modeler,
     * RoleLifecycler, and elsewhere.
     *
     * The name is a little odd because some subclasses want to 
     * have a WorkflowCase reference for other reasons.
     */
    WorkflowCase _pendingWorkflow;

    /**
     * True if this object is considered to be disabled. This is not
     * relevant for all objects, the subclass must map this explicitly
     * if it supports being disabled.  
     */
    boolean _disabled;

    /*
     * MetaData we keep for the extended attributes that have been
     * updated manually from the UI.  It applies to objects that
     * are editable and have been modified from the UI.
     * 
     * This can only apply to objects that have extended 
     * attributes and was initially targeted at the Link
     * and Identity attributes.
     *
     */
    List<AttributeMetaData> _attributeMetaData;

    /**
     * Transient attribute set by HibernatePersistenceManager to indicate
     * that the object returned by a lockObject call already had a suitable lock.
     * ObjectUtil.unlockIfNecessary needs to know this to know whether to 
     * release the lock or let it be.
     */
    boolean _refreshedExistingLock;

    /**
     * List of ObjectClassifications for the SailPointObject
     */
    public List<ObjectClassification> _classifications;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public SailPointObject() {
    }

    /**
     * Return true if the object has a name.
     * This is used by the AbstractSailPointObject and the 
     * PersistenceManagers to tell if the name can be used in 
     * a search filter.
     * <p>
     * It is almost always true, but a few classes like log records
     * do not have meaningful names.
     * Consider using {@link sailpoint.api.ObjectUtil#hasName(SailPointContext, Class)} instead
     */
    public boolean hasName() {
        return true;
    }
    
    /**
     * Return true if the object name can be used as a unique database key.
     * It is almost always true, but a few classes like SODConstraint
     * want to allow names for lookup, but cannot guarantee that the names
     * are unique.
     *
     * This is relevant only if hasName is true.
     */
    public boolean isNameUnique() {
        return hasName();
    }
    
    /**
     * Return a list of property names other than "id" that combined 
     * make up a unique identifier for this object. If the value is
     * null, and isNameUnique is true it is assumed that "name" is
     * the unique key.
     *
     * This is overloaded in a few subclasses where name alone is
     * not a unique key. One example is ManagedAttribute which are
     * identified by the combination of _application, _attribute,
     * and _value properties.  
     *
     * This is only used when importing files of XML that do not
     * contain unique database ids. To prevent duplicates,
     * first objects must be matched by name or unique key combination
     * before new objects are created.
     */
    public String[] getUniqueKeyProperties() {
        return null;
    }

    /**
     * Return the class name to be used when auditing events on instances
     * of this class. By default this is the Java class name, but in some
     * cases alternate names are substituted since they are directly visible 
     * in the UI.
     */
    public String getAuditClassName() {
        return getClass().getSimpleName();
    }

    /**
     * @exclude
     * Return true if this object is always serialized as XML within
     * the parent object. This is necessary to defeat 
     * HibernatePersistenceManager's behavior of trying to persist
     * all instances of SailPointObjects referenced from a parent before
     * saving the parent. Usually this is what you want, but in cases where
     * an object can exist standalone or serialized it needs to be turned off.
     * This actually might not be enough, since it is the container of the
     * object that should be deciding not the object itself. Its just
     * a temporary solution for ScoreDefinition until it is decided whether
     * to make them real SailPointObjects or not.
     */
    public boolean isXml() {
        return false;
    }

    /**
     * A few SailPointObjects do not have assigned scopes. They should
     * override this to return false if they do not.
     */
    public boolean hasAssignedScope() {
        return true;
    }

    /**
     * The database id. This will be automatically set by the persistent
     * store upon saving.
     */
    @XMLProperty
    public String getId() {
        return _id;
    }

    public void setId(String id) {
        if (id != null && id.length() == 0) {
            // jsl - noticed this when we added transient workflows, 
            // backing beans that have a hidden field workItem.id will
            // post an empty string that then gets copied back to the id.
            // This then screws up various transience checking since we usually
            // assume that id==null means it hasn't been saved.  non-null
            // empty ids are never valid so catch this.  Should actually
            // use trim() here too...
            id = null;
        }
        _id = id;
    }

    /**
     * Return true if the object has been persisted in the database.
     * This is normally determined by having a null id but occasionally
     * when objects are being created in the UI the JSF forms can post
     * an empty string since the id is often a hidden field.
     */
    public boolean isPersisted() {
        return (_id == null || _id.trim().length() > 0);
    }

    /**
     * Objects normally have unique user-defined names, though some like
     * AuditRecord do not have names at all, and others like SODConstraint
     * have names that might not be unique. The subclasses will overload the
     * hasName and isUniqueName methods to indicate whether the name
     * is a unique search key.
     */
    @XMLProperty
    public String getName() {
        return _name;
    }

    /**
     * Set the name of this object. This method will trim the 
     * whitespace if necessary. The check for whitespace is necessary, 
     * otherwise Hibernate will think there are changes
     * and possibly update an object when its not necessary.
     */
    public void setName(String name) {

        if (name == null || name.length() == 0)
            _name = null;
        else if ( needsTrim(name) ) 
            _name = name.trim();
        else
            _name = name;
    }

    private boolean needsTrim(String name) {
        if ( name != null && name.length() > 0) {
            int lastIndex = name.length() - 1;
            if ( ( Character.isWhitespace(name.charAt(0) ) ) || 
                 ( Character.isWhitespace(name.charAt(lastIndex) ) ) ) {
                return true; 
            }
        }
        return false;
    }

    /**
     * Returns lock information. If this is non-null
     * it means a lock was acquired, though it might be expired.
     * The LockInfo class provides methods to parse and generate this
     * string.
     */
    public String getLock() {
        return _lock;
    }

    @XMLProperty
    public void setLock(String lock) {
        _lock = lock;
    }
    
    /**
     * Utility method to check if object is locked.
     * Need to make sure that the object is fresh.
     * This will not go to the db.
     */
    public boolean isLocked() throws GeneralException {
        
        boolean locked = false;
        
        LockInfo lockInfo = getLockInfo();
        if (lockInfo != null) {
            locked = !lockInfo.isExpired();
        }
        
        return locked;
    }
    
    /**
     * Return the dirty flag. Note that this is neither persistent or
     * in the XML model.
     */
    public boolean isDirty() {
        return _dirty;
    }

    public void setDirty(boolean b) {
        if (log.isInfoEnabled()) {
            if (_dirty != b) {
                String action = (b) ? "Setting" : "Clearing";
                log.info(action + " dirty flag on " + toString());
            }
        }
        _dirty = b;
    }

    /** 
     * Returns the owner of this object.
     * Not all objects will have owners.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Identity getOwner() {
        return _owner;
    }

    /** 
     * Sets the owner of this object.
     */
    public void setOwner(Identity owner) {
        _owner = owner;
    }

    /**
     * Return the object description. Descriptions are generally
     * longer than the name and are intended for display in
     * a multi-line text area.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
        _description = s;
    }

    /**
     * Return the date the object was last modified.
     */
    @XMLProperty
    public Date getModified() {
        return _modified;
    }

    /**
     * This is intended for use only by the persistent storage manager
     * to be set immediately prior to storage.
     */
    public void setModified(Date d) {
        _modified = d;
    }

    /**
     * Return the date the object was created.
     */
    @XMLProperty
    public Date getCreated() {
        return _created;
    }

    /**
     * This is intended for use only by the persistent storage manager
     * to be set immediately prior to storage.
     */
    public void setCreated(Date created) {
        _created = created;
    }
    
    /**
     * Get the signature list for this object.
     * This is expected to be overloaded in classes that support
     * signatures.
     */
    public List<SignOffHistory> getSignOffs() {
        return null;
    }

    /**
     * Set the signature for this object.
     * This is expected to be overloaded in classes that support
     * signatures.
     */
    public void addSignOff(SignOffHistory esig) {
    }

    @XMLProperty
    public boolean isImmutable() {
        return this._immutable;
    }

    public void setImmutable(boolean immutable) {
        this._immutable = immutable;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Scope getAssignedScope() {
        return _assignedScope;
    }
    
    /**
     * The scope in which this object lives. This is not required, but if
     * set it can restrict access to this object to the owner or people that
     * have control over this scope.
     */
    public void setAssignedScope(Scope scope) {
        _assignedScope = scope;

        // Denormalize path if it has changed.
        String path = (null != scope) ? scope.getPath() : null;
        if (!Util.nullSafeEq(path, getAssignedScopePath(), true)) {
            setAssignedScopePath(path);
        }
    }
    
    /**
     * The path of the assignedScope. This is denormalized from the assigned
     * scope onto each object so that scoping queries do not always require
     * joining to the scope table.  
     */
    @XMLProperty
    public String getAssignedScopePath() {
        return _assignedScopePath;
    }
    
    public void setAssignedScopePath(String path) {
        _assignedScopePath = path;
    }
    
    /**
     * Returns true if the object is disabled. Not all objects
     * support disabling semantics.
     */
    @XMLProperty
    public boolean isDisabled() {
        return _disabled;
    }

    public void setDisabled(boolean b) {
        _disabled = b;
    }

    /**
     * Return the pending workflow case on this object.
     * This is used for subclasses that support an approval 
     * process like Bundle.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public WorkflowCase getPendingWorkflow() {
        return _pendingWorkflow;
    }

    public void setPendingWorkflow(WorkflowCase wfcase) {
        _pendingWorkflow = wfcase;
    }

    /**
     * Get the transient UI unique id.
     */
    public String getUid() {
        return _uid;
    }

    public void setUid(String id) {
        _uid = id;
    }

    public boolean isRefreshedExistingLock() {
        return _refreshedExistingLock;
    }

    public void setRefreshedExistingLock(boolean b) {
        _refreshedExistingLock = b;
    }

    /**
     * Get all the classifications for the object
     * @return List of ObjectClassificaitons
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<ObjectClassification> getClassifications() { return _classifications; }

    /**
     * Set the classifications on the object
     * @param c List of ObjectClassifications
     */
    public void setClassifications(List<ObjectClassification> c) { this._classifications = c; }

    public boolean addClassification(ObjectClassification classification) {
        boolean added = false;
        if (classification != null) {
            if (_classifications == null) {
                _classifications = new ArrayList<>();
            }

            if (!_classifications.contains(classification)) {
                _classifications.add(classification);
                added = true;
            }
        }

        return added;
    }

    public boolean removeClassification(ObjectClassification classification) {
        boolean removed = false;
        if (classification != null && _classifications != null) {
            removed = _classifications.remove(classification);
            if (_classifications.isEmpty()) {
                _classifications = null;
            }
        }

        return removed;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Extended Attributes
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A class is expected to overload this if it has extended attributes.
     * The actual Attributes property cannot be down here because classes 
     * have not been consistent in where they put them. Usually
     * it is in an _attributes property but Application was using
     * that for a different purpose and had to use _extendedAttributes.
     *
     * @ignore
     * Sadly this can't be an Attributes object because we've got some
     * legacy maps to deal with and I didn't want to convert them at runtime.
     *
     * Note that this is not an XML property, the subclasses define their
     * own XML representation.
     */
    public Map<String, Object> getExtendedAttributes() {
        return null;
    }

    /**
     * A class should overload this if it has extended attributes and
     * needs additional logic to get the appropriate attribute.
     *
     * @param key
     * @return The requested attribute value
     */
    public Object getExtendedAttribute(String key) {
        Object value = null;
        Map<String, Object> extended = this.getExtendedAttributes();
        if (extended != null) {
            value = extended.get(key);
        }
        return value;
    }

    /**
     * A class is expected to overload this if it has an external 
     * attribute map.  
     * @ignore
     * Not using these yet, and we could just have it
     * down here since there is no legacy use of the "externalAttributes"
     * property.
     */
    public Map<String, String> getExternalAttributes() {
        return null;
    }

    /**
     * ObjectConfig wants to call this if it has something to promote
     * and there was no Attributes object before.
     */
    public void setExternalAttributes(Map<String,String> atts) {
    }

    /**
     * Common accessor for numbered extended attributes. Prior to 
     * 6.1, these were stored in an array that had to be synced with
     * the attribute map. Now they are always redirected to the map to avoid
     * synchronization issues. Because of this setExtended does nothing
     * since the value is already in the map and only Hibernate calls
     * the setExtended methods.  
     * 
     * NOTE: Index should be between 1 and {@link #MAX_EXTENDED_ATTRIBUTES}
     */
    public String getExtended(int index) {

        String value = null;

        ObjectConfig config = ObjectConfig.getObjectConfig(this.getClass());
        if (config != null) {
            ObjectAttribute att = config.getObjectAttribute(index);
            if (att != null) {
                Map<String,Object> atts = getExtendedAttributes();
                if (atts != null) {
                    Object o = atts.get(att.getName());
                    if (o != null) {
                        // For Date, convert to a utime.  Something like
                        // this used to be done in Identitizer.setExtendedAttribute
                        // when promoting, but now we do it on the way out
                        if (o instanceof Date) {
                            Date date = (Date)o;
                            long utime = date.getTime();
                            value = Long.toString(utime);
                        }   
                        else
                            value = o.toString();
                    }
                }
            }
        }
        return value;
    }

    /**
     * Hibernate requires a getter and setter pair for mapped columns.
     * All of the numbered methods forward to this generic method
     * which by default does nothing but can be overridden in a subclass
     * in case it does not want to use an attributes map.
     */
    public void setExtended(int index, String value) {
    }

    //
    // Hibernate Accessors
    // 

    public void setExtended1(String s) {
        setExtended(1, s);
    }
    public String getExtended1() {
        return getExtended(1);
    }

    public void setExtended2(String s) {
        setExtended(2, s);
    }
    public String getExtended2() {
        return getExtended(2);
    }

    public void setExtended3(String s) {
        setExtended(3, s);
    }
    public String getExtended3() {
        return getExtended(3);
    }

    public void setExtended4(String s) {
        setExtended(4, s);
    }
    public String getExtended4() {
        return getExtended(4);
    }

    public void setExtended5(String s) {
        setExtended(5, s);
    }
    public String getExtended5() {
        return getExtended(5);
    }

    public void setExtended6(String s) {
        setExtended(6, s);
    }
    public String getExtended6() {
        return getExtended(6);
    }

    public void setExtended7(String s) {
        setExtended(7, s);
    }
    public String getExtended7() {
        return getExtended(7);
    }

    public void setExtended8(String s) {
        setExtended(8, s);
    }
    public String getExtended8() {
        return getExtended(8);
    }

    public void setExtended9(String s) {
        setExtended(9, s);
    }
    public String getExtended9() {
        return getExtended(9);
    }

    public void setExtended10(String s) {
        setExtended(10, s);
    }
    public String getExtended10() {
        return getExtended(10);
    }

    public void setExtended11(String s) {
        setExtended(11, s);
    }
    public String getExtended11() {
        return getExtended(11);
    }

    public void setExtended12(String s) {
        setExtended(12, s);
    }
    public String getExtended12() {
        return getExtended(12);
    }

    public void setExtended13(String s) {
        setExtended(13, s);
    }
    public String getExtended13() {
        return getExtended(13);
    }

    public void setExtended14(String s) {
        setExtended(14, s);
    }
    public String getExtended14() {
        return getExtended(14);
    }

    public void setExtended15(String s) {
        setExtended(15, s);
    }
    public String getExtended15() {
        return getExtended(15);
    }

    public void setExtended16(String s) {
        setExtended(16, s);
    }
    public String getExtended16() {
        return getExtended(16);
    }

    public void setExtended17(String s) {
        setExtended(17, s);
    }
    public String getExtended17() {
        return getExtended(17);
    }

    public void setExtended18(String s) {
        setExtended(18, s);
    }
    public String getExtended18() {
        return getExtended(18);
    }

    public void setExtended19(String s) {
        setExtended(19, s);
    }
    public String getExtended19() {
        return getExtended(19);
    }

    public void setExtended20(String s) {
        setExtended(20, s);
    }
    public String getExtended20() {
        return getExtended(20);
    }

    /**
     * override the setExtendedIdentityXX functions
     * in the subclass to support identity extended
     * attributes
     */

    public boolean supportsExtendedIdentity() {
        return false;
    }
    
    public void setExtendedIdentity1(Identity val) {
    }
    
    public Identity getExtendedIdentity1() {
        return null;
    }

    public void setExtendedIdentity2(Identity val) {
    }
    
    public Identity getExtendedIdentity2() {
        return null;
    }

    public void setExtendedIdentity3(Identity val) {
    }
    
    public Identity getExtendedIdentity3() {
        return null;
    }

    public void setExtendedIdentity4(Identity val) {
    }
    
    public Identity getExtendedIdentity4() {
        return null;
    }

    public void setExtendedIdentity5(Identity val) {
    }
    
    public Identity getExtendedIdentity5() {
        return null;
    }

    public void setExtendedIdentity6(Identity val) {
    }
    
    public Identity getExtendedIdentity6() {
        return null;
    }

    public void setExtendedIdentity7(Identity val) {
    }
    
    public Identity getExtendedIdentity7() {
        return null;
    }
    
    public void setExtendedIdentity8(Identity val) {
    }
    
    public Identity getExtendedIdentity8() {
        return null;
    }
    
    public void setExtendedIdentity9(Identity val) {
    }
    
    public Identity getExtendedIdentity9() {
        return null;
    }
    
    public void setExtendedIdentity10(Identity val) {
    }
    
    public Identity getExtendedIdentity10() {
        return null;
    }
    
    public void setExtendedIdentity11(Identity val) {
    }
    
    public Identity getExtendedIdentity11() {
        return null;
    }
    
    public void setExtendedIdentity12(Identity val) {
    }
    
    public Identity getExtendedIdentity12() {
        return null;
    }
    
    public void setExtendedIdentity13(Identity val) {
    }
    
    public Identity getExtendedIdentity13() {
        return null;
    }
    
    public void setExtendedIdentity14(Identity val) {
    }
    
    public Identity getExtendedIdentity14() {
        return null;
    }
    
    public void setExtendedIdentity15(Identity val) {
    }
    
    public Identity getExtendedIdentity15() {
        return null;
    }
    
    public void setExtendedIdentity16(Identity val) {
    }
    
    public Identity getExtendedIdentity16() {
        return null;
    }
    
    public void setExtendedIdentity17(Identity val) {
    }
    
    public Identity getExtendedIdentity17() {
        return null;
    }
    
    public void setExtendedIdentity18(Identity val) {
    }
    
    public Identity getExtendedIdentity18() {
        return null;
    }
    
    public void setExtendedIdentity19(Identity val) {
    }
    
    public Identity getExtendedIdentity19() {
        return null;
    }
    
    public void setExtendedIdentity20(Identity val) {
    }
    
    public Identity getExtendedIdentity20() {
        return null;
    }
    
    public Identity getExtendedIdentity(int number) {

        if (number < 1 || number > MAX_EXTENDED_IDENTITY_ATTRIBUTES) {
            throw new IllegalStateException("wrong identity extended attribute number: " + number);
        }
        
        switch (number) {
            case 1:
                return getExtendedIdentity1();
            case 2:
                return getExtendedIdentity2();
            case 3:
                return getExtendedIdentity3();
            case 4:
                return getExtendedIdentity4();
            case 5:
                return getExtendedIdentity5();
            case 6:
                return getExtendedIdentity6();
            case 7:
                return getExtendedIdentity7();
            case 8:
                return getExtendedIdentity8();
            case 9:
                return getExtendedIdentity9();
            case 10:
                return getExtendedIdentity10();
            case 11:
                return getExtendedIdentity11();
            case 12:
                return getExtendedIdentity12();
            case 13:
                return getExtendedIdentity13();
            case 14:
                return getExtendedIdentity14();
            case 15:
                return getExtendedIdentity15();
            case 16:
                return getExtendedIdentity16();
            case 17:
                return getExtendedIdentity17();
            case 18:
                return getExtendedIdentity18();
            case 19:
                return getExtendedIdentity19();
            case 20:
                return getExtendedIdentity20();
        }
        
        throw new IllegalStateException("should never be here, make sure that you have defined " + MAX_EXTENDED_ATTRIBUTES + " in your function");
    }

    public void setExtendedIdentity(Identity val, int number) {

        if (number < 1 || number > MAX_EXTENDED_IDENTITY_ATTRIBUTES) {
            throw new IllegalStateException("wrong identity extended attribute number: " + number);
        }
        
        switch (number) {
            case 1:
                setExtendedIdentity1(val);
                return;
            case 2:
                setExtendedIdentity2(val);
                return;
            case 3:
                setExtendedIdentity3(val);
                return;
            case 4:
                setExtendedIdentity4(val);
                return;
            case 5:
                setExtendedIdentity5(val);
                return;
            case 6:
                setExtendedIdentity6(val);
                return;
            case 7:
                setExtendedIdentity7(val);
                return;
            case 8:
                setExtendedIdentity8(val);
                return;
            case 9:
                setExtendedIdentity9(val);
                return;
            case 10:
                setExtendedIdentity10(val);
                return;
            case 11:
                setExtendedIdentity11(val);
                return;
            case 12:
                setExtendedIdentity12(val);
                return;
            case 13:
                setExtendedIdentity13(val);
                return;
            case 14:
                setExtendedIdentity14(val);
                return;
            case 15:
                setExtendedIdentity15(val);
                return;
            case 16:
                setExtendedIdentity16(val);
                return;
            case 17:
                setExtendedIdentity17(val);
                return;
            case 18:
                setExtendedIdentity18(val);
                return;
            case 19:
                setExtendedIdentity19(val);
                return;
            case 20:
                setExtendedIdentity20(val);
                return;
        }
        
        throw new IllegalStateException("should never be here, make sure that you have defined " + MAX_EXTENDED_ATTRIBUTES + " in your function");
    }
    
    public boolean isExtendedIdentityType(ObjectAttribute attributeDefinition) {
        
        return (attributeDefinition.getExtendedNumber() > 0 
                && ObjectAttribute.TYPE_IDENTITY.equals(attributeDefinition.getType()) 
                && supportsExtendedIdentity());
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Attribute MetaData
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.LIST,xmlname="AttributeMetaDatas")
    public List<AttributeMetaData> getAttributeMetaData() {
        return _attributeMetaData; 
    }

    public void setAttributeMetaData(List<AttributeMetaData> meta) {
        _attributeMetaData = meta;
    }

    public AttributeMetaData getAttributeMetaData(String attrName) {
        AttributeMetaData found = null;
        if ( _attributeMetaData != null && attrName != null) {
            for (AttributeMetaData meta : _attributeMetaData) {
                if (attrName.equals(meta.getAttribute())) {
                    found = meta;
                    break;
                }
            }
        }
        return found;
    }

    public AttributeMetaData getAttributeMetaData(ObjectAttribute def) {
        return (def != null) ? getAttributeMetaData(def.getName()) : null;
    }

    public void addAttributeMetaData(AttributeMetaData amd) {
        if (amd != null) {
            if (_attributeMetaData == null) {
                _attributeMetaData = new ArrayList<AttributeMetaData>();
            }
            _attributeMetaData.add(amd);
        }
    }

    public void removeAttributeMetaData(String attrName) {
        if (_attributeMetaData != null) {
            AttributeMetaData meta = getAttributeMetaData(attrName);
            if (meta != null)
                _attributeMetaData.remove(meta);
        }
    }

    public void remove(AttributeMetaData md) {
        if (md != null)
            removeAttributeMetaData(md.getAttribute());
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // XMLReferenceTarget
    //
    //////////////////////////////////////////////////////////////////////

    public String getReferenceClass() {
        return getClass().getName();
    }

    public String getReferenceId() {
        return _id;
    }

    public String getReferenceName() {
        return _name;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Object overrides - non-ID equality required by hibernate.
    //
    //////////////////////////////////////////////////////////////////////

    public String toString()
    {
        return new ToStringBuilder(this)
            .append("id", getId())
            .append("name", getName())
            .toString();
    }

    /**
     * We override equals so that two objects will compare equal
     * if they have the same database id or unique name, but are
     * different Java objects. This is necessary 
     * for Hibernate because caches commonly have to be cleared or 
     * objects from one session need to be used to match things loaded in
     * a different session.
     *
     * @ignore
     * Be very careful about putting things down here or overloading
     * it in a subclass.  The persistence layer expects this to mean 
     * "identity" equality not "logical" or "content" equality.
     */
    public boolean equals(Object o)
    {
        boolean eq = false;
        
        if (this == o)
            eq = true;
        
        else if (this.getClass().isInstance(o)) {
            SailPointObject so = (SailPointObject) o;

            // bug 30339: Checking the Id first instead of the name.  This will keep from having to load
            // the whole object in cases where the Ids are already the same.
            if (getId() != null && so.getId() != null)
                eq = getId().equals(so.getId());

            else if (isNameUnique() && getName() != null && so.getName() != null)
                eq = getName().equals(so.getName());
        }

        return eq;
    }

    public int hashCode()
    {
        int code;

        // jsl - not sure why we have to do this, but it looks like
        // it needs to be consistent with the equals rules?
        // same issues using name if isNameUnique is false
        if (isNameUnique()) {
            code =  new HashCodeBuilder()
                .append(this.getClass().hashCode())
                .append(_name)
                .toHashCode();
        }
        else {
            if (_id != null) {
                // we are assuming id is  (more or less) unique
                // across different classes
                return _id.hashCode();
            } else {
                code = this.getClass().hashCode();
            }
        }

        return code;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generic hook for object visitors.
     */
    public void visit(Visitor v) throws GeneralException {
        v.visitSailPointObject(this);
    }
    
    /**
     * Parse the lock string into a more usable structure.
     */
    public LockInfo getLockInfo() throws GeneralException {

        return (_lock != null) ? new LockInfo(_lock) : null;
    }

    /**
     * Helper for the XML serializers.
     * Convert a set of objects into a set of references.
     */
    public Set<Reference> getReferenceSet(Set<SailPointObject> objs) {

        Set<Reference> refs = null;
        if (objs != null) {
            refs = new HashSet<Reference>();
            for (SailPointObject obj : objs)
                refs.add(new Reference(obj));
        }
        return refs;
    }

    /**
     * Provide a hint regarding what columns to display for anything that
     * wants to display a list of these objects.
     * 
     * A LinkedHashMap is used for the return value because of its ordering
     * capabilities.
     * 
     * The Map key is name in the object model and the Map value is the column
     * label.
     * 
     * @return a map with the column name and column label
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("name", "Name");
        return cols;
    }

    /**
     * Provide a display format for each line in the list of these objects.
     * This string is used by PrintWriter.format().
     *
     * @return a print format string
     */
    public static String getDisplayFormat() {
        return "%s\n";
    }

    /**
     * True if the object is autoCreated due to unresolved references
     * @return True if the object is autoCreated due to unresolved references
     */
    public boolean isAutoCreated() {
        return false;
    }

    /**
     * Perform a deep copy of the object.
     * This differs from clone() which has historically done a shallow
     * copy and is not passed an XMLReferenceResolver.
     * Places that have been doing a clone probably should have been
     * doing a deep copy.
     */
    public Object deepCopy(XMLReferenceResolver res) 
        throws GeneralException {

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        return f.clone(this, res);
    }

    /**
     * Another deepCopy variant that takes a Resolver which 
     * is more commonly passed to sailpoint.object methods.
     * Here a special proxy object needs to be used to get it
     * into the XMLObjectFactory.
     */
    public Object deepCopy(Resolver res) 
        throws GeneralException {

        XMLObjectFactory f = XMLObjectFactory.getInstance();
        return f.clone(this, new XMLResolverProxy(res));
    }
    
    /**
     * A variant of deepCopy intended for use when you are
     * creating a new object using another as a template.
     * This object is the template, a deepCopy is done then
     * some things are removed that are not relevant in the new object.
     */
    public Object derive(Resolver res) 
        throws GeneralException {

        Object o = deepCopy(res);
        // why does the deepCopy signature return Object?
        if (o instanceof SailPointObject) {
            SailPointObject spo = (SailPointObject)o;
            spo.clearPersistentIdentity();
            // when deriving the top-level object we also clear the name
            spo.setName(null);
        }
        return o;
    }

    /**
     * Utility to clear out "object identity" information.
     * This is used when deriving new objects from existing objects.
     * Note that the name is NOT cleared, since some template
     * clones need to keep the names of child objects (for example, Policy
     * and GenericConstraint).
     */
    public void clearPersistentIdentity() {
        setId(null);
        setCreated(null);
        setModified(null);
        setLock(null);
        setPendingWorkflow(null);
    }

    /**
     * This method traverses all members of a SailPointObject so that they
     * are loaded and available to a session even after they have been 
     * detached.  The default implementation does nothing.  Subclasses must
     * implement this functionality themselves if they need to provide it.
     */
    public void load() { }

    //////////////////////////////////////////////////////////////////////
    //
    // Helpers
    //
    //////////////////////////////////////////////////////////////////////

    private static final Comparator<SailPointObject> SP_OBJECT_BY_NAME_COMPARATOR =
        new Comparator<SailPointObject>() {
            public int compare(SailPointObject p1, SailPointObject p2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(p1.getName(), p2.getName());
            }
        };
        
    public static Comparator<SailPointObject> getByNameComparator() {
        return SP_OBJECT_BY_NAME_COMPARATOR;
    }
}
