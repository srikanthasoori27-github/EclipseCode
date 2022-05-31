/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A object maintaining the state of a running IIQ service.
 * Not all services will publish status.
 *
 * Author: Jeff
 *
 * This is an optional object that may be created and maintained
 * by a Service executor that wants to publish complex status.
 * It is recommended that services do this just so we have an 
 * idea for what is running where.  
 *
 * The service is encouraged to periodically update meaningful
 * status so that we can see if it is running properly.
 *
 * See the ServiceDefinition class for more information on services.
 */

package sailpoint.object;

import java.util.Date;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class ServiceStatus extends SailPointObject implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // SailPointObject.name will be combination of the ServiceDefinition.name
    // and the host name

    // A launch date isn't particularly interesting since this is the same
    // as the date the system was started.

    /**
     * The definition of the service.
     */
    ServiceDefinition _definition;

    /**
     * The specific host this service is running on.
     * This is also part of the name, but having it in a property makes
     * it easier to deal with.
     */
    String _host;

    /**
     * The date the service last ran.
     */
    Date _lastStart;

    /**
     * The date the service last ended.
     */
    Date _lastEnd;

    /**
     * A XML bag for putting service-specific status.
     */
    Attributes<String,Object> _attributes;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ServiceStatus() {
    }

    public boolean hasAssignedScope() {
        return false;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public ServiceDefinition getDefinition() {
        return _definition;
    }

    public void setDefinition(ServiceDefinition def) {
        _definition = def;
    }

    @XMLProperty
    public String getHost() {
        return _host;
    }

    public void setHost(String s) {
        _host = s;
    }

    @XMLProperty
    public Date getLastStart() {
        return _lastStart;
    }

    public void setLastStart(Date d) {
        _lastStart = d;
    }

    @XMLProperty
    public Date getLastEnd() {
        return _lastEnd;
    }

    public void setLastEnd(Date d) {
        _lastEnd = d;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> a) {
        _attributes = a;
    }

}
