/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.pam;

import java.util.List;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Target;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This service contains methods that help to define the relationships between PAM object and users and groups when
 * the PAM system relies on an external user store (such as Active Directory or LDAP) for some of its user information.
 * When using an external user store, there are two Links/ManagedAttributes in IIQ for each PAM user/group.  One Link/MA
 * lives on the external application (eg - the AD account or group).  The other Link/MA lives on the PAM system and is
 * a "stub" account/group that has a loose reference to the external account/group.
 *
 * The relationship between a stub Link/ManagedAttribute and the external Link/ManagedAttribute is maintained in two
 * attributes that exist on the stub PAM Link/MA:
 *
 *  - nativeIdentifier: The native identity of the external account/group object that the stub is linked to.
 *  - source: The name of the IIQ application on which the external account/group lives.
 *
 * In order to query across this relationship, the nativeIdentifier and the source are also stored in correlation keys
 * on the Link/ManagedAttribute.
 */
public class PamExternalUserStoreService {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The name of the attribute on Links and ManagedAttributes that holds the external native identifier.
     */
    public static final String ATTR_EXTERNAL_NATIVE_IDENTIFIER = "nativeIdentifier";

    /**
     * The name of the attribute on Links and ManagedAttributes that holds the external application name.
     */
    public static final String ATTR_EXTERNAL_SOURCE = "source";


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;

    private Target target;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     *
     * @param context  The SailPointContext to use.
     */
    public PamExternalUserStoreService(SailPointContext context, Target target) {
        this.context = context;
        this.target = target;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Given the attributes from a Link or ManagedAttribute, return the source native identifier if there is one.
     *
     * @param attrs  The Attributes from a Link or ManagedAttribute.
     *
     * @return The source native identifier if there is one.
     */
    public String getExternalNativeIdentity(Attributes<String,Object> attrs) {
        return (null != attrs) ? (String) attrs.get(ATTR_EXTERNAL_NATIVE_IDENTIFIER) : null;
    }

    /**
     * Given the attributes from a Link or ManagedAttribute, return the source application name if there is one.
     *
     * @param attrs  The Attributes from a Link or ManagedAttribute.
     *
     * @return The source application if there is one.
     */
    public String getExternalApplicationName(Attributes<String,Object> attrs) {
        return (null != attrs) ? (String) attrs.get(ATTR_EXTERNAL_SOURCE) : null;
    }

    /**
     * Given the attributes from a Link or ManagedAttribute, return the source application if there is one.
     *
     * @param attrs  The Attributes from a Link or ManagedAttribute.
     *
     * @return The source application if there is one.
     */
    public Application getExternalApplication(Attributes<String,Object> attrs) throws GeneralException {
        Application app = null;

        String appName = getExternalApplicationName(attrs);
        if (null != appName) {
            app = this.context.getObjectByName(Application.class, appName);
            if (null == app) {
                throw new GeneralException("The external application '" + appName + "' could not be found.");
            }
        }

        return app;
    }

    /**
     * Return the external Link that is referenced by the given PAM link, if one exists.
     *
     * @param pamLink  The PAM "stub" link for which to return the external Link.
     *
     * @return The external Link that is referenced by the given PAM link, or null if one does not exist.
     */
    public Link getExternalLink(Link pamLink) throws GeneralException {
        Link externalLink = null;

        String nativeId = getExternalNativeIdentity(pamLink.getAttributes());

        if (null != nativeId) {
            Application app = getExternalApplication(pamLink.getAttributes());
            if (null != app) {
                IdentityService svc = new IdentityService(this.context);
                externalLink = svc.getLink(pamLink.getIdentity(), app, null, nativeId);
            }
        }

        return externalLink;
    }

    /**
     * Return the ManagedAttribute for the given PAM group, or the associated external group if the given ID
     * references an external group.
     *
     * @param groupId  The ID of the ManagedAttribute for the PAM local or stub group.
     *
     * @return The ManagedAttribute for the given PAM group, or the associated external group if the given ID
     *     references an external group.
     */
    public ManagedAttribute getExternalOrLocalGroup(String groupId) throws GeneralException {
        ManagedAttribute ma = this.context.getObjectById(ManagedAttribute.class, groupId);

        // Check to see if this is an external group.  If so, use the managed attribute for the external group rather
        // than the local PAM group.
        String externalNativeId = this.getExternalNativeIdentity(ma.getAttributes());
        if (null != externalNativeId) {
            Application app = this.getExternalApplication(ma.getAttributes());
            if (null != app) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("application", app));
                qo.add(Filter.eq("value", externalNativeId));
                List<ManagedAttribute> attrs = this.context.getObjects(ManagedAttribute.class, qo);
                if (!Util.isEmpty(attrs)) {
                    if (attrs.size() > 1) {
                        throw new GeneralException("Found multiple managed attributes on '" + app.getName() +
                                                   "' with native identity " + externalNativeId);
                    }
                    ma = attrs.get(0);
                }
            }
        }

        return ma;
    }

    /**
     * Return whether the given group is an external PAM group (ie - is defined outside of the PAM application).
     *
     * @param group  The group to check.
     *
     * @return True if the given group is external, false otherwise.
     */
    public boolean isExternalGroup(ManagedAttribute group) throws GeneralException {
        Application pamApp = PamUtil.getApplicationForTarget(context, target);
        return !pamApp.equals(group.getApplication());
    }

    /**
     * Return the stub group that lives on the PAM application for the given external group.
     *
     * @param externalGroup  The external group that defines the membership outside of the PAM system.
     *
     * @return The PAM stub group associated with the given external group, or null if one cannot be found.
     */
    public ManagedAttribute findStubGroupForExternalGroup(ManagedAttribute externalGroup) throws GeneralException {
        ManagedAttribute stubGroup = null;

        String nativeIdKey = this.getNativeIdentifierCorrelationKey(false);
        String sourceKey = this.getSourceCorrelationKey(false);

        if((nativeIdKey != null) && (sourceKey != null)) {
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq(nativeIdKey, externalGroup.getValue()));
            qo.add(Filter.eq(sourceKey, externalGroup.getApplication().getName()));

            List<ManagedAttribute> attrs = this.context.getObjects(ManagedAttribute.class, qo);
            if (!attrs.isEmpty()) {
                if (attrs.size() == 1) {
                    stubGroup = attrs.get(0);
                } else if (attrs.size() > 1) {
                    //In case we have containers with same name each one from a different application
                    Application currentApp = PamUtil.getApplicationForTarget(context, target);
                    for ( ManagedAttribute ma: attrs) {
                        if (ma.getApplicationId().equals(currentApp.getId())) {
                            stubGroup = ma;
                            break;
                        }
                    }
                }
            }
        }

        return stubGroup;
    }

    /**
     * Return the attribute name for the correlation key on the stub PAM account/group that correlates to the native
     * identity of the external account/group.
     *
     * @param isAccount  If true, the account's external native identifier correlation key is returned, otherwise the
     *     group's correlation key is returned.
     *
     * @return The attribute name for the source native identifier on the PAM application's account/group schema.
     *
     * @throws GeneralException If the PAM application does not have a correlation key for the native identifier on
     *     the account/group schema.
     */
    public String getNativeIdentifierCorrelationKey(boolean isAccount) throws GeneralException {
        return getCorrelationKey(ATTR_EXTERNAL_NATIVE_IDENTIFIER, isAccount);
    }

    /**
     * Return the attribute name for the correlation key on the stub PAM account/group that correlates to the source
     * application of the external account/group.
     *
     * @param isAccount  If true, the account's external source application correlation key is returned, otherwise the
     *     group's correlation key is returned.
     *
     * @return The attribute name for the source application on the PAM application's account/group schema.
     *
     * @throws GeneralException If the PAM application does not have a correlation key for the source application on
     *     the account/group schema.
     */
    public String getSourceCorrelationKey(boolean isAccount) throws GeneralException {
        return getCorrelationKey(ATTR_EXTERNAL_SOURCE, isAccount);
    }

    /**
     * Return the attribute name for the correlation key on the given attribute and schema.
     *
     * @param attrName  The name of the AttributeDefinition for which to find the correlation key.
     * @param isAccount  If true, search in the account schema, otherwise search in the group schema.
     *
     * @return The attribute name for the correlation key on the given attribute and schema.
     *
     * @throws GeneralException If the PAM application does not have a correlation key for the given attribute
     *     and schema.
     */
    private String getCorrelationKey(String attrName, boolean isAccount) throws GeneralException {
        String schemaName = (isAccount) ? Connector.TYPE_ACCOUNT : Connector.TYPE_GROUP;

        Application app = PamUtil.getApplicationForTarget(context, target);
        Schema schema = app.getSchema(schemaName);
        if (null == schema) {
            throw new GeneralException("A " + schemaName + " schema is required on " + app.getName());
        }

        AttributeDefinition attrDef = schema.getAttributeDefinition(attrName);
        if (null == attrDef) {
            return null;
        }

        int key = attrDef.getCorrelationKey();
        if (key <= 0) {
            throw new GeneralException("Expected " + attrName + " to be a correlation key");
        }

        return "key" + key;
    }
}
