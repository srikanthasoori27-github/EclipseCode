/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class used to maintain the state of a provisioning request
 * sent to a connector.  These can be generated every time
 * we call Connector.provision (or IntegrationExecutor.provision)
 * and the connector indicates that the plan is being evaluated
 * asynchronously.
 *
 * Author: Jeff
 *
 * NOTE: There was a class with this name in releases prior
 * to 5.2 but it did not serve the same purpose.  The old class
 * was part of the "provisioning pages" experiment that was
 * never productized.  
 *
 *  
 */

package sailpoint.object;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @exclude
 */
@XMLClass
public class ProvisioningRequest extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The identity for whom this request was sent.
     */
    Identity _identity;

    /**
     * The name of the Application or IntegrationConfig this
     * request was sent through.  This will be an application
     * name for newer connectors that support provisioning.  It will
     * be an IntegrationConfig name for older integrations with the
     * provisioning implemented in an IntegrationExecutor.
     */
    String _target;

    /**
     * The name of the identity or system entity that made the request.
     * If the request was created by a background task, the requester
     * maybe an abstract name like "System" or "Identity Refresh",
     * it is not necessarily an Identity object name.
     */
    String _requester;

    /**
     * The date this request expires.
     * When set, this is the date at which we will stop waiting
     * for this request to be processed by the target system.
     * The request object will be deleted which will allow
     * the request to be generated again.  
     */
    Date _expiration;

    /**
     * The provisioning plan that was sent to the connector.
     */
    ProvisioningPlan _plan;

    /**
     * Transient flag used only during request pruning.
     */
    boolean _dirty;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ProvisioningRequest() {
    }

    public boolean hasName() {
        return false;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="IdentityRef")
    public void setIdentity(Identity id) {
        _identity = id;
    }

    public Identity getIdentity() {
        return _identity;
    }

    @XMLProperty
    public void setTarget(String s) {
        _target = s;
    }

    public String getTarget() {
        return _target;
    }

    @XMLProperty
    public void setRequester(String s) {
        _requester = s;
    }

    public String getRequester() {
        return _requester;
    }

    @XMLProperty
    public void setExpiration(Date d) {
        _expiration = d;
    }

    public Date getExpiration() {
        return _expiration;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setPlan(ProvisioningPlan p) {
        _plan = p;
    }

    public ProvisioningPlan getPlan() {
        return _plan;

    }

    public void setDirty(boolean b) {
        _dirty = b;
    }

    public boolean isDirty() {
        return _dirty;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Provide a hint regarding what columns to display for anything that
     * wants to display a list of these objects.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("identity", "Identity");
        cols.put("target", "Target");
        cols.put("expiration", "Expiration");
        return cols;
    }

    /**
     * Provide a display format for each line in the list of these objects.
     * This string is used by PrintWriter.format().
     *
     * @return a print format string
     */
    public static String getDisplayFormat() {
        return "%-40s %-24s %-20s %s\n";
    }


}
