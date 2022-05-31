/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.object.AttributeSource;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Rule;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.identity.IdentityAttributeBean.SourceBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class LinkConfigEditBean extends BaseAttributeEditBean {
    private static final long serialVersionUID = 7197388158120614711L;

    private static final Log log = LogFactory.getLog(LinkConfigEditBean.class);
    
    private ObjectConfig linkConfig;
    
    public LinkConfigEditBean() {
        restore();
        try {
            initialize();
        } catch (GeneralException e) {

            Message errMsg = new Message(Message.Type.Error,
                            MessageKeys.ERR_LINK_CONFIG_CANNOT_BE_FETCHED);
            addMessage(errMsg, null);
            log.error(errMsg.getMessage(), e);
        }
    }
   
    public String save() {
        String result = null;
        
        sourcePriorities = getRequestParameter("configForm:newSourceOrder");
        maintainOrder(sourcePriorities);
        
        try {
            ObjectAttribute changedAttribute = null;
            if (editedAttribute.isNew()) {
                changedAttribute = createAttributeObjFromBean(editedAttribute);
            } else {
                changedAttribute = linkConfig.getObjectAttribute(originalAttributeName);
            }
            
            if (editedAttribute.isNew() || !editedAttribute.getName().equals(originalAttributeName)) {
                final String nameToValidate = editedAttribute.getName();
                ObjectAttribute existingAttribute = linkConfig.getObjectAttribute(nameToValidate);
                if (existingAttribute != null) {
                    Message errMsg = new Message(Message.Type.Error,
                            MessageKeys.ERR_DUPLICATE_ATTR_NAME, nameToValidate);
                    addMessage(errMsg, null);
                    result = "error";
                }
                
                if (ObjectUtil.isReservedAttributeName(getContext(), Link.class, nameToValidate)) {
                    Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_RESERVED_ATTR_NAME, nameToValidate);
                    addMessage(errMsg);
                    result = "error";
                }
            }

            // For new attributes or renames we have to check named columns again
            // editedAttribute.isNamedColumn may be uninitialized or no longer valid
            ExtendedAttributeUtil.PropertyMapping pm = ExtendedAttributeUtil.getPropertyMapping(ObjectConfig.LINK, editedAttribute.getName());
            editedAttribute.setNamedColumn(pm != null && pm.namedColumn);

            changedAttribute.setName(editedAttribute.getName());
            changedAttribute.setDisplayName(editedAttribute.getDisplayName());
            changedAttribute.setEditMode(ObjectAttribute.EditMode.valueOf(editedAttribute.getEditMode()));
            changedAttribute.setType(editedAttribute.getType());
            changedAttribute.setMulti(editedAttribute.isMulti());

            // these are mutually exclusive
            if ( editedAttribute.isMulti() ) {
                changedAttribute.setExtendedNumber(0);
                changedAttribute.setEditMode(ObjectAttribute.EditMode.ReadOnly);
            } 
            else if (editedAttribute.isNamedColumn()) {
                // jsl - extended is a misnomer now, it means "searchable"
                // and there are two kinds, extended and named
                // this can't be edited
                changedAttribute.setNamedColumn(true);
                changedAttribute.setExtendedNumber(0);
            }
            else if ( editedAttribute.isExtended() ) {
                int extendedNumber = getAvailableExtendedSlot();
                
                if (extendedNumber > 0) {
                    changedAttribute.setExtendedNumber(extendedNumber);
                } else {
                    result = "error";
                    Message errMsg = new Message(Message.Type.Error,
                            MessageKeys.ERR_MAX_NUM_ATTRS_REACHED, maxExtendedAttributes);
                    addMessage(errMsg, null);
                    log.error(errMsg.getMessage());
                }
            } 
            else {
                changedAttribute.setExtendedNumber(0);
            }
            
            List<SourceBean> sourceBeans = editedAttribute.getMappedSources();

            AttributeSource [] sources = new AttributeSource[sourceBeans.size()];
            for (SourceBean sourceBean : sourceBeans) {
                AttributeSource source = createSourceFromBean(sourceBean);
                if ( sourceBean.isGlobalRule() ) { 
                   // This is a global rule so set it on the attribute, 
                   // but leave the source around for ui/editing reasons
                   changedAttribute.setRule(source.getRule());  
                }
                sources[sourceBean.getCurrentOrder()-1] = source;
            }
            changedAttribute.setSources(Arrays.asList(sources));   
            
            if (sources.length == 0) {
                changedAttribute.setRule(null);
            }
            
            boolean isError = "error".equals(result);
            boolean isValid = validateAttribute(changedAttribute);
            
            if (!isError && isValid) {
                if (editedAttribute.isNew()) {
                    linkConfig.add(changedAttribute);
                } else {
                    linkConfig.replace(originalAttributeName, changedAttribute);
                }
                
                getContext().saveObject(linkConfig);
                getContext().commitTransaction();
                result = "save";
                clearSession();
            } else if (!isValid) {
                result = "error";
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_ATTR_HAS_INVALID_CHARS), null);
            }
        } catch (GeneralException e) {
            Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_APPS_NOT_AVAIL_SRCS_NOT_SET);
            log.error(errMsg.getMessage(), e);
            addMessage(errMsg, null);
            result = "error";
        }
        
        return result;
    }

    public ObjectConfig getLinkConfig() 
        throws GeneralException {
        return getContext().getObjectByName(ObjectConfig.class, ObjectConfig.LINK);
    }

    @SuppressWarnings("unchecked")
    public void initialize() throws GeneralException {
        linkConfig = getLinkConfig();
        if ( linkConfig == null ) { 
            throw new GeneralException("LinkConfig object is missing.");
        }
                
        if (originalAttributeName == null) {
            Map session = getSessionScope();
            
            originalAttributeName = getRequestParameter("configForm:editedObjectId");
            session.put(ATT_ORIGINAL_NAME, originalAttributeName);
            
            if (originalAttributeName == null) {
                editedAttribute = new IdentityAttributeBean();
            } else {                
                List<ObjectAttribute> attrs = linkConfig.getObjectAttributes();
                if ( attrs != null ) { 
                    for (ObjectAttribute attribute : linkConfig.getObjectAttributes()) {
                    if (attribute.getName().equals(originalAttributeName)) {
                            List<String> factoryNames = new ArrayList<String>();
                            editedAttribute = new IdentityAttributeBean(ObjectConfig.LINK, attribute, factoryNames);
                            session.put(ATT_IDENTITY_ATTRIBUTE_BEAN, editedAttribute);
                        }
                    }
                }
            }
        }
        
        if (sourcePriorities == null) {
            sourcePriorities = getRequestParameter("configForm:newSourceOrder");
        }

        // jsl - this is no longer configured, you can have as many as
        // you want and will be instructed to add .hbm.xml mappings
        /*
        Configuration systemConfig = getContext().getConfiguration();
        try {
            maxExtendedAttributes = systemConfig.getInt(Configuration.LINK_MAX_SEARCHABLE_ATTRIBUTES);
        } catch (NullPointerException e) {
            maxExtendedAttributes = 5;
        }
        */
        maxExtendedAttributes = 1000000;
    }    
    protected ObjectAttribute getAttribute(String name) {
        return linkConfig.getObjectAttribute(name);
    }

    protected List<ObjectAttribute> getExtendedAttributes() {
        return linkConfig.getExtendedAttributeList();
    }
    
    /*
    Don't cache any of the rule lists, otherwise new rules created by the
    rule editor while working with the cert schedule can't be detected.
    See bug #5901 for details on the rule editor. - DHC
    */
    public List<SelectItem> getRuleList() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.LinkAttribute, true);
    }    
}
