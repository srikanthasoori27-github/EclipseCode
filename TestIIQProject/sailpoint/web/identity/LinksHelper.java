package sailpoint.web.identity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Iconifier;
import sailpoint.api.Iconifier.Icon;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeSource;
import sailpoint.object.Attributes;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.AttributeEditBean;
import sailpoint.web.LinkDetailsBean;
import sailpoint.web.LinkDetailsBean.LinkAttributeBean;
import sailpoint.web.messages.MessageKeys;

/**
 * This class is initialized by IdentityDTO
 * for the View Identity -> Application Accounts tab  
 *
 */
public class LinksHelper {

    private static final Log log = LogFactory.getLog(LinksHelper.class);
    
    private IdentityDTO parent;
    /**
     * Derived value set to true if the identity has links
     * that include template instance identifiers.  Used to conditionalize
     * the display of an instance column since not all identities will have them.
     */
    private Boolean linkInstances;
    /**
     * Icons to display per link based on the uiconfig settings.
     */
    private Map<String,List<Icon>> accountIconMap;
    
    public LinksHelper(IdentityDTO parent) {

        if (log.isInfoEnabled()) {
            log.info(getClass().getName() + ": constructor()");
        }
        
        this.parent = parent;
    }
    
    public boolean isLinkInstances() throws GeneralException {

        if (this.linkInstances == null) {
            this.linkInstances = new Boolean(false);
            Identity ident = this.parent.getObject();
            if ( ident != null ) {
                List<Link> links = ident.getLinks();
                if (links != null) {
                    for (Link link : links) {
                        if (link.getInstance() != null) {
                            this.linkInstances = new Boolean(true);
                            break;
                        }
                    }
                }
            }
        }
        return this.linkInstances.booleanValue();
    }

    
    public List<LinkBean> getLinks() throws GeneralException {
        
        if (this.parent.getState().getLinks() == null) {
            this.parent.getState().setLinks(fetchLinkBeans());
        }
        return this.parent.getState().getLinks();
    }

    private List<LinkBean> fetchLinkBeans() {
        
        List<LinkBean> linkBeans = new ArrayList<LinkBean>();
        Identity identity = this.parent.getObject();
        List<Link> links = identity.getLinks();
        if (!Util.isEmpty(links)) {
            for (Link link : links) {
                linkBeans.add(new LinkBean(link));
            }
            Collections.sort(linkBeans);
        }
        
        return linkBeans;
    }
    
    public Map<String,Boolean> getLinkSelections() {
        return this.parent.getState().getLinkSelections();
    }

    private Map<String,List<Icon>> buildAccountIcons() {

        Iconifier iconifier = new Iconifier();
        return iconifier.getAccountIconsByLink(this.parent.getObject());
    }

    public Map<String, List<Icon>> getIconMapping() {
        
        if (this.accountIconMap == null) {
            this.accountIconMap = buildAccountIcons();
        }
        
        return this.accountIconMap;
    }

    /**
     * IIQETN-1644 : we expect single IIQ account request.
     * 
     *  Retrieves the existing IIQ AccountRequest from the provisioning plan.
     *  If it does not exist, create a new one.
     *      
     *
     **/
    AccountRequest getIIQAccountRequest(ProvisioningPlan plan, ProvisioningPlan.AccountRequest.Operation op) {
        AccountRequest account = plan.getIIQAccountRequest();
        if (account == null) {
            account = new AccountRequest();
            account.setOperation(op);
            account.setApplication(ProvisioningPlan.APP_IIQ);
            plan.add(account);
        }
        return account;
    }
    
    void addRemoveLinksToPlan(ProvisioningPlan plan) {
        
        if (!this.parent.getState().isAnyLinkForRemove()) {
            return;
        }

        AccountRequest account = getIIQAccountRequest(plan, ProvisioningPlan.AccountRequest.Operation.Modify);
        
        AttributeRequest attributeRequest = new AttributeRequest();
        attributeRequest.setName(ProvisioningPlan.ATT_IIQ_LINKS);
        attributeRequest.setOperation(Operation.Remove);
        
        List<String> toRemove = new ArrayList<String>();
        for (LinkBean link : this.parent.getState().getLinks()) {
            if (link.isPendingDelete()) {
                toRemove.add(link.getId());
            }
        }
        
        attributeRequest.setValue(toRemove);
        account.add(attributeRequest);
    }
    
    void addMoveLinksToPlan(ProvisioningPlan plan) {
        
        if (!this.parent.getState().isAnyLinkForMove()) {
            return;
        }
        
        AccountRequest account = getIIQAccountRequest(plan, ProvisioningPlan.AccountRequest.Operation.Modify);

        //TODO: collapse for each identity
        for (LinkBean link : this.parent.getState().getLinks()) {
            if (link.isPendingMove()) {
                AttributeRequest attributeRequest = new AttributeRequest();
                attributeRequest.setName(ProvisioningPlan.ATT_IIQ_LINKS);
                attributeRequest.setOperation(Operation.Remove);
                attributeRequest.setValue(link.getId());
                attributeRequest.put(ProvisioningPlan.ARG_DESTINATION_IDENTITY, link.getTargetIdentityName());
                account.add(attributeRequest);
            }
        }
    }
    
    void addLinkEditsToPlan(ProvisioningPlan plan)
        throws GeneralException {
        
        if (!this.parent.getState().isAnyLinkEdited()) {
            return;
        }
        
        for (LinkBean link : this.parent.getState().getLinks()) {
            if (link.isEditable()) {
                // Check to see if there are any changes
                AttributeEditBean editBean = link.getLinkAttributeEditor();
                Map<BaseAttributeDefinition, Object> changes = editBean
                        .getChanges(this.parent.getContext().getObjectById(Link.class, link.getId()));

                if (changes != null) {
                    AccountRequest linkAccount = new AccountRequest();
                    linkAccount.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
                    linkAccount.setApplication(link.getApplicationName());
                    linkAccount.setNativeIdentity(link.getNativeIdentity());
                    plan.add(linkAccount);

                    Iterator<Map.Entry<BaseAttributeDefinition, Object>> it = changes.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<BaseAttributeDefinition, Object> entry = it.next();
                        BaseAttributeDefinition def = entry.getKey();
                        Object value = entry.getValue();

                        AttributeRequest req = new AttributeRequest();
                        req.setName(def.getName());
                        req.setOperation(ProvisioningPlan.Operation.Set);
                        req.put(ProvisioningPlan.ARG_LINK_EDIT, "true");

                        // hack..for Manager the value usually comes in as an
                        // Identity,
                        // simplify this to a name to reduce clutter in the plan
                        if (value instanceof Identity) {
                            value = ((Identity) value).getName();
                        }

                        req.setValue(value);
                        linkAccount.add(req);
                    }
                }
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Link Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Delete all selected Links.
     */
    public String deleteLinks() throws GeneralException {

        Map<String,Boolean> selections = this.parent.getState().getLinkSelections();
        if (selections == null) {
            return null;
        }
        for (String key : selections.keySet()) {
            if (selections.get(key)) {
                this.parent.getState().removeLink(key);
            }
        }
        this.parent.getState().clearLinkSelections();
        this.parent.saveSession();
        return null;
    }

    /**
     * This will put the pending moves into IdentityEditNew bean
     * When save is called the provisioning or workflow should 
     * happen. 
     */
    public String getMoveLink() throws GeneralException {
        String linkId = this.parent.getRequestParameter("linkId");
        // Get target identity name. The user may specify an existing identity
        // or the name of a new identity we'll need to create.
        String newIdentityName = this.parent.getRequestParameter("newIdentity");
        String existingIdentity = this.parent.getRequestParameter("existingIdentity");
        SailPointContext ctx = this.parent.getContext();

        try {
            // make sure new identity name is unique
            if (!Util.isNullOrEmpty(newIdentityName)){
                if ( 0 < ctx.countObjects(Identity.class, new QueryOptions(Filter.eq("name", newIdentityName))))
                    return JsonHelper.failure(this.parent.getMessage(MessageKeys.IDENTITY_ACCOUNTS_MOVE_ERR_DUP_NAME),
                                                     "duplicateName");
            }

            // if we have an existing identity we can move the link, otherwise
            // we'll need to create a new identity to hold the link
            if (!Util.isNullOrEmpty(existingIdentity)){
                this.parent.getState().moveLink(linkId, existingIdentity);
            } else if (!Util.isNullOrEmpty(newIdentityName)){
                this.parent.getState().moveLink(linkId, newIdentityName);
            } else {
                return JsonHelper.failure("Don't know identity to move link to", "system");
            }
            
            this.parent.saveSession();

         } catch (Exception e) {
            log.error(e);
            return JsonHelper.failure("", "system");
        }
        
         //TODO: do we need to worry about user getting deleted?
         return JsonHelper.success("identityDeleted", false);
    }
    
    
    public static class LinkBean implements Comparable<LinkBean>, Serializable {
        
        // TODO: generate
        private static final long serialVersionUID = 1L;
        private String id;
        private String applicationName;
        private String instance;
        private String nativeIdentity;
        private String displayableName;
        private Date lastRefresh;
        private boolean manuallyCorrelated;
        private boolean authoritative;
        private boolean composite;
        private boolean pendingDelete;
        private boolean pendingMove;
        private String targetIdentityName;
        
        private boolean displayed = false;
        private boolean editable = false;
        
        private boolean disabled;
        private boolean locked;
        
        private boolean applicationSupportsEnable;
        private boolean applicationSupportsUnlock;
        
        private AttributeEditBean linkAttributeEditor;
        
        private LinkDetailsBean linkDetails;
        private List<LinkAttributeBean> attributes;
        Attributes<String, Object> linkAttributes;
        
        private Application application;
        
        public LinkBean(Link link) {
            
            this.id = link.getId();
            this.applicationName = link.getApplicationName();
            this.application = link.getApplication();
            this.instance = link.getInstance();
            this.displayableName = link.getDisplayableName();
            this.nativeIdentity = link.getNativeIdentity();
            this.lastRefresh = link.getLastRefresh();
            this.manuallyCorrelated = link.isManuallyCorrelated();
            this.authoritative = link.getApplication().isAuthoritative();
            this.composite = link.isComposite();
            this.disabled = link.isDisabled();
            this.locked = link.isLocked();
            this.applicationSupportsEnable = link.getApplication().supportsFeature(Application.Feature.ENABLE);
            this.applicationSupportsUnlock = link.getApplication().supportsFeature(Application.Feature.UNLOCK);

            linkDetails = new LinkDetailsBean(id);
            
            // Prepare the attribute editor, we may or may not have editable attributes 
            linkAttributes = new Attributes<String, Object>();
            if (link.getAttributes() != null) {
                linkAttributes.putAll(link.getAttributes());
            } else {
                linkAttributes.putAll(Link.getObjectConfig().getDefaultValues());
            } 
            // preload the link attribute editor to avoid hibernate session reuse
            getLinkAttributeEditor();
        }
        
        /**
         * Check to see if there are editable attributes
         * @return
         */
        @SuppressWarnings("unchecked")
        public boolean isAttributeEditAvailable() {
            if (linkAttributeEditor == null) {
                return false;
            }
            List<ObjectAttribute> defs = (List<ObjectAttribute>)linkAttributeEditor.getAttributeDefinitions();
            for (ObjectAttribute bad : defs) {
                if (bad.isEditable()) {
                     return true;
        }
            }
            return false;
        }
        
        public boolean isDisplayEntitlementDescription() throws GeneralException{
            return linkDetails.isDisplayEntitlementDescription();
        }
        
        public List<LinkAttributeBean> getAttributes() {
            return attributes;
        }
        
        public AttributeEditBean getLinkAttributeEditor() {
            if (linkAttributeEditor == null) {
                // Prepare the attribute editor, we may or may not have editable
                // attributes
                Set<String> extendedAttrDisplayNames = null;
                
                try {
                    // These are the attributes that will be shown
                    attributes = linkDetails.getFormattedAttributes();
                    extendedAttrDisplayNames = linkDetails.getExtendedAttributes().keySet();
                }
                catch (GeneralException e) {
                    log.error("Exception: [" + e.getMessage() + "]", e);
                }
                
                // Use the 'attributes' list to construct object config
                // It is possible that there exists an extended attribute that does not exist in the 'attributes' list
                // Add these to the linkObjConfig and linkattributes.
                
                ObjectConfig linkObjConfig = new ObjectConfig();

                List<ObjectAttribute> editableAttrsList = Link.getObjectConfig().getEditableAttributes();
                Map<String, String> editableAttrsMap = new HashMap<String, String>();
                for (ObjectAttribute oa : editableAttrsList) {
                    if (isEditable(oa)) {
                        linkObjConfig.add(oa);
                        if (!linkAttributes.containsKey(oa.getName())) {
                            linkAttributes.put(oa.getName(), oa.getDefaultValue());
                        }
                        editableAttrsMap.put(oa.getName(), oa.getType());
                    }
                }
      
                // Go through the link attributes
                // and identify the editable ones
                for (LinkAttributeBean lab : attributes) {
                    if (!editableAttrsMap.containsKey(lab.getName())) {
                        ObjectAttribute newAttr = new ObjectAttribute();
                        newAttr.setDisplayName(lab.getDisplayName());
                        newAttr.setName(lab.getName());
                        if (extendedAttrDisplayNames.contains(lab.getDisplayName())) {
                            newAttr.setExtendedNumber(1);  // this is just so that the UI sees the attribute as extended
                        }
                        linkObjConfig.add(newAttr);
                    }
                }
                
                // it is ok to just create a dummy new link
                // because it is only going to be used to test
                // supportsExtendedIdentity, which is false for now
                // we may need to revisit as we support extended identity attributes
                // jsl - yes, I'd rather this be a function of the ObjectConfig
                // not a SailPointObject overload.  See ObjectConfig.isExtendedIdentity
                linkAttributeEditor = new AttributeEditBean(null, Link.class, linkObjConfig.getObjectAttributes(), null, linkAttributes);
            }

            return linkAttributeEditor;
        }
        
        /**
         * Determine if an attribute is editable in the context of this link.
         * This is usually true if the link is for an application that is referenced
         * in the source list for the attribute.  It will also be considered
         * editable if the link has no sources (unusual) or if it has a source that
         * uses a global rule.
         */
        private boolean isEditable(ObjectAttribute att) {

            boolean editable = false;

            if (att.isSourcedBy(this.application)) {
                // we're included as a source
                editable = true;
            }
            else {
                List<AttributeSource> sources = att.getSources();
                if (sources == null || sources.size() == 0) {
                    // lack of sources implies editability, 
                    // unusual but allow it
                    editable = true;
                }
                else {
                    // look for sources that use a global rule
                    for (AttributeSource src : sources) {
                        if (src.getApplication() == null) {
                            // techincally we could check for
                            // src.getRule() != null but assume
                            // sources without apps always trigger  
                            // editability, the "rule" just happens 
                            // to return null all the time
                            editable = true;
                        }
                    }
                }
            }
            return editable;
        }
        
        /**
         * Gets the status of the Link.
         * @return "Disabled" if the link is disabled,
         *         "Locked" if the link is locked,
         *         "Active" otherwise.
         */
        public String getStatus()
        {
            if (disabled) {
                return "Disabled";
            }
            
            if (locked) {
                return "Locked";
            }
            
            return "Active";
        }
        
        public boolean getApplicationSupportsUnlock() {
            return applicationSupportsUnlock;
        }
        
        public boolean getApplicationSupportsEnable() {
            return applicationSupportsEnable;
        }
        
        public boolean isLocked() {
            return locked;
        }
        
        public void setLocked(boolean locked) {
            this.locked = locked;
        }
        
        public boolean isDisabled() {
            return disabled;
        }
        
        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }
        
        public boolean isEditable() {
            return editable;
        }
        
        public boolean isDisplayed() {
            return displayed;
        }
        
        public void toggleEditable() {
            editable = !editable;
        }
        
        public void toggleDisplayed() {
            displayed = !displayed;
        }
        
        public LinkDetailsBean getLinkDetails() {
            return linkDetails;
        }
        
        public String getId() {
            return this.id;
        }
        public void setId(String val){
            this.id = val;
        }
        public String getApplicationName() {
            return this.applicationName;
        }
        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }
        public String getInstance() {
            return this.instance;
        }
        public void setInstance(String instance) {
            this.instance = instance;
        }
        public String getDisplayableName() {
            return this.displayableName;
        }
        public void setDisplayableName(String displayableName) {
            this.displayableName = displayableName;
        }
        public Date getLastRefresh() {
            return this.lastRefresh;
        }
        public void setLastRefresh(Date lastRefresh) {
            this.lastRefresh = lastRefresh;
        }
        public boolean isManuallyCorrelated() {
            return this.manuallyCorrelated;
        }
        public void setManuallyCorrelated(boolean manuallyCorrelated) {
            this.manuallyCorrelated = manuallyCorrelated;
        }
        public boolean isAuthoritative() {
            return this.authoritative;
        }
        public void setAuthoritative(boolean authoritative) {
            this.authoritative = authoritative;
        }
        public boolean isComposite() {
            return this.composite;
        }
        public void setComposite(boolean composite) {
            this.composite = composite;
        }
        public boolean isPendingDelete() {
            return this.pendingDelete;
        }
        public void setPendingDelete(boolean pendingDelete) {
            this.pendingDelete = pendingDelete;
        }
        public boolean isPendingMove() {
            return this.pendingMove;
        }
        public void setPendingMove(boolean pendingMove) {
            this.pendingMove = pendingMove;
        }
        public String getTargetIdentityName() {
            return this.targetIdentityName;
        }
        public void setTargetIdentityName(String targetIdentityName) {
            this.targetIdentityName = targetIdentityName;
        }
        public String getNativeIdentity() {return this.nativeIdentity;}
        public void setNativeIdentity(String val) {this.nativeIdentity = val;}
        
        @Override
        public int hashCode() {
            return this.id.hashCode();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            
            LinkBean other = (LinkBean) obj;
            if (other == null) {
                return false;
            }
            
            return this.id.equals(other.getId());
        }
        
        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }

        public int compareTo(LinkBean o) {
            return this.getApplicationName().compareTo(o.getApplicationName());
        }
    }
}
