/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.ObjectConfig;
import sailpoint.service.LCMConfigService;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A utility class that helps with identity searches - in calculating dynamic
 * filters and adding population percentages to query results.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentitySearchUtil {
    private static Log log = LogFactory.getLog(IdentitySearchUtil.class);

    private static final String Q_PREFIX = "q_";
    private static final String DATE_PREFIX = "date_";
    private static final String IDENTITY_ATTR_PREFIX = "Identity.";
    private static final String PARAM_IDENTITY_IDS = "identityIds";

    private SailPointContext context;
    

    /**
     * Constructor.
     */
    public IdentitySearchUtil(SailPointContext context) {
        this.context = context;
    }

    /**
     * Return the search mode to use for LCM searches.
     */
    public Filter.MatchMode getLCMSearchMode() throws GeneralException {
        return new LCMConfigService(this.context).getSearchMode();
    }
    
    /**
     * Return any identity filters from the given request.
     */
    public List<Filter> getIdentityFilters(HttpServletRequest req, ObjectConfig objectConfig)
        throws GeneralException {

        List<Filter> filters = new ArrayList<Filter>();
        getDynamicFilters(req, null, filters, objectConfig);
        return filters;
    }

    /**
     * Parse the dynamic filters off the given request and add them to the given
     * filters only if they are contained in the list of filters for this object (role/and identityFilters lists.
     */
    @SuppressWarnings("unchecked")
    public void getDynamicFilters(HttpServletRequest req,
                                  List<Filter> filters,
                                  List<Filter> identityFilters,
                                  ObjectConfig objectConfig)
            throws GeneralException {
        Map<String, String> map = new HashMap<String, String>();
        Enumeration<String> parameterNames = req.getParameterNames();
        while(parameterNames.hasMoreElements()) {
            String key = parameterNames.nextElement();
            map.put(key, req.getParameter(key));
        }
        getDynamicFilters(map, filters, identityFilters, objectConfig);
    }

    /**
     * Parse the dynamic filters off the given request and add them to the given
     * filters only if they are contained in the list of filters for this object (role/and identityFilters lists.
     */
    @SuppressWarnings("unchecked")
    public void getDynamicFilters(Map<String, String> parameters,
                                  List<Filter> filters,
                                  List<Filter> identityFilters,
                                  ObjectConfig objectConfig)
        throws GeneralException {
        
        // Avoid null problems.
        filters = (null != filters) ? filters : new ArrayList<Filter>();
        identityFilters = (null != identityFilters) ? identityFilters : new ArrayList<Filter>();
        
        Filter.MatchMode searchMode = getLCMSearchMode();

        Enumeration<String> paramNames = Collections.enumeration(parameters.keySet());
        while (paramNames.hasMoreElements()) {
            String name = (String)paramNames.nextElement();
            String value = Util.getString(parameters.get(name));
            
            if (null != value) {

                /** If this is an identity population query we don't need to check in the object config, otherwise
                 * we need to make sure that this field exists in the object config or it will blow up the search
                 * since the field doesn't exist on the object
                 */
                if(name.startsWith(Q_PREFIX) &&
                        (objectConfigContains(name.substring(Q_PREFIX.length()), objectConfig) || name.contains(IDENTITY_ATTR_PREFIX))) {

                    // if filtering by type then exact match
                    Filter.MatchMode matchMode = "q_type".equals(name) ? Filter.MatchMode.EXACT : searchMode;
                    filters.add(Filter.ignoreCase(Filter.like(name.substring(Q_PREFIX.length()), value, matchMode)));
    
                    if(name.contains(IDENTITY_ATTR_PREFIX)) {
                        identityFilters.add(Filter.ignoreCase(Filter.like(name.substring(name.indexOf(".")+1), value, searchMode)));
                    }
                }
                // Date searches from AvailableRolesGrid.js are formatted as "startlong|endlong" which correspond
                // to 00:00:00 and 23:59:59 of the selected date.
                else if (name.startsWith(DATE_PREFIX) && 
                    (objectConfigContains(name.substring(5), objectConfig)) || name.contains(IDENTITY_ATTR_PREFIX)) {
                    String start = value.substring(0, value.indexOf("|"));
                    String end = value.substring(value.indexOf("|")+1, value.length());
                    filters.add(Filter.ge(name.substring(5), start));
                    filters.add(Filter.le(name.substring(5), end));
    
                    if(name.contains(IDENTITY_ATTR_PREFIX)) {
                        identityFilters.add(Filter.ge(name.substring(name.indexOf(".")+1), start));
                        identityFilters.add(Filter.le(name.substring(name.indexOf(".")+1), end));
                    }
                }
                else if (name.equals("name")) {
                    filters.add(Filter.ignoreCase(Filter.like(name, value, searchMode)));
                } 
                else if (name.equals(PARAM_IDENTITY_IDS)) {
                    List<String> identityIds = Util.csvToList(value);
                    filters.add(Filter.in("Identity.id", identityIds));
                    identityFilters.add(Filter.in("id", identityIds));
                }
            }
        }
    }
    
    
    /** When we are applying search parameters to the list of filters, we need to test that the values come in are
     * part of the object config so we don't apply the wrong filters to the wrong search.  For example, we don't want to apply role
     * attributes to the entitlement search and visa versa.
     * 
     * If the config is null, just return false.
     */
    private boolean objectConfigContains(String name, ObjectConfig config) {
        
        if(config==null) {
            return false;
        }
        
        return config.hasObjectAttribute(name);
    }

    /**
     * Query parameters that start with "q_" indicate extended attributes.  This method process
     * the passed set of Strings if a string starts with "q_" the leading bit is trimmed off and
     * the the remaineder is looked up in the ObjectConfig.  If all "q_" strings are found then
     * we return true.  Otherwise false.  Also if there are no possible extended attributes found
     * we return true.
     *
     * @param objectConfig The object to find extended attributes for
     * @param attributeNames The list of possible extended attribute names
     * @return If all the possible attribute names in attributeNames are valid for the config
     */
    public boolean hasAllExtendedAttributes(ObjectConfig objectConfig, Set<String> attributeNames) {
        for (String key : attributeNames) {
            if (key.startsWith(Q_PREFIX)) {
                String extendedAttributeName = key.substring(Q_PREFIX.length());
                if (!extendedAttributeName.contains(IDENTITY_ATTR_PREFIX) && 
                        !objectConfig.hasObjectAttribute(extendedAttributeName)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Return true if any of the attributes are filters for Identity search
     * @param attributeNames List of attribute names
     * @return True if Identity filter is present, otherwise false
     */
    public boolean hasIdentitySearchAttribute(Set<String> attributeNames) {
        for (String attributeName : Util.safeIterable(attributeNames)) {
            if (attributeName.equals(PARAM_IDENTITY_IDS)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Return true if any of the attributes are filters for Population search
     * @param attributeNames List of attribute names
     * @return True if Population filters are present, otherwise false
     */
    public boolean hasPopulationSearchAttribute(Set<String> attributeNames) {
        for (String attributeName : Util.safeIterable(attributeNames)) {
            if (attributeName.contains(IDENTITY_ATTR_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the query parameter name for this filter. Adds the prefix that can be parsed
     * by getDynamic filters later.
     * @param filterDTO ListFilterDTO to use to generate query parameter name
     * @param makeIdentityAttribute If true, add the identity attribute prefix
     * @return Name of query parameter
     */
    public String getQueryParameterName(ListFilterDTO filterDTO, boolean makeIdentityAttribute) {
        String newProperty = filterDTO.getProperty();
        if (!filterDTO.isDefault()) {
            if (makeIdentityAttribute) {
                newProperty = IDENTITY_ATTR_PREFIX + newProperty;
            }
            if (ListFilterDTO.DataTypes.Date.equals(filterDTO.getDataType())) {
                newProperty = DATE_PREFIX + newProperty;
            } else {
                newProperty = Q_PREFIX + newProperty;
            }
        }
        return newProperty;
    }

    /**
     * Given a query parameter name, get the filter property name. This strips off
     * q_ and date_ prefixes, as well as conditionally the Identity. prefix
     * @param queryParameterName Original query parameter name
     * @param stripIdentityAttribute If true, consider Identity. prefix as well
     * @return converted filter property name
     */
    public String getFilterPropertyName(String queryParameterName, boolean stripIdentityAttribute) {
        String propertyName = queryParameterName;
        if (propertyName.startsWith(DATE_PREFIX)) {
            propertyName = propertyName.substring(DATE_PREFIX.length());
        } else if (propertyName.startsWith(Q_PREFIX)) {
            propertyName = propertyName.substring(Q_PREFIX.length());
        }
        
        if (stripIdentityAttribute && propertyName.startsWith(IDENTITY_ATTR_PREFIX)) {
            propertyName = propertyName.substring(IDENTITY_ATTR_PREFIX.length());
        }
        
        return propertyName;
    }

    /**
     * Given a full set of query parameters, pull out the Identity parameters
     * and take out the weird prefixes.  
     * @param queryParameters Query parameters. Will be modified.
     * @return Map containing Identity filter parameters
     */
    public Map<String, String> separateIdentityParameters(Map<String, String> queryParameters) {
        Map<String, String> identityParameters = new HashMap<String, String>();
        Map<String, String> nonIdentityParameters = new HashMap<String, String>();
        Iterator<Map.Entry<String, String>> queryParameterIterator = queryParameters.entrySet().iterator();
        while (queryParameterIterator.hasNext()) {
            Map.Entry<String, String> entry = queryParameterIterator.next();
            String newKey = getFilterPropertyName(entry.getKey(), false);
            if (newKey.startsWith(IDENTITY_ATTR_PREFIX)) {
                identityParameters.put(newKey.substring(IDENTITY_ATTR_PREFIX.length()), entry.getValue());
            } else if (PARAM_IDENTITY_IDS.equals(newKey)) {
                identityParameters.put(newKey, entry.getValue());
                /* UserAccessIdentityFilterContext expects the Identity. prefix on this for the main filter.
                 * Unfortunately our query params use 'identityIds', which does not allow us to distinguish between
                 * identity filter and regular filer. So put the param back on with the prefix we expect */
                newKey = IDENTITY_ATTR_PREFIX + PARAM_IDENTITY_IDS;
            }
            
            nonIdentityParameters.put(newKey, entry.getValue());
            queryParameterIterator.remove();
        }
        
        queryParameters.putAll(nonIdentityParameters);
        return identityParameters;
    }
}
