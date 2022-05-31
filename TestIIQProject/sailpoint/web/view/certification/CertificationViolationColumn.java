/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.api.ViolationDetailer;
import sailpoint.object.PolicyViolation;
import sailpoint.tools.GeneralException;
import sailpoint.web.view.ViewEvaluationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationViolationColumn extends CertificationItemColumn {

    protected final static String COL_VIOLATION = "policyViolation";
    protected final static String CACHE_VIOLATION = "violation";


    @Override
    public List<String> getProjectionColumns() throws GeneralException{
        return Arrays.asList(COL_VIOLATION);
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException{

        PolicyViolation violation = (PolicyViolation)row.get(COL_VIOLATION);
        Object val = null;
        if (violation != null){
            ViolationDetailer view = getViewDetails(getContext(), violation);
            String violationProperty =
                    getColumnConfig().getProperty().substring(getColumnConfig().getProperty().indexOf(".") + 1,
                            getColumnConfig().getProperty().length());
            val = evaluate(view, violationProperty);
        }

        return val;
    }

    private ViolationDetailer getViewDetails(ViewEvaluationContext context, PolicyViolation violation) throws GeneralException {
        if (context.getRowAttributes().containsKey(CACHE_VIOLATION)){
            return (ViolationDetailer)context.getRowAttributes().get(CACHE_VIOLATION);
        }

        ViolationDetailer violationDetails= new ViolationDetailer(context.getSailPointContext(), violation, getLocale(), getTimeZone());
        context.getRowAttributes().put(CACHE_VIOLATION, violationDetails);
        return violationDetails;
    }
}
