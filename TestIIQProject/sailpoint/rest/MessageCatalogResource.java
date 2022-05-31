package sailpoint.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * REST Resource to fetch entries from the Message Catalog.
 * 
 * @author ryan.pickens
 *
 */
@Path("messageCatalog")
public class MessageCatalogResource extends BaseResource {

    
    /**
     * Returns the localized version of a catalog entry
     */
    @GET
    @Path("{catKey}")
    public String getLocalizedString_pathParam(@PathParam("catKey") String catalog_key) throws GeneralException {
        return getLocalizedString(catalog_key);
    }

    /**
     * Returns the localized version of a catalog entry
     */
    @GET
    public String getLocalizedString_queryParam(@QueryParam("catKey") String catalog_key) throws GeneralException {
        return getLocalizedString(catalog_key);
    }

    private String getLocalizedString(String catalog_key) throws GeneralException {

        //TODO-Implement more meaningful authorizer
        authorize(new AllowAllAuthorizer());
        Message m = new Message(catalog_key);
        return m.getLocalizedMessage(getLocale(), getUserTimeZone());

    }

}
