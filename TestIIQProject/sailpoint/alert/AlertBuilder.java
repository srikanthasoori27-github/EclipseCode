/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.alert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Alert;
import sailpoint.object.Application;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.persistence.Sequencer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * AlertBuilder is responsible for creating the Alert object. There are a couple of
 * behavioral functions consolidated here so they can be shared by SCIM or Aggregation
 * code.
 */
public class AlertBuilder {
    
    private static final Log log = LogFactory.getLog(AlertBuilder.class);
    
    private SailPointContext context;
    
    public AlertBuilder(SailPointContext context) {
        this.context = context;
    }

    public Alert build(ResourceObject obj, Application appSource, Schema currentSchema) throws GeneralException {
        //TODO: Will we ever update old Alerts, or do we treat everything as a new alert?
        //For now we will create new alerts for any RO returned from the connector (should use delta by default)
        if (obj.isDelete()) {
            log.error("Delete not supported for ResourceObjects. Ignoring");
            return null;
        }

        Alert a = new Alert();
        a.setSource(appSource);
        a.setNativeId(obj.getIdentity());
        a.setDisplayName(buildDisplayName(obj, currentSchema));
        a.setAttributes(obj.getAttributes());
        a.setName(buildName(a));

        //TODO: Is this correct? Assume connectors set this correct
        a.setType(obj.getObjectType());


        return a;
    }
    
    /**
     * Builds the name of the alert using the Sequencer class
     * @param a the Alert
     * @return a new name for the alert
     * @throws GeneralException
     */
    public String buildName(Alert a) throws GeneralException {
        Sequencer sequencer = new Sequencer();
        return sequencer.generateId(context, a);
    }
    
    /**
     * Logic here uses the ResourceObject displayName or the value of the schema display attribute
     * if ResourceObject displayName is null
     * @param obj ResourceObject
     * @param schema Schema, most likely the Alert schema
     * @return a new displayname
     */
    public String buildDisplayName(ResourceObject obj, Schema schema) {
        //Use the displayName on the ResourceObject if preset, if not use the schema's displayAttribute.
        //SHould this order be reversed? -rap
        String result = null;
        if (Util.isNotNullOrEmpty(obj.getDisplayName())) {
            result = obj.getDisplayName();
        } else if (Util.isNotNullOrEmpty(schema.getDisplayAttribute())) {
            result = obj.getString(schema.getDisplayAttribute());
        }
        return result;
    }
    
    public String buildDisplayName(Alert obj) {
        String result = null;
        if (Util.isNotNullOrEmpty(obj.getDisplayName())) {
            result = obj.getDisplayName();
        } else if (obj.getSource() != null && obj.getSource().getSchema(Schema.TYPE_ALERT) != null) {
            String displayAttrName = obj.getSource().getSchema(Schema.TYPE_ALERT).getDisplayAttribute();
            if (Util.isNotNullOrEmpty(displayAttrName)) {
                result = Util.otos(obj.getAttribute(displayAttrName));
            }
        }
        return result;
    }
}
