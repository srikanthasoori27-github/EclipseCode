/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class holding scores and statistics for one Identity.
 *
 * Author: Jeff
 * 
 * The score model has been refactored several times and is now
 * a more general "index" that may contain things other than scores,
 * but we've always called this a Scorecard so we'll leave it that
 * way to avoid an ugly schema migration.
 *
 */

package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Map;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.GeneralException;

/**
 * A class holding scores and statistics for one Identity.
 *
 */
@XMLClass
public class Scorecard extends BaseIdentityIndex
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Owning Identity.
     */
    // !! should be using _owner field
    Identity _identity;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public Scorecard() {
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitScorecard(this);
    }

    // Note that this is not serialized in the XML, it is set
    // known containment in an <Identity> element.  This is only
    // here for a Hibernate inverse relationship.  This does however
    // mean that you cannot checkout and edit Scorecard objects individually.

    /**
     * The identity that owns this scorecard.
     */
    public Identity getIdentity() {
        return _identity; 
   }

    public void setIdentity(Identity id) {
        _identity = id;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("identity", "Identity");
        cols.put("created", "Created");
        cols.put("compositeScore", "Score");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %-30s %s\n";
    }

}
