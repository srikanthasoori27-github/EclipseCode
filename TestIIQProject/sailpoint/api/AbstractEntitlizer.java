/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */


/**
 * A class encapsulating various maintenance of the IdentityEntitlement
 * objects added in 6.0.
 *
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CorrelationModel.CorrelationRole;
import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.LinkInterface;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * 
 * Abstract class to share some common utility methods 
 * used by other api objects that deal with IdentityEntitlements. 
 * 
 * NOTE: Be careful with queries that include the name, value, 
 * instance or native identity field.  They have case insensitive
 * indexes that will only be used if the queries are case 
 * insensitive.
 * 
 * @see {@link Entitlizer}
 * @see {@link CertificationEntitlizer}
 * @see {@link RequestEntitlizer}
 * @see {@link RoleEntitlizer}
 *  
 * @author dan.smith@sailpoint.com
 *
 */
public class AbstractEntitlizer {
    
    private static Log log = LogFactory.getLog(Entitlizer.class);

    private static final String COL_VALUE = "value";

    public static final Filter ROLE_FILTER =  
        Filter.ignoreCase(Filter.in("name", 
                          Arrays.asList(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, 
                                        ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) ) 
                         );    
    /**
     * Required SailPointContext.  
     */ 
    SailPointContext _context;
    
    /**
     * Correlation model is used to fetch Role details.
     * This is basically a light weight version of our role model cached in memory.
     * 
     * Typically built during entitlement correlation and then used directly after
     * during entitlement promotion.
     */
    CorrelationModel CM;

    /**
     * Arguments to configure behavior, typically just the arguments
     * that come into the aggregation and refresh.  
     */
    private Attributes<String,Object> _arguments;
    
    protected AbstractEntitlizer() {
        _context = null;
    }

    protected AbstractEntitlizer(SailPointContext context) {        
        _context = context;
    }
    
    protected AbstractEntitlizer(SailPointContext context, Attributes<String, Object> arguments) {
        this(context);
        _arguments = arguments;
    }
    
    /**
     * Typically the correlation model is configured through the
     * EntitlementCorrelator.  When possible it will be explicitly
     * set on this class, otherwise we have to initialize it 
     * ourselves. 
     * 
     * @throws GeneralException
     */
    protected CorrelationModel getCorrelationModel() throws GeneralException  {        
        if ( CM == null ) {
            CM = CorrelationModel.getCorrelationModel(_context, _arguments);
        }
        
        return CM;
    }
    
    /**
     * In most cases this set as the same CorrelationModel used by EntitlementCorrelator to
     * avoid having two different versions of the role data.
     * 
     * @param model CorrelationModel to set
     */
    public void setCorrelationModel(CorrelationModel model) {
        CM = model;
    } 
    
    ///////////////////////////////////////////////////////////////////////////
    // 
    // Retrieval of IdentityEntitlements
    //
    /////////////////////////////////////////////////////////////////////////
    
    /**
     * 
     * Retrieve the current entitlements for the given identity,
     * application, native identity and instance.
     * 
     */
    protected List<IdentityEntitlement> getAccountEntitlements(Identity identity, 
                                                               Application application, 
                                                               String nativeIdentity, 
                                                               String instance) 
        throws GeneralException {
        
        QueryOptions qo = new QueryOptions();
        qo.add(buildAccountFilter(identity, application, nativeIdentity, instance));
        qo.add(Filter.not(ROLE_FILTER));
        return getEntitlements(qo);
    }

    protected List<IdentityEntitlement> getAssignedAttributeEntitlements(Identity identity, AttributeAssignment assignment)
        throws GeneralException {

        if (assignment != null) {
            QueryOptions ops = new QueryOptions();
            if ( identity != null )
                ops.add(Filter.eq("identity", identity));

            ops.add(Filter.eq("application.id", assignment.getApplicationId()));

            ops.add(Filter.eq("value", assignment.getStringValue()));

            ops.add(Filter.ignoreCase(Filter.eq("name", assignment.getName())));

            if (Util.isNotNullOrEmpty(assignment.getAssignmentId())) {
                //match on assignmentId
                ops.add(Filter.eq("assignmentId", assignment.getAssignmentId()));
            } else {
                //Null AssignmnetID && (null nativeID or matching nativeId)
                ops.add(Filter.and(Filter.isnull("assignmentId"),
                            Filter.or(Filter.isnull("nativeIdentity"),
                                Filter.ignoreCase(Filter.eq("nativeIdentity", assignment.getNativeIdentity())))));
            }

            if (Util.getString(assignment.getInstance()) == null) {
                ops.add(Filter.isnull("instance"));
            } else {
                ops.add(Filter.ignoreCase(Filter.eq("instance", assignment.getInstance())));
            }


            return getEntitlements(ops);
        }
        return null;
    }
    
    /**
     * Retrieve the assignedRoles Identity Entitlements.
     */
    protected List<IdentityEntitlement> getAssignedRolesEntitlements(Identity identity, String...bundleNames) 
        throws GeneralException {
        
        QueryOptions qo = new QueryOptions();        
        //
        // Query for the list of current entitlements for this user's assigned role
        //
        List<Filter> filters = new ArrayList<Filter>();
        
        filters.add(Filter.eq("identity", identity));
        filters.add(Filter.ignoreCase(Filter.eq("name", ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES)));     
        if ( bundleNames != null && bundleNames.length > 0 ){            
            filters.add(buildValueFilter(Arrays.asList(bundleNames)));
        }
        
        Filter filter =   Filter.and(filters);                
        qo.addFilter(filter);        
        
        return getEntitlements(qo);     
    }
    
    /**
     * Retrieve the detectedRole Identity Entitlements.
     */
    protected List<IdentityEntitlement> getDetectedRoleEntitlements(Identity identity, String... bundleNames) 
        throws GeneralException {
    
        QueryOptions qo = new QueryOptions();        
        //
        // Query for the list of current entitlements for this user's detected roles
        //
        List<Filter> filters = new ArrayList<Filter>();
        
        filters.add(Filter.eq("identity", identity));
        filters.add(Filter.ignoreCase(Filter.eq("name", ProvisioningPlan.ATT_IIQ_DETECTED_ROLES)));        
        if ( bundleNames != null && bundleNames.length > 0){            
            filters.add(buildValueFilter(Arrays.asList(bundleNames)));
        }
        
        Filter filter =   Filter.and(filters);                
        qo.addFilter(filter);        
            
        return getEntitlements( qo );        
    }

    /**
     * Using brute force go through the list of the entitlements and find
     * the entitlement that attributes the same attribute name and value.
     * 
     * This list here is paired down to an application/instance level.
     *  TODO: This will not work for multiple RoleAssignments. Will return the first one found.
     */
//    protected IdentityEntitlement findEntitlement(List<IdentityEntitlement> entitlements, 
//                                                  String attrName, String attrValue) {
//                
//        if ( entitlements != null ) {
//            for ( IdentityEntitlement entitlement : entitlements ) {
//                if ( entitlement == null ) {
//                   continue;
//                }
//                if ( ( Util.nullSafeCompareTo(attrName, entitlement.getName()) == 0 ) &&
//                     ( Util.nullSafeCompareTo(attrValue, entitlement.getStringValue()) == 0 ) ) {
//                    return entitlement;
//                }                
//            }
//        }        
//        return null;        
//    }

    
    /**
     * Fetches the IdentityEntitlement mapping to a Role (Assigned or Detected), based on assignmentId if applicable
     * @param entitlements List of IdentityEntitlements for a specified Identity
     * @param attrName Assigned/Detected @see ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES
     * @param attrValue Role Name
     * @param assignmentId AssignmentId. Detected Roles could be an issue if we ever certify detectedRoles that would normally
     * be "rolled up" into an assignment due to the fact that RoleDetections contain a list of assignmentIds, and there will only
     * be one entry in the IdentityEntitlements (containing only one of the assignmentId)
     * @return IdentityEntitlement corresponding to the specified RoleAssignment or RoleDetection
     */
    protected IdentityEntitlement findRoleEntitlement(List<IdentityEntitlement> entitlements, String attrName, String attrValue, String assignmentId) {
        if ( entitlements != null ) {
            for ( IdentityEntitlement entitlement : entitlements ) {
                if ( entitlement == null ) {
                   continue;
                }
                if ( ( Util.nullSafeCompareTo(attrName, entitlement.getName()) == 0 ) &&
                     ( Util.nullSafeCompareTo(attrValue, entitlement.getStringValue()) == 0 ) &&
                     (Util.nullSafeCompareTo(assignmentId, entitlement.getAssignmentId()) == 0 ) ) {
                    return entitlement;
                }                
            }
        }        
        return null;   
    }

    
    /**
     * Using brute force go through the list of the entitlements and find
     * the entitlement that attributes the same attribute name, value and appName.
     */
    protected IdentityEntitlement findEntitlementWithApp(List<IdentityEntitlement> entitlements, 
                                                  String attrName, String attrValue, String appName, String nativeId) {
                
        if ( entitlements != null ) {
            for ( IdentityEntitlement entitlement : entitlements ) {
                if ( entitlement == null ) {
                   continue;
                }
                // pjeong: bug20990 do case insensitive comparison for native id
                if ( ( Util.nullSafeCompareTo(attrName, entitlement.getName()) == 0 ) &&
                     ( Util.nullSafeCompareTo(attrValue, entitlement.getStringValue()) == 0 ) &&
                     ( Util.nullSafeCaseInsensitiveEq(nativeId, entitlement.getNativeIdentity()) ) &&
                     ( Util.nullSafeCompareTo(appName, entitlement.getAppName()) == 0 )) {
                    return entitlement;
                }                
            }
        }        
        return null;        
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Get a list of strings from the values. 
     */
    @SuppressWarnings("unchecked")    
    protected List<String> valueToStringList(Object val) {
        List<String> strVals = new ArrayList<String>();

        if ( val != null ) {
            List<Object> objects = Util.asList(val);
            if ( objects != null ) {
                for ( Object o : objects) {
                    String strVal = o.toString();
                    if ( strVal != null ) {
                        strVals.add(strVal);
                    }
                }
            }
        }
        return ( Util.size(strVals) > 0 ) ? strVals : null;
    }
    
    /**
     * Fetch all of IdentityEntitlement for the given QueryOptions.  
     * 
     * In this case getObjects works because we need the entire object
     * for each entitlement.  
     * 
     */
    protected List<IdentityEntitlement> getEntitlements(QueryOptions ops) 
        throws GeneralException {
        
        return _context.getObjects(IdentityEntitlement.class, ops);                
    }
    
    /**
     * 
     * Convenience method to help build an account level filter
     * for entitlements.  As always in the system you'll need
     * at least an identityName, appName and NativeIdentity.
     * Instance is optional an can be specified null.
     */    
    protected Filter buildAccountFilter(Identity identity, Application app, String nativeIdentity, String instance) { 
        List<Filter> filters = new ArrayList<Filter>();
        
        if ( identity != null ) 
           filters.add(Filter.eq("identity", identity)); 
        
        filters.add(Filter.eq("application", app));
        filters.add(Filter.ignoreCase(Filter.eq("nativeIdentity", nativeIdentity)));
       
        if (Util.getString(instance) == null) {
            filters.add(Filter.isnull("instance"));
        } else {
            filters.add(Filter.ignoreCase(Filter.eq("instance", instance)));
        } 
        return Filter.and(filters);
    }    
    
    /**
     * 
     * Look through a user's assigned role list, checking the permits and required
     * to determine if the role detection is allowed.
     * 
     * @param roleAssignments The role assignments.
     * @param roleDetection The role detection.
     * @return A list of role names allowing the detection.
     * @throws GeneralException
     */
     protected List<String> getAllowedBy(List<RoleAssignment> roleAssignments, RoleDetection roleDetection) 
        throws GeneralException {

        if ( roleDetection == null ) 
            return null;

        // Use set to avoid possible role name duplication
        Set<String> allowedBySet = new HashSet<String>();
         
        if ( roleAssignments != null ) {
            for ( RoleAssignment assignment : roleAssignments ) {
                if ( assignment == null ) continue;
                
                //Bug#17061 -- get assignmentRoleId from name if the id is null.
                String assignedRoleId = assignment.getRoleId();                
                if (assignedRoleId == null) {
                    assignedRoleId = ObjectUtil.getId(_context, Bundle.class, assignment.getRoleName());
                }
                
                CorrelationModel model = getCorrelationModel();
                
                if (model != null) {
                    Collection<CorrelationRole> requiredPermitted =
                            model.getRequiredAndPermittedForRole(_context, assignedRoleId);
                    if ( Util.size(requiredPermitted) > 0 ) {                    
                        for ( CorrelationRole allowed : requiredPermitted ) {
                            if ( allowed == null ) continue;
                            if ( Util.nullSafeCompareTo(allowed.getName(), roleDetection.getRoleName() ) == 0 ||
                                 Util.nullSafeCompareTo(allowed.getId(), roleDetection.getRoleId() ) == 0 ) {
                                String assignedRoleName = assignment.getRoleName();
                                if ( assignedRoleName != null ) 
                                    allowedBySet.add(assignedRoleName);                            
                            }
                        }   
                    }
                }
            }
        }
         
        List<String> allowedBy = new ArrayList<String>(allowedBySet);
         
        return ( allowedBy.size() > 0 ) ? allowedBy : null;
    }
     
     ///////////////////////////////////////////////////////////////////////////
     //
     // Entitlement(s) Filter Building
     //
     ///////////////////////////////////////////////////////////////////////////

     /**
      * Use the model stored in an Entitlements object ( EntitlementGroup or EntitlementSnapshot )
      * to find the related IdentityEntitlements.
      * 
      * These queries end up being formed like :
      */
     protected Filter entitlementToFilter(Entitlements group ) throws GeneralException {
         Filter egFilter = null;      
         
         if ( group != null ) {
             // first variable is null because the "base" filter includes identity filter
             Filter acctFilter  = buildAccountFilter(null, group.getApplicationObject(_context), group.getNativeIdentity(), group.getInstance());
             if ( acctFilter != null ) {
                 Filter nameValues = buildNameValueFilter(group);
                 if ( nameValues != null ) {
                     egFilter = Filter.and(acctFilter, nameValues);
                 }                   
             }                             
         }
         return egFilter;
     }
     

      
     /**
      * For both the attributes and the permissions in an Entitlements class
      * build filters for the attributeName and Value specified in the 
      * Entitlements object.
      * 
      * @return Ored Filter of the combined group attrName/Values
      */
     private Filter buildNameValueFilter(Entitlements group) {        
         
         List<String> attrNames = group.getAttributeNames();
         Attributes<String,Object> values = group.getAttributes();
         List<Permission> permissions = group.getPermissions();        
         
         List<Filter> nameValueFilters = new ArrayList<Filter>();            
         if ( attrNames != null ) {
             for ( String attribute : attrNames ) {                    
                 Object val = values.get(attribute);
                 List<String> strVals = filterValues(group, attribute, valueToStringList(val));
                 if ( Util.size(strVals) > 0 ) {
                     Filter valueFilter = buildValueFilter(strVals);
                     if ( valueFilter != null ) {
                         nameValueFilters.add(Filter.and(Filter.ignoreCase(Filter.eq("name", attribute)), valueFilter));
                     }
                 }
             }
         }  
         if ( Util.size(permissions) > 0 ) {
             Filter permFilter = buildPermissionFilter(group, permissions);
             if ( permFilter != null ) {
                 nameValueFilters.add(permFilter);
             }
         }
         return ( nameValueFilters.size() > 0 ) ? Filter.or(nameValueFilters) : null;
     }
     
     /**
      * Go over all of the permissions and build a filter
      * 
      * @return Ored Filter of all the target/rights specified in the group.
      */
     private Filter buildPermissionFilter(Entitlements group, List<Permission> permissions) {
         List<Filter> permFilters = new ArrayList<Filter>();            
         if ( permissions != null ) {
             for ( Permission permission : permissions ) {                    
                 String target = permission.getTarget();
                 List<String> rights = filterValues(group, target, permission.getRightsList()); 
                 if ( Util.size(rights) > 0 ) {
                     Filter valueFilter = buildValueFilter(rights);
                     if ( valueFilter != null ) {
                         permFilters.add(Filter.and(Filter.ignoreCase(Filter.eq("name", target)), valueFilter));
                     }
                 }
             }
         }            
         return ( permFilters.size() > 0 ) ? Filter.or(permFilters) : null;
     }

    protected Filter buildValueFilter(List<String> vals) {
        return buildValueFilter(vals, COL_VALUE);
    }

     /**
      * Helper method, originally tried using .in filters but this caused issues in the
      * scale data. 
      */
     protected Filter buildValueFilter(List<String> vals, String valueColumn) {

         Filter valueFilter = null;
         if ( vals != null ) {
             if ( vals.size() <= 500 ) {
                 valueFilter = Filter.ignoreCase(Filter.in(valueColumn, vals));
             } else {
                 List<Filter> valueFilters = new ArrayList<Filter>();
                 for ( String val : vals ) {
                     valueFilters.add(Filter.ignoreCase(Filter.eq(valueColumn, val)));
                 }
                 if ( valueFilters.size() > 0 )
                     valueFilter = Filter.or(valueFilters);
             }
         }
         return valueFilter;
     }     
     
     /**
      * Used by the RoleEntitlizer to filter out values that were already promoted
      * as part of the Link promotion. By default it doesn't filter anything, but the
      * RoleEntilizer overrides this method and negotiates with data it keeps about
      * what's already been promoted.
      */
     protected List<String> filterValues(Entitlements group, String attrName, List<String> vals) {
         return vals;
     }
     
     /**
      * Look through the current identity's attribute assignment list and see if 
      * the entitlement is specified.
      */
     protected boolean isAssignedOnIdentity(IdentityEntitlement current,             
                                            List<AttributeAssignment> currentAssignments) {
         

         return getAttributeAssignment(current, currentAssignments) != null;
     }

    /**
     * Return the identity's attribute assignment associated with the given IdentityEntitlement
     * @param current
     * @param currentAssignments
     * @return
     */
    public AttributeAssignment getAttributeAssignment(IdentityEntitlement current,
                                                         List<AttributeAssignment> currentAssignments) {
        if ( current != null ) {
            if ( currentAssignments != null ) {
                Application entitlementApp = current.getApplication();
                if ( entitlementApp == null )
                    return null;
                for ( AttributeAssignment assignment : currentAssignments ) {
                    if ( Util.nullSafeCompareTo(entitlementApp.getId(),assignment.getApplicationId()) == 0  &&
                            Util.nullSafeCompareTo(current.getNativeIdentity(),assignment.getNativeIdentity()) == 0 &&
                            Util.nullSafeCompareTo(current.getName(),assignment.getName()) == 0 &&
                            Util.nullSafeCompareTo(current.getInstance(),assignment.getInstance()) == 0 &&
                            Util.nullSafeCompareTo(current.getStringValue(), assignment.getStringValue()) == 0 ) {
                        return assignment;
                    }
                }
            }
        }
        return null;
    }
     
     /**
      * Look through the current identity's attribute assignment list and return
      * the one you seek
      * 
      * @param current
      * @param currentAssignments
      * @return AttributeAssignment
      */
     protected AttributeAssignment getAssignmentFromIdentity(IdentityEntitlement current,             
                                            List<AttributeAssignment> currentAssignments) {
         
         AttributeAssignment retAssignment = null;
         if ( current != null ) {
             if ( currentAssignments != null ) {
                 Application entitlementApp = current.getApplication();
                 if ( entitlementApp == null ) 
                     return retAssignment;
                 for ( AttributeAssignment assignment : currentAssignments ) {                     
                     if ( Util.nullSafeCompareTo(entitlementApp.getId(),assignment.getApplicationId()) == 0  &&
                          Util.nullSafeCompareTo(current.getNativeIdentity(),assignment.getNativeIdentity()) == 0 &&
                          Util.nullSafeCompareTo(current.getName(),assignment.getName()) == 0 &&
                          Util.nullSafeCompareTo(current.getInstance(),assignment.getInstance()) == 0 &&
                          Util.nullSafeCompareTo(current.getStringValue(), assignment.getStringValue()) == 0 ) {    
                          retAssignment = assignment;
                          break;
                     }                    
                 }
             }
         }
         return retAssignment;
     }
     
    ///////////////////////////////////////////////////////////////////////////
    //
    // Bulk Operations
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Make a single call for a group of entitlement ids to help
     * reduce the number of db calls and worrying about hibernate
     * cache bloat.
     * 
     * We limit here to a 100 so that we don't overload the sql
     * size.
     *      
     * @ignore
     * It would be nice if the HQL could include a subquery
     * or allow for multiple joins when doing batch updates.
     * I was able to use a subquery, just not against the same
     * object that's being updated.
     * 
     */
    public void bulkUpdateColumns(QueryOptions ops, Map<String,Object> columnUpdates) 
        throws GeneralException {

        if (ops != null) {
            ops.setCloneResults(true);
        } else {
            ops = new QueryOptions();
            ops.setCloneResults(true);
        }

        Iterator<Object[]> rows = _context.search(IdentityEntitlement.class, ops, Arrays.asList("id"));
        if ( rows != null ) {
            List<String> ids = new ArrayList<String>();
            while ( rows.hasNext() ) {
                Object[] row = rows.next();
                String id = (String)row[0];
                if ( id == null ) continue;
                ids.add(id);
                if ( ids.size() > 100 ) {
                    // batch delete
                    bulkUpdateColumnsHQL(ids, columnUpdates);
                    ids.clear();
                }
            }
            bulkUpdateColumnsHQL(ids, columnUpdates);
        }                
    }
    
    /**
     * Null each column specified in the columns parameter,
     * for all entitlements that match the query options. 
     */
    protected void bulkNullColumns(QueryOptions ops, String...columns) 
        throws GeneralException {
        
        if ( columns != null ) {
            Map<String,Object> map = new HashMap<String,Object>();
            for ( String column : columns ) {
                map.put(column, null);
            }
            bulkUpdateColumns(ops, map);
        }        
    }
    
    /**
     * Update several IdentityEntitlement objects at one time, given the ids
     * of the entitlements and a Map of the properties to update
     * 
     * There are only two types of columns on the IdentityEntitlements
     * table, so right now only support Boolean and String types.
     * 
     * @param ids the entitlement hibernate ids that should be updated
     * @param columns the columns to update
     * @throws GeneralException
     */
    private void bulkUpdateColumnsHQL(List<String> ids, Map<String,Object> columns) 
        throws GeneralException {
        
        if ( Util.size(ids) > 0 && ( columns != null && !columns.isEmpty() ) ) {
            StringBuilder hqlUpdate = new StringBuilder("UPDATE IdentityEntitlement ent SET ");
            Set<String> keys = columns.keySet();
            if ( keys != null ) {
                Iterator<String> it = keys.iterator();
                int i = 0;
                while ( it.hasNext() ) {
                    String value = "null";
                    String property = it.next();
                    if ( property == null )
                        continue;
                    Object o = columns.get(property);
                    if ( o != null ) {
                        if ( o instanceof String) {
                            value = "'" + (String)o+ "'";
                        } else
                        if ( o instanceof Boolean ) {
                            if ( (Boolean)o ) {
                                value = "true";
                            } else 
                                value = "false";
                        }                            
                    }                    
                    if (i++ > 0) 
                        hqlUpdate.append(", ");
                    hqlUpdate.append("ent." + property + " = "+ value);   
                }
            }
            hqlUpdate.append(" where ent.id in ("+listToSingleQuotedString(ids)+")");
            
            int updated = _context.update(hqlUpdate.toString(), new HashMap<String,Object>());
            if ( log.isDebugEnabled() ) {
                log.debug("Cleared flag from ["+updated+"] Entitlements.");
            }
        }
    }
    
    /**
     * Turn a list of id's into an hql acceptable 'in' statement 
     * contents.
     */
    protected String listToSingleQuotedString(List<String> ids) {
        
        if ( Util.size(ids) == 0 ) 
            return null;
        
        StringBuilder sb = new StringBuilder();
        for ( String id : ids ) {
            if ( sb.length() > 0 ) 
                sb.append(",");
            sb.append("'");
            sb.append(id);
            sb.append("'");
        }
        return sb.toString();
    }
    
    /**
     * Object to keep state to tell us if a value has been changed
     * to help drive us if we need to call saveObject.
     * 
     * This is required to efficiently refresh IdentityEntitlements
     * in explicit save mode.
     * 
     * It checks the current values before calling the setters
     * and when detects a change it will set the _hasChanged
     * flag to true, which indicates the Entitlement should be 
     * persisted.
     * 
     * You can just create one of these, use it to update 
     * and then call hasUpdates() to determine if we need
     * to call saveObject.
     * 
     * @author dan.smith
     *
     */
    protected class EntitlementUpdater {
        
        /**
         * Will be set to true if we've updated any of the tracked attributes.
         */
        private boolean _hasChanges;
        
        /**
         * The object we'll be potentially updating.
         */
        private IdentityEntitlement _entitlement;
        
        public EntitlementUpdater(IdentityEntitlement entitlement) {
            _hasChanges = false;
            _entitlement = entitlement;
            if ( _entitlement == null ) {
                throw new UnsupportedOperationException("Entitlement must be non-null.");                
            }
            // new objects should always be saved
            if ( _entitlement.getId() == null ) {
                updated();
            }
        }
        
        public boolean hasUpdates() {
            return _hasChanges;
        }
        
        private void updated() {
            _hasChanges = true;
        }
        
        public IdentityEntitlement getEntitlement() {
            return _entitlement;
        }
        
        public void setAllowed(boolean allowed) {
            if  ( allowed != _entitlement.isAllowed() ) {                
                _entitlement.setAllowed(allowed);
                updated();
            }
        }
        
        /**
         * 
         * Clear any existing role data from this entitlement.
         * 
         * For callers that need to save when things are modified 
         * this method returns true any time _grantedByRole,
         * or the source assigned or source detected role
         * is list.
         *  
         */
        public void clearRoleData() {
           
            if ( _entitlement.isGrantedByRole() ) {
                _entitlement.setGrantedByRole(false);
                updated();
            }
            
            if ( _entitlement.getSourceDetectedRoles() != null ) {
                setSourceDetectedRoles(null);
                updated();
            }
            
            if ( _entitlement.getSourceAssignableRoles() != null ) { 
                setSourceAssignableRoles(null);
                updated();
            }            
        }

        public void setAggregationState(AggregationState connected) {
            if ( !Util.nullSafeEq(connected, _entitlement.getAggregationState()) ) {
                _entitlement.setAggregationState(connected);
                updated();
            }            
        }

        public void setGrantdByRole(boolean b) {
            boolean current = _entitlement.isGrantedByRole();
            if ( current != b ) {
                _entitlement.setGrantedByRole(b);
                updated();
            }
        }

        public void setSourceDetectedRoles(String sourceDetectablesCsv) {            
            if ( Util.nullSafeCompareTo(_entitlement.getSourceDetectedRoles(), sourceDetectablesCsv) != 0 ) {
                _entitlement.setSourceDetectedRoles(sourceDetectablesCsv);            
                updated();
            }            
        }

        public void setSourceAssignableRoles(String sourceAssignablesCsv) {
            if ( Util.nullSafeCompareTo(_entitlement.getSourceAssignableRoles(), sourceAssignablesCsv) != 0 ) {
                _entitlement.setSourceAssignableRoles(sourceAssignablesCsv);
                updated();
            }            
        }
        
        public void setAnnotation(String ann) {
            if (!Util.nullSafeEq(ann, _entitlement.getAnnotation(), true)) {
                _entitlement.setAnnotation(ann);
                updated();
            }
        }

        public void setType(ManagedAttribute.Type type) {
            if (!Util.nullSafeEq(type, _entitlement.getType(), true)) {
                _entitlement.setType(type);
                updated();
            }
        }

        public void setSource(String source) {
            if (!Util.nullSafeEq(source, _entitlement.getSource(), true)) {
                _entitlement.setSource(source);
                updated();
            }
        }

        public void setAssigned(boolean assigned) {
            if (assigned != _entitlement.isAssigned()) {
                _entitlement.setAssigned(assigned);
                updated();
            }
        }

        public void setStartDate(Date start) {
            if (!Util.nullSafeEq(start, _entitlement.getStartDate(), true)) {
                _entitlement.setStartDate(start);
                updated();
            }
        }

        public void setEndDate(Date end) {
            if (!Util.nullSafeEq(end, _entitlement.getEndDate(), true)) {
                _entitlement.setEndDate(end);
                updated();
            }
        }

        /**
         * Set the grantedByRole flag, update the source detected role list and the allowed by list.
         * 
         * If any of the values for the entitlement change return true to indicate the entitlement is dirty and has
         * to be saved.
         */
        public void setGrantedByRole(String roleName, List<String> allowedBy) {
            if ( !_entitlement.isGrantedByRole() ) {
                _entitlement.setGrantedByRole(true);
                updated();
            }            
            addSourceDetectedRole(roleName);            
            if ( allowedBy != null ) {
                for ( String allowed : allowedBy ) {
                    addSourceAssignableRole(allowed);
                }
            }
        }
     
        /**
         * Set the native identity, instance and application 
         * given a Link object/
         */
        public void setLinkDetails(LinkInterface link) {
            
            if ( link != null ) {
                if ( Util.nullSafeCompareTo(link.getNativeIdentity(), _entitlement.getNativeIdentity()) != 0 ) {                    
                    _entitlement.setNativeIdentity(link.getNativeIdentity());
                    updated();
                }
                if ( Util.nullSafeCompareTo(link.getDisplayableName(), _entitlement.getDisplayName()) != 0 ) {                    
                    _entitlement.setDisplayName(link.getDisplayableName());
                    updated();
                }                        
                if ( Util.nullSafeCompareTo(link.getInstance(), _entitlement.getInstance()) != 0 ) {                    
                    _entitlement.setInstance(link.getInstance());
                    updated();
                }            
            }
        }

        /**
         * Set the entitlement properties based on the role 
         * assignment data. 
         */
        public void setRoleAssignmentDetails(RoleAssignment assignment) {
            if ( assignment != null ) {
                if ( Util.nullSafeCompareTo(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, _entitlement.getName()) != 0 )  {
                    _entitlement.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                    updated();
                }
                
                if ( Util.nullSafeCompareTo(assignment.getRoleName(), _entitlement.getStringValue()) != 0 ) {
                    _entitlement.setValue(assignment.getRoleName());
                    updated();
                }            
                
                if ( Util.nullSafeCompareTo(assignment.getSource(), _entitlement.getSource()) != 0 ) {
                    _entitlement.setSource(assignment.getSource());
                    updated();
                }
                
                if ( Util.nullSafeCompareTo(assignment.getAssigner(), _entitlement.getAssigner()) != 0 ) {
                    _entitlement.setAssigner(assignment.getAssigner());
                    updated();
                }            
                
                if ( !Util.nullSafeEq(assignment.getStartDate(), _entitlement.getStartDate(), true) ) {
                    _entitlement.setStartDate(assignment.getStartDate());
                    updated();
                }
                
                if ( !Util.nullSafeEq(assignment.getEndDate(), _entitlement.getEndDate(), true) ) {
                    _entitlement.setEndDate(assignment.getEndDate());
                    updated();
                }

                if (!Util.nullSafeEq(assignment.getAssignmentId(), _entitlement.getAssignmentId(), true)) {
                    _entitlement.setAssignmentId(assignment.getAssignmentId());
                    updated();
                }

                if (!Util.nullSafeEq(assignment.getComments(), _entitlement.getAssignmentNote(), true)) {
                    _entitlement.setAssignmentNote(assignment.getComments());
                    updated();
                }
            }
        }
        
        /**
         * Set the entitlement properties based on the role
         * detection information. 
         */
        public void setRoleDetectionDetails(RoleDetection detection) {
            setRoleDetectionDetails(detection, null);
        }

        /**
         * Set the entitlement properties based on the role
         * detection information.
         */
        public void setRoleDetectionDetails(RoleDetection detection, RoleRequest roleRequest) {
            if ( detection != null ) {            
                if ( Util.nullSafeCompareTo(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES, _entitlement.getName()) != 0 ) {
                    _entitlement.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
                    updated();
                }                
                if ( !_entitlement.isAssigned() ) {
                    _entitlement.setAssigned(false);
                    updated();
                }
                
                if ( Util.nullSafeCompareTo(_entitlement.getStringValue(), detection.getRoleName()) != 0 ) {
                    _entitlement.setValue(detection.getRoleName());
                    updated();
                }

                // allow nulls to be equal for assignment id
                if (!Util.nullSafeEq(_entitlement.getAssignmentId(), detection.getFirstAssignmentId(), true)) {
                    _entitlement.setAssignmentId(detection.getFirstAssignmentId());
                    updated();
                }

                if (roleRequest != null) {
                    if (!Util.nullSafeEq(_entitlement.getStartDate(), roleRequest.getStartDate(), true)) {
                        _entitlement.setStartDate(roleRequest.getStartDate());
                        updated();
                    }

                    if (!Util.nullSafeEq(_entitlement.getEndDate(), roleRequest.getEndDate(), true)) {
                        _entitlement.setEndDate(roleRequest.getEndDate());
                        updated();
                    }
                }
            }
        }
        
        /**
         * Method to add a detectable role name to the entitlement;
         * 
         * @return true if the role was added, false if it already existed in the list
         */
        public boolean addSourceDetectedRole(String roleName) {
            boolean updated = false;
            if ( roleName != null ) { 
                String sourceDetectedRoles = _entitlement.getSourceDetectedRoles();
                if ( sourceDetectedRoles == null ) {
                    setSourceDetectedRoles(roleName);
                    updated();
                } else {
                    List<String> list = Util.csvToList(sourceDetectedRoles);
                    if ( list == null ) 
                        list = new ArrayList<String>();
                    if ( !list.contains(roleName)) {
                        list.add(roleName);
                        setSourceDetectedRoles(Util.listToCsv(list));
                        updated();
                    }
                }
            }
            return updated;
        }
        
        /**
         * Method to add a assignable role name to the entitlement;
         * 
         * @return true if the role was added, false if it already existed in the list
         */
        public boolean addSourceAssignableRole(String roleName ) {
            boolean updated = false;
            if ( roleName != null ) { 
                String sourceAssignableRoles = _entitlement.getSourceAssignableRoles();
                if ( sourceAssignableRoles == null ) {
                    setSourceAssignableRoles(roleName);
                    updated();
                } else {
                    List<String> list = Util.csvToList(sourceAssignableRoles);
                    if ( list == null )  
                        list = new ArrayList<String>();
                    
                    if ( !list.contains(roleName) ) {
                        updated();
                        list.add(roleName);                
                    }
                    setSourceAssignableRoles(Util.listToCsv(list));
                }
            }
            return updated;
        }
    }

    protected Attributes<String, Object> getArguments() {
        return _arguments;
    }
    
    protected class EntitlementFinder {
        private Map<EntitlementKey, IdentityEntitlement> entitlementMap;
        EntitlementFinder(List<IdentityEntitlement> entitlements) {
            entitlementMap = new HashMap<EntitlementKey, IdentityEntitlement>();
            for (IdentityEntitlement entitlement : Util.iterate(entitlements)) {
                entitlementMap.put(new EntitlementKey(entitlement), entitlement);
            }
        }

        IdentityEntitlement findEntitlement(EntitlementKey key) {
            return entitlementMap.get(key);
        }

        int getEntitlementCount() {
            return entitlementMap.size();
        }
    }
    
    protected class EntitlementKey {
        private String applicationName;
        private String nativeIdentity;
        private String instance;
        private String name;
        private String value;
        
        EntitlementKey(AttributeAssignment assignment) {
            applicationName = assignment.getApplicationName();
            nativeIdentity = assignment.getNativeIdentity();
            instance = assignment.getInstance();
            name = assignment.getName();
            value = assignment.getStringValue();
        }

        EntitlementKey(IdentityEntitlement entitlement) {
            applicationName = entitlement.getAppName();
            nativeIdentity = entitlement.getNativeIdentity();
            instance = entitlement.getInstance();
            name = entitlement.getName();
            value = entitlement.getStringValue();
        }

        EntitlementKey(String applicationName, String nativeIdentity, String instance, String name, String value) {
            this.applicationName = applicationName;
            this.nativeIdentity = nativeIdentity;
            this.instance = instance;
            this.name = name;
            this.value = value;
        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((applicationName == null) ? 0 : applicationName.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((instance == null) ? 0 : instance.hashCode());
            result = prime * result + ((nativeIdentity == null) ? 0 : nativeIdentity.toUpperCase().hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EntitlementKey other = (EntitlementKey) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (applicationName == null) {
                if (other.applicationName != null)
                    return false;
            } else if (!applicationName.equals(other.applicationName))
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (instance == null) {
                if (other.instance != null)
                    return false;
            } else if (!instance.equals(other.instance))
                return false;
            if (nativeIdentity == null) {
                if (other.nativeIdentity != null)
                    return false;
            } else if (!Util.nullSafeCaseInsensitiveEq(nativeIdentity, other.nativeIdentity))
                return false;
            if (value == null) {
                if (other.value != null)
                    return false;
            } else if (!value.equals(other.value))
                return false;
            return true;
        }
        private AbstractEntitlizer getOuterType() {
            return AbstractEntitlizer.this;
        }
    }
}
