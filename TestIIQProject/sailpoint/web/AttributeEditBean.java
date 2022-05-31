/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.BaseAttributeDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTypeDefinition;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.modeler.EditablePermissions;
import sailpoint.web.modeler.RoleAttributesUtil.IFilteredAttributesInfo;
import sailpoint.web.system.ObjectAttributeDTO;
import sailpoint.web.util.WebUtil;

public class AttributeEditBean extends BaseDTO implements Serializable {
    private static final long serialVersionUID = 4252988364760980102L;
    
    private static final Log log = LogFactory.getLog(AttributeEditBean.class);
    
    // the Object whose attributes we are talking about
    String editedObjectId; 
    Class editedObjectClass;
    protected BaseBean ownerBean;
    Map<String, BaseAttributeDefinition> objectAttributeMap;
    List<String> viewAttributes;
    Attributes<String, Object> attributes;
    Map<String, EditablePermissions> permissions;
    Map<String, String> dateValues;
    Map<String, Boolean> boolValues;
    Map<String, IdentityProxy> identities;
    List<SelectItem> availableRights;
    private List<? extends BaseAttributeDefinition> attributeDefinitions;
    private List<? extends BaseAttributeDefinition> missingAttributeDefinitions;
    private Map<String, ObjectAttributeDTO> missingAttributesInfo;
    private List<ObjectAttributeDTO> objectAttributeDTOs;
    
    /**
     * Build an AttributeEditBean
     * @param editedObjectId ID for the object that is being edited; null if a new object is being edited or if we don't plan to tinker with extended identity attributes from the object
     * @param editedObjectClass Class of the object that is being edited
     * @param objectConfig The ObjectConfig for the type of SailPointObject whose attributes are being edited
     * @param viewAttributes Ordered list of names for attributes that are viewable
     * @param attributeValues Map of attribute values keyed by attribute name
     */
    public AttributeEditBean(String editedObjectId, Class editedObjectClass, List<? extends BaseAttributeDefinition> objectAttributes, List<String> viewAttributes, Attributes<String, Object> attributeValues) {
        init(editedObjectId, editedObjectClass, objectAttributes, viewAttributes, attributeValues);
    }

    public AttributeEditBean(String editedObjectId, Class editedObjectClass, BaseBean ownerBean, List<? extends BaseAttributeDefinition> objectAttributes, List<String> viewAttributes, Attributes<String, Object> attributeValues) {
        this.ownerBean = ownerBean;
        init(editedObjectId, editedObjectClass, objectAttributes, viewAttributes, attributeValues);
    }
    
    private void init(String editedObjectId, Class editedObjectClass, Collection<? extends BaseAttributeDefinition> objectAttributes, List<String> viewAttributes, Attributes<String, Object> attributeValues) {
        
        this.editedObjectId = editedObjectId;
        this.editedObjectClass = editedObjectClass;
        this.objectAttributeMap = new HashMap<String, BaseAttributeDefinition>();
        if (objectAttributes != null && !objectAttributes.isEmpty()) {
            for (BaseAttributeDefinition attribute : objectAttributes) {
                objectAttributeMap.put(attribute.getName(), attribute);
            }
        }
        
        if (viewAttributes != null && !viewAttributes.isEmpty()) {
            this.viewAttributes = new ArrayList<String>();
            this.viewAttributes.addAll(viewAttributes);
        } else {
            this.viewAttributes = null;
        }
        
        this.attributes = attributeValues;
        this.dateValues = new HashMap<String, String>();
        this.permissions = new HashMap<String, EditablePermissions>();
        this.identities = new HashMap<String, IdentityProxy>();
        this.boolValues = new HashMap<String, Boolean>();
        
        translateAttributes(attributeValues);
    }
    
    /**
     * Translate the attribute values provided into objects that we can store in the bean.
     * This is also called before the roleEditorBean calls launchworkflow.  In case there is
     * an exception thrown from the workflow, this allows the exception to be shown in the UI,
     * since there may not be another chance to grab the correct values.  More info in bug 6561.
     */
    public void translateAttributes(Attributes<String, Object> attributeValues) {
        
        @SuppressWarnings("unchecked")
        List<? extends BaseAttributeDefinition> attributeDefinitions = (List<? extends BaseAttributeDefinition>)getAttributeDefinitions();
        
        if (attributeDefinitions != null && !attributeDefinitions.isEmpty()) {
            for (BaseAttributeDefinition attributeDefinition: attributeDefinitions) {
                if (attributeDefinition != null) {
                    String attributeName = attributeDefinition.getName();
                    if (BaseAttributeDefinition.TYPE_PERMISSION.equals(attributeDefinition.getType())) {
                        Permission p = (Permission) attributeValues.get(attributeName);
                        EditablePermissions permsDTO = new EditablePermissions(p);
                        permissions.put(attributeName, permsDTO);
                    } else if (BaseAttributeDefinition.TYPE_DATE.equals(attributeDefinition.getType())) {
                        Date dateValue = attributeValues.getDate(attributeName);
                        if (dateValue == null) {
                            dateValues.put(attributeName, "");
                        } else {
                            dateValues.put(attributeName, String.valueOf(dateValue.getTime()));
                        }
                    } else if (BaseAttributeDefinition.TYPE_BOOLEAN.equals(attributeDefinition.getType())) { 
                        Boolean boolVal = attributeValues.getBooleanObject(attributeName);
                        if(boolVal == null) {
                            boolValues.put(attributeName,  null);
                        } else {
                            boolValues.put(attributeName, boolVal);
                        }
                    } else if (BaseAttributeDefinition.TYPE_INT.equals(attributeDefinition.getType())) {
                        // jsf page references attributes for Integer type attribute.
                        // Be sure to set the value as an Integer. Field validity is checked.
                        try {
                            Integer intVal = attributes.getInteger(attributeName);
                            // This will put either an Integer value or null
                            attributes.put(attributeName, intVal);
                        } catch (NumberFormatException nfe) {
                            log.warn("Incorrect integer value for " + attributeName + ". Exception: " + nfe.getMessage());
                        }
                    } else if (ObjectAttribute.TYPE_IDENTITY.equals(attributeDefinition.getType())) {
                        SailPointObject editedObject = getEditedObject();
                        if (editedObject != null) {
                            if (attributeDefinition instanceof ObjectAttribute && ((ObjectAttribute)attributeDefinition).getExtendedNumber() > 0 && editedObject.supportsExtendedIdentity()) {
                                // the value will be contained in extendedIdentity1 etc which are
                                // relationships.
                                loadIdentityInfoFromExtendedIdentityAttributes((ObjectAttribute)attributeDefinition);
                            } else {
                                // It is also possible that the value may be stored
                                // as identity names (string values) inside the attributes.
                                // this will be true for Application and role identity
                                // extended attributes.
                                loadIdentityInfoFromAttributes(attributeName, attributeValues.get(attributeName));
                            }
                        }
                    }
                }
            }
            availableRights = EditablePermissions.getAvailableRights(getContext());
        }
    }
    
    public void reloadAttributes(Attributes<String, Object> attributes) {
        
        fetchAttributeDefinitions();
        init(this.editedObjectId, this.editedObjectClass, this.objectAttributeMap.values(), this.viewAttributes, attributes);
    }
    
    private void loadIdentityInfoFromAttributes(String attributeName, Object value) {
        Identity identity = null;
        if (value == null) {
            identity = null;
        } else if (value instanceof String) {
            try {
                identity = SailPointFactory.getCurrentContext().getObjectByName(Identity.class, (String) value);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        } else if (value instanceof Identity) {
            identity = (Identity) value;
        } else {
            throw new IllegalStateException("Invalid value for identity type");
        }

        this.identities.put(attributeName, new IdentityProxy(identity));
    }
    
    private void loadIdentityInfoFromExtendedIdentityAttributes(ObjectAttribute attributeDefinition) {
        SailPointObject editedObject = getEditedObject();
        if (editedObject != null) {
            Identity relatedIdentity = editedObject.getExtendedIdentity(attributeDefinition.getExtendedNumber());
            this.identities.put(attributeDefinition.getName(), new IdentityProxy(relatedIdentity));
        }
    }
    
    public List<? extends BaseAttributeDefinition> getAttributeDefinitions() {
        
        if (this.attributeDefinitions == null) {
            fetchAttributeDefinitions();
        }
        
        return this.attributeDefinitions;
    }
    
    private List<ObjectAttributeDTO> getObjectAttributeDTOs() { 
        if (this.objectAttributeDTOs == null) {
           loadObjectAttributeDTOs();
            
        }
        
        return this.objectAttributeDTOs;
    }
    
    private void loadObjectAttributeDTOs() {
        this.objectAttributeDTOs = new ArrayList<ObjectAttributeDTO>();
        List<? extends BaseAttributeDefinition> attrDefs = getAttributeDefinitions();
        if (attrDefs != null) {
            for (BaseAttributeDefinition attrDef: attrDefs) {
                ObjectAttributeDTO dto = new ObjectAttributeDTO(editedObjectClass.getSimpleName(), (ObjectAttribute)attrDef);
                this.objectAttributeDTOs.add(dto);
                
                //set AllowedValues for Identity Type to List of Pair
                if (ObjectConfig.IDENTITY.equals(editedObjectClass.getSimpleName()) && Identity.ATT_TYPE.equals(attrDef.getName())) {
                    ObjectConfig config = Identity.getObjectConfig();
                    List<Object> types = new ArrayList<Object>();
                    for (IdentityTypeDefinition def : config.getIdentityTypesList()) {
                        types.add(new Pair(def.getName(), getMessage(def.getDisplayName())));
                    }
                    dto.setAllowedValues(types);
                }
            }
        }
    }
    
    /**
     * return a list of attribute definitions which don't make
     * sense but values for which exist nevertheless. We need
     * this to show warning in the UI.
     */
    public Map<String, ObjectAttributeDTO>  getMissingAttributesInfo() {

        if (this.missingAttributesInfo == null) {
            loadMissingAttributesInfo();
        }        
        
        return this.missingAttributesInfo;
    }
    
    private void loadMissingAttributesInfo() {
        
        this.missingAttributesInfo = new HashMap<String, ObjectAttributeDTO>();
        
        for (BaseAttributeDefinition missingAttribute : this.missingAttributeDefinitions) {
            this.missingAttributesInfo.put(missingAttribute.getName(), new ObjectAttributeDTO(editedObjectClass.getSimpleName(), (ObjectAttribute)missingAttribute));
        }
    }
    
    private void fetchAttributeDefinitions() {
        // Never allow editing of non-viewable attributes... filter them here
        Collection<? extends BaseAttributeDefinition> allDefsCollection = this.objectAttributeMap.values();
        List<BaseAttributeDefinition> allDefs = Arrays.asList(allDefsCollection.toArray(new BaseAttributeDefinition[allDefsCollection.size()]));        
        List<BaseAttributeDefinition> viewFilteredDefs = filterViewAttributeDefinitions(allDefs);
        
        IFilteredAttributesInfo filteredInfo = getFilteredAttributesInfo(viewFilteredDefs);
        this.attributeDefinitions = filteredInfo.getFilteredAttributeDefinitions();
        this.missingAttributeDefinitions = filteredInfo.getMissingAttributeDefinitions();

        loadObjectAttributeDTOs();
        loadMissingAttributesInfo();
    }
    
    private List<BaseAttributeDefinition> filterViewAttributeDefinitions(List<BaseAttributeDefinition> allDefs) {
        
        List<BaseAttributeDefinition> filteredDefs = new ArrayList<BaseAttributeDefinition>();
        
        if (viewAttributes == null || viewAttributes.isEmpty()) {
            if (allDefs != null && !allDefs.isEmpty())
                filteredDefs.addAll(allDefs);
        } else {
            for (String attributeName : viewAttributes) {
                BaseAttributeDefinition attribute = objectAttributeMap.get(attributeName);
                // Make sure that this viewable attribute actually exists
                if (attribute != null) 
                    filteredDefs.add(attribute);
            }
        }
        
        return filteredDefs;
    }
    
    /**
     * By default this method does nothing.
     * override this method in inherited classes to
     * filter out attributes that should not be shown.
     * IMP NOTE: if you modify the list then you should CLONE
     * and return a new list instead of modify the original list.
     *
     * This also returns a list of missing attributes which have been
     * filtered from the list (perhaps due to role type change) but they are still present in the object.
     * So they are a list of deprecated attributedefinitions.
     */
    protected IFilteredAttributesInfo getFilteredAttributesInfo(final List<? extends BaseAttributeDefinition> attributeDefinitions) {
        
        return new IFilteredAttributesInfo() {
            
            public List<? extends BaseAttributeDefinition> getMissingAttributeDefinitions() {
                return new ArrayList<BaseAttributeDefinition>();
            }
            
            public List<? extends BaseAttributeDefinition> getFilteredAttributeDefinitions() {
                return attributeDefinitions;
            }
        };
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getCategories()
    {
        List<String> categories = new ArrayList<String>(); 
        
        List<BaseAttributeDefinition> allAttrs = (List<BaseAttributeDefinition>)getAttributeDefinitions();
        for (BaseAttributeDefinition attr : allAttrs)
        {
            String categoryName = attr.getCategoryName();
            if (categoryName == null || categoryName.trim().length() == 0)
            {
                continue;
            }
            categories.add(categoryName);
        }
        
        return categories;
    }
    
    @SuppressWarnings("unchecked")
    public List<ObjectAttributeDTO> getAttributeDefinitionsWithNoCategory()
    {
        List<ObjectAttributeDTO> noCategoryAttributes = new ArrayList<ObjectAttributeDTO>();
        
        List<ObjectAttributeDTO> allAttrs = getObjectAttributeDTOs();
        for (ObjectAttributeDTO attr : allAttrs)
        {
            String categoryName = attr.getCategoryName();
            if (categoryName == null || categoryName.trim().length() == 0)
            {
            	noCategoryAttributes.add(new ObjectAttributeDTO(attr));
            }
        }
        
        return noCategoryAttributes;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, List<ObjectAttributeDTO>> getAttributeDefinitionsByCategory()
    {
        Map<String, List<ObjectAttributeDTO>> attributesMap = new HashMap<String, List<ObjectAttributeDTO>>();

        List<ObjectAttributeDTO> allAttrs = getObjectAttributeDTOs();
        for (ObjectAttributeDTO attr : allAttrs)
        {
            String categoryName = attr.getCategoryDisplayName(getLocale(), getUserTimeZone());
            if (categoryName == null || categoryName.trim().length() == 0)
            {
                continue;
            }
            
            if (!attributesMap.containsKey(categoryName))
            {
                attributesMap.put(categoryName, 
                        new ArrayList<ObjectAttributeDTO>());
            }
            
            attributesMap.get(categoryName).add(new ObjectAttributeDTO(attr));
        }
        
        return attributesMap;
    }
    

    public void setAttributeDefinitions(List<? extends BaseAttributeDefinition> attributeDefinitions) {
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes.setMap(attributes);
    }
    
    public Map<String, String> getDateValues() {
        return dateValues;
    }
    
    public void setDateValues(Map<String, String> dateValues) {
        if (this.dateValues != dateValues) {
            this.dateValues.clear();
            this.dateValues.putAll(dateValues);
        }
    }
    
    public Map<String, Boolean> getBoolValues() {
        return boolValues;
    }
    
    public void setBoolValues(Map<String, Boolean> boolValues) {
        if (this.boolValues != boolValues) {
            this.boolValues.clear();
            this.boolValues.putAll(boolValues);
        }
    }

    public Map<String, IdentityProxy> getIdentities() {
        return identities;
    }

    public void setIdentities(Map<String, IdentityProxy> identities) {
        if (this.identities != identities) {
            this.identities.clear();
            this.identities.putAll(identities);
        }
    }
    
    public Map<String, EditablePermissions> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(Map<String, EditablePermissions> permissions) {
        if (this.permissions != permissions) {
            this.permissions.clear();
            this.permissions.putAll(permissions);
        }
    }

    public List<SelectItem> getAvailableRights() {
        return availableRights;
    }
    
    /**
     * @throws GeneralException when there is a problem fetching an Identity from persistent storage
     *
     * jsl - this appears to be copying edits made using some special helper
     * objects over to the "attributes" map which is what applyChnagesToObject and commit()
     * look at.  
     */
    public boolean applySpecialValues() throws GeneralException {
        boolean anyChanged = false;

        List<? extends BaseAttributeDefinition> attributeDefinitions = getAttributeDefinitions();
        for (BaseAttributeDefinition attributeDefinition: attributeDefinitions) {
            String attributeName = attributeDefinition.getName();
            if (BaseAttributeDefinition.TYPE_PERMISSION.equals(attributeDefinition.getType())) {
                Permission p = (Permission) attributes.get(attributeName);
                EditablePermissions ep = permissions.get(attributeName);
                p.setRights(Util.listToCsv(ep.getSelectedRights()));
                p.setTarget(ep.getTarget());
                anyChanged = true;
            } 
            else if (BaseAttributeDefinition.TYPE_DATE.equals(attributeDefinition.getType())) {
                String dateString = dateValues.get(attributeName);
                if (dateString != null && dateString.trim().length() > 0) {
                    Long dateInMillis = Long.parseLong(dateString);
                    attributes.put(attributeName, new Date(dateInMillis.longValue()));
                } else {
                    attributes.put(attributeName, null);
                }
                anyChanged = true;
            }
            else if (BaseAttributeDefinition.TYPE_BOOLEAN.equals(attributeDefinition.getType())) {
                Boolean boolVal = boolValues.get(attributeName);
                if(boolVal != null) {
                    attributes.put(attributeName, boolVal);
                } else {
                    attributes.put(attributeName, null);
                }
                anyChanged=true;
            }
            else if (BaseAttributeDefinition.TYPE_INT.equals(attributeDefinition.getType())) {
                // jsf page references attributes for Integer type attribute.
                // Be sure to set the value as an Integer. Field validity is checked.
                try {
                    Integer intVal = attributes.getInteger(attributeName);
                    // This will put either an Integer value or null
                    attributes.put(attributeName, intVal);
                } catch (NumberFormatException nfe) {
                    log.warn("Incorrect integer value for " + attributeName + ". Exception: " + nfe.getMessage());
                }
                anyChanged=true;
            }
            else if (ObjectAttribute.TYPE_IDENTITY.equals(attributeDefinition.getType())) {
                //TQM: why are we storing the whole identity object (huge xml including dependent objects) in the object config?
                // jsl - good question, we absolutely don't want to do this..
                IdentityProxy identityProxy = identities.get(attributeName);
                if (identityProxy != null) {
                    String id = identityProxy.getId();
                    if (id != null && id.trim().length() > 0) {
                        Identity identity = getContext().getObjectById(Identity.class, id);
                        if (attributeName.compareToIgnoreCase(Identity.ATT_MANAGER) == 0) {
                            attributes.put(attributeName, identity);
                        } else {
                            attributes.put(attributeName, identity.getName());
                        }
                    } else {
                        attributes.put(attributeName, null);
                    }
                } else {
                    attributes.put(attributeName, null);
                }
                anyChanged = true;
            }
        }

        return anyChanged;
    }
    
    /**
     * Determine which attributes have been changed and build
     * a Map<ObjectAttribute,Object> representing the changes.  This is what
     * we now use in IdentityBean to convert the changes held in AttributeEditBean
     * into a ProvisioningPlan.
     */
    public Map<BaseAttributeDefinition,Object> getChanges(SailPointObject obj) throws GeneralException {

        Map<BaseAttributeDefinition,Object> changes = null;

        // commit values being held in helper objects back to the attributes map
        applySpecialValues();

        Map<String, Object> editedAttributes = getAttributes();
        Set<String> attributeNames = editedAttributes.keySet();
        if (attributeNames != null) {
            for (String attributeName : attributeNames) {
                BaseAttributeDefinition attributeDef = objectAttributeMap.get(attributeName);
                boolean isEditable = true;
                if (attributeDef instanceof ObjectAttribute) {
                    isEditable = ((ObjectAttribute)attributeDef).isEditable();
                }
                if (attributeDef != null && isEditable) {
                    Object originalValue = null;
                    if (obj instanceof Link) {
                        Link link = (Link)obj;
                        originalValue = link.getAttribute(attributeName);
                    }
                    else {
                        SailPointObject editedObject = getEditedObject();
                        if (ObjectAttribute.TYPE_IDENTITY.equals(attributeDef.getType()) 
                            && editedObject != null
                            && editedObject.supportsExtendedIdentity() 
                            && ((ObjectAttribute)attributeDef).getExtendedNumber() > 0 
                            && attributeDef.getName().compareTo(Identity.ATT_MANAGER) != 0) {
                            
                            Identity relatedIdentity = obj.getExtendedIdentity(((ObjectAttribute)attributeDef).getExtendedNumber());
                            if (relatedIdentity != null) {
                                originalValue = relatedIdentity.getId();
                            }
                        } else if (obj instanceof Identity) {
                            originalValue = ObjectUtil.getObjectAttributeValue(obj, ((ObjectAttribute)attributeDef));
                        }
                    }
                    
                    // Coerce the values into their types so that comparisons
                    // will not return false positives.
                    Object newValue = editedAttributes.get(attributeName);
                    Object valueToCheck = editedAttributes.get(attributeName);

                    if (ObjectAttribute.TYPE_IDENTITY.equals(attributeDef.getType())) {
                        IdentityProxy proxy = identities.get(attributeName);
                        valueToCheck = (null == proxy) ? null : proxy.getId();
                    }
                    else if (ObjectAttribute.TYPE_BOOLEAN.equals(attributeDef.getType()) ||
                             (originalValue instanceof Boolean)) {
                        // Note that we coerce if the type is boolean or the original
                        // value is a boolean.  This is because some of the attribute
                        // definitions don't have a type (eg - inactive) but are
                        // automatically coerced during attribute promotion.
                        originalValue = Util.otob(originalValue);
                        valueToCheck = Util.otob(valueToCheck);
                    }
                    else {
                        // TODO: consider handling other types that might need coersion.
                    }

                    if (!Util.nullSafeEq(originalValue, valueToCheck, true, true)) {
                        if (changes == null)
                            changes = new HashMap<BaseAttributeDefinition,Object>();
                        changes.put(attributeDef, newValue);
                    }
                }
            }
        }

        return changes;
    }

    /**
     * Called to commit changes to an object that does not maintain
     * attribute metadata.  
     *
     * This is used for Application which also has to be careful about the
     * values to be copied since the Attributes map has a mixture of 
     * config attributes and extended attributes.  Only copy things that
     * are defined as extended attributes, everything else may be
     * stale.
     */
    public boolean commit(SailPointObject obj) throws GeneralException {

        boolean anyChanged = applySpecialValues();

        // the ObjectConfig guides us, not the map keys
        Collection<BaseAttributeDefinition> defs = objectAttributeMap.values();
        if (defs != null) {
            Map<String,Object> editedAttributes = getAttributes();
            Map<String,Object> destAttributes = obj.getExtendedAttributes();
            if (destAttributes == null) {
                // we have no interface to make one, the parent bean
                // needs to see that it exists
                throw new GeneralException("Missing destination attribute map");
            }

            for (BaseAttributeDefinition def : defs) {
                if (def instanceof ObjectAttribute && !obj.isExtendedIdentityType((ObjectAttribute)def)) {
                    String name = def.getName();
                    destAttributes.put(name, editedAttributes.get(name));
                    anyChanged = true;
                }
            }
        }

        return anyChanged;
    }

    @SuppressWarnings("serial")
    public class IdentityProxy implements Serializable {
        private String id;

        public IdentityProxy(Identity identity) {
            if (identity == null) { 
                this.id = null;
            }
            else {
                this.id = identity.getId();
            }
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getDisplayableName() throws GeneralException {
            return WebUtil.getDisplayNameForId(Identity.class.getSimpleName(), this.id);
        }
        
        public void setDisplayableName(String val) {
            // do nothing
        }
        
        public void setIdentity(Identity identity) {
            this.id = identity.getId();
        }
        
        public Identity getIdentity() throws GeneralException {
            if (id == null)
                return null;
            else
                return getContext().getObjectById(Identity.class, id);
        }
    }
    
    private SailPointObject getEditedObject() {
        SailPointObject editedObject;
        try {
            if (editedObjectId == null) {
                editedObject = (SailPointObject) editedObjectClass.getConstructor().newInstance();
            } else {
                editedObject = getContext().getObjectById(editedObjectClass, editedObjectId);
            }
        } catch (IllegalArgumentException e) {
            log.error("Could not instantiate a " + editedObjectClass.getName() + " object with id " + editedObjectId, e);
            editedObject = null;
        } catch (SecurityException e) {
            log.error("Could not instantiate a " + editedObjectClass.getName() + " object with id " + editedObjectId, e);
            editedObject = null;
        } catch (GeneralException e) {
            log.error("Could not instantiate a " + editedObjectClass.getName() + " object with id " + editedObjectId, e);
            editedObject = null;
        } catch (InstantiationException e) {
            log.error("Could not instantiate a " + editedObjectClass.getName() + " object with id " + editedObjectId, e);
            editedObject = null;
        } catch (IllegalAccessException e) {
            log.error("Could not instantiate a " + editedObjectClass.getName() + " object with id " + editedObjectId, e);
            editedObject = null;
        } catch (InvocationTargetException e) {
            log.error("Could not instantiate a " + editedObjectClass.getName() + " object with id " + editedObjectId, e);
            editedObject = null;
        } catch (NoSuchMethodException e) {
            log.error("Could not instantiate a " + editedObjectClass.getName() + " object with id " + editedObjectId, e);
            editedObject = null;
        }
        
        return editedObject;
    }

    public Map<String, BaseAttributeDefinition> getObjectAttributeMap() {
        return this.objectAttributeMap;
    }
}
