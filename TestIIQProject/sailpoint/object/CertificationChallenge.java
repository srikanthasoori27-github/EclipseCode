/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A CertificationChallenge holds challenge information when a third-party
 * (currently, the person that a certification decision will affect) wishes to
 * refute a certification decision. In practice, this is currently being used
 * to let a user challenge a remediation decision in a certification so that
 * they can keep the entitlement being remediated. This can also be used to
 * challenge mitigations, but for now it is just remediations.
 * 
 * When challenged, the challenge work item is deleted and the fate of the
 * challenge lies in the hands of the certifier. The certifier can choose to
 * approve or reject a challenge request. Approving a challenge request
 * requires the certifier to choose another decision - the challenge will remain
 * with an Accepted Decision and optional comments. Rejecting marks the
 * challenge as rejected with optional comments.
 *
 * Challenges are created at the start of the "challenge period". The challenge
 * lifecycle is largely maintained by the ChallengePhaseHandler and the
 * Certificationer through WorkItem assimilation.
 * 
 * This extends WorkItemMonitor since it can trigger creation of a WorkItem and
 * can have the WorkItem changes assimilated back into it. For a challenge, the
 * WorkItemMonitor fields are used as follows:
 * 
 * <ul>
 *   <li>ownerName: Name of the challenger.</li>
 *   <li>completionState: Whether the challenge WorkItem was finished (either as
 *       challenged or accepted) or expired.</li>
 *   <li>completionComments: The comments from the challenger regarding the
 *       reason for a challenge.</li>
 * </ul>
 */
@XMLClass
public class CertificationChallenge extends WorkItemMonitor {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Decisions that a certifier can make on a challenged item.
     */
    @XMLClass(xmlname="ChallengeDecision")
    public static enum Decision {

        /**
         * The certifier accepts the challenge request, and will select a new
         * decision for the item.
         */
        Accepted("cert_item_challenge_decision_accepted"),
        
        /**
         * The certifier rejects the challenge request - the decision stays.
         */
        Rejected("cert_item_challenge_decision_rejected") ;

        private String messageKey;

        Decision(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * True if the associated decision is being challenged.
     */
    private boolean challenged;

    /**
     * The Decision that the challenger has made about this challenge request.
     * If null, no decision has been made yet.
     */
    private Decision decision;

    /**
     * Comments from the certifier about the challenged decision.
     */
    private String decisionComments;

    /**
     * Name of the Identity that decided on the challenge.
     */
    private String deciderName;

    /**
     * True if this was challenged, but the certifier did not make a decision on
     * the challenge request by the end of the challenge period.
     */
    private boolean challengeDecisionExpired;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public CertificationChallenge() {
        super();
    }

    /**
     * Constructor that takes a work item ID and owner.
     */
    public CertificationChallenge(String workItem, Identity owner) {
        super(workItem, owner);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * True if the associated decision is being challenged.
     */
    public boolean isChallenged() {
        return challenged;
    }

    /**
     * @exclude
     * @deprecated Use {@link #challenge(Identity, String, String)} instead. This is
     *             required by the persistence frameworks.
     */
    @Deprecated
    @XMLProperty
    public void setChallenged(boolean challenged) {
        this.challenged = challenged;
    }

    /**
     * The Decision that the challenger has made about this challenge request.
     * If null, no decision has been made yet.
     */
    public Decision getDecision() {
        return decision;
    }

    /**
     * @exclude
     * @deprecated Use {@link #rejectChallenge(Identity, String)} or
     *             {@link #acceptChallenge(Identity, String)} instead.  This is required by the
     *             persistence frameworks.
     */
    @Deprecated
    @XMLProperty
    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    /**
     * Comments from the certifier about the challenged decision.
     */
    public String getDecisionComments() {
        return decisionComments;
    }

    /**
     * @exclude
     * @deprecated Use {@link #rejectChallenge(Identity, String)} or instead. This is
     *             required by the persistence frameworks.
     */
    @Deprecated
    @XMLProperty
    public void setDecisionComments(String rejectionComments) {
        this.decisionComments = rejectionComments;
    }
    
    /**
     * Name of the Identity that decided on the challenge.
     */
    public String getDeciderName() {
        return deciderName;
    }

    /**
     * @exclude
     * @deprecated Use {@link #rejectChallenge(Identity, String)} instead. This is
     *             required by the persistence frameworks.
     */
    @Deprecated
    @XMLProperty
    public void setDeciderName(String name) {
        this.deciderName = name;
    }

    /**
     * True if this was challenged, but the certifier did not make a decision on
     * the challenge request by the end of the challenge period.
     */
    public boolean isChallengeDecisionExpired() {
        return challengeDecisionExpired;
    }

    /**
     * @exclude
     * @deprecated Use {@link #expireChallengeDecision()} instead. This is
     *             required by the persistence frameworks.
     */
    @Deprecated
    @XMLProperty
    public void setChallengeDecisionExpired(boolean expired) {
        this.challengeDecisionExpired = expired;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return whether a decision has been made by the certifier (accept or
     * reject) about the challenge request.
     * 
     * @return True if a decision has been made, false otherwise.
     */
    public boolean hasBeenDecidedUpon() {
        return (null != this.decision);
    }

    /**
     * Return whether this challenge request has been rejected by the certifier.
     * 
     * @return True if this challenge request has been rejected by the
     *         certifier, false otherwise.
     */
    public boolean isRejected() {
        return Decision.Rejected.equals(this.decision);
    }

    /**
     * Return whether this challenge is still active or not.  An active
     * challenge is a challenge that can either still be challenged by the
     * challenger, or has been challenged but no decision has been made (and it
     * has not expired).
     * 
     * @ignore
     * NOTE: If this logic is changed, you will also need to change the query
     * in {@link sailpoint.api.CertificationService#isReadyForSignOff(Certification)}.
     */
    public boolean isActive() {
        
        // Non-null work item means that the item can still be challenged.
        return (null != this.getWorkItem()) || requiresDecision();
    }

    /**
     * Return whether this challenge requires a decision from the certifier.
     */
    public boolean requiresDecision() {

        // Requires decision if challenged/not decided/not expired.
        return this.challenged && !this.hasBeenDecidedUpon() &&
               !this.challengeDecisionExpired;
    }

    /**
     * Mark this as challenged by the given challenger with the given comments.
     * This also nulls out the work item ID since the work item is deleted when
     * challenged.
     * 
     * @param  challenger  The person challenging the decision.
     * @param  workItem    The ID of the work item from which the challenge came.
     * @param  comments    Comments from the challenger about the challenge.
     */
    public void challenge(Identity challenger, String workItem, String comments) {
        this.challenged = true;

        // Save the context in which the challenge occurred.
        super.saveContext(challenger, workItem);

        // Save the challenge information.
        this.challenged = true;
        super.setCompletionComments(comments);
        if (null != challenger) {
            super.setCompletionUser(challenger.getName());
        }

        // Null out the work item because it gets deleted when the challenge
        // happens.
        super.setWorkItem(null);
    }

    /**
     * The challenger does not care to challenge the decision (hence,
     * acceptDecision), mark the challenge as such. This also nulls out the
     * work item ID since the work item is deleted when the decision is
     * accepted.
     * 
     * @param  challenger  The person accepting the decision.
     * @param  workItem    The ID of the work item from which the challenge was
     *                     accepted.
     */
    public void acceptDecision(Identity challenger, String workItem) {
        this.challenged = false;

        // Save the context in which the decision was accepted.
        super.saveContext(challenger, workItem);

        // Null out the work item because it gets deleted when the decision is
        // accepted.
        super.setWorkItem(null);
    }

    /**
     * Return whether the challenger accepted or challenged the decision.
     * 
     * @return True if the challenger accepted or challenged the decision.
     */
    public boolean didChallengerAct() {
        
        return (null != super.getCompletionState());
    }
    
    /**
     * The challenge period has expired. The work item is now gone and the
     * opportunity to act on challenges has passed.
     * 
     * @return True if the work item was still set before being expired, false
     *         otherwise.
     */
    boolean expireChallenge() {

        boolean workItemNulled = (null != super.getWorkItem());

        this.challenged = false;
        super.setWorkItem(null);

        return workItemNulled;
    }

    /**
     * This has been challenged but no decision (accept or reject) was made by
     * the end of the challenge period.  Expire the challenge request decision.
     */
    void expireChallengeDecision() {
        this.challengeDecisionExpired = true;
        super.setWorkItem(null);
    }

    /**
     * The certifier is accepting the challenge from the challenger, and will
     * choose a new decision.
     * 
     * @param  who       The person accepting the challenge.
     * @param  comments  Comments from the certifier about the decision.
     */
    void acceptChallenge(Identity who, String comments) {
        makeDecision(Decision.Accepted, who, comments);
    }
    
    /**
     * The certifier is rejecting the challenge from the challenger - in other
     * words ... NO DICE ... the decision the certifier made is staying!
     *  
     * @param  who       The person rejecting the challenge.
     * @param  comments  Comments from the certifier about the challenge
     *                   rejection.
     */
    void rejectChallenge(Identity who, String comments) {
        makeDecision(Decision.Rejected, who, comments);
    }

    /**
     * Set the given certification decision, decider, and comments.
     */
    private void makeDecision(Decision decision, Identity who, String comments) {

        this.decision = decision;
        this.decisionComments = comments;

        if (null != who) {
            this.deciderName = who.getDisplayableName();
        }
    }
}
