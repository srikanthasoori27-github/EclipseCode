/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Scorer calculator for extra entitlements.
 * 
 * Author: Jeff
 * 
 * ACCOUNT DEFAULT WEIGHT
 *
 * Each application may have a default account weight that
 * is factored into the score simply by having an account.
 * This can be used in cases where you do not want to bother
 * with fine grained entitlement weights.  The account weight
 * is only factored in if there are extra entitlements on an 
 * account which may or may not be weighted.  The notion here
 * is that if you have an account but all the entitlements
 * are covered by roles then the account weight shouldn't apply.
 * Otherwise you've got this score component that never goes away.
 *
 * If you do have extra entitlements then the account weight
 * is added to the entitlement weight.  We can consider
 * adjusting the account weight based on entitlement weights.
 * For example, if all the extra entitlements are covered by
 * fine grained weights, then there is no need to apply the
 * account weight.
 *
 * How we compensate the account weight is unclear since there
 * is no certification of the account itself, only the entitlements.
 * If the compensation for all the entitlements takes the score
 * to zero then it makes sense to also remove the account weight.
 * If the compensated score is non-zero however it isn't obvious
 * how the account weight should be reduced since there may have
 * been many different compensation levels applied to each
 * entitlement weight.  
 *
 * We're going to punt for now and assume that the account weight
 * is uncompensated if there is a non-zero score.
 *
 * PERMISSION AND ATTRIBUTE WEIGHTING
 *
 * There are several levels of weighting that may be used:
 * 
 * Global Default Right
 *
 * The ScoreConfig contains a single default weight Permission 
 * rights (defaultRightScore) and attribute values 
 * (defaultAttributeScore).  These weights are used if we
 * cannot find a more specific weight.
 *
 * Global Specific Right
 *
 * The ScoreConfig has a reference to a RightConfig object
 * which contains the definitions of the Permission rights
 * we recognize.  The RightConfig has a list of Right
 * objects containing the default weight for any occurrence
 * of that right in any application.  
 *
 * Application Specific Right
 *
 * The ScoreConfig contains an attribute "applicationEntitlementWeights"
 * that has a list of ApplicationEntitlementWeight objects.  These in turn
 * contain a list of EntitlementWeight objects scoped for a particular
 * Application. 
 *
 * EntitlementWeight has has a type, target, value, and weight.
 * For type=permission the target is an arbitrary string naming
 * an applciation-specific permission target, "value" is the name
 * of one of our rights configured in the RightConfig, and weight
 * is the weight to assigned to that right.
 *
 * For type=attribute, target is the name of an attribute defined
 * in the user schema for the application, value is one possible
 * value for that attribute, and weight is the weight to assign
 * to that value.
 * 
 * Wildcards
 * 
 * In theory we could support wildcards or regular expressions
 * in the ApplicationEntitlementWeight and EntitlementWeight
 * objects, but that is not currently supported.
 * 
 * COMPENSATION
 *
 * Compensation for extra entitlements works the same way
 * as compensation for business roles.  Once the base weight
 * for an entitlement is determined it is adjusted by one
 * of the compensation factors for certified, mitigated, 
 * remediated, etc.
 * 
 */

package sailpoint.score;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityHistoryService;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.ActivityConfig;
import sailpoint.object.Application;
import sailpoint.object.ApplicationConfig;
import sailpoint.object.ApplicationEntitlementWeights;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.EntitlementWeight;
import sailpoint.object.EntitlementWeight.EntitlementType;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.Permission;
import sailpoint.object.Right;
import sailpoint.object.RightConfig;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorecard;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class EntitlementScorer extends AbstractScorer {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
	private static Log log = LogFactory.getLog(EntitlementScorer.class);
    
    SailPointContext _context;
    Date _now;
    
    EntitlementScoreConfig _entitlementScoreConfig;
    
    //
    // Compensation factors
    //
    
    boolean _compensated;
    float _uncertifiedFactor;
    float _certifiedFactor;
    float _mitigatedFactor;
    float _expiredFactor;
    float _remediatedFactor;
    
    /**
     * The current identity that is being processed.
     */
    Identity _identity;

    /**
     * The following two are used to make sure that we don't
     * load identity history items more than we need to
     * For details see {@link HistoryItemsFetchInfo}
     */
    HistoryItemsFetchInfo _historyItemsFetchInfo;
    HistoryItemsFetchInfo _lastHistorysItemFetchInfo;
    /**
     * The following holds the certificationAction taken for an attribute ("group", "managers")
     * or permission ("read") for each {@link HistoryItemsFetchInfo}
     */
    Map<Pair<String, String>, CertificationAction> _historyAttributeActionCache;
    Map<Permission, CertificationAction> _historyPermissionActionCache;
    
    
    /**
     * A flag that says whether to check certification history for
     * compensated scoring.  This is an optimization to prevent many
     * expensive lookups for CertificationHistoryItems if an identity
     * has no history.
     */
    Boolean _checkCertHistory;
    
    /**
     * Kludge to keep a count of the number of permissions or
     * attributes we encounter that had no default weighting and
     * no compensation.  These need to trigger the addition of the
     * default account weight.  
     *
     * We can't simply check to see if the final score was zero, 
     * because this coudl have been the result of applying compensation
     * to a weighted permission, which should not trigger the 
     * default account weight.  
     *
     * It's a kludge because it was added to avoid complicating
     * the control flow from score() down to scoreOneValue() and 
     * scorePermission() which are already returning a value.
     */
    int _unweightedEntitlements;
    

    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public EntitlementScorer() {
    }

    /**
     * Have to implement this but it won't be used.
     */
    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {

        int score = 0;
        Scorecard card = (Scorecard)index;

        if (def.isCompensated())
            score = card.getEntitlementScore();
        else
            score = card.getRawEntitlementScore();

        return score;
    }

    /**
     * Calculate a score from the contents of an Identity cube.
     */
    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        final String METER_NAME =
            "Score - entitlement " + (def.isCompensated() ? "compensated" : "raw");
        Meter.enterByName(METER_NAME);

        // can only be used with these
        _identity = (Identity) src;
        _checkCertHistory = null;
        Scorecard card = (Scorecard)index;

        List<ScoreItem> items = new ArrayList<ScoreItem>();
        float score = 0;

        _context = context;
        _now = new Date();

        _entitlementScoreConfig =
            new EntitlementScoreConfig(context, config, def);
        
        Attributes<String,Object> args = config.getEffectiveArguments(def);

        _compensated = def.isCompensated();

        _uncertifiedFactor = 
            getFloat(args, ARG_FACTOR_UNCERTIFIED, DEFAULT_FACTOR_UNCERTIFIED);
        _certifiedFactor = 
            getFloat(args, ARG_FACTOR_CERTIFIED, DEFAULT_FACTOR_CERTIFIED);
        _mitigatedFactor = 
            getFloat(args, ARG_FACTOR_MITIGATED, DEFAULT_FACTOR_MITIGATED);
        _expiredFactor = 
            getFloat(args, ARG_FACTOR_EXPIRED, DEFAULT_FACTOR_EXPIRED);
        _remediatedFactor = 
            getFloat(args, ARG_FACTOR_REMEDIATED, DEFAULT_FACTOR_REMEDIATED);

        // then add the exceptional entitlement weights
        List<EntitlementGroup> exceptions = _identity.getExceptions();
        if (exceptions != null) {

            for (EntitlementGroup eg : exceptions) {
                Application app = eg.getApplication();
                float appScore = 0.0f;
                int permissionCount = 0;
                int attributeCount = 0;

                // control flow kludge
                _unweightedEntitlements = 0;

                // NOTE: If we could assume that all permissions and
                // attributes for a given Application were certified 
                // in one item (which they are now) we could optimize
                // the search a bit.  Bit even with that, we still can't
                // assume that a given right or value was included in the
                // last item so we still have to look inside.

                List<Permission> perms = eg.getPermissions();
                if (perms != null) {
                    for (Permission p : perms) {
                        permissionCount++;
                        float permissionScore = scorePermission(app, eg.getInstance(), eg.getNativeIdentity(),
                                p, config, def);
                        appScore += permissionScore;
                        score += permissionScore;

                        // We'll add one item for each Permission, 
                        // in theory we could go finer grained and have an 
                        // item for each right/target pair, but when 
                        // displaying "advice" its better to have less clutter.

                        if (_compensated) {
                            ScoreItem item = new ScoreItem(def);

                            Message targetMsg = new Message(MessageKeys.ENTITLEMENT_SCORER_TARGET_DESC,
                                    app.getName(), p.getMessage());

                            item.setTargetMessage(targetMsg);
                            // note that we'll truncate the float here
                            // which may affect the percentage calculations later,
                            // should be close enough though...
                            item.setScore((int)permissionScore);
                            items.add(item);
                        }
                    }
                }

                // We're assumging that the only thing in here
                // are "compliance relevant" attributes.  Entitlement
                // correlator must have already done the filtering.

                Attributes<String,Object> atts = eg.getAttributes();
                if (atts != null) {
                    for (Map.Entry<String,Object> e : atts.entrySet()) {
                        attributeCount++;
                        String name = e.getKey();
                        Object value = e.getValue();
                        float attributeScore = scoreValue(app, eg.getInstance(), eg.getNativeIdentity(),
                                name, value, config, def);
                        appScore += attributeScore;
                        score += attributeScore;
                        
                        if (_compensated) {
                            ScoreItem item = new ScoreItem(def);
                            // Just the attribute name might be enough, but
                            // the values are also useful. Unfortunately the
                            // value list is potentially unconstrained!!
                            Message targetMsg =
                                    new Message(MessageKeys.ENTITLEMENT_SCORER_ENTITLEMENT_TARGET_DESC,
                                            app.getName(), name, value);
                            item.setTargetMessage(targetMsg);
                            item.setScore((int)attributeScore);
                            items.add(item);
                        }
                    }
                }

                // Now factor in the default account weight for this app.
                // We're assuming that there will be only one EntitlementGroup
                // entry for a given Application on the list.  If appScore
                // is positive it means we had some extra entitlements not
                // covered by roles.  If unweightedEntitlements is positive, 
                // it means we had extra entitlements but they were not
                // given weights.  We have to check both because the appScore
                // may be zero if we found all unweighted entitlemnts.

                if (appScore > 0.0 || _unweightedEntitlements > 0) {
                    ApplicationEntitlementWeights appWeights =
                        _entitlementScoreConfig.getAppWeights(app);
                    // there is no compensation on this at the moment
                    if (appWeights != null) {
                        float weight = (float)appWeights.getAccountWeight();
                        if (weight > 0.0f) {

                            score += weight;
                            ScoreItem item = new ScoreItem(def);
                            Message targetMsg =
                                    new Message(MessageKeys.ENTITLEMENT_SCORER_TARGET_DESC, app.getName(),
                                            MessageKeys.ENTITLEMENT_SCORER_DEF_ACCOUNT_SCORE);                            
                            item.setTargetMessage(targetMsg);
                            item.setScore((int)weight);
                            items.add(item);
                        }
                    }
                }
            }
        }

        // round up and constrain
        int intScore = Math.round(score);
        int constrainedScore = constrainScore(config, intScore);
        
        if (_compensated)
            card.setEntitlementScore(constrainedScore);
        else
            card.setRawEntitlementScore(constrainedScore);

        // Note that percentages are being calculated from the original 
        // score not the constrained score.  This will make the percentages
        // accurage but this may look funy after constrainting.
        // Example item A and B both contributes 1000, so their percentage
        // is 50%.  But the score of 2000 is rolled down to 1000 so really
        // A and B could be considered in the 100% range.  But this
        // would mean that the total of the item percentages could be
        // greater than 100% which might look funny.  Think on these things...

        if (_compensated) {
            setPercentages(items, intScore);
            card.addItems(items);
        }

        Meter.exitByName(METER_NAME);
    }

    /**
     * Calculate a score for all the rights in an exceptional Permission.
     */
    private float scorePermission(Application app, String instance, String nativeIdentity,
                                  Permission p, ScoreConfig scoreConfig, ScoreDefinition def) throws GeneralException{

        float score = 0;

        if (app != null && p != null) {

            String csv = p.getRights();
            List<String> rights = null;
            if (csv != null) 
                rights = Util.csvToList(csv, true);

            if (rights == null || rights.size() == 0) {
                // shouldn't happen, ingnore or give it
                // a tick just for showing up?
            }
            else {
                String target = p.getTarget();
                for (String right : rights) {
                    float weight = _entitlementScoreConfig.getRightWeight(app, target, right);
                    if (weight == 0.0f) {
                        // this needs to trigger the default account score
                        _unweightedEntitlements++;
                    }
                    else {
                        weight = compensate(weight, app, instance, nativeIdentity,
                                target, right, true, scoreConfig, def);
                        score += weight;
                    }
                }
            }
        }

        return score;
    }

    /**
     * Calculate a score for value(s) of an exceptional attribute.
     */
    private float scoreValue(Application app, String instance, String nativeIdentity,
                             String name, Object value, ScoreConfig scoreConfig, ScoreDefinition def)
            throws GeneralException{

        float score = 0f;
        
        // assume that null values don't count
        if (app != null && name != null && value != null) {
            if (value instanceof Collection) {
                @SuppressWarnings("rawtypes")
                Iterator it = ((Collection)value).iterator();
                while (it.hasNext()) 
                    score += scoreOneValue(app, instance, nativeIdentity,  name, it.next(), scoreConfig, def);
            }
            else {
                score += scoreOneValue(app, instance, nativeIdentity, name, value, scoreConfig, def);
            }
        }

        return score;
    }

    /**
     * Calculate the score for a single value of an exceptional attribute.
     */
    private float scoreOneValue(Application app, String instance, String nativeIdentity,
                                String name, Object value, ScoreConfig scoreConfig, ScoreDefinition def)
            throws GeneralException{
        float score = 0f;

        if (value != null) {
            String str = value.toString();

            int weight = _entitlementScoreConfig.getValueWeight(app, name, str);
            if (weight == 0)
                _unweightedEntitlements++;
            else
                score = compensate(weight, app, instance, nativeIdentity, name, str, false, scoreConfig, def);
        }
        
        return score;
    }

    /**
     * Return whether or not certification history should be checked when
     * compensating.  This returns false if the user has no history for this
     * item type.
     */
    private boolean isCheckCertHistory(Identity identity) throws GeneralException {
        if (null == _checkCertHistory) {
            IdentityHistoryService svc = new IdentityHistoryService(_context);
            int numDecisions = svc.countEntitlementDecisions(identity.getId());
            _checkCertHistory = (numDecisions > 0);
        }
        return _checkCertHistory;
    }
    
    /**
     * Compensate a single permission or attribute score.
     *
     * NOTE: PolicyScorer is looking on the Identity to get
     * MitigationExpirations.  Is that more reliable than looking at
     * the last certification?  Ugh, yet another complicated model to
     * sift through.
     */
    private float compensate(float weight, 
                             Application app,
                             String instance,
                             String nativeIdenity,
                             String name, 
                             String value,
                             boolean permission,
                             ScoreConfig config,
                             ScoreDefinition def) throws GeneralException{
        
        float score = weight;
        
        if (_compensated) {
            float factor = _uncertifiedFactor;

            boolean hasWeightedFactors =
                (1.0f != _uncertifiedFactor) ||
                (1.0f != _certifiedFactor) ||
                (1.0f != _mitigatedFactor) ||
                (1.0f != _expiredFactor) ||
                (1.0f != _remediatedFactor);
            
            // Only look at history if there is some.  Also only do this if
            // there are non-1 factors, otherwise the history doesn't matter.
            if (hasWeightedFactors && isCheckCertHistory(_identity)) {
            
                factor = compensateForCertHistory(app, instance, nativeIdenity, name, value, permission, factor);
            }
            
            // Apply the appropriate activity monitoring factor to the compensation
            float activityMonitoringFactor = 1.0f;
            ActivityConfig activityConfig = _identity.getActivityConfig();
            
            if (activityConfig != null && activityConfig.enabled(app)) {
                ApplicationConfig appConfig = config.getApplicationConfig(app, def);
                activityMonitoringFactor = Float.parseFloat(appConfig.getActivityMonitoringFactor());
            }
            
            // If no monitoring factor was found on the application itself, 
            // it might be on one or more of the business roles that contain this application instead
            if (activityMonitoringFactor == 1.0f) {
                List<Bundle> businessRolesWithThisApp = _identity.getBundles(app);
                for (Bundle businessRole : businessRolesWithThisApp) {
                    ActivityConfig jfConfig = businessRole.getActivityConfig();
                    if (jfConfig != null && jfConfig.enabled(app)) {
                        ApplicationConfig appConfig = config.getApplicationConfig(app, def);
                        activityMonitoringFactor = Float.parseFloat(appConfig.getActivityMonitoringFactor());                        
                    }
                }
            }
            
            score = weight * factor * activityMonitoringFactor;
        }

        return score;
    }

    private float compensateForCertHistory(Application app, String instance, String nativeIdentity, String name, String value, boolean permission, float factor)
            throws GeneralException {

        _historyItemsFetchInfo = new HistoryItemsFetchInfo(_identity.getId(), app.getName(), instance, nativeIdentity);
        if (!_historyItemsFetchInfo.equals(_lastHistorysItemFetchInfo)) {
            loadHistoryActionCache();
        } 
        
        CertificationAction action = findAction(permission, name, value);
   
        if (action != null) {
            switch (action.getStatus()) {
      
            case Approved: {
                factor = _certifiedFactor;
            }
            break;
            case Mitigated: {
                Date exp = action.getMitigationExpiration();
                if (exp == null) {
                    // assume it never expires?
                    factor = _mitigatedFactor;
                }
                else if (_now.compareTo(exp) >= 0) {
                    // the mitigation expired
                    factor = _expiredFactor;
                }
                else {
                    factor = _mitigatedFactor;
                }
            }
            break;
            case Remediated: {
                factor = _remediatedFactor;
            }
            break;
            default: {
                // shouldn't see Delegated in a completed
                // certification, assume uncertified
            }
            break;
            }
        }
        
        _lastHistorysItemFetchInfo = _historyItemsFetchInfo;
        
        return factor;
    }
    
    private CertificationAction findAction(boolean permission, String name, String value) {

        CertificationAction action = null;
        if (permission) {
            for (Permission onePermission : _historyPermissionActionCache.keySet()) {
                if (onePermission.hasTarget(name) && onePermission.hasRight(value)) {
                    action = _historyPermissionActionCache.get(onePermission);
                    break;
                }
            }
        } else {
            action = _historyAttributeActionCache.get(new Pair<String, String>(name, value));
        }

        return action;
    }

    private void loadHistoryActionCache() throws GeneralException {

        _historyAttributeActionCache = new HashMap<Pair<String, String>, CertificationAction>();
        _historyPermissionActionCache = new HashMap<Permission, CertificationAction>();
        
        List<IdentityHistoryItem> historyItems = fetchHistoryItems();
        
        for (IdentityHistoryItem historyItem : historyItems) {
            EntitlementSnapshot entitlements = historyItem.getEntitlements();
            if (entitlements == null) {continue;}
            
            List<Permission> permissions = entitlements.getPermissions();
            if (permissions != null) {
                for (Permission onePermission : permissions) {
                    if (onePermission == null) {continue;}
                    _historyPermissionActionCache.put(onePermission, historyItem.getAction());
                }
            }

            Attributes<String, Object> attributes = entitlements.getAttributes();
            if (attributes != null) { 
                for (String key : attributes.keySet()) {
                    Object value = attributes.get(key);
                    if (value instanceof Collection) {
                        @SuppressWarnings("rawtypes")
                        Collection collection = (Collection) value;
                        for (Object oneValue : collection) {
                            if (oneValue instanceof String) {
                                Pair<String, String> pair = new Pair<String, String>(key, (String) oneValue);
                                _historyAttributeActionCache.put(pair, historyItem.getAction());
                            }
                        }
                    } else if (value instanceof String) {
                        Pair<String, String> pair = new Pair<String, String>(key, (String) value);
                        _historyAttributeActionCache.put(pair, historyItem.getAction());
                    }
                }
            }
        }
    }
    
    private List<IdentityHistoryItem> fetchHistoryItems() throws GeneralException {
    
        IdentityHistoryService historyService = new IdentityHistoryService(_context);
        return historyService.getMostRecentAccountDecisions(_historyItemsFetchInfo.identityId, _historyItemsFetchInfo.application, _historyItemsFetchInfo.instance, _historyItemsFetchInfo.nativeIdentity);
    }
    
    
    /**
     * Reconcile the ScoreConfig with the RightConfig.
     * Reconciliation involves two steps:
     * 1. Remove all permissions from the ScoreDefinitions that are not found in the RightConfig
     * 2. Add missing permissions with default weights to the ScoreDefintions
     */
    @Override @SuppressWarnings("unchecked")
    public void update(SailPointContext context, ScoreConfig scoreConfig) {
        ScoreDefinition entitlementScoreDefinition = scoreConfig.getIdentityScore(ScoreConfig.SCORE_RAW_ENTITLEMENT);
        if (entitlementScoreDefinition != null) {
            List<ApplicationEntitlementWeights> entitlementWeightsList =
                (List<ApplicationEntitlementWeights>)entitlementScoreDefinition.getArgument(EntitlementScoreConfig.ARG_APPLICATION_WEIGHTS);
            if (entitlementWeightsList != null) {
                try {
                    List<EntitlementWeight> defaultWeights = getDefaultWeights(context);
                    for (ApplicationEntitlementWeights appEntitlementWeights : entitlementWeightsList) {
                        List<EntitlementWeight> entitlementWeights = appEntitlementWeights.getWeights();
                        if (entitlementWeights != null) {
                            // Add what needs adding
                            List<EntitlementWeight> weightsToAdd = new ArrayList<EntitlementWeight>();
                            for (EntitlementWeight defaultWeight : defaultWeights) {
                                if (!foundEntitlementIn(entitlementWeights, defaultWeight)) {
                                    weightsToAdd.add(defaultWeight);
                                }
                            }
                            for (EntitlementWeight weightToAdd : weightsToAdd) {
                                EntitlementWeight addedWeight = (EntitlementWeight)weightToAdd.clone();
                                entitlementWeights.add(addedWeight);
                            }
                            
                            // Remove what needs removing
                            List<EntitlementWeight> weightsToRemove = new ArrayList<EntitlementWeight>();
                            for (EntitlementWeight entitlementWeight : entitlementWeights) {
                                if (entitlementWeight.getType() == EntitlementType.permission && !foundEntitlementIn(defaultWeights, entitlementWeight)) {
                                    weightsToRemove.add(entitlementWeight);
                                }
                            }
                            entitlementWeights.removeAll(weightsToRemove);
                        }
                    }
                } catch (GeneralException e) {
                    log.error("EntitlementScorer failed to update the ScoreConfig because the RightsConfig could not be accessed.", e);
                }
            }
        }
    }
    
    private List<EntitlementWeight> getDefaultWeights(SailPointContext context) throws GeneralException {
        List<EntitlementWeight> defaultWeights = new ArrayList<EntitlementWeight>();
        RightConfig rightConfig = context.getObjectByName(RightConfig.class, RightConfig.OBJ_NAME);
        
        if (rightConfig != null) {
            List<Right> rights = rightConfig.getRights();
            
            if (rights != null) {
                for (Right right : rightConfig.getRights()) {
                    defaultWeights.add(new EntitlementWeight(EntitlementType.permission, null, right.getName(), Integer.toString(right.getWeight())));
                }
            }
        }
        return defaultWeights;
    }
    
    private boolean foundEntitlementIn(List<EntitlementWeight> entitlementWeights, EntitlementWeight weightToFind) {
        boolean found = false;
        
        for (EntitlementWeight weight : entitlementWeights) {
            if (weight.compareTo(weightToFind) == 0) {
                found = true;
                break;
            }
        }
        
        return found;
    }

    /**
     * This class is essentially a hashkey
     * which determines whether history items
     * need to be fetched from the database.
     * If the keys match then there is no need to fetch
     * the history again.
     */
    private static final class HistoryItemsFetchInfo {

        private String identityId;
        private String application;
        private String instance;
        private String nativeIdentity;
        
        private HistoryItemsFetchInfo(String identityId, String application, String instance, String nativeIdentity) {
        
            this.identityId = identityId;
            this.application = application;
            this.instance = instance;
            this.nativeIdentity = nativeIdentity;
        }
        
        public boolean equals(Object obj) {
        
            if (!(obj instanceof HistoryItemsFetchInfo)) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            
            HistoryItemsFetchInfo other = (HistoryItemsFetchInfo) obj;

            return  
                new EqualsBuilder()
                    .append(identityId, other.identityId)
                    .append(application, other.application)
                    .append(instance, other.instance)
                    .append(nativeIdentity, other.nativeIdentity)
                        .isEquals();

        }
        
        public int hashCode() {
        
            return 
                new HashCodeBuilder()
                    .append(identityId)
                    .append(application)
                    .append(instance)
                    .append(nativeIdentity)
                        .toHashCode();
        }
        
        @Override
        public String toString() {

            return 
                new ToStringBuilder(this)
                    .append("identityId", identityId)
                    .append("application", application)
                    .append("instance", instance)
                    .append("nativeIdentity", nativeIdentity)
                        .toString();
        }
    }
}
