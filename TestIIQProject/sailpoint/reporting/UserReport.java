/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.reporting.datasource.IdentityDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * A UserReport class, used to generate a report for Identity objects.
 * 
 */
public class UserReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(UserReport.class);

    //////////////////////////////////////////////////////////////////////
    //
    // 
    //
    //////////////////////////////////////////////////////////////////////

    private final String DETAIL_REPORT = "UserMainReport";
    private final String GRID_REPORT = "UserGridReport";

    static String IDENTITY_EXTERN = "IdentityExternalAttribute";
    static String IDENTITY_EXTERN_NAME = IDENTITY_EXTERN + ".attributeName";
    static String IDENTITY_EXTERN_VALUE =  IDENTITY_EXTERN + ".value";
    static String IDENTITY_EXTERN_ID =  IDENTITY_EXTERN + ".objectId";
   
    @Override 
    public String getJasperClass() {
        String className = GRID_REPORT;
        if ( showDetailed() ) {
            className = DETAIL_REPORT;
        }
        return className;
    }
    
    protected List<Filter> buildFilters(Attributes<String,Object> inputs) {
        
        List<Filter> filters = new ArrayList<Filter>();
        addEQFilter(filters, inputs, "identities", "id", null);
        addEQFilter(filters, inputs, "managers", "manager.id", null);
        addLikeFilter(filters, inputs, "lastname", "lastname", null);
        addLikeFilter(filters, inputs, "firstname", "firstname", null);
        addLikeFilter(filters, inputs, "email", "email", null);
        if(inputs.get("capabilities")!=null) {
            addEQFilter(filters, inputs, "capabilities", "capabilities.name", null);
        }
        addEQFilter(filters, inputs, "businessRoles", "assignedRoles.id", "noBusinessRoles");
        addEQFilter(filters, inputs, "applications", "links.application.id", null);
        addDateTypeFilter(filters, inputs, "lastLogin", "lastLoginType", "lastLogin", "noLastLogin");
        addDateTypeFilter(filters, inputs, "lastRefresh", "lastRefreshType", "lastRefresh", "noLastRefresh");
        
        if(inputs.getBoolean("useInactive"))
            addBooleanFilter(filters, inputs, "inactive", "inactive", null);
        
        getGroupFilter(filters, inputs);

        addIdentityAttributes(filters, inputs);
        log.debug("Filter: " + filters);
        return filters;
        
    }

    private void addIdentityAttributes(List<Filter> filters, 
                                                  Attributes<String,Object> inputs) {

        ObjectConfig config = Identity.getObjectConfig();
        List<ObjectAttribute> attrs = null;
        if ( config != null ) {
            attrs = config.getObjectAttributes();
        }
        if ( attrs == null ) {
            attrs = new ArrayList<ObjectAttribute>();
        }
        for ( ObjectAttribute attr : attrs ) {
            String name = attr.getName();    
            Object o = inputs.get(name);
            if ( o == null ) continue;
            
            /** Handle Multi-valued Attributes **/
            if ( attr.isMulti() ) {
                String operator = inputs.getString(OP_OPERATOR_PREFIX+name);
                if ( operator != null ) {
                    List<String> vals = Util.delimToList("\n", o.toString(), true);
                    if ( ( vals != null ) && ( vals.size() > 0 ) ) {
                        if ( "OR".compareTo(operator) == 0  ) {
                            Filter filter = Filter.and(Filter.join("id", IDENTITY_EXTERN_ID),
                                                       Filter.eq(IDENTITY_EXTERN_NAME, name),
                                                       Filter.in(IDENTITY_EXTERN_VALUE, vals));
                            filters.add(filter); 
                        } else
                        if ( "AND".compareTo(operator) == 0  ) {
                            List<Filter> ccFilters = new ArrayList<Filter>();
                            for ( int i=0; i<vals.size(); i++ ) {
                                Filter filter = Filter.and(Filter.join("id", IDENTITY_EXTERN_ID),
                                                           Filter.eq(IDENTITY_EXTERN_NAME, name),
                                                           Filter.eq(IDENTITY_EXTERN_VALUE, vals.get(i)));
                                ccFilters.add(filter); 
                            }
                            if ( ( ccFilters != null ) && ( ccFilters.size() > 0 ) ) {
                                filters.add(Filter.collectionCondition("IdentityExternalAttribute", Filter.and(ccFilters)));
                            }
                        }
                    }
                }
            /** Make a special exemption for display name - Bug 5537 **/
            } else if (attr.getExtendedNumber() > 0 || attr.getName().equals("displayName")){
                addLikeFilter(filters, inputs, attr.getName(), attr.getName(), null);
            }
        } 
    }

    public TopLevelDataSource getDataSource()
        throws GeneralException {

        Attributes<String,Object> args = getInputs();
        SailPointContext ctx = getContext();
        List<Filter> filters = buildFilters(args);
        /** Need to handle extended attributes and create filters for them **/
        ObjectConfig conf = ctx.getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        List<ObjectAttribute> attributes = conf.getExtendedAttributeList();
        if(attributes!=null) {
            for(ObjectAttribute attr : attributes) {
                addLikeFilter(filters, args, attr.getName(), attr.getName(), null);
            }
        }
        return new IdentityDataSource(filters, getLocale(), getTimeZone(), args);
    }
    
    /** checks the report's arguments to see if it contains group definitions.  If it does, we
     * pull the filter off of that group definition.
     *
     */
    private void getGroupFilter(List<Filter> filters, Attributes<String,Object> inputs) {
    	if(inputs.getString("groupDefinition")!=null) {
            String groupDefinitionName = inputs.getString("groupDefinition");
           
            Filter groupDefFilter = null;
            
            if(groupDefinitionName.contains(",")) {
                List<String> defs = Util.csvToList(groupDefinitionName);
                groupDefFilter = Filter.in("id", defs);
            } else {
                groupDefFilter = Filter.eq("id", groupDefinitionName);
            }
        	QueryOptions qo = new QueryOptions();
            qo.add(groupDefFilter);
            List<String> props = Arrays.asList(new String[]{"filter"});
            try {
                Iterator<Object[]> rows = getContext().search(GroupDefinition.class, qo, props);
                List<Filter> groupFilters = new ArrayList<Filter>();
                while(rows.hasNext()) {
                    Filter f = (Filter)rows.next()[0];
                    groupFilters.add(f);
                }
                
                if(groupFilters!=null) {
                    filters.add(Filter.or(groupFilters));
                }
            } catch (GeneralException ge) {
                
            }
        	
        }
    }
}
