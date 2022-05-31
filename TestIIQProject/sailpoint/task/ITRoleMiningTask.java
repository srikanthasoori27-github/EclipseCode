/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * Task used to mine IT roles from entitlements  
 *
 * @author Bernie Margolis
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ITRoleMiningTaskResult;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.ITRoleMiningTaskResult.EntitlementStatistics;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlementsKey;
import sailpoint.role.MiningService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.Message.Type;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;

public class ITRoleMiningTask extends AbstractTaskExecutor {
    private static final Log log = LogFactory.getLog(ITRoleMiningTask.class);
    public static final String IDENTITY_FILTER = "identityFilter";
    public static final String APPLICATIONS = "applications";
    public static final String IDENTITY_FILTER_TYPE = "roleMiningFilterType";
    public static final String IDENTITY_FILTER_TYPE_BY_ATTRIBUTES = "searchByAttributes";
    public static final String IDENTITY_FILTER_TYPE_BY_IPOP = "searchByIpop";
    public static final String INCLUDED_ENTITLEMENTS = "includedEntitlements";
    public static final String MIN_IDENTITIES_PER_ROLE = "minIdentitiesPerRole";
    public static final String MIN_ENTITLEMENTS_PER_ROLE = "minEntitlementsPerRole";
    public static final String MAX_CANDIDATE_ROLES = "maxCandidateRoles";
    public static final String IT_ROLE_MINING_RESULTS = "itRoleMiningResults";
    public static final String RESULT_NAME = "itRoleMiningResultName";
    public static final String POPULATION_NAME = "populationName";
    public static final String IS_EXCLUDED = "isExcluded";
    
    private boolean terminate = false;
    private boolean maxCandidatesExceeded = false;
    private int minEntitlementsPerRole;
    private int minIdentitiesPerRole;
    private int maxCandidates;
    
    /*
     * This is an internal partial representation of entitlements that is used to 
     * determine whether or not to include a link in an identity's entitlement set
     */
    private Set<SimplifiedEntitlement> entitlements;
    
    /*
     * This is used to denote whether the variable above is a set of included or excluded entitlements.  
     * It's necessary because it used to be the included ones but we changed that. 
     */
    private boolean isExcluded;
    
    private static final int IDENTITY_BATCH_SIZE = 10; 
    
    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
            throws Exception {
        XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
        String serializedIdentityFilters = (String) args.get(IDENTITY_FILTER);
        List<Filter> identityFilters = (List<Filter>)xmlDeserializer.parseXml(context, serializedIdentityFilters, false);
        List<String> applicationIds = Util.csvToList((String) args.get(APPLICATIONS));
        // IdentityItem will only be partially populated.  In the case of attributes it will only have an app, name, and value 
        // and in the case of Permissions it will only have an app and a target
        String serializedEntitlementItems = (String) args.get(INCLUDED_ENTITLEMENTS);
        List<IdentityItem> entitlementItems = (List<IdentityItem>)xmlDeserializer.parseXml(context, serializedEntitlementItems, false);
        entitlements = initializeEntitlements(entitlementItems);
        // 
        // This hack is necessary because legacy apps had an included entitlements list but we opted to change it to an excluded
        // entitlements list because we want to facilitate bad entitlement mining practices. --Bernie
        Object isExcludedObj = args.get(IS_EXCLUDED);
        if (isExcludedObj == null) {
            isExcluded = false;
        } else {
            isExcluded = Util.otob(isExcludedObj);
        }
        minIdentitiesPerRole = Util.otoi(args.get(MIN_IDENTITIES_PER_ROLE));
        minEntitlementsPerRole = Util.otoi(args.get(MIN_ENTITLEMENTS_PER_ROLE));
        maxCandidates = Util.otoi(args.getInteger(MAX_CANDIDATE_ROLES));
        
        Map <SimplifiedEntitlementsKey, EntitlementStatistics> entitlementStatistics;
        QueryOptions identityQuery = new QueryOptions(identityFilters.toArray(new Filter[identityFilters.size()]));
        identityQuery.setDistinct(true);
        int identityCount = context.countObjects(Identity.class, identityQuery);
        
        if (applicationIds == null || applicationIds.isEmpty()) {
            List<Message> failureMessages = Arrays.asList(new Message[] {
                new Message(MessageKeys.IT_ROLE_MINING_TERMINATED),
                new Message(MessageKeys.IT_ROLE_MINING_REQUIRES_APPLICATIONS)
            });
            fail(result, failureMessages, null);
            entitlementStatistics = null;
        } else if (identityCount == 0) {
            List<Message> failureMessages = Arrays.asList(new Message[] {
                    new Message(MessageKeys.IT_ROLE_MINING_TERMINATED),
                    new Message(MessageKeys.IT_ROLE_MINING_REQUIRES_IDENTITIES)
                });
            fail(result, failureMessages, null);
            entitlementStatistics = null;
        } else {
            int identitiesProcessed = 0;
            
            // First we go over all the links for an identity and aggregate the entitlements contained therein into a unified set
            Map<String, SimplifiedEntitlementsKey> entitlementsByIdentity = new HashMap<String, SimplifiedEntitlementsKey>();
            entitlementStatistics = new HashMap<SimplifiedEntitlementsKey, EntitlementStatistics>();

            while (identitiesProcessed < identityCount && !this.terminate) {
                entitlementsByIdentity.clear();
                identityQuery.setFirstRow(identitiesProcessed);
                identityQuery.setResultLimit(IDENTITY_BATCH_SIZE);
                for (String appId : applicationIds) {
                    processLinks(context, identityQuery, appId, entitlementsByIdentity);
                }
                if (!entitlementsByIdentity.isEmpty()) {
                    processEntitlementsForIdentities(entitlementsByIdentity, entitlementStatistics);
                }
                
                identitiesProcessed += IDENTITY_BATCH_SIZE;
            }
            
            // At the end of the whole mess calculate statistics for entitlement sets that are supersets of others
            if (!this.terminate) {
                calculateSuperMatches(entitlementStatistics);
                weedOutEntitlementsThatLackIdentities(entitlementStatistics);
            }
            
            if (this.terminate) {
                List<Message> failureMessages = Arrays.asList(new Message[] {
                        new Message(MessageKeys.IT_ROLE_MINING_TERMINATED),
                        new Message(Type.Error, MessageKeys.IT_ROLE_MINING_STATISTICS_NOT_ACCURATE)
                    });
                fail(result, failureMessages, null);            
            }
            
            if (maxCandidatesExceeded) {
                List<Message> failureMessages = Arrays.asList(new Message[] {
                        new Message(Type.Error, MessageKeys.IT_ROLE_MINING_MAX_CANDIDATES_EXCEEDED, maxCandidates)
                });
                fail(result, failureMessages, null);            
            }
        }
        
        List<ITRoleMiningTaskResult> taskResults = new ArrayList<ITRoleMiningTaskResult>();
        if (entitlementStatistics != null && !entitlementStatistics.isEmpty()) {
            Set<SimplifiedEntitlementsKey> entitlements = entitlementStatistics.keySet();
            for (SimplifiedEntitlementsKey entitlement : entitlements) {
                taskResults.add(new ITRoleMiningTaskResult(entitlement, entitlementStatistics.get(entitlement), identityCount));
            }
            
            if (taskResults != null) {
                Collections.sort(taskResults, Collections.reverseOrder(MiningService.TASK_RESULT_BY_EXACT_MATCH_COMPARATOR));
                int identifierNumber = 1;
                for (ITRoleMiningTaskResult taskResult : taskResults) {
                    taskResult.setIdentifier("Group" + identifierNumber++);
                }
            }
        }
        
        String resultName = (String) args.get(RESULT_NAME);
        result.setName(resultName);
        // ITRoleMiningTaskResult extends AbstractXmlObject, so set the attribute directly instead of doing a toXml first.
        // If we modify the ITRoleMiningTaskResult list with toXml, we end up escaping the whole xml 
        // and lose any literals like ampersands, etc.
        result.setAttribute(IT_ROLE_MINING_RESULTS, taskResults);
        result.setAttribute(IDENTITY_FILTER, identityFilters);
        result.setAttribute(APPLICATIONS, (String) args.get(APPLICATIONS));
        result.setAttribute(IS_EXCLUDED, isExcluded);
        result.setAttribute(INCLUDED_ENTITLEMENTS, entitlements);
        result.setTerminated(terminate);
    }

    public boolean terminate() {
        this.terminate = true;
        return this.terminate;
    }
    
    private TaskResult fail(final TaskResult result, final List<Message> failureMessage, final Throwable reason) {
        if (reason != null) {
            result.addException(reason);
        }
        result.addMessages(failureMessage);
        return result;
    }
    
    private void processLinks(SailPointContext context, QueryOptions identityQuery, String appId, Map<String, SimplifiedEntitlementsKey> entitlementsByIdentity) throws GeneralException {
        List<Identity> identitiesToProcess = context.getObjects(Identity.class, identityQuery);
        List<Link> linksToProcess = context.getObjects(Link.class, new QueryOptions(Filter.and(Filter.eq("application.id", appId), Filter.in("identity", identitiesToProcess))));
        if (linksToProcess != null && !linksToProcess.isEmpty()) {
            for (Link link : linksToProcess) { 
                String identityId = link.getIdentity().getId();
                SimplifiedEntitlementsKey entitlementsForIdentity = entitlementsByIdentity.get(identityId);
                if (entitlementsForIdentity == null) {
                    // The getEntitlementsKey() method also filters for the entitlements that the user wanted to include.
                    // If no such entitlements exist then null is returned
                    entitlementsForIdentity = getEntitlementsKey(link);
                    if (entitlementsForIdentity != null && entitlementsForIdentity.meetsEntitlementsRequirement(minEntitlementsPerRole)) {
                        entitlementsByIdentity.put(identityId, entitlementsForIdentity);
                    }
                } else {
                    entitlementsForIdentity.addLink(link, entitlements, isExcluded);
                }
            }
        } 
    }
    
    private void processEntitlementsForIdentities(Map<String, SimplifiedEntitlementsKey> entitlementsForIdentities, Map <SimplifiedEntitlementsKey, EntitlementStatistics> entitlementStatistics) {
        // Update or create statistics for each identity's set of entitlements
        Set<String> identityIds = entitlementsForIdentities.keySet();
        for (String identityId : identityIds) {
            SimplifiedEntitlementsKey entitlementsForIdentity = entitlementsForIdentities.get(identityId);
            EntitlementStatistics statistics = entitlementStatistics.get(entitlementsForIdentity);
            if (statistics == null) {
                if (maxCandidates > entitlementStatistics.size()) {
                    statistics = new EntitlementStatistics();
                    entitlementStatistics.put(entitlementsForIdentity, statistics);
                } else {
                    maxCandidatesExceeded = true;
                }
            }
            
            if (statistics != null) {
                statistics.addExactMatch();
            }
        }
    }
    
    private void calculateSuperMatches(Map <SimplifiedEntitlementsKey, EntitlementStatistics> entitlementStatistics) {
        if (entitlementStatistics != null && !entitlementStatistics.isEmpty()) {
            Set<SimplifiedEntitlementsKey> entitlementSetsToCheckAgainst = entitlementStatistics.keySet();
            List<SimplifiedEntitlementsKey> entitlementSetsToCheck = new ArrayList<SimplifiedEntitlementsKey>();
            entitlementSetsToCheck.addAll(entitlementSetsToCheckAgainst);
            // Ugh... O(n^2) -- Can we do better here?
            for (SimplifiedEntitlementsKey entitlementsKeyToCheck : entitlementSetsToCheck) {
                if (this.terminate) {
                    break;
                } else {
                    for (SimplifiedEntitlementsKey entitlementsKeyToCheckAgainst : entitlementSetsToCheckAgainst) {
                        if (this.terminate) {
                            break;
                        } else {
                            if (entitlementsKeyToCheck.isSuperSetOf(entitlementsKeyToCheckAgainst)) {
                                EntitlementStatistics statisticsForSuperMatch = entitlementStatistics.get(entitlementsKeyToCheck);
                                EntitlementStatistics statisticsToUpdate = entitlementStatistics.get(entitlementsKeyToCheckAgainst);
                                statisticsToUpdate.addSuperMatches(statisticsForSuperMatch.getExactMatches());
                            }
                        }
                    }
                }
            }            
        }
    }
    
    private void weedOutEntitlementsThatLackIdentities(Map<SimplifiedEntitlementsKey, EntitlementStatistics> entitlementStatistics) {
        Set<SimplifiedEntitlementsKey> entitlements = entitlementStatistics.keySet();
        Set<SimplifiedEntitlementsKey> entitlementsToRemove = new HashSet<SimplifiedEntitlementsKey>();
        if (entitlements != null && !entitlements.isEmpty()) {
            for (SimplifiedEntitlementsKey entitlement : entitlements) {
                EntitlementStatistics stats = entitlementStatistics.get(entitlement);
                if (stats.getExactMatches() < minIdentitiesPerRole) {
                    entitlementsToRemove.add(entitlement);
                }
            }
        }
        
        // Do this out here to avoid ConcurrentModificationExceptions
        if (!entitlementsToRemove.isEmpty()) {
            for (SimplifiedEntitlementsKey entitlementToRemove : entitlementsToRemove) {
                entitlementStatistics.remove(entitlementToRemove);
            }
        }
    }
    
    private SimplifiedEntitlementsKey getEntitlementsKey(Link link) {
        SimplifiedEntitlementsKey entitlementsKey = new SimplifiedEntitlementsKey(link, entitlements, isExcluded);
        if (entitlementsKey.getSimplifiedEntitlements().isEmpty()) {
            entitlementsKey = null;
        }
        
        return entitlementsKey;
    }
            
    private Set<SimplifiedEntitlement> initializeEntitlements(List<IdentityItem> includedEntitlementItems) {
        Set<SimplifiedEntitlement> includedEntitlements = MiningService.convertIdentityItemsToSimplifiedEntitlements(includedEntitlementItems);
        
        if (includedEntitlementItems != null && !includedEntitlementItems.isEmpty()) {
            for (IdentityItem includedEntitlementItem : includedEntitlementItems) {

                if (includedEntitlementItem.isPermission()) {
                    // Add a representation of a class of permisssions for a given app/target combination
                    // TODO: Permission displaynames are messed up because they get broken out into a one-entitlement-per right form. 
                    //       Ignore the managed attribute display names on them for now.
                    includedEntitlements.add(new SimplifiedEntitlement(includedEntitlementItem.getApplication(), null, null, includedEntitlementItem.getName(), null, null, null));
                } else {
                    try {
                        String displayName = MiningService.getDisplayName(includedEntitlementItem.getApplication(), includedEntitlementItem.getName(), (String)includedEntitlementItem.getValue());
                        // Add a representation of a class of entitlement attributes for a given app/name/value combination
                        includedEntitlements.add(new SimplifiedEntitlement(includedEntitlementItem.getApplication(), null, null, includedEntitlementItem.getName(), (String)includedEntitlementItem.getValue(), displayName));
                    } catch (ClassCastException e) {
                        log.debug("IdentityItems for the role mining task should have been split into single-attribute SimplifiedEntitlements before being submitted to the task.");
                    } 
                }
            }
        }
        
        return includedEntitlements;
    }
}

