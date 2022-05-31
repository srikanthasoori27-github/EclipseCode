/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Collection;
import java.util.List;

import sailpoint.api.ObjectUtil;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * General auth check methods for access to IdentityRequest objects
 * 
 * @author patrick.jeong
 *
 */
public class IdentityRequestAuthorizer {
	
	/**
	 * Authorize access to identity request
	 * 
	 * @param requestObject IdentityRequest to authorize
	 * @param context UserContext
	 * @return True if authorized, otherwise false
	 * @throws GeneralException
	 */
	public static boolean isAuthorized(IdentityRequest requestObject, UserContext context) throws GeneralException {
		 if (requestObject == null)
		        return true;
		    
		 Identity loggedInUser  = context.getLoggedInUser();
		 
		 List<Capability> caps = loggedInUser.getCapabilityManager().getEffectiveCapabilities();
		 Collection<String> rights = loggedInUser.getCapabilityManager().getEffectiveFlattenedRights();
		 
	    // allow sysadmin access
	    if (Capability.hasSystemAdministrator(caps)) 
	    	return true;
	
	    if (Authorizer.hasAccess(caps, rights, SPRight.FullAccessIdentityRequest))
        	return true;
	    
	    if (Authorizer.hasAccess(caps, rights, SPRight.ViewIdentityRequest))
	        return true;
	    
	    // allow owner access
	    if (requestObject.getOwner() != null && isOwner(requestObject.getOwner(), loggedInUser))
	        return true;
	    
	    // allow requester access
	    if (requestObject.getRequesterId() != null && loggedInUser.getId().equals(requestObject.getRequesterId()))
	        return true;

	    // allow requester workgroup access (Rapid Setup added workgroups as the requester of IdentityRequests)
	    if (requestObject.getRequesterId() != null && isUserInRequesterWorkgroup(requestObject.getRequesterId(), loggedInUser))
	        return true;

	    // allow requestee access to details
	    if (requestObject.getTargetId() != null && loggedInUser.getId().equals(requestObject.getTargetId()))
	        return true;
	    
	    // if user is an approval owner allow access
	    String requestId = requestObject.getName();
        Filter requestIdFilter = Filter.eq("identityRequestId", requestId);

        QueryOptions qoWorkItem = new QueryOptions();
        qoWorkItem.add(requestIdFilter);
        qoWorkItem.add(ObjectUtil.getOwnerFilterForIdentity(loggedInUser));
        if (context.getContext().countObjects(WorkItem.class, qoWorkItem) > 0) {
            return true;
        }

        //IIQETN-4850 :- Since WorkItemArchive don't store owner id, we have to make a different 
        //QueryOptions to allow to retrieve the correct workItem after being approved.
        QueryOptions qoWorkItemArc = new QueryOptions();
        qoWorkItemArc.add(requestIdFilter);
        qoWorkItemArc.add(ObjectUtil.getOwnerNameArchiveFilterForIdentity(loggedInUser));
        if (context.getContext().countObjects(WorkItemArchive.class, qoWorkItemArc) > 0) {
            return true;
        }
		
		// check identity request item approver
		List<IdentityRequestItem> items = requestObject.getItems();
        for (IdentityRequestItem item : Util.safeIterable(items)) {
            if (isOwner(item.getOwnerName(), loggedInUser) || 
                isOwner(item.getApproverName(), loggedInUser) ||
                isOwner(item.getOwner(), loggedInUser)) {
                return true;
            }
        }
		
	    return false;
	}

    /**
     * Authorize access to IdentityRequest
     *
     * @param requestId ID of IdentityRequest to authorize
     * @param context UserContext
     * @return True if authorized, otherwise false
     * @throws GeneralException
     */
	public static boolean isAuthorized(String requestId, UserContext context) throws GeneralException {
		if (requestId == null) {
			return true; // sure why not
		}
		
		IdentityRequest requestObject = context.getContext().getObjectById(IdentityRequest.class, requestId);
		
		return isAuthorized(requestObject, context);
	}
	
	/**
	 * Check owner name against user, including user's workgroups
	 * @param ownerName  Name of the owner of the object in question
	 * @param loggedInUser Identity to check for ownership
	 * @return If Identity or her workgroups matches the owner name
	 */
	public static boolean isOwner(String ownerName, Identity loggedInUser) {
	    //Check user ownage
	    if (loggedInUser.getName().equals(ownerName)) {
	        return true;
	    }

	    //Check user in workgroup ownage
	    List<Identity> workgroups = (loggedInUser != null) ? loggedInUser.getWorkgroups() : null;
	    if (!Util.isEmpty(workgroups) )  {
	        for ( Identity workgroup : workgroups ) {
	            if ( workgroup.getName().equals(ownerName) ) {
	                return true;
	            }
	        }
	    }

	    return false;
	}
	
	/*
	 * Additional isOwner method that allows us to not depend on a potentially-changed
	 * name
	 * @param owner
	 * @param loggedInUser
	 */
	public static boolean isOwner(Identity owner, Identity loggedInUser) {
	    if (owner != null && loggedInUser != null) {
	        if (owner.getId().equals(loggedInUser.getId())) {
	            return true;
	        }
	        
	        if (owner.isWorkgroup()) {
	            List<Identity> workgroups = loggedInUser.getWorkgroups();
	            if(!Util.isEmpty(workgroups)) {
	                if(workgroups.contains(owner)) {
	                    return true;
	                }
	            }
	        }
	    }

	    return false;
	}

    /**
     * Check requester against users workgroups
     * @param requesterId the requesterId of the IdentityRequest
     * @param user loggedInUser
     * @return If any of the Identity workgroups matches the requester workgroup
     */
    private static boolean isUserInRequesterWorkgroup(String requesterId, Identity user) {
        if (requesterId != null && user != null) {
            //Check requester workgroup against users workgroups
            List<Identity> workgroups = (user != null) ? user.getWorkgroups() : null;
            if (!Util.isEmpty(workgroups) )  {
                for ( Identity workgroup : workgroups ) {
                    if (workgroup.getId().equals(requesterId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
