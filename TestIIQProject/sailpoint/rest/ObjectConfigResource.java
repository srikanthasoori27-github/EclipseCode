/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.system.ObjectAttributeDTO;
import sailpoint.web.system.ObjectConfigBean;
import sailpoint.web.system.ObjectConfigDTO;
import sailpoint.web.system.ObjectConfigSession;
import sailpoint.web.system.RoleObjectConfigBean;
import sailpoint.web.system.RoleObjectConfigSession;

/**
 * A resource for returning the attributes from the ObjectConfig.
 *
 * @author <a href="mailto:bernie.margolis@sailpoint.com">Bernie Margolis</a>
 */
@Path("objectConfig")
public class ObjectConfigResource extends BaseResource {
    private static Log log = LogFactory.getLog(ObjectConfigResource.class);

    /**
     * Fetch the attributes for the specified ObjectConfig
     * @param configName Name of the ObjectConfig whose attributes are being fetched
     * @return Map containing the grid result for the specified ObjectConfig's attributes
     * @throws GeneralException
     */
    @GET
    @Path("attributes")
    public Map<String, Object> getAttributesGrid(@QueryParam("configName") String configName) throws GeneralException {
    	authorize(new RightAuthorizer(SPRight.FullAccessSystemConfig));
    	
        Map<String, Object> response = new HashMap<String, Object>();
        List<ObjectAttributeDTO> attributes = getAttributes(configName);
        List<Map<String, Object>> attributeRows = new ArrayList<Map<String, Object>>();
        response.put("attributes", attributeRows);
        if (Util.isEmpty(attributes)) {
            response.put("totalCount", 0);
        } else {
            response.put("totalCount", attributes.size());
            for (ObjectAttributeDTO attribute : attributes) {
                Map<String, Object> attributeRow = new HashMap<String, Object>();
                attributeRow.put("id", attribute.getUid());
                attributeRow.put("name", attribute.getDisplayableName(getLocale()));
                attributeRow.put("category", attribute.getCategoryDisplayName(getLocale(), getUserTimeZone()));
                attributeRow.put("description", attribute.getDescription());
                attributeRows.add(attributeRow);
            }
        }

        return response;
    }
    
    /*
     * Return the attributes appropriate to the given config.  This operation is more 
     * complex than one would expect because roles require a specialized RoleObjectConfigSession
     * object.
     */
    private List<ObjectAttributeDTO> getAttributes(String configName) throws GeneralException {
        List<ObjectAttributeDTO> attributeDTOs;
        
        ObjectConfigSession configSession = getConfigSession(configName);
        if (configSession == null) {
            // Fail silently and return nothing
            attributeDTOs = Collections.emptyList();
        } else {
            ObjectConfigDTO dto = configSession.getDto();
            if (dto != null) {
                attributeDTOs = dto.getAttributes();
            } else {
                attributeDTOs = Collections.emptyList();
            }
        }
        
        return attributeDTOs;
    }
    
    /*
     * Find or generate an ObjectConfigSession appropriate to the specfied ObjectConfig
     */
    private ObjectConfigSession getConfigSession(String configName) throws GeneralException {
        ObjectConfigSession configSession;
        ObjectConfig config = getContext().getObjectByName(ObjectConfig.class, configName);

        Class<? extends ObjectConfigSession> configSessionClass;
        String sessionATT;
        
        // Roles have a different ObjectConfigSession from other objects, so we need to 
        // get the attributes from the appropriate one to avoid editing conflicts later
        if ("Bundle".equals(configName)) {
            configSessionClass = RoleObjectConfigSession.class;
            sessionATT = RoleObjectConfigBean.ATT_ROLE_SESSION;
        } else {
            configSessionClass = ObjectConfigSession.class;
            sessionATT = ObjectConfigBean.ATT_SESSION;
        }

        HttpSession currentSession = getSession();
        
        try {
            configSession = (ObjectConfigSession) currentSession.getAttribute(sessionATT);
            
            // Note:  If there is no existing ObjectConfig for this class then fail silently
            // and let this method's consumer deal with the absence of a configSession
            if (configSession == null && config != null) {
                configSession = configSessionClass.getConstructor(ObjectConfig.class).newInstance(config);
                currentSession.setAttribute(sessionATT, configSession);                
            }
            
        } catch (Exception e) {
            // This indicates a serious coding error that should be resolved prior to release.  We should never get here. 
            log.error("Could not invoke the constructor for " + configSessionClass.getName() + " that accepts a single ObjectConfig", e);
            configSession = null;
        }

        return configSession;
    }
}
