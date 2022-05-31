/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.groupdefinition;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.Util;
import sailpoint.object.GroupDefinition;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.service.groupdefinition.GroupDefinitionDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

/**
 * A REST resource to fetch information for a single GroupDefinition.
 */
@Path("/groupdefinition")
public class GroupDefinitionResource extends BaseResource {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Get a GroupDefinitionDTO for the specified population.  Currently accessible through the ViewRole
     * or ManageRole SPRight for role mining purposes.
     * @return {GroupDefinitionDTO} return GroupDefinitionDTO
     */
    @GET
    @Path("{name}")
    public ObjectResult getGroupDefinition(@PathParam("name") String name) throws GeneralException {
        // This is only used on the IT Role Mining page right now, so it's only available
        // through the same rights that govern role mining.  If other pages want to migrate
        // to this (and they probably will), then the rights will have to change accordingly
        authorize(new RightAuthorizer(SPRight.ViewRole, SPRight.ManageRole));

        if (Util.isNullOrEmpty(name)) {
            throw new InvalidParameterException("A name must be provided to get a GroupDefinition");
        }
        SailPointContext context = getContext();
        GroupDefinition population = getContext().getObjectByName(GroupDefinition.class, name);
        GroupDefinitionDTO groupDefinitionDTO = new GroupDefinitionDTO(population, context);
        return new ObjectResult(groupDefinitionDTO);
    }
}
