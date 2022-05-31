/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of a class that provides scoring services.
 *
 * Author: Jeff
 * 
 * We originally only support scoring with Identity and Scorecard
 * classes, but this was generalized in 2.5 to allow scoring
 * for Application and Link, and in theory other things.
 *
 * The implementation is therefore generic in using SailPointObject
 * and GenericIndex, though the implementations will immediately
 * downcast to the objects they expect.
 * 
 * I like this better than having a bunch of specific interfaces 
 * and making Scorekeeper have to think about which one to use.
 * 
 * There are two fundamental types of scorers: individual scorers
 * and aggregate scorers.  Individual scorers calcuate a score
 * by analyzing one object.  This type of scorer is used to 
 * calculate identity scores.
 *
 * Aggregate scorers calculate scores by analyzing many objects.
 * This type of scorer is used to calculate application scores
 * by examining all the Links to that application.
 *
 * Aggregate scorers generally want to maintain state in member
 * fields and update the scorecard only when the iteration is complete.
 * To enable this, the prepare() method is called before the scoring
 * iteration starts, and the finish() method is called when it ends.
 * In between prepare() and finish(), score() will be called any
 * number of times.
 *
 * Individual scorers normally update the index whenever the score() 
 * method is called.  The prepare() and finish() methods are not called.
 * 
 * A SailPonitContext is provided to the scoring methods in case the
 * scorer needs to look at other objects, such as searching a 
 * history list or the activity logs
 */

package sailpoint.object;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * The interface of a class that provides scoring services.
 */
public interface Scorer
{
    /**
     * Reset the internal state of aggregate scorers.
     */
    public void prepare(SailPointContext context, 
                        ScoreConfig config,
                        ScoreDefinition def,
                        GenericIndex index)
        throws GeneralException;

    /**
     * Update score state from one object.
     */
    public void score(SailPointContext context, 
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex card)   
        throws GeneralException;

    /**
     * Save the accumulated score state of aggregate scorers.
     */
    public void finish(SailPointContext context, 
                       ScoreConfig config,
                       ScoreDefinition def,
                       GenericIndex index)
        throws GeneralException;
    
    
    public ScoreItem isMatch(SailPointObject obj)
        throws GeneralException;

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard. This is used in the calculation of
     * composite scores from an partially refreshed scorecard.
     */
    public int getScore(ScoreDefinition def, GenericIndex card);

    /**
     * Update the ScoreConfig with any changes that might have been made in configuration 
     * files specific to this scorer
     * @param context Current SailPointContext  
     * @param scoreConfig ScoreConfig object that needs to be updated
     */
    public void update(SailPointContext context, ScoreConfig scoreConfig);
};
