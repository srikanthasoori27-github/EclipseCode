package sailpoint.service.certification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.RoleSnapshot;
import sailpoint.object.RoleSnapshot.ProfileSnapshot;
import sailpoint.service.identity.BaseEntitlementDTO;
import sailpoint.service.identity.RoleProfileDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.ProfileDTO;
import sailpoint.web.modeler.RoleUtil;

/**
 * Class extracting logic from ProfileColumn into a common area.  Used to provide RoleProfile details to
 * BusinessRoleProfile CertificationItem types, as well as other places we show profile details.
 */
public class RoleProfileHelper {

    private static final Log log = LogFactory.getLog(RoleProfileHelper.class);

    private final ProfileDTO profile;
    private final SailPointContext context;
    private final Locale locale;
    private final TimeZone timeZone;

    /**
     * Constructor for use with ProfileSnapshot
     * @param profile ProfileSnapshot
     * @param context SailPointContext
     * @param locale Locale
     * @param timeZone TimeZone
     * @throws GeneralException
     */
    public RoleProfileHelper(ProfileSnapshot profile, SailPointContext context, Locale locale, TimeZone timeZone) throws GeneralException {
        this.profile = new ProfileDTO();
        this.profile.setApplication(context.getObjectByName(Application.class, profile.getApplication()));
        this.profile.setConstraints(profile.getConstraints());
        for (Permission permission : Util.iterate(this.profile.getPermissions())) {
            this.profile.addPermission(permission);
        }
        this.context = context;
        this.locale = locale;
        this.timeZone = timeZone;
    }

    /**
     * Constructor for use with live Profile
     * @param profile ProfileSnapshot
     * @param context SailPointContext
     * @param locale Locale
     * @param timeZone TimeZone
     * @throws GeneralException
     */
    public RoleProfileHelper(Profile profile, SailPointContext context, Locale locale, TimeZone timeZone) {
        this.profile = new ProfileDTO(profile);
        this.context = context;
        this.locale = locale;
        this.timeZone = timeZone;
    }

    /**
     * Returns the localized name of the role profile
     * @return The localized name of the role profile
     */
    public String getName() {
        Message msg = new Message(MessageKeys.TEXT_ENTITLEMENTS_ON_APP, profile.getApplication());
        return msg.getLocalizedMessage(locale, timeZone);
    }

    /**
     * Returns list of descriptions of the role profile's constraints
     * @return list of descriptions of the role profile's constraints
     * @throws GeneralException If unable to get any constraints description
     */
    public List<String> getContraintsDescription() {
        List<String> contraintsDescription = new ArrayList<String>();

        if (profile != null && profile.getConstraints() != null && !profile.getConstraints().isEmpty()) {

            for (Filter f : profile.getConstraints()) {
                // We used to use some allegedly more human readable expression here, but it was lacking
                // in detail and isn't what someone would see in the role editor anyway, so let just use
                // the filter string for now everywhere.
                contraintsDescription.add(f.getExpression(true));
            }
        }

        return contraintsDescription;
    }

    /**
     * Returns list of descriptions of profile's permissions
     * @return list of descriptions of profile's permissions
     */
    public Map<String, String> getPermissions() {
        Map<String, String> permissions = new HashMap<>();
        if (profile != null && profile.getPermissions() != null && !profile.getPermissions().isEmpty()) {
            for (Permission permission : profile.getPermissions()) {
                permissions.put(permission.getTarget(), permission.getRights());
            }
        }
        return permissions;
    }

    /**
     * Returns a RoleProfileDTO for the given ProfileSnapshot based on a RoleSnapshot
     * @param roleSnapshot RoleSnapshot
     * @return RoleProfileDTO
     * @throws GeneralException
     */
    public RoleProfileDTO getRoleProfileDTO(RoleSnapshot roleSnapshot) throws GeneralException {
        // Fudge a role with only the pieces needed to pass simple entilement testings in RoleUtil.
        Bundle role = new Bundle();
        role.setName(roleSnapshot.getObjectName());
        role.setDisplayName(roleSnapshot.getObjectDisplayableName());
        // RoleUtil assumes if there are no profiles it is new and always simple, so we have to fudge a profile too
        role.assignProfiles(Collections.singletonList(this.profile.getProfile()));

        return getRoleProfileDTO(role, false);
    }

    /**
     * Creates a RoleProfileDTO for the given Profile based on a live role
     * @param role Bundle that owns this profile
     * @param includeClassifications True to include classifications in entitlements, otherwise false
     * @return RoleProfileDTO
     */
    public RoleProfileDTO getRoleProfileDTO(Bundle role, boolean includeClassifications) {
        List<RoleProfileDTO> profiles = new ArrayList<>();

        RoleProfileDTO dto = new RoleProfileDTO();
        Application app = profile.getApplication();
        dto.setApplication(app.getName());
        if (profile.getConstraints() != null) {
            dto.setConstraints(profile.getConstraints().stream()
                    .map(filter -> filter.getExpression(true))
                    .collect(Collectors.toList())
            );
        }
        if (profile.getPermissions() != null) {
            dto.setPermissions(profile.getPermissions().stream().collect(Collectors.toMap(Permission::getTarget, Permission::getRights)));
        }
        try {
            // Sigh, I hate that all our RoleUtil methods use JSON instead of objects but I dont want to rewrite that now.
            String entitlementJson = RoleUtil.getReadOnlySimpleEntitlementsJson(role, null, Collections.singletonList(this.profile), false, this.locale, new RoleUtil.SimpleEntitlementCriteria());
            Map<String, Object> entitlementMap = JsonHelper.mapFromJson(String.class, Object.class, entitlementJson);
            if (!Util.isEmpty(entitlementMap)) {
                List<RoleUtil.LeafFilterEntitlement> entitlements = JsonHelper.listFromJson(RoleUtil.LeafFilterEntitlement.class, JsonHelper.toJson(entitlementMap.get("entitlements")));
                if (entitlements != null) {
                    dto.setEntitlements(entitlements.stream().map((ent) -> createEntitlement(ent, app, includeClassifications)).collect(Collectors.toList()));
                }
            }
        } catch (Exception ex) {
            // If we get an exception here, just log it and move on, user can still see the constraint string if no entitlements are set.
            log.debug("Unable to get simple entitlements", ex);
        }

        return dto;
    }

    /**
     * Create a BaseEntitlementDTO from the LeafFilterEntitlement returned from RoleUtil.getSimpleLeafJSON
     * @param entitlement LeafFilterEntitlement
     * @param application The Application
     * @return BaseEntitlementDTO
     */
    private BaseEntitlementDTO createEntitlement(RoleUtil.LeafFilterEntitlement entitlement, Application application, boolean includeClassifications) {

        BaseEntitlementDTO entitlementDTO = new BaseEntitlementDTO();
        entitlementDTO.setApplication(entitlement.getApplicationName());
        entitlementDTO.setApplicationId(application.getId());
        entitlementDTO.setAttribute(entitlement.getProperty());
        entitlementDTO.setValue(entitlement.getValue());

        // This is only set if the Explanator has a hit, which means its a ManagedAttribute, so flesh the rest out from there.
        if (!Util.isNullOrEmpty(entitlement.getDisplayValue())) {
            entitlementDTO.setDisplayValue(entitlement.getDisplayValue());
            entitlementDTO.setDescription(entitlement.getDescription());
            if (includeClassifications && !Util.isNullOrEmpty(entitlement.getClassifications())) {
                entitlementDTO.setClassificationNames(Util.csvToList(entitlement.getClassifications()));
            }
            try {
                // Ugh, still need to get the MA id from ManagedAttributer
                ManagedAttribute ma = ManagedAttributer.get(this.context, application, entitlementDTO.getAttribute(), entitlementDTO.getValue());
                // Should never be null if the other parts are set, but if it happens then its not a useful group from a UI perspective
                if (ma != null) {
                    entitlementDTO.setManagedAttributeId(ma.getId());
                    entitlementDTO.setGroup(true);
                }
            } catch (GeneralException ex) {
                // If we have some issue here, just log it and move on, entitlements are "nice to have" but no reason to blow up over them.
                log.debug("Error fetching ManagedAttribute", ex);
            }
        }

        return entitlementDTO;
    }

}
