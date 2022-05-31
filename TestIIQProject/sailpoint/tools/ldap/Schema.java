/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.tools.ldap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.tools.GeneralException;

/**
 * A Schema represents the object classes and attribute definitions returned
 * from an LDAP schema. Usually created by LDAPUtil.getSchema(DirConext).
 */
public class Schema {

    private List<String> objectClassDefs;
    private List<String> attributeDefs;
    
    private Map<String, ObjectClass> objectClasses;
    private Map<String, AttributeDefinition> attributes;
    

    /**
     * Constructor.
     * 
     * @param  objectClassDefs  The strings with the RFC 2252 object class
     *                          definitions for an LDAP server.
     * @param  attrDefs         The strings with the RFC 2252 attribute
     *                          definitions for an LDAP server.
     */
    public Schema(List<String> objectClassDefs, List<String> attrDefs) {
        this.objectClassDefs = objectClassDefs;
        this.attributeDefs = attrDefs;
        this.objectClasses = new HashMap<String, ObjectClass>();
        this.attributes = new HashMap<String, AttributeDefinition>();
    }

    /**
     * Return the ObjectClass with the given name from this schema, or null if
     * there is no definition for it.
     */
    public ObjectClass getObjectClass(String objectClassName)
        throws GeneralException {

        String lower = objectClassName.toLowerCase();
        
        ObjectClass clazz = this.objectClasses.get(lower);
        if (null == clazz) {
            for (Iterator<String> it=this.objectClassDefs.iterator(); it.hasNext(); ) {
                String current = it.next();
                if (hasName(current, lower)) {
                    // Remove it from the list once we load it.
                    it.remove();

                    clazz = new ObjectClass(current, this);
                    this.objectClasses.put(lower, clazz);
                    break;
                }
            }
        }

        return clazz;
    }

    /**
     * Return the AttributeDefinition with the given name from this schema, or
     * null if there is no definition for it.
     */
    public AttributeDefinition getAttributeDefinition(String attrName)
        throws GeneralException {
    
        String lower = attrName.toLowerCase();

        AttributeDefinition attrDef = this.attributes.get(lower);
        if (null == attrDef) {
            for (Iterator<String> it=this.attributeDefs.iterator(); it.hasNext(); ) {
                String current = it.next();
                if (hasName(current, lower)) {
                    // Remove it from the list once we load it.
                    it.remove();
    
                    attrDef = new AttributeDefinition(attrName, current);
                    this.attributes.put(lower, attrDef);
                    break;
                }
            }
        }
    
        return attrDef;
    }

    /**
     * Return whether the RFC 2252 object class or attribute definition has the
     * given name.
     */
    private static boolean hasName(String def, String name) {
        
        // Syntax is either:
        //   - NAME 'name'
        //   - NAME ( 'name1' 'name2' )
        //
        // Is this alright?  Can the quoted name be found anywhere else if it's
        // not the right name?
        return (def.toLowerCase().indexOf("'" + name + "'") > -1);
    }
}
