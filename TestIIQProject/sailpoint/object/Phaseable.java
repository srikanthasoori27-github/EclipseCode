/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.object.Certification.Phase;

import java.util.Date;


/**
 * A Phaseable object can be moved through the various certification phases.
 */
public interface Phaseable {

    /**
     * Get the current phase.
     */
    public Phase getPhase();

    /**
     * Set the current phase.
     */
    public void setPhase(Phase phase);

    /**
     * Get the date at which the next phase transition should occur - can be
     * null.
     */
    public Date getNextPhaseTransition();

    /**
     * Set the date at which the next phase transition should occur.
     */
    public void setNextPhaseTransition(Date next);

    /**
     * Return the certification for this phaseable that is being phased.
     */
    public Certification getCertification();

    /**
     * Return the next enabled phase according to the current phase and the
     * phase configuration.
     */
    public Phase getNextPhase();

    /**
     * Return the previous enabled phase according to the current phase and the
     * phase configuration.
     */
    public Phase getPreviousPhase();
}
