/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. 
 * 
 * The class is used for the integration of SAP GRC Access Control with the identityIQ
 * A Library to invoke certain web services which enables us to generate a request at
 * SAP GRC side and to check the status of the request being proceed at SAP GRC side.
 * 
 * The library is used from the SAP GRC Request Executor workflow, this workflow prepares
 * the matching SAP GRC request from the IIQ LCM request and executes it on SAP GRC. A request gets
 * generated on GRC and a request number is sent back to the workflow. The request number then 
 * later used to check the status of the request. SAP GRC approves approves the request, we track the 
 * status of the request in regular intervals. This library prepares the decisions on whether to go
 * ahead and proceed the provisioning or not.
 * 
 * As a part of parsing the response received from the SAP GRC side, we check each requested item 
 * with each item in the GRC response, and compares the action made on the item, with their start
 * date and end date. We currently are failing the entire request if any mismatch is found.
 *
 * */

package sailpoint.workflow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;

import functions.rfc.sap.document.sap_com.Char20;
import functions.rfc.sap.document.sap_com.Char3;
import functions.rfc.sap.document.sap_com.Char7;
import mc_style.functions.soap.sap.document.sap_com.GRAC_AUDIT_LOGS_WSStub;
import mc_style.functions.soap.sap.document.sap_com.GRAC_REQUEST_DETAILS_WSStub;
import mc_style.functions.soap.sap.document.sap_com.GRAC_USER_ACCES_WSStub;
import mc_style.functions.soap.sap.document.sap_com.GracIdmInboundAuditLogs;
import mc_style.functions.soap.sap.document.sap_com.GracIdmInboundAuditLogsResponse;
import mc_style.functions.soap.sap.document.sap_com.GracIdmReqDetailsServices;
import mc_style.functions.soap.sap.document.sap_com.GracIdmReqDetailsServicesResponse;
import mc_style.functions.soap.sap.document.sap_com.GracIdmRiskWoutNoServices;
import mc_style.functions.soap.sap.document.sap_com.GracIdmRiskWoutNoServicesResponse;
import mc_style.functions.soap.sap.document.sap_com.GracIdmUsrAccsReqServices;
import mc_style.functions.soap.sap.document.sap_com.GracIdmUsrAccsReqServicesResponse;
import mc_style.functions.soap.sap.document.sap_com.GracSApiAuditLogs;
import mc_style.functions.soap.sap.document.sap_com.GracSApiAuditlogData;
import mc_style.functions.soap.sap.document.sap_com.GracSApiAuditlogDataChild;
import mc_style.functions.soap.sap.document.sap_com.GracSSimobjLst;
import mc_style.functions.soap.sap.document.sap_com.GracSWsApiConnectorLst;
import mc_style.functions.soap.sap.document.sap_com.GracSWsApiMessage;
import mc_style.functions.soap.sap.document.sap_com.GracSWsApiObjidLst;
import mc_style.functions.soap.sap.document.sap_com.GracSWsApiRiskAnlys;
import mc_style.functions.soap.sap.document.sap_com.GracSWsApiUserInfo;
import mc_style.functions.soap.sap.document.sap_com.GracSWsRdAction;
import mc_style.functions.soap.sap.document.sap_com.GracSWsRdOpApprovers;
import mc_style.functions.soap.sap.document.sap_com.GracSWsRdOpReqPath;
import mc_style.functions.soap.sap.document.sap_com.GracSWsRdOpReqitemdetails;
import mc_style.functions.soap.sap.document.sap_com.GracSWsRdRole;
import mc_style.functions.soap.sap.document.sap_com.GracSWsReportType;
import mc_style.functions.soap.sap.document.sap_com.GracSWsReqDetailsOp;
import mc_style.functions.soap.sap.document.sap_com.GracSWsReqhdr;
import mc_style.functions.soap.sap.document.sap_com.GracSWsSimulation;
import mc_style.functions.soap.sap.document.sap_com.GracSWsUaIpCustfldVal;
import mc_style.functions.soap.sap.document.sap_com.GracSWsUaIpPrameterList;
import mc_style.functions.soap.sap.document.sap_com.GracSWsUaIpReqlineitem;
import mc_style.functions.soap.sap.document.sap_com.GracSWsUaIpUserGroup;
import mc_style.functions.soap.sap.document.sap_com.GracTApiAuditLogs;
import mc_style.functions.soap.sap.document.sap_com.GracTApiAuditlogData;
import mc_style.functions.soap.sap.document.sap_com.GracTApiAuditlogDataChild;
import mc_style.functions.soap.sap.document.sap_com.GracTSimobjLst;
import mc_style.functions.soap.sap.document.sap_com.GracTWsApiConnectorLst;
import mc_style.functions.soap.sap.document.sap_com.GracTWsApiObjidLst;
import mc_style.functions.soap.sap.document.sap_com.GracTWsApiRiskData;
import mc_style.functions.soap.sap.document.sap_com.GracTWsRaOpRiskData;
import mc_style.functions.soap.sap.document.sap_com.GracTWsRdAction;
import mc_style.functions.soap.sap.document.sap_com.GracTWsRdOpApprovers;
import mc_style.functions.soap.sap.document.sap_com.GracTWsRdOpReqPath;
import mc_style.functions.soap.sap.document.sap_com.GracTWsRdOpReqitemdetails;
import mc_style.functions.soap.sap.document.sap_com.GracTWsRdRole;
import mc_style.functions.soap.sap.document.sap_com.GracTWsReportType;
import mc_style.functions.soap.sap.document.sap_com.GracTWsSimulation;
import mc_style.functions.soap.sap.document.sap_com.GracTWsUaIpCustfldVal;
import mc_style.functions.soap.sap.document.sap_com.GracTWsUaIpPrameterList;
import mc_style.functions.soap.sap.document.sap_com.GracTWsUaIpReqlineitem;
import mc_style.functions.soap.sap.document.sap_com.GracTWsUaIpUserGroup;
import mc_style.functions.soap.sap.document.sap_com.GracTWsUserInfo;
import mc_style.functions.soap.sap.document.sap_com.ServiceStub;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMFactory;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.InputSource;

import sailpoint.api.SailPointContext;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.SAPGRCConnector;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.Comment;
import sailpoint.object.ExpansionItem;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class SAPGRCIntegrationLibrary {

    private static Log log = LogFactory.getLog(SAPGRCIntegrationLibrary.class);

    // Message Code for SUCCESS returned after a web service call
    public final static String MSG_CODE_SUCCESS = "0";

    // Header information constants
    private final static String HEADER_WEB_SERVICE_URL = "url";
    private final static String HEADER_USER_NAME = "username";
    private final static String HEADER_PASSWORD = "password";
    private final static String HEADER_TIMEOUT = "responseTimeout";

    // default timeout in miliseconds for 1 minute
    private final static int TIMEOUT_IN_MILLISECONDS = 60000;

    // comma separated list of users for whom the request is made
    private String usersRequested = null;

    private static final String IS_GRC_ENABLED = "isGRCEnabled";
    private static final String IS_PARTIAL_PROVISIONING_ENABLED = "isPartialProvisioningEnabled";
    private static final String SAP_DIRECT = "SAP - Direct";
    private static final String SAP_PORTAL = "SAP Portal - UMWebService";
    private static final String SAP_GRC = "SAP GRC";
    private static final String GRC_RESPONSE = "requestStatusMap";
    // String saying comments are from external SAP GRC system
    private static final String GRC_AUTHOR = "External GRC System - SAP GRC";
    // Description for Access Request Interaction
    private static final String DESC_INTERACTION = "External GRC Approval - Account Changes for User: ";
    // The displayName of the identity.
    private static final String ARG_DISPLAY_NAME = "identityDisplayName";

    // Retry
    private static final String RETRIABLE_ERROS = "retriableErrors";
    private static final String NUMBER_OF_RETRIES = "numberOfRetries";
    private static final String RETRY_ERROR = "RetryError";
    private static final String RETRY_STATUS_TRUE = "TRUE";
    private static final String RETRY_STATUS_FALSE = "FALSE";

    // SAP GRC status
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PARTIAL_OK = "PARTIAL_OK";
    private static final String STATUS_ERROR = "ERROR";
    private static final String STATUS_PENDING = "PENDING";
    private static final String REJECTED = "RE";
    private static final String STRING_EMPTY = "";
    private static final String DELIMETER_COMMA = ",";
    private static final String DATE_FORMAT_YYYYDDMM = "yyyyMMdd";
    private static final String DATE_FORMAT_DD_MMM_YYYY = "dd-MMM-yyyy";
    private static final String INDENT_YES = "yes";
    private static final String INDENT_PROP = "2";

    // provisioning action codes
    private static final String PROV_ACTION_ASSIGN = "006";
    private static final String PROV_ACTION_REMOVE = "009";
    private static final String PROV_ACTION_RETAIN = "010";

    // item types
    private static final String TYPE_ROLE = "ROL";
    private static final String TYPE_PROFILE = "PRF";

    // request variables
    private static final String WORKFLOW_POLLING_INTERVAL = "grc_polling_interval";
    private static final String REQUEST_LANGUAGE = "Language";
    private static final String RESPONSE_LANGUAGE = "language";
    private static final String REQUEST_REQUEST_HEADER_DATA_MAP = "requestHeaderDataMap";
    private static final String REQUEST_CUSTOM_FIELDS_VAL_MAP = "customFieldsValMap";
    private static final String REQUEST_USER_GROUPS_MAP = "userGroupsMap";
    private static final String REQUEST_REQUESTED_LINE_ITEM_MAP = "requestedLineItemMap";
    private static final String REQUEST_USER_INFO_MAP = "userInfoMap";
    private static final String REQUEST_PARAMETER_MAP = "parameterMap";
    private static final String REQUEST_CREDENTIALS_MAP = "credentialsMap";
    private static final String REQUEST_STUB_DETAILS_MAP = "requestStubDetailsMap";
    private static final String REQUEST_RISK_LEVEL = "riskLevel";
    private static final String REQUEST_RULE_SET_ID = "ruleSetId";
    private static final String REQUEST_REPORT_TYPE = "reportType";    
    private static final String SIMULATION_RISK_ONLY = "simulationRiskOnly";
    // header data
    private static final String HEADER_REQTYPE = "Reqtype";
    private static final String HEADER_PRIORITY = "Priority";
    private static final String HEADER_REQINITSYSTEM = "ReqInitSystem";
    private static final String HEADER_REQUESTORID = "Requestorid";
    private static final String HEADER_EMAIL = "Email";
    private static final String HEADER_REQUESTREASON = "RequestReason";
    private static final String HEADER_REQDUEDATE = "ReqDueDate";
    private static final String HEADER_FUNCAREA = "Funcarea";
    private static final String HEADER_BPROC = "Bproc";

    // parameter variables
    private static final String PARAMETER = "Parameter";
    private static final String PARAMETER_VALUE = "ParameterValue";
    private static final String PARAMETER_DESC = "ParameterDesc";

    // SAP GRC application details variables
    private static final String GRC_APP_GRC_USER_ID = "grc_user_id";
    private static final String GRC_APP_GRC_PASSWORD = "grc_password";
    private static final String GRC_APP_USER_ACCESS_URL = "grc_user_access_url";
    private static final String GRC_APP_REQUEST_DETAIL_URL = "grc_request_detail_url";
    private static final String GRC_APP_AUDIT_LOG_URL = "grc_audit_log_url";
    private static final String GRC_RISK_ANALYSIS_URL = "grc_risk_analysis_url";
    private static final String GRC_APP_MITIGATION_KEYWORD = "grc_mitigation_search_keyword";
    private static final String GRC_APP_CONNECTION_TIMEOUT = "grc_connection_timeout";
    private static final String GRC_APP_POLLING_INTERVAL = "grc_polling_interval";
    private static final String GRC_APP_REQUEST_INITIATION_SYSTEM = "grc_request_initiation_system";
    private static final String GRC_APP_DETAIL = "appDetailSAPGRC";

    private static final String GRC_DEFAULT_SEARCH_MITIGATION_KEYWORD = "Mitigation Control";
    private static final String APP_NAME_GRC = "applicationNameSAPGRC";
    private static final String GRC_CONN_NAME = "GRCConnectorName";

    // user info map data
    private static final String USERINFO_USERID = "Userid";
    private static final String USERINFO_FNAME = "Fname";
    private static final String USERINFO_LNAME = "Lname";
    private static final String USERINFO_MANAGER = "Manager";
    private static final String USERINFO_TITLE = "Title";
    private static final String USERINFO_SNCNAME = "SncName";
    private static final String USERINFO_UNSECSNC = "UnsecSnc";
    private static final String USERINFO_ACCNO = "Accno";
    private static final String USERINFO_USERGROUP = "UserGroup";
    private static final String USERINFO_EMPPOSITION = "Empposition";
    private static final String USERINFO_EMPJOB = "Empjob";
    private static final String USERINFO_PERSONNELNO = "Personnelno";
    private static final String USERINFO_PERSONNELAREA = "Personnelarea";
    private static final String USERINFO_COMMMETHOD = "CommMethod";
    private static final String USERINFO_FAX = "Fax";
    private static final String USERINFO_TELNUMBER = "Telnumber";
    private static final String USERINFO_DEPARTMENT = "Department";
    private static final String USERINFO_COMPANY = "Company";
    private static final String USERINFO_LOCATION = "Location";
    private static final String USERINFO_COSTCENTER = "Costcenter";
    private static final String USERINFO_PRINTER = "Printer";
    private static final String USERINFO_ORGUNIT = "Orgunit";
    private static final String USERINFO_EMPTYPE = "Emptype";
    private static final String USERINFO_MANAGEREMAIL = "ManagerEmail";
    private static final String USERINFO_MANAGERFIRSTNAME = "ManagerFirstname";
    private static final String USERINFO_MANAGERLASTNAME = "ManagerLastname";
    private static final String USERINFO_STARTMENU = "StartMenu";
    private static final String USERINFO_LOGONLANG = "LogonLang";
    private static final String USERINFO_DECNOTATION = "DecNotation";
    private static final String USERINFO_DATEFORMAT = "DateFormat";
    private static final String USERINFO_ALIAS = "Alias";
    private static final String USERINFO_USER = "User";

    // response variables
    private static final String RESPONSE_REQUESTNUMBER = "requestNumber";
    private static final String RESPONSE_REQUESTSTATUS = "RequestStatus";
    private static final String RESPONSE_MSGTYPE = "MsgType";
    private static final String RESPONSE_MSGSTATEMENT = "MsgStatement";
    private static final String RESPONSE_RISKVIOLATIONS = "RiskViolations";
    private static final String RESPONSE_REQUESTEDITEMS = "RequestedItems";
    private static final String RESPONSE_CURRENTSTAGE = "CurrentStage";

    // risk violation data variables
    private static final String RISKVIOLATIONS_USERID = "UserId";
    private static final String RISKVIOLATIONS_RISKID = "RiskId";
    private static final String RISKVIOLATIONS_RISKDESC = "RiskDesc";
    private static final String RISKVIOLATIONS_RISKLEVELDESC = "RiskLevelDesc";
    private static final String RISKVIOLATIONS_RULEID = "RuleId";
    private static final String RISKVIOLATIONS_SYSTEM = "System";
    private static final String RISKVIOLATIONS_COMPOSITROLE = "CompositRole";
    private static final String RISKVIOLATIONS_ROLE = "Role";
    private static final String RISKVIOLATIONS_ROLELIST = "RoleList";
    private static final String RISKVIOLATIONS_ACTION = "Action";

    // requested item variables
    private static final String REQUESTEDITEMS_ITEMID = "ItemId";
    private static final String REQUESTEDITEMS_ITEMDESC = "ItemDesc";
    private static final String REQUESTEDITEMS_CONNECTOR = "Connector";
    private static final String REQUESTEDITEMS_PROVITEMTYPE = "ProvItemType";
    private static final String REQUESTEDITEMS_PROVITEMTYPEDESC = "ProvItemTypeDesc";
    private static final String REQUESTEDITEMS_PROVTYPE = "ProvType";
    private static final String REQUESTEDITEMS_PROVTYPEDESC = "ProvTypeDesc";
    private static final String REQUESTEDITEMS_ASSIGNMENTTYPE = "AssignmentType";
    private static final String REQUESTEDITEMS_ASSIGNMENTTYPEDESC = "AssignmentTypeDesc";
    private static final String REQUESTEDITEMS_PROVSTATUS = "ProvStatus";
    private static final String REQUESTEDITEMS_PROVSTATUSDESC = "ProvStatusDesc";
    private static final String REQUESTEDITEMS_STATUS = "Status";
    private static final String REQUESTEDITEMS_APPROVALSTATUS = "ApprovalStatus";
    private static final String REQUESTEDITEMS_APPROVALSTATUSDESC = "ApprovalStatusDesc";
    private static final String REQUESTEDITEMS_PROVACTION = "ProvAction";
    private static final String REQUESTEDITEMS_PROVACTIONDESC = "ProvActionDesc";
    private static final String REQUESTEDITEMS_VALIDFROM = "ValidFrom";
    private static final String REQUESTEDITEMS_VALIDTO = "ValidTo";
    private static final String REQUESTEDITEMS_COMMENTS = "Comments";
    private static final String REQUESTEDITEMS_OWNERS = "Owners";
    private static final String REQUESTEDITEMS_REQITEMDESC = "ReqItemDesc";
    private static final String REQUESTEDITEMS_ITEMNAME = "ItemName";
    private static final String REQUESTEDITEMS_ROLETYPE = "RoleType";
    private static final String REQUESTEDITEMS_FFOWNER = "FfOwner";

    // current stage variables
    private static final String CURRENTSTAGE_CURSTAGENAME = "CurstageName";
    private static final String CURRENTSTAGE_CURSTAGESTATUS = "CurstageStatus";
    private static final String CURRENTSTAGE_CURSTAGEDESC = "CurstageDesc";
    private static final String CURRENTSTAGE_APPROVER = "Approver";
    private static final String DEFAULT_APPL_POLLING_INTERVAL = "1";
    private static final String DEFAULT_APPL_CONNECTION_TIMEOUT = "1";

    // audit log data variables
    private static final String AUDITLOG_RESPONSE = "AuditLogs";
    private static final String AUDITLOG_REQUESTEDBY = "Requestedby";
    private static final String AUDITLOG_SUBMITTEDBY = "Submittedby";
    private static final String AUDITLOG_CREATEDATE = "Createdate";
    private static final String AUDITLOG_PRIORITY = "Priority";
    private static final String AUDITLOG_ITAUDITDATA = "Itauditdata";
    private static final String AUDITLOG_ITEM = "item";
    private static final String AUDITLOG_ACTIONDATE = "Actiondate";
    private static final String AUDITLOG_ACTIONVALUE = "Actionvalue";
    private static final String AUDITLOG_DEPENDANTID = "Dependantid";
    private static final String AUDITLOG_DESCRIPTION = "Description";
    private static final String AUDITLOG_DISPLAYSTRING = "Displaystring";
    private static final String AUDITLOG_ID = "Id";
    private static final String AUDITLOG_PATH = "Path";
    private static final String AUDITLOG_STAGE = "Stage";
    private static final String AUDITLOG_USERID = "Userid";
    private static final String AUDITLOG_ITAUDITDATACHILD = "Itauditdatachild";

    // workflow variable to store result of proactive check 
    private static final String WFC_PROACTIVE_VIOLATION_FOUND = "violationFound";
    private static final String INCLUDE = "S";
    private static final String EXCLUDE = "";
    private static final String OBJECT_TYPE_USER = "ObjectType";
    private static final String REPORT_FORMAT = "ReportFormat";
    private static final String USER = "USR";
    private static final String REPORTFORMAT = "DETAILED";

    private static final String SAPGRCACCOUNTREQUEST= "accountRequestSAPGRC";
    private static final String SUNRISE_DATE = "addDate";
    private static final String SUNSET_DATE = "removeDate";
    private static final String GRCDELIMITER = "grcDelimiter";
    private static final String BUSINESS_ROLE_ASSIGNMENT = "assignedRoles";
    private static final String ROLE = "Role";
    private static final String PROFILES = "Profiles";
    private static final String IS_APP_CUA_ENABLED = "IsCUASystem";
    private static final String ARG_COMMENTS_LIST = "commentList";
    private static final String GRCRequestDateFormatString = "yyyyMMdd";
    private String defaultEndDate = "99991231";
    private String defaultStartDate = new SimpleDateFormat(GRCRequestDateFormatString).format(new Date());
    private static final String SAP_ENTL_BUSINESS_ROLE_MAP = "sapEntlBusinessRoleMap";
    private static final String EXPANDED_BUSINESS_ROLE_MAP = "expandedBusinessRoleMap";
    private static final String SAP_BUSINESS_ROLE_MAP = "sapBusinessRoleMap";

    // comment holder object which holds all the messages parsed from the response
    private SAPGRCMessages _grcComments = new SAPGRCMessages();

	/**
	 * MultiValueMap which maintains association between rejected SAP role and it's associated
	 * connector name.
	 */
	private Map rejectedSAPRoleConnNameMap;
    
    /*
     * Library functions required for SAP GRC Request Executor Subprocess 
     */

    /**
     * Function resolves the time out parameter, 
     * and converts the timeout in milliseconds.
     * @param timeout Timeout in minutes.
     * @return Timeout in milisecods.
     */
    private int getTimeout(String timeout) {
        // by default timeout is one minute (60000 milliseconds)
        int conectionTimeout = TIMEOUT_IN_MILLISECONDS;
        if ( Util.isNotNullOrEmpty(timeout) && Util.isInt(timeout) ) {
            try {
                int timeInMinutes = Integer.parseInt(timeout);
                if (timeInMinutes > 0) {
                    conectionTimeout = timeInMinutes * TIMEOUT_IN_MILLISECONDS;
                }
            } catch (Exception e) {
                if ( log.isErrorEnabled() ) {
                    log.error("Error while parsing the timeout, setting default timeout as 1 minute " + e.getMessage(), e);
                }
            }
        }
        return conectionTimeout;
    }

    /**
     * Function converts a Java.lang.String to functions.rfc.sap.document.sap_com.String.
     * @param javaString - a String object from java.lang package
     * @return gracString - a String object from functions.rfc.sap.document.sap_com package
     */
    private functions.rfc.sap.document.sap_com.String getGracString( String javaString) {
        functions.rfc.sap.document.sap_com.String gracString = new functions.rfc.sap.document.sap_com.String();
        gracString.setString(javaString);
        return gracString;
    }

    /**
     *  function forms a grac Char1 from java String
     *  @param javaString
     *  @return functions.rfc.sap.document.sap_com.Char1
     */
    private functions.rfc.sap.document.sap_com.Char1 getGracChar( String javaString) {
        functions.rfc.sap.document.sap_com.Char1 gracChar = new functions.rfc.sap.document.sap_com.Char1();
        gracChar.setChar1(javaString);
        return gracChar;
    }

    /**
     * Function gets the value from the given <String,String> map by the specified key.
     * it returns empty string if no value found in the map.
     * @param map - Map having String to String mapping.
     * @param key - A key for which value to be found.
     * @return - Value for the specified key OR empty string.
     */
    private String getStringValueByKey(Map<String, String> map, String key) {
        String value = STRING_EMPTY;
        if (!Util.isEmpty(map)) {
            value = map.get(key);
            if (Util.isNullOrEmpty(value)) {
                // reason behind we are making it empty is, web service requires some value to be 
                // entered in the field, It can be a valid calue, or empty string not specified
                // if we do not get value in the input map, putting it as an empty string
                value = STRING_EMPTY;
            }
        }
        return value;
    }

    /* Sample ARA web service request
         <ns2:GracIdmRiskWoutNoServices xmlns:ns2="urn:sap-com:document:sap:soap:functions:mc-style">
              <ConnectorId>
                 <item>
                    <Connector>ZECPCNT100</Connector>
                 </item>
             </ConnectorId>
                <ObjectId>
                  <item>
                     <Objid>SUSHANT</Objid>
                   </item>
             </ObjectId>
            <ObjectType>USR</ObjectType>
               <ReportFormat>DETAILED</ReportFormat>
                 <Simulation>
                    <item>
                       <Connector>ZECPCNT100</Connector>
                       <Simuobtype>ROL</Simuobtype>
                       <SimuobjidLst>
                    <item>
                        <Simuobjid>/ISDFPS/ME_ADM_SCND</Simuobjid>
                    </item>
                         </SimuobjidLst>
                         <Excludesimu/>
                    </item>
                 </Simulation>
               <SimulationRiskOnly>S</SimulationRiskOnly>
               </ns2:GracIdmRiskWoutNoServices>
         */
    /**
     * This function is a pre-assessment of a request before it is submitted on GRC.
     * This analyzes the risks in the request by querying web service GRAC_RISK_ANALYSIS_WOUT_NO_WS.
     * Return result would be true or false stating if request has risks or not.
     * @param wfc Workflow Context.
     * @return If methods has risks this would return true, otherwise false.
     * @throws GeneralException, ConnectorException, RemoteException
     */
    private boolean executeProActiveCheck(WorkflowContext wfc) throws GeneralException, ConnectorException, RemoteException {     
         
        Attributes<String, Object> argList = wfc.getArguments();
        boolean foundRisk = false;    
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> credentialsMap = (Map<String, String>) argList.get(REQUEST_CREDENTIALS_MAP);
            String risk_analysis_url = credentialsMap.get(GRC_RISK_ANALYSIS_URL);
            if (Util.isNullOrEmpty(risk_analysis_url)) {
                throw new GeneralException("Risk Analysis is a mandatory check. Please provide URL for Access Risk Analysis web service.");
            }
            String grc_user_id = credentialsMap.get(GRC_APP_GRC_USER_ID);
            String grc_password = credentialsMap.get(GRC_APP_GRC_PASSWORD);
            SailPointContext spContext = wfc.getSailPointContext();
            String connectionTimeout = (String) credentialsMap.get(GRC_APP_CONNECTION_TIMEOUT);
            if ( Util.isNullOrEmpty(connectionTimeout) ) {
                connectionTimeout = DEFAULT_APPL_CONNECTION_TIMEOUT;
            }
            int milliseconds = getTimeout(connectionTimeout);
            if(Util.isNotNullOrEmpty(grc_password))
                grc_password = spContext.decrypt(grc_password);

            Map<String,Object> headerInfo = new HashMap<String,Object>();
            headerInfo.put(HEADER_WEB_SERVICE_URL, risk_analysis_url);
            headerInfo.put(HEADER_USER_NAME, grc_user_id);
            headerInfo.put(HEADER_PASSWORD, grc_password);
            headerInfo.put(HEADER_TIMEOUT, milliseconds);
            ServiceStub  serviceStub = (ServiceStub) SAPGRCConnector.prepareHeader(ServiceStub.class, headerInfo);


            foundRisk = checkGRCViolations(wfc,serviceStub);
        }catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Execution of the Pro active check Web service resulted in error : " + ex.getMessage(), ex);
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        return foundRisk;
    }

    /**
     * This library function executes a SAP GRC User Access request GRAC_USER_ACCES_WS.
     * inputs to the requests are basically maps which holds all the simple and complex data
     * function prepares the request and fires a call to SAM GRC to execute the request
     * Web service returns a request number back if request call is successful.
     * @param wfc Workflow Context.
     * @return Request number generated by SAP GRC Access Request.
     * @throws GeneralException, ConnectorException, RemoteException
     */
    @SuppressWarnings("unchecked")
    public String executeUserAccessRequest(WorkflowContext wfc) throws GeneralException, ConnectorException, RemoteException {

        String requestNo = null;
        if (log.isDebugEnabled()) {
            log.debug("Checking for risks in the request");
        }
        boolean isViolationFound = executeProActiveCheck( wfc );
        Attributes<String, Object> argList = wfc.getArguments();
        wfc.setVariable(WFC_PROACTIVE_VIOLATION_FOUND, isViolationFound);
        if ( isViolationFound ) {
            if (log.isDebugEnabled()) {
                log.debug("Creating SAP GRC Access Request");
            }
            Map<String,Object> headerInfo = null;
            Map<String,Object> requestStubDetailsMap = getRequestStubDetailsMap(wfc);
            try {
                SailPointContext spContext = wfc.getSailPointContext();


                // getting inputs from workflow context
                String language = (String) argList.get(REQUEST_LANGUAGE);
                if (log.isDebugEnabled()) {
                    log.debug("Received Language : " +  language);
                }
                if (Util.isNullOrEmpty(language)) {
                    language = STRING_EMPTY;
                }
                Map<String, String> requestHeaderDataMap = (Map<String, String>) argList.get(REQUEST_REQUEST_HEADER_DATA_MAP);
                if (log.isDebugEnabled()) {
                    log.debug("Received requestHeaderDataMap : " +  requestHeaderDataMap);
                }
                Map<String, String> customFieldsValMap = (Map<String, String>) argList.get(REQUEST_CUSTOM_FIELDS_VAL_MAP);
                if (log.isDebugEnabled()) {
                    log.debug("Received customFieldsValMap : " +  customFieldsValMap);
                }
                Map<String, String> userGroupsMap = (Map<String, String>) argList.get(REQUEST_USER_GROUPS_MAP);
                if (log.isDebugEnabled()) {
                    log.debug("Received userGroupsMap : " +  userGroupsMap);
                }
                List<Map<String, String>> requestedLineItemMap = (List<Map<String, String>>) argList.get(REQUEST_REQUESTED_LINE_ITEM_MAP);
                if (log.isDebugEnabled()) {
                    log.debug("Received requestedLineItemMap : " +  requestedLineItemMap);
                }
                List<Map<String, String>> userInfoMap = (List<Map<String, String>>) argList.get(REQUEST_USER_INFO_MAP);
                if (log.isDebugEnabled()) {
                    log.debug("Received userInfoMap : " +  userInfoMap);
                }
                List<String> userIdsRequested = new ArrayList<String>();
                if (!Util.isEmpty(userInfoMap)) {
                    for (Map<String,String> userInfos : userInfoMap) {
                        String userId = userInfos.get(USERINFO_USERID);
                        if ( Util.isNotNullOrEmpty(userId)) {
                            userIdsRequested.add(userId);
                        }
                    }
                    if (!Util.isEmpty(userIdsRequested)) {
                        usersRequested = StringUtils.join(userIdsRequested, DELIMETER_COMMA);
                        //Putting the requested user list in a Map
                        requestStubDetailsMap.put("usersRequested", usersRequested);
                        if (log.isDebugEnabled()) {
                            log.debug("Users requested : " +  usersRequested);
                        }
                    }
                }

                List<Map<String, String>> parameterMap = (List<Map<String, String>>) argList.get(REQUEST_PARAMETER_MAP);
                if (log.isDebugEnabled()) {
                    log.debug("Received parameterMap : " +  parameterMap);
                }

                // this is a temporary map - which holds credentials data
                // We will remove this once credentials storage feature is done.
                Map<String, String> credentialsMap = (Map<String, String>) argList.get(REQUEST_CREDENTIALS_MAP);
                String user_access_url = credentialsMap.get(GRC_APP_USER_ACCESS_URL);
                String request_status_url = credentialsMap.get(GRC_APP_REQUEST_DETAIL_URL);
                String grc_user_id = credentialsMap.get(GRC_APP_GRC_USER_ID);
                String grc_password = credentialsMap.get(GRC_APP_GRC_PASSWORD);
                if(null != grc_password)
                    grc_password = spContext.decrypt(grc_password);
                String connectionTimeout = (String) credentialsMap.get(GRC_APP_CONNECTION_TIMEOUT);
                if ( Util.isNullOrEmpty(connectionTimeout) ) {
                    connectionTimeout = DEFAULT_APPL_CONNECTION_TIMEOUT;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Received Timeout : " + connectionTimeout);
                }
                int milliseconds = getTimeout(connectionTimeout);
                // preparing the request and filling the header data
                headerInfo = new HashMap<String,Object>();
                headerInfo.put(HEADER_WEB_SERVICE_URL, user_access_url);
                headerInfo.put(HEADER_USER_NAME, grc_user_id);
                headerInfo.put(HEADER_PASSWORD, grc_password);
                headerInfo.put(HEADER_TIMEOUT, milliseconds);
                GRAC_USER_ACCES_WSStub  userAccessserviceStub = (GRAC_USER_ACCES_WSStub) SAPGRCConnector.prepareHeader(GRAC_USER_ACCES_WSStub.class, headerInfo);

                headerInfo.put(HEADER_WEB_SERVICE_URL, request_status_url);

                //setting header info map for request Details
                requestStubDetailsMap.put("headerInfo", headerInfo);
                //Setting requestDtailMap into workflowContext.
                wfc.setVariable(REQUEST_STUB_DETAILS_MAP, requestStubDetailsMap);

                GracIdmUsrAccsReqServices userAccessRequest = new GracIdmUsrAccsReqServices();

                // setting CustomFieldsVal
                populateCustomFields(userAccessRequest, customFieldsValMap);

                // setting Language : <Language>String 5</Language>
                userAccessRequest.setLanguage(getGracString(language));

                // setting user groups
                populateUserGroups(userAccessRequest, userGroupsMap);

                // setting header data
                populateRequestHeaderData(userAccessRequest, requestHeaderDataMap);

                // setting Parameters
                populateParameters(userAccessRequest, parameterMap);

                //Setting requested line item
                populateRequestedLineItem(userAccessRequest, requestedLineItemMap);

                //Setting user Info
                populateUserInfo(userAccessRequest, userInfoMap);

                // Lets Print the request in logs
                if (log.isDebugEnabled()) {
                    printXml(userAccessRequest);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Firing a GRAC_USER_ACCES_WS web service call for user(s) " + usersRequested);
                }
                // Firing the request
                GracIdmUsrAccsReqServicesResponse userAccessResponse =  userAccessserviceStub.gracIdmUsrAccsReqServices(userAccessRequest);

                // Reading Response
                if ( null != userAccessResponse ) {
                    GracSWsApiMessage returnMessage = userAccessResponse.getMsgReturn();
                    if ( null != returnMessage ) {
                        Char3 msgNo = returnMessage.getMsgNo();
                        if ( null != msgNo ) {
                            String messageNo = msgNo.getChar3();
                            // 0 = SUCCESS  and  4 = ERROR
                            if ( messageNo.equals(MSG_CODE_SUCCESS)) {
                                Char20 reqNo = userAccessResponse.getRequestNo();
                                if( null != reqNo ) {
                                    requestNo = reqNo.getChar20();
                                    if (log.isInfoEnabled()) {
                                        log.info("Request generated for account(s) : " + usersRequested + " with request number : " + requestNo);
                                    }
                                    if (Util.isNotNullOrEmpty(requestNo)) {

                                        // Update the identityRequest page
                                        updateCurrentStep(wfc, requestNo);



                                    } else {
                                        if (log.isInfoEnabled()) {
                                            log.info("No request number received from SAP GRC for account(s) : " + usersRequested);
                                        }
                                        throw new GeneralException("No request number received from SAP GRC for account(s) : " + usersRequested);
                                    }
                                }
                            } else {
                                if (log.isDebugEnabled()) {
                                    log.debug("Execution of the Access Request Web service resulted in error for account(s) " + usersRequested);
                                }
                                String messageType = null, messageStatement = null;
                                Char7 msgTypeChar7 = returnMessage.getMsgType();
                                if ( null != msgTypeChar7) {
                                    messageType = msgTypeChar7.toString();
                                }
                                functions.rfc.sap.document.sap_com.String msgStatement = returnMessage.getMsgStatement();
                                if ( null != msgStatement) {
                                    messageStatement = msgStatement.toString();
                                }
                                if (Util.isNotNullOrEmpty(messageStatement) && Util.isNotNullOrEmpty(messageType)) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Execution of the Access Request Web service resulted in error. Message Type : " + messageType + ", Message Reason : " + messageStatement);
                                    }
                                    throw new GeneralException("Execution of the Access Request Web service resulted in error. Message Type : " + messageType + ", Message Reason : " + messageStatement);
                                }
                                throw new GeneralException("Execution of the Access Request Web service resulted in error.");
                            }
                        }
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Access Request Web service returned null for user(s) " + usersRequested);
                    }
                    throw new GeneralException("Access Request Web service returned null for user(s) " + usersRequested); 
                }
            } catch (Exception ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Execution of the Access Request Web service resulted in error : " + ex.getMessage(), ex);
                }
                GeneralException exception = new GeneralException(ex);
                errorHandler(wfc, exception);
            }
        } else {
            // proactive check has found no risk in the request.
            // As request is clean, add the message
            _grcComments.setProActiveMsg();
            requestNo = "SP_NO_RISKS";
        }

        return requestNo;
    }

    /**
     * Function to get audit logs in map format .This will be called by "SAP GRC Request executor" work-flow
     * @param wfc This input parameter  will be the object of WorkflowContext.
     * @return Map<String,Object> Map will return in the form of <String , Object> as key,value.
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> getAuditLogDetails(WorkflowContext wfc) throws GeneralException {

        Map<String,Object> auditLogMap = new HashMap<String,Object>();
        Attributes<String, Object> argList = wfc.getArguments();
        //If customer does not want the Audit log then he will not provide the audit log and this will be the check for us to detect that should we dump the AudiLog in log file or not.
        Map<String, String> credentialsMap = (Map<String, String>) argList.get(REQUEST_CREDENTIALS_MAP);
        String user_access_url = credentialsMap.get(GRC_APP_AUDIT_LOG_URL);
        if (Util.isNotNullOrEmpty(user_access_url)) {
            String requestNo = (String) wfc.get(RESPONSE_REQUESTNUMBER);

            // We will check if the request id is not null
            if(Util.isNotNullOrEmpty(requestNo)) {
                SailPointContext spContext = wfc.getSailPointContext();
                String grc_user_id = credentialsMap.get(GRC_APP_GRC_USER_ID);
                String grc_password = credentialsMap.get(GRC_APP_GRC_PASSWORD);
                if(null != grc_password)
                    grc_password = spContext.decrypt(grc_password);

                String connectionTimeout = (String) credentialsMap.get(GRC_APP_CONNECTION_TIMEOUT);
                if ( Util.isNullOrEmpty(connectionTimeout) ) {
                    connectionTimeout = DEFAULT_APPL_CONNECTION_TIMEOUT;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Received Timeout : " + connectionTimeout);
                }
                int milliseconds = getTimeout(connectionTimeout);

                // Mitigation keyword to be searched in the audit Log response.
                // fetching from GRC application.
                String mitigationSearString = credentialsMap.get(GRC_APP_MITIGATION_KEYWORD);

                // preparing the request and filling the header data
                Map<String,Object> headerInfo = new HashMap<String,Object>();
                headerInfo.put(HEADER_WEB_SERVICE_URL, user_access_url);
                headerInfo.put(HEADER_USER_NAME, grc_user_id);
                headerInfo.put(HEADER_PASSWORD, grc_password);
                headerInfo.put(HEADER_TIMEOUT, milliseconds);
                GracIdmInboundAuditLogs gracIdmInboundAuditLogs = new GracIdmInboundAuditLogs(); 
                gracIdmInboundAuditLogs.setRequestNumber(getGracString(requestNo));

                // Printing audit log request in logs
                printXml(gracIdmInboundAuditLogs);

                //Preparing the stub by providing the populated header information. 
                GRAC_AUDIT_LOGS_WSStub userAccessserviceStub = null;
                try {
                    userAccessserviceStub = (GRAC_AUDIT_LOGS_WSStub) SAPGRCConnector.prepareHeader(GRAC_AUDIT_LOGS_WSStub.class, headerInfo);
                    GracIdmInboundAuditLogsResponse audiLogResponse = userAccessserviceStub.gracIdmInboundAuditLogs(gracIdmInboundAuditLogs);
                    auditLogMap = parseGRCAuditLog(audiLogResponse, mitigationSearString, wfc);
                } catch (ConnectorException ce) {
                    if (log.isErrorEnabled()) {
                        log.error("Execution of the Audit Log Web service resulted in error : " + ce.getMessage(), ce);
                    }
                    GeneralException exception = new GeneralException(ce);
                    errorHandler(wfc, exception);
                } catch (RemoteException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Execution of the Audit Log Web service resulted in error : " + e.getMessage(), e);
                    }
                    GeneralException exception = new GeneralException(e);
                    errorHandler(wfc, exception);
                }              
            } else {
                GeneralException exception = new GeneralException("Request ID is null. ");
                errorHandler(wfc, exception);
            }
        }
        return auditLogMap;
    }

    /**
     * This library function executes a SAP GRC Audit Logs request web service GRAC_AUDIT_LOGS_WS.
     * inputs to the requests are basically WorkflowContext which holds all the simple and complex data
     * function prepares the request and fires a call to SAP GRC to execute the request
     * @param audiLogResponse AuditLog web service response to be parsed.
     * @param mitigationSearString Mitigation control keyword to be found iin the web service response.
     * @return Map <String,Object> Map will contain the information regarding Audit Log in the form of <key, value> pair 
     * @throws GeneralException
     */
    private Map <String,Object> parseGRCAuditLog(GracIdmInboundAuditLogsResponse audiLogResponse, String mitigationSearString, WorkflowContext wfc) throws GeneralException {
        Map<String,Object> auditLogMap = new HashMap<String,Object>();
        if ( null != audiLogResponse ) {
            GracSWsApiMessage returnMessage = audiLogResponse.getMsgReturn();
            if ( null != returnMessage ) {
                Char3 msgNo = returnMessage.getMsgNo();
                if ( null != msgNo ) {
                    String messageNo = msgNo.getChar3();
                    // 0 = SUCCESS  and  4 = ERROR
                    if ( messageNo.equals(MSG_CODE_SUCCESS)) {
                        GracTApiAuditLogs auditLog = audiLogResponse.getAuditLogs();
                        if (null != auditLog) {
                            GracSApiAuditLogs[] list = auditLog.getItem();
                            if (!Util.isEmpty(list)) {
                                // if not provided default is "Mitigation Control"
                                if (Util.isNullOrEmpty(mitigationSearString)) {
                                    mitigationSearString = GRC_DEFAULT_SEARCH_MITIGATION_KEYWORD;
                                }
                                List<Map<String,Object>> evaluateItems =  evaluateItems(list, mitigationSearString);
                                auditLogMap.put(AUDITLOG_RESPONSE, evaluateItems);
                            }
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Could not execute the Audit Log Web Service for account(s) " + usersRequested);
                        }
                        String messageType = null, messageStatement = null;
                        Char7 msgTypeChar7 = returnMessage.getMsgType();
                        if ( null != msgTypeChar7) {
                            messageType = msgTypeChar7.toString();
                        }
                        functions.rfc.sap.document.sap_com.String msgStatement = returnMessage.getMsgStatement();
                        if ( null != msgStatement) {
                            messageStatement = msgStatement.toString();
                        }
                        if (Util.isNotNullOrEmpty(messageStatement) && Util.isNotNullOrEmpty(messageType)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Could not execute the Audit Log Web Service. Message Type : " + messageType + ", Message Reason : " + messageStatement);
                            }
                            GeneralException exception = new GeneralException("Could not execute the Audit Log Web Service. Message Type : " + messageType + ", Message Reason : " + messageStatement);
                            errorHandler(wfc, exception);
                            
                        }
                        GeneralException exception = new GeneralException("Could not execute the Audit Log Web Service.");
                        errorHandler(wfc, exception);

                    }
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Access Request Web Service returned null for user(s) " );
            }
            GeneralException exception = new GeneralException("Access Request Web Service returned null for user(s) ");
            errorHandler(wfc, exception);
        }
        return auditLogMap;
    }

    /**
     * Function fills the customFiledsVal data in the request.
     * @param userAccessRequest User access request object.
     * @param customFieldsValMap Map holding CustomFieldsVal data.
     */
    private void populateCustomFields(GracIdmUsrAccsReqServices userAccessRequest, Map<String, String> customFieldsValMap) {
        /*
        <CustomFieldsVal>
          <item>
           <Fieldname>String 1</Fieldname>
           <Value>String 2</Value>
          </item>
         </CustomFieldsVal>
         */
        if (!Util.isEmpty(customFieldsValMap)) {
            GracTWsUaIpCustfldVal customValList = new GracTWsUaIpCustfldVal();
            for ( Map.Entry<String, String> entry : customFieldsValMap.entrySet() ) {
                String fieldName = entry.getKey();
                String value = entry.getValue();
                GracSWsUaIpCustfldVal customValItem = new GracSWsUaIpCustfldVal();
                customValItem.setFieldname(getGracString(fieldName)); // Fieldname
                customValItem.setValue(getGracString(value)); // Value
                customValList.addItem(customValItem);
            }
            userAccessRequest.setCustomFieldsVal(customValList);
        }
    }

    /**
     * Function fills the UserGroups data in the request.
     * @param userAccessRequest User access request object.
     * @param userGroupsMap Map holding UserGroup data.
     */
    private void populateUserGroups(GracIdmUsrAccsReqServices userAccessRequest, Map<String, String> userGroupsMap) {
        /*
        <UserGroup>
          <item>
           <UserGroup>String 45</UserGroup>
           <UserGroupDesc>String 46</UserGroupDesc>
          </item>
         </UserGroup>
         */
        if (!Util.isEmpty(userGroupsMap)) {
            GracTWsUaIpUserGroup userGroupList = new GracTWsUaIpUserGroup();
            for ( Map.Entry<String, String> entry : userGroupsMap.entrySet() ) {
                String userGroup = entry.getKey();
                String userGroupDesc = entry.getValue();
                GracSWsUaIpUserGroup userGroupItem = new GracSWsUaIpUserGroup();
                userGroupItem.setUserGroup(getGracString(userGroup)); // UserGroup
                userGroupItem.setUserGroupDesc(getGracString(userGroupDesc)); // UserGroupDesc
                userGroupList.addItem(userGroupItem);
            }
            userAccessRequest.setUserGroup(userGroupList);
        }
    }

    /**
     * Function fills the RequestHeaderData data in the request.
     * @param userAccessRequest User access request object.
     * @param requestHeaderDataMap Map holding RequestHeaderData data.
     */
    private void populateRequestHeaderData(GracIdmUsrAccsReqServices userAccessRequest, Map<String, String> requestHeaderDataMap) {
        /*
        <RequestHeaderData>
          <Reqtype>String 12</Reqtype>
          <Priority>String 13</Priority>
          <ReqDueDate>String 14</ReqDueDate>
          <ReqInitSystem>String 15</ReqInitSystem>
          <Requestorid>String 16</Requestorid>
          <Email>String 17</Email>
          <RequestReason>String 18</RequestReason>
          <Funcarea>String 19</Funcarea>
          <Bproc>String 20</Bproc>
       </RequestHeaderData>
         */
        if (!Util.isEmpty(requestHeaderDataMap)) {
            GracSWsReqhdr requestHeader = new GracSWsReqhdr();
            requestHeader.setReqtype(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_REQTYPE)));
            requestHeader.setPriority(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_PRIORITY)));
            requestHeader.setReqInitSystem(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_REQINITSYSTEM)));
            requestHeader.setRequestorid(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_REQUESTORID)));
            requestHeader.setEmail(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_EMAIL))); 
            requestHeader.setRequestReason(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_REQUESTREASON)));
            requestHeader.setReqDueDate(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_REQDUEDATE))); 
            requestHeader.setFuncarea(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_FUNCAREA)));
            requestHeader.setBproc(getGracString(getStringValueByKey(requestHeaderDataMap, HEADER_BPROC)));
            userAccessRequest.setRequestHeaderData(requestHeader);
        }
    }

    /**
     * Function fills the Parameter data in the request.
     * @param userAccessRequest User access request object.
     * @param parameterMap Map holding Parameter data.
     */
    private void populateParameters(GracIdmUsrAccsReqServices userAccessRequest, List<Map<String, String>> parameterMap) {
        /*
        <Parameter>
         <item>
          <Parameter>String 6</Parameter>
          <ParameterValue>String 7</ParameterValue>
          <ParameterDesc>String 8</ParameterDesc>
         </item>
        </Parameter>
         */
        if (!Util.isEmpty(parameterMap)) {
            GracTWsUaIpPrameterList parameterList = new GracTWsUaIpPrameterList();
            for ( Map<String, String> valueMap : parameterMap ) {
                GracSWsUaIpPrameterList parameterItem = new GracSWsUaIpPrameterList();
                parameterItem.setParameter(getGracString(valueMap.get(PARAMETER))); // Parameter
                parameterItem.setParameterValue(getGracString(valueMap.get(PARAMETER_VALUE))); // ParameterValue
                parameterItem.setParameterDesc(getGracString(valueMap.get(PARAMETER_DESC))); // ParameterDesc
                parameterList.addItem(parameterItem);
            }
            userAccessRequest.setParameter(parameterList);
        }
    }

    /**
     * Function fills the RequestedLineItem data in the request.
     * @param userAccessRequest User access request object.
     * @param requestedLineItemMap Map holding RequestedLineItem data.
     */
    private void populateRequestedLineItem(GracIdmUsrAccsReqServices userAccessRequest, List<Map<String, String>> requestedLineItemMap) {
        /*
        <RequestedLineItem>
         <item>
          <ItemName>String 21</ItemName>
          <Connector>String 22</Connector>
          <ProvItemType>String 23</ProvItemType>
          <ProvType>String 24</ProvType>
          <AssignmentType>String 25</AssignmentType>
          <ProvStatus>String 26</ProvStatus>
          <ValidFrom>String 27</ValidFrom>
          <ValidTo>String 28</ValidTo>
          <FfOwner>String 29</FfOwner>
          <Comments>String 30</Comments>
          <ProvAction>String 31</ProvAction>
          <RoleType>String 32</RoleType>
         </item>
        </RequestedLineItem> 
         */
        if (!Util.isEmpty(requestedLineItemMap)) {
            GracTWsUaIpReqlineitem lineItem = new GracTWsUaIpReqlineitem();
            for ( Map<String, String> valueMap : requestedLineItemMap ) {
                GracSWsUaIpReqlineitem swLineItem = new GracSWsUaIpReqlineitem();
                swLineItem.setItemName(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_ITEMNAME)));
                swLineItem.setConnector(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_CONNECTOR)));
                swLineItem.setProvItemType(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_PROVITEMTYPE)));
                swLineItem.setProvType(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_PROVTYPE)));
                swLineItem.setProvStatus(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_PROVSTATUS)));
                swLineItem.setAssignmentType(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_ASSIGNMENTTYPE)));
                swLineItem.setProvAction(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_PROVACTION)));
                swLineItem.setRoleType(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_ROLETYPE)));
                swLineItem.setValidFrom(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_VALIDFROM)));
                swLineItem.setValidTo(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_VALIDTO)));
                swLineItem.setFfOwner(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_FFOWNER)));
                swLineItem.setComments(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_COMMENTS)));
                lineItem.addItem(swLineItem);
            }
            userAccessRequest.setRequestedLineItem(lineItem);
        }
    }

    /**
     * Function fills the UserInfo data in the request.
     * @param userAccessRequest User access request object.
     * @param userInfoMap Map holding UserInfo data.
     */
    private void populateUserInfo(GracIdmUsrAccsReqServices userAccessRequest, List<Map<String, String>> userInfoMap) {
        /*
        <UserInfo>
         <item>
          <Userid>String 49</Userid>
          <Title>String 50</Title>
          <Fname>String 51</Fname>
          <Lname>String 52</Lname>
          <SncName>String 53</SncName>
          <UnsecSnc>String 54</UnsecSnc>
          <Accno>String 55</Accno>
          <UserGroup>String 56</UserGroup>
          <ValidFrom>String 57</ValidFrom>
          <ValidTo>String 58</ValidTo>
          <Empposition>String 59</Empposition>
          <Empjob>String 60</Empjob>
          <Personnelno>String 61</Personnelno>
          <Personnelarea>String 62</Personnelarea>
          <CommMethod>String 63</CommMethod>
          <Fax>String 64</Fax>
          <Email>String 65</Email>
          <Telnumber>String 66</Telnumber>
          <Department>String 67</Department>
          <Company>String 68</Company>
          <Location>String 69</Location>
          <Costcenter>String 70</Costcenter>
          <Printer>String 71</Printer>
          <Orgunit>String 72</Orgunit>
          <Emptype>String 73</Emptype>
          <Manager>String 74</Manager>
          <ManagerEmail>String 75</ManagerEmail>
          <ManagerFirstname>String 76</ManagerFirstname>
          <ManagerLastname>String 77</ManagerLastname>
          <StartMenu>String 78</StartMenu>
          <LogonLang>String 79</LogonLang>
          <DecNotation>String 80</DecNotation>
          <DateFormat>String 81</DateFormat>
          <Alias>String 82</Alias>
          <UserType>String 83</UserType>
         </item>
         </UserInfo>
         */
        if (!Util.isEmpty(userInfoMap)) {
            GracTWsUserInfo userInfo = new GracTWsUserInfo();
            for ( Map<String, String> valueMap : userInfoMap ) {
                GracSWsApiUserInfo swUserInfo = new GracSWsApiUserInfo();
                swUserInfo.setUserid(getGracString(getStringValueByKey(valueMap, USERINFO_USERID)));
                swUserInfo.setFname(getGracString(getStringValueByKey(valueMap, USERINFO_FNAME)));
                swUserInfo.setLname(getGracString(getStringValueByKey(valueMap, USERINFO_LNAME)));
                swUserInfo.setEmail(getGracString(getStringValueByKey(valueMap, HEADER_EMAIL)));
                swUserInfo.setManager(getGracString(getStringValueByKey(valueMap, USERINFO_MANAGER)));
                swUserInfo.setTitle(getGracString(getStringValueByKey(valueMap, USERINFO_TITLE)));
                swUserInfo.setSncName(getGracString(getStringValueByKey(valueMap, USERINFO_SNCNAME)));
                swUserInfo.setUnsecSnc(getGracString(getStringValueByKey(valueMap, USERINFO_UNSECSNC)));
                swUserInfo.setAccno(getGracString(getStringValueByKey(valueMap, USERINFO_ACCNO)));
                swUserInfo.setUserGroup(getGracString(getStringValueByKey(valueMap, USERINFO_USERGROUP)));
                swUserInfo.setValidFrom(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_VALIDFROM)));
                swUserInfo.setValidTo(getGracString(getStringValueByKey(valueMap, REQUESTEDITEMS_VALIDTO)));
                swUserInfo.setEmpposition(getGracString(getStringValueByKey(valueMap, USERINFO_EMPPOSITION)));
                swUserInfo.setEmpjob(getGracString(getStringValueByKey(valueMap, USERINFO_EMPJOB)));
                swUserInfo.setPersonnelno(getGracString(getStringValueByKey(valueMap, USERINFO_PERSONNELNO)));
                swUserInfo.setPersonnelarea(getGracString(getStringValueByKey(valueMap, USERINFO_PERSONNELAREA)));
                swUserInfo.setCommMethod(getGracString(getStringValueByKey(valueMap, USERINFO_COMMMETHOD)));
                swUserInfo.setFax(getGracString(getStringValueByKey(valueMap, USERINFO_FAX)));
                swUserInfo.setTelnumber(getGracString(getStringValueByKey(valueMap, USERINFO_TELNUMBER)));
                swUserInfo.setDepartment(getGracString(getStringValueByKey(valueMap, USERINFO_DEPARTMENT)));
                swUserInfo.setCompany(getGracString(getStringValueByKey(valueMap, USERINFO_COMPANY)));
                swUserInfo.setLocation(getGracString(getStringValueByKey(valueMap, USERINFO_LOCATION)));
                swUserInfo.setCostcenter(getGracString(getStringValueByKey(valueMap, USERINFO_COSTCENTER)));
                swUserInfo.setPrinter(getGracString(getStringValueByKey(valueMap, USERINFO_PRINTER)));
                swUserInfo.setOrgunit(getGracString(getStringValueByKey(valueMap, USERINFO_ORGUNIT)));
                swUserInfo.setEmptype(getGracString(getStringValueByKey(valueMap, USERINFO_EMPTYPE)));
                swUserInfo.setManagerEmail(getGracString(getStringValueByKey(valueMap, USERINFO_MANAGEREMAIL)));
                swUserInfo.setManagerFirstname(getGracString(getStringValueByKey(valueMap, USERINFO_MANAGERFIRSTNAME)));
                swUserInfo.setManagerLastname(getGracString(getStringValueByKey(valueMap, USERINFO_MANAGERLASTNAME)));
                swUserInfo.setStartMenu(getGracString(getStringValueByKey(valueMap, USERINFO_STARTMENU)));
                swUserInfo.setLogonLang(getGracString(getStringValueByKey(valueMap, USERINFO_LOGONLANG)));
                swUserInfo.setDecNotation(getGracString(getStringValueByKey(valueMap, USERINFO_DECNOTATION)));
                swUserInfo.setDateFormat(getGracString(getStringValueByKey(valueMap, USERINFO_DATEFORMAT)));
                swUserInfo.setAlias(getGracString(getStringValueByKey(valueMap, USERINFO_ALIAS)));
                swUserInfo.setUserType(getGracString(getStringValueByKey(valueMap, USERINFO_USER)));
                userInfo.addItem(swUserInfo);
            }
            userAccessRequest.setUserInfo(userInfo);
        }
    }

    /**
     * Function fills the map with the provided value.
     * @param map - A map holds <String, Object>.
     * @param key - String key of map.
     * @param value - Value to be filled.
     */
    private void populateData(Map<String, Object> map, String key, String value) {
        map.put(key, value);
        if (log.isDebugEnabled()) {
            log.debug("Received " + key + " : " + value);
        }
    }

    /**
     * Function prepares the result map consisting of requested line items and their provisioning actions.
     * Every line item on GRC can have different status based on the approval done.
     * @param requestDetails Request Detail web service object.
     * @param requestDetailsMap Map holding request detail data.
     */
    private void extractRequestedItemDetails(GracSWsReqDetailsOp requestDetails, Map<String, Object> requestDetailsMap) {
        GracTWsRdOpReqitemdetails requestedItemsContainer = requestDetails.getRequestedItems();
        if ( null != requestedItemsContainer ) {
            GracSWsRdOpReqitemdetails[] requestedItemList = requestedItemsContainer.getItem();
            if (!Util.isEmpty(requestedItemList)) {
                List<Map<String, Object>> requestedItemsMap = new ArrayList<Map<String, Object>>();
                for (GracSWsRdOpReqitemdetails requestedItem : requestedItemList) {
                    Map<String, Object> reqItem = new HashMap<String, Object>();

                    functions.rfc.sap.document.sap_com.String gracItemId = requestedItem.getItemId();
                    if ( null != gracItemId ) {
                        String itemId = gracItemId.getString();
                        populateData(reqItem, REQUESTEDITEMS_ITEMID, itemId);
                    }

                    functions.rfc.sap.document.sap_com.String gracItemDesc = requestedItem.getItemDesc();
                    if ( null != gracItemDesc ) {
                        String itemDesc = gracItemDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_ITEMDESC, itemDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracConnector = requestedItem.getConnector();
                    if ( null != gracConnector ) {
                        String connector = gracConnector.getString();
                        populateData(reqItem, REQUESTEDITEMS_CONNECTOR, connector);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvItemType = requestedItem.getProvItemType();
                    if ( null != gracProvItemType ) {
                        String provItemType = gracProvItemType.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVITEMTYPE, provItemType);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvItemTypeDesc = requestedItem.getProvItemTypeDesc();
                    if ( null != gracProvItemTypeDesc ) {
                        String provItemTypeDesc = gracProvItemTypeDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVITEMTYPEDESC, provItemTypeDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvType = requestedItem.getProvType();
                    if ( null != gracProvType ) {
                        String provType = gracProvType.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVTYPE, provType);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvTypeDesc = requestedItem.getProvTypeDesc();
                    if ( null != gracProvTypeDesc ) {
                        String provTypeDesc = gracProvTypeDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVTYPEDESC, provTypeDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracAssignmentType = requestedItem.getAssignmentType();
                    if ( null != gracAssignmentType ) {
                        String assignmentType = gracAssignmentType.getString();
                        populateData(reqItem, REQUESTEDITEMS_ASSIGNMENTTYPE, assignmentType);
                    }

                    functions.rfc.sap.document.sap_com.String gracAssignmentTypeDesc = requestedItem.getAssignmentTypeDesc();
                    if ( null != gracAssignmentTypeDesc ) {
                        String assignmentTypeDesc = gracAssignmentTypeDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_ASSIGNMENTTYPEDESC, assignmentTypeDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvStatus = requestedItem.getProvStatus();
                    if ( null != gracProvStatus ) {
                        String provStatus = gracProvStatus.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVSTATUS, provStatus);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvStatusDesc = requestedItem.getProvStatusDesc();
                    if ( null != gracProvStatusDesc ) {
                        String provStatusDesc = gracProvStatusDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVSTATUSDESC, provStatusDesc);
                    }

                    functions.rfc.sap.document.sap_com.String graSstatus = requestedItem.getStatus();
                    if ( null != graSstatus ) {
                        String status = graSstatus.getString();
                        populateData(reqItem, REQUESTEDITEMS_STATUS, status);
                    }

                    functions.rfc.sap.document.sap_com.String gracApprovalStatus = requestedItem.getApprovalStatus();
                    if ( null != gracApprovalStatus ) {
                        String approvalStatus = gracApprovalStatus.getString();
                        populateData(reqItem, REQUESTEDITEMS_APPROVALSTATUS, approvalStatus);
                    }

                    functions.rfc.sap.document.sap_com.String gracApprovalStatusDesc = requestedItem.getApprovalStatusDesc();
                    if ( null != gracApprovalStatusDesc ) {
                        String approvalStatusDesc = gracApprovalStatusDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_APPROVALSTATUSDESC, approvalStatusDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvAction = requestedItem.getProvAction();
                    if ( null != gracProvAction ) {
                        String provAction = gracProvAction.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVACTION, provAction);
                    }

                    functions.rfc.sap.document.sap_com.String gracProvActionDesc = requestedItem.getProvActionDesc();
                    if ( null != gracProvActionDesc ) {
                        String provActionDesc = gracProvActionDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_PROVACTIONDESC, provActionDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracValidFrom = requestedItem.getValidFrom();
                    if ( null != gracValidFrom ) {
                        String validFrom = gracValidFrom.getString();
                        populateData(reqItem, REQUESTEDITEMS_VALIDFROM, validFrom);
                    }

                    functions.rfc.sap.document.sap_com.String gracValidTo = requestedItem.getValidTo();
                    if ( null != gracValidTo ) {
                        String validTo = gracValidTo.getString();
                        populateData(reqItem, REQUESTEDITEMS_VALIDTO, validTo);
                    }

                    functions.rfc.sap.document.sap_com.String gracComments = requestedItem.getComments();
                    if ( null != gracComments ) {
                        String comments = gracComments.getString();
                        populateData(reqItem, REQUESTEDITEMS_COMMENTS, comments);
                    }

                    functions.rfc.sap.document.sap_com.String gracOwners = requestedItem.getOwners();
                    if ( null != gracOwners ) {
                        String Owners = gracOwners.getString();
                        populateData(reqItem, REQUESTEDITEMS_OWNERS, Owners);
                    }

                    functions.rfc.sap.document.sap_com.String gracReqItemDesc = requestedItem.getReqItemDesc();
                    if ( null != gracReqItemDesc ) {
                        String reqItemDesc = gracReqItemDesc.getString();
                        populateData(reqItem, REQUESTEDITEMS_REQITEMDESC, reqItemDesc);
                    }

                    requestedItemsMap.add(reqItem);
                }
                requestDetailsMap.put(RESPONSE_REQUESTEDITEMS, requestedItemsMap);
            }
        }
    }

    /**
     * Function prepares the result map which includes the current stage at which the request is residing.
     * @param requestDetails Request Detail web service object.
     * @param requestDetailsMap Map holding request detail data.
     */
    private void extractCurrentStageDetails(GracSWsReqDetailsOp requestDetails, Map<String, Object> requestDetailsMap) {
        GracTWsRdOpReqPath requestPathContainer = requestDetails.getRequestPaths();
        if ( null != requestPathContainer ) {
            GracSWsRdOpReqPath[] reqPathItemList = requestPathContainer.getItem();
            if (!Util.isEmpty(reqPathItemList)) {
                List<Map<String, Object>> currentStageMap = new ArrayList<Map<String, Object>>();
                for (GracSWsRdOpReqPath requestedItem : reqPathItemList) {
                    Map<String, Object> reqItem = new HashMap<String, Object>();

                    functions.rfc.sap.document.sap_com.String gracCurstageName = requestedItem.getCurstageName();
                    if ( null != gracCurstageName ) {
                        String curstageName = gracCurstageName.getString();
                        populateData(reqItem, CURRENTSTAGE_CURSTAGENAME, curstageName);
                    }

                    functions.rfc.sap.document.sap_com.String gracCurstageStatus = requestedItem.getCurstageStatus();
                    if ( null != gracCurstageStatus ) {
                        String curstageStatus = gracCurstageStatus.getString();
                        populateData(reqItem, CURRENTSTAGE_CURSTAGESTATUS, curstageStatus);
                    }

                    functions.rfc.sap.document.sap_com.String gracCurstageDesc = requestedItem.getCurstageDesc();
                    if ( null != gracCurstageDesc ) {
                        String curstageDesc = gracCurstageDesc.getString();
                        populateData(reqItem, CURRENTSTAGE_CURSTAGEDESC, curstageDesc);
                    }

                    GracTWsRdOpApprovers curstageApproversContainer = requestedItem.getCurstageApprovers();
                    List<String> approverList = new ArrayList<String>();
                    if  ( null != curstageApproversContainer ) {
                        GracSWsRdOpApprovers[] approverItemList = curstageApproversContainer.getItem();
                        if (!Util.isEmpty(approverItemList)) {
                            for (GracSWsRdOpApprovers approver : approverItemList) {
                                functions.rfc.sap.document.sap_com.String gracUserId = approver.getUserid();
                                if ( null != gracUserId ) {
                                    String approverId = gracUserId.getString();
                                    if (log.isDebugEnabled()) {
                                        log.debug("Received approverId: " + approverId);
                                    }
                                    approverList.add(approverId);
                                }
                            }
                        }
                    }
                    if (!Util.isEmpty(approverList)) {
                        String approvers = StringUtils.join(approverList, DELIMETER_COMMA);
                        populateData(reqItem, CURRENTSTAGE_APPROVER, approvers);
                    }

                    currentStageMap.add(reqItem);
                }
                requestDetailsMap.put(RESPONSE_CURRENTSTAGE, currentStageMap);
            }
        }
    }

    /**
     * this function prepares a result map which consist of the risk analysis done in the approval stages
     * @param requestDetails Request Detail web service object.
     * @param requestDetailsMap Map holding request detail data.
     */
    private void extractRiskViolations(GracSWsReqDetailsOp requestDetails, Map<String, Object> requestDetailsMap) {
        GracTWsRaOpRiskData riskViolationData = requestDetails.getRiskViolationData();
        if ( null != riskViolationData ) {
            List<Map<String,Object>> riskViolationDataList = new ArrayList<Map<String,Object>>();
            GracSWsApiRiskAnlys[] riskAnalysisList = riskViolationData.getItem();
            if (!Util.isEmpty(riskAnalysisList)) {
                for ( GracSWsApiRiskAnlys riskAnalysis : riskAnalysisList ) {
                    Map<String,Object> riskViolationDataMap = new HashMap<String,Object>();

                    functions.rfc.sap.document.sap_com.String gracUserId = riskAnalysis.getUserId();
                    if ( null != gracUserId ) {
                        String userId = gracUserId.getString();
                        populateData(riskViolationDataMap, RISKVIOLATIONS_USERID, userId);
                    }

                    functions.rfc.sap.document.sap_com.String gracRiskId = riskAnalysis.getRiskId();
                    if ( null != gracRiskId ) {
                        String riskId = gracRiskId.getString();
                        populateData(riskViolationDataMap, RISKVIOLATIONS_RISKID, riskId);
                    }

                    functions.rfc.sap.document.sap_com.String gracRiskDesc = riskAnalysis.getRiskDesc();
                    if ( null != gracRiskDesc ) {
                        String riskDesc = gracRiskDesc.getString();
                        populateData(riskViolationDataMap, RISKVIOLATIONS_RISKDESC, riskDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracRiskLevelDesc = riskAnalysis.getRiskLevelDesc();
                    if ( null != gracRiskLevelDesc ) {
                        String riskLevelDesc = gracRiskLevelDesc.getString();
                        populateData(riskViolationDataMap, RISKVIOLATIONS_RISKLEVELDESC, riskLevelDesc);
                    }

                    functions.rfc.sap.document.sap_com.String gracRuleId = riskAnalysis.getRuleId();
                    if ( null != gracRuleId ) {
                        String ruleId = gracRuleId.getString();
                        populateData(riskViolationDataMap, RISKVIOLATIONS_RULEID, ruleId);
                    }

                    functions.rfc.sap.document.sap_com.String gracSystem = riskAnalysis.getSystem();
                    if ( null != gracSystem ) {
                        String system = gracSystem.getString();
                        populateData(riskViolationDataMap, RISKVIOLATIONS_SYSTEM, system);
                    }

                    GracTWsRdRole roleList = riskAnalysis.getRoleList();
                    GracSWsRdRole[] roleListContainer = roleList.getItem();
                    if (!Util.isEmpty(roleListContainer)) {
                        List<Map<String,Object>> rolesList = new ArrayList<Map<String,Object>>();
                        for ( GracSWsRdRole roleItem : roleListContainer ) {
                            Map<String,Object> rolesMap = new HashMap<String,Object>();

                            functions.rfc.sap.document.sap_com.String gracCompositRole = roleItem.getCompositRole();
                            if ( null != gracCompositRole ) {
                                String compositeRole = gracCompositRole.getString();
                                populateData(rolesMap, RISKVIOLATIONS_COMPOSITROLE, compositeRole);
                            }

                            functions.rfc.sap.document.sap_com.String gracRole = roleItem.getRole();
                            if ( null != gracRole ) {
                                String role = gracRole.getString();
                                populateData(rolesMap, RISKVIOLATIONS_ROLE, role);
                            }

                            rolesList.add(rolesMap);
                        }
                        riskViolationDataMap.put(RISKVIOLATIONS_ROLELIST, rolesList);
                    }

                    GracTWsRdAction actionData = riskAnalysis.getAction();
                    if ( null != actionData ) {
                        GracSWsRdAction[] actionItemList = actionData.getItem();
                        if (!Util.isEmpty(actionItemList)) {
                            List<String> actionList = new ArrayList<String>();
                            for ( GracSWsRdAction actionItem : actionItemList ) {
                                functions.rfc.sap.document.sap_com.String gracAction = actionItem.getAction();
                                if ( null != gracAction ) {
                                    String action = gracAction.getString();
                                    actionList.add(action);
                                    if (log.isDebugEnabled()) {
                                        log.debug("Received action: " + action);
                                    }
                                }
                            }
                            riskViolationDataMap.put(RISKVIOLATIONS_ACTION, actionList);
                        }
                    }
                    riskViolationDataList.add(riskViolationDataMap);
                }
            }
            requestDetailsMap.put(RESPONSE_RISKVIOLATIONS, riskViolationDataList);
        }
    }

    /**
     * Method checks the status of request executing at SAP GRC side.
     * and returns the result object containing the status and other fields.
     *
     * Based on the status of the request we proceed or terminate the IdentityIQ's 
     * access request for the same.
     * Some possible exit cases can be of following :-
     *
     * 1) Request is rejected
     * request status is FAILED.
     *
     * 2) Request is partially approved
     * request status is PARTIAL_OK.
     *
     * 3) Any change in the requested items
     * request status is OK but start_date, end_date, or provisioning action are different for the requested item
     * or any new roles are added or existing assignments are removed.
     *
     * 4) No role owner is assigned for the requested role.
     * request status is PENDING and current stage status is empty.
     *
     * 5) Request is forwarded from security stage to manager stage
     * request status is empty and current stage status is ERROR.
     *
     * @param wfc - The workflow context.
     * @return requestStatusMap Map holds response of the the user access request.
     * @throws GeneralException
     */
    public Map<String, Object> checkRequestDetails(WorkflowContext wfc) throws GeneralException {
        Map<String, Object> requestDetailsMap = new HashMap<String, Object>();
        try {

            if (log.isDebugEnabled()) {
                log.debug("Checking Request Details");
            }
            // Getting request number from the workflow context
            Attributes<String, Object> argList = wfc.getArguments();
            boolean isViolationFound = isViolationFound(argList);
            String requestNo = (String) argList.get(RESPONSE_REQUESTNUMBER);
            if (log.isInfoEnabled()) {
                log.info("Checking status of the request " + requestNo + " which is generated for account(s) : " + usersRequested);
            }
            if (Util.isNullOrEmpty(requestNo)) {
                if (log.isDebugEnabled()) {
                    log.debug("requestNumber can not be null or empty. Request Number is mandatory input in Request Details Web service.");
                }
                throw new GeneralException("requestNumber can not be null or empty. Request Number is mandatory input in Request Details Web service.");
            }


            // Adding request number in map.
            requestDetailsMap.put(RESPONSE_REQUESTNUMBER,requestNo);

            if ( isViolationFound ) {
                // firing a call to GRC Web service with the prepared request.
                GracIdmReqDetailsServicesResponse requestDetailsResponse = getRequestDetailResponse(wfc);

                if (log.isDebugEnabled()) {
                    log.debug("RequestDetails response : " + requestDetailsResponse);
                }
                // extracting result
                if ( null != requestDetailsResponse ) {
                    GracSWsApiMessage returnMessage = requestDetailsResponse.getMsgReturn();
                    if ( null != returnMessage ) {
                        Char3 msgNo = returnMessage.getMsgNo();
                        if ( null != msgNo ) {
                            String messageNo = msgNo.getChar3();
                            // 0 = SUCCESS  and  4 = ERROR
                            if ( messageNo.equals(MSG_CODE_SUCCESS)) {
                                GracSWsReqDetailsOp requestDetails = requestDetailsResponse.getRequestDetails();
                                if ( null != requestDetails ) {

                                    String requestStatus = null;
                                    // Getting Status
                                    functions.rfc.sap.document.sap_com.String gracRequestStatus = requestDetails.getRequestStatus();
                                    if ( null != gracRequestStatus ) {
                                        requestStatus = gracRequestStatus.getString();
                                        requestDetailsMap.put(RESPONSE_REQUESTSTATUS, requestStatus);
                                        if (log.isInfoEnabled()) {
                                            log.info("Received RequestStatus: " + requestStatus + " for account(s) : " + usersRequested );
                                        }
                                    }

                                    // Get details of at which stage the request is currently residing,
                                    extractCurrentStageDetails(requestDetails, requestDetailsMap);

                                    if (!requestStatus.equalsIgnoreCase(STATUS_PENDING)) {
                                        // Get Risk Violation Details if any risk analysis is performed
                                        extractRiskViolations(requestDetails, requestDetailsMap);

                                        // Get requested item details
                                        extractRequestedItemDetails(requestDetails, requestDetailsMap);
                                    }

                                }
                            } else {
                                if (log.isDebugEnabled()) {
                                    log.debug("Could not fetch the details of the request  " + requestNo);
                                }
                                String messageType = null, messageStatement = null;
                                Char7 msgTypeChar7 = returnMessage.getMsgType();
                                if ( null != msgTypeChar7) {
                                    messageType = msgTypeChar7.toString();
                                    requestDetailsMap.put(RESPONSE_MSGTYPE, messageType);
                                    if (log.isDebugEnabled()) {
                                        log.debug("Received MsgType: " + messageType);
                                    }
                                }
                                functions.rfc.sap.document.sap_com.String msgStatement = returnMessage.getMsgStatement();
                                if ( null != msgStatement) {
                                    messageStatement = msgStatement.toString();
                                    requestDetailsMap.put(RESPONSE_MSGSTATEMENT, messageStatement);
                                    if (log.isDebugEnabled()) {
                                        log.debug("Received MsgStatement: " + messageStatement);
                                    }
                                }
                                if (Util.isNotNullOrEmpty(messageStatement) && Util.isNotNullOrEmpty(messageType)) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Could not fetch the details of the request  " + requestNo + ". Message Type : " + messageType + ", Message Reason : " + messageStatement);
                                    }
                                    throw new GeneralException("Could not fetch the details of the request  " + requestNo + ". Message Type : " + messageType + ", Message Reason : " + messageStatement);
                                }
                                throw new GeneralException("Could not fetch the details of the request  " + requestNo);
                            }
                        }
                    }
                }
            } else {
                requestDetailsMap.put(RETRY_ERROR, RETRY_STATUS_FALSE);
            }
        } catch ( Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Error occurred while checking details of the request : " + ex.getMessage(), ex);
            }
            requestDetailsMap = retryError(wfc,ex);
        }
        if (log.isDebugEnabled()) {
            log.debug("Request Status Result : " + requestDetailsMap);
        }
        return requestDetailsMap;
    }


    /**
     * Function to initialize request detail stub and retun a handle of response.
     * @param wfc The workflow context.
     * @return  GracIdmReqDetailsServicesResponse the class containing response detail.
     * @throws GeneralException
     */
    public GracIdmReqDetailsServicesResponse getRequestDetailResponse(WorkflowContext wfc) throws GeneralException {
        GracIdmReqDetailsServicesResponse requestDetailsResponse = null;
        // A stub object for REQUEST_DETAILS web service.
        GRAC_REQUEST_DETAILS_WSStub requestDetailsserviceStub = null;
        // Request details webservice object which is used to poll the request status
        GracIdmReqDetailsServices requestDetailsService = null;
        Map<String, Object> headerInfo = null;

        try {
            Object requestStubDetailsObj = wfc.getVariable(REQUEST_STUB_DETAILS_MAP);
            Object parameterObject = null;
            Map<String, Object> requestStubDetailsMap = null;
            if (requestStubDetailsObj instanceof Map) {
                requestStubDetailsMap = (Map) requestStubDetailsObj;
                if (!Util.isEmpty(requestStubDetailsMap)) {
                    if (Util.isEmpty(headerInfo)) {
                        if (null != (parameterObject = (requestStubDetailsMap.get("headerInfo")))) {
                            headerInfo = (Map) parameterObject;
                        }
                    }

                    if (Util.isNullOrEmpty(usersRequested)) {
                        if (null != (parameterObject = (requestStubDetailsMap.get("usersRequested")))) {
                            usersRequested = (String) parameterObject;
                        }
                    }

                }
            }

            requestDetailsserviceStub = (GRAC_REQUEST_DETAILS_WSStub) SAPGRCConnector.prepareHeader(GRAC_REQUEST_DETAILS_WSStub.class, headerInfo);

            // prepare the request details web service
            requestDetailsService = new GracIdmReqDetailsServices();
            // setting language : <Language>String 1</Language>
            requestDetailsService.setLanguage(getGracString((String) wfc.getVariable(RESPONSE_LANGUAGE))); // No null check required for language at this place.
            // setting request number : <ReqNo>String 2</ReqNo>
            requestDetailsService.setRequestNumber(getGracString((String) wfc.getVariable(RESPONSE_REQUESTNUMBER))); // No null check required for request number at this place.

            // dumping request xml in the logs
            if (log.isDebugEnabled()) {
                printXml(requestDetailsService);
            }
            requestDetailsResponse = requestDetailsserviceStub.gracIdmReqDetailsServices(requestDetailsService);
        } catch (RemoteException e) {
            throw new GeneralException(e);
        } catch (ConnectorException e) {
            throw new GeneralException(e);
        }

        return requestDetailsResponse;
    }

    /**
     * Function to retry the error in Script.
     * @param wfc The workflow context.
     * @param ex Exception object.
     * @return requestStatusMap Response map holding details of the user access request.
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> retryError(WorkflowContext wfc, Exception ex) throws GeneralException {
        if (log.isInfoEnabled()) {
            log.info("Checking for retry for user(s) " + usersRequested);
        }
        boolean foundError = false;
        Object requestStatusObject = wfc.get(GRC_RESPONSE);
        String retriableErrors = wfc.getString(RETRIABLE_ERROS);
        String numberOfRetries = wfc.getString(NUMBER_OF_RETRIES);
        int retryCount = 0;
        Map<String, Object> requestStatusMap = null;
        String exceptionMessage = "Error occurred while checking details of the request : " + ex.getMessage();
        if (log.isInfoEnabled()) {
            log.info("Retriable Errors : " + retriableErrors + " Number Of Retries :" + numberOfRetries + " for user :" + usersRequested);
        }

        if (null != numberOfRetries)
            retryCount = Integer.parseInt(numberOfRetries.trim());
        String[] errorList = null;

        // Getting requstStatus Map present in workflow context
        if (null != requestStatusObject) {
            if (requestStatusObject instanceof Map) {
                requestStatusMap = (Map) requestStatusObject;
                requestStatusMap.remove(RESPONSE_REQUESTSTATUS);
                requestStatusMap.put(RETRY_ERROR, RETRY_STATUS_FALSE);
            }
        }
        if (null == requestStatusObject) {
            GeneralException exception = new GeneralException("Error: requestStatus Map is empty.");
            errorHandler(wfc, exception);
        }

        if (null != retriableErrors) {
            if (retryCount > 0) {
                errorList = retriableErrors.split(DELIMETER_COMMA);
                if (!Util.isEmpty(errorList)) {
                    for (String error : errorList) {
                        if (log.isInfoEnabled()) {
                            log.info("Error in list :  " + error + " Exception message : " + ex.getMessage() + " for user :" + usersRequested );
                        }
                        if (ex.getMessage().contains(error)) {
                            requestStatusMap.put(RETRY_ERROR, RETRY_STATUS_TRUE);
                            if (log.isInfoEnabled()) {
                                log.info("Retrying the operation for user(s) " + usersRequested);
                            }
                            foundError = true;
                            break;
                        }

                    }
                }
                if(!foundError) {
                    if (log.isInfoEnabled()) {
                        log.info("No retry Because error is not from 'retriableErrors' error list.");
                    }
                    GeneralException exception = new GeneralException("Error : " + ex.getMessage());
                    errorHandler(wfc, exception);
                }
                else {
                    retryCount--;
                    numberOfRetries = STRING_EMPTY + retryCount;
                    wfc.setVariable(NUMBER_OF_RETRIES, numberOfRetries);
                    if (log.isInfoEnabled()) {
                        log.info("Current retry count " + retryCount + " for user " + usersRequested);
                    }
                }

            } else {
                StringBuilder error = new StringBuilder();
                error.append("All retry attempts has been made for user: ");
                error.append(usersRequested);
                error.append(". Exiting the workflow with exception ");
                error.append(ex.getMessage());
                if (log.isInfoEnabled()) {
                    log.info(error.toString());
                }
                GeneralException exception = new GeneralException(error.toString());
                errorHandler(wfc, exception);
            }
        } else {
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        return requestStatusMap;
    }

    /**
     * Function updates the identityRequest page's current step.
     * with the message Pending on SAP GRC with request number.
     * @param wfc The workflow context.
     * @param requestNo User Access Request number.
     * @throws GeneralException
     */
    public void updateCurrentStep(WorkflowContext wfc, String requestNo) throws GeneralException {
        String currState = "Pending in SAP GRC.";

        //A list of message containing only one message that is the access request id of the SAP GRC request.
        List<Message> msgList = new ArrayList<Message>();
        Message msg = new Message(MessageKeys.WORKFLOW_GRC_MSG_REQUEST_ID, requestNo);

        if (log.isInfoEnabled()) {
            log.info(msg.getLocalizedMessage());
        }

        // get the identity Request and update the current GRC state 
        SailPointContext ctx = wfc.getSailPointContext();
        IdentityRequest identityRequest = IdentityRequestLibrary.getIdentityRequest( wfc );
        if ( null == identityRequest ) {
            throw new GeneralException("Identity Request is null");
        }

        //Adding request Id in Info message of Identity request
        msgList.add(msg);
        identityRequest.addMessages(msgList);

        // save the context
        identityRequest.setState(currState);
        identityRequest.computeHasMessages();
        ctx.saveObject(identityRequest);
        ctx.commitTransaction();
    }

    /**
     * Function dumps the xml representation of a request in the logs when set to debug level.
     * @param request GRAC request formed to be sent to SAP GRC server.
     */
    private void printXml(Object request) {
        try {
            OMFactory factory = OMAbstractFactory.getOMFactory();
            String streamReader = null;
            if ( request instanceof GracIdmUsrAccsReqServices) {
                log.debug("GracIdmUsrAccsReqServices Request XML :");
                streamReader = ((GracIdmUsrAccsReqServices)request).getOMElement( new QName("GracIdmUsrAccsReqServices"), factory).toStringWithConsume();
            } else if (request instanceof GracIdmReqDetailsServices) {
                log.debug("GracIdmReqDetailsServices Request XML :");
                streamReader = ((GracIdmReqDetailsServices)request).getOMElement( new QName("GracIdmReqDetailsServices"), factory).toStringWithConsume();
            } else if (request instanceof GracIdmInboundAuditLogs) {
                log.debug("GracIdmInboundAuditLogs Request XML :");
                streamReader = ((GracIdmInboundAuditLogs)request).getOMElement( new QName("GracIdmInboundAuditLogs"), factory).toStringWithConsume();
            } else if (request instanceof GracIdmRiskWoutNoServices) {
                log.debug("GracIdmRiskWoutNoServices Request XML :");
                streamReader = ((GracIdmRiskWoutNoServices)request).getOMElement( new QName("GracIdmRiskWoutNoServices"), factory).toStringWithConsume();
            }
            Transformer serializer= SAXTransformerFactory.newInstance().newTransformer();
            serializer.setOutputProperty(OutputKeys.INDENT, INDENT_YES);
            //serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", INDENT_PROP);
            //serializer.setOutputProperty("{http://xml.customer.org/xslt}indent-amount", "2");
            Source xmlSource = new SAXSource( new InputSource( new ByteArrayInputStream(streamReader.getBytes())));
            StreamResult res = new StreamResult( new ByteArrayOutputStream());            
            serializer.transform(xmlSource, res);
            log.debug(new String(((ByteArrayOutputStream)res.getOutputStream()).toByteArray()));
        } catch ( Exception e ) {
            log.debug("Exception in parsing the request xml : " + e);
        }
    }

    /*
     * Library functions for SAP GRC DATA Generator Subprocess 
     */

    /**
     * Function to get PollingInterval in "SAP GRC Request executor" workflow.
     * @param wfc The workflow context.
     * @return String Polling interval.
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String getSAPGRCPollingInterval(WorkflowContext wfc) throws GeneralException {
        String pollingInterval = null;
        Map<String, String> credentialsMap = null;
        Object credentialObject = wfc.get(REQUEST_CREDENTIALS_MAP);
        if(null != credentialObject) {
            if(credentialObject instanceof Map) {
                credentialsMap = (Map<String, String>) credentialObject;
                pollingInterval = credentialsMap.get(WORKFLOW_POLLING_INTERVAL);
                if (Util.isNullOrEmpty(pollingInterval)) {
                    pollingInterval = DEFAULT_APPL_POLLING_INTERVAL;
                }
            }
            if(log.isDebugEnabled()) {
                log.debug("Credential map in getSAPGRCPollingInterval is "+credentialsMap);
                log.debug("Polling Interval in credential Map is " + pollingInterval);
            }
        }

        return pollingInterval;
    }

    /**
     * Function to find whether SAP application has GRC enabled or not.
     * @param appName Application to be checked.
     * @param context Sailpoint Context.
     * @return Boolean value saying if application is SAP GRC enabled or not.
     * @throws GeneralException
     */
    private boolean isApplicationGRCEnabled(String appName, SailPointContext context) throws GeneralException {
        Application app = context.getObjectByName(Application.class, appName);
        boolean flag = false;
        if (null != app) {
            flag = app.getBooleanAttributeValue(IS_GRC_ENABLED);
        }
        return flag;
    }

    /**
     * Function to find whether the application type is "SAP - Direct" or "SAP Portal".
     * @param application Application to be checked.
     * @param context Sailpoint Context.
     * @return Boolean value saying if application is of type SAP Direct or not.
     * @throws Exception
     */
    public boolean isSAPDirectApplication(String application, SailPointContext context) throws GeneralException {
        Application app = null;
        boolean isSapApplication = false;
        Object applicationObject = context.getObjectByName(Application.class, application);
        if (null != applicationObject) {
            app = (Application) applicationObject;
            if (app.getType().equals(SAP_DIRECT))
                isSapApplication = true;
            else if (app.getType().equals(SAP_PORTAL)) {
                isSapApplication = true;
            }
        }
        return isSapApplication;

    }

    /**
     * Function to find whether the application type is "SAP - Direct" or "SAP
     * Portal".
     *
     * @param application
     *            Application to be checked.
     * @param context
     *            Sailpoint Context.
     * @return Boolean value saying if application is of type SAP Direct or not.
     * @throws Exception
     */
    public boolean isSAPGRCApplication(String application,
                                        SailPointContext context)
        throws GeneralException {
        Application app = null;
        boolean isSapGRCApplication = false;
        Object applicationObject = context.getObjectByName(Application.class,
                application);
        if (null != applicationObject) {
            app = (Application) applicationObject;
            if (app.getType().equals(SAP_GRC)) {
                isSapGRCApplication = true;
            }
        }
        return isSapGRCApplication;

    }

    /**
     * Function to get GRC connection name present in Application configuration page.
     * @param appName Application to be checked.
     * @param context  Sailpoint Context.
     * @return SAP GRC Connector name.
     * @throws GeneralException
     */
    public String getGRCConnectorName(String appName, SailPointContext context) throws GeneralException {
        Application app = (Application) context.getObjectByName(Application.class, appName);
        String grcConnectorName = null;
        if (null != app) {
            grcConnectorName = (String) app.getAttributeValue(GRC_CONN_NAME);
        }
        if (log.isDebugEnabled()) {
            log.debug("GRC connector name of application " + app + " is " + grcConnectorName);
        }
        return grcConnectorName;

    }

    /**
     * Function to filter all account requests which belong to SAP applications.
     * @param wfc Workflow Context.
     * @return List of account requests for application of type SAP Direct.
     * @throws GeneralException
     */
    public List<AccountRequest> filterAccountRequestSAPGRC(WorkflowContext wfc) throws GeneralException {
        List<AccountRequest> grcAccountRequest = new ArrayList<AccountRequest>();
        boolean isGRCEnabledSAPDirectApp = false;
        boolean isSAPGRCApplication = false;
        try {
            SailPointContext spContext = wfc.getSailPointContext();
            ProvisioningProject project = (ProvisioningProject) wfc.get(IdentityLibrary.VAR_PROJECT);
            List<ProvisioningPlan> grcPlan = new ArrayList<ProvisioningPlan>();
            List<ProvisioningPlan> planList = project.getPlans();
            if(null != planList) {
                for (ProvisioningPlan plan : planList) {
                    String targetIntegration = plan.getTargetIntegration();
                    if (null != targetIntegration) {
                        if (isSAPGRCApplication(targetIntegration, spContext) &&
                                !Util.isEmpty(plan.getAccountRequests())) {
                            isSAPGRCApplication = true;
                        }
                        if (isSAPDirectApplication(targetIntegration, spContext)) {
                            if (isApplicationGRCEnabled(targetIntegration, spContext)) {
                                if (log.isDebugEnabled())
                                     log.debug("provisioning plan is " + ProvisioningPlan.getLoggingPlan(plan).toXml());
                                grcPlan.add(plan);
                                List<AccountRequest> accReqList = plan.getAccountRequests();
                                if (!Util.isEmpty(accReqList)) {
                                    isGRCEnabledSAPDirectApp = true;
                                }
                                for (AccountRequest accreq : accReqList) {
                                    grcAccountRequest.add(accreq);
                                }
                            }
                        }

                    }
                }
                if (isSAPGRCApplication && isGRCEnabledSAPDirectApp) {
                    String errorMessage = "Unable to process this request," +
                            "Ensure that separate requests are placed for SAP GRC Application and " +
                            "GRC enabled SAP Direct application.";
                    log.error(errorMessage);
                    throw new GeneralException(errorMessage);
                }
            }
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage());
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        /*
         * Workflow variable accountRequestSAPGRC is used in step Initialize Detail Map
         * to fetch the SAP GRC Request.
         */
        wfc.setVariable(SAPGRCACCOUNTREQUEST, grcAccountRequest);
        return grcAccountRequest;
    }

    /**
     * Function to fetch expansion items of business roles
     *
     * @param wfc
     *            Workflow Context.
     * @return Map of entitlements present in business role with key as
     *         businessRole+grcDelimiter+ApplicationName Ex: Expansion items in
     *         provisioning project are as follows: <ExpansionItems>
     *         <ExpansionItem application="sap direct 25" cause="Role" name=
     *         "Roles" nativeIdentity="ARUN" operation="Add" sourceInfo="sap
     *         busi role" value="HR_CRITICAL_ACTION"/>
     *         <ExpansionItem application="sap direct 25" cause="Role" name=
     *         "Roles" nativeIdentity="ARUN" operation="Add" sourceInfo="sap
     *         busi role" value="HR_CRITICAL_ACTION1"/> </ExpansionItems> Method
     *         returns the map as follows: {sap busi rolegrcDelimitersap direct
     *         25grcDelimiterATOZER=[HR_CRITICAL_ACTION, HR_CRITICAL_ACTION1]}
     *         "grcDelimiter" is a custom delimiter used to separate
     *         businessRoleName and ApplicationName
     * @throws GeneralException
     */
    public Map getRoleExpansionItems(WorkflowContext wfc)
        throws GeneralException {
        Map<String,List> businessRoleExpansion = new HashMap<String,List>();
        try {
            ProvisioningProject project = (ProvisioningProject) wfc
                    .get(IdentityLibrary.VAR_PROJECT);
            List<ExpansionItem> expansionItems = project.getExpansionItems();
            List<String> entitlementList = null;
            for (ExpansionItem expansionItem : Util.iterate(expansionItems)) {
                if (expansionItem.getCause().toString()
                        .equalsIgnoreCase(ROLE)) {
                    String busiRole = expansionItem.getSourceInfo();
                    String entitlement = (String) expansionItem.getValue();
                    String applicationName = expansionItem.getApplication();
                    if (businessRoleExpansion.containsKey(
                            busiRole + GRCDELIMITER + applicationName)) {
                        entitlementList.add(entitlement);
                        businessRoleExpansion.put(
                                busiRole + GRCDELIMITER + applicationName,
                                entitlementList);
                    } else {
                        entitlementList = new ArrayList<String>();
                        entitlementList.add(entitlement);
                        businessRoleExpansion.put(
                                busiRole + GRCDELIMITER + applicationName,
                                entitlementList);
                    }
                }
            }
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage());
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        return businessRoleExpansion;
    }

    /**
     * Function to fetch the sunrise and sunset date of business roles
     * @param wfc Workflow Context.
     * @return
     *         Map of dates assigned to a business role with key
     *         as businessRole+startDate for sunrise date and
     *         businessRole+endDate for sunset date
     *         Ex:
     *         Sunrise and Sunset dates of business roles are as follows:
     *             <AttributeRequest assignmentId="6a33a0f23ac546aa9680be867025c5d2" name="assignedRoles" op="Add" value="sap busi role">
     *                <Attributes>
     *                 <Map>
     *                  <entry key="addDate">
     *                    <value>
     *                      <Date>1566974690</Date>
     *                    </value>
     *                  </entry>
     *                   <entry key="removeDate">
     *                    <value>
     *                      <Date>1567061090</Date>
     *                   </value>
     *                 </entry>
     *                </Map>
     *               </Attributes>
     *             </AttributeRequest>
     *         Method returns the map as follows:
     *           roleDates {sap busi roleremoveDate=Thu Aug 29 00:00:00 IST 2019, sap busi roleaddDate=Wed Aug 28 00:00:00 IST 2019}
     * @throws GeneralException
     */
    public Map getBusinessRoleDates(WorkflowContext wfc)
        throws GeneralException {
        Map roleDates = new HashMap();
        try {
            ProvisioningProject project = (ProvisioningProject) wfc
                    .get(IdentityLibrary.VAR_PROJECT);
            for (ProvisioningPlan plan : Util.iterate(project.getPlans())) {
                for (AccountRequest accReq : Util
                        .iterate(plan.getAccountRequests())) {
                    for (ProvisioningPlan.AttributeRequest attReq : Util
                            .iterate(accReq.getAttributeRequests())) {
                        String attrName = attReq.getName();
                        if (BUSINESS_ROLE_ASSIGNMENT
                                .equalsIgnoreCase(attrName)) {
                            String atrValue = (String) attReq.getValue();
                            if (attReq.getArguments() != null) {
                                Date arg_startDate = attReq.getArguments()
                                        .getDate(SUNRISE_DATE);
                                Date arg_endDate = attReq.getArguments()
                                        .getDate(SUNSET_DATE);
                                roleDates.put(atrValue + SUNRISE_DATE,
                                        arg_startDate);
                                roleDates.put(atrValue + SUNSET_DATE,
                                        arg_endDate);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage());
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        return roleDates;
    }

    /**
     * Function to check if the business role and directly assigned entitlement
     * have different start and end dates.
     *
     * @param businessRoleMap
     *            a map containing the entitlemts and corresponding business
     *            roles
     * @param entitlement
     *            name of the entitlemnt whos dates needs to be campared
     * @param attrReq
     *            attribute request to fetch the arguments.
     * @param roleDates
     *            a map containing business roles with start and end date
     * @return businessRoleList : list of business roles where dates of
     *         businessrole and entitlement are different
     * @throws GeneralException
     */
    public List<String> getBusinessRolesMismachedDates(Map<String,List> businessRoleMap,
                                                       String entitlement,
                                                       sailpoint.object.ProvisioningPlan.AttributeRequest attrReq,
                                                       Map roleDates)
        throws GeneralException {
        List businessRoleList = new ArrayList<>();
        Date startDate, endDate;
        boolean isDatesMismach = false;
        if (businessRoleMap.containsKey(entitlement)) {
            List<String> businessRoles = businessRoleMap.get(entitlement);
            if (attrReq.getArguments() != null) {
                Date sunriseDate = attrReq.getArguments().getDate(SUNRISE_DATE);
                Date sunsetDate = attrReq.getArguments().getDate(SUNSET_DATE);
                for (String businessRole : businessRoles) {
                    isDatesMismach = isDateDifferent(businessRole, sunriseDate,
                            sunsetDate, roleDates);
                    if (isDatesMismach) {
                        businessRoleList.add(businessRole);
                    }
                }
            }
        }
        return businessRoleList;
    }

    /**
     * Function to check if the business role and directly assigned entitlement
     * have different start and end dates.
     *
     * @param wfc
     *            Workflow Context.
     * @param businessRoleMap
     *            a map containing the entitlemts and corresponding business
     *            roles
     * @param roleDates
     *            a map containing business roles with start and end date
     * @return void
     * @throws GeneralException
     */
    public void checkEntitlemtDates(WorkflowContext wfc,
                                    Map<String,List> businessRoleMap,
                                    Map roleDates)
        throws GeneralException {
        try {
            List<String> businessRoleListAll = new ArrayList<>();
            List<String> entitlementListAll = new ArrayList<>();
            List<AccountRequest> accReqList = filterAccountRequestSAPGRC(wfc);
            for (AccountRequest accReq : Util.iterate(accReqList)) {
                List<sailpoint.object.ProvisioningPlan.AttributeRequest> attrReqList = accReq
                        .getAttributeRequests();
                for (sailpoint.object.ProvisioningPlan.AttributeRequest attrReq : Util
                        .iterate(attrReqList)) {
                    if (attrReq != null) {
                        Object entitlementNameObject = attrReq.getValue();
                        if (entitlementNameObject instanceof String) {
                            String entitlement = (String) entitlementNameObject;
                            List businessRoleList = getBusinessRolesMismachedDates(
                                    businessRoleMap, entitlement, attrReq,
                                    roleDates);
                            if (!Util.isEmpty(businessRoleList)) {
                                if (!businessRoleListAll
                                        .containsAll(businessRoleList)) {
                                    businessRoleListAll
                                            .addAll(businessRoleList);
                                }
                                if (!entitlementListAll.contains(entitlement)) {
                                    entitlementListAll.add(entitlement);
                                }
                            }
                        } else if (entitlementNameObject instanceof List) {
                            List<String> entitlementList = (List) entitlementNameObject;
                            for (String entitlement : Util
                                    .iterate(entitlementList)) {
                                List businessRoleList = getBusinessRolesMismachedDates(
                                        businessRoleMap, entitlement, attrReq,
                                        roleDates);
                                if (!Util.isEmpty(businessRoleList)) {
                                    if (!businessRoleListAll
                                            .containsAll(businessRoleList)) {
                                        businessRoleListAll
                                                .addAll(businessRoleList);
                                    }
                                    if (!entitlementListAll
                                            .contains(entitlement)) {
                                        entitlementListAll.add(entitlement);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!Util.isEmpty(entitlementListAll)) {
                throw new Exception("Business Roles " + businessRoleListAll +
                        " with Common Entitlement " + entitlementListAll +
                        " have Different Sunrise and Sunset Dates ");
            }

        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage());
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
    }

    /**
     * Function to fetch a map of sap entitlements and business roles
     * @param wfc Workflow Context.
     * @param expansionItems businessroles and its entitlements.
     * @param roleDates map of business roles and dastes assigned to it
     * @return List of account requests for application of type SAP Direct.
     *         Ex:
     *         Expansion items in provisioning project are as follows:
     *          <ExpansionItems>
     *                <ExpansionItem application="sap direct" cause="Role" name="Roles" operation="Add" sourceInfo="SAP_BUSINESS_ROLE" value="SAP_Developer"/>
     *                <ExpansionItem application="sap direct" cause="Role" name="Roles" operation="Add" sourceInfo="SAP_BUSINESS_ROLE" value="/IPRO/AUTHOR"/>
     *                <ExpansionItem application="sap direct" cause="Role" name="Roles" operation="Add" sourceInfo="SAP_BUSINESS_ROLE_2" value="SAP_Developer"/>
     *                <ExpansionItem application="sap direct" cause="Role" name="Roles" operation="Add" sourceInfo="SAP_BUSINESS_ROLE_2" value="SAP_QA"/>
     *         </ExpansionItems>
     *          Method returns the map as follows:
     *          {SAP_Developer=[SAP_BUSINESS_ROLE, SAP_BUSINESS_ROLE_2],/IPRO/AUTHO=[SAP_BUSINESS_ROLE],SAP_Developer=[SAP_BUSINESS_ROLE_2]}
     *
     * @throws GeneralException
     */
    public Map<String,List> getSAPEntlBusinessRoleMap(WorkflowContext wfc,
                                                      List<ExpansionItem> expansionItems,
                                                      Map roleDates)
        throws GeneralException {
        Map<String,List> businessRoleMap = new HashMap<String,List>();
        boolean isDatesMismach = false;
        List<String> commonEntitlements = new ArrayList<>();
        try {
            SailPointContext spContext = wfc.getSailPointContext();
            for (ExpansionItem item : Util.iterate(expansionItems)) {
                if (item.getCause().toString().equalsIgnoreCase(ROLE)) {
                    String applicationName = item.getApplication();
                    if (isSAPDirectApplication(applicationName, spContext)) {
                        if (isApplicationGRCEnabled(applicationName,
                                spContext)) {
                            String roleName = item.getSourceInfo();
                            String entitlementName = (String) item.getValue();
                            if (businessRoleMap.containsKey(entitlementName)) {
                                isDatesMismach = isDateDifferent(
                                        businessRoleMap, entitlementName,
                                        roleDates, roleName);
                                if (isDatesMismach && !commonEntitlements
                                        .contains(entitlementName)) {
                                    commonEntitlements.add(entitlementName);
                                }
                                // add this role to the entitlement map
                                List<String> businessRoles = businessRoleMap
                                        .get(entitlementName);
                                businessRoles.add(roleName);
                                businessRoleMap.put(entitlementName,
                                        businessRoles);
                            } else {
                                List<String> businessRoleList = new ArrayList<String>();
                                businessRoleList.add(roleName);
                                businessRoleMap.put(entitlementName,
                                        businessRoleList);
                            }
                        }
                    }
                }
            }
            if (!Util.isEmpty(commonEntitlements)) {
                List<String> businessRoleList = new ArrayList<String>();
                for (String entitlement : Util.iterate(commonEntitlements)) {
                    businessRoleList = businessRoleMap.get(entitlement);
                }
                throw new Exception("Business Roles " + businessRoleList +
                        " with Common Entitlement " + commonEntitlements +
                        " have Different Sunrise and Sunset Dates ");
            }
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage());
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        return businessRoleMap;

    }

    /**
     * Function to compare the dates of common entilements in the cart
     *
     * @param wfc
     *            workflow context
     * @return businessRoleMap Map of entitlement and business role.
     *
     * @throws GeneralException
     */
    public Map getBusinessRoleChckngCommonEntl(WorkflowContext wfc)
        throws GeneralException {
        Map<String,List> businessRoleMap = new HashMap<String,List>();
        try {
            ProvisioningProject project = (ProvisioningProject) wfc
                    .get(IdentityLibrary.VAR_PROJECT);
            List<ExpansionItem> expansionItems = project.getExpansionItems();
            // fetching the dates assigned to a business role
            Map roleDates = getBusinessRoleDates(wfc);
            // fetching a map of entitlements and respective business roles
            // if the business roles with common entitlement have different
            // dates the request is failed.
            businessRoleMap = getSAPEntlBusinessRoleMap(wfc, expansionItems,
                    roleDates);
            // comparing the dates of common entitlement and business roles
            checkEntitlemtDates(wfc, businessRoleMap, roleDates);

        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage());
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        return businessRoleMap;
    }

    /**
     * Method to get the effective sap role start date and end date. It uses IIQ
     * sunrise and sunset date to calculate the same. In case there is no
     * sunrise and sunset date it put null value.
     *
     * @param wfc
     * @return map which contains key as an entitlementName and value as
     *         startDate/endDate.
     * @throws GeneralException
     */
    public Map<String,String> getEffectiveStartDateEndDates(WorkflowContext wfc)
        throws GeneralException {
        Map<String,String> sapRoleDates = new HashMap<String,String>();
        try {
            Date sunriseDate = null;
            Date sunsetDate = null;
            ProvisioningProject project = (ProvisioningProject) wfc
                    .get(IdentityLibrary.VAR_PROJECT);
            SailPointContext context = wfc.getSailPointContext();
            // Get the roleDates
            Map roleDates = getBusinessRoleDates(wfc);
            log.debug("roleDates= " + roleDates);
            // get the entilementBusinessRoleMap
            Map entilementBusinessRoleMap = getBusinessRoleChckngCommonEntl(
                    wfc);
            log.debug(
                    "entilementBusinessRoleMap= " + entilementBusinessRoleMap);
            for (ProvisioningPlan plan : Util.iterate(project.getPlans())) {
                log.info("Plan is = " + plan.toXml());
                // Process each account request
                for (AccountRequest accReq : Util
                        .iterate(plan.getAccountRequests())) {
                    String applicationName = accReq.getApplication();
                    // Test only for SAP Application
                    if (isSAPDirectApplication(applicationName, context)) {
                        for (ProvisioningPlan.AttributeRequest attReq : Util
                                .iterate(accReq.getAttributeRequests())) {
                            String attrName = attReq.getName();
                            // This is for Direct entitlement request
                            if (!BUSINESS_ROLE_ASSIGNMENT
                                    .equalsIgnoreCase(attrName)) {
                                String startDate = null;
                                String endDate = null;
                                // Entitlement name
                                Object entitlementNameObj = attReq.getValue();
                                if (entitlementNameObj instanceof String) {
                                    String entitlementName = (String) entitlementNameObj;
                                    log.debug("entitlementName is = " +
                                            entitlementName);
                                    if (!Util.isEmpty(
                                            entilementBusinessRoleMap) &&
                                            entilementBusinessRoleMap
                                                    .containsKey(
                                                            entitlementName)) {
                                        log.debug(
                                                "Entitlement present in the business role");
                                        // This entitlement is associated with
                                        // Business Role (BR)
                                        // Get it's associated Business Roles
                                        List businessRoles = (List) entilementBusinessRoleMap
                                                .get(entitlementName);
                                        String businessRole = (String) businessRoles
                                                .get(0);
                                        log.debug("businessRole is " +
                                                businessRole);
                                        sunriseDate = (Date) roleDates.get(
                                                businessRole + SUNRISE_DATE);
                                        sunsetDate = (Date) roleDates.get(
                                                businessRole + SUNSET_DATE);
                                        log.debug("sunriseDate is " +
                                                sunriseDate +
                                                " sunsetDate is " + sunsetDate);
                                    } else {
                                        log.debug("attReq.getArguments()" +
                                                attReq.getArguments());
                                        if (attReq.getArguments() != null) {
                                            sunriseDate = attReq.getArguments()
                                                    .getDate(SUNRISE_DATE);
                                            sunsetDate = attReq.getArguments()
                                                    .getDate(SUNSET_DATE);
                                            log.debug("sunriseDate is " +
                                                    sunriseDate +
                                                    " sunsetDate is " +
                                                    sunsetDate);
                                        }
                                    }
                                    // get the effectiveDates
                                    Map<String,String> effectiveDateMap = getEffectiveDates(
                                            sunriseDate, sunsetDate);
                                    if (!Util.isEmpty(effectiveDateMap)) {
                                        startDate = effectiveDateMap
                                                .get(SUNRISE_DATE);
                                        endDate = effectiveDateMap
                                                .get(SUNSET_DATE);
                                    }
                                    log.debug("Effective for entitlementName " +
                                            entitlementName + ": startDate= " +
                                            startDate + "endDate= " + endDate);
                                    sapRoleDates.put(
                                            entitlementName + SUNRISE_DATE,
                                            startDate);
                                    sapRoleDates.put(
                                            entitlementName + SUNSET_DATE,
                                            endDate);

                                } else if (entitlementNameObj instanceof List) {
                                    List<String> entitlementList = (List<String>) entitlementNameObj;
                                    for (String entitlementName : entitlementList) {
                                        log.debug("entitlementName is = " +
                                                entitlementName);

                                        if (!Util.isEmpty(
                                                entilementBusinessRoleMap) &&
                                                entilementBusinessRoleMap
                                                        .containsKey(
                                                                entitlementName)) {
                                            log.debug(
                                                    "Entitlement present in the business role");
                                            // This entitlement is associated
                                            // with Business Role (BR)
                                            // Get it's associated Business
                                            // Roles
                                            List businessRoles = (List) entilementBusinessRoleMap
                                                    .get(entitlementName);
                                            String businessRole = (String) businessRoles
                                                    .get(0);
                                            log.debug("businessRole is " +
                                                    businessRole);
                                            sunriseDate = (Date) roleDates
                                                    .get(businessRole +
                                                            SUNRISE_DATE);
                                            sunsetDate = (Date) roleDates.get(
                                                    businessRole + SUNSET_DATE);
                                            log.debug("sunriseDate is " +
                                                    sunriseDate +
                                                    " sunsetDate is " +
                                                    sunsetDate);
                                        } else {
                                            log.debug("attReq.getArguments()" +
                                                    attReq.getArguments());
                                            if (attReq.getArguments() != null) {
                                                sunriseDate = attReq
                                                        .getArguments()
                                                        .getDate(SUNRISE_DATE);
                                                sunsetDate = attReq
                                                        .getArguments()
                                                        .getDate(SUNSET_DATE);
                                            }
                                        }
                                        // get the effectiveDates
                                        Map<String,String> effectiveDateMap = getEffectiveDates(
                                                sunriseDate, sunsetDate);
                                        if (!Util.isEmpty(effectiveDateMap)) {
                                            startDate = effectiveDateMap
                                                    .get(SUNRISE_DATE);
                                            endDate = effectiveDateMap
                                                    .get(SUNSET_DATE);
                                        }
                                        log.debug(
                                                "Effective for entitlementName " +
                                                        entitlementName +
                                                        ": startDate= " +
                                                        startDate +
                                                        "endDate= " + endDate);
                                        sapRoleDates.put(
                                                entitlementName + SUNRISE_DATE,
                                                startDate);
                                        sapRoleDates.put(
                                                entitlementName + SUNSET_DATE,
                                                endDate);

                                    }
                                }
                            }
                        }
                    }
                }
            }
            log.debug("sapRoleDates = " + sapRoleDates);
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error(ex.getMessage());
            }
            GeneralException exception = new GeneralException(ex);
            errorHandler(wfc, exception);
        }
        return sapRoleDates;
    }

    /**
     * Method to get the effective dates if either sunrise or sunset dates are
     * provided
     *
     * @param sunriseDate
     *            start date of the entitlement
     * @param sunsetDate
     *            end date of the entitlement
     * @return Map of start and end dates.
     */
    public Map<String,String> getEffectiveDates(Date sunriseDate,
                                                Date sunsetDate) {
        Map<String,String> datesMap = null;
        String startDate = null, endDate = null;
        if (sunriseDate != null || sunsetDate != null) {
            datesMap = new HashMap<String,String>();
            if (sunriseDate != null) {
                startDate = Util.dateToString(sunriseDate,
                        GRCRequestDateFormatString);
            } else {
                startDate = defaultStartDate;
            }
            if (sunsetDate != null) {
                endDate = Util.dateToString(sunsetDate,
                        GRCRequestDateFormatString);
            } else {
                endDate = defaultEndDate;
            }
            datesMap.put(SUNRISE_DATE, startDate);
            datesMap.put(SUNSET_DATE, endDate);
        }
        return datesMap;
    }

    /**
     * Function to check if dates of business roles are different
     *
     * @param businessRoleMap
     *            Map of entilement and business role. entitlementName key of
     *            the map roleDate map containing the businessroles and dates
     *            businessRoleName business role name
     * @return true if the dates of the business roles with common entitlement
     *         is different false if the dates of the business roles with common
     *         entitlement is same
     *
     * @throws GeneralException
     */
    private boolean isDateDifferent(Map<String,List> businessRoleMap,
                                    String entitlementName, Map roleDate,
                                    String businessRoleName) {

        List<String> businessRoles = (List) businessRoleMap
                .get(entitlementName);
        Date sunRiseDate, sunSetDate;
        Date currentRiseDate = (Date) roleDate
                .get(businessRoleName + SUNRISE_DATE);
        Date currentSunSetDate = (Date) roleDate
                .get(businessRoleName + SUNSET_DATE);
        for (String businessRole : businessRoles) {
            sunRiseDate = (Date) roleDate.get(businessRole + SUNRISE_DATE);
            sunSetDate = (Date) roleDate.get(businessRole + SUNSET_DATE);
            if (((currentRiseDate != null && sunRiseDate == null) ||
                    (currentSunSetDate != null && sunSetDate == null)) ||
                    ((sunRiseDate != null && currentRiseDate == null) ||
                            (sunSetDate != null &&
                                    currentSunSetDate == null)) ||
                    ((sunRiseDate != null &&
                            !sunRiseDate.equals(currentRiseDate)) ||
                            (sunSetDate != null &&
                                    !sunSetDate.equals(currentSunSetDate)))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Function to check if dates of business roles and entitlemtn are different
     *
     * @param businessRoleMap
     *            Map of entilement and business role. sunriseDate start date of
     *            the entitlement sunsetDate end date of entitlement roleDate
     *            map containing the businessroles and dates
     * @return true if the dates of the business roles with common entitlement
     *         is different false if the dates of the business roles with common
     *         entitlement is same
     *
     * @throws GeneralException
     */
    private boolean isDateDifferent(String businessRole, Date sunriseDate,
                                    Date sunsetDate, Map roleDates) {
        Date addDate = (Date) roleDates.get(businessRole + SUNRISE_DATE);
        Date removeDate = (Date) roleDates.get(businessRole + SUNSET_DATE);

        if (((sunriseDate != null && addDate == null) ||
                (sunsetDate != null && removeDate == null)) ||
                ((addDate != null && sunriseDate == null) ||
                        (removeDate != null && sunsetDate == null)) ||
                ((addDate != null && !addDate.equals(sunriseDate)) ||
                        (removeDate != null &&
                                !removeDate.equals(sunsetDate)))) {
            return true;
        }

        return false;
    }

    /**
     * This function is required to fill all parameters which are present for 
     * application of SAP GRC type and provide the sub process with all required parameters to fire web service on GRC server.
     * @param wfc Workflow Context.
     * @throws GeneralException
     */
    public Map<String, String> buildGRCCredentialMap(WorkflowContext wfc) throws GeneralException {
        sailpoint.object.Application app = null;
        SailPointContext spcontext = wfc.getSailPointContext();

        // Map containing credential details present in SAP GRC type of application
        Map<String, String> appDetailsSAPGRC = null;

        StringBuffer error = null;
        String applicationName = null;

        appDetailsSAPGRC = getAppDetailSAPGRC(wfc,appDetailsSAPGRC);

        if(Util.isEmpty(appDetailsSAPGRC)) {
            applicationName = (String) wfc.get(APP_NAME_GRC);
            if (null != applicationName) {
                app = (sailpoint.object.Application) spcontext.getObjectByName(sailpoint.object.Application.class, applicationName);
                if (null != app) {
                    if (app.getType().equals(SAP_GRC)) {

                        appDetailsSAPGRC = new HashMap<String, String>();

                        putAttributeInMap(app, GRC_APP_GRC_USER_ID,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_GRC_PASSWORD,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_USER_ACCESS_URL,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_REQUEST_DETAIL_URL,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_CONNECTION_TIMEOUT,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_POLLING_INTERVAL,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_AUDIT_LOG_URL,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_MITIGATION_KEYWORD,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_APP_REQUEST_INITIATION_SYSTEM,appDetailsSAPGRC);
                        putAttributeInMap(app, GRC_RISK_ANALYSIS_URL,appDetailsSAPGRC);
                        appDetailsSAPGRC.put(APP_NAME_GRC, applicationName);
                    } else {
                        error = new StringBuffer();
                        error.append("Application name provided in Subprocess '");
                        error.append(wfc.getWorkflow().getName());  //'SAP GRC Data Generator'
                        error.append("' for the process variable 'applicationNameSAPGRC' with name '");
                        error.append(applicationName);
                        error.append("' is not of 'SAP GRC' type");
                    }

                } else {
                    error = new StringBuffer();
                    error.append("Application '");
                    error.append(applicationName);
                    error.append("' provided in the Subprocess '");
                    error.append(wfc.getWorkflow().getName());
                    error.append("' for the process variable 'applicationNameSAPGRC' is either not present or not of type 'SAP GRC'");
                }
            } else {
                error = new StringBuffer();
                error.append("No application name provided in the Subprocess '");
                error.append(wfc.getWorkflow().getName());
                error.append("' for the process variable 'applicationNameSAPGRC'");
            }
            if ( null != error && error.length() > 0 ) {
                if (log.isErrorEnabled()) {
                    log.error(error);
                }
                GeneralException exception = new GeneralException(error.toString());
                errorHandler(wfc, exception);
            }
            setAppDetailSAPGRC(wfc,appDetailsSAPGRC);
        }
        return appDetailsSAPGRC;
    }

    /**
     * Pick GRC details from application and fill it in map.
     * @param app SAP GRC Application.
     * @param key Attribute to be copied from application to map.
     */
    private void putAttributeInMap(Application app, String key,Map<String, String> appDetailSAPGRC) {
        Object parameterValue = null;
        if (null != (parameterValue = app.getAttributeValue(key)))
            appDetailSAPGRC.put(key, (String) parameterValue);
    }

    /**
     * Function returns the request initaition system name.
     * @param wfc Workflow Context.
     * @return Request initiation system name.
     * @throws GeneralException
     */
    public String getRequestInitializationSystem(WorkflowContext wfc) throws GeneralException {
        String requestInitializationSystem = null;
        Map<String,String> appDetailSapGRC = null;
        appDetailSapGRC = buildGRCCredentialMap(wfc);
        if(!(Util.isEmpty(appDetailSapGRC))) {
            requestInitializationSystem = appDetailSapGRC.get(GRC_APP_REQUEST_INITIATION_SYSTEM);
        }
        return requestInitializationSystem;
    }

    /**
     * Function to get SAP GRC application configuration details.
     * @param wfc The workflow context.
     * @param appDetailSAPGRCMap Map holding details of the SAP GRC application.
     * @return Map<String,String> containing SAP GRC application details
     */
    public Map<String, String> getAppDetailSAPGRC(WorkflowContext wfc, Map<String, String> appDetailSAPGRCMap) {
        Object requestStubDetailsObject = null;
        Object appDetailSAPGRCObject = null;
        Map<String, Object> requestStubDetailsMap = null;
        requestStubDetailsObject = wfc.getVariable(REQUEST_STUB_DETAILS_MAP);
        if (null != requestStubDetailsObject) {
            if (requestStubDetailsObject instanceof Map) {
                requestStubDetailsMap = (Map) requestStubDetailsObject;
                appDetailSAPGRCObject = requestStubDetailsMap.get(GRC_APP_DETAIL);
                if (null != appDetailSAPGRCObject) {
                    appDetailSAPGRCMap = (Map) appDetailSAPGRCObject;
                }
            }
        }
        return appDetailSAPGRCMap;
    }

    /**
     * Function to set SAP GRC application configuration details in workflow context.
     * @param wfc The workflow context.
     * @param appDetailSAPGRCMap Map holding details of the SAP GRC application.
     */
    public void setAppDetailSAPGRC(WorkflowContext wfc, Map<String, String> appDetailSAPGRCMap) {

        Map<String, Object> requestStubDetailsMap = getRequestStubDetailsMap(wfc);
        requestStubDetailsMap.put(GRC_APP_DETAIL, appDetailSAPGRCMap);
        wfc.setVariable(REQUEST_STUB_DETAILS_MAP, requestStubDetailsMap);
    }

    /**
     * Function to get requestStubDetailMap from workflow context.
     * @param wfc The workflow context.
     * @return Map<String,Object> requstStubDetailsMap from workflow context.
     */
    public Map<String, Object> getRequestStubDetailsMap(WorkflowContext wfc) {

        Object requestStubDetailsObject = null;
        Object appDetailSAPGRCObject = null;
        Map<String, Object> requestStubDetailsMap = null;
        requestStubDetailsObject = wfc.getVariable(REQUEST_STUB_DETAILS_MAP);
        if (null != requestStubDetailsObject) {
            if (requestStubDetailsObject instanceof Map) {
                requestStubDetailsMap = (Map) requestStubDetailsObject;

            }
        } else {
            requestStubDetailsMap = new HashMap<String, Object>();
        }
        return requestStubDetailsMap;
    }

    /**
     * This function checks if the "violationFound" flag is in the workflow.
     * If so, it gets its value and returns back.
     * @param args - Workflow arguments.
     * @return - Boolean value of "violationFound" flag.
     */
    private boolean isViolationFound( Attributes<String,Object> args) {
        // Bug 28095
        // If this is upgraded environment and ARM request was generated before upgrade,
        // then WFC_PROACTIVE_VIOLATION_FOUND will be null so we will make isViolationFound = true
        boolean isViolationFound = true;
        Object violationFoundFlag = args.get(WFC_PROACTIVE_VIOLATION_FOUND);
        if ( null != violationFoundFlag ) {
            isViolationFound = ( Boolean ) violationFoundFlag;
        }
        return isViolationFound;
    }

    /**
     * This function marks all request items to failed and as a result of this the Completion status becomes Failed and throws an exception.
     * By default if any exception occurs IIQ engine leaves the Completion and Execution status to Pending and Verifying respectively.
     * To have a correct status in IdentityRequest page on workflow failure we are marking 
     * ProvisioningState of all the request items in the request to Failed. So that Completion and Execution status
     * becomes Failure and Completed respectively. The method should get called only if any exception is encountered. 
     * @param wfc - The Workflow context.
     * @param exception - GeneralException object.
     * @throws GeneralException
     */
    private void errorHandler( WorkflowContext wfc, GeneralException exception ) throws GeneralException {
        Attributes<String, Object> argList = wfc.getArguments();

        // current identity request
        IdentityRequest identityRequest = IdentityRequestLibrary.getIdentityRequest( wfc );
        if ( null != identityRequest ) {
            List<IdentityRequestItem> requestItemList = identityRequest.getItems();
            for ( IdentityRequestItem item : requestItemList ) {
                // marking ProvisioningState to failed.
                // this will mark Completion status to Failed instead of leaving it in Pending state.
                item.setProvisioningState( ProvisioningState.Failed );
            }

            // Saving the modified identity request
            SailPointContext context = wfc.getSailPointContext();
            context.saveObject(identityRequest);
            context.commitTransaction();
        }

        throw exception;
    }

    /**
     * Update GRC response in this request.
     * If GRC has rejected this request, we need to cancel an entire cart.
     * Build our own transient approval set from the grc response.
     * Reject all items if grc rejects ANY item.
     * Add any comments from GRC response in IdentityRequest.
     *
     * In future, we need to reject only selected grc items.
     *
     * @param wfc Workflow Context.
     * @return Modified approvalSet.
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public ApprovalSet updateGRCResponse(WorkflowContext wfc) throws GeneralException {
        

        if ( null == wfc ) {
            throw new GeneralException("Workflow Context is null");
        }

        SailPointContext context = wfc.getSailPointContext();
        if ( null == context ) {
            throw new GeneralException("SailPoint Context is null");
        }

        if ( null == wfc.getRootWorkflowCase() ) {
            throw new GeneralException("Root Workflow Case is null");
        }

        Attributes<String,Object> args = wfc.getArguments();
        if ( null == args ) {
            throw new GeneralException("Workflow Arguments are null");
        }

        boolean isViolationFound = isViolationFound(args);

        ProvisioningPlan plan = (ProvisioningPlan) args.get( IdentityLibrary.ARG_PLAN );
        if ( null == plan ) {
            throw new GeneralException("Provisioning Plan is null");
        }

        List<Map<String, String>> grcRequestItems =
                ( List<Map<String, String>> ) args.get( REQUEST_REQUESTED_LINE_ITEM_MAP );
        if ( Util.isEmpty(grcRequestItems) ) {
            throw new GeneralException("GRC Requested Line Item Map is null");
        }

        Map<String, Object> grcResponse = (Map<String, Object>) args.get( GRC_RESPONSE );
        if ( Util.isEmpty(grcResponse) ) {
            throw new GeneralException("GRC Request Status Map is null");
        }

        String idDisplayName = args.getString( ARG_DISPLAY_NAME );
        if ( Util.isNullOrEmpty(idDisplayName) ) {
            throw new GeneralException("Identity Display Name is null");
        }
        
        boolean isPartialProvisioningEnabled = false;
        Map<String,String> credentialsMap = (Map<String,String>) args
                .get(REQUEST_CREDENTIALS_MAP);
        if (!Util.isEmpty(credentialsMap)) {
            isPartialProvisioningEnabled = isApplicationPartialProvisioningEnabled(
                    credentialsMap.get(APP_NAME_GRC), context);
            if (isPartialProvisioningEnabled) {
                ProvisioningProject project = (ProvisioningProject) wfc
                        .get(IdentityLibrary.VAR_PROJECT);
                // If project is null it says the required configuration is missing to enabled
                // partial provisioning for SAP GRC Integration. project is an actual provisioning
                // project which is required to get expanded IIQ business role.
                if (null == project) {
                    log.error(
                            "Required configuration to enabled partial provisioning in SAP GRC " +
                                    "Integration is missing. Make sure 'project' is added as process variable " +
                                    "in 'SAP GRC Request Executor' and as an argument in step 'Invoke SAP " +
                                    "GRC Request Executor' from 'SAP GRC Data Generator' workflow. ");
                    errorHandler(wfc,
                            new GeneralException(MessageKeys.CON_SAP_GRC_PARTIAL_PROVISIONING_CONFIG_ERROR));
                }
            }
        }

        // Creating transient approval set for grc
        ApprovalSet grcSet = new ApprovalSet();
        boolean grcRejected = false;

        List<Comment> grcComments = null;
        List<String> sapDirectApplicationNames = new ArrayList<>();
        for (AccountRequest request : Util.iterate(plan.getAccountRequests())) {
            String name = request.getApplication();
            if (!name.equalsIgnoreCase(ProvisioningPlan.APP_IIQ)) {
                Application app = context.getObjectByName(Application.class,
                        name);
                String type = app.getType();
                if (type.equalsIgnoreCase(SAP_DIRECT)
                        && isApplicationGRCEnabled(name, context)) {
                    sapDirectApplicationNames.add(name);
                }
            }
            IdentityLibrary.addApprovalItems(null, request, grcSet, context);
        }

        Map<String, Map<String, Object>> sapBusinessRoles = (Map<String, Map<String, Object>>) args
                .get(SAP_BUSINESS_ROLE_MAP);
        // 1. Check for complete request approval and rejection.
        if (isViolationFound && isPartialProvisioningEnabled
                && (isStatusIncorrect(grcResponse)
                        || isCompleteGRCRequestRejected(grcRequestItems,
                                grcResponse))) {
            log.info(
                    "GRC has rejected the complete request, marking approval all SAP items as rejected.");
            grcRejected = true;
            Map<String, Object> expandedBusinessRole = (null != sapBusinessRoles)
                    ? sapBusinessRoles.get(EXPANDED_BUSINESS_ROLE_MAP)
                    : new MultiValueMap();
            log.debug(
                    "expandedBusinessRole map which contains business role as key and value as its associated entitlement "
                            + expandedBusinessRole);
            grcComments = getCommentList(args, grcResponse);
            updateApporvalSetForCompleteApproveReject(expandedBusinessRole,
                    sapDirectApplicationNames, grcComments, grcSet,
                    grcRejected);
        } else if (isPartialProvisioningEnabled
                && isGRCRequestPartiallyRejected(grcResponse)) {

            // 2. Check for lineItem approval and rejection. This is the main
            // part
            // where we handle partial approval/rejection.
            log.info("The request is paritally approved/rejected.");
            Map<String, Object> sapEntlBusinessRoleMap = (null != sapBusinessRoles)
                    ? sapBusinessRoles.get(SAP_ENTL_BUSINESS_ROLE_MAP)
                    : new MultiValueMap();
            log.debug(
                    "sapEntlBusinessRoleMap map which contains entitlement as key and value as its associated IIQ business role "
                            + sapEntlBusinessRoleMap);
            List<Map<String, String>> grcResponseItemsList = (List<Map<String, String>>) grcResponse
                    .get(RESPONSE_REQUESTEDITEMS);
            rejectedSAPRoleConnNameMap = new MultiValueMap();
            List<String> rejectedSAPEntitlements = getRejectedSAPEntitlement(
                    grcRequestItems, grcResponseItemsList);
            log.debug("List of rejected SAP entitlement from SAP GRC "
                    + rejectedSAPEntitlements.toString());
            grcComments = getCommentList(args, grcResponse);
            updateApporvalSetForPartialApproveReject(sapEntlBusinessRoleMap,
                    rejectedSAPEntitlements, grcComments, grcSet, context);

        } else if (isGRCRejected(grcRequestItems, grcResponse)) {
            // 3. Now we are done with checking complete request
            // approval/rejection
            // and
            // lineItem approval/rejection. This step checks for rest of the
            // scenario
            // like
            // adding/removing the items from the request.
            log.info(
                    "The request is rejected as GRC has updated it by adding/removing one or more item.");
            grcRejected = true;
            grcComments = getCommentList(args, grcResponse);
            updateGRCApprovalSet(grcComments, grcSet, grcRejected);
        } else {
            // 4. Complete GRC request is approved.
            log.info("Complete GRC request is approved.");
            grcComments = getCommentList(args, grcResponse);
            updateGRCApprovalSet(grcComments, grcSet, grcRejected);
        }
        
        // Create dummy approval for grc
        Approval approval = new Approval();
        approval.setApprovalSet( grcSet );
        approval.setComplete( true );
        approval.setOwner( GRC_AUTHOR );
        approval.setStartDate( new Date() );
        approval.setEndDate( new Date() );
        if ( grcRejected ) {
            approval.setState( WorkItem.State.Rejected );
        } else {
            approval.setState( WorkItem.State.Finished );
        }
        approval.setWorkItemDescription( DESC_INTERACTION + idDisplayName );

        // Adding approval in task result
        TaskResult result = wfc.getRootWorkflowCase().getTaskResult();
        WorkflowSummary summary =
                ( WorkflowSummary ) result.getAttribute( WorkflowCase.RES_WORKFLOW_SUMMARY );
        if ( null == summary ) {
            summary = new WorkflowSummary();
        }
        summary.setApprovalSet( grcSet );

        // Bug 26998 - If comments are not populated in summary, they are fetched from each
        // approval item. Since we are adding same comments list in each item, same list is added
        // multiple times in summary. So it is important to set comments for summary.
        ApprovalSummary appSum = new ApprovalSummary( approval );
        appSum.setComments(grcComments);

        summary.addInteraction( appSum );
        result.setAttribute( WorkflowCase.RES_WORKFLOW_SUMMARY, summary );

        IdentityRequestLibrary.assimilateWorkItemApprovalSetToIdentityRequest( wfc, grcSet );    
        
        // The plan is modified only if there is violation which is approved and
        // when there is no violation for the roles assigned.Rejected violations 
        // will not be provisioned.
         
        if (!grcRejected) {
            settingWorkflowVariable( wfc, isViolationFound );
        }

        return grcSet;
    }
    
    /**
     * Function to check whether SAP GRC application is CUA enabled.
     *
     * @param appName
     *            Application to be checked.
     * @param context
     *            SailPoint Context.
     * @return Boolean value saying if SAP GRC application is partial
     *         provisioning enabled or not.
     * @throws GeneralException
     */
    private boolean isApplicationCUAEnabled(String appName,
            SailPointContext context) throws GeneralException {
        Application app = context.getObjectByName(Application.class, appName);
        boolean flag = false;
        if (null != app) {
            flag = app.getBooleanAttributeValue(IS_APP_CUA_ENABLED);
        }
        return flag;
    }

    /**
     * Method to extract the role name for CUA enabled applications.
     *
     * @param roleName
     *            CUA role name which contains GRC connector name and actual
     *            role name.
     * @return Actual role name.
     * @throws GeneralException
     */
    private String getCUAEntitlementName(String entitlementNameCUA)
            throws GeneralException {
        try {
            int slashPosition = entitlementNameCUA.indexOf('\\');
            if (slashPosition != -1) {
                entitlementNameCUA = entitlementNameCUA
                        .substring(slashPosition + 1);
            }
        } catch (Exception e) {
            log.error(
                    "Error while extracting actual role name from CUA based role name "
                            + entitlementNameCUA,
                    e);
            throw new GeneralException(e);
        }
        return entitlementNameCUA;
    }

    /**
     * Method to get the profile name from profile ID.
     *
     * @param profileID
     *            Profile ID which is a combination of profile name and profile
     *            description.
     * @return Profile name.
     * @throws GeneralException
     */
    private String getProfileName(String profileID) throws GeneralException {
        try {
            profileID = profileID.substring(0, profileID.indexOf(" ("));
        } catch (Exception e) {
            log.error(
                    "Error while extracting profile name from profile profile ID. "
                            + profileID,
                    e);
            throw new GeneralException(e);
        }
        return profileID;
    }

    /**
     * Function to find whether SAP GRC application is enabled to support
     * partial provisioning in SAP GRC Integration.
     *
     * @param appName
     *            Application to be checked.
     * @param context
     *            SailPoint Context.
     * @return Boolean value saying if SAP GRC application is partial
     *         provisioning enabled or not.
     * @throws GeneralException
     */
    private boolean isApplicationPartialProvisioningEnabled(String appName,
            SailPointContext context) throws GeneralException {
        Application app = context.getObjectByName(Application.class, appName);
        boolean flag = false;
        if (null != app) {
            flag = app
                    .getBooleanAttributeValue(IS_PARTIAL_PROVISIONING_ENABLED);
        }
        return flag;
    }

    /**
     * Method to get updated GRC approvalSet in case the request is rejected due
     * to some scneario's like GRC has approved the request by adding, removing
     * or updating the the item.
     *
     * @param comments
     *            comments added by SAP GRC admin.
     * @param grcSet
     *            {@code ApprovalSet} transient grc approvalSet created.
     * @param {{@code boolean} flag which indicates whether GRC has rejected or
     *            approved the request.
     */
    private void updateGRCApprovalSet(List<Comment> comments,
            ApprovalSet grcSet, boolean grcRejected) {
        // Add grc comments to approval items, comments are useful in email
        // notifications.
        // TODO Currently we do not have mapping of SAP entitlements with
        // appropriate
        // master
        // request. So adding all messages to every item, this needs to be
        // changed and
        // filtered messages should be added to corresponding item only.
        for (ApprovalItem item : Util.iterate(grcSet.getItems())) {
            item.setComments(comments);
            if (grcRejected) {
                item.addRejecter(GRC_AUTHOR);
                item.reject();
            } else {
                // Bug 26992 - Need to mark item as finished else it is
                // considered as incomplete and removed from cart.
                item.approve();
                item.setApprover(GRC_AUTHOR);
            }
            item.setOwner(GRC_AUTHOR);
        }
    }

    /**
     * Method to get updated GRC approvalSet in case the request is completely
     * approved or rejected.
     *
     * @param expandedBusinessRole
     *            {@code MultiValueMap} which contains key as an IIQ business roles
     *            and values as list of associated SAP entitlement.
     * @param sapDirectApplicationNames
     *            {@code List} of application name of type 'SAP - Direct'.
     * @param comments
     *            comments added by SAP GRC admin.
     * @param grcSet
     *            {@code ApprovalSet} transient grc approvalSet created.
     * @param {{@code boolean} flag which indicates whether GRC has rejected or
     *            approved the request.
     */
    private void updateApporvalSetForCompleteApproveReject(
            Map<String, Object> expandedBusinessRole,
            List<String> sapDirectApplicationNames, List<Comment> comments,
            ApprovalSet grcSet, boolean grcRejected) {

        for (ApprovalItem item : Util.iterate(grcSet.getItems())) {
            item.setComments(comments);
            if (grcRejected && sapDirectApplicationNames
                    .contains(item.getApplication())) {
                item.addRejecter(GRC_AUTHOR);
                item.reject();
            } else if (grcRejected && item.getApplication()
                    .equalsIgnoreCase(ProvisioningPlan.APP_IIQ)) {
                // if item.getApplication() is IIQ then it is an item for IIQ
                // business
                // role.
                // We have to reject those business role having rejected sap
                // entitlement
                // inside it.
                String businessRoleName = (String) item.getValue();
                if (null != expandedBusinessRole
                        && expandedBusinessRole.containsKey(businessRoleName)) {
                    // then fail that business role else approve it.
                    item.addRejecter(GRC_AUTHOR);
                    item.reject();
                }

            } else {
                // Bug 26992 - Need to mark item as finished else it is
                // considered as incomplete and removed from cart.
                item.approve();
                if (sapDirectApplicationNames.contains(item.getApplication())) {
                    item.setApprover(GRC_AUTHOR);
                    item.setOwner(GRC_AUTHOR);
                }
            }
        }
    }

    private List<Comment> getCommentList(Attributes<String, Object> args,
            Map<String, Object> grcResponse) {
        // set item comments list
        List<String> itemCommentList = null;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> grcResponseItemsList = (List<Map<String, String>>) grcResponse
                .get(RESPONSE_REQUESTEDITEMS);
        if (!Util.isEmpty(grcResponseItemsList)) {
            itemCommentList = new ArrayList<String>();
            for (Map<String, String> lineItem : grcResponseItemsList) {
                if (lineItem.get(REQUESTEDITEMS_APPROVALSTATUS)
                        .equalsIgnoreCase(REJECTED)
                        || lineItem.get(REQUESTEDITEMS_STATUS)
                                .equalsIgnoreCase(STATUS_FAILED)) {
                    String comment = lineItem.get(REQUESTEDITEMS_COMMENTS);
                    itemCommentList.add((Util.isNotNullOrEmpty(comment))
                            ? ("\nReason: " + comment) : "");
                }
            }
        }
        List<Comment> messagesList = getGRCComments(itemCommentList);
        // custom messages added from the workflow
        @SuppressWarnings("unchecked")
        List<String> commentList = (List<String>) args.get(ARG_COMMENTS_LIST);
        // check if user has provided any comments
        // if so, adding them on UI as type Info
        if (!Util.isEmpty(commentList)) {
            if (null == messagesList) {
                messagesList = new ArrayList<Comment>();
            }
            for (String messageStr : commentList) {
                Comment cmnt = new Comment();
                cmnt.setAuthor(GRC_AUTHOR);
                cmnt.setComment(messageStr);
                // Bug 27134 - Comments from GRC is Missing time Stamp in IIQ
                // request
                cmnt.setDate(new Date());
                messagesList.add(cmnt);
            }
        }
        return messagesList;
    }

    /**
     * This method is used to throw any checked exception in case we have to
     * handle exception in lambda expression.
     */
    @SuppressWarnings("unchecked")
    static <T extends Exception, R> R sneakyThrow(Exception t) throws T {
        throw (T) t;
    }

    /**
     * Method to create business role and entitlement association.
     *
     * @param wfc
     *            Workflow context.
     * @return map which contains key as a business role name and value as its
     *         associated entitlement name.
     * 
     */
    public Map getRoleBusinessMap(WorkflowContext wfc) {
        Map businessRoleMap = new MultiValueMap();
        SailPointContext spContext = wfc.getSailPointContext();
        ProvisioningProject project = (ProvisioningProject) wfc
                .get(IdentityLibrary.VAR_PROJECT);
        // Get the expansion items. Expansion items contains details of
        // entitlement present in business role.
        List<ExpansionItem> expansionItems = project.getExpansionItems();
        if (!Util.isEmpty(expansionItems)) {
            log.debug("expansionItems in the provisioning project= "
                    + expansionItems.toString());
            expansionItems.forEach(item -> {
                if (item.getCause().toString().equalsIgnoreCase(ROLE)) {
                    String applicationName = item.getApplication();
                    try {
                        if (isSAPDirectApplication(applicationName, spContext)
                                && isApplicationGRCEnabled(applicationName,
                                        spContext)) {
                            String roleName = item.getSourceInfo();
                            String entitlementName = getEntitlementName(
                                    applicationName, spContext, item);
                            businessRoleMap.put(roleName, entitlementName);
                        }
                    } catch (GeneralException e) {
                        sneakyThrow(e);
                    }
                }
            });
        }
        return businessRoleMap;
    }

    /**
     * Method to create the single map of <br>
     * 1. Map of business role and entitlement association. <br>
     * 2. Map of association between IIQ business role and SAP entitlement.
     * Resulting map will be added in the {@code WorkflowContext}.
     *
     * @param workflowContext
     */
    public void getSAPBusinessRoles(WorkflowContext workflowContext) {
        Map<String, Map> sapBusinessRoleMap = new HashMap<>();
        sapBusinessRoleMap.put(EXPANDED_BUSINESS_ROLE_MAP,
                getRoleBusinessMap(workflowContext));
        sapBusinessRoleMap.put(SAP_ENTL_BUSINESS_ROLE_MAP,
                getSAPEntlBusinessRoleMap(workflowContext));
        workflowContext.setVariable(SAP_BUSINESS_ROLE_MAP, sapBusinessRoleMap);
    }

    /**
     * Method to check whether complete grc request is rejected.
     *
     * @param grcRequestItemsList
     *            The requested items list from IdentityIQ.
     * @param requestDetailsMap
     *            The status map from SAP GRC.
     * @return Returns true if complete request is rejected otherwise false.
     */
    private boolean isCompleteGRCRequestRejected(
            List<Map<String, String>> grcRequestItemsList,
            Map<String, Object> requestDetailsMap) {
        boolean isRequestRejected = false;
        if (Util.nullSafeCaseInsensitiveEq(
                (String) requestDetailsMap.get(RESPONSE_REQUESTSTATUS),
                STATUS_FAILED)) {
            isRequestRejected = true;
            if (!Util.isEmpty(grcRequestItemsList)) {
                grcRequestItemsList.forEach(lineItem -> {
                    String itemName = lineItem.get(REQUESTEDITEMS_ITEMNAME);
                    String connectorName = lineItem
                            .get(REQUESTEDITEMS_CONNECTOR);
                    String itemMsg = itemName + "(" + connectorName + ")";
                    _grcComments.addRejectedItemMsg(itemMsg);
                });
            }
        }
        return isRequestRejected;
    }

    /**
     * Method to check whether sap grc request is partially rejected.
     *
     * @param requestDetailsMap
     *            The status map from SAP GRC.
     * @return Returns true if SAP GRC admin has partially rejected the request.
     * @throws GeneralException
     */
    private boolean isGRCRequestPartiallyRejected(
            Map<String, Object> requestDetailsMap) throws GeneralException {
        boolean isRequestRejected = false;
        if (null != requestDetailsMap.get(RESPONSE_REQUESTSTATUS)) {
            if (Util.nullSafeCaseInsensitiveEq(
                    (String) requestDetailsMap.get(RESPONSE_REQUESTSTATUS),
                    STATUS_PARTIAL_OK)) {
                isRequestRejected = true;
            }
        }
        return isRequestRejected;
    }

    /**
     * Method to get updated GRC approvalSet in case the request is partially
     * approved.
     *
     * @param businessRoleMap
     *            {@code Map} which contains key as an entitlement and list of
     *            business roles as value.
     * @param rejectedSAPEntitlements
     *            {@code List} of rejected SAP entitlement.
     * @param comments
     *            comments added by SAP GRC admin.
     * @param grcSet
     *            {@code ApprovalSet} transient grc approvalSet created.
     */
    private void updateApporvalSetForPartialApproveReject(
            Map<String, Object> businessRoleMap,
            List<String> rejectedSAPEntitlements, List<Comment> comments,
            ApprovalSet grcSet, SailPointContext context) {
        List<String> rejectedBusinessRoles = getRejectedBusinessRoles(
                businessRoleMap, rejectedSAPEntitlements, context);
        log.debug("List of rejected IIQ business roles are "
                + rejectedBusinessRoles.toString());
        List<ApprovalItem> apporvalItems = grcSet.getItems();
        if (!Util.isEmpty(apporvalItems)) {
            apporvalItems.forEach(item -> {
                item.setComments(comments);
                item.approve();
                item.setApprover(GRC_AUTHOR);
                item.setOwner(GRC_AUTHOR);
                String itemValue = (String) item.getValue();
                try {
                    if (isApplicationCUAEnabled(item.getApplication(),
                            context)) {
                        itemValue = getCUAEntitlementName(itemValue);
                    } else if (item.getName().equalsIgnoreCase(PROFILES)) {
                        itemValue = getProfileName(itemValue);
                    }
                } catch (GeneralException e) {
                    log.error("Error while updating approvalSet for item "
                            + itemValue);
                    sneakyThrow(e);
                }
                if (item.getApplication()
                        .equalsIgnoreCase(ProvisioningPlan.APP_IIQ)
                        && item.getName().equalsIgnoreCase(
                                ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES)) {
                    // If approval item has application name as IIQ then it's an
                    // approval
                    // item for IIQ Business role.
                    String businessRoleName = itemValue;
                    if (null != rejectedBusinessRoles) {
                        if (rejectedBusinessRoles.contains(businessRoleName)) {
                            // If the business role is present in
                            // rejectedBusinessRoles
                            // then fail it.
                            item.addRejecter(GRC_AUTHOR);
                            item.reject();
                        }
                    }
                } else
                    try {
                       if (rejectedSAPEntitlements.contains(itemValue)
                            && isSAPRoleConnectorNameSame(itemValue, item.getApplication(), context)) {
                         item.addRejecter(GRC_AUTHOR);
                         item.reject();
                     }
				} catch (GeneralException e) {
					log.error("Error while rejecting SAP role " + itemValue, e);
					sneakyThrow(e);
				}
            });
        }
    }

    /**
     * Method to get the list of rejected IIQ business role.
     *
     * @param businessRoleMap
     *            {@code Map} which contains key as sap entitlement and value as
     *            IIQ business role.
     * @param rejectedSAPEntitlements
     *            {@code List} of rejected sap entitlement.
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<String> getRejectedBusinessRoles(Map<String,Object> businessRoleMap,
                                                  List<String> rejectedSAPEntitlements,
                                                  SailPointContext context) {
        List<String> rejectedBusinessRoles = new ArrayList<>();
        Optional.ofNullable(businessRoleMap).ifPresent(businessRoleMapPred -> {
            businessRoleMapPred.entrySet().forEach(entryObject -> {
                Entry<String,Object> entry = entryObject;
                String entitlementRoleName = entry.getKey();
                String[] entitlementRoleNameArr = entitlementRoleName
                        .split("#");
                String sapEntitlement = entitlementRoleNameArr[0];
                String roleName = entitlementRoleNameArr[1];
                List<String> appName = (List<String>) entry.getValue();
                if (rejectedSAPEntitlements.contains(sapEntitlement) &&
                        isRejectedSAPRoleConnNameSame(sapEntitlement, appName,
                                context)) {
                    rejectedBusinessRoles.add(roleName);
                }

            });
        });
        return rejectedBusinessRoles;
    }

    /**
     * Method to check whether requested role's associated connector names present in
     * rejectedSAPRoleConnNameMap which contains list of rejected sap role with their
     * associated connector name. This method is specially used in case we have two roles
     * from different system with the same name.
     *
     * @param roleName
     *            SAP Role name.
     * @param appNameList
     *            list of applications to which SAP Role belongs.
     * @param context
     *            SailPoint context.
     * @return true if role's connector name present in rejectedSAPRoleConnNameMap. false
     *         otherwise.
     * @throws GeneralException
     */
    private boolean isRejectedSAPRoleConnNameSame(String roleName,
                                                  List<String> appNameList,
                                                  SailPointContext context) {
        return Optional.ofNullable(rejectedSAPRoleConnNameMap)
                .map(rejSAPRoleConnNameMap -> {
                    List<String> connectorNames = (List<String>) rejSAPRoleConnNameMap
                            .get(roleName);
                    return appNameList.stream().anyMatch(appName -> {
                        boolean isSAPRoleConnNameSame = false;
                        try {
                            isSAPRoleConnNameSame = connectorNames.contains(
                                    getGRCConnectorName(appName, context));
                        } catch (GeneralException e) {
                            log.error(
                                    "Exception occurred while calculating rejected business roles.",
                                    e);
                            sneakyThrow(e);
                        }
                        return isSAPRoleConnNameSame;
                    });
                }).orElse(false);
    }

    
    /**
     * Method to get the list of rejected SAP entitlement.
     *
     * @param grcRequestItemsList
     *            The requested items list from IdentityIQ.
     * @param grcResponseItemsList
     *            The response items list from SAP GRC.
     * @return {@code List} of rejected SAP Entitlement.
     */
    private List<String> getRejectedSAPEntitlement(
            List<Map<String, String>> grcRequestItemsList,
            List<Map<String, String>> grcResponseItemsList) {
        List<String> rejectedSAPEntitments = new ArrayList<>();
        log.debug("IIQ Requested Items : " + grcRequestItemsList);
        log.debug("GRC Response Items : " + grcResponseItemsList);
        if (!Util.isEmpty(grcResponseItemsList)) {
            grcResponseItemsList.forEach(item -> {
                String approvalStatus = item.get(REQUESTEDITEMS_APPROVALSTATUS);
                String itemStatus = item.get(REQUESTEDITEMS_STATUS);
                String itemName = item.get(REQUESTEDITEMS_ITEMDESC);
                String connectorName = item.get(REQUESTEDITEMS_CONNECTOR);
                log.debug("Checking item : " + itemName
                        + ", approval status is : " + approvalStatus);
                if (approvalStatus.equalsIgnoreCase(REJECTED)
                        || itemStatus.equalsIgnoreCase(STATUS_FAILED)) {
                    rejectedSAPEntitments.add(itemName);
                    log.debug("Item is rejected : " + itemName);
                    String itemMsg = itemName + "(" + connectorName + ")";
                    _grcComments.addRejectedItemMsg(itemMsg);
                    rejectedSAPRoleConnNameMap.put(itemName, connectorName);
                }
            });
            log.debug("SAP Role with their respective connector name " + rejectedSAPRoleConnNameMap);
        }
        return rejectedSAPEntitments;
    }

	/**
	 * Method to check whether requested role's connector name present in
	 * rejectedSAPRoleConnNameMap which contains list of rejected sap role with
	 * their associated connector name. This method is specially used in case we
	 * have two roles from different system with the same name.
	 *
	 * @param roleName
	 *            SAP Role name.
	 * @param appName
	 *            application name.
	 * @param context
	 *            Sailpoint Context.
	 * @return true if role's connector name present in rejectedSAPRoleConnNameMap.
	 *         false otherwise.
	 * @throws GeneralException
	 */
	private boolean isSAPRoleConnectorNameSame(String roleName, String appName, SailPointContext context)
			throws GeneralException {
		boolean isSAPRoleConnNameSame = false;
		if (isSAPDirectApplication(appName, context) && isApplicationGRCEnabled(appName, context)
				&& !Util.isEmpty(rejectedSAPRoleConnNameMap)) {
			List<String> connectorNames = (List<String>) rejectedSAPRoleConnNameMap.get(roleName);
			if (connectorNames.contains(getGRCConnectorName(appName, context))) {
				isSAPRoleConnNameSame = true;
			}
		}
		return isSAPRoleConnNameSame;
	}

    /**
     * Method to get the association between IIQ business role and SAP
     * entitlement.
     *
     * @param wfc
     *            Workflow Context.
     * @return {@code MultiValueMap} which contains key as SAP entitlement name and
     *         value as list of IIQ business role.
     * 
     */
    public Map getSAPEntlBusinessRoleMap(WorkflowContext wfc) {
        Map businessRoleMap = new MultiValueMap();
        SailPointContext spContext = wfc.getSailPointContext();
        ProvisioningProject project = (ProvisioningProject) wfc
                .get(IdentityLibrary.VAR_PROJECT);
        // Get the expansion items
        List<ExpansionItem> expansionItems = project.getExpansionItems();
        if (!Util.isEmpty(expansionItems)) {
            log.info("expansionItems in provisioning project = "
                    + expansionItems.toString());
            expansionItems.forEach(item -> {
                if (item.getCause().toString().equalsIgnoreCase(ROLE)) {
                    String applicationName = item.getApplication();
                    try {
                        if (isSAPDirectApplication(applicationName, spContext)
                                && isApplicationGRCEnabled(applicationName,
                                        spContext)) {
                            String roleName = item.getSourceInfo();
                            String entitlementName = getEntitlementName(
                                    applicationName, spContext, item);
                            /**
                             * CONUMSHIAN-3651 :- As a part of this bug we changed the way we
                             * used to represent businessRoleMap. The new structure would contain
                             * key as 'sapRoleName#iiqBusinessRoleName' and value as the list of
                             * applications to which that sap role belongs. This data structure is
                             * helpful while calculating rejected business roles where one or more
                             * business role contains sap role with same name but from two different
                             * systems. Example;- the new map will look like :
                             * {HR_CRITICAL_ACTION#CUA_BS_ROLE=[sap_direct],
                             * HR_CRITICAL_ACTION#sap_direct_bs_role=[sap_cua]}.
                             */
                            businessRoleMap.put(entitlementName + "#" +roleName, item.getApplication());
                        }
                    } catch (GeneralException e) {
                        log.error(
                                "Exception occurred while preparing Map which contains association between " +
                                "IIQ business role and SAP entitlement.");
                        sneakyThrow(e);
                    }
                }
            });
        }
        return businessRoleMap;
    }

    /**
     * Method to get the entitlement. For more detail see {@link getProfileName}
     * and {@link getCUAEntitlementName}
     *
     * @param applicationName
     * @param sailPointContext
     * @param entitlementName
     * @param expansionItem
     * @return entitlementName
     * @throws GeneralException
     */
    private String getEntitlementName(String applicationName,
            SailPointContext sailPointContext, ExpansionItem expansionItem)
            throws GeneralException {
        String entitlementName = (String) expansionItem.getValue();
        if (isApplicationCUAEnabled(applicationName, sailPointContext)) {
            entitlementName = getCUAEntitlementName(entitlementName);
        } else if (expansionItem.getName().equalsIgnoreCase(PROFILES)) {
            entitlementName = getProfileName(entitlementName);
        }
        return entitlementName;
    }

    /**
     * Setting the start and end date provided in the GRC data generated
     * workflow as a variable in the IIQ workflow. If all the start date and end
     * date of all the line items are same, a string value of startDate and
     * endDate are passed to support backward compatibility, if the dates are
     * different then start date map and end date map are passed. The variables
     * are passed in workflow as it has to be passed to SAP direct connector for
     * provisioning.
     *
     * Ex: responseStartDatesMap: responseEndDatesMap:
     *
     * @param wfc
     *            Workflow Context.
     * @param isViolationFound
     *            flag to determine violation found or not
     */
    private void settingWorkflowVariable(WorkflowContext wfc,
                                         boolean isViolationFound) {
        Attributes<String,Object> args = wfc.getArguments();
        boolean isDatesSame = false;
        if (!Util.isEmpty(args)) {
            Map<String,Object> grcResponse = (Map<String,Object>) args
                    .get(GRC_RESPONSE);
            List<Map<String,String>> grcRequestItems = (List<Map<String,String>>) args
                    .get(REQUEST_REQUESTED_LINE_ITEM_MAP);
            Map<String,String> lineItem = new HashMap<String,String>();
            String endDate = null;
            String startDate = null;
            Map<String,String> responseStartDatesMap = new HashMap<String,String>();
            Map<String,String> responseEndDatesMap = new HashMap<String,String>();
            List<Object> RequestResponseDatesList = new ArrayList<>();
            // If violation (risk) is found in the request and it is approved
            // then the
            // start and end dates are picked up from the response of GRC
            // if there is no risk then the dates are picked from the request
            // item.
            if (isViolationFound) {
                if (!Util.isEmpty(grcResponse)) {
                    List<Map<String,String>> grcResponseItemsList = (List<Map<String,String>>) grcResponse
                            .get(RESPONSE_REQUESTEDITEMS);
                    if (!Util.isEmpty(grcResponseItemsList)) {
                        // start date and end date of each response line item is
                        // set in the form of map
                        // and set in list
                        RequestResponseDatesList = getRequestResponseDates(
                                grcResponseItemsList, REQUESTEDITEMS_ITEMDESC);
                        // if all the dates are same, then fetch the dates from
                        // the 1st line item
                        lineItem = grcResponseItemsList.get(0);
                    }
                }
            } else {
                if (!Util.isEmpty(grcRequestItems)) {
                    // start date and end date of each response line item is set
                    // in the form of map
                    // and set in list
                    RequestResponseDatesList = getRequestResponseDates(
                            grcRequestItems, REQUESTEDITEMS_ITEMNAME);
                    // if all the dates are same, then fetch the dates from the
                    // 1st line item
                    lineItem = grcRequestItems.get(0);
                }
            }
            isDatesSame = (boolean) RequestResponseDatesList.get(0);
            responseStartDatesMap = (Map<String,String>) RequestResponseDatesList
                    .get(1);
            responseEndDatesMap = (Map<String,String>) RequestResponseDatesList
                    .get(2);

            if (isDatesSame) {
                if (log.isDebugEnabled()) {
                    log.debug(" Dates of all Entitlements is same.");
                }
                String validTo = lineItem.get(REQUESTEDITEMS_VALIDTO);
                String validFrom = lineItem.get(REQUESTEDITEMS_VALIDFROM);
                endDate = changeDateFormat(validTo);
                startDate = changeDateFormat(validFrom);
                wfc.setVariable("endDate", endDate);
                wfc.setVariable("startDate", startDate);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(" Dates of of Entitlements are different.");
                    log.debug("Start Date map is: " + responseStartDatesMap);
                    log.debug("End Date map is: " + responseEndDatesMap);
                }
                wfc.setVariable("endDate", responseEndDatesMap);
                wfc.setVariable("startDate", responseStartDatesMap);
            }
        }
    }

    /**
     * Used to change the format of the date to format accepted by SAP 
     * Direct connector. If date provided is in wrong format then the
     * default dates will be provisioned.
     *
     * @param date set in sap grc data generator workflow.
     */
     private String changeDateFormat(String date) {
    	 String stringDate = null;
         try {
        	 if (!Util.isNullOrEmpty(date)) {
        		 Date eDate = new SimpleDateFormat("yyyyMMdd").parse(date);
        		 stringDate = new SimpleDateFormat("yyyy-MM-dd").format(eDate);
        	 }
         } catch (Exception e) {
        	 if ( log.isWarnEnabled() ) {
        		 log.warn( new Message(MessageKeys.WORKFLOW_GRC_MSG_SAP_DATE_FORMATTING_ERROR, date, e ));
             }
         }
         return stringDate;
     }

    /**
     * Setting the start and end date provided in the GRC data generated
     * workflow as a variable in the IIQ workflow. If all the start date and end
     * date of all the line items are same, a string value of startDate and
     * endDate are passed to support backward compatibility, if the dates are
     * different then start date map and end date map are passed. Ex:
     * responseStartDatesMap: responseEndDatesMap:
     *
     * @param requestResponseItemsList
     *            list containing the response/request line items.
     * @param itemValue
     *            item value to fetch the name of the entitlement from the line
     *            item
     */
    List<Object> getRequestResponseDates(List<Map<String,String>> requestResponseItemsList,
                                         String itemValue) {
        String previousStartDate = null, previousEndDate = null;
        boolean isDatesSame = true;
        List<Object> RequestResponseDatesList = new ArrayList<>();
        Map<String,String> responseStartDatesMap = new HashMap<String,String>();
        Map<String,String> responseEndDatesMap = new HashMap<String,String>();
        for (Map<String,String> requestResponseItem : requestResponseItemsList) {
            String currentEndDate, currentStartDate;
            // comparing the dates of the current line item with the dates of
            // the previous line item
            // if the dates are same we set the workflow dates as string, if the
            // dates are different,
            // a map of dates are set in workflow.
            if (Util.isNullOrEmpty(previousStartDate) &&
                    Util.isNullOrEmpty(previousEndDate)) {
                previousStartDate = requestResponseItem
                        .get(REQUESTEDITEMS_VALIDFROM);
                currentStartDate = previousStartDate;
                previousEndDate = requestResponseItem
                        .get(REQUESTEDITEMS_VALIDTO);
                currentEndDate = previousEndDate;

            } else {
                currentEndDate = requestResponseItem
                        .get(REQUESTEDITEMS_VALIDTO);
                currentStartDate = requestResponseItem
                        .get(REQUESTEDITEMS_VALIDFROM);
                if (isDatesSame && previousEndDate.equals(currentEndDate) &&
                        previousStartDate.equals(currentStartDate)) {
                    previousStartDate = currentStartDate;
                    previousEndDate = currentEndDate;

                } else {
                    isDatesSame = false;
                }
            }
            String entitlementName = requestResponseItem.get(itemValue);
            String endDate = changeDateFormat(currentEndDate);
            String startDate = changeDateFormat(currentStartDate);
            responseStartDatesMap.put(entitlementName, startDate);
            responseEndDatesMap.put(entitlementName, endDate);
        }
        RequestResponseDatesList.add(isDatesSame);
        RequestResponseDatesList.add(responseStartDatesMap);
        RequestResponseDatesList.add(responseEndDatesMap);
        return RequestResponseDatesList;
    }

    /**
     * A library function which execute the audit log details and returns the map.
     * containing the all audit information.
     * @param wfc Workflow Context object.
     * @return Map of audit information.
     * @throws GeneralException
     */
    public Map<String,Object> getAuditLogMap(WorkflowContext wfc) throws GeneralException {
        Map<String,Object> auditLogMap = new HashMap<String,Object>();
        Attributes<String, Object> argList = wfc.getArguments();
        boolean isViolationFound = isViolationFound(argList);
        if ( isViolationFound ) {
            auditLogMap.putAll(getAuditLogDetails(wfc));
            if (log.isTraceEnabled()) {
                log.trace("Audit Log map is : " + auditLogMap);
            }
        }
        return auditLogMap;
    }

    /**
     * Function checks the status of the request and the status of the current stage
     * and decides whether the request is good or not. We check the following :- 
     * 1) If the request has no Request Status
     * 2) If the Current Stage has status ERROR
     * 3) If the request has no current stage
     * @param requestDetailsMap SAP GRC response map.
     * @return Boolean value indicating status of the response is valid or not.
     */
    @SuppressWarnings("unchecked")
    private boolean isStatusIncorrect(Map<String, Object> requestDetailsMap) {
        boolean flag = false;

        String requestNumber = (String) requestDetailsMap.get(RESPONSE_REQUESTNUMBER);

        List<Map<String, Object>> currentStageMap = (List<Map<String, Object>>) requestDetailsMap.get(RESPONSE_CURRENTSTAGE);
        if(!Util.isEmpty(currentStageMap)) {
            for (Map<String, Object> currentStage : currentStageMap) {
                String currentStageStatus  = (String) currentStage.get(CURRENTSTAGE_CURSTAGESTATUS);
                String currentStageName = (String) currentStage.get(CURRENTSTAGE_CURSTAGENAME);
                if (log.isDebugEnabled()) {
                    log.debug("current stage status is : " + currentStageStatus);
                    log.debug("current stage name is : " + currentStageName);
                }

                // If current stage name is empty then this is am exception on GRC workflow
                // GRC workflow should be corrected. Failing the request in such cases.
                if (Util.isNullOrEmpty(currentStageName)) {
                    flag = true;
                    if (log.isInfoEnabled()) {
                        log.info("No stage defined in the SAP GRC workflow for request " + requestNumber + ". Please contact SAP GRC administrator.");
                    }
                }

                // if current stage status has ERROR then fail the request
                if (currentStageStatus.equalsIgnoreCase(STATUS_ERROR)) {
                    flag = true;
                    if (log.isInfoEnabled()) {
                        log.info("The current stage of the request " + requestNumber + " has status ERROR. Please contact SAP GRC administrator.");
                    }
                }
            }
        }

        if (flag) {
            _grcComments.addStatusMsg(requestNumber);
        }
        if (log.isDebugEnabled()) {
            log.debug("isStatusIncorrect returning value : " + flag);
        }
        return flag;
    }

    /**
     * Return GRC comments for this request if any.
     * @param itemCommentList 
     * @return List of comments.
     */
    private List<Comment> getGRCComments(List<String> itemCommentList) {
        List<Comment> comments = new ArrayList<Comment>();
        addComments(comments, _grcComments.getAddedItemsMsgs(), null);
        addComments(comments, _grcComments.getRemovedItemsMsgs(), null);
        addComments(comments, _grcComments.getRetainedItemsMsgs(), null);
     // add item comments only for rejected items as interactions are only displaying rejected items data
        addComments(comments, _grcComments.getRejectedItemsMsgs(), itemCommentList); 
        addComments(comments, _grcComments.getStatusMsgs(), null);
        addComments(comments, _grcComments.getMitigationStatusMsg(), null);
        
        Message msg = _grcComments.getProActiveMsg();
        if ( null != msg ) {
            Comment cmnt = new Comment();
            cmnt.setAuthor( GRC_AUTHOR );
            cmnt.setComment( msg.getLocalizedMessage() );
            cmnt.setDate( new Date() );
            comments.add(cmnt);
        }
        return comments;
    }

    /**
     * Add SAP GRC messages into comments.
     * @param comments Identity request comments
     * @param grcMessages SAP GRC messages
     * @param itemCommentList 
     */
    private void addComments(List<Comment> comments, List<Message> grcMessages, List<String> itemCommentList) {
    	int itemCount=0;
        for ( Message msg : Util.iterate(grcMessages) ) {
            Comment cmnt = new Comment();
            cmnt.setAuthor( GRC_AUTHOR );
            cmnt.setComment( msg.getLocalizedMessage() + (!Util.isEmpty(itemCommentList)? itemCommentList.get(itemCount):""));
            // Bug 27134 - Comments from GRC is Missing time Stamp in IIQ request
            cmnt.setDate( new Date() );
            comments.add( cmnt );
            itemCount++;
        }
    }

    /**
     * Function parses the response and checks if requested plan and response plan are same.
     * @param grcRequestItemsList The requested items list from IdentityIQ.
     * @param requestDetailsMap The status map from SAP GRC.
     * @return Returns true if there is a change in response, else false.
     * @throws GeneralException
     */
    private boolean isGRCRejected(List<Map<String, String>> grcRequestItemsList, Map<String, Object> requestDetailsMap) throws GeneralException {
        boolean isRequestRejected =  false;
        String status = (String) requestDetailsMap.get(RESPONSE_REQUESTSTATUS); 
        if (Util.isNotNullOrEmpty(status)) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> grcResponseItemsList = (List<Map<String, String>>) requestDetailsMap.get(RESPONSE_REQUESTEDITEMS);

            if (status.equalsIgnoreCase(STATUS_FAILED)) {
                isRequestRejected = true;
                if (!Util.isEmpty(grcRequestItemsList)) {
                    for (Map<String, String> lineItem : grcRequestItemsList) {
                        String itemName = lineItem.get(REQUESTEDITEMS_ITEMNAME);
                        String connectorName = lineItem.get(REQUESTEDITEMS_CONNECTOR);
                        String itemMsg = itemName + "(" + connectorName + ")";
                        _grcComments.addRejectedItemMsg( itemMsg );
                    }
                }
            } else {
                // calling this method is whole request was not rejected, to check what action was taken on GRC.
                isRequestRejected = isGRCRequestRejected(grcRequestItemsList, grcResponseItemsList);
            }
            // if the status is FAILED or PARTIAL_OK, then making the flag true,
            // in case of status OK, the result from the above method will be returned
            if(status.equalsIgnoreCase(STATUS_PARTIAL_OK)) {
                isRequestRejected = true;
            }
        }
        return isRequestRejected;
    }  

    /**
     * Function to take a decision to mark GRC decision rejected.
     * @param grcRequestItemsList The requested items list from IdentityIQ.
     * @param requestDetailsMap The status map from SAP GRC.
     * @return Returns true if there is a change in response, else false.
     * @throws GeneralException
     */
    private boolean isGRCRequestRejected(List<Map<String, String>> grcRequestItemsList, List<Map<String, String>> grcResponseItemsList) throws GeneralException {

        boolean value = false;

        Map<String, Object> roleAndActionMap = new HashMap<String, Object>();
        Map<String, Object> profileAndActionMap = new HashMap<String, Object>();
        List<String> responseItemList = new ArrayList<String>();
        List<String> requestItemList = new ArrayList<String>();

        // Collecting the data from GRC response
        // marking the request failed, if any item is rejected.
        // To check if any item is rejected we check the approvalStatus
        // AP : Approved, RE : Rejected 

        if (log.isDebugEnabled()) {
            log.debug("IIQ Requested Items : " + grcRequestItemsList);
            log.debug("GRC Response Items : " + grcResponseItemsList);
        }
        if (!Util.isEmpty(grcResponseItemsList)) {
            for (Map<String, String> item : grcResponseItemsList) {
                String approvalStatus = item.get(REQUESTEDITEMS_APPROVALSTATUS);
                String itemStatus = item.get(REQUESTEDITEMS_STATUS);
                String itemName = item.get(REQUESTEDITEMS_ITEMDESC);
                String connectorName = item.get(REQUESTEDITEMS_CONNECTOR);
                //CONETN-2410:Since there can be same role/profile name for different SAP system, we are appending role/profile name 
                //with connector name to make it unique.
                String connectorItem = itemName + connectorName;
                if (log.isDebugEnabled()) {
                    log.debug("Checking item : " + itemName + ", approval status is : " + approvalStatus);
                }
                // this list we require later to check any extra item was added from GRC or not.
                responseItemList.add(connectorItem);
                String itemType = item.get(REQUESTEDITEMS_PROVITEMTYPE);

                if (itemType.equalsIgnoreCase(TYPE_ROLE)) {
                    roleAndActionMap.put(connectorItem, item);
                } else if (itemType.equalsIgnoreCase(TYPE_PROFILE)) {
                    profileAndActionMap.put(connectorItem, item);
                }
                if (approvalStatus.equalsIgnoreCase(REJECTED) || itemStatus.equalsIgnoreCase(STATUS_FAILED)) {
                    // this item is Rejected
                    if (log.isDebugEnabled()) {
                        log.debug("Item is rejected : " + itemName);
                    }
                    value = true;
                    String itemMsg = itemName + "(" + connectorName + ")";
                    _grcComments.addRejectedItemMsg( itemMsg );
                }
            }
        }

        // the response list is ready, here checking if all the requested 
        // items are matching the items in the response based on the operation, start date and end date.

        if (!Util.isEmpty(grcRequestItemsList)) {
            for (Map<String, String> lineItem : grcRequestItemsList) {
                String itemName = lineItem.get(REQUESTEDITEMS_ITEMNAME);
                String connectorName = lineItem.get(REQUESTEDITEMS_CONNECTOR);
                // this list we require later to check any extra item was added from GRC or not.
                requestItemList.add(itemName + connectorName);

                String itemType = lineItem.get(REQUESTEDITEMS_PROVITEMTYPE);
                if (log.isDebugEnabled()) {
                    log.debug("Checking requested item : " + itemName + ", provisioning type is : " + itemType);
                }

                boolean isRoleApproved = true;
                boolean isProfileApproved = true;
                // check if the item in requested data is in the response
                // if so check the operation, validFrom and validTo.
                // if every thing matches, item is approved in GRC.
                // else add the item in message and flag the result.
                if (itemType.equalsIgnoreCase(TYPE_ROLE)) {
                    isRoleApproved = isItemApprovedOnGRC(itemName, lineItem, roleAndActionMap, connectorName);
                } else if (itemType.equalsIgnoreCase(TYPE_PROFILE)) {
                    isProfileApproved = isItemApprovedOnGRC(itemName, lineItem, profileAndActionMap, connectorName);
                }
                if (log.isDebugEnabled()) {
                    log.debug("isRoleApproved : " + isRoleApproved + ", isProfileApproved : " + isProfileApproved);
                }
                if ( !(isRoleApproved && isProfileApproved)) {
                    // item failed to make match flag the result 
                    if (log.isDebugEnabled()) {
                        log.debug("Item is rejected : " + itemName);
                    }
                    value =  true;
                    // Going ahead, this will be the place where we would reject 
                    // each approvalItem for the corresponding requested item.
                }
            }
        }

        // till now we have compared the requested items with the items in the GRC response
        // we also need to check if any item is added from the GRC, it may happen that requested 
        // items may be approved but in addition GRC person is adding or removing some existing 
        // role assignment. We need to take care of that as well.

        // iterate the response items, and check each if it is present in the requested item list.
        // if present then we already have checked the operation and validity data.
        // if not, then we are marking the request as rejected, and adding the culprit roles
        if (!Util.isEmpty(responseItemList)) {
            for (String item : responseItemList) {
                if (!requestItemList.contains(item)) {
                    // this item is added at GRC, flag the result
                    value = true;
                    if (log.isDebugEnabled()) {
                        log.debug("Extra item found in the GRC response : " + item);
                    }
                    if (roleAndActionMap.containsKey(item)) {
                        // this is role, add the message
                        prepareGRCMessages(roleAndActionMap, item);
                    } else {
                        // this is profile, add the message
                        prepareGRCMessages(profileAndActionMap, item);
                    }
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("isGRCRequestRejected returning : " + value);
        }
        return value;
    }

    /**
     * Function compares requested item and response item and adds the reason in the messages.
     * @param itemAndActionMap Map holding line items from SAP GRC and its provisioning action details.
     * @param itemName Item name to be checked.
     */
    private void prepareGRCMessages(Map<String, Object> itemAndActionMap, String itemName) {
        if (itemAndActionMap.containsKey(itemName)) {
            @SuppressWarnings("unchecked")
            Map<String, String> itemDetails = (Map<String, String>) itemAndActionMap.get(itemName);
            String prov = itemDetails.get(REQUESTEDITEMS_PROVACTION);
            String validFrom = itemDetails.get(REQUESTEDITEMS_VALIDFROM);
            String validTo = itemDetails.get(REQUESTEDITEMS_VALIDTO);
            String itemDesc = itemDetails.get(REQUESTEDITEMS_ITEMDESC);
            checkOperationAndAddMessage(prov, itemDesc, validFrom, validTo);
        }
    }

    /**
     * Function compares the response item's provisioning action and accordingly adds message. 
     * @param prov Provisioning action given by GRC response.
     * @param itemName Item to be checked.
     * @param validFrom Start date of an item.
     * @param validTo End date of an item.
     */
    private void checkOperationAndAddMessage(String prov, String itemName, String validFrom, String validTo) {
        if (prov.equalsIgnoreCase(PROV_ACTION_ASSIGN)) {
            // 006 - Add
            _grcComments.addAddedItemMsg(itemName);
        } else if (prov.equalsIgnoreCase(PROV_ACTION_REMOVE)) {
            // 009 - Remove
            _grcComments.addRemovedItemMsg(itemName);
        } else if (prov.equalsIgnoreCase(PROV_ACTION_RETAIN)) {
            // 010 - Retain
            _grcComments.addRetainedItemMsg(itemName, validFrom, validTo);
        }
    }

    /**
     * Function to find whether the item has been approved on GRC or not.
     * @param itemName Role name or profile name requested.
     * @param lineItem Map of requested item.
     * @param itemAndActionMap Response item map.
     * @return Returns true if there is no change in response, else false.
     * @throws GeneralException
     */
    private boolean isItemApprovedOnGRC(String itemName, Map<String, String> requestedLineItem, Map<String, Object> itemAndActionMap, String connectorName) throws GeneralException{
        boolean flag = true;
        if (itemAndActionMap.containsKey(itemName + connectorName)) {
            String reqProvAction = requestedLineItem.get(REQUESTEDITEMS_PROVACTION);
            String reqValidFrom = requestedLineItem.get(REQUESTEDITEMS_VALIDFROM);
            String reqValidTo = requestedLineItem.get(REQUESTEDITEMS_VALIDTO);
            @SuppressWarnings("unchecked")
            Map<String,String> itemDetails = (Map<String, String>) itemAndActionMap.get(itemName + connectorName);
            String provAction = itemDetails.get(REQUESTEDITEMS_PROVACTION);
            String validFrom = itemDetails.get(REQUESTEDITEMS_VALIDFROM);
            String validTo = itemDetails.get(REQUESTEDITEMS_VALIDTO);
            if (log.isDebugEnabled()) {
                log.debug("Request item : " + itemName + ", provAction :  " + reqProvAction + ", validFrom : " + reqValidFrom + ", validTo : " + reqValidTo);
                log.debug("Response item : " + itemName + ", provAction :  " + provAction + ", validFrom : " + validFrom + ", validTo : " + validTo);
            }
            if (provAction.equalsIgnoreCase(reqProvAction)) {
                if (!provAction.equalsIgnoreCase(PROV_ACTION_REMOVE) &&
                        ((Util.isNotNullOrEmpty(reqValidFrom) && !validFrom.equalsIgnoreCase(reqValidFrom)) ||
                                (Util.isNotNullOrEmpty(reqValidTo) && !validTo.equalsIgnoreCase(reqValidTo)))) {
                    _grcComments.addRetainedItemMsg(itemName, validFrom, validTo);
                    flag = false;
                    if (log.isDebugEnabled()) {
                        log.debug("Item is retained : " + itemName);
                    }
                }
            } else {
                checkOperationAndAddMessage(provAction, itemName, validFrom, validTo);
                flag = false;
                if (log.isDebugEnabled()) {
                    log.debug("Item's provision action is not matching : " + itemName);
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Item is removed from the GRC request : " + itemName);
            }
            _grcComments.addRemovedItemMsg(itemName);
            flag = false;
        }
        if (log.isDebugEnabled()) {
            log.debug("isItemApprovedOnGRC returning : " + flag);
        }
        return flag;
    }

    /**
     *  A class which holds the messages when SAP GRC request is failed, partially passed, or passed with some
     *  additional remediation action, class gathers all the reasons because of why the GRC response is different 
     *  than the requested plan.
     */
    class SAPGRCMessages {

        /**
         * Lists which holds the messages for added, removed, 
         * retained and rejected items on SAP GRC.
         */
        List<Message> rejectedItemMsgs = null;
        List<Message> addedItemMsgs = null;
        List<Message> removedItemMsgs = null;
        List<Message> retainedItemMsgs = null;
        List<Message> statusMsgs = null;
        List<Message> mitigationMsgs = null;
        Message proActiveNoRisksMsg = null;

        /**
         * constructor
         */
        public SAPGRCMessages() {

            rejectedItemMsgs = new ArrayList<Message>();
            addedItemMsgs = new ArrayList<Message>();
            removedItemMsgs = new ArrayList<Message>();
            retainedItemMsgs = new ArrayList<Message>();
            statusMsgs = new ArrayList<Message>();
            mitigationMsgs  = new ArrayList<Message>();
        }

        /**
         * Add message for ProActive Check.
         */
        public void setProActiveMsg() {
            proActiveNoRisksMsg = new Message(Message.Type.Info, MessageKeys.WORKFLOW_GRC_MSG_PRO_ACTIVE_CHECK_NO_RISKS);
        }

        /**
         * Add message of rejection of an item.
         * @param item item Item name to be added.
         */
        public void addRejectedItemMsg(String item) {
            Message msg = new Message(Message.Type.Info, MessageKeys.WORKFLOW_GRC_MSG_ROLE_REJECTION, item);
            rejectedItemMsgs.add(msg);
        }

        /**
         * Add message of addition of an item.
         * @param item item Item name to be added.
         */
        public void addAddedItemMsg(String item) {
            Message msg = new Message(MessageKeys.WORKFLOW_GRC_MSG_ROLE_ADDITION, item);
            addedItemMsgs.add(msg);
        }

        /**
         * Add message of removal of an item.
         * @param item Item name to be added.
         */
        public void addRemovedItemMsg(String item) {
            Message msg = new Message(MessageKeys.WORKFLOW_GRC_MSG_ROLE_REMOVAL, item);
            removedItemMsgs.add(msg);
        }

        /**
         * Add mitigation message.
         * @param str Mitigation message
         */
        public void addMitigationStatusMsg(String mitigationMessage) {
            Message msg = null;          
            if (Util.isNotNullOrEmpty(mitigationMessage)) {
                msg = new Message(MessageKeys.WORKFLOW_GRC_MSG_MITIGATION_MESSAGE, mitigationMessage);
                mitigationMsgs.add(msg);
            }
        }

        /**
         * Add message related to status of the request.
         * @param stageName Stage name to be added in the message if any.
         */
        public void addStatusMsg(String requestNumber) {
            Message msg = new Message(MessageKeys.WORKFLOW_GRC_MSG_INCORRECT_STATUS, requestNumber);
            statusMsgs.add(msg);
        }

        /**
         * Add message of retention of an item.
         * @param item Item name to be added.
         * @param validFrom Item's start date.
         * @param validTo Item's end date.
         */
        public void addRetainedItemMsg(String item, String validFrom, String validTo) {
            Message msg = null;
            if (Util.isNotNullOrEmpty(validFrom) && Util.isNotNullOrEmpty(validTo)) {
                try {
                    validFrom = getDate(validFrom);
                    validTo = getDate(validTo);
                    msg = new Message(MessageKeys.WORKFLOW_GRC_MSG_ROLE_RETENTION_WITH_VALIDITY, item, validFrom, validTo);
                } catch (ParseException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("Exception in parsing dates : " + e.getLocalizedMessage(), e );
                    }
                }
            }
            if ( null == msg ) {
                msg = new Message(MessageKeys.WORKFLOW_GRC_MSG_ROLE_RETENTION, item);
            }
            retainedItemMsgs.add(msg);
        }

        /**
         * Get messages of added item.
         * @return Message for proAcive check..
         */
        public Message getProActiveMsg() {
            return proActiveNoRisksMsg;
        }

        /**
         * Get messages of added item.
         * @return List of added items.
         */
        public List<Message> getAddedItemsMsgs() {
            return addedItemMsgs;
        }

        /**
         * Get messages of removed item.
         * @return List of removed items.
         */
        public List<Message> getRemovedItemsMsgs() {
            return removedItemMsgs;
        }

        /**
         * Get messages of rejected item.
         * @return List of rejected items.
         */
        public List<Message> getRejectedItemsMsgs() {
            return rejectedItemMsgs;
        }

        /**
         * Get messages of retained item.
         * @return List of retained items. 
         */
        public List<Message> getRetainedItemsMsgs() {
            return retainedItemMsgs;
        }

        /**
         * Get messages related to status of the request
         * @return List of status messages 
         */
        public List<Message> getStatusMsgs() {
            return statusMsgs;
        }

        /**
         * Get mitigation messages.
         * @return List of mitigation messages. 
         */
        public List<Message> getMitigationStatusMsg() {
            return mitigationMsgs;
        }

        /**
         * Function converts date from YYYYDDMM to DD-MM-YYYY.
         * @param strDate - YYYYDDMM date
         * @return Date in DD-MM-YYYY format.
         * @throws ParseException
         */
        private String getDate(String strDate) throws ParseException {
            String formattedDate = null;
            SimpleDateFormat dFormat = new SimpleDateFormat(DATE_FORMAT_YYYYDDMM);
            SimpleDateFormat dFormatFinal = new SimpleDateFormat(DATE_FORMAT_DD_MMM_YYYY);
            Date date = dFormat.parse(strDate);
            formattedDate = dFormatFinal.format(date);
            return formattedDate;
        }
    }
    /**
     * Function convert from Grac String to Java String.
     * it returns empty string if no value found in the Grac String.
     * @param gracString - Grac string from sap documentation.
     * @return String - Java String which will be converted from Grac String. 
     */
    private String convertGRACToJavaString(functions.rfc.sap.document.sap_com.String gracString) { 
        String javaString = STRING_EMPTY;
        if (null != gracString) {
            //Casted String to make the statement forcefully into java string.
            javaString = (String)gracString.getString();
        } 
        return javaString;
    }

    /**
     * This method will evaluate the Item List which is populated at the time of calling GracTApiAuditLogs.getItem() 
     * @param GracSApiAuditLogs[] list GracSApiAuditLogs object will be come in the form of list, they are usually item.
     * @return List<Map<String,Object>>  List will be return in the form of <String,Object> as a key,value pair.
     */
    private List<Map<String,Object>> evaluateItems(GracSApiAuditLogs[] list, String mitigationSearString) {
        /*
        <Requestnumber>1034</Requestnumber>
        <Requestedby>Basis, (BASIS)</Requestedby>
        <Submittedby>Basis, (BASIS)</Submittedby>
        <Status>Decision pending</Status>
        <Createdate>20150818</Createdate>
        <Priority>Medium</Priority>
        <Itauditdata>
         */
        List<Map<String,Object>> innerItemsData = new ArrayList <Map<String,Object>>();
        if(!Util.isEmpty(list)) {
            for(GracSApiAuditLogs grp:list) { 
                Map <String,Object> innerItemHashMap = new HashMap <String,Object>();
                Map<String,Object> evaluateItems = new HashMap <String,Object>();
                innerItemHashMap.put(RESPONSE_REQUESTNUMBER,convertGRACToJavaString(grp.getRequestnumber()));
                innerItemHashMap.put(AUDITLOG_REQUESTEDBY,convertGRACToJavaString(grp.getRequestedby()));
                innerItemHashMap.put(AUDITLOG_SUBMITTEDBY,convertGRACToJavaString(grp.getSubmittedby()));
                innerItemHashMap.put(REQUESTEDITEMS_STATUS,convertGRACToJavaString(grp.getStatus()));
                innerItemHashMap.put(AUDITLOG_CREATEDATE,convertGRACToJavaString(grp.getCreatedate()));
                innerItemHashMap.put(AUDITLOG_PRIORITY,convertGRACToJavaString(grp.getPriority()));
                GracTApiAuditlogData localItauditdata = grp.getItauditdata();
                GracSApiAuditlogData[] evaluateItemsData = localItauditdata.getItem();
                List<Map<String,Object>> listItemsData =  evaluateItemsData (evaluateItemsData, mitigationSearString);
                innerItemHashMap.put(AUDITLOG_ITAUDITDATA, listItemsData);
                evaluateItems.put(AUDITLOG_ITEM, innerItemHashMap);
                innerItemsData.add(evaluateItems);
            }
        }
        return innerItemsData;
    }

    /**
     * This method will evaluate the Item List Data which is populated at the time of calling GracTApiAuditlogData.getItem(); 
     * @param GracSApiAuditlogData[] evaluateItemsData Parameter will be in the form of list which will contains the details of object GracSApiAuditlogData.
     * @return List<Map<String,Object>> List will be return in the form of <String,Object> as a key,value pair.
     */
    private List<Map<String,Object>> evaluateItemsData( GracSApiAuditlogData[] evaluateItemsData, String mitigationSearString) {
        /*
         <item>
         <Actiondate>20150818122154.0320000 </Actiondate>
         <Actionvalue/>
         <Dependantid/>
         <Description/>
         <Displaystring>Request 1034 of type New Account Submitted by  Basis ( BASIS ) for FNTU994 LNTU994 ( TESTUSER994 ) with Priority Medium</Displaystring>
         <Id>005056881EE71EE591B476A41D84BDE9</Id>
         <Path/>
         <Stage/>
         <Userid>BASIS</Userid>
         */
        List<Map<String,Object>> listItemsData = new ArrayList <Map<String,Object>>();
        if(!Util.isEmpty(evaluateItemsData)) {
            for(GracSApiAuditlogData grpData:evaluateItemsData) { 
                Map<String,Object> evaluateItemsDataMap = new HashMap <String,Object>();
                Map<String,Object> evaluateInternalItemsDataMap = new HashMap <String,Object>();
                evaluateItemsDataMap.put(AUDITLOG_ACTIONDATE,convertGRACToJavaString(grpData.getActiondate()));
                evaluateItemsDataMap.put(AUDITLOG_ACTIONVALUE,convertGRACToJavaString(grpData.getActionvalue()));
                evaluateItemsDataMap.put(AUDITLOG_DEPENDANTID,convertGRACToJavaString(grpData.getDependantid()));
                evaluateItemsDataMap.put(AUDITLOG_DESCRIPTION,convertGRACToJavaString(grpData.getDescription()));
                evaluateItemsDataMap.put(AUDITLOG_DISPLAYSTRING,convertGRACToJavaString(grpData.getDisplaystring()));
                evaluateItemsDataMap.put(AUDITLOG_ID,convertGRACToJavaString(grpData.getId()));
                evaluateItemsDataMap.put(AUDITLOG_PATH,convertGRACToJavaString(grpData.getPath()));
                evaluateItemsDataMap.put(AUDITLOG_STAGE,convertGRACToJavaString(grpData.getStage()));
                evaluateItemsDataMap.put(AUDITLOG_USERID,convertGRACToJavaString(grpData.getUserid()));
                GracTApiAuditlogDataChild gracTApiAuditlogDataChild = grpData.getItauditdatachild();
                GracSApiAuditlogDataChild[] grpDataChild = gracTApiAuditlogDataChild.getItem(); 
                List<Map<String,Object>> evaluateItemsChildData = evaluateItemsChildData(grpDataChild, mitigationSearString);
                evaluateItemsDataMap.put(AUDITLOG_ITAUDITDATACHILD,evaluateItemsChildData);
                evaluateInternalItemsDataMap.put(AUDITLOG_ITEM, evaluateItemsDataMap);
                listItemsData.add(evaluateInternalItemsDataMap);
            } 
        }
        return listItemsData;

    }
    /**
     * This method will evaluate the Child Data of Item List which is populated at the time of calling GracTApiAuditlogDataChild.getItem(); 
     * @param GracSApiAuditlogDataChild[] grpDataChild Parameter will be in the form of list which will contains the details of object GracSApiAuditlogDataChild.
     * @return List<Map<String,Object>> Map of List will be return in the form  <String,Object> as a key,value pair.
     */
    private  List<Map<String,Object>> evaluateItemsChildData(GracSApiAuditlogDataChild[] grpDataChild, String mitigationSearString) {
        /*
         <item>
           <Actiondate>20150818122154.0320000 </Actiondate>
           <Actionvalue/>
           <Dependantid>005056881EE71EE591B476A41D84BDE9</Dependantid>
           <Description/>
           <Displaystring>FISODROLE-ZECDCLN100 ( TESTUSER994 ) role added to the request for action 'Assign' with validity 00.00.0000-00.00.0000</Displaystring>
           <Id>005056881EE71EE591B476A41D84DDE9</Id>
           <Path/>
           <Stage/>
           <Userid>BASIS</Userid>
         */
        List<Map<String,Object>> listMap = new ArrayList <Map<String,Object>>();       
        if(!Util.isEmpty(grpDataChild)) {
            for(GracSApiAuditlogDataChild grpChildData : grpDataChild) {
                Map<String,Object> evaluateItemsChildDataMap = new HashMap <String,Object>();
                Map<String,Object> evaluateInternalItemsChildDataMap = new HashMap <String,Object>();
                evaluateItemsChildDataMap.put(AUDITLOG_ACTIONDATE,convertGRACToJavaString(grpChildData.getActiondate()));
                evaluateItemsChildDataMap.put(AUDITLOG_ACTIONVALUE,convertGRACToJavaString(grpChildData.getActionvalue()));
                evaluateItemsChildDataMap.put(AUDITLOG_DEPENDANTID,convertGRACToJavaString(grpChildData.getDependantid()));
                evaluateItemsChildDataMap.put(AUDITLOG_DESCRIPTION,convertGRACToJavaString(grpChildData.getDescription()));
                evaluateItemsChildDataMap.put(AUDITLOG_DISPLAYSTRING,convertGRACToJavaString(grpChildData.getDisplaystring()));
                if(Util.isNotNullOrEmpty(convertGRACToJavaString(grpChildData.getDisplaystring()))) {
                   if(convertGRACToJavaString(grpChildData.getDisplaystring()).indexOf(mitigationSearString) != -1) {
                      _grcComments.addMitigationStatusMsg(convertGRACToJavaString(grpChildData.getDisplaystring()));
                   }
                }
                evaluateItemsChildDataMap.put(AUDITLOG_ID,convertGRACToJavaString(grpChildData.getId()));
                evaluateItemsChildDataMap.put(AUDITLOG_PATH,convertGRACToJavaString(grpChildData.getPath()));
                evaluateItemsChildDataMap.put(AUDITLOG_STAGE,convertGRACToJavaString(grpChildData.getStage()));
                evaluateItemsChildDataMap.put(AUDITLOG_USERID,convertGRACToJavaString(grpChildData.getUserid()));
                evaluateInternalItemsChildDataMap.put(AUDITLOG_ITEM, evaluateItemsChildDataMap);
                listMap.add(evaluateInternalItemsChildDataMap);
            }
        }
        return listMap;
    }

    /**
     * This function fills the Simulation object based on how the IIQ request is.
     * Entitlements are set based on the operation performed on them i.e. Add or Remove.
     * @param type This indicates type of the entitlement (ROL/PRF) requested. 
     * @param roleName Name of the item.
     * @param action Operation performed on the item.
     * @param connectorName SAP GRC Connector Name.
     * @param simulationGracT Simulation container object.
     */
    public void addSimulationObject(String type, String roleName, String action, String connectorName, GracTWsSimulation simulationGracT) {

        String excludeSimu = null;
        if ( action.equals(PROV_ACTION_ASSIGN) ) {
            // this means it is not setting exclude flag so that item will be considered in the check.
            excludeSimu = EXCLUDE;
        } else if ( action.equals(PROV_ACTION_REMOVE)) {
            // it is setting true for exclude flag so that item will not be considered in the check.
            excludeSimu = INCLUDE;
        }
        GracSWsSimulation[] list = simulationGracT.getItem();
        boolean connectorFound = false;
        if (!Util.isEmpty(list)) {
            // Check if already the same connector is present for same type with same exclude flag,
            // if so add this item also in the same object, do not create new one.
            for ( GracSWsSimulation item : list ) {
                if (item.getConnector().getString().equals(connectorName) && 
                    item.getExcludesimu().getChar1().equals(excludeSimu) &&
                    item.getSimuobtype().getString().equals(type)) {
                    GracTSimobjLst simuList = item.getSimuobjidLst();
                    GracSSimobjLst simObjnListGracS = new GracSSimobjLst();
                    simObjnListGracS.setSimuobjid(getGracString(roleName));
                    simuList.addItem(simObjnListGracS);
                    connectorFound = true;
                }
            }
        }

        // if no matching connector/type/exclude flag found then 
        // create a new object and add this into that.
        if ( !connectorFound ) {
            GracSSimobjLst simObjnListGracS = new GracSSimobjLst();
            simObjnListGracS.setSimuobjid(getGracString(roleName));
            GracTSimobjLst simObjnListGracT = new GracTSimobjLst();
            simObjnListGracT.addItem(simObjnListGracS);
            GracSWsSimulation simulationGracS =  new GracSWsSimulation();
            simulationGracS.setConnector(getGracString(connectorName));
            simulationGracS.setExcludesimu(getGracChar(excludeSimu));
            simulationGracS.setSimuobjidLst(simObjnListGracT);
            simulationGracS.setSimuobtype(getGracString(type));
            simulationGracT.addItem(simulationGracS);
        }
    }

    /**
     * This method will fetch values from reportType of String
     * @param serviceRequest
     * @param reportType
     */
    private void fetchReportType(GracIdmRiskWoutNoServices serviceRequest, String reportType) {
        String[] reportTypeArr = null;
        GracTWsReportType objReportTypeGracT = new GracTWsReportType();
        if (!Util.isEmpty(reportType)){
            reportTypeArr = reportType.split(DELIMETER_COMMA);
            GracSWsReportType[] reportTypeArrGracS = new GracSWsReportType[reportTypeArr.length];
            int numberOfItems = 0;
            for(String reportTypeStr : reportTypeArr) {
                GracSWsReportType objReportTypeGracS = new GracSWsReportType();
                objReportTypeGracS.setReportType(getGracString(reportTypeStr));
                reportTypeArrGracS[numberOfItems++] =  objReportTypeGracS ;
                objReportTypeGracT.setItem(reportTypeArrGracS);
            }
            serviceRequest.setReportType(objReportTypeGracT);
        }
    }
    
    /**
     * This function will test if received parameter is null or not if yes then throws GeneralException
     * 
     * @param parameter
     * @return String
     */
    private String validateAttributes(Object parameter) throws GeneralException {
        if ( ( null != parameter ) && (!(parameter instanceof  String) )) {
            throw new GeneralException("Argument '" + parameter + "' expected in 'String' format.");
        }
        return (String)parameter;
    }
    
    /**
     * This method will prepare the request envolope with different mandatory paramaters which are required for execution of web service GRAC_IDM_RISK_WOUT_NO_SERVICES
     * @param wfc :- workflow context
     * @param serviceStub :- object of stub class of web service GRAC_IDM_RISK_WOUT_NO_SERVICES
     * @return boolean this will detect the risk is there or not.
     */
    public boolean checkGRCViolations(WorkflowContext wfc, ServiceStub  serviceStub) throws RemoteException, GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("Preparing ARA request");
        }
        boolean isRiskFound = false;
        List<String> connectorList = new ArrayList<String>();
        
        Attributes<String, Object> argList = wfc.getArguments();
        
        // check need to add if the provided items are in String. ArrayList or any other type is not allowed.
        
        // ReportType, RiskLevel and RuleSetID can be in multiple values, e.g. "01,02,03" and "GLOBAL,CLIENT_RULESETID"
        
        String reportType = validateAttributes(argList.get(REQUEST_REPORT_TYPE));
        
        String riskLevel = validateAttributes(argList.get(REQUEST_RISK_LEVEL));
        
        String ruleSetId = validateAttributes(argList.get(REQUEST_RULE_SET_ID));
        
        String simulationRiskOnly = validateAttributes(argList.get(SIMULATION_RISK_ONLY));
        
        
        if (log.isDebugEnabled()) {
            log.debug("reportType : " + reportType + "\nriskLevel : " +  riskLevel + "\nruleSetId : " + ruleSetId+ "\nsimulationRiskOnly : " + simulationRiskOnly);
        }
        
        /*
         <ObjectId>
          <item>
           <Objid>SUNILB</Objid>
          </item>
         </ObjectId>
         */
        GracIdmRiskWoutNoServices serviceRequest = new GracIdmRiskWoutNoServices();
        GracTWsApiObjidLst objectIdListGracT = new GracTWsApiObjidLst();
        List<Map<String, String>> userInfoMap = (List<Map<String, String>>) argList.get(REQUEST_USER_INFO_MAP);
        for ( Map<String, String> valueMap : userInfoMap ) {
            String accountName = getStringValueByKey(valueMap, USERINFO_USERID);
            GracSWsApiObjidLst objectIdGracS =  new GracSWsApiObjidLst();
            objectIdGracS.setObjid(getGracString(accountName));
            GracSWsApiObjidLst[] objectIdListGracS = { objectIdGracS };
            objectIdListGracT.setItem(objectIdListGracS);
        }
        serviceRequest.setObjectId(objectIdListGracT); 

        /*
         <ConnectorId>
          <item>
         <Connector>ZECDCLN100</Connector>
         </item>
         </ConnectorId>
         <Simulation>
         <item>
         <Connector>ZECDCLN100</Connector>
         <Simuobtype>ROL</Simuobtype>
         <SimuobjidLst>
         <item>
         <Simuobjid>FISODROLE</Simuobjid>
         </item>
         </SimuobjidLst>
         <Excludesimu></Excludesimu>
         </item>
         </Simulation>
         */
        List<Map<String, String>> requestedLineItemMap = (List<Map<String, String>>) argList.get(REQUEST_REQUESTED_LINE_ITEM_MAP);
        GracTWsApiConnectorLst connectorGracT = new GracTWsApiConnectorLst();
        GracTWsSimulation simulationGracT = new GracTWsSimulation();     
        if (!Util.isEmpty(requestedLineItemMap)) {
            HashSet<String> connectorSet = new HashSet<String>();
            for ( Map<String, String> valueMap : requestedLineItemMap ) {
                String action = getStringValueByKey(valueMap, REQUESTEDITEMS_PROVACTION); 
                String itemName = getStringValueByKey(valueMap, REQUESTEDITEMS_ITEMNAME);
                String itemType = getStringValueByKey(valueMap, REQUESTEDITEMS_PROVITEMTYPE);
                String connectorName = getStringValueByKey(valueMap, REQUESTEDITEMS_CONNECTOR);
                if (connectorSet.add(connectorName)) {
                    GracSWsApiConnectorLst connectorGracS = new GracSWsApiConnectorLst();
                    connectorGracS.setConnector(getGracString(connectorName)); 
                    connectorGracT.addItem(connectorGracS);
                    connectorList.add(connectorName);
                }
                addSimulationObject(itemType, itemName, action, connectorName, simulationGracT);

                //in the request following code will add  <ObjectType>USR</ObjectType> tag
                String objectType = getStringValueByKey(valueMap, OBJECT_TYPE_USER);
                if (Util.isNotNullOrEmpty(objectType)) {
                    serviceRequest.setObjectType(getGracString(objectType));
                } else {
                    serviceRequest.setObjectType(getGracString(USER));
                }

                // report format will be set as  <ReportFormat>DETAILED</ReportFormat> or any other 

                String reportFormat = getStringValueByKey(valueMap, REPORT_FORMAT);
                if (Util.isNotNullOrEmpty(reportFormat)) {
                    serviceRequest.setReportFormat(getGracString(reportFormat.trim()));
                } else {
                    // By default with ETN CONUMSHIAN-300 report format is set to <ReportFormat>2</ReportFormat>
                    serviceRequest.setReportFormat(getGracString(REPORTFORMAT)); 
                }
            }
            connectorList.clear();
            connectorList =  null;

            /*uptill now the code will create the envolope request as <Connector>ZECDCLN100</Connector>
              <Simuobtype>ROL</Simuobtype>
              <SimuobjidLst>
              <item>
              <Simuobjid>FISODROLE</Simuobjid>
              </item>
              </SimuobjidLst>
              <Excludesimu></Excludesimu> */

            // Setting GRC Connector Name.This can come in single or multiple format
            serviceRequest.setConnectorId(connectorGracT);
            
            fetchReportType(serviceRequest,reportType);
            // Setting simulation objects like   </Simulation>
            serviceRequest.setSimulation(simulationGracT);

            //following code will add   <SimulationRiskOnly>S</SimulationRiskOnly> tag in request envelope
            // This parameter is set to "X" when you want to check risk against provided items for check.
            // In our case we have to consider the existing roles assigned to the user plus our provided items.            
            // so setting this as EMPTY if not sent from workflow.
            if(Util.isNotNullOrEmpty(simulationRiskOnly)){
                if(simulationRiskOnly.trim().length() > 1){
                    if (log.isErrorEnabled()) {
                        log.error("Simulation Risk Only should contain single character value!");
                    }
                    throw new GeneralException("Simulation Risk Only should contain a single character value!");
                }
                serviceRequest.setSimulationRiskOnly(getGracChar(simulationRiskOnly.trim()));
            } else {
                serviceRequest.setSimulationRiskOnly(getGracChar(EXCLUDE));
            }          
            
            
            //To check risk against multiple risk levels we are passing each risk level and calculating risk.
            //if risk is detected against any riskLevel we are not checking further just returning back the risk found flag.
            
            if (!Util.isEmpty(riskLevel)) {
                String[] riskLevelArr = null;
                riskLevelArr = riskLevel.split(DELIMETER_COMMA);
                for (String riskLevelItem : riskLevelArr) {
                    serviceRequest.setRiskLevel(getGracString(riskLevelItem.trim()));
                    if (!Util.isEmpty(ruleSetId)) {
                        // If RuleSetIDs are provided then combination of RiskLevel and RuleSetID added to request.
                        if ( populateRuleSetIds(ruleSetId, serviceRequest, serviceStub) ) {
                            if (log.isDebugEnabled()) {
                                log.debug("Risk found - RiskLevel : " + riskLevelItem);
                            }
                            isRiskFound = true;
                            break;
                        }
                    } else {
                        // Only RiskLevel in the request.
                        if ( calculateRisk(serviceStub,serviceRequest) ) {
                            isRiskFound = true;
                            break;
                        }
                    }
                }
            } else if (!Util.isEmpty(ruleSetId)) {
                // If RiskLevel is not provided but RuleSetID is provided.
                if ( populateRuleSetIds(ruleSetId, serviceRequest, serviceStub) ) {
                    isRiskFound = true;
                }
            } else {
                isRiskFound = calculateRisk(serviceStub,serviceRequest);
            }
        }
        return isRiskFound;
    }

    /**
     * The function will insert ruleSetId in the request and execute it. Function returns true if risk is found.
     * @param ruleSetId - RuleSetID list in String format, comma delimited if multivalued.
     * @param serviceRequest - ServiceRequest object.
     * @param serviceStub - Service Stub object.
     * @return boolean value - True if risk found.
     * @throws RemoteException
     * @throws GeneralException
     */
    private boolean populateRuleSetIds(String ruleSetId, GracIdmRiskWoutNoServices serviceRequest, ServiceStub  serviceStub) throws RemoteException, GeneralException {
        boolean isRisky = false;
        String[] ruleSetIdArr = null;
        ruleSetIdArr = ruleSetId.split(DELIMETER_COMMA);
        for (String ruleSetIdItem : ruleSetIdArr) {
            serviceRequest.setRuleSetId(getGracString(ruleSetIdItem.trim()));
            if ( calculateRisk(serviceStub,serviceRequest) ) {
                if (log.isDebugEnabled()) {
                    log.debug("Risk found - RuleSet ID : " + ruleSetIdItem);
                }
                isRisky = true;
                break;
            }
        }
        return isRisky;
    }
    /**
     * The function will insert riskLevel in the request and execute it. Function returns true if risk is found.
     * @param serviceStub - Service Stub object.
     * @param serviceRequest - ServiceRequest object.
     * @return boolean value - True if risk found.
     * @throws RemoteException
     * @throws GeneralException
     */
    private boolean calculateRisk(ServiceStub  serviceStub, GracIdmRiskWoutNoServices serviceRequest)throws RemoteException, GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("Calculating ARA request");
        }
        boolean isRiskFound = false;
        printXml(serviceRequest);
        // fire the request
        GracIdmRiskWoutNoServicesResponse serviceResponse = serviceStub.gracIdmRiskWoutNoServices( serviceRequest );
        // Reading Response
        if ( null != serviceResponse ) {
            GracSWsApiMessage returnMessage = serviceResponse.getMsgReturn();
            if ( null != returnMessage ) {
                Char3 msgNo = returnMessage.getMsgNo();
                if ( null != msgNo ) {
                    String messageNo = msgNo.getChar3();
                    // 0 = SUCCESS  and  4 = ERROR
                    if ( messageNo.equals(MSG_CODE_SUCCESS)) {
                        GracTWsApiRiskData  gracTWsApiRiskData  = serviceResponse.getRiskAnalysisWithoutNoData();
                        // if gracTWsApiRiskData contains the items that means there is risk because in those items the risk data will be displayed
                        //otherwise if there will be no risk then the tag will be come like </RiskAnalysisWithoutNoData> in the response.
                        if (gracTWsApiRiskData.isItemSpecified()) {
                            isRiskFound = true;
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Execution of the Access Risk Analysis Web service resulted in error for account(s) " + usersRequested);
                        }
                        String messageType = null, messageStatement = null;
                        Char7 msgTypeChar7 = returnMessage.getMsgType();
                        if ( null != msgTypeChar7) {
                            messageType = msgTypeChar7.toString();
                        }
                        functions.rfc.sap.document.sap_com.String msgStatement = returnMessage.getMsgStatement();
                        if ( null != msgStatement) {
                            messageStatement = msgStatement.toString();
                        }
                        if (Util.isNotNullOrEmpty(messageStatement) && Util.isNotNullOrEmpty(messageType)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Execution of the Access Risk Analysis Web service resulted in error. Message Type : " + messageType + ", Message Reason : " + messageStatement);
                            }
                            throw new GeneralException("Execution of the Access Risk Analysis Web service resulted in error. Message Type : " + messageType + ", Message Reason : " + messageStatement);
                        }
                        throw new GeneralException("Execution of the Access Risk Analysis Web service resulted in error.");
                    }
                }
            }
        }
        return isRiskFound;
    }
}
