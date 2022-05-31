/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.suggest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditConfig.AuditAction;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Classification;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * @author: jonathan.bryant@sailpoint.com
 */
public class SuggestHelper {

    private static Log log = LogFactory.getLog(SuggestHelper.class);

    private static List<Class> DISPLAYABLE_NAME_CLASSES =
            Arrays.asList(Bundle.class, ManagedAttribute.class, Classification.class);

    public static List<Map<String, Object>> getSuggestResults(Class spClass, QueryOptions options, 
                                                              SailPointContext context)
            throws GeneralException {

        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();

        List<String> projectionColumns = getProjectionColumns(spClass);
        if (! projectionColumns.contains("id")) {
            projectionColumns.add("id");
        }    
        if (! projectionColumns.contains("name")) {
            projectionColumns.add("name");
        }

        Iterator<Object[]> results = context.search(spClass, options, projectionColumns);
        if (results != null){
            while(results.hasNext()){
                out.add(getResultMap(spClass, results.next()));
            }
        }

        return out;
    }

    public static Map<String, Object> getSuggestObject(Class spClass, String idOrName,
                                                              SailPointContext context)
            throws GeneralException {

        List<String> projectionColumns = getProjectionColumns(spClass);
        if (! projectionColumns.contains("id")) {
            projectionColumns.add("id");
        }
        if (! projectionColumns.contains("name")) {
            projectionColumns.add("name");
        }

        Filter nameFilter = Filter.eq("name", idOrName);
        QueryOptions ops;
        if (ObjectUtil.isUniqueId(idOrName)) {
            //Presumably an ID, but we'll check name or ID
            ops = new QueryOptions(Filter.or(Filter.eq("id", idOrName), nameFilter));
        } else {
            //Not an id, only use name. Prevent DB2 -302 error when value length > 32
            ops = new QueryOptions(nameFilter);
        }

        // if we're searching for identity, allow workgroups as well.
        if (Identity.class.equals(spClass)){
            ops.addFilter(Filter.or(Filter.eq("workgroup", true), Filter.eq("workgroup", false)));
        }

        Iterator<Object[]> results = context.search(spClass, ops, projectionColumns);
        if (results != null){
            if(results.hasNext()){
                return getResultMap(spClass, results.next());
            }
        }

        return null;
    }

    public static Map<String, Object> getSuggestColumnValue(Class spClass,
                                                            String column, Object value, SailPointContext context)
            throws GeneralException {

        if (value != null && !"null".equals(value)){
            Map<String, Object> columnObj = new HashMap<String, Object>();
            String displayName = getColumnDisplayName(context, spClass, column, value, null);
            columnObj.put("id", value);
            columnObj.put("displayName",displayName != null ? displayName : value);
            return columnObj;
        }

        return null;
    }

    public static Map<String, Object> getSuggestColumnValue(Class spClass,
            String column, Object value, SailPointContext context, Locale locale)
                    throws GeneralException {
    
        if (value != null && !"null".equals(value)){
            Map<String, Object> columnObj = new HashMap<String, Object>();
            String displayName = getColumnDisplayName(context, spClass, column, value, locale);
            displayName = displayName == null ? value.toString() : displayName;
            columnObj.put("id", value);
            columnObj.put("displayName",displayName != null ? displayName : value);
            return columnObj;
        }
        
        return null;
    }

    private static List<String> getProjectionColumns(Class clazz){
        List<String> projectionColumns = new ArrayList<String>();
        projectionColumns.add("id");
        projectionColumns.add("name");
        
        if (Scope.class.equals(clazz)) {
            projectionColumns.add("displayName");
        } else if (Identity.class.equals(clazz)){
            projectionColumns.add("displayName");
            projectionColumns.add("firstname");
            projectionColumns.add("lastname");
            projectionColumns.add("email");
            projectionColumns.add("workgroup");
            projectionColumns.add("managerStatus");
        } else if (Classification.class.equals(clazz)) {
            projectionColumns.add("displayableName");
            projectionColumns.add("origin");
        } else if (DISPLAYABLE_NAME_CLASSES.contains(clazz)) {
            projectionColumns.add("displayableName");
        }

        return projectionColumns;
    }

    public static Map<String, Object> getResultMap(Class clazz, Object[] row){
        String id = (String)row[0];
        String name = (String)row[1];
        String displayName = null;
        if (row.length > 2) {
            displayName = (String)row[2];
        }

        Map<String, Object> values = new HashMap<String, Object>();
        values.put("id", id != null ? id : "");
        values.put("name", name != null ? name : "");
        if (name == null) {
            name = "";
        }
        values.put("displayName", displayName != null ? displayName : name);
        
        if (Identity.class.equals(clazz)){
            if (row[2] != null)
                values.put("displayName", row[2]);
            values.put("firstname", row[3] != null ? (String)row[3] : "");
            values.put("lastname", row[4] != null ? (String)row[4] : "");
            values.put("email", row[5] != null ? (String)row[5] : "");
            values.put("icon", (Boolean)row[6] ? "groupIcon" : "userIcon");
            values.put("isWorkgroup", (Boolean)row[6]);
            values.put("emailclass", row[5]!=null ? "email" : "noEmail");              
            
            /** For backwards compatibility **/
            values.put("displayableName", row[2] != null ? (String)row[2] : name);
            values.put("managerStatus", row[7]);
            
        } else if (Classification.class.equals(clazz)) {
            values.put("origin", row[3] == null ? "" : row[3]);
        }

        return values;
    }

    /**
     * Helper method to add default orderings for the specified class to the specified QueryOptions
     * @param spClass Class of the object for which a search is being made -- i.e. Bundle, Identity, Managed Attribute
     * @param qo QueryOptions to which the orderings are being added
     */
    public static void addDefaultOrderings(Class<? extends SailPointObject> spClass, QueryOptions qo) {
        if (DISPLAYABLE_NAME_CLASSES.contains(spClass)) {
            qo.addOrdering("displayableName", true);
        } else if (spClass == Identity.class) {
            qo.addOrdering("displayName", true);
        } else {
            qo.addOrdering("name", true);
        }

        qo.addOrdering("id", true);
    }

    public static String extractNameOrIdFromSuggestObject(Object suggestObject) {
        String newValue = null;

        if (suggestObject instanceof Map) {
            Map valueMap = (Map)suggestObject;
            if (valueMap.containsKey("name")) {
                newValue = Util.getString(valueMap, "name");
            } else {
                newValue = Util.getString(valueMap, "id");
            }
        } else if (suggestObject instanceof String) {
            newValue = (String) suggestObject;
        }

        return newValue;
    }

    /**
     * In some cases we may want to get display names for column values. This provides a hook
     * to add a lookup for your class/column combination.
     *
     * todo If this gets any more cases, a more sophisticated
     * approach might be warranted here
     * @param locale 
     * @throws GeneralException 
     */
    private static String getColumnDisplayName(SailPointContext context, Class clazz, String column, Object value, Locale locale) throws GeneralException{

        if (column != null){
            if (clazz.equals(Bundle.class) && "type".equals(column) && value != null){
                ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
                List<RoleTypeDefinition> defs =
                        (List<RoleTypeDefinition>)conf.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);
                if (defs != null){
                    for(RoleTypeDefinition def : defs){
                        if (value.equals(def.getName())){
                            return def.getDisplayableName();
                        }
                    }
                }
            } else if (clazz.equals(Identity.class) && "type".equals(column) && value != null) {
                ObjectConfig conf = ObjectConfig.getObjectConfig(clazz);
                IdentityTypeDefinition def = conf.getIdentityType((String) value);
                if (def != null){
                    return new Message(def.getDisplayableName()).getLocalizedMessage(locale, null);
                }
            } else if (clazz.equals(AuditEvent.class) && "action".equals(column) && value != null) {
                // IIQSAW-1243 -- convert AuditAction name to displayName
                AuditConfig config = context.getObjectByName(AuditConfig.class, AuditConfig.OBJ_NAME);
                AuditAction action = config.getAuditAction(value.toString());
                if (action != null) {
                    String displayKey = action.getDisplayName();
                    if (displayKey != null) {
                        return new Message(displayKey).getLocalizedMessage(locale, null);
                    }  else {
                        return null;
                    }
                }
            }
        }

        return null;
    }

    public static Filter getQueryFilter(Class spClass, String query) {
        String queryProperty = "name";
        if (DISPLAYABLE_NAME_CLASSES.contains(spClass)) {
            queryProperty = "displayableName";
        }

        return Filter.ignoreCase(Filter.like(queryProperty, query, Filter.MatchMode.START));
    }

    /**
     * Validates the class and the column are valid for suggests
     * @param context SailPointContext
     * @param clazz Class to check
     * @param column Name of the column to check, can be dot notation
     * @return True if valid, False if invalid due to blacklist
     * @throws GeneralException
     */
    public static boolean isValidColumn(SailPointContext context, Class clazz, String column) {
        if (clazz == null) {
            return false;
        }

        try {
            // If this is a dot property, check the first part against the given class first
            // Eg., if this is Identity and column is manager.password, we want to first test Identity.manager, then get the
            // class of manager (which is Identity again) and then test Identity.password.
            String firstColumn = column;
            boolean moreColumns = false;
            int firstDotIndex = column.indexOf('.');
            if (firstDotIndex > 1) {
                firstColumn = column.substring(0, firstDotIndex);
                moreColumns = true;
            }

            Configuration configuration = context.getConfiguration();
            @SuppressWarnings("unchecked")
            Map<String, String> columnSuggestBlacklist = (Map<String, String>) configuration.get(Configuration.COLUMN_SUGGEST_BLACKLIST);
            String blacklist = (columnSuggestBlacklist != null) ? columnSuggestBlacklist.get(clazz.getSimpleName()) : null;

            boolean isValid = Util.isNothing(blacklist) ||
                    !(("*".equals(blacklist)) || Util.nullSafeContains(Util.csvToList(blacklist), firstColumn));

            // If there is more to check, then call again with the class of the first part of the property, and the remainder of the column
            if (isValid && moreColumns) {
                //Look for extended attribute defs in case there is no 'getter.'
                Class newClazz = ObjectUtil.getExtendedAttributeClass(clazz, firstColumn);
                if (newClazz == null) {
                    newClazz = Util.getPropertyType(clazz, firstColumn);
                }
                return isValidColumn(context, newClazz, column.substring(firstDotIndex + 1));
            } else {
                return isValid;
            }
        } catch (GeneralException ex) {
            log.error("Unable to validate column", ex);
            return false;
        }
    }

    /**
     * Take a filter string and compile into Filter, validating the filter properties against blacklist.
     * @param context SailPointContext
     * @param clazz Class of the SailPoint object
     * @param filterString Filter string
     * @return Filter object
     * @throws GeneralException if any filter property in compiled filter is invalid
     */
    public static Filter compileFilterString(SailPointContext context, Class clazz, String filterString) throws GeneralException {
        Filter compiled = Filter.compile(filterString);
        if (!isValidFilter(context, clazz, compiled)) {
            throw new GeneralException("Invalid filterString");
        }

        return compiled;
    }

    /**
     * Validates the class and the filter are valid for suggests
     * @param context SailPointContext
     * @param clazz Class to check
     * @param filter Filter object
     * @return True if valid, False if invalid due to blacklist
     * @throws GeneralException
     */
    public static boolean isValidFilter(SailPointContext context, Class clazz, Filter filter) {
        if (filter instanceof Filter.CompositeFilter) {
            List<Filter> children = ((Filter.CompositeFilter)filter).getChildren();
            if (children != null) {
                return children.stream().allMatch(f -> isValidFilter(context, clazz, f));
            } else {
                return true;
            }
        } else {
            String property = ((Filter.LeafFilter)filter).getProperty();
            if (Util.isNullOrEmpty(property)) {
                return false;
            }

            int firstDot = property.indexOf('.');
            if (firstDot > 0) {
                if (Character.isUpperCase(property.charAt(0))) {
                    // If this filter is joining something, the property can be a class name
                    Class propClass = Util.getSailPointObjectClass(property.substring(0, firstDot));

                    if (null != propClass) {
                        return isValidColumn(context, propClass, property.substring(firstDot +1));
                     } else {
                        return isValidColumn(context, clazz, property);
                     }
                } else {
                    return isValidColumn(context, clazz, property);
                }
            } else {
                return isValidColumn(context, clazz, property);
            }
        }
    }
}
