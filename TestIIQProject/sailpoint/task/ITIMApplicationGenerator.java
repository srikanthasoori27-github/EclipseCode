/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.lang.reflect.Method;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ConnectorProxy;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Resolver;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * This task will auto-create ITIM account applications given a list of ITIM
 * person applications.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ITIMApplicationGenerator extends AbstractTaskExecutor {

    public static final String ARG_ITIM_APP_IDS = "itimAppIds";
    public static final String ARG_APP_NAME_PREFIX = "appNamePrefix";
    public static final String ARG_APP_NAME_SUFFIX = "appNameSuffix";
    
    public static final String RESULT_APPS_CREATED = "appsCreated";
    public static final String RESULT_APPS_IGNORED = "appsIgnored";
    public static final String ITIMLDAPCONNECTOR_CLASS = "class sailpoint.connector.ITIMLDAPConnector";
    
    /**
     * Default constructor.
     */
    public ITIMApplicationGenerator() {
        super();
    }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#execute(sailpoint.api.SailPointContext, sailpoint.object.TaskSchedule, sailpoint.object.TaskResult, sailpoint.object.Attributes)
     */
    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, TaskSchedule schedule,
                        TaskResult result, Attributes<String, Object> args)
        throws Exception {

        List<String> itimAppIds = args.getList(ARG_ITIM_APP_IDS);
        if (null == itimAppIds) {
            result.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_MISSING_ITIM_APP_IDS));
            return;
        }

        int ignored = 0;
        int created = 0;

        for (String itimAppId : itimAppIds) {
            Application itimApp =
                context.getObjectByName(Application.class, itimAppId);
            if (null == itimApp) {
                result.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_ITIM_APP_NOT_FOUND));
                return;
            }
    
            Connector connector = ConnectorFactory.getConnector(itimApp, null);
            Connector internalConn = ((ConnectorProxy) connector).getInternalConnector();
            if (null != internalConn) {
                if (!(internalConn.getClass().toString()
                        .equals(ITIMLDAPCONNECTOR_CLASS))) {
                    result.addMessage(new Message(Message.Type.Error,
                            MessageKeys.ERR_APP_IS_NOT_ITIM,
                            itimApp.getName()));
                    return;
                }
            }

            String prefix = args.getString(ARG_APP_NAME_PREFIX);
            String suffix = args.getString(ARG_APP_NAME_SUFFIX);
            
            Method m = internalConn.getClass().getMethod(
                    "" + "createAccountApplications", Resolver.class,
                    String.class, String.class);
            List<Application> apps = (List<Application>) m.invoke(internalConn,
                    context, prefix, suffix);
    
            if (null != apps) {
                for (Application app : apps) {
                    Application existing =
                        context.getObjectByName(Application.class, app.getName());
    
                    if (null != existing) {
                        ignored++;
                    }
                    else {
                        created++;
                        context.saveObject(app);
                    }
                }
    
            }

            context.commitTransaction();
        }
        
        // TODO: Add identity attribute for erglobalid?
        
        result.setAttribute(RESULT_APPS_CREATED, created);
        result.setAttribute(RESULT_APPS_IGNORED, ignored);
    }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#terminate()
     */
    public boolean terminate() {
        // Don't support terminate now.
        return false;
    }
}
