package sailpoint.rest.ui;

import sailpoint.authorization.SystemConfigOptionsAuthorizer;
import sailpoint.object.Configuration;
import sailpoint.rest.BaseResource;
import sailpoint.service.AuthenticationAnswerDTO;
import sailpoint.service.SecurityQuestionsService;
import sailpoint.service.AuthenticationQuestionDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resource for getting and setting security answers and questions.
 */
@Path("securityQuestions")
public class SecurityQuestionsResource extends BaseResource {

    /**
     * Constructor
     */
    public SecurityQuestionsResource() {
        super();
    }

    /**
     * Get the list of security answers.
     * Used for security question/answer setup not for actual auth using security questions.
     * If the user has answers then there will be answers to the questions otherwise if the user has not setup answers
     * then they will be empty. The number of answers returned will be the number of auth answers required.
     * @return List list of the security answer DTO objects
     */
    @GET @Path("answers")
    public List<AuthenticationAnswerDTO> getAuthenticationAnswers() throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.AUTH_QUESTIONS_ENABLED));
        SecurityQuestionsService securityQuestionsService = new SecurityQuestionsService(this);
        return securityQuestionsService.getAuthenticationAnswersForSetup(this.getLoggedInUser());
    }

    /**
     * Set the list of security answers.

     * @return Response
     */
    @POST
    @Path("answers")
    public Response setAuthenticationAnswers(List<Map<String, Object>> answers) throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.AUTH_QUESTIONS_ENABLED));

        List<AuthenticationAnswerDTO> securityAnswerDTOS = new ArrayList<>();

        // convert map to dtos
        for (Map answerMap : answers) {
            if (answerMap != null) {
                securityAnswerDTOS.add(new AuthenticationAnswerDTO(answerMap));
            }
        }

        SecurityQuestionsService securityQuestionsService = new SecurityQuestionsService(this);

        List<Message> errors = securityQuestionsService.validateAuthenticationAnswers(securityAnswerDTOS, false);

        if (errors.size() > 0) {
            List<String> errMsgs = new ArrayList<>();
            // convert error messages to strings
            for (Message msg : errors) {
                errMsgs.add(msg.getLocalizedMessage());
            }
            return Response.status(Response.Status.BAD_REQUEST).entity(errMsgs).build();
        }

        securityQuestionsService.saveAnswers(securityAnswerDTOS, this.getLoggedInUser());

        return Response.ok().build();
    }

    /**
     * Get the list of possible questions a user may choose to answer for security.
     * @return List<AuthenticationQuestionDTO>
     * @throws GeneralException
     */
    @GET @Path("questions")
    public List<AuthenticationQuestionDTO> getQuestions() throws GeneralException {
        authorize(new SystemConfigOptionsAuthorizer(Configuration.AUTH_QUESTIONS_ENABLED));
        SecurityQuestionsService securityQuestionsService = new SecurityQuestionsService(this);
        return securityQuestionsService.getQuestions();
    }
}
