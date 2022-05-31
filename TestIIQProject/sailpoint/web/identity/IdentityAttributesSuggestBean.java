/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONWriter;

import sailpoint.authorization.WebResourceAuthorizer;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;


/**
 * A JSF bean that returns identity attributes for a suggest field.
 *
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class IdentityAttributesSuggestBean extends BaseBean
{
    private static final Log log = LogFactory.getLog(IdentityAttributesSuggestBean.class);
    
    /**
     * Default constructor.
     */
    public IdentityAttributesSuggestBean() throws GeneralException {
        authorize(new WebResourceAuthorizer("systemSetup/loginConfig.jsf", "systemSetup/quicklinkPopulationsEditor.jsf", "define/roles/roleTabs.jsf"));
    }
    
    public String getAttributes() {
        String attributes;
        
        Map requestParams = getRequestParam();
        String queryVal = (String) requestParams.get("query");
        int start = Util.atoi(getRequestParameter("start"));
        int limit = getResultLimit();
        String userOnlyParam = getRequestParameter("userOnly");
        boolean userAttributesOnly;
        if (userOnlyParam == null || !Boolean.valueOf(userOnlyParam)) {
            userAttributesOnly = false;
        } else {
            userAttributesOnly = true;
        }
        boolean filterSystem = Util.otob(getRequestParameter("filterSystem"));
        
        List<String> attributesToExclude = Util.csvToList((String) requestParams.get("attributesToExclude"));
        
        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            jsonWriter.object();

            ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
            List<ObjectAttribute> identityAttributes;
            if (userAttributesOnly) {
                identityAttributes = identityConfig.getSearchableAttributes();
            } else {
                identityAttributes = identityConfig.getObjectAttributes();
            }
         
            List<ObjectAttribute> eligibleIdentityAttributes = new ArrayList<ObjectAttribute>();
            if (identityAttributes != null) {
                if (attributesToExclude != null) {
                    for (ObjectAttribute identityAttribute : identityAttributes) {
                        if (!attributesToExclude.contains(identityAttribute.getName())) {
                            eligibleIdentityAttributes.add(identityAttribute);
                        }
                    }
                } else {
                    eligibleIdentityAttributes = identityAttributes;
                }
                if (filterSystem) {
                    Iterator<ObjectAttribute> it = eligibleIdentityAttributes.iterator();
                    while (it.hasNext()) {
                        ObjectAttribute identityAttribute = it.next();
                        if (identityAttribute.isSystem()) {
                            it.remove();
                        }
                    }
                }
            }
            
            // Note: The following blocks are admittedly less than optimal, but we don't anticipate very
            // many identity attributes anyways.  
            // Build a list of the matches and sort it
            List<ObjectAttribute> eligibleFilteredIdentityAttributes = new ArrayList<ObjectAttribute>();
            for (ObjectAttribute identityAttribute : eligibleIdentityAttributes) {
                if (identityAttribute.getDisplayableName().toUpperCase().startsWith(queryVal.toUpperCase()) || 
                    identityAttribute.getName().toUpperCase().startsWith(queryVal.toUpperCase())) {
                    eligibleFilteredIdentityAttributes.add(identityAttribute);
                }
            }
            Collections.sort(eligibleFilteredIdentityAttributes, ObjectAttribute.getByDisplayableNameComparator());

            // Use a sublist of the matches, as specified by the query parameters
            int end = start + limit;
            if (end > eligibleFilteredIdentityAttributes.size())
                end = eligibleFilteredIdentityAttributes.size();
            List<ObjectAttribute> attributesToReturn = eligibleFilteredIdentityAttributes.subList(start, end);
            
            int totalCount = eligibleFilteredIdentityAttributes.size();
            
            jsonWriter.key("totalCount");
            jsonWriter.value(totalCount);
            
            jsonWriter.key("attributes");
            jsonWriter.array();
            
            for (ObjectAttribute identityAttribute : attributesToReturn) {
                jsonWriter.object();
                jsonWriter.key("name");
                jsonWriter.value(identityAttribute.getName());
                jsonWriter.key("displayName");
                jsonWriter.value(identityAttribute.getDisplayableName());
                jsonWriter.endObject();
            }
            
            jsonWriter.endArray();
            
            jsonWriter.endObject();
            
            attributes = jsonString.toString();
        } catch (Exception e) {
            log.error("Could not build JSON for identities right now", e);
            attributes = JsonHelper.emptyListResult("totalCount", "attributes");
        } 
        
        log.debug("Returning json: " + jsonString.toString());
        
        return attributes;
    }
}
