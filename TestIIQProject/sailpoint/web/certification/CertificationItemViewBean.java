/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseBean;
import sailpoint.web.identity.IdentityDTO;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationItemViewBean extends BaseBean {

    private String itemId;
    private String workItemId;

    private IdentityDTO identity;

    public CertificationItemViewBean(){
        super();

        if (super.getRequestOrSessionParameter("itemId") != null )
            itemId = super.getRequestOrSessionParameter("itemId");
        if (super.getRequestOrSessionParameter("workItemId") != null )
            workItemId = super.getRequestOrSessionParameter("workItemId");
    }

    public String getItemId() {
        return this.itemId;
    }

    public IdentityDTO getIdentity() throws GeneralException{

        if (identity == null){
            CertificationItem item = getCertificationItem();
            if (item == null)
                return null;

            Identity identityObj = null;
            String identityNameOrId = item.getIdentity();
            if (identityNameOrId != null) {
                identityObj = getContext().getObjectByName(Identity.class, identityNameOrId);
            } else {
                identityNameOrId = item.getTargetId();
                identityObj = getContext().getObjectById(Identity.class, identityNameOrId);
            }

            if (identityObj == null)
                return null;

            if (!isAuthorized(item)){
                throw new GeneralException("Access to certification item id:" + itemId + " is denied to user " +
                        getLoggedInUserName() + ". Workitem ID was "+ workItemId);
            }

            identity = new IdentityDTO(identityObj);
        }
        return identity;
    }

    private CertificationItem getCertificationItem() throws GeneralException{
        if (itemId == null)
            return null;

        return getContext().getObjectById(CertificationItem.class, itemId);
    }

    private boolean isAuthorized(CertificationItem item) throws GeneralException{
        Certification cert = item.getCertification();
        return CertificationAuthorizer.isAuthorized(cert, workItemId, this);
    }

}
