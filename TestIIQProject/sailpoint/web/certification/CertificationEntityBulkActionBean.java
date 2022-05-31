/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationItemSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.util.NavigationHistory;


/**
 * A JSF bean for performing bulk actions on a certification entity.
 * 
 * @author Kelly Grizzle
 */
public class CertificationEntityBulkActionBean extends BaseBulkActionBean {

    /**
     * The ID of the certification entity.
     */
    private String entityId;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public CertificationEntityBulkActionBean() throws GeneralException {
        super();

        // Try to pull request parameters from bulk certification request.
        this.entityId = Util.getString(super.getRequestParameter("entityId"));
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ/WRITE PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    @Override
    public Certification getObject() throws GeneralException {
        // If the ID is coming from the bulk action dialog, we need to special-case the id
        String objectId = super.getRequestOrSessionParameter("certificationEntityBulkActionForm:id");
        if (objectId != null) {
            super.setObjectId(objectId);
        }

        return super.getObject();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * We'll allow selecting app remediators for now - this may not be too
     * expensive since we're just dealing with one identity.
     */
    public boolean allowSelectingAppRemediators() {
        return true;
    }

    /**
     * Don't allow selecting the items.
     */
    public boolean getItemsSelectable() {
        return false;
    }

    /**
     * A selector that will not overwrite previous decisions, and only makes
     * bulk decisions on items that are editable (if within a delegation).
     */
    private static class ItemSelector implements CertificationItemSelector {

        private BaseBean baseBean;
        private String workItemId;

        public ItemSelector(BaseBean baseBean, String workItemId) {
            this.baseBean = baseBean;
            this.workItemId = workItemId;
        }

        public boolean matches(CertificationItem item) throws GeneralException {

            // If in a work item, this should only match items that are
            // editable.  Don't worry about this for non-delegations because the
            // delegation get revoked.
            if (null != this.workItemId) {

                // Using a CertificationItemBean since this has the logic to
                // determine if an item is read-only or not.
                CertificationItemBean itemBean =
                    new CertificationItemBean(this.baseBean, item, item.getParent(), this.workItemId);
                if (itemBean.isReadOnly()) {
                    return false;
                }
            }

            // Don't overwrite previous decisions.
            return !item.isActedUpon();
        }
    }

    /**
     * Get the CertificationItemSelector used to filter the bulk certification
     * if there is one.
     */
    CertificationItemSelector getItemSelector() {

        return new ItemSelector(this, this.workItemId);
    }

    /**
     * Return the entity that we're operating on.
     */
    List<? extends AbstractCertificationItem> getItems() throws GeneralException {
        
        List<AbstractCertificationItem> list = new ArrayList<AbstractCertificationItem>();
        list.add(getContext().getObjectById(CertificationEntity.class, this.entityId));
        return list;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to bulk certify.
     */
    public String certify() throws GeneralException
    {
        super.certify(getItems());

        String prev = NavigationHistory.getInstance().back();
        CertificationPreferencesBean certPrefBean  = new CertificationPreferencesBean(getObjectId());

        // does this even need a nav string returned, or can we return ""?
        return (null != prev) ? prev : certPrefBean.getDefaultView();
    }
}
