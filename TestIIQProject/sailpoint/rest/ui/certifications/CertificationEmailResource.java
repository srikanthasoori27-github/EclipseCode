/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.certifications;

import javax.ws.rs.Path;

import sailpoint.object.Certification;
import sailpoint.object.EmailTemplate;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.BaseEmailResource;
import sailpoint.service.EmailService;
import sailpoint.service.EmailTemplateDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

/**
 * Resource to handle certification emails
 */
public class CertificationEmailResource extends BaseResource {

    /**
     * Implementation of EmailResourceContext to use with reminder emails
     */
    private class CertificationReminderEmailContext extends BaseEmailResource.EmailResourceContext {
        private String certificationId;

        /**
         * Constructor
         * @param certificationId ID of the certification
         */
        CertificationReminderEmailContext(String certificationId) {
            this.certificationId = certificationId;
        }

        /**
         * Implementation of getEmailTemplate to fetch the template for certification reminders
         */
        @Override
        public EmailTemplateDTO getEmailTemplate(String comment) throws GeneralException {
            EmailService service = new EmailService(getContext());
            EmailTemplate template = service.getCertificationReminderTemplate(getLoggedInUser(), this.certificationId, comment);
            EmailTemplateDTO templateDTO = new EmailTemplateDTO(template);
            templateDTO.setToIdentityWithIdentity(service.getCertReminderEmailRecipient(this.certificationId));
            return templateDTO;
        }

    }

    private String certificationId;

    /**
     * Constructor.
     *
     * @param parent        BaseResource
     * @param certification Certification to use for emails
     * @throws GeneralException
     */
    public CertificationEmailResource(BaseResource parent, Certification certification) throws GeneralException {
        super(parent);

        if (certification == null) {
            throw new InvalidParameterException("certification");
        }

        this.certificationId = certification.getId();
    }

    /**
     * Passthrough to an instance of BaseEmailResource to handle reminder emails.
     * @return BaseEmailResource using CertificationReminderEmailContext
     * @throws GeneralException
     */
    @Path("reminder")
    public BaseEmailResource getReminderEmailResource() throws GeneralException {
        return new BaseEmailResource(new CertificationReminderEmailContext(this.certificationId), this);
    }
}
