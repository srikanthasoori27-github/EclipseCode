/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.web.Authorizer;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * Service to get risk related data
 *
 * @author patrick.jeong
 */
public class RiskService {

    public static final String RISK_TYPE_APP = "riskTypeApplication";
    public static final String RISK_TYPE_IDENTITY = "riskTypeIdentity";

    private UserContext userContext;

    /**
     * Constructor
     * @param userContext
     */
    public RiskService(UserContext userContext) {
        this.userContext = userContext;
    }

    /**
     * Get a list of top five riskiest apps and/or identities depending on what rights the user has.
     *
     * @return map of list of risky things
     */
    public Map<String, List<RiskyThing>> getTopFive() throws GeneralException {
        Map<String, List<RiskyThing>> riskyThingMap = new HashMap<String, List<RiskyThing>>();

        // hasAccess also allows for sys admin
        if (Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(),
                SPRight.ViewApplicationRiskScoreChart)) {
            riskyThingMap.put(RISK_TYPE_APP, getTopFiveRiskyData(Application.class));
        }

        if (Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(), userContext.getLoggedInUserRights(),
                SPRight.ViewRiskScoreChart)) {
            riskyThingMap.put(RISK_TYPE_IDENTITY, getTopFiveRiskyData(Identity.class));
        }

        return riskyThingMap;
    }

    /**
     * Get top five risky objects sorted by compositeScore(desc) and then name(asc).
     *
     * @param cls
     * @return list of risky things data
     * @throws GeneralException
     */
    private <T extends SailPointObject> List<RiskyThing> getTopFiveRiskyData(Class<T> cls) throws GeneralException {
        // For Identity class use displayName for ordering and data property
        String nameProperty = Identity.class.getName().equals(cls.getName()) ? "displayName" : "name";

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.notnull("scorecard"));
        qo.addOrdering("scorecard.compositeScore", false);
        qo.addOrdering(nameProperty, true);
        qo.setResultLimit(5);

        List<String> props = new ArrayList<String>();
        props.add(nameProperty);
        props.add("scorecard.compositeScore");

        Iterator<Object[]> dataIterator = getContext().search(cls, qo, props);

        List<RiskyThing> riskyThingsList = new ArrayList<RiskyThing>();

        // convert them to RiskyThing objects
        while(dataIterator.hasNext()) {
            Object[] riskyData = dataIterator.next();
            riskyThingsList.add(new RiskyThing((String)riskyData[0], (Integer)riskyData[1]));
        }

        return riskyThingsList;
    }

    /**
     * Gets the context.
     *
     * @return The context.
     */
    private SailPointContext getContext() {
        return userContext.getContext();
    }

    /**
     * Class used to hold risky data
     */
    public static class RiskyThing {

        private String displayableName;
        private int score;

        public RiskyThing(String displayableName, int score) {
            this.displayableName = displayableName;
            this.score = score;
        }

        public String getDisplayableName() {
            return displayableName;
        }

        public void setDisplayableName(String displayableName) {
            this.displayableName = displayableName;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }
    }
}
