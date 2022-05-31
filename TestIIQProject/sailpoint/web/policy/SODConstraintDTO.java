/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO for SODConstraint objects.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.Localizer;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.SODConstraint;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseDTO;
import sailpoint.web.util.WebUtil;

public class SODConstraintDTO extends BaseConstraintDTO {

    //////////////////////////////////////////////////////////////////////
    //
    // SODRole
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Internal class to represent one role reference in the constraint.
     * Note that since we're sharing the same table goo for both sides,
     * have to use _uid from BaseDTO as the unique id for the page rather
     * than the database id since in theory the same Bundle could appear
     * on both sides.
     */
    public class SODRole extends BaseDTO {
        
        String _objectId;
        boolean _right;
        String _name;
        String _displayableName;
        String _description;

        SODRole() {
        }

        SODRole(Bundle b, boolean right) {
            _objectId = b.getId();
            _right = right;
            _name = b.getName();
            _displayableName = b.getDisplayableName();
            _description = WebUtil.localizeAttribute(b, Localizer.ATTR_DESCRIPTION);
        }
        
        public String getObjectId() {
            return _objectId;
        }

        public boolean isRight() {
            return _right;
        }

        public String getName() {
            return _name;
        }
        
        public String getDisplayableName() {
            return _displayableName;
        }

        public String getDescription() {
            return _description;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Summary of the LHS.
     */
    String _lhs;

    /**
     * Summary of the RHS.
     */
    String _rhs;
    
    /**
     * Beans on the left.
     */
    List<SODRole> _left;

    /**
     * Beans on the right.
     */
    List<SODRole> _right;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public SODConstraintDTO() {
        this(new SODConstraint());
    }

    public SODConstraintDTO(SODConstraint src) {

        super(src);
        List<Bundle> bundles = src.getLeftBundles();
        if (bundles != null) {
            _left = new ArrayList<SODRole>();
            for (Bundle b : bundles) {
              //Bundle descriptions are now stored as Localized Attributes
                //Fetch the localized attribute value for description matching the current locale
                b.setDescription(WebUtil.localizeAttribute(b, Localizer.ATTR_DESCRIPTION));
                _left.add(new SODRole(b, false));
            }
        }

        bundles = src.getRightBundles();
        if (bundles != null) {
            _right = new ArrayList<SODRole>();
            for (Bundle b : bundles) {
              //Bundle descriptions are now stored as Localized Attributes
                //Fetch the localized attribute value for description matching the current locale
                b.setDescription(WebUtil.localizeAttribute(b, Localizer.ATTR_DESCRIPTION));
                _right.add(new SODRole(b, true));
            }
        }

        _lhs = getRoleSummary(_left);
        _rhs = getRoleSummary(_right);
    }

    private String getRoleSummary(List<SODRole> roles) {

        String summary = null;
        if (roles != null) {
            StringBuffer buf = new StringBuffer();
            int count = 0;
            for (SODRole role : roles) {
                if (count > 0) buf.append(",");
                buf.append(role.getDisplayableName());
                count++;
            }
            summary = buf.toString();
        }
        return summary;
    }

    public SODConstraintDTO(SODConstraintDTO src) {

        super(src);

        _lhs = src._lhs;
        _rhs = src._rhs;

        // the SODRoles themselves aren't editable, only the list
        List<SODRole> roles = src.getLeftRoles();
        if (roles != null)
            _left = new ArrayList<SODRole>(roles);

        roles = src.getRightRoles();
        if (roles != null)
            _right = new ArrayList<SODRole>(roles);
    }

    public BaseConstraintDTO clone() {
        return new SODConstraintDTO(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getLeftSummary() {
        return _lhs;
    }

    public String getRightSummary() {
        return _rhs;
    }

    public List<SODRole> getLeftRoles() {
        return _left;
    }

    public int getLeftCount() {
        return (_left != null) ? _left.size() : 0;
    }

    public List<SODRole> getRightRoles() {
        return _right;
    }

    public int getRightCount() {
        return (_right != null) ? _right.size() : 0;
    }

    /**
     * Lookup an SODRole by its generated unique id.
     * This can be from either list.
     * Do we still use this?
     */
    private SODRole getItem(String id) {

        SODRole found = null;
        if (_left != null) {
            for (SODRole role : _left) {
                if (role.getUid().equals(id)) {
                    found = role;
                    break;
                }
            }
        }
        if (found == null && _right != null) {
            for (SODRole role : _right) {
                if (role.getUid().equals(id)) {
                    found = role;
                    break;
                }
            }
        }
        return found;
    }

    @Override
    public Map<String, Object> getJsonMap() {
        Map<String, Object> jsonMap = super.getJsonMap();
        jsonMap.put("summary", getSummary());
        jsonMap.put("lhs", getLeftSummary());
        jsonMap.put("rhs", getRightSummary());
        return jsonMap;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add an item for a new role.
     */
    public void add(String roleName, boolean right) throws GeneralException {

        // sigh need the description
        Bundle role = getContext().getObjectByName(Bundle.class, roleName);
        if (role != null) {
            //Bundle descriptions are now stored as Localized Attributes
            //Fetch the localized attribute value for description matching the current locale
            //The other way to do this is to add a reference to the Bundle SailPointObject in the SODRole
            //This will allow us to fetch the description clientside via WebUtil localizeAttribute
            role.setDescription(WebUtil.localizeAttribute(role, Localizer.ATTR_DESCRIPTION));
            SODRole dto = new SODRole(role, right);
            if (right) {
                if (_right == null)
                    _right = new ArrayList<SODRole>();
                _right.add(dto);
            }
            else {
                if (_left == null)
                    _left = new ArrayList<SODRole>();
                _left.add(dto);
            }
        }
    }

    /**
     * Remove an SODRole by its generated unique id.
     */
    public boolean remove(String id) {

        boolean removed = false;

        if (_left != null) {
            for (SODRole role : _left) {
                if (role.getUid().equals(id)) {
                    _left.remove(role);
                    removed = true;
                    break;
                }
            }
        }
        if (!removed && _right != null) {
            for (SODRole role : _right) {
                if (role.getUid().equals(id)) {
                    _right.remove(role);
                    removed = true;
                    break;
                }
            }
        }
        return removed;
    }

    /**
     * After editing refresh the summary text.
     */
    public void refresh() {
        _summary = getName();
        _lhs = getRoleSummary(_left);
        _rhs = getRoleSummary(_right);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called by PolicyDTO during commit to create a new constraint instance
     * in the persistence model.
     */
    public BaseConstraint newConstraint() {
        return new SODConstraint();
    }

    /**
     * Commit gets a BaseConstraint rather than a GenericConstraint 
     * so PolicyDTO can manage both generic and SOD constraints
     * on the samem list.  Somewhat ugly, think about generics...
     */
    public void commit(BaseConstraint src)
        throws GeneralException {

        super.commit(src);

        SODConstraint sodsrc = (SODConstraint)src;

        sodsrc.setLeftBundles(convert(_left));
        sodsrc.setRightBundles(convert(_right));
    }

    /**
     * Convert a list of SODRoles into a list of Bundle objects.
     */
    private List<Bundle> convert(List<SODRole> src) {

        List<Bundle> bundles = null;
        if (src != null && src.size() > 0) {
            bundles = new ArrayList<Bundle>();
            for (SODRole role : src) {
                Bundle bundle = resolveById(Bundle.class, role.getObjectId());
                if (bundle != null)
                    bundles.add(bundle);
            }
        }
        return bundles;
    }

}
