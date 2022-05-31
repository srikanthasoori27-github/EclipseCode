package sailpoint.web.modeler;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.tools.GeneralException;
import sailpoint.web.AttributeEditBean;
import sailpoint.web.modeler.RoleAttributesUtil.IFilteredAttributesInfo;

public class RoleAttributeEditBean extends AttributeEditBean {

    //TODO: generate
    private static final long serialVersionUID = 1L;
    private static final Log log = LogFactory.getLog(RoleAttributeEditBean.class);
    private Bundle editedRole;

    RoleAttributeEditBean(
            RoleEditorBean roleEditorBean,
            ObjectConfig objectConfig,
            List<String> viewAttributes,
            Attributes<String, Object> attributeValues) {

        // its ok for now to new up and send a bundle because it will only be used to check
        // supportsExtendedIdentity which is false for Bundle class
        // if we add the functionality to allow exteded identity attributes for roles
        // we will have to do something different.
        //
        // TODO: this needs refactoring... we should just send the editedRole here when calling
        // the constructor.
        super(null, Bundle.class, roleEditorBean, objectConfig.getObjectAttributes(), viewAttributes, attributeValues);

        if (log.isInfoEnabled()) {log.info("RoleAttributeEditBean()");}
    }
    
    private Bundle getEditedRole() {
        
        if (this.editedRole == null) {
            try {
                this.editedRole = ((RoleEditorBean) this.ownerBean).getObject();
            } catch (GeneralException ex) {
                throw new IllegalStateException("Could not find role object");
            }
        }
        return this.editedRole;
    }

    /**
     * It will filter the attributes by role type.
     * Bug#21333: When it is a new role we don't want to filter so just return the super version of this method.
     * Otherwise we want to filter the attributeDefinitions for role types.
     *
     * @param attributeDefinitions the definitions that need to be filtered.
     * @return attributes which are filtered per role type.
     */
    @Override
    protected IFilteredAttributesInfo getFilteredAttributesInfo(List<? extends BaseAttributeDefinition> attributeDefinitions) {
        if (isNewRole()) {
            return super.getFilteredAttributesInfo(attributeDefinitions);
        }

        return RoleAttributesUtil.getDisallowedFilteredAttributesInfo(attributeDefinitions, getEditedRole());
    }

    /**
     * Whether the currently edited role is new.
     *
     * @return true if the currently edited role is new
     */
    private boolean isNewRole() {
        return RoleEditorBean.class.cast(this.ownerBean).isNewRole();
    }
}
