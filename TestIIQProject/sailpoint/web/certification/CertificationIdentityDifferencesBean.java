/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import sailpoint.object.Difference;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.PermissionDifference;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple JSF bean that helps in rendering differences on a certification
 * identity.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationIdentityDifferencesBean {

    private IdentityDifference diffs;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public CertificationIdentityDifferencesBean() {}

    /**
     * Constructor that takes the differences to render.
     * 
     * @param  diffs  The IdentityDifference to be rendered.
     */
    public CertificationIdentityDifferencesBean(IdentityDifference diffs) {
        this.diffs = diffs;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if there any changes detected.
     */
    public boolean isChangesDetected() {
        return ((null != this.diffs) &&
                (!isEmpty(this.diffs.getAttributeDifferences()) ||
                 !isEmpty(this.diffs.getLinkDifferences()) ||
                 !isEmpty(this.diffs.getPermissionDifferences())));
    }

    /**
     * Return whether the list is empty or null.
     */
    private static boolean isEmpty(List list) {
        return ((null == list) || list.isEmpty());
    }

    /**
     * Get the business role differences.
     */
    public Difference getBundleDifferences() {
        return (null != this.diffs) ? this.diffs.getBundleDifferences() : null;
    }

    /**
     * Get the assigned role differences
     */
    public Difference getAssignedRoleDifferences() {
        return (null != this.diffs) ? this.diffs.getAssignedRoleDifferences() : null;
    }

    /**
     * Get the policy violation differences.
     */
    public Difference getPolicyViolationDifferences() {
        return (null != this.diffs) ? this.diffs.getPolicyViolationDifferences() : null;
    }

    /**
     * Get the link differences.
     */
    public List<Difference> getLinkDifferences() {
        return (null != this.diffs) ? this.diffs.getLinkDifferences() : null;
    }

    /**
     * Get the permission differences.
     */
    public List<PermissionDifference> getPermissionDifferences() {
        return (null != this.diffs) ? this.diffs.getPermissionDifferences() : null;
    }

    /**
     * Get the identity attribute differences (excluding the bundles and policy
     * violations).
     */
    public List<Difference> getIdentityAttributeDifferences() {
        List<Difference> iaDiffs = new ArrayList<Difference>();
        if ((null != this.diffs) && (null != this.diffs.getAttributeDifferences())) {
            for (Difference diff : this.diffs.getAttributeDifferences()) {
                if (!Identity.ATT_BUNDLES.equals(diff.getAttribute()) &&
                    !IdentityDifference.ATT_POLICY_VIOLATIONS.equals(diff.getAttribute())) {
                    iaDiffs.add(diff);
                }
            }
        }
        return iaDiffs;
    }
}
