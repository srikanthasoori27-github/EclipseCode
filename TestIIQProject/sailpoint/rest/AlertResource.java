/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.Alert;
import sailpoint.object.SPRight;
import sailpoint.service.alert.AlertDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;

import javax.ws.rs.GET;

/**
 * Created by ryan.pickens on 8/15/16.
 */
public class AlertResource extends BaseResource {

    private String _alertId;

    public AlertResource(String alertId, BaseResource parent) {
        super(parent);
        _alertId = alertId;
    }

    @GET
    public AlertDTO getAlert() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewAlert));

        Alert alert = getContext().getObjectById(Alert.class, _alertId);
        if (alert == null) {
            throw new ObjectNotFoundException(Alert.class, _alertId);
        }

        return new AlertDTO(alert, this);
    }
}
