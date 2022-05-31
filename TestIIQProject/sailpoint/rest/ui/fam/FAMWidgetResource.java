/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.fam;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.fam.FAMService;
import sailpoint.object.SPRight;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.tools.GeneralException;

@Path(Paths.FAM_WIDGET)
public class FAMWidgetResource extends BaseResource {

    /**
     * Constructor
     */
    public FAMWidgetResource() {
        
    }

    public FAMWidgetResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Call the API to get the sensitive resources data from FAM server.
     * @return map of Attributes
     * @throws GeneralException when bad things happen
     */
    @GET 
    @Path(Paths.FAMWidget.SENSITIVE_DATA)
    public Map<String, Object> getSensitiveData() throws GeneralException {
        authorize(new RightAuthorizer(SPRight.ViewFAMAdminWidgets));

        FAMService famService = new FAMService(getContext());
        return famService.getWidgetService().getSensitiveDataExposure();
    }

   /** 
    * Call the API to get the sensitive resources data from FAM server.
    * @return map of Attributes
    * @throws GeneralException when bad things happen
    */
   @GET 
   @Path(Paths.FAMWidget.SENSITIVE_RESOURCE)
   public Map<String, Object> getSensitiveResource() throws GeneralException {
       authorize(new RightAuthorizer(SPRight.ViewFAMAdminWidgets));

       FAMService famService = new FAMService(getContext());
       return famService.getWidgetService().getSensitiveResources();
   }
}
