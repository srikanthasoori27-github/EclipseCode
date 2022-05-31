/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Base class for Scorer impelementations.
 *
 * You don't have to extend this, but it provides some common
 * utility methods.
 * 
 * Author: Jeff
 */

package sailpoint.score;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.ApplicationConfig;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.GenericIndex;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorer;
import sailpoint.tools.GeneralException;

abstract class AbstractScorer implements Scorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Names of ScoreConfig arguments that are used by several 
    // scorers.
    //

    public static final String ARG_FACTOR_UNCERTIFIED = "uncertifiedFactor";
    public static final String ARG_FACTOR_CERTIFIED = "certifiedFactor";
    public static final String ARG_FACTOR_MITIGATED = "mitigatedFactor";
    public static final String ARG_FACTOR_EXPIRED = "expiredFactor";
    public static final String ARG_FACTOR_REMEDIATED = "remediatedFactor";

    public static final float DEFAULT_FACTOR_UNCERTIFIED = 1.0f;
    public static final float DEFAULT_FACTOR_CERTIFIED = 0.0f;
    public static final float DEFAULT_FACTOR_MITIGATED = 0.5f;
    public static final float DEFAULT_FACTOR_EXPIRED = 1.5f;
    public static final float DEFAULT_FACTOR_REMEDIATED = 2.0f;

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Default implementation of the aggregate scorer initialization
     * method for individual scorers that don't need one.
     */
    public void prepare(SailPointContext context, 
                        ScoreConfig config,
                        ScoreDefinition def,
                        GenericIndex index)
        throws GeneralException {
    }

    /**
     * Default implementation of the aggregate scorer finilization
     * method for individual scorers that don't need one.
     */
    public void finish(SailPointContext context, 
                       ScoreConfig config,
                       ScoreDefinition def,
                       GenericIndex index)
        throws GeneralException {
    }

    /**
     * Dig an integer argument out of the argument map.
     */
    public int getInt(Attributes args, String name, int dflt) {
        int value = dflt;
        if (args != null && args.get(name) != null)
            value = args.getInt(name);
        return value;
    }

    public int getInt(Attributes args, String name) {

        return getInt(args, name, 0);
    }

    public float getFloat(Attributes args, String name, float dflt) {
        float value = dflt;
        if (args != null && args.get(name) != null)
            value = args.getFloat(name);
        return value;
    }

    public float getFloat(Attributes args, String name) {
        return getFloat(args, name, 0.0f);
    }

    public List getList(Attributes args, String name) {
        List value = null;
        if (args != null)
            value = args.getList(name);
        return value;
    }

    /**
     * Is this something we need to configure?
     */
    public int getMaximumScore(ScoreConfig config) {
        
        int max = config.getMaximumScore();
        if (max <= 0)
            max = ScoreDefinition.DEFAULT_RANGE;
        return max;
    }

    /**
     * Limit a calculated score to the configured maximum.
     * Not sure this really needs to be configurable, 
     * but may want to apply gradual compression rather than a hard limit.
     */
    public int constrainScore(ScoreConfig config, int score) {

        int max = getMaximumScore(config);

        if (score > max)
            score = max;

        else if (score < 0)
            score = 0;

        return score;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Activity Monitoring
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convenience method for scorers that need to get activity monitoring factors for business roles.
     * Factors will be fetched for all the applications associated with this business role
     * @param context SailPointContext
     * @param businessRole Bundle that represents the business role whose activity monitoring score we're fetching
     * @param appsAssociatedWithIdentity applications that are hanging off the identity... not sure if this is necessary yet
     * @param config ScoreConfig from which we're fetching the activity monitoring factors
     * @return the activity monitoring factor as determined by our algorithm.
     * @throws GeneralException
     */
    protected double getActivityMonitoringFactorForJF(SailPointContext context, Bundle businessRole, Set<Application> appsAssociatedWithIdentity, ScoreConfig config, ScoreDefinition def) throws GeneralException {
        // Start this off with a nonsense value so that we can detect chanes
        double retval = -1.0d;
        
        // Get the set of applications that are have activity monitoring enabled for the identity with this business role
        Set<Application> apps = new HashSet<Application>();
        apps.addAll(appsAssociatedWithIdentity);
        apps.addAll(businessRole.getMonitoredApplications());
        
        for (Application app : apps) {
            ApplicationConfig appConfig = config.getApplicationConfig(app, def);
            Double amFactor = Double.parseDouble(appConfig.getActivityMonitoringFactor());
            if (amFactor > retval) {
                retval = amFactor;
            }
        }
        
        // If no enabled monitoring factors could be found, then we shouldn't affect the risk score
        if (retval == -1.0d) {
            retval = 1.0d;
        }
        
        return retval;
    }
    
    //////////////////////////////////////////////////////////////////////
    // 
    // ScoreItems
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Utility to assign percentages to a set of score items.
     */
    public void setPercentages(List<ScoreItem> items, int score) {

        if (items != null) {
            for (ScoreItem item : items) {
                int itemScore = item.getScore();
                if (itemScore > score) {
                    // the overall score must have been constrained, 
                    // assume we contributed all of it
                    // ?? actually this could cause other displayed percentages
                    // to look funny.  Either need to calculate this before
                    // the contraint, or remember the percentage of constraint and
                    // apply that to this calculation!
                    item.setScorePercentage(100);
                }
                else if (itemScore > 0) {
                    float ratio = (float)itemScore / (float)score;
                    int percent = (int)(ratio * 100.0f);
                    item.setScorePercentage(percent);
                }
            }
        }
    }

    public void update(SailPointContext context, ScoreConfig scoreConfig) {
        // Don't do anything unless this is overridden
    }
}
