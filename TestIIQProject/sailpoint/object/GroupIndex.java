/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class containing statistics about a group of identities.
 * 
 * Author: Jeff
 * 
 * The identities in the group are loosly associated by a search filter, 
 * there are no direct references between the Identity and GroupIndex classes.
 * GroupIndex objects are generated dynamically from a GroupDefinition.
 * 
 */

package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.GeneralException;

/**
 * A class containing statistics about a group of identities.
 */
@XMLClass
public class GroupIndex extends BaseIdentityIndex
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A reference back the group definition that was used to generate
     * this index.
     */
    GroupDefinition _definition;

    //
    // General Statistics
    //
    
    /**
     * The number of identities in the group.
     */
    int _memberCount;

    //
    // Composite Score Buckets
    //

    /**
     * Maximum number of bands that composite scores can be segmented into.
     * These correspond to the bands defined in the ScoreConfig.
     * In theory there can be an arbitrary number of these, but in practice
     * they are only useful with a relatively small number. 3 is common
     * (low, medium, high), 5 is similar to various "star" ranking system.
     * 10 is also a familiar scale, but beyond 10 it becomes too fine
     * grained. There is no formally defined maximum number of
     * bands, but there needs to be one. Assume 10. Avoid a one-to-many
     * relationship table by just blowing them out inline. Obviously this
     * will become unwieldy if it needs to go beyond 10.
     */
    public static final int MAX_BANDS = 10;

    int _band1;
    int _band2;
    int _band3;
    int _band4;
    int _band5;
    int _band6;
    int _band7;
    int _band8;
    int _band9;
    int _band10;

    /**
     * The number of bands with valid results. This is taken from
     * whatever was in the ScoreConfig at the time the index was
     * generated.
     */
    int _bandCount;

    /**
     * Total number of certifications due for this entity
     */
    int _certificationsDue;

    /**
     * Total number of certifications owned by this entity which were 
     * completed on time
     */
    int _certificationsOnTime;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    public GroupIndex() {
    }

    /**
     * These are allowed to have names, but they might not be unique.
     * This might not be necessary, but it is consistent with GroupDefinitions
     * and makes them easier to identify.
     */
    @Override
    public boolean hasName() {
        return true;
    }

    @Override
    public boolean isNameUnique() {
        return false;
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitGroupIndex(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="GroupDefinitionRef")
    public void setDefinition(GroupDefinition def) {
        _definition = def;
    }

    /**
     * A reference back the group definition that was used to generate
     * this index.
     */
    public GroupDefinition getDefinition() {
        return _definition;
    }

    /**
     * The number of identities in the group.
     */
    @XMLProperty
    public int getMemberCount() {
        return _memberCount;
    }

    public void setMemberCount(int i) {
        _memberCount = i;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Band Properties
    //
    // For Hibernate we'll expose these as discrete properties.
    // Started using an array with pseudo-property accessors, but
    // it was more trouble than it was worth.  
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The number of bands with valid results. This is taken from
     * whatever was in the ScoreConfig at the time the index was
     * generated.
     */
    @XMLProperty
    public int getBandCount() {
        return _bandCount;
    }
    
    public void setBandCount(int i) {
        _bandCount = i;
    }

    @XMLProperty
    public int getBand1() {
        return _band1;
    }

    public void setBand1(int i) {
        _band1 = i;
    }

    @XMLProperty
    public int getBand2() {
        return _band2;
    }

    public void setBand2(int i) {
        _band2 = i;
    }

    @XMLProperty
    public int getBand3() {
        return _band3;
    }

    public void setBand3(int i) {
        _band3 = i;
    }

    @XMLProperty
    public int getBand4() {
        return _band4;
    }

    public void setBand4(int i) {
        _band4 = i;
    }

    @XMLProperty
    public int getBand5() {
        return _band5;
    }

    public void setBand5(int i) {
        _band5 = i;
    }

    @XMLProperty
    public int getBand6() {
        return _band6;
    }

    public void setBand6(int i) {
        _band6 = i;
    }

    @XMLProperty
    public int getBand7() {
        return _band7;
    }

    public void setBand7(int i) {
        _band7 = i;
    }

    @XMLProperty
    public int getBand8() {
        return _band8;
    }

    public void setBand8(int i) {
        _band8 = i;
    }

    @XMLProperty
    public int getBand9() {
        return _band9;
    }

    public void setBand9(int i) {
        _band9 = i;
    }

    @XMLProperty
    public int getBand10() {
        return _band10;
    }

    public void setBand10(int i) {
        _band10 = i;
    }

    /**
     * Total number of certifications due for this entity
     */
    @XMLProperty
    public int getCertificationsDue() {
        return _certificationsDue;
    }

    public void setCertificationsDue(int certificationsDue) {
        _certificationsDue = certificationsDue;
    }

    public void incCertificationsDue(){
        _certificationsDue++;    
    }

    /**
     * Total number of certifications owned by this entity which were 
     * completed on time
     */
    @XMLProperty
    public int getCertificationsOnTime() {
        return _certificationsOnTime;
    }

    public void setCertificationsOnTime(int certificationsOnTime) {
        _certificationsOnTime = certificationsOnTime;
    }

    public void incCertificationsOnTime(){
        _certificationsOnTime++;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * For applications that want to iterate over the configured
     * number of bands in an extensible way.
     */
    public int getBand(int index) {
        int value = 0;
        switch (index) {
        case 0: value = _band1; break;
        case 1: value = _band2; break;
        case 2: value = _band3; break;
        case 3: value = _band4; break;
        case 4: value = _band5; break;
        case 5: value = _band6; break;
        case 6: value = _band7; break;
        case 7: value = _band8; break;
        case 8: value = _band9; break;
        case 9: value = _band10; break;
        }
        return value;
    }

    public void setBand(int index, int value) {
        switch (index) {
        case 0: _band1 = value; break;
        case 1: _band2 = value; break;
        case 2: _band3 = value; break;
        case 3: _band4 = value; break;
        case 4: _band5 = value; break;
        case 5: _band6 = value; break;
        case 6: _band7 = value; break;
        case 7: _band8 = value; break;
        case 8: _band9 = value; break;
        case 9: _band10 = value; break;
        }
    }

    /**
     * Reset the state of an index after it has been used.
     */
    public void reset() {

        super.reset();

        _memberCount=0;
        _bandCount = 0;

        _band1 = 0;
        _band2 = 0;
        _band3 = 0;
        _band4 = 0;
        _band5 = 0;
        _band6 = 0;
        _band7 = 0;
        _band8 = 0;
        _band9 = 0;
        _band10 = 0;

        _certificationsDue = 0;
        _certificationsOnTime = 0;
    }

    public boolean isDifferent(GroupIndex other) {
        return super.isDifferent(other) ||
                _certificationsDue != other.getCertificationsDue()  ||
        _certificationsOnTime != other.getCertificationsOnTime();
    }

    /**
     * Assimilate one identity index.
     * The average scores are just being totaled now, 
     * call finalize when done iterating.
     */
    public void accumulate(BaseIdentityIndex other, List<ScoreBandConfig> bands) {

        // we may be called with a null identity scorecard but
        // be sure to always tick the member count
        _memberCount++;

        if (other != null) {

            _compositeScore += other.getCompositeScore();
            _businessRoleScore += other.getBusinessRoleScore();
            _rawBusinessRoleScore += other.getRawBusinessRoleScore();
            _policyScore += other.getPolicyScore();
            _rawPolicyScore += other.getRawPolicyScore();
            _entitlementScore += other.getEntitlementScore();
            _rawEntitlementScore += other.getRawEntitlementScore();
            _certificationScore += other.getCertificationScore();

            //_certificationsCompleted += other.getCertificationsCompleted();
            // _certificationsCompletedOnTime += other.getCertificationsCompletedOnTime();

            _totalViolations += other.getTotalViolations();

            _totalDelegations += other.getTotalDelegations();
            _totalRemediations += other.getTotalRemediations();
            _totalMitigations += other.getTotalMitigations();
            _totalApprovals += other.getTotalApprovals();

            // bump our band counters for the composite score
            if (bands != null) {

                int score = other.getCompositeScore();

                _bandCount = bands.size();
                if (_bandCount > MAX_BANDS) _bandCount = MAX_BANDS;

                for (int i = 0 ; i < _bandCount ; i++) {
                    ScoreBandConfig band = bands.get(i);

                    if (score >= band.getLowerBound() && 
                        score <= band.getUpperBound()) {

                        setBand(i, getBand(i) + 1);
                    }
                }
            }
        }
    }

    /**
     * After accumulating all the group scores, do an average.
     * Someday could do something fancier like tossing out
     * some number of the extremes on either side.
     */
    public void average() {

        if (_memberCount > 0) {
            
            // note that statistics like _totalViolations 
            // are not averaged, I suppose that could be useful too

            _compositeScore      /= _memberCount;
            _rawBusinessRoleScore /= _memberCount;
            _businessRoleScore    /= _memberCount;
            _entitlementScore    /= _memberCount;
            _rawEntitlementScore /= _memberCount;
            _policyScore         /= _memberCount;
            _rawPolicyScore      /= _memberCount;
            _certificationScore  /= _memberCount;
            
            // don't average this one, we're interested in the total
            // for the entire organization
            //_totalViolations     /= _memberCount;
        }
    }

    /**
     * Copy the contents of one scorecard to another.
     */
    public void assimilate(GroupIndex src) {

        super.assimilate(src);

        _certificationsDue = src.getCertificationsDue();
        _certificationsOnTime = src.getCertificationsOnTime();
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("definition", "Group");
        cols.put("definition.factory", "Factory");
        //cols.put("created", "Created");
        cols.put("memberCount", "Members");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %-30s %s\n";
    }

}
