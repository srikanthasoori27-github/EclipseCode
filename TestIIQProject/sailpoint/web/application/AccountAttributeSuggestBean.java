/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.application;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONWriter;

import sailpoint.authorization.WebResourceAuthorizer;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;


/**
 * A JSF bean that returns account attributes for a suggest field.  
 * 
 * Note: This is only used by the Managed Attribute edit page so it only displays attributes that are
 * marked as managed.  If anyone needs to use it in a more general purpose fashion this will
 * need to be modified to only filter when a custom request parameter indicates that it should.
 * --Bernie
 *
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
public class AccountAttributeSuggestBean extends BaseBean {

    private static final Log log = LogFactory.getLog(AccountAttributeSuggestBean.class);
    
    /**
     * Default constructor.
     */
    public AccountAttributeSuggestBean() throws GeneralException {
        authorize(new WebResourceAuthorizer("define/groups/editAccountGroup.jsf"));
    }
    
    public String getAttributes() {
        Map requestParams = getRequestParam();
        int start = Util.atoi(getRequestParameter("start"));
        int limit = getResultLimit();
        String query = getRequestParameter("query");
        String appRef = (String) requestParams.get("application");
        
        List<AttributeDefinition> attributeDefs = null;
        String groupAttribute = null;
        try {
            Application app = getContext().getObjectByName(Application.class, appRef);
            Schema accountSchema = app.getSchema(Application.SCHEMA_ACCOUNT);
            if (accountSchema != null) {
                attributeDefs = accountSchema.getAttributes();
                groupAttribute = accountSchema.getGroupAttribute(Application.SCHEMA_GROUP);
            }
        } catch (GeneralException e) {
            log.error("The Account Attributes Suggest Bean was unable to retrieve the account attributes from application " + appRef, e);
        }

        
        StringWriter jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        String attributes;
        
        try {
            jsonWriter.object();

            int totalCount;
            int end = start + limit;
            List<AttributeDefinition> attributesToReturn;
            if (attributeDefs != null && !attributeDefs.isEmpty()) {
                Collections.sort(attributeDefs, BaseAttributeDefinition.getByDisplayableNameComparator(this.getLocale()));
                List<AttributeDefinition> unfilteredDefs = attributeDefs;
                attributeDefs = new ArrayList<AttributeDefinition>();
                for (AttributeDefinition def : unfilteredDefs) {
                    // Only include managed non-group attributes
                    if (def.getName() != null && 
                        def.isManaged() && 
                        ((groupAttribute == null || !def.getName().equals(groupAttribute)))) {
                        // If we have a query apply it
                        if (query != null && query.trim().length() > 0) {
                            if (def.getName().startsWith(query)) {
                                attributeDefs.add(def);
                            }
                        } else {
                            // If there is no query we've already screened sufficiently
                            attributeDefs.add(def);                            
                        }
                    }
                }
                
                totalCount = attributeDefs.size();
                if (end > attributeDefs.size()) {
                    end = attributeDefs.size();
                }

                // Use a sublist of the matches, as specified by the query parameters
                attributesToReturn = attributeDefs.subList(start, end);
            } else {
                totalCount = 0;
                attributesToReturn = new ArrayList<AttributeDefinition>();
            }

            jsonWriter.key("totalCount");
            jsonWriter.value(totalCount);
            
            jsonWriter.key("attributes");
            jsonWriter.array();
            
            for (AttributeDefinition identityAttribute : attributesToReturn) {
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
