/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import sailpoint.api.Explanator;
import sailpoint.authorization.utils.LcmUtils;
import sailpoint.integration.ListResult;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.UIPreferences;
import sailpoint.service.CurrentAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * A sub-resource to deal with exceptions (additional entitlements) on an
 * identity.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ExceptionsResource extends BaseListResource {
    public static final String ENTITLEMENTS_REQUEST_COLUMNS_KEY = "sailpoint.web.lcm.EntitlementsRequestBean";

    private String identity;
    
    
    /**
     * Sub-resource constructor.
     */
    public ExceptionsResource(String identity, BaseResource parent) {
        super(parent);
        this.identity = identity;
    }

    /**
     * Return the identity we're operating on.
     */
    private Identity getIdentity() throws GeneralException {
        Identity i = getContext().getObjectById(Identity.class, this.identity);
        if (i == null) {
            throw new ObjectNotFoundException(Identity.class, this.identity);
        }
        return i;
    }
    
    /**
     * Return the exceptions on the identity, optionally filtering by
     * application and instance.  This returns a row per entitlement value.
     * 
     * @param  application   The application name or ID to filter by (optional).
     * @param  instance      The instance name to filter by (optional).
     * @param  excludePerms  If true, permissions are not returned.  If null or
     *                       false, permissions are returned (optional).
     * 
     * @return A ListResult with details about the exceptions.
     */
    @GET
    public ListResult getExceptions(@QueryParam("application") String application,
                                    @QueryParam("instance") String instance,
                                    @QueryParam("excludePermissions") Boolean excludePerms,
                                    @QueryParam("showEntitlementDescriptions") Boolean showEntDescs)
        throws GeneralException {

        Identity identity = getIdentity();
        String identityId = (identity == null) ? null : identity.getId(); 
        LcmUtils.authorizeTargetIdentity(identityId, getContext(), this);

        return getExceptions(application, instance, excludePerms, showEntDescs, true);
    }

    public ListResult getExceptions(String application, String instance, 
            Boolean excludePerms, Boolean showEntDescs, boolean trim)
        throws GeneralException {

        CurrentAccessService currentAccessService = new CurrentAccessService(getContext(), getIdentity());
        // No pending entitlements for classic UI
        boolean excludePermissions = (excludePerms != null) ? excludePerms.booleanValue() : false;
        List<CurrentAccessService.CurrentAccessEntitlement> entitlements = 
                currentAccessService.getExceptions(excludePermissions, true); 

        filter(entitlements, application, instance);
        
        // Determine whether we're showing descriptions initially.
        boolean showDescriptionsFirst = showDescriptionsFirst(showEntDescs);
        
        // Turn these into objects.
        List<EntitlementRow> rows = new ArrayList<EntitlementRow>(entitlements.size());
        for (CurrentAccessService.CurrentAccessEntitlement ent : entitlements) {
            rows.add(new EntitlementRow(ent, showDescriptionsFirst));
        }
        
        if (trim)
            return super.getListResultFromObjects(ENTITLEMENTS_REQUEST_COLUMNS_KEY, rows);
        else
            return super.getListResultFromObjects(ENTITLEMENTS_REQUEST_COLUMNS_KEY, rows, false);
        }
    
    /**
     * Return whether descriptions should be displayed initially.
     */
    private boolean showDescriptionsFirst(Boolean showEntDescs)
        throws GeneralException {
        
        if (null == showEntDescs) {
            Attributes<String,Object> defaults =
                getContext().getConfiguration().getAttributes();
            Identity id = getLoggedInUser();
            Object val = 
                id.getUIPreference(UIPreferences.PRF_DISPLAY_ENTITLEMENT_DESC,
                                   defaults, Configuration.DISPLAY_ENTITLEMENT_DESC);
            showEntDescs = Util.otob(val);
        }

        return showEntDescs;
    }
    
    /**
     * Filter the given list of EntitlementGroups based on the given criteria.
     */
    private void filter(List<CurrentAccessService.CurrentAccessEntitlement> ents, String application,
                        String instance) {

        application = (null != application) ? application.toUpperCase() : null;
        instance = (null != instance) ? instance.toUpperCase() : null;

        for (Iterator<CurrentAccessService.CurrentAccessEntitlement> it=ents.iterator(); it.hasNext(); ) {
            CurrentAccessService.CurrentAccessEntitlement entitlement = it.next();
            if ((null != application) &&
                    !entitlement.getApplicationName().toUpperCase().startsWith(application)) {
                it.remove();
                break;
            }

            if ((null != instance) &&
                    !entitlement.getInstance().toUpperCase().startsWith(instance)) {
                it.remove();
                break;
            }

            // If the thing has no attributes or permissions, get rid of it
            if (!entitlement.isPermission() && !entitlement.isAttribute()) {
                it.remove();
                break;
            }
        }
    }

    /**
     * An object used to format results with one entitlement per row when
     * listing entitlements.
     */
    public class EntitlementRow {

        private String id;
        private String application;
        private String instance;
        private String nativeIdentity;
        private String displayName;
        private String attribute;
        private String value;
        private String displayValue;
        private String permissionTarget;
        private String permissionRight;
        private String description;
        private boolean showDescriptionFirst;

        /**
         * Constructor.
         */
        public EntitlementRow(CurrentAccessService.CurrentAccessEntitlement ent, boolean showDescriptionFirst)
            throws GeneralException {

            this.id = String.valueOf(ent.hashCode());
            this.application = ent.getApplicationName();
            this.instance = ent.getInstance();
            this.nativeIdentity = ent.getNativeIdentity();
            this.displayName = ent.getAccount();
            this.showDescriptionFirst = showDescriptionFirst;

            if (ent.isAttribute()) {
                this.attribute = ent.getAttribute();
                this.value = ent.getValue();
                this.displayValue = this.value;

                // get the display name of any group attributes
                if (WebUtil.isGroupAttribute(this.application, this.attribute)) {
                    this.displayValue = WebUtil.getGroupDisplayableName(this.application,
                            this.attribute, this.value);
                }

                this.description = Explanator.getDescription(this.application, this.attribute, this.value, getLocale()); 
            } else if (ent.isPermission()) {
                this.permissionTarget = ent.getPermissionTarget();
                this.permissionRight = ent.getPermissionRights();
                this.description = Explanator.getPermissionDescription(this.application, this.permissionTarget, getLocale());
            } else {
                throw new RuntimeException("Expected single values entitlements.");
            }
        }
        
        public String getId() {
            return this.id;
        }
        public String getApplication() {
            return this.application;
        }
        public String getInstance() {
            return this.instance;
        }
        public String getNativeIdentity() {
            return this.nativeIdentity;
        }
        public String getDisplayName() {
            return this.displayName;
        }
        public String getAttribute() {
            return this.attribute;
        }
        public String getValue() {
            return this.value;
        }
        public String getDisplayValue() {
            return this.displayValue;
        }
        public String getPermissionTarget() {
            return this.permissionTarget;
        }
        public String getPermissionRight() {
            return this.permissionRight;
        }
        public String getDescription() {
            return this.description;
        }
        public boolean isShowDescriptionFirst() {
            return this.showDescriptionFirst;
        }
    }
}
