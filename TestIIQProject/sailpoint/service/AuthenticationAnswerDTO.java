package sailpoint.service;


import sailpoint.api.EncodingUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.AuthenticationQuestion;
import sailpoint.tools.GeneralException;

import java.util.Locale;
import java.util.Map;

/**
 * A DTO that holds the selected question and answer.
 */
public class AuthenticationAnswerDTO {

    public static String HASHED_ANSWER_MASKED = "******";

    private String id;
    private String questionId;
    private String answer;

    // Forgot Password case when we are answering these questions
    private String question;
    private String answerProvided;

    private String hashedAnswer;

    // The question index - 1-based used to format "Question #1".
    private int idx;

    /**
     * Constructor.  The answer and context will be null for a new answer.
     */
    public AuthenticationAnswerDTO(AuthenticationAnswer answer,
                                   int idx, SailPointContext context,
                                   Locale locale)
            throws GeneralException {

        this.idx = idx;
        AuthenticationQuestion selectedQuestion = null;

        // Pull stuff from the answer if we have one.
        if (null != answer) {
            this.id = answer.getId();
            if (EncodingUtil.isEncrypted(answer.getAnswer())) {
                this.answer = context.decrypt(answer.getAnswer());
            } else if (EncodingUtil.isHashed(answer.getAnswer())) {
                this.answer = HASHED_ANSWER_MASKED;
                this.setHashedAnswer(answer.getAnswer());
            }
            selectedQuestion = answer.getQuestion();
        }

        if (null != selectedQuestion) {
            this.questionId = selectedQuestion.getId();
            this.question = selectedQuestion.getQuestion(locale);
        }
    }

    /**
     * Construct using data map
     * @param dataMap
     */
    public AuthenticationAnswerDTO(Map<String, Object> dataMap) {
        this.id = (String) dataMap.get("id");
        this.questionId = (String) dataMap.get("questionId");
        this.answer = (String) dataMap.get("answer");
        this.idx = (Integer)dataMap.get("index");
    }

    /**
     * Copy the data from this DTO back onto the given AuthenticationAnswer.
     */
    public void populate(AuthenticationAnswer answer, SailPointContext context)
            throws GeneralException {

        // Sanity check.
        if ((null != this.id) && !this.id.equals(answer.getId())) {
            throw new RuntimeException("Expected same ID for authentication answer: " + this.id + " != " + answer.getId());
        }
        //do not reset the value if it is hashed
        if (!HASHED_ANSWER_MASKED.equals(this.answer)) {
            answer.setAnswer(this.answer != null ? this.answer.trim() : this.answer);
        }
        //We create AnswerDTO regardless of whether we have a question. THis means questionID can be null
        if(null != this.questionId)
            answer.setQuestion(context.getObjectById(AuthenticationQuestion.class, this.questionId));
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestionId() {
        return this.questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getAnswer() {
        return this.answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public int getIndex() {
        return this.idx;
    }

    public void setIndex(int idx) {
        this.idx = idx;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswerProvided() {
        return answerProvided;
    }

    public void setAnswerProvided(String answerProvided) {
        this.answerProvided = answerProvided;
    }

    public String getHashedAnswer() {
        return hashedAnswer;
    }

    public void setHashedAnswer(String hashedAnswer) {
        this.hashedAnswer = hashedAnswer;
    }
}
