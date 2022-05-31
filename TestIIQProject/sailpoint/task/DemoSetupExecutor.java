/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import sailpoint.api.CertificationBuilder;
import sailpoint.api.CertificationBuilderFactory;
import sailpoint.api.Certificationer;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.api.certification.CertCreationHelper;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.server.Importer;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * A task that causes background changes to help walk through a demo scenario.
 * 
 * @author Kelly Grizzle
 */
public class DemoSetupExecutor extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Constants for the modes in which this task can be run.
     */
    public static enum Mode {

        /**
         * Mode to run to initially setup the demo.  This combines
         * GENERATE_INITIAL_CERTIFICATIONS, ACT_ON_CERTIFICATIONS, and
         * GENERATE_CERTIFICATION_DIFFS.
         */
        INITIAL_DEMO_SETUP,

        /**
         * Mode in which entitlements will be correlated and certification
         * reports will be generated for the usual suspects in the demo data.
         */
        GENERATE_INITIAL_CERTIFICATIONS,

        /**
         * Mode in which some decisions are made on all of the certifications to
         * get the demo looking like it is in the middle of the certification
         * process.
         */
        ACT_ON_CERTIFICATIONS,

        /**
         * Mode in which differences will be generated and certification reports
         * re-generated to display business role and entitlement diffs.
         */
        GENERATE_CERTIFICATION_DIFFS,

        /**
         * Mode that resets back to a base state.
         */
        RESET
    }

    /**
     * The name of the argument that holds the mode in which this task is being
     * run.  This can be run in different modes to set up different parts of the
     * demo.
     */
    public static final String ARG_MODE = "mode";

    /**
     * The name of the argument that holds the name of the manager for which to
     * re-generate the certification.
     */
    public static final String ARG_MANAGER_NAME = "managerName";

    /**
     * The name of the argument that holds the name of the application for which
     * to re-generate the certification.
     */
    public static final String ARG_APPLICATION_NAME = "applicationName";

    /**
     * The name of the e-order application.
     */
    private static final String EORDER_APP_NAME = "E-Order Management System";

    /**
     * The name of the Dave Hildebrand manager.
     */
    private static final String DHILDEBRAND_NAME = "dhildebrand";


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Context given to us by the scheduler.  We can commit transactions.
     */
    private SailPointContext context;

    /**
     * TaskResult given to us by the scheduler.
     */
    private TaskResult result;

    /**
     * Arguments given to us by the scheduler.
     */
    private Attributes<String,Object> args;

    /**
     * Set by the terminate method to indiciate that we should stop
     * when convenient.
     */
    private boolean terminate;


    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public DemoSetupExecutor() {}

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        this.terminate = true;
        return true;
    }

    /**
     * Run the demo setup.
     */
    public void execute(SailPointContext context, TaskSchedule sched,
                        TaskResult result, Attributes<String,Object> args)
        throws Exception {

        this.context = context;
        this.result = result;
        this.args = args;
        this.terminate = false;

        try {
            // Get the mode of operation.  Default to generating certification
            // differences.
            String modeArg = args.getString(ARG_MODE);
            modeArg = (null == modeArg) ? Mode.GENERATE_CERTIFICATION_DIFFS.name() : modeArg;
            Mode mode = Mode.valueOf(Mode.class, modeArg);

            switch (mode) {
            case INITIAL_DEMO_SETUP:
                initialDemoSetup();
                break;
            case GENERATE_INITIAL_CERTIFICATIONS:
                generateInitialCertifications();
                break;
            case ACT_ON_CERTIFICATIONS:
                actOnCertifications();
                break;
            case GENERATE_CERTIFICATION_DIFFS:
                generateCertificationDiffs();
                break;
            case RESET:
                reset();
                break;
            }
        }
        catch (Throwable t) {
            result.addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_EXCEPTION, t));
        }
        finally {
            result.setTerminated(this.terminate);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Setup the demo from a clean install
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Generates the initial certifications, generates differences and reruns
     * the certifications to show changes detected, and randomly acts upon the
     * other certifications.
     */
    private void initialDemoSetup() throws GeneralException {

        Attributes<String,Object> prevArgs = this.args;

        try {
            this.generateInitialCertifications();

            this.args = new Attributes<String,Object>(prevArgs);
            this.args.put(ARG_APPLICATION_NAME, EORDER_APP_NAME);
            this.generateCertificationDiffs();

            this.args = new Attributes<String,Object>(prevArgs);
            this.args.put(ARG_MANAGER_NAME, DHILDEBRAND_NAME);
            this.generateCertificationDiffs();

            this.args = prevArgs;
            this.actOnCertifications();
        }
        finally {
            this.args = prevArgs;
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Generate the initial certifications
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Get the certifier user.
     */
    private Identity getCertifier() throws GeneralException {
    
        return context.getObjectByName(Identity.class, "Admin");
    }

    /**
     * Correlate entitlements and generate certification reports for all app
     * owners and managers.
     */
    private void generateInitialCertifications() throws GeneralException {

        try {
            // Correlate all entitlements.
            
            // jsl - note that this is no longer used by actual tasks,
            // they all use IdentityRefreshExecutor which has some performance
            // optimizations.  Unless we need to pass in filters, we could
            // just use EntitlementCorrelator directly here and delete the old executor
            EntitlementCorrelationExecutor ecTask = new EntitlementCorrelationExecutor();
            ecTask.execute(context, null, result, args);

            if (terminate)
                return;

            // Generate global manager and app owner certifications.
            String adminUserName = BrandingServiceFactory.getService().getAdminUserName();
            Identity launcher =
                this.context.getObjectByName(Identity.class, adminUserName);

            TaskSchedule ts = CertCreationHelper.createSchedule(context,
                    new CertCreationHelper.SimpleCertDef(launcher, Certification.Type.Manager, true));

            // Wait on task to finish (allow waiting up to five minutes).
            TaskManager tm = new TaskManager(this.context);
            tm.awaitTask(ts, 300);
            
            if (terminate)
                return;

            // Launch the global app owner cert.
            ts = CertCreationHelper.createSchedule(context,
                    new CertCreationHelper.SimpleCertDef(launcher, Certification.Type.ApplicationOwner, true));

            // Wait on task to finish (allow waiting up to five minutes).
            tm.awaitTask(ts, 300);
        }
        catch (Exception e) {
            throw new GeneralException(e);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Generate certification differences
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Generate certification diffs for the requested application or manager
     * certification.  Note that this will only display diffs when run for the
     * first time for each app owner/manager because the same differences are
     * generated each time.
     * 
     * This does the following:
     *  1) Finish previous certification for requested app/manager if not
     *     yet signed off.
     *  2) Add "Returns" business role to kgrizzle.
     *  3) Remove "Sales Order Entry" from kgrizzle by removing "Groups:
     *     Order Entry" on Elite Sales.  This will make "Groups: Order
     *     Entry Clerk" on E-Order Management System be an extra
     *     entitlement.
     *  4) Add extra permission "grant" to Invoice List on E-Order
     *     Management System for kgrizzle.
     *  5) Add an additional entitlement to other users on the E-Order
     *     Management System so they show up with changes detected.
     *  6) Run entitlement correlation on the modified users.
     *  7) Generate new certification for requested app/manager.
     */
    private void generateCertificationDiffs() throws GeneralException {

        // Step 1 - Sign off on previous certification.
        String appName = args.getString(ARG_APPLICATION_NAME);
        String managerName = args.getString(ARG_MANAGER_NAME);
        signOffOnPrevious(appName, managerName);

        // Steps 2-4 - Modify the identity to add/remove business roles and
        // entitlements.
        // TODO: This is all hardcoded for kgrizzle right now.  Consider making
        // this configurable through the task arguments.
        swizzleEntitlements("kgrizzle");

        // Step 5 - Add simple additional entitlements to other users.
        // TODO: Fill in this list once demodata has more users.
        List<String> usersToTweak = Arrays.asList(new String[]
            { "droberts", "thayes", "sperkins", "emiller", "sswinford", "lwagner" });
        for (String userName : usersToTweak)
            addAttributeValue(userName, EORDER_APP_NAME, "memberOf", "Product Re-pricing");

        // Step 6 - Correlate entitlements.
        EntitlementCorrelator ec = new EntitlementCorrelator(context);
        List<String> usersToCorrelate = new ArrayList<String>(usersToTweak);
        usersToCorrelate.add("kgrizzle");
        for (String userName : usersToCorrelate)
            ec.processIdentity(context.getObjectByName(Identity.class, userName));

        // Step 7 - Generate new certification.
        Certificationer certificationer = new Certificationer(context);

        CertificationBuilderFactory builderFactory = new CertificationBuilderFactory(context);
        CertificationBuilder builder = null;
        if (null != appName) {
            Application app = context.getObjectByName(Application.class, appName);
            List<String> appIds = Collections.singletonList(app.getId());
            builder = builderFactory.getAppOwnerCertBuilder(appIds);
        }
        else {
            Identity manager = context.getObjectByName(Identity.class, managerName);
            builder = builderFactory.getManagerCertBuilder(manager);
        }
        Certification cert =
            certificationer.generateCertification(getCertifier(), builder.getContext());
        certificationer.start(cert);
    }

    /**
     * Sign off on the previous certification.  Either appName or managerName
     * should be specified (but not both).  If a previous certification is not
     * found, this throws an exception.
     * 
     * @param  appName      The name of the application for an app owner cert.
     * @param  managerName  The name of the manager for a manager cert.
     */
    private void signOffOnPrevious(String appName, String managerName)
        throws GeneralException {

        if ((null == appName) && (null == managerName))
            throw new GeneralException("Application or manager must be specified.");

        // Only need to work on this if it hasn't been signed yet.
        Certification prev = getPreviousCertification(appName, managerName);
        if (!prev.hasBeenSigned()) {
            // Bulk approve any items that aren't yet complete.
            if (!prev.isComplete()) {
                if (null != prev.getEntities()) {
                    for (CertificationEntity identity : prev.getEntities()) {
                        if (!identity.isComplete()) {
                            CertificationAction action = new CertificationAction();
                            action.setStatus(CertificationAction.Status.Approved);
                            identity.bulkCertify(getCertifier(), null, action, null, false);
                        }
                    }
                }
            }

            // Tell the Certificationer to finish the certification.
            Certificationer certificationer = new Certificationer(context);
            certificationer.sign(prev, getCertifier());
            certificationer.finish(prev);
        }
    }

    /**
     * Get the previous certification for the given application or manager.
     * Either appName or managerName is assumed to be non-null.  Throw an
     * exception if we cannot find a previous certification of the specified
     * type.
     * 
     * @param  appName      The name of the application for an app owner cert.
     * @param  managerName  The name of the manager for a manager cert.
     * 
     * @return The previous certification for the given application or manager.
     */
    private Certification getPreviousCertification(String appName, String managerName)
        throws GeneralException {

        Filter filter = null;
        if (null != appName) {
            Application app = context.getObjectByName(Application.class, appName);
            if (null == app)
                throw new GeneralException("Need to import demo data - could not find: " + appName);
            filter =
                Filter.and(Filter.eq("applicationId", app.getId()),
                           Filter.eq("type", Certification.Type.ApplicationOwner));
        }
        else {
            filter =
                Filter.and(Filter.eq("manager", managerName),
                           Filter.eq("type", Certification.Type.Manager));
        }

        QueryOptions ops = new QueryOptions();
        ops.add(filter);
        ops.setOrderBy("created");
        ops.setOrderAscending(false);
        List<Certification> prevCerts = this.context.getObjects(Certification.class, ops);
        if ((null == prevCerts) || prevCerts.isEmpty())
            throw new GeneralException("No previous certification to sign off on.");

        return prevCerts.get(0);
    }

    /**
     * Add and remove entitlements and business roles from the given identity.
     * 
     * @param  identityName  The identity to act upon.
     */
    @SuppressWarnings("unchecked")
    private void swizzleEntitlements(String identityName) throws GeneralException {

        Identity identity = context.getObjectByName(Identity.class, identityName);
        if (null == identity)
            throw new GeneralException("Could not find user " + identityName);

        // Add "Returns" business role by "Returns" Groups on both E-Order Mgt.
        // System and Elite Sales.
        Link eOrder = getOrCreateLink(identity, EORDER_APP_NAME);
        Link elite = getOrCreateLink(identity, "Elite Sales");
        addAttributeValue(eOrder, "memberOf", "Returns");
        addAttributeValue(elite, "memberOf", "Returns");

        // Remove "Sales Order Entry" business role by removing "Groups: Order
        // Entry" from Elite Sales.
        List<String> groups = (List<String>) elite.getAttribute("memberOf");
        if (null != groups)
            groups.remove("Order Entry");

        // Add extra permission "grant" to Invoice List on E-Order Management
        // System.
        List<Permission> perms =
            (List<Permission>) eOrder.getAttribute(Connector.ATTR_DIRECT_PERMISSIONS);
        if (null == perms)
            perms = new ArrayList<Permission>();
        eOrder.setAttribute(Connector.ATTR_DIRECT_PERMISSIONS, perms);
        Permission invoiceList = null;
        for (Permission perm : perms) {
            if ("Invoice List".equals(perm.getTarget())) {
                invoiceList = perm;
                break;
            }
        }

        if (null == invoiceList) {
            invoiceList = new Permission();
            invoiceList.setTarget("Invoice List");
        }
        List<String> rights = invoiceList.getRightsList();
        if (!rights.contains("grant")) {
            rights.add("grant");
            invoiceList.setRights(Util.listToCsv(rights));
        }
    }

    /**
     * Add a value to a multi-valued attribute on the given user's requested link.
     * 
     * @param  userName  The name of the user to which to add the value.
     * @param  appName   The name of the app on which to add the value.
     * @param  attrName  The name of the attribute to which to add the value.
     * @param  val       The value to add.
     */
    private void addAttributeValue(String userName, String appName,
                                   String attrName, String val)
        throws GeneralException {

        Identity identity = context.getObjectByName(Identity.class, userName);
        if (null == identity)
            throw new GeneralException("Could not find user " + userName);
        Link link = getOrCreateLink(identity, appName);
        addAttributeValue(link, attrName, val);
    }

    /**
     * Get or create the Link for the given application name on the requested
     * Identity.
     * 
     * @param  identity  The Identity from which to retrieve (or on which to
     *                   add) the Link.
     * @param  appName   The name of the application of the link to get/create.
     * 
     * @return The Link for the given application name on the requested Identity.
     */
    private Link getOrCreateLink(Identity identity, String appName)
        throws GeneralException {

        Application app = context.getObjectByName(Application.class, appName);
        if (null == app)
            throw new GeneralException("Could not find application " + appName);
        Link link = identity.getLink(app, null, identity.getName());

        // Link should be there, but we'll create it if it is not.
        if (null == link) {
            link = new Link();
            link.setDisplayName(identity.getName());
            link.setNativeIdentity(identity.getName());
            link.setApplication(app);
            Attributes<String,Object> attrs = new Attributes<String,Object>();
            attrs.put("firstname", identity.getName());
            attrs.put("lastname", identity.getName());
            link.setAttributes(attrs);
            identity.add(link);
        }

        return link;
    }

    /**
     * Add the value to the multi-valued attribute on the given Link.
     * 
     * @param  link      The Link on which to add the value.
     * @param  attrName  The name of the multi-valued attribute to which to add
     *                   the value.
     * @param  val       The value to add (if not already present).
     */
    @SuppressWarnings("unchecked")
    private void addAttributeValue(Link link, String attrName, String val) {
        List<String> vals = (List<String>) link.getAttribute(attrName);
        if (null == vals)
            vals = new ArrayList<String>();
        if (!vals.contains(val))
            vals.add(val);
        link.setAttribute(attrName, vals);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Act on the certifications
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Randomly approve, mitigate, delegate, remediate pieces of all active
     * certifications (except for E-Order Management System).
     */
    private void actOnCertifications() throws GeneralException {

        List<Certification> certs = context.getObjects(Certification.class);
        if (null != certs) {
            for (Certification cert : certs) {
                if (doWeTweakThisThang(cert)) {
                    actOnCertification(cert);
                }
            }
        }
    }

    /**
     * Return whether or not to approve, mitigate, etc... this certification.
     * We will ignore the certifications on which we are generating differences.
     */
    private boolean doWeTweakThisThang(Certification cert) throws GeneralException {
    
        // Only tweak the Elite Sales app owner certification because most
        // other apps have high percentages of auto-approved identities.
        Application app = cert.getApplication(context);
        if (null != app) {
            if ("Elite Sales".equals(app.getName()))
                return true;
            else
                return false;
        }

        // Tweak any manager certification except for Dave's.
        Identity certifier = cert.getManager(context);
        if ((null != certifier) && DHILDEBRAND_NAME.equals(certifier.getName()))
            return false;

        return true;
    }

    /**
     * Randomly approve, mitigate, remediate, and delegate the identities and
     * items in the given certification.
     * 
     * @param  cert  The Certification to act upon.
     */
    private void actOnCertification(Certification cert) throws GeneralException {

        // Complete any random percentage.  Delegate a smaller percentage, any
        // amount of the remaining up to a max of 10% (any more than that and
        // the certification owner can be considered a slacker).
        double percentToComplete = Math.random();

        // We want Elite Sales to show up without many changes so that it will
        // be in the red.  Give it a max of 20% complete.
        Application app = cert.getApplication(context);
        if ((null != app) && "Elite Sales".equals(app.getName())) {
            percentToComplete = Math.random() * 0.20;
        }

        double percentToDelegate = Math.random() *
                                      Math.min(0.1, Math.random() * (1-percentToComplete));

        if (null != cert.getEntities()) {
            for (CertificationEntity identity : cert.getEntities()) {

                double myNumber = Math.random();

                if (shouldIDoIt(myNumber, 0, percentToComplete) && !identity.isComplete()) {
                    randomlyComplete(identity);
                }
                else if (shouldIDoIt(myNumber, percentToComplete, percentToComplete+percentToDelegate)) {
                    delegate(identity);
                }
            }
        }

        context.saveObject(cert);
        Certificationer certificationer = new Certificationer(context);
        certificationer.refresh(cert);
    }

    /**
     * Given a random number and upper/lower bounds, tell me whether I should
     * do it or not.
     */
    private static boolean shouldIDoIt(double myNumber, double bottomBand,
                                       double topBand) {
        return (bottomBand <= myNumber) && (myNumber <= topBand);
    }

    /**
     * Randomly approve, mitigate, and/or remediate the items in the given
     * identity.
     * 
     * @param  identity  The CertificationIdentity to act upon.
     */
    private void randomlyComplete(CertificationEntity identity)
        throws GeneralException {

        List<CertificationItem> items = getAllLeafs(identity);
        for (CertificationItem item : items) {

            // Select between approve, mitigate, and remediate.  78% approve, 
            // 17% remediate, 5% mitigate.
            double myNumber = Math.random();

            if (shouldIDoIt(myNumber, 0, 0.78)) {
                // Approve.
                item.approve(context, getCertifier(), null);
            }
            else if (shouldIDoIt(myNumber, 0.78, 0.95)) {
                // Remediate.
                item.remediate(context, getCertifier(), null,
                               CertificationAction.RemediationAction.OpenWorkItem,
                               "pholt",
                               "Please remove this entitlement from " + identity.getName(),
                               "This employee does not need this access.", null, null);
            }
            else {
                // Mitigate.
                item.mitigate(context, getCertifier(), null,
                              new Date(System.currentTimeMillis() + 1000*60*60*24*14),
                              "Allow this for two more weeks.");
            }
        }
    }

    /**
     * Delegate either the given CertificationIdentity or one of the items in
     * the certification (it is randomly chosen which gets delegated).
     * 
     * @param  identity  The identity to delegate.
     */
    private void delegate(CertificationEntity identity) {

        CertificationDelegation delegation = new CertificationDelegation();
        String fullname =
            Util.getFullname(identity.getFirstname(), identity.getLastname());
        delegation.setOwnerName("pholt");
        delegation.setDescription("Please certify access on " + fullname);
        delegation.setComments("Would you mind taking care of this for me?  I don't know this person.");

        // Flip a coin ... heads delegate the identity, tails delegate one of
        // the items.
        double myNumber = Math.random();
        if (shouldIDoIt(myNumber, 0, 0.5)) {
            identity.setDelegation(delegation);
        }
        else {
            CertificationItem item = getFirstLeaf(identity);
            if (null != item)
                item.setDelegation(delegation);
        }
    }

    /**
     * Get the first leaf item in the given identity.
     */
    private CertificationItem getFirstLeaf(CertificationEntity identity) {

        CertificationItem item = null;
        List<CertificationItem> items = identity.getItems();
        while ((null == item) && (null != items)) {
            if (!items.isEmpty()) {
                CertificationItem current = items.get(0);
                if (null == current.getItems())
                    item = current;
                else
                    items = current.getItems();
            }
        }
        return item;
    }

    /**
     * Get all leaf items from the given identity.
     */
    private List<CertificationItem> getAllLeafs(CertificationEntity identity) {

        List<CertificationItem> items = new ArrayList<CertificationItem>();
        items.addAll(getAllLeafs(identity.getItems()));
        return items;
    }

    private List<CertificationItem> getAllLeafs(List<CertificationItem> items) {

        List<CertificationItem> leafs = new ArrayList<CertificationItem>();

        if (null != items) {
            for (CertificationItem item : items) {
                if ((null == item.getItems()) || item.getItems().isEmpty())
                    leafs.add(item);
                else
                    leafs.addAll(getAllLeafs(item.getItems()));
            }
        }

        return leafs;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Reset back to initial state
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Reset all identities in the demo data base to their original state.
     * TODO: Should we delete all certifications, work items, etc...???
     */
    private void reset() throws GeneralException {

        String demodata = Util.readFile("test/demodata.xml");
        Importer importer = new Importer(context);
        importer.setClassToImport(Identity.class);
        importer.importXml(demodata);
    }
}
