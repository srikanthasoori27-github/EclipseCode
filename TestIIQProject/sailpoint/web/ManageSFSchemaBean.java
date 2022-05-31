/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This class is used to manage the Additional schema attribute of SuccessFactors Connector.  
 */
package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import javax.faces.context.FacesContext;
import sailpoint.object.Application;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import sailpoint.web.messages.MessageKeys;

public class ManageSFSchemaBean extends BaseEditBean<Application> {

    static final String ATT_SF_SCHEMA_SETTINGS = "AttributeMapping";
    // The schemaExtMap which is used to render additional schema table on
    // SuccessFactors UI
    LinkedHashMap<String, String> schemaExtMap = new LinkedHashMap<String, String>();

    // schema name is additional schema name whose value is render from SuccessFactors UI
    String schemaName;
    // schema path is SFAPI XPATH whose value is render from SuccessFactors UI
    String schemaPath;

    // The schemaSuccess is to check if the addition or removal of schema attribute is done
    private boolean schemaSuccess;

    // result attribute of SuccessFactors UI which is used to display exception messages
    private String result;

    public String getresult() {
        return result;
    }

    private static Application app = null;

    /**
     * Clears the application object of the class
     */
    public static void clearApplicationObject() {
        app = null;
    }
    public static void setApplicationObject(Application obj) {
        app = obj;
    }
    
    // The selectedSchema value of checkbox of additional schema table row which is render from UI
    private Map<String, Boolean> selectedSchema = new HashMap<String, Boolean>();

    public Map<String, Boolean> getselectedSchema() {
        return selectedSchema;
    }

    public void setselectedSchema(Map<String, Boolean> selectedSchema) {
        this.selectedSchema = selectedSchema;
    }
    

    public String getschemaName() {
        return schemaName;
    }

    public void setschemaName(String schemaName) {
        this.schemaName = schemaName;
    }
  

    public String getschemaPath() {
        return schemaPath;
    }

    public void setschemaPath(String schemaPath) {
        this.schemaPath = schemaPath;
    }

    public Map<String, String> getschemaExtMap() {
        if (schemaExtMap == null) {
            if (app != null) {
                // check the AttributeMapping entry exist in the database (app) if it is then
                // add in schemaExtMap
                Map attrMap = new HashMap();
                attrMap = (Map) app.getAttributeValue(ATT_SF_SCHEMA_SETTINGS);
                if (!Util.isEmpty(attrMap)) {
                    schemaExtMap = new LinkedHashMap<String, String>(attrMap);
                }
            }
        }
        return schemaExtMap;
    }

    public boolean isSchemaSuccess() {
        return schemaSuccess;
    }

    public void setschemaExtMap(LinkedHashMap<String, String> schemaExtMap) {
        this.schemaExtMap = schemaExtMap;
    }
    
    @Override
    public Map getSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();      
    }
    @Override
    public boolean isStoredOnSession() {
        return true;
    }

    @Override
    protected Class getScope() {
        return Application.class;
    }
    
    @SuppressWarnings("unchecked")
    public ManageSFSchemaBean() {
        schemaSuccess = false;
        result = "";
        schemaExtMap =  (LinkedHashMap<String, String>) getSessionScope().get(
                ATT_SF_SCHEMA_SETTINGS);
    }

    /**
     * This function add the additional schema attributes entry in schemaExtMap map.
     * @return
     */
    @SuppressWarnings("unchecked")
    public String addXPathData() {
        if (schemaExtMap == null)
            schemaExtMap = new LinkedHashMap<String, String>();
        if (Util.isNullOrEmpty(schemaName) || Util.isNullOrEmpty(schemaPath)) {
            // checking schemaName and schemaPath should not be blank at the time of adding
            // data in additional schema table
            // if it is blank throw the error on UI.
            schemaSuccess = false;
            this.result = new Message(Message.Type.Error, MessageKeys.ERR_SF_SCHEMANAME_REQUIRED).toString();
        } else {
            boolean existsAlready = false;
            String tempSchemaAttrName = "";
            String tempSchemaXpath = "";
            
            for (Map.Entry<String, String> entry : schemaExtMap.entrySet()) {
                // iterate schemaExtMap map which is having value from database and current
                // sessionand add the key value in tempSchemaAttrName and value of map in tempSchemaXpath.
                tempSchemaAttrName = entry.getKey();
                tempSchemaXpath = entry.getValue();

                if (tempSchemaAttrName.equalsIgnoreCase(schemaName)
                        || (Util.isNotNullOrEmpty(tempSchemaXpath) && tempSchemaXpath.equalsIgnoreCase(schemaPath))) {
                    existsAlready = true;
                }
            }
            if (!existsAlready) {
                // if schemaName and schemaPath value is not already present in additional
                // schema attribute then add that value in schemaExtMap map.
                schemaExtMap.put(schemaName, schemaPath);
                getSessionScope().put(ATT_SF_SCHEMA_SETTINGS, schemaExtMap);
                schemaSuccess =  true;
                schemaName = "";
                schemaPath = "";
            } else {
                schemaSuccess = false;
                this.result = new Message(Message.Type.Error,
                        MessageKeys.ERR_SF_SCHEMANAME_PATH_EXISTS, schemaName, schemaPath).toString();
            }
            
        }
        return "";
    }
    
    /**
     * This function remove the additional schema attributes entry which is selected 
     * @return 
     */
    @SuppressWarnings("unchecked")
    public String removeXPathData() {
        List<String> removeSchemas = new ArrayList<String>();
        if (schemaExtMap == null) {
            // To delete entry first check the value is present in the schemaExtMap if value
            // is not there then throw the exception
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SF_NO_SCHEMA_DEFINED),
                    null);
            return "";
        }

        if (selectedSchema != null) {
            //selectedSchema contains the value of row for check box which is selected to delete 
            selectedSchema.keySet();
            for (Map.Entry<String,Boolean> entry : selectedSchema.entrySet())  {
                // Here iterate the selectedSchema and if value is available in schemaExtMap map
                // then add the key value in removeSchemas list.
                if (entry.getValue()) {
                    if(schemaExtMap.get(entry.getKey()) != null)
                    removeSchemas.add(entry.getKey());
                }
            }
        }
        
        if ((removeSchemas == null) || (removeSchemas.size() == 0)) {
            schemaSuccess = false;
            this.result = new Message(Message.Type.Error, MessageKeys.ERR_SF_NO_SCHEMA_SELECTED).toString();
            return "";
        } else {
            for (String removeSchema : removeSchemas)  {
                // If value is there in removeSchemas list then iterate it and remove the entry 
                // from schemaExtMap.
                schemaExtMap.remove(removeSchema);
                schemaSuccess =  true;
            }
        }
        getSessionScope().put(ATT_SF_SCHEMA_SETTINGS, schemaExtMap);
        return "";
    }
}
