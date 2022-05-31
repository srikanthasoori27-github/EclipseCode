/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.LazyInitializationException;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Schema;

public class ProfileUtil {
    private static final Log log = LogFactory.getLog(ProfileUtil.class);
    
    public static List<AttributeDefinition> getAppAttributeDefinitions(ProfileDTO profile) {
        log.debug("Getting app attribute definitions.");
        List<AttributeDefinition> appAttributeDefinitions = new ArrayList<AttributeDefinition>();

        if (profile != null) {
            Application app = profile.getApplication();
            if (null != app) {
                try {
                    Schema appSchema = app.getSchema(Connector.TYPE_ACCOUNT);
                    if (appSchema != null) {
                        appAttributeDefinitions = appSchema.getAttributes();
                    }
                } catch (LazyInitializationException e) {
                    log.error("For some reason this profile wasn't loaded properly.  Profile id: " + profile.getUid(), e);
                }
            }
        }

        log.debug("Returning appAttributeDefinitions: " + appAttributeDefinitions.toString());

        return appAttributeDefinitions;
    }  // getAppAttributeDefinitions()
    
    public static AttributeDefinition getAppAttributeDefinition(String attributeName, ProfileDTO profile) {
        if (attributeName != null){
            for(AttributeDefinition def : getAppAttributeDefinitions(profile) ){
                if (def.getName().equals(attributeName)) {
                    return def;
                }
            }
        }
        return null;
    }

}
