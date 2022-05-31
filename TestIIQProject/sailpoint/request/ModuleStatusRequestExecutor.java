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
 * Obtain the status for a given Module on a specific Host
 */
public class ModuleStatusRequestExecutor extends AbstractRequestExecutor {

    private static Log log = LogFactory.getLog(ModuleStatusRequestExecutor.class);


    public static final String DEFINITION_NAME = "Module Status Request";

    public static final String REQUEST_NAME = "Module Status Request for App[%s] Host[%s]";

    public static final String ARG_MODULE_NAME = "moduleName";

    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> args) throws RequestPermanentException, RequestTemporaryException {

        String moduleName = args.getString(ARG_MODULE_NAME);

        if (Util.isNullOrEmpty(moduleName)) {
            throw new RequestPermanentException("Module Name is required");
        }

        try {
            Server serv = context.getObjectByName(Server.class, Util.getHostName());

            if (serv != null) {

                Map moduleStatuses = Util.otom(serv.get(Server.ATT_MODULE_STATUS));
                if (moduleStatuses == null) {
                    moduleStatuses = new HashMap<String, Object>();
                }

                StatisticsService svc = new StatisticsService(context);
                Map moduleStat = svc.getModuleStatus(moduleName);

                moduleStatuses.put(moduleName, moduleStat);

                serv.put(Server.ATT_MODULE_STATUS, moduleStatuses);

                context.saveObject(serv);
                context.commitTransaction();

            }

        } catch (GeneralException ge) {
            log.error("Error getting ModuleStatus for module[" + moduleName + "] on Host["
                + Util.getHostName() + "]");
        }

    }
}
