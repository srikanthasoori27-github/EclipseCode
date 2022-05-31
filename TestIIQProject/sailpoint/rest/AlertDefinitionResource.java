/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.AlertDefinition;
import sailpoint.object.SPRight;
import sailpoint.service.alert.AlertDefinitionDTO;
import sailpoint.service.alert.AlertDefinitionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.ObjectNotFoundException;

/**
 * Created by ryan.pickens on 9/6/16.
 */
public class AlertDefinitionResource extends BaseResource {

    private static Log _log = LogFactory.getLog(AlertDefinitionListResource.class);

    private String _alertDefId;

    public AlertDefinitionResource(String alertDefId, BaseResource parent) {
        super(parent);
        _alertDefId = alertDefId;
    }

    @GET
    public AlertDefinitionDTO getAlertDefinition() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessAlertDefinition, SPRight.ViewAlertDefinition));

        AlertDefinition def = getContext().getObjectById(AlertDefinition.class, _alertDefId);
        if (def == null) {
            throw new ObjectNotFoundException(AlertDefinition.class, _alertDefId);
        }

        return new AlertDefinitionDTO(def, this);
    }

    @PUT
    public void updateAlertDefinition(Map<String, Object> data) throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessAlertDefinition));

        try {
            String json = JsonHelper.toJson(data);
            AlertDefinitionDTO alertDefDto = JsonHelper.fromJson(AlertDefinitionDTO.class, json);
            AlertDefinitionService svc = getService();
            svc.updateAlertDefinition(alertDefDto);

        } catch(Exception e) {
            _log.warn("Unable to Update AlertDefinition" + data, e);
            throw e;
        }
    }

    @DELETE
    public Response delete() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.FullAccessAlertDefinition));

        AlertDefinition def = getContext().getObjectById(AlertDefinition.class, _alertDefId);
        if (def == null) {
            throw new ObjectNotFoundException(AlertDefinition.class, _alertDefId);
        }
        AlertDefinitionService svc = getService();

        svc.deleteAlertDefinition(def);

        // If we get this far without throwing, return OK
        return Response.ok().build();
    }

    protected AlertDefinitionService getService() {
        return new AlertDefinitionService(this);
    }

}
