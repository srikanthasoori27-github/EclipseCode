/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The resuls of an impact or "what if" analysis.  
 * 
 * Author: Jeff
 * 
 * Currently the only thing we analyze are changes in role
 * assignment that would result if candidate roles were promted
 * or pending role and profile changes were approved.  This may 
 * evolve to track other things like policy violations and risk scores.
 *
 * Currently one of these is generated by the WhatIfExecutor and
 * left serizlized in the TaskResult.  
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Used to hold the results of an impact analysis on a role.
 * These will be stored inside a <code>TaskResult</code>.
 */
@XMLClass
public class ImpactAnalysis extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //  
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Role assignment statistics. Normally, an item will be on this
     * list for every role that either gained or lost members during
     * the analysis.  
     */
    List<RoleAssignments> _roles;

    /**
     * Information about conflicts between the role definition 
     * and SOD policies.
     *
     * @ignore
     * NOTE: Wonderer support analysis of more than one role though
     * we don't currently use it that way.  The items on this list
     * can in theory then be for different roles and each will
     * have a "roleName" property you could be displayed. But at
     * the moment you can assume that we're only analysing one role
     * at a time and hide the column.
     */
    List<PolicyConflict> _conflicts;

    /**
     * Information about roles that are similar to this one.
     */
    List<Similarity> _similarities;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //  
    //////////////////////////////////////////////////////////////////////

    public ImpactAnalysis() {
    }

    public void add(RoleAssignments ra) {
        if (ra != null) {
            if (_roles == null)
                _roles = new ArrayList<RoleAssignments>();
            _roles.add(ra);
        }
    }
    
    // ugh - I wish we hadn't done this
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setRoleAssignments(List<RoleAssignments> roles) {
        _roles = roles;
    }

    /**
     * Role assignment statistics. Normally, an item will be on this
     * list for every role that either gained or lost members during
     * the analysis.  
     */
    public List<RoleAssignments> getRoleAssignments() {
        return _roles;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setPolicyConflicts(List<PolicyConflict> cons) {
        _conflicts = cons;
    }

    /**
     * Information about conflicts between the role definition 
     * and SOD policies.
     */
    public List<PolicyConflict> getPolicyConflicts() {
        return _conflicts;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setSimilarities(List<Similarity> sims) {
        _similarities = sims;
    }

    /**
     * Information about roles that are similar to this one.
     */
    public List<Similarity> getSimilarities() {
        return _similarities;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //  
    //////////////////////////////////////////////////////////////////////

    // convenient for JSF
    public int getRoleAssignmentCount() {
        return (_roles != null) ? _roles.size() : 0;
    }

    public int getTotalRoleLosses() {
        int losses = 0;
        if (_roles != null) {
            for (RoleAssignments ra : _roles)
                losses += ra.getLosses();
        }
        return losses;
    }
    
    public int getTotalRoleGains() {
        int gains = 0;
        if (_roles != null) {
            for (RoleAssignments ra : _roles)
                gains += ra.getGains();
        }
        return gains;
    }

    public int getTotalPolicyConflicts() {
        return (_conflicts != null) ? _conflicts.size() : 0;
    }

    public int getTotalRoleSimilarities() {
        return (_similarities != null) ? _similarities.size() : 0;
    }

    public void add(PolicyConflict pc) {
        if (pc != null) {
            if (_conflicts == null)
                _conflicts = new ArrayList<PolicyConflict>();
            _conflicts.add(pc);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // RoleAssignments
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Class tracking changes in role assignment.
     */
    @XMLClass
    public static class RoleAssignments {

        /**
         * The maximum number of identity names remembered
         * for gains and losses. This is sometimes interesting
         * to display but it does not scale.
         */
        public static final int DFLT_MAX_IDENTITIES = 100;

        String _name;
        String _uid;
        int _gains;
        int _losses;

        List<String> _gainedIdentities;
        List<String> _lostIdentities;

        int maxIdentities = DFLT_MAX_IDENTITIES;

        public RoleAssignments() {
        }

        public RoleAssignments(Bundle role) {
            _name = role.getName();
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        public void setMaxIdentities(int max) {
            maxIdentities = max;
        }

        @XMLProperty
        public void setGains(int i) {
            _gains = i;
        }

        public int getGains() {
            return _gains;
        }

        public void incGains() {
            _gains++;
        }

        @XMLProperty
        public void setLosses(int i) {
            _losses = i;
        }

        public int getLosses() {
            return _losses;
        }

        public void incLosses() {
            _losses++;
        }

        @XMLProperty
        public void setGainedIdentities(List<String> ids) {
            _gainedIdentities = ids;
        }

        public List<String> getGainedIdentities() {
            return _gainedIdentities;
        }

        @XMLProperty
        public void setLostIdentities(List<String> ids) {
            _lostIdentities = ids;
        }

        public List<String> getLostIdentities() {
            return _lostIdentities;
        }

        /**
         * @ignore
         * Kludge for JSF.  We want to iterate over these
         * and creating hidden expandos for the identity name lists.
         * this requires the assignment of unique object ids to various divs.
         * We originally used the _name for this but this can have & in 
         * it which confuses the XML "tidy" filter.  We don't really
         * care what these are and the page is read only so just generate
         * something when asked.
         */
        public String getUid() {
            if (_uid == null)
                _uid = Util.uuid();
            return _uid;
        }

        //
        // Convenience builders
        //

        public void addLoss(Identity id) {
            _losses++;
            if (_lostIdentities == null)
                _lostIdentities = new ArrayList<String>();
            if (maxIdentities < 0 || _lostIdentities.size() < maxIdentities)
                _lostIdentities.add(id.getName());
        }
            
        public void addGain(Identity id) {
            _gains++;
            if (_gainedIdentities == null)
                _gainedIdentities = new ArrayList<String>();
            if (maxIdentities < 0 || _gainedIdentities.size() < maxIdentities)
                _gainedIdentities.add(id.getName());
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // PolicyConflict
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Class tracking conflicts with SOD policies.
     */
    @XMLClass
    public static class PolicyConflict {
        
        String _roleName;
        String _policyName;
        String _constraintName;
        String _description;

        public PolicyConflict() {
        }

        @XMLProperty
        public void setRoleName(String s) {
            _roleName = s;
        }

        public String getRoleName() {
            return _roleName;
        }

        @XMLProperty
        public void setPolicyName(String s) {
            _policyName = s;
        }

        public String getPolicyName() {
            return _policyName;
        }

        @XMLProperty
        public void setConstraintName(String s) {
            _constraintName = s;
        }

        public String getConstraintName() {
            return _constraintName;
        }

        @XMLProperty(mode=SerializationMode.ELEMENT)
        public void setDescription(String s) {
            _description = s;
        }

        public String getDescription() {
            return _description;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Similarity
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * Class tracking objects with similar features.
     */
    @XMLClass
    public static class Similarity {
        
        String _name;
        String _otherName;
        int _percent;

        public Similarity() {
        }

        @XMLProperty
        public void setName(String s) {
            _name = s;
        }

        public String getName() {
            return _name;
        }

        @XMLProperty
        public void setOtherName(String s) {
            _otherName = s;
        }

        public String getOtherName() {
            return _otherName;
        }

        @XMLProperty
        public void setPercent(int i) {
            _percent = 0;
        }

        public int getPercent() {
            return _percent;
        }
    }


}

