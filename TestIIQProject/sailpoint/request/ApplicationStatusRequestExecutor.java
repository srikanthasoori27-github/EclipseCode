/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.request;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.Server;
import sailpoint.service.StatisticsService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Obtain the status for a given Application on a specific Host
 */
public class ApplicationStatusRequestExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(ApplicationStatusRequestExecutor.class);


    public static final String DEFINITION_NAME = "Application Status Request";

    public static final String REQUEST_NAME = "Application Status Request for App[%s] Host[%s]";

    public static final String ARG_APP_NAME = "applicationName";

    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> args) throws RequestPermanentException, RequestTemporaryException {

        String appName = args.getString(ARG_APP_NAME);

        if (Util.isNullOrEmpty(appName)) {
            throw new RequestPermanentException("Application Name is required");
        }

        try {
            Server serv = context.getObjectByName(Server.class, Util.getHostName());

            if (serv != null) {

                Map appStatuses = Util.otom(serv.get(Server.ATT_APPLICATION_STATUS));
                if (appStatuses == null) {
                    appStatuses = new HashMap<String, Object>();
                }

                StatisticsService svc = new StatisticsService(context);
                Map appStat = svc.getAppStatus(appName);

                appStatuses.put(appName, appStat);

                serv.put(Server.ATT_APPLICATION_STATUS, appStatuses);

                context.saveObject(serv);
                context.commitTransaction();

            }

        } catch (GeneralException ge) {
            log.error("Error getting ApplicationStatus for application[" + appName + "] on Host["
                + Util.getHostName() + "]");
        }

    }
}
