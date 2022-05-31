/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointFactory;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.InvalidConfigurationException;
import sailpoint.connector.sm.GetTargetPermissions;
import sailpoint.connector.sm.SMConstants;
import sailpoint.object.AccessMapping;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.PE2SiteConfig;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Schema;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.connector.sm.SMWrapper;

/**
 * PE2TargetCollector - Class that initiates the execution of Target collections
 * for Mainframe based connectors like RACF, TSS and ACF2
 */

public class PE2TargetCollector extends AbstractTargetCollector {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(PE2TargetCollector.class);

    /**
     * Configuration item found in the Map that contains a list of Target
     * objects to give per target information
     */
    public static final String ATT_PE2_SITE_LIST = "PE2Collections";

    public PE2TargetCollector(TargetSource source) {
        super(source);
    }

    /**
     * AbstractTargetCollector methods
     */
    @Override
    public CloseableIterator<Target> iterate(Map<String, Object> options) throws GeneralException {

        String appName = getStringAttribute("PE2ApplName");
        Application app = SailPointFactory.getCurrentContext().getObject(Application.class, appName);

        // Stop target aggregation if AccountDirectPermissions, AccountInDirectPermissions or GroupDirectPermissions
        // is present in account/ group schema respectively.
        if (app.getType().equalsIgnoreCase("ACF2 - Full")) {
            Schema accSchema = app.getSchema(Schema.TYPE_ACCOUNT);
            Schema grpSchema = app.getSchema(Schema.TYPE_GROUP);
            if (accSchema.getAttributeNames().contains("AccountDirectPermissions")
                    || accSchema.getAttributeNames().contains("AccountIndirectPermissions")
                    || grpSchema.getAttributeNames().contains("GroupDirectPermissions")) {
                InvalidConfigurationException ex = new InvalidConfigurationException();
                ex.setDetailedError(
                        "Could not start target aggregation as permission attributes are present in schema.");
                ex.setPossibleSuggestion("Remove AccountDirectPermissions, AccountIndirectPermissions"
                        + " attributes from account schema and GroupDirectPermissions attribute from group schema.");
                throw new GeneralException(ex);
            }
        }

        return new TargetIterator(options);
    }

    @Override
    public void testConfiguration() throws GeneralException {
        throw new GeneralException("Method not supported");
    }

    /*
     * Override update method to revoke a permission.
     */
    @Override
    public ProvisioningResult update(AbstractRequest req) throws GeneralException {

        if (_log.isDebugEnabled())
            _log.debug("Abstract request received: " + req.toXml());

        ProvisioningResult result = new ProvisioningResult();
        List<PermissionRequest> perms = req.getPermissionRequests();
        if (perms != null) {
            for (PermissionRequest perm : perms) {
                try {
                    handlePermissionRequest(perm, req.getApplicationName());
                } catch (Exception e) {
                    if (_log.isErrorEnabled())
                        _log.error("Exception handling permission request: " + e.getMessage(), e);
                    result = new ProvisioningResult();
                    result.setStatus(ProvisioningResult.STATUS_FAILED);
                    result.addError(e.getMessage());
                    perm.setResult(result);
                }
            }
        }

        if (_log.isDebugEnabled())
            _log.debug("Result sent: " + result.toXml());
        return result;
    }

    /*
     * Method to initiate revoke permission request to deleteTargetPermission
     */
    private void handlePermissionRequest(PermissionRequest perm, String strAppName) throws Exception {
        ProvisioningResult provResult = new ProvisioningResult();
        Application app = SailPointFactory.getCurrentContext().getObjectByName(Application.class, strAppName);

        if (_log.isDebugEnabled())
            _log.debug("Permission request received: " + perm.toString());

        String target = perm.getTarget();
        String rights = perm.getRights();
        String strHost = app.getStringAttributeValue("host");
        String strMSCSName = app.getStringAttributeValue("MscsName");
        String strMSCSType = app.getStringAttributeValue("MscsType");
        String strMSCSAdmin = app.getStringAttributeValue("user");
        int nPort = Integer.valueOf(app.getStringAttributeValue("port"));
        String desKeyFileName = app.getStringAttributeValue("EncryptionKeyFileName");
        String strEncrypt = app.getStringAttributeValue("Encryption");
        if (strEncrypt == null || "".equals(strEncrypt))
            strEncrypt = "-"; // The encryption should be either set to correct value or -. Empty value will not work.
        
        String password = app.getStringAttributeValue("password");
        if (Util.isNotNullOrEmpty(password))
            password = SailPointFactory.getCurrentContext().decrypt(password);
        
        String ebcdicCharSet = app.getStringAttributeValue("IBMcharacterSet");

        String charSet = app.getStringAttributeValue("characterSet");

        if (charSet == null || "".equals(charSet.trim())) {
            //set to default charset
            charSet = "ISO-8859-1";
        }
        boolean limitNumbOftrans = app.getBooleanAttributeValue("limitNumberOfTransactions");
        if (!limitNumbOftrans) {
            limitNumbOftrans = app.getBooleanAttributeValue("limitNumberOftransactions");
        }

        String timeOut = (String) app.getAttributeValue(Configuration.SM_READ_TIMEOUT);

        if (Util.isNullOrEmpty(timeOut)) {
            timeOut = (String) Configuration.getSystemConfig().get(Configuration.SM_READ_TIMEOUT);
        }

        //default timeout value will be 10 minutes
        int sm_timeout = 10;
        if (timeOut != null && !"".equals(timeOut)) {
            try {
                sm_timeout = Integer.parseInt(timeOut);
            } catch (NumberFormatException ex) {
                //do nothing...even if there is problem with timeout value, we will continue with default one
                if (_log.isErrorEnabled())
                    _log.error("Incorrect timeout received. considering default value of timeout" + ex.getMessage(), ex);
                sm_timeout = 10;
            }
        }

        boolean isSSLEnabled = app.getBooleanAttributeValue("TLSEnabled");

        String smSocketConnectRetry = (String) app.getAttributeValue(Configuration.SM_SOCKET_CONNECT_RETRY);

        String strMaxActiveTrans = (String) app.getAttributeValue(Configuration.SM_MAX_ACTIVE_TRANSACTIONS);

        boolean disableHostnameVerification = Util
                .atob(app.getStringAttributeValue(SMConstants.DISABLE_HOSTNAME_VERIFICATION));
        String cgCertSubject = app.getStringAttributeValue(SMConstants.CG_CERT_SUBJECT);

        SMWrapper smWrapper = new SMWrapper(app.getName(), strHost, nPort, strMSCSName, strMSCSType, strMSCSAdmin,
                strEncrypt, desKeyFileName, charSet, sm_timeout, limitNumbOftrans, ebcdicCharSet, isSSLEnabled,
                Util.atob(app.getStringAttributeValue("disableOnePhaseAggregation")), smSocketConnectRetry,
                strMaxActiveTrans, strMSCSAdmin, password, disableHostnameVerification, cgCertSubject);

        // execute revocation logic
        Map<String, Object> addInfoKeys = new HashMap<String, Object>();
        Map<String, String> accountDetails = new HashMap<String, String>();

        addInfoKeys = (Map<String, Object>) app.getAttributeValue("splTargetPermissionsInterestingKwds");
        boolean result = smWrapper.deleteTargetPermission(target, rights, addInfoKeys, accountDetails, app);

        // if everything is fine then set result

        provResult = new ProvisioningResult();
        if (result == true)
            provResult.setStatus(ProvisioningResult.STATUS_COMMITTED);
        else
            provResult.setStatus(ProvisioningResult.STATUS_FAILED);

        if (_log.isDebugEnabled())
            _log.debug("Result sent: " + provResult);
        perm.setResult(provResult);
    }

    ///////////////////////////////////////////////////////////////////
    //
    // Target Permission Iterators
    //
    ///////////////////////////////////////////////////////////////////

    public class TargetIterator implements CloseableIterator<Target> {
        private List<Target> _Targets = null;
        List<PE2SiteConfig> configs = new ArrayList();

        @SuppressWarnings("unchecked")
        public TargetIterator(Map<String, Object> options) throws GeneralException {
            configs = new ArrayList(getListAttribute(ATT_PE2_SITE_LIST));
        }

        /**
         * Processes targets one after the another
         */
        public boolean hasNext() {
            if (_Targets != null && !_Targets.isEmpty()) {
                return true;
            }
            boolean bRetVal = false;
            while (!configs.isEmpty() && bRetVal == false) {
                try {
                    if (_Targets == null) {
                        _Targets = new ArrayList<Target>();
                    }
                    String strGetType, strGetGeneric, strGetUnit, strGetVolume, strAppName, strResName, strResType;
                    Map<String, String> objectList = new HashMap<String, String>();
                    List<Map<String, Object>> resourceList = new ArrayList<Map<String, Object>>();
                    GetTargetPermissions targetPermissions = new GetTargetPermissions();
                    resourceList = null;

                    //Get the Target details from object.
                    PE2SiteConfig objConfig = configs.get(0);
                    strResName = objConfig.getTargetName();
                    strResType = objConfig.getTargetType();
                    strGetType = objConfig.getType();
                    strGetGeneric = objConfig.getGeneric();
                    strGetUnit = objConfig.getUnit();
                    strGetVolume = objConfig.getVolume();

                    if (_log.isDebugEnabled()) {
                        _log.debug("Processing Resource:" + strResName + " of type:" + strResType);
                    }

                    objectList.put("RES.NAME", strResName);
                    objectList.put("RES.TYPE", strResType);
                    objectList.put("GET.TYPE", strGetType);
                    objectList.put("GET.GENERIC", strGetGeneric);
                    objectList.put("GET.UNIY", strGetUnit);
                    objectList.put("GET.VOLUME", strGetVolume);

                    strAppName = getStringAttribute("PE2ApplName");

                    configs.remove(0);
                    try {
                        // Get the Resources and Permissions 
                        resourceList = targetPermissions.getResourcesAndPermissions(strAppName, objectList);
                        String nativeId, permissions, key;
                        boolean isUser;
                        if (resourceList != null && !resourceList.isEmpty()) {
                            for (Map<String, Object> oneResource : resourceList) {
                                Set s = oneResource.entrySet();
                                Iterator i = s.iterator();
                                key = null;
                                while (i.hasNext()) {
                                    List<TargetAccess> lstPerm = new ArrayList<TargetAccess>();
                                    Map.Entry entry = (Map.Entry) i.next();
                                    key = (String) entry.getKey();
                                    List<sailpoint.connector.sm.Download.TargetAccess> lstUserPermissions = (List<sailpoint.connector.sm.Download.TargetAccess>) entry
                                            .getValue();
                                    Iterator lstIterator = lstUserPermissions.iterator();
                                    while (lstIterator.hasNext()) {
                                        sailpoint.connector.sm.Download.TargetAccess oneObjTargetAccess = (sailpoint.connector.sm.Download.TargetAccess) lstIterator
                                                .next();
                                        isUser = oneObjTargetAccess.isUser();
                                        nativeId = oneObjTargetAccess.getLoginName();
                                        permissions = oneObjTargetAccess.getAccess();
                                        TargetAccess objTargetAccess = new TargetAccess(nativeId, permissions, isUser);
                                        lstPerm.add(objTargetAccess);
                                        if (_log.isDebugEnabled())
                                            _log.debug("Resource:" + key + " Native ID:" + nativeId + " Permissions:"
                                                    + permissions + " Is user:" + isUser);
                                    }
                                    if (key != null && lstPerm != null) {
                                        Target obj = buildTarget(key, lstPerm);
                                        _Targets.add(obj);
                                        key = null;
                                        lstPerm = null;
                                    }
                                }
                            }
                            if (!_Targets.isEmpty())
                                bRetVal = true;

                        }
                    } catch (ConnectorException e) {
                        if (_log.isErrorEnabled())
                            _log.error("Caught Connector Exception", e);
                        //e.printStackTrace();
                        throw new RuntimeException(e.getMessage(), e);
                    }
                } catch (Exception e) {
                    if (_log.isErrorEnabled())
                        _log.error("Caught General Exception", e);
                    //e.printStackTrace();
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            return bRetVal;
        }

        private Target buildTarget(String fqName, List<TargetAccess> perms) {
            Target target = new Target();
            target.setName(fqName);
            target.setFullPath(fqName);
            List<AccessMapping> groupAccess = new ArrayList<AccessMapping>();
            List<AccessMapping> userAccess = new ArrayList<AccessMapping>();

            for (TargetAccess mapping : perms) {
                AccessMapping targetMapping = new AccessMapping();
                String userName = mapping.getLoginName();
                targetMapping.setNativeIds(Util.asList(userName));
                if (mapping.isUser()) {
                    userAccess.add(targetMapping);
                } else
                    groupAccess.add(targetMapping);
                String rights = mapping.getAccess();
                targetMapping.setRights(rights);
                if (_log.isDebugEnabled()) {
                    _log.debug("Rights on [" + target.getName() + "] for [" + userName + "] ==>" + rights);
                }
                if (userAccess.size() > 0)
                    target.setAccountAccess(userAccess);
                if (groupAccess.size() > 0)
                    target.setGroupAccess(groupAccess);
            }

            return target;
        }

        public Target next() {
            Target nextTarget = _Targets.remove(0);
            if (nextTarget == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            if (_log.isInfoEnabled()) {
                _log.info("Returning Target Name [" + nextTarget.getName() + "]");
            }
            printProgress();
            return nextTarget;
        }

        public void close() {
            //_PE2sites = null;
        }

        public void remove() {
            throw new UnsupportedOperationException("Remove is unsupported.");
        }

        protected void printProgress() {

        }
    }//End of class TargetIterator

    public class TargetAccess {
        public String _LoginName = "";
        public String _Access = "";
        public boolean _isUser = true;

        public TargetAccess(String LoginName, String Access, boolean isUser) {
            _LoginName = LoginName;
            _Access = Access;
            _isUser = isUser;
        }

        public String getLoginName() {
            return _LoginName;
        }

        public String getAccess() {
            return _Access;
        }

        public boolean isUser() {
            return _isUser;
        }
    }
}
