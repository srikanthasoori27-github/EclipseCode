package sailpoint.reporting;

import java.util.List;

import net.sf.jasperreports.engine.JasperReport;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.reporting.datasource.ChallengeQuestionStatusReportDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

public class ChallengeQuestionStatusReport extends UserReport {

	private static final String REQUIRED_NUMBER_OF_ANSWERS = "requiredNumberOfAnswers";
	@Override
	public TopLevelDataSource getDataSource() throws GeneralException {
        Attributes<String,Object> args = getInputs();
        SailPointContext ctx = getContext();
        List<Filter> filters = buildFilters(args);
        /** Need to handle extended attributes and create filters for them **/
        ObjectConfig conf = ctx.getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);
        List<ObjectAttribute> attributes = conf.getExtendedAttributeList();
        if(attributes!=null) {
            for(ObjectAttribute attr : attributes) {
                addLikeFilter(filters, args, attr.getName(), attr.getName(), null);
            }
        }
        return new ChallengeQuestionStatusReportDataSource(filters, getLocale(), getTimeZone(), args);
	}

	@Override
	public void preFill(SailPointContext ctx, Attributes<String, Object> args,
			JasperReport report) throws GeneralException {
		super.preFill(ctx, args, report);
		getInputs().put( REQUIRED_NUMBER_OF_ANSWERS, getRequiredAuthenticationAnswers() );
	}
	
	private int getRequiredAuthenticationAnswers() {
		int response = 0;
		try {
			response = getContext().getConfiguration().getInt( Configuration.NUM_AUTH_QUESTION_ANSWERS_REQUIRED );
		} catch (GeneralException e) {
			/* Zero is probably an acceptable default if the above throws an exception */
		}
		return response;
	}
}
