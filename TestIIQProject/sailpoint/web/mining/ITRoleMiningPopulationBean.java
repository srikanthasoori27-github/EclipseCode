/**
 * 
 */
package sailpoint.web.mining;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.ITRoleMiningTaskResult;
import sailpoint.object.ITRoleMiningTaskResult.EntitlementStatistics;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlementsKey;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.task.ITRoleMiningTask;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.BaseListBean;

/**
 * @author peter.holcomb
 *
 */
public class ITRoleMiningPopulationBean extends BaseListBean<Identity> {
    private static Log log = LogFactory.getLog(ITRoleMiningPopulationBean.class);

    public static final String ATT_TASK_RESULT = "ITRoleMiningPopTaskResult";
    public static final String ATT_IDENTIFIER = "ITRoleMiningPopIdentifier";
    public static final String ATT_MATCH_TYPE = "ITRoleMiningPopMatchType";

    String identifier;
    String taskResultId;

    TaskResult taskResult;
    ITRoleMiningTaskResult miningTaskResult;
    Set<SimplifiedEntitlement> entitlements;
    boolean isExcludedEntitlements;
    
    /** Whether to match identities based on an exact match or not **/
    boolean matchExact = false;

    public ITRoleMiningPopulationBean() {
        super();
        setScope(Identity.class);
        identifier = (String)getRequestParameter(ATT_IDENTIFIER);
        taskResultId = (String)getRequestParameter(ATT_TASK_RESULT);
        if(getRequestParameter(ATT_MATCH_TYPE)!=null) {
            matchExact = Integer.parseInt(getRequestParameter(ATT_MATCH_TYPE))==1;
        }
    }

    /************************************************************
     * BaseListBean Overrides
     ***********************************************************/

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();  

        /** Remove the limit since we'll have to move through all identities anyways**/
        qo.setResultLimit(0);
        qo.setFirstRow(0);
        qo.setDistinct(true);
        List<Filter> filters = getIdentityFilters();
        if(filters!=null) {
            qo.add(Filter.and(filters));
        }

        return qo;
    }

    @Override
    /** We know the count is going to be the total population of the entitlement set **/
    public int getCount() throws GeneralException {
        if(getMiningTaskResult()!=null) {
            EntitlementStatistics stats = getMiningTaskResult().getStatistics();
            if(matchExact)
                return stats.getExactMatches();
            else
                return stats.getSuperMatches();
        }
        return 0;
    }

    @Override
    public List<Map<String,Object>> getRows() throws GeneralException {
        List<Map<String,Object>> rows = super.getRows();

        /** We need to look into the population and find who has a matching entitlement set **/
        rows = filterRows(rows);
        return trimAndSortResults(rows);
    }
    
    
    /** This function takes the list of identities that match the filters for this mining population and does the following:
     *  - Loads the SimplifiedEntitlementsKey that we got from the Group that we are looking at
     *  - Grabs the list of application ids that were configured during the mining
     *  - For each identity, we load every link to any of the applications in the list of application ids
     *  - For each link, we build a simplified entitlement key and add it to a list.
     *  - Once we have each entitlement key for each link for that identity, we examine them against the group's key
     *  to determine if there is a match.  We are either matching for an exact match (the identity only has the
     *  entitlements from the group) or for a non-exact match where the identity has the entitlements from the group
     *  in addition to other entitlements.
     *  
     * @param rows
     * @return
     * @throws GeneralException
     */
    private List<Map<String,Object>> filterRows(List<Map<String,Object>> rows) throws GeneralException {
        SimplifiedEntitlementsKey groupEntitlementsKey = getMiningTaskResult().getEntitlementSet();
        List<String> applicationIds = getApplicationIds();

        /** Iterate over the identities **/
        Iterator<Map<String,Object>> rowIter = rows.iterator();
        while(rowIter.hasNext()) {
            Map<String,Object> row = rowIter.next();
            String identityId = (String)row.get("id");

            SimplifiedEntitlementsKey identityKey = null;

            for (String appId : applicationIds) {

                /** Get the links for this application and build the entitlements keys for each identity **/
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("application.id", appId));
                qo.add(Filter.eq("identity.id", identityId));
                List<Link> links = getContext().getObjects(Link.class, qo);

                for (Link link : links) { 
                    if(identityKey==null) {
                        identityKey = new SimplifiedEntitlementsKey(link, getEntitlements(), isExcludedEntitlements());
                    } else {
                        identityKey.addLink(link, getEntitlements(), isExcludedEntitlements());
                    }

                }
            }
            
            /** If we are looking for identities who have these entitlemens (as well as others) **/
            boolean match = false;
            if(!matchExact) {
                if(identityKey!=null && identityKey.isSuperSetOf(groupEntitlementsKey)) {
                    match = true;
                }
                
            /** Look for exact matches (identities who only have this set of entitlements **/
            } else {
                if(identityKey !=null && identityKey.equals(groupEntitlementsKey)) {
                    match = true;
                }
            }
            
            /** If not a match, remove the row **/
            if(!match)
                rowIter.remove();
            
        }        
        
        return rows;
    }

    /************************************************************
     * Getters/Setters
     ***********************************************************/

    private ITRoleMiningTaskResult getMiningTaskResult() {
        if(miningTaskResult==null && identifier!=null && getTaskResult()!=null) {
            
            List<ITRoleMiningTaskResult> roleMiningTaskResults = new ArrayList<ITRoleMiningTaskResult>();
            
            Object attrITRoleMiningResults = getTaskResult().getAttribute(ITRoleMiningTask.IT_ROLE_MINING_RESULTS);
            
            // Accommodate the "old" way of using a serialized String
            if (attrITRoleMiningResults instanceof String) {
                String serializedResults = (String) attrITRoleMiningResults;
                XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
                // Previously, toXml was called for itRoleMiningResults data before it was set on the TaskResult.
                // That effectively lost any ampersand literals, so accommodate those before parsing the xml.
                serializedResults = serializedResults.replace("&", "&amp;");
                roleMiningTaskResults = (List<ITRoleMiningTaskResult>)xmlDeserializer.parseXml(getContext(), serializedResults, false);
            } else if (attrITRoleMiningResults instanceof List) {
                roleMiningTaskResults = (List<ITRoleMiningTaskResult>)attrITRoleMiningResults;
            }
            
            if(roleMiningTaskResults!=null && !roleMiningTaskResults.isEmpty()) {
                for(ITRoleMiningTaskResult itResult : roleMiningTaskResults) {
                    if(itResult.getIdentifier()!=null && itResult.getIdentifier().equals(identifier)) {
                        miningTaskResult = itResult;
                        break;
                    }
                }
            }
        }
        return miningTaskResult;
    }
    
    /** Get the actual task result by name **/
    private TaskResult getTaskResult() {
        if(taskResult==null && taskResultId!=null) {
            try {
                taskResult = getContext().getObjectById(TaskResult.class, taskResultId);
            } catch(GeneralException ge) {
                log.warn("Unable to load task result for: " + taskResultId + " Exception: " + ge.getMessage());
            }
        }
        return taskResult;
    }
    
    /** gets the list of application ids to search for links -- if it is not an exact match, we can just 
     * look at the applications from the entitlements in this group
     * @return
     */
    private List<String> getApplicationIds() {
        List<String> applicationIds = new ArrayList<String>();
        if(!matchExact) {
            SimplifiedEntitlementsKey groupEntitlementsKey = getMiningTaskResult().getEntitlementSet();
            if(groupEntitlementsKey!=null) {
                for(SimplifiedEntitlement entitlement : groupEntitlementsKey.getSimplifiedEntitlements()) {
                    String applicationId = entitlement.getApplicationId();
                    if(!applicationIds.contains(applicationId))
                        applicationIds.add(applicationId);
                }
            }
            
        } else {
            applicationIds = Util.csvToList((String)getTaskResult().getAttribute(ITRoleMiningTask.APPLICATIONS));
        }
        return applicationIds;
    }

    private Set<SimplifiedEntitlement> getEntitlements() {
        if(entitlements==null && getTaskResult()!=null) {
            entitlements = (Set<SimplifiedEntitlement>)getTaskResult().getAttribute(ITRoleMiningTask.INCLUDED_ENTITLEMENTS);
        }
        return entitlements;
    }

    private boolean isExcludedEntitlements() {
        Object excludedObj = getTaskResult().getAttribute(ITRoleMiningTask.IS_EXCLUDED);
        if (excludedObj == null) {
            isExcludedEntitlements = false;
        } else {
            isExcludedEntitlements = Util.otob(excludedObj);
        }
        
        return isExcludedEntitlements;
    }
    
    /** Grab the identity filters off of the it task result **/
    private List<Filter> getIdentityFilters() {
        if(getTaskResult()!=null) {
            return (List<Filter>)getTaskResult().getAttribute(ITRoleMiningTask.IDENTITY_FILTER);            
        }
        return null;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getTaskResultId() {
        return taskResultId;
    }

    public void setTaskResultId(String taskResultId) {
        this.taskResultId = taskResultId;
    }


}
