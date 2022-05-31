package sailpoint.web.identity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.RoleRelationships;
import sailpoint.tools.GeneralException;

/**
 * Backing bean for DetectedRoles in the Entitlements tab for identity.
 * This is initialized by IdentityDTO
 */
public class DetectedRolesHelper {

    private static final Log log = LogFactory.getLog(DetectedRolesHelper.class);

    private IdentityDTO parent;
    
    public DetectedRolesHelper(IdentityDTO parent) {
        this.parent = parent;
    }
    
    public List<DetectedRoleBean> getDetectedRoles() {
        
        if (this.parent.getState().getDetectedRoles() == null) {
            this.parent.getState().setDetectedRoles(fetchDetectedRoles());
        }

        return this.parent.getState().getDetectedRoles();
    }
    
    private List<DetectedRoleBean> fetchDetectedRoles() {
        
        List<DetectedRoleBean> detectedRoles = new ArrayList<DetectedRoleBean>();

        Identity identity = this.parent.getObject();
        List<Bundle> bundles = identity.getBundles();
        try {
            if (bundles != null) {
                RoleRelationships rr = new RoleRelationships();
                rr.analyze(identity);
                for (Bundle bundle : bundles) {
                    // We're passing in null for the non-flattened entitlement
                    // mappings. This is now loaded with an AJAX call.
                    detectedRoles.add(createDetectedRoleBean(bundle, rr));
                }
            }
        } catch (Exception e) {
            detectedRoles = null;
            log.warn("Exception: " + e.getMessage());
        }
        
        return detectedRoles;
    }
    
    private DetectedRoleBean createDetectedRoleBean(Bundle bundle, RoleRelationships rr) {

        DetectedRoleBean bean = new DetectedRoleBean();
        
        bean.setId(bundle.getId());
        bean.setName(bundle.getName());
        try {
            SailPointContext context = SailPointFactory.getCurrentContext();
            FacesContext fc = FacesContext.getCurrentInstance();
            Locale locale = ((fc != null) ? fc.getViewRoot().getLocale() : Locale.getDefault());
            
            Localizer localizer = new Localizer(context);
            bean.setDescription(localizer.getLocalizedValue(bundle, Localizer.ATTR_DESCRIPTION, locale));
        } catch (GeneralException ge) {
            log.warn("Unable to localize description due to exception: " + ge.getMessage());
            bean.setDescription(bundle.getDescription());
        }
        
        bean.setIcon(bundle.getRoleTypeDefinition() == null? null : bundle.getRoleTypeDefinition().getIcon());
        bean.setPermittedBy(rr.getPermittedNames(bundle));

        return bean;
    }
    
    public static class DetectedRoleBean implements Serializable {

        // TODO: replace with generated
        private static final long serialVersionUID = 1L;
    
        private String id;
        private String icon;
        private String name;
        private String description;
        private String permittedBy;
    
        public String getId() {
            return this.id;
        }
    
        public void setId(String id) {
            this.id = id;
        }
    
        public String getIcon() {
            return this.icon;
        }
    
        public void setIcon(String icon) {
            this.icon = icon;
        }
    
        public String getName() {
            return this.name;
        }
    
        public void setName(String name) {
            this.name = name;
        }
    
        public String getDescription() {
            return this.description;
        }
    
        public void setDescription(String description) {
            this.description = description;
        }
    
        public String getPermittedBy() {
            return this.permittedBy;
        }
    
        public void setPermittedBy(String permittedBy) {
            this.permittedBy = permittedBy;
        }
    }
}
