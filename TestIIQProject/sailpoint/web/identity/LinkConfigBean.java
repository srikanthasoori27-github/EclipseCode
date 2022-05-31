/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * LinkConfigBean
 * 
 */
package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * This is a backing bean for the Link Configuration page mostly stolen from the 
 * @author Bernie Margolis
 */
public class LinkConfigBean extends BaseAttributeConfigBean<ObjectConfig> {

    private static final Log log = LogFactory.getLog(LinkConfigBean.class);
	
    public LinkConfigBean() throws GeneralException {
        super();
        setScope(ObjectConfig.class);
        initializeAttributes();
    }
    
    public String getObjectConfigName() {
        return ObjectConfig.LINK;
    }

    public String deleteSelectedAttrAction() {
        String result = null;
        
        try {
            ObjectConfig cfg = (ObjectConfig)getObject();
            ObjectAttribute attributeToRemove = cfg.getObjectAttribute(editedAttribute);
            cfg.remove(attributeToRemove);
            getContext().saveObject(cfg);
            getContext().commitTransaction();
            getContext().decache();
            result = "delete";
        } catch (GeneralException e) {
            result = null;
            Message errMsg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ATTR_CANT_BE_DELETED);
            log.error(errMsg.getMessage(), e);
            addMessage(errMsg, null);
        }
        
        return result;
    }
        
    private void initializeAttributes() throws GeneralException {
        attributes.clear();
        ObjectConfig existingConfig = (ObjectConfig)getObject();

        if (existingConfig == null) {
            // Fetch this from persistent storage
            existingConfig = getLinkConfig();
            if (existingConfig == null) {
                // unlike Identity we could bootstrap one since there
                // isn't much in the default one
                throw new GeneralException(MessageKeys.ERR_OBJECT_CONF_NOT_FOUND);
            }
            super.setObject(existingConfig);
        }

        if (existingConfig == null) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_OBJECT_CONF_NOT_FOUND), null);
            return;
        }
        
        final List<ObjectAttribute> identityAttributes = existingConfig.getObjectAttributes();

        if ( identityAttributes != null ) {
        
            for (ObjectAttribute attribute : identityAttributes) {
                if (!attribute.isSystem() && !attribute.getName().equals(getEditedAttribute())) {
                    List<String> groupFactories = new ArrayList<String>();
                    attributes.put(attribute.getName(), new IdentityAttributeBean(ObjectConfig.LINK, attribute, groupFactories));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    /**
     * Override since all of the attributes of Link are considered
     * to be extended.
     */
    public String addAttribute() {
        Map session = getSessionScope();
        IdentityAttributeBean newAttribute = new IdentityAttributeBean();
        newAttribute.setNew(true);
        newAttribute.setExtended(true);
        session.put(ATT_IDENTITY_ATTRIBUTE_BEAN, newAttribute);
        return "add";
    }



}
