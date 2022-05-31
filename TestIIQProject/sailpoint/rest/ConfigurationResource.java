/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Right;
import sailpoint.object.RightConfig;
import sailpoint.tools.GeneralException;

/**
 * Datasource for Permission combo box components.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
@Path("config")
public class ConfigurationResource extends BaseResource {

    public static String URL = "/rest/config/rights/";

    @GET
    @Path("rights")
    public ListResult getRightConfig()
        throws GeneralException {
    	
    	authorize(new AllowAllAuthorizer());

        List<Map<String, String>> rightsList = new ArrayList<Map<String, String>>();

        RightConfig conf =
            getContext().getObjectByName(RightConfig.class, RightConfig.OBJ_NAME);
        
        List<Right> rights = conf.getRights();
        if (rights != null){
            for (Right availableRight : rights) {
                Map<String, String> map = new HashMap<String, String>();
                map.put("description", availableRight.getDescription());
                map.put("weight", String.valueOf(availableRight.getWeight()));
                map.put("name", availableRight.getName());
                
                // for select components
                map.put("id", availableRight.getName());
                String displayableName = availableRight.getDisplayName() != null ? availableRight.getDisplayName() :
                    availableRight.getName();
                map.put("displayName", displayableName);
               
                rightsList.add(map);
            }
        }

        return new ListResult(rightsList, rightsList.size());
    }
}