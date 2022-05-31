/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.List;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Permission;
import sailpoint.object.Resolver;
import sailpoint.object.Right;
import sailpoint.object.RightConfig;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseDTO;
import sailpoint.web.util.WebUtil;

public class EditablePermissions extends BaseDTO {
    private static final long serialVersionUID = 3747984356649607057L;

    private static final Log log = LogFactory.getLog(EditablePermissions.class);
    
    private List<String> selectedRights;
    private String target;
    
    /** 
     * Name is an optional field used only for permissions that are defined as attributes.
     */
    private String name;

    public EditablePermissions(String name, Permission p) {
        this.name = name;
        selectedRights = p.getRightsList();
        target = p.getTarget();
    }
    
    public EditablePermissions(Permission p) {
        selectedRights = p.getRightsList();
        target = p.getTarget();
    }

    public EditablePermissions() {
        selectedRights = new ArrayList<String>();
        target = "";
    }

    public void setSelectedRights(List<String> selectedRights) {
        this.selectedRights = selectedRights;
    }

    public List<String> getSelectedRights() {
        return selectedRights;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        //IIQETN-5960 :- Wiping out any dangerous javascript for permissions
        this.target = WebUtil.safeHTML(target);
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public static List<SelectItem> getAvailableRights(Resolver r) {
        List<SelectItem> availableRights = new ArrayList<SelectItem>();

        try {
            RightConfig rights = r.getObjectByName(RightConfig.class, "RightConfig");

            for (Right availableRight : rights.getRights()) {
                String displayName = availableRight.getDisplayName();
                if (displayName == null) {
                    availableRights.add(new SelectItem(availableRight.getName()));
                } else {
                    availableRights.add(new SelectItem(availableRight.getName(), displayName));
                }
            }
        } catch (IllegalArgumentException e) {
            // If we can't get them, hardcode the existing 1.1 permissions and log the error
            log.error(EditablePermissions.class.getName() + ": Could not fetch the RightConfig object", e);
            availableRights.clear();
            availableRights.add(new SelectItem("create", "create"));
            availableRights.add(new SelectItem("read", "read"));
            availableRights.add(new SelectItem("update", "update"));
            availableRights.add(new SelectItem("delete", "delete"));
            availableRights.add(new SelectItem("execute", "execute"));
        } catch (GeneralException e) {
            // If we can't get them, hardcode the existing 1.1 permissions and log the error
            log.error(EditablePermissions.class.getName() + ": Could not fetch the RightConfig object", e);
            availableRights.clear();
            availableRights.add(new SelectItem("create", "create"));
            availableRights.add(new SelectItem("read", "read"));
            availableRights.add(new SelectItem("update", "update"));
            availableRights.add(new SelectItem("delete", "delete"));
            availableRights.add(new SelectItem("execute", "execute"));
            
        }

        return availableRights;
    }
} // class EditablePermissions

