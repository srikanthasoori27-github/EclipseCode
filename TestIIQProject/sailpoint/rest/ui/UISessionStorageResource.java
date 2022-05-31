package sailpoint.rest.ui;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.rest.BaseResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.service.SessionStorage;
import sailpoint.service.UISessionStorageService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import java.util.Map;

/**
 * Rest resource for storing and accessing UI related http session stored values.
 * Intended for persisting UI settings like, filters and sort settings.
 */
@Path("sessionStorage")
public class UISessionStorageResource extends BaseResource {

    /**
     * Get a session stored value by key.
     * Returns empty map if key is empty or value does not exist for the key.
     *
     * @param key String key to access session storage
     * @return ObjectResult result with session stored value
     * @throws GeneralException
     */
    @GET
    public ObjectResult getSessionStorageValue(@QueryParam("key") String key) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        Object sessionStorageValue = null;

        UISessionStorageService uiSessionStorageService = new UISessionStorageService(getSessionStorage());

        if (Util.isNotNullOrEmpty(key)) {
            sessionStorageValue = uiSessionStorageService.get(key);
        }

        ObjectResult result = new ObjectResult(sessionStorageValue);
        result.setStatus(RequestResult.STATUS_SUCCESS);

        return result;
    }

    /**
     * Set session stored values by key. The key/value pairs in the map will be stored in the session.
     *
     * @param values Map<String, Object> the values to set in session storage
     * @return SuccessResult result object
     * @throws GeneralException
     */
    @POST
    public SuccessResult setSessionStorageValue(Map<String, Object> values) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        UISessionStorageService uiSessionStorageService = new UISessionStorageService(getSessionStorage());
        uiSessionStorageService.put(values);
        return new SuccessResult(true);
    }

    /**
     * Gets the session storage.
     * @return SessionStorage The session storage.
     */
    private SessionStorage getSessionStorage() {
        return new HttpSessionStorage(getSession());
    }
}
