/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.IdentityService;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;


/**
 * A JSF bean that returns identities for a suggest field.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentitiesSuggestBean extends BaseBean
{
    private static final Log log = LogFactory.getLog(IdentitiesSuggestBean.class);

    // Need to make this one public for callers of getNameFilter()
    public static final int SUGGEST_TYPE_IDENTITY = 1;
    private static final int SUGGEST_TYPE_MANAGER = 2;
    private static final int SUGGEST_TYPE_IDENTITY_EXTENDED = 1;
    private static final int SUGGEST_TYPE_CAPABILITY = 4;
    
    public static final String IDENTITY_CLASS_EMAIL = "emailclass";
    public static final String IDENTITY_CLASS_NO_EMAIL_VALUE = "noEmail";
    public static final String IDENTITY_CLASS_EMAIL_VALUE = "email";
    
    public static final String IDENTITY_CLASS_ICON = "icon";
    public static final String IDENTITY_CLASS_ICON_GROUP_VALUE = "groupIcon";
    public static final String IDENTITY_CLASS_ICON_USER_VALUE = "userIcon";
    
    private boolean isJsonQuery;
    
    /**
     * Default constructor.
     */
    public IdentitiesSuggestBean() {
        isJsonQuery = false;
    }

    public List<Identity> getIdentities() throws GeneralException
    {
        QueryOptions qo = buildQueryOptions();
        List<Identity> identities = getContext().getObjects(Identity.class, qo);
        return identities;
    }
    
    public String getIdentitiesJSON() throws GeneralException {
        log.debug("Request params: " + getRequestParam().toString());
        isJsonQuery = true;
        QueryOptions qo = buildQueryOptions();
        return jsonForIdentities(qo);
    }
    
    private QueryOptions buildQueryOptions() throws GeneralException {
        IdentityService identitySvc = new IdentityService(getContext());
        @SuppressWarnings("unchecked")
        Map<String, ?> request = getRequestParam();
        if (Util.isNullOrEmpty((String)request.get(IdentityService.SUGGEST_CONTEXT)) && Util.isNullOrEmpty((String)request.get(IdentityService.SUGGEST_ID))) {
            throw new GeneralException("Context or suggestId is required");
        }
        QueryOptions qo = identitySvc.getIdentitySuggestQueryOptions(request, getLoggedInUser());
        if (qo == null) {
            throw new GeneralException("Could not find IdentityFilter for identity suggest");
        }
        
        // A little hackery here.  Search for a request parameter named currentValue.  If we find it 
        // assume that the current request is being used to initialize the suggest and make the filter
        // look only for that value
        // TODO: For now this is only being done for the workgroups page.  We need to take the workgroup
        // Filter out from below and generalize it.  This will be part of the work that need to be done
        // across to convert all our suggests.
        String initValue = (String)request.get("currentValue");
        if ( initValue != null ) {
            qo.add(Filter.eq("id", initValue));
            qo.add(Filter.in("workgroup", Arrays.asList(new Boolean[] {true, false})));
        }
        
        // Let the AJAX request params set the limits if we're going to ultimately return
        // JSON to an EXTJS combobox.  Otherwise, support the old-style suggest component
        if (!isJsonQuery) {
            qo.setResultLimit(8);
        }
        
        return qo;
    }

    /**
     * Construct a Filter to match an Identity's name
     *
     * @param query String of data to search for
     * @param type The type of Identity search
     * @return
     */
    public static Filter getNameFilter(String query, int type) {
        Filter filter = null;

        if (!Util.isNullOrEmpty(query)) {

            String[] parts = query.split(" ");
            List<Filter> filters = new ArrayList<Filter>();

            // Recognize a few possibilities:
            //  1) One part: search for first name OR last name.
            //  2) Two parts: search for first name AND last name (assuming the
            //     first part is the first name and second part is the last name).
            //  3) More than two parts: We're most likely dealing with a
            //     southerner (a la - Bubba Joe Smith) or a last name that has
            //     spaces (a la - Maria Del Rio) ... don't try to get too smart
            //     just OR together first name and last name conditions.
            if (1 == parts.length) {
                filters.add(Filter.ignoreCase(Filter.like("firstname", parts[0], Filter.MatchMode.START)));
                filters.add(Filter.ignoreCase(Filter.like("lastname", parts[0], Filter.MatchMode.START)));
                filters.add(Filter.ignoreCase(Filter.like("name", parts[0], Filter.MatchMode.START)));
                filters.add(Filter.ignoreCase(Filter.like("displayName", parts[0], Filter.MatchMode.START)));

                if (type == SUGGEST_TYPE_IDENTITY_EXTENDED) {
                    filters.add(Filter.ignoreCase(Filter.like("email", parts[0], Filter.MatchMode.START)));
                }
            }
            else if (2 == parts.length) {
                filters.add(Filter.and(Filter.ignoreCase(Filter.like("firstname", parts[0], Filter.MatchMode.START)),
                        Filter.ignoreCase(Filter.like("lastname", parts[1], Filter.MatchMode.START))));
                filters.add(Filter.and(Filter.ignoreCase(Filter.like("firstname", parts[1], Filter.MatchMode.START)),
                        Filter.ignoreCase(Filter.like("lastname", parts[0], Filter.MatchMode.START))));
                filters.add(Filter.ignoreCase(Filter.like("displayName", parts[0] + " " + parts[1], Filter.MatchMode.START)));
            }
            else if (parts.length > 2) {
                for (String part : parts) {
                    filters.add(Filter.ignoreCase(Filter.like("firstname", part, Filter.MatchMode.START)));
                    filters.add(Filter.ignoreCase(Filter.like("lastname", part, Filter.MatchMode.START)));
                    filters.add(Filter.ignoreCase(Filter.like("displayName", part, Filter.MatchMode.START)));
                }
            }
            filter = Filter.or(filters);
        }

        return filter;
    }
    
    private void setJsonQueryOptions(QueryOptions qo) {
        int start = -1;
        String startString = (String) getRequestParam().get("start");
        if (startString != null) {
            start = Integer.parseInt(startString);
        }
        
        if (start > 0) {
            qo.setFirstRow(start);
        }

        
        int limit = getResultLimit();
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
    }
    
    private String jsonForIdentities(QueryOptions qo) throws GeneralException {
        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();

            // Get the raw count first 
            int numIdentityResults = getContext().countObjects(Identity.class, qo);
            
            jsonWriter.key("totalCount");
            jsonWriter.value(numIdentityResults);
            
            Iterator<Object[]> idIterator = null;
            if (numIdentityResults > 0) {
	            // Apply the limits and run a projection query
	            setJsonQueryOptions(qo);
	            //qo.add(Filter.in("workgroup", Arrays.asList(new Boolean[] {true, false})));
	            idIterator = getContext().search(Identity.class, qo, Arrays.asList(new String [] {"id", "name", "firstname", "lastname", "email", "workgroup", "displayName"}));
            }

            jsonWriter.key("identities");
            jsonWriter.array();
            
            if (null != idIterator) {
                while (idIterator.hasNext()) {
                    jsonWriter.object();
                    Object[] idAttribs = idIterator.next();
                    jsonWriter.key("id");
    
                    if (idAttribs[0] != null) {
                        jsonWriter.value(idAttribs[0]);
                    } else {
                        jsonWriter.value("");
                    }
                    
                    jsonWriter.key("name");
                    if (idAttribs[1] != null) {
                        jsonWriter.value(idAttribs[1]);
                    } else {
                        jsonWriter.value("");
                    }
    
                    jsonWriter.key("firstname");
                    if (idAttribs[2] != null) {
                        jsonWriter.value(idAttribs[2]);
                    } else {
                        jsonWriter.value("");
                    }
                    
                    jsonWriter.key("lastname");
    
                    if (idAttribs[3] != null) {
                        jsonWriter.value(idAttribs[3]);
                    } else {
                        jsonWriter.value("");
                    }
                    
                    jsonWriter.key("email");
                    if (idAttribs[4] != null) {
                        jsonWriter.value(idAttribs[4]);
                    } else {
                        jsonWriter.value(getMessage(MessageKeys.NO_EMAIL));
                    }
                    
                    jsonWriter.key(IDENTITY_CLASS_EMAIL);
                    if (idAttribs[4] != null) {
                        jsonWriter.value(IDENTITY_CLASS_EMAIL_VALUE);
                    } else {
                        jsonWriter.value(IDENTITY_CLASS_NO_EMAIL_VALUE);
                    }
                    
                    jsonWriter.key("displayableName");
                    String displayableName = (String)idAttribs[6];
                    if (displayableName == null)
                        displayableName = (String) idAttribs[1];
                    jsonWriter.value(displayableName);

                    //
                    // A string to indicate which icon should be displayed
                    //
                    jsonWriter.key(IDENTITY_CLASS_ICON);
                    if (idAttribs[5] != null) {
                        if ( (Boolean)idAttribs[5] )
                            jsonWriter.value(IDENTITY_CLASS_ICON_GROUP_VALUE);
                        else
                            jsonWriter.value(IDENTITY_CLASS_ICON_USER_VALUE);
                    } else {
                        jsonWriter.value("");
                    }
                    jsonWriter.endObject();
                }
            }
            
            jsonWriter.endArray();
            
            jsonWriter.endObject();
        } catch (JSONException e) {
            throw new GeneralException("Could not build JSON for identities right now", e);
        }
        
        log.debug("Returning json: " + jsonString.toString());
        
        return jsonString.toString();
    }
}
