/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.quicklink;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.QuickLinkLaunchAuthorizer;
import sailpoint.integration.Util;
import sailpoint.object.QuickLink;
import sailpoint.object.SailPointObject;
import sailpoint.rest.BaseResource;
import sailpoint.service.quicklink.QuickLinkLauncher;
import sailpoint.service.quicklink.QuickLinkLauncher.QuickLinkLaunchResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.URLUtil;
import sailpoint.web.QuickLinkDTO;

/**
 * A REST sub-resource to manage a single QuickLink.
 */
public class QuickLinkResource extends BaseResource {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A quick link launch request.
     */
    public static class LaunchRequest {

        public static final String ATT_SELF_SERVICE = "selfService";

        private boolean selfService;

        /**
         * Constructor.
         *
         * @param  map  The map that contains the request body.
         */
        public LaunchRequest(Map<String,Object> map) {
            if (null != map) {
                this.selfService = Util.getBoolean(map, ATT_SELF_SERVICE);
            }
        }

        /**
         * Return whether this is a self service request or not.
         */
        public boolean isSelfService() {
            return selfService;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private String quicklinkName;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     *
     * @param  parent  The parent resource.
     * @param  quicklinkName  The name of the QuickLink.
     */
    public QuickLinkResource(BaseResource parent, String quicklinkName) {
        super(parent);
        this.quicklinkName = quicklinkName;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Authorize by quicklink name and return quicklink DTO
     * @return {QuickLinkDTO} return QuickLinkDTO
     */
    @GET
    public QuickLinkDTO getQuickLinkAndAuthorize() throws GeneralException {
        QuickLink ql = getQuickLink();
        authorizeByQuickLink();
        QuickLinkDTO quickLinkDTO = new QuickLinkDTO(ql, getLoggedInUserDynamicScopeNames());
        return quickLinkDTO;
    }  

    /**
     * Authorized by quicklink, return identities resource
     * 
     * @return IdentityListResource passing the quickLinkDTO explicitly
     * @throws GeneralException
     */
    @Path("identities")   
    public IdentitiesResource getIdentities() throws GeneralException {  
        QuickLinkDTO quickLink = getQuickLinkAndAuthorize();  
        return new IdentitiesResource(this, quickLink);  
    }
    
    /**
     * Launch this QuickLink and return a response.
     *
     * @param  request  The request body which will be marshalled into a LaunchRequest.
     *
     * @return A QuickLinkLaunchResult.
     *
     * @throws ObjectNotFoundException  If the QuickLink does not exist.
     * @throws GeneralException  For any other server errors.
     */
    @POST
    @Path("launch")
    public QuickLinkLaunchResult launch(Map<String,Object> request)
        throws ObjectNotFoundException, GeneralException {

        LaunchRequest lr = new LaunchRequest(request);
        QuickLink ql = getQuickLink();

        authorize(new QuickLinkLaunchAuthorizer(ql, lr.isSelfService()));

        // We are not supporting workflows that transition to work items yet, so pass in a fake
        // session map.  At some point we'll need to fix this up once we figure out how workflow
        // sessions will work here.
        Map<String,Object> sessionMap = new HashMap<String,Object>();

        QuickLinkLauncher launcher = new QuickLinkLauncher(getContext(), getLoggedInUser());
        QuickLinkLaunchResult result = launcher.launch(ql, !lr.isSelfService(), sessionMap);
        return scrubResult(result);
    }

    /**
     * Returns a form resource for the quicklink
     * @return A form resource for the quicklink
     */
    @Path("form")
    public QuickLinkFormResource getFormResource() throws GeneralException {
        return new QuickLinkFormResource(this.getQuickLink(), this);
    }

    /**
     * Authorize by quick link
     * @throws GeneralException
     */
    private void authorizeByQuickLink() throws GeneralException{
        authorize(new LcmActionAuthorizer(getQuickLink()));
    }

    /**
     * A QuickLinkLaunchResult contains an arguments map that could contain anything since the
     * values can come from evaluating a script.  This means they could be SailPointObjects.  We
     * don't want to send a deep-serialized JSON of these down to the client, so we will scrub
     * them prior to returning the result.  This means that any SailPointObject in the Map will
     * be represented as a Map that contains an "id" and "name" of the SailPointObject.
     *
     * @param  result  The QuickLinkLaunchResult to scrub, which may be modified.
     *
     * @return The scrubbed result.
     */
    private QuickLinkLaunchResult scrubResult(QuickLinkLaunchResult result) {
        if ((null != result) && !Util.isEmpty(result.getArguments())) {
            Map<String,Object> args = result.getArguments();
            for (String key : args.keySet()) {
                Object value = args.get(key);
                if (value instanceof SailPointObject) {
                    SailPointObject spo = (SailPointObject) value;
                    Map<String,Object> objMap = new HashMap<String,Object>();
                    objMap.put("id", spo.getId());
                    objMap.put("name", spo.getName());
                    args.put(key, objMap);
                }
            }
        }
        return result;
    }

    /**
     * Load the QuickLink for this resource, throwing an ObjectNotFoundException if null.
     */
    private QuickLink getQuickLink() throws ObjectNotFoundException, GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, URLUtil.decodeUTF8(this.quicklinkName));
        if (null == ql) {
            throw new ObjectNotFoundException(QuickLink.class, this.quicklinkName);
        }
        return ql;
    }
}
