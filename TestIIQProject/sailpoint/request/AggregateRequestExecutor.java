/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.request;

import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.RequestResult;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.service.ServiceHandler;
import sailpoint.tools.GeneralException;

/**
 * This request executor is used to retry aggregation service requests that
 * failed because identity is locked.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class AggregateRequestExecutor extends AbstractRequestExecutor {

    public static final String DEF_NAME = "Aggregate Request";
    
    public static final String ARG_IDENTITY_NAME = "identityName";
    public static final String ARG_APP_NAME = "applicationName";
    public static final String ARG_RESOURCE_OBJECT = "resourceObject";
    public static final String ARG_AGG_OPTIONS= "aggregationOptions";
    

    /**
     * Attempt to aggregate using the service code.  If the identity is still
     * locked this will throw a temporary exception so we will retry it.  If
     * there is any other type of failure this throws a permanent exception.
     */
    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        // Grab the arguments and verify that we have everything we need.
        String identityName = args.getString(ARG_IDENTITY_NAME);
        String appName = args.getString(ARG_APP_NAME);
        Map<String,Object> resourceObj =
            (Map<String,Object>) args.get(ARG_RESOURCE_OBJECT);
        Map<String,Object> aggOptions =
            (Map<String,Object>) args.get(ARG_AGG_OPTIONS);

        if ((null == identityName) || (null == appName) || (null == resourceObj)) {
            throw new RequestPermanentException("Identity name, app name, and resource object are required: " + args);
        }

        Map<String,Object> result = null;
        ServiceHandler handler = new ServiceHandler(context);
        try {
            result = handler.aggregate(identityName, appName, resourceObj, aggOptions, true);
        }
        catch (GeneralException e) {
            throw new RequestPermanentException(e);
        }

        // If locked, throw a temporary exception so we will retry.
        if (null != result) {
            RequestResult reqResult = new RequestResult(result);
            if (reqResult.isRetry()) {
                throw new RequestTemporaryException("Identity '" + identityName + "' locked.  Will attempt retry.");
            }
            else if (reqResult.isFailure()) {
                throw new RequestPermanentException("Aggregate service failed with: " + result);
            }
        }
    }
}
