/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing bean for the audit configuration pages.
 *
 * Author: Jeff
 *
 */

package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditConfig.AuditAction;
import sailpoint.object.AuditConfig.AuditAttribute;
import sailpoint.object.AuditConfig.AuditClass;
import sailpoint.object.AuditConfig.AuditSCIMResource;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Source;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.messages.MessageKeys;

public class AuditConfigBean extends BaseObjectBean<AuditConfig>
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private Map<String, List<AuditAttribute>> attributesByClass;

    private AuditConfig previous;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public AuditConfigBean() throws GeneralException {

        super();
        setScope(AuditConfig.class);

        SailPointContext con = getContext();
        AuditConfig obj = con.getObjectByName(AuditConfig.class, AuditConfig.OBJ_NAME);
        setObject(obj);
        setObjectId(obj.getId());

        syncObjectConfig(Identity.class, "Identity", AuditEvent.IdentityAttributes);
        syncObjectConfig(Link.class, "Link", new String[]{});
        
        //keep a copy of previous value for auditing any change.
        previous = (AuditConfig)obj.deepCopy(((XMLReferenceResolver)con));
    }

    /**
     * Hack to check for attributes in ObjectConfig:Identity that aren't
     * in the AuditConfig.  Saves having to explain how to do this
     * manually.
     */
    private void syncObjectConfig(Class clazz, String className, String[] staticAttrs) throws GeneralException {

        AuditConfig aconfig = getObject();

        // add the defaults (auto upgrade)
        // actually this shouldn't be necessary any more, we've
        // got a merge in upgrade.xml
        for (int i = 0 ; i < staticAttrs.length ; i++) {
            String name = staticAttrs[i];
            AuditAttribute auatt = aconfig.getAuditAttribute(Identity.class, name);
            if (auatt == null) {
                auatt = new AuditAttribute();
                auatt.setClassName(className);
                auatt.setName(name);
                aconfig.add(auatt);
            }
        }

        // add the extended attributes
        // also include standard attributes (manager, email, etc)
        // since we can allow editing those too
        ObjectConfig iconfig = ObjectConfig.getObjectConfig(clazz);
        if (iconfig != null) {
            List<ObjectAttribute> atts = iconfig.getObjectAttributes();
            if (atts != null) {
                for (ObjectAttribute att : atts) {
                    if (att.isExtended() || att.isStandard()) {
                        AuditAttribute auatt = 
                            aconfig.getAuditAttribute(clazz, att.getName());
                        if (att.isEditable() && auatt == null) {
                            auatt = new AuditAttribute();
                            auatt.setClassName(className);
                            auatt.setName(att.getName());
                            // !! avoid display names for now since we won't
                            // see those in the log data?
                            auatt.setDisplayName(att.getDisplayName());
                            aconfig.add(auatt);
                        }
                        else if (!att.isEditable() && auatt != null) {
                            // no longer editable, remove from config
                            aconfig.remove(auatt);
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get list of unique attribute class names. This is used so we
     * can iterate over each class, displaying it's AuditAttributes.
     * @return
     * @throws GeneralException
     */
    public List<String> getAttributeClassNames() throws GeneralException{
        return new ArrayList(getAttributesByClass().keySet());
    }

    /**
     * Gets map of AuditAttributes by the attribute's class name.
     * @return
     * @throws GeneralException
     */
    public Map<String, List<AuditAttribute>> getAttributesByClass() throws GeneralException{

        if (attributesByClass == null){

            attributesByClass = new HashMap<String, List<AuditAttribute>>();

            List<AuditAttribute> attrs = getObject().getAttributes();
            if (attrs != null){
                for(AuditAttribute attr : attrs){
                    if (attributesByClass.containsKey(attr.getClassName())){
                        attributesByClass.get(attr.getClassName()).add(attr);
                    } else {
                        List<AuditAttribute> classAttrs = new ArrayList<AuditAttribute>();
                        classAttrs.add(attr);
                        attributesByClass.put(attr.getClassName(), classAttrs);
                    }
                }
            }
        }

        return attributesByClass;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    public String saveAction() throws GeneralException {
        
        //bug#21640
        auditChange(previous, getObject());

        // note that it is important to call saveObject to get
        // the global cache changed
        getContext().saveObject(getObject());
        getContext().commitTransaction();
        return "success";
    }

    public String cancelAction() { 
        return "cancel"; 
    }

    /**
     * Create an audit even for any value change in the AuditConfig.
     */
    private void auditChange(AuditConfig oldConfig, AuditConfig newConfig) 
        throws GeneralException {

        AuditEvent event = new AuditEvent();
        event.setAction(AuditEvent.AuditConfigChange);
        event.setSource(getLoggedInUserName());
        event.setInterface(Source.UI.toString());
        event.setAttributeName("op");
        event.setAttributeValue(AuditEvent.ActionUpdate);
        
        //actions
        Map<String, String> oldActionMap = new HashMap<String, String>();
        for (AuditAction oldAction : Util.iterate(oldConfig.getActions())) {
           oldActionMap.put(oldAction.getName(), oldAction.toString());
        }
        for (AuditAction newAction : Util.iterate(newConfig.getActions())) {
            if (newAction.isObsolete()) {
                continue;
            }
            String oldActionString = oldActionMap.get(newAction.getName());
            String newActionString = newAction.toString();
            
            if (!Util.nullSafeEq(oldActionString, newActionString)) {
                event.setAttribute(new Message(Message.Type.Info, newAction.getDisplayableName()).toString(), 
                        new Message(Message.Type.Info, MessageKeys.AUDIT_CONFIG_VALUE_UPDATE, 
                                oldActionString, newActionString));
            }
        }

        //classes
        Map<String, String> oldClassMap = new HashMap<String, String>();
        for (AuditClass oldAuditClass: Util.iterate(oldConfig.getClasses())) {
            oldClassMap.put(oldAuditClass.getName(), oldAuditClass.toString());
        }
        for (AuditClass newAuditClass: Util.iterate(newConfig.getClasses())) {
            if (newAuditClass.isObsolete()) {
                continue;
            }
            String oldClassString = oldClassMap.get(newAuditClass.getName());
            String newClassString = newAuditClass.toString();
            
            if (!Util.nullSafeEq(oldClassString, newClassString)) {
                event.setAttribute(new Message(Message.Type.Info, newAuditClass.getDisplayableName()).toString(), 
                        new Message(Message.Type.Info, MessageKeys.AUDIT_CONFIG_VALUE_UPDATE, 
                                oldClassString, newClassString));
            }
        }
        
        //scim resources
        Map<String, String> oldResourceMap = new HashMap<String, String>();
        for (AuditSCIMResource oldAuditResource: Util.iterate(oldConfig.getResources())) {
            oldResourceMap.put(oldAuditResource.getName(), oldAuditResource.toString());
        }
        for (AuditSCIMResource newAuditResource: Util.iterate(newConfig.getResources())) {
            if (newAuditResource.isObsolete()) {
                continue;
            }
            String oldResourceString = oldResourceMap.get(newAuditResource.getName());
            String newResourceString = newAuditResource.toString();
            
            if (!Util.nullSafeEq(oldResourceString, newResourceString)) {
                event.setAttribute(new Message(Message.Type.Info, newAuditResource.getDisplayableName()).toString(), 
                        new Message(Message.Type.Info, MessageKeys.AUDIT_CONFIG_VALUE_UPDATE, 
                                oldResourceString, newResourceString));
            }
        }

        //attributes
        Map<String, String> oldAttributeMap = new HashMap<String, String>();
        for (AuditAttribute oldAttribute: Util.iterate(oldConfig.getAttributes())) {
            oldAttributeMap.put(oldAttribute.getName(), oldAttribute.toString());
        }
        for (AuditAttribute newAttribute: Util.iterate(newConfig.getAttributes())) {
            if (newAttribute.isObsolete()) {
                continue;
            }
            String oldAttributeString = oldAttributeMap.get(newAttribute.getName());
            String newAttributeString = newAttribute.toString();
            
            if (!Util.nullSafeEq(oldAttributeString, newAttributeString)) {
                event.setAttribute(new Message(Message.Type.Info, newAttribute.getDisplayableName()).toString(), 
                        new Message(Message.Type.Info, MessageKeys.AUDIT_CONFIG_VALUE_UPDATE, 
                                oldAttributeString, newAttributeString));
            }
        }
        
        Auditor.log(event);
    }

}
