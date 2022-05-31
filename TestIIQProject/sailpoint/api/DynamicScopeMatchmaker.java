/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Capability;
import sailpoint.object.DynamicScope;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This class centralizes the business logic that determines if a DynamicScope applies to an Identity.
 * @author chris.annino
 *
 */
public class DynamicScopeMatchmaker {

    private static final Log log = LogFactory.getLog(DynamicScopeMatchmaker.class);
    private SailPointContext context;
    
    
    public DynamicScopeMatchmaker(SailPointContext context) {
        this.context = context;
    }
    
    /**
     * iterates though the list of dynamic scopes determining what scopes match the user
     * and returns a list of scope id's that match.
     * @param identity
     * @return a list of dynamic scope ids that apply for this user
     * @throws GeneralException 
     */
    public List<String> getMatches(Identity identity) throws GeneralException {
        List<String> matches = new ArrayList<String>();
        
        List<DynamicScope> dynaScopes = context.getObjects(DynamicScope.class);
        for (DynamicScope ds : Util.safeIterable(dynaScopes)) {
            if (isMatch(ds, identity)) {
                matches.add(ds.getName());
            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug(String.format("found %s matches for identity %s", matches.size(), (identity == null) ? "null" : identity.getName()));
        }
        return matches;
    }
    
    /**
     * This invokes isMatch(DynamicScope dynamicScope, Identity identity, boolean includeAllSystemAdmins)
     * with includeAllSystemAdmins set to true.
     *
     * @see #isMatch(DynamicScope, Identity, boolean)
     *
     * @param dynamicScope
     * @param identity
     * @return
     * @throws GeneralException
     */
    public boolean isMatch(DynamicScope dynamicScope, Identity identity) throws GeneralException {
        return isMatch(dynamicScope, identity, true);
    }
        
    /**
     * we evaluate the what scopes are associated with the identity in the following order : 
     * <ol>
     *   <li>return true if the identity has capability SystemAdministrator and includeAllSystemAdmins is true</li>
     *   <li>return false if the identity is part of the exclusion list</li>
     *   <li>return true if allowAll is set on the dynamic scope</li>
     *   <li>return true if the identity is part of the inclusion list</li>
     *   <li>return the result of the IdentitySelector if one is defined</li>
     *   <li>return false if none of the above evaluate to true</li>
     * </ol>
     * @param dynamicScope
     * @param identity
     * @param includeAllSystemAdmins if set to true, this method will return true if the identity has the SystemAdministrator
     *          capability (regardless of if they are a member of this DynamicScope).  If set to false, this method will only
     *          return true if the identity is a member of this DynamicScope.
     * @return
     * @throws GeneralException
     */
    public boolean isMatch(DynamicScope dynamicScope, Identity identity, boolean includeAllSystemAdmins) throws GeneralException {
        if (dynamicScope == null || identity == null) {
            return false;
        }
        
        if (includeAllSystemAdmins && identity.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
            return true;
        }
        
        List<Identity> exclusions = dynamicScope.getExclusions();
        if (isIdentityWorkgroupMatch(exclusions, identity)) {
            return false;
        }
        
        if (dynamicScope.isAllowAll()) {
            return true;
        }
        
        List<Identity> inclusions = dynamicScope.getInclusions();
        if (isIdentityWorkgroupMatch(inclusions, identity)) {
            return true;
        }
        
        if (dynamicScope.getSelector() != null) {
            Matchmaker match = new Matchmaker(context);
            return match.isMatch(dynamicScope.getSelector(), identity); 
        }
        
        return false;
    }
    
    /**
     * first checks to see if identity is included in the parameter listIdentities.  We aren't worried about a database hit, 
     * as SailPointObject.equals() will compare the name (already read from the DB) and the list of identities is coming from an inclusion/exclusion list that should
     * be small.  The next condition checked is if the identity is in the same workgroup as a workgroup in the list.  We do this by 
     * creating a list of the identity.workgroup.ids (avoiding a heavy database hit) and checking if any ids in the listIdentities match.
     * @param listIdentities
     * @param identity
     * @return
     * @throws GeneralException
     */
    private boolean isIdentityWorkgroupMatch(List<Identity> listIdentities, Identity identity) throws GeneralException {
        if (!Util.isEmpty(listIdentities)) {
            
            if (listIdentities.contains(identity)) {
                return true;
            }
            
            // create a list of workgroup ids which this identity is a member of
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("id", identity.getId()));
            Iterator<Object[]> iterate = context.search(Identity.class, ops, "workgroups.id");
            
            Set<String> workgroupIds = new HashSet<String>();
            while (iterate != null && iterate.hasNext()) {
                Object[] idResult = iterate.next();
                if (idResult != null && idResult.length == 1) {
                    workgroupIds.add((String)idResult[0]);
                }
            }
            // now check to see if listIdentities is a part of the workgroups
            for (Identity tempIdentity : listIdentities) {
                if (tempIdentity != null && workgroupIds.contains(tempIdentity.getId())) {
                    return true;
                }
            }
            
        }
        
        return false;
    }

    /**
     * Determine if a given Identity is part of a given PopulationRequestAuthority.
     * If currUser is SysAdmin, return true if ident exists
     * @param currUser Logged in user
     * @param ident Identity to test membership
     * @param authority PopulationRequestAuthority in question
     * @return true if the identity belongs to the PopulationRequestAuthority
     * @throws GeneralException
     */
    public boolean isMember(Identity currUser, Identity ident, DynamicScope.PopulationRequestAuthority authority)
        throws GeneralException {
        LCMConfigService configService = new LCMConfigService(context);


        QueryOptions ops = configService.buildFilter(currUser, authority);

        if (ops != null) {
            ops.add(Filter.eq("id", ident.getId()));
            return context.countObjects(Identity.class, ops) > 0 ? true : false;
        } else {
            return false;
        }

    }
}
