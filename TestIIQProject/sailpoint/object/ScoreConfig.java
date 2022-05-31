/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding global configurations for scoring.
 * There is only one of these, referenced through a constant name.
 *
 * Author: Jeff
 * 
 * Currently this only contains identity scoring definitions, but try
 * to keep this general enough to hold other kinds of scorers if necessary.
 *
 * Currently the ScoreDefinitions are child components, they are not
 * top-level entities in he Hibernate database.  It seems unlikely that
 * we will want to search on these so its much simpler just to keep them
 * inline.  The disadvantage is that we can't have a library of 
 * ScoreDefinitions and select among them.  We may want to move in that 
 * direction if we find that there are several common algorithms that
 * customers may want.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class holding global configurations for scoring.
 * There is only one of these, referenced through a constant name.
 */
@XMLClass
public class ScoreConfig extends SailPointObject 
    implements Cloneable
{
    private static final long serialVersionUID = -1725275055018280056L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The well known name of the singleton object in the repository.
     */
    public static final String OBJ_NAME = "ScoreConfig";

    //
    // Standard score definition nanes
    // Unfortunately the UI still has hard wired knowledge of particular
    // definition names.
    // Sigh, "businessRole" is unfortunate but we'd have to upgrade
    // all the scorecards to fix the name.
    //

    public static final String SCORE_RAW_ROLE = "rawBusinessRoleScore";
    public static final String SCORE_RAW_ENTITLEMENT = "rawEntitlementScore";
    public static final String SCORE_RAW_POLICY = "rawPolicyScore";
    
    public static final String SCORE_ROLE = "businessRoleScore";
    public static final String SCORE_ENTITLEMENT = "entitlementScore";
    public static final String SCORE_POLICY = "policyScore";
    public static final String SCORE_CERT = "certificationScore";

    public static final String SCORE_COMPOSITE = "Composite";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * List of score definitions for identity cube scoring.
     */
    List<ScoreDefinition> _identityScores;
    
    /**
     * List of score definitions for application scoring.
     */
    List<ScoreDefinition> _applicationScores;

    /**
     * List of application-specific score configuration.
     */
    List<ApplicationConfig> _applicationConfigs;
    
    /**
     * Configuration of the bands for visualization.
     */
    List <ScoreBandConfig> _bands;
    int _maximumNumberOfBands;

    /**
     * The maximum score that each Scorer implementation should produce.
     */
    int _maximumScore;
    
    /**
     * Optional reference to a RightConfig object to be used by the
     * scorers within this score configuration. This is used only
     * by the unit tests, though it could also be useful for 
     * what-if analysis. If this is null, the scorers should default
     * to using the global RightConfig object.
     */
    RightConfig _rightConfig;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public ScoreConfig() {
        setName(OBJ_NAME);
    }

    /**
     * Fully load the object into the Hibernate cache.
     */
    public void load() {

        RightConfig rc = getRightConfig();
        if (rc != null)
            rc.getName();
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setIdentityScores(List<ScoreDefinition> scores) {
        _identityScores = scores;
    }
    
    /**
     * List of score definitions for identity cube scoring.
     */
    public List<ScoreDefinition> getIdentityScores() {
        return _identityScores;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public void setApplicationScores(List<ScoreDefinition> scores) {
        _applicationScores = scores;
    }
    
    /**
     * List of score definitions for application scoring.
     */
    public List<ScoreDefinition> getApplicationScores() {
        return _applicationScores;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setApplicationConfigs(List<ApplicationConfig> configs) {
        _applicationConfigs = configs;
    }

    /**
     * List of application-specific score configuration.
     */
    public List<ApplicationConfig> getApplicationConfigs() {
        return _applicationConfigs;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public void setBands(List<ScoreBandConfig> bands) {
        _bands = bands;
    }
    
    /**
     * Configuration of the bands for visualization.
     */
    public List<ScoreBandConfig> getBands() {
        return _bands;
    }
    
    public int getNumberOfBands() {
        return _bands.size();
    }
    
    /**
     * The maximum score that each Scorer implementation should produce.
     */
    public int getMaximumScore() {
        return _maximumScore;
    }
    
    @XMLProperty    
    public void setMaximumScore(int maximumScore) {
        _maximumScore = maximumScore;
    }

    public int getMaximumNumberOfBands() {
        return _maximumNumberOfBands;
    }

    @XMLProperty
    public void setMaximumNumberOfBands(int maximumNumberOfBands) {
        _maximumNumberOfBands = maximumNumberOfBands;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RightConfigRef")
    public void setRightConfig(RightConfig rc) {
        _rightConfig = rc;
    }

    /**
     * Optional reference to a RightConfig object to be used by the
     * scorers within this score configuration. This is used only
     * by the unit tests, though it could also be useful for 
     * what-if analysis. If this is null, the scorers should default
     * to using the global RightConfig object.
     */
    public RightConfig getRightConfig() {
        return _rightConfig;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public ScoreDefinition getIdentityScore(String name) {
        return ScoreDefinition.getScore(_identityScores, name);
    }
    
    public void removeIdentityScore(String name) {
        ScoreDefinition scoreToRemove = ScoreDefinition.getScore(_identityScores, name);
        if (scoreToRemove != null)
            _identityScores.remove(scoreToRemove);
    }
    
    public void addIdentityScore(ScoreDefinition d) {
        if (_identityScores == null)
            _identityScores = new ArrayList<ScoreDefinition>();
        _identityScores.add(d);
    }
    
    public ScoreDefinition getApplicationScore(String name) {
        return ScoreDefinition.getScore(_applicationScores, name);
    }

    public void removeApplicationScore(String name) {
        ScoreDefinition scoreToRemove = ScoreDefinition.getScore(_applicationScores, name);
        if (scoreToRemove != null)
            _applicationScores.remove(scoreToRemove);
    }

    public void addApplicationScore(ScoreDefinition d) {
        if (_applicationScores == null)
            _applicationScores = new ArrayList<ScoreDefinition>();
        _applicationScores.add(d);
    }

    /**
     * @param a application whose ApplicationConfig is being fetched
     * @return ApplicationConfig for the specified application
     */
    public ApplicationConfig getApplicationConfig(Application a, ScoreDefinition scoreDef) {
        ApplicationConfig retval = null;
        

        if (_applicationConfigs != null) {
            for (ApplicationConfig appConfig : _applicationConfigs) {
                // note that you cannot assume reference equality, the  
                // ScoreConfig and the Application may have come from different
                // Hibernate sessions
                Application capp = appConfig.getApplication();
                if (a.equals(capp)) {
                    retval = appConfig;
                }
            }
        }

        if (retval == null) {
            // If no granular factor is set on the application config, defer to the one set on the ScoreDefinition
            retval = new ApplicationConfig(a);
            String activityMonitoringFactor = Util.otoa(scoreDef.getArgument("activityMonitoringFactor"));
            retval.setActivityMonitoringFactor(activityMonitoringFactor);
        }
        
        return retval;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Definition inheritance
    //
    // There is a simple form of "inheritance" between ScoreDefinitions
    // if they reference each other by name through the "parent" property.
    // Since these aren't real SailPointObjects, we have to resolve
    // them at a level above the ScoreDefinition classes.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the effective argument map by merging this one with
     * that defined by the parent.
     */
    public Attributes<String,Object> getEffectiveArguments(ScoreDefinition def) {
        
        Attributes<String,Object> args = new Attributes<String,Object>();

        getEffectiveArguments(def, args);

        return args;
    }

    public void getEffectiveArguments(ScoreDefinition def, 
                                      Attributes<String,Object> effective) {

        // recurse on the parents first
        String parentName = def.getParent();
        if (parentName != null) {
            ScoreDefinition parent = this.getIdentityScore(parentName);
            if (parent != null)
                getEffectiveArguments(parent, effective);
            else {
                // !! log
            }
        }

        // then replace with ours
        Attributes<String,Object> args = def.getArguments();
        if (args != null)
            effective.putAll(args);
    }
}
