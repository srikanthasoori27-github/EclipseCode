/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * The definition of an IIQ Service.
 *
 * Author: Jeff
 *
 * Services are similar to tasks, they are started when the system starts
 * and will run as long as the system is up.  They may launch
 * and manage threads, or they may simply suspened and be resumed by
 * a system thread at a configurable interval.
 *
 * There are three catagories of services:
 *
 *   Standard Services
 *     - always running in every instance
 *         examples: CacheService, TaskService
 * 
 *   Optional Services
 *     - running in selected instances or not running at all
 *       examples: SMListenerService, ResourceEventService
 *
 *   Custom Services
 *     - optional services that are not part of the product
 *
 * To start a service you create ServiceDefinition objects in the database.
 * While a service is running it may maintain a ServiceStatus object
 * containing information about the runtime state of the service.
 *
 * A single ServiceDefinition may start a service on several hosts.
 * Each host will have it's own ServiceStatus object.
 * 
 */

package sailpoint.object;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ServiceDefinition extends SailPointObject implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Special value for the hosts property to indicate that the 
     * service runs on all hosts.
     */
    public static final String HOST_GLOBAL = "global";

    /**
     * The plugin name argument.
     */
    public static final String ARG_PLUGIN_NAME = "pluginName";

    /**
     * Name of the executor class.
     * The class must extend the sailpoint.server.Service class.
     */
    String _executor;

    /**
     * Services are executed periodically, the time between executions
     * is called the "interval" which is a value in seconds. Note that
     * it is not guaranteed that services will run at regular intervals,
     * long running service executions can delay the execution of
     * other services.
     */
    int _interval;

    /**
     * Hosts on which this service runs.
     * This is a csv whose elements must match the names
     * returned by the Util.getHostName method.
     *
     * If this is null, the service is dormant unless it is one
     * of the standard services that are started implicitly.
     *
     * If this is "global" it will be started on all hosts.
     */
    String _hosts;

    /**
     * When true, the service is allowed to run in the console.
     * By default, only the standard services run in the console.
     *
     * @ignore
     * This probably needs to be generalized in to an "applications"
     * list.
     *
     * !! Not actually mapping this yet, try not to use it.
     */
    boolean _console;

    /**
     * Service-specific configuration.
     */
    Attributes<String,Object> _attributes;

    /**
     * Transient map used for checking hosts.
     */
    Map<String,String> _hostMap;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ServiceDefinition() {
    }

    public boolean hasAssignedScope() {
        return false;
    }

    @XMLProperty
    public String getExecutor() {
        return _executor;
    }

    public void setExecutor(String s) {
        _executor = s;
    }

    @XMLProperty
    public int getInterval() {
        return _interval;
    }

    public void setInterval(int i) {
        _interval = i;
    }

    @XMLProperty
    public String getHosts() {
        return _hosts;
    }

    public void setHosts(String s) {
        _hosts = s;
    }

    @XMLProperty
    public boolean isConsole() {
        return _console;
    }

    public void setConsole(boolean b) {
        _console = b;
    }
    
    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

    /**
     * Helper utility to merge a host list from the database and one 
     * from the iiq.properties file.  This will happen in Servicer when
     * it starts services, it is possible for the merged list to be persited
     * so must check for duplicates.
     */
    public void addHosts(String hosts) {
        if (hosts != null) {
            List<String> oldList = Util.csvToList(_hosts);
            List<String> newList = Util.csvToList(hosts);
            for (String host : newList) {
                if (!oldList.contains(host)) 
                    oldList.add(host);
            }
            _hosts = Util.listToCsv(oldList);
            _hostMap = null;
        }
    }

    /**
     * Return true if the current host is on the hosts list for this
     * service definition.
     */
    public boolean isThisHostAllowed() {
        String thisHost = Util.getHostName();
        return isHostAllowed(thisHost);
    }

    /**
     * Return true if the given host is on the hosts list for this
     * service definition.
     */
    public boolean isHostAllowed(String hostName) {

        if (_hostMap == null) {
            _hostMap = new HashMap<String,String>();
            List<String> hosts = Util.csvToList(_hosts);
            for (String host : Util.iterate(hosts)) {
                _hostMap.put(host, host);
            }
        }
        
        return ((_hostMap.get(HOST_GLOBAL) != null) ||
                (_hostMap.get(hostName) != null));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    //////////////////////////////////////////////////////////////////////

    public Object get(String name) {
        return ((_attributes != null) ? _attributes.get(name) : null);
    }

    public void put(String name, Object value)
    {
        if (_attributes == null)
            _attributes = new Attributes<String, Object>();

        // sigh, I with Attributes did this
        if (value == null)
            _attributes.remove(name);
        else
            _attributes.put(name, value);
    }

    public String getString(String name) {
        return (_attributes != null) ? _attributes.getString(name) : null;
    }

    public int getInt(String name) {
        return (_attributes != null) ? _attributes.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_attributes != null) ? _attributes.getBoolean(name) : false;
    }


}
