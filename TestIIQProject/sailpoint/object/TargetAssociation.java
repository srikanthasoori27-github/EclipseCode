/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.tools.Message;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@XMLClass
public class TargetAssociation extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static final long serialVersionUID = 1L;

    /**
     * Values for targetType to denote what type of Target referenced
     * @ignore
     * This is confusing.  P means it was a permission
     * indexed from a group or role.  TP means it is unstructured data
     * with permissions.  If _targetType is not one of those two
     * it is the name of an attribute.
     */
    public static enum TargetType {
        P("target_type_permission"),
        TP("target_type_target_permission");

        String _displayName;

        TargetType(String displayName) {
            _displayName = displayName;
        }

        public String getDisplayName() {
            return new Message(_displayName).getLocalizedMessage();
        }

        public String getDisplayName(Locale loc, TimeZone tz) {
            return new Message(_displayName).getLocalizedMessage(loc, tz);
        }
    }

    /**
     * Value for _type (referencing object type)
     */
    public static enum OwnerType {
        R("Role"),
        A("Managed Attribute"),
        L("Link");

        String _type;

        OwnerType(String s) {
            _type = s;
        }

        public String getType() {
            return _type;
        }
    }

    /**
     * The hibernate id of the object that has access to this target.
     * 
     * This will be a ManagedAttribute id if the associations is owned by
     * a group or other aggregated schema object.  It will be a Permission
     * or Attribute association if it was built by the effective access
     * indexing task.  It will be a file share association if it was built
     * by the target collector.
     * 
     * This will be a Bundle id if the association is owned by a role.
     * It can only be built by the effectve access indexing task.
     * 
     * This will be a Link id if the association is owned by an account.
     * It can only be built by the target collector when it sees a direct
     * permission to a file share on an account.
     *
     */
    private String _ownerId;

    /**
     * Type of object that has this target.
     * Currently ManagedAttribute or Account.
     * TODO: Now that we have an enum for these, use it!
     */
    private String _ownerType;

    /**
     * The name of the Application that has this target.
     * This is used only for role associations since the role
     * can contain targets from more than one application.  It is
     * not necessary for ManagedAttribute associations.
     */
    String _applicationName;

    /**
     * For associations built from indexed attribute, the type
     * will be the name of the attribute.
     *
     * For associations built from direct permissions, the type
     * will be the reserved word "P".
     *
     * For associations built by the target collector this will be null.
     * TODO: Might be nice to have this be "U" or "unstructured", "fileshare"
     * or something promoted from the Target so we can query it 
     * consistently.
     */
    private String _targetType;

    /**
     * Target reference.  This is used only for targets that
     * were collected by a TargetCollector, typically file shares.
     */
    private Target _target;

    /**
     * Simple target name used for associations from indexed attributes
     * or permissions.  These won't have Target objects.
     * ATTRIBUTE, these won't have Target objects.
     * TODO: Should we copy the Target.name here just for
     * consistency?
     */
    private String _targetName;
    
    /**
     * CSV List of Rights assigned to the associated object.
     * Not relevant for TARGET_TYPE_ATTRIBUTE.
     */
    private String _rights;
    
    /**
     * For unstructured target permissions, this is true if the permissions
     * were inherited through some native inheritance mechanism.  The only
     * example of this we have currently is Windows, where permissions on 
     * a directory will be implicitly applied to the directories it contains.
     *
     * This is different than confusingly named groupInherited.
     * I'd prefer that this be named nativeInherited.
     */
    private boolean _inherited;

    /**
     * Effectiveness of the permissions
     * 0 - ineffective
     * 1 - partially effective
     * 2 - fully effective
     */
    private int _effective;

    /**
     * True if the permission is an Deny permission, false if allow
     */
    private boolean _denyPermission;

    /**
     * Date of the last Aggregation
     */
    private Date _lastAggregation;

    /**
     * For any type of association, when this is true it means that
     * the association was inherited through a group hierarchy.  This is
     * different than the "inherited" property.
     *
     * Part of the indexing of target associations is copying associations from
     * one level of a group (ManagedAttribute) hierarchy to child groups.  This
     * is called flattening.  The result is that you can query for groups
     * that grant a target indirectly through the group hierarchy.
     *
     * The inherited property is similar, but that applies to some native
     * relationship in the unstructured target that we don't model in IIQ.
     */
    boolean _flattened;

    /**
     * The string representation of the IIQ ManagedAttribute hierarchy path to the 
     * target.  This is only set by the effective access indexing task.
     * No assumptions must be made about the syntax or completness of
     * this value, it is for display purposes only.  It may be trimmed to
     * fit the column if the hierarchy is deep with long names.
     * 
     * If the target is held directly on the ManagedAttribute it will be null
     * If the target is indirectly accessed through a group hierarchy, this
     * will be a delimited list of the display names of the parent groups.
     * 
     * If the target is accessed through a role, the hierarchy will be copied
     * from the TargetAssociation of the ManagedAttribute.  The role hierarchy
     * will not be included.
     */
    String _hierarchy;


    /**
     * Argument used to store Classifications the TargetAssociation provides
     */
    public static String ATT_CLASSIFICATIONS = "classifications";

    Attributes<String, Object> _attributes;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public TargetAssociation() {
    }

    /**
     * These don't have names.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    /**
     * This is for TargetAggregator which needs to do periodic decache
     * while modifying anobject.
     */
    public void load() {
        if (_target != null) {
            // this also has a TargetSource and a parent Target
            // but we don't need those for aggregation
            _target.getFullPath();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @ignore
     * Really wish this was more than just objectId but it's too late now
     * without an ugly upgrader, I guess we could have two methods but we
     * can't change the name used in Filters without potentially breaking
     * a customization.
     * ?? really, who would have been playing with these
     */
    @XMLProperty
    public String getObjectId() {
        return _ownerId;
    }

    public void setObjectId(String id) {
        _ownerId = id;
    }

    /**
     * Set the owning object id with a more obvious method name.
     */
    public void setOwnerId(String id) {
        setObjectId(id);    
    }

    public String getOwnerId() {
        return getObjectId();
    }
    
    @XMLProperty
    public String getHierarchy() {
        return _hierarchy;
    }

    public void setHierarchy(String s) {
        _hierarchy = s;
    }

    /**
     * Set the owner type.
     * @ignore
     * This has to use "type" as the XML name for backward compatibility 
     * unless we want to write an upgrader.
     */
    @XMLProperty(xmlname="type")
    public void setOwnerType(String type) {
        _ownerType = type;
    }

    public String getOwnerType() {
        return _ownerType;
    }

    @XMLProperty
    public String getApplicationName() {
        return _applicationName;
    }

    public void setApplicationName(String id) {
        _applicationName = id;
    }

    @XMLProperty
    public String getTargetType() {
        return _targetType;
    }

    public void setTargetType(String type) {
        _targetType = type;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname="ReferencedTarget")
    public Target getTarget() {
        return _target;
    }

    public void setTarget(Target target) {
        _target = target;
    }

    @XMLProperty
    public String getTargetName() {
        return _targetName;
    }

    public void setTargetName(String rights) {
        _targetName = rights;
    }

    /**
     * Get the unique name for the Target within its source.
     * This was added for debug logging, it is not persisted.
     */
    public String getUniqueTargetName() {
        return ((_target != null) ? _target.getUniqueName() : _targetName);
    }
    
    @XMLProperty
    public String getRights() {
        return _rights;
    }

    public void setRights(String rights) {
        _rights = rights;
    }

    @XMLProperty
    public boolean isInherited() {
        return _inherited;
    }

    public void setInherited(boolean b) {
        _inherited = b;
    }

    @XMLProperty
    public boolean isFlattened() {
        return _flattened;
    }

    public void setFlattened(boolean b) {
        _flattened = b;
    }

    @XMLProperty
    public int getEffective() {
        return _effective;
    }

    public void setEffective(int _effective) {
        this._effective = _effective;
    }

    @XMLProperty
    public boolean isDenyPermission() {
        return _denyPermission;
    }

    public void setDenyPermission(boolean _isDeny) {
        this._denyPermission = _isDeny;
    }

    public boolean isAllowPermission() {
        return !_denyPermission;
    }

    public void setAllowPermission(boolean b) {
        this._denyPermission = !b;
    }

    /**
     * Return the attributes map.
     */
    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {

        return _attributes;
    }

    /**
     * Set the attributes map.
     * This is normally called only from the XML parser. Application code should
     * get/set attributes one at a time.
     */
    public void setAttributes(Attributes<String, Object> atts) {

        _attributes = atts;
    }

    /**
     * Return the value of an attribute.
     */
    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    /**
     * Set the value of an attribute.
     */
    public void setAttribute(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String, Object>();
        _attributes.put(name, value);
    }

    public boolean isAccount() {
        if ( ( _ownerType != null ) && 
             ( OwnerType.L.name().compareTo(_ownerType) == 0 ) ) {
            return true;
        }
        return false;
    }

    /**
     * Return true if this is an attribute association.
     */
    public boolean isAttribute() {
        return (_targetType != null && !TargetType.P.name().equals(_targetType)
                && !TargetType.TP.name().equals(_targetType));
    }

    /**
     * Return true if this is a permission association.
     */
    public boolean isPermission() {
        return TargetType.P.name().equals(_targetType);
    }

    /**
     * Return true if this is an unstructured target (permission) associaiton.
     */
    public boolean isUnstructured() {
        return TargetType.TP.name().equals(_targetType);
    }

    /**
     * Helper for getDisplayColumns, derive the target name from 
     * one of two places.
     */
    public String getEffectiveTargetName() {
        return ((_target != null) ? _target.getDisplayName() : _targetName);
    }

    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "ID");
        cols.put("objectId", "Reference By");
        cols.put("ownerType", "Reference Type");
        cols.put("targetType", "Target Type");
        cols.put("targetName", "Target Name");
        cols.put("target", "Target");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %-34s %-16s %-12s %-16s %s\n";
    }

    @XMLProperty
    public Date getLastAggregation() {
        return _lastAggregation;
    }

    public void setLastAggregation(Date _lastAggregation) {
        this._lastAggregation = _lastAggregation;
    }

}
