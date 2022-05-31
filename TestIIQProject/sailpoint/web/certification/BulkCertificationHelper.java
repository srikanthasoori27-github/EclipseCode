/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.CertificationSchedule;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.NavigationHistory.Page;

/**
 * This class contains static methods used to help beans to schedule bulk certifications
 * @author Bernie Margolis
 */
public class BulkCertificationHelper {
    final SailPointContext context;
    final Map sessionScope;
    final Page whereToReturn;
    
    /**
     * Identity IDs to include in a Bulk Certification
     */
    private List<String> selectedIdentities;

    /**
     * Identity Names to include in a Bulk Certification. This list is used when
     * possible to avoid an expensive call to ObjectUtil.convertToNames
     */
    private List<String> selectedIdentityNames;
    
    /**
     * Reverse the selection (i.e. include all the identities that aren't selected) if this is true;
     * Leave it as is otherwise
     */
    private boolean certifyAll;

    public BulkCertificationHelper(final SailPointContext context, List<String> selectedIdentities, boolean certifyAll,
                                   List<String> selectedIdentityNames) {
        this(context, selectedIdentities, certifyAll);
        this.selectedIdentityNames = selectedIdentityNames;
    }

    public BulkCertificationHelper(final SailPointContext context, List<String> selectedIdentities, boolean certifyAll) {
        this(null, context, null);
        this.selectedIdentities = selectedIdentities;
        this.certifyAll = certifyAll;
    }

    public BulkCertificationHelper(final Map sessionScope, final SailPointContext context, final Page whereToReturn) {
        this.context = context;
        this.sessionScope = sessionScope;
        this.whereToReturn = whereToReturn;
    }
    
    /**
     * Bulk/Individual certification setting that specifies the identities being certified
     * @return
     */
    public List<String> getSelectedIdentities() {
        return selectedIdentities;
    }
    public void setSelectedIdentities(List<String> selectedIdentities) {
        this.selectedIdentities = selectedIdentities;
    }

    /**
     * Bulk/Individual certification setting that specifies the identities being certified
     * @return
     */
    public List<String> getSelectedIdentityNames() {
        return selectedIdentityNames;
    }
    public void setSelectedIdentityNames(List<String> selectedIdentityNames) {
        this.selectedIdentityNames = selectedIdentityNames;
    }

    /**
     * @return A flag that determines if we should add all the identities returned by the search to the 
     * certification.  This is an alternative to the identitiesToCertify.
     */
    public boolean isCertifyAll() {
        return certifyAll;
    }

    /**
     * Set the flag that determines if we should add all the identities returned by the search to the 
     * certification.  This is an alternative to the identitiesToCertify.
     */
    public void setCertifyAll(boolean certifyAll) {
        this.certifyAll = certifyAll;
    }

    /**
     * Generate a certification schedule DTO, put it on the session, and then set the navigation history.
     * @param availableIdentities list of maps containing identity ids and names to generate cert schedule for
     * @param schedulingIdentity scheduling identity
     * @return navigation string to go to the cert scheduling page
     * @throws GeneralException
     */
    public String scheduleBulkCertificationAction(List<Map<String, String>> availableIdentities, Identity schedulingIdentity)
            throws GeneralException {
        if (schedulingIdentity ==  null) {
            throw new InvalidParameterException("scheduling identity param required");
        }

        CertificationScheduleDTO newCertSchedule = this.generateCertificationScheduleDTO(availableIdentities, schedulingIdentity);

        // Put the right stuff in the session before transitioning pages.
        CertificationScheduleBean.newSchedule(sessionScope, newCertSchedule);

        NavigationHistory.getInstance().saveHistory(whereToReturn);
        return "scheduleBulkCertification";
    }

    /**
     * Generate a CertificationScheduleDTO
     *
     * @param availableIdentities list of maps containing identity ids and names to generate cert schedule for
     * @param schedulingIdentity the identity scheduling the cert
     * @return CertificationScheduleDTO the dto object
     * @throws GeneralException
     */
    public CertificationScheduleDTO generateCertificationScheduleDTO(List<Map<String, String>> availableIdentities,
                                                                     Identity schedulingIdentity) throws GeneralException {
        if (schedulingIdentity ==  null) {
            throw new InvalidParameterException("scheduling identity param required");
        }

        CertificationSchedule schedule = new CertificationSchedule(context, schedulingIdentity);
        List<String> identitiesToCertify;

        // Invert the selection if isCertifyAll is checked
        if (certifyAll) {
            identitiesToCertify = new ArrayList<>();

            for (Map<String, String> identityAttrs : Util.iterate(availableIdentities)) {
                if (selectedIdentities != null) {
                    if(!selectedIdentities.contains(identityAttrs.get("id"))) {
                        identitiesToCertify.add(identityAttrs.get("name"));
                    }
                } else {
                    identitiesToCertify.add(identityAttrs.get("name"));
                }
            }
        // Use names if they're available, otherwise resort to looking up by id
        } else if (!Util.isEmpty(selectedIdentityNames)) {
            identitiesToCertify = selectedIdentityNames;
        } else {
            identitiesToCertify = ObjectUtil.convertToNames(context, Identity.class, selectedIdentities);
        }

        schedule.getDefinition().setIdentitiesToCertify(identitiesToCertify);
        schedule.getDefinition().setType(Certification.Type.Identity);

        return new CertificationScheduleDTO(this.context, schedule);
    }
}
