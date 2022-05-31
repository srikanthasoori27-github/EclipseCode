package sailpoint.web.application;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.AttributeDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.SchemaAttributeDTO;

/**
 * Created by ryan.pickens on 11/3/14.
 */
public class AttributeDefinitionEditBean extends BaseBean {

    private static final Log log = LogFactory.getLog(AttributeDefinitionEditBean.class);

    //JSON Keys
    private static String JSON_SCHEMA_OBJECT_TYPE = "schemaObjectType";
    private static String JSON_ATTR_UID = "uid";

    private static String JSON_ATTR_MANAGED = "managed";
    private static String JSON_ATTR_ENTITLEMENT = "entitlement";
    private static String JSON_ATTR_MULTIVALUED = "multiValued";
    private static String JSON_ATTR_CORRELATIONKEY = "correlationKeyAssigned";
    private static String JSON_ATTR_MINABLE = "minable";
    private static String JSON_ATTR_INDEXED = "indexed";
    private static String JSON_ATTR_REMEDIATION_MODIFIABLE = "remediationModificationType";



    protected List<SchemaDTO> schemas;
    //JSON representation of the Attribute Definition being edited
    protected String updateAttributeJSON;

    public AttributeDefinitionEditBean(List<SchemaDTO> schemaDTO) {
        schemas = schemaDTO;
    }

    public void setUpdateAttributeJSON(String json) {
        this.updateAttributeJSON = json;
    }

    public String getUpdateAttributeJSON() {
        return this.updateAttributeJSON;
    }

    public List<SchemaDTO> getSchemas() {
        return this.schemas;
    }


    public String updateAttribute() throws GeneralException {
        //Deserialize JSON
        Map<String, Object> json = deserializeJSON(getUpdateAttributeJSON());

        if (json == null) {
            log.error("Could not deserialize JSON: " + getUpdateAttributeJSON());
            return "";
        }

        //Find the schema
        String schemaObjectType = Util.getString(json,JSON_SCHEMA_OBJECT_TYPE);
        SchemaDTO schema = null;
        if(Util.isNotNullOrEmpty(schemaObjectType)) {
            for(SchemaDTO dto : Util.safeIterable(getSchemas())) {
                if (dto.getObjectType().equals(schemaObjectType)) {
                    schema = dto;
                    break;
                }
            }

            if (schema == null) {
                log.error("Could not find matching Schema");
                return "";
            }
        } else {
            log.error("No Schema Object Type provided in JSON");
            return "";
        }

        //Find the attribute definition
        String attributeUID = Util.getString(json,JSON_ATTR_UID);
        SchemaAttributeDTO attribute = null;
        if(Util.isNotNullOrEmpty(attributeUID)) {
            attribute = schema.getAttributeByUid(attributeUID);
            if(attribute == null) {
                log.error("Could not find attribute on Schema " + schema.getObjectType() + " with UID " + attributeUID);
                return "";
            }

            //Set the properties on the definition
            updateAttributeFields(json, attribute);

        } else {
            log.error("No Attribute UID found in JSON. " + getUpdateAttributeJSON());
        }

        return "";
    }

    private Map<String, Object> deserializeJSON(String json) throws GeneralException {
        return JsonHelper.mapFromJson(String.class, Object.class, json);
    }

    private void updateAttributeFields(Map<String, Object> json, SchemaAttributeDTO dto) {

        //Set Managed
        dto.setManaged(Util.getBoolean(json,JSON_ATTR_MANAGED));

        //Set Entitlement
        dto.setEntitlement(Util.getBoolean(json,JSON_ATTR_ENTITLEMENT));

        //Set Multi-Vaued
        dto.setMultiValued(Util.getBoolean(json,JSON_ATTR_MULTIVALUED));

        //Set Correlation key
        dto.setCorrelationKeyAssigned(Util.getBoolean(json,JSON_ATTR_CORRELATIONKEY));

        //Set Minable
        dto.setMinable(Util.getBoolean(json,JSON_ATTR_MINABLE));

        //Set Indexed
        dto.setIndexed(Util.getBoolean(json,JSON_ATTR_INDEXED));

        //Set Remediation Modifiable
        try {
            AttributeDefinition.UserInterfaceInputType remediationType = null;
            String newRemediationType = Util.getString(json,JSON_ATTR_REMEDIATION_MODIFIABLE);
            if (!Util.isNullOrEmpty(newRemediationType)) {
                remediationType = AttributeDefinition.UserInterfaceInputType.valueOf(newRemediationType); 
            }
            dto.setRemediationModificationType(remediationType);
        } catch(IllegalArgumentException ex) {
            //Should we default if we can't get the enum value?
            log.error("Could not get enum value for Remediation Modifiable Type " + Util.getString(json,JSON_ATTR_REMEDIATION_MODIFIABLE));
        }


    }

    /**
     * Property to set on the schema (IdentityAttribute, DisplayAttribute, InstanceAttribute)
     */
    private static String JSON_ATTR_SCHEMA_PROP = "schemaProperty";
    public enum SchemaProp {
        IDENTITY,
        DISPLAY,
        INSTANCE
    };

    protected String updateSchemaPropertyJSON;

    public String getUpdateSchemaPropertyJSON() {
        return this.updateSchemaPropertyJSON;
    }

    public void setUpdateSchemaPropertyJSON(String s) {
        this.updateSchemaPropertyJSON = s;
    }

    public String updateSchemaAttribute() throws GeneralException {

        Map<String, Object> json = deserializeJSON(getUpdateSchemaPropertyJSON());

        SchemaDTO schema = getSchemaFromJSON(json);

        if (schema == null) {
            return "";
        }

        //Find the attribute definition
        String attributeUID = Util.getString(json,JSON_ATTR_UID);
        SchemaAttributeDTO attribute = null;
        if(Util.isNotNullOrEmpty(attributeUID)) {
            attribute = schema.getAttributeByUid(attributeUID);
            if(attribute == null) {
                log.error("Could not find attribute on Schema " + schema.getObjectType() + " with UID " + attributeUID);
                return "";
            }

            //Get the property to update
            String prop = Util.getString(json,JSON_ATTR_SCHEMA_PROP);
            if (Util.isNotNullOrEmpty(prop)) {
                try {
                    switch(SchemaProp.valueOf(prop)) {
                        case IDENTITY: schema.setIdentityAttribute(attribute.getName());
                            break;
                        case DISPLAY: schema.setDisplayAttribute(attribute.getName());
                            break;
                        case INSTANCE: schema.setInstanceAttribute(attribute.getName());
                            break;
                        default: break;
                    }
                } catch (IllegalArgumentException ex) {
                    log.error("Setting of Schema Property " + prop + " not supported.");
                }
            }
        } else {
            log.error("No Attribute UID found in JSON. " + getUpdateAttributeJSON());
        }




        return "";
    }

    private SchemaDTO getSchemaFromJSON(Map<String, Object> jsonMap) {
        //Find the schema
        String schemaObjectType = Util.getString(jsonMap,JSON_SCHEMA_OBJECT_TYPE);
        SchemaDTO schema = null;
        if(Util.isNotNullOrEmpty(schemaObjectType)) {
            for(SchemaDTO dto : Util.safeIterable(getSchemas())) {
                if (dto.getObjectType().equals(schemaObjectType)) {
                    return dto;
                }
            }

            if (schema == null) {
                log.error("Could not find matching Schema");
            }
        } else {
            log.error("No Schema Object Type provided in JSON");
        }
        return null;
    }

}
