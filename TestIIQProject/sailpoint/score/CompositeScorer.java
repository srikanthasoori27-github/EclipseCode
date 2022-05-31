/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Calculate a composite score from the other scores.
 * 
 * Author: Jeff
 */

package sailpoint.score;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.GenericIndex;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorer;
import sailpoint.tools.GeneralException;

public class CompositeScorer extends AbstractScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(CompositeScorer.class);

    public static final String ARG_TYPE = "type";
    public static final String TYPE_APPLICATION = "application";

    /**
     * The size of the array used to keep track of component scores.
     * This needs to be as large as the maximum number of component
     * scores, currently this is 5 for identity scores and 6 for application 
     * scores.  If we ever think this limit will be reached, should
     * dynamically grow the array but this should be fine for a long time.
     */
    public static final int MAX_COMPONENTS = 20;

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public CompositeScorer() {
    }

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {

        return index.getCompositeScore();
    }

    /**
     * Calculate a composite score from other scores.
     */
    public void score(SailPointContext context, 
                      ScoreConfig config,
                      ScoreDefinition def, 
                      SailPointObject srcUnused,
                      GenericIndex index)
        throws GeneralException {

        final String METER_NAME = "Score - composite";
        Meter.enterByName(METER_NAME);

        int composite = 0;
        int outerRange = getMaximumScore(config);

        List<ScoreDefinition> scores = null;
        if (TYPE_APPLICATION.equals(def.getString(ARG_TYPE)))
            scores = config.getApplicationScores();
        else
            scores = config.getIdentityScores();

        if (scores != null) {

            // In order to calculate the percentage contributions to the 
            // final score we have to remember the scaled contributions
            // of each component.  Would be nice to avoid the silly
            // array garbage but it is unclear whether we need to make
            // ScoreDefinition and Scorer instances reentrant.  I guess
            // we could use a thread local...
            int[] contributions = new int[MAX_COMPONENTS];
            int contributionIndex = 0;

            for (ScoreDefinition scoredef : scores) {

                int percentage = scoredef.getWeight();

                // Only pay attention to component scores.
                // With the introduction of "baseline" vs "compensated"
                // we should only be looking at the compenstaed scores.
                // We now have an isComponent property to test, but 
                // originally we accomplished the same thing by ignoring
                // scores whose weight was zero.  For backward compatibility
                // use the non-zero percentage method though for the UI
                // the flag must now be set.

                if (!scoredef.isComposite() && percentage > 0) {

                    // load the implementation 
                    Scorer scorer = scoredef.getScorerInstance();
                    if (scorer != null) {

                        // look kids, it's "math" (patent pending)

                        // I thought we might allow these to have differnt
                        // ranges, but in practice we're allways using the
                        // same maximum so the scaledValue should be the same
                        int innerRange = scoredef.getRange();
                        if (innerRange <= 0)
                            innerRange = outerRange;

                        int value = scorer.getScore(scoredef, index);

                        if (value > innerRange) value = innerRange;
                        float ratio = (float)value / (float)innerRange;
                    
                        // weight is the percentage of the outer range
                        float weight = (float)percentage / 100.0f;
                        float scaledRange = (float)outerRange * weight;
                        int scaledValue = (int)(scaledRange * ratio);
                        
                        // remember this for later
                        if (contributionIndex < MAX_COMPONENTS)
                            contributions[contributionIndex++] = scaledValue;

                        composite += scaledValue;
                    }
                }
            }

            // Now that we have a composite score, calculate the contributions
            // of each ScoreItem to that score.  
            // !! Should we do this before or after constraining to the
            // outerRange?
            if (composite > 0) {
                int maxScores = contributionIndex;
                contributionIndex = 0;
                for (ScoreDefinition scoredef : scores) {
                    int percentage = scoredef.getWeight();
                    if (!scoredef.isComposite() && percentage > 0) {
                        if (contributionIndex < maxScores) {

                            int contribution = contributions[contributionIndex];
                            float ratio = (float)contribution / (float)composite;
                            calcCompositeContributions(index, scoredef, ratio);
                        }
                        contributionIndex++;
                    }
                }
            }

        }

        // limit this in case the weights are off
        if (composite > outerRange) composite = outerRange;

        index.setCompositeScore(composite);

        pruneScoreItems(index);

        Meter.exitByName(METER_NAME);
    }

    /**
     * When used with an aggregate scoring algorithm like 
     * application scoring, ScoreKeeper will call finish()
     * at the end rather than score().  
     */
    public void finish(SailPointContext context, 
                       ScoreConfig config,
                       ScoreDefinition def,
                       GenericIndex index)
        throws GeneralException {

        // can just call score() with a null src
        score(context, config, def, null, index);
    }

    /** 
     * Called for each component of the composite score.
     * The scorers should have left one or more ScoreItems behind
     * representing the things that went into the the calculation of
     * that score.  The percentage that each of these contributed to
     * the final composite score is now calculated.
     *
     * Adjustment has the ratio of the scaled component score and
     * the final composite score.  This is multipled with the
     * item's component score percentage to get the percentage
     * contribution to the composite score.
     *
     * !! Astute readers will note that we're not iterating 
     * very efficiently.  ScoreItems are all on one flat list
     * and we'll traverse the entire list for each ScoreDefinition.  
     * It would be better if the list were segmented, but since we're
     * dealing with short lists and a background task it shouldn't
     * make much difference.
     */
    private void calcCompositeContributions(GenericIndex index,
                                            ScoreDefinition def,
                                            float adjustment) {
        
        List<ScoreItem> items = index.getScoreItems();
        if (items != null) {
            for (ScoreItem item : items) {
                // since these are simple XML objects, we don't store
                // references, but the names shouldn't have changed
                if (item.isRelated(def)) {

                    // percentage this item contributed to the individual score
                    int percentage = item.getScorePercentage();
                    
                    // factor in the adjustment to get the percentage
                    // contribution to the composite score, round up

                    int compPercentage = Math.round((float)percentage * adjustment);

                    item.setCompositePercentage(compPercentage);
                }
            }
        }
    }
                                              
    /**
     * After scoring is complete whip through the score items
     * removing those that don't contribute much to the compsite score.
     * What is left behind are the recommended "action items".
     */
    private void pruneScoreItems(GenericIndex index) {

        List<ScoreItem> items = index.getScoreItems();
        if (items != null) {

            // hmm, let's leave all the non-zero percentage items in 
            // here and see how busy it gets.
            // Should we order them here or let the UI do that?

            List<ScoreItem> filtered = new ArrayList<ScoreItem>();
            for (ScoreItem item : items) {
                if (item.getCompositePercentage() > 0)
                    filtered.add(item);
            }

            Collections.sort(filtered, new ScoreItemComparator());
            index.setScoreItems(filtered);
        }

    }

    public static class ScoreItemComparator implements Comparator<ScoreItem> {

        public boolean equals(Object o) {
            return (o == this);
        }

        public int compare(ScoreItem s1, ScoreItem s2) {

            int value = 0;
            int v1 = s1.getCompositePercentage();
            int v2 = s2.getCompositePercentage();

            // note that we want the list in descending order so the
            // usual return value is reversed
            
            if (v1 < v2) 
                value = 1;
            else if (v1 > v2)
                value = -1;

            return value;
        }
    }
    
    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }

}
