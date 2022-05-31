/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class holding optional informtion about a PolicyViolation.
 * These may be created by the PolicyExecutors, saved in the 
 * PolicyViolation attributes map, then used when rendering
 * information about the violation.  Currently the main consumer
 * of this is sailpoint.api.PolicyUtil.
 *
 * Author: Jeff
 * 
 * The intent here is to provide a more structured model that
 * can be analyzed by Beanshell in the UI or in a workflow.  
 * Without this the only mechanism to convey violation details
 * is in the free-form text of the violation description.
 *
 * The original motivation for this was a customer request
 * to show the specific entitlements involved in an SOD violation.
 * For Role SOD policies they wanted to see the entitlements that
 * gave them the conflicting roles.   
 *
 * For Entitlement SOD policies, you can sometimes achieve
 * the necessary level of detail with careful wording in the
 * policy rule description.  But for complex rules with several
 * possible combinations of entitlements a static description 
 * isn't enough.  A rendering rule can be used to generate
 * a description containing the relevant entitlements but
 * it is difficult to analyze programatically.
 *
 * We began by having PolicyUtil build a model similar to this
 * (PolicyUtil.EntitlementSummary) by analyzing the policies
 * and trying to derive the relevant entitements using the
 * expensive EntitlementCorrelator and simulating the evaluation
 * of IdentitySelectors.  This worked for Role SOD policies
 * and Entitlement SOD policies that used IdentitySelectors, 
 * but not for generic policies that used Scripts or Rules.
 *
 * For Scripts and Rules we added the notion of "pragmas"
 * which were commented lines of source code following
 * a certain syntax. 
 *
 *  // @SODLeft application='Active Directory' name='memberOf' value='a'
 *
 * Pragmas work provided that the rule can only detect
 * one combination of entitlements.  But more complex rules
 * may detect different combinations based on HR attributes.
 * When the SOD entitlements cannot be specified in pragmas
 * the rule may build a ViolationDetails object containing the
 * relevant entitlements.
 * 
 * The class is fairly specific to SOD policies, but it could
 * be extended to hold details about other policies.  I'd like
 * to avoid too many "detail" models.  Just defining
 * standard names for things in the PolicyViolation._arguments
 * map goes a long way, ViolationDetails would only be necessary
 * if he detail structure was complex.
 *
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;


/**
 * A class holding optional information about a PolicyViolation.
 * These can be created by the PolicyExecutors, saved in the 
 * PolicyViolation attributes map, then used when rendering
 * information about the violation. Currently the main consumer
 * of this is sailpoint.api.PolicyUtil.
 */
@XMLClass
public class ViolationDetails extends AbstractXmlObject 
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // 
    // SOD Items
    //

    /**
     * Items related to the left side of an SOD policy.
     */
    List<IdentityItem> _left;

    /**
     * Items related to the right side of an SOD policy.
     */
    List<IdentityItem> _right;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ViolationDetails() {
    }

    @XMLProperty(mode=SerializationMode.LIST, xmlname="LeftItems")
    public List<IdentityItem> getLeft() {
        return _left;
    }

    public void setLeft(List<IdentityItem> items) {
        _left = items;
    }

    @XMLProperty(mode=SerializationMode.LIST, xmlname="RightItems")
    public List<IdentityItem> getRight() {
        return _right;
    }

    public void setRight(List<IdentityItem> items) {
        _right = items;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void addLeft(List<IdentityItem> items) {
        if (items != null) {
            for (IdentityItem item : items)
                addLeft(item);
        }
    }

    public void addLeft(IdentityItem item) {
        if (_left == null)
            _left = new ArrayList<IdentityItem>();
        assimilate(_left, item);
    }

    public void addRight(List<IdentityItem> items) {
        if (items != null) {
            for (IdentityItem item : items)
                addRight(item);
        }
    }

    public void addRight(IdentityItem item) {
        if (_right == null)
            _right = new ArrayList<IdentityItem>();
        assimilate(_right, item);
    }

    /**
     * Add a new item, filtering duplicates and merging where possible.
     */
    private void assimilate(List<IdentityItem> items, IdentityItem neu) {

        if (neu != null) {
            boolean assimilated = false;
            for (IdentityItem item : items) {
                if (item.isEqual(neu)) {
                    item.assimilate(neu.getValue());
                    assimilated = true;
                    break;
                }
            }
        
            if (!assimilated)
                items.add(neu);
        }
    }

}
