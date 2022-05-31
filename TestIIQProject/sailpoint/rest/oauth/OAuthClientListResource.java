/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.oauth;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseListResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.oauth.OAuthClientDTO;
import sailpoint.service.oauth.OAuthClientService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.util.Sorter;

/**
 * This oauth client list resource has been built to retrieve oauth clients.
 * 
 * @author danny.feng
 *
 */
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.APPLICATION_JSON)
@Path("oauth2/clients")
public class OAuthClientListResource  extends BaseListResource implements BaseListServiceContext {

    private static final Log log = LogFactory.getLog(OAuthClientListResource.class);

    @Path("{nameOrId}")
    public OAuthClientResource getOAuthClientResource(@PathParam("nameOrId") String nameOrId) {
        return new OAuthClientResource(this, nameOrId);
    }

    
    @SuppressWarnings("unchecked")
    @GET  
    public ListResult list() throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessOAuthClientConfiguration, SPRight.ViewOAuthClientConfiguration));
        
        List<OAuthClientDTO> clients = getService().getClientDTOs();
        if (Util.isNotNullOrEmpty(this.getSortBy())) {
            boolean ascending = Util.nullSafeEq(Sorter.SORTER_DIRECTION_DESC, this.getSortDirection()) ? false : true;
            Collections.sort(clients, new ObjectPropertyComparator(this.getSortBy(), ascending));
        } else {
            Collections.sort(clients, new ObjectPropertyComparator(OAuthClientDTO.PARAM_CLIENT_NAME, true));
        }
        
        int toIndex = this.getStart() + this.getLimit();
        if (clients.size() < toIndex || this.getLimit() == 0) {
            toIndex = clients.size();
        }
        List<OAuthClientDTO> result = clients.subList(getStart(), toIndex);
        
        return new ListResult(result, clients.size());
    }
    
    @POST
    public Response create(Map<String,Object> data) throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessOAuthClientConfiguration));

        try {
            String json = JsonHelper.toJson(data);
            OAuthClientDTO clientDto = JsonHelper.fromJson(OAuthClientDTO.class, json);

            OAuthClientService service = getService();
            if (!service.isNameUnique(null, clientDto.getName())) {
                return Response.status(Status.CONFLICT).entity(OAuthClientService.MSG_CLIENT_NAME_NOT_UNIQUE).build();
            }
            
            OAuthClientDTO client = service.createClient(clientDto);
            if (client != null) {
                return Response.created(null).entity(client).build();
            } else {
                log.warn("Unable to update OAuthClient:" + data);
                return Response.serverError().build();
            }
        } catch(Exception e) {
            log.warn("Unable to create OAuthClient:" + data, e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
    
    private OAuthClientService getService() {
        return new OAuthClientService(this.getContext());
    }
    
    class ObjectPropertyComparator implements Comparator {

        String property;
        boolean ascending;
        
        ObjectPropertyComparator(String property, boolean ascending) {
            this.property = property;
            this.ascending = ascending;
        }
        
        /* (non-Javadoc)
         * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
         */
        @SuppressWarnings("unchecked")
        @Override
        public int compare(Object o1, Object o2) {
            int result = 0;
            Object value1 = null;
            Object value2 = null;
            try {
                value1 = ObjectUtil.objectToPropertyValue(o1, property);
                value2 = ObjectUtil.objectToPropertyValue(o2, property);
            } catch (GeneralException e) {
                result = 0;
            }
            
            if (value1 == null) {
                result = -1;
            } else if (value2 == null) {
                result = 1;
            } else if (Comparable.class.isAssignableFrom(value1.getClass()) ){
                result = ((Comparable)value1).compareTo((Comparable) value2);
            } else if (value1 instanceof IdentitySummaryDTO) {
                String displayName1 = ((IdentitySummaryDTO)value1).getDisplayName();
                String displayName2 = ((IdentitySummaryDTO)value2).getDisplayName();
                result = Util.nullSafeCompareTo(displayName1, displayName2);
            } else {
                result = 0;
            }
            
            if (!ascending) {
                result = 0 - result;
            }
            return result;
        }
        
    }

}