/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

/**
 * Marker interface for any object that can be certified.  
 *
 * @ignore
 * We could add a method
 * such as generateCertificationItem() that requires all Certifiables to
 * generate their own items, but this may require more smarts than we currently
 * have in the object package.  For example, CertificationItems that are
 * created for Bundles include both the bundle name and the EntitlementSnapshots
 * that grant the business role.  Calculating this list of EntitlementSnapshots
 * would require access to the EntitlementCorrelator.
 *
 */
public interface Certifiable {

}
