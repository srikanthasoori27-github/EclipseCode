/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A subcomponent of the Application model holding information related
 * to provisioning.
 *
 * Author: Jeff
 *
 * This is similar to and an eventual replacement for the IntegrationConfig
 * but rather than being standalone objects they are part of the Application.
 * There are three things that any application that supports provisioning
 * must have:
 *
 *   - flag indicating whether the underlying system has a "meta" user
 *     (like Sun, OIM) or if it is just a passthrough to the managed systems
 *     (like ITIM, SM)
 *
 *   - a plan initializer rule where customizations can be inserted before
 *     the provisioning plan is evaluated
 *
 *   - operation transformations, which define whether the underlying system
 *     can support things like Disable, Enable, Lock, Unlock
 *     
 *   - flag indiciating this is a "universal manager" that does not need
 *     explicit declaration of every managed resource
 * 
 * In addition, the ManagedResource and ResourceAttribute model originally
 * inside IntegrationConfig can also be used to define resource and
 * attribute name mappings.  This is optional, and it is unclear whether this
 * belongs on the application connecting to the provisioning system, or
 * whether it belongs on the application that represents the managed system.
 * 
 * For now we are going to expect it on the application representing
 * the provisioning system which makes it behave much like the old
 * IntegrationConfig.
 *
 * These things could just be folded up into the Application model, but I
 * like having them encapsulated since they are so closely related and
 * we might have to drag over the role sync stuff from IntegratinoConfig
 * someday.  
 *
 * Unlike IntegrationConfig though we won't have an attributes map where
 * connection parameters are held, those will be in the Application's
 * attribute map as usual.
 * 
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A subcomponent of the Application model holding information related
 * to provisioning.
 */
@XMLClass
public class ProvisioningConfig extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(ProvisioningConfig.class);

    /**
     * True if the underlying system maintains a "meta" user that
     * requests must be performed against rather than being a simple
     * pass through. When this is true an identity must have a Link
     * to this application containing the native id of the meta user.
     */
    boolean _metaUser;

    /**
     * True if this application can handle provisioning requests for all
     * applications. This is primarily a debugging fix so that the
     * a ManagedResource list does not have to be fleshed out for everything.
     */
    boolean _universalManager;

    /**
     * When true, ProvisioningRequest objects are not saved whenever
     * a plan is sent through this connector.
     * This was off by default prior to 6.0.
     */
    boolean _noProvisioningRequests;

    /**
     * When true, "optimistic provisioning" is applied to any request
     * sent to this application. This means that the the contents
     * of an AccountRequest for this application will be applied
     * immediately to the corresponding Link in the cube rather than
     * waiting for the next aggregation to see the provisioning results.
     * You can also do optimistic provisioning for the entire plan
     * with workflow arguments, but this gives you more control.
     */
    boolean _optimisticProvisioning;

    /**
     * The number of days a ProvisioningRequest is allowed to live
     * before it is considered expired.  If this is zero, the expiration
     * days are taken from the system configuration entry
     * "provisioningRequestExpirationDays".
     */
    int _provisioningRequestExpiration;

    /**
     * Option indicating that this connector does not handle 
     * PermissionRequests. When this is true the connector will
     * be sent AttributeRequests but PermissionRequests will be routed
     * to the unmanaged plan and displayed in work items. This is necessary
     * only for the new 5.5 read/write connectors that do not handle
     * permissions added to the cube by the unstructured data collector,
     * primarily this is AD, there might be others.
     *
     * This was a temporary model addition that has been replaced
     * in 6.1 with Feature.NO_DIRECT_PERMISSIONS_PROVISIONING and
     * Feature.NO_UNSTRUCTURED_TARGETS_PROVISIONING.
     * 
     * If this is set, it is assumed to apply to both types of
     * permissions.  
     *
     * @ignore
     * Was this ever used?  I don't see it in the connector registry
     * in either the trunk of 5.5.
     */
    boolean _noPermissions;

    /**
     * List of resources that are managed by the target system.
     * This is used to filter provisioning requests
     * into requests that only involve resources on this list and also
     * defines name transformations for the resource and its attributes.
     */
    List<ManagedResource> _managedResources;

    /**
     * A short-hand for an operation transformation that maps delete
     * requests into disable requests for all applications managed
     * by this config. Alternatively, you can use the operation
     * transformations in the ManagedResource list.
     */
    boolean _deleteToDisable;
    
    /**
     * A rule that is used to load arbitrary data in a ProvisioningPlan.
     * This helps economize what data gets passed across to the integration,
     * instead of sending lots of unneeded data (for example - loading just the name
     * of the person being remediated, instead of passing the entire Identity
     * object to the integration).
     */
    Rule _planInitializerRule;

    /**
     * A script that can be used to initialize the provisioning plan.
     * This is an alternative to an initializer rule that allows the logic
     * to live inside the Application which is nice for templates.
     */
    Script _planInitializerScript;

    /**
     * A script that will be called automatically when the application
     * is saved to initialize the "cluster" property.
     * This is a bit of a work around to optimize the search for all applications
     * that are managed by the same SM instance. The script is expected
     * to find the configuration attributes containing the SM host name 
     * and port and combine those into a single string to be used as
     * the cluster id.
     *
     * @ignore
     * If we find other uses for this it should be moved out of ProvisioningConfig
     * since it is not necessarily related to provisioning. But this is a 
     * convenient home for now since we don't have to extend the Application
     * schema to put things in here.
     */
    Script _clusterScript;


    //
    // Runtime caches
    //

    Map<String,ManagedResource> _localResourceMap;
    Map<String,ManagedResource> _remoteResourceMap;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public ProvisioningConfig() {
    }

    public void load() {
        if (_managedResources != null) {
            for (ManagedResource res : _managedResources)
                res.load();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
	public boolean isMetaUser() {
		return _metaUser;
	}

	public void setMetaUser(boolean b) {
		_metaUser = b;
	}

    @XMLProperty
	public boolean isUniversalManager() {
		return _universalManager;
	}

	public void setUniversalManager(boolean b) {
		_universalManager = b;
	}

    @XMLProperty
	public boolean isNoProvisioningRequests() {
		return _noProvisioningRequests;
	}

	public void setNoProvisioningRequests(boolean b) {
		_noProvisioningRequests = b;
	}

    @XMLProperty
	public boolean isOptimisticProvisionings() {
		return _optimisticProvisioning;
    }

	public void setOptimisticProvisionings(boolean b) {
		_optimisticProvisioning = b;
	}

    /**
     * The number of days a ProvisioningRequest is allowed to live
     * before it is considered expired.  If this is zero, the expiration
     * days are taken from the system configuration entry
     * "provisioningRequestExpirationDays".
     */
    @XMLProperty
	public int getProvisioningRequestExpiration() {
		return _provisioningRequestExpiration;
	}

	public void setProvisioningRequestExpiration(int i) {
		_provisioningRequestExpiration = i;
	}

    @XMLProperty
	public boolean isNoPermissions() {
		return _noPermissions;
	}

	public void setNoPermissions(boolean b) {
		_noPermissions = b;
	}

    /**
     * A rule that is used to load arbitrary data in a ProvisioningPlan.
     * This helps economize what data gets passed across to the integration,
     * instead of sending lots of unneeded data (for example - loading just the name
     * of the person being remediated, instead of passing the entire Identity
     * object to the integration).
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
	public Rule getPlanInitializer() {
		return _planInitializerRule;
	}

	public void setPlanInitializer(Rule rule) {
		_planInitializerRule = rule;
	}
    
    @XMLProperty(mode=SerializationMode.INLINE)
	public Script getPlanInitializerScript() {
		return _planInitializerScript;
	}

	public void setPlanInitializerScript(Script script) {
		_planInitializerScript = script;
	}

    /**
     * Return a DynamicValue that can be used to evaluate
     * the plan initializer.    
     */
    public DynamicValue getPlanInitializerLogic() {
        DynamicValue result = null;
        if (_planInitializerRule != null || _planInitializerScript != null)
            result = new DynamicValue(_planInitializerRule, _planInitializerScript, null);
        return result;
    }

    @XMLProperty(mode=SerializationMode.INLINE)
	public Script getClusterScript() {
		return _clusterScript;
	}

	public void setClusterScript(Script script) {
		_clusterScript = script;
	}

    /**
     * List of resources that are managed by the target system.
     * This is used to filter role sync and provisioning requests
     * into requests that only involve resources on this list and also
     * defines name transformations for the resource and its attributes.
     *
     * @ignore
     * 3.0 had a "ManagedResources" property that we're not going to 
     * upgrade but pick a different name to ease the Hibernate transition
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<ManagedResource> getManagedResources() {
        return _managedResources;
    }

    public void setManagedResources(List<ManagedResource> resources) {
        _managedResources = resources;
    }

    @XMLProperty
    public boolean isDeleteToDisable() {
        return _deleteToDisable;
    }
    
    public void setDeleteToDisable(boolean deleteToDisable) {
        _deleteToDisable = deleteToDisable;
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Runtime Mapping
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Bootstrap a resource map map for master searches.
     */
    private Map<String,ManagedResource> getResourceMap() {
        if (_localResourceMap == null) {
            _localResourceMap = new HashMap<String,ManagedResource>();  
            if (_managedResources != null) {
                for (ManagedResource res : _managedResources) {
                    String local = res.getLocalName();
                    // if there is no local name, assume it is the same
                    // on both sides
                    if (local == null)
                        local = res.getName();
                    if (local != null)
                        _localResourceMap.put(local, res);
                }
            }
        }
        return _localResourceMap;
    }

    private Map<String,ManagedResource> getRemoteResourceMap() {
        if (_remoteResourceMap == null) {
            _remoteResourceMap = new HashMap<String,ManagedResource>();  
            if (_managedResources != null) {
                for (ManagedResource res : _managedResources) {
                    String name = res.getName();
                    // if name is null assume they're the same
                    // on both sides
                    if (name == null)
                        name = res.getLocalName();
                    if (name != null) {
                        // log redundant names
                        if (_remoteResourceMap.get(name) != null) {
                            if (log.isErrorEnabled())
                                log.error("Duplicate managed resource name: " + name);
                        }
                        _remoteResourceMap.put(name, res);
                    }
                }
            }
        }
        return _remoteResourceMap;
    }

    /**
     * Get the managed resource definition for a local resource (Application).
     */
    public ManagedResource getManagedResource(String name) {
        Map<String,ManagedResource> map = getResourceMap();
        return map.get(name);
    }

    public ManagedResource getManagedResource(Application app) {
        return (app != null) ? getManagedResource(app.getName()) : null;
    }

    /**
     * Get the managed resource definition using a remote resource name.
     */
    public ManagedResource getRemoteManagedResource(String name) {
        Map<String,ManagedResource> map = getRemoteResourceMap();
        return map.get(name);
    }

    /**
     * Add a new managed resource.
     */
    public void add(ManagedResource res) {
        if (res != null) {
            if (_managedResources == null)
                _managedResources = new ArrayList<ManagedResource>();
            _managedResources.add(res);
            _localResourceMap = null;
            _remoteResourceMap = null;
        }
    }

    public void removeManagedResource(Application app) {
        if (app != null) {
            ManagedResource res = getManagedResource(app);
            if (res != null)
            _managedResources.remove(res);
        }
    }

}
