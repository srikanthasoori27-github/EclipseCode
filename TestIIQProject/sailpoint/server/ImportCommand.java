/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 *
 */
package sailpoint.server;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import sailpoint.api.RoleChangeAnalyzer;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountGroup;
import sailpoint.object.Application;
import sailpoint.object.Argument;
import sailpoint.object.AttributeSource;
import sailpoint.object.AttributeTarget;
import sailpoint.object.Attributes;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditConfig.AuditAction;
import sailpoint.object.AuditConfig.AuditAttribute;
import sailpoint.object.AuditConfig.AuditClass;
import sailpoint.object.AuditConfig.AuditSCIMResource;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.ConnectorConfig;
import sailpoint.object.Dictionary;
import sailpoint.object.DictionaryTerm;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.IdentityFilter;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.ImportAction;
import sailpoint.object.ImportMergable;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ManagedResource;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.QuickLinkOptions;
import sailpoint.object.RequestDefinition;
import sailpoint.object.RoleChangeEvent;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.Rule;
import sailpoint.object.RuleRegistry;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ServiceDefinition;
import sailpoint.object.Signature;
import sailpoint.object.TaskDefinition;
import sailpoint.object.UIConfig;
import sailpoint.object.Workflow;
import sailpoint.plugin.PluginInstaller;
import sailpoint.server.Importer.Monitor;
import sailpoint.service.plugin.PluginsService;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.Authorizer;


/**
 * Concrete implementations of ImportExecutors that also have a revision which
 * can be used to determine if the command should be run.  As the name implies,
 * this is consistent with the GoF command pattern.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class ImportCommand implements ImportExecutor {



    static final Log log = LogFactory.getLog(ImportCommand.class);

    String _systemVersion;
    
    String _group;

    /**
     * Argument that comes in through the ImportAction definiton.
     */
    AbstractXmlObject _arg;

    /**
     * Constructor.
     */
    public ImportCommand(String systemVersion) {
        _systemVersion = systemVersion;
    }

    /**
     * Return the system version for which this command should be executed.
     */
    public String getSystemVersion() {
        return _systemVersion;
    }
    
    public String getGroup() {
    	return _group;
    }
    
    public void setGroup(String group) {
    	_group = group;
    }
    
    /**
     * Returns the argument stored on the ImportAction.
     * 
     * @return AbstractXmlObject
     */
    public AbstractXmlObject getArgument() {
        return _arg;
    }

    public void setArgument(AbstractXmlObject arg) {
        _arg = arg;
    }

    /**
     * Save a parsed object and inform the monitor.
     */
    private void save(Context context, String tag, SailPointObject obj)
        throws GeneralException {

        if (obj != null) {
            if (context.getMonitor() != null)
                context.getMonitor().report(obj);

            log.info("Importing " + obj);

            // Do the visitor out here in case it needs to commit the transaction,
            // if we actually need to do that then it makes the dependency between
            // the visitor and the implementation of importObject awkward, since
            // ideally importObject could do its own visitation.  But we don't
            // want it automatically committing the transaction.

            // KLUDGE: We still have test files with AccountGroups in them, 
            // auto upgrade these to ManagedAttributes
            if (obj instanceof AccountGroup)
                obj = upgradeAccountGroup((AccountGroup)obj);

            new ImportVisitor(context.getContext()).visit(obj);

            context.getContext().importObject(obj);

            new PostImportVisitor(context.getContext()).visit(obj);

            context.getContext().commitTransaction();
            //context.getContext().decache(obj);
            context.getContext().decache();
        }
        else {
            // !! need to have a monitor callback for these
            log.error("Unknown XML element: " + tag);
        }
    }

    /**
     * Special saver for ImportActions.  We've lost the tag
     * by now, so fake one up so we can call the other save method.
     */
    void save(Context context, SailPointObject obj)
        throws GeneralException {

        save(context, getBaseClassName(obj.getClass()), obj);
    }

    String getBaseClassName(Class cls) {

        return getBaseClassName(cls.getName());
    }

    String getBaseClassName(String path) {

        String base = path;
        int dot = path.lastIndexOf(".");
        if (dot > 0)
            base = path.substring(dot + 1);
        return base;
    }
    
    public abstract String getDescription();

    /**
     * Hopefully temporary kludge to upgrade AccountGroup objects into
     * ManagedAttributes.  Really should change all the test files but that
     * will take time.
     */
    private ManagedAttribute upgradeAccountGroup(AccountGroup src) {

        log.warn("Upgrading AccountGroup object to ManagedAttribute");
        return new ManagedAttribute(src);
    }
    

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // SAVE COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Command that saves a SailPointObject.
     */
    public static class Save extends ImportCommand {

        private String tag;
        private SailPointObject obj;
        private Class<? extends SailPointObject> classToImport;

        public Save(String revision, String tag, SailPointObject o,
                    Class<? extends SailPointObject> classToImport) {
            super(revision);
            this.tag = tag;
            this.obj = o;
            this.classToImport = classToImport;
        }

        public boolean requiresConnection() {
            return false;
        }

        public void execute(Context context) throws GeneralException {
            if ((null != this.classToImport) &&
                !this.classToImport.isAssignableFrom(obj.getClass())) {
                log.info("Not importing " + obj + " because it is not of type " +
                         this.classToImport.getName());
            }
            else {
                super.save(context, this.tag, this.obj);
            }
        }
        
        public String getDescription()
        {
            return MessageFormat.format("Save: {0}", obj.getClass().getSimpleName());
        }
        
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Save RoleChangeEvents COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Command that imports roles analyzing role changes and generates RoleChangeEvents.
     */
    public static class SaveRoleChangeEvents extends Save {

        public SaveRoleChangeEvents(String revision,
                String tag,
                SailPointObject o,
                Class<? extends SailPointObject> classToImport) {
            super(revision, tag, o, classToImport);
        }

        /**
         * If an Object is a Bundle, then calculate and save RoleChangeEvents.
         * Caller should check if RolePropagation is enabled.
         * It uses Save.execute to save Bundle object.
         */
        public void execute(Context context) throws GeneralException {
            List<RoleChangeEvent> events = null;
            if (super.obj instanceof Bundle) {
                Bundle role = (Bundle) super.obj;
                log.info("Calculating RoleChangeEvents for Role - " + role.getDisplayableName());
                // Logic of calculating role changes depends on id of the role.
                // Imported role may not have an id or have a different id than existing role.
                // So setting role id here.
                String id = getExistingRoleId(context.getContext(), role.getName());
                if (Util.isNotNullOrEmpty(id)) {
                    role.setId(id);
                    // Get List of RoleChangeEvents.
                    RoleChangeAnalyzer analyzer = new RoleChangeAnalyzer(context.getContext());
                    events = analyzer.calculateRoleChanges(role);
                    // Clear old roles from hibernate session after calculating events.
                    context.getContext().decache();
                    // Do not save events here before importing Bundle.
                }
            }
            // Execute Save Command now
            super.execute(context);
            // Save events for this Bundle.
            if (!Util.isEmpty(events)) {
                for (RoleChangeEvent rce : events) {
                    if (null != context.getMonitor()) {
                        context.getMonitor().info("  RoleChangeEvent created for Bundle:" +
                                rce.getBundleName());
                    }
                    context.getContext().saveObject(rce);
                }
                context.getContext().commitTransaction();
                context.getContext().decache();
            }
        }

        /**
         * Return ID of an argument Role name.
         */
        private String getExistingRoleId(SailPointContext spContext, String name)
                throws GeneralException {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", name));
            Iterator<Object []> itIds = spContext.search(Bundle.class, ops, "id");
            if (!Util.isEmpty(itIds) && itIds.hasNext()) {
                Object[] row = itIds.next();
                if (null != row[0]) {
                    return row[0].toString();
                }
            }
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // IMPORT ACTION COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Abstract command that executes an ImportAction.
     */
    public static abstract class ImportActionCommand extends ImportCommand {

        ImportAction action;

        public ImportActionCommand(String systemVersion, ImportAction action) {
            super(systemVersion);
            this.action = action;
        }        
        
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // MERGE COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Variant of Merge that only sets values if they are currently null.
     * Should this just be the default behavior?  Would we ever unconditionally
     * set something in System Config?
     */
    public static class MergeIfNull extends Merge {

        public MergeIfNull(String systemVersion, ImportAction action) {
            super(systemVersion, action);
            _ifNull = true;
        }

    }

    /**
     * A command that merges a specified object into an object that already may
     * exist in the system.
     */
    public static class Merge extends ImportActionCommand {

        /**
         * Flag set in MergeIfNull subclass to cause merge only
         * if the current value is null.  This has actually been the
         * default behavior for merging into ObjectConfigs
         */
        boolean _ifNull;

        public Merge(String systemVersion, ImportAction action) {
            super(systemVersion, action);
        }

        public boolean requiresConnection() {
            return false;
        }
        
        public String getDescription()
        {
            return MessageFormat.format("Merge: {0}", action.getArgument().getClass().getSimpleName());
        }

        public void execute(Context context) throws GeneralException {
            Object arg = action.getArgument();
            if (arg instanceof ObjectConfig) {
                processMerge(context, (ObjectConfig)arg);
            }
            else if (arg instanceof ScoreConfig) {
                processMerge(context, (ScoreConfig)arg);
            }
            else if (arg instanceof Configuration) {
                processMerge(context, (Configuration)arg);
            }
            else if (arg instanceof UIConfig) {
                processMerge(context, (UIConfig)arg);
            }
            else if (arg instanceof AuditConfig) {
                processMerge(context, (AuditConfig)arg);
            }
            else if (arg instanceof Dictionary) {
                processMerge(context, (Dictionary)arg);
            }
            else if (arg instanceof EmailTemplate) {
                processMerge(context, (EmailTemplate)arg);
            }
            else if (arg instanceof IntegrationConfig) {
                processMerge(context, (IntegrationConfig) arg);
            }
            else if (arg instanceof RuleRegistry) {
                processMerge(context, (RuleRegistry) arg);
            }
            else if (arg instanceof TaskDefinition) {
                processMerge(context, (TaskDefinition) arg);
            }
            else if (arg instanceof ServiceDefinition) {
                processMerge(context, (ServiceDefinition) arg);
            }
            else if (arg instanceof FullTextIndex) {
                processMerge(context, (FullTextIndex) arg);
            }
            else if (arg instanceof RequestDefinition) {
                processMerge(context, (RequestDefinition) arg);
            }
            else if (arg instanceof QuickLinkOptions) {
                processMerge(context, (QuickLinkOptions) arg);
            }
            else if (arg instanceof QuickLink) {
                processMerge(context, (QuickLink) arg);
            }
            else if (arg instanceof Capability) {
                processMerge(context, (Capability) arg);
            }
            else {
                log.error("Unsupported object for merge action: " + arg);
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // FullTextIndex Merge
        //
        //////////////////////////////////////////////////////////////////////
        private void processMerge(Context context, FullTextIndex index)
                throws GeneralException {
        
            SailPointContext spcon = context.getContext();
            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(index);

            FullTextIndex master = spcon.getObjectByName(FullTextIndex.class,
                                                    index.getName());
            if (master == null)
                save(context, index);
            else {
                Attributes<String,Object> newAttrs = index.getAttributes();

                Attributes<String,Object> masterAttrs = master.getAttributes();

                Attributes<String,Object> mergedAttrs = mergeAttributes(newAttrs, masterAttrs, _ifNull);
                
                master.setAttributes(mergedAttrs);

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }


        //////////////////////////////////////////////////////////////////////
        //
        // IntegrationConfig Merge
        //
        //////////////////////////////////////////////////////////////////////

        private void processMerge(Context context, IntegrationConfig recent)
            throws GeneralException {
            
            new ICMergeHelper(
                    context, 
                    context.getContext().getObjectByName(IntegrationConfig.class,recent.getName()),
                    recent).merge();
        }
        
        static final class PropertyHelper
        {
        
            public static PropertyDescriptor findDescriptor(Object object, String propertyName)
                throws GeneralException
            {
                BeanInfo beanInfo = getBeanInfo(object.getClass());
                
                for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors())
                {
                    if (descriptor.getName().equals(propertyName))
                    {
                        return descriptor;
                    }
                }
                
                return null;
            }
            
            public static Object getPropertyValue(Object object, String propertyName)
                throws GeneralException
            {
                return getPropertyValue(object, findDescriptor(object, propertyName));
            }
            
            public static Object getPropertyValue(Object object, PropertyDescriptor descriptor)
            {
                if (descriptor == null)
                {
                    return null;
                }
                
                java.lang.reflect.Method readMethod = descriptor.getReadMethod();
                if (readMethod == null)
                {
                    return null;
                }
        
                try
                {
                    return sailpoint.tools.Reflection.getValue(readMethod, object);
                }
                catch(Exception ex)
                {
                    return null;
                }
        
            }
            
            public static void setPropertyValue(Object object, String propertyName, Object value)
                throws GeneralException
            {
                setPropertyValue(object, findDescriptor(object, propertyName) , value);
            }
            
            public static void setPropertyValue(Object object, PropertyDescriptor descriptor, Object value)
            {
                if (descriptor == null)
                {
                    return;
                }
                
                Method writeMethod = descriptor.getWriteMethod();
                if (writeMethod == null)
                {
                    return;
                }
        
                try
                {
                    sailpoint.tools.Reflection.setValue(writeMethod, object, value);
                }
                catch(Exception ex)
                {
                    return;
                }
            }
            
            private static Map<Class<?>, BeanInfo> _beanInfoCache = new HashMap<Class<?>, BeanInfo>();
            public static BeanInfo getBeanInfo(Class<?> clazz)
                    throws GeneralException
            {
                if (_beanInfoCache.containsKey(clazz))
                {
                    return _beanInfoCache.get(clazz);
                }

                BeanInfo beanInfo;
                try
                {
                    beanInfo = Introspector.getBeanInfo(clazz);
                }
                catch (IntrospectionException ex)
                {
                    throw new GeneralException(ex);
                }
                _beanInfoCache.put(clazz, beanInfo);

                return beanInfo;
            }
        }
        
        private class ICMergeHelper
        {
            private Context importContext;
            private IntegrationConfig originalConfig;
            private IntegrationConfig recentConfig;
            
            private ICMergeHelper(Context importContext, IntegrationConfig original, IntegrationConfig recent)
            {
                this.importContext = importContext;
                this.originalConfig = original;
                this.recentConfig = recent;
            }
            
            private void merge()
                throws GeneralException
            {
                if (this.importContext.getMonitor() != null)
                {
                    this.importContext.getMonitor().mergingObject(this.recentConfig);
                }
                
                if (this.originalConfig == null)
                {
                    save(this.importContext, this.recentConfig);
                    return;
                }
                
                BeanInfo beanInfo = PropertyHelper.getBeanInfo(IntegrationConfig.class);
                List<String> propertyNames = Arrays.asList(
                        "executor", 
                        "execStyle",
                        "template",
                        "signature",
                        "attributes",
                        "application",
                        "resources",
                        "roleSyncStyle",
                        "synchronizedRoles",
                        "roleSyncFilter",
                        "roleSyncContainer",
                        "planInitializer"
                        );
                for(PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors())
                {
                    if (descriptor.getName() == null || !propertyNames.contains(descriptor.getName()))
                    {
                        continue;
                    }

                    Class<?> clazz = descriptor.getPropertyType();
                    if (List.class.isAssignableFrom(clazz))
                    {
                        List<?> recentList = (List<?>) PropertyHelper.getPropertyValue(this.recentConfig, descriptor);
                        List<?> originalList = (List<?>) PropertyHelper.getPropertyValue(this.originalConfig, descriptor);
                        
                        Object merged = mergeIfLists(recentList, originalList);
                        PropertyHelper.setPropertyValue(this.originalConfig, descriptor, merged);
                    }
                    else if (Attributes.class.isAssignableFrom(clazz))
                    {
                        @SuppressWarnings("unchecked")
                        Attributes<String, Object> recentAttributes = (Attributes<String, Object>) PropertyHelper
                                .getPropertyValue(this.recentConfig, descriptor);
                        @SuppressWarnings("unchecked")
                        Attributes<String, Object> originalAttributes = (Attributes<String, Object>) PropertyHelper
                                .getPropertyValue(this.originalConfig, descriptor);
                        
                        Attributes<String, Object> merged = mergeAttributes(recentAttributes, originalAttributes, _ifNull);
                        PropertyHelper.setPropertyValue(this.originalConfig, descriptor, merged);
                    }
                    else
                    {
                        Object recentValue = PropertyHelper.getPropertyValue(this.recentConfig, descriptor);
                        Object originalValue = PropertyHelper.getPropertyValue(this.originalConfig, descriptor);
                        Object toSet = originalValue;
                        if (originalValue == null)
                        {
                            toSet = recentValue; 
                        }
                        else if (recentValue != null)
                        {
                            toSet = recentValue;
                        }
                        PropertyHelper.setPropertyValue(this.originalConfig, descriptor, toSet);
                    }
                }                
                
                this.importContext.getContext().saveObject(this.originalConfig);
                this.importContext.getContext().commitTransaction();
                this.importContext.getContext().decache();
            }
            
        }

        //////////////////////////////////////////////////////////////////////
        //
        // ObjectConfig Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * The ObjectAttribues list can be merged generically.
         * The configAttributes are however tricky because we don't
         * know what's in them.  Assume if there is a Map we do map
         * entry merging, and if there is a list we do list element merging.
         */
        private void processMerge(Context context, ObjectConfig config)
            throws GeneralException {

            ObjectConfig master = context.getContext().getObjectByName(ObjectConfig.class,
                                                                 config.getName());

            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(config);

            if (master == null) {
                // this becomes the master
                save(context, config);
            }
            else {
                List<ObjectAttribute> atts = config.getObjectAttributes();
                if (atts != null) {
                    for (ObjectAttribute att : atts) {
                        ObjectAttribute matt = master.getObjectAttribute(att.getName());
                        if (matt == null)
                            master.add(att);
                        else {
                            // Override the display name if one is provided.
                            // Display names should only be provided on system and standard
                            // attributes, and they will have corresponding message keys so
                            // we shouldn't have to worry about custom display names
                            String displayName = att.getDisplayName();
                            if (displayName != null) {
                                matt.setDisplayName(displayName);
                            }

                            Rule rule = att.getRule();
                            if (rule != null) {
                                if (matt.getRule() == null)
                                    matt.setRule(rule);
                            }

                            rule = att.getListenerRule();
                            if (rule != null) {
                                if (matt.getListenerRule() == null)
                                    matt.setListenerRule(rule);
                            }

                            Workflow wf = att.getListenerWorkflow();
                            if (wf != null) {
                                if (matt.getListenerWorkflow() == null)
                                    matt.setListenerWorkflow(wf);
                            }

                            String type = att.getType();
                            if (type != null) {
                                if (matt.getType() == null) {
                                    matt.setType(type);
                                }
                            }

                            ObjectAttribute.EditMode editMode = att.getEditMode();
                            if (null != editMode) {
                                if (_ifNull) {
                                    if (null == matt.getEditMode()) {
                                        matt.setEditMode(editMode);
                                    }
                                } else {
                                    matt.setEditMode(editMode);
                                }
                            }

                            // sources are trickier
                            List<AttributeSource> sources = att.getSources();
                            if (sources != null) {
                                List<AttributeSource> msources = matt.getSources();
                                if (msources == null)
                                    matt.setSources(sources);
                                else {
                                    for (AttributeSource src : sources) {
                                        AttributeSource match = matt.getSource(src);
                                        if (match == null)
                                            matt.add(src);
                                        else {
                                            // again, who wins on the rule?
                                            if (match.getRule() == null)
                                                match.setRule(src.getRule());
                                        }
                                    }
                                }
                            }

                            // targets are also trickier
                            List<AttributeTarget> targets = att.getTargets();
                            if (targets != null) {
                                List<AttributeTarget> mtargets = matt.getTargets();
                                if (mtargets == null)
                                    matt.setTargets(targets);
                                else {
                                    for (AttributeTarget target : targets) {
                                        AttributeTarget match = matt.getTarget(target);
                                        if (match == null)
                                            matt.add(target);
                                        else {
                                            // again, who wins on the rule?
                                            if (match.getRule() == null)
                                                match.setRule(target.getRule());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Attributes<String,Object> masterAtts = master.getConfigAttributes();
                Attributes<String,Object> newAtts = config.getConfigAttributes();

                if (masterAtts != null) {
                    // Pre-flatten the role type definition maps to make sure they are properly migrated to lists
                    Object oldRoleTypeDefs = masterAtts.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);

                    if (null != oldRoleTypeDefs && oldRoleTypeDefs instanceof Map) {
                        List roleTypeDefList = this.convertMapToList((Map) oldRoleTypeDefs);
                        masterAtts.put(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS, roleTypeDefList);
                    }

                    // Pre-flatten the identity type definition maps to make sure they are properly migrated to lists
                    Object oldIdentityTypeDefs = masterAtts.get(ObjectConfig.ATT_IDENTITY_TYPE_DEFINITIONS);

                    if (null != oldIdentityTypeDefs && oldIdentityTypeDefs instanceof Map) {
                        List identityTypeDefList = this.convertMapToList((Map) oldIdentityTypeDefs);
                        masterAtts.put(ObjectConfig.ATT_IDENTITY_TYPE_DEFINITIONS, identityTypeDefList);
                    }
                }

                if (newAtts != null) {
                    Object newRoleTypeDefs = newAtts.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);

                    if (null != newRoleTypeDefs && newRoleTypeDefs instanceof Map) {
                        List roleTypeDefList = this.convertMapToList((Map) newRoleTypeDefs);
                        newAtts.put(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS, roleTypeDefList);
                    }

                    Object newIdentityTypeDefs = newAtts.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);

                    if (null != newIdentityTypeDefs && newIdentityTypeDefs instanceof Map) {
                        List identityTypeDefList = this.convertMapToList((Map) newIdentityTypeDefs);
                        newAtts.put(ObjectConfig.ATT_IDENTITY_TYPE_DEFINITIONS, identityTypeDefList);
                    }
                }

                masterAtts = mergeAttributes(newAtts, masterAtts, _ifNull);
                master.setConfigAttributes(masterAtts);

                context.getContext().saveObject(master);
                context.getContext().commitTransaction();
                context.getContext().decache();
            }
        }

        /**
         * Small utility method for flattening maps
         * @param map
         * @return
         */
        private List convertMapToList(Map map) {
            List list = new ArrayList();
            list.addAll(map.values());
            return list;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // RuleRegistry Merge
        //
        //////////////////////////////////////////////////////////////////////

        @SuppressWarnings("unchecked")
        private void processMerge(Context context, RuleRegistry registry)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(registry);

            RuleRegistry master = spcon.getObjectByName(RuleRegistry.class, registry.getName());
            if (master == null)
                save(context, registry);
            else {
                // merge the templates
                if (registry.getTemplates() != null){
                    for (Rule rTemplate : registry.getTemplates()) {
                        Rule mTemplate = master.getTemplate(rTemplate.getType(), false);
                        if (mTemplate == null)
                            mTemplate = new Rule();

                        // if the new template isn't equal to the master template,
                        // replace the master
                        if (!rTemplate.equals(mTemplate)) {
                            master.removeTemplate(mTemplate);
                            master.addTemplate(rTemplate);
                        }
                    }
                }
                
                // merge the callouts
                mergeMaps(master.getRegistry(), registry.getRegistry(), false);

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // EmailTemplate Merge
        //
        //////////////////////////////////////////////////////////////////////

        @SuppressWarnings("unchecked")
        private void processMerge(Context context, EmailTemplate template)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(template);

            EmailTemplate master = spcon.getObjectByName(EmailTemplate.class, template.getName());
            if (master == null)
                save(context, template);
            else {

                master.setBody(template.getBody());
                master.setDescription(template.getDescription());
                master.setSubject(template.getSubject());

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // ScoreConfig Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * ScoreConfiguration merger.
         */
        private void processMerge(Context context, ScoreConfig config)
            throws GeneralException {

            ScoreConfig master = context.getContext().getObjectByName(ScoreConfig.class,
                                                                config.getName());

            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(config);

            if (master == null) {
                // this becomes the master
                save(context, config);
            }
            else {
                List<ScoreDefinition> defs = config.getIdentityScores();
                if (defs != null) {
                    for (ScoreDefinition def : defs) {
                        ScoreDefinition mdef = master.getIdentityScore(def.getName());
                        if (mdef == null)
                            master.addIdentityScore(def);
                        else {
                            // make sure some new attributes get carried over
                            // NOTE: This one has historically overwritten
                            // the master property.  That's okay here because
                            // these are not customizable.
                            // !! what about the disabled flag?
                            mdef.setComponent(def.isComponent());
                            mdef.setComposite(def.isComposite());
                            mdef.setDisabled(def.isDisabled());
                            mdef.setName(def.getName());
                            mdef.setConfigPage(def.getConfigPage());
                            mdef.setSignature(def.getSignature());
                            mdef.setDescription(def.getDescription());
                            mdef.setDisplayName(def.getDisplayName());
                            mdef.setShortName(def.getShortName());
                            mdef.setSignature(def.getSignature());

                            // avoid updating any arguments other than target and suggestion since they
                            // may have been customized. Target and suggestion are less sensitive and may
                            // be changed in the upgrade.
                            if (def.getArgument("target") != null)
                                mdef.setArgument("target", def.getArgument("target"));

                            if (def.getArgument("suggestion") != null)
                                mdef.setArgument("suggestion", def.getArgument("suggestion") );

                        }
                    }
                }

                defs = config.getApplicationScores();
                if (defs != null) {
                    for (ScoreDefinition def : defs) {
                        ScoreDefinition mdef = master.getApplicationScore(def.getName());
                        if (mdef == null)
                            master.addApplicationScore(def);
                        else {
                            mdef.setDescription(def.getDescription());
                            mdef.setDisplayName(def.getDisplayName());
                            mdef.setSignature(def.getSignature());

                            // avoid updating any arguments other than target and suggestion since they
                            // may have been customized. Target and suggestion are less sensitive and may
                            // be changed in the upgrade.
                            if (def.getArgument("target") != null)
                                mdef.setArgument("target", def.getArgument("target"));

                            if (def.getArgument("suggestion") != null)
                                mdef.setArgument("suggestion", def.getArgument("suggestion") );
                        }
                    }
                }

                context.getContext().saveObject(master);
                context.getContext().commitTransaction();
                context.getContext().decache();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Configuration Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * System configuration merger.
         */
        private void processMerge(Context context, Configuration config)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(config);

            Configuration master = spcon.getObjectByName(Configuration.class,
                                                   config.getName());

            if (master == null)
                save(context, config);
            else {
                Attributes<String,Object> newAttrs = config.getAttributes();
                Attributes<String,Object> masterAttrs = master.getAttributes();
                master.setAttributes(mergeAttributes(newAttrs, masterAttrs, _ifNull));
                spcon.saveObject(master);
                spcon.commitTransaction();

                // decache so we don't leave large thigns like 
                // ConnectorRegistry around for dirty checking
                //spcon.decache(master);
                spcon.decache();
            }

            if (Configuration.WEB_RESOURCE_CONFIG.equals(config.getName())) {
                // This will immediately reset the Authorizer cache for this one IIQ
                // server or console, but the CacheService is responsible for the
                // periodic reset of Authorizer cache for all IIQ servers/consoles.
                Authorizer.resetInstance();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // UIConfig Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * UIConfig merger.
         */
        @SuppressWarnings("unchecked")
        private void processMerge(Context context, UIConfig uiConfig)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(uiConfig);

            UIConfig master = spcon.getObjectByName(UIConfig.class,
                                                    UIConfig.OBJ_NAME);
            if (master == null)
                save(context, uiConfig);
            else {
                Attributes<String,Object> newAttrs = uiConfig.getAttributes();
                Attributes<String,Object> masterAttrs = master.getAttributes();

                Attributes<String,Object> mergedAttrs = mergeAttributes(newAttrs, masterAttrs, _ifNull);

                // Now that we've merged that attributes, make sure we don't have duplicates
                // in the lists of ColumnConfig objects.
                if(mergedAttrs != null){
                    for(String key : mergedAttrs.getKeys()){
                        Object val = mergedAttrs.get(key);
                        
                        
                        /** Handle column configs with special behavior **/
                        if (val instanceof List && !((List)val).isEmpty()
                                && ((List)val).get(0) instanceof ColumnConfig){

                            mergedAttrs.put(key, mergeColumnConfig(val));
                        }
                    }
                }

                master.setAttributes(mergedAttrs);

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }
        
        private List<ColumnConfig> mergeColumnConfig(Object val) {
            List<ColumnConfig> mergedVal = (List<ColumnConfig>)val;
            List<ColumnConfig> filteredVal = new ArrayList<ColumnConfig>();
            Set<String> properties = new HashSet<String>();

            // Iterate through the list of columns. For each column,
            // ensure that there aren't any columns with the same dataindex
            // later in the list. If so the later item is a replacement for the
            // current item
            for(int i=0; i < mergedVal.size();i++){
                ColumnConfig curCol = mergedVal.get(i);
                String uniqueId = getUniqueId(curCol);

                // Only keep this column if we have not seen it before.
                if (!properties.contains(uniqueId)){
                    properties.add(uniqueId);
                    for(int j=i+1; j < mergedVal.size();j++){
                        ColumnConfig mergedColumn = mergedVal.get(j);
                        String mergedUniqueId = getUniqueId(mergedColumn);
                        if (uniqueId.equals(mergedUniqueId)){
                            // If we find a match later in the list, use it.  Otherwise, this will
                            // be the original curCol.
                            curCol = mergedVal.get(j);
                            break;
                        }
                    }
                    filteredVal.add(curCol);
                }
            }
            
            return filteredVal;
        }

        private static String getUniqueId(ColumnConfig col) {
            // create a unique string since columns may share the same dataindex
            return col.getDataIndex() + "-" + col.getProperty();
        }


        //////////////////////////////////////////////////////////////////////
        //
        // AuditConfig Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * AuditConfig merger.
         */
        @SuppressWarnings("unchecked")
        private void processMerge(Context context, AuditConfig config)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(config);

            AuditConfig master = spcon.getObjectByName(AuditConfig.class,
                                                       AuditConfig.OBJ_NAME);
            if (master == null) {
                save(context, config);
                return;
            }
            
            List<AuditClass> newClasses = config.getClasses();
            List<AuditClass> masterClasses = master.getClasses();
            if (masterClasses == null) {
                master.setClasses(newClasses);
            }
            else if (newClasses != null) {
                for (AuditClass newClass : newClasses) {
                    // hmm, don't rely on equals() only match by name
                    AuditClass existing = master.getAuditClass(newClass.getName());
                    if (newClass.isObsolete()) {
                        if (existing != null)
                            masterClasses.remove(existing);
                    }
                    else if (existing == null) {
                        masterClasses.add(newClass);
                    }
                    else {
                        // update display name but not the enabling flags
                        existing.setDisplayName(newClass.getDisplayName());
                    }
                }
            }
            
            List<AuditSCIMResource> newResources = config.getResources();
            List<AuditSCIMResource> masterResources = master.getResources();
            if (masterResources == null) {
                master.setResources(newResources);
            }
            else if (newResources != null) {
                for (AuditSCIMResource newResource : newResources) {
                    // hmm, don't rely on equals() only match by name
                    AuditSCIMResource existing = master.getAuditSCIMResource(newResource.getName());
                    if (newResource.isObsolete()) {
                        if (existing != null)
                            masterResources.remove(existing);
                    }
                    else if (existing == null) {
                        masterResources.add(newResource);
                    }
                    else {
                        // update display name but not the enabling flags
                        existing.setDisplayName(newResource.getDisplayName());
                    }
                }
            }

            List<AuditAction> newActions = config.getActions();
            List<AuditAction> masterActions = master.getActions();
            if (masterActions == null) {
                master.setActions(newActions);
            }
            else if (newActions != null) {
                for (AuditAction newAction : newActions) {
                    // hmm, don't rely on equals() only match by name
                    AuditAction existing = master.getAuditAction(newAction.getName());
                    if (newAction.isObsolete()) {
                        if (existing != null)
                            masterActions.remove(existing);
                    }
                    else if (existing == null) {
                        masterActions.add(newAction);
                    }
                    else {
                        // update display name
                        existing.setDisplayName(newAction.getDisplayName());
                        
                        // We're now merging the enabled flag. This can cause issues during incremental
                        // merges where enabled isn't specified. See Bug #8808 for more detail.                            
                        existing.setEnabled(newAction.isEnabled());
                    }
                }
            }

            List<AuditAttribute> newAttributes = config.getAttributes();
            List<AuditAttribute> masterAttributes = master.getAttributes();
            if (masterAttributes == null) {
                master.setAttributes(newAttributes);
            }
            else if (newAttributes != null) {
                for (AuditAttribute newAttribute : newAttributes) {
                    AuditAttribute existing =
                        master.getAuditAttribute(newAttribute.getClassName(),
                                                 newAttribute.getName());

                    if (newAttribute.isObsolete()) {
                        if (existing != null)
                            masterAttributes.remove(existing);
                    }
                    else if (existing == null) {
                        masterAttributes.add(newAttribute);
                    }
                    else {
                        // update display name but not the enabling flags
                        existing.setDisplayName(newAttribute.getDisplayName());
                    }
                }
            }

            spcon.saveObject(master);
            spcon.commitTransaction();
            spcon.decache();
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Dictionary Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Dictionary merger.
         */
        @SuppressWarnings("unchecked")
        private void processMerge(Context context, Dictionary dictionary)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            if (context.getMonitor() != null)
                context.getMonitor().mergingObject(dictionary);

            Dictionary master = spcon.getObjectByName(Dictionary.class, Dictionary.OBJ_NAME);
            if (master == null)
                save(context, dictionary);
            else {
                List<DictionaryTerm> newTerms = dictionary.getTerms();
                List<DictionaryTerm> masterTerms = master.getTerms();
                if (masterTerms == null) {
                    master.setTerms(newTerms);
                }
                else if (newTerms != null) {
                    TreeSet<String> wordsTree = new TreeSet<String>();
                    TreeSet<String> existingWordsTree = new TreeSet<String>();
                    
                    for (DictionaryTerm existingTerm : Util.safeIterable(masterTerms)) {
                        if (existingTerm != null && existingTerm.getValue() != null) {
                            existingWordsTree.add(existingTerm.getValue().toLowerCase());
                        }
                    }
                    
                    //improve merge performance by lowering case (what PasswordConstraintDictionary does anyhow)
                    //and using a treeset to ensure no duplicates for the new entries
                    for (DictionaryTerm newTerm : newTerms) {
                        if (newTerm != null && newTerm.getValue() != null && !existingWordsTree.contains(newTerm.getValue().toLowerCase()) && !wordsTree.contains(newTerm.getValue().toLowerCase())) {
                            wordsTree.add(newTerm.getValue().toLowerCase());
                        }
                    }

                    for(String entry : Util.iterate(wordsTree)) {
                        DictionaryTerm newTerm = new DictionaryTerm();
                        newTerm.setValue(entry);
                        newTerm.setDictionary(master);
                        masterTerms.add(newTerm);
                    }
                }
                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // TaskDefinition Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * TaskDefinition merger.
         * This will add input and return items to the Signature and
         * add things to the arguments map.
         */
        @SuppressWarnings("unchecked")
        private void processMerge(Context context, TaskDefinition task)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            Importer.Monitor monitor = context.getMonitor();
            if (monitor != null)
                monitor.mergingObject(task);

            TaskDefinition master = spcon.getObjectByName(TaskDefinition.class, task.getName());
            if (master == null) {
                // these are intended to be complete well-formed tasks
                // so don't save them?
                //save(context, task);
                String msg = "No matching TaskDefinition: " + task.getName();
                if (monitor != null)
                    monitor.info(msg);
                log.warn(msg);
            }
            else {
                master.setSignature(mergeSignatures(task.getSignature(),
                                                    master.getSignature()));

                master.setArguments(mergeAttributes(task.getArguments(), 
                                                    master.getArguments(),
                                                    _ifNull));

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // RequestDefinition Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * RequestDefinition merger.
         * 
         * This will only merge the attributes for now.
         */
        private void processMerge(Context context, RequestDefinition requestDefinition)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            Importer.Monitor monitor = context.getMonitor();
            if (monitor != null) {
                monitor.mergingObject(requestDefinition);
            }

            RequestDefinition master = spcon.getObjectByName(RequestDefinition.class, requestDefinition.getName());
            if (master == null) {
                // these are intended to be complete well-formed tasks
                // so don't save them?
                //save(context, task);
                String msg = "No matching TaskDefinition: " + requestDefinition.getName();
                if (monitor != null) {
                    monitor.info(msg);
                }
                if (log.isWarnEnabled()) {
                    log.warn(msg);
                }
            }
            else {
                master.setArguments(mergeAttributes(requestDefinition.getArguments(), 
                                                    master.getArguments(),
                                                    _ifNull));

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }


        //////////////////////////////////////////////////////////////////////
        //
        // Capability Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Capability merger.
         *
         * This will only merge the InheritedCapabilities for now.
         */
        private void processMerge(Context context, Capability capability)
                throws GeneralException {

            SailPointContext spcon = context.getContext();
            Importer.Monitor monitor = context.getMonitor();
            if (monitor != null) {
                monitor.mergingObject(capability);
            }

            Capability master = spcon.getObjectByName(Capability.class, capability.getName());
            if (master == null) {
                // these are intended to be complete well-formed tasks
                // so don't save them?
                //save(context, task);
                String msg = "No matching Capability: " + capability.getName();
                if (monitor != null) {
                    monitor.info(msg);
                }
                if (log.isWarnEnabled()) {
                    log.warn(msg);
                }
            }
            else {
                master.setInheritedCapabilities(mergeCapabilityLists(capability.getInheritedCapabilities(),
                        master.getInheritedCapabilities()));

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }

        //////////////////////////////////////////////////////////////////////
        //
        // QuickLinkOptions Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * QuickLinkOptions merger.
         */
        private void processMerge(Context context, QuickLinkOptions quickLinkOptions)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            Monitor monitor = context.getMonitor();
            if (monitor != null) {
                context.getMonitor().mergingObject(quickLinkOptions);
            }

            String mergeeDSName = quickLinkOptions.getDynamicScope().getName();
            String mergeeQL = quickLinkOptions.getQuickLink().getName();
            QueryOptions qo = (new QuickLinkOptionsConfigService(spcon)).getQueryOptions(Arrays.asList(mergeeDSName), 
                    mergeeQL, null, true, true, true);
            List<QuickLinkOptions> qloList = spcon.getObjects(QuickLinkOptions.class, qo);
            if (qloList != null) {
                if (qloList.size() > 1) {
                    final String msg = "Multiple QuickLinkOptions found for DynamicScope: " + mergeeDSName + " and QuickLink: " + mergeeQL + ". Aborting Merge.";
                    if (monitor != null) {
                        monitor.info(msg);
                    }
                    log.info(msg);
                    return;
                }
                
                // we found an existing QuickLinkOptions to merge or we need to create a new QuickLinkOptions
                QuickLinkOptions master = (qloList.size() == 1) ? qloList.get(0) : null; 
                
                if (master == null) {
                    save(context, quickLinkOptions);
                } else {
                    Attributes<String,Object> newAttrs = quickLinkOptions.getOptions();
                    Attributes<String,Object> masterAttrs = master.getOptions();
                    master.setOptions(mergeAttributes(newAttrs, masterAttrs, _ifNull));
                    spcon.saveObject(master);
                    spcon.commitTransaction();

                    // Should not need a decache here, we are working with small objects
                }
                    
            }
        }

        private void processMerge(Context context, QuickLink quickLink)
            throws GeneralException {
            SailPointContext spcon = context.getContext();
            Monitor monitor = context.getMonitor();
            if (monitor != null) {
                context.getMonitor().mergingObject(quickLink);
            }

            QuickLink ql = spcon.getObjectByName(QuickLink.class, quickLink.getName());
            if (ql == null) {
                String msg = "No Matching QuickLink: " + quickLink.getName();
                if (monitor != null) {
                    monitor.info(msg);
                }
                log.warn(msg);
            } else {
                for (QuickLinkOptions opts : Util.safeIterable(quickLink.getQuickLinkOptions())) {
                    QuickLinkOptions masterOpts = ql.getQuickLinkOptions(opts.getDynamicScope());
                    if (masterOpts != null) {
                        //Merge
                        Attributes<String,Object> newAttrs = opts.getOptions();
                        Attributes<String,Object> masterAttrs = masterOpts.getOptions();
                        masterOpts.setOptions(mergeAttributes(newAttrs, masterAttrs, _ifNull));
                    } else {
                        //Insert
                        ql.addQuickLinkOptions(opts);
                    }
                }

                //Merge QL Arguments
                Attributes<String, Object> newArgs = quickLink.getArguments();
                Attributes<String, Object> oldArgs = ql.getArguments();
                ql.setArguments(mergeAttributes(newArgs, oldArgs, _ifNull));

                spcon.saveObject(ql);
                spcon.commitTransaction();
                spcon.decache();
            }
        }
        
        //////////////////////////////////////////////////////////////////////
        //
        // ServiceDefinition Merge
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * ServiceDefinition merger.
         * This will add things to the arguments map.
         */
        @SuppressWarnings("unchecked")
        private void processMerge(Context context, ServiceDefinition serviceDefinition)
            throws GeneralException {

            SailPointContext spcon = context.getContext();
            Importer.Monitor monitor = context.getMonitor();
            if (monitor != null)
                monitor.mergingObject(serviceDefinition);

            ServiceDefinition master = spcon.getObjectByName(ServiceDefinition.class, serviceDefinition.getName());
            if (master == null) {
                String msg = "No matching ServiceDefinition: " + serviceDefinition.getName();
                if (monitor != null)
                    monitor.info(msg);
                log.warn(msg);
            }
            else {
                master.setAttributes(mergeAttributes(serviceDefinition.getAttributes(), 
                                                    master.getAttributes(),
                                                    _ifNull));

                spcon.saveObject(master);
                spcon.commitTransaction();
                spcon.decache();
            }
        }
        
        /**
         * Merge one signature into another.
         * This is currently only used by TaskDefinition but it could be
         * used for Rules as well.
         */
        private Signature mergeSignatures(Signature src, Signature dest)
            throws GeneralException {

            if (src != null) {
                if (dest == null)
                    dest = src;
                else {
                    if (dest.getName() == null) 
                        dest.setName(src.getName());
                    if (dest.getDescription() == null)
                        dest.setDescription(src.getDescription());
                    if (dest.getReturnType() == null)
                        dest.setReturnType(src.getReturnType());
                    
                    dest.setArguments(mergeArguments(src.getArguments(),
                                                     dest.getArguments()));

                    dest.setReturns(mergeArguments(src.getReturns(),
                                                   dest.getReturns()));
                }
            }

            return dest;
        }

        private List<Argument> mergeArguments(List<Argument> src,
                                              List<Argument> dest) {

            if (src != null && src.size() > 0) {
                if (dest == null)
                    dest = src;
                else {
                    for (Argument srcarg : src) {
                        Argument destarg = findArgument(dest, srcarg);
                        if (destarg == null)
                            dest.add(srcarg);
                        else {
                            // I suppose we could merge some things
                            // in the Argument but this should be rare
                            if (destarg.getPrompt() == null)
                                destarg.setPrompt(srcarg.getPrompt());
                            if (destarg.getHelpKey() == null)
                                destarg.setHelpKey(srcarg.getHelpKey());
                            if (destarg.getFilterString() == null)
                                destarg.setFilterString(srcarg.getFilterString());
                            if (destarg.getInputTemplate() == null)
                                destarg.setInputTemplate(srcarg.getInputTemplate());
                        }
                    }
                }
            }

            return dest;
        }

        private Argument findArgument(List<Argument> list, Argument src) {
            Argument found = null;
            if (list != null && src != null) {
                String name = src.getName();
                if (name != null) {
                    for (Argument arg : list) {
                        if (name.equals(arg.getName())) {
                            found = arg;
                            break;
                        }
                    }
                }
            }
            return found;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Merge Utilitites
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Merge a new and existing list of capabilities, returning empty list if necessary
         */
        private static List<Capability> mergeCapabilityLists(List<Capability> newCaps,
                                                                   List<Capability> oldCaps) {
            List<Capability> mergedList = new ArrayList<>();
            mergedList.addAll(oldCaps);
            mergedList.addAll(newCaps);
            return mergedList;
        }

        /**
         * Merge a new and existing Attributes map, creating the destination
         * if necessary.
         */
        private static Attributes<String,Object> mergeAttributes(Attributes<String,Object> newAtts,
                                                                 Attributes<String,Object> oldAtts,
                                                                 boolean ifNull) {
            if (newAtts != null && newAtts.size() > 0) {
                if (oldAtts == null)
                    oldAtts = new Attributes<String,Object>();
                mergeMaps(newAtts, oldAtts, ifNull);
            }
            return oldAtts;
        }

        /**
         * Merge a new and existing map.  Depending on where this came
         * from it may be an Attributes<String,Object> or simply a HashMap.
         * The masterMap must have been created by now if there is something
         * to merge.
         *
         * In a few cases (ObjectConfig:Bundle) the map values may
         * themselves be complex objects that need to be merged.
         * So recurse on Maps and Lists.
         */
        @SuppressWarnings("unchecked")
        private static void mergeMaps(Map newMap, Map masterMap, boolean ifNull) {

            // We won't alter the "hierarchy" implied by dotted paths,
            // we simply overwrite previous keys with the new ones.

            if (newMap != null) {

                Iterator<Map.Entry> it = newMap.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry entry = it.next();
                    Object key = entry.getKey();
                    Object newVal = entry.getValue();
                    Object oldVal = masterMap.get(key);

                    if (oldVal == null || !ifNull) {
                        Object merged;

                        // if either side is a list, promote the other and merge
                        if (newVal instanceof List || oldVal instanceof List)
                            merged = mergeIfLists(newVal, oldVal);

                        // Sigh, assume we have to have Attributes instead of
                        // a HashMap so we don't have to convert.  Should be the
                        // case if the XML is written properly.
                        else if (newVal instanceof Map && oldVal instanceof Map) {
                            mergeMaps((Map)newVal, (Map)oldVal, ifNull);
                            merged = oldVal;
                        }
                        
                        // In the case of merging an identityselectorconfig, we
                        // need to manage the merge of an IdentityFilter
                        else if (newVal instanceof IdentityFilter && oldVal instanceof IdentityFilter) {
                            merged = mergeIdentityFilter((IdentityFilter)oldVal, (IdentityFilter)newVal);
                            masterMap.put(key, merged);
                        }

                        else
                            merged = (null != newVal) ? newVal : oldVal;

                        // jsl - occasionaly we like to remove keys so if the result
                        // is null take it out
                        if (merged != null)
                            masterMap.put(key, merged);
                        else
                            masterMap.remove(key);
                    }
                }
            }
        }
        
        /**
         * Given an existing IdentityFilter and a new IdentityFilter, merge
         * the new into the old.  This is very rudimentary at this point
         * and was implemented mainly to handle the orderby sequence of 
         * an imported identityselectorconfiguration.
         */
        private static IdentityFilter mergeIdentityFilter(IdentityFilter oldVal, IdentityFilter newVal) {
            
            // IdentityFilter.getFilterSrc() could be null now, so need to check for that.
            if (newVal.getFilterSrc() != null && newVal.getFilterSrc().getFilter() != null) {
                //Need to do some null Checking
                if(oldVal.getFilterSrc() != null) {
                    oldVal.getFilterSrc().setFilter(newVal.getFilterSrc().getFilter());
                } else {
                    oldVal.setFilterSrc(newVal.getFilterSrc());
                }
            }
            
            //MEH 16624
            if(newVal.getFilterSrc() != null && newVal.getFilterSrc().getFilterTemplate() != null) {
                if(oldVal.getFilterSrc() != null) {
                    oldVal.getFilterSrc().setFilterTemplate(newVal.getFilterSrc().getFilterTemplate());
                } else {
                    oldVal.setFilterSrc(newVal.getFilterSrc());
                }
            }
            
            if (newVal.getFilterScript() != null) {
                oldVal.setFilterScript(newVal.getFilterScript());
            }
            
            if (newVal.getOrderBy() != null ) {
                oldVal.setOrderBy(newVal.getOrderBy());
            }
            
            if (newVal.getOrder() != null) {
                oldVal.setOrder(newVal.getOrder());
            }

            return oldVal;
        }

        /**
         * Merge the two values into a List if either of them is a list.  If neither
         * is a list, just return the new value.
         */
        @SuppressWarnings("unchecked")
        private static Object mergeIfLists(Object newVal, Object oldVal) {

            Object merged = (null != newVal) ? newVal : oldVal;

            if ((null != newVal) && (null != oldVal)) {
                List newList = null;
                List oldList = null;
                boolean isCSV = false;

                /** If it's an instance of a list **/
                // jsl - technically the logic here is broken though I doubt
                // we're hitting it.  In the case of List<String> we get to this method
                // only if one side or the other is a list.  In that case we should just
                // coerce the other to a List<String> no matter how may commas it has
                if(newVal instanceof List) {
                    newList = Util.asList(newVal);
                    /** Else if it's an instance of a string and looks like a list **/
                } else if(newVal instanceof String && ((String)newVal).contains(",")) {
                    newList = Util.csvToList((String)newVal);
                    isCSV = true;
                }

                if(oldVal instanceof List) {
                    oldList = Util.asList(oldVal);
                    /** Else if it's an instance of a string and looks like a list **/
                } else if(oldVal instanceof String && ((String)oldVal).contains(",")) {
                    oldList = Util.csvToList((String)oldVal);
                    isCSV = true;
                }

                if(newList!=null && oldList!=null) {
                    List mergedList = new ArrayList<Object>();

                    /** Start with the old list and add all items from the old list
                     * to a new merged list.  If any of the old items are contained in the new
                     * list, pull them from the new list instead.  At the end, copy any remaining new
                     * items to the end of the list, this will persist any ordering
                     * on the old list **/
                    for(int i=0; i< oldList.size(); i++) {
                        Object oldObject = oldList.get(i);

                        /* The contains relationship is too strong a criteria for what we need to do.
                         * We want to merge attributes even if they only share a common name.
                         * Using contains() will only merge objects that are exactly alike, at which
                         * point there is really nothing to merge anyways.  The oldObjectName and
                         * indexOfOldObjNameInNewList variables are added to facilitate our check
                         * --Bernie
                         */
                        String oldObjectName = null;
                        int indexOfOldObjNameInNewList = -1;

                        if (oldObject instanceof SailPointObject) {
                            oldObjectName = ((SailPointObject) oldObject).getName();
                            indexOfOldObjNameInNewList = getIndexOfNameInList(newList, oldObjectName);
                        } else if (oldObject instanceof RoleTypeDefinition) {
                            oldObjectName = ((RoleTypeDefinition) oldObject).getName();
                            indexOfOldObjNameInNewList = getIndexOfNameInList(newList, oldObjectName);
                        } else if (oldObject instanceof IdentityTypeDefinition) {
                            oldObjectName = ((IdentityTypeDefinition) oldObject).getName();
                            indexOfOldObjNameInNewList = getIndexOfNameInList(newList, oldObjectName);
                        } else if (oldObject instanceof ConnectorConfig ) {
                            oldObjectName = ((ConnectorConfig) oldObject).getClassName();
                            indexOfOldObjNameInNewList = getIndexOfNameInList(newList, oldObjectName);
                        } else if (oldObject instanceof ManagedResource) {
                            oldObjectName = ((ManagedResource) oldObject).getName();
                            indexOfOldObjNameInNewList = getIndexOfNameInList(newList, oldObjectName);
                        }
                        
                        

                        if(newList.contains(oldObject)) {
                            Object newObject = newList.remove(newList.indexOf(oldObject));

                            /** Special handling of quicklinks - Bug #13613 - PH**/
                            if(newObject instanceof ImportMergable) {
                                ImportMergable newMergable = (ImportMergable)newObject;
                                /** Merge the new stuff on to the */
                                newMergable.merge(oldObject);
                            }

                            mergedList.add(newObject);
                        } else if (indexOfOldObjNameInNewList > -1) {
                            mergedList.add(newList.remove(indexOfOldObjNameInNewList));
                        } else {
                            mergedList.add(oldObject);
                        }
                    }
                    mergedList.addAll(newList);
                    if(isCSV)
                        merged = Util.listToCsv(mergedList);
                    else
                        merged = mergedList;
                }
            }

            return merged;

        }

        // Returns the index of the element in the list whose name matches the specified one
        // Returns -1 if no such element is found
        private static int getIndexOfNameInList(List list, String name) {
            int index = -1;
            if (null != name && name.trim().length() > 0) {
                for (int i = 0; i < list.size(); ++i) {
                    Object o = list.get(i);

                    if (o instanceof SailPointObject) {
                        String objName = ((SailPointObject) o).getName();
                        if (name.equals(objName)) {
                            index = i;
                            break;
                        }
                    }

                    if (o instanceof RoleTypeDefinition) {
                        String objName = ((RoleTypeDefinition) o).getName();
                        if (name.equals(objName)) {
                            index = i;
                            break;
                        }
                    }

                    if (o instanceof IdentityTypeDefinition) {
                        String objName = ((IdentityTypeDefinition) o).getName();
                        if (name.equals(objName)) {
                            index = i;
                            break;
                        }
                    }

                    if (o instanceof ConnectorConfig ) {
                        String objName = ((ConnectorConfig) o).getClassName();
                        if (name.equals(objName)) {
                            index = i;
                            break;
                        }
                    }

                    if (o instanceof ManagedResource) {
                        String objName = ((ManagedResource) o).getName();
                        if (name.equals(objName)) {
                            index = i;
                            break;
                        }
                    }
                }
            }

            return index;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // INCLUDE COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Command that includes another file to be imported.
     */
    public static class Include extends ImportActionCommand {

        private Importer importer;

        public Include(String revision, ImportAction action, Importer importer) {
            super(revision, action);
            this.importer = importer;
        }

        public boolean requiresConnection() {
            return false;
        }

        public void execute(Context context) throws GeneralException {
            String fileName = action.getValue();
            if ( fileName != null ) {
                    // Util.readFile() will look in the right places for the file
                String xml = Util.readFile(fileName);
                if ( xml != null ) {
                    if (context.getMonitor() != null)
                        context.getMonitor().includingFile(fileName);
                    importer.importXml(xml, context);
                }
            }
        }
        
        public String getDescription()
        {
            return MessageFormat.format("Include: {0}", action.getValue());
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // INSTALL PLUGIN COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Command that installs a plugin ZIP file
     */
    public static class InstallPlugin extends ImportActionCommand {

        private Importer importer;
        private static final boolean DEFAULT_THROW_ON_ERROR = false;

        public InstallPlugin(String systemVersion, ImportAction action) {
            super(systemVersion, action);
        }

        public boolean requiresConnection() {
            return false;
        }

        public void execute(Context context) throws GeneralException {

            boolean throwOnFailure = DEFAULT_THROW_ON_ERROR;
            Attributes attrs = action.getAttributes();
            if (attrs != null) {
                throwOnFailure = attrs.getBoolean("throwOnError", DEFAULT_THROW_ON_ERROR);
            }

            String path = action.getValue();
            if ( path != null ) {
                String expandedPath = Util.findFile(path);
                if (!expandedPath.equals(path)) {
                    path = expandedPath;
                }

                String errMsg = null;
                File specifiedFile = new File(path);
                if (!specifiedFile.exists()) {
                    errMsg = "Plugin file or directory '" + path + "' not found";
                }
                else if (!specifiedFile.canRead()) {
                    errMsg = "Unable to read plugin file '" + specifiedFile + "'";
                }
                else if (!path.endsWith(".zip")) {
                    errMsg = "Plugin file '" + specifiedFile + "' is not a zip file";
                }
                else {
                    // file looks valid, so finally let's make sure that plugins are enabled
                    Environment environment = Environment.getEnvironment();
                    if (!environment.getPluginsConfiguration().isEnabled()) {
                        errMsg = "Plugins are currently disabled.  Cannot install plugin file " + specifiedFile;
                    }
                }

                if (errMsg != null) {
                    if (throwOnFailure) {
                        throw new GeneralException(errMsg);
                    }
                    else if (null != context.getMonitor()) {
                        context.getMonitor().warn(errMsg);
                    }
                    return;
                }
                else {
                    // Ok, let's go ahead and install it
                    if (null != context.getMonitor()) {
                        context.getMonitor().info("Installing plugin from file '" + specifiedFile + "'");
                    }
                    installPlugin(context, specifiedFile);
                }
            }
        }

        private void installPlugin(Context context, File pluginFile) throws GeneralException {
            SailPointContext ctx = context.getContext();
            FileInputStream fileInputStream = null;

            try {
                boolean cache = true;
                fileInputStream = new FileInputStream(pluginFile);

                Environment environment = Environment.getEnvironment();
                boolean runSqlScripts = environment.getPluginsConfiguration().isRunSqlScripts();
                boolean importObjects = environment.getPluginsConfiguration().isImportObjects();

                PluginsService.PluginInstallData installData = new PluginsService.PluginInstallData(
                        pluginFile.getName(),
                        fileInputStream,
                        cache,
                        runSqlScripts,
                        importObjects
                );

                PluginsService pluginsService = new PluginsService(ctx);
                PluginInstaller.PluginInstallationResult result = pluginsService.installPlugin(installData, environment.getPluginsCache());
                Plugin plugin = result.getPlugin();

                if (null != context.getMonitor()) {
                    context.getMonitor().info("Successfully installed plugin '" + plugin.getName() + "'");
                }
            } catch (FileNotFoundException e) {
                if (null != context.getMonitor()) {
                    context.getMonitor().warn("Cannot find plugin file '" + pluginFile.getAbsolutePath() + "'");
                }
                throw new GeneralException(e);
            } finally {
                IOUtil.closeQuietly(fileInputStream);
            }

        }

        public String getDescription()
        {
            return MessageFormat.format("Install Plugin: {0}", action.getValue());
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // EXECUTE COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Command that delegates custom execution to the executor that is defined
     * in the ImportAction's value field.  If the custom executor class has a
     * constructor that takes an ImportAction, we will inject it upon
     * construction.
     */
    public static class Execute extends ImportActionCommand {

        private ImportExecutor executor;

        public Execute(String systemVersion, ImportAction action) {
            super(systemVersion, action);
            
            if (action != null) {
            	setGroup(action.getGroup());
            }
        }

        public ImportExecutor getExecutor() {
            if (null == this.executor) {
                String className = action.getValue();
                try {
                    Class clazz = Class.forName(className);

                    try {
                        Constructor c = clazz.getConstructor(new Class[] { ImportAction.class});
                        this.executor = (ImportExecutor) c.newInstance(new Object[] { this.action } );
                    }
                    catch (NoSuchMethodException e) {
                        // Didn't have a constructor that took an ImportAction -
                        // try the default constructor.
                        try {
                            Constructor c = clazz.getConstructor((Class[]) null);
                            this.executor = (ImportExecutor) c.newInstance();
                        }
                        catch (NoSuchMethodException e2) {
                            throw new RuntimeException(clazz + " did not have a suitable constructor - " +
                                                       "need either a default constructor or constructor " +
                                                       "that takes an ImportAction.");
                        }
                    }
                }
                catch (ClassNotFoundException e) {
                    throw new RuntimeException("Could not find class: " + className, e);
                }
                catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
                catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                catch (InstantiationException e) {
                    throw new RuntimeException(e);
                }
            }

            return this.executor;
        }

        public boolean requiresConnection() {
            return getExecutor().requiresConnection();
        }

        public void execute(Context context) throws GeneralException {

            ImportExecutor executor = getExecutor();

            if (null != context.getMonitor()) {
                context.getMonitor().executing(executor);
            }
            if ( action != null ) {
                executor.setArgument(action.getArgument());
            }
            executor.execute(context);            
        }
        
        public String getDescription()
        {
            return MessageFormat.format("Execute: {0}", getExecutor().getClass().getSimpleName());
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // LOG_CONFIG COMMAND
    //
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Command that changes the log4j configuration to a file that is defined
     * in the ImportAction's value field.
     */
    public static class LogConfig extends ImportActionCommand {

        public LogConfig(String systemVersion, ImportAction action) {
            super(systemVersion, action);
        }

        public boolean requiresConnection() {
            return false;
        }

        public void execute(Context context) throws GeneralException {
            String value = action.getValue();
            String logConfigFile = Util.findFile("user.dir", value, true);

            File f = new File(logConfigFile);
            if ( f.exists() ) {
                final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                ctx.setConfigLocation(f.toURI());

                    // warn is a little severe, but we really want to record in
                    // the logging output that there was a change in settings
                context.getMonitor().info("Reloading logging config from " + logConfigFile);
            } else {
                throw new GeneralException("Unable to reload logging config from " + logConfigFile + ": file does not exist.");
            }

        }

        public String getDescription() {
            return MessageFormat.format("LogConfig: {0}", action.getValue());
        }

    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // MERGE_CONNECTOR_REGISTRY Action
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /*
     * BUG# 16440 : Separate connector files with a merge causes slowdown during upgrade
     * 
     */
    public static class ConnectorRegistryMerger extends ImportActionCommand {
        
        private static final String SPECIAL_ACTION_NAME = "connectorRegistryUpdateAction";
        private static final String ARG_FILE_LIST = "fileList";
        
        /**
         * Peel this off since we'll need it in methods to read and write the
         * connector registry.
         */
        SailPointContext _context = null;
        
        /**
         * For output to the console as we read each file.
         */
        Importer.Monitor _monitor = null;
        
        public ConnectorRegistryMerger(String systemVersion, ImportAction action) {
            super(systemVersion, action);
        }        
        
        public boolean requiresConnection() {
            return false;
        }
        
        public String getDescription()        {
            return MessageFormat.format("mergeConnectorRegistry: {0}", action.getArgument().getClass().getSimpleName());
        }

        /**
         * Special import command that will read in the new application templates and
         * write them to the connector registry.
         * 
         */
        @SuppressWarnings("unchecked")
        @Override
        public void execute(Context context) throws GeneralException {
            _context = context.getContext();
            _monitor = context.getMonitor();
          
            List<String> fileList = null;            
            AbstractXmlObject argument = action.getArgument();
            if ( argument != null ) {
                Configuration config = (Configuration)argument; 
                if ( config != null ) {
                   Map<String,Object> args = config.getAttributes();
                   if ( args != null ) {
                       fileList = (List<String>)args.get(ARG_FILE_LIST);
                   }
                }
            }
            
            if ( Util.size(fileList) == 0 ) {
                info("ConnectorRegistry Merge, nothing todo no connector files specified to import command.");
            } else {
                Map<String,Application> appMap = readFilesAndBuildMap(fileList);            
                if ( !Util.isEmpty(appMap) ) {
                    mergeAppsIntoRegistry(appMap);
                } else {
                    info("Reading in the connector files did not produce any applications");
                }
            }
        }
        
        /**
         * This is new in 6.1 and the idea is to read all of the files specified with the
         * Import command, read them into a Map by application type. 
         * 
         * Each file contains an application wrapped by a special ImportAction to avoid
         * the applications from being imported on their own. The importaction is only
         * supported through this import action command.
         * 
         * @param fileList
         * @return Map of applicationType Template application
         * @throws GeneralException
         */
        private Map<String,Application> readFilesAndBuildMap(List<String> fileList) throws GeneralException {
            Map<String, Application> neuAppMap = new HashMap<String,Application>();           
            try {
                if ( fileList != null ) {
                    for (  String file : fileList ) {
                        if ( file == null ) 
                            continue;    
                        if ( _monitor != null )
                            _monitor.includingFile(file);
                        String xml = Util.readFile(file);
                        if ( xml != null ) {
                            ImportAction importAction = (ImportAction)XMLObjectFactory.getInstance().parseXml(_context, xml, false);
                            if ( importAction != null) {
                                if ( !Util.nullSafeEq(SPECIAL_ACTION_NAME, importAction.getName()) ) {
                                    info("Import action for connector registry not correct " + importAction.getName() + " expected " + SPECIAL_ACTION_NAME);
                                } else {
                                    Application app = (Application)importAction.getArgument();
                                    if ( app != null ) {
                                        String tempName = app.getName();
                                        if ( tempName == null ) {
                                            log.error("Template without a name imported from " + file);
                                        }
                                        neuAppMap.put(tempName, app);
                                    }
                                }
                            }

                        }
                    }
                }
            } catch(Throwable e) {
                throw new GeneralException("Exception reading connector registry application files." + e);                
            }
            return neuAppMap;
        }
        
        /**
         * Merge the new application definitions into the connector registry.
         * 
         * If the registry does not exist create a new one assuming its a new installation.
         * 
         * @param newAppDefMap
         * @throws GeneralException
         */
        @SuppressWarnings("unchecked")
        private void mergeAppsIntoRegistry(Map<String,Application> newAppDefMap) throws GeneralException {
            
            Configuration config = _context.getObjectByName(Configuration.class, Configuration.CONNECTOR_REGISTRY);
            if ( config == null ) {
                // new installation
                config = new Configuration();
                config.setName(Configuration.CONNECTOR_REGISTRY);
            }

            Attributes<String,Object> map = config.getAttributes();
            if ( map == null ) {
                map = new Attributes<String,Object>();
                config.setAttributes(map);
            }
            List<Application> apps = map.getList("applicationTemplates");
            if ( apps == null ) {
                apps = new ArrayList<Application>();
                map.put("applicationTemplates", apps);
            } else {                
                for ( int i=0; i<apps.size(); i++ ) {
                    Application app = apps.get(i);
                    if ( app != null ) {
                        String existingAppName  = app.getName();
                        if ( existingAppName  == null ) {
                            log.error("Found application template in the registry without a name." + app.getId());
                        } else {
                            Application neuApp = newAppDefMap.get(existingAppName);
                            if ( neuApp != null ) {
                                apps.set(i, neuApp);
                                newAppDefMap.remove(existingAppName);
                            }
                        }
                    }
                }
            }                
            if  (newAppDefMap.size() > 0 ) {
                Collection<Application> leftOverApps = newAppDefMap.values();
                if ( Util.size(leftOverApps) > 0 ) {                    
                    apps.addAll(leftOverApps);
                }
            }
            _context.saveObject(config);
            _context.commitTransaction();
        }
        
        private void info(String msg) {
            if ( _monitor != null ) {
                _monitor.info(msg);
            } else {
                log.info(msg);
            }
        }
    }
}
