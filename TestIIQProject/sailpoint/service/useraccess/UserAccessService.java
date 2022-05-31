package sailpoint.service.useraccess;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Class encapsulating functionality related to LCM user access requests.
 *
 */
public class UserAccessService {

    private SailPointContext context;

    /**
     * Constructor
     * @param context SailPointContext
     * @throws GeneralException
     */
    public UserAccessService(SailPointContext context) throws GeneralException {
        if (context == null) {
            throw new GeneralException("context must be defined");
        }

        this.context = context;
    }

    /**
     * Search for User Access, including Roles, Entitlements or a combination of the two.  
     * All options should be contained in the searchOptions.
     * @param searchOptions UserAccessSearchOptions with all the necessary details  
     * @return UserAccessSearchResults with results of the search
     * @throws GeneralException
     */
    public UserAccessSearchResults search(UserAccessSearchOptions searchOptions)
            throws GeneralException {

        return search(new ArrayList<String>(), searchOptions);
    }

    /**
     * Search for User Access, including Roles, Entitlements or a combination of the two.
     * @param searchTerm Term to use for text search
     * @param searchOptions UserAccessSearchOptions with all the necessary details  
     * @return UserAccessSearchResults with results of the search
     * @throws GeneralException
     */
    public UserAccessSearchResults search(String searchTerm, UserAccessSearchOptions searchOptions)
            throws GeneralException {

        List<String> searchTerms = new ArrayList<String>();
        if (!Util.isNullOrEmpty(searchTerm)) {
            searchTerms.add(searchTerm);
        }
        return search(searchTerms, searchOptions);
    }

    /**
     * Search for User Access, including Roles, Entitlements or a combination of the two.
     * @param searchTerms List of query terms
     * @param searchOptions UserAccessSearchOptions with all the necessary details  
     * @return UserAccessSearchResults with results of the search
     * @throws GeneralException
     */
    public UserAccessSearchResults search(List<String> searchTerms, UserAccessSearchOptions searchOptions)
            throws GeneralException {
        if (searchOptions == null) {
            throw new GeneralException("searchOptions must be defined");
        }

        UserAccessSearcher searcher = null;
        if (searchOptions.isCombinedSearch()) {
            searcher = new CombinedSearcher(this.context, searchOptions);
        } else {
            if (searchOptions.isIncluded(UserAccessSearchOptions.ObjectTypes.Role)) {
                searcher = new RoleSearcher(this.context, searchOptions);
            } else if (searchOptions.isIncluded(UserAccessSearchOptions.ObjectTypes.Entitlement)) {
                searcher = new EntitlementSearcher(this.context, searchOptions);
            }
        }

        UserAccessSearchResults results;
        if (searcher == null) {
            results = new UserAccessSearchResults(null, 0);
        } else {
            results = searcher.search(searchTerms);
        }

        return results;
    }

    /**
     * Check if the ObjectType is enabled for the given requester and target identities
     * @param objectType ObjectTypes value to check
     * @param requester Identity making the request
     * @param targetIdentity Identity being targeted. Can be null.
     * @return True if enabled, otherwise false
     * @throws GeneralException
     */
    public boolean isEnabled(UserAccessSearchOptions.ObjectTypes objectType, Identity requester, Identity targetIdentity)
            throws GeneralException {
        
        boolean enabled = false;
        
        UserAccessSearchOptions options = new UserAccessSearchOptions();
        options.setRequester(requester);
        options.setTargetIdentity(targetIdentity);
        
        if (UserAccessSearchOptions.ObjectTypes.Role.equals(objectType)) {
            enabled = new RoleSearcher(this.context, options).isAccessTypeEnabled();
        } else if (UserAccessSearchOptions.ObjectTypes.Entitlement.equals(objectType)) {
            enabled = new EntitlementSearcher(this.context, options).isAccessTypeEnabled();
        }

        return enabled;
    }
}