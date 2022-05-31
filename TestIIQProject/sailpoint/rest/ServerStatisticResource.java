/*
 *  (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.object.ServerStatistic;
import sailpoint.service.ServerStatisticDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;

import javax.ws.rs.GET;

public class ServerStatisticResource extends BaseResource {

    private String _serverStatisticId;

    public ServerStatisticResource(String serverStatisticId, BaseResource parent) {
        super(parent);
        _serverStatisticId = serverStatisticId;
    }

    @GET
    public ServerStatisticDTO getServerStatistic() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

        ServerStatistic serverStatistic = getContext().getObjectById(ServerStatistic.class, _serverStatisticId);
        if (serverStatistic == null) {
            throw new ObjectNotFoundException(ServerStatistic.class, _serverStatisticId);
        }

        return new ServerStatisticDTO(serverStatistic, this);
    }
}
