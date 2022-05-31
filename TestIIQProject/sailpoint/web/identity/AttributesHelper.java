package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.Util;
import sailpoint.web.AttributeEditBean;
import sailpoint.web.system.ObjectAttributeDTO;

/**
 * Backing bean for Attributes Information of an Identity
 * This is initialized by IdentityDTO
 */
public class AttributesHelper {

    private static final Log log = LogFactory.getLog(AttributesHelper.class);
    
    /**
     * The configured attributes we will display for the identity.
     * Since this never changes, it would be marginally faster
     * to keep this in the IdentityEditNew?
     */
    private List<ObjectAttributeDTO> attributes;
    private LazyLoad<AttributeEditBean> attributeEditor;
    private LazyLoad<Boolean> attributeEditAvailable;
    private IdentityDTO parent;
    /**
     * Special proxy object that sits between the JSF page and the
     * Identity to provide a uniform Map interface to all configurable
     * properties.
     */
    private transient IdentityProxy proxy;
    private String relatedIdentityId;
    
    public AttributesHelper(IdentityDTO parent) {
    
        this.parent = parent;

        this.attributeEditor = new LazyLoad<AttributeEditBean>(new ILazyLoader<AttributeEditBean>(){
            public AttributeEditBean load() throws GeneralException {
                return fetchAttributeEditor();
            }
        });
        
        this.attributeEditAvailable = new LazyLoad<Boolean>(new ILazyLoader<Boolean>(){
            public Boolean load() throws GeneralException {
                return fetchAttributeEditAvailable();
            }
        });
    }
    
    public boolean isAttributesEditable() {
        return this.parent.getState().isAttributesEditable();
    }

    public void setAttributesEditable(boolean editable) {
        this.parent.getState().setAttributesEditable(editable);
    }

    public AttributeEditBean getAttributeEditor() throws GeneralException {
        return this.attributeEditor.getValue();
    }

    public boolean isCanViewIdentities() {

        boolean canViewIdentities;

        try {
            CapabilityManager capabilityManager = this.parent.getLoggedInUser().getCapabilityManager();        
            canViewIdentities = capabilityManager.hasCapability(Capability.SYSTEM_ADMINISTRATOR) || capabilityManager.getEffectiveFlattenedRights().contains("ViewIdentity");
        } catch (GeneralException e) {
            log.error("The IdentityBean could not determine capabilities.", e);
            canViewIdentities = false;
        }
        
        return canViewIdentities;
    }
    
    private AttributeEditBean fetchAttributeEditor() throws GeneralException {
        if (this.parent.getState() == null) {
            // this should never happen.
            return null;
        }
        if (this.parent.getState().getAttributeEditor() == null) {
            ObjectConfig config = Identity.getObjectConfig();
            Identity identity = this.parent.getObject();
            Attributes<String, Object> identityAttributes = new Attributes<String, Object>();
            if (identity.getAttributes() != null) {
                identityAttributes.putAll(identity.getAttributes());
            } else {
                identityAttributes.putAll(config.getDefaultValues());
            }

            // put identity extended attributes
            List<ObjectAttribute> extendedAttributes = config.getExtendedAttributeList();
            for (ObjectAttribute attribute : extendedAttributes) {
                if (ObjectAttribute.TYPE_IDENTITY.equals(attribute.getType())) {
                    Identity relatedIdentity = identity.getExtendedIdentity(attribute.getExtendedNumber());
                    if (relatedIdentity != null) {
                        identityAttributes.put(attribute.getName(), relatedIdentity.getName());
                    }
                }
            }
            
            identityAttributes.put(Identity.ATT_MANAGER, identity.getManager());
            identityAttributes.put(Identity.ATT_USERNAME, identity.getName());
            identityAttributes.put(Identity.ATT_ADMINISTRATOR, identity.getAdministrator());
            
            List<String> viewableIdentityAttributes = new ArrayList<>(this.parent.getUIConfig().getIdentityViewAttributesList());
            IdentityTypeDefinition typeDefinition = identity.getIdentityTypeDefinition();
            if (typeDefinition == null) {
                typeDefinition = Identity.getObjectConfig().getDefaultIdentityTypeDefinition();
            }
            if (typeDefinition != null && typeDefinition.getDisallowedAttributes() != null) {
                for (String disallowed : typeDefinition.getDisallowedAttributes()) {
                    viewableIdentityAttributes.remove(disallowed);
                }
            }

            loadHiddenIdentities(identityAttributes, viewableIdentityAttributes);

            AttributeEditBean editor = new AttributeEditBean(identity.getId(), Identity.class, config.getObjectAttributes(), viewableIdentityAttributes, identityAttributes);
            this.parent.getState().setAttributeEditor(editor);
        }
        return this.parent.getState().getAttributeEditor();
    }

    /**
     * Due to the case where there is an Identity attribute that is present in viewableIdentityAttributes, means that there could be an unloaded
     * Identity inside of identityAttributes. When that Identity is referenced later, the session is already closed meaning it would result in an
     * lazy load error where there is no session available to try and get that object.
     *
     * For example if you remove manager from identityViewAttributes and try to save an identity that has a manager from the warehouse, it would get a no session error
     * trying to reference the manager even though it is not visible to the UI.
     *
     * This method will look and pre-emptively load an identity that is hidden from view when the session is still available so that it can be referenced later on.
     *
     * This issue existed before introducing Identity types but brought it to light since the UI would be dynamic in showing and hiding fields based on Type.
     *
     * bli - Maybe a better solution is to also remove it as well from the identityAttributes alltogether if it is not a viewable attribute but weary of making that change
     * since the AttirbuteEditBean is also used by other files besides Identity
     * @param identityAttributes All of the identityAttributes to be set on the AttributeEditBean.
     * @param viewableIdentityAttributes List of Identity attributes to be visible to the user.
     */
    private void loadHiddenIdentities(Attributes<String, Object> identityAttributes, List<String> viewableIdentityAttributes) {
        if (identityAttributes != null && viewableIdentityAttributes != null) {
            for (String key : Util.iterate(identityAttributes.keySet())) {
                if (key != null && !viewableIdentityAttributes.contains(key)) {
                    Object identity = identityAttributes.get(key);
                    if (identity instanceof Identity) {
                        ((Identity) identity).load();
                    }
                }
            }
        }

    }

    /**
     * Return an object that can dereference all Identity properties
     * as if it were a map.
     */
    public IdentityProxy getProxy() throws GeneralException {
        if (this.proxy == null)
            this.proxy = new IdentityProxy(this.parent.getObject(), this.parent.getUserTimeZone(), this.parent.getLocale());
        return this.proxy;
    }
    
    public boolean isAttributeEditAvailable() throws GeneralException {
        return this.attributeEditAvailable.getValue();
    }
    
    private boolean fetchAttributeEditAvailable() {
        boolean editAvailable = false;
        try {
            List<ObjectAttributeDTO> currentAttributes = getAttributes();
            for (ObjectAttributeDTO attribute : currentAttributes) {
                if (attribute.isEditable()) {
                    editAvailable = true;
                }
            }
        } catch (GeneralException e) {
            log.error("Failed to get ObjectAttributes", e);
        }

        return editAvailable;
    }

    public String toggleEditMode() {
        setAttributesEditable(!isAttributesEditable());
        this.parent.saveSession();
        return "";
    }

    public String followManagerLink() {
        try {
            String managerId = (String) getProxy().get("managerId");

            return IdentityDTO.createNavString(IdentityDTO.NAV_STRING_PAGE, managerId);
        } catch (GeneralException e) {
            log.error("Failed to get the current manager from the identity proxy", e);
            return null;
        }
    }

    public String followIdentityLink() {
        if (this.relatedIdentityId == null) {
            return null;
        }

        return IdentityDTO.createNavString(IdentityDTO.NAV_STRING_PAGE, relatedIdentityId);
    }
    

    public void identityLinkClicked(ActionEvent event) {

        String relatedIdentityName = getAttributeValue(event, "relatedIdentityName");
        if (relatedIdentityName == null) {
            return;
        }

        try {
            Identity relatedIdentity = SailPointFactory.getCurrentContext().getObjectByName(Identity.class, relatedIdentityName);
            if (relatedIdentity == null) {
                log.error("could not find identity with name: " + relatedIdentityName);
                return;
            }
            this.relatedIdentityId = relatedIdentity.getId();
        } catch (GeneralException ex) {
            log.error("Failed to get relatedIdentity ", ex);
            return;
        }
    }
    
    //TODO: move to utility class
    private static String getAttributeValue(ActionEvent event, String attributeName) {
        
        if (event == null || event.getComponent() == null || event.getComponent().getAttributes() == null) {
            return null;
        }
        Object value = event.getComponent().getAttributes().get(attributeName);
        if (value == null) {
            return null;
        }
        String stringValue = value.toString();
        if (Util.isNullOrEmpty(stringValue)) {
            return null;
        }
        return stringValue;
    }
    
    /**
     * Return a list of IdentityAttribute objects representing the
     * configured attributes that we will display.
     *
     * Currently displaying everything in the config, but could have
     * another list like identityTableAttributes to restirct those.
     */
    public List<ObjectAttributeDTO> getAttributes() throws GeneralException {
        
        if (this.attributes == null) {
            this.attributes = new ArrayList<ObjectAttributeDTO>();
            UIConfig uiConfig = this.parent.getUIConfig();
            ObjectConfig idConfig = this.parent.getIdentityConfig();
            if (uiConfig != null && idConfig != null) {

                List<String> atts = uiConfig.getIdentityViewAttributesList();
                if (atts != null) {
                    //filter viewable attributes through disallowed attributes list
                    IdentityTypeDefinition type = this.parent.getObject().getIdentityTypeDefinition();
                    if (type == null) {
                        type = idConfig.getDefaultIdentityTypeDefinition();
                    }
                    List<String> disallowed = type == null ? null : type.getDisallowedAttributes();
                    for (String att : atts) {
                        if (disallowed == null || !disallowed.contains(att)) {
                            ObjectAttribute ida = idConfig.getObjectAttribute(att);
                            if (ida != null)
                                this.attributes.add(new ObjectAttributeDTO(ObjectConfig.IDENTITY, ida));
                        }
                    }
                }
            }
        }
        return this.attributes;
    }

    void addAttributesInfoToAccountRequest(AccountRequest account) throws GeneralException {

        Identity identity = this.parent.getObject();
        // AttributeEditBean is managing state for extended attributes
        if (isAttributesEditable()) {
            AttributeEditBean bean = this.parent.getState().getAttributeEditor();
            Map<BaseAttributeDefinition,Object> changes = bean.getChanges(identity);
            if (changes != null) {
                Iterator<Map.Entry<BaseAttributeDefinition,Object>> it = changes.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<BaseAttributeDefinition,Object> entry = it.next();
                    BaseAttributeDefinition def = entry.getKey();
                    Object value = entry.getValue();

                    AttributeRequest req = new AttributeRequest();
                    req.setName(def.getName());
                    req.setOperation(ProvisioningPlan.Operation.Set);

                    // hack..for Manager the value usually comes in as an Identity,
                    // simplify this to a name to reduce clutter in the plan    
                    if (value instanceof Identity)
                        value = ((Identity)value).getName();

                    req.setValue(value);
                    account.add(req);
                }
            }
            
        }
    }

}
