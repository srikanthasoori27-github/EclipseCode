/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. 
 *
 *  DirectRoleMiner
 *
 *  This custom task executor will take some arguments and mine for roles
 *  based on a group, threshold and application list.  The task will search
 *  for all the entitlements based on the filter and application list.  Then
 *  for any entitlements over the specificied threshold, a business role will
 *  be created.
 *
 *  Arguments for this task include:
 *
 *    Role Name of the role to which mined entitlements will be attached (if that option is selected)
 *    New Role Name - Name to use for a newly created Business Role (if that option is selected)
 *    Application List - List of applications to mine entitlements on
 *    Group Definition - This is what the mining activity will be run on
 *    Threshold % - This represents the percent of users for each entitlement
 *                  required to include that entitlement in the business profile
 *
 *  @author Terry Sigle
 *    
 */
package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Entitlement;
import sailpoint.object.Filter;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleConfig;

public class EntitlementRoleGenerator extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(EntitlementRoleGenerator.class);

    //
    // Input Arguments
    //
    public static final String ARG_APPLICATIONS = "applications";
    public static final String ARG_LOCALE = "locale";
    public static final String ARG_ROLE_TYPE = "roleType";
    public static final String ARG_ATTRIBUTE_FILTER = "attributeFilter";
    public static final String ARG_PERMISSIONS_FILTER = "permissionsFilter";
    public static final String ARG_NAMING_TEMPLATE = "roleNamingTemplate";

    public static final String RESULT_NUM_ROLES_GENERATED = "numRolesGenerated";
    public static final String RESULT_NUM_ENTITLEMENTS_DETECTED = "numEntitlementsFound";

    // List of returned attributes to the TaskResult
    final static String NUM_ROLES_GENERATED = "numRolesGenerated";
    final static String TASK_RESULTS = "taskResults";
    final static String ERROR_MSG = "errorMsg";

    private final static int MAX_OBJECTS_TO_MINE_AT_ONCE = 1000; // Hard-code for now, change if we have to

    /**
     * Set by the terminate method to indicate that we should stop
     * when convenient.
     */
    private boolean terminate;

    private SailPointContext context = null;

    /**
     * The result so various methods
     * can write warnings and errors if necessary.
     */
    private TaskResult result;

    private Locale locale;

    private Map<String, ProgressMarker> progressMap;

    private int numDetectedEntitlements;

    public EntitlementRoleGenerator() {
    }

    /**
     * Parse a string application IDs or names separated by ',' and return a List of
     * Applications
     */
    private List<Application> getApplications(String apps) {
        ArrayList<Application> appsList = new ArrayList<Application>();

        try {
            RFC4180LineParser parser = new RFC4180LineParser(',');

            if (apps != null) {
                ArrayList<String> tmpList = parser.parseLine(apps);

                for (String appId : tmpList) {
                    Application app = getContext().getObjectById(Application.class, appId.trim());
                    appsList.add(app);
                }
            }
        } catch (Exception e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        }

        return appsList;
    }

    // ************************************************************************
    // Task to create an Entitlement Role for every entitlement found in the 
    // specified application(s)
    // The arguments passed to this task include:
    //
    // appIds - CSV list of applications for which to generate entitlement roles
    // ************************************************************************
    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, TaskSchedule sched,
            TaskResult result, Attributes<String, Object> args)
                    throws Exception {
        terminate = false;
        this.result = result;
        setContext(context);
        locale = Locale.getDefault();
        String localeStr = args.getString(ARG_LOCALE);
        if ( localeStr != null ) 
            locale = new Locale(localeStr);      
        progressMap = new HashMap<String, ProgressMarker>();

        // Check for all the required inputs and combinations
        String roleType = (String) args.get(ARG_ROLE_TYPE);
        RoleConfig roleConfig = new RoleConfig();
        List<RoleTypeDefinition> roleTypeDefs = roleConfig.getRoleTypeDefinitionsList();

        if (roleType == null || roleType.trim().length() == 0) {
            if (roleTypeDefs != null && !roleTypeDefs.isEmpty()) {
                roleType = "entitlement";

            }
        } else {
            // Make sure the role type is valid.  If it's not valid, check if a display name
            // was used instead.  If so, correct the value.  Otherwise log a warning and leave
            // it to the user to fix their config to match the type later
            boolean roleTypeWasFound = false;
            for (RoleTypeDefinition roleTypeDef : roleTypeDefs) {
                if (roleType.equals(roleTypeDef.getName())) {
                    roleTypeWasFound = true;
                }
            }

            if (!roleTypeWasFound) {
                for (RoleTypeDefinition roleTypeDef : roleTypeDefs) {
                    if (roleType.equals(roleTypeDef.getDisplayName())) {
                        roleType = roleTypeDef.getName();
                        roleTypeWasFound = true;
                    }
                }
            }

            if (!roleTypeWasFound) {
                // TODO: come back to this and determine how far we go if the desired role type was not found
                result.addMessage(new Message(Message.Type.Warn, MessageKeys.ENTITLEMENT_ROLE_GENERATOR_UNDEFINED_ROLE_TYPE, roleType));
            }
        }

        log.debug("Setting role type to " + roleType);

        List<Message> returnMsgs = new ArrayList<Message>();

        // Make sure a valid application was specified
        Object appObjs = args.get(ARG_APPLICATIONS);
        List<Application> applicationList = ObjectUtil.getObjects(context, Application.class, appObjs);

        if (applicationList == null || applicationList.isEmpty()) {
            returnMsgs.add(new Message(Message.Type.Error, MessageKeys.ENTITLEMENT_ROLE_GENERATOR_APPS_REQUIRED));
        }

        if (returnMsgs.size() > 0) {
            result.addMessages(returnMsgs);

            return;
        }

        int numCreatedRoles = 0;

        String attributeFilter = (String) args.get(ARG_ATTRIBUTE_FILTER);
        if (attributeFilter != null) {
            attributeFilter = attributeFilter.trim();
            if (attributeFilter.length() == 0) attributeFilter = null;
        }

        String permissionsFilter = (String) args.get(ARG_PERMISSIONS_FILTER);
        if (permissionsFilter != null) {
            permissionsFilter = permissionsFilter.trim();
            if (permissionsFilter.length() == 0) permissionsFilter = null;
        }

        String nameTemplate = (String) args.get(ARG_NAMING_TEMPLATE);
        if (nameTemplate != null) {
            nameTemplate = nameTemplate.trim();
            if (nameTemplate.length() == 0) nameTemplate = null;
        }

        try {
            Set<Entitlement> minedEntitlements = getEntitlements(applicationList, attributeFilter, permissionsFilter);

            // Repeatedly mine entitlements until we run out of things to mine.
            // This is necessary to constrain the amount of memory we use.
            // We mine entitlements for MAX_OBJECTS_TO_MINE_AT_ONCE objects before
            // flushing them out to roles.
            while (minedEntitlements.size() > 0) {
                numCreatedRoles += createRoles(minedEntitlements, roleType, nameTemplate);
                minedEntitlements = getEntitlements(applicationList, attributeFilter, permissionsFilter);
            }

            result.setAttribute(RESULT_NUM_ROLES_GENERATED, Integer.toString(numCreatedRoles));
            result.setAttribute(RESULT_NUM_ENTITLEMENTS_DETECTED, Integer.toString(numDetectedEntitlements));
        } catch (Exception ex) {
            Message errMsg = errorMsg(ex.toString());
            log.error(errMsg.getLocalizedMessage(), ex);
            result.addMessage(errMsg);
        } finally {
            result.setTerminated(this.terminate);
        }
        log.debug("Finished...");

    }

    public SailPointContext getContext() {
        return context;
    }

    public void setContext(SailPointContext context) {
        this.context = context;
    }

    private Set<Entitlement> getEntitlements(List<Application> applicationList, String attributeFilter, String permissionsFilter) throws GeneralException {
        Set<Entitlement> minedEntitlements = new HashSet<Entitlement>();
        int numMinedObjects = 0;

        if (applicationList != null && !applicationList.isEmpty()) {
            List<String> appNameList = new ArrayList<String>();

            for (Application application : applicationList) {
                checkForTermination();
                QueryOptions appQuery = new QueryOptions(Filter.eq("application", application));
                appQuery.setDistinct(true);
                String applicationName = application.getName();
                ProgressMarker progress = progressMap.get(applicationName); //TDODO: progress marker's ent counts are WAY too high
                if (progress == null) {
                    progress = createProgressMarker(application);
                    progressMap.put(applicationName, progress);
                }

                int firstRow;
                int limit;

                firstRow = progress.getTotalIdentityEntitlements() - progress.getRemainingIdentityEntitlements();
                limit = MAX_OBJECTS_TO_MINE_AT_ONCE - numMinedObjects;

                // get entitlements from IdentityEntitlement
                // TODO: this
                int minedAttributes = addEntitlements(application, minedEntitlements, firstRow, limit, numMinedObjects, progress.getRemainingIdentityEntitlements(), attributeFilter);

                progress.decrementIdentityEntitlements(minedAttributes);
                numMinedObjects += minedAttributes;

                log.debug("Mined " + minedAttributes + " entitlements for the " + applicationName + " application.");

                firstRow = progress.getTotalManagedAttributes() - progress.getRemainingManagedAttributes();
                limit = MAX_OBJECTS_TO_MINE_AT_ONCE - numMinedObjects;
                // get permissions from ManagedAttribute
                // TODO: this
                int minedPermissions = addPermissions(application, minedEntitlements, firstRow, limit, numMinedObjects, progress.getRemainingManagedAttributes(), permissionsFilter);

                progress.decrementManagedAttributes(minedPermissions);
                numMinedObjects += minedPermissions;

                log.debug("Mined " + minedPermissions + " permissions for the " + applicationName + " application.");

            }
        } else {
            result.addMessage(new Message(Message.Type.Warn, MessageKeys.ENTITLEMENT_ROLE_GENERATOR_APPS_REQUIRED));
        }

        return minedEntitlements;
    }


    /*
     * Creates a ProgressMarker for entitlements and permissions associated to the target application
     */
    private ProgressMarker createProgressMarker(Application application) throws GeneralException {
        // For entitlements, I need to count over a distinct set of columns in IdentityEntitlement, however
        // counting distinct columns is not possible in hql (ex, select count (distinct name, value) from foo)
        // So, a second best (and admittedly, not great), is to count each set of distinct values per distinct attribute name.
        // That means for every unique entitlement name, there are N + 1 hits to the DB in this method, ie: O(N)
        // This probably won't be a big impact since you'd have to expect an application to have several attributes that are
        // marked as entitlements.  That'd be one heckuva schema and therefore the performance of this little algorithm
        // is proably not their first concern.
        int countIE = 0;

        // app query is our base QueryOptions
        QueryOptions appQuery = new QueryOptions();
        appQuery.add(Filter.eq("application", application));

        // entQuery is used just for entitlements on IdentityEntitlement
        QueryOptions entQuery = new QueryOptions(appQuery);
        entQuery.add(Filter.ne("type", ManagedAttribute.Type.Permission.name()));
        entQuery.setDistinct(true);
        List<String> countValueProperties = new ArrayList<String>();
        countValueProperties.add("count(distinct value)");
        // returns a list of attribute names for 'application'
        Iterator<Object[]> nameIter = context.search(IdentityEntitlement.class, entQuery, "name");
        while (nameIter.hasNext()) {
            String nextName = (String)nameIter.next()[0];
            QueryOptions byName = new QueryOptions(entQuery);
            byName.add(Filter.eq("name", nextName));
            byName.setDistinct(false); // Unnecesary statement other than to make a claim for later reading: I've provided a distinct clause already
            Iterator<Object[]> countIter = context.search(IdentityEntitlement.class, byName, countValueProperties);
            if (countIter.hasNext()) {
                countIE += (Long)countIter.next()[0];
            }
        }

        // One more count: how many MAs for this app are there
        // TODO: I'm building these QueryOptions too many times.  Pull that
        // work into a private method
        QueryOptions permQuery = new QueryOptions(appQuery);
        permQuery.setDistinct(true); // not much else for this QO
        int countMA = context.countObjects(ManagedAttribute.class, permQuery);
        ProgressMarker progress = new ProgressMarker(countIE, countMA);
        return progress;
    }

    private void addUserFilter(String filter, QueryOptions options) {
        if (filter != null && filter.trim().length() > 0) {
            Filter userFilter = Filter.compile(filter);
            options.add(userFilter);
        }
    }

    /**
     * Add permissions to our entitlement set and return the number of objects that were mined for permissions.
     * @param application
     * @param entitlementSet
     * @param firstRow
     * @param limit
     * @param numMinedObjects
     * @param remainingObject
     * @param filter
     * @return
     * @throws GeneralException
     */
    private int addPermissions(Application application, Set<Entitlement> entitlementSet, int firstRow, int limit,
            int numMinedObjects, int remainingObject, String filter) throws GeneralException {
        int minedObjects = 0;
        int initialEntitlementSetSize = entitlementSet.size();
        QueryOptions managedAttrQuery = new QueryOptions();
        managedAttrQuery.add(Filter.eq("application", application));

        if (numMinedObjects < MAX_OBJECTS_TO_MINE_AT_ONCE && remainingObject > 0) {
            managedAttrQuery.setFirstRow(firstRow);
            managedAttrQuery.setResultLimit(limit);
            managedAttrQuery.setDistinct(true);
            if (filter != null) {
                addUserFilter("value.startsWith(\"" + filter + "\")", managedAttrQuery);
            }
            Iterator<ManagedAttribute> maDataIter = context.search(ManagedAttribute.class, managedAttrQuery);
            while (maDataIter.hasNext()) {
                ManagedAttribute ma = maDataIter.next();
                List<Permission> perms = ma.getPermissions();
                for (Permission perm : perms) {
                    Entitlement ent = new Entitlement(application.getName(), perm, ma.getDescription(), locale);
                    entitlementSet.add(ent);
                }
                minedObjects++;
            }

            if (minedObjects > limit) {
                minedObjects = limit;
            }
        }

        numDetectedEntitlements += entitlementSet.size() - initialEntitlementSetSize;

        return minedObjects;
    }

    /**
     * Add entitlements to our entitlement set and return the number of objects that were mined for entitlements
     * @return the number of objects that were mined for entitlements
     * @private
     */
    private int addEntitlements(Application application, Set<Entitlement> entitlementSet, int firstRow, int limit, 
            int numMinedObjects, int remainingObjects, String filter) throws GeneralException {
        int minedObjects = 0;
        int initialEntitlementSetSize = entitlementSet.size();
        QueryOptions entitlementQuery = new QueryOptions();
        entitlementQuery.add(Filter.eq("application", application));

        if (numMinedObjects < MAX_OBJECTS_TO_MINE_AT_ONCE && remainingObjects > 0) {
            entitlementQuery.setFirstRow(firstRow);
            entitlementQuery.setResultLimit(limit);
            entitlementQuery.add(Filter.ne("type", ManagedAttribute.Type.Permission.name()));
            entitlementQuery.setDistinct(true);
            if (filter != null) {
                addUserFilter("name.startsWith(\"" + filter + "\")", entitlementQuery);
            }

            // now we need to get unique name/value pairs of entitlements
            List<String> properties = new ArrayList<String>();
            properties.add("name");
            properties.add("value");
            Iterator<Object[]> entitlementDataIter = context.search(IdentityEntitlement.class, entitlementQuery, properties);

            while (entitlementDataIter.hasNext()) {
                Object[] nextRow = (Object[])entitlementDataIter.next();
                String name = (String)nextRow[0];
                String value = (String)nextRow[1];

                String description = Explanator.getDescription(application, name, value, locale);
                // using Entitlement since it has all the role name template logictudes
                Entitlement entitlement = new Entitlement(application.getName(), name, value, description, locale);
                entitlementSet.add(entitlement);
                minedObjects++;
            }


            if (minedObjects > limit) {
                minedObjects = limit;
            }
        }

        numDetectedEntitlements += entitlementSet.size() - initialEntitlementSetSize;

        return minedObjects;
    }

    /**
     * @param minedEntitlements
     * @return the number of roles that were actually created
     */
    private int createRoles(Set<Entitlement> minedEntitlements, String roleType, String nameTemplate) {
        int numRolesSaved = 0;
        int numDuplicatesFound = 0;
        int numFailures = 0;
        Set<String> roleNamesPendingCreation = new HashSet<String>();
        Map<String, Bundle> newAppRoles = new HashMap<String, Bundle>();
        final String ORGANIZATIONAL_ROLE_TYPE = "organizational";

        try {
            // Everything will go under the "Entitlement" role
            final String rootEntitlementRoleName = new Message(MessageKeys.LABEL_ENTITLEMENTS).getLocalizedMessage().trim();
            Bundle rootEntitlementRole = getContext().getObjectByName(Bundle.class, rootEntitlementRoleName);
            if (rootEntitlementRole == null) {
                rootEntitlementRole = new Bundle();
                rootEntitlementRole.setName(rootEntitlementRoleName);
                rootEntitlementRole.setType(ORGANIZATIONAL_ROLE_TYPE);
                getContext().saveObject(rootEntitlementRole);
            }

            for (Entitlement entitlement : minedEntitlements) {
                Bundle newBundle = new Bundle();

                // using Entitlement since it has all the role name template logictudes
                String entitlementRoleName = entitlement.getRoleName(nameTemplate);

                // Check the name of the entitlement.  If it's null or blank after
                // trimming, then set the rolename to "null".
                if (entitlementRoleName == null) {
                    entitlementRoleName = "null";
                } else {
                    entitlementRoleName = entitlementRoleName.trim();
                    if (entitlementRoleName.length() == 0) {
                        entitlementRoleName = "null";
                    }
                }

                // Make sure we don't generate duplicates
                if (!roleNamesPendingCreation.contains(entitlementRoleName)) {
                    try {
                        QueryOptions roleNameQuery = new QueryOptions(Filter.eq("name", entitlementRoleName));
                        int existingRoleCount = getContext().countObjects(Bundle.class, roleNameQuery);
                        // Let's not add a new entitlement if one already existed
                        if (existingRoleCount == 0) {
                            Application app = getContext().getObjectByName(Application.class, entitlement.getApplicationName());
                            newBundle.setName(entitlementRoleName);
                            newBundle.setOwner(app.getOwner());
                            Profile newProfile = new Profile();
                            if (entitlement.getAttributeName() != null) {
                                newProfile.setAttribute(entitlement.getAttributeName(), entitlement.getAttributeValue());
                                ArrayList<String> values = new ArrayList<String>();
                                values.add(entitlement.getAttributeValue());
                                newProfile.setConstraints(Arrays.asList(Filter.containsAll(entitlement.getAttributeName(), values)));
                            }

                            if (entitlement.getPermission() != null) {
                                newProfile.setPermissions(Arrays.asList(entitlement.getPermission()));
                            }
                            newProfile.setApplication(app);
                            newBundle.setDescription(entitlement.getDescription());
                            newProfile.setDescription(entitlement.getDescription());
                            newBundle.setType(roleType);
                            newBundle.add(newProfile);
                            String appName = app.getName();
                            Bundle applicationRole = getContext().getObjectByName(Bundle.class, appName);
                            if (applicationRole == null) {
                                applicationRole = newAppRoles.get(appName);
                            }                            
                            if (applicationRole == null) {
                                applicationRole = new Bundle();
                                applicationRole.setName(appName);
                                applicationRole.setType(ORGANIZATIONAL_ROLE_TYPE);
                                applicationRole.addInheritance(rootEntitlementRole);
                                newAppRoles.put(appName, applicationRole);
                                getContext().saveObject(applicationRole);
                            }
                            newBundle.addInheritance(applicationRole);
                            getContext().saveObject(newBundle);
                            numRolesSaved++;
                            roleNamesPendingCreation.add(entitlementRoleName);
                            //                        backitOut.append("delete Bundle '" + entitlementRoleName + "'\n");
                        } else {
                            numDuplicatesFound++;
                        }
                    } catch (GeneralException e) {
                        log.error("Failed to save a newly created role for the entitlement named " + entitlementRoleName, e);
                        numFailures++;
                    }
                } else {
                    numDuplicatesFound++;
                }
            }        
            try {
                getContext().commitTransaction();
                getContext().decache();
            } catch (GeneralException e) {
                numRolesSaved = 0;
                log.error("Failed to newly created entitlement roles", e);
            }
        } catch (GeneralException e) {
            log.error("The role entitlement generator cannot access the database right now.", e);
        }

        log.debug("Processed " + minedEntitlements.size() + " entitlements but only created " + numRolesSaved + " roles.");
        log.debug("Found " + numDuplicatesFound + " duplicates and " + numFailures + " failures.");
        //        System.out.println("Back it out: " + backitOut.toString());

        return numRolesSaved;
    }


    private Message errorMsg(String msg) {
        return new Message(Message.Type.Error, msg);
    }

    private Message warnMsg(String msg) {
        return new Message(Message.Type.Warn, msg);
    }

    private void checkForTermination() throws GeneralException {
        if ( terminate ) 
            throw new GeneralException(new Message(Message.Type.Error, MessageKeys.EDS_TERMINATED));
    }

    public boolean terminate() {
        terminate = true;
        return terminate;
    }

    private class ProgressMarker {
        int totalManagedAttributes;
        int remainingManagedAttributes;
        int totalIdentityEntitlements;
        int remainingIdentityEntitlements;

        public ProgressMarker(int totalIdentityEntitlements, int totalManagedAttributes) {
            this.totalManagedAttributes = totalManagedAttributes;
            this.remainingManagedAttributes = totalManagedAttributes;
            this.totalIdentityEntitlements = totalIdentityEntitlements;
            this.remainingIdentityEntitlements = totalIdentityEntitlements;
        }

        public int getTotalIdentityEntitlements() {
            return totalIdentityEntitlements;
        }

        public int getRemainingIdentityEntitlements() {
            return remainingIdentityEntitlements;
        }

        public void decrementIdentityEntitlements(int numIdentityEntitlements) {
            remainingIdentityEntitlements -= numIdentityEntitlements;
        }

        public int getTotalManagedAttributes() {
            return totalManagedAttributes;
        }

        public int getRemainingManagedAttributes() {
            return remainingManagedAttributes;
        }

        public void decrementManagedAttributes(int numManagedAttributes) {
            remainingManagedAttributes -= numManagedAttributes;
        }
    }
}
