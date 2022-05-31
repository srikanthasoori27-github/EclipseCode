/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.List;

import sailpoint.object.AuthenticationAnswer;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IdentityLockedException;

/**
 * The challenger is used to allow identities to authenticate using
 * authentication (aka - challenge) questions.  This also assists in other
 * authentication question/answer related activities - saving answers,
 * checking whether the user needs to provide more answers, etc...
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Challenger {

    

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    
    private Lockinator padLock;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public Challenger(SailPointContext ctx) {
        this.context = ctx;
        this.padLock = new Lockinator(ctx);
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Attempt to authenticate the given identity using the provided answers,
     * return true if authentication is successful or false otherwise.
     * 
     * @param  identity  The Identity that is authenticating.
     * @param  answers   A List of answers to authentication questions.  This is
     *                   not the persistent list of answers on the Identity
     *                   object, but rather the answers provided for the auth
     *                   attempt.
     * @throws GeneralException 
     *
     * @exclude
     * We could have used a map or lighter structure instead of a list of
     * answers, but this gives us a bit more flexibility in the future if we
     * allow identities to define their own questions.
     */
    public boolean authenticate(Identity identity, List<AuthenticationAnswer> usersAnswers)
        throws GeneralException, IdentityLockedException {

        if (identity == null || usersAnswers == null) {
            return false;
        }
        
        boolean success = false;
        
        List<AuthenticationAnswer> correctAnswers = identity.getAuthenticationAnswers();
        
        int numCorrectRequired = getNumCorrectAnswersRequired();
        int numCorrect = 0;
        
        String providedAnswer;
        String correctAnswer;
        String questionId;
        for (AuthenticationAnswer userAnswer : usersAnswers) {
            // String compare answers, case insensitive 
            // Remove spaces and punctuation
            questionId = userAnswer.getQuestion().getId();
            providedAnswer = userAnswer.getAnswer().trim().toLowerCase();
            correctAnswer= getCorrectAnswer(correctAnswers, questionId);
            if (correctAnswer == null) {
                // the answer was not set for that question ???
                // this shouldn't happen
                success = false;
                break;
            }
            boolean isMatch = EncodingUtil.isMatch(providedAnswer, correctAnswer, context);
            if (isMatch) {
                if (++numCorrect >= numCorrectRequired) {
                    // answered enough correctly
                    success =  true;
                    break;
                }
            }
            else if (EncodingUtil.isEncrypted(correctAnswer)) { 
                // if its not an exact match try to match against a more lenient string
                // correctAnswer should get the same treatment the providedAnswer does
                // also a null check, because nulls?
                correctAnswer = context.decrypt(correctAnswer);
                correctAnswer = correctAnswer != null ? correctAnswer.replaceAll("\\s+", "") : correctAnswer;
                providedAnswer = providedAnswer.replaceAll("\\s+", "");
                if (providedAnswer.compareToIgnoreCase(correctAnswer) == 0) {
                    if (++numCorrect >= numCorrectRequired) {
                        // answered enough correctly
                        success =  true;
                        break;
                    }
                }
            }
        }
        
        if (!success) {
            int attempts = identity.getFailedAuthQuestionAttempts();
            int maxFailures = getMaxFailedAttempts();
            boolean lockedOut = false;
            identity.setFailedAuthQuestionAttempts(++attempts);
            if (attempts >= maxFailures) {
                // lockout identity
                padLock.lockUser(identity, "", true);
                //identity.setAuthLockStart(new Date());
                lockedOut = true;
            } else{
            // save identity
            context.saveObject(identity);
            context.commitTransaction();
            }
            if (lockedOut) {
                throw new IdentityLockedException();
            }
        }
        
        return success;
    }

    private String getCorrectAnswer(List<AuthenticationAnswer> correctAnswers, String questionId) throws GeneralException {
        for (AuthenticationAnswer answer : correctAnswers) {
            if (answer.getQuestion() != null && questionId != null &&  questionId.equals(answer.getQuestion().getId())) {
                return answer.getAnswer();
            }
        }
        return null;
    }


    /**
     * Return whether the given identity needs to provide more answers for
     * authentication questions.
     */
    public boolean hasUnansweredQuestions(Identity identity)
        throws GeneralException {

        int numRequired = getNumAnswersRequired();
        int numAnswered = 0;
        List<AuthenticationAnswer> answers = identity.getAuthenticationAnswers();
        if (null != answers) {
            for (AuthenticationAnswer answer : answers) {
                if (null != answer.getAnswer()) {
                    numAnswered++;
                }
            }
        }

        return (numAnswered < numRequired);
    }

    /**
     * Check if the user is locked out or not
     * 
     * @param user
     * @return
     * @throws GeneralException
     */
    public boolean isLockedOut(Identity user) throws GeneralException {
        // check if locked out

        //We need to check number of Failed Auth Question Attempts now because a user can also be locked out due to
        //failed logins
        if (padLock.isUserLocked(user, Lockinator.LockType.AuthQuestion)) {
            return true;
        }
        return false;
    }
    
    /**
     * Return the number of answers that a user must provide when setting up
     * authentication answers.  Note: this may be different than the number of
     * answers required for actual authentication.
     */
    public int getNumAnswersRequired() throws GeneralException {
        return this.context.getConfiguration().getInt(Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED);
    }
    
    /**
     * max number of times user can try to authenticate using auth questions
     * @return
     * @throws GeneralException
     */
    public int getMaxFailedAttempts() throws GeneralException {
        return this.context.getConfiguration().getInt(Configuration.MAX_AUTH_QUESTION_FAILURES);
    }

    /**
     * lock period in millis
     * @return
     * @throws GeneralException
     */
    public long getLockoutPeriod() throws GeneralException {
        return this.context.getConfiguration().getLong(Configuration.AUTH_QUESTION_LOCKOUT_DURATION_MILLIS);
    }

    
    /**
     * the number of answers required for actual authentication.
     */
    public int getNumCorrectAnswersRequired() throws GeneralException {
        return this.context.getConfiguration().getInt(Configuration.NUM_AUTH_QUESTIONS_FOR_AUTHN);
    }
}
