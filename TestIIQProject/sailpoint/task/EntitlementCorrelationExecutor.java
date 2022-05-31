/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * NOTE: This class is no longer used, we now use 
 * IdentityRefreshExecutor for all identity scans.
 * Keeping this around in case we have some older TaskDefinitions
 * that still reference this but we should fix those someday. - jsl
 */

package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.Util;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import java.util.Iterator;


/**
 * A task that iterates over identities and correlates entitlements to those
 * defined by the business processes.
 * 
 * @author Kelly Grizzle
 */
public class EntitlementCorrelationExecutor extends AbstractTaskExecutor
{
    private static final Log LOG = LogFactory.getLog(EntitlementCorrelationExecutor.class);

    
    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Filter string to apply when searching for identities on which to
     * correlate entitlements.  Optional - if not specified entitlements are
     * correlated on all identities.
     */
    public static final String ARG_IDENTITY_FILTER = "filter";

    /**
     * Key of the return value in which the total number of identities processed
     * is returned.
     */
    public static final String RETURN_TOTAL = "total";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Context given to us by the scheduler.  We can commit transactions.
     */
    SailPointContext context;

    /**
     * An optional Filter used to filter the identities on which entitlements
     * are correlated.
     */
    private Filter filter;

    /**
     * Set by the terminate method to indiciate that we should stop
     * when convenient.
     */
    private boolean terminate;

    /**
     * Statistic about the number of identies processed. 
     */
    private int totalIdentites = 0;


    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public EntitlementCorrelationExecutor() {}

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate()
    {
        this.terminate = true;
        return true;
    }

    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and dissappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched,
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        this.context = context;
        this.terminate = false;

        try
        {
            QueryOptions qo = new QueryOptions();
            String filterString = args.getString(ARG_IDENTITY_FILTER);
            if (null != filterString)
            {
                this.filter = Filter.compile(filterString);
                qo.add(this.filter);
            }

            LOG.info("Beginning entitlement correlation pass with filter: " + filterString);

            Iterator<Identity> it =
                this.context.search(Identity.class, qo);
            EntitlementCorrelator ec = new EntitlementCorrelator(this.context);

            while (it.hasNext() && !this.terminate)
            {
                Identity identity = it.next();
                LOG.debug("Correlating entitlements for: " + identity.getName());
                ec.processIdentity(identity);

                // refresh derived summary attributes
                identity.updateBundleSummary();

                this.context.saveObject(identity);
                this.context.commitTransaction();

                // Decache to prevent cache from exploding.
                this.context.decache(identity);

                this.totalIdentites++;
            }

            LOG.info("Entitlement correlation pass completed.");
            result.setTerminated(this.terminate);
        }
        catch (Throwable t) {
            result.addMessage(new Message(Message.Type.Error,
                MessageKeys.ERR_EXCEPTION, t));
        }

        // save some things
        // hmm, for some reasons integers aren't making it into the XML...
        result.setAttribute(RETURN_TOTAL, Util.itoa(this.totalIdentites));

        LOG.debug(this.totalIdentites + " processed during entitlement correlation task.");
    }
}
