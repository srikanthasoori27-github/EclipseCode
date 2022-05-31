/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import sailpoint.object.Attributes;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb
 *
 */
public class EntitlementMiningIdentityBean extends BaseBean
{
    String identityId;
    String name;
    String displayName;
    String lastname;
    String firstname;
    
    /** This is the map that contains a link from the application that this identity is
     * part of, to the native identity and attributes of the aplication.  The key
     * to this map, the applicationID, returns a value which is a list of EntitlementMiningIdentityAttrBeans
     * These holds the nativeIdentity and attributes for that identity on the application**/
    Map<String, List<EntitlementMiningIdentityAttrBean>> attrMap;
    
    public static final Comparator<EntitlementMiningIdentityBean> COMPARATOR =
        new Comparator<EntitlementMiningIdentityBean>()
        {
            public int compare(EntitlementMiningIdentityBean a1, EntitlementMiningIdentityBean a2)
            {
                String a1Name = (a1.getDisplayName()==null ? a1.getName() : a1.getDisplayName());
                String a2Name = (a2.getDisplayName()==null ? a2.getName() : a2.getDisplayName());
                
                return a1Name.compareToIgnoreCase(a2Name);
            }
        };
    
    public static class EntitlementMiningIdentityAttrBean {
        
        String nativeIdentity;
        String accountName;
        Attributes attributes;
        
        public EntitlementMiningIdentityAttrBean() {
            
        }
        /**
         * @return the attributes
         */
        public Attributes getAttributes() {
            return attributes;
        }
        /**
         * @param attributes the attributes to set
         */
        public void setAttributes(Attributes attributes) {
            this.attributes = attributes;
        }
        /**
         * @return the nativeIdentity
         */
        public String getNativeIdentity() {
            return nativeIdentity;
        }
        /**
         * @param nativeIdentity the nativeIdentity to set
         */
        public void setNativeIdentity(String nativeIdentity) {
            this.nativeIdentity = nativeIdentity;
        }

        /**
         * @return the account display name
         */
        public String getAccountName() {
            return accountName;
        }

        /**
         * Set the account display name
         * @param accountName Account name
         */
        public void setAccountName(String accountName) {
            this.accountName = accountName;
        }

        /**
         * Check if the given attribute name and value is present in the bean
         * @param attributeName Name of the attribute
         * @param attributeValue Value of the attribute
         * @return True if present, otherwise false
         */
        public boolean hasEntitlement(String attributeName, String attributeValue) {
            if (this.attributes == null || !this.attributes.containsKey(attributeName)) {
                return false;
            }
            
            for (Object singleValue : Util.iterate(Util.asList(this.attributes.get(attributeName)))) {
                if (Util.nullSafeEq(attributeValue, singleValue)) {
                    return true;
                }
            }
            
            return false;
        }
    }

    public EntitlementMiningIdentityBean () {

    }

    public EntitlementMiningIdentityBean(String identityId, String name, String displayName, String lastname, String firstname) {
        this.identityId = identityId;
        this.name = name;
        this.displayName = displayName;
        this.lastname = lastname;
        this.firstname = firstname;
    }
    /**
     * @return the attrMap
     */
    public Map<String, List<EntitlementMiningIdentityAttrBean>> getAttrMap() {
        return attrMap;
    }
    /**
     * @param attrMap the attrMap to set
     */
    public void setAttrMap(Map<String, List<EntitlementMiningIdentityAttrBean>> attrMap) {
        this.attrMap = attrMap;
    }
    /**
     * @return the identityId
     */
    public String getIdentityId() {
        return identityId;
    }
    /**
     * @param identityId the identityId to set
     */
    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getAccountNames(String applicationId, String attributeName, String attributeValue) {
        List<EntitlementMiningIdentityAttrBean> attrBeans = Collections.emptyList();
        List<String> accountNames = new ArrayList<>();
        if (this.attrMap != null) {
            attrBeans = this.attrMap.get(applicationId);
        }
        for (EntitlementMiningIdentityBean.EntitlementMiningIdentityAttrBean attrBean : Util.iterate(attrBeans)) {
            if (attrBean.hasEntitlement(attributeName, attributeValue)) {
                accountNames.add(attrBean.getAccountName());
            }
        }

        Collections.sort(accountNames, String.CASE_INSENSITIVE_ORDER);
        return Util.listToCsv(accountNames);

    }

    @Override
    public boolean equals(Object o) {
        if (o == null || this.identityId == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (!(o instanceof EntitlementMiningIdentityBean)) {
            return false;
        }

        EntitlementMiningIdentityBean bean = (EntitlementMiningIdentityBean) o;

        return bean.identityId != null && bean.identityId.equals(this.identityId);
    }
}