/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * BaseAttributeConfigBean
 * 
 * Created October 25, 2006
 */
package sailpoint.web.identity;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.util.WebUtil;

/**
 * This is a backing bean for the Identity Configuration page
 * 
 * @author Bernie Margolis
 */
abstract public class BaseAttributeConfigBean<E extends SailPointObject>
    extends BaseObjectBean {

    private static final Log log = LogFactory.getLog(BaseAttributeConfigBean.class);
    public static final String ATT_IDENTITY_ATTRIBUTE_BEAN = "IdentityAttributeBean";
    
    /*
     * Map of IdentityAttributes keyed by attribute name
     */
    protected Map<String, IdentityAttributeBean> attributes;    
    protected String editedAttribute;
    protected List<String> configWarnings;
	
    public BaseAttributeConfigBean() throws GeneralException {
        super();
        editedAttribute = null;
        attributes = new HashMap<String, IdentityAttributeBean>();
    }
    
    public boolean isEmpty() {
    	return attributes.isEmpty();
    }
    
    @SuppressWarnings("unchecked")
    public List<IdentityAttributeBean> getAttributesAsList() {
        List<IdentityAttributeBean> attributeBeans = 
            new ArrayList<IdentityAttributeBean>();
                
        attributeBeans.addAll(attributes.values());
        
        // Sort the attributes according to the parameters specified by
        // Rico livegrid or ExtJS
        String property = null;
        WebUtil.SortOrder order = null;

        String extJSDirection = super.getRequestParameter("dir");
        String extJSProperty = super.getRequestParameter("sort");
        
        if (extJSDirection != null && extJSProperty != null) {
            // Apply the simplified ExtJS sorting when we can
            if (extJSDirection.equalsIgnoreCase("ASC")) {
                order = WebUtil.SortOrder.ascending;
            } else {
                order = WebUtil.SortOrder.descending;
            }
            
            property = extJSProperty;
        } else {
            // If we didn't get ExtJS parameters, fall back to the livegrid way
            Map<String, String> sortCols = getSortColumnMap();
            for (Map.Entry<String, String> entry : sortCols.entrySet()) {
                String direction = super.getRequestParameter(entry.getKey());
                if (null != direction) {
                    property = entry.getValue();
                    
                    if (direction.equalsIgnoreCase("ASC")) {
                        order = WebUtil.SortOrder.ascending;
                    } else {
                        order = WebUtil.SortOrder.descending;
                    }
                    
                    break;
                }            
            }
        }
        
        if (property == null) {
            property = "displayName";
            order = WebUtil.SortOrder.ascending;
        }
        
        attributeBeans = (List<IdentityAttributeBean>) WebUtil.sortObjectsByProperty(attributeBeans, IdentityAttributeBean.class, property, order, true, true);


        int limit = getResultLimit();
        int start = Util.atoi(getRequestParameter("start"));
        int end = start + limit;
        if (end > attributeBeans.size())
            end = attributeBeans.size();
        // Apply a limit if one is submitted
        List<IdentityAttributeBean> limitedAttributes = new ArrayList<IdentityAttributeBean>();
        limitedAttributes = attributeBeans.subList(start, end);
        return limitedAttributes;
    }

    /**
     * @return a JSON representation of a set of object attributes
     */
    public String getAttributesAsJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();
            jsonWriter.key("totalCount");
            jsonWriter.value(getAttributeCount());
            jsonWriter.key("attributes");
            jsonWriter.array();
            List<IdentityAttributeBean> attributes = getAttributesAsList();
            if (attributes != null) {
                for (IdentityAttributeBean attribute: attributes) {
                    jsonWriter.object();
                    jsonWriter.key("id");
                    jsonWriter.value(attribute.getName());
                    jsonWriter.key("name");
                    jsonWriter.value(attribute.getName());
                    jsonWriter.key("displayName");
                    String displayName = Internationalizer.getMessage(attribute.getDisplayName(), getLocale());
                    if ( displayName == null )
                        displayName = attribute.getDisplayName();
                    jsonWriter.value(displayName);
                    jsonWriter.key("primarySource");
                    jsonWriter.value(attribute.getPrimarySource());
                    jsonWriter.key("characteristics");
                    jsonWriter.value(attribute.getCharacteristics());
                    jsonWriter.key("standard");
                    jsonWriter.value(attribute.isStandard());
                    jsonWriter.key("groupFactoryNames");
                    jsonWriter.value(attribute.getGroupFactoryNames());
                    jsonWriter.endObject();
                }
            }
            jsonWriter.endArray();
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not output JSON for Identity Attributes", e);
        }

        return jsonString.toString();
    }
    
    public Map<String, IdentityAttributeBean> getIdentityAttributeMap() {
        return attributes;
    }
    
    public int getAttributeCount() {
        final int attributeCount;
        
        if (attributes == null)
            attributeCount = 0;
        else
            attributeCount = attributes.size();
        
        return attributeCount;
    }

    public String getEditedAttribute() {
        return editedAttribute;
    }

    public void setEditedAttribute(String editedAttribute) {
        this.editedAttribute = editedAttribute;
    }
    
    public String createNewAttributeAction() throws GeneralException {        
        return "createNewAttribute";
    }
        
    @SuppressWarnings("unchecked")
    public String editAttribute() {
        Map session = getSessionScope();
        session.put(ATT_IDENTITY_ATTRIBUTE_BEAN, attributes.get(getEditedAttribute()));
        return "edit";
    }
    
    @SuppressWarnings("unchecked")
    public String addAttribute() {
        Map session = getSessionScope();
        IdentityAttributeBean newAttribute = new IdentityAttributeBean();
        newAttribute.setNew(true);
        session.put(ATT_IDENTITY_ATTRIBUTE_BEAN, newAttribute);
        return "add";
    }
    
        
    public String getDefaultSortColumn() throws GeneralException {
        return "displayName";
    }
    
    private Map<String, String> getSortColumnMap() {
        HashMap<String, String> columnMap = new HashMap<String, String>();
        columnMap.put("displayName", "displayName");
        columnMap.put("primarySource", "primarySource");
        return columnMap;
    }

    abstract public String deleteSelectedAttrAction(); 


    /**
     * Has to be overloaded because getObject isn't always reliable.
     * Why not?? - jsl
     */
    public String getObjectConfigName() {
        return null;
    }

    /**
     * Ideally this could be down in BaseAttributeConfigBean but 
     * we've got sequencing problems, getObject() returns null.
     */
    public List<String> getConfigWarnings() throws Exception {
        if (configWarnings == null) {
            ObjectConfig config = (ObjectConfig)getObject();
            if (config == null) {
                // why the hell isn't this set yet!?
                String name = getObjectConfigName();
                if (name != null)
                    config = getContext().getObjectByName(ObjectConfig.class, name);
            }

            if (config != null) {
                List<Message> warnings = ExtendedAttributeUtil.validate(getContext(), config);
                if (warnings != null) {
                    List<String> strings = new ArrayList<String>();
                    for (Message warning : warnings)
                        strings.add(warning.getLocalizedMessage(getLocale(), getUserTimeZone()));
                    configWarnings = strings;
                }
            }
        }
        return configWarnings;
    }

}
