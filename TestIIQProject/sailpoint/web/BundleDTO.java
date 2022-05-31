/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.api.Localizer;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationItem;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class BundleDTO extends BaseDTO {

    private static Log log = LogFactory.getLog(BundleDTO.class);

    private String roleId;
    private String name;
    private String description;
    private String displayableName;
    private String typeName;
    private boolean assignable;
    private String ownerName;
    private boolean permitsAllowed;
    private boolean requirementsAllowed;
    private boolean hasInheritance;
    private String scope;
    private Attributes<String, Object> attributes;

    /**
     *  Ext-js css class used which renders the image in front
     * of the node in a tree.
     */
     private String iconCls;

    /**
     * Ext-js property which defines the css class of the tree node text
     */
    private String cls;


    public BundleDTO() {

        roleId = getRequestParameter("bundleId");

        // if this is coming from an extjs tree
        if (getRequestParameter("node") != null && !"".equals(getRequestParameter("bundleId")))
            roleId = getRequestParameter("node");
        else if (getRequestParameter("certificationId") != null){
            String itemId = getRequestParameter("certificationId");
            CertificationItem item = null;
            try {
                item = getContext().getObjectById(CertificationItem.class, itemId);
            } catch (GeneralException e) {
                log.error(e);
            }
            if (item!=null)
                roleId = item.getTargetId();
        }

        try {
            Bundle bundle = null;
            if (roleId != null)
                bundle = getContext().getObjectById(Bundle.class, roleId);

            if (bundle != null)
                init(bundle);
        } catch (GeneralException e) {
            // todo do something helpful here
            throw new RuntimeException(e);
        }
    }
    

    public BundleDTO(Bundle role) {
        try {
            if (role != null){
                roleId= role.getId();
                init(role);
            }
        } catch (GeneralException e) {
            // todo do something helpful here
            throw new RuntimeException(e);
        }
    }

    private void init(Bundle bundle) throws GeneralException{

        name = bundle.getName();
        
        /** Load the description from the localization table **/
        Localizer localizer = new Localizer(getContext(), bundle.getId());
        description = localizer.getLocalizedValue(Localizer.ATTR_DESCRIPTION, getLocale());

        hasInheritance = bundle.getInheritance() != null && !bundle.getInheritance().isEmpty();
        displayableName = bundle.getDisplayableName();
        typeName = typeName != null ? typeName : bundle.getType();
        scope = bundle.getAssignedScope() != null ? bundle.getAssignedScope().getDisplayableName() : null;
        attributes = bundle.getAttributes();

        if (bundle.getOwner() != null && bundle.getOwner().getName() != null) {
            ownerName = bundle.getOwner().getName();
        } else {
            ownerName = new Message(MessageKeys.NONE).getLocalizedMessage(getLocale(), null);
        }

        ObjectConfig roleConfig = getContext().getObjectByName(ObjectConfig.class, ObjectConfig.ROLE);
        RoleTypeDefinition roleTypeDef = roleConfig.getRoleTypesMap().get(bundle.getType());
        if (roleTypeDef != null) {
            assignable = roleTypeDef.isAssignable();
            iconCls = roleTypeDef.getIcon();
            permitsAllowed = !roleTypeDef.isNoPermits();
            requirementsAllowed = !roleTypeDef.isNoRequirements();
            typeName = roleTypeDef.getDisplayName();
        }

    }

    public String getId() {
        String i = this.getUid();
        return i;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getName() {
        return name;
    }

    /**
     * @deprecated Use {@link #getDisplayableName()} instead
     */
    public String getFullName() {
        return getDisplayableName();
    }
    
    public String getDisplayableName() {
        return displayableName;
    }
    
    public void setDisplayableName(String displayableName) {
        this.displayableName = displayableName;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getRoleOwnerName() {
        return ownerName;
    }

    public void setRoleOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getRoleDescription() {
        return description;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    // IIQMAG-2974 After removal of flexjson everything is now deep serialized. This could lead to infinite
    // recursion loops in the attributes list when serializing RoleDetailsBean.RoleDetailDTO so we are excluding
    // the attributes from json serialization.
    @JsonIgnore
    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public List<String> getAttributeKeys(){

        if(attributes == null){
            return Collections.EMPTY_LIST;
        }else {
            List<String> keys = new ArrayList<String>();
            keys.addAll(attributes.keySet());
            return keys;
        }
    }

    public boolean isAssignable() {
        return assignable;
    }

    public void setAssignable(boolean assignable) {
        this.assignable = assignable;
    }

    public boolean isPermitsAllowed() {
        return permitsAllowed;
    }

    public boolean isRequirementsAllowed() {
        return requirementsAllowed;
    }

    @JsonIgnore
    public String getJson() {
        return JsonHelper.toJson(this);

    }

    /*
    * EXT-JS Tree node properties
    */

    /**
     * Node text displayed in an extjs tree
     * @return
     */
    public String getText() {
        return WebUtil.escapeHTML(getDisplayableName(), false);
    }

    /**
     * Ext-js property, used in rendering ext tree nodes
     * @return
     */
    public boolean isLeaf() {
        return hasInheritance;
    }

     public String getIconCls() {
        return iconCls;
    }

    public void setIconCls(String cls) {
        iconCls = cls;
    }

    public String getCls() {
        return cls;
    }

    public void setCls(String cls) {
        this.cls = cls;
    }

}

