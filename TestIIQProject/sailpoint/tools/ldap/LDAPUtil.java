/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.tools.ldap;

import java.util.ArrayList;
import java.util.List;

import javax.naming.CompositeName;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import sailpoint.tools.GeneralException;


/**
 * Utility class to help with LDAP access.
 */
public class LDAPUtil {

    /**
     * Private constructor. All methods are static.
     */
    private LDAPUtil() {}

    /**
     * Return a single string value for the attribute with the given name from
     * the given Attributes. This throws if the value is not single-valued.
     */
    public static String getSingleValue(Attributes attrs, String attrName)
        throws GeneralException, NamingException {
    
        String value = null;
    
        Attribute attribute = attrs.get(attrName);
        if (null != attribute) {
            if (attribute.size() > 1) {
                throw new GeneralException("Expected a single value.");
            }
            else if (attribute.size() == 1) {
                value = (String) attribute.get();
            }
        }
    
        return value;
    }

    /**
     * Load the given directory object by its DN and return a single string
     * value for the attribute with the given name. Throws if the value is not
     * single-valued.
     */
    public static String getAttributeValue(DirContext ctx, String dn, String attr)
        throws GeneralException {
    
        String value = null;
    
        String[] attrs = new String[] { attr };
    
        try {
            Name name = new CompositeName().add(dn);
            Attributes ldapAttrs = ctx.getAttributes(name, attrs);
            
            value = getSingleValue(ldapAttrs, attr);
        }
        catch (InvalidNameException ine) {
            throw new GeneralException(ine);
        }
        catch (NamingException ne) {
            throw new GeneralException(ne);
        }
    
        return value;
    }

    /**
     * Return the DN of the object in the given directory that holds the schema
     * definition.
     */
    public static String getSchemaDN(DirContext ctx)
        throws GeneralException, NamingException {
        
        String schemaDN = null;
        Attributes rootDSE = getRootDSE(ctx);
        if (null != rootDSE) {
            schemaDN = getSingleValue(rootDSE, "subschemaSubentry");
        }
    
        return (null != schemaDN) ? schemaDN : "cn=schema";
    }
    
    /**
     * Return the root DSE from the directory.
     */
    public static Attributes getRootDSE(DirContext ctx) throws NamingException {
        
        Attributes attrs = null;
    
        SearchControls ctrls = new SearchControls();
        ctrls.setSearchScope(SearchControls.OBJECT_SCOPE);
        NamingEnumeration<SearchResult> results =
            ctx.search("", "(objectClass=*)", ctrls);
        
        if ((null != results) && results.hasMore()) {
            SearchResult result = results.next();
            attrs = result.getAttributes();
        }
    
        return attrs;
    }

    /**
     * Return the Schema for the given directory.
     */
    public static Schema getSchema(DirContext ctx)
        throws GeneralException, NamingException {
        
        Schema schema = null;

        String schemaDN = getSchemaDN(ctx);

        SearchControls ctrls = new SearchControls();
        ctrls.setSearchScope(SearchControls.OBJECT_SCOPE);
        ctrls.setReturningAttributes(new String[] { "objectClasses", "attributeTypes" } );

        NamingEnumeration<SearchResult> results =
            ctx.search(schemaDN, "(objectClass=*)", ctrls);

        if ((null != results) && results.hasMore()) {
            SearchResult result = results.next();

            Attributes ldapAttrs = result.getAttributes();
            Attribute ocAttr = ldapAttrs.get("objectClasses");
            Attribute attrTypesAttr = ldapAttrs.get("attributeTypes");
            List<String> objectClasses = getAllAttributeValues(ocAttr);
            List<String> attrDefs = getAllAttributeValues(attrTypesAttr);
            
            schema = new Schema(objectClasses, attrDefs);
        }

        return schema;
    }

    /**
     * Return a List of string values from the given attribute.
     * @param attr 
     * @throws NamingException
     */
    public static List<String> getAllAttributeValues(Attribute attr)
        throws NamingException {
        
        List<String> vals = null;
        
        if (null != attr) {
            vals = new ArrayList<String>();
            NamingEnumeration<?> ne = attr.getAll();
            while (ne.hasMore()) {
                vals.add((String) ne.next());
            }
        }

        return vals;
    }

    /**
     * Parse some content out of an RFC 2252 schema definition string.
     * 
     * @param  str          The RFC 2252 schema definition string.
     * @param  attrType     The type of attribute (for example - NAME).
     * @param  startDelim   The start delimiter of the value (for example - '(').
     * @param  endDelim     The end delimiter of the value (for example - ')').
     * 
     * @return The parsed content.
     * 
     * @throws GeneralException If the string is malformed.
     */
    private static String parseSchemaContent(String str, String attrType,
                                             char startDelim, char endDelim)
        throws GeneralException {

        String content = null;

        int attrIdx = str.indexOf(attrType);
        if (attrIdx > -1) {
            int startIdx = str.indexOf(startDelim, attrIdx);

            if (startIdx < 0) {
                throw new GeneralException("Could not find start delimiter for type '" +
                                           attrType + "': " + str);
            }

            startIdx++;
            int endIdx = str.indexOf(endDelim, startIdx);

            if (endIdx < 0) {
                throw new GeneralException("Could not find end delimiter for type '" +
                                           attrType + "': " + str);
            }

            content = str.substring(startIdx, endIdx);
        }

        return content;
    }

    /**
     * Return the value of a quoted attribute in an RFC 2252 schema definition
     * string.
     */
    static String getRFC2252QuotedAttribute(String str, String attrName)
        throws GeneralException {

        return parseSchemaContent(str, attrName, '\'', '\'');
    }

    /**
     * Return the value of an unquoted string attribute in an RFC 2252 schema
     * definition string.
     */
    static String getRFC2252StringAttribute(String str, String attrName)
        throws GeneralException {
    
        return parseSchemaContent(str, attrName, ' ', ' ');
    }

    /**
     * Return the a list of the OIDs for the requested attribute in an RFC 2252
     * schema definition string.
     */
    static List<String> getRFC2252OidOrOidList(String str, String attrType)
        throws GeneralException {
        
        List<String> oids = new ArrayList<String>();

        int attrIdx = str.indexOf(attrType);
        if (attrIdx > -1) {

            boolean isList = false;
            int currentIdx = attrIdx + attrType.length();

            // Look for the next non-whitespace character.  If it is an open
            // paren, we're dealing with a list.  Otherwise we have a single
            // value.
            while (currentIdx < str.length()) {
                char c = str.charAt(currentIdx);
                if (' ' != c) {
                    isList = ('(' == c);
                    break;
                }
                currentIdx++;
            }
            
            // If we have a list, grab the content out of the parens and split
            // the OIDs out from the $ separator.
            if (isList) {
                String content = parseSchemaContent(str, attrType, '(', ')');
                String[] parts = content.split("\\$");
                if ((null != parts) && (parts.length > 0)) {
                    for (String part : parts) {
                        oids.add(part.trim());
                    }
                }
            }
            else {
                // We have a single value, just grab it.
                String value = getRFC2252StringAttribute(str, attrType);
                oids.add(value);
            }
        }

        return oids;
    }

    /**
     * Escape the given value to be used in a search string according to RFC
     * 2254 escaping rules.
     * 
     * @param  val  The value to escape.
     * 
     * @return An RFC 2254 escaped version of the given value if it is a String,
     *         or just the given value if it is not a String.
     */
    public static Object escapeLDAPSearchStringValue(Object val)
    {
        if (val instanceof String)
        {
            StringBuilder sb = new StringBuilder((String) val);
            for (int i=0; i<sb.length(); i++)
            {
                char c = sb.charAt(i);
                switch(c)
                {
                case '*':
                    i = replace(sb, i, "\\2a");
                    break;
                case '(':
                    i = replace(sb, i, "\\28");
                    break;
                case ')':
                    i = replace(sb, i, "\\29");
                    break;
                case '\\':
                    i = replace(sb, i, "\\5c");
                    break;
                }
            }
            return sb.toString();
        }
        return val;
    }

    /**
     * Replace the character at the given position in the StringBuilder with the
     * given replacement string and return the index of the last character in
     * the replacement in the StringBuilder.
     * 
     * @param  sb           The StringBuilder to modify.
     * @param  idx          The index of the character to replace.
     * @param  replacement  The replacement String.
     * 
     * @return The index of the last character in the replacement in the
     *         StringBuilder.
     */
    private static int replace(StringBuilder sb, int idx, String replacement)
    {
        sb.deleteCharAt(idx);
        sb.insert(idx, replacement);
        return idx + replacement.length();
    }
}
