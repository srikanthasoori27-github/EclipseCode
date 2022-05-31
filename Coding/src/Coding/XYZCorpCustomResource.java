

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;

@Path("XYZCustom")
public class XYZCorpCustomResource extends BaseResource{

    @GET
    @Path("custom/{param1}")
    public String getCustomString(@PathParam("param1") String param1)
        throws GeneralException {
        return param1;
    }
}