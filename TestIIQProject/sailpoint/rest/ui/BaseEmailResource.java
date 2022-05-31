/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui;

import sailpoint.api.BasicMessageRepository;
import sailpoint.rest.BaseResource;
import sailpoint.service.EmailService;
import sailpoint.service.EmailTemplateDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;

/**
 * Base sub resource that can easily be used for a pair of GET and POST methods
 * to fetch and send a related email template
 */
public class BaseEmailResource extends BaseResource {

    /**
     * Classes that use BaseEmailResource should implement this abstract class
     * for fetching the email template
     */
    public abstract static class EmailResourceContext {

        /**
         * Get the email template DTO that can be modified before sending. This must
         * be implemented by anyone using BaseEmailResource
         * @param comment comment to use in the template
         * @return EmailTemplateDTO
         * @throws GeneralException
         */
        public abstract EmailTemplateDTO getEmailTemplate(String comment) throws GeneralException;
    }

    private static final String EMAIL_TEMPLATE_PARAM = "emailTemplate";

    private EmailResourceContext emailContext;

    /**
     * Constructor
     * @param emailContext The EmailResourceContext implementation to use.
     * @param parent Parent BaseResource
     */
    public BaseEmailResource(EmailResourceContext emailContext, BaseResource parent) {
        super(parent);

        this.emailContext = emailContext;
    }

    /**
     * Fetches an EmailTemplateDTO that can be modified before sending. Relies on the
     * EmailResourceContext for logic.
     * @return EmailTemplateDTO
     * @throws GeneralException
     */
    @GET
    public EmailTemplateDTO getTemplate() throws GeneralException {
        return this.emailContext.getEmailTemplate(null);
    }

    /**
     * Sends the provided template out through the email service.
     * @param inputs Map of inputs. emailTemplate is required!
     * @return Response indicating success.
     * @throws GeneralException
     */
    @POST
    @Path("send")
    public Response sendEmail(Map<String, Object> inputs) throws GeneralException {
        if (inputs == null || inputs.get(EMAIL_TEMPLATE_PARAM) == null) {
            throw new InvalidParameterException(EMAIL_TEMPLATE_PARAM);
        }

        EmailTemplateDTO dto = new EmailTemplateDTO((HashMap) inputs.get(EMAIL_TEMPLATE_PARAM));
        BasicMessageRepository errorHandler = new BasicMessageRepository();
        EmailService service = new EmailService(getContext(), errorHandler);

        service.sendEmail(dto, this.emailContext.getEmailTemplate(StringEscapeUtils.escapeHtml4(dto.getComment())), getLoggedInUser());

        if (errorHandler.getMessages().isEmpty()) {
            return Response.ok().build();
        }

        return Response.notModified().entity(errorHandler.getMessages()).status(500).build();
    }
}
