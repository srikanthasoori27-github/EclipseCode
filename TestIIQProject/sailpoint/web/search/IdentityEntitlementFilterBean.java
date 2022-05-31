/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.integration.JsonUtil;
import sailpoint.object.Filter;
import sailpoint.object.SearchItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseDTO;

/**
 * Helper DTO that encapsulates the filters that have been added
 * during an identity search from the slicer dicer.
 * 
 * Used by the SearchBean to enter entitlement filters. 
 * 
 * @author dan.smith@sailpoint.com
 *
 */
public class IdentityEntitlementFilterBean extends BaseDTO {

    private static final long serialVersionUID = 1L;
    private static final Log log = LogFactory.getLog(IdentityEntitlementFilterBean.class);

    /**
     * For delete the selected filter in the ui that needs to be deleted.
     */
    String selectedFilterId;
    
    /**
     * The accumulated list of filters that have been added to the search.
     */
    List<IdentityEntitlementFilter> filters;
    
    /**
     * The bean that instantiated this bean, most of the time
     * its the SearchBean.  We nned this so we can persists
     * the search to the session. 
     */
    BaseBean baseBean;
    
    /**
     * The currently defined filter.
     */
    IdentityEntitlementFilter current;
    
    /**
     * Key where this object is stored in the session, its 
     * here so that the caller can specify which key
     * is used to store this bean on the session.
     */
    String sessionKey;
    
    public IdentityEntitlementFilterBean(BaseBean baseBean, String sessionKey) {
       super();
       this.baseBean = baseBean;       
       if ( filters == null ) {
           filters = new ArrayList<IdentityEntitlementFilter>();
       }
       if ( current == null ) {
           current = new IdentityEntitlementFilter();
       }
       this.sessionKey = sessionKey;
        restoreFilters();
    }

    /**
     * Restores the filters present in the baseBean's SearchItem.
     */
    private void restoreFilters() {
        if (this.baseBean instanceof SearchBean) {
            this.addFilters(((SearchBean) this.baseBean).getSearchItem());
        }
    }
    
    public String addFilter() throws Exception {
        // FYI - 'current' is set through JSF bindings with hiddenInputs which are
        // in turn bound to DistinctRestSuggests.  When 'Add' is pressed this is
        // updated with the values from the suggests and made available here.
        if ( current != null ) {
            if (!Util.isAnyNullOrEmpty(
                    current.getApplication(), 
                    current.getAttributeName(), 
                    current.getAttributeValue())) {
                // only add if none of them are empty
                filters.add(new IdentityEntitlementFilter(current.getApplication(), current.getAttributeName(), current.getAttributeValue()));
                // save the state back to the session 
                baseBean.getSessionScope().put(sessionKey, this);
            }
        }
        return null;
    }

    /**
     * Pull out Filters from the SearchItem and add them to the List.
     *
     * @param item SearchItem to pull filters from
     */
    public void addFilters(SearchItem item) {
        if (item != null) {
            List<Filter> searchFilters = item.getFilters();
            if (searchFilters != null) {
                for (Filter f : searchFilters) {
                    this.addFilters(f);
                }
            }
        }
    }

    /**
     * Add identityEntitlment Filters
     *
     * @param f The Filter to add
     */
    public void addFilters(Filter f) {
        if (f instanceof Filter.LeafFilter) {
            Filter.CompositeFilter cf = ((Filter.LeafFilter) f).getCollectionCondition();
            if (cf != null) {
                List<Filter> children = cf.getChildren();
                if (children != null) {
                    for (Filter child : children) {
                        if (child instanceof Filter.CompositeFilter) {
                            this.addFilters(child); // Recursive
                        }
                    }
                }
            }
        }
        else if (f instanceof Filter.CompositeFilter) {
            // Strings for the attributes
            String app = "";
            String attribute = "";
            String value = "";

            List<Filter> children = ((Filter.CompositeFilter) f).getChildren();

            if (children != null) {
                // Loop through children and pull out attribute properties
                for (Filter child : children) {
                    if (child instanceof Filter.LeafFilter) {

                        Filter.LeafFilter c = (Filter.LeafFilter) child;

                        // If the filter still has a collection, run it through again
                        if (c.getCollectionCondition() != null) {
                            this.addFilters(child); // Recursive
                        }

                        // Otherwise pull out the values so we can create an IdentityEntitlementFilter
                        if (c.getProperty().equals("application.name")) {
                            app = (String) c.getValue();
                        }
                        else if (c.getProperty().equals("name")) {
                            attribute = (String) c.getValue();
                        }
                        else if (c.getProperty().equals("value")) {
                            value = (String) c.getValue();
                        }
                    }
                    // or run it through again if the complexity is too damn high!
                    else if (child instanceof Filter.CompositeFilter) {
                        this.addFilters(child); // Recursive
                    }
                }
            }

            // Only add if none of the attributes are empty
            // and this exact combo doesn't already exist in the filters list.
            if (!Util.isAnyNullOrEmpty(app, attribute, value) && !containsFilter(app, attribute, value)) {
                filters.add(new IdentityEntitlementFilter(app, attribute, value));
                // save the state back to the session
                baseBean.getSessionScope().put(sessionKey, this);
            }
        }
    }

    /**
     * Checks if the collective properties already make up an IdentityEntitlementFilter within 'filters'.
     *
     * @param app The application name
     * @param attr The attribute name
     * @param val The attribute value
     * @return true if the properties match an existing IdentityEntitlementFilter filter.
     */
    private boolean containsFilter(String app, String attr, String val) {
        if (filters != null) {
            for (IdentityEntitlementFilter f : filters) {
                if (Util.nullSafeEq(f.getApplication(), app) &&
                        Util.nullSafeEq(f.getAttributeName(), attr) &&
                        Util.nullSafeEq(f.getAttributeValue(), val)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public String removeFilter() {
        if (null != selectedFilterId)  {
            for (Iterator<IdentityEntitlementFilter> it=filters.iterator(); it.hasNext(); )
            {
                if (selectedFilterId.equals(it.next().getId()))
                {
                    it.remove();
                    break;
                }
            }
        }        
        selectedFilterId = null;        
        return null;
    }
    
    public String getSelectedFilterId() {
        return selectedFilterId;
    }

    public void setSelectedFilterId(String selectedFilterId) {
        this.selectedFilterId = selectedFilterId;
    }

    public List<IdentityEntitlementFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<IdentityEntitlementFilter> filters) {
        this.filters = filters;
    }

    public String reset() {
        current = new IdentityEntitlementFilter();
        filters = new ArrayList<IdentityEntitlementFilter>();
        return "";
    }
    
    public BaseBean getBaseBean() {
        return baseBean;
    }

    public void setBaseBean(BaseBean baseBean) {
        this.baseBean = baseBean;
    }

    public IdentityEntitlementFilter getCurrent() {
        return current;
    }

    public void setCurrent(IdentityEntitlementFilter current) {
        this.current = current;
    }
        
    /**
     * 
     * Nested DTO for each entitlement filter that has been entered on 
     * the Identity slicer dicer.
     * 
     * @author dan.smith
     *
     */
    public class IdentityEntitlementFilter {
        
        private String id;
        private String application;
        private String attributeName;
        private String attributeValue;
        private String displayValue;
        
        public IdentityEntitlementFilter() {
            this.id = Util.uuid();
            application = "";
            attributeName = "";
            attributeValue = "";
            displayValue = "";
        }
        
        public IdentityEntitlementFilter(String app, String attr, String val) {
            this();
            application = app;
            attributeName = attr;
            attributeValue = val;
            
            String explanatorDisplayValue = Explanator.getDisplayValue(app, attributeName, attributeValue);
            if ( Util.getString(explanatorDisplayValue) != null ) {
                displayValue = explanatorDisplayValue;
            } else {
                displayValue = attributeValue;
            }
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getApplication() {
            return application;
        }

        public void setApplication(String application) {
            this.application = application;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }

        public String getAttributeValue() {
            return attributeValue;
        }

        public void setAttributeValue(String attributeValue) {
            this.attributeValue = attributeValue;
        }
        
        public String getDisplayValue() {
            return this.displayValue;
        }
        
        public String toJSON() throws GeneralException {            
            HashMap<String,String> map = new HashMap<String,String>();
            map.put("displayValue", displayValue);
            map.put("attributeName", attributeName);
            map.put("attributeValue", attributeValue);
            map.put("application", application);
            map.put("id", id);
            String json = null;
            try {
                json = JsonUtil.render(map);
            } catch(Exception e) {
                throw new GeneralException("error converting to JSON: " + e) ;
            }            
            return json;
        }
    }
}
