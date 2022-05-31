/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.List;

import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;

import sailpoint.api.ObjectUtil;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationItemSelector;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.SelfCertificationException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * A JSF bean for performing bulk actions on a certification.
 * 
 * @author Kelly Grizzle
 */
public class CertificationBulkActionBean extends BaseBulkActionBean {

    /**
     * An interface providing the context for this bulk certification request.
     * Ideally, this bean would be managed by some sort of a controlling
     * CertificationPageBean and have this information injected during
     * construction.  If we refactor this later, this interface can be removed
     * or at least supplied by the CertificationPageBean.
     */
    private static interface Context {
    
        /**
         * Return the name of the JSF bean that is listing the certification
         * contents.
         */
        public String getListBeanName();

        /**
         * Return the class of the AbstractCertificationItems that are being
         * bulk certified.
         */
        public Class<? extends AbstractCertificationItem> getItemClass();
    }


    /**
     * The IDs of the AbstractCertificationItems on which the bulk action should
     * be performed.  This is not used actions that occur on "all" items.
     */
    private List<String> selectedItemIds;

    /**
     * The cached bulk action choices, filtered based on which bulk actions are
     * allowed.
     */
    private List<SelectItem> actionChoices;

    /**
     * Whether the certification page is listing entities or items.  This is
     * either pulled from the request or from the certification bean.  This
     * should be removed if we refactor the CertificationBean (see the javadoc
     * for Context.
     */
    private Boolean listEntities;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     * @throws GeneralException 
     */
    public CertificationBulkActionBean() throws GeneralException {
        super();
        
        // Try to pull request parameters from bulk certification request.
        String selectedIds = Util.getString(super.getRequestParameter("selectedItemIds"));
        if (null != selectedIds) {
            this.selectedItemIds = Util.csvToList(selectedIds);
        }

        // If listEntities is on the request, initialize the context.
        String listEntitiesStr = Util.getString(super.getRequestParameter("listEntities"));
        if (null != listEntitiesStr) {
            this.listEntities = Util.atob(listEntitiesStr);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ/WRITE PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    public List<String> getSelectedItemIds() {
        return selectedItemIds;
    }

    public void setSelectedItemIds(List<String> selectedIdentityIds) {
        this.selectedItemIds = selectedIdentityIds;
    }

    public boolean isListEntities() {
        return this.listEntities;
    }

    public void setListEntities(boolean b) {
        this.listEntities = b;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the xhtml include file to display when setting custom entity 
     * fields in bulk as defined in UIConfig.
     */
    public String getBulkCustomEntityFieldsInclude() throws GeneralException {
        return Util.getString(super.getUIConfig().getBulkCustomEntityFieldsInclude());
    }
    /**
     * Get a filtered list of action choices based on whether the system config
     * allows bulk certification or not.
     */
    public List<SelectItem> getActionChoices() throws GeneralException {

        if (null == this.actionChoices) {
            this.actionChoices = new ArrayList<SelectItem>();
            Configuration sysConfig = getContext().getConfiguration();

            if (certDefinition == null) {
                ValueBinding vb =  getFacesContext().getApplication().createValueBinding("#{certification}");
                CertificationBean cert = (CertificationBean) vb.getValue(getFacesContext());
                certDefinition = cert.getObject().getCertificationDefinition(getContext());
                
                if (certDefinition == null) {
                    certDefinition = new CertificationDefinition();
                    certDefinition.initialize(getContext());
                }
            }
            
            boolean allowBulkApprove = certDefinition.isAllowListBulkApprove(getContext());
            
            if (allowBulkApprove) {
                this.actionChoices.add(new SelectItem(CertificationAction.Status.Approved.name(),
                                                      super.getMessage(MessageKeys.CERT_DECISION_APPROVE)));
            }

            boolean allowBulkRevoke = certDefinition.isAllowListBulkRevoke(getContext());
          
            if (allowBulkRevoke) {
                this.actionChoices.add(new SelectItem(CertificationAction.Status.Remediated.name(),
                                                      super.getMessage(MessageKeys.CERT_DECISION_REMEDIATE)));
            }
            
            boolean allowBulkAccountRevoke = certDefinition.isAllowListBulkAccountRevocation(getContext());
            
            if (allowBulkAccountRevoke) {
                this.actionChoices.add(new SelectItem(CertificationAction.Status.RevokeAccount.name(),
                                                      super.getMessage(MessageKeys.CERT_DECISION_REVOKE_ALL_ACCOUNTS)));
            }
            
            boolean allowBulkMitigate = certDefinition.isAllowListBulkMitigate(getContext());

            if (allowBulkMitigate) {
                boolean entityCanBeMitigated = true;
                Certification cert = super.getObject();
                if (null != cert){
                    Certification.Type certType = cert.getType();
                    entityCanBeMitigated = !certType.isType(Certification.Type.AccountGroupMembership,
                            Certification.Type.AccountGroupMembership,
                            Certification.Type.BusinessRoleComposition);
                }
                
                boolean allowExceptions = certDefinition.isAllowExceptions(getContext());
                
                if (entityCanBeMitigated && allowExceptions) {
                    this.actionChoices.add(new SelectItem(CertificationAction.Status.Mitigated.name(),
                                                          super.getMessage(MessageKeys.CERT_DECISION_MITIGATE)));
                }
            }

            boolean allowBulkReassign = certDefinition.isAllowListBulkReassign(getContext());
            
            if (allowBulkReassign) {
                this.actionChoices.add(new SelectItem(CertificationAction.Status.Delegated.name(),
                                                      super.getMessage(MessageKeys.CERT_DECISION_BULK_REASSIGN)));
            }

            // If there are some choices, add an item that says to select and identity.
            if (!this.actionChoices.isEmpty()) {
                this.actionChoices.add(0, new SelectItem("", super.getMessage(MessageKeys.CERT_DECISION_BULK_SELECT_DECISION)));
            }

            if (sysConfig.getBoolean(Configuration.ALLOW_LIST_VIEW_BULK_SAVE_CUSTOM_ENTITY_FIELDS)) {
                this.actionChoices.add(new SelectItem(ACTION_SAVE_ENTITY_CUSTOM_FIELDS,
                                                      super.getMessage(MessageKeys.CERT_DECISION_BULK_SAVE_ENTITY_CUSTOM_FIELDS)));
            }
        }
        
        return this.actionChoices;
    }

    private Context getBulkActionContext() throws GeneralException {

        if (null == this.listEntities) {
            ValueBinding vb =
                getFacesContext().getApplication().createValueBinding("#{certification}");
            CertificationBean cert = (CertificationBean) vb.getValue(getFacesContext());
            this.listEntities = cert.isListEntities();
        }

        return new Context() {
            public Class<? extends AbstractCertificationItem> getItemClass() {
                return (listEntities) ? CertificationEntity.class : CertificationItem.class;
            }

            public String getListBeanName() {
                return (listEntities) ? "certificationEntityList" : "certificationIdentityItemsList";
            }
        };
    }

    /**
     * Get all AbstractCertificationItems that match the filter on the
     * certification page.
     */
    @SuppressWarnings("unchecked")
    private List<AbstractCertificationItem> getAllItems()
        throws GeneralException {

        List<AbstractCertificationItem> items =  null;

        AbstractCertificationContentsListBean certListBean = getListBean();
        if (null != certListBean) {
            items = certListBean.getObjects();
        }
        
        //Need to pull out any items that may have been deselected on the live grid.
        //These will be passed in on the selectedItemsIds list.
        
        //Create a new list so that if we remove items from the list, we don't affect the other bean
        List<AbstractCertificationItem> filteredItems = new ArrayList<AbstractCertificationItem>();
        for(AbstractCertificationItem certItem : items) {
            if(selectedItemIds==null || !selectedItemIds.contains(certItem.getId())) {
                filteredItems.add(certItem);
            }            
        }

        return filteredItems;
    }

    /**
     * Return the AbstractCertificationContentsListBean used to list the identities.
     */
    @SuppressWarnings("unchecked")
    private AbstractCertificationContentsListBean getListBean() throws GeneralException {
        String beanName = this.getBulkActionContext().getListBeanName();
        ValueBinding vb =
            getFacesContext().getApplication().createValueBinding("#{" + beanName + "}");
         return (AbstractCertificationContentsListBean) vb.getValue(getFacesContext());
    }

    /**
     * Get all CertificationIdentities that are selected.
     */
    private List<AbstractCertificationItem> getSelectedItems()
        throws GeneralException {
    
        List<AbstractCertificationItem> items =  null;
        if ((null != this.selectedItemIds) && !this.selectedItemIds.isEmpty()) {

            Class<? extends AbstractCertificationItem> clazz = this.getBulkActionContext().getItemClass();
            for (String id : this.selectedItemIds) {
                AbstractCertificationItem item = getContext().getObjectById(clazz, id);
                if (null != item) {
                    if (null == items) {
                        items = new ArrayList<AbstractCertificationItem>();
                    }
                    items.add(item);
                }
            }
        }
            
        return items;
    }

    /**
     * Return whether the filter can be applied to certification items to
     * selectively bulk certify.
     */
    public boolean getItemsSelectable() {
        return (null != this.getItemSelector());
    }

    /**
     * Get the CertificationItemSelector used to filter the bulk certification
     * if there is one.
     */
    @SuppressWarnings("unchecked")
    CertificationItemSelector getItemSelector() {
        
        CertificationItemSelector selector = null;

        try {
            AbstractCertificationContentsListBean listBean = getListBean();
            if (null != listBean) {
                selector = listBean.getCertificationItemSelector();
            }
        }
        catch (GeneralException e) {
            throw new RuntimeException(e);
        }
        
        return selector;
    }

    /**
     * Don't allow selecting app remediators for now - this could be really
     * expensive depending on the number of identities in the list.
     */
    public boolean allowSelectingAppRemediators() {
        return false;
    }

    /**
     * Return the entities that will be affected.  Note that this isn't quite
     * right, since we're only returning the selected entities.  Currently, we
     * don't have a way of knowing whether the action will be performed on all
     * entities or only the selected entities - this is determined by the action
     * that is called.
     */
    List<? extends AbstractCertificationItem> getItems() throws GeneralException {
        
        return getSelectedItems();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Action to bulk certify all items in a livegrid using the action property
     * on this bean.
     */
    public String certifyAll() throws GeneralException
    {
        List<AbstractCertificationItem> items = getAllItems();
        certify(items);
        return "";
    }

    /**
     * Action to bulk certify (approve) all visible, selected items using the
     * action property on this bean.
     */
    public String certify() throws GeneralException
    {
        List<AbstractCertificationItem> items = getSelectedItems();
        certify(items);
        
        return "";
    }

    /**
     * After a reassignment, the contents for the filter select lists can
     * change.  To avoid a JSF validation error, request that the filters get
     * reset.
     */
    void resetCertificationFilters() {
        AbstractCertificationContentsListBean.setResetFiltersFlag(this);
    }
    
    /**
     * Action to bulk reassign all items in the entire livegrid, not just on the
     * screen.
     */
    public String reassignAll() throws GeneralException
    {
        List<AbstractCertificationItem> items = getAllItems();
        reassign(items);
        resetCertificationFilters();
        return "";
    }

    /**
     * Action to bulk reassign all currently visible, selected items.
     */
    public String reassign() throws GeneralException {

        List<AbstractCertificationItem> items = getSelectedItems();
        reassign(items);
        resetCertificationFilters();
        return null;
    }

    /**
     * Bulk reassign the given certification items using the bulk reassignment
     * properties on this bean.
     * 
     * @param  items  The AbstractCertificationItems to bulk reassign.
     */
    private void reassign(List<AbstractCertificationItem> items)
        throws GeneralException {

        if (null != items) {

            Identity me = getLoggedInUser();
            Identity newOwner =
                getContext().getObjectByName(Identity.class, this.action.getOwnerName());
            try {
                Certification cert = getObject();
                cert.bulkReassign(me, items, newOwner,
                                  this.action.getDescription(),
                                  this.action.getComments(),
                                  getContext().getConfiguration());
                CertificationBean.saveAndRefreshCertification(cert, getContext(), this);

                // Refreshing with bulk reassignments will decache everything
                // in the session.  Reattach the cert and store it in the bean.
                super.setObject(ObjectUtil.reattach(getContext(), cert));
            }
            catch (SelfCertificationException e) {
                super.addMessageToSession(new Message(Message.Type.Error,
                                                      MessageKeys.ERR_CANNOT_SELF_CERTIFY_REASSIGN,
                                                      e.getSelfCertifier().getDisplayableName()));
            }
        }
    }

    /**
     * Action to save the custom entity fields for all entities.
     */
    public String saveAllEntityCustomFields() throws GeneralException
    {
        List<AbstractCertificationItem> items = getAllItems();
        saveCustomFields(items, true);
        return "";
    }

    /**
     * Action to save the custom entity fields for all visible selected entities.
     */
    public String saveEntityCustomFields() throws GeneralException
    {
        List<AbstractCertificationItem> items = getSelectedItems();
        saveCustomFields(items, true);
        return "";
    }
}
