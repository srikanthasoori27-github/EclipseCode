/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.tools.ldap;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;


/**
 * An ObjectClass represents an object class defined in an LDAP schema. Note
 * that the attributes returned do not include attributes from parent object
 * classes (for example - SUP).
 * <pre>
 *   RFC: http://www.ietf.org/rfc/rfc2252.txt
 * </pre>
 */
public class ObjectClass {

    private static final Log LOG = LogFactory.getLog(ObjectClass.class);

    /**
     * Enumeration of the types of object classes.
     */
    public static enum Type {
        STRUCTURAL,
        AUXILIARY,
        ABSTRACT
    }
    
    private Schema schema;
    
    private String name;
    private List<String> supers;
    private List<String> must;
    private List<String> may;
    private Type type;
    

    /**
     * Constructor.
     * 
     * @param  str     The RFC 2252 definition of the object class.
     * @param  schema  The Schema to use to load attribute definitions.
     */
    public ObjectClass(String str, Schema schema) throws GeneralException {

        this.schema = schema;
        
        // ( 1.3.6.1.4.1.6054.3.122.1.2 NAME 'eractivedirectoryaccount'
        //    SUP top
        //    MUST ( cn $ eruid $ lastname )
        //    MAY ( department $ erpassword $ firstname $ memberof $ telephoneNumber )
        // )
        //
        // Note that the RFC 2252 states that SUP may be an OID list, so try to
        // get multiple, although there is likely only one.
        this.name = LDAPUtil.getRFC2252QuotedAttribute(str, "NAME");
        this.supers = LDAPUtil.getRFC2252OidOrOidList(str, "SUP");
        this.must = LDAPUtil.getRFC2252OidOrOidList(str, "MUST");
        this.may = LDAPUtil.getRFC2252OidOrOidList(str, "MAY");

        // Default to structural if none are present.
        this.type = Type.STRUCTURAL;
        if (str.indexOf("AUXILIARY") > -1) {
            this.type = Type.AUXILIARY;
        }
        else if (str.indexOf("ABSTRACT") > -1) {
            this.type = Type.ABSTRACT;
        }
    }

    /**
     * Return the name of this object class.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Return the super classes of this object class.
     */
    public List<String> getSupers() {
        return this.supers;
    }
    
    /**
     * Return the type of this object class.
     */
    public Type getType() {
        return this.type;
    }
    
    /**
     * Return the name of the must attributes for this object class.
     */
    public List<String> getMust() {
        return this.must;
    }

    /**
     * Return the name of the may attributes for this object class.
     */
    public List<String> getMay() {
        return this.may;
    }

    /**
     * Return all attribute names for this object class.
     */
    public List<String> getAllAttributeNames() {

        List<String> all = new ArrayList<String>();

        if (null != this.must) {
            all.addAll(this.must);
        }
        if (null != this.may) {
            all.addAll(this.may);
        }

        return all;   
    }
    
    /**
     * Return the must attributes for this object class.
     */
    public List<AttributeDefinition> getMustAttributes() throws GeneralException {

        return getAttributes(this.must);
    }

    /**
     * Return the may attributes for this object class.
     */
    public List<AttributeDefinition> getMayAttributes() throws GeneralException {

        return getAttributes(this.may);
    }

    /**
     * Return all attributes for this object class.
     */
    public List<AttributeDefinition> getAllAttributes() throws GeneralException {
        
        List<AttributeDefinition> allAttrs = new ArrayList<AttributeDefinition>();
        List<AttributeDefinition> must = getMustAttributes();
        List<AttributeDefinition> may = getMayAttributes();
        
        if (null != must) {
            allAttrs.addAll(must);
        }
        if (null != may) {
            allAttrs.addAll(may);
        }

        return allAttrs;
    }
    
    /**
     * Retrieve the AttributeDefinitions from the Schema for the given list of
     * attribute names.
     */
    private List<AttributeDefinition> getAttributes(List<String> attrNames)
        throws GeneralException {
        
        List<AttributeDefinition> attrDefs = null;

        if (null != attrNames) {
            attrDefs = new ArrayList<AttributeDefinition>(attrNames.size());
            for (String attrName : attrNames) {
                AttributeDefinition attrDef =
                    this.schema.getAttributeDefinition(attrName);
                if (null != attrDef) {
                    attrDefs.add(attrDef);
                }
                else {
                    LOG.warn("Attribute definition not found in schema: " + attrName);
                }
            }
        }
        
        return attrDefs;
    }
    
    @Override
    public String toString() {
        return "Name: " + this.name + "; Must: " + this.must + "; May: " +
            this.may;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AttributeDefinition)) {
            return false;
        }

        return this.name.toLowerCase().equals(((AttributeDefinition) o).getName().toLowerCase());
    }

    @Override
    public int hashCode() {
        return this.name.toLowerCase().hashCode();
    }
}
