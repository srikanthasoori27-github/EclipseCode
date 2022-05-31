/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.view.certification;

import java.util.List;
import java.util.Map;

import sailpoint.object.CertificationAction;
import sailpoint.tools.GeneralException;

/**
 * Column evaluator to get the column value for the cert item current decision comment. Used by cert export
 *
 * @author patrick.jeong
 */
public class CertificationItemCurrentDecisionCommentColumn extends CertificationItemColumn {
    protected final static String COL_ACTION = "action";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        CertificationAction action = (CertificationAction)row.get(COL_ACTION);
        String currentDecisionComment = "";
        if (action != null) {
            currentDecisionComment = action.getComments();
        }
        return currentDecisionComment;
    }
}