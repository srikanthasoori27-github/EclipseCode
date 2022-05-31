package sailpoint.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.AttributeDefinition;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class SchemaAttributeDTO extends BaseDTO {
    private boolean selected;
    private String name;
    private String description;
    private String type;
    private String schemaObjectType;
    private boolean managed;
    private boolean entitlement;
    private boolean multiValued;
    private boolean correlationKeyAssigned;
    private boolean group;
    private boolean minable;
    private boolean indexed;
    private AttributeDefinition.UserInterfaceInputType remediationModificationType;
    private String compositeSourceAttribute;
    private String compositeSourceApplication;
    // internalName added for application reconfiguration task. 
    // This attribute will be used by the reconfigured connector
    private String internalName;
    private String objectMapping;

    public SchemaAttributeDTO(AttributeDefinition attributeDef) {
        super();
        this.name = attributeDef.getName();
        this.description = attributeDef.getDescription();
        setTypeFromAttrDef(attributeDef, this);
        this.managed = attributeDef.isManaged();
        this.entitlement = attributeDef.isEntitlement();
        this.multiValued = attributeDef.isMulti();
        this.correlationKeyAssigned = attributeDef.isCorrelationKeyAssigned();
        this.group = attributeDef.isGroup();
        this.minable = attributeDef.isMinable();
        this.indexed = attributeDef.isIndexed();
        this.remediationModificationType = attributeDef.getRemediationModificationType();
        this.compositeSourceApplication = attributeDef.getCompositeSourceApplication();
        this.compositeSourceAttribute = attributeDef.getCompositeSourceAttribute();
        this.internalName = attributeDef.getInternalName();
        this.objectMapping = attributeDef.getObjectMapping();
    }

    public SchemaAttributeDTO(AttributeDefinition attDef, String schemaObjectType) {
        this(attDef);
        this.schemaObjectType = schemaObjectType;
    }

    /**
     * Logic to determine if the type is contained in the AttributeType enum
     * @return true is the type value is contained in the AttributeType enum
     */
    public boolean isPrimitiveType() {
        return AttributeDefinition.AttributeType.hasValue(this.type);
    }
    
    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setSchemaObjectType(String type) {
        this.schemaObjectType = type;
    }

    public String getSchemaObjectType() { return this.schemaObjectType; }
    /**
     * Set the type on the DTO accordingly from the AttributeDefinition. We show the Type as the schemaObjectType
     * in the UI if the AttributeDefinition has a schemaObjectType present.
     *
     * @param ad
     *
     * @ignore
     * These were seperated in case we wanted a type of JSON/Map/etc. returned by the connector, but wanted to cast these
     * objects to a schema representation. If we don't ever anticipate this scenario, we could combine the two
     */
    public static void setTypeFromAttrDef(AttributeDefinition ad, SchemaAttributeDTO dto) {
        if(ad != null && dto != null) {
            //If the Attribute Definition has a schemaObjectType, set the type to the schemaObjectType
            if (Util.isNotNullOrEmpty(ad.getSchemaObjectType())) {
                dto.setType(ad.getSchemaObjectType());
            } else {
                dto.setType(ad.getType());
            }
        }
    }

    /**
     * Set the type on the AttributeDefinition depending on what is configured on the DTO. If the DTO type is not a primitive
     * type, we will set the type on the Attribute Definition to string and set the schemaObjectType to the DTO type. If
     * the DTO type is a primitive type, we will set the AttributeDefinition type to that of the DTO and clear the schemaObjectType
     * @param ad
     */
    public void setTypeFromDTO(AttributeDefinition ad, SchemaAttributeDTO dto) {
        //If not a primitive Type, we assume it maps to a schemaObjectType. Thus, set the schemaObjectType = type
        //and the type = string
        if(ad != null && dto != null) {
            if (!dto.isPrimitiveType()) {
                ad.setSchemaObjectType(dto.getType());
                ad.setType(AttributeDefinition.AttributeType.STRING.getValue());
            } else {
                ad.setType(dto.getType());
                ad.setSchemaObjectType(null);
            }
        }
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isManaged() {
        return managed;
    }

    public void setManaged(boolean managed) {
        this.managed = managed;
    }

    public boolean isEntitlement() {
        return entitlement;
    }

    public void setEntitlement(boolean entitlement) {
        this.entitlement = entitlement;
    }

    public boolean isMultiValued() {
        return multiValued;
    }

    public void setMultiValued(boolean multiValued) {
        this.multiValued = multiValued;
    }

    public boolean isCorrelationKeyAssigned() {
        return correlationKeyAssigned;
    }

    public void setCorrelationKeyAssigned(boolean correlationKeyAssigned) {
        this.correlationKeyAssigned = correlationKeyAssigned;
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(boolean group) {
        this.group = group;
    }

    public boolean isMinable() {
        return minable;
    }

    public void setMinable(boolean minable) {
        this.minable = minable;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public String getObjectMapping() { return objectMapping; }

    public void setObjectMapping(String s) { this.objectMapping = s; }

    /**
     * Return a CSV Representation of the Schema AttributeDefinition boolean attributes. This will return a CSV list
     * of the attributes (Managed,Entitlement,MultiValued,CorrelationKey,Minable) that are true.
     * @return
     */
    public String getCSVAttributes() {

        boolean needsComma = false;
        StringBuilder sb = new StringBuilder();
        if(isManaged()) {
            sb.append(new Message(MessageKeys.ATTR_MANAGED).getLocalizedMessage());
            needsComma=true;
        }

        if (isEntitlement()) {
            if (needsComma) {
                sb.append(", ");
            }
            sb.append(new Message(MessageKeys.ENTITLEMENT).getLocalizedMessage());
            needsComma=true;
        }

        if (isMultiValued()) {
            if (needsComma) {
                sb.append(", ");
            }
            sb.append(new Message(MessageKeys.ATTR_MULTI_VALUED).getLocalizedMessage());
            needsComma=true;
        }

        if (isCorrelationKeyAssigned()) {
            if (needsComma) {
                sb.append(", ");
            }
            sb.append(new Message(MessageKeys.ATTR_CORRELATION_KEY).getLocalizedMessage());
            needsComma=true;
        }

        if (isMinable()) {
            if (needsComma) {
                sb.append(", ");
            }
            sb.append(new Message(MessageKeys.ATTR_MINABLE).getLocalizedMessage());
        }

        if (isIndexed()) {
            if (needsComma) {
                sb.append(", ");
            }
            sb.append(new Message(MessageKeys.ATTR_INDEXED).getLocalizedMessage());
        }

        return sb.toString();
    }
    
    /**
     * Returns a JSON string of the attributes required for the Attribute Definition Schema popup
     * @return JSON object of the fields to populate for the popup
     */
    public String getJSONAttributes() {
        // Create a map with just the properties we want
        Map<String, Object> attributesMap = new HashMap<String, Object>() {{
            put("schemaObjectType", getSchemaObjectType());
            put("managed", isManaged());
            put("uid", getUid());
            put("entitlement", isEntitlement());
            put("multiValued", isMultiValued());
            put("correlationKeyAssigned", isCorrelationKeyAssigned());
            put("minable", isMinable());
            put("indexed", isIndexed());
            put("remediationModificationType", getRemediationModificationType());
        }};
        return JsonHelper.toJson(attributesMap);
    }

    public AttributeDefinition.UserInterfaceInputType getRemediationModificationType() {
        return remediationModificationType;
    }

    public void setRemediationModificationType(
            AttributeDefinition.UserInterfaceInputType remediationModificationType) {
        this.remediationModificationType = remediationModificationType;
    }

    public String getCompositeSourceAttribute() {
        return compositeSourceAttribute;
    }

    public void setCompositeSourceAttribute(String compositeSourceAttribute) {
        this.compositeSourceAttribute = compositeSourceAttribute;
    }

    public String getCompositeSourceApplication() {
        return compositeSourceApplication;
    }

    public void setCompositeSourceApplication(String compositeSourceApplication) {
        this.compositeSourceApplication = compositeSourceApplication;
    }
    
    public String getInternalName() {
        return internalName;
    }

    public void setInternalName(String internalName) {
        if ( Util.isNullOrEmpty( internalName ) ) {
            this.internalName = null;
        } else {
            this.internalName = internalName;
        }
    }

    public static List<SchemaAttributeDTO> getAttributeDTOs(Collection<AttributeDefinition> attributeDefinitions) {
        List<SchemaAttributeDTO> attributeDTOs = new ArrayList<SchemaAttributeDTO>();
        
        if (attributeDefinitions != null) {
            for (AttributeDefinition definition : attributeDefinitions) {
                SchemaAttributeDTO dto = new SchemaAttributeDTO(definition);
                dto.setName(definition.getName());
                dto.setDescription(definition.getDescription());
                setTypeFromAttrDef(definition, dto);
                dto.setManaged(definition.isManaged());
                dto.setEntitlement(definition.isEntitlement());
                dto.setMultiValued(definition.isMultiValued());
                dto.setCorrelationKeyAssigned(definition.isCorrelationKeyAssigned());
                dto.setGroup(definition.isGroup());
                dto.setMinable(definition.isMinable());
                dto.setIndexed(definition.isIndexed());
                dto.setRemediationModificationType(definition.getRemediationModificationType());
                dto.setCompositeSourceAttribute(definition.getCompositeSourceAttribute());
                dto.setCompositeSourceApplication(definition.getCompositeSourceApplication());
                dto.setInternalName(definition.getInternalName());
                dto.setObjectMapping(definition.getObjectMapping());
                attributeDTOs.add(dto);
            }
        }
        
        return attributeDTOs;
    }
    
    public void update(AttributeDefinition defToUpdate) {
        defToUpdate.setName(name);
        defToUpdate.setDescription(description);
        setTypeFromDTO(defToUpdate, this);
        defToUpdate.setManaged(managed);
        defToUpdate.setEntitlement(entitlement);
        defToUpdate.setMulti(multiValued);
        defToUpdate.setCorrelationKeyAssigned(correlationKeyAssigned);
        defToUpdate.setGroup(group);
        defToUpdate.setMinable(minable);
        defToUpdate.setIndexed(indexed);
        defToUpdate.setRemediationModificationType(remediationModificationType);
        defToUpdate.setInternalName(internalName);
        defToUpdate.setObjectMapping(objectMapping);
        // djs: BUG#5685 : is null valid for either of these? 
        // setCompositeSource just concationates the two values which will turn a null value to "null"
        if ( ( compositeSourceApplication != null ) || ( compositeSourceAttribute != null ) )
            defToUpdate.setCompositeSource(compositeSourceApplication, compositeSourceAttribute);
    }
}
