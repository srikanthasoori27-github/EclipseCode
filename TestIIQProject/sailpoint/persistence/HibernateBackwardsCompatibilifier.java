/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.persistence;

import org.hibernate.hql.internal.ast.tree.DotNode;

/**
 * A spring bean that sets up super secret hibernate options so that our product
 * will still work after upgrading to a newer version of hibernate.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class HibernateBackwardsCompatibilifier {

    /**
     * Constructor - sets the options.
     */
    public HibernateBackwardsCompatibilifier() {

        // See bug 6613.  In HHH-2257, hibernate changed behavior so that
        // putting entity references in an HQL select clause causes their
        // tables to be joined.  There are a couple of problems with this:
        //
        //  1) This currently produces SQL that causes MySQL to barf.
        //  2) Even if the SQL worked, inner joining the referenced tables
        //     would cause the result set to be severely restricted if the
        //     referenced entities are null (this was surprisinly the intended
        //     behavior in HHH-2257).
        //
        // There are other ways around this problem, but none of them are
        // particularly appealing.  For now, we will use this option, which
        // according to the hibernate code was just introduced for backwards
        // compatibility testing in unit tests.
        DotNode.regressionStyleJoinSuppression = true;
    }
}
