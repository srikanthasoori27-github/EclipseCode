/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.score;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.ApplicationEntitlementWeights;
import sailpoint.object.Attributes;
import sailpoint.object.EntitlementWeight;
import sailpoint.object.Right;
import sailpoint.object.RightConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * EntitlementScoreConfig allows easy access to pieces of the raw entitlement
 * identity score.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class EntitlementScoreConfig {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The default weight for rights in a Permission.
     */
    public static final String ARG_DEFAULT_RIGHT_WEIGHT = 
    "defaultRightScore";
    
    /**
     * The default weight for values of an attribute.
     */
    public static final String ARG_DEFAULT_ATTRIBUTE_WEIGHT = 
    "defaultAttributeScore";
    
    /**
     * Attribute holding a list of ApplicationEntitlementWeight objects
     * for more specific scoring.
     */
    public static final String ARG_APPLICATION_WEIGHTS = 
    "applicationEntitlementWeights";
    

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    int _defaultRightWeight;
    int _defaultAttributeWeight;
    
    /**
     * The RightConfig extracted from the ScoreConfig.
     */
    RightConfig _rightConfig;
    
    /**
     * The application specific weights extracted from the score config.
     */
    List<ApplicationEntitlementWeights> _applicationWeights;
    

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public EntitlementScoreConfig(SailPointContext context)
        throws GeneralException {
        this(context, null, null);
    }
    
    /**
     * Constructor.
     */
    @SuppressWarnings("unchecked")
    public EntitlementScoreConfig(SailPointContext context,
                                  ScoreConfig config,
                                  ScoreDefinition def)
        throws GeneralException {
        
        // Grab the score config if one wasn't supplied.
        if (null == config) {
            config = context.getObjectByName(ScoreConfig.class, ScoreConfig.OBJ_NAME);
        }
        
        // Grab the entitlement score definition if one wasn't supplied.
        if (null == def) {
            def = config.getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);
        }

        Attributes<String,Object> args = config.getEffectiveArguments(def);
        
        _defaultRightWeight = args.getInt(ARG_DEFAULT_RIGHT_WEIGHT, 0);
        _defaultAttributeWeight = args.getInt(ARG_DEFAULT_ATTRIBUTE_WEIGHT, 0);

        // The rights configuration is now global, but may be overridden
        // by the ScoreConfig.
        // There is a Map in here that we might want to cache
        // on the Scorekeeper for repeated use.
        _rightConfig = config.getRightConfig();
        if (_rightConfig == null)
            _rightConfig = context.getObjectByName(RightConfig.class, RightConfig.OBJ_NAME);

        // !! Need to be building lookup maps for this
        _applicationWeights =
            (List<ApplicationEntitlementWeights>) args.getList(ARG_APPLICATION_WEIGHTS);
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Weight lookups
    //
    //////////////////////////////////////////////////////////////////////
    
    public ApplicationEntitlementWeights getAppWeights(String appName) {
        
        ApplicationEntitlementWeights weights = null;
        
        if (appName != null && _applicationWeights != null) {

            // need a HashMap, but where?
            for (ApplicationEntitlementWeights aew : _applicationWeights) {
                // if the app was deleted there was a time where we could
                // have dangling references in the ScoreConfig, be sure
                // to check for null and ignore them
                Application scoreapp = aew.getApplication();
                if (scoreapp != null && appName.equals(scoreapp.getName())) {
                    
                    weights = aew;
                    break;
                }
            }
        }
        
        return weights;
    }
    
    public ApplicationEntitlementWeights getAppWeights(Application app) {
        return (null != app) ? getAppWeights(app.getName()) : null;
    }
    
    /**
     * Calculate the weight for one Permission target/right pair.
     * Unlike attribute weighting, we don't require a target, if the
     * target is null it becomes the default weight for this permission.
     * !! Need lookup maps
     */
    public int getRightWeight(String appName, String target, String right) {
        
        int weight = 0;
        
        if (appName != null && target != null && right != null) {
            
            // whatever happens, I'll always have you
            weight = _defaultRightWeight;
            
            EntitlementWeight eWeight = null;
            EntitlementWeight eWeightDefault = null;
            ApplicationEntitlementWeights appWeights = getAppWeights(appName);
            
            if (appWeights != null) {
                List<EntitlementWeight> eWeights = appWeights.getWeights();
                if (eWeights != null) {
                    for (EntitlementWeight ew : eWeights) {
                        if (ew.isPermission()) {
                            // todo: could have various forms of wildcarding
                            if (right.equals(ew.getRight())) {
                                if (ew.getTarget() == null) {
                                    eWeightDefault = ew;
                                }
                                else if (target.equals(ew.getTarget())) {
                                    eWeight = ew;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            
            // if we had a default weight (without a target) but no
            // specific weight, use it
            if (eWeight == null) eWeight = eWeightDefault;
            
            if (eWeight != null) {
                // !! why is this a String?
                weight = Util.atoi(eWeight.getWeight());
            }
            else if (_rightConfig != null) {
                // fall back to RightConfig if we can
                Right r = _rightConfig.getRight(right);
                if (r != null)
                    weight = r.getWeight();
            }
        }
        
        return weight;
    }
    
    public int getRightWeight(Application app, String target, String right) {
        return (null != app) ? getRightWeight(app.getName(), target, right) : null;
    }
    
    /**
     * Calculate the weight for one attribute/value pair.
     * !! Need a lookup map
     */
    public int getValueWeight(String appName, String name, String value) {
        
        int weight = 0;
        
        if (appName != null && name != null && value != null) {
            
            // whatever happens, I'll always have you
            weight = _defaultAttributeWeight;
            
            EntitlementWeight eWeight = null;
            ApplicationEntitlementWeights appWeights = getAppWeights(appName);
            
            if (appWeights != null) {
                List<EntitlementWeight> eWeights = appWeights.getWeights();
                if (eWeights != null) {
                    for (EntitlementWeight ew : eWeights) {
                        if (ew.isAttribute()) {
                            // todo: could have various forms of
                            // wildcarding
                            if (name.equals(ew.getAttribute()) &&
                                value.equals(ew.getValue())) {
                                eWeight = ew;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (eWeight != null) {
                // !! make this an int
                weight = Util.atoi(eWeight.getWeight());
            }
        }
        
        return weight;
    }

    public int getValueWeight(Application app, String name, String value) {
        return (null != app) ? getValueWeight(app.getName(), name, value) : null;
    }
}
