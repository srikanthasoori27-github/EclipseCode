/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.managedattribute;

import sailpoint.integration.ListResult;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.managedattribute.AccountGroupMemberListService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Util;

import javax.ws.rs.GET;

/**
 * Resource to list account group members for a managed attribute
 */
public class AccountGroupMemberListResource extends BaseListResource implements BaseListServiceContext {
    
    private static final String COLUMNS_KEY = "uiAccountGroupMemberTableColumns";
    
    private ManagedAttribute managedAttribute;
    
    public AccountGroupMemberListResource(ManagedAttribute managedAttribute, BaseResource parent) throws GeneralException {
        super(parent);

        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }

        this.managedAttribute = managedAttribute;
    }

    /**
     * Get the list of account group members
     * @return ListResult of IdentitySummaryDTO representing identities in the account group
     * @throws GeneralException
     */
    @GET
    public ListResult getMembers() throws GeneralException {
        AccountGroupMemberListService listService = new AccountGroupMemberListService(getContext(), this, new BaseListResourceColumnSelector(COLUMNS_KEY));
        return listService.getMembers(managedAttribute);
    }

    /**
     * Override to provide default sort by if none is given.
     */
    @Override
    public String getSortBy() {
        return Util.isNullOrEmpty(this.sortBy) ? "identity.name" : this.sortBy;
    }
}
