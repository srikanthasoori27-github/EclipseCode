/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the specified user has access to the specified certification.
 * 
 * @author jeff.upton
 */
public class CertificationAuthorizer implements Authorizer {
	
	private Certification _certification;
	private String _workItemId;
	private WorkItem _workItem;
	
	public CertificationAuthorizer(Certification certification) {
		_certification = certification;
	}
	
	public CertificationAuthorizer(Certification certification, WorkItem workItem) {
		_certification = certification;
		_workItem = workItem;
	}

	public CertificationAuthorizer(Certification certification, String workItemId) {
		_certification = certification;
		_workItemId = workItemId;
	}

	public void authorize(UserContext userContext) throws GeneralException {
		if (_workItem != null) {
			if (!isAuthorized(_certification, _workItem, userContext)) {
				throw new UnauthorizedAccessException(new Message(MessageKeys.CERT_UNAUTHORIZED_ACCESS_EXCEPTION));
			}
		} else {		
			if (!isAuthorized(_certification, _workItemId, userContext)) {
				throw new UnauthorizedAccessException(new Message(MessageKeys.CERT_UNAUTHORIZED_ACCESS_EXCEPTION));
			}
		}
	}	
	
	public static boolean isAuthorized(Certification cert, String workItemId, UserContext userContext) throws GeneralException {
		WorkItem workItem = null;
		if (workItemId != null) {
			workItem = userContext.getContext().getObjectById(WorkItem.class, workItemId);
		}
		
		return isAuthorized(cert, workItem, userContext);
	}
	
	public static boolean isAuthorized(Certification cert, WorkItem workItem, UserContext userContext) throws GeneralException {
		if (cert == null)
            return true;

        // This lets in system administrators or users that can read
        // (FullAccessCertifications) or write (CertifyAllCertifications).
        // Over the years (FullAccessCertifications) has mutated into read/write ability, creating ViewGroupCertification
        // for true read only rights.
        if (sailpoint.web.Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(),
                                 userContext.getLoggedInUserRights(),
                                 new String[] { SPRight.CertifyAllCertifications,
                                                SPRight.FullAccessCertifications,
                                                SPRight.ViewGroupCertification,
                                                SPRight.FullAccessWorkItems })) {
            return true;
        }

        if (isCertifier(userContext.getLoggedInUser(), cert.getCertifiers()))
            return true;

        if (isReassignmentParentOwner(userContext.getLoggedInUser(), cert))
            return true;

        if (isCreatorOrOwner(userContext.getLoggedInUser(), cert.getCreator()))
            return true;

        if (workItem != null) {
            if (cert.getId().equals(workItem.getCertification()) && isCreatorOrOwner(userContext.getLoggedInUser(), workItem.getOwner().getName()))
                return true;
        }

        // If the certification has been assigned a scope, and the
        // identity controls the scope, the identity has access
        // to the certification.
        if (userContext.isScopingEnabled()) {
            // Usually performed by super.isAuthorized(), but that doesn't work
            // since we have set skipAuthorization to true.
            if (isCertificationInUserScope(userContext.getContext(), cert.getId())) {
                return true;
            }
        }

        // Check for outbox work items..
        if (hasRequestedWorkItems(userContext.getContext(), userContext.getLoggedInUser(), cert.getId())) {
            return true;
        }
        
        // check certification hierarchy to determine if user has access to one of the parents.
        return (cert.getParent() != null && isAuthorized(cert.getParent(), workItem, userContext));
	}

    private static boolean hasRequestedWorkItems(SailPointContext context, Identity requester, String certId) throws GeneralException {

        if (requester == null) {
            return false;
        }
        
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("certification", certId));
        queryOptions.add(Filter.eq("requester.id", requester.getId()));
        
        if (context.countObjects(WorkItem.class, queryOptions) > 0) {
            return true;
        }
        
        if (!Util.isEmpty(requester.getWorkgroups())) {     
            queryOptions = new QueryOptions();
            queryOptions.add(Filter.eq("certification", certId));
            queryOptions.add(Filter.in("requester", requester.getWorkgroups()));
            
            return context.countObjects(WorkItem.class, queryOptions) > 0;
        } else {
            return false;
        }
    }
	
	/**
     * Return whether the logged in user is a "certification admin".
     */
    public static boolean isCertificationAdmin(UserContext baseBean) {
        return isCertificationAdmin(baseBean.getLoggedInUserCapabilities(), 
                baseBean.getLoggedInUserRights());
    }
    
    public static boolean isCertificationAdmin(Identity identity) {
        return isCertificationAdmin(identity.getCapabilities(), 
                identity.getCapabilityManager().getEffectiveFlattenedRights());
    }
    
    public static boolean isCertificationAdmin(List<Capability> capabilities,
                                               Collection<String> rights) {
        return sailpoint.web.Authorizer.hasAccess(capabilities, rights, 
                        new String[] { SPRight.CertifyAllCertifications });
    }

    /**
     * Return whether the given logged in user is a certifier of the given
     * cert's parent (if it is a reassignment).
     */
    public static boolean isReassignmentParentOwner(Identity loggedIn, Certification cert) {
        Certification parent = cert.getParent();
        if ( ( cert.isBulkReassignment() ) && ( parent != null ) ) {
            List<String> certifiers = parent.getCertifiers();
            if ( isCertifier(loggedIn, certifiers) )
                return true;
        }
        return false;
    }

    public static boolean isCertifier( Identity user, List<String> certifiers) {
        if ( ( certifiers != null ) && ( user != null ) ) {
            if ( ( certifiers.contains(user.getName()) ) || ( isAssignedWorkgroup(user, certifiers) ) )
                return true;
        }
        return false;
    }

    public static boolean isCreatorOrOwner(Identity user, String creator) {
        if ( ( creator != null ) && ( user != null ) ) {
            if ( creator.equals(user.getName()) )
                return true;

            if ( isAssignedWorkgroup(user, creator) )
                return true;
        }
        return false;
    }

    public static boolean isAssignedWorkgroup(Identity user, String target) {
         List<String> list = new ArrayList<String>();
         list.add(target);
         return isAssignedWorkgroup(user, list);
    }

    public static boolean isAssignedWorkgroup(Identity user, List<String> targetList ) {
        List<Identity> workgroups = (user != null ) ? user.getWorkgroups() : null;
        if ( Util.size(workgroups) > 0 ) {
            for ( Identity wg : workgroups ) {
                String name = wg.getName();
                if ( targetList.contains(name) )
                    return true;
            }
        }
        return false;
    }


    /**
     * Checks the given object and ensures that the authenticated user
     * controls the scope assigned to the certification.
     */
    private static boolean isCertificationInUserScope(SailPointContext context, String id) throws GeneralException{

        // this is a new object, no auth needed
        if (id==null || "".equals(id))
            return true;

        QueryOptions scopingOptions = new QueryOptions();
        scopingOptions.setScopeResults(true);
        scopingOptions.add(Filter.eq("id", id));
        scopingOptions.setUnscopedGloballyAccessible(false);

        int count = context.countObjects(Certification.class, scopingOptions);
        return (count > 0);
    }
}
