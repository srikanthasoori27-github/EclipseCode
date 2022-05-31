/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.Indexes;
import sailpoint.tools.Index;

/**
 * Class used to represent a multi-valued extended attribute.
 * Single valued extended attributes are stored as columns
 * in the associated class table, multi-valued attributes
 * have to be stored in a different table.
 */
@Indexes({
        // this has to have it's own for Terminator
 @Index(name="externalObjectId",property="objectId", subClasses=true),

 @Index(name="externalNameValComposite",property="attributeName",
        caseSensitive=false, subClasses=true),
 @Index(name="externalNameValComposite",property="value",
        caseSensitive=false, subClasses=true),

 @Index(name="externalOidNameComposite",property="objectId",
        caseSensitive=true, subClasses=true),
 @Index(name="externalOidNameComposite",property="attributeName",
        caseSensitive=false, subClasses=true)})
@XMLClass
public class ExternalAttribute extends SailPointObject
    implements Cloneable {

    private static final long serialVersionUID = 3362870191826185018L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    String _attrName;

    String _attrValue;

    String _objectId;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ExternalAttribute() { }

    public ExternalAttribute(String attName, String attValue, String objectId) {
        _attrName = attName;
        _attrValue = attValue;
        _objectId = objectId;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getAttributeName() {
        return _attrName;
    }

    public void setAttributeName(String name) {
        _attrName = name;
    }

    public String getObjectId() {
        return _objectId;
    }

    public void setObjectId(String objId) {
        _objectId = objId;
    }

    public String getValue() {
        return _attrValue;
    }

    public void setValue(String value) {
        _attrValue = value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * These do not have names, though now that they support the concept
     * of non-unique names this could be used for displayName.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }
}
