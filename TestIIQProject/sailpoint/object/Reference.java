/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class to represent references to SailPointObjects.
 * 
 * Author: Jeff
 * 
 * This is a funny one because the <Reference> element
 * syntax is understood in two places.  Here we use annotations
 * to serialize it when a Reference object is encountered
 * within a container like a Map or List.  Within
 * the XML processor there are also several special serializers
 * like ReferenceSerializer to handle properties annotated 
 * as references.  These will use the same syntax but they
 * do not instantiate a Reference object.
 *
 * You may use this in cases where you want to store a reference
 * to an object in a Map such as Configuration:SystemConfiguration
 * but don't want the object to be serialized inline.
 *
 */

package sailpoint.object;

import org.hibernate.Hibernate;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;

/**
 * Class to represent references to SailPointObjects.
 */
@XMLClass
public class Reference implements Serializable, Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -6223480049563420635L;
    
    String _class;
    String _id;
    String _name;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Reference() {
    }
    
    public Reference(String className, String id) {
        _class = className;
        _id = id;
    }

    public Reference(String className, String id, String name) {
        _class = className;
        _id = id;
        _name = name;
    }

    public Reference(SailPointObject obj) {

        // <insert expletive> hibernate!  If the object is a hibernate proxy,
        // we need to rip out the real class.
        Class clazz = Hibernate.getClass(obj);

        _class = clazz.getName();
        _id = obj.getId();
        _name = obj.getName();
    }

    @XMLProperty(xmlname="class")
    public String getClassName() {
        return _class;
    }

    public void setClassName(String s) {
        _class = s;
    }

    @XMLProperty
    public String getId() {
        return _id;
    }

    public void setid(String s) {
        _id = s;
    }

    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = s;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public String getNameOrId() {
        return (_name != null) ? _name : _id;
    }

    public String getIdOrName() {
        return (_id != null) ? _id : _name;
    }

    /**
     * Attempt to resolve this reference to a SailPointObject using the given
     * resolver.
     * 
     * @param  resolver  The Resolver to use to resolve this object.
     * 
     * @return The SailPointObject pointed to by this reference if it still
     *         exists, null otherwise.
     */
    public SailPointObject resolve(Resolver resolver)
        throws GeneralException {
        
        SailPointObject obj = null;
        
        if (null != _class) {
            try {
                Class<? extends SailPointObject> clazz =
                    Class.forName(_class).asSubclass(SailPointObject.class);
                if (getId() != null) {
                    obj = resolver.getObjectById(clazz, getId());
                } else {
                    obj = resolver.getObjectByName(clazz, getName());
                }
            }
            catch (ClassNotFoundException e) {
                throw new GeneralException(e);
            }
        }
        
        return obj;
    }
    
    // jsl - why are these here?

    @Override
    public Object clone() {
        Object o = null;
        try {
            o = super.clone();
        }
        catch (CloneNotSupportedException e) {
            // We implement cloneable ... this ain't gonna happen.  Get over it!
        }
        return o;
    }
    
    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((_class == null) ? 0 : _class.hashCode());
        result = PRIME * result + ((_id == null) ? 0 : _id.hashCode());
        result = PRIME * result + ((_name == null) ? 0 : _name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Reference other = (Reference) obj;
        if (_class == null) {
            if (other._class != null)
                return false;
        } else if (!_class.equals(other._class))
            return false;
        if (_id == null) {
            if (other._id != null)
                return false;
        } else if (!_id.equals(other._id))
            return false;
        if (_name == null) {
            if (other._name != null)
                return false;
        } else if (!_name.equals(other._name))
            return false;
        return true;
    }
}
