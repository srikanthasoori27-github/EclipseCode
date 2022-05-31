package sailpoint.service;

import sailpoint.object.AuthenticationQuestion;
import sailpoint.web.UserContext;

/**
 * AuthenticationQuestion DTO
 */
public class AuthenticationQuestionDTO {
    private String id;
    private String question;

    public AuthenticationQuestionDTO(UserContext context, AuthenticationQuestion authenticationQuestion) {
        this.id = authenticationQuestion.getId();
        this.question = authenticationQuestion.getQuestion(context.getLocale());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
