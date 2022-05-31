/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ApplicationEntitlementWeights;
import sailpoint.object.EntitlementWeight;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * This bean is used to indicate which app is currently being edited by the entitlement 
 * risk score config pages.  If JSF had a proper parameter forwarding mechanism, this
 * wouldn't be necessary.
 * @author Bernie Margolis
 */
public class PermissionsRiskConfigBean extends BaseObjectBean<ScoreConfig> {
    private static Log log = LogFactory.getLog(PermissionsRiskConfigBean.class);
    
    private EditedEntitlementAppBean editedEntitlement;
    
    public PermissionsRiskConfigBean() {
        super();
        
        setScope(ScoreConfig.class);
        
        try {
            if (getObject() == null) {
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                setObject(config);
                setObjectId(config.getId());
            }
            
            editedEntitlement = (EditedEntitlementAppBean) getFacesContext().getApplication().createValueBinding("#{editedEntitlementApp}").getValue(getFacesContext());
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE), null);
            log.error("The database is not accessible right now.", e);
        }
    }
        
    @SuppressWarnings("unchecked")
    public String saveChanges() {
        try {
            ScoreDefinition entitlementsDef = getObject().getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);

            List<EntitlementsBarConfigBean.AppPermission> updatedPermissions = editedEntitlement.getApplication().getPermissions();
                        
            List<ApplicationEntitlementWeights> entitlementWeights = 
                (List<ApplicationEntitlementWeights>) entitlementsDef.getArgument("applicationEntitlementWeights");
            
            String editedApp = editedEntitlement.getApplication().getName();
            ApplicationEntitlementWeights appWeights = getWeightsForApplication(editedApp, entitlementWeights);
            
            // TODO: Not the most optimal solution here (note the n^2), but how many attributes can one really
            // be expected to have for a given application?  If this becomes problematic, however, 
            // it should be redone
            for (EntitlementsBarConfigBean.AppPermission permissionDTO : updatedPermissions) {
                for (EntitlementWeight permissionWeight : appWeights.getWeights()) {
                    if (permissionWeight.getType() == EntitlementWeight.EntitlementType.permission &&
                        permissionWeight.getValue().equals(permissionDTO.getName())) {
                        permissionWeight.setWeight(permissionDTO.getWeight());
                    }
                }
            }
            
            saveAction();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE), null);
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        return "save";
    }
    
    public String cancelChanges() {
        return "cancel";
    }
    
    private ApplicationEntitlementWeights getWeightsForApplication(final String appName, List<ApplicationEntitlementWeights> entitlementWeights) {
        ApplicationEntitlementWeights retval = null;
        
        for (ApplicationEntitlementWeights appWeights : entitlementWeights) {
            if (appWeights.getApplication().getName().equals(appName)) {
                retval = appWeights;
            }
        }
                
        return retval;
    }
}
