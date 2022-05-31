/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for the application risk configuraiton page.
 */

package sailpoint.web;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ScoreConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class ApplicationRiskConfigBean extends BaseObjectBean<ScoreConfig> {

    private static Log log = LogFactory.getLog(ApplicationRiskConfigBean.class);

    public ApplicationRiskConfigBean() {
        super();
        
        try {
            setScope(ScoreConfig.class);
            
            Map requestParams = getRequestParam();
            boolean forceLoad = Util.otob(requestParams.get(FORCE_LOAD));
            
            // Clear out the tab config if necessary
            if (forceLoad) {
            }
            
            if (getObject() == null) {
                ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
                setObject(config);
                setObjectId(config.getId());
            }

            initializeWeights();
        } 
        catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE),
                    e.getMessageInstance());
            log.error("The database is not accessible right now.", e);
        }
    }

    private void initializeWeights() throws GeneralException {
    }

    public String saveChangesAction() {                
        try {
            
            saveAction();

        } 
        catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE),
                    e.getMessageInstance());
            log.error("No changes can be saved right now because the system is offline.", e);
        }
        
        return "save";
    }
    
    public String cancelChangesAction() {
        return "cancel";
    }
    
}
