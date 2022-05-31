/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of Scorer for testing.
 * This is just a testing hack so we don't have to worry
 * about this accurately tracking the evolution of future scores.
 * 
 * Author: Jeff
 */

package sailpoint.score;

import java.util.List;
import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorecard;
import sailpoint.object.Scorer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class TestScorer implements Scorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(TestScorer.class);

    /**
     * Random number generator.  Keep this around so we can generate
     * different numbers on each run while the JVM is alive.
     */
    Random _random;


    //////////////////////////////////////////////////////////////////////
    //
    // Scoring Methods
    //
    //////////////////////////////////////////////////////////////////////

    public TestScorer() {
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    public static void println(ScoreDefinition def, Object o) {
        System.out.println("Score " + def.getName() + ": " + o);
    }

    /**
     * Generate a random number between a low a high value inclusive.
     */
    public int rand(int low, int high) {

        if (_random == null)
            _random = new Random();

        int range = (high - low) + 1;
        int r = _random.nextInt(range);
        
        return r + low;
    }
    
    public void prepare(SailPointContext context, 
                        ScoreConfig config,
                        ScoreDefinition def,
                        GenericIndex index)
        throws GeneralException {
    }

    public void finish(SailPointContext context, 
                       ScoreConfig config,
                       ScoreDefinition def,
                       GenericIndex index)
        throws GeneralException {
    }

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.
     *
     * We used to have a type enumeration here but that wasn't
     * extensible.  Make assumptions about the class names instead.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {

        int score = 0;
        Scorecard card = (Scorecard)index;
        String scorer = def.getScorer();

        if (scorer.endsWith("BusinessRoleScorer")) {
            if (def.isCompensated())
                score = card.getBusinessRoleScore();
            else
                score = card.getRawBusinessRoleScore();
        }
        else if (scorer.endsWith("EntitlementScorer")) {
            if (def.isCompensated())
                score = card.getEntitlementScore();
            else
                score = card.getRawEntitlementScore();
        }
        else if (scorer.endsWith("PolicyScorer")) {
            if (def.isCompensated())
                score = card.getPolicyScore();
            else
                score = card.getRawPolicyScore();
        }
        else  if (scorer.endsWith("CertificationScorer")) {
            score = card.getCertificationScore();
        }

        return score;
    }

    /**
     * Calculate a score from the contents of an Identity cube.
     * A context is provided in case the scorer needs to look outside
     * the Identity, such as searching the IdentityHistory list or
     * the ActivityLogs.
     */
    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        // can only be used with these
        Identity id = (Identity)src;
        Scorecard card = (Scorecard)index;
        String scorer = def.getScorer();

        int score = 0;
        int range = config.getMaximumScore();

        // special flag to generate random values
        boolean random = false;
        Object arg = def.getArgument("random");
        if (arg != null)
            random = Util.atob(arg.toString());

        if (random) 
            score += rand(0, range);

        if (scorer.endsWith("BusinessRoleScorer")) {

            if (!random) {
                // 10 points per business role
                List<Bundle> bundles = id.getBundles();
                if (bundles != null) {
                    for (Bundle b : bundles)
                        score += 10;
                }
                // clamp this to range
                if (score > range) score = range;
            }

            if (def.isCompensated())
                card.setBusinessRoleScore(score);
            else
                card.setRawBusinessRoleScore(score);
        }
        else if (scorer.endsWith("EntitlementScorer")) {
            if (def.isCompensated())
                card.setEntitlementScore(score);
            else
                card.setRawEntitlementScore(score);
        }
        else if (scorer.endsWith("PolicyScorer")) {
            if (def.isCompensated())
                card.setPolicyScore(score);
            else
                card.setRawPolicyScore(score);
        }
        else if (scorer.endsWith("CertificationScorer")) {
            card.setCertificationScore(score);
        }
    }

    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }
    
    public void update(SailPointContext context, ScoreConfig config) {
        
    }
}
