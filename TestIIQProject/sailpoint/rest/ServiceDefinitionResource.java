/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.object.SPRight;
import sailpoint.object.ServiceDefinition;
import sailpoint.rest.jaxrs.PATCH;
import sailpoint.service.ServiceDefinitionDTO;
import sailpoint.service.ServiceDefinitionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.Map;

@Path("serviceDefinitions")
public class ServiceDefinitionResource extends BaseResource {

        @GET
        @Path("{name}")
        public ServiceDefinitionDTO getServiceDefinition(@PathParam("name") String name)
                throws GeneralException {

                authorize(new RightAuthorizer(SPRight.ViewEnvironmentMonitoring, SPRight.FullAccessEnvironmentMonitoring));

                ServiceDefinition def = getContext().getObjectByName(ServiceDefinition.class, name);
                if (def == null) {
                        throw new ObjectNotFoundException(ServiceDefinition.class, name);
                }

                return new ServiceDefinitionDTO(def);
        }

        @PATCH
        @Path("{name}")
        public void patchServiceDefinition(@PathParam("name") String serviceDefName, Map<String, Object> values)
                throws GeneralException {

                authorize(new RightAuthorizer(SPRight.FullAccessEnvironmentMonitoring));

                if (Util.isEmpty(values)) {
                        throw new InvalidParameterException("values");
                }

                ServiceDefinitionService svc = new ServiceDefinitionService(serviceDefName, this);
                svc.patch(values);
        }

}
