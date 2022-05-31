/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.view.certification;

import java.util.List;
import java.util.Map;

import sailpoint.object.CertificationAction;
import sailpoint.tools.GeneralException;

/**
 * Column evaluator to get the column value for the cert item current decision state.
 * Used by cert export. Note that this column evaluator doesn't display the 'Cleared' status value because we don't
 * want to show that in the CSV export file.
 *
 * @author patrick.jeong
 */
public class CertificationItemCurrentDecisionStateColumn extends CertificationItemColumn {

    protected final static String COL_ACTION = "action";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        CertificationAction action = (CertificationAction)row.get(COL_ACTION);
        String currentDecisionState = "";
        // don't show cleared status
        if (action != null && action.getStatus() != CertificationAction.Status.Cleared) {
            // if action is revoke account the status will be Remediated instead of RevokeAccount so we need to
            // do some adjusting here based on the revoke_account flag.
            if (action.isRevokeAccount()) {
                currentDecisionState = CertificationAction.Status.RevokeAccount.getLocalizedMessage(getLocale(), getTimeZone());
            }
            else {
                currentDecisionState = action.getStatus().getLocalizedMessage(getLocale(), getTimeZone());
            }
        }
        return currentDecisionState;
    }
}
