/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementation of IntegrationExecutor interface used for system testing.
 *
 * This object will talk with the DelimitedFileConnector based applications
 * and update the file to include any changes that are provisioned through
 * this executor.
 *
 * Initially designed for my unittest purposes when testing our new LCM
 * server side. This will give my tests someway to persist changes to the file
 * that were requested via provisioning plans.
 *
 * Right now, this integration assumes there are attributes named disabled, 
 * locked and password.
 *
 * TODO: 
 *   1) make the disable,lock and password concepts configurable on app?
 *   2) rule for ro to csv ?
 *   3) nested assumes csv
 * 
 * Author: Dan
 *
 */

package sailpoint.integration;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.integration.ProvisioningPlan.AccountRequest;
import sailpoint.integration.ProvisioningPlan.AttributeRequest;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Implementation of IntegrationExecutor interface used for system testing.
 * 
 * As of 5.5 this class has been deprecated in favor of the 
 * DelimitedFileConnector's implementation, which uses the
 * newer interface and gets the sailpoint.object.Provisioning
 * plan instead of dealing with the integration based model.
 * 
 * @See sailpoint.connector.DelimitedFileConnector
 */
@Deprecated
public class DelimitedFileExecutor extends AbstractIntegrationExecutor {

    private static Log log = LogFactory.getLog(DelimitedFileExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public DelimitedFileExecutor() {
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationInterface
    //
    //////////////////////////////////////////////////////////////////////

    Application _application;
    Connector _connector;

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(Map args) throws Exception {
        super.configure(args);
    }

    public String ping() throws Exception {
        println("DelimitedFileExecutor: ping");
        return "pingggggggg......";
    }

    @SuppressWarnings("unchecked")
    public RequestResult provision(String identity, ProvisioningPlan plan) throws Exception {
        RequestResult result = new RequestResult();

        println("DelimitedFileExecutor: provision " + identity);

        if (plan != null) {
            // check test condition
            if (isSimmualtedRetry(plan)) {
                result.setStatus(RequestResult.STATUS_RETRY);
            } else {
                List<AccountRequest> accounts = plan.getAccountRequests();
                if (Util.size(accounts) > 0) {
                    for (AccountRequest account : accounts) {
                        try {
                            handleAccountRequest(account);
                        } catch (Exception e) {
                            if (log.isErrorEnabled()) {
                                log.error("Execption handling accountRequest : "
                                        + new sailpoint.object.ProvisioningPlan.AccountRequest(account.toMap()).toXml()
                                        + e.toString(), e);
                            }

                            result.addError(e);
                        }
                    }
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void handleAccountRequest(AccountRequest account) throws Exception {
        String appName = account.getApplication();
        if (appName == null) {
            throw new GeneralException("Application name was not found on account request.");
        }

        _application = SailPointFactory.getCurrentContext().getObjectByName(Application.class, appName);
        if (_application == null) {
            throw new GeneralException("Application [" + appName + " was not found.");
        }

        String connectorClass = (_application != null) ? _application.getConnector() : null;

        if (Util.nullSafeCompareTo(connectorClass, "sailpoint.connector.DelimitedFileConnector") == 0) {
            _connector = ConnectorFactory.createConnector(_application.getConnector(), _application, null);
        } else {
            throw new GeneralException("DelimitedFileExecutor is configued and being called "
                    + "for non DelimitedFileConnector provisioning request! Requested connector class was "
                    + connectorClass);
        }

        String op = account.getOperation();
        String accountId = account.getNativeIdentity();

        if (ProvisioningPlan.OP_ACCOUNT_CREATE.equals(op)) {
            //
            // Account create
            //
            ResourceObject obj = null;
            try {
                obj = _connector.getObject(Connector.TYPE_ACCOUNT, accountId, null);
            } catch (Exception e) {
                // this is the hope, since we can't create something that exists
            }

            if (obj != null) {
                throw new Exception("Object already exists [" + accountId + "]");
            }

            // create
            obj = create(account);

            // update
            updateObject(obj, true, false);

            // if its a create request move on...
            return;
        }

        ResourceObject ro = _connector.getObject(Connector.TYPE_ACCOUNT, accountId, null);
        if (ro == null) {
            throw new Exception("Unable to find account with id[" + accountId + "]");
        }

        if (ProvisioningPlan.OP_ACCOUNT_DELETE.equals(op)) {
            //
            // Account Delete
            //
            updateObject(ro, false, true);

            if (log.isInfoEnabled()) {
                log.info("Account [" + accountId + "] deleted.");
            }
        } else if (ProvisioningPlan.OP_ACCOUNT_DISABLE.equals(op)) {
            //
            // Account Disable
            //
            ro.setAttribute(Connector.ATT_IIQ_DISABLED, new Boolean(true));
            updateObject(ro, false, false);

            if (log.isInfoEnabled()) {
                log.info("Account [" + accountId + "] revoked.");
            }
        } else if (ProvisioningPlan.OP_ACCOUNT_ENABLE.equals(op)) {
            //
            // Account Enable
            //
            ro.setAttribute(Connector.ATT_IIQ_DISABLED, new Boolean(false));
            updateObject(ro, false, false);

            if (log.isInfoEnabled()) {
                log.info("Account [" + accountId + "] restored.");
            }
        } else if (ProvisioningPlan.OP_ACCOUNT_LOCK.equals(account.getOperation())) {
            //
            // Account Lock
            //
            ro.setAttribute(Connector.ATT_IIQ_LOCKED, new Boolean(true));
            updateObject(ro, false, false);

            if (log.isInfoEnabled()) {
                log.info("Account [" + accountId + "] locked.");
            }
        } else if (ProvisioningPlan.OP_ACCOUNT_UNLOCK.equals(account.getOperation())) {
            //
            // Account Unlock
            //
            ro.setAttribute(Connector.ATT_IIQ_LOCKED, new Boolean(false));
            updateObject(ro, false, false);

            if (log.isInfoEnabled()) {
                log.info("Account [" + accountId + "] unlocked.");
            }
        } else if (ProvisioningPlan.OP_ACCOUNT_MODIFY.equals(account.getOperation())) {
            //
            // Account General-Modify
            //
            List<AttributeRequest> attributes = account.getAttributeRequests();
            if (attributes != null) {
                for (AttributeRequest att : attributes) {
                    modify(att, ro);
                }
                updateObject(ro, false, false);
            }

            if (log.isInfoEnabled()) {
                log.info("Account [" + accountId + "] modified.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ResourceObject create(AccountRequest account) throws Exception {
        ResourceObject obj = new ResourceObject();

        // Use RO here for ease since we already have a modify() method that
        // takes one
        List<AttributeRequest> attributes = account.getAttributeRequests();
        if (Util.size(attributes) > 0) {
            for (AttributeRequest att : attributes) {
                modify(att, obj);
            }
        }

        Map<String, Object> objectAttributes = obj.getAttributes();
        String nativeIdentity = account.getNativeIdentity();
        if (Util.getString(nativeIdentity) == null) {
            throw new Exception("Account request did not include nativeIdentity.");
        }

        // Always add in the identity attribute explicitly to the attribute list
        // so we can use the defaultTransformObject method
        // The default method handles displayAttribute, instance and do any
        // necessary coersion that is required based on the schema.
        Schema accountSchema = _application.getAccountSchema();
        String identityAttr = accountSchema.getIdentityAttribute();
        objectAttributes.put(identityAttr, nativeIdentity);

        obj.setIdentity(nativeIdentity);
        obj.setDisplayName(nativeIdentity);
        obj.setObjectType(accountSchema.getObjectType());

        // Removed compilation dependency of the AbstractConnector by canceling the call
        // to the AbstractConnector.defaultTransformObject(accountSchema, objectAttributes).
        // We are safe here, since there is no test that uses operation code "Create" in the AccountRequest.

        return obj;
    }

    @SuppressWarnings("unchecked")
    private void modify(AttributeRequest attReq, ResourceObject ro) throws Exception {

        String attrName = attReq.getName();
        Object attrValue = attReq.getValue();
        String op = attReq.getOperation();

        if (attrName == null) {
            throw new GeneralException("Attribute name was null.");
        }
        if (op == null) {
            throw new GeneralException("Modify Operation was null.");
        }

        Object calculatedValue = null;
        if (ProvisioningPlan.OP_ADD.equals(op)) {
            List<String> orig = (List<String>) ro.getAttribute(attrName);

            List<String> newValues = new ArrayList<String>();
            if (orig != null)
                newValues.addAll(orig);

            List<String> vals = Util.asList(attrValue);
            for (String val : vals) {
                newValues.add(val);
            }
            calculatedValue = newValues;
        } else if (ProvisioningPlan.OP_REMOVE.equals(op)) {
            List<String> orig = (List<String>) ro.getAttribute(attrName);

            List<String> vals = Util.asList(attrValue);
            for (String val : vals) {
                orig.remove(val);
            }
            calculatedValue = orig;

        } else if (ProvisioningPlan.OP_SET.equals(op)) {
            calculatedValue = attrValue;
        }

        if (calculatedValue != null) {
            ro.put(attrName, compactValue(attrName, calculatedValue));
        } else {
            ro.put(attrName, null);
        }
    }

    /**
     * Its easier to deal with ADD/REMOVE requests as Lists, so compact them
     * back to just strings if they are not multi.
     */
    private Object compactValue(String attrName, Object val) {
        Object compacted = val;
        Schema schema = _application.getSchema(Connector.TYPE_ACCOUNT);
        if (schema != null) {
            AttributeDefinition def = schema.getAttributeDefinition(attrName);
            if ((def == null) || (!def.isMulti())) {
                if (val instanceof List)
                    compacted = Util.listToCsv((List) val);
            }
        }
        return compacted;
    }

    @SuppressWarnings("unchecked")
    private void writeColumns(FileWriter writer) throws Exception {
        List<String> columns = (List<String>) _application.getAttributeValue("columnNames");
        if (columns == null) {
            throw new GeneralException("Columns could not be found, the must be specified on the application.");
        }

        String csv = Util.listToCsv(columns);
        if (csv != null) {
            writer.write(csv + "\n");
        }
    }

    /**
     * Go through the file that sourced the data and append/modify the data with
     * our updated object.
     */
    private void updateObject(ResourceObject obj, boolean isNew, boolean isDelete) throws Exception {

        String fileName = (String) _application.getAttributeValue("file");
        String outputFile = (String) _application.getAttributeValue("provisioningFile");
        if (outputFile == null) {
            outputFile = fileName + ".provisioned";
        }

        FileWriter writer = new FileWriter(new File(outputFile));
        writeColumns(writer);
        CloseableIterator<ResourceObject> it = null;
        try {
            it = _connector.iterateObjects(Connector.TYPE_ACCOUNT, null, null);
            while (it.hasNext()) {
                ResourceObject ro = it.next();
                String identity = ro.getIdentity();
                if (identity.compareTo(obj.getIdentity()) == 0) {
                    if (isDelete)
                        // omit it
                        continue;
                    else
                        // replace
                        ro = obj;
                }
                String csv = resourceObjectToCsv(ro);
                writer.write(csv + "\n");
            }
            // append any new items
            if (isNew) {
                String csv = resourceObjectToCsv(obj);
                writer.write(csv + "\n");
            }
            writer.flush();
        } finally {
            if (it != null)
                it.close();
            if (writer != null)
                writer.close();
        }
    }

    @SuppressWarnings("unchecked")
    private String resourceObjectToCsv(ResourceObject obj) throws Exception {
        String csv = "";
        List<String> columns = (List<String>) _application.getAttributeValue("columnNames");
        if (columns == null)
            throw new Exception("Columns names could not be found on appliction.");

        String delimiter = (String) _application.getAttributeValue("delimiter");
        int i = 0;
        for (String column : columns) {
            if (i++ > 0)
                csv += delimiter;
            Object o = obj.getAttribute(column);
            if (o != null) {
                if (o instanceof List) {
                    String nestedCsv = Util.listToCsv((List<String>) o);
                    csv += "\"" + nestedCsv + "\"";
                } else {
                    csv += o.toString();
                }
            }
        }
        return (csv.length() > 0) ? csv : null;
    }

    public RequestResult getRequestStatus(String requestID) throws Exception {
        throw new Exception("getRequestStatus: not implemented");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationExecutor
    //
    //////////////////////////////////////////////////////////////////////

    public void finishRoleDefinition(IntegrationConfig config, Bundle src, RoleDefinition dest) throws Exception {
        println("DelimitedFileExecutor: finishRoleDefinition " + src.getName());
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Used only by Unitests
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Static counter used for testing retry attempts.
     */
    private static int retryCount = 0;

    /**
     * Special plan level argument that can be specified to simulate a retry.
     */
    public static final String TEST_RETRY_ATTR = "simmulatedRetryArg";

    @SuppressWarnings("unchecked")
    private boolean isSimmualtedRetry(ProvisioningPlan plan) {
        // The tests will annotate the plan with a special attribute
        // in the arguments of the plan that'll indicate how many times
        // we should retry
        Map<String, Object> args = plan.getArguments();
        Object retryAttributeObject = Util.get(args, TEST_RETRY_ATTR);
        if (retryAttributeObject != null) {
            int retry = Util.otoi(retryAttributeObject);
            if (retry > 0) {
                if (args == null) {
                    args = new HashMap<String, Object>();
                    plan.setArguments(args);
                }
                // is this going anywhere?
                // I don't think this works with older executors
                args.put("simmulatedRetryCount", ++retryCount);
                if (retryCount < retry) {
                    // yes lets retry for kicks
                    System.out.println("DelimitedFileExecutor: Marking retry [" + retryCount + "]");
                    return true;
                } else {
                    if (retryCount >= retry) {
                        System.out.println("DelimitedFileExecutor: Marking retry [" + retryCount
                                + "] !!Simmulated retry completed after attempt.");
                        retryCount = 0;
                        return false;
                    }
                }
            }
        }

        return false;
    }

}
