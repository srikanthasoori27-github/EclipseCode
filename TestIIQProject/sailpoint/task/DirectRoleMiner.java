/*  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

/**
 *  DirectRoleMiner
 *
 *  This class is used by the Business Functional Role Miner to mine for roles
 *  based on a group, threshold and application list.  The task will search
 *  for all the entitlements based on the filter and application list.  Then
 *  for any entitlements over the specificied threshold, a business role will
 *  be created.
 *
 *  Arguments for this task include:
 *
 *  Business Process - Business Process to attach the Business Profile to
 *      Business Role - Name to use for the new Business Role
 *      Application List - List of applications to mine entitlements on
 *      Group Definition - This is what the mining activity will be run on
 *      Threshold % - This represents the percent of users for each entitlement
 *                    required to include that entitlement in the business profile
 * @author Terry Sigle
 */
public class DirectRoleMiner {

    private static Log log = LogFactory.getLog(DirectRoleMiner.class);

    // List of returned attributes to the mineRole results object
    final static String NUM_IDENTITIES_MINED = "numIdentitiesMined";
    final static String NUM_CANDIDATE_ENTITLEMENTS = "numCandidateEntitlements";
    final static String NUM_USED_ENTITLEMENTS = "numUsedEntitlements";
    final static String THRESHOLD = "threshold";
    final static String SIMULATE = "simulate";
    final static String TASK_RESULTS = "taskResults";
    final static String ERROR_MSG = "errorMsg";
    private List<String> miningResults = new ArrayList<String>();

    // Variables passed to the object from calling class. Normally these would
    // be set on the constructor.
    private SailPointContext context = null;
    private Bundle role;
    private List<Filter> filters;
    private List<Application> applications;
    private int threshold;
    private Map<String, Object> results = new HashMap<String, Object>();
    private String taskName;
    private boolean _terminate;

    public DirectRoleMiner(SailPointContext context, Bundle role,
            List<Filter> filters, List<Application> applications, int threshold,
            TaskSchedule schedule) throws GeneralException {

        setContext(context);
        setRole(role);
        setFilters(filters);
        setApplications(applications);
        setThreshold(threshold);
        this.taskName = schedule.getTaskDefinitionName(context);
        _terminate = false;
    }

    public Map<String, Object> mineRole() throws GeneralException {

        List<Application> applications = getApplications();
        List<Filter> filters = getFilters();
        int threshold = getThreshold();
        Bundle role = getRole();

        // Perform a level of error checking before continuing
        if (getContext() == null) {
            throw new GeneralException("A valid context is required");
        }

        if (filters == null) {
            throw new GeneralException("A list of Filters is required");
        }

        if (applications == null) {
            throw new GeneralException("A list of Applications is required");
        }

        miningResults.add("Resulting for Role/Profiles using threshold ["
                + threshold + " %]:");

        // Create a Mapping of Entitlement Buckets for each application
        Map<String, Map<String, EntitlementBucket>> entitlementApplications = new HashMap<String, Map<String, EntitlementBucket>>();

        // Get the list of entitlements for all the apps
        Map<String, List<String>> appEntitlements = this.getEntitlementAttributeNames(applications);

        // Create a Sting list of the Application IDs
        List<String> appIds = new ArrayList<String>();

        for (Application app : applications) {
            appIds.add(app.getId());
        }

        // Create some counters to be returned to the mining results
        int numIdentities = 0;
        int numEntitlements = 0;

        try {
            // *********************************************************
            // Create a QueryOptions with the filters passed
            // *********************************************************
            QueryOptions qo = new QueryOptions();
            for (Filter filter : filters) {
                qo.add(filter);
            }

            // *********************************************************
            // Get all the Identities that are a part of this Filter/Group
            // *********************************************************
            Iterator<Identity> it = getContext().search(Identity.class, qo);

            while (it.hasNext() && !_terminate) {
                numIdentities++;

                // ******************************************************
                // Get the Identity and any Exceptions. This list of
                // Exceptions is the entitlements not found in the list of
                // Business Roles on the identity
                // ******************************************************
                Identity identity = it.next();

                // log.debug("Identity :" + identity.getName());

                // Get a list of all the linked accounts/attributes
                List<Link> idLinks = identity.getLinks();

                // if there are no links then go on to then next identity
                if (null == idLinks) {
                    continue;
                }

                // for each link...
                for (Link idLink : idLinks) {
                    String currApp = idLink.getApplication().getId();
                    List<String> currEnt = appEntitlements.get(currApp);

                    if (appIds.contains(currApp) && currEnt != null) {
                        // Get all the attributes for current link
                        Attributes<String, Object> attrs = idLink.getAttributes();

                        // if there are no attributes on the linked application then
                        // go on to the next link.
                        if (null == attrs) {
                            continue;
                        }

                        Set<String> attrKeys = attrs.keySet();

                        for (String attrKey : attrKeys) {
                            if (currEnt.contains(attrKey)) {

                                Map<String, EntitlementBucket> entitlementMap = entitlementApplications.get(currApp);

                                if (entitlementMap == null) {
                                    entitlementMap = new HashMap<String, EntitlementBucket>();
                                }

                                List<String> attrValues = attrs.getList(attrKey);

                                // if there are no values for this attribute then go on
                                // to the next one
                                if (null == attrValues) {
                                    continue;
                                }

                                // ***********************************************
                                // For each attribute value, add this into
                                // an Entitlement Bucket, basically creating
                                // a
                                // count for that value
                                //
                                // Note: I'm going to go ahead and trim()
                                // any
                                // values. So " abc" == "abc" == "abc "
                                // ***********************************************
                                for (String attrValue : attrValues) {
                                    attrValue.trim();

                                    String entitlement = attrKey + "." + attrValue;

                                    // log.debug(" BUCKET: " + entitlement);

                                    EntitlementBucket bucket = entitlementMap.get(entitlement);

                                    if (bucket == null) {
                                        bucket = new EntitlementBucket(currApp, attrKey, attrValue);
                                        numEntitlements++;
                                    }

                                    bucket.increment();

                                    entitlementMap.put(entitlement, bucket);
                                }

                                entitlementApplications.put(currApp, entitlementMap);
                            }
                        }
                    }
                }
            }

            // Capture some result information
            results.put(NUM_IDENTITIES_MINED, numIdentities);
            results.put(NUM_CANDIDATE_ENTITLEMENTS, numEntitlements);

            // *********************************************************
            // Now we have all of the entitlement buckets. So, we need to
            // now create the new Business Role and Profiles for any
            // buckets greater than the threshold passed.
            // *********************************************************
            updateBusinessRole(role, threshold, numIdentities, entitlementApplications);

            // *********************************************************
            // The following is just for debug information
            // *********************************************************
            if (log.isDebugEnabled()) {
                for (Application app : applications) {
                    Map<String, EntitlementBucket> applEntitlements = entitlementApplications.get(app.getId());

                    if (applEntitlements != null) {
                        // log.debug("---BUCKET---Application [" + app.getId() + "]");

                        Set<String> entitlementKeys = applEntitlements.keySet();

                        for (String entitlement : entitlementKeys) {
                            EntitlementBucket bucket = applEntitlements.get(entitlement);
                            log.debug(bucket);
                        }
                    }
                }
            }

            results.put(TASK_RESULTS, miningResults);
        } catch (Exception e) {
            Object cause = e.getCause();

            if (cause instanceof org.hibernate.QueryException) {
                results.put(ERROR_MSG, "Unable to search on Identities using existing Filter");
            } else {
                log.error("Directed Mining failed.", e);
            }
        } finally {

        }

        // Print out a debug list of all results
        log.debug("Results for mineRole()");
        log.debug("----------------------");
        Set<String> resultKeys = results.keySet();

        for (String resultKey : resultKeys) {
            log.debug("  " + resultKey + ": " + results.get(resultKey));
        }

        return results;
    }

    public void refreshIdentities() {
        try {
            Identitizer identitizer = new Identitizer(getContext());
            identitizer.setRefreshSource(Source.Task, this.taskName);

            identitizer.setCorrelateEntitlements(true);

            // *********************************************************
            // Create a QueryOptions with the filters passed
            // *********************************************************
            QueryOptions qo = new QueryOptions();
            for (Filter filter : filters) {
                qo.add(filter);
            }

            List<String> props = new ArrayList<String>();
            props.add("id");
            Iterator<Object[]> identities = getContext().search(Identity.class, qo, props);

            while (identities.hasNext() && !_terminate) {
                String id = (String) (identities.next())[0];
                Identity identity = context.getObjectById(Identity.class, id);

                identitizer.refresh(identity);

                context.saveObject(identity);
                context.commitTransaction();
                context.decache(identity);
            }
        } catch (Exception e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }
    }

    public void terminate() {
        _terminate = true;
    }

    private void updateBusinessRole(Bundle role, int threshold,
            int totalIdentities,
            Map<String, Map<String, EntitlementBucket>> entitlementApplications) {

        try {
            int numUsedEntitlements = 0;
            // Loop through all the potential applications with entitlements.
            Set<String> appKeys = entitlementApplications.keySet();

            for (String appId : appKeys) {
                Map<String, EntitlementBucket> applEntitlements = entitlementApplications.get(appId);

                if (applEntitlements != null) {
                    // log.debug("---BUCKET---Application [" + appId + "]");

                    // Add the application to it
                    Application app = getContext().getObjectById(Application.class, appId);

                    // Construct a new Profile Name
                    String profileName = role.getName() + "_" + app.getName();

                    // See if the profile exists. If so, then we'll use that.
                    // Note: we may want to change this if we don't want to
                    // overwrite an existing
                    // profile.
                    Profile profile = getContext().getObjectByName(Profile.class, profileName);

                    // If no profile is found, then we are creating a new one
                    // else, we'll nuke all the current constraints on this
                    // existing profile
                    if (profile == null) {
                        profile = new Profile();
                        profile.setOwner(role.getOwner());
                        profile.setName(profileName);
                    } else {
                        profile.setConstraints(null);
                    }

                    profile.setApplication(app);

                    // Start building the filter constraints
                    Map<String, List<String>> filterConstraints = new HashMap<String, List<String>>();

                    Set<String> entitlementKeys = applEntitlements.keySet();

                    // This is where we decide what entitlements and values to
                    // add to the constraints. If the number of values is over
                    // the threshold, then we'll add it. Otherwise, drop it
                    // on the floor.
                    for (String entitlement : entitlementKeys) {
                        EntitlementBucket bucket = applEntitlements.get(entitlement);

                        int entValCount = bucket.getCount();
                        int entValPct = (entValCount * 100) / totalIdentities;

                        // log.debug("Entitlement = " + entitlement);
                        // olg.debug("Bucket[" + bucket.getName() + "].["
                        // + bucket.getValue() + "] Count - "
                        // + bucket.getCount());
                        // log.debug(" Checking entValPct " + entValPct
                        // + " against threshold " + threshold);
                        if (entValPct >= threshold) {
                            String entName = bucket.getName();
                            String entVal = bucket.getValue();

                            List<String> entVals = filterConstraints.get(entName);

                            if (entVals == null) {
                                entVals = new ArrayList<String>();
                                filterConstraints.put(entName, entVals);
                            }

                            entVals.add(entVal);
                            // log.debug(bucket);
                        }
                    }

                    Set<String> entNames = filterConstraints.keySet();

                    String profileString = "";

                    for (String entName : entNames) {
                        List<String> entVals = filterConstraints.get(entName);

                        Filter c = Filter.containsAll(entName, entVals);
                        profile.addConstraint(c);

                        numUsedEntitlements += entVals.size();
                        profileString += "     " + c.getExpression() + "\n";
                    }

                    // ******************************
                    // Save the Profile if it has constraints
                    // ******************************
                    if (profile.getConstraints() != null) {
                        role.add(profile);
                        miningResults.add("  PROFILE - " + profile.getName());
                        miningResults.add(profileString);
                    }
                }
            }

            results.put(NUM_USED_ENTITLEMENTS, numUsedEntitlements);
        } catch (Exception e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }
    }

    // *************************************
    // Private inner class to hold the entitlment
    // buckets made up of:
    // Application
    // Entitlement Name
    // Entitlement Value
    // Entitlement Name/Value Count
    // *************************************
    private class EntitlementBucket {
        private String application = null;;
        private String entitlementName = null;
        private String entitlementValue = null;
        private int count = 0;

        public EntitlementBucket(String application, String entitlementName,
                String entitlementValue) {
            this.application = application;
            this.entitlementName = entitlementName;
            this.entitlementValue = entitlementValue;
            count = 0;
        }

        public int increment() {
            count++;
            return count;
        }

        public int getCount() {
            return count;
        }

        public String getName() {
            return entitlementName;
        }

        public String getValue() {
            return entitlementValue;
        }

        public String toString() {
            return ("---BUCKET---Application [" + application
                    + "] Entitlement [" + entitlementName + "."
                    + entitlementValue + "] --- " + count);
        }
    }

    private Map<String, List<String>> getEntitlementAttributeNames(List<Application> applications) {
        Map<String, List<String>> appEntitlements = new HashMap<String, List<String>>();

        for (Application application : applications) {
            List<String> attrs = new ArrayList<String>();
            List<Schema> schemas = application.getSchemas();
            if (schemas != null) {
                attrs = new ArrayList<String>();
                for (Schema schema : schemas) {
                    // Filtering out group attributes -- Bug 2586
                    if (Connector.TYPE_ACCOUNT.equals(schema.getObjectType())) {
                        List<String> entAttrs = schema.getEntitlementAttributeNames();
                        log.debug("Application: " + application.getName() + "Attrs: " + entAttrs);
                        if (entAttrs != null)
                            attrs.addAll(entAttrs);
                    }
                }
            }
            appEntitlements.put(application.getId(), attrs);
        }
        return appEntitlements;
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public List<Application> getApplications() {
        return applications;
    }

    public void setApplications(List<Application> applications) {
        this.applications = applications;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public SailPointContext getContext() {
        return context;
    }

    public void setContext(SailPointContext context) {
        this.context = context;
    }

    public Bundle getRole() {
        return role;
    }

    public void setRole(Bundle role) {
        this.role = role;
    }
}
