/**
 * 
 */
package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleIndex;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

/**
 * @author peter.holcomb
 *
 */
public class RoleIndexRefreshExecutor extends AbstractTaskExecutor {

    public static final String RET_ROLES = "totalRoles";
    
    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Normally set if we're running from a background task.
     */
    TaskMonitor _monitor;

    /**
     * Result object we're leaving things in.
     */
    TaskResult _result;
    
    private boolean terminate = false;
    
    /**
     * 
     */
    public RoleIndexRefreshExecutor() { }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#execute(sailpoint.api.SailPointContext, sailpoint.object.TaskSchedule, sailpoint.object.TaskResult, sailpoint.object.Attributes)
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
    throws Exception {
        _context = context;
        _result = result;
        _monitor = new TaskMonitor(context, result);

        /** Delete old indexes **/
        deleteRoleIndexes();
  
        IncrementalObjectIterator<Bundle> roles =
            new IncrementalObjectIterator<Bundle>(_context, Bundle.class, (QueryOptions) null);
        int totalRoles = 0;
        while(roles.hasNext() && !terminate) {
            totalRoles++;
            Bundle role = roles.next();
            _monitor.updateProgress("Refreshing Index for: "+role.getName());
            RoleIndex index = new RoleIndex(role);
            
            index.setAssociatedToRole(isRequiredOrPermitted(role));

            index.setAssignedCount(getCount(role, "assigned")); 
            index.setDetectedCount(getCount(role, "detected")); 
            
            index.setEntitlementCountInheritance(getEntitlementCount(role, true));
            index.setEntitlementCount(getEntitlementCount(role, false));
            
            index.setLastCertifiedMembership(getLastCertifiedDate(role, Certification.Type.BusinessRoleMembership));
            index.setLastCertifiedComposition(getLastCertifiedDate(role, Certification.Type.BusinessRoleComposition));
            
            index.setLastAssigned(getLastAssignedDate(role));
            index.setBundle(role);
            role.setRoleIndex(index);

            context.saveObject(role);
            context.commitTransaction();
            context.decache();
        }

        result.setAttribute(RET_ROLES, totalRoles);
        
        if (terminate) {
        	result.setTerminated(true);
        }
    }
    
    /** Sets the date that the role was last assigned to an identity **/
    private Date getLastAssignedDate(Bundle role) throws GeneralException{
        Date lastAssigned = null;
        
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.containsAll("assignedRoles", Arrays.asList(role)));

        IncrementalObjectIterator<Identity> identities =
            new IncrementalObjectIterator<Identity>(_context, Identity.class, qo);
        while(identities.hasNext()) {
            Identity identity = identities.next();

            // Loop through all the assignments for the role and take the latest assign date found.
            List<RoleAssignment> assignments = identity.getRoleAssignments(role);
            if (assignments != null) {
                for (RoleAssignment assignment : assignments) {
                    if (lastAssigned == null || lastAssigned.before(assignment.getDate())) {
                        lastAssigned = assignment.getDate();
                    }
                }
            }

            _context.decache();
        }
        
        return lastAssigned;
    }
    
    /** Queries for the most recent certification entity on this role and returns the created date **/
    private Date getLastCertifiedDate(Bundle role, Certification.Type type) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("certification.type", type));
        qo.add(Filter.notnull("completed"));
        if(type.equals(Certification.Type.BusinessRoleComposition))
            qo.add(Filter.eq("targetName", role.getName()));
        else
            qo.add(Filter.eq("items.bundle", role.getName()));
        
        qo.setOrderBy("completed");
        qo.setOrderAscending(false);
        qo.setResultLimit(1);
        
        Iterator<CertificationEntity> entities = _context.search(CertificationEntity.class, qo);
        if(entities.hasNext()){
            CertificationEntity entity = entities.next();
            return entity.getCompleted();
        }
        return null;
    }

    /** Counts the number of entitlements on this role **/
    private int getEntitlementCount(Bundle role, boolean includeInheritance) throws GeneralException {
        int entitlementCount = 0;
        List<Profile> profiles = new ArrayList<Profile>();
        
        profiles.addAll(role.getProfiles());
        
        if(includeInheritance) {
            /** Get the roles that inherit from this role **/
            for(Bundle bundle : role.getInheritance()) {
                if(bundle.getProfiles()!=null){
                    profiles.addAll(bundle.getProfiles());
                }
            }
        }
        
        for(Profile profile: profiles) {
            if(profile!=null && profile.getConstraints()!=null)
                entitlementCount+=profile.getConstraints().size();
            
            if(profile!=null && profile.getPermissions()!=null) 
                entitlementCount+=profile.getPermissions().size();
        }

        return entitlementCount;
    }

    /** Counts the number of identities that have this role assigned or detected **/
    private int getCount(Bundle role, String type) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        if(type.equals("assigned"))
            qo.add(Filter.containsAll("assignedRoles", Arrays.asList(role)));
        else
            qo.add(Filter.containsAll("bundles", Arrays.asList(role)));

        return _context.countObjects(Identity.class, qo);
    }

    /**
     * Return true if the given role is required or permitted by another role.
     */
    private boolean isRequiredOrPermitted(Bundle bundle) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("permits.id", bundle.getId()));
        int numRoles = _context.countObjects(Bundle.class, qo);

        // Could use an OR query here, but large role models don't perform well
        // like this.  Better to do two separate queries if we need to.
        if (0 == numRoles) {
            qo = new QueryOptions();
            qo.add(Filter.eq("requirements.id", bundle.getId()));
            numRoles = _context.countObjects(Bundle.class, qo);
        }
        
        return (numRoles > 0);
    }
    
    public void deleteRoleIndexes() throws GeneralException {

        Terminator t = new Terminator(_context);
        
        IncrementalObjectIterator<RoleIndex> it =
            new IncrementalObjectIterator<RoleIndex>(_context, RoleIndex.class, (QueryOptions) null);
        while(it.hasNext()) {
            t.deleteObject(it.next());
            _context.decache();
        }
    }
    
    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#terminate()
     */
    public boolean terminate() {
    	terminate = true;
        return true;
    }

}
