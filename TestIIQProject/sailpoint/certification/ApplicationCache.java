/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A cache of information about Applications organized for fast lookup.
 *
 * Author: Jeff
 *
 * Used by both EntityBuilder and ItemBuilder.
 * Somewhat like the one in Aggregator but that does way more than we need.
 * 
 */

package sailpoint.certification;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Identity;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


public class ApplicationCache {
        
    static Log log = LogFactory.getLog(ApplicationCache.class);
    SailPointContext _context;
    Map<String, ApplicationInfo> _cache = new HashMap<String, ApplicationInfo>();
        
    public ApplicationCache(SailPointContext con) {
        _context = con;
    }

    public ApplicationInfo get(String appName) throws GeneralException {
            
        ApplicationInfo info = _cache.get(appName);
        if (info == null) {

            Application app = _context.getObjectByName(Application.class, appName);
            if (app == null) {
                log.error("Application evaporated: " + appName);;
            }
            else {
                info = new ApplicationInfo(app);
                _cache.put(appName, info);
            }
        }

        return info;
    }

    /**
     * A cache of information from the Application schemas
     * related to groups and managed attributes.
     *
     * If something is marked managed it is assumed managed.
     * If it not we have to check to see if it can be aggregated.
     * I THINK aggregatable objects all have managed=true, but
     * it isn't technically required so check.
     *
     * How groups attributes are modeled has changed over time.
     * Originally had an isGroup flag, I don't think that is still used.
     * For most of the product, the Schema had a groupAttribute property
     * which named the single account attribute having the group list.
     * It was assusmed this was aggregatable.
     *
     * With the addition of multiple group schemas the attribute
     * now has a schemaObjectType property that names the schema.
     * These are also assumed to be aggregatable.
     */
    public class ApplicationInfo {

        private String _id;
        private String _owner;
        private Map<String,String> _managedAttributes = new HashMap<String,String>();
        private Map<String,String> _objectTypes = new HashMap<String,String>();
        
        public ApplicationInfo(Application app) throws GeneralException {
            
            _id = app.getId();
            
            Identity owner = app.getOwner();
            if (owner != null)
                _owner = owner.getName();

            Schema schema = app.getAccountSchema();
            if (schema != null) {
                String groupAttribute = schema.getGroupAttribute();
                List<AttributeDefinition> atts = schema.getAttributes();
                for (AttributeDefinition att : Util.iterate(atts)) {
                    String name = att.getName();
                    String type = att.getSchemaObjectType();
                    if (att.isManaged() ||
                        type != null ||
                        name.equals(groupAttribute)) {
                        
                        _managedAttributes.put(name, name);

                        // for EntitlementFilter to know how to build the MA hash
                        if (type != null) {
                            _objectTypes.put(name, type);
                        }
                        else if (name.equals(groupAttribute)) {
                            // shouldn't this have had an objectType after upgrade?
                            // I was going to do this but it messed up the unit tests 
                            // that do not have a group schema and expect the type in the has
                            // to be just "Entitlement".  I suppose we could check
                            // for the existence of a group schema and assume they
                            // are aggregatablt, but it should really have a schemaObjectType by now
                            // _objectTypes.put(name, Schema.TYPE_GROUP);
                        }
                    }
                }
            }
        }
        
        public String getId() {
            return _id;
        }
        
        public String getOwner() {
            return _owner;
        }
        
        public boolean isManaged(String attribute) {
            return (_managedAttributes.get(attribute) != null);
        }

        public String getObjectType(String attribute) {
            return (_objectTypes.get(attribute));
        }

    }
    
}
