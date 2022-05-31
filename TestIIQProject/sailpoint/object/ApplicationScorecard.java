/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding various scores and statistics calculated
 * from application accounts.  This is maintained for an Application
 * similar to the way a Scorecard is maintained for an Identity.
 *
 * Author: Jeff
 *
 * This differs from an identity Scorecard in that it is calculated from
 * the scores from Links for a particular application rather than
 * from the consolidated identity cube.  The scores are also less
 * oriented toward entitlements and policy in the IdentityIQ business
 * model, and more on the number of accounts that have certain
 * qualities such as system, dormant, privileged, etc.
 *
 * Like identity scores, there are a number of application component
 * scores that are combined into a composite score.  Application
 * component scores are expected to be customized more often
 * than identity component scores because they will be sensitive to
 * account attributes that can vary for each deployment.  
 *
 * Because application scores are much more extensible, the
 * scorecard model will be more generic than that for identity scores.
 *
 * Component scores will be contained in a Map rather than first-class
 * properties.  This allows them to be added at any time, but also 
 * means they cannot be directly searchable.  This needs further thought.
 * The alternative is to use ExtensibleSailPointObject and map however
 * many columns are needed but this makes the configuration a little harder.
 *
 * Non searchability is less of an issue for application scores because
 * there are very few applications compared to identities.  That makes
 * it feasible to bring all of the application scorecards into memory
 * for analysis, SQL is not needed to do the work.
 *
 */

package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Map;


import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;

/**
 * A class holding various scores and statistics calculated
 * from application accounts.
 */
@XMLClass
public class ApplicationScorecard extends GenericIndex
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    /** 
     * The name of a statistic stored in the index map that holds
     * the number of links scanned when scoring this application.
     */
    public static final String ATT_TOTAL_LINKS = "totalLinks";

    /** 
     * The name of a statistic stored in the index map that holds
     * the number of entitlements scanned when scoring this application.
     */
    public static final String ATT_TOTAL_ENTITLEMENTS = "totalEntitlements";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Associated application.
     */
    Application _application;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public ApplicationScorecard() {
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitApplicationScorecard(this);
    }

    // Note that this is not serialized in the XML, it is set
    // known containment in an <Identity> element.  This is only
    // here for a Hibernate inverse relationship.  This does however
    // mean that you cannot checkout and edit Scorecard objects individually.

    public Application getApplication() {
        return _application;
    }

    public void setApplication(Application app) {
        _application = app;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Store the link total in the attribute map.
     * @ignore
     * This should be a first-class field but it is 
	 * difficult to change the Hibernate schema.
     */
    public void setTotalLinks(int i) {
        setScore(ATT_TOTAL_LINKS, i);
    }

    public int getTotalLinks() {
        return getScore(ATT_TOTAL_LINKS);
    }
    
    /**
     * Store the entitlement total in the attribute map.
     */
    public void setTotalEntitlements(int i) {
        setScore(ATT_TOTAL_ENTITLEMENTS, i);
    }

    public int getTotalEntitlements() {
        return getScore(ATT_TOTAL_ENTITLEMENTS);
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("application", "Application");
        cols.put("created", "Created");
        cols.put("compositeScore", "Score");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %-20s %s\n";
    }

    @Override
    public boolean hasName() {
        return false;
    }
}
