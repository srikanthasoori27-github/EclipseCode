/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class used to maintain a queue of change events received from
 * connectors that support change notification.  These are normally
 * creatd by the SMListenerService as it receives events
 * from SM instances.  In theory it could be used by other connectors.
 * 
 * There is some funtional overlap between this and the older
 * web service interface that was called by ITIM and Sun finished
 * provisioning requests.  We're basically receiving a ResourceObject
 * and need to do a targeted aggregation of that one object.
 * The differce though is that it needs to be up to the Connector
 * to decide how to listen for events, they can't always use a 
 * web service.  In SM's case, there is a specific protocol
 * that must be used.
 *
 * NOTE: There is some tension over whether this should be called
 * a ApplicationEvent rather than a ResourceEvent.  We don't 
 * usually talk about resources or even connectors, we talk about 
 * applications which are specific configurations of a connector.
 * The problem is that we already have an ApplicationChangeEvent that 
 * is used for changes to the Application object itself rather than 
 * an account managed by that application.  I would like to avoid
 * this name ambiguity.  
 *
 * Since the primary unit of information received was a 
 * ResourceObject, it seems okay to call this a ResourceEvent
 * since they are closely related.
 *
 */

package sailpoint.object;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class used to maintain a queue of change events received from
 * Connectors that support change notification. These are normally
 * created by the SMListenerService as it receives events
 * from SM instances.
 */
@XMLClass
public class ResourceEvent extends SailPointObject
    implements Cloneable, Serializable {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 1L;

    private static Log log = LogFactory.getLog(ResourceEvent.class);

    /**
     * The Application that sent us this event.
     * 
     */
    Application _application;

    /**
     * An object describing the changes made to an account. 
     * Normally this will contain a single AccountRequest, in theory
     * if the source Application were a multiplexing application like a 
     * provisioning system we could be receiving notifications for
     * more than one account.
     *
     * @ignore
     * I thought about just having this be an AccountRequest since that's
     * all we need right now.  Still having the ProvisioningPlan wrapper
     * provides a way to pass other information like "requester" which 
     * may be interesting.
     */
    ProvisioningPlan _plan;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ResourceEvent() {
    }

    /**
     * Let the ScopeService know this class does not have a scope.
     */
    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    /**
     * Let the PersistenceManager know the name is not queryable.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    /**
     * Return the application that sent us this event.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application app) {
        _application = app;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ProvisioningPlan getPlan() {
        return _plan;
    }
    
    public void setPlan(ProvisioningPlan plan) {
        _plan = plan;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "ID");
        cols.put("created", "Date");
        cols.put("application.name", "Application");
        return cols;
    }

    /**
     * Provide a display format for each line in the list of these objects.
     * This string is used by PrintWriter.format().
     *
     * @return a print format string
     */
    public static String getDisplayFormat() {
        return "%-34s %-24s %s\n";
    }

}
