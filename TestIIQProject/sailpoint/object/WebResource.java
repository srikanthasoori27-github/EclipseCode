/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Defines what is required to view a given url as well as methods to validate
 * access.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
@XMLClass
public class WebResource extends AbstractXmlObject implements ImportMergable {

    /**
     * The url for this resource. Might be a full url, or a wildcard
     * such as /define/identity/*.
     */
    private String url;

    /**
     * Rights required to access this resource. Only one matching right
     * is required for access.
     * <p/>
     * If the rights=="*" all users are given access.
     * <p/>
     * If the value is empty and there are no other properties on this
     * resource that grant access (for example, enablingAttributes), the resource will
     * only be accessible by sys admins.
     */
    private String rights;

    /**
     * Identity Attributes which grant access to this resource. Key is the
     * attribute name. Only one attribute is required for a match.
     */
    private Map<String, Object> enablingAttributes;


    /**
     * List of urls which require this resource to be accessible.
     * For example, if the user has access to /define/applications.jsf
     * they should always be given access to /define/index.jsf. In that case
     * /define/applications.jsf is a dependantResource of /define/index.jsf;
     */
    private List<String> childResources;


    /**
     * Default constructor
     */
    public WebResource() {
    }

    /**
     * Returns true if the identity has capabilities, rights or attributes
     * which give them access to this resource. If the user has the
     * SystemAdministrator capability or if this.allowAll() is true,
     * access is always granted.
     *
     * @param identity Identity whose access is being checked.
     * @return True if the identity is allowed to access this resource,
     */
    public boolean hasAccess(Identity identity) {
        if (identity == null)
            return false;

        return hasAccess(identity.getCapabilityManager().getEffectiveCapabilities(), identity.getCapabilityManager().getEffectiveFlattenedRights(),
                identity.getAttributes());
    }

    /**
     * Returns true if the given capabilities, rights or attributes grant access
     * to this resource.
     *
     * @param identityCaps       List of capabilities to check
     * @param identityRights     List of right names which should be checked
     * @param identityAttributes List of identity attributes to check
     * @return true if the rights or attributes grant access to this resource.
     */
    public boolean hasAccess(List<Capability> identityCaps, Collection<String> identityRights,
                             Map<String, Object> identityAttributes) {

        return (Capability.hasSystemAdministrator(identityCaps)
                || allowAll()
                || hasEnablingRight(identityRights)
                || hasEnablingAttribute(identityAttributes));
    }

    /**
     * Returns true if all users have access to this resource.
     *
     * @return true if rights == "*"
     */
    private boolean allowAll() {
        return "*".equals(rights);
    }

    /**
     * Returns True if the collection of userRights contain at least
     * one right required for accessing this resource.
     *
     * @param userRights Collection of user rights
     * @return True if the collection of userRights contain at least
     *         one right required for accessing this resource.
     */
    private boolean hasEnablingRight(Collection<String> userRights) {

        if (userRights == null || rights == null)
            return false;

        List<String> enablingRights = getRightsList();
        for (String right : userRights) {
            if (enablingRights.contains(right))
                return true;
        }

        return false;
    }

    /**
     * Returns true if the resource's enablingAttributes map contains
     * the an entry whose key and value match the given attribute name
     * and value.
     *
     * @param attributeName  The attribute name to lookup
     * @param attributeValue The attribute's value
     * @return True if the resource contains a matching attribute value
     */
    private boolean hasEnablingAttribute(String attributeName, Object attributeValue) {
        if (enablingAttributes == null || enablingAttributes.isEmpty() ||
                !enablingAttributes.containsKey(attributeName))
            return false;

        Object resourceAttrValue = enablingAttributes.get(attributeName);

        if (resourceAttrValue == null)
            return attributeValue == null;

        return resourceAttrValue.equals(attributeValue);
    }

    /**
     * Returns true if the resource's enablingAttributes map contains
     * AT LEAST ONE matching attribute from the given list
     * of identity attributes.
     *
     * @param identityAttributes Identity's attributes
     * @return True if the resource contains a matching attribute value from
     *         the given list of identity attributes.
     */
    private boolean hasEnablingAttribute(Map<String, Object> identityAttributes) {

        if (identityAttributes == null || enablingAttributes == null)
            return false;

        for (String attr : identityAttributes.keySet()) {
            if (hasEnablingAttribute(attr, identityAttributes.get(attr)))
                return true;
        }

        return false;
    }

    @XMLProperty
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @XMLProperty(mode = SerializationMode.LIST)
    public List<String> getChildResources() {
        return childResources;
    }

    public void setChildResources(List<String> childResources) {
        this.childResources = childResources;
    }    

    @XMLProperty
    public String getRights() {
        return rights;
    }

    public void setRights(String rights) {
        this.rights = rights;
    }

    public List<String> getRightsList() {
        if (rights != null)
            return Util.csvToList(rights);

        return null;
    }

    @XMLProperty(mode = SerializationMode.ELEMENT)
    public Map<String, Object> getEnablingAttributes() {
        return enablingAttributes;
    }

    public void setEnablingAttributes(Map<String, Object> enablingAttributes) {
        this.enablingAttributes = enablingAttributes;
    }

    /**
     * Return true if the urls match.
     */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WebResource that = (WebResource) o;

        if (url != null ? !url.equals(that.url) : that.url != null) return false;

        return true;
    }

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }
    
    /* When merging WebResource's we merge child resources by taking the parameter obj list of resources
     * then adding the current list of child resources to the end of the list.  Similarly, we also take the 
     * list of Rights as well as EnablingAttributes and do the same.  NOTE: when merging rights, special handling
     * applies to allow all (*).  Merging will allow for more restrictive rights, however, you can not merge from
     * more restrictive rights to allow all (*) rights. i.e. *->FullAccessProvisioning merges however 
     * FullAccessProvisioning->* will retain the right FullAccessProvisioning.
     * @see sailpoint.object.ImportMergable#merge(java.lang.Object)
     */
    @Override
    public void merge(Object obj) {
        if (obj instanceof WebResource) {
            WebResource old = (WebResource)obj;
            mergeChildResources(old);
            mergeRights(old);
            mergeEnablingAttributes(old);
        }
    }

    private void mergeChildResources(WebResource obj) {
        if (obj != null) { 
            List<String> oldChildren = obj.getChildResources();
        
            if (!Util.isEmpty(oldChildren)) {
                // make sure we use linked hash set for ordering purposes
                Set<String> mergedResources = new LinkedHashSet<String>();
                mergedResources.addAll(oldChildren);
                
                if (!Util.isEmpty(childResources)) {
                    mergedResources.addAll(childResources);
                }
                
                this.setChildResources(new ArrayList<String>(mergedResources));
            }
        }
    }
    
    private void mergeRights(WebResource obj) {
        if (obj != null) {
            List<String> oldRights = obj.getRightsList();
        
            if (!Util.isEmpty(oldRights)) {
                // make sure we use linked hash set for ordering purposes
                Set<String> mergedRights = new LinkedHashSet<String>();
                mergedRights.addAll(oldRights);

                // merge new rights ignoring when you're trying to make rights
                // less restrictive using *
                if (!Util.isEmpty(this.getRightsList()) && !this.allowAll()) {
                    //replace rights if oldRights were allowAll
                    if (obj.allowAll()) {
                        mergedRights.clear();
                    }
                    mergedRights.addAll(this.getRightsList());
                }

                this.setRights(Util.setToCsv(mergedRights));
            }
        }
    }
    
    private void mergeEnablingAttributes(WebResource obj) {
        if (obj != null) {
            Map<String,Object> oldEnablers = obj.getEnablingAttributes();
            
            if (!Util.isEmpty(oldEnablers)) {
                Map<String, Object> mergedEnablers = new HashMap<String, Object>();
                mergedEnablers.putAll(oldEnablers);
                
                if (!Util.isEmpty(this.getEnablingAttributes())) {
                    mergedEnablers.putAll(this.getEnablingAttributes());
                }
                
                this.setEnablingAttributes(mergedEnablers);
            }
        }
    }
}
