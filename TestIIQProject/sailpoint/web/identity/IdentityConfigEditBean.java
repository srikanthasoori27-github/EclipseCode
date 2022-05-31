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
import sailpoint.object.AttributeTarget;
import sailpoint.object.Filter;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Workflow;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityAttributeBean.SourceBean;
import sailpoint.web.identity.IdentityAttributeBean.TargetBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class IdentityConfigEditBean extends BaseAttributeEditBean {
    private static final long serialVersionUID = -5862337617503278827L;
    private static final Log log = LogFactory.getLog(IdentityConfigEditBean.class);
    private static final String ATT_IDENTITY_ATTRIBUTE_BEAN = "IdentityAttributeBean";
    private static final String ATT_ORIGINAL_NAME = "IdentityAttributeName";
    
    private ObjectConfig identityConfig;

    public IdentityConfigEditBean() {
        restore();
        try {
            initialize();
        } catch (GeneralException e) {
            Message errMsg = new Message(Message.Type.Error,
                    MessageKeys.ERR_IDENTITY_CONFIG_CANNOT_BE_FETCHED);
            addMessage(errMsg, null);
            log.error(errMsg.getMessage(), e);
        }
    }
    
    public String save() {
        String result = null;
        
        sourcePriorities = getRequestParameter("configForm:newSourceOrder");
        
        maintainOrder(sourcePriorities);
        
        try {
            ObjectAttribute changedAttribute;
            if (editedAttribute.isNew()) {
                changedAttribute = createAttributeObjFromBean(editedAttribute);
            } else {
                changedAttribute = identityConfig.getObjectAttribute(originalAttributeName);
            }
            
            if (!editedAttribute.getName().equals(originalAttributeName) && Identity.isStandardAttributeName(originalAttributeName)) {
                // User tried to rename a standard attribute that we require
                Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_STANDARD_ATTR_NAME, originalAttributeName);
                addMessage(errMsg, null);
                result = "error";
            } else if (editedAttribute.isNew() || !editedAttribute.getName().equals(originalAttributeName)) {
                final String nameToValidate = editedAttribute.getName();
                
                // make sure the name of the new/edited attribute isn't already taken,...
                ObjectAttribute existingAttribute = identityConfig.getObjectAttribute(nameToValidate);
                if (existingAttribute != null) {
                    Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_DUPLICATE_ATTR_NAME, nameToValidate);
                    addMessage(errMsg, null);
                    result = "error";
                } else if (ObjectUtil.isReservedAttributeName(getContext(), Identity.class, nameToValidate)) {
                 // ...and isn't a reserved attribute name that would cause ambiguity when trying to query later
                    Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_RESERVED_ATTR_NAME, nameToValidate);
                    addMessage(errMsg, null);
                    result = "error";
            	}
            }

            // For new attributes or renames we have to check named columns again
            // editedAttribute.isNamedColumn may be uninitialized or no longer valid
            ExtendedAttributeUtil.PropertyMapping pm = ExtendedAttributeUtil.getPropertyMapping(ObjectConfig.IDENTITY, editedAttribute.getName());
            editedAttribute.setNamedColumn(pm != null && pm.namedColumn);

            changedAttribute.setName(editedAttribute.getName());
            changedAttribute.setDisplayName(WebUtil.safeHTML(editedAttribute.getDisplayName()));
            changedAttribute.setGroupFactory(editedAttribute.isGroupFactory());
            changedAttribute.setExternal(editedAttribute.isExternal());
            changedAttribute.setStandard(editedAttribute.isStandard());
            changedAttribute.setSilent(editedAttribute.isSilent());

            //bug30243 - identity mappings should only supports the dataTypes (String and Identity), but the
            //attribute "active" is the exception and it supports boolean. Seems like we should not expose
            //dataType boolean to the UI ..... so using a special case to avoid updates of types
            //only when the attribute is "inactive" and the type is "boolean". However there is the
            //possibility to have an attribute "inactive" with type "string" hence update the attribute.
            if (!changedAttribute.getName().equalsIgnoreCase("inactive") || !changedAttribute.getType().equalsIgnoreCase("boolean")) {
                changedAttribute.setType(editedAttribute.getType());
            }

            changedAttribute.setEditMode(ObjectAttribute.EditMode.valueOf(editedAttribute.getEditMode()));
            changedAttribute.setListenerRule(resolveByName(Rule.class, editedAttribute.getListenerRule()));
            changedAttribute.setListenerWorkflow(resolveById(Workflow.class, editedAttribute.getListenerWorkflow()));
            changedAttribute.setMulti(editedAttribute.isMulti());
           
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
            else if (editedAttribute.isExtended()) {
                
                int extendedNumber;
                if (ObjectAttribute.TYPE_IDENTITY.equals(editedAttribute.getType())) {
                    extendedNumber = getAvailableExtendedSlotByType(ObjectAttribute.TYPE_IDENTITY, SailPointObject.MAX_EXTENDED_IDENTITY_ATTRIBUTES);
                } else {
                    extendedNumber = getAvailableExtendedSlot();
                }
                
                if (extendedNumber > 0) {
                    changedAttribute.setExtendedNumber(extendedNumber);
                } else {
                    result = "error";
                    Message errMsg = new Message(Message.Type.Error, MessageKeys.ERR_MAX_NUM_ATTRS_REACHED, maxExtendedAttributes);
                    //Make sure to not confuse the user with a giant number it's only currently 5.
                    if (ObjectAttribute.TYPE_IDENTITY.equals(editedAttribute.getType())) {
                        errMsg = new Message(Message.Type.Error, MessageKeys.ERR_MAX_NUM_ATTRS_REACHED, SailPointObject.MAX_EXTENDED_IDENTITY_ATTRIBUTES);
                    }
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
                   // but leave the source around for ui/ordering reasons
                   changedAttribute.setRule(source.getRule());  
                }
                sources[sourceBean.getCurrentOrder()-1] = source;
            }
            changedAttribute.setSources(Arrays.asList(sources));   
            
            //Bug#16374: reset global rule to null if no source defined.
            if (sources.length == 0) {
                changedAttribute.setRule(null);
            }

            // Save the targets.
            List<AttributeTarget> targets = new ArrayList<AttributeTarget>();
            if (!Util.isEmpty(editedAttribute.getTargets())) {
                for (TargetBean targetBean : editedAttribute.getTargets()) {
                    targets.add(targetBean.toTarget(getContext()));
                }
            }
            changedAttribute.setTargets(targets);

            boolean isError = "error".equals(result);
            boolean isValid = validateAttribute(changedAttribute);
            
            if (!isError && isValid) {
                if (editedAttribute.isNew()) {
                    identityConfig.add(changedAttribute);
                } else {
                    identityConfig.replace(originalAttributeName, changedAttribute);
                }
                
                getContext().saveObject(identityConfig);

                // Synch the group factories with the name change if one has occurred
                if (!editedAttribute.isNew() && !editedAttribute.getName().equals(originalAttributeName)) {
                    List<GroupFactory> factoriesToChange = getContext().getObjects(GroupFactory.class, new QueryOptions().add(new Filter [] { Filter.eq("factoryAttribute", originalAttributeName)}));
                    for (GroupFactory factoryToChange : factoriesToChange) {
                        factoryToChange.setFactoryAttribute(editedAttribute.getName());
                        getContext().saveObject(factoryToChange);
                    }
                }
                
                getContext().commitTransaction();
                result = "save";
                clearSession();
            } else if (!isValid) {
                result = "error";
                Message errMsg = new Message(Message.Type.Error,
                    MessageKeys.ERR_ATTR_HAS_INVALID_CHARS);
                addMessage(errMsg, null);
            }
        } catch (GeneralException e) {
            Message errMsg = new Message(Message.Type.Error,
                    MessageKeys.ERR_APPS_NOT_AVAIL_SRCS_NOT_SET);
            addMessage(errMsg, null);
            log.error(errMsg.getMessage(), e);
            result = "error";
        }
        
        return result;
    }

    @SuppressWarnings("unchecked")
    public void initialize() throws GeneralException {
        identityConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        
        //bug#21562
        //load the config to avoid LazyInitializationException,
        //since when rule changed, all attributes get reloaded.
        identityConfig.load();
                
        if (originalAttributeName == null) {
            Map session = getSessionScope();
            
            originalAttributeName = getRequestParameter("configForm:editedObjectId");
            session.put(ATT_ORIGINAL_NAME, originalAttributeName);
            
            if (originalAttributeName == null) {
                editedAttribute = new IdentityAttributeBean();
            } else {                
                for (ObjectAttribute attribute : identityConfig.getObjectAttributes()) {
                    if (attribute.getName().equals(originalAttributeName)) {
                        List<String> factoryNames = new ArrayList<String>();
                        List<GroupFactory> factories = getContext().getObjects(GroupFactory.class, new QueryOptions().add(new Filter[] {Filter.eq("factoryAttribute", originalAttributeName)}));
                        if (factories != null && !factories.isEmpty()) {
                            for (GroupFactory factory : factories) {
                                factoryNames.add(factory.getName());
                            }
                        }
                        
                        editedAttribute = new IdentityAttributeBean(ObjectConfig.IDENTITY, attribute, factoryNames);
                        session.put(ATT_IDENTITY_ATTRIBUTE_BEAN, editedAttribute);
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
            maxExtendedAttributes = systemConfig.getInt(Configuration.IDENTITY_MAX_SEARCHABLE_ATTRIBUTES);
        } catch (NullPointerException e) {
            maxExtendedAttributes = 4;
        }
        */
        maxExtendedAttributes = 1000000;
    }

    /*
    Don't cache any of the rule lists, otherwise new rules created by the
    rule editor while working with the cert schedule can't be detected.
    See bug #5901 for details on the rule editor. - DHC
    */
    public List<SelectItem> getRuleList() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.IdentityAttribute, true);
    }    

    public List<SelectItem> getTargetRuleList() throws GeneralException {
        return WebUtil.getRulesByType(getContext(), Rule.Type.IdentityAttributeTarget, true);
    }
    
    protected ObjectAttribute getAttribute(String name) {
        return identityConfig.getObjectAttribute(name);
    }

    protected List<ObjectAttribute> getExtendedAttributes() {
        return identityConfig.getExtendedAttributeList();

    }
    
}
