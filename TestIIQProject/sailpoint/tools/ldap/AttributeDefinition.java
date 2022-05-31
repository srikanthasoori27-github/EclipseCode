/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.tools.ldap;

import sailpoint.tools.GeneralException;

/**
 * An AttributeDefinition represents an attribute definition defined in an LDAP
 * schema.
 * <pre>
 *   RFC: http://www.ietf.org/rfc/rfc2252.txt
 * </pre>
 *
 */
public class AttributeDefinition {

    private String name;
    private String description;
    private String syntax;
    private boolean multi;

    
    /**
     * Constructor.
     * 
     * @param  attrName  The name of the attribute.
     * @param  attrDef   The RFC 2252 attribute definition string.
     */
    public AttributeDefinition(String attrName, String attrDef)
        throws GeneralException {
        
        // ( 1.3.6.1.4.1.6054.3.1.2.9 NAME 'erLastLogon'
        //    DESC 'The last time the user loged on.'
        //    SYNTAX 1.3.6.1.4.1.1466.115.121.1.15
        //    SINGLE-VALUE
        // )

        // We pass in the primary name to avoid having to parse out a list of
        // names.
        this.name = attrName;

        this.description = LDAPUtil.getRFC2252QuotedAttribute(attrDef, "DESC");
        this.syntax = LDAPUtil.getRFC2252StringAttribute(attrDef, "SYNTAX");
        this.multi = (attrDef.indexOf("SINGLE-VALUE") == -1);
    }

    /**
     * Return the name of the attribute.
     */
    public String getName() {
        return this.name;
    }
    
    /**
     * Return the description of the attribute.
     */
    public String getDescription() {
        return this.description;
    }
    
    /**
     * Return the syntax of the attribute (see the RFC what each type is used
     * for).
     */
    public String getSyntax() {
        return this.syntax;
    }
    
    /**
     * Return whether this is a multi-valued attribute.
     */
    public boolean isMulti() {
        return this.multi;
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
