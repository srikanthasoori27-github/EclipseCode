/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.service.RiskService;
import sailpoint.service.RiskService.RiskyThing;
import sailpoint.tools.GeneralException;

/**
 * Rest resource for managing risk data.
 *
 * @author patrick.jeong
 */
@Path("risk")
public class RiskResource extends BaseResource {

    /**
     * Get data for top five risky apps and/or identities
     *
     * @return list of risky apps and/or identities
     * @throws GeneralException
     */
    @GET
    @Path("widgets/topFive")
    public Map<String, List<RiskyThing>> getTopFive() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewRiskScoreChart, SPRight.ViewApplicationRiskScoreChart));
        return new RiskService(this).getTopFive();
    }

}
