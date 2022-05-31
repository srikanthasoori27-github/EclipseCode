/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.JsonUtil;
import sailpoint.object.Filter;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.ProvisioningPlan;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.search.SearchBean;

/**
 *
 * A FilterBuilder that is called by the slicer dicer to
 * build up filters when searching against identities and
 * IdentityEntitlements.
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 *
 */
public class IdentityEntitlementsFilterBuilder extends BaseFilterBuilder {
    
    private static final Log log = LogFactory.getLog(SearchBean.class);

    public IdentityEntitlementsFilterBuilder() { }
    
    @Override
    @SuppressWarnings("unchecked")
    public Filter getFilter() throws GeneralException {

        Object attrValue = this.value;
        String propertyName = this.propertyName;
        
        if ( attrValue == null ) 
            return null;
        
        Filter filter = null;
        if ( Util.nullSafeCompareTo("SPT_IdentityEntitlements", propertyName) == 0 ) {
            List<Filter> filterList = new ArrayList<Filter>();
            List<String> list = Util.asList(attrValue);
            if ( list != null ) {
                for ( String json : list ) {
                    try {
                        Map<String,String> deserialized = (Map<String,String>)JsonUtil.parse(json);
                        if ( deserialized != null ) {
                            Filter attrFilter = buildFilterFromMap(deserialized);
                            if ( attrFilter != null )
                                filterList.add(attrFilter);
                        }    
                    } catch(Exception e) {
                        throw new GeneralException(e);
                    }
                }                    
            }
            if ( filterList.size() > 0 ) {
                filter = Filter.collectionCondition("identityEntitlements", Filter.and(filterList));
            }
        } else {
            List<Filter> filterList = new ArrayList<Filter>();            
            // Roles have their own assignment model which is complicated.
            if ( Util.nullSafeCompareTo("identityEntitlements.assigned", propertyName) == 0 ) {
                if (attrValue != null ) { 
                    filterList.add(excludeRoleFilter());
                    filterList.add(Filter.eq(propertyName, Util.otob(attrValue)));
                }               
            } else 
            if ( Util.nullSafeCompareTo("identityEntitlements.aggregationState", propertyName) == 0 ) {
                if ( Util.getString((String)attrValue) != null ) {
                    filterList.add(excludeRoleFilter());
                    AggregationState aggState = AggregationState.valueOf((String)attrValue);
                    filterList.add(Filter.eq(propertyName, aggState));
                } 
            } else
            if  ( name != null ) {
                if ( ( "isEntitlementCertEmpty".compareTo(name) == 0 ) ||
                     ( "isEntitlementRequestEmpty".compareTo(name) == 0 ) ) { 
                    
                    boolean boolVal = Util.otob(attrValue);    
                    addIsNull(boolVal, filterList);

                } else
                if ( ( "isEntitlementPendingCert".compareTo(name) == 0 ) ||                         
                     ( "isEntitlementPendingRequest".compareTo(name) == 0 ) ) {

                    boolean boolVal = Util.otob(attrValue);
                    // subtle but these are checking for non null, with
                    // inverse polarity as the empty options
                    addIsNull(!boolVal, filterList);                    
                }

            } else {
                log.warn("Unhandled attribute specified for entitlement search. attrName ="+name+" vaue="+attrValue);
            }
            if ( Util.size(filterList) > 0 ) {
                filter = Filter.and(filterList);
            }
        }        
        return filter;
    }
    
    private void addIsNull(boolean isnullCheck, List<Filter> filterList) {
        if ( isnullCheck ) {
            filterList.add(Filter.isnull(propertyName));
        } else {
            filterList.add(Filter.notnull(propertyName));
        }
    }
    
    private Filter excludeRoleFilter() {
        return Filter.not(Filter.in("identityEntitlements.name", 
                          Arrays.asList(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) ) );
    }
    
    /**
     * Build a map from a serialized json string representing a filter
     * built by IdentityEntitlementFilterBean.
     * 
     * @param filterMap
     * @return
     */
    private Filter buildFilterFromMap(Map<String, String> filterMap) {
        
        List<Filter> filters = new ArrayList<Filter>();        
        
        String application = filterMap.get("application");
        if ( Util.getString(application) != null ) {
             filters.add(Filter.eq("application.name", application)); 
        }
        
        String attribute = filterMap.get("attributeName");
        if ( Util.getString(attribute) != null ) {
            filters.add(Filter.eq("name", attribute));
        }
        
        String value = filterMap.get("attributeValue");
        if ( Util.getString(value) != null ) {
            filters.add(Filter.eq("value", value));
        }        

        return ( filters.size() == 0 ) ? null : Filter.and(filters);
    }

}
