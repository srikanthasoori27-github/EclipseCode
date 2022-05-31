/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.application.FacesMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.CertificationItemSelector;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.SailPointObject;
import sailpoint.object.CertificationAction.Status;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * An abstract JSF bean for performing bulk actions on a certification or
 * certification entity.
 * 
 * @author Kelly Grizzle
 */
public abstract class BaseBulkActionBean extends BaseCertificationActionBean {

    private static final Log log = LogFactory.getLog(BaseBulkActionBean.class);

    /**
     * Constant for a pseudo-action to save custom fields on certification
     * entities.  This value can be passed in the bulkAction request parameter
     * to indicate that we should save the custom fields.
     */
    public static final String ACTION_SAVE_ENTITY_CUSTOM_FIELDS =
        "saveEntityCustomFields";
    

    /**
     * Indicates whether we're saving custom entity fields.
     */
    boolean saveEntityCustomFields;

    /**
     * The ID of the work item this is coming from if the entity is delegated.
     */
    String workItemId;

    /**
     * A CertificationAction used to capture properties about the bulk action
     * to perform.  Not using the CertificationActionBean because we don't need
     * all of the display properties.  This is null if we're saving custom
     * entity fields.
     */
    CertificationAction action;

    /**
     * BUG #5126: The status of the bulk action being performed.  This field
     * should not be necessary since <code>action</code> is holding the status
     * already and should be keeping up with this via a t:saveState tag.
     * However, a customer has seen this value be null.  We can't reproduce, but
     * to prevent this problem are adding this field as a fallback.  This is
     * only used when bulk certifying if the action.getStatus() is null.
     * Hopefully at some point we can find the root cause of this issue and
     * remove this field.
     */
    CertificationAction.Status actionStatus;
    
    /**
     * Whether to force bulk certifying all certification items on the filtered
     * identities if there is a CertificationItemSelector available.
     */
    boolean bulkCertifyAllItems;

    private String custom1;
    private String custom2;
    private Map<String,Object> customMap;
    private boolean overwriteCustomFields = true;
    
    protected CertificationDefinition certDefinition;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     * @throws GeneralException 
     */
    public BaseBulkActionBean() throws GeneralException {
        super();

        setScope(Certification.class);
        setStoredOnSession(false);

        // Certification authorization is a bit complex.  We'll do this
        // ourselves in isAuthorized().
        super.setSkipAuthorization(true);
        
        // Try to pull request parameters from bulk certification request.
        String certId = Util.getString(getRequestParameter("certificationId"));
        String bulkStatusStr = Util.getString(getRequestParameter("bulkAction"));
        this.workItemId = Util.getString(getRequestParameter("workItemId"));

        if (null != certId) {
            super.setObjectId(certId);
            if (null != bulkStatusStr) {

                if (ACTION_SAVE_ENTITY_CUSTOM_FIELDS.equals(bulkStatusStr)) {
                    this.saveEntityCustomFields = true;
                }
                else {
                    CertificationAction.Status status =
                        CertificationAction.Status.valueOf(bulkStatusStr);
    
                    // Turn a revoke account into a special remediation.
                    boolean isRevokeAccount = false;
                    if (CertificationAction.Status.RevokeAccount.equals(status)) {
                        status = CertificationAction.Status.Remediated;
                        isRevokeAccount = true;
                    }
                    
                    this.actionStatus = status;
                    
                    this.action = new CertificationAction();
                    this.action.setStatus(status);
                    this.action.setMitigationExpiration(new Date());
                    this.action.setRevokeAccount(isRevokeAccount);
    
                    this.action.setDescription(getActionDescription(action));
                }
            }
            certDefinition = getObject().getCertificationDefinition(getContext());
        }
    }

    /**
     * To avoid confusion with other parameters getting passed in for a
     * certification, we'll also look for values prefixed with "bulkAction_".
     */
    @Override
    protected String getRequestParameter(String name) {
        String val = super.getRequestParameter(name);
        
        if (null == Util.getString(val)) {
            val = super.getRequestParameter("bulkAction_" + name);
        }

        return val;
    }
    
    /**
     * Use the CertificationBean to do the authorization check.
     */
    @Override
    protected boolean isAuthorized(SailPointObject object)
        throws GeneralException {
        
        // Consider a value binding here.  Not sure if the CertificationBean
        // will be in the request scope during this popup.
        CertificationBean certBean =
            new CertificationBean((Certification) object, null, null, this.workItemId);
        return certBean.isAuthorized(object);
    }

    /**
     * Creates a description for the given action and the type of
     * entity being acted upon i.e. identities, account groups, etc.
     *
     * @param status Bulk action selected by user
     * @return description for the given action
     */
    public String getActionDescription(CertificationAction action){

        CertificationAction.Status status = action.getStatus();
        
        // currently, we only need descriptions for remediations and delegations
        if (!CertificationAction.Status.Remediated.equals(status) &&
            !CertificationAction.Status.Delegated.equals(status))
            return null;

        // try and get the entity type so we can compose the msg. If there's an
        // exception, rather than blowing up, punt and return the identity-specific message.
        CertificationEntity.Type entityType = CertificationEntity.Type.Identity;
        try{
            Certification cert = getObject();
            if (cert!=null && cert.getEntities()!=null && !cert.getEntities().isEmpty()){
                CertificationEntity entity = cert.getEntities().get(0);
                entityType = entity.getType();
            }
        } catch (GeneralException e) {
            log.warn("Could not get entity type to generate bulk action description text.", e);
        }

        String remediationMessage = null;
        String revokeAccountMessage = null;
        String delegateMessage = null;
        switch(entityType){
            case AccountGroup:
                remediationMessage=getMessage(MessageKeys.PLZ_REMEDIATE_ACT_GRPS);
                revokeAccountMessage = getMessage(MessageKeys.PLZ_REVOKE_ACCOUNTS);
                delegateMessage=getMessage(MessageKeys.PLZ_CERTIFY_ACT_GRPS);
                break;
            case BusinessRole:
                remediationMessage=getMessage(MessageKeys.PLZ_REMEDIATE_ROLES);
                delegateMessage=getMessage(MessageKeys.PLZ_CERTIFY_ROLES);
                break;
            default:
                remediationMessage = getMessage(MessageKeys.PLZ_REMEDIATE_IDENTITIES);
                revokeAccountMessage = getMessage(MessageKeys.PLZ_REVOKE_ACCOUNTS);
                delegateMessage = getMessage(MessageKeys.PLZ_CERTIFY_IDENTITIES);
        }


       switch(status){
           case Remediated:
               return (action.isRevokeAccount()) ? revokeAccountMessage : remediationMessage;
           case Delegated:
               return delegateMessage;
       }

        return null;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ/WRITE PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if we are saving custom fields on the CertificationEntities,
     * or false if this is some other type of bulk action.
     */
    public boolean isSaveEntityCustomFields() {
        return this.saveEntityCustomFields;
    }
    
    public void setSaveEntityCustomFields(boolean b) {
       this.saveEntityCustomFields = b;
    }
    
    /**
     * Return the CertificationAction on which the bulk action settings will be
     * stored.
     */
    public CertificationAction getAction() {
        if (null == this.action) {
            this.action = new CertificationAction();
        }
        return this.action;
    }

    public void setAction(CertificationAction action) {
        this.action = action;
    }

    public CertificationAction.Status getActionStatus() {
        return this.actionStatus;
    }
    
    public void setActionStatus(CertificationAction.Status actionStatus) {
        this.actionStatus = actionStatus;
    }
    
    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public boolean isBulkCertifyAllItems() {
        return bulkCertifyAllItems;
    }

    public void setBulkCertifyAllItems(boolean bulkCertifyAllItems) {
        this.bulkCertifyAllItems = bulkCertifyAllItems;
    }

    /**
     * Gets the owner name. We delegate this call to the action so that
     * this class has a similiar interface to CertificationActionBean.
     *
     * @return Owner name or null
     */
    public String getOwnerName() {
        return action!=null ? action.getOwnerName() : null;
    }

    /**
     * Sets the certifier for this 
     *
     * @param ownerName
     */
    public void setOwnerName(String ownerName) {
        action.setOwnerName(ownerName);
    }

    /**
     * Return the value for the custom1 field which will be saved.
     */
    public String getCustom1() {
        return this.custom1;
    }

    public void setCustom1(String custom1) {
        this.custom1 = custom1;
    }

    /**
     * Return the value for the custom2 field which will be saved.
     */
    public String getCustom2() {
        return this.custom2;
    }

    public void setCustom2(String custom2) {
        this.custom2 = custom2;
    }

    /**
     * Return the value for the custom map which will be saved.
     */
    public Map<String, Object> getCustomMap() {
        if (null == this.customMap) {
            this.customMap = new HashMap<String,Object>();
        }
        return this.customMap;
    }

    public void setCustomMap(Map<String, Object> customMap) {
        this.customMap = customMap;
    }

    /**
     * Return whether non-null custom field values on certification entities
     * should be overwritten.  This defaults to true to remain consistent with
     * our current bulk action behavior (ie - bulk approve overwrites other
     * decisions).  Set this to false to allow non-null values to remain on
     * entities when saved.
     */
    public boolean isOverwriteCustomFields() {
        return this.overwriteCustomFields;
    }

    public void setOverwriteCustomFields(boolean overwriteCustomFields) {
        this.overwriteCustomFields = overwriteCustomFields;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return whether this dialog is being displayed solely for the purpose of
     * confirmation.
     */
    public boolean isOnlyShowConfirmation() throws GeneralException {
        
        boolean requireConfirmation = false;

        Configuration config = getContext().getConfiguration();
        
        if (certDefinition != null) {
            requireConfirmation = certDefinition.isRequireBulkCertifyConfirmation(getContext());
        }
        else {
            // in case there might be a case where the cert def is null?
            requireConfirmation = config.getBoolean(Configuration.REQUIRE_BULK_CERTIFY_CONFIRMATION, false);
        }
        
        if (!requireConfirmation) {
            return false;
        }
        
        Status status = getStatus();
        
        // Return false for delegation or mitigation.
        if (Status.Delegated.equals(status) || Status.Mitigated.equals(status)) {
            return false;
        }
        
        // Return false if we're approving and requiring comments.
        if (isRequireApprovalComments() && Status.Approved.equals(status)) {
            return false;
        }

        // Return false if we're remediating and there is no default remediator.
        if ((Status.Remediated.equals(status) || Status.RevokeAccount.equals(status)) &&
            (null == config.get(Configuration.DEFAULT_REMEDIATOR))) {
            return false;
        }

        return true;
    }
    
    /**
     * Return whether the filter can be applied to certification items to
     * selectively bulk certify.
     */
    public abstract boolean getItemsSelectable();

    /**
     * Return whether or not to calculate the app owner and app remediators for
     * this bulk request.  The idea is that this may be quite expensive for a
     * large set of identities/items and could really slow down the initial
     * calculation.
     * @return
     */
    public abstract boolean allowSelectingAppRemediators();
    
    /**
     * Get the CertificationItemSelector used to filter the bulk certification
     * if there is one.
     */
    abstract CertificationItemSelector getItemSelector();

    /**
     * Get the AbstractCertificationItems that will be included in this bulk
     * request.
     */
    abstract List<? extends AbstractCertificationItem> getItems() throws GeneralException;

    /**
     * Returns true if the user must add a comment when approving a
     * certification item.
     * @return
     * @throws GeneralException
     */
    public boolean isRequireApprovalComments() throws GeneralException{
        Certification cert = getObject();
        return cert!=null && cert.isRequireApprovalComments();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // REMEDIATOR SELECTION OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a list of application associated with the certification item
     * in question. For an exception we return the exception application. For a
     * bundle we include all apps in all the profiles in the bundle. For a
     * policy violation we only return the apps in the bundles the user has
     * chosen to remediate.
     *
     * @return Non-null list of Application object.
     */
    public void initApplications() {

        if (allowSelectingAppRemediators()) {
            setApplications(new ArrayList<Application>());
            Set<Application> appSet = new HashSet<Application>();
    
            try {
                List<? extends AbstractCertificationItem> items = getItems();

                for (AbstractCertificationItem item : items) {
                    List<Application> itemApps = item.getApplications(getAction(), true, getContext());
                    if (itemApps != null)
                        appSet.addAll(itemApps);
                }
        
                setApplications(new ArrayList<Application>(appSet));
            } catch (GeneralException e) {
                throw new RuntimeException(e);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Bulk certify the given identities using the action property on this
     * bean.
     * 
     * @param  items  The certification items to bulk certify.
     */
    void certify(List<? extends AbstractCertificationItem> items)
        throws GeneralException {

        if(null!= items) {
            getSessionScope().remove(SESSION_MESSAGES);
            Identity me = getLoggedInUser();

            // Use a null selector if we are certifying all items.
            CertificationItemSelector selector =
                (this.bulkCertifyAllItems) ? null : this.getItemSelector();

            // If this is a remediation without an owner name, use the default
            // remediation configured in system config.
            if ((null == this.action.getOwnerName()) && this.action.isRemediation()) {
                Configuration config = getContext().getConfiguration();
                this.action.setOwnerName(config.getString(Configuration.DEFAULT_REMEDIATOR));
            }

            // BUG #5126: We have seen the status of the action be null, which
            // is causing an NPE when bulk certifying.  The cause of this is
            // currently unknown and the problem cannot be reproduced easily.
            // Put a safe-guard here in case the action is null.
            if (null == this.action.getStatus()) {
                log.warn("Action status was null when bulk certifying, falling " +
                         "back to hidden value: " + this.actionStatus);
                this.action.setStatus(this.actionStatus);
            }
            
            for(AbstractCertificationItem item : items) {
                List<CertificationItem> notCertified =
                    item.bulkCertify(me, this.workItemId, this.action, selector, false);
                
                // Add messages to the FacesContext if any items were not bulk
                // certified by this request - typically policy violations.
                addNotCertifiedMsgs(item.getCertificationEntity(), notCertified);
            }
            CertificationBean.saveAndRefreshCertification(getObject(), getContext(), this);
        }
    }

    /**
     * Return the requested action status for this bulk action request.  This is
     * safer than just calling action.getStatus() - see bug #5126.
     */
    CertificationAction.Status getStatus() {
        return (null != this.action.getStatus()) ? this.action.getStatus()
                                                 : this.actionStatus;
    }
    
    /**
     * Add messages to the FacesContext saying that the given items were not
     * bulk certified.
     * 
     * @param  items  A non-null list of items that were not bulk certified.
     */
    private void addNotCertifiedMsgs(CertificationEntity identity,
                                     List<CertificationItem> items)
        throws GeneralException {
    
        String identityName = null;
        Identity id = identity.getIdentity(getContext());
        if (null != id) {
            identityName =
                Util.getString(Util.getFullname(identity.getFirstname(),
                                                identity.getLastname()));
            if (null == identityName) {
                identityName = id.getName();
            }
        }

        // get all the current faces messages and stick em in a set so we can
        // make sure we aren't adding duplicate messages
        Set<String> messages = new HashSet<String>();
        if (getMessages()!=null){          
            for(FacesMessage msg : getMessages()){
                messages.add(msg.getSummary());
            }
        }

        for (CertificationItem item : items) {
            Message msg = null;

            if (null != identityName)
                msg = new Message(Message.Type.Warn, MessageKeys.ERR_CANT_BLK_CERT_IDENT,
                        item.getErrorDescription(), identityName);
            else
                msg = new Message(Message.Type.Warn, MessageKeys.ERR_CANT_BLK_CERT,
                        item.getErrorDescription());

            // Make sure we don't overwhelm the list with a lot of duplicate msgs
            if (!messages.contains(msg)){

                super.addMessageToSession(msg, msg);
            }
            messages.add(msg.getLocalizedMessage());
        }
    }

    /**
     * Save the custom fields (custom1, custom2, and customMap) on the given
     * list of items.  This will save on the CertificationEntities rather than
     * items if saveOnEntities is true.
     * 
     * @param  items           The AbstractCertificationItems to save on.
     * @param  saveOnEntities  True to save the custom fields on the entities.
     */
    void saveCustomFields(List<AbstractCertificationItem> items,
                          boolean saveOnEntities)
        throws GeneralException {
        
        Collection<AbstractCertificationItem> entities = items;

        // If we are saving the custom fields on entities, get the unique
        // entities for the selected items.  This is needed if we're setting
        // custom fields on entities from the worksheet view.
        if (saveOnEntities) {
            entities = new HashSet<AbstractCertificationItem>();
            for (AbstractCertificationItem item : items) {
                CertificationEntity entity = item.getCertificationEntity();
                if (!entities.contains(entity)) {
                    entities.add(entity);
                }
            }
        }

        String custom1 = Util.getString(this.custom1);
        String custom2 = Util.getString(this.custom2);
        
        // Iterate over the entities and save the fields.
        for (AbstractCertificationItem item : entities) {
            if (shouldWriteCustom(Util.getString(item.getCustom1()), custom1)) {
                item.setCustom1(custom1);
            }

            if (shouldWriteCustom(Util.getString(item.getCustom2()), custom2)) {
                item.setCustom2(custom2);
            }

            if (null != this.customMap) {
                for (Map.Entry<String,Object> entry : this.customMap.entrySet()) {
                    String key = entry.getKey();
                    Object val = entry.getValue();
    
                    // Turn empty strings into null if we're dealing with a string.
                    if (val instanceof String) {
                        val = Util.getString((String) val);
                    }
                    
                    Map<String,Object> entryMap = item.getCustomMap();
                    Object currentVal = entryMap.get(key);
                    if (currentVal instanceof String) {
                        currentVal = Util.getString((String) currentVal);
                    }
    
                    if (shouldWriteCustom(currentVal, val)) {
                        entryMap.put(key, val);
                    }
                }
            }
        }

        // Finally, save and refresh.
        CertificationBean.saveAndRefreshCertification(getObject(), getContext(), this);
    }

    /**
     * Return whether we should write the given custom field value to an entity
     * based on the overwriteCustomFields setting and the current/new values.
     */
    private boolean shouldWriteCustom(Object currentVal, Object newVal) {
        
        // Write if we're forcing overwrite or the current value is null.
        // For now we don't care if the new value is null or not.
        return this.overwriteCustomFields || (null == currentVal);
    }
}
