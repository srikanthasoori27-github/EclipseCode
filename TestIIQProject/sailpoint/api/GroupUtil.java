/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Utilities for analyzing AccountGroups.
 * Updated in 6.0 to use ManagedAttribute.
 *
 * Author: Jeff
 *
 * Not much in here but we started needing the same little
 * methods in several places once we added support
 * for group provisioning.  
 *
 * In 6.0 we started putting ManagedAttribute search
 * utilities in ManagedAttributer so we forward to that.
 * Consider removing this class and just using ManagedAttributer.
 *
 * !! Also have AccountGroupService, consider moving this over there
 * or put the plan related stuff in GroupLibrary where it is used.
 */
package sailpoint.api;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;

/**
 * Utilities for analyzing groups.
 */
public class GroupUtil {

    private static final Log log = LogFactory.getLog(GroupUtil.class);

    //////////////////////////////////////////////////////////////////////
    //
    // ManagedAttribute Search
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Search for a group with the given attributes.
     * Unlike most objects, ManagedAttributes don't have unique names, they
     * are uniquely defined by a combination of the application,
     * reference attribute, and native group name.
     *
     * The expectation is that there will be only one match, if
     * we find more than one we log and return the first.
     */
    public static ManagedAttribute getGroup(SailPointContext context, 
                                            Application app, String attribute, 
                                            String name)
        throws GeneralException {

        // If an attribute name is not passed, we look for the
        // ACCOUNT schema attribute that has the group flag set.
        // In theory there can be more than one, but provisioning
        // code isn't always in a position to know so this must
        // be set in the schema.
        if (attribute == null) {
            AttributeDefinition groupatt = app.getGroupAttribute();
            if (groupatt == null)
                throw new GeneralException("No defined group attribute in application: " + 
                                           app.getName());
        }

        ManagedAttribute ma = ManagedAttributer.get(context, app, false, attribute, name);

        return ma;
    }

    /**
     * Lookup a group using a name and deriving the name of the
     * reference attribute.
     */
    public static ManagedAttribute getGroup(SailPointContext context, Application app,
                                        String name)
        throws GeneralException {

        return getGroup(context, app, null, name);
    }

    /**
     * Lookup a group targeted by an ObjectRequest.
     */
    public static ManagedAttribute getGroup(SailPointContext context, 
                                        ObjectRequest req,
                                        Application app)
        throws GeneralException {

        ManagedAttribute group = null;

        // allow this to come from the request
        if (app == null) {
            String appname = req.getApplication();
            if (appname != null)
                app = context.getObjectByName(Application.class, appname);
        }
        if (app == null)
            throw new GeneralException("Missing application");

        // There is no way to specify the reference attribute
        // in the ObjectRequest, though I suppose we could
        // use a request arg if necessary.  We'll pass null
        // and derive the name from the account schema.
        String attribute = null;

        String name = getGroupName(context, req, app);
        // Should we try to recover from this?  
        // If we return null we'll just create a new one
        // which will also fail
        if (name == null)
            throw new GeneralException("Unable to locate target group: no native identity");

        group = getGroup(context, app, attribute, name);

        return group;
    }

    public static ManagedAttribute getGroup(SailPointContext context, ObjectRequest req)
        throws GeneralException {

        return getGroup(context, req, null);
    }

    /**
     * Get the native identity for a group from a request.
     */
    public static String getGroupName(SailPointContext context, ObjectRequest req, 
                                      Application app)
        throws GeneralException {

        String id = req.getNativeIdentity();
        if (id == null) {
            // might be here for op=Create, look for the naming attribute
            if (app == null) {
                String appname = req.getApplication();
                if (appname != null)
                    app = context.getObjectByName(Application.class, appname);
            }
            if (app != null) {
                Schema schema = app.getSchema(Connector.TYPE_GROUP);
                if (schema != null) {
                    String identityAttribute = schema.getIdentityAttribute();
                    if (identityAttribute != null) {
                        AttributeRequest attr = req.getAttributeRequest(identityAttribute);
                        if (attr != null) {
                            Object value = attr.getValue();
                            if (value != null)
                                id = value.toString();
                        }
                    }
                }
            }
        }
        return id;
    }

    public static String getGroupName(SailPointContext context, ObjectRequest req)
        throws GeneralException {

        return getGroupName(context, req, null);
    }

    public static String getGroupName(SailPointContext context, ProvisioningPlan plan)
        throws GeneralException {

        String name = null;
        List<ObjectRequest> requests = plan.getObjectRequests();
        if (requests != null && requests.size() > 0) {
            ObjectRequest req = requests.get(0);
            name = getGroupName(context, req);
        }
        return name;
    }

    /**
     * Derive the a single unique name for this gropu by combining
     * other fields.  Be consistent with the ones above.
     */
    public static String getGroupName(ManagedAttribute group) {

        String appname = "???";
        Application app = group.getApplication();
        if (app != null)
            appname = app.getName();
        
        // ?? what about display name
        return appname + ":" + group.getNativeIdentity();
    }

}
