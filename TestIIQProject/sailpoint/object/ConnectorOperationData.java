package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
//import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.faces.model.SelectItem;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
//import sailpoint.tools.Util;
//import sailpoint.web.BaseBean;
//import sailpoint.web.ManageRestBean.ConnectorOperationBean;
import sailpoint.web.util.WebUtil;

public class ConnectorOperationData {
    
    public final static String CONNECTOR_OP_TEST_CONNECTION = "Test Connection";
    public final static String CONNECTOR_OP_ACCOUNT_AGGREGATION = "Account Aggregation";
    public final static String CONNECTOR_OP_GROUP_AGGREGATION = "Group Aggregation";
    public final static String CONNECTOR_OP_ENABLE_ACCOUNT = "Enable Account";
    public final static String CONNECTOR_OP_DISABLE_ACCOUNT = "Disable Account";
    public final static String CONNECTOR_OP_CHANGE_PASSWORD = "Change Password";
    public final static String CONNECTOR_OP_ADD_ENTITLEMENT = "Add Entitlement";
    public final static String CONNECTOR_OP_REMOVE_ENTITLEMENT = "Remove Entitlement";
    public final static String CONNECTOR_OP_CREATE_ACCOUT = "Create Account";
    public final static String CONNECTOR_OP_DELETE_ACCOUNT = "Delete Account";
    public final static String CONNECTOR_OP_UPDATE_ACCOUNT = "Update Account";
    public final static String CONNECTOR_OP_GET_OBJECT = "Get Object";
    public final static String CONNECTOR_OP_GET_OBJECT_GROUP = "Get Object-Group";
    public final static String CONNECTOR_OP_PTA_AUTHENTICATION = "Pass-through Authentication";
    public final static String CONNECTOR_OP_UNLOCK_ACCOUNT = "Unlock Account";
    public final static String CONNECTOR_OP_CUSTOM_AUTHENTICATION = "Custom Authentication";
    public final static String CONNECTOR_OP_GET_PARTITIONS = "Get Partitions";
    public final static String CONNECTOR_OP_PARTITIONED_ACCOUNT_AGGREGATION = "Partitioned Account Aggregation";
    String _order;
    String _operation;
    String _name;
    String _contextUrl;
    String _customAuthUrl;
    String _httpMethodType;
    List<MapDTO> _header;
    List<MapDTO> _body;
    List<MapDTO> _resAttMapObj;
    String _responseCode;
    String _status;
    String _bodyFormat;
    String _jsonBody;
    String _rootPath;
    String paginationSteps;
    int pagingInitialOffset = 0;
    int pagingSize = 50;
    List<MapDTO> xpathNamespaces;
    String parentEndpointName;
    
    private ArrayList<String> _options = new ArrayList<String>(
            Arrays.asList(CONNECTOR_OP_CUSTOM_AUTHENTICATION, CONNECTOR_OP_TEST_CONNECTION, CONNECTOR_OP_ACCOUNT_AGGREGATION,
                    CONNECTOR_OP_GROUP_AGGREGATION, CONNECTOR_OP_ENABLE_ACCOUNT, CONNECTOR_OP_DISABLE_ACCOUNT,
                    CONNECTOR_OP_CHANGE_PASSWORD, CONNECTOR_OP_ADD_ENTITLEMENT, CONNECTOR_OP_REMOVE_ENTITLEMENT,
                    CONNECTOR_OP_CREATE_ACCOUT, CONNECTOR_OP_DELETE_ACCOUNT, CONNECTOR_OP_UPDATE_ACCOUNT,
                    CONNECTOR_OP_GET_OBJECT, CONNECTOR_OP_GET_OBJECT_GROUP, CONNECTOR_OP_PTA_AUTHENTICATION,
                    CONNECTOR_OP_UNLOCK_ACCOUNT, CONNECTOR_OP_GET_PARTITIONS, CONNECTOR_OP_PARTITIONED_ACCOUNT_AGGREGATION));
    private static final ArrayList<String> _methodtype = 
        new ArrayList<String>( Arrays.asList
                ("GET", "PUT", "POST", "DELETE", "PATCH"));
    private List<SelectItem> _methodTypeList ;
    private List<SelectItem> _operationList;
    String _beforeRule;
    String _afterRule;


    public static final String ATT_OPERATION_TYPE = "operationType";
    public static final String ATT_SEQUENCE_NUMBER_FOR_ENDPOINT = "sequenceNumberForEndpoint";
    public static final String ATT_UNIQUE_NAME = "uniqueNameForEndPoint";
    public static final String ATT_CONTEXT_URL = "contextUrl";
    public static final String ATT_CUSTOM_AUTH_URL = "customAuthUrl";
    public static final String ATT_HTTP_METHOD_TYPE = "httpMethodType";
    public static final String ATT_HEADER = "header";
    public static final String ATT_BODY = "body";
    public static final String ATT_BODY_FORM_DATA = "bodyFormData";
    public static final String ATT_RES_MAP_OBJ = "resMappingObj";
    public static final String ATT_RES_CODE = "responseCode";
    public static final String ATT_ROOT_PATH = "rootPath";
    public static final String ATT_JSON_BODY = "jsonBody";
    public static final String ATT_BEFORE_RULE = "beforeRule";
    public static final String ATT_AFTER_RULE = "afterRule";
    public static final String ATT_STATUS = "status";
    public static final String ATT_BODY_FORMAT = "bodyFormat";
    public static final String ATT_BODY_FORMAT_RAW = "raw";
    public static final String ATT_PAGINATION_STEPS = "paginationSteps";
    public static final String ATT_PAGINATION_INITIAL_OFFSET = "pagingInitialOffset";
    public static final String ATT_PAGINATION_SIZE = "pagingSize";
    public static final String ATT_XPATH_NAMESPACES = "xpathNamespaces";
    public static final String ATT_PARENT_ENDPOINT_NAME = "parentEndpointName";

    public ConnectorOperationData() {
        this(Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    public ConnectorOperationData(Map<String, Object> data) {
        super();
        _order = (String) data.get(ATT_SEQUENCE_NUMBER_FOR_ENDPOINT);
        _name = (String) data.get(ATT_UNIQUE_NAME);
        _contextUrl = (String) data.get(ATT_CONTEXT_URL);
        _customAuthUrl = (String) data.get(ATT_CUSTOM_AUTH_URL);
        _httpMethodType = (String) data.get(ATT_HTTP_METHOD_TYPE);
        _beforeRule = (String) data.get(ATT_BEFORE_RULE);
        _afterRule = (String) data.get(ATT_AFTER_RULE);
        _operation = (String) data.get(ATT_OPERATION_TYPE);
        _status = (String) data.get(ATT_STATUS);

        _body = new ArrayList<MapDTO>();
        if (data.get(ATT_BODY) != null) {
            HashMap<String, Object> bodyMap = (HashMap<String, Object>) data
                    .get(ATT_BODY);

            if (bodyMap.get(ATT_BODY_FORM_DATA) != null) {
                mapToMapDTO(ATT_BODY_FORM_DATA, (Map<String, Object>) bodyMap);
            }

            _bodyFormat = (bodyMap.get(ATT_BODY_FORMAT) != null)
                    ? (String) bodyMap.get(ATT_BODY_FORMAT)
                    : null;

            _jsonBody = (bodyMap.get(ATT_JSON_BODY) != null)
                    ? (String) bodyMap.get(ATT_JSON_BODY)
                    : null;
        }

        _header = new ArrayList<MapDTO>();
        if (data.get(ATT_HEADER) != null) {
            mapToMapDTO(ATT_HEADER, data);
        }

        _resAttMapObj = new ArrayList<MapDTO>();
        if (data.get(ATT_RES_MAP_OBJ) != null) {
            mapToMapDTO(ATT_RES_MAP_OBJ, data);
        }

        if (data.get(ATT_RES_CODE) != null
                && data.get(ATT_RES_CODE) instanceof ArrayList) {
            ArrayList<String> resList = (ArrayList<String>) data
                    .get(ATT_RES_CODE);
            String csv = Util.listToCsv(resList);
            if (csv != null) {
                _responseCode = csv;
            }
        }
        _rootPath = (String) data.get(ATT_ROOT_PATH);

        paginationSteps = (String) data.get(ATT_PAGINATION_STEPS);

        if (data.get(ATT_PAGINATION_INITIAL_OFFSET) != null) {
            pagingInitialOffset = (int) data.get(ATT_PAGINATION_INITIAL_OFFSET);
        }

        if (data.get(ATT_PAGINATION_SIZE) != null) {
            pagingSize = (int) data.get(ATT_PAGINATION_SIZE);
        }

        xpathNamespaces = new ArrayList<MapDTO>();
        if (data.get(ATT_XPATH_NAMESPACES) != null) {
            mapToMapDTO(ATT_XPATH_NAMESPACES, data);
        }

        parentEndpointName = (String) data.get(ATT_PARENT_ENDPOINT_NAME);
    }

    public String getOrder() {
        return _order;
    }
    public void setOrder(String order) {
        _order = order;
    }
    public String getName() {
        return _name;
    }
    public void setName(String name) {
        _name = name;
    }
    public String getContextUrl() {
        return _contextUrl;
    }
    public void setContextUrl(String endPoint) {
        _contextUrl = endPoint;
    }
    public String getCustomAuthUrl() {
        return _customAuthUrl;
    }
    public void setCustomAuthUrl(String endPoint) {
        _customAuthUrl = endPoint;
    }
    public String getHttpMethodType() {
        return _httpMethodType;
    }
    public void setHttpMethodType(String method) {
        _httpMethodType = method;
    }
    
    public void setStatus(String status) {
        _status = status;
    }
    
    public String getStatus() {
        return _status;
    }

    public void setRootPath(String rPath) {
        _rootPath = rPath;
    }
    
    public String getRootPath() {
        return _rootPath;
    }

    public void setBodyFormat(String formatType) {
        _bodyFormat = formatType;
    }
    
    public String getBodyFormat() {
        return _bodyFormat;
    }
    
    public List<MapDTO> getHeader() {
        return _header;
    }
    public void setHeader(List<MapDTO> header) {
        _header = header;
    }

    public List<MapDTO> getResAttMapObj() {
        return _resAttMapObj;
    }

    public void setResAttMapObj(List<MapDTO> resObj) {
        this._resAttMapObj = resObj;
    }

    public String getResponseCode() {
        return _responseCode;
    }
    public void setResponseCode(String resCode) {
        _responseCode = resCode;
    }
 
    public void setBeforeRule(String brule) {
        this._beforeRule = brule;
    }
    
    public String getBeforeRule() {
        return _beforeRule;
    }
    
    public void setAfterRule(String arule) {
        this._afterRule = arule;
    }
    
    public String getAfterRule() {
        return _afterRule;
    }
    
    public void setJsonBody(String jbody) {
        this._jsonBody = jbody;
    }
    public String getJsonBody() {
        return _jsonBody;
    }
    
    public void setOperation(String op) {
        this._operation = op;
    }
    public String getOperation() {
        return _operation;
    }

    public List<MapDTO> getBody() {
        return _body;
    }
    public void setBody(List<MapDTO> body) {
        _body = body;
    }

    public void addResAttributeMapObj(){
        MapDTO a = new MapDTO();
        _resAttMapObj.add(a);
    }
    
    public List<String> getOptions() {
        return _options;
    }

    public List<String> getMethodTypes() {
        return _methodtype;
    }

    public void deleteResAttributeMapObj(String id) {
        if (_resAttMapObj != null){
            for (int i=0; i < _resAttMapObj.size(); i++){
                MapDTO element  = _resAttMapObj.get(i);
                if (element.getId()!= null && 
                        element.getId().equals(id)){
                    _resAttMapObj.remove(i);
                    break;
                }
            }
        }
    }
    
    public void deleteBody(String id) {
        if (_body != null){
            for (int i=0; i < _body.size(); i++){
                MapDTO element  = _body.get(i);
                if (element.getId()!= null && 
                        element.getId().equals(id)){
                    _body.remove(i);
                    break;
                }
            }
        }
    }
    
    public void deleteHeader(String id) {
        if (_header != null){
            for (int i=0; i < _header.size(); i++){
                MapDTO element  = _header.get(i);
                if (element.getId()!= null && 
                        element.getId().equals(id)){
                    _header.remove(i);
                    break;
                }
            }
        }
    }

    
    public void addBody() {
        MapDTO a = new MapDTO();
        _body.add(a);
    }
    
    public void addHeader() {
        MapDTO a = new MapDTO();
        _header.add(a);
   }
    
    public Map<String, Object> getConfigObject(String configStr) {
        List<MapDTO> beans = new ArrayList<MapDTO>();
        if (configStr.equalsIgnoreCase(ATT_HEADER))
            beans = getHeader();
        else if (configStr.equalsIgnoreCase(ATT_BODY_FORM_DATA))
            beans = getBody();
        else if (configStr.equalsIgnoreCase(ATT_RES_MAP_OBJ))
            beans = getResAttMapObj();
        else if (configStr.equalsIgnoreCase(ATT_XPATH_NAMESPACES))
            beans = getXpathNamespaces();
        if ((beans != null) && !(beans.isEmpty())) {
            Map<String, Object> data = new HashMap<String, Object>();
            for (int i = 0; i < beans.size(); i++) {
                MapDTO bb = (MapDTO) beans.get(i);
                data.put(bb.getKey(), bb.getValue());
            }
            return data;
        }

        return null;
    }
    
    private void mapToMapDTO(String attributeName, Map<String, Object> data){
        @SuppressWarnings("unchecked")
        Map<String, Object> beans = (Map<String, Object>) data.get(attributeName);
        if ((beans != null) && !(beans.isEmpty())) {
            // the objects should be saved as maps
            // convert each object to map first before saving
            Set<String> keys = beans.keySet();
            for (String entry : keys) {
                String value = (String) beans.get(entry);
                Map<String, String>  mapData = new HashMap<String, String>();
                mapData.put(entry, value);
                MapDTO m = new MapDTO(mapData);
                if (attributeName.equals(ATT_BODY_FORM_DATA))
                    _body.add(m);
                else if (attributeName.equals(ATT_HEADER))
                    _header.add(m);
                else if (attributeName.equals(ATT_RES_MAP_OBJ))
                    _resAttMapObj.add(m);
                else if (attributeName.equals(ATT_XPATH_NAMESPACES))
                    xpathNamespaces.add(m);
            }
        }
    }

    public List<SelectItem> getOperationList(boolean filterCustomAuth) {
        if (_operationList == null
                || _operationList.size() < getOptions().size())
            _operationList = WebUtil.getSelectItems(getOptions(), false);
        if (filterCustomAuth) {
            _operationList.removeIf(e -> e.getValue()
                    .equals(CONNECTOR_OP_CUSTOM_AUTHENTICATION));
        }
        return _operationList;
    }
    
    /**
     * This method will return all the supported operations list for v1 version
     * of the web services connector. For v1 version it removes the unsupported
     * items from the list.
     */
    public List<SelectItem> getOperationListV1() throws GeneralException {
        _operationList = getOperationList(true);
        _operationList.removeIf(
                e -> (e.getValue().equals(CONNECTOR_OP_GET_PARTITIONS)
                        || e.getValue().equals(
                                CONNECTOR_OP_PARTITIONED_ACCOUNT_AGGREGATION)));
        return _operationList;
    }
    
     public List<SelectItem> getMethodTypeList() throws GeneralException {
            if (_methodTypeList == null)
                _methodTypeList = WebUtil.getSelectItems(getMethodTypes(), false);

            return _methodTypeList;
        }

    public String getPaginationSteps() {
        return paginationSteps;
    }

    public void setPaginationSteps(String paginationSteps) {
        this.paginationSteps = paginationSteps;
    }
    
    public int getPagingInitialOffset() {
        return pagingInitialOffset;
    }

    public void setPagingInitialOffset(int pagingInitialOffset) {
        this.pagingInitialOffset = pagingInitialOffset;
    }

    public int getPagingSize() {
        return pagingSize;
    }

    public void setPagingSize(int pagingLimit) {
        this.pagingSize = pagingLimit;
    }

    public List<MapDTO> getXpathNamespaces() {
        return xpathNamespaces;
    }

    public void setXpathNamespaces(List<MapDTO> xpathNamespaces) {
        this.xpathNamespaces = xpathNamespaces;
    }
    
    public void addXpathNamespace() {
        MapDTO nspc = new MapDTO();
        xpathNamespaces.add(nspc);
    }

    public void deleteXpathNamespace(String id) {
        if (xpathNamespaces != null) {
            for (int i = 0; i < xpathNamespaces.size(); i++) {
                MapDTO element = xpathNamespaces.get(i);
                if (element.getId() != null && element.getId().equals(id)) {
                    xpathNamespaces.remove(i);
                    break;
                }
            }
        }
    }

    public String getParentEndpointName() {
        return parentEndpointName;
    }

    public void setParentEndpointName(String parentEndpointName) {
        this.parentEndpointName = parentEndpointName;
    }
}

