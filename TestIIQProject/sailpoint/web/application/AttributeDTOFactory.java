/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.application;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.SecurityIQConnector;

/**
 * Created by ryan.pickens on 5/25/17.
 */
public class AttributeDTOFactory {

    private static Log log = LogFactory.getLog(AttributeDTOFactory.class);

    public static AttributeDTO getAttributeDTO(SchemaDTO bean) {
        if (SecurityIQConnector.CONNECTOR_TYPE.equals(bean.getAppObjectBean().getType())) {
            return getSIQAttributeDTO(bean);
        } else {
            return null;
        }
    }


    public static AttributeDTO getSIQAttributeDTO(SchemaDTO dto) {
        return new SIQAttributeDTO(dto);
    }

}
