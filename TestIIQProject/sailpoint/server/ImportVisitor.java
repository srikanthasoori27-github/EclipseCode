/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of the visitor pattern to encapsulate 
 * class-specific logic related to importing XML.
 *
 * Currently this handles a few special cases for testing and demos
 * where we need to import files that contain named references
 * to other objects, and these names must be converted to 
 * Hibernate unique ids.  Necessary because we can't predefine
 * object ids, and we can't create the XML for dependent objects
 * if we don't know what the id will be.  Instead we allow the
 * object to be referenced by name, and fix up the value during 
 * import.
 *
 * I'm not terribly happy with this, but its the most expedient
 * way to solve this, and it is only for the unit tests.
 * 
 * NOTE!!
 *
 * Most of these are written to assume that if the object has an id, that
 * we don't need to delete the children.  This isn't really true, 
 * if you check out an existing object, textually remove a child and
 * check it back in, it will leak.  This is relatively uncommon now,
 * but it does need to be fixed!
 *
 * TODO
 *
 * Similar issues for the following relationships:
 *
 * Profile->Permission
 * Profile->Filter
 *  These are stored as tables of strings, same issues?
 *
 * TaskResult->errors
 * TaskResult->warnings
 * 
 * EmailTemplate->sessionProperties
 *   This should be an XML type.
 *
 * EntitlementGroup->permissions
 * EntitlementSnapshot->permissions
 *   Can this be XML?
 *
 * Rule->signature
 * TaskDefinition->signature
 *
 * RuleRegistry->registry
 *   Can be XML?
 *
 * SODConstraint->left
 * SODConstraint->right
 *   This should ok as long as SODConstraint can be only referenced
 *   indirectly through a Policy.
 *
 * Schema->attributes
 *   Should be ok as long as Schema can't be accessed directly.
 * 
 * WorkItem->comments
 *
 */

package sailpoint.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.ManagedAttributer;
import sailpoint.connector.Connector;
import sailpoint.connector.DefaultApplicationFactory;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.DynamicScope;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.LocalizedAttribute;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectClassification;
import sailpoint.object.Policy;
import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Server;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.Visitor;
import sailpoint.object.WorkItemConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

public class ImportVisitor extends Visitor {
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ImportVisitor.class);

    SailPointContext _context;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ImportVisitor(SailPointContext context) {
        _context = context;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Delete a list of objects.
     * Sigh, can't use List<SailPointObject> here because of the
     * downcasting rule, anyone know a better way?
     *
     * Even though the caller will probably null the reference to the
     * passed list, Hibernate will complain about "deleted object would be
     * re-saved by cascade" if we don't remove them from the list as we
     * delete.    This also gives us an opportunity to selectively
     * delete things that don't have names.
     *
     * UPDATE: The onlyUnnamed flag should only be true if the name
     * is a unique key, otherwise the HibernatePersistenceManager will
     * let it leak when the parent's List is replaced.  I don't remember
     * why this was important, but it doesn't appear to be used any more.
     */
    private void deleteObjects(List objects, boolean onlyUnnamed)
        throws GeneralException {

        if (objects != null && !objects.isEmpty()) {
            // be careful modifying the iterator
            List copy = new ArrayList(objects);
            for (Object o : copy) {
                if (o instanceof SailPointObject) {
                    SailPointObject spo = (SailPointObject)o;
                    if (!onlyUnnamed || spo.getName() == null) {

                        // TODO: Some classes have to have an 
                        // extra level of referencing pruning here
                        // to avoid foreign key constraints, could
                        // try to generalize this into a callback interface.
                        
                        objects.remove(o);
                        _context.removeObject(spo);
                        _context.commitTransaction();
                    }
                }
            }
        }
    }

    private void deleteObjects(List objects) throws GeneralException {
        deleteObjects(objects, false);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Application
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Check existing applications to a template and if not configured
     * attempt to resolve one through the defined template applications.
     * 
     * Check for a single target source, if one exists auto
     * upgrade it to a list. ( change made in 6.0p1 )
     * 
     * If an application has both a list and single value 
     * ignore the single value. The source may not have a name, '
     * if it doesn't add name relative to the application.
     *
     * Move the application group related attributes, if they exist on the application,
     * to the group schema.
     * 
     */
    public void visitApplication(Application application) 
        throws GeneralException {

        String id = application.getId();
        if (id == null) {
            // If the id is null, check to see if we have a match
            Application existing = _context.getObjectByName(Application.class, application.getName());
            if (existing != null) {                
                deleteObjects(existing.getSchemas());
                deleteObjects(existing.getActivityDataSources());
                deleteObjects(existing.getPasswordPolicies());
                //deleteObjects(existing.getTargetSources());
                _context.decache(existing);
            }
        } 
        String templateName = application.getTemplateApplication();
        if ( templateName == null ) {
            templateName = DefaultApplicationFactory.lookupTemplateByConnector(application.getConnector());
            application.setTemplateApplication(templateName);
        }
        
        // djs: fix the template's usage if null default to Create
        List<Form> forms = application.getProvisioningForms();
        for (Form form : Util.iterate(forms)) {
            fixFormUsageCreate(application, forms, form);
            fixFormSchemaObjectType(form);
        }

        // Check for secret types and change them to string
        // At some this may need to be removed
        List<Schema> schemas = application.getSchemas();
        if ( schemas != null ) {
            for ( Schema schema : schemas ) {
                if ( schema != null ) {
                    fixSchemaForApplication(application, schema);
                }
            }
        }
        
        // Move existing single valued targetsource to a list
        TargetSource source = application.getTargetSource();
        if ( source != null  &&  Util.size(application.getTargetSources()) == 0 ) {
            if ( log.isWarnEnabled() ) {
                log.warn("Application ["+application.getName()+"] has a single target source, promoting it to a list of target sources.");
            }
            application.add(source);
            // once promoted remove it 
            application.setTargetSource(null);
        }

        //copy attributes from template to application
        synchSCIM2Attributes(application);

        //Try to update group attributes if we can. Must only have a single group schema
        if(application.getGroupSchemas().size() == 1) {
            // Set the schemaObjectType on the correct AttributeDefinition
            AttributeDefinition groupAtt = application.getGroupAttribute();
            if (groupAtt != null) {
                if (Util.isNullOrEmpty(groupAtt.getSchemaObjectType())) {
                    groupAtt.setSchemaObjectType(Application.SCHEMA_GROUP);
                    application.getSchema(Application.SCHEMA_ACCOUNT).setGroupAttribute(null);
                    if(log.isInfoEnabled()) {
                        log.info("Setting schemaObjectType on AttributeDefinition " + groupAtt.getName() + ", and " +
                                "removing it from the Account Schema attributes.");
                    }
                }
            }

            if(application.getGroupSchema() != null) {
                Schema schema = application.getGroupSchema();
                //We don't have the hierarchy attribute set on the schema, check to see if there is one on the app in the attributes
                if(Util.isNullOrEmpty(schema.getHierarchyAttribute())) {
                    if(Util.isNotNullOrEmpty(application.getStringAttributeValue(Connector.CONFIG_GROUP_HIERARCHY_ATTRIBUTE))) {
                        //If there is a group hierarchy on the app, but not the schema, migrate it to the schema
                        schema.setHierarchyAttribute(application.getStringAttributeValue(Connector.CONFIG_GROUP_HIERARCHY_ATTRIBUTE));
                        if(log.isInfoEnabled()) {
                            log.info("Moving group hierarchy attribute from the application attributes to the group schema " +
                                    "attributes and removing the entry from the application attributes");
                        }
                    }
                }
                application.removeAttribute(Connector.CONFIG_GROUP_HIERARCHY_ATTRIBUTE);

                //Check for GROUPS_HAVE_MEMBERS feature String, if present, we need to move it to the group schema
                if(application.supportsFeature(Application.Feature.GROUPS_HAVE_MEMBERS)) {
                    schema.addFeature(Application.Feature.GROUPS_HAVE_MEMBERS);
                    application.removeFeature(Application.Feature.GROUPS_HAVE_MEMBERS);
                    if(log.isInfoEnabled()) {
                        log.info("Moving GROUPS_HAVE_MEMBERS feature string from the application features to the group " +
                                "schema features.");
                    }
                }

                //Check for the GROUP_MEMBER_ATTRIBUTE in the application attributes
                if(Util.isNotNullOrEmpty(application.getStringAttributeValue(Application.ATTR_GROUP_MEMBER_ATTRIBUTE))) {
                    //If the group_member_attribute is non null, set it on the schema
                    schema.addConfig(Application.ATTR_GROUP_MEMBER_ATTRIBUTE, application.getStringAttributeValue(Application.ATTR_GROUP_MEMBER_ATTRIBUTE));
                    application.removeAttribute(Application.ATTR_GROUP_MEMBER_ATTRIBUTE);
                    if(log.isInfoEnabled()) {
                        log.info("Moving Group Member Attribute from application attributes to the group schema attributes.");
                    }
                }

                //Move the NO_GROUP_PERMISSIONS_PROVISIONING feature if applicable
                if (application.supportsFeature(Application.Feature.NO_GROUP_PERMISSIONS_PROVISIONING)) {
                    //Deprecated NO_GROUP_PERMISSIONS_PROVISIONING in favor of NO_PERMISSIONS_PROVISIONING
                    schema.addFeature(Application.Feature.NO_PERMISSIONS_PROVISIONING);
                    application.removeFeature(Application.Feature.NO_GROUP_PERMISSIONS_PROVISIONING);
                    if(log.isInfoEnabled()) {
                        log.info("Moving NO_GROUP_PERMISSIONS_PROVISIONING feature from application to group schema." +
                        " Renaming the Feature to NO_PERMISSIONS_PROVISIONING");
                    }
                }
            }

        }
    }

    /**
     * Convenience method used to synchronize attributes from the application
     * template to the current application.
     * We are just copying LOAD_BY_SYSCLASSLOADER attribute for SCIM2
     */
    private void synchSCIM2Attributes(Application application) {
        final String SCIM2 = "openconnector.connector.scim2.SCIM2Connector";
        final String CCLASS = "connectorClass";
        final String LOAD_BY_SYSCLASSLOADER = "load-by-sysclassloader";

        Map<String, Object> currentAtt = application.getAttributes();

        //if application has no attributes OR has no connector class attribute OR
        //the class connector is different to SCIM2 OR the LOAD_BY_SYSCLASSLOADER
        //is already there, then return back immediately
        if (Util.isEmpty(currentAtt) || !currentAtt.containsKey(CCLASS) ||
                !currentAtt.get(CCLASS).toString().equalsIgnoreCase(SCIM2) ||
                        currentAtt.containsKey(LOAD_BY_SYSCLASSLOADER)) {
            return;
        }

        Application appTemplate = null;
        try {
            //fetch the application template
            if (application.getTemplateApplication() != null) {
                appTemplate = DefaultApplicationFactory.getTemplateByType(application.getTemplateApplication());
            }
        } catch (GeneralException e) {
            log.warn("Application [" + application.getName() + "] has no default template.", e);
        }

        //break in case there is no default template, the template is
        //pulled from attribute "templateApplication"
        if (appTemplate == null) {
            return;
        }

        Map<String, Object> templateAtt = appTemplate.getAttributes();
        if (!Util.isEmpty(templateAtt)) {
            currentAtt.put(LOAD_BY_SYSCLASSLOADER, templateAtt.get(LOAD_BY_SYSCLASSLOADER));
        }
    }

    private void fixFormUsageCreate(Application application, List<Form> forms, Form form) {
        Form.Type type = form.getType();
        if ( type == null && forms.size() == 1 ) {
            if (log.isWarnEnabled()) {
                log.warn("Application [" + application.getName() + "] has a single provisioning poilcy without a usage configured, setting usage to 'Create'.");
            }
            form.setType(Form.Type.Create);
        }
    }

    private void fixFormSchemaObjectType(Form form) {

        // Previous release upgraders had to convert Usage.CreateGroup etc.
        // to Usage.Create with objectType=group.  That should be done
        // in Template to Form conversion now, but we still have to
        // deal with a missing object type needing to be upgraded to TYPE_ACCOUNT.
        
        if (form.getObjectType() == null) {
            form.setObjectType(Connector.TYPE_ACCOUNT);
        }
    }

    private void fixSchemaForApplication(Application application, Schema schema) {
        fixSchemaAttributes(application, schema);

        fixSchemaFeatures(application, schema);
    }

    private void fixSchemaFeatures(Application application, Schema schema) {
        if (application.supportsFeature(Application.Feature.GROUP_PROVISIONING)) {
            if (Connector.TYPE_GROUP.equals(schema.getObjectType())) {
                moveFeatureFromAppToSchema(application, schema, Application.Feature.GROUP_PROVISIONING, Application.Feature.PROVISIONING);
            }
        }
    }

    private void moveFeatureFromAppToSchema(Application application, Schema schema, Application.Feature applicationFeature, Application.Feature schemaFeature) {
        if (application.supportsFeature(applicationFeature)) {
            if (!schema.supportsFeature(schemaFeature)) {
                schema.addFeature(schemaFeature);
                application.removeFeature(applicationFeature);
                if (log.isInfoEnabled()) {
                    log.info("moving feature " + applicationFeature + "  to schema feature: " + schemaFeature + " for application: " + application.getName());
                }
            } else {
                // both app and schema support feature.
                if (log.isWarnEnabled()) {
                    log.warn("both app and schema support feature: " + applicationFeature);
                }
                application.removeFeature(applicationFeature);
            }
        }
    }

    private void fixSchemaAttributes(Application application, Schema schema) {
        List<AttributeDefinition> defs = schema.getAttributes();
        if ( defs != null ) {
            for ( AttributeDefinition def : defs ) {
               if ( def != null ) {
                   String type = def.getType();
                   if ( Util.nullSafeCompareTo(type, AttributeDefinition.TYPE_SECRET) == 0 ) {
                       if (log.isWarnEnabled())
                           log.warn("Application ["+application.getName()+"] schema attribute ["+def.getName()+"] of type 'secret' was converted to a 'string' type.");
                       def.setType(AttributeDefinition.TYPE_STRING);
                   }
               }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Bundle
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Originally Profiles were shared, but now they are owned
     * by the Bundle in exactly the same way that Links are owned
     * by Identities.
     */
    public void visitBundle(Bundle bundle) 
        throws GeneralException {

        String id = bundle.getId();
        
        if (id == null) {
            Bundle existing = _context.getObjectByName(Bundle.class,
                                                 bundle.getName());
            if (existing != null) {
                deleteObjects(existing.getProfiles());
                //Clean up Classifications too?
                deleteObjects(existing.getClassifications());

                // It is vital that this be removed from the cache
                // or else it seems to linger after the replicate()
                // call and the next transaction will flush it again.
                _context.decache(existing);
            }
        }

        for (ObjectClassification oc : Util.safeIterable(bundle.getClassifications())) {
            oc.setOwnerType(Bundle.class.getSimpleName());
        }


    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certification
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The main problem we have to solve is the "child replace" 
     * problem when the imported objects have neither names or ids.
     * HibernatePersistenceManager will try to stitch the incomming
     * objects with the persistent objects using "replicate" but
     * for that to work we have to be able to locate the existing object.
     *
     * Since CertificationIdentity does not have a name, there is no
     * way for us to associate one with the existing object.  We could
     * guess by position or partial comparison but this is not reliable.
     *
     * What ends up happing is that the top level object is replicated,
     * but the list contains all new objects.  Unfortuantely because
     * we do not use delete-orphan (which has problems of its own) this
     * causes the old CertificationIdentities to leak.  This is especially
     * bad because they retain a foreign key reference to the parent
     * Certification which makes it impossible to delete the Certification,
     * and the CertificationIdentities are no longer reachable.
     *
     * Since the goal here is to replace all of the objects, we will start
     * by first deleting all the current CertificationIdentities so they
     * won't leak.  We commit the transaction after this just to make sure.
     * It is this commit that is one reason why we can't do this down in
     * HibernatePersistenceManager, because importObject is not allowed
     * to auto-commit.  I suppose we could relax that restriction if we
     * decide to push this behavior lower.
     *
     * This is a general problem when importing any parent object that
     * contains child objects that do not have unique names.
     */
    public void visitCertification(Certification cert) 
        throws GeneralException {

        // If the top level cert has an id, then we can assume its
        // been stored once and don't need to mess with it.  Might
        // not want to defer this if we think we can check one out, 
        // edit the contents, and add identities or items.

        // empty the existing object first
        String id = cert.getId();
        if (id == null) {
            // looks like an idless import
            Certification existing = _context.getObjectByName(Certification.class, cert.getName());
            if (existing != null) {

                deleteObjects(existing.getEntities());
                _context.decache(existing);
            }
            
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity
    //  
    //////////////////////////////////////////////////////////////////////

    public void visitIdentity(Identity identity) 
        throws GeneralException {

        String id = identity.getId();
        if (id == null) {
            Identity existing = _context.getObjectByName(Identity.class,
                                                   identity.getName());
            if (existing != null) {
                deleteObjects(existing.getLinks());
                deleteObjects(existing.getExceptions());
                deleteObjects(existing.getMitigationExpirations());

                // these aren't SailPointObjects so we have
                // to commit a null reference?
                Map<String,String> externalAtts = existing.getExternalAttributes();
                if (externalAtts != null && externalAtts.size() > 0) {
                    existing.setExternalAttributes(null);
                    _context.commitTransaction();
                }

                // It is vital that this be removed from the cache
                // or else it seems to linger after the replicate()
                // call and the next transaction will flush it again.
                _context.decache(existing);
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Localized Attribute
    //  
    //////////////////////////////////////////////////////////////////////
    
    public void visitLocalizedAttribute(LocalizedAttribute attribute) 
        throws GeneralException {

        /** If the target id isn't set, look up the id on the sailpoint object based on the class/name and then set it **/
        if(Util.isNullOrEmpty(attribute.getTargetId())) {
            if(!Util.isNullOrEmpty(attribute.getTargetName()) && !Util.isNullOrEmpty(attribute.getTargetClass())) {
                try {
                    Class clz = Class.forName("sailpoint.object."+attribute.getTargetClass());
                    SailPointObject object = (SailPointObject) _context.getObjectByName(clz, attribute.getTargetName());
                    if(object!=null) {
                        attribute.setTargetId(object.getId());
                    }
                } catch(ClassNotFoundException cnfe) {
                    log.error("Class not found for "+attribute.getTargetName()+". Exception: " + cnfe.getMessage(), cnfe);
                }
            }
        }

        // jsl - this one is funny, the SailPointObject.id isn't authoritative
        // here, it is the attribute name.  Normally they will match, but
        // if we're importing from a foreign db we should ignore the ids since
        // nothing references these by id.
        LocalizedAttribute existing = null;
        try {
            existing =_context.getObjectByName(LocalizedAttribute.class, attribute.getName());
        } 
        catch(GeneralException ge) {
        }

        if (existing != null) {
            String extid = attribute.getId();
            if (extid != null && !extid.equals(existing.getId()))
                log.warn("Changing database id for LocalizedAttribute " + 
                         attribute.getName());
            attribute.setId(existing.getId());

            // decache it and let the new one be saved over it
            _context.decache(existing);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Policy
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Similar pattern for Policy to waste the current SODConstraints.
     */
    public void visitPolicy(Policy policy) 
        throws GeneralException {

        String id = policy.getId();
        if (id == null) {
            // looks like an idless import
            Policy existing = _context.getObjectByName(Policy.class, policy.getName());
            if (existing != null) {

                // bug#7012, try to preserve ids if we can match them by name
                // not working yet
                // this doesn't work because Hibernate thinks objects with ids
                // must exist and we delete them below
                // try to fix the persistence layer some day to allow this
                boolean preserveIds = false;
                if (preserveIds) {
                    List neu = policy.getConstraints();
                    if (neu != null) {
                        for (Object el : neu) {
                            BaseConstraint con = (BaseConstraint)el;
                            if (con.getId() == null) {
                                String name = con.getName();
                                if (name != null) {
                                    BaseConstraint match = existing.getConstraint(null, name);
                                    if (match != null) {
                                        con.setId(match.getId());
                                        con.setCreated(match.getCreated());
                                    }
                                }
                            }
                        }
                    }
                    deleteObjects(existing.getSODConstraints());
                    deleteObjects(existing.getActivityConstraints());
                    deleteObjects(existing.getGenericConstraints());
                }
                else {
                    // new way, if we have an existin one, copy the id but don't delete it
                    List toDelete = new ArrayList();
                    correlateConstraints(policy.getSODConstraints(), existing.getSODConstraints(), toDelete);
                    correlateConstraints(policy.getActivityConstraints(), existing.getActivityConstraints(), toDelete);
                    correlateConstraints(policy.getGenericConstraints(), existing.getGenericConstraints(), toDelete);
                    // save the exting one to get the removals from the lists
                    _context.saveObject(existing);
                    _context.commitTransaction();
                    deleteObjects(toDelete);
                }

                // make sure existing is out of the cache when we go to save the new one
                _context.decache();

            }
        }
    }

    /**
     * Helper for visitPolicy that correlates BaseConstraint objects from one of the 
     * three lists.
     */
    private <T extends BaseConstraint> void correlateConstraints(List<T> neu, List<T> old, List toDelete) {

        Map<String,BaseConstraint> oldNames = new HashMap<String,BaseConstraint>();
        Map<String,BaseConstraint> used = new HashMap<String,BaseConstraint>();
        
        for (BaseConstraint oldcon : Util.iterate(old)) {
            if (oldcon.getName() != null) {
                oldNames.put(oldcon.getName(), oldcon);
            }
        }
        
        // correlate objects by name and transfer the ids
        for (BaseConstraint newcon : Util.iterate(neu)) {
            if (newcon.getName() != null) {
                BaseConstraint oldcon = oldNames.get(newcon.getName());
                if (oldcon != null) {
                    if (log.isInfoEnabled())
                        log.info("Correlated constraint: " + oldcon.getName());
                    newcon.setId(oldcon.getId());
                    newcon.setCreated(oldcon.getCreated());
                    used.put(oldcon.getName(), oldcon);
                }
                else if (log.isInfoEnabled()) {
                    log.info("New constraint: " + newcon.getName());
                }
            }
        }

        // anything not correlated goes on the delete list
        if (old != null) {
            ListIterator<T> it = old.listIterator();
            while (it.hasNext()) {
                BaseConstraint oldcon = it.next();
                if (oldcon.getName() == null || used.get(oldcon.getName()) == null) {
                    if (log.isInfoEnabled())
                        log.info("Deleting constraint: " + oldcon.getName());
                    toDelete.add(oldcon);
                    it.remove();
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // TaskDefinition
    //  
    //////////////////////////////////////////////////////////////////////

    public void visitTaskDefinition(TaskDefinition task) 
        throws GeneralException {


        String id = task.getId();
        if (id == null) {
            TaskDefinition existing = _context.getObjectByName(TaskDefinition.class,
                                                         task.getName());
            if (existing != null) {
                WorkItemConfig so = existing.getSignoffConfig();
                if (so != null) {
                    existing.setSignoffConfig(null);
                    // simply setting this to null is NOT enough,
                    // it orphans, have to explicitly delete
                    _context.removeObject(so);
                    // AND you have to save the parent object, not
                    // sure why this is necessary since we dirtied
                    // it with the null set above
                    _context.saveObject(existing);
                    _context.commitTransaction();
                }
                _context.decache(existing);
            }
        }
        
        // if the type is null, resolve the effective type from the parent and use it
        if (task.getType() == null) {
            Type type = task.getEffectiveType();
            if (type != null) {
                task.setType(type);
            }
        }
        
        // same with subType
        if (task.getSubType() == null) {
            Message subType = task.getEffectiveSubType();
            if (subType != null) {
                task.setSubType(subType.getKey());
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ManagedAttribute
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Upgrade purview to Application.
     *
     * Sigh...almost identical code exists in the ManagedAttributeUpgrader
     * task.
     */
    public void visitManagedAttribute(ManagedAttribute att) 
        throws GeneralException {

        // if this comes back non-null it's an old one that needs upgrading
        String purview = att.getPurview();
        if (purview != null) {

            log.warn("Upgrading ManagedAttribute purview: " + purview);

            Application app = _context.getObjectById(Application.class, purview);
            if (app == null) {
                log.error("Unresolved ManagedAttribute purview: " + purview);
            }
            else {
                att.setApplication(app);
                att.setPurview(null);
            }

            // wrap localized description attributes
            Attributes<String, Object> atts = att.getAttributes();
            if (atts != null && !atts.isEmpty() && 
                atts.get(ManagedAttribute.ATT_DESCRIPTIONS) == null) {

                Map<String,String> descriptions = new HashMap<String,String>();
                Iterator<String> it2 = atts.keySet().iterator();
                while (it2.hasNext()) {
                    String lang = it2.next();
                    Object o = atts.get(lang);
                    if (o instanceof String)
                        descriptions.put(lang, (String)o);
                }
                att.setAttributes(null);
                att.setDescriptions(descriptions);
            }

            if (ManagedAttribute.Type.Permission.name().equals(att.getType())) {
                String attname = att.getAttribute();
                if (ManagedAttribute.OLD_PERMISSION_ATTRIBUTE.equals(attname)) {
                    // value (target) goes up to attribute
                    att.setAttribute(att.getValue());
                    att.setValue(null);
                }
            }
        }

        //If the group property is set, we will set the Type to the corresponding schema objectType
        if (att.isGroup() && att.getAttribute() != null) {
            Application app = att.getApplication();
            Schema accntSchema = app.getAccountSchema();
            if(accntSchema != null) {
                AttributeDefinition ad = accntSchema.getAttributeDefinition(att.getAttribute());
                if(ad != null && ad.getSchemaObjectType() != null) {
                    att.setType(ad.getSchemaObjectType());
                }
            }
        }

        // give it a hash if it doesn't have one yet
        // happens with old unit test files
        if (att.getHash() == null) {
            ManagedAttributer ma = new ManagedAttributer(_context);
            att.setHash(ma.getHash(att));
        }

        for (ObjectClassification oc : Util.safeIterable(att.getClassifications())) {
            //Set the ownerType. ID should get done automatically
            oc.setOwnerType(ManagedAttribute.class.getSimpleName());
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // QuickLink
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Upgrade the referenced DynamicScopes within a QuickLink to create a
     * QuickLinkOptions that links the QuickLink to DynamicScope
     */
    public void visitQuickLink(QuickLink ql) throws GeneralException {
        if (null != _context && null != ql) {
            String id = ql.getId();
            if (id == null) {
                QuickLink existing = _context.getObjectByName(QuickLink.class,
                        ql.getName());
                if (existing != null) {
                    deleteObjects(existing.getQuickLinkOptions());
                }
            }
            List<DynamicScope> scopes = ql.getDynamicScopes();
            List<QuickLinkOptions> qlO = ql.getQuickLinkOptions();
            //if there are no DynamicScopes or null, then there are no qlOptions to create
            if (!Util.isEmpty(scopes)) {
                //for every scope, create the representative qlOption
                for (DynamicScope scope : scopes) {
                    //only create the qlOption if one does not exist
                    if (Util.isEmpty(qlO)) {
                        QuickLinkOptions qlOptions = new QuickLinkOptions();
                        qlOptions.setDynamicScope(scope);
                        qlOptions.assignProperties(ql, scope, true);
                        qlOptions.setQuickLink(ql);
                        ql.addQuickLinkOptions(qlOptions);
                    } else {
                        boolean found = false;
                        for (QuickLinkOptions opt : Util.safeIterable(qlO)) {
                            if (opt.getDynamicScope() != null && opt.getDynamicScope().equals(scope)) {
                                if (log.isWarnEnabled()) {
                                    log.warn("Associated QuickLinkOptions was already found for Quick Link " + ql.getName() +
                                            " and Dynamic Scope " + scope.getName() + ". Skipped creation of Quick Link Options. Import a new Quick Link Options" +
                                            " to save any new properties.");
                                }
                                found = true;
                            }
                        }
                        if (!found) {
                            QuickLinkOptions qlOptions = new QuickLinkOptions();
                            qlOptions.setDynamicScope(scope);
                            qlOptions.assignProperties(ql, scope, true);
                            qlOptions.setQuickLink(ql);
                            ql.addQuickLinkOptions(qlOptions);
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Server
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public void visitServer(Server server) throws GeneralException {

        // remove from includedServices any service names that are present
        // in excludedServices

        List<String> includedServices = (List<String>)server.get(Server.ATT_INCL_SERVICES);
        List<String> excludedServices = (List<String>)server.get(Server.ATT_EXCL_SERVICES);

        if (includedServices != null && excludedServices != null) {
            Iterator<String> includedServicesIter = includedServices.iterator();
            while (includedServicesIter.hasNext()) {
                String includedService = includedServicesIter.next();
                if (excludedServices.contains(includedService)) {
                    if (log.isWarnEnabled()) {
                        log.warn("In Server '" + server.getName() + "', removing service '" + includedService + "' from " + Server.ATT_INCL_SERVICES + " because " +
                                "it is also present in " + Server.ATT_EXCL_SERVICES);
                    }
                    includedServicesIter.remove();
                }
            }
        }

    }


    //////////////////////////////////////////////////////////////////////
    //
    // Target
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A unique hash was added to Targets in 7.2.  If importing older objects that don't have
     * this, calculate it on the fly.
     */
    @Override
    public void visitTarget(Target target) throws GeneralException {
        if (null == target.getUniqueNameHash()) {
            target.assignUniqueHash();
        }
    }
}
