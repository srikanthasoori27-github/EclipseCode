/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.classification;

import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

import java.util.Iterator;

public interface ClassificationFetcher {

    /**
     * Fetch all Classifications
     * @param ops
     * @return
     */
    Iterator<ClassificationResult> getClassifications(QueryOptions ops) throws GeneralException;

    /**
     * Get Classifications for a given SailPointObject
     * @param obj SailPointObject to get classifications for
     * @param ops QueryOptions to apply to classifications
     * @return
     */
    Iterator<ClassificationResult> getClassifications(SailPointObject obj, QueryOptions ops)
        throws GeneralException;

}
