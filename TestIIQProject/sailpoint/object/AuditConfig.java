/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Configuration for the internal audit log.
 *
 * Author: Jeff
 * 
 */

package sailpoint.object;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AuditEvent.SCIMResource;
import sailpoint.tools.Util;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Configuration for the internal audit log.
 * 
 * There are four lists of config objects, one for 
 * general operations not associated with any particular
 * model class, one for CRUD operations on model classes,
 * one for CRUD operations on SCIM Rest Resources 
 * and one for changes to individual attributes.
 *
 * The objects in the lists serve both to hold the audit 
 * selections, as well as give the UI the list of all things that can be audited.
 * In other words, if something does not appear on the list it will not appear
 * in the UI.  If you write AuditConfig classes in XML this cannot
 * be a "sparse" list, you need to include an entry for all general operations
 * and all classes that can be audited even if auditing will be disabled.
 *
 */
@XMLClass
public class AuditConfig extends SailPointObject 
    implements Cloneable, Serializable, Cacheable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(AuditConfig.class);

    /**
     * The name of the default audit config in the database.
     */
    public static final String OBJ_NAME = "AuditConfig";
 
    // 
    // SailPointObject._disabled flag is used for performance testing.
    // Let's you disable auditing without having to modify
    // all the class configurations.
    //

    /**
     * Because audit checks happen all over the place, a global
     * configuration cache is maintained to avoid hitting Hibernate.
     */
    static CacheReference _cache;

    /**
     * Class configurations.
     */
    List<AuditClass> _classes;
    
    /**
     * SCIM Resource configurations.
     */
    List<AuditSCIMResource> _resources;

    /**
     * Attribute configurations.
     */
    List<AuditAttribute> _attributes;

    /**
     * General Actions.
     */
    List<AuditAction> _actions;

    /**
     * Runtime map created from _classes to speed lookup.
     */
    Map<Class,AuditClass> _classMap;
    
    /**
     * Runtime map created from _resources to speed lookup.
     */
    Map<SCIMResource,AuditSCIMResource> _resourceMap;

    /**
     * Runtime map created from _attributes to speed lookup.
     */
    Map<String,AuditAttribute> _attributeMap;

    /**
     * Runtime map created from _actions to speed lookup.
     */
    Map<String,AuditAction> _actionMap;

    //////////////////////////////////////////////////////////////////////
    //
    // AuditClass
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Each of the major SailPointObject classes can have a different
     * audit configuration.  Might be nice to have a global default.
     */
    @XMLClass
    public static class AuditClass {

        String _name;
        String _displayName;
        boolean _create;
        boolean _update;
        boolean _delete;
        Map<String,String> _actionMap;

        // kludgey flag used only on import to delete things during a merge
        boolean _obsolete;

        public AuditClass() {
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setDisplayName(String s) {
            _displayName = s;
        }

        public String getDisplayName() {
            return _displayName;
        }

        public String getDisplayableName() {
            return (_displayName != null) ? _displayName : _name;
        }

        @XMLProperty
        public void setCreate(boolean b) {
            _create = b;
        }

        public boolean isCreate() {
            return _create;
        }

        @XMLProperty
        public void setUpdate(boolean b) {
            _update = b;
        }

        public boolean isUpdate() {
            return _update;
        }

        @XMLProperty
        public void setDelete(boolean b) {
            _delete = b;
        }

        public boolean isDelete() {
            return _delete;
        }

        @XMLProperty
        public void setObsolete(boolean b) {
            _obsolete = b;
        }

        public boolean isObsolete() {
            return _obsolete;
        }

        /**
         * Keep classes out of the _classMap if there is
         * nothing to be audited.
         */
        public boolean isInvisible() {
            return (!_create && !_update && !_delete);
        }

        public boolean isEnabled(String action) {
            
            return (getActionMap().get(action) != null);
        }
        
        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("create:").append(_create).append(";");
            buf.append("update:").append(_update).append(";");
            buf.append("delete:").append(_delete).append(";");
            return buf.toString();
        }

        protected Map<String,String> getActionMap() {
            if (_actionMap == null) {
                synchronized(this) {
                    if (_actionMap == null) {
                        Map<String,String> map = new HashMap<String,String>();
                        if (_create)
                            map.put(AuditEvent.ActionCreate, "true");
                        if (_update) 
                            map.put(AuditEvent.ActionUpdate, "true");
                        if (_delete) 
                            map.put(AuditEvent.ActionDelete, "true");
                        _actionMap = map;
                    }
                }
            }
            return _actionMap;
        }

    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // AuditSCIM
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Audit configuration for CRUD operations on SCIM endpoints.
     */
    @XMLClass
    public static class AuditSCIMResource extends AuditClass {
        boolean _read;
        
        @XMLProperty
        public void setRead(boolean b) {
            _read = b;
        }

        public boolean isRead() {
            return _read;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isInvisible() {
            return super.isInvisible() && !isRead();
        }
        
        @Override
        public String toString() {
            String parent = super.toString();
            StringBuffer buf = new StringBuffer(parent);
            return buf.append("read:").append(isRead()).append(";").toString();
        }
        
        @Override
        protected Map<String,String> getActionMap() {
            // initialize _actionMap
            super.getActionMap();
            synchronized(this) {
                if (isRead())
                    _actionMap.put(AuditEvent.ActionRead, "true");
            }
            return _actionMap;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // AuditAttribute
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Audit configuration for individual attributes.  
     * 
     * The action for these will be "change".
     * 
     * The target will be formatted as <class>:<name>
     * 
     * string1 is the attribute name
     * 
     * string2 is the operation: set, add, remove
     * 
     * string3 is a CSV of the values
     *
     * @ignore
     * 
     * A checkboxes for add verses remove, as seen with  
     * create/update/delete, seems less useful here.
     *
     * displayName is probably not useful here because this will not be the
     * name used in the searches and shown in the results?
     * 
     * These are currently only used for identity attributes, but it could
     * be used for other classes.
     */
    @XMLClass
    public static final class AuditAttribute implements IXmlEqualable<AuditAttribute> {

        String _class;
        String _name;
        String _displayName;
        boolean _enabled;

        // kludgey flag used only on import to delete things during a merge
        boolean _obsolete;

        public AuditAttribute() {
        }

        @Override
        public boolean contentEquals(AuditAttribute other) {
            
            return
                Util.nullSafeEq(getClassName(), other.getClassName(), true)
                &&
                Util.nullSafeEq(getName(), other.getName(), true)
                &&
                Util.nullSafeEq(getDisplayName(), other.getDisplayName(), true)
                &&
                Util.nullSafeEq(isObsolete(), other.isObsolete(), true);
        }

        
        @XMLProperty(xmlname="class")
        public void setClassName(String s) {
            _class = s;
        }

        public String getClassName() {
            return _class;
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setDisplayName(String s) {
            _displayName = s;
        }

        public String getDisplayName() {
            return _displayName;
        }

        public String getDisplayableName() {
            return (_displayName != null) ? _displayName : _name;
        }

        @XMLProperty
        public void setEnabled(boolean b) {
            _enabled = b;
        }

        public boolean isEnabled() {
            return _enabled;
        }

        @XMLProperty
        public void setObsolete(boolean b) {
            _obsolete = b;
        }

        public boolean isObsolete() {
            return _obsolete;
        }
        
        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            //buf.append("name:").append(_name).append(";");
            //buf.append("class:").append(_class).append(";");
            buf.append("enabled:").append(_enabled).append(";");
            return buf.toString();
        }


    }

    //////////////////////////////////////////////////////////////////////
    //
    // AuditAction
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * These are simpler than AuditClass since they have only one
     * boolean enable flag, but it also serves as a container
     * for other metadata like displayName and the list itself determines
     * what is displayed in the configuration UI.
     */
    @XMLClass
    public static final class AuditAction {

        String _name;
        String _displayName;
        boolean _enabled;

        // kludgey flag used only on import to delete things during a merge
        boolean _obsolete;

        public AuditAction() {
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setDisplayName(String s) {
            _displayName = s;
        }

        public String getDisplayName() {
            return _displayName;
        }

        public String getDisplayableName() {
            return (_displayName != null) ? _displayName : _name;
        }

        @XMLProperty
        public void setEnabled(boolean b) {
            _enabled = b;
        }

        public boolean isEnabled() {
            return _enabled;
        }

        @XMLProperty
        public void setObsolete(boolean b) {
            _obsolete = b;
        }

        public boolean isObsolete() {
            return _obsolete;
        }
        
        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            //buf.append("name:").append(_name).append(";");
            buf.append("enabled:").append(_enabled).append(";");
            return buf.toString();
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public AuditConfig() {
    }

    public void add(AuditAttribute att) {
        if (att != null) {
            if (_attributes == null)
                _attributes = new ArrayList<AuditAttribute>();
            _attributes.add(att);
            _attributeMap = null;
        }
    }

    public void remove(AuditAttribute att) {
        if (_attributes != null) {
            _attributes.remove(att);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cacheable Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Return true to cache this object. 
     * There should only be one AuditConfig object, but there can can
     * some variants in the unit tests so check the name.
     */
    @Override
    public boolean isCacheable(SailPointObject config) {

        return OBJ_NAME.equals(config.getName());
    }

    /**
     * @exclude
     */
    @Override
    public void setCache(CacheReference cache) {
        _cache = cache;
    }

    /**
     * Return the cached AuditConfig.
     */
    public static AuditConfig getAuditConfig() {

        AuditConfig config = null;
        if (_cache != null)
            config = (AuditConfig)_cache.getObject();

        if (config == null) {
            // so the caller doesn't have to bother with null checking
            // if we're in that small boostrapping window
            config = new AuditConfig();
        }

        return config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.LIST,xmlname="AuditActions")
    public List<AuditAction> getActions() {
        return _actions;
    }

    public void setActions(List<AuditAction> actions) {
        _actions = actions;
        _actionMap = null;
    }

    @XMLProperty(mode=SerializationMode.LIST,xmlname="AuditClasses")
    public List<AuditClass> getClasses() {
        return _classes;
    }

    public void setClasses(List<AuditClass> classes) {
        _classes = classes;
        _classMap = null;
    }
    
    @XMLProperty(mode=SerializationMode.LIST,xmlname="AuditSCIMResources")
    public List<AuditSCIMResource> getResources() {
        return _resources;
    }
    
    public void setResources(List<AuditSCIMResource> resources) {
        _resources = resources;
        _resourceMap = null;
    }

    @XMLProperty(mode=SerializationMode.LIST,xmlname="AuditAttributes")
    public List<AuditAttribute> getAttributes() {
        return _attributes;
    }

    public void setAttributes(List<AuditAttribute> attributes) {
        _attributes = attributes;
        _attributeMap = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // General Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Test to see if a general action is can be audited.
     */
    public boolean isEnabled(String action) {
        boolean enabled = false;
        if (!_disabled) {
            // note that this is only effective if we're maintaining
            // it in the global cache
            if (_actionMap == null) {
                synchronized(this) {
                    if (_actionMap == null) {
                        _actionMap = new HashMap<String,AuditAction>();
                        if (_actions != null) {
                            for (AuditAction a : _actions) {
                                if (a.isEnabled())
                                    _actionMap.put(a.getName(),a);
                            }
                        }
                    }
                }
            }
            enabled = (_actionMap.get(action) != null);
        }
        return enabled;
    }

    /**
     * Look up an action by name, this is only for definition merging
     * so it does not have to be quick.
     */
    public AuditAction getAuditAction(String name) {
        AuditAction found = null;
        if (_actions != null && name != null) {
            for (AuditAction action : _actions) {
                if (name.equals(action.getName())) {
                    found = action;
                    break;
                }
            }
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Class Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the enabled audit class map, bootstrapping if necessary.
     */
    private Map<Class,AuditClass> getClassMap() {
        if (_classMap == null) {
            synchronized(this) {
                if (_classMap == null) {
                    _classMap = new HashMap<Class,AuditClass>();
                    if (_classes != null) {
                        for (AuditClass config : _classes) {
                            // collapse if there isn't anything to audit
                            String name = config.getName();
                            if (name != null && !config.isInvisible()) {
                                if (name.indexOf('.') < 0)
                                    name = "sailpoint.object." + name;

                                try {
                                    Class cls = Class.forName(name);
                                    // whatever you do, this can never be enabled!
                                    if (cls != AuditEvent.class)
                                        _classMap.put(cls, config);
                                }
                                catch (Throwable t) {
                                    if (log.isErrorEnabled())
                                        log.error("Invalid class name in AuditConfig: " + name, t);
                                }
                            }
                        }
                    }
                }   
            }
        }
        return _classMap;
    }

    private AuditClass getAuditClass(SailPointObject o) {

        Map<Class,AuditClass> map = getClassMap();
        return map.get(o.getClass());
    }

    /**
     * Test to see if anything about this class can be audited.
     * This is used by the Hibernate Interceptor to decide whether
     * or not to remember objects of this class as they are being flushed.
     */
    public boolean isAudited(SailPointObject obj) {
        boolean enabled = false;
        if (!_disabled && obj != null) {
            AuditClass config = getAuditClass(obj);
            // assume entries are collapsed to null if nothing is turned on
            enabled = (config != null);
        }
        return enabled;
    }

    /**
     * Test to see if the action is enabled for this object.  
     * Be careful here, the action determines if attention is paid
     * to the classMap, the object might just be the target argument.
     */
    public boolean isEnabled(String action, SailPointObject obj) {
        boolean enabled = false;
        if (!_disabled) {
            enabled = isEnabled(action);
            if (!enabled) {
                // if it isn't a general action, try class specific actions
                // crap! We don't have a nice model for actions to tell
                // us whether or not this is even relevant as a class 
                // specific action.  We may check for no reason.
                if (obj != null) {
                    AuditClass config = getAuditClass(obj);
                    if (config != null) {
                        enabled = config.isEnabled(action);
                    }
                }
            }
        }
        return enabled;
    }

    /**
     * Look up a class by name, this is only for definition merging
     * so it does not have to be quick.
     */
    public AuditClass getAuditClass(String name) {
        AuditClass found = null;
        if (_classes != null && name != null) {
            for (AuditClass auclass : _classes) {
                if (name.equals(auclass.getName())) {
                    found = auclass;
                    break;
                }
            }
        }
        return found;
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // SCIMResource Actions
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isEnabled(String action, SCIMResource resource) {
        boolean enabled = false;
        if (!_disabled) {
            if (resource != null) {
                AuditSCIMResource config = getAuditSCIMResource(resource);
                if (config != null) {
                    enabled = config.isEnabled(action);
                }
            }
        }
        return enabled;
    }
    
    protected Map<SCIMResource, AuditSCIMResource> getSCIMResourceMap() {
        if (_resourceMap == null) {
            synchronized(this) {
                if (_resourceMap == null) {
                    _resourceMap = new HashMap<SCIMResource,AuditSCIMResource>();
                    if (_resources != null) {
                        for (AuditSCIMResource config : _resources) {
                            // collapse if there isn't anything to audit
                            String name = config.getName();
                            if (name != null) {
                                try {
                                    SCIMResource res = SCIMResource.valueOf(name);
                                    _resourceMap.put(res, config);
                                }
                                catch (Throwable t) {
                                    if (log.isErrorEnabled())
                                        log.error("Invalid resource in AuditConfig: " + name, t);
                                }
                            }
                        }
                    }
                }   
            }
        }
        return _resourceMap;
    }
    
    public AuditSCIMResource getAuditSCIMResource(String resourceName) {
        SCIMResource resource = SCIMResource.valueOf(resourceName);
        return getAuditSCIMResource(resource);
    }
    
    protected AuditSCIMResource getAuditSCIMResource(SCIMResource resource) {
        Map<SCIMResource,AuditSCIMResource> resMap = getSCIMResourceMap();
        return resMap.get(resource); 
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Attribute Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the enabled audit attributes map, bootstrapping if necessary.
     * Map is keyed by attribute name.  Class is ignored for now since you are
     * only dealing with identities.
     */
    private Map<String,AuditAttribute> getAttributeMap() {
        if (_attributeMap == null) {
            synchronized(this) {
                if (_attributeMap == null) {
                    _attributeMap = new HashMap<String,AuditAttribute>();
                    if (_attributes != null) {
                        for (AuditAttribute config : _attributes) {
                            String name = config.getName();
                            if (name != null) {
                                _attributeMap.put(name, config);
                            }
                        }
                    }
                }
            }
        }
        return _attributeMap;
    }

    /**
     * Class is passed in for the future, but it is ignored.
     */
    public AuditAttribute getAuditAttribute(Class cls, String name) {

        Map<String,AuditAttribute> map = getAttributeMap();
        return map.get(name);
    }

    /**
     * Test to see if an attribute is audited.
     */
    public boolean isEnabled(Class cls, String name) {
        boolean enabled = false;
        if (!_disabled && name != null) {
            AuditAttribute config = getAuditAttribute(cls, name);
            if (config != null)
              enabled = (config.isEnabled());
        }
        return enabled;
    }

    /**
     * Helper for upgrades.  Lookup an attribute by classname and name.
     */
    public AuditAttribute getAuditAttribute(String cls, String name) {
        AuditAttribute found = null;
        if (_attributes != null && cls != null && name != null) {
            for (AuditAttribute att : _attributes) {
                if (cls.equals(att.getClassName()) &&
                    name.equals(att.getName())) {
                    found = att;
                    break;
                }
            }
        }
        return found;
    }

}
