package sailpoint.server;

import sailpoint.api.SailPointContext;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

public class ServicerUtil {

    /**
     * Read the ServiceDefinitions in the database, and bootstrap
     * some important ones if they are missing.
     *
     * The bootstrapping is an unsightly kludge but since we've never
     * had Service definnitions for these it's easier initially if
     * we don't have to force an upgrade for the unit tests.
     */
    public static List<ServiceDefinition> getDefinitions(SailPointContext con)
            throws GeneralException {

        List<ServiceDefinition> definitions = con.getObjects(ServiceDefinition.class);

        // bootstrap the missing ones
        if (definitions == null)
            definitions = new ArrayList<ServiceDefinition>();

        boolean taskFound = false;
        boolean requestFound = false;
        for (ServiceDefinition def : definitions) {
            if (TaskService.NAME.equals(def.getName()))
                taskFound = true;
            if (RequestService.NAME.equals(def.getName()))
                requestFound = true;
        }

        if (!taskFound)
            definitions.add(bootstrapService(TaskService.NAME));

        if (!requestFound)
            definitions.add(bootstrapService(RequestService.NAME));

        return definitions;
    }

    /**
     * Make a stub definition for one of the schedulers.
     */
    private static ServiceDefinition bootstrapService(String name) {
        ServiceDefinition def = new ServiceDefinition();
        def.setName(name);
        def.setExecutor("sailpoint.server." + name + "Service");
        def.setHosts(ServiceDefinition.HOST_GLOBAL);
        return def;
    }

    /**
     * Does the Server override the ServiceDefinition to specifically request to exclude or include a service?
     * @param context
     * @param serviceName the name of the service to check
     * @param serverName the name of the Server to check
     * @return False if serviceName is in Server exclusion list; otherwise True if in inclusion list; otherwise null
     */
    public static Boolean isServiceAllowedByServer(SailPointContext context, String serviceName, String serverName) throws GeneralException {

        if (Util.isNullOrEmpty(serviceName)) {
            return null;
        }

        Boolean allowed = null;

        Server server = context.getObjectByName(Server.class, serverName);
        if (server != null) {


            List<String> includedServices = (List<String>)server.get(Server.ATT_INCL_SERVICES);
            List<String> excludedServices = (List<String>)server.get(Server.ATT_EXCL_SERVICES);
            boolean isIncluded = includedServices != null ? includedServices.contains(serviceName) : false;
            boolean isExcluded = excludedServices != null ? excludedServices.contains(serviceName) : false;
            if (isExcluded) {
                allowed = Boolean.FALSE;
            } else if (isIncluded) {
                allowed = Boolean.TRUE;
            } else {
                allowed = null;
            }
        }

        return allowed;
    }

    /**
     * Based on both the configuration of the Server, and the configuration of the ServiceDefinition,
     * determine if the service is supposed to run on the server.
     * @param context
     * @param svcDef
     * @param serverName
     * @return
     * @throws GeneralException
     */
    public static boolean isServiceAllowedOnServer(SailPointContext context, ServiceDefinition svcDef, String serverName) throws GeneralException
    {
        boolean isServiceAllowed = false;

        String serviceName = svcDef.getName();
        Boolean allowedByServer = ServicerUtil.isServiceAllowedByServer(context, serviceName, serverName);
        if (Boolean.FALSE == allowedByServer) {
            // Request service specifically excluded by this server
            isServiceAllowed = false;
        }
        else if (Boolean.TRUE == allowedByServer) {
            // Request service specifically included by this server
            isServiceAllowed = true;
        }
        else {
            // defer to the service definition's host list
            isServiceAllowed = svcDef.isHostAllowed(serverName);
        }

        return isServiceAllowed;
    }


}
