package sailpoint.connector.sm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class GetTargetPermissions {

    public GetTargetPermissions() {
    }

    public List<Map<String, Object>> getResourcesAndPermissions(String strAppName, Map<String, String> objectList)
            throws Exception {
        List<Map<String, Object>> resourceList = new ArrayList<Map<String, Object>>();
        try {
            Application App = SailPointFactory.getCurrentContext().getObject(Application.class, strAppName);
            String strHost = App.getStringAttributeValue("host");
            String strMSCSName = App.getStringAttributeValue("MscsName");
            String strMSCSType = App.getStringAttributeValue("MscsType");
            String strMSCSAdmin = App.getStringAttributeValue("user");
            int nPort = Integer.valueOf(App.getStringAttributeValue("port"));
            String desKeyFileName = App.getStringAttributeValue("EncryptionKeyFileName");
            String strEncrypt = App.getStringAttributeValue("Encryption");
            String password = App.getStringAttributeValue("password");
            if (Util.isNotNullOrEmpty(password))
                password = SailPointFactory.getCurrentContext().decrypt(password);

            boolean limitNumbOftrans = App.getBooleanAttributeValue("limitNumberOfTransactions");
            if (!limitNumbOftrans) {
                limitNumbOftrans = App.getBooleanAttributeValue("limitNumberOftransactions");
            }
            if (strEncrypt == null || "".equals(strEncrypt))
                strEncrypt = "-"; // The encryption should be either set to correct value or -. Empty value will not work.
            String ebcdicCharSet = App.getStringAttributeValue("IBMcharacterSet");

            String charSet = App.getStringAttributeValue("characterSet");

            if (charSet == null || "".equals(charSet.trim())) {
                //set to default charset
                charSet = "ISO-8859-1";
            }

            boolean isSSLEnabled = App.getBooleanAttributeValue("TLSEnabled");

            String timeOut = (String) App.getAttributeValue(Configuration.SM_READ_TIMEOUT);

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
                }
            }

            String smSocketConnectRetry = (String) App.getAttributeValue(Configuration.SM_SOCKET_CONNECT_RETRY);

            String strMaxActiveTrans = (String) App.getAttributeValue(Configuration.SM_MAX_ACTIVE_TRANSACTIONS);

            boolean disableHostnameVerification = Util
                    .atob(App.getStringAttributeValue(SMConstants.DISABLE_HOSTNAME_VERIFICATION));
            String cgCertSubject = App.getStringAttributeValue(SMConstants.CG_CERT_SUBJECT);

            SMWrapper smWrapper = new SMWrapper(App.getName(), strHost, nPort, strMSCSName, strMSCSType, strMSCSAdmin,
                    strEncrypt, desKeyFileName, charSet, sm_timeout, limitNumbOftrans, ebcdicCharSet, isSSLEnabled,
                    Util.atob(App.getStringAttributeValue("disableOnePhaseAggregation")), smSocketConnectRetry,
                    strMaxActiveTrans, strMSCSAdmin, password, disableHostnameVerification, cgCertSubject);
            resourceList = smWrapper.downloadResourceAndPermissions("resource", 1024, App, objectList);
        } catch (GeneralException e) {
            throw new Exception(e.getMessage(), e);
        }
        return resourceList;
    }
}
