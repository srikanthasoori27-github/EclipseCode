/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Schema;
import sailpoint.tools.JsonHelper;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.extjs.ErrorResponse;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponse;
import sailpoint.web.extjs.GridResponseMetaData;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class ApplicationDetailsDataSourceBean extends BaseObjectBean<Application> {

    private static final Log log = LogFactory.getLog(ApplicationDetailsDataSourceBean.class);

    public ApplicationDetailsDataSourceBean() {
        super();
        setStoredOnSession(false);
        this.setScope(Application.class);
        setObjectName(getRequestParameter("name"));
    }

    protected Class<Application> getScope() {
        return Application.class;
    }


    /*

    Left this in in case we need it someday

    public String getSchemaJson(){
        String objType = getRequestParameter("objType");

        try {
        Application app = this.getObject();
        List<SchemaDTO> dtos = new ArrayList<SchemaDTO>();
        if (app != null && app.getSchemas() != null){
          for(Schema schema : app.getSchemas()){
              if (objType == null || objType.equals(schema.getObjectType())){
                  dtos.add(new SchemaDTO(schema));
              }
          }
        }
        return new JSONSerializer().exclude("*.class").serialize(new GridResponse(dtos));
        } catch (Throwable e) {
        log.error(e);
        ErrorResponse response = new ErrorResponse(ErrorResponse.SYS_ERROR, e.getMessage());
        return new JSONSerializer().exclude("*.class").serialize(response);
        }
    }
    */


    public String getSchemaAttributeDefinitionJson(){

        String objType = getRequestParameter("objType");

        try {
            Application app = this.getObject();
            List<Map> dtos = new ArrayList<Map>();
            if (app != null){                
                if (app != null && app.getSchemas() != null){
                    for(Schema schema : app.getSchemas()){
                        if (objType == null || objType.equals(schema.getObjectType())){
                            if (schema.getAttributes() != null){
                                for(AttributeDefinition attr : schema.getAttributes()){
                                    Map dto = new HashMap();
                                    dto.put("name", attr.getName());
                                    dto.put("displayName", attr.getDisplayableName());
                                    dtos.add(dto);
                                }
                            }
                        }
                    }
                }
            }

            GridResponseMetaData meta = new GridResponseMetaData(null, Arrays.asList(new GridColumn("name"),
                    new GridColumn("displayName")));
            GridResponse response = new GridResponse(meta, dtos, dtos.size());           
            return JsonHelper.toJson(response);
        } catch (Throwable e) {
            log.error(e);
            ErrorResponse response = new ErrorResponse(ErrorResponse.SYS_ERROR, e.getMessage());
            return JsonHelper.toJson(response);
        }
    }
}
