/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.policy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.ActivityConstraint;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.TimePeriod;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb.
 * Checks whether the identity violates any activity constraints.
 *
 */
public class ActivityPolicyExecutor extends AbstractPolicyExecutor {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ActivityPolicyExecutor.class);

    private static final String DETAILS_RENDERER = "policyActivityDetails.xhtml";
    
    SailPointContext context;
    List<PolicyViolation> violations;
    

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    public ActivityPolicyExecutor() {
        // TODO Auto-generated constructor stub
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // PolicyExecutor
    //
    //////////////////////////////////////////////////////////////////////
    /* (non-Javadoc)
     * @see sailpoint.object.PolicyExecutor#evaluate(sailpoint.api.SailPointContext, sailpoint.object.Policy, sailpoint.object.Identity)
     */
    public List<PolicyViolation> evaluate(SailPointContext context,
            Policy policy, Identity id) throws GeneralException {
        violations = null;
        this.context = context;
        
        if (log.isDebugEnabled())
            log.debug("Scanning Activity for Identity: " + id);
        
        List<ActivityConstraint> constraints = policy.getActivityConstraints();
        if(constraints!=null) {
            for(ActivityConstraint con : constraints) {
                //Ignore disabled constraints
                if (!con.isDisabled()) {                    
                    /**
                     * TODO: PH: Need to figure out how to have this scan only
                     * activities since a specified date or since last scan...
                     */
                    if(isInPopulation(con, id)) {
                        findViolations(con, id, policy);
                        
                    }
                    
                }                
            }
        }        
        return violations;
    }
    
    /**
     * Takes the identity and a list of filters on the identity table to search whether
     * this identity should be 
     */
    private boolean isInPopulation(ActivityConstraint con, Identity id) throws GeneralException{
        boolean inPopulation = true;
        List<Filter> identityFilters = con.getIdentityFilters();
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("id", id.getId()));
        
        List<Filter> filters = new ArrayList<Filter>();
        if(identityFilters!=null){
        	
            for(Filter filter : identityFilters) {
                filters.add(filter);
            }
        }
        
        if(filters.size()>1)
        {
        	qo.add(new CompositeFilter(BooleanOperation.AND, filters));
        } else if(!filters.isEmpty()) {
        	qo.add(filters.get(0));
        }
    
        List<String> props = new ArrayList();
        props.add("name");

        Iterator<Object[]> it = context.search(Identity.class, qo, props);
        if(!it.hasNext()){
            inPopulation = false;
            log.debug("No Identity Found");
        } else {
            if (log.isDebugEnabled())
                log.debug("Found Identity with Identity: " + (String)it.next()[0]);
        }      
        return inPopulation;
    }
    
    private void findViolations(ActivityConstraint con, Identity id, Policy policy) throws GeneralException {
        List<Filter> activityFilters = con.getActivityFilters();
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identityId", id.getId()));
        
        /** Add TimePeriods as a list of or'd filters **/
        if(con.getTimePeriods()!=null && !con.getTimePeriods().isEmpty()){
            List<Filter> timePeriodFilters = new ArrayList<Filter>();
            for(TimePeriod tp : con.getTimePeriods()) {
                timePeriodFilters.add(Filter.eq("id", tp.getId()));
            }
            
            if (log.isDebugEnabled())
                log.debug("Time Period Filters: " + timePeriodFilters);
            
            qo.add(Filter.collectionCondition("timePeriods", Filter.or(timePeriodFilters)));
        }
        qo.setDistinct(true);
        
        List<Filter> filters = new ArrayList<Filter>();
        if(activityFilters!=null){
        	
            for(Filter filter : activityFilters) {
                // For a while, we were stripping the ignoreCase. However,
                // this had the possibility to change behavior. Although,
                // selecting ignoreCase, without the proper index will
                // work due to upper(property) == upper(value), this will
                // prove to be far from performant.
                //stripIgnoreCase(filter);
                filters.add(filter);
            }
        }
        
        if(filters.size()>1)
        {
        	qo.add(new CompositeFilter(BooleanOperation.AND, filters));
        } else if(!filters.isEmpty()) {
        	qo.add(filters.get(0));
        }
   
        if (log.isDebugEnabled())
            log.debug("Activity Identity Id: " + id.getId());
        
        List<String> props = new ArrayList();
        props.add("id");

        Iterator<Object[]> it = context.search(ApplicationActivity.class, qo, props);
        while(it.hasNext()){
            Object[] activity = it.next();
            String activityId = (String)activity[0];
            
            if (log.isDebugEnabled())
                log.debug("Found Activity Violation: Identity [" + id + 
                          "] Activity [" + activityId + "] ");
            
            addViolation(id, policy, con, activityId);
        }
        return;
    }
    
    /**
     * Traverse a filter hierarchy removing ignoreCase flags.
     * See bug 27952 for why, we can just let the new HQLFilterVisitor
     * figure it out.
     */
    private void stripIgnoreCase(Filter f) {
        if (f instanceof Filter.LeafFilter) {
            Filter.LeafFilter lf = (Filter.LeafFilter)f;
            lf.setIgnoreCase(false);
        }
        else if (f instanceof Filter.CompositeFilter) {
            Filter.CompositeFilter cf = (Filter.CompositeFilter)f;
            List<Filter> children = cf.getChildren();
            for (Filter child : Util.iterate(children)) {
                stripIgnoreCase(child);
            }
        }
    }


    private void addViolation(PolicyViolation v) {
        if (violations == null)
            violations = new ArrayList<PolicyViolation>();
        violations.add(v);
    }
    
    private void addViolation(Identity id, Policy p, ActivityConstraint c, String activityId) {

        PolicyViolation v = new PolicyViolation();
        v.setStatus(PolicyViolation.Status.Open);
        v.setIdentity(id);
        v.setPolicy(p);
        v.setConstraint(c);
        v.setAlertable(isSevere(p, c));
        v.setActivityId(activityId);
        v.setRenderer(DETAILS_RENDERER);
        v.setOwner(p.getViolationOwnerForIdentity(this.context, id, c));

        // allow a rule to post-process the violation
        v = formatViolation(context, id, p, c, v);

        addViolation(v);
    }
    
    private boolean isSevere(Policy p, ActivityConstraint c) {
        return true;
    }


    /**
     * @return the violations
     */
    public List<PolicyViolation> getViolations() {
        return violations;
    }


    /**
     * @param violations the violations to set
     */
    public void setViolations(List<PolicyViolation> violations) {
        this.violations = violations;
    }

}
