/**
 * 
 */
package sailpoint.role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.ITRoleMiningTaskResult;
import sailpoint.object.IdentityItem;
import sailpoint.object.Permission;
import sailpoint.object.Schema;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Filter.LogicalOperation;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.mining.EntitlementsJsonObj;

/**
 * @author peter.holcomb
 *
 */
public class MiningService {
    
    private static Log log = LogFactory.getLog(MiningService.class);
    private SailPointContext context;
    
    public MiningService(SailPointContext ctx) {
        this.context = ctx;
    }
    
    /**************************************************************************
     * Comparators
     **************************************************************************/
    public static Comparator<SimplifiedEntitlement> SIMPLIFIED_ENTITLEMENT_COMPARATOR = new Comparator<SimplifiedEntitlement>() {
        public int compare(SimplifiedEntitlement o1, SimplifiedEntitlement o2) {
            int result;
            
            String val1 = o1.getApplicationName();
            String val2 = o2.getApplicationName();
            result = compareStrings(val1, val2);
            
            if (result == 0) {
                val1 = o1.getDisplayName();
                val2 = o2.getDisplayName();
                result = compareStrings(val1, val2);
            }
            
            return result;
        }        
    };

    public static Comparator<ITRoleMiningTaskResult> TASK_RESULT_BY_IDENTIFIER_COMPARATOR = new Comparator<ITRoleMiningTaskResult>() {
        public int compare(ITRoleMiningTaskResult o1, ITRoleMiningTaskResult o2) {
            int result;
            String identifier1 = o1.getIdentifier();
            String identifier2 = o2.getIdentifier();
            if (identifier1 == null) {
                if (identifier2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (identifier2 == null) {
                result = 1;
            } else {
                int id1 = Util.otoi(identifier1.substring("Group".length()));
                int id2 = Util.otoi(identifier2.substring("Group".length()));
                result = id1 - id2;
            }
            
            return result;
        }
    };
    
    public static final Comparator<ITRoleMiningTaskResult> TASK_RESULT_BY_ALL_MATCH_COMPARATOR = new Comparator<ITRoleMiningTaskResult>() {
        public int compare(ITRoleMiningTaskResult o1, ITRoleMiningTaskResult o2) {
            int allMatch1 = o1.getStatistics().getSuperMatches();
            int allMatch2 = o2.getStatistics().getSuperMatches();
            return allMatch1 - allMatch2;
        }
    };
    
    public static final Comparator<ITRoleMiningTaskResult> TASK_RESULT_BY_EXACT_MATCH_COMPARATOR = new Comparator<ITRoleMiningTaskResult>() {
        public int compare(ITRoleMiningTaskResult o1, ITRoleMiningTaskResult o2) {
            int exactMatch1 = o1.getStatistics().getExactMatches();
            int exactMatch2 = o2.getStatistics().getExactMatches();
            return exactMatch1 - exactMatch2;
        }
    };
    
    public static final ByEntitlementComparator TASK_RESULT_BY_ENTITLEMENT_COMPARATOR = new ByEntitlementComparator();
    
    public static Comparator<ITRoleMiningTaskResult> getByEntitlementComparator(SimplifiedEntitlement entitlement) {
        TASK_RESULT_BY_ENTITLEMENT_COMPARATOR.setEntitlement(entitlement);
        return TASK_RESULT_BY_ENTITLEMENT_COMPARATOR;
    }
    
    public static boolean isMultiValuedAttribute(SailPointContext context, String application, String attributeName) throws GeneralException {
        boolean isMulti = false;
        Application app = context.getObjectById(Application.class, application);
        if (app == null) {
            throw new GeneralException("Attribute information could not be determined for the " + attributeName + " attribute on the " + application + " application because the application could not be found.");
        } else {
            Schema schema = app.getAccountSchema();
            if (schema == null) {
                throw new GeneralException("Attribute information could not be determined for the " + attributeName + " attribute on the " + application + " application because the application's schema could not be found.");
            } else {
                AttributeDefinition attributeDef = schema.getAttributeDefinition(attributeName);
                if (attributeDef == null) {
                    throw new GeneralException("Attribute information could not be determined for the " + attributeName + " attribute on the " + application + " application because its attribute definition could not be found.");
                } else {
                    isMulti = attributeDef.isMulti();
                }
            }
        }
        return isMulti;
    }
    
    public static class ByEntitlementComparator implements Comparator<ITRoleMiningTaskResult> {
        private SimplifiedEntitlement entitlement;
        
        public ByEntitlementComparator(){}
        
        public void setEntitlement(SimplifiedEntitlement entitlement) {
            this.entitlement = entitlement;
        }
        
        public int compare(ITRoleMiningTaskResult o1, ITRoleMiningTaskResult o2) {
            int entitlementMatchValue1 = o1.contains(entitlement) ? 1 : -1;
            int entitlementMatchValue2 = o2.contains(entitlement) ? 1 : -1;
            return entitlementMatchValue2 - entitlementMatchValue1;
        }
    }
    
    public static int compareStrings(String string1, String string2) {
        int result;
        
        if (string1 == null && string2 == null) {
            result = 0;
        } else if (string1 == null) {
            result = -1;
        } else if (string2 == null) {
            result = 1;
        } else {
            result = string1.compareTo(string2);
        }
        
        return result;
    }
    
    public static String getDisplayName(final String applicationId, final String name, final String value) {

        return Explanator.getDisplayValue(applicationId, name, value);
    }

    public static EntitlementsJsonObj getEntitlementsJsonObj(Set<SimplifiedEntitlement> entitlementSet, SailPointContext context, Locale locale, boolean readOnly) throws GeneralException {
        Map<String, List<Filter>> attributeEntitlements = new HashMap<String, List<Filter>>();
        Map<String, List<Permission>> permissionEntitlements = new HashMap<String, List<Permission>>();
        
        /* The nested maps maintain targets by application as well as target name and values for multivalued 
         * attributes by attribute name.  Why jump through all these hoops?  The reason is that we need to 
         * collect all matches for multivalued attribute before we can build proper filters for them.  
         * Similarly for Permissions we need to collect all the rights we want to check on the target 
         * before we can build a valid permission.  Sorry about the mess.  --Bernie
         */
        
        Map<String, Map<String, List<String>>> targetRightMap = new HashMap<String, Map<String, List<String>>>();
        Map<String, Map<String, List<String>>> multiValuedAttributeMap = new HashMap<String, Map<String, List<String>>>();
        
        // Reorganize the information by app
        for (SimplifiedEntitlement containedEntitlement : entitlementSet) {
            String appId = containedEntitlement.getApplicationId();
            if (containedEntitlement.isPermission()) {
                Map<String, List<String>> targetRightMapForApp = targetRightMap.get(appId);
                if (targetRightMapForApp == null) {
                    targetRightMapForApp = new HashMap<String, List<String>>();
                    targetRightMap.put(appId, targetRightMapForApp);
                }
                String target = containedEntitlement.getNameOrTarget();
                String right = containedEntitlement.getRightOrValue();
                List<String> rights = targetRightMapForApp.get(target);
                if (rights == null) {
                    rights = new ArrayList<String>();
                    targetRightMapForApp.put(target, rights);
                }
                rights.add(right);
            } else {
                String name = containedEntitlement.getNameOrTarget();
                String value = containedEntitlement.getRightOrValue();
                Map<String, List<String>> multiValuedAttributeMapForApp = multiValuedAttributeMap.get(appId);
                if (multiValuedAttributeMapForApp == null) {
                    multiValuedAttributeMapForApp = new HashMap<String, List<String>>();
                    multiValuedAttributeMap.put(appId, multiValuedAttributeMapForApp);
                }
                
                if (isMultiValuedAttribute(context, appId, name)) {
                    List<String> values = multiValuedAttributeMapForApp.get(name);
                    if (values == null) {
                        values = new ArrayList<String>();
                        multiValuedAttributeMapForApp.put(name, values);
                    }
                    values.add(value);
                } else {
                    List<Filter> constraints = attributeEntitlements.get(appId);
                    if (constraints == null) {
                        constraints = new ArrayList<Filter>();
                        attributeEntitlements.put(appId, constraints);
                    }
                    constraints.add(Filter.eq(name, value));
                }
            }
        }

        // Create filters for multivalued attributes using the values collected in the logic above
        Set<String> appsWithMultiValAttributeEntitlements = multiValuedAttributeMap.keySet();
        if (appsWithMultiValAttributeEntitlements != null && !appsWithMultiValAttributeEntitlements.isEmpty()) {
            for (String app : appsWithMultiValAttributeEntitlements) {
                Map<String, List<String>> multiValuedAttributeMapForApp = multiValuedAttributeMap.get(app);
                Set<String> multiValuedAttributes = multiValuedAttributeMapForApp.keySet();
                if (multiValuedAttributes != null && !multiValuedAttributes.isEmpty()) {
                    for (String multiValuedAttribute : multiValuedAttributes) {
                        List<String> values = multiValuedAttributeMapForApp.get(multiValuedAttribute);
                        if (values != null && !values.isEmpty()) {
                            List<Filter> constraints = attributeEntitlements.get(app);
                            if (constraints == null) {
                                constraints = new ArrayList<Filter>();
                                attributeEntitlements.put(app, constraints);
                            }
                            constraints.add(Filter.containsAll(multiValuedAttribute, values));
                        }
                    }
                }
            }                
        }
        
        // Create Permissions using the targets and rights collected in the logic above
        Set<String> appsWithPermissionEntitlements = targetRightMap.keySet();
        if (appsWithPermissionEntitlements != null && !appsWithPermissionEntitlements.isEmpty()) {
            for (String app : appsWithPermissionEntitlements) {
                Map<String, List<String>> targetRightMapForApp = targetRightMap.get(app);
                Set<String> targets = targetRightMapForApp.keySet();
                if (targets != null && !targets.isEmpty()) {
                    for (String target : targets) {
                        List<String> rights = targetRightMapForApp.get(target);
                        List<Permission> permissions = permissionEntitlements.get(app);
                        if (permissions == null) {
                            permissions = new ArrayList<Permission>();
                            permissionEntitlements.put(app, permissions);
                        }
                        permissions.add(new Permission(rights, target));
                    }
                }
            }
        }

        // Now we can build the entitlements object that will generate our JSON
        EntitlementsJsonObj jsonObj = new EntitlementsJsonObj(attributeEntitlements, permissionEntitlements, context, locale, readOnly);
        return jsonObj;
    }
    
    public static Map<String, String> convertIdentityFiltersToAttributes(List<Filter> identityFilters) {
        Map<String, String> valuesForIdentityFilters = new SafeMap();
        
        /* The identityFilter for atttribute-based filtering is a list with two filters.  
         * The first is a composite filter that contains the identity attributes 
         * (i.e. the part we're interested in) and the second is an 'in' filter 
         * along with a list of the application ids which we are mining.  We can ignore the
         * second part for our purposes here.
         */
        Filter potentialAttributeFilter = identityFilters.get(0);
        if (potentialAttributeFilter instanceof CompositeFilter) {
            CompositeFilter identityAttributeFilter = (CompositeFilter) identityFilters.get(0);
            if (isStandAloneIdentityFilter(identityAttributeFilter)) {
                valuesForIdentityFilters.putAll(getIdentityValueForIdentityFilter(identityAttributeFilter));                
            } else {
                List<Filter> identityAttributes = identityAttributeFilter.getChildren();
                if (identityAttributes != null && !identityAttributes.isEmpty()) {
                    for (Filter identityAttribute : identityAttributes) {
                        if (isIdentityFilter(identityAttribute)) {
                            valuesForIdentityFilters.putAll(getIdentityValueForIdentityFilter((CompositeFilter) identityAttribute));
                        } else {
                            LeafFilter identityAttributeLeaf = (LeafFilter) identityAttribute;
                            valuesForIdentityFilters.put(identityAttributeLeaf.getProperty(), identityAttributeLeaf.getValue().toString());
                        }
                    }
                }
            }
        } else if (potentialAttributeFilter instanceof LeafFilter) {
            // All attribute filters are '==' operations here.  The only other thing we could expect here is an 'in' filter, and we can ignore that
            // because it only applies to the application list.
            if (((LeafFilter) potentialAttributeFilter).getOperation() == LogicalOperation.EQ) {
                valuesForIdentityFilters.put(((LeafFilter)potentialAttributeFilter).getProperty(), ((LeafFilter)potentialAttributeFilter).getValue().toString());
            }
        }

        return valuesForIdentityFilters;
    }
    
    public static Set<SimplifiedEntitlement> convertIdentityItemsToSimplifiedEntitlements(List<IdentityItem> identityItems) {
        Set<SimplifiedEntitlement> simplifiedEntitlements = new HashSet<SimplifiedEntitlement>();
        
        if (identityItems != null && !identityItems.isEmpty()) {
            for (IdentityItem identityItem : identityItems) {

                if (identityItem.isPermission()) {
                    // Add a representation of a class of permisssions for a given app/target combination
                    // TODO: Permission displaynames are messed up because they get broken out into a one-entitlement-per right form. 
                    //       Ignore the managed attribute display names on them for now.
                    simplifiedEntitlements.add(new SimplifiedEntitlement(identityItem.getApplication(), null, null, identityItem.getName(), null, null, null));
                } else {
                    try {
                        String displayName = MiningService.getDisplayName(identityItem.getApplication(), identityItem.getName(), (String)identityItem.getValue());
                        // Add a representation of a class of entitlement attributes for a given app/name/value combination
                        simplifiedEntitlements.add(new SimplifiedEntitlement(identityItem.getApplication(), null, null, identityItem.getName(), (String)identityItem.getValue(), displayName));
                    } catch (ClassCastException e) {
                        log.debug("IdentityItems for the role mining task should have been split into single-attribute SimplifiedEntitlements before being submitted to the task.");
                    } 
                }
            }
        }
        
        return simplifiedEntitlements;
    }

    
    /**
     * This is just like HashMap, except the get never returns null
     * @author Bernie Margolis
     */
    public static class SafeMap extends HashMap<String, String> {
        @Override
        /**
         * Unlike HashMap.get, this function returns a blank String ("")
         * if the specified value is not found in the map.  This is useful
         * in our specific case because we expect for blank values to default to 
         * an empty string
         */
        public String get(Object key) {
            String result = super.get(key);
            if (result == null) {
                result = "";
            }
            return result;
        }
    }
    
    /*
     * This method only applies inside of a composite.  A composite inside a composite is always an identity filter.
     * Outside the composite we have to resort to the measures below
     */
    private static boolean isIdentityFilter(Filter attributeFilter) {
        return attributeFilter instanceof CompositeFilter;
    }
    
    /* 
     * The next two methods are hard to decipher without knowing how identity filters are composited in IT role mining.
     * Here's the code that builds them:
     *  if (value.length == 1) {
     *      filter = Filter.or(
     *          Filter.ignoreCase(Filter.like(attributeName + ".firstname", value[0], MatchMode.START)),
     *          Filter.ignoreCase(Filter.like(attributeName + ".lastname", value[0], MatchMode.START)),
     *          Filter.ignoreCase(Filter.like(attributeName + ".name", value[0], MatchMode.START))
     *      ); 
     *  } else if (value.length == 2) {
     *      filter = Filter.or(
     *          Filter.and(Filter.ignoreCase(Filter.like(attributeName + ".firstname", value[0], MatchMode.START)), Filter.ignoreCase(Filter.like(attributeName + ".lastname", value[1], MatchMode.START))),
     *          Filter.ignoreCase(Filter.like(attributeName + ".name", value[0] + " " + value[1], MatchMode.START)));
     *  } else if (value.length > 2) {
     *      List<Filter> filters = new ArrayList<Filter>();
     *      for (int i = 0; i < value.length; ++i) {
     *          filters.add(Filter.ignoreCase(Filter.like(attributeName + ".firstname", value[i], MatchMode.START)));
     *          filters.add(Filter.ignoreCase(Filter.like(attributeName + ".lastname", value[i], MatchMode.START)));
     *          filters.add(Filter.ignoreCase(Filter.like(attributeName + ".name", value[i], MatchMode.START)));
     *      }
     *      filter = Filter.or(filters);
     *  } else {
     *      filter = null;
     *  }
     */
    private static boolean isStandAloneIdentityFilter(CompositeFilter attributeFilter) {
        boolean isIdentityFilter = true;
        
        for (Filter child : attributeFilter.getChildren()) {
            boolean isPartIdentityFilter;
            if (child instanceof CompositeFilter &&
                ((CompositeFilter)child).getChildren().get(0) instanceof LeafFilter && 
                ((LeafFilter)((CompositeFilter)child).getChildren().get(0)).getOperation() == LogicalOperation.LIKE) {
                isPartIdentityFilter = true;
            } else if (child instanceof LeafFilter && ((LeafFilter)child).getOperation() == LogicalOperation.LIKE) {
                isPartIdentityFilter = true;
            }  else {
                isPartIdentityFilter = false;
            }
            
            isIdentityFilter &= isPartIdentityFilter;
        }
        
        return isIdentityFilter;
    }
    
    private static Map<String, String> getIdentityValueForIdentityFilter(CompositeFilter identityFilter) {
        Map<String, String> returnValue = new HashMap<String, String>();
        List<Filter> subFilters = identityFilter.getChildren();
        int numSubFilters = subFilters.size();
        if (numSubFilters == 3) {
            // Simple single-string name
            String dataProperty = ((LeafFilter)subFilters.get(0)).getProperty();
            int endOfProperty = dataProperty.lastIndexOf(".firstname");
            String property = dataProperty.substring(0, endOfProperty);
            String value = ((LeafFilter)subFilters.get(0)).getValue().toString();
            returnValue.put(property, value);
        } else if (numSubFilters == 2) {
            // Two-parter
            CompositeFilter andFilter = (CompositeFilter)subFilters.get(0);
            String dataProperty = ((LeafFilter)andFilter.getChildren().get(0)).getProperty();
            int endOfProperty = dataProperty.lastIndexOf(".firstname");
            String property = dataProperty.substring(0, endOfProperty);
            String firstname = ((LeafFilter)andFilter.getChildren().get(0)).getValue().toString();
            String lastname = ((LeafFilter)andFilter.getChildren().get(1)).getValue().toString();
            String value = firstname + " " + lastname;
            returnValue.put(property, value);
        } else {
            // More than two parts
            int numParts = numSubFilters / 3;
            StringBuilder valueBuilder = new StringBuilder();
            String dataProperty = ((LeafFilter)subFilters.get(0)).getProperty();
            int endOfProperty = dataProperty.lastIndexOf(".firstname");
            String property = dataProperty.substring(0, endOfProperty);
            
            for (int i = 0; i < numParts; ++i) {
                String part = ((LeafFilter)subFilters.get(i * 3)).getValue().toString();
                valueBuilder.append(part);
                if (i + 1 < numParts) {
                    valueBuilder.append(" ");
                }
            }
            
            returnValue.put(property, valueBuilder.toString());
        }
        
        return returnValue;
    }

}
