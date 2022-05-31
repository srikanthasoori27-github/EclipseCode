/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * IdentityConfigBean
 * 
 * Created October 25, 2006
 */
package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Grouper;
import sailpoint.object.Filter;
import sailpoint.object.GroupFactory;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * This is a backing bean for the Identity Configuration page
 * 
 * @author Bernie Margolis
 */
public class IdentityConfigBean extends BaseAttributeConfigBean<ObjectConfig> {
    private static final Log log = LogFactory.getLog(IdentityConfigBean.class);
    
    @SuppressWarnings("unchecked")
    public IdentityConfigBean() throws GeneralException {
        super();
        setScope(ObjectConfig.class);
        attributes = new HashMap<String, IdentityAttributeBean>();
        initializeAttributes();
    }
    
    public String getObjectConfigName() {
        return ObjectConfig.IDENTITY;
    }

    public String deleteSelectedAttrAction() {
        String result = null;
        
        try {
            ObjectConfig cfg = (ObjectConfig)getObject();
            
            ObjectAttribute attributeToRemove = cfg.getObjectAttribute(editedAttribute);
            Set<GroupFactory> groupFactoriesToDelete = new HashSet<GroupFactory>();
            if (attributeToRemove.isGroupFactory()) {
                List<GroupFactory> deletedFactories = 
                    getContext().getObjects(GroupFactory.class, new QueryOptions().add(new Filter[] {Filter.eq("factoryAttribute", editedAttribute)}));
                if (deletedFactories != null && !deletedFactories.isEmpty())
                    groupFactoriesToDelete.addAll(deletedFactories);
            }
            if (groupFactoriesToDelete != null && !groupFactoriesToDelete.isEmpty()) {
                Grouper grouper = new Grouper(getContext());
                for (GroupFactory groupFactoryObj : groupFactoriesToDelete) {
                    grouper.deleteGroupFactory(groupFactoryObj);
                }
            }
            
            cfg.remove(attributeToRemove);
            getContext().saveObject(cfg);
            getContext().commitTransaction();
            getContext().decache();
            result = "delete";
        } catch (GeneralException e) {
            result = null;
            final String errMsg = "Attributes cannot be deleted right now.";
            log.error(errMsg, e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_ATTR_CANT_BE_DELETED), null);
        }
        
        return result;
    }
        
    @SuppressWarnings("unchecked")
    private void initializeAttributes() throws GeneralException {
        attributes.clear();
        
        // The ObjectConfig is not expected to be persisted in the session so we can safely  
        // get a fresh one every time to make sure that we are always viewing the latest
        // version
        ObjectConfig identityConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        setObject(identityConfig);
        
        final List<ObjectAttribute> identityAttributes = identityConfig.getObjectAttributes();
        
        for (ObjectAttribute attribute : identityAttributes) {
            if (!attribute.isSystem() && !attribute.getName().equals(getEditedAttribute())) {
                List<String> groupFactories = new ArrayList<String>();
                if (attribute.isGroupFactory()) {
                    List<GroupFactory> groupFactoryObjs = 
                        getContext().getObjects(GroupFactory.class, new QueryOptions().add(new Filter[] {Filter.eq("factoryAttribute", attribute.getName())}));
                    for (GroupFactory factoryObj : groupFactoryObjs) {
                        groupFactories.add(factoryObj.getName());
                    }
                }
                
                // make sure the display name gets internationalized properly,
                // or it will mess up the sort on the sys config identity atts page.
                IdentityAttributeBean bean = new IdentityAttributeBean(ObjectConfig.IDENTITY, attribute, groupFactories);
                String displayName = Internationalizer.getMessage(bean.getDisplayName(), getLocale());
                if (displayName != null)
                    bean.setDisplayName(displayName);
                
                attributes.put(attribute.getName(), bean);
            }
        }
    }

}
