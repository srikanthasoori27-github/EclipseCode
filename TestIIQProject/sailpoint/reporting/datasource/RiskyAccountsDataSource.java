/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Link;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorer;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class RiskyAccountsDataSource extends ProjectionDataSource implements JavaDataSource{

    private static final Log log = LogFactory.getLog(RiskyAccountsDataSource.class);

    List<Scorer> scorers;
    private String currentRowIssues = null;

    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
                           String groupBy, List<Sort> sort) throws GeneralException {
        super.setTimezone((TimeZone)arguments.get(JRParameter.REPORT_TIME_ZONE));
        super.setLocale((Locale) arguments.get(JRParameter.REPORT_LOCALE));

        ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
		List<ScoreDefinition> scores = config.getApplicationScores();
		if (scores!=null) {
			for (ScoreDefinition score : scores) {
				if ( !score.isDisabled() ) {
					Scorer ins = score.getScorerInstance();
					ins.prepare(getContext(), config, score, null);
					if (scorers == null)
						scorers = new ArrayList<Scorer>();
					scorers.add(ins);
				}
			}
		}

        ReportHelper helper = new ReportHelper(context, getLocale(), getTimezone());
        QueryOptions ops = helper.getFilterQueryOps(report, arguments);
        init(Link.class, ops, report.getGridColumns(), getLocale(), getTimezone());
    }

    @Override
    public boolean next() throws JRException {

        currentRowIssues = null;
        while(currentRowIssues == null && super.next()){
            try {
                String id = (String)currentRow.get("issues");
                Link link = getContext().getObjectById(Link.class, id);

                List<ScoreItem> securityRisks = getSecurityRisk(link);
                List<String> issues = new ArrayList<String>();
                if (securityRisks != null && !securityRisks.isEmpty()){
                    for(int i=0; i<securityRisks.size(); i++) {
                        issues.add(securityRisks.get(i).getTargetMessage().getLocalizedMessage(getLocale(),
                                getTimezone()));
                    }

                    // This will delimit the issue list with localized delimiter
                    Message issueMsg = new Message(MessageKeys.MSG_PLAIN_TEXT, issues);
                    currentRowIssues = issueMsg.getLocalizedMessage(getLocale(), getTimezone());
                }

                getContext().decache();

            } catch (GeneralException e) {
                throw new JRException(e);
            }
        }

        return currentRowIssues != null;
    }

    @Override
    public Object getFieldValue(String fieldName) throws GeneralException {

        if ("issues".equals(fieldName)){
            return currentRowIssues;
        } else {
            return super.getFieldValue(fieldName);
        }
    }

    public void setLimit(int startPage, int pageSize) {
        // not supported
    }

    private List<ScoreItem> getSecurityRisk(Link link) {

        List<ScoreItem> securityRisks = new ArrayList<ScoreItem>();
		ScoreItem securityRisk = null;

		if(link.getIdentity()!=null) {
			if(link.getIdentity().isInactive()) {
				securityRisk = new ScoreItem();
				securityRisk.setTargetMessage(new Message(MessageKeys.IDENTITY_APP_RISK_DS_INACTIVE_ID));
				securityRisks.add(securityRisk);
			}
		}

		if(scorers!=null) {
			for(Scorer scorer : scorers) {
				try {
					ScoreItem item = scorer.isMatch(link);
					if(item!=null) {
						securityRisks.add(item);
					}
				} catch (GeneralException ge) {
					log.warn("Exception encountered while processing isMatch. Exception: " + ge.getMessage());
				}
			}
		}

		return securityRisks;
	}

}
