/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object encapsulating all persistent HttpSession state
 * for a policy editing session.
 *
 * Author: Jeff
 *
 * This is conceptually similar to EditSession, but makes
 * assumptions about the pure DTO model used by GenericPolicyBean
 * and eventually the other policy beans.
 *
 * There's actually not much to do here, but keep it around in case
 * we want to save something other than the PolicyDTO.
 *
 * !! I don't like this, just merge this with PolicyDTO, don't
 * need another level of indirection..
 */

package sailpoint.web.policy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Describer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Describable;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Policy;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

public class PolicySession implements Serializable 
{
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(PolicySession.class);

    /**
     * DTO for the policy being edited.
     */
    PolicyDTO _policy;

    /**
     * Property set by the main policy page to have the id
     * of the constraint selected for editing.
     * This will be the id column of LiveGrid data source.
     * Since we have to deal with new objects that haven't been
     * committed yet, this uses generated uuids assigned
     * to the GenericConstraintBean rather than the repository id.
     */
    String _constraintId;

    /**
     * The constraint bean currently being edited.
     * This is a clone of one from the PolicyDTO.
     */
    BaseConstraintDTO _constraint;

    /**
     * Uid of the selected SODRole item for deletion, when 
     * editing SOD policy constraints.
     */
    String _itemId;

    /**
     * Cached list of roles for selection in role SOD policies.
     */
    SelectItem[] _roles;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public PolicySession()
    {
    }

    public PolicySession(Policy p)
    {
        setPolicy(p);
    }

    public void setPolicy(Policy p) {

        _policy = new PolicyDTO(p);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public PolicyDTO getPolicy() {
        return _policy;
    }

    public String getConstraintId() {
        return _constraintId;
    }

    public void setConstraintId(String id) {
        _constraintId = id;
    }

    public BaseConstraintDTO getConstraint() {
        return _constraint;
    }

    public String getItemId() {
        return _itemId;
    }

    public String getDescriptionJSON() {
        return _policy.getDescriptionsJson();
    }

    public void setDescriptionJSON(String descriptionsJson) {
        _policy.setDescriptionsJson(descriptionsJson);
    }

    public void setItemId(String s) {
        _itemId = s;
    }

    /**
     * Build a SelectItem list of role names for selection
     * in a role SOD policy.
     * !! This shouldn't be used, need an Ext suggest.
     */
    public SelectItem[] getRoles(SailPointContext con) 
        throws GeneralException {
        
        if (_roles == null) {
            List<SelectItem> items = new ArrayList<SelectItem>();
            List<String> props = new ArrayList<String>();
            props.add("name");

            // TODO: Will want to filter based on correlatability,
            // either here or during result iteration
            Iterator<Object[]> result = con.search(Bundle.class, null, props);
            while (result.hasNext()) {
                Object[] row = result.next();
                String name = (String)row[0];
                items.add(new SelectItem(name, name));
            }
            _roles = items.toArray(new SelectItem[items.size()]);
        }

        return _roles;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Common Constraint Editing
    //
    //////////////////////////////////////////////////////////////////////

    public BaseConstraintDTO findConstraint(String uid) {

        BaseConstraintDTO found = null;
        List<BaseConstraintDTO> cons = _policy.getConstraints();
        if (uid != null && cons != null) {
            for (BaseConstraintDTO con : cons) {
                // note that we're using "uid", not "id"
                if (uid.equals(con.getUid())) {
                    found = con;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Find the constraint for with the posted uid.
     */
    public BaseConstraintDTO getSelectedConstraint() {

        return findConstraint(_constraintId);
    }

    /**
     * Initialize editing of an existing constraint.
     * The constraint is cloned so we can wail on it and cancel later.
     * Return true if we were able to setup editing.
     */
    public boolean editSelectedConstraint() {

        boolean editing = false;

        BaseConstraintDTO src = getSelectedConstraint();
        if (src != null) {
            // Note that the new constraint DTO will have been given
            // a generated uuid but we don't trash _constraintId, that
            // needs to be preserved so we can find the original
            // later if we decide to commit.
            _constraint = src.clone();
            editing = true;
        }

        return editing;
    }

    /**
     * Initialize editing of a new constraint.
     * We do not need to clone the constraint.
     */
    public boolean editNewConstraint() {

        // let the PolicyDTO build it so it can set the matchType
        _constraint = _policy.newConstraint();

        // make sure this is null so we know it's new
        _constraintId = null;

        return true;
    }

    /**
     * Remove the currently selected constraint from the DTO model. 
     */
    public boolean deleteSelectedConstraint() {

        boolean deleted = false;
        BaseConstraintDTO con = getSelectedConstraint();
        if (con != null) {
            _policy.remove(con);
            deleted = true;
        }
        return deleted;
    }

    /**
     * Clear editing state after a cancel action.
     */
    public void cancelConstraint() {

        _constraintId = null;
        _constraint = null;
    }

    /**
     * Commit the constraint we've been editing into the PolicyDTO.
     */
    public void commitConstraint() throws GeneralException {

        if (_constraint != null) {
            _constraint.validate();
            _policy.replace(_constraintId, _constraint);
        }

        // done editing
        _constraintId = null;
        _constraint = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // GenericPolicy Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Action handler for the "Add Attribute" or "Add Permission" buttons.
     * The index of the IdentitySelector is passed.
     */
    public void addMatchTerm(int psn, boolean permission)
            throws GeneralException {

        String type = permission ? IdentitySelector.MatchTerm.Type.Permission.name() :
                IdentitySelector.MatchTerm.Type.Entitlement.name();

        addMatchTerm(psn, type);
    }
    /**
     * Action handler for the "Add Attribute" or "Add Permission" buttons.
     * The index of the IdentitySelector is passed.  The explicit type is
     * passed so we don't need to assume.
     */
    public void addMatchTerm(int psn, String type)
        throws GeneralException {

        if (_constraint != null) {
            GenericConstraintDTO gcon = (GenericConstraintDTO)_constraint;
            IdentitySelectorDTO sel = gcon.getSelector(psn);

            boolean checkEffective = false;
            if (getPolicy().getType().equals(Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD)
                    || getPolicy().isCheckEffective()) {
                checkEffective = true;
            }
            sel.addMatchTerm(checkEffective, type);
        }
    }

    /**
     * Action handler for the "Delete Selected" button of the match term
     * table.
     */
    public void deleteMatchTerms(int psn) {

        if (_constraint != null) {
            GenericConstraintDTO gcon = (GenericConstraintDTO)_constraint;
            IdentitySelectorDTO sel = gcon.getSelector(psn);
            sel.deleteSelectedTerms();
        }

    }
    
    public void groupMatchTerms(int pos) {
        if (_constraint != null) {
            GenericConstraintDTO gcon = (GenericConstraintDTO)_constraint;
            IdentitySelectorDTO sel = gcon.getSelector(pos);
            sel.groupSelectedTerms();
        }
    }

    public void ungroupMatchTerms(int pos) {
        if (_constraint != null) {
            GenericConstraintDTO gcon = (GenericConstraintDTO)_constraint;
            IdentitySelectorDTO sel = gcon.getSelector(pos);
            sel.ungroupSelectedTerms();
        }
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // SODPolicy Actions
    //
    //////////////////////////////////////////////////////////////////////


    //////////////////////////////////////////////////////////////////////
    //
    // Common Actions
    //
    //////////////////////////////////////////////////////////////////////

    public void saveAction(SailPointContext con, Policy p)
        throws GeneralException {

        _policy.setCheckEffectiveOfMatchTerms();

        // commit the changes
        List<SailPointObject> deleted = new ArrayList<SailPointObject>();
        _policy.commit(p, deleted);
        Describer describer = new Describer((Describable)p);
        describer.saveLocalizedAttributes(con);

        for (SailPointObject obj : deleted) {
            // can sometimes get here if we add something then
            // delete it before comitting the add, ignore
            // if there is no persistent id
            if (obj.getId () != null)
                con.removeObject(obj);
        }
    }

}
