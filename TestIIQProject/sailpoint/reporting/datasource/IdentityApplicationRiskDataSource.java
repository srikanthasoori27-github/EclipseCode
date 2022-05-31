/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class IdentityApplicationRiskDataSource extends
UncorrelatedIdentityDataSource {

	private static final Log log = LogFactory.getLog(UncorrelatedIdentityDataSource.class);

	List<Map<String,Object>> riskyIdentities;
	List<Scorer> scorers;

	public IdentityApplicationRiskDataSource(List<Filter> filters, Locale locale, TimeZone timezone, 
			Attributes<String,Object> args) {
		super(filters, locale, timezone, args);
	}

	@Override
	public void internalPrepare() throws GeneralException {
		super.internalPrepare();
		ScoreConfig config = 
			(ScoreConfig)getContext().getObjectByName(ScoreConfig.class, 
					"ScoreConfig");
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
	}

	public Object getFieldValue(JRField jrField) throws JRException {
		Object returnValue = super.getFieldValue(jrField);

		if(returnValue ==null) {
			String fieldName = jrField.getName();

			if(fieldName.equals("riskyIdentities")) {
				returnValue = getRiskyIdentities();;
			}
		}
		return returnValue;
	}

	public List<Map<String,Object>> getRiskyIdentities() {
		try {
			riskyIdentities = new ArrayList<Map<String, Object>>();

			QueryOptions qo = new QueryOptions();
			qo.add(Filter.eq("application.id", _object.getId()));

			List<Link> links = getContext().getObjects(Link.class, qo);
			if(links!=null && !links.isEmpty()) {
				for(Link link : links) {
					String recommendedAction = null;
					List<ScoreItem> securityRisks = getSecurityRisk(link, recommendedAction);
					if(securityRisks!=null && !securityRisks.isEmpty()) {
						Map<String, Object> map = new HashMap<String, Object>();
						map.put("appName", link.getApplicationName());
						map.put("username", link.getIdentity().getName());
						map.put("firstname", link.getIdentity().getLastname());
						map.put("lastname", link.getIdentity().getFirstname());

                        List<String> issues = new ArrayList<String>();
                        for(int i=0; i<securityRisks.size(); i++) {
							issues.add(securityRisks.get(i).getTargetMessage().getLocalizedMessage(getLocale(),
                                    getTimezone()));
						}

                        // This will delimit the issue list with localized delimiter
                        Message issueMsg = new Message(MessageKeys.MSG_PLAIN_TEXT, issues);
                        map.put("issue", issueMsg.getLocalizedMessage(getLocale(), getTimezone()));

						riskyIdentities.add(map);
					}
					getContext().decache(link);
				}
			}
		}catch (GeneralException ge) {
			log.error("Unable to load links for the application. Exception: " + ge.getMessage());
			riskyIdentities = null;
		}
		return riskyIdentities;
	}

	private List<ScoreItem> getSecurityRisk(Link link, String recommendedAction) {
		List<ScoreItem> securityRisks = new ArrayList<ScoreItem>();
		ScoreItem securityRisk = null;
		/** Handle Inactive/Orphan Accounts **/

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
