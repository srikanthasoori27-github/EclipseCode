/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ApplicationScorecard;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class ApplicationScorecardBean extends BaseObjectBean<ApplicationScorecard> {
	private static Log log = LogFactory.getLog(ApplicationScorecardBean.class);

	/** The application id passed to this bean to fetch the scorecard for **/
	private String applicationId;
	/**
	 * 
	 */
	public ApplicationScorecardBean() {
		super();
		setScope(ApplicationScorecard.class);
	}

	public String refreshScorecardObject() {
		return "";
	}

	@SuppressWarnings("unchecked")
	public ApplicationScorecard getObject() {
		ApplicationScorecard scorecard = null;
		try {
			 scorecard = super.getObject();
			/** If we can't find the scorecard but have the application id, let's try to load it by 
			 * application id.
			 */
			if(scorecard==null && applicationId!=null && !applicationId.equals("")) {
				QueryOptions qo = new QueryOptions();
				qo.add(Filter.eq("application.id", applicationId));

				/** Only get the latest scorecard **/
				qo.setResultLimit(1);
				qo.setOrderBy("created");
				qo.setOrderAscending(false);

				List<ApplicationScorecard> scorecards = getContext().getObjects(ApplicationScorecard.class, qo);
				if(scorecards!=null && scorecards.size()>0)
					scorecard = scorecards.get(0);
			}
		} catch (GeneralException ge) {
		    if (log.isInfoEnabled())
		        log.info("Unable to getObject due to exception :" + ge.getMessage(), ge);
		}
		return scorecard;

	}
	public String getApplicationId() {
		return applicationId;
	}
	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

}
