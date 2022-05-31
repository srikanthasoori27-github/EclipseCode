/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.application;

import sailpoint.web.BaseDTO;

/**
 * Created by ryan.pickens on 5/25/17.
 */
public abstract class AttributeDTO extends BaseDTO {


    SchemaDTO _schemaDTO;

    AttributeDTO(SchemaDTO dto) {
        this._schemaDTO = dto;
    }



    public SchemaDTO getSchemaDTO() {
        return _schemaDTO;
    }

    public void setSchemaDTO(SchemaDTO schemaDTO) {
        this._schemaDTO = schemaDTO;
    }

    public abstract void saveAttributeData();


}
