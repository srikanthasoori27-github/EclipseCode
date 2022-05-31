/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.scim;

import java.util.ArrayList;
import java.util.List;

import sailpoint.scim.mapping.AttributeMapping;
import sailpoint.scim.mapping.SchemaMapping;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Defines property mappings for containing attributes of scim schema.
 * 
 * @author danny.feng
 *
 */
@SuppressWarnings("serial")
@XMLClass
public class SchemaPropertyMapping extends AbstractXmlObject implements SchemaMapping {

    /**
     * The urn of the scim schema.
     */
    private String urn;
    
    /**
     * Containing attribute property mappings. 
     */
    private List<AttributeMapping> attributeMappingList;

    
    public SchemaPropertyMapping() {
    }
    
    @XMLProperty
    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    @XMLProperty(mode = SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<AttributeMapping> getAttributeMappingList() {
        return attributeMappingList;
    }

    public void setAttributeMappingList(List<AttributeMapping> attributeMappingList) {
        this.attributeMappingList = attributeMappingList;
    }  
    
    /**
     * Appends additional AttributePropertyMappings to containing list.
     * It is used for appending property mappings of extended attributes. 
     * 
     * @param list The additional attribute property mappings to append.
     */
    public void addAttributePropertyMappings(List<AttributeMapping> list) {
        if (attributeMappingList == null) {
            attributeMappingList = new ArrayList<AttributeMapping>();
        }
        attributeMappingList.addAll(list);
    }
    
    /**
     * Retrieves the AttributePropertyMapping by name.
     * 
     * @param name The name of target AttributePropertyMapping.
     * @return the target AttributePropertyMapping
     */
    public AttributeMapping getAttributePropertyMapping(String name) {
        for (AttributeMapping mapping : Util.safeIterable(attributeMappingList)) {
            if (Util.nullSafeEq(mapping.getName(), name)) {
                return mapping;
            }
        }
        return null;
    }
}
