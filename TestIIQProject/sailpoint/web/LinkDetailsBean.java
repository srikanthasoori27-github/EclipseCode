/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeSource;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.LinkInterface;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.Schema;
import sailpoint.object.UIPreferences;
import sailpoint.service.certification.CertificationItemService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * UI Bean exposing link properties, though it may be backed by anything
 * implementing LinkInterface.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class LinkDetailsBean extends BaseBean implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 8029429047259982567L;

    private static Log log = LogFactory.getLog(LinkDetailsBean.class);

    // Link ID passed in as request parameters
    private String linkId;

    // CertificationItem ID passed in as a request param. This is
    // used in certifications where we want to display data from a LinkSnapshot
    private String certificationItemId;

    // The actual object backing this bean
    private LinkInterface link;

    // if true, don't display entitlement attributes. This is a request param
    private boolean showNonEntitlementAttrsOnly = false;

    // If true, entitlement descriptions will be displayed in place of entitlement
    // names. This is both a user pref and a certification attribute, so both places
    // must be checked when in a certification
    private Boolean displayEntitlementDescription;

    // cache of calculated attribute list, including descriptions. Structure is:
    // Attribute Name->Attribute Value->Value Object(see createValueMap)
    private List<LinkAttributeBean> formattedAttributes;

    // the certification item containing the link we're displaying.
    // This will be null on non-cert pages.
    private CertificationItem certItem;

    // This link's application
    private Application application;

    private String refererType;
    private String refererId;

    public LinkDetailsBean(String linkId) {
        super();
        this.linkId = linkId;
    }

    public LinkDetailsBean() {

        super();

        if (getRequestParameter("id") != null) {
            linkId = getRequestParameter("id");
        } else {
            certificationItemId = getRequestParameter("certItem");
        }

        if (getRequestParam().containsKey("nonEntitlements"))
            showNonEntitlementAttrsOnly = Boolean.parseBoolean(getRequestParameter("nonEntitlements"));

        if (getRequestParam().containsKey("showDesc"))
            displayEntitlementDescription = Boolean.parseBoolean(getRequestParameter("showDesc"));

        if (!Util.isNothing(getRequestParameter("identityId"))) {
            this.refererId = getRequestParameter("identityId");
            this.refererType = "identity";
        } else {
            this.refererType = getRequestParameter("refererType");
            this.refererId = getRequestParameter("refererId");
        }
    }

    /**
     * Returns a LinkInterface impl, whether it be an actual link, or a LinkSnapshot
     * @return
     * @throws GeneralException
     */
    public LinkInterface getObject() throws GeneralException {

        if (link != null)
            return link;

        if (linkId != null){
            link = getContext().getObjectById(Link.class, linkId);
        } else if (certificationItemId != null) {
            certItem = getContext().getObjectById(CertificationItem.class, certificationItemId);
            if (certItem != null){
                this.link = new CertificationItemService(this).getLink(certItem);
            }
        }

        return link;
    }

    public String getRefererId() {
        return this.refererId;
    }

    public String getRefererType() {
        return this.refererType;
    }

    /**
     * Determine how to display entitlements to users. See comments on
     * property displayEntitlementDescription
     * @throws GeneralException
     */
    public boolean isDisplayEntitlementDescription() throws GeneralException{

        if (displayEntitlementDescription != null)
            return displayEntitlementDescription;

        Identity user = getLoggedInUser();
        Object pref = user.getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC);
        if (null != pref) {
            displayEntitlementDescription = Util.otob(pref);
        } else if (this.certItem != null && certItem.getCertification().getDisplayEntitlementDescription() != null){
            displayEntitlementDescription = certItem.getCertification().getDisplayEntitlementDescription();
        } else {
            displayEntitlementDescription = Configuration.getSystemConfig().getBoolean(Configuration.DISPLAY_ENTITLEMENT_DESC);
        }

        return displayEntitlementDescription;
    }

    public String getApplicationName() throws GeneralException {
        return getObject() != null ? getObject().getApplicationName() : null;
    }

    public boolean isShowNonEntitlementAttributesOnly() {
        return this.showNonEntitlementAttrsOnly;
    }

    public List<LinkAttributeBean> getFormattedAttributes() throws GeneralException {

        if (formattedAttributes != null)
            return formattedAttributes;

        formattedAttributes = new ArrayList<LinkAttributeBean>();
        Attributes<String, Object> attrs = (getObject() != null) ? getObject().getAttributes() : null;
        if ( Util.isEmpty(attrs) )
            return formattedAttributes;

        boolean showDescriptions = this.isDisplayEntitlementDescription();

        //
        // First add all of the extended attributes to the top of this
        // list. Resolve display names
        //
        Map<String,String> extendedAttributes = getExtendedAttributes();
        if ( !Util.isEmpty(extendedAttributes) ) {
            // Keys are localized display names
            // The entries value is either the sourcedAttributename if directly mapped
            // OR the extended attribute name if sourced by a rule.
            Collection<String> keySet = extendedAttributes.keySet();
            if ( Util.size(keySet) > 0 ) {
                List<String> keys = new ArrayList<String>(keySet);
                // sort by display name
                Collections.sort(keys);
                for (String displayName : keys ) {
                    String attrName = extendedAttributes.get(displayName);
                    if (!isEntitlement(attrName) || !showNonEntitlementAttrsOnly) {
                        Object attrVal = attrs.get(attrName);
                        List<Map<String,Object>> values = formatAttribute(attrName, attrVal);
                        if ( values != null )
                            formattedAttributes.add(new LinkAttributeBean(attrName, displayName, values, showDescriptions));
                    }
                }
            }
        }
        // Mark the last extended attribute for the view
        if (formattedAttributes.size() > 0)
            formattedAttributes.get(formattedAttributes.size()-1).setLastExtended();

        //
        // Second, add in the non-extended, non-directly-mapped Link attributes
        //
        List<String> attrNamesWithoutExtended = filterLinkAttributes(attrs.keySet());
        if ( Util.size(attrNamesWithoutExtended) > 0 ) {
            for (String attrName : attrNamesWithoutExtended ) {
                if ( !isEntitlement(attrName) || !showNonEntitlementAttrsOnly ) {
                    Object attrVal = attrs.get(attrName);
                    List<Map<String,Object>> values = formatAttribute(attrName, attrVal);
                    if ( values != null )
                        formattedAttributes.add(new LinkAttributeBean(attrName, attrName, values, showDescriptions));
                }
            }
        }
        return formattedAttributes;
    }

    /**
     * Build a sorted list of attributes names that aren't extended attributes
     * and also aren't a source for an extended attribute.
     */
    private List<String> filterLinkAttributes(Collection<String> attrs)
        throws GeneralException {

        List<String> toFilter = new ArrayList<String>();
        ObjectConfig linkConfig = Link.getObjectConfig();
        if ( linkConfig != null ) {
            List<ObjectAttribute> linkExtendedAttributes = linkConfig.getObjectAttributes();
            if ( Util.size(linkExtendedAttributes) > 0 ) {
                for ( ObjectAttribute attr : linkExtendedAttributes ) {
                    // filter out any extended link attributes AND also any
                    // attributes that are mapped directly to extended attributes
                    toFilter.add(attr.getName());
                    String sourcedBy = attr.sourcedBy(getApplication());
                    // pjeong: dont filter out the editable ones. we want to show both because they may be different
                    if (!attr.isEditable() && Util.getString(sourcedBy) != null ) {
                        toFilter.add(sourcedBy);
                    }
                }
            }
        }
        List<String> names = new ArrayList<String>();
        if ( Util.size(attrs) > 0 ) {
            for ( String attrName : attrs ) {
                if ( attrName != null && !toFilter.contains(attrName) ) {
                    names.add(attrName);
                }
            }
            if ( names != null)
                Collections.sort(names);
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> formatAttribute(String attrName, Object attrVal)
        throws GeneralException {

        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        if ( attrVal != null ) {
            if ( Collection.class.isAssignableFrom(attrVal.getClass())) {
                for (Iterator iterator = ((Collection) attrVal).iterator(); iterator.hasNext();) {
                    Object val = iterator.next();
                    Map<String, Object> valueMap = createValueMap(attrName, val);

                    //Need to strip out permissions and look up their description
                    if(val instanceof Permission){
                        String description = getPermissionDescription((Permission)val);
                        if (description != null) {
                            valueMap.put("description", description);
                        }
                    }

                    values.add(valueMap);
                }
            } else {
                values.add(createValueMap(attrName, attrVal));
            }
        }
        return ( values.size() > 0 ) ? values : null;
    }

    /**
     * Build a map of the extended attributes.
     * This map is key'ed by the displayName of the attribute and stores either the
     * source attribute ( for directly mapped attributes ) OR the name of the extended
     * attribute if the attribute is sourced by a rule.
     */
    public Map<String,String> getExtendedAttributes() throws GeneralException {
        Map<String,String> attrMap = new HashMap<String,String>();
        List<ObjectAttribute> linkExtendedAttributes = Link.getObjectConfig().getObjectAttributes();
        if ( Util.size(linkExtendedAttributes) > 0 ) {
            for ( ObjectAttribute linkAttribute : linkExtendedAttributes ) {
                String displayName = linkAttribute.getDisplayableName();
                String attributeName = linkAttribute.getName();
                String mappedTo = null;
                AttributeSource source = linkAttribute.getSource(getApplication());
                if (source != null && source.getRule() == null) {
                    mappedTo = source.getName();
                }
                Message msg = new Message(MessageKeys.MSG_PLAIN_TEXT,displayName);
                if (!linkAttribute.isEditable() && !Util.isNullOrEmpty(mappedTo)) {
                    attrMap.put(msg.getLocalizedMessage(getLocale(), getUserTimeZone()), mappedTo);
                } else {
                    attrMap.put(msg.getLocalizedMessage(getLocale(), getUserTimeZone()), attributeName);
                }
            }
        }
        return attrMap;
    }

    private Application getApplication() throws GeneralException{
        if (application == null && getObject() != null) {
            application = getContext().getObjectByName(Application.class, getObject().getApplicationName());
        }
        return application;
    }

    /**
     * @param attribute
     * @return true if the given attribute is an entitlement attribute
     * @throws GeneralException
     */
    private boolean isEntitlement(String attribute) throws GeneralException {
        Application app = getApplication();
        if (app == null)
            return false;
        Schema schema = app.getSchema(Application.SCHEMA_ACCOUNT);
        if (schema == null)
            return false;
        AttributeDefinition def = schema.getAttributeDefinition(attribute);
        return def != null && def.isEntitlement();
    }

    /**
     * Creates a map with the attribute value, 'preferred' entitlement display value and the secondary
     * entitlement display value. The way this works is that user has the option of viewing
     * either the entitlement desciption or entitlement value. This logic looks at that preference and
     * puts the right value in the right place. Using a map here makes the jsf template much simpler.
     *
     * @param attributeName
     * @param value
     * @return
     * @throws GeneralException
     */
    private Map<String, Object> createValueMap(String attributeName, Object value) throws GeneralException {

        HashMap<String, Object> map = new HashMap<String, Object>();

        Object formattedVal = value;
        if (value != null && value instanceof Date)
            formattedVal = Internationalizer.getLocalizedDate((Date) value, getLocale(), getUserTimeZone());
        map.put("value", formattedVal);
        map.put("description", getDescription(attributeName, value));

        return map;
    }

    /**
     * Gets entitlement description for a given attribute and value.
     * @param attrName
     * @param attrValue
     * @return
     * @throws GeneralException
     */
    private String getDescription(String attrName, Object attrValue) throws GeneralException {

        if (attrValue == null || !(attrValue instanceof String))
            return null;

         // Bug #24022 - Don't go fetching descriptions for attributes that aren't even ManagedAttributes
         String description = null;
         
         Schema schema = getApplication().getSchema(Application.SCHEMA_ACCOUNT);
         if (schema != null) {
             AttributeDefinition def = schema.getAttributeDefinition(attrName);
             if (def != null && def.isManaged()) {
                 description = Explanator.getDescription(getApplication(), attrName, (String)attrValue, getLocale());
             }
         }
         
         return description;
    }

    private String getPermissionDescription(Permission permission) {
        String description = null;
        if(permission!=null && permission.getTarget()!=null) {
            try {
                description = Explanator.getPermissionDescription(getApplication(), permission.getTarget(), getLocale());
            }catch(GeneralException ge) {
                log.warn("Unable to fetch description for target: " + permission.getTarget());
                log.warn("Exception: " + ge.getMessage());
            }
        }
        return description;
    }

    public String getItemId() {
        if(linkId!=null)
            return linkId;
        else
            return certificationItemId;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Bean to hold each attributes name, displayname and values
    //
    ///////////////////////////////////////////////////////////////////////////

    public static class LinkAttributeBean {

        private String displayName;
        private String attrName;
        private List<Map<String, Object>> values;

        private boolean lastExtended = false;

        public LinkAttributeBean() {
        }

        public LinkAttributeBean(String attrName, String displayName,
                                 List<Map<String, Object>> valueMaps,
                                 final boolean showDescriptions) {
            this.attrName = attrName;
            this.displayName = displayName;
            this.values = valueMaps;

            Collections.sort(this.values, new Comparator<Map<String,Object>>() {

                public int compare(Map<String,Object> o1, Map<String,Object> o2) {
                    // Sort by what will be displayed.
                    String val1 = getDisplayedValue(o1, showDescriptions);
                    String val2 = getDisplayedValue(o2, showDescriptions);
                    int response = 0;
                    if( val1 != val2 ) {
                        if( val1 == null ) {
                            response = -1;
                        } else if( val2 == null ){
                            response = 1;
                        } else {
                            response = val1.compareToIgnoreCase( val2 );
                        }
                    }
                    return response;
                }

                private String getDisplayedValue(Map<String,Object> valueMap,
                                                 boolean showDescriptions) {
                    String val = null;
                    Object valObject = valueMap.get("value");
                    if (valObject instanceof Permission) {
                        val = ((Permission) valObject).getTarget();
                    }
                    else if (null != valObject) {
                        val = valObject.toString();
                    }

                    if (showDescriptions) {
                        String desc = (String) valueMap.get("description");
                        if (null != Util.getString(desc)) {
                            val = desc;
                        }
                    }
                    return val;
                }
            });
        }

        public String getName() {
            return attrName;
        }
        public void setName(String attrName) {
            this.attrName = attrName;
        }

        public List<Map<String, Object>> getValues() {
            return values;
        }
        public void setValues(List<Map<String, Object>> values) {
            this.values = values;
        }

        public String getDisplayName() {
            return displayName;
        }
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        // Used to mark the last extended attribute in the view
        public boolean isLastExtended() {
            return lastExtended;
        }

        public void setLastExtended() {
            lastExtended = true;
        }
        
        // Whether or not the property begins with 'IIQ'
        public boolean isInternal() {
        	return displayName != null && displayName.startsWith("IIQ");
        }
    }
}
