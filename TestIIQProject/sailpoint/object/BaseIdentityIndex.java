/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding various scores and statistics we calculate
 * for identities or groups of identities.  This is the base
 * class for both GroupIndex and Scorecard (should be IdentityIndex).
 * 
 * Author: Jeff
 *
 * Note that although this inherts from GenericIndex we have 
 * always represented most of the identity scores as concrete
 * integer properties which allows them to be used in searches.
 * I'm not sure if that's really necessary but I don't feel like
 * changing it now and it makes aggregating group indexes much easier.
 * 
 */

package sailpoint.object;

import java.lang.reflect.Method;

import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * A class holding various scores and statistics calculated
 * for identities or groups of identities. This is the base
 * class for both GroupIndex and Scorecard.
 */
@XMLClass
public class BaseIdentityIndex extends GenericIndex
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Scores
    //

    /**
     * Score calculated based on the Business Role (bundle)s assigned
     * to the identity, but not compensated by certification.
     */
    int _rawBusinessRoleScore;

    /**
     * Score calculated based on the Business Role (bundle)s assigned
     * to the identity, compensated by certification.
     */
    int _businessRoleScore;

    /**
     * Score calculated based on the extra entitlements discovered
     * for this identity, not compensated by certification.
     */
    int _rawEntitlementScore;

    /**
     * Score calculated based on the extra entitlements held by the user
     * (those that do not match assigned bundles).
     */
    int _entitlementScore;
    
    /**
     * Score calculated based on policy violations without compensation.
     */
    int _rawPolicyScore;

    /**
     * Score calculated based on policy violations.
     */
    int _policyScore;

    /**
     * Score calculated based on certification history.
     * Time since the last certification, number of mitigations and/or
     * remediations during certification, etc.
     */
    int _certificationScore;

    //
    // Statistics
    //

    /**
     * Total number of policy violations.
     */
    int _totalViolations;

    int _totalRemediations;

    int _totalDelegations;

    int _totalMitigations;

    int _totalApprovals;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public BaseIdentityIndex() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Score Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public int getBusinessRoleScore() {
        return _businessRoleScore;
    }

    public void setBusinessRoleScore(int i) {
        _businessRoleScore = i;
    }

    @XMLProperty
    public int getRawBusinessRoleScore() {
        return _rawBusinessRoleScore;
    }

    public void setRawBusinessRoleScore(int i) {
        _rawBusinessRoleScore = i;
    }

    @XMLProperty
    public int getEntitlementScore() {
        return _entitlementScore;
    }

    public void setEntitlementScore(int i) {
        _entitlementScore = i;
    }

    @XMLProperty
    public int getRawEntitlementScore() {
        return _rawEntitlementScore;
    }

    public void setRawEntitlementScore(int i) {
        _rawEntitlementScore = i;
    }

    @XMLProperty
    public int getPolicyScore() {
        return _policyScore;
    }

    public void setPolicyScore(int i) {
        _policyScore = i;
    }

    @XMLProperty
    public int getRawPolicyScore() {
        return _rawPolicyScore;
    }

    public void setRawPolicyScore(int i) {
        _rawPolicyScore = i;
    }

    @XMLProperty
    public int getCertificationScore() {
        return _certificationScore;
    }

    public void setCertificationScore(int i) {
        _certificationScore = i;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Statistics
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public int getTotalViolations() {
        return _totalViolations;
    }

    public void setTotalViolations(int i) {
        _totalViolations = i;
    }

    @XMLProperty
    public int getTotalRemediations() {
        return _totalRemediations;
    }

    public void setTotalRemediations(int TotalRemediations) {
        this._totalRemediations = TotalRemediations;
    }

    @XMLProperty
    public int getTotalDelegations() {
        return _totalDelegations;
    }

    public void setTotalDelegations(int totalDelegations) {
        this._totalDelegations = totalDelegations;
    }

    @XMLProperty
    public int getTotalMitigations() {
        return _totalMitigations;
    }

    public void setTotalMitigations(int totalMitigations) {
        this._totalMitigations = totalMitigations;
    }

    public int getTotalApprovals() {
        return _totalApprovals;
    }

    @XMLProperty
    public void setTotalApprovals(int totalApprovals) {
        this._totalApprovals = totalApprovals;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Reset the index after it has been used.
     */
    public void reset() {

        super.reset();

        _businessRoleScore = 0;
        _rawBusinessRoleScore = 0;
        _policyScore = 0;
        _rawPolicyScore = 0;
        _entitlementScore = 0;
        _rawEntitlementScore = 0;
        _certificationScore = 0;
        _totalViolations = 0;
        _totalRemediations = 0;
        _totalDelegations = 0;
        _totalMitigations = 0;
        _totalApprovals = 0;
    }

    /**
     * Return true if the contents of two indexes differ.
     * Used to decide if scores have changed and another
     * IdentitySnapshot is needed.
     */
    public boolean isDifferent(BaseIdentityIndex other) {

        boolean diff = (_compositeScore        != other.getCompositeScore() ||
                        _businessRoleScore     != other.getBusinessRoleScore() ||
                        _rawBusinessRoleScore  != other.getRawBusinessRoleScore() ||
                        _entitlementScore      != other.getEntitlementScore() ||
                        _rawEntitlementScore   != other.getRawEntitlementScore() ||
                        _policyScore           != other.getPolicyScore() ||
                        _rawPolicyScore        != other.getRawPolicyScore() ||
                        _certificationScore    != other.getCertificationScore() ||
                        _totalViolations       != other.getTotalViolations() ||
                        _totalRemediations     != other.getTotalRemediations()  ||
                        _totalDelegations      != other.getTotalDelegations()  ||
                        _totalMitigations      != other.getTotalMitigations() ||
                        _totalApprovals        != other.getTotalApprovals());
        

        if (!diff) {
            // look at custom scores
            diff = super.isDifferent(other);
        }

        return diff;
    }

    /**
     * Copy the contents of one scorecard to another.
     */
    public void assimilate(BaseIdentityIndex src) {

        _incomplete = src.isIncomplete();
        _compositeScore = src.getCompositeScore();

        _businessRoleScore = src.getBusinessRoleScore();
        _rawBusinessRoleScore = src.getRawBusinessRoleScore();
        _entitlementScore = src.getEntitlementScore();
        _rawEntitlementScore = src.getRawEntitlementScore();
        _policyScore = src.getPolicyScore();
        _rawPolicyScore = src.getRawPolicyScore();
        _certificationScore = src.getCertificationScore();
        _totalViolations = src.getTotalViolations();
        _totalRemediations = src.getTotalRemediations();
        _totalDelegations = src.getTotalDelegations();
        _totalMitigations = src.getTotalMitigations();
        _totalApprovals = src.getTotalApprovals();
   
    }

    /**
     * Utility for places in the UI that want to deal with scores
     * generically. The ScoreConfig will have a canonical name
     * for each score but most are not in the map.
     */
    public int getScore(String name) {

        int score = 0;
        if (name != null) {
            // note that we don't use "this" since it is normally
            // a subclass that won't have our method
            Method getter = null;
            try {
                getter = Reflection.getGetter(BaseIdentityIndex.class, name);
            }
            catch (Throwable t) {
                // must be really bad?
            }
            if (getter == null)
                score = super.getScore(name);
            else {
                try {
                    Object result = Reflection.invoke(getter, this, null);
                    score = Util.otoi(result);
                }
                catch (Throwable t) {
                    // method must have been protected or there was a 
                    // configuration error
                    score = super.getScore(name);
                }
            }
        }
        return score;
    }

    /**
     * Called if you are sure you are dealing with an extended score
     * and want to avoid the reflection overhead now in getScore.
     */
    public int getGenericScore(String name) {
        return super.getScore(name);
    }

}
