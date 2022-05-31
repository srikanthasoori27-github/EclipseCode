/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import sailpoint.api.SailPointContext;
import sailpoint.api.Identitizer;
import sailpoint.object.TaskSchedule;
import sailpoint.object.TaskResult;
import sailpoint.object.Attributes;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.Util;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * This task allows administrators to update composite accounts without
 * having to perform a full refresh. To accomplish this the refresh is
 * limited to identities which are likely to have an account on
 * one or more composites listed in the 'applications' task argument.
 * <p/>
 * We can limit the scope of the refresh first by including account holders
 * on the primary tier applications for the given composites. If the primary,
 * is not defined, we have to refresh any identity that has a link on at least
 * one tier.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class CompositeAccountRefreshExecutor extends IdentityRefreshExecutor {

    public String ARG_APPLICATIONS = "applications";


    public CompositeAccountRefreshExecutor() {
        super();
    }

    
    /**
     * Creates a filter which should retrieve all the identities who might have accounts on
     * any of the composite apps listed in the applications argument. This filter is
     * then passed to the identity refresh executor to limit the extent of the
     * refresh.
     *
     * @param context
     * @param sched
     * @param result
     * @param args
     * @throws Exception
     */
    public void execute(SailPointContext context, TaskSchedule sched, TaskResult result, Attributes<String, Object> args) throws Exception {

        String appsArg = args.getString(ARG_APPLICATIONS);
        List<String> compositeApps = appsArg != null ? Util.csvToList(appsArg) : null;

        Set<String> appsToSearch = new HashSet<String>();
        if (compositeApps != null && !compositeApps.isEmpty()) {
            Filter nameOdIdFilter = Filter.or(Filter.in("id", compositeApps), Filter.in("name", compositeApps));
            
            List<Application> apps = context.getObjects(Application.class, new QueryOptions(nameOdIdFilter));
            if (apps != null) {
                for (Application app : apps) {

                    // check all users who have an account on the composite
                    appsToSearch.add(app.getName());

                    // get list of links which should be search when looking for
                    // possible composite accounts
                    if (app.getCompositeDefinition() != null){
                        String primaryTier = app.getCompositeDefinition().getPrimaryTier();
                        if (primaryTier == null)
                            appsToSearch.addAll(app.getCompositeDefinition().getTierAppList());
                        else
                            appsToSearch.add(primaryTier);
                    }
                }

                if (!appsToSearch.isEmpty()) {
                    Filter filter = Filter.in("links.application.name", appsToSearch);
                    args.put(IdentityRefreshExecutor.ARG_COMPILED_FILTER, filter);
                    args.put(Identitizer.ARG_REFRESH_COMPOSITE_APPLICATIONS, true);
                    args.put(Identitizer.ARG_COMPOSITE_APPLICATIONS, true);
                    args.put(Identitizer.ARG_COMPOSITE_APPLICATIONS, apps);
                    super.execute(context, sched, result, args);
                }
            }
        }
    }
}
