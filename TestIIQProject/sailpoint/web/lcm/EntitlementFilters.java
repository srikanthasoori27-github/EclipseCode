/**
 * (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.lcm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import sailpoint.tools.GeneralException;

/**
 * This class is a data structure to hold the entitlement filters for deep link to checkout 
 *  summary view.
 * This holds filters to uniquely identify an entitlement from combination of following filters
 *  {application, value, attribute, type}
 * 
 * @author ketan.avalaskar
 *
 */
public class EntitlementFilters {

    /**
     * This class holds properties to uniquely identify an entitlement
     */
    public static class Filter {
        private String application;
        private String value;
        private String attribute;
        private String type;

        public String getApplication() {
            return application;
        }

        protected void setApplication(String application) {
            this.application = application;
        }

        public String getValue() {
            return value;
        }

        protected void setValue(String value) {
            this.value = value;
        }

        public String getAttribute() {
            return attribute;
        }

        protected void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        public String getType() {
            return type;
        }

        protected void setType(String type) {
            this.type = type;
        }
    }

    private Map<Integer, Filter> filters = new HashMap<Integer, Filter>();

    private Filter getOrCreateFilter(int idx) {
        Filter f = this.filters.get(idx);
        if (null == f) {
            f = new Filter();
            this.filters.put(idx, f);
        }
        return f;
    }

    /**
     * Returns a Collection of Filter values.
     * Values are filter properties to identify an entitlement.
     * @return: a collection of entitlement filter property values
     */
    public Collection<Filter> getFilters() {
        return this.filters.values();
    }

    private void checkNull(String value, String attrName, int index) throws GeneralException {
        if (null != value) {
            throw new GeneralException("Found multiple values for " + attrName + index);
        }
    }

    /**
     * Sets a value of application in appropriate filter as per the index specified.
     * If filter at specified index is not present, then it is created first.
     * @param index: index of filter collection at which application should be stored
     * @param application: application name/id specified
     * @throws GeneralException: if application already set at this index
     */
    public void setApplication(int index, String application) throws GeneralException {
        Filter f = getOrCreateFilter(index);
        checkNull(f.application, "application", index);
        f.setApplication(application);
    }

    /**
     * Sets a value of an entielement in appropriate filter as per the index specified.
     * If filter at specified index is not present, then it is created first.
     * @param index: index of filter collection at which entitlement value should be stored
     * @param value: value of an entitlement
     * @throws GeneralException: if value already set at this index
     */
    public void setValue(int index, String value) throws GeneralException {
        Filter f = getOrCreateFilter(index);
        checkNull(f.value, "value", index);
        f.setValue(value);
    }

    /**
     * Sets an entielement attribute in appropriate filter as per the index specified.
     * If filter at specified index is not present, then it is created first.
     * @param index: index of filter collection at which entitlement attribute should be stored
     * @param attribute: entitlement attribute
     * @throws GeneralException: if attribute already set at this index
     */
    public void setAttribute(int index, String attribute) throws GeneralException {
        Filter f = getOrCreateFilter(index);
        checkNull(f.attribute, "attribute", index);
        f.setAttribute(attribute);
    }

    /**
     * Sets type of an entielement in appropriate filter as per the index specified.
     * If filter at specified index is not present, then it is created first.
     * @param index: index of filter collection at which entitlement type should be stored
     * @param type: type of an entitlement
     * @throws GeneralException: if type already set at this index
     */
    public void setType(int index, String type) throws GeneralException {
        Filter f = getOrCreateFilter(index);
        checkNull(f.type, "type", index);
        f.setType(type);
    }
}
