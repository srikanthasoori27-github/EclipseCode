/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.CertifiableDescriptor;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;


/**
 * A utility class that does work needed by both the IdentityHistoryResource
 * and the IdentityHistoryExportBean. 
 *
 * @author <a href="mailto:derry.cannon@sailpoint.com">Derry Cannon</a>
 */
public class IdentityHistoryUtil 
    {
    private static final Log log = LogFactory.getLog(IdentityHistoryUtil.class);
    
    
    /**
     * Converts the row returned from the db to the format needed by the 
     * data store on the UI side.  There's a lot of tweaking and fiddling
     * that needs to take place before it's useful to the UI.
     */
    public static Map<String, Object> convertRow(Map<String, Object> map, Object[] row, 
        List<String> cols, Locale locale, SailPointContext context)
        throws GeneralException 
        {
        // load the map with all of the projection columns that aren't in the UI -
        // those got loaded by the caller before calling this method
        for (int i = 0; i < row.length; i++)
            {
            if (!map.containsKey(cols.get(i)))
                map.put(cols.get(i), row[i]);
            }
        
        calculateDescription(map, locale);        
        calculateStatus(map, locale);
        calculateTypeIcon(map, context);
        calculateComments(map);
        calculateActingWorkItem(map);
        
        getGroupDisplayableName(map);

        // make sure that this gets called AFTER calculateTypeIcon()
        localizeCertificationType(map, locale);
        
        // remove anything that doesn't need to go any further downstream
        cleanMap(map);
        
        return map;
        }

    
    /**
     * Simpler version of convertRow() used when getting history by cert item id.
     */
    public static Map<String, Object> convertRow(Map<String, Object> map, Locale locale)
        throws GeneralException 
        {
        calculateComments(map);
        calculateActingWorkItem(map);
        cleanMap(map);
        
        if (map.get("status") == null)
            {
            Message msg = new Message(MessageKeys.LABEL_COMMENT);
            map.put("status", msg.getLocalizedMessage(locale, null));
            }
        
        return map;
        }

    
    /**
     * Creates a modified list of projection columns.
     * 
     * @param cols List of column names
     * 
     * @return Modified list of column names
     * 
     * @throws GeneralException 
     */
    public static List<String> supplementProjectionColumns(List<String> cols) 
        {
        cols.add("type");
        cols.add("action");
        cols.add("certifiableDescriptor");
        cols.add("attribute");
        cols.add("value");
        cols.add("policy");
        cols.add("constraintName");
        cols.add("role");
        
        // this is a grid column that doesn't exist in the db - 
        // we'll calculate it after the search
        cols.remove("description");
        
        return cols;
        }
    
    
    /**
     * Build the query options based on the contents of the given Map. 
     * Where this gets tricky is that the Map might be a MultivaluedMap
     * from the REST service or a parameter values map from the FacesContext,
     * depending on the caller. 
     * 
     * @param params
     * @param qo
     * 
     * @return QueryOptions object
     * 
     * @throws GeneralException
     */
    public static QueryOptions getQueryOptions(Map<String, Object> params, QueryOptions qo, 
        Locale locale) throws GeneralException
        {              
        // filter options        
        if (notEmpty(getFirst(params, "actor")))
            qo.add(Filter.eq("actor", getFirst(params, "actor")));

        if (notEmpty(getFirst(params, "startDate")))
            {
            Long startTime = new Long(getFirst(params, "startDate"));
            qo.add(Filter.gt("entryDate", new Date(startTime.longValue())));
            }

        if (notEmpty(getFirst(params, "endDate")))
            {
            Long endTime = new Long(getFirst(params, "endDate"));

            // people expect the end date to be inclusive, so adjust accordingly
            Calendar endDate = Calendar.getInstance(locale);
            endDate.setTime(new Date(endTime.longValue()));
            endDate.add(Calendar.DATE, 1);
            
            qo.add(Filter.lt("entryDate", endDate.getTime()));
            }

        String type = getFirst(params, "type");
        if ((type != null) && (!type.equals("all")))
            {
            CertificationItem.Type certType = CertificationItem.Type.valueOf(type);
            qo.add(Filter.eq("certificationType", certType));
            
            // Those filters that come in via text fields need to be case-insensitive.
            // Those that come in via other form elements will already exactly
            // match what's in the db, so the case-insensitivity isn't necessary.
            switch(certType)
                {
                case Account:
                    if (notEmpty(getFirst(params, "application")))
                        qo.add(Filter.eq("application",
                            getFirst(params, "application")));

                    if (notEmpty(getFirst(params, "account")))
                        qo.add(Filter.ignoreCase(Filter.like("account",
                            getFirst(params, "account"))));
                case Exception:
                    if (notEmpty(getFirst(params, "application")))
                        qo.add(Filter.eq("application", 
                            getFirst(params, "application")));
                    
                    if (notEmpty(getFirst(params, "account")))
                        qo.add(Filter.ignoreCase(Filter.like("account", 
                            getFirst(params, "account"))));
                    
                    if (notEmpty(getFirst(params, "attribute")))
                        qo.add(Filter.ignoreCase(Filter.like("attribute", 
                            getFirst(params, "attribute"))));
                    
                    if (notEmpty(getFirst(params, "value")))
                        qo.add(Filter.ignoreCase(Filter.like("value", 
                            getFirst(params, "value"))));
                    
                    break;
                case PolicyViolation:
                    if (notEmpty(getFirst(params, "policy")))
                        qo.add(Filter.eq("policy", 
                            getFirst(params, "policy")));
                    
                    if (notEmpty(getFirst(params, "constraintName")))
                        qo.add(Filter.ignoreCase(Filter.like("constraintName", 
                            getFirst(params, "constraintName"))));
                    
                    break;
                case Bundle:
                    if (notEmpty(getFirst(params, "role")))
                        qo.add(Filter.eq("role", getFirst(params, "role")));
                    
                    break;
                default:
                    log.warn("Unsupported type: " + certType.toString());
                }
            }
        
        // if no decisions are selected, the first element in the decisions 
        // list is an empty string, in which case, skip the next block.
        List<String> decisions = getList(params, "decisions");
        if ((decisions != null) && (!decisions.get(0).equals("")))
            {
            // comments don't have a status value, so we have to
            // jump through a few hoops here
            boolean includeComments = decisions.contains("Comments");
            if (includeComments)
                decisions.remove("Comments");
            
            // convert the decisions to actual Status objects - this helps 
            // make the searching less error-prone
            List<CertificationAction.Status> statuses = 
                new ArrayList<CertificationAction.Status>();
            for (String decision : decisions)
                {
                statuses.add(CertificationAction.Status.valueOf(decision));
                }
            
            // now build the filters
            if (includeComments && !statuses.isEmpty())
                {
                Filter status = Filter.in("status", statuses);
                Filter comment = Filter.isnull("status");
                qo.add(Filter.or(status, comment));
                }
            else if (includeComments)
                {
                qo.add(Filter.isnull("status"));
                }
            else 
                {
                qo.add(Filter.in("status", statuses));
                }
            }
        
        return qo;
        }
    
    
    
    /**
     * Calculates the description to display depending on the cert type  
     * of the given row.
     * 
     * @param map Map containing the modified row data
     */
    public static void calculateDescription(Map<String, Object> map, Locale locale)
        {
        CertificationItem.Type type = (CertificationItem.Type)map.get("certificationType");
        switch (type) 
            {
            case Bundle:
                map.put("description", (String)map.get("role"));
                break;
            case AccountGroupMembership: 
            case Exception:
            case Account:
            case DataOwner:
                String description = new String();
                CertifiableDescriptor descriptor =
                    (CertifiableDescriptor) map.get("certifiableDescriptor");
                if (null != descriptor) {
                    EntitlementSnapshot snap = descriptor.getExceptionEntitlements();
                    if (snap != null){
                        Message msg = EntitlementDescriber.summarize(snap);
                        description = (msg != null) ? 
                            msg.getLocalizedMessage(locale, null) : null;
                    }
                }
                map.put("description", description);
        
                break;
            case PolicyViolation:
                map.put("description", (String)map.get("constraintName"));
                break;
            }
        }

    
    /**
     * Comments don't have a status since there's not an action involved.
     * However, we need to display something in that UI column.
     * 
     * @param map Map containing the modified row data
     */
    public static void calculateStatus(Map<String, Object> map, Locale locale) 
        {
        IdentityHistoryItem.Type type = (IdentityHistoryItem.Type)map.get("type");
        if (IdentityHistoryItem.Type.Comment.equals(type))
            {
            Message msg = new Message(MessageKeys.LABEL_COMMENT);
            map.put("status", msg.getLocalizedMessage(locale, null));
            map.put("statusIcon", "comments");
            
            // return here so we don't overwrite the comment status
            // with the action status
            return;
            }
        
        CertificationAction action = (CertificationAction)map.get("action");
        if (null != action) {
            if (CertificationAction.Status.Remediated.equals(action.getStatus()) &&
                action.isRevokeAccount())
                {
                Message msg = new Message(CertificationAction.Status.RevokeAccount.getMessageKey());
                map.put("status", msg.getLocalizedMessage(locale, null));
                map.put("statusIcon", CertificationAction.Status.RevokeAccount.toString());
                }
            else
                {
                Message msg = new Message(action.getStatus().getMessageKey());
                map.put("status", msg.getLocalizedMessage(locale, null));
                map.put("statusIcon", action.getStatus().toString());
                }
        } else {
            log.error("Identity history item of type Decision is missing an action: " + map.get("id"));
            Message msg = new Message(map.get("status").toString());
            map.put("status", msg.getLocalizedMessage(locale, null));
            map.put("statusIcon", map.get("status").toString());
        }
        }

    
    /**
     * Calculate the type icon to use when displaying this row data.
     * 
     * @param map Map containing the modified row data
     * @param context The SailPointContext to use.  This differs depending on 
     *                whether the caller is the REST service or a bean
     * 
     * @throws GeneralException
     */
    private static void calculateTypeIcon(Map<String, Object> map, SailPointContext context) 
        throws GeneralException 
        {
        map.put("typeIcon", map.get("certificationType"));
        
        String role = (String)map.get("role");
        if (role != null)
            {
            Bundle bundle = context.getObjectByName(Bundle.class, role);
            map.put("typeIcon", (bundle == null) ? "" : bundle.getType());
            }
        }    
    
    
    /**
     * Calculate which comment to use: the comment returned from the db,
     * or the comments on the cert action.
     * 
     * @param map Map containing the modified row data
     */
    public static void calculateComments(Map<String, Object> map) 
        {
        IdentityHistoryItem.Type type = (IdentityHistoryItem.Type)map.get("type");
        if (IdentityHistoryItem.Type.Decision.equals(type))
            {
            CertificationAction action = (CertificationAction)map.get("action");
            if (action == null)
                log.error("Identity history item of type Decision is missing an action: " + map.get("id"));
            else
                map.put("comments", action.getComments());
            }
        }
    
    /**
     * Gets the acting work item from the certification action.
     * @param map The map containing modified row data.
     */
    public static void calculateActingWorkItem(Map<String, Object> map)
    {
    	IdentityHistoryItem.Type type = (IdentityHistoryItem.Type)map.get("type");
        if (IdentityHistoryItem.Type.Decision.equals(type)) {    	
	    	CertificationAction action = (CertificationAction)map.get("action");
	    	if (action == null) {
	    		log.error("Identity history item of type Decision is missing an action: " + map.get("id"));
	    	} else {
	    		map.put("actingWorkItem", action.getActingWorkItem());
	    	}
        }
    }


    /**
     * If the value is a group DN, make sure to return something friendlier
     * 
     * @param map
     * @throws GeneralException 
     */
    private static void getGroupDisplayableName(Map<String, Object> map) 
        throws GeneralException 
        {
        String appName = (String)map.get("application");
        String attrName = (String)map.get("attribute");
        if (WebUtil.isGroupAttribute(appName, attrName))
            {
            String value = (String)map.get("value");
            String friendly = WebUtil.getGroupDisplayableName(appName, attrName, value);
            map.put("value", friendly);
            }
        }


    /**
     * The certification type is not localizable, so super.convertRow()
     * can't handle it.  Since we don't want "Bundle" showing up in the UI,...
     * 
     * @param map
     */
    public static void localizeCertificationType(Map<String, Object> map, Locale locale) 
        {
        CertificationItem.Type type = (CertificationItem.Type)map.get("certificationType");
        Message msg = new Message(type.getMessageKey());
        map.put("certificationType", msg.getLocalizedMessage(locale, null));
        }


    /**
     * Remove any data from the map that isn't needed by the caller
     * of the service.  In particular, the entitlement snapshot 
     * creates problems if you try to send it over the wire. 
     * 
     * @param map Map to clean
     */
    public static void cleanMap(Map<String, Object> map)
        {
        map.remove("type");
        map.remove("action");
        map.remove("certifiableDescriptor");
        }
    
    
    /**
     * Utility method to determine that a String is both not null and not empty
     * 
     * @param str String to check
     * 
     * @return True if not null and not empty; false otherwise
     */
    private static boolean notEmpty(String str)
        {
        return (str != null) && (str.length() > 0);
        }
    
    
    /**
     * Utility method to handle the differing Maps we're dealing with.
     * Looks up the first value found for the given key.
     * 
     * @param map Map containing query parameters
     * @param key Key whose first value is being requested
     * 
     * @return String value for the given key if found; null otherwise
     */
    private static String getFirst(Map<String, Object> map, String key)
        {
        Object obj = map.get(key);
        if (obj instanceof List<?>)
            {
            List<?> list = (List<?>)obj;
            return (String)list.get(0);
            }
        else if (obj instanceof String[])
            {
            String[] vals = (String[])obj;
            return (String)vals[0];
            }
        else if (obj instanceof String)
            {
            return (String)obj;
            }
        else
            {
            // should never get here 
            return null;
            }        
        }
    
    
    /**
     * Utility method to return the value associated with the given
     * key as a List.  Depending on the Map passed in, we might have
     * to construct the list from an array of Strings.
     * 
     * @param map Map containing query parameters
     * @param key Key whose value is being requested
     * 
     * @return List containing the requested values; null if not found 
     */
    @SuppressWarnings("unchecked")
    private static List<String> getList(Map<String, Object> map, String key)
        {
        Object obj = map.get(key);
        if (obj instanceof List<?>)
            {
            return (List<String>)obj;
            }
        else if (obj instanceof String[])
            {
            return new ArrayList<String>(Arrays.asList((String[])obj));
            }
        else if (obj instanceof String)
            {
            List<String> list = new ArrayList<String>();
            list.add((String)obj);
            
            return list;
            }
        else
            {
            // should never get here 
            return null;
            }
        }
    }
