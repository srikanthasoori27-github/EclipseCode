package sailpoint.connector.sm;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceEvent;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;

public class SMInterceptor implements Runnable {
    private static Log log = LogFactory.getLog(SMInterceptor.class);
    Application _app;
    int _retryInterval;
    ConnectSM objConnectSM;
    static short countRS = 0, countMsg = 0, countConfirm = 0;

    public SMInterceptor(Application appl, int retryInterval) throws GeneralException {
        _app = appl;
        _retryInterval = retryInterval;
    }

    public void connectToGateway() throws InterruptedException {
        String strSMHost = (String) _app.getAttributeValue("host");
        String strSMPort = (String) _app.getAttributeValue("port");

        int port = Integer.parseInt(strSMPort);

        String mscsType = (String) _app.getAttributeValue("MscsType");

        String encryption = (String) _app.getAttributeValue("Encryption"); // Encryption method used in SM

        String desKeyFileName = (String) _app.getAttributeValue("EncryptionKeyFileName");
        String charSet = (String) _app.getAttributeValue("characterSet");

        if (charSet == null || "".equals(charSet.trim())) {
            //default charset
            charSet = "ISO-8859-1";
        }

        String ebcdicCharSet = (String) _app.getAttributeValue("IBMcharacterSet");

        boolean isSSLEnabled = _app.getBooleanAttributeValue("TLSEnabled");

        boolean isConnected = false;

        String smSocketConnectRetry = (String) _app.getAttributeValue(Configuration.SM_SOCKET_CONNECT_RETRY);

        String strMaxActiveTrans = (String) _app.getAttributeValue(Configuration.SM_MAX_ACTIVE_TRANSACTIONS);

        while (!isConnected) {
            SailPointContext spc = null;
            try {
                boolean disableOnePhaseAggregation = Util
                        .atob(_app.getStringAttributeValue("disableOnePhaseAggregation"));

                String appUserName = _app.getStringAttributeValue(Connector.CONFIG_USER); 
                String password = _app.getStringAttributeValue(Connector.CONFIG_PASSWORD);
                if (Util.isNotNullOrEmpty(password)) {
                    spc = SailPointFactory.createContext();
                    password = spc.decrypt(password);
                }
                    

                boolean disableHostnameVerification = Util
                        .atob(_app.getStringAttributeValue(SMConstants.DISABLE_HOSTNAME_VERIFICATION));
                String cgCertSubject = _app.getStringAttributeValue(SMConstants.CG_CERT_SUBJECT);

                SMWrapper smWrapperObj = new SMWrapper(_app.getName(), strSMHost, port,
                        _app.getStringAttributeValue("MscsName"), mscsType, appUserName, encryption, desKeyFileName,
                        charSet, (int) (Long.MAX_VALUE / (60 * 1000)), false, ebcdicCharSet, isSSLEnabled,
                        disableOnePhaseAggregation, smSocketConnectRetry, strMaxActiveTrans, appUserName, password,
                        disableHostnameVerification, cgCertSubject);
                objConnectSM = smWrapperObj.m_objConnectSM;
                isConnected = true;
            } catch (GeneralException e) {
                if (log.isErrorEnabled())
                    log.error("Connection to Connector Gateway to application " + _app.getName()
                            + " not available. Retry after " + _retryInterval + " minutes.");
                Thread.sleep(_retryInterval * 60 * 1000);
            } finally {
                releaseContext(spc);
            }
        }
    }

    public void startInterceptions() throws Exception {

        String strMSCSName, strMSCSAdmin, strMSCSType, strAppUserName, strAppPassword = "";

        strMSCSName = objConnectSM.GetMSCSName();
        strMSCSType = objConnectSM.GetMSCSType();
        strMSCSAdmin = objConnectSM.GetMSCSAdmin();
        strAppUserName = objConnectSM.getM_strAppUserName();
        strAppPassword = objConnectSM.getM_strAppPassword();

        long ThreadId = 0;
        ThreadId = Thread.currentThread().getId();
        StringBuffer strReqGenHeader = new StringBuffer("S"); ///S for interceptions

        String strSIID = SMConstants.GenerateSIID();

        strReqGenHeader.append(strSIID);

        strReqGenHeader.append("000001"); //Seq ID

        strReqGenHeader.append(SMConstants.strDataCenterID);

        strReqGenHeader.append(SMConstants.strAppID);

        strReqGenHeader.append(SMConstants.strWorkstationID);

        strReqGenHeader.append("        ");

        strReqGenHeader.append("T").append(objConnectSM.GetEncryptionType());

        strReqGenHeader.append("RS001");

        StringBuffer strReqRSHeader = new StringBuffer();

        String charSet = (String) _app.getAttributeValue("characterSet");

        if (charSet != null && "UTF-8".equals(charSet.trim())) {
            strReqRSHeader.append(SMConstants.strTransactionIDForUTF8);
        } else {
            strReqRSHeader.append(SMConstants.strTransactionID);
        }
        strReqRSHeader.append(SMConstants.strActionID);

        //MSCS NAME
        strReqRSHeader.append(SMConstants.ConvertToHexString(strMSCSName.length(), 3));
        strReqRSHeader.append(strMSCSName);

        //MSCSType
        strReqRSHeader.append(SMConstants.ConvertToHexString(strMSCSType.length(), 3));
        strReqRSHeader.append(strMSCSType);

        //MSCSAdmin
        strReqRSHeader.append(SMConstants.ConvertToHexString(strMSCSAdmin.length(), 3));
        strReqRSHeader.append(strMSCSAdmin);

        strReqRSHeader.append("000");

        //HotPath
        strReqRSHeader.append("001");
        //hot path=off
        strReqRSHeader.append("2");

        //AddInfo Value length size
        strReqRSHeader.append(SMConstants.strAddInfoValueLenSize);

        //??
        strReqRSHeader.append("0000000000");
        strReqRSHeader.append("000");

        if (!objConnectSM.disableOnePhaseAggregation) {
            strReqRSHeader.append(SMConstants.strPE2Version);
        }
        
        //Appending EX: So that MF Agent can parse user credentials easily.
        strReqRSHeader.append("PE2EX:");
        
        //Appending the count of extended attributes before appending extended attributes to the message.
        strReqRSHeader.append(SMConstants.ConvertToHexString(2, 1));
        
        //Appending App Admin credentials i.e. username and password
        strReqRSHeader.append(SMConstants.ConvertToHexString(strAppUserName.length(), 3));
        strReqRSHeader.append(strAppUserName);
        
        if (Util.isNotNullOrEmpty(strAppPassword)) {
            strReqRSHeader.append(SMConstants.ConvertToHexString(strAppPassword.length(), 3));
            strReqRSHeader.append(strAppPassword);
        } else {
            strReqRSHeader.append("000");
        }

        strReqGenHeader.append(SMConstants.ConvertToHexString((4 + strReqRSHeader.length()), 4));

        strReqGenHeader.append(strReqRSHeader);

        String strReq = "a " + SMConstants.ConvertToHexString(strReqGenHeader.length(), 8) + strReqGenHeader;
        if (log.isInfoEnabled()) {
            log.info("ThreadId: " + ThreadId + " Starting smInterceptor Handshake for Application " + _app.getName());
        }

        objConnectSM.Send(strReq);

        try {
            String strResponse = objConnectSM.Receive();

            String strTemp = "T" + objConnectSM.GetEncryptionType() + "CC";
            while (!strResponse.contains(strTemp)) {
                strResponse = objConnectSM.Receive();
            }

            strReqGenHeader = new StringBuffer("S");
            strReqGenHeader.append(strSIID);

            strReqGenHeader.append("000002");

            strReqGenHeader.append(SMConstants.strDataCenterID);

            strReqGenHeader.append(SMConstants.strAppID);

            strReqGenHeader.append(SMConstants.strWorkstationID);

            strReqGenHeader.append("        ");

            strReqGenHeader.append("T").append(objConnectSM.GetEncryptionType());

            strReqGenHeader.append("IV");

            strReq = "a " + SMConstants.ConvertToHexString(strReqGenHeader.length(), 8) + strReqGenHeader;

            objConnectSM.Send(strReq);

            strResponse = objConnectSM.Receive();

            strTemp = "T" + objConnectSM.GetEncryptionType() + "IV";

            while (!strResponse.contains(strTemp)) {
                strResponse = objConnectSM.Receive();
            }

            strReqGenHeader = new StringBuffer("S");
            strReqGenHeader.append(strSIID);

            strReqGenHeader.append("000003");

            strReqGenHeader.append(SMConstants.strDataCenterID);

            strReqGenHeader.append(SMConstants.strAppID);

            strReqGenHeader.append(SMConstants.strWorkstationID);

            strReqGenHeader.append("        ");

            strReqGenHeader.append("L").append(objConnectSM.GetEncryptionType());

            strReqGenHeader.append("FF");

            strReq = "a " + SMConstants.ConvertToHexString(strReqGenHeader.length(), 8) + strReqGenHeader;

            objConnectSM.Send(strReq);

        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("ThreadId :" + ThreadId + "Exception in startInterceptions:" + e.getMessage(), e);
            }
            throw e;
        }

        if (log.isInfoEnabled()) {
            log.info("ThreadId: " + ThreadId + " Completed smInterceptor Handshake for Application " + _app.getName()
                    + " successfully");
        }
    }

    public void run() {
        long ThreadId = Thread.currentThread().getId();
        try {
            connectToGateway();
            startInterceptions();
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(
                        "ThreadId: " + ThreadId + " Exception while connecting to Connector Gateway " + e.getMessage(),
                        e);
            }
        }

        //get list of all applications on cluster
        SailPointContext spc = null;
        Map<ApplicationDataKey, ApplicationData> applicationDatas = new HashMap<ApplicationDataKey, ApplicationData>();
        Map<String, String> transactions = new HashMap<String, String>();
        try {
            spc = SailPointFactory.createContext();
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("cluster", _app.getCluster()));
            List<Application> appsInCluster = spc.getObjects(Application.class, ops);
            if (appsInCluster == null) {
                appsInCluster = new ArrayList<Application>();
            }
            if (appsInCluster.size() == 0) {
                // Always fall back to the app that we already know about
                appsInCluster.add(_app);
            }

            for (Application appInCluster : appsInCluster) {
                if (log.isTraceEnabled()) {
                    log.trace("ThreadId: " + ThreadId + " apps --> " + appInCluster.getName());
                }

                ApplicationData data = new ApplicationData(appInCluster);
                applicationDatas.put(data.getKey(), data);
            }
        } catch (GeneralException e1) {
            if (log.isErrorEnabled())
                log.error(
                        "ThreadId: " + ThreadId + " Exception while getting Clustered Applications " + e1.getMessage(),
                        e1);
        } finally {
            releaseContext(spc);
        }

        while (true) {
            try {
                String strInterception = objConnectSM.Receive();
                // Create a fresh context every time that we receive
                spc = SailPointFactory.createContext();

                String strSIID = new String();
                if (countMsg < 0) {
                    countMsg = 0;
                    countConfirm = 0;
                    countRS = 0;
                }

                if (strInterception.contains("KEEPALIVE_MESSAGE") || (strInterception.charAt(29) == 'L' && strInterception.charAt(31) == 'R')) {
                    log.debug("Keep alive message received");
                    continue;
                }

                if (strInterception.length() < 33) {
                    continue;
                }
                strSIID = strInterception.substring(1, 10);

                if ("RS".equals(strInterception.substring(31, 33))) {
                    transactions.put(strSIID, strInterception);
                    countRS++;
                } else {
                    if (!transactions.isEmpty() && !transactions.containsKey(strSIID)) {
                        if (log.isErrorEnabled())
                            log.error("ThreadId: " + ThreadId + " Ignoring message: " + strInterception
                                    + "\nThe start message for SIID" + strSIID
                                    + " is not found in active transactions.");
                        continue;
                    }

                    //If the MSD LAST bit is T then send CC
                    // You will find CC message in download, check confirm download
                    if(strInterception.length() > 39 && strInterception.charAt(39) == 'T') {
                        sendInterceptionConfirmation(strInterception);
                        countConfirm++;
                    }
                    
                    String strRSMsg = transactions.get(strSIID);
                    transactions.remove(strSIID);
                    countMsg++;

                    //Get managed system name and type
                    String strRequired = strRSMsg.substring(48);
                    int currIndex = 0;
                    int mscsNameLength = Integer.parseInt(strRequired.substring(currIndex, 3), 16);
                    currIndex = currIndex + 3;
                    String mscsName = strRequired.substring(currIndex, currIndex + mscsNameLength);

                    currIndex = currIndex + mscsNameLength;

                    int mscsTypeLength = Integer.parseInt(strRequired.substring(currIndex, currIndex + 3), 16);
                    currIndex = currIndex + 3;
                    String mscsType = strRequired.substring(currIndex, currIndex + mscsTypeLength);

                    ApplicationDataKey key = new ApplicationDataKey(mscsType, mscsName);
                    ApplicationData currentApplicationData = applicationDatas.get(key);
                    String encoding = currentApplicationData.getEncoding();

                    if (currentApplicationData == null) {
                        // This should never happen.  Prior to bug 22852 we skipped this check
                        // without running into problems, but better safe than sorry
                        continue;
                    } else if (log.isInfoEnabled()) {
                        log.info("ThreadId: " + ThreadId + " Receieved a new interception with SIID: " + strSIID
                                + " for Application " + currentApplicationData.getName());
                    }

                    //if its Last (L) packet, no need to send confirmation
                    if (strInterception.charAt(29) != 'L' && strInterception.charAt(29) != ' ') {
                        //send confirmation for interception
                        sendConfirmation(strSIID);
                        countConfirm++;
                    }

                    String operation = strInterception.substring(31, 33);

                    //AA - Add User
                    //UA - Update User
                    //DA - Delete User
                    //RA - Revoke User
                    //PA - Password Changed
                    //NA - Rename User
                    //MA - Move User
                    //SA - Special User Sync
                    //UC - Add Connection

                    if ("PA".equals(operation)) {
                        Map<String, Object> attrs = parsePassword(strInterception, encoding);

                        String password = (String) attrs.get("password");
                        String userId = (String) attrs.get("USER_ID");
                        String appName = currentApplicationData.getName();
                        SailPointContext context = SailPointFactory.getCurrentContext();
                        IdentityLifecycler cycler = new IdentityLifecycler(context);
                        cycler.launchPasswordIntercept(appName, userId, password);
                        if (log.isInfoEnabled()) {
                            log.info("ThreadId: " + ThreadId + " Adding a new " + operation
                                    + " interception with SIID: " + strSIID + " for processing");
                        }
                    } else {
                        ResourceEvent resEvent = ResourceEventFactory.createResourceEvent(ThreadId, operation,
                                strInterception, currentApplicationData);
                        if (resEvent != null) {
                            if (log.isInfoEnabled()) {
                                log.info("ThreadId: " + ThreadId + " Adding a new " + operation
                                        + " interception with SIID: " + strSIID + " for processing");
                            }
                            if (log.isTraceEnabled()) {
                                log.trace("ThreadId: " + ThreadId + " Creating a ResourceEvent: " + resEvent.toXml());
                            }
                            spc.saveObject(resEvent);
                            spc.commitTransaction();
                        }
                    }
                }
            } catch (IOException e) {
                if (log.isErrorEnabled()) {
                    log.error("ThreadId: " + ThreadId + " IOException: " + e.getMessage(), e);
                }

                try {
                    connectToGateway();
                    startInterceptions();
                } catch (Exception ex) {
                    if (log.isErrorEnabled()) {
                        log.error("ThreadId: " + ThreadId
                                + " Exception while connecting to Connector Gateway to application - " + _app.getName()
                                + ". Retry after  " + _retryInterval + " Minutes..." + ex.getMessage(), ex);
                    }

                    try {
                        Thread.sleep(_retryInterval * 60 * 1000);
                    } catch (InterruptedException e1) {
                        if (log.isErrorEnabled())
                            log.error(
                                    "ThreadId: " + ThreadId + " Unable to keep the thread in sleep mode."
                                            + ex.getMessage(), ex);
                    }
                }
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error("ThreadId: " + ThreadId + " Exception: " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    if (log.isErrorEnabled())
                        log.error(
                                "ThreadId: " + ThreadId + " Unable to keep the thread in sleep mode." + e.getMessage(),
                                e);
                }
            } finally {
                releaseContext(spc);
            }
            if (log.isDebugEnabled())
                log.debug("ThreadId: " + ThreadId + " Counters - Init msgs received: " + countRS + " Data received: "
                        + countMsg + " Confirmations sent: " + countConfirm);
        }
    }

    @Untraced
    private Map<String, Object> parsePassword(String strInterception, String encoding)
            throws UnsupportedEncodingException {

        Map<String, Object> mapKeywords = new HashMap<String, Object>();

        //This is string which contains data we required...ignore other part
        String strRequired = strInterception.substring(61);

        byte[] msgBytes = strRequired.getBytes(encoding);

        int nTempLen = 0;
        int nCurIndex = 0;
        nTempLen = Integer.valueOf(new String(msgBytes, nCurIndex, 3), 16).intValue();

        nCurIndex += 3;

        mapKeywords.put("USER_ID", new String(msgBytes, nCurIndex, nTempLen, encoding));

        nCurIndex += nTempLen;

        nTempLen = Integer.valueOf(new String(msgBytes, nCurIndex, 3), 16).intValue();
        nCurIndex += 3;
        nCurIndex += nTempLen;

        nTempLen = Integer.valueOf(new String(msgBytes, nCurIndex, 3), 16).intValue();
        nCurIndex += 3;
        nCurIndex += nTempLen;

        nTempLen = Integer.valueOf(new String(msgBytes, nCurIndex, 3), 16).intValue();
        nCurIndex += 3;
        mapKeywords.put("password", new String(msgBytes, nCurIndex, nTempLen, encoding));

        return mapKeywords;

    }

    public void sendConfirmation(String strSIID) throws IOException {
        StringBuffer strReqGenHeader = new StringBuffer("U");

        strReqGenHeader.append(strSIID);

        strReqGenHeader.append("000001");

        strReqGenHeader.append(SMConstants.strDataCenterID);

        strReqGenHeader.append(SMConstants.strAppID);

        strReqGenHeader.append(SMConstants.strWorkstationID);

        strReqGenHeader.append("WSUSERID");

        strReqGenHeader.append("L").append(objConnectSM.GetEncryptionType());

        strReqGenHeader.append("CC");

        String strReq = "a " + SMConstants.ConvertToHexString(strReqGenHeader.length(), 8) + strReqGenHeader;

        objConnectSM.Send(strReq);
    }

    /**
     * Used to send the confirmation message to the mainframe connector via
     * connector gateway. Agent sends interceptions in data chunks. Once first chunk
     * is sent from agent, it sends message with MSD Last Bit as "T". After this
     * message agent expects confirmation from PE2 for sending next chunk of
     * interceptions. Agent will not send next interceptions till it receives this
     * confirmation message. This chunk limit is configurable at agent side.
     */
    public void sendInterceptionConfirmation(String strInterception) throws IOException {
        
        //Need to increment sequence number. As it may be a defect in case agent starts checking for it.
        String confirmationMessage = strInterception.substring(10, 41).concat("CC");
        confirmationMessage = "a " + SMConstants.ConvertToHexString(confirmationMessage.length(), 8) + confirmationMessage;
        objConnectSM.Send(confirmationMessage);
    }
    
    static class ApplicationDataKey {
        private String mscsType;
        private String mscsName;

        ApplicationDataKey(ApplicationData applicationData) {
            //To fix the issue caused by CONMF-278 we need to convert MSCS name and MSCS type in upper case.
            mscsType = applicationData.getMscsType().toUpperCase();
            mscsName = applicationData.getMscsName().toUpperCase();
        }

        ApplicationDataKey(final String mscsType, final String mscsName) {
            this.mscsType = mscsType;
            this.mscsName = mscsName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mscsName == null) ? 0 : mscsName.hashCode());
            result = prime * result + ((mscsType == null) ? 0 : mscsType.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ApplicationDataKey other = (ApplicationDataKey) obj;
            if (mscsName == null) {
                if (other.mscsName != null)
                    return false;
            } else if (!mscsName.equals(other.mscsName))
                return false;
            if (mscsType == null) {
                if (other.mscsType != null)
                    return false;
            } else if (!mscsType.equals(other.mscsType))
                return false;
            return true;
        }
    }

    /**
     * This class maintains all the ApplicationData required by the
     * SMInterceptor to prevent it from accidentally bringing the Application
     * itself into a persistent state. The reasons for this are two-fold: 1. We
     * don't want to constantly re-fetch the same application 2. We don't want
     * to accidentally modify the application
     * 
     * Note that this class is exposed at the package level for the sake of the
     * unit tests.
     */
    static class ApplicationData {
        private String name;
        private String groupAttribute;
        private String mscsType;
        private String mscsName;
        private String encoding;
        private String multiColumnSeparator;
        private Set<String> accountAttributeNames;
        private Set<String> multiValuedAccountAttributeNames;
        private Set<String> groupAttributeNames;
        private Set<String> multiValuedGroupAttributeNames;
        private Map<String, String> userAdminMap;
        private Application applicationReference;

        ApplicationData(Application application) {
            name = application.getName();

            Schema accountSchema = application.getSchema("account");
            if (accountSchema == null) {
                if (log.isErrorEnabled())
                    log.error("IdentityIQ cannot process the " + name
                            + " application because it is missing its account schema.");
            } else {
                if (accountSchema.getAttributeDefinition(SMConstants.groupAttribute) != null) {
                    groupAttribute = SMConstants.groupAttribute;
                }
            }

            Object mscsTypeValue = application.getAttributeValue("MscsType");
            if (mscsTypeValue != null) {
                mscsType = mscsTypeValue.toString();
            } else {
                mscsType = "";
            }

            Object mscsNameValue = application.getAttributeValue("MscsName");
            if (mscsNameValue != null) {
                mscsName = mscsNameValue.toString();
            } else {
                mscsName = "";
            }

            //connector = new SMConnector(appl);
            //we need encoding while parsing the data in interceptions
            encoding = (String) application.getAttributeValue("characterSet");

            if (encoding == null || "".equals(encoding.trim())) {
                //default charset
                encoding = "ISO-8859-1";
            }

            multiColumnSeparator = Util.otos(application.getAttributeValue("multiColumnSeperator"));

            accountAttributeNames = new HashSet<String>();
            multiValuedAccountAttributeNames = new HashSet<String>();
            populateAttributes(accountSchema, accountAttributeNames, multiValuedAccountAttributeNames);

            groupAttributeNames = new HashSet<String>();
            multiValuedGroupAttributeNames = new HashSet<String>();
            Schema groupSchema = application.getSchema("group");
            if (groupSchema != null) {
                populateAttributes(groupSchema, groupAttributeNames, multiValuedGroupAttributeNames);
            }

            userAdminMap = (Map<String, String>) application.getAttributeValue("UserAdminMap");

            applicationReference = application;
        }

        private void populateAttributes(Schema schema, Set<String> attributeNames, Set<String> multiValuedAttributeNames) {
            List<AttributeDefinition> attributes = schema.getAttributes();
            if (!Util.isEmpty(attributes)) {
                for (AttributeDefinition attribute : attributes) {
                    String attributeName = attribute.getInternalOrName();
                    if (attribute.isMulti()) {
                        multiValuedAttributeNames.add(attributeName);
                    }
                    attributeNames.add(attributeName);
                }
            }
        }

        public String getName() {
            return name;
        }

        public String getGroupAttribute() {
            return groupAttribute;
        }

        public String getMscsType() {
            return mscsType;
        }

        public String getMscsName() {
            return mscsName;
        }

        public String getEncoding() {
            return encoding;
        }

        public String getMultiColumnSeparator() {
            return multiColumnSeparator;
        }

        public Set<String> getAccountAttributeNames() {
            return Collections.unmodifiableSet(accountAttributeNames);
        }

        public boolean isMultiValuedAccountAttribute(String attributeName) {
            return multiValuedAccountAttributeNames.contains(attributeName);
        }

        public boolean isMultiValuedGroupAttribute(String attributeName) {
            return multiValuedGroupAttributeNames.contains(attributeName);
        }

        public Map<String, String> getUserAdminMap() {
            return userAdminMap;
        }

        /**
         * @return an Application object for the purpose of attaching a
         *         reference to the plan. Note that this application will be in
         *         a detached state and should *ONLY* be used to attach a
         *         reference to the plan. Any properties required from the
         *         application should be stored and/or accessed through the
         *         ApplicationData object
         */
        public Application getApplicationReference() {
            return applicationReference;
        }

        public ApplicationDataKey getKey() {
            return new ApplicationDataKey(this);
        }
    }

    /**
     * This class generates ResourceEvents for the SMInterceptor. It's exposed
     * public for the sake of unit testing.
     */
    public static class ResourceEventFactory {
        public static ResourceEvent createResourceEvent(long threadId, String operation, String interception,
                ApplicationData applicationData) throws GeneralException, UnsupportedEncodingException {
            ProvisioningPlan plan;

            if ("AC".equals(operation) || "UC".equals(operation) || "DC".equals(operation)) {
                plan = createGroupPlanForUser(threadId, operation, interception, applicationData);
            } else if ("AA".equals(operation) || "UA".equals(operation) || "DA".equals(operation)
                    || "VA".equals(operation)) {
                plan = createAccountPlan(threadId, operation, interception, applicationData);
            } else if ("AB".equals(operation) || "UB".equals(operation) || "DB".equals(operation)) {
                plan = createGroupPlanForGroup(threadId, operation, interception, applicationData);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Skipping operation: " + operation);
                }
                return null;
            }

            ResourceEvent resourceEvent = new ResourceEvent();
            resourceEvent.setApplication(applicationData.getApplicationReference());
            resourceEvent.setPlan(plan);
            return resourceEvent;
        }

        private static ProvisioningPlan createGroupPlanForUser(long threadId, String operation, String interception,
                ApplicationData applicationData) throws GeneralException, UnsupportedEncodingException {
            String groupAttribute = applicationData.getGroupAttribute();

            if (Util.isNullOrEmpty(groupAttribute)) {
                throw new GeneralException(
                        "Attempted connection operation is unsupported because groups Attribute is not defined in account schema");
            }
            Map<String, Object> attrs = parseConnection(interception, applicationData.getEncoding(), groupAttribute);

            AccountRequest accRequest = new AccountRequest();
            accRequest.setOperation(AccountRequest.Operation.Modify);
            accRequest.setNativeIdentity((String) attrs.get("USER_ID"));
            accRequest.setApplication(applicationData.getName());
            AttributeRequest attrReq = new AttributeRequest();
            if ("AC".equals(operation) || "UC".equals(operation)) {
                attrReq.setOperation(Operation.Add);
            } else {
                attrReq.setOperation(Operation.Remove);
            }
            attrReq.setName(groupAttribute);
            attrReq.setValue(attrs.get(groupAttribute));
            accRequest.add(attrReq);
            ProvisioningPlan provPlan = new ProvisioningPlan();
            provPlan.add(accRequest);
            if (log.isDebugEnabled()) {
                log.debug("ThreadId: " + threadId + " Provisioning plan: " + provPlan.toXml());
            }
            return provPlan;
        }

        private static ProvisioningPlan createAccountPlan(long threadId, String operation, String interception,
                ApplicationData applicationData) throws GeneralException, UnsupportedEncodingException {

            interception = interception.substring(61);
            String encoding = applicationData.getEncoding();

            Map<String, Object> attrs = parseResponseUser(interception.getBytes(encoding), applicationData);

            AccountRequest accRequest = new AccountRequest();
            if ("AA".equals(operation)) {
                setAccountStatus(attrs);
                fillAccountRequest(accRequest, attrs);
                accRequest.setOperation(AccountRequest.Operation.Create);
            } else if ("UA".equals(operation) || "VA".equals(operation)) {
                //first set account status
                setAccountStatus(attrs);

                if (attrs.containsKey("groups")) {
                    attrs.remove("groups");
                }

                fillAccountRequest(accRequest, attrs);
                accRequest.setOperation(AccountRequest.Operation.Modify);
            } else {
                accRequest.setOperation(AccountRequest.Operation.Delete);
            }

            accRequest.setNativeIdentity((String) attrs.get("USER_ID"));
            accRequest.setApplication(applicationData.getName());

            ProvisioningPlan provPlan = new ProvisioningPlan();
            provPlan.add(accRequest);

            if (log.isDebugEnabled()) {
                log.debug("ThreadId: " + threadId + " Provisioning plan: " + provPlan.toXml());
            }
            return provPlan;
        }

        private static void fillAccountRequest(AccountRequest accRequest, Map<String, Object> attrs) {
            Set<String> keys = attrs.keySet();
            Iterator<String> iterator = keys.iterator();

            while (iterator.hasNext()) {
                AttributeRequest attrReq = new AttributeRequest();

                String attrName = iterator.next();
                attrReq.setName(attrName);

                attrReq.setValue(attrs.get(attrName));
                attrReq.setOperation(Operation.Set);

                accRequest.add(attrReq);
            }
        }

        private static ProvisioningPlan createGroupPlanForGroup(long threadId, String operation, String interception,
                ApplicationData applicationData) throws GeneralException, UnsupportedEncodingException {
            ObjectRequest objRequest = new ObjectRequest();
            objRequest.setType(SMConstants.GROUP);
            interception = interception.substring(61);
            Map<String, Object> attrs = parseResponseGroup(interception.getBytes(applicationData.getEncoding()),
                    applicationData);
            if ("AB".equals(operation)) {
                fillObjectRequest(objRequest, attrs);
                objRequest.setOp(ObjectOperation.Create);
            } else if ("UB".equals(operation)) {
                fillObjectRequest(objRequest, attrs);
                objRequest.setOp(ObjectOperation.Modify);
            } else {
                objRequest.setOp(ObjectOperation.Delete);
            }

            objRequest.setNativeIdentity((String) attrs.get("GROUP_ID"));
            objRequest.setApplication(applicationData.getName());

            ProvisioningPlan provPlan = new ProvisioningPlan();
            provPlan.addObjectRequest(objRequest);

            if (log.isDebugEnabled()) {
                log.debug("ThreadId: " + threadId + " Provisioning plan: " + provPlan.toXml());
            }

            return provPlan;
        }

        private static void fillObjectRequest(ObjectRequest objRequest, Map<String, Object> attrs) {
            Set<String> keys = attrs.keySet();
            Iterator<String> iterator = keys.iterator();

            while (iterator.hasNext()) {
                AttributeRequest attrReq = new AttributeRequest();

                String attrName = iterator.next();
                attrReq.setName(attrName);

                attrReq.setValue(attrs.get(attrName));
                attrReq.setOperation(Operation.Set);

                objRequest.add(attrReq);
            }
        }

        private static Map<String, Object> parseConnection(String strInterception, String encoding,
                String strGrpAttribute) throws UnsupportedEncodingException {
            Map<String, Object> mapKeywords = new HashMap<String, Object>();

            //This is string which contains data we required...ignore other part
            String strRequired = strInterception.substring(61);

            byte[] msgBytes = strRequired.getBytes(encoding);

            int nTempLen = 0;
            int nCurIndex = 0;
            nTempLen = Integer.valueOf(new String(msgBytes, nCurIndex, 3), 16).intValue();

            nCurIndex += 3;

            mapKeywords.put(strGrpAttribute, new String(msgBytes, nCurIndex, nTempLen, encoding));

            nCurIndex += nTempLen;

            nTempLen = Integer.valueOf(new String(msgBytes, nCurIndex, 3), 16).intValue();

            nCurIndex += 3;

            mapKeywords.put("USER_ID", new String(msgBytes, nCurIndex, nTempLen, encoding));

            return mapKeywords;
        }

        @Untraced
        private static Map<String, Object> parseResponseUser(byte[] strMsg, ApplicationData applicationData)
                throws UnsupportedEncodingException {
            List<String> attNames = new ArrayList<String>();
            attNames.addAll(applicationData.getAccountAttributeNames());
            String encoding = applicationData.getEncoding();

            int nTempLen = 0;
            int nCurIndex = 0;
            Map<String, Object> mapKeywords = new HashMap<String, Object>();
            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("USER_ID"))
            mapKeywords.put("USER_ID", new String(strMsg, nCurIndex, nTempLen, encoding));
            attNames.remove("USER_ID");

            nCurIndex += nTempLen;

            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("USER_OE_PR"))
            mapKeywords.put("USER_OE_PR", new String(strMsg, nCurIndex, nTempLen, encoding));
            attNames.remove("USER_OE_PR");

            nCurIndex += nTempLen;

            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("UG_DEF"))
            mapKeywords.put("UG_DEF", new String(strMsg, nCurIndex, nTempLen, encoding));
            attNames.remove("UG_DEF");
            nCurIndex += nTempLen;

            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("USER_PWD"))
            mapKeywords.put("password", new String(strMsg, nCurIndex, nTempLen, encoding));
            attNames.remove("password");
            nCurIndex += nTempLen;

            //Password Life 
            //1 - Permanent
            //2 - Reset
            //3 - Ignore
            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("PWD_LIFE"))
            mapKeywords.put("PWD_LIFE", new String(strMsg, nCurIndex, nTempLen, encoding));
            attNames.remove("PWD_LIFE");
            nCurIndex += nTempLen;

            //User Status
            //1 - Revoke
            //2 - Restore
            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("User_STA"))
            mapKeywords.put("User_STA", new String(strMsg, nCurIndex, nTempLen, encoding));
            attNames.remove("User_STA");
            nCurIndex += nTempLen;

            //User Admin
            //1 - User
            //2 - Auditor
            //3 - Admin
            //4 - Both
            //5 - Ignore
            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("USER_ADMIN"))
            Map<String, String> userAdminMap = applicationData.getUserAdminMap();
            String userAdminVal = new String(strMsg, nCurIndex, nTempLen, encoding);
            String userAdminLabel = userAdminVal;
            if (userAdminMap != null) {
                userAdminLabel = userAdminMap.get(userAdminVal);
            }
            mapKeywords.put("USER_ADMIN", userAdminLabel);
            attNames.remove("USER_ADMIN");
            nCurIndex += nTempLen;

            //Default Connection Change Action
            //1 - keep as regular
            //2 - drop
            //3 - Ignore
            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            //if (attNames.contains("DEF_UG_ACT"))
            mapKeywords.put("DEF_UG_ACT", new String(strMsg, nCurIndex, nTempLen, encoding));
            attNames.remove("DEF_UG_ACT");
            nCurIndex += nTempLen;

            //AddInfo
            int nAddInfoCount = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            char delimit = 0x01;

            char delimitForCol = 0x02;

            String smMultiColumnSeperator = applicationData.getMultiColumnSeparator();

            if (smMultiColumnSeperator == null) {
                smMultiColumnSeperator = "#";
            }

            for (int i = 0; i < nAddInfoCount; i++) {
                nCurIndex += 2; //Keyword type

                nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 2), 16).intValue();
                nCurIndex += 2;

                String strKeyword = new String(strMsg, nCurIndex, nTempLen, encoding);
                nCurIndex += nTempLen;

                nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 4), 16).intValue();
                nCurIndex += 4;

                String strValue = new String(strMsg, nCurIndex, nTempLen, encoding);
                nCurIndex += nTempLen;

                // We receive attribute name from agent in upper case so we need to convert it to camel case as we have
                // stored it in camel case in the schema.
                if (strKeyword.equalsIgnoreCase(SMConstants.ATTRIBUTE_ACC_DIRECT_PERMISSIONS)) {
                    strKeyword = SMConstants.ATTRIBUTE_ACC_DIRECT_PERMISSIONS;
                } else if (strKeyword.equalsIgnoreCase(SMConstants.ATTRIBUTE_ACC_INDIRECT_PERMISSIONS)) {
                    strKeyword = SMConstants.ATTRIBUTE_ACC_INDIRECT_PERMISSIONS;
                }

                if (strKeyword.equalsIgnoreCase("RU_SUSPENDED") || strKeyword.equalsIgnoreCase("RU_LOCKED")) {
                    mapKeywords.put(strKeyword, strValue);
                } else {
                    if (applicationData.isMultiValuedAccountAttribute(strKeyword)) {

                        if (strValue.length() > 0) {
                            String newVal = "";
                            for (int k = 0; k < strValue.length(); k++) {
                                char ch = strValue.charAt(k);
                                if (ch == delimitForCol) {
                                    newVal = newVal + smMultiColumnSeperator;
                                    continue;
                                }
                                newVal = newVal + ch;
                            }
                            strValue = newVal;
                        }

                        StringTokenizer st = new StringTokenizer(strValue, String.valueOf(delimit));
                        List<String> tmpList = new ArrayList<String>();

                        while (st.hasMoreTokens()) {
                            tmpList.add(st.nextToken());
                        }

                        mapKeywords.put(strKeyword, tmpList);
                    } else {
                        if (attNames.contains(strKeyword)) {
                            mapKeywords.put(strKeyword, strValue);
                        }
                    }

                    attNames.remove(strKeyword);

                    if (attNames.size() > 0) {
                        continue;
                    }
                }
            }

            return mapKeywords;
        }

        private static Map<String, Object> parseResponseGroup(byte[] strMsg, ApplicationData applicationData)
                throws UnsupportedEncodingException {
            int nTempLen = 0;
            int nCurIndex = 0;
            String encoding = applicationData.getEncoding();

            Map<String, Object> mapKeywords = new HashMap<String, Object>();
            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();

            nCurIndex += 3;
            mapKeywords.put("GROUP_ID", new String(strMsg, nCurIndex, nTempLen, encoding));
            nCurIndex += nTempLen;

            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;
            mapKeywords.put("GROUP_OE_PR", new String(strMsg, nCurIndex, nTempLen, encoding));
            nCurIndex += nTempLen;

            nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;
            mapKeywords.put("GROUP_PR", new String(strMsg, nCurIndex, nTempLen, encoding));
            nCurIndex += nTempLen;

            //AddInfo
            int nAddInfoCount = Integer.valueOf(new String(strMsg, nCurIndex, 3), 16).intValue();
            nCurIndex += 3;

            char delimit = 0x01;
            char delimitForCol = 0x02;

            String smMultiColumnSeperator = applicationData.getMultiColumnSeparator();

            if (smMultiColumnSeperator == null) {
                smMultiColumnSeperator = "#";
            }

            for (int i = 0; i < nAddInfoCount; i++) {
                nCurIndex += 2; //Keyword type

                nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 2), 16).intValue();
                nCurIndex += 2;
                String strKeyword = new String(strMsg, nCurIndex, nTempLen, encoding);
                nCurIndex += nTempLen;

                nTempLen = Integer.valueOf(new String(strMsg, nCurIndex, 4), 16).intValue();
                nCurIndex += 4;
                String strValue = new String(strMsg, nCurIndex, nTempLen, encoding);
                nCurIndex += nTempLen;

                // We receive attribute name from agent in upper case so we need to convert it to camel case as we have
                // stored it in camel case in the schema.
                if (strKeyword.equalsIgnoreCase(SMConstants.ATTRIBUTE_GRP_DIRECT_PERMISSIONS)) {
                    strKeyword = SMConstants.ATTRIBUTE_GRP_DIRECT_PERMISSIONS;
                }

                if (applicationData.isMultiValuedGroupAttribute(strKeyword)) {
                    if (strValue.length() > 0) {
                        String newVal = "";
                        for (int k = 0; k < strValue.length(); k++) {
                            char ch = strValue.charAt(k);
                            if (ch == delimitForCol) {
                                newVal = newVal + smMultiColumnSeperator;
                                continue;
                            }
                            newVal = newVal + ch;
                        }
                        strValue = newVal;
                    }

                    StringTokenizer st = new StringTokenizer(strValue, String.valueOf(delimit));
                    List<String> tmpList = new ArrayList<String>();

                    while (st.hasMoreTokens()) {
                        tmpList.add(st.nextToken());
                    }

                    mapKeywords.put(strKeyword, tmpList);
                } else {
                    mapKeywords.put(strKeyword, strValue);
                }
            }
            return mapKeywords;
        }

        private static void setAccountStatus(Map<String, Object> record) {
            /*
             * This is added to populate attributes checked by IIQ to show the
             * account status if RU_SUSPENDED keyword is present then
             * ATT_IIQ_DISABLED should be set to the value of this keyword
             * ATT_IIQ_LOCKED should be set based on the value of RU_LOCKED If
             * RU_SUSPENDED is not present then we assume that the Application
             * only supports revoke but not lock. In this case ATT_IIQ_DISABLED
             * is set based on the value of UserStatus ATT_IIQ_LOCKED is always
             * set to false
             */
            if (record.containsKey("RU_SUSPENDED")) {
                if (record.get("RU_SUSPENDED").equals("Y")) {
                    record.put(Connector.ATT_IIQ_DISABLED, Boolean.TRUE);
                } else {
                    record.put(Connector.ATT_IIQ_DISABLED, Boolean.FALSE);
                }
                if (record.containsKey("RU_LOCKED") && record.get("RU_LOCKED").equals("Y")) {
                    record.put(Connector.ATT_IIQ_LOCKED, Boolean.TRUE);
                } else {
                    record.put(Connector.ATT_IIQ_LOCKED, Boolean.FALSE);
                }
            } else {
                if (record.containsKey("User_STA")) {
                    if (record.get("User_STA").equals("1")) {
                        record.put(Connector.ATT_IIQ_DISABLED, Boolean.TRUE);
                        record.put(Connector.ATT_IIQ_LOCKED, Boolean.FALSE);
                    } else {
                        record.put(Connector.ATT_IIQ_DISABLED, Boolean.FALSE);
                        record.put(Connector.ATT_IIQ_LOCKED, Boolean.FALSE);
                    }
                }
            }
        }
    }

    private void releaseContext(SailPointContext context) {
        if (context != null) {
            try {
                SailPointFactory.releaseContext(context);
            } catch (GeneralException e) {
                if (log.isErrorEnabled())
                    log.error("Failed to release SailPointContext", e);
            }
        }
    }
}
