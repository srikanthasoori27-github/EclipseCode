package sailpoint.service;

import sailpoint.api.Challenger;
import sailpoint.api.EncodingUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.AuthenticationQuestion;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.*;

/**
 * Service for authentication answers and questions
 */
public class SecurityQuestionsService {
    private UserContext userContext;
    private SailPointContext context;

    /**
     * Constructor
     * @param userContext
     */
    public SecurityQuestionsService(UserContext userContext) {
        this.userContext = userContext;
        this.context = userContext.getContext();
    }

    /**
     * Get the authentication question/answers for user. Used to setup auth questions not for answering auth questions.
     * @param identity Identity to get authentication answers for
     * @return List<AuthenticationAnswersDTO> list of AuthenticationAnswerDTOs
     * @throws GeneralException
     */
    public List<AuthenticationAnswerDTO> getAuthenticationAnswersForSetup(Identity identity) throws GeneralException {
        return this.loadAnswers(identity, true);
    }

    /**
     * Get the authentication question/answers for user. Used for answering auth questions.
     * @param identity Identity to get authentication answers for
     * @return List<AuthenticationAnswersDTO> list of AuthenticationAnswerDTOs
     * @throws GeneralException
     */
    public List<AuthenticationAnswerDTO> getAuthenticationAnswers(Identity identity) throws GeneralException {
        return this.loadAnswers(identity, false);
    }

    /**
     * internal helper for loading answers
     * @param forSetup true if getting answers for setup
     * @return List<AuthenticationAnswerDTO> list of AuthenticationAnswerDTO
     */
    private List<AuthenticationAnswerDTO> loadAnswers(Identity identity, boolean forSetup) throws GeneralException {
        Challenger challenger = new Challenger(this.context);

        int numRequired = challenger.getNumCorrectAnswersRequired();

        if (forSetup) {
            numRequired = challenger.getNumAnswersRequired();
        }

        return this.loadAnswers(identity, numRequired);
    }

    /**
     * Load answers for identity
     * @param identity Identity to get authentication answers for
     * @param numRequired number of items to load
     * @return List<AuthenticationAnswersDTO> list of AuthenticationAnswerDTOs
     * @throws GeneralException
     */
    private List<AuthenticationAnswerDTO> loadAnswers(Identity identity, int numRequired) throws GeneralException {
        List<AuthenticationAnswerDTO> answers = new ArrayList<>();
        List<AuthenticationAnswer> authenticationAnswers = identity.getAuthenticationAnswers();
        Iterator<AuthenticationAnswer> iterAuthAnswers = authenticationAnswers.iterator();
        for (int i = 1; i <= numRequired; i++) {
            AuthenticationAnswer authenticationAnswer = null;
            if (iterAuthAnswers.hasNext()) {
                authenticationAnswer = iterAuthAnswers.next();
            }
            answers.add(new AuthenticationAnswerDTO(authenticationAnswer, i, this.context, userContext.getLocale()));

        }
        return answers;
    }

    /**
     * Validate answers.
     * If answerId exists that means the user is editing or removing the answer.
     * If answerId does not exist that means this is a new answer setup.
     * If the answerId exists and the questionId does not that means the answer is being cleared.
     * If the answerId does not exist and the questionId does not exist there is nothing to do
     *
     * Validation rules:
     *
     * Each selected question must be unique.
     * Each answer must be unique.
     *
     * @param dtos list of AuthenticationAnswerDTO
     * @param requireAnswers true if all questions must be answered. false when setting up auth questions
     * @return List<Message> list of error messages
     * @throws GeneralException
     */
    public List<Message> validateAuthenticationAnswers(List<AuthenticationAnswerDTO> dtos, boolean requireAnswers) throws GeneralException {
        List<Message> errors = new ArrayList<>();

        if (dtos == null || dtos.size() == 0) {
            return errors;
        }

        List<String> selectedQuestions = new ArrayList<>();
        List<String> selectedAnswers = new ArrayList<>();

        List<String> hashedAnswers = new ArrayList<>();
        //add all hashed answers, so can check uniqueness
        for (AuthenticationAnswerDTO dto : dtos) {
            if (AuthenticationAnswerDTO.HASHED_ANSWER_MASKED.equals(dto.getAnswer())) {
                hashedAnswers.add(dto.getHashedAnswer());
            }
        }

        for (AuthenticationAnswerDTO answerDTO : dtos) {
            String answerId = answerDTO.getId();
            String questionId = answerDTO.getQuestionId();
            String answer = answerDTO.getAnswer();
            answer = (answer != null) ? answer.toLowerCase().trim() : null;

            int index = answerDTO.getIndex();

            // check if there is no answerId and questionId
            if (Util.isNullOrEmpty(answerId) && Util.isNullOrEmpty(questionId) && Util.isNullOrEmpty(answer) && !requireAnswers) {
                // nothing was filled in skip this
                continue;
            }

            // if answerId exists then we are modifying an answer
            if (Util.isNotNullOrEmpty(answerId)) {
                AuthenticationAnswer authenticationAnswer = this.context.getObjectById(AuthenticationAnswer.class, answerId);

                if (authenticationAnswer == null) {
                    // bad request invalid answerId
                    errors.add(new Message(Message.Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_ANSWER_REQUIRED, Integer.toString(index)));
                }
            }

            // check the question
            if (Util.isNullOrEmpty(questionId)) {
                // if questionId is null and there is an answer we need the questionId
                if (requireAnswers || Util.isNotNullOrEmpty(answer)) {
                    errors.add(new Message(Message.Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_QUESTION_REQUIRED, Integer.toString(index)));
                }
            }
            else {
                // check if dupe
                if (selectedQuestions.contains(questionId)) {
                    errors.add((new Message(Message.Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_DUPLICATE_QUESTION, Integer.toString(index))));
                }
                else {
                    // try to get the question
                    AuthenticationQuestion authenticationQuestion = this.context.getObjectById(AuthenticationQuestion.class, questionId);

                    // check question exists and answer exists
                    if (authenticationQuestion == null) {
                        // invalid questionId
                        errors.add(new Message(Message.Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_QUESTION_REQUIRED, Integer.toString(index)));
                    }
                }
                selectedQuestions.add(questionId);
            }

            // check the answer
            if (Util.isNullOrEmpty(answer)) {
                if (requireAnswers || Util.isNotNullOrEmpty(questionId)) {
                    errors.add(new Message(Message.Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_ANSWER_REQUIRED, Integer.toString(index)));
                }
            }
            else {
                //Only check for answer that not hashed
                if (!AuthenticationAnswerDTO.HASHED_ANSWER_MASKED.equals(answer)) {
                    // Each answer must be unique - users can just enter "foo" for everything.
                    if (selectedAnswers.contains(answer)) {
                        errors.add(new Message(Message.Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_DUPLICATE_ANSWER, Integer.toString(index)));
                    }
                    else {
                        //check against other hashed answers
                        for (String hashed : hashedAnswers) {
                            if (EncodingUtil.isMatch(answer, hashed, this.context)) {
                                errors.add(new Message(Message.Type.Error, MessageKeys.AUTH_ANSWERS_ERROR_DUPLICATE_ANSWER, Integer.toString(index)));
                                break;
                            }
                        }
                    }
                    selectedAnswers.add(answer);
                }
            }

        }

        return errors;
    }

    /**
     * Save the list of validated answers. Must validate before saving.
     * @param answers List<AuthenticationAnswerDTO> list of answers
     * @param identity identity that the answers are being saved for
     * @throws GeneralException
     */
    public void saveAnswers(List<AuthenticationAnswerDTO> answers, Identity identity) throws GeneralException {
        if (answers == null || answers.size() == 0 || identity == null) {
            return;
        }

        List<AuthenticationAnswer> authAnswers = new ArrayList<>();

        for (AuthenticationAnswerDTO answerDTO : answers) {
            String answerId = answerDTO.getId();
            String questionId = answerDTO.getQuestionId();

            AuthenticationAnswer authenticationAnswer;

            // if answerId exists then we are modifying an answer
            if (Util.isNotNullOrEmpty(answerId)) {
                authenticationAnswer = this.context.getObjectById(AuthenticationAnswer.class, answerId);
            }
            else {
                // new answer
                authenticationAnswer =  new AuthenticationAnswer();
            }

            // check if question exists
            if (Util.isNullOrEmpty(questionId)) {
                // if the answerId exists but the question id is null then the user cleared out the answer
                this.context.removeObject(authenticationAnswer);
                authenticationAnswer.setId(null);
                authenticationAnswer.setQuestion(null);
                authenticationAnswer.setAnswer(null);
            }
            else {
                answerDTO.populate(authenticationAnswer, this.context);
            }

            authAnswers.add(authenticationAnswer);
        }

        if (authAnswers.size() > 0) {
            identity.assignAuthenticationAnswers(authAnswers);
            this.context.saveObject(identity);
            this.context.commitTransaction();
        }
    }

    /**
     * Return the list of possible questions
     * @return List<AuthenticationQuestionDTO>
     */
    public List<AuthenticationQuestionDTO> getQuestions() throws GeneralException {

        List<AuthenticationQuestionDTO> questionDTOS = new ArrayList<>();

        List<AuthenticationQuestion> questions = this.userContext.getContext().getObjects(AuthenticationQuestion.class);

        for (AuthenticationQuestion question : questions) {
            questionDTOS.add(new AuthenticationQuestionDTO(this.userContext, question));
        }

        return questionDTOS;
    }
}
