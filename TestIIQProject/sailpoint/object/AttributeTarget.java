/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Part of the ObjectAttribute model.  Used for attribute synchronization
 * to push attribute values to other target applications.  In practice
 * these are used only for Identity attributes.
 * 
 * These must contain at least an application and attribute name to specify
 * where the value will be pushed.  Additionally, a rule can be specified to
 * transform the value being pushed to the target.
 * 
 * When an identity has multiple accounts on the same application, the
 * <code>provisionAllAccounts</code> flag indicates whether to push the value
 * to all accounts or require account selection.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@XMLClass @SuppressWarnings("serial")
public class AttributeTarget extends AttributeSource {

    private boolean provisionAllAccounts;
    
    
    /**
     * Default constructor.
     */
    public AttributeTarget() {
        super();
    }

    /**
     * Return whether to provision all accounts if an identity has multiple
     * accounts on the target application.  When false the desired accounts
     * must be manually selected.
     */
    @XMLProperty
    public boolean isProvisionAllAccounts() {
        return this.provisionAllAccounts;
    }

    public void setProvisionAllAccounts(boolean provisionAll) {
        this.provisionAllAccounts = provisionAll;
    }
}
