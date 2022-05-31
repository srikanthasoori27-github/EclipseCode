package sailpoint.web.identity;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * This class determines whether or not the given Identity and any of its 
 * workgroups own any policy violations
 * 
 * @author bernie.margolis
 */
public class PolicyViolationFinder {
    private static final int BATCH_SIZE = 100;
    private QueryOptions workgroupQuery;
    private SailPointContext context;
    private Identity identity;
    private int remainingWorkgroups;
    private int currentBatch;
    private int numViolations;

    public PolicyViolationFinder(Identity identity, SailPointContext context) throws GeneralException {
        //See if Identity or any of the identity's workgroups are violation owners
        this.context = context;
        this.identity = identity;
        prepare();
    }

    private void prepare() throws GeneralException {
        workgroupQuery = new QueryOptions(Filter.eq("id", identity.getId()));
        workgroupQuery.addOrdering("workgroups.id", true);
        workgroupQuery.setResultLimit(BATCH_SIZE);
        remainingWorkgroups = context.countObjects(Identity.class, workgroupQuery);
        currentBatch = 0;
        numViolations = 0;
    }

    /**
     * @return true if the Identity and/or its workgroups own PolicyViolations; false otherwise
     * @throws GeneralException
     */
    public boolean hasViolations() throws GeneralException {
        // Check the Identity itself before doing anything else
        numViolations += getNumViolationsOwnedByGroups(Arrays.asList(identity.getId()));
        // Keep fetching batches until we either find a violation or run out of groups
        while (numViolations == 0 && remainingWorkgroups > 0) {
            doBatch();
        }
        return numViolations > 0;
    }

    private void doBatch() throws GeneralException {
        workgroupQuery.setFirstRow(currentBatch * BATCH_SIZE);
        Set<String> workgroupIds = getWorkgroupIds();
        numViolations += getNumViolationsOwnedByGroups(workgroupIds);
        currentBatch++;
        remainingWorkgroups -= BATCH_SIZE;
    }

    private Set<String> getWorkgroupIds() throws GeneralException {
        Set<String> workgroupIds = new HashSet<String>();
        Iterator<Object[]> ids = context.search(Identity.class, workgroupQuery, "workgroups.id");
        if (ids != null && ids.hasNext()) {
            while (ids.hasNext()) {
                workgroupIds.add((String)ids.next()[0]);
            }
        }
        return workgroupIds;
    }

    private int getNumViolationsOwnedByGroups(Collection<String> workgroupIds) throws GeneralException{
        Filter violationFilter = Filter.in("owner.id", workgroupIds);
        int numViolations = context.countObjects(PolicyViolation.class, new QueryOptions(violationFilter));
        return numViolations;
    }
}
