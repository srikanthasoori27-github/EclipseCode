package sailpoint.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.ConnectorOperationData;
import sailpoint.object.MapDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


public class ManageRestBean extends BaseEditBean<Application> {

    private static final String HYPHEN = "-";
    private static Application _app = null;
    private static Log log = LogFactory.getLog(ManageRestBean.class);
    public String _orderNo;
    
    ConnectorOperationBean _innerObj;
    private Map<String, Boolean> _selectedParams = new HashMap<String, Boolean>();
    private  List<ConnectorOperationBean> _connectionParametersList;
    private List<SelectItem> _authMethodTypeList;
    private List<SelectItem> oauth2GrantTypeList;

    public static final String ATT_CONNECTION_PARAMETERS = "connectionParameters";
    public static final String ATT_INNER_BEAN_PARAMETERS = "innerBeanParameters"; 
    public static final String ATT_CUSTOM_AUTH_KEY = "Custom Authentication"; 
    public final static String ATT_GET_PARTITIONS = "Get Partitions";
    public final static String ATT_PARTITIONED_ACCOUNT_AGGREGATION = "Partitioned Account Aggregation";
    
    // BELOW OAUTH CONSTANTS MUST BE SAME AS THAT OF GrantType.java IN CONNECTOR
    // COMMON BUNDLE.
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String AUTHORIZATION_CODE = "authorization_code";
    private static final String IMPLICIT = "implicit";
    private static final String PASSWORD = "password";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final String SAML_BEARER_ASSERTION = "SamlBearerAssertion";
    private static final String SAML2_BEARER = "urn:ietf:params:oauth:grant-type:saml2-bearer";
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Group Object added from UI
     */
    public String groupObjectType;

    public String getGroupObjectType() {
        return groupObjectType;
    }

    /**
     * @param groupObjectType
     *            the groupObjectType to set
     * This method will create options for newly added group and add these options
     * in connector operation list. 
     */
    public void setGroupObjectType(String groupObjectType) {
        initializeConnectionParameterList();
        if (!Util.isEmpty(_connectionParametersList)) {
            _connectionParametersList.forEach(
                    connectorOperationBean -> checkAndCreateGroupOperations(connectorOperationBean, groupObjectType));
            addEditState(ATT_CONNECTION_PARAMETERS, _connectionParametersList);
        }
    }
    public ConnectorOperationBean getInnerobj() {
        return _innerObj;
    }
    
    /**
     * This method will check all group schema Object except for Group and create
     * necessary options for each group object.
     */
    public void createOperationsForMultiGroups(ConnectorOperationBean connectorOperationBean) {
        _app.getGroupSchemaObjectTypes().stream()
                .filter(groupSchemaObjectType -> !(Application.SCHEMA_GROUP.equalsIgnoreCase(groupSchemaObjectType)))
                .forEach(groupSchemaObjectType -> checkAndCreateGroupOperations(connectorOperationBean,
                        groupSchemaObjectType));
    }

    /**
     * This method will check if operation related to multiGroup not present
     * then create the operations for the same.
     * @param connectorOperationBean - innerBean Contains Operations related object
     * @param groupSchemaObjectType - Group Object type added from UI.
     */
    public void checkAndCreateGroupOperations(ConnectorOperationBean connectorOperationBean,
            String groupSchemaObjectType) {
        if (!(connectorOperationBean.object.getOptions()
                .contains(ConnectorOperationData.CONNECTOR_OP_GET_OBJECT + HYPHEN + groupSchemaObjectType))) {
            connectorOperationBean.object.getOptions()
                    .add(ConnectorOperationData.CONNECTOR_OP_GET_OBJECT + HYPHEN + groupSchemaObjectType);
            connectorOperationBean.object.getOptions()
                    .add(ConnectorOperationData.CONNECTOR_OP_GROUP_AGGREGATION + HYPHEN + groupSchemaObjectType);
            connectorOperationBean.object.getOptions()
                    .add(ConnectorOperationData.CONNECTOR_OP_ADD_ENTITLEMENT + HYPHEN + groupSchemaObjectType);
            connectorOperationBean.object.getOptions()
                    .add(ConnectorOperationData.CONNECTOR_OP_REMOVE_ENTITLEMENT + HYPHEN + groupSchemaObjectType);
        }
    }

    public void setInnerobj(ConnectorOperationBean _innerobj) {
        this._innerObj = _innerobj;
    }

    public ManageRestBean() {
        super();
        if(_app != null && getEditState(ATT_INNER_BEAN_PARAMETERS) != null)
            _innerObj = (ConnectorOperationBean) getEditState(ATT_INNER_BEAN_PARAMETERS);
        else {
            if (_innerObj == null)
                _innerObj = new ConnectorOperationBean();
        }
    }
    
    public boolean isStoredOnSession() {
        return true;
    }
    
    protected Class<Application> getScope() {
        return Application.class;
    }
    
    public Map<String, Boolean> getSelectedParams() {
        return _selectedParams;
    }
    public void setSelectedParams(Map<String, Boolean> selectedParams) {
        this._selectedParams = selectedParams;
    }
    
    @Override
    public Map getSessionScope() {
        return FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
    }
    
    public static void setApplicationObject(Application obj) {
        _app = obj;
    }

    public static Application getApplicationObject() {
        return _app;
    }

    public static void clearApplicationObject() {
        _app = null;
    }
    
    public void setOrderNo(String ordNo) {
        _orderNo = ordNo;
    }
    
    public String getOrderNo() {
        return _orderNo;
    }

    public List<SelectItem> getAuthMethodTypeList() throws GeneralException {
        if (_authMethodTypeList == null) {
            _authMethodTypeList = new ArrayList<>();
            _authMethodTypeList.add(new SelectItem("OAuth2Login", "OAuth2"));
            _authMethodTypeList.add(new SelectItem("OAuthLogin", "API Token"));
            _authMethodTypeList.add(new SelectItem("BasicLogin", "Basic Authentication"));
            if (_app.getAttributeValue("version") != null && _app.getAttributeValue("version").equals("v2")) {
                _authMethodTypeList.add(new SelectItem("No Auth", "No / Custom Authentication"));
            } else {
                _authMethodTypeList.add(new SelectItem("No Auth", "No Authentication"));
            }
        }
        return _authMethodTypeList;
    }
    
    public List<SelectItem> getOauth2GrantTypeList() throws GeneralException {
        if (oauth2GrantTypeList == null) {
            oauth2GrantTypeList = new ArrayList<>();
            oauth2GrantTypeList.add(
                    new SelectItem(CLIENT_CREDENTIALS, "Client Credentials"));
            oauth2GrantTypeList.add(
                    new SelectItem(REFRESH_TOKEN, "Refresh Token"));
            oauth2GrantTypeList
                    .add(new SelectItem(JWT_BEARER, "JWT"));
            oauth2GrantTypeList
                    .add(new SelectItem(PASSWORD, "Password"));
            oauth2GrantTypeList
            .add(new SelectItem(SAML_BEARER_ASSERTION, "SAML Bearer Assertion"));
        }
        return oauth2GrantTypeList;
    }
    
    @SuppressWarnings("unchecked")
    public void orderChange(boolean isOrderUp) {
        String ordNo = getOrderNo();
        boolean orderChanged = false;
        
        _connectionParametersList = (List<ConnectorOperationBean>) getEditState(ATT_CONNECTION_PARAMETERS);
        List<ConnectorOperationBean> tempOrderList = _connectionParametersList;
        for (int i = 0; i < tempOrderList.size(); i++) {
            ConnectorOperationBean opObj = tempOrderList.get(i);
            if (opObj.getObject().getOrder() != null) {
                if (isOrderUp) {
                    if (opObj.getObject().getOrder()
                            .equalsIgnoreCase(ordNo) && i > 0) {
                        ConnectorOperationBean tempOpObj = tempOrderList.get(i-1);
                        if (opObj.getObject().getOrder().isEmpty() || 
                                tempOpObj.getObject().getOrder().isEmpty()){
                            tempOrderList.set(i-1, opObj);
                            tempOrderList.set(i, tempOpObj);
                            orderChanged = true;
                            break;
                        } 
                        else if (Util.isNotNullOrEmpty(tempOpObj.getObject().getOrder())) {
                            String tempOrdNum = tempOpObj.getObject().getOrder();
                            opObj.getObject().setOrder(tempOrdNum);
                            tempOpObj.getObject().setOrder(ordNo);
                            tempOrderList.set(i-1, opObj);
                            tempOrderList.set(i, tempOpObj);
                            orderChanged = true;
                            break;
                        }
                    }
                } else {
                    if (opObj.getObject().getOrder()
                            .equalsIgnoreCase(ordNo) && i < tempOrderList.size()-1) {
                        ConnectorOperationBean tempOpObj = tempOrderList.get(i+1);
                        if (opObj.getObject().getOrder().isEmpty() || 
                                tempOpObj.getObject().getOrder().isEmpty()){
                            tempOrderList.set(i+1, opObj);
                            tempOrderList.set(i, tempOpObj);
                            orderChanged = true;
                            break;
                        }
                        else if (Util.isNotNullOrEmpty(tempOpObj.getObject().getOrder())) {
                            String tempOrdNum = tempOpObj.getObject().getOrder();
                            opObj.getObject().setOrder(tempOrdNum);
                            tempOpObj.getObject().setOrder(ordNo);
                            tempOrderList.set(i+1, opObj);
                            tempOrderList.set(i, tempOpObj);
                            orderChanged = true;
                            break;
                        }
                    }
                }
            }
        }
        if (orderChanged){
            _connectionParametersList = tempOrderList;
            addEditState(ATT_CONNECTION_PARAMETERS,
                    _connectionParametersList);
        }
    }

    /**
     * Read list of testConnectionparam
     * 
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public List<ConnectorOperationBean> getConnectionParametersList() throws GeneralException {

        initializeConnectionParameterList();

        if (!Util.isEmpty(_connectionParametersList)) {
            Map<String, Object> data = new HashMap<String, Object>();
            List<ConnectorOperationBean> beans = new ArrayList<ConnectorOperationBean>();
            for (int i = 0; i < _connectionParametersList.size(); i++) {
                if (_connectionParametersList
                        .get(i) instanceof ConnectorOperationBean) {
                    beans.add(_connectionParametersList.get(i));
                } else {
                    data = (Map<String, Object>) _connectionParametersList
                            .get(i);
                    if ((Util
                            .isNullOrEmpty((String) data.get(
                                    ConnectorOperationData.ATT_CONTEXT_URL))
                            && Util.isNullOrEmpty((String) data.get(
                                    ConnectorOperationData.ATT_CUSTOM_AUTH_URL)))
                            || Util.isNullOrEmpty((String) data.get(
                                    ConnectorOperationData.ATT_SEQUENCE_NUMBER_FOR_ENDPOINT))
                            || Util.isNullOrEmpty((String) data.get(
                                    ConnectorOperationData.ATT_OPERATION_TYPE))) {
                        data.put(ConnectorOperationData.ATT_STATUS, new Message(
                                MessageKeys.LABEL_CONNECTOR_OPERATION_NOTCONFIGURED_STATUS)
                                        .getLocalizedMessage());
                    } else {
                        data.put(ConnectorOperationData.ATT_STATUS, new Message(
                                MessageKeys.LABEL_CONNECTOR_OPERATION_CONFIGURED_STATUS)
                                        .getLocalizedMessage());
                    }
                    beans.add(new ConnectorOperationBean(data));
                }
            }
            _connectionParametersList = beans;
            _connectionParametersList
                    .forEach(this::createOperationsForMultiGroups);
            addEditState(ATT_CONNECTION_PARAMETERS, _connectionParametersList);

        }
        return _connectionParametersList;
    }

    /**
     * This method will initialize connectionParameters from edited state or
     * application object.
     */
    @SuppressWarnings("unchecked")
    public void initializeConnectionParameterList() {
        _connectionParametersList = (List<ConnectorOperationBean>) getEditState(
                ATT_CONNECTION_PARAMETERS);
        if (_connectionParametersList == null) {
            if (_app != null) {
                _connectionParametersList = (List<ConnectorOperationBean>) _app
                        .getListAttributeValue(ATT_CONNECTION_PARAMETERS);
            }
        }
        if (_app != null && _app.getAttributeValue("version") != null
                && _app.getAttributeValue("version").equals("v1")) {
            removeV2EndpointDetails(_connectionParametersList);
        }
    }

    /**
     * This method will remove the v2 endpoint details from
     * the existing configured endpoint list if the connector version is
     * switched to v1
     * 
     * @param _connectionParametersList List of configured endpoint beans
     */
    @SuppressWarnings("unchecked")
    private void removeV2EndpointDetails(
            List<ConnectorOperationBean> _connectionParametersList) {
        List<ConnectorOperationBean> tempList = new ArrayList<>();
        if (!Util.isEmpty(_connectionParametersList)) {
            for (int i = 0; i < _connectionParametersList.size(); i++) {
                String operationType;
                if (_connectionParametersList
                        .get(i) instanceof ConnectorOperationBean) {
                    operationType = _connectionParametersList.get(i).getObject()
                            .getOperation();
                } else {
                    Map<String, Object> data = (Map<String, Object>) _connectionParametersList
                            .get(i);
                    operationType = (String) data.get("operationType");
                }
                if (ATT_CUSTOM_AUTH_KEY.equals(operationType)
                        || ATT_GET_PARTITIONS.equals(operationType)
                        || ATT_PARTITIONED_ACCOUNT_AGGREGATION
                                .equals(operationType)) {
                    tempList.add(_connectionParametersList.get(i));
                }
            }
            _connectionParametersList.removeAll(tempList);
        }
    }
    
    public void resetConnectionParameters() throws GeneralException {
        if (_innerObj != null) {
            _innerObj.getObject().setContextUrl(null);
            _innerObj.getObject().setCustomAuthUrl(null);
            _innerObj.getObject().setHttpMethodType("GET");
            
            List<MapDTO> header = new ArrayList<MapDTO>();
            List<MapDTO> body = new ArrayList<MapDTO>();
            List<MapDTO> resAttribute = new ArrayList<MapDTO>();
            _innerObj.getObject().setHeader(header);
            _innerObj.getObject().setBody(body);
            _innerObj.getObject().setResAttMapObj(resAttribute);
            _innerObj.getObject().setBodyFormat(ConnectorOperationData.ATT_JSON_BODY);
            _innerObj.getObject().setJsonBody(null);
            _innerObj.getObject().setResponseCode(null);
            _innerObj.getObject().setRootPath(null);
            _innerObj.getObject().setAfterRule(null);
            _innerObj.getObject().setBeforeRule(null);
            addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
        }
        
    }
    
    @SuppressWarnings("unchecked")
    public void addNewConnectionParameters() throws GeneralException {
        _connectionParametersList = (List<ConnectorOperationBean>) getEditState(ATT_CONNECTION_PARAMETERS);
        if (_connectionParametersList == null){
            _connectionParametersList = new ArrayList<ManageRestBean.ConnectorOperationBean>();
        }
        ConnectorOperationBean connectionParaObject =  new ConnectorOperationBean();
        if (Util.isNullOrEmpty(connectionParaObject.getObject().getContextUrl())
                && Util.isNullOrEmpty(
                        connectionParaObject.getObject().getCustomAuthUrl())) {
            connectionParaObject.getObject().setStatus(new Message(
                    MessageKeys.LABEL_CONNECTOR_OPERATION_NOTCONFIGURED_STATUS)
                            .getLocalizedMessage());
        } else {
            connectionParaObject.getObject().setStatus(new Message(
                    MessageKeys.LABEL_CONNECTOR_OPERATION_CONFIGURED_STATUS)
                            .getLocalizedMessage());
        }
        if (_connectionParametersList.size() > 0) {
            connectionParaObject.object.getOptions().addAll(CollectionUtils.subtract(
                    _connectionParametersList.get(0).object.getOptions(), connectionParaObject.object.getOptions()));
        }
        _connectionParametersList.add(connectionParaObject);
        addEditState(ATT_CONNECTION_PARAMETERS,_connectionParametersList);
    }
    
    @SuppressWarnings("unchecked")
    public void updateConnectorList() {
        int index = 0;
        String id = null;
        if (_innerObj != null) {
            id = _innerObj.getId();
        }
        _connectionParametersList = (List<ConnectorOperationBean>) getEditState(ATT_CONNECTION_PARAMETERS);
        int conSize = _connectionParametersList.size();
        while (conSize > index) {
            ConnectorOperationBean currentBean = _connectionParametersList
                    .get(index);
            if (id !=null && id.contains(currentBean.getId())) {
               copyObject(_innerObj, currentBean);
               addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
               break;
            }
            index ++;
        }
    }
    
    private void copyObject(ConnectorOperationBean objtoUpdate, ConnectorOperationBean tempConOpeObj) {
        objtoUpdate.setId(tempConOpeObj.getId());
        ConnectorOperationData conObj = tempConOpeObj.getObject();
        ConnectorOperationData dataConOpObj = objtoUpdate.getObject();
        if (conObj != null){
            dataConOpObj.setAfterRule(conObj.getAfterRule());
            dataConOpObj.setBeforeRule(conObj.getBeforeRule());
            dataConOpObj.setBodyFormat(conObj.getBodyFormat());
            dataConOpObj.setContextUrl(conObj.getContextUrl());
            dataConOpObj.setCustomAuthUrl(conObj.getCustomAuthUrl());
            dataConOpObj.setHttpMethodType(conObj.getHttpMethodType());
            dataConOpObj.setJsonBody(conObj.getJsonBody());
            dataConOpObj.setName(conObj.getName());
            dataConOpObj.setOperation(conObj.getOperation());
            dataConOpObj.setOrder(conObj.getOrder());
            dataConOpObj.setResponseCode(conObj.getResponseCode());
            dataConOpObj.setRootPath(conObj.getRootPath());
            dataConOpObj.setStatus(conObj.getStatus());
            List<MapDTO> header = new ArrayList<MapDTO>();
            List<MapDTO> body = new ArrayList<MapDTO>();
            List<MapDTO> resAttMapObj = new ArrayList<MapDTO>();
            
            getMapDtoFromList(ConnectorOperationData.ATT_HEADER, conObj, header);
            dataConOpObj.setHeader((List<MapDTO>)header);
            getMapDtoFromList(ConnectorOperationData.ATT_BODY_FORM_DATA, conObj, body);
            dataConOpObj.setBody((List<MapDTO>)body);
            getMapDtoFromList(ConnectorOperationData.ATT_RES_MAP_OBJ, conObj, resAttMapObj);
            dataConOpObj.setResAttMapObj((List<MapDTO>)resAttMapObj);
            
            dataConOpObj.setPaginationSteps(conObj.getPaginationSteps());
            dataConOpObj.setPagingInitialOffset(conObj.getPagingInitialOffset());
            dataConOpObj.setPagingSize(conObj.getPagingSize());
            dataConOpObj.setXpathNamespaces(conObj.getXpathNamespaces());
            dataConOpObj.setParentEndpointName(conObj.getParentEndpointName());
        }
    }
    
    private void getMapDtoFromList(String configArg, ConnectorOperationData conObj, List<MapDTO>mapDtoList) {
        List<MapDTO> tempMapDtoList = null;

        if (configArg.equalsIgnoreCase(ConnectorOperationData.ATT_HEADER))
            tempMapDtoList = conObj.getHeader();
        else if (configArg.equalsIgnoreCase(ConnectorOperationData.ATT_BODY_FORM_DATA))
            tempMapDtoList = conObj.getBody();
        else 
            tempMapDtoList = conObj.getResAttMapObj();

        if (tempMapDtoList != null){
            Iterator<MapDTO> it = tempMapDtoList.iterator();
            while (it != null && it.hasNext()){
                MapDTO tempDtoObj=(MapDTO) it.next();
                if (tempDtoObj != null){
                    MapDTO mapDtoObj = new MapDTO(tempDtoObj);
                    if (mapDtoObj != null){
                        mapDtoList.add(mapDtoObj);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void updateRestInnerObj() {
        String id = null;
        int index = 0;

        if (_innerObj != null) {
            id = _innerObj.getId();
        }
        _connectionParametersList = (List<ConnectorOperationBean>) getEditState(ATT_CONNECTION_PARAMETERS);
        if (_connectionParametersList != null) {
            int orderNo = totalOrderNo(); 
            while (_connectionParametersList.size() > index) {
                ConnectorOperationBean currentBean = _connectionParametersList.get(index);
                if (id.contains(currentBean.getId())) {
                    copyObject(currentBean, _innerObj );
                    if (Util.isNullOrEmpty(currentBean.getObject().getOrder())) {
                        currentBean.getObject().setOrder(Integer.toString(orderNo));
                        _innerObj.getObject().setOrder(Integer.toString(orderNo));
                    }
                    if ((Util.isNullOrEmpty(
                            currentBean.getObject().getContextUrl())
                            && Util.isNullOrEmpty(
                                    currentBean.getObject().getCustomAuthUrl()))
                            || Util.isNullOrEmpty(
                                    currentBean.getObject().getOrder())
                            || Util.isNullOrEmpty(
                                    currentBean.getObject().getOperation())) {
                        currentBean.getObject().setStatus(new Message(
                                MessageKeys.LABEL_CONNECTOR_OPERATION_NOTCONFIGURED_STATUS)
                                        .getLocalizedMessage());
                        _innerObj.getObject().setStatus(new Message(
                                MessageKeys.LABEL_CONNECTOR_OPERATION_NOTCONFIGURED_STATUS)
                                        .getLocalizedMessage());
                    } else {
                        currentBean.getObject().setStatus(new Message(MessageKeys.LABEL_CONNECTOR_OPERATION_CONFIGURED_STATUS)
                        .getLocalizedMessage());
                        _innerObj.getObject().setStatus(new Message(MessageKeys.LABEL_CONNECTOR_OPERATION_CONFIGURED_STATUS)
                        .getLocalizedMessage());
                    }
                    _connectionParametersList.set(index, currentBean);
                    addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
                    break;
                }
                index++;
            }
            addEditState(ATT_CONNECTION_PARAMETERS, _connectionParametersList);
        }
    }

    @SuppressWarnings("unchecked")
    public void configConnectionParameters(String id) {
        initializeConnectionParameterList();
        if (_connectionParametersList != null){
            Iterator<ConnectorOperationBean> iter = _connectionParametersList.iterator();
            while (iter.hasNext()) {
                ConnectorOperationBean currentBean = iter.next();
                if (id.contains(currentBean.getId())) {
                    copyObject(_innerObj, currentBean);
                    addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
                    break;
                }
            } 
        }
        return ;
    }

    @SuppressWarnings("unchecked")
    public void deleteConnectionParameters(String id, int order) {
        int size, idx = 0;
        boolean isDelete = false;

        _connectionParametersList = (List<ConnectorOperationBean>) getEditState(ATT_CONNECTION_PARAMETERS);
        if (id != null && _connectionParametersList != null) {
            size = _connectionParametersList.size();
            for (int i = 0; i < size; i++) {
                ConnectorOperationBean opObj = _connectionParametersList.get(i);
                int ordNumber = Util.atoi(opObj.getObject().getOrder());
                if (size != order){
                    if (ordNumber > order && order != 0){
                        int val = ordNumber - 1;
                        opObj.getObject().setOrder(Integer.toString(val));
                    }
                }
                if (opObj.getId().equalsIgnoreCase(id)) {
                    idx = i;
                    isDelete = true;
                }
            } 
            if (isDelete ) {
                _connectionParametersList.remove(idx);
                addEditState(ATT_CONNECTION_PARAMETERS,_connectionParametersList);
            }
        }
        return ;
    }

    public void addResAttributeObject() {
        _innerObj.getObject().addResAttributeMapObj();
        addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);

    }
    
    public void addHeaderObject(){
         _innerObj.getObject().addHeader();
         addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);

     }

    public void addBodyObject(){
        _innerObj.getObject().addBody();
        addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);

    }

    public void deleteBodyObject(String id){
         _innerObj.getObject().deleteBody(id);
         addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);

     }
    
    public void deleteHeaderObject(String id){
         _innerObj.getObject().deleteHeader(id);
         addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
     }

    public void deleteResAttributeObject(String id){
         _innerObj.getObject().deleteResAttributeMapObj(id);
         addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
     }
    
    public void addXpathNamespaceEntry() {
        _innerObj.getObject().addXpathNamespace();;
        addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
    }

    public void deleteXpathNamespaceEntry(String id) {
        _innerObj.getObject().deleteXpathNamespace(id);
        addEditState(ATT_INNER_BEAN_PARAMETERS, _innerObj);
    }

    public void settingBodyFormat(){
        String bodyFormat = _innerObj.getObject().getBodyFormat();
        if (bodyFormat.equalsIgnoreCase("formData")){
            if (_innerObj != null && _innerObj.getObject() != null) {
                List<MapDTO> body = _innerObj.getObject().getBody();
                if (body != null && body.size() == 0){
                    addBodyObject();
                }
            }
        }
    }
    
    private int  totalOrderNo(){
        int index = 0, conSize = 0;
        while (_connectionParametersList.size() > index) {
            ConnectorOperationBean currentBean = _connectionParametersList.get(index);
            if (Util.isNotNullOrEmpty(currentBean.getObject().getOrder())) {
                conSize ++;
            }
            index ++;
        }
        return conSize +1 ;
    }

    public class ConnectorOperationBean extends BaseBean {
        private ConnectorOperationData object;

        private String _id;

        public ConnectorOperationBean() {
            super();
            _id = "D" + Util.uuid();
            object = new ConnectorOperationData();
        }

        public ConnectorOperationBean(Map<String, Object> objectMap) {
            super();
            _id = "D" + Util.uuid();
            object = new ConnectorOperationData(objectMap);
        }

        public ConnectorOperationData getObject() {
            return object;
        }

        public String getId() {
            return _id;
        }

        public void setId(String _id) {
            this._id = _id;
        }
    }

}
