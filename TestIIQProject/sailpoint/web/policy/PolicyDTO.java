/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Policy during editing.
 * Currently used only by GenericPolicyBean but  eventually the others.
 *
 * Author: Jeff
 *
 * Note that this isn't a complete DTO because we don't copy the immutable
 * fields like type, typeKey, executor, etc.  We cannto therefore
 * fully reconstruct a Policy from this, the contents has to be merged
 * into an existing Policy.  This Policy has typically been
 * saved on the HttpSession but it could also be fetched fresh from
 * the database.
 * 
 */

package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.object.ActivityConstraint;
import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Configuration;
import sailpoint.object.GenericConstraint;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.Policy.ViolationOwnerType;
import sailpoint.object.PolicyAlert;
import sailpoint.object.Rule;
import sailpoint.object.SODConstraint;
import sailpoint.object.SailPointObject;
import sailpoint.object.Signature;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.SailPointObjectDTO;
import sailpoint.web.extjs.DescriptionData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class PolicyDTO extends SailPointObjectDTO
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(PolicyDTO.class);

    //
    // We're not keeping copies of all the policy type fields
    // though we might want to grab configPage 
    // so we don't have to depend on having the Policy object
    // around to select the config pages?
    //

    /**
     * We don't allow this to be edited but but it is used
     * to kludge a generic pages for some unusual policies that
     * don't need all the fields (like a global violation rule).
     */
    String _type;

    /**
     * Derived from the policy type, this controls which
     * attributes will be included in the selection menus
     * for the match terms.  
     */
    String _matchType;

    /**
     * Policy state.  This could really be a boolean since we only
     * have two states but let's track the Policy model.
     */
    String _state;

    /**
     * Arguments for custom policies.
     * This is currently only used for Risk policy.
     * This is too annoying to make a DTO for, will need to serialize
     * as XML but will need to be VERY careful about object references
     * inside it.  This probably has to be disallowed, this can only have
     * simple values.
     */
    Attributes<String,Object> _arguments;
    
    String _violationWorkflow;
    String _violationRule;
    boolean _noViolationRule;

    /**
     * Signature to render the _arguments map.
     * These don't have references so they should serialize nicely.
     */
    Signature _signature;
     
    /**
     * Optional alert configuration.
     */
    // !! wanted a full DTO for this but it's too complicated right now
    //PolicyAlertDTO _alert;
    PolicyAlertBean _alert;

    /**
     * Constraint DTOs.
     * These will either be GenericConstraintDTOs or SODConstraintDTOs, 
     * we try to treat them generically here and let the JSF page call the
     * type-specfic property accessors.  In a few cases we have to 
     * use instanceof, could try to use generic types someday...
     */
    List<BaseConstraintDTO> _constraints;
    
    /**
     * violationOwner stuff
     */
    
    ViolationOwnerType _violationOwnerType;
    
    String _violationOwner;
    
    String _violationOwnerRule;
    
    DescriptionData _descriptionData;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyDTO(Policy p) {
        super(p);

        _type = p.getType();

        Policy.State state = p.getState();
        if (state != null)
            _state = state.toString();

        // as long as these don't have references they should
        // be okay to share
        _arguments = p.getArguments();
        _signature = p.getSignature();

        // Note that these have to be converted to object ids
        // for the selection menus.  Hmm, don't really like this
        // dependency on how they are rendered, would be better
        // if the UI components could convert these to/from names,
        // but we're using Util.Bean.getRules with an ordinary  
        // JSF select menu, we'd have to write a custom component?
        _violationRule = getName(Rule.class, p.getViolationRule());
        _violationWorkflow = getId(Workflow.class, p.getViolationWorkflow());
        
        _violationOwnerType = p.getViolationOwnerType() == null? ViolationOwnerType.Identity : p.getViolationOwnerType();
        _violationOwner = p.getViolationOwner() == null? null: p.getViolationOwner().getId();
        _violationOwnerRule = p.getViolationOwnerRule() == null? null: p.getViolationOwnerRule().getName();

        // boostrap one of these if we don't have one
        // TODO: try to see if this is empty on commit and remove it
        PolicyAlert alert = p.getAlert();
        if (alert == null) {
            alert = new PolicyAlert();
            // note that these start off enabled
            alert.setDisabled(true);
        }
        _alert = new PolicyAlertBean(alert);

        _constraints = new ArrayList<BaseConstraintDTO>();
            
        // models are bifurcated, don't really like this...
        if (Policy.TYPE_SOD.equals(_type)) {
            List<SODConstraint> srcConstraints = p.getSODConstraints();
            if (srcConstraints != null) {
                for (SODConstraint c : srcConstraints)
                    _constraints.add(new SODConstraintDTO(c));
            }
        }
        else if (Policy.TYPE_ACTIVITY.equals(_type)) {
            List<ActivityConstraint> srcConstraints = p.getActivityConstraints();
            if (srcConstraints != null) {
                for (ActivityConstraint c : srcConstraints)
                    _constraints.add(new ActivityConstraintDTO(c));
            }
        }
        else {
            // KLUDGE: For EntitlementSOD and EffectiveEntitlementSOD policies,
            // we decided to only allow entitlement attributes to appear
            // in the select menus to reduce clutter.  There isn't anything in the Policy
            // model that says this so we derive it from the type.  If
            // we start having more of these then need to rethink 
            // the design.
            if (Policy.TYPE_ENTITLEMENT_SOD.equals(p.getType()) ||
                    Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD.equals(p.getType())) {
                _matchType = IdentitySelectorDTO.MATCH_TYPE_ENTITLEMENT;
            }
        
            List<GenericConstraint> srcConstraints = p.getGenericConstraints();
            if (srcConstraints != null) {
                for (GenericConstraint c : srcConstraints) {
                    GenericConstraintDTO dto = new GenericConstraintDTO(c, _matchType);
                    _constraints.add(dto);
                }
            }
        }
        
        _descriptionData = new DescriptionData(p.getDescriptions(), Localizer.getDefaultLocaleName(Configuration.getSystemConfig()));
    
    }

    /**
     * Used to create a new empty constraint.
     * This is done in here so we can convey the matchType.
     */
    public BaseConstraintDTO newConstraint() {

        BaseConstraintDTO neu;

        if (Policy.TYPE_SOD.equals(_type)) {
            neu = new SODConstraintDTO();
            neu.setName(getMessage(MessageKeys.DEFAULT_NEW_POLICY_SUMMARY));
        }
        else if (Policy.TYPE_ACTIVITY.equals(_type)) {
            neu = new ActivityConstraintDTO();
            neu.setName(getMessage(MessageKeys.DEFAULT_NEW_POLICY_SUMMARY));
        }
        else {
            neu = new GenericConstraintDTO(null, _matchType);
        }

        // other initialization?
        neu.setName("new rule");
        
        return neu;
    }
    
    /**
     * Used to remove a constraint DTO bean that has been deleted.
     */
    public void removeConstraint(BaseConstraintDTO gc) {
        if (_constraints != null)
            _constraints.remove(gc);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public String getType() {
        return _type;
    }

    @Override
    public void setName(String s) {
        if(s != null) {
            s = WebUtil.sanitizeHTML(s);
        }

        super.setName(s);
    }

    public String getState() {
        return _state;
    }

    public void setState(String s) {
        _state = s;
    }

    public Attributes<String,Object> getArguments() {
        // bootstrap some if we want them
        if (_arguments == null)
            _arguments = new Attributes<String,Object>();
        return _arguments;
    }

    /**
     * The generic signature editing include (include/signature.xhtml)
     * wants to see this property on the target object.
     */
    public Attributes<String,Object> getAttributes() {
        return getArguments();
    }

    public String getViolationRule() {
        return _violationRule;
    }

    public void setViolationRule(String s) {
        _violationRule = s;
    }

    public String getViolationWorkflow() {
        return _violationWorkflow;
    }

    public void setViolationWorkflow(String s) {
        _violationWorkflow = s;
    }

    public Signature getSignature() {
        return _signature;
    }

    public PolicyAlertBean getAlert() {
        return _alert;
    }

    public List<BaseConstraintDTO> getConstraints() {
        return _constraints;
    }
    
    @XMLProperty
    public ViolationOwnerType getViolationOwnerType() {
        return _violationOwnerType;
    }
    
    public void setViolationOwnerType(ViolationOwnerType val) {
        _violationOwnerType = val;
    }
    
    @XMLProperty
    public String getViolationOwner() {
        return _violationOwner;
    }

    public void setViolationOwner(String s) {
        _violationOwner = trim(s);
    }
    
    @XMLProperty
    public String getViolationOwnerRule() {
        return _violationOwnerRule;
    }
    
    public void setViolationOwnerRule(String val) {
        _violationOwnerRule = val;
    }
    
    @XMLProperty
    public Identity getViolationOwnerObject() {
        Identity violationOwner = null;
        if (_violationOwner != null) {
            try {
                violationOwner = getContext().getObjectById(Identity.class, _violationOwner);
            }
            catch (GeneralException e) {
                // propagate this or allow it to become null?
                addMessage(e);
            }
        }
        return violationOwner;
    }

    public void setViolationOwnerObject(Identity violationOwner) {
        if (violationOwner != null)
            _violationOwner = violationOwner.getId();
        else
            _violationOwner = null;
    }

    /**
     * For simple custom policies with one constraint.
     */
    public BaseConstraintDTO getConstraint() {
        BaseConstraintDTO con = null;
        if (_constraints != null && _constraints.size() > 0)
            con = _constraints.get(0);
        return con;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constraint List Management
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by PolicySession when deleting the selected constraint.
     */
    public void remove(BaseConstraintDTO con) {
        if (_constraints != null)
            _constraints.remove(con);
    }
    
    /**
     * Called when committing an edited constraint.
     * Replace the constraint DTO in our list with the new one that
     * was cloned from it.  The uid of the original object is passed in
     * since the neu has a different one.  If uid is null it means
     * we're creating a new object so just append it.
     */
    public void replace(String uid, BaseConstraintDTO neu) {
        if (uid == null) {
            if (_constraints == null)
                _constraints = new ArrayList<BaseConstraintDTO>();
            _constraints.add(neu);
        }
        else if (_constraints != null) {
            for (int i = 0 ; i < _constraints.size() ; i++) {
                BaseConstraintDTO con = _constraints.get(i);
                if (uid.equals(con.getUid())) {
                    _constraints.set(i, neu);
                    break;
                }
            }
        }

        // a hook for subclasses to refresh summary text after editing
        neu.refresh();
    }
    
    public String getDescriptionsJson() {
        return _descriptionData.getDescriptionsJson();
    }
    
    public void setDescriptionsJson(String descriptionsJson) {
        _descriptionData.setDescriptionsJson(descriptionsJson);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Save editing state.
     * If we have any constraints that were deleted add them to the
     * deleted list.
     *
     * This is awkward because of the different constraint classes
     * we can be dealing with.
     */
    public void commit(Policy p, List<SailPointObject> deleted)
        throws GeneralException {
        
        super.commit(p);

        Policy.State state = Enum.valueOf(Policy.State.class, _state);
        if (state != null)
            p.setState(state);

        // assume no references so just copy over, but collapse empty maps
        if (_arguments == null || _arguments.size() == 0)
            p.setArguments(null);
        else
            p.setArguments(_arguments);
        
        p.setDescriptions(_descriptionData.getDescriptionMap());
        
        // since the rule and workflow ARE arguments be sure to set this after
        // setting the argument map
        // these were maintained as ids for the menu, have to convert
        // them back into names
        p.setViolationRule(getName(Rule.class, trim(_violationRule)));
        p.setViolationWorkflow(getName(Workflow.class, trim(_violationWorkflow)));

        copyViolationOwnerInfoToPolicy(p);

        // Sigh, this isn't a DTO it has been accumulating changes to 
        // a wrapped WorkitemConfig object.  We now have to do validation,
        // and refetch references to make sure everything is in the
        // same Hibernate session.
        if (_alert != null) {
            PolicyAlert alert = _alert.commit();
            p.setAlert(alert);
        }
        
        // Try to deal with the two types of constraint list in the same
        // way as much as possible, think about using generic types
        // to make this easier.  Note that we have to keep the list
        // untyped because you can't downcase from List<SODConstraint>
        // to a List<BaseConstraint> (forget why that is...)

        List srcConstraints;
        if (Policy.TYPE_SOD.equals(_type)) {
            srcConstraints = p.getSODConstraints();
            if (srcConstraints == null) {
                List<SODConstraint> scons = new ArrayList<SODConstraint>();
                p.setSODConstraints(scons);
                srcConstraints = scons;
            }
        }
        else if (Policy.TYPE_ACTIVITY.equals(_type)) {
            srcConstraints = p.getActivityConstraints();
            if (srcConstraints == null) {
                List<ActivityConstraint> scons = new ArrayList<ActivityConstraint>();
                p.setActivityConstraints(scons);
                srcConstraints = scons;
            }
        }
        else {
            srcConstraints = p.getGenericConstraints();
            if (srcConstraints == null) {
                List<GenericConstraint> gcons = new ArrayList<GenericConstraint>();
                p.setGenericConstraints(gcons);
                srcConstraints = gcons;
            }
        }

        commit(p, srcConstraints, deleted);
    }

    public void copyViolationOwnerInfoToPolicy(Policy p) {

        // tqm: I didn't quite understand the idOrName business
        // (instead of just using an id or name consistently) but, 
        // I have kept the code similar for _violationOwner as it is for _owner stuff
        // I think consistency is more important here anyway

        if (_violationOwnerType == null) {
            _violationOwnerType = ViolationOwnerType.Identity;
        }
        p.setViolationOwnerType(_violationOwnerType);

        if (_violationOwnerType == ViolationOwnerType.None) {
            p.setViolationOwner(null);
        } else if (_violationOwnerType == ViolationOwnerType.Identity) {
            if (_violationOwner != null) {
                p.setViolationOwner(resolveById(Identity.class, trim(_violationOwner)));
            } else {
                p.setViolationOwner(null);
            }
            p.setViolationOwnerRule(null);
        } else if (_violationOwnerType == ViolationOwnerType.Manager){
            p.setViolationOwner(null);
            p.setViolationOwnerRule(null);
        } else if (_violationOwnerType == ViolationOwnerType.Rule) {
            if (_violationOwnerRule != null) {
                p.setViolationOwnerRule(resolveByName(Rule.class, trim(_violationOwnerRule)));
            } else {
                p.setViolationOwnerRule(null);
            }
            p.setViolationOwner(null);
        }
        else {
            throw new IllegalStateException("Unknown violationOwnerType: " + _violationOwnerType);
        }
    }
    
    /**
     * Commit the constraint list, being careful to reuse
     * previously existing objects, and keeping track of deleted ones.
     */
    private void commit(Policy p, 
                        List srcConstraints,
                        List<SailPointObject> deleted) 
        throws GeneralException {

        // avoid the "parenting problem" with lists, keep the original
        // List in place but rebuild the elements in DTO order
        List avail = new ArrayList(srcConstraints);
        srcConstraints.clear();

        if (_constraints != null) {
            for (BaseConstraintDTO dto : _constraints) {
                BaseConstraint src = findConstraint(avail, dto);
                if (src == null) {
                    src = dto.newConstraint();
                    src.setPolicy(p);
                }
                srcConstraints.add(src);
                dto.commit(src);
            }
        }

        // anything remaining is deleted
        for (Object o : avail) {
            deleted.add((SailPointObject)o);
        }
    }

    /**
     * Find a constraint from the persistence model that matches a DTO.
     * !! seems common, should have a helper method in SailPointObjectDTO 
     * for this?
     */
    private BaseConstraint findConstraint(List avail, 
                                          BaseConstraintDTO dto) {
        BaseConstraint found = null;
        if (avail != null) {
            for (int i = 0 ; i < avail.size() ; i++) {
                BaseConstraint con = (BaseConstraint)avail.get(i);
                // these should all have had repo ids
                String id = con.getId();
                if (id != null && id.equals(dto.getPersistentId())) {
                    found = con;
                    avail.remove(con);
                    break;
                } else if (id == null) {
                    //try to find using name. Required for policy simulation when constraint is not in DB
                    //in this case rule name should be unique in the policy
                    if (con.getName() != null && con.getName().equals(dto.getName())) {
                        found = con;
                        avail.remove(con);
                        break;
                    }
                }
            }
        }
        return found;
    }

    public boolean isCheckEffective() {
        return getArguments().getBoolean(Policy.ARG_CHECK_EFFECTIVE);
    }

    public void setCheckEffective(boolean checkEffective) {
        this.getArguments().put(Policy.ARG_CHECK_EFFECTIVE, checkEffective);
        
    }



    /**
     * If this is an effective entitlement SOD policy or AdvancedPolicy with checkEffective on, 
     * set checkEffective to true for all MatchTerm.  
     * Otherwise, set checkEffective false for all MatchTerm.
     */
    public void setCheckEffectiveOfMatchTerms() {

        if (_constraints != null) {
            boolean checkEffective = isCheckEffective() || Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD.equals(getType());
            for (BaseConstraintDTO dto : Util.safeIterable(_constraints)) {
                if (dto instanceof GenericConstraintDTO) {
                    GenericConstraintDTO gcDto = (GenericConstraintDTO) dto;
                    List<IdentitySelectorDTO> identSelectoDTOs = gcDto.getSelectors();
                    for (IdentitySelectorDTO identSelectorDTO : Util.safeIterable(identSelectoDTOs)) {
                        IdentitySelectorDTO.MatchExpressionDTO matchExpressionDTO = identSelectorDTO.getMatchExpression();
                        if (matchExpressionDTO != null) {
                            List<IdentitySelectorDTO.MatchTermDTO> matchTermDTOs = matchExpressionDTO.getTerms();
                            for (IdentitySelectorDTO.MatchTermDTO matchTermDTO : Util.safeIterable(matchTermDTOs)) {
                                matchTermDTO.setCheckEffectiveTree(checkEffective);
                            }

                        }
                    }

                }
            }
        }
    }

}
