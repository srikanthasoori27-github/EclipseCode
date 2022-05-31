/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a RoleTypeDefinition.
 *
 * Author: Jeff
 * 
 */

package sailpoint.web.system;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.JsonUtil;
import sailpoint.object.Bundle;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.SPRight;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.Util;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.web.BaseDTO;
import sailpoint.web.util.WebUtil;

public class RoleTypeDefinitionDTO extends BaseDTO
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // This is currently a SailPointObject but it shouldn't be, we
    // only need the name.

    //TODO:Generate
    private static final long serialVersionUID = 1L;
    String _name;
    String _displayName;
    String _description;
    String _icon;

    String _rights;

    private List<String> _disallowedAttributes;
    private LazyLoad<AttributeSelections> _attributeSelections;

    boolean _noSupers;
    boolean _noSubs;
    boolean _noDetection;
    boolean _noDetectionUnlessAssigned;
    boolean _noProfiles;
    boolean _noAutoAssignment;
    boolean _noAssignmentSelector;
    boolean _noManualAssignment;

    boolean _noPermits;
    boolean _notPermittable;
    boolean _noRequirements;
    boolean _notRequired;
    boolean _noIIQ;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Make a DTO for a new type.
     */
    public RoleTypeDefinitionDTO() {
        _attributeSelections = new LazyLoad<AttributeSelections>(new ILazyLoader<AttributeSelections>() {
            public AttributeSelections load() {
                return new AttributeSelections(null);
            }
            
        });
    }

    /**
     * Make a DTO for the first time.
     */
    public RoleTypeDefinitionDTO(RoleTypeDefinition src) {

        _name = WebUtil.safeHTML(src.getName());
        _displayName = WebUtil.safeHTML(src.getDisplayName());
        // note that we use htmlify for better formatting
        // since description text can be hand edited
        _description = WebUtil.safeHTML(Util.htmlify(src.getDescription()));
        _icon = WebUtil.safeHTML(src.getIcon());
        _noSupers = src.isNoSupers();
        _noSubs = src.isNoSubs();
        _noDetection = src.isNoDetection();
        _noDetectionUnlessAssigned = src.isNoDetectionUnlessAssigned();
        _noProfiles = src.isNoProfiles();
        _noAutoAssignment = src.isNoAutoAssignment();
        _noAssignmentSelector = src.isNoAssignmentSelector();
        _noManualAssignment = src.isNoManualAssignment();
        _noPermits = src.isNoPermits();
        _notPermittable = src.isNotPermittable();
        _noRequirements = src.isNoRequirements();
        _notRequired = src.isNotRequired();
        _noIIQ = src.isNoIIQ();
        if (src.getRights() != null && !src.getRights().isEmpty()){
            List<String> rights = new ArrayList<String>();
            for(SPRight right : src.getRights()){
                rights.add(right.getName());
            }
            _rights = Util.listToCsv(rights);
        }
        _disallowedAttributes = src.getDisallowedAttributes();
        _attributeSelections = new LazyLoad<AttributeSelections>(new ILazyLoader<AttributeSelections>() {
            public AttributeSelections load() {
                return new AttributeSelections(_disallowedAttributes);
            }
            
        });
    }

    /**
     * Clone the DTO for editing.
     */
    public RoleTypeDefinitionDTO(RoleTypeDefinitionDTO src) {

        // be sure to taks the other uid so we can match them up later
        setUid(src.getUid());

        _name = src.getName();
        _displayName = src.getDisplayName();
        _description = src.getDescription();
        _icon = src.getIcon();
        _noSupers = src.isNoSupers();
        _noSubs = src.isNoSubs();
        _noDetection = src.isNoDetection();
        _noDetectionUnlessAssigned = src.isNoDetectionUnlessAssigned();
        _noProfiles = src.isNoProfiles();
        _noAutoAssignment = src.isNoAutoAssignment();
        _noAssignmentSelector = src.isNoAssignmentSelector();
        _noManualAssignment = src.isNoManualAssignment();
        _noPermits = src.isNoPermits();
        _notPermittable = src.isNotPermittable();
        _noRequirements = src.isNoRequirements();
        _notRequired = src.isNotRequired();
        _noIIQ = src.isNoIIQ();
        _rights = src.getRights();
        _disallowedAttributes = src.getDisallowedAttributes();
        _attributeSelections = new LazyLoad<AttributeSelections>(new ILazyLoader<AttributeSelections>() {
            public AttributeSelections load() {
                return new AttributeSelections(_disallowedAttributes);
            }
            
        });
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = WebUtil.safeHTML(s);
    }

    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String s) {
        _displayName = WebUtil.safeHTML(s);
    }
    
    /**
     * Used by the grid.
     */
    public String getDisplayableName() {
        return (_displayName != null && _displayName.trim().length() > 0) ? _displayName : _name;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
        _description = WebUtil.safeHTML(s);
    }
    
    public String getIcon() {
        return _icon;
    }

    public void setIcon(String s) {
        _icon = WebUtil.safeHTML(s);
    }

    public boolean isNoSupers() {
        return _noSupers;
    }

    public void setNoSupers(boolean b) {
        _noSupers = b;
    }

    public boolean isNoSubs() {
        return _noSubs;
    }

    public void setNoSubs(boolean b) {
        _noSubs = b;
    }

    public boolean isNoDetection() {
        return _noDetection;
    }

    public void setNoDetection(boolean b) {
        _noDetection = b;
    }

    public boolean isNoDetectionUnlessAssigned() {
        return _noDetectionUnlessAssigned;
    }

    public void setNoDetectionUnlessAssigned(boolean b) {
        _noDetectionUnlessAssigned = b;
    }
    
    public boolean isNoProfiles() {
        return _noProfiles;
    }

    public void setNoProfiles(boolean b) {
        _noProfiles = b;
    }

    public boolean isNoAutoAssignment() {
        return _noAutoAssignment;
    }

    public void setNoAutoAssignment(boolean b) {
        _noAutoAssignment = b;
    }

    public boolean isNoAssignmentSelector() {
        return _noAssignmentSelector;
    }

    public void setNoAssignmentSelector(boolean b) {
        _noAssignmentSelector = b;
    }

    public boolean isNoManualAssignment() {
        return _noManualAssignment;
    }

    public void setNoManualAssignment(boolean b) {
        _noManualAssignment = b;
    }

    public boolean isNoPermits() {
        return _noPermits;
    }

    public void setNoPermits(boolean b) {
        _noPermits = b;
    }

    public boolean isNotPermittable() {
        return _notPermittable;
    }

    public void setNotPermittable(boolean b) {
        _notPermittable = b;
    }

    public boolean isNoRequirements() {
        return _noRequirements;
    }

    public void setNoRequirements(boolean b) {
        _noRequirements = b;
    }

    public boolean isNotRequired() {
        return _notRequired;
    }

    public void setNotRequired(boolean b) {
        _notRequired = b;
    }

    public boolean isNoIIQ() {
        return _noIIQ;
    }

    public void setNoIIQ(boolean b) {
        _noIIQ = b;
    }

    public String getRights() {
        return _rights;
    }

    public void setRights(String rights) {
        _rights = rights;
    }
    
    public List<String> getDisallowedAttributes() {
        return _disallowedAttributes;
    }
    
    public void setDisallowedAttributes(List<String> val) {
        _disallowedAttributes = val;
    }
    
    public List<String> getSelectedIds() throws GeneralException {
        return _attributeSelections.getValue().getSelectedIds();
    }
    
    public void setSelectedIds(List<String> val) throws GeneralException {
        _attributeSelections.getValue().setSelectedIds(val);
    }
    
    public String getAllowedAttributesJson() throws Exception {

        return _attributeSelections.getValue().getAllowedAttributesJson();
    }
    
    /**
     * This class holds role attribute selection information.
     * This is showed in the role type grid at the bottom of 
     * the page.
     */
    public static class AttributeSelections implements Serializable {
        
        //TODO: generate
        private static final long serialVersionUID = 1L;
        
        private List<String> disallowedAttributes;
        private Map<String, Boolean> selectedState;
        private List<String> selectedIds;
        private String allowedAttributesJson;
        
        public AttributeSelections(List<String> disallowedAttributes) {
            this.disallowedAttributes = disallowedAttributes;
            load();
        }
        
        public List<String> getSelectedIds() {
            return this.selectedIds;
        }
        
        public void setSelectedIds(List<String> selectedIds) {
            this.selectedIds = selectedIds;
        }
        
        public String getAllowedAttributesJson() {
            return this.allowedAttributesJson;
        }
        
        public void setAllowedAttributesJson(String allowedAttributesJson) {
            this.allowedAttributesJson = allowedAttributesJson;
        }

        public List<String> createNewDisallowedAttributesFromSelectedIds() {
            
            List<String> val = new ArrayList<String>();
            for (String key : this.selectedState.keySet()) {
                if (this.selectedIds == null || !this.selectedIds.contains(key)) {
                    val.add(key);
                }
            }
            return val;
        }
        
        private void load() {
            
            this.selectedState = new HashMap<String,Boolean>();
            this.selectedIds = new ArrayList<String>();
            
            Map<String, Object> object = new HashMap<String, Object>();

            List<Map<String, Object>> attributeInfos = new ArrayList<Map<String, Object>>();
            List<ObjectAttribute> attributes = ObjectConfig.getObjectConfig(Bundle.class).getObjectAttributes();
            if (Util.isEmpty(attributes)) {
                object.put("numAttributes", 0);
            } else {
                for (ObjectAttribute attribute : attributes) {
                    Map<String, Object> attributeInfo = getAttributeInfo(attribute);
                    boolean allowed = isAllowed(attribute); 
                    this.selectedState.put(attribute.getName(), allowed);
                    attributeInfo.put("allowed", allowed);
                    if (allowed) {
                        this.selectedIds.add(attribute.getName());
                    }
                    attributeInfos.add(attributeInfo);
                }
                object.put("numAttributes", attributes.size());
            }
            object.put("attributes", attributeInfos);

            try {
                this.allowedAttributesJson = JsonUtil.render(object);
            } catch (Exception ex) {
                //TODO: log
                this.allowedAttributesJson = null;
            }
        }
        
        private Map<String, Object> getAttributeInfo(ObjectAttribute attr) {
            
            Map<String, Object> val = new HashMap<String, Object>();
            val.put("id", attr.getName());
            val.put("name", attr.getName());
            val.put("category", attr.getCategoryName());
            val.put("description", attr.getDescription());
            return val;
        }
        
        private boolean isAllowed(ObjectAttribute attr) {

            if (Util.isEmpty(this.disallowedAttributes)) {
                return true;
            }
            
            for (String disallowedAttr : this.disallowedAttributes){
                if (disallowedAttr.equals(attr.getName())) {
                    return false;
                }
            }
            return true;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert back to the persistent model when we're ready to commit.
     */
    public RoleTypeDefinition convert(SailPointContext ctx) throws GeneralException{
        
        RoleTypeDefinition type = new RoleTypeDefinition();
        type.setName(_name);
        type.setDisplayName(_displayName);
        type.setDescription(_description);
        type.setIcon(_icon);

        type.setNoSupers(_noSupers);
        type.setNoSubs(_noSubs);
        type.setNoDetection(_noDetection);
        type.setNoDetectionUnlessAssigned(_noDetectionUnlessAssigned);
        type.setNoProfiles(_noProfiles);
        type.setNoAutoAssignment(_noAutoAssignment);
        type.setNoAssignmentSelector(_noAssignmentSelector);
        type.setNoManualAssignment(_noManualAssignment);

        type.setNoPermits(_noPermits);
        type.setNotPermittable(_notPermittable);
        type.setNoRequirements(_noRequirements);
        type.setNotRequired(_notRequired);
        type.setNoIIQ(_noIIQ);
        type.setDisallowedAttributes(_attributeSelections.getValue().createNewDisallowedAttributesFromSelectedIds());

        if (_rights != null){
            List<SPRight> rights = new ArrayList<SPRight>();
            for(String right : Util.csvToList(_rights)){
                rights.add(ctx.getObjectByName(SPRight.class, right));
            }
            if (!rights.isEmpty())
                type.setRights(rights);
        }

        return type;
    }


}
