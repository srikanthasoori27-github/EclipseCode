/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.task.BatchRequestTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.IdentityAttributeComparator;
import sailpoint.workflow.BatchRequestLibrary;

/**
 * Responsible for validating batch request content.
 *
 * @author patrick.jeong
 *
 */
public class BatchRequestValidator {
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    private static final Log log = LogFactory.getLog(BatchRequestTaskExecutor.class);
    SailPointContext _context;
    Message errorMsg;
    private Boolean handleExistingCreate;
    List<ObjectAttribute> attributes;
    String targetId = "";
    List<String> headers;
    List<String> requestValues;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    public BatchRequestValidator(SailPointContext context, List<String> headers, Attributes<String, Object> attributes) {
        _context = context;
        if (attributes != null) {
            setRunArgs(attributes);
        }

        if (headers != null) {
            prepareHeaders(headers);
        }
    }

    // ////////////////////////////////////////////////////////////////////
    //
    // Helpers
    //
    // ////////////////////////////////////////////////////////////////////
   /**
    * Trim and convert to lowercase
    */
    private void prepareHeaders(List<String> dirtyHeaders) {
        headers = new ArrayList<String>();

        for (String hVal : dirtyHeaders) {
            if (hVal == null)
                continue;
            headers.add(hVal.trim().toLowerCase());
        }
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Validation methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     *
     * @throws GeneralException
     */
    private boolean validateCreateRequest() throws GeneralException {
        boolean isValid = true;

        // This should have been checked in header validation but we check again anyways
        if (!headers.contains(BatchRequestLibrary.NAME_HEADER)) {
            errorMsg = Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD, BatchRequestLibrary.NAME_HEADER);
            throw new GeneralException(errorMsg);
        }
        else {
            Identity id = _context.getObjectByName(Identity.class, requestValues.get(headers.indexOf(BatchRequestLibrary.NAME_HEADER)));

            if (!handleExistingCreate && id != null) {
                isValid = false;
                errorMsg = Message.error(MessageKeys.BATCH_REQUEST_IDENTITY_ALREADY_EXISTS, id.getName());
            }
        }
        return isValid;
    }

    private Application getApplication() throws GeneralException {
        Application app = null;
        // check header again
        if (headers.contains(BatchRequestLibrary.APP_HEADER)) {
            QueryOptions qo = new QueryOptions();
            qo.setIgnoreCase(true);
            qo.add(Filter.eq("name", requestValues.get(headers.indexOf(BatchRequestLibrary.APP_HEADER))));
            Iterator<Application> res = _context.search(Application.class, qo);
            if (res.hasNext()) {
                app = res.next();
                // replace app name with true app name
                requestValues.remove(headers.indexOf(BatchRequestLibrary.APP_HEADER));
                requestValues.add(headers.indexOf(BatchRequestLibrary.APP_HEADER), app.getName());
                Util.flushIterator(res);
            }
        }
        return app;
    }

    /**
     * Validate the identity has an account to modify
     * Not for account create op.
     * @return
     * @throws GeneralException
     */
    private boolean validateAccount(Identity target, Application app) throws GeneralException {
        IdentityService identSvc = new IdentityService(_context);
        int linkCount = identSvc.countLinks(target, app);
        boolean isValid = true;
        if (linkCount == 0) {
            errorMsg = Message.error(MessageKeys.BATCH_REQUEST_LINK_NOT_FOUND);
            isValid = false;
        }
        else if (linkCount> 1 && headers.contains(BatchRequestLibrary.IDENTITY_HEADER)) {
            errorMsg = Message.error(MessageKeys.BATCH_REQUEST_AMBIGUOUS_ACCOUNT);
            isValid = false;
        }
        return isValid;
    }

    /**
     *
     * @return
     * @throws GeneralException
     */
    private boolean validateRoleValues() throws GeneralException {

        String role = requestValues.get(headers.indexOf(BatchRequestLibrary.ROLES_HEADER));

        String[] rolesList = role.split("\\|");

        StringBuffer newRolenames = new StringBuffer();

        for (int j = 0; j < rolesList.length;) {
            String roleName = rolesList[j].trim();
            QueryOptions qo = new QueryOptions();
            qo.setIgnoreCase(true);
            qo.add(Filter.eq("name", roleName));
            Iterator<Bundle> res = _context.search(Bundle.class, qo);
            if (!res.hasNext()) {
                errorMsg = Message.error(MessageKeys.BATCH_REQUEST_ROLE_NOT_FOUND, roleName);
                return false;
            }
            else {
                newRolenames.append(res.next().getName());
                if (++j < rolesList.length) {
                    newRolenames.append("|");
                }
                Util.flushIterator(res);
            }
        }
        // fix possible role name case sensitive issues
        requestValues.remove(headers.indexOf(BatchRequestLibrary.ROLES_HEADER));
        requestValues.add(headers.indexOf(BatchRequestLibrary.ROLES_HEADER),
                newRolenames.toString());

        //validate sunrise and sunset date scenarios
        if (headers.contains(BatchRequestLibrary.SUNSET_HEADER) ||
                headers.contains(BatchRequestLibrary.SUNRISE_HEADER)) {
            return validateSunriseAndSunsetDates();
        }


        return true;
    }

    /**
     * Validate the sunset date. Make sure its not in the past
     * Validate that the sunrise is before the sunset date if there is a sunrise date
     *
     * @return
     * @throws GeneralException
     */
    private boolean validateSunriseAndSunsetDates() throws GeneralException {
        String sunsetValue = requestValues.get(headers.indexOf(BatchRequestLibrary.SUNSET_HEADER));
        Date sunsetDate = null;
        if (Util.isNotNullOrEmpty(sunsetValue)) {
            //Validate we can parse the date
            try {
                sunsetDate = Util.stringToDate(sunsetValue);
            } catch (ParseException e) {
                errorMsg = Message.error(MessageKeys.BATCH_REQUEST_VALIDATION_DATE_PARSE_ERROR, sunsetValue);
                return false;
            }
            //Validate sunset is not in the past
            Date now = new Date();
            if (sunsetDate.before(now)) {
                errorMsg = Message.error(MessageKeys.BATCH_REQUEST_VALIDATION_INVALID_SUNSET_DATE);
                return false;
            }
        }

        if (headers.contains(BatchRequestLibrary.SUNRISE_HEADER)) {
            String sunriseValue = requestValues.get(headers.indexOf(BatchRequestLibrary.SUNRISE_HEADER));
            if (Util.isNotNullOrEmpty(sunriseValue)) {
                Date sunriseDate;
                //Validate we can parse the sunrise date
                try {
                    sunriseDate = Util.stringToDate(sunriseValue);
                } catch (ParseException e) {
                    errorMsg = Message.error(MessageKeys.BATCH_REQUEST_VALIDATION_DATE_PARSE_ERROR, sunriseValue);
                    return false;
                }
                //Validate sunrise is before sunset if a sunset date is set
                if (sunsetDate != null && sunsetDate.before(sunriseDate)) {
                    errorMsg = Message.error(MessageKeys.BATCH_REQUEST_VALIDATION_INVALID_SUNSET_BEFORE_SUNRISE_DATE);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Validate requested attribute names
     *
     * @return
     * @throws GeneralException
     */
    private boolean validateAttributeNames(Application app) throws GeneralException {
        String entName = requestValues.get(headers.indexOf(BatchRequestLibrary.ENTNAME_HEADER));

        // check to see if attribute name is valid
        List<Schema> scms = app.getSchemas();
        for (Schema sc : scms) {
            if (sc.getAttributeNames().contains(entName)) {
                break;
            }
            else {
                errorMsg = Message.error(MessageKeys.BATCH_REQUEST_ENT_NOT_FOUND, entName);
                return false;
            }
        }

        return true;
    }

    /**
     * Validate the current record and return validation status.
     *
     * @param requestValues
     * @param lastOperationType
     * @return
     * @throws GeneralException
     */
    public boolean validateRequestValues(List<String> requestValues, String lastOperationType) throws GeneralException {
        this.requestValues = requestValues;

        if (requestValues.size() < headers.size()) {
            // not enough matching data, skip record
            errorMsg = Message.error(MessageKeys.BATCH_REQUEST_MISSING_DATA_ELEMENTS);
            return false;
        }

        String op = requestValues.get(0).trim().toLowerCase();
        lastOperationType = lastOperationType.trim().toLowerCase();

        // Check that the operation is valid mix.
        if (!checkValidOperation(op, lastOperationType)) {
            errorMsg = Message.error(MessageKeys.BATCH_REQUEST_INVALID_OPERATION, op);
            // invalid op mixes can't run at all
            throw new GeneralException(errorMsg);
        }

        // verify if identity exists already
        if (BatchRequestLibrary.OP_CREATE_IDENTITY.equals(op)) {
            return validateCreateRequest();
        }

        // Check cubeName or nativeIdentity is valid if not create identity
        Identity target = getTargetIdentity(op, requestValues, headers);

        if (target != null) {
            targetId = target.getId();
        }
        else {
            errorMsg = Message.error(MessageKeys.BATCH_REQUEST_TARGET_IDENTITY_NOT_FOUND);
            return false;
        }

        // Check app
        Application app = getApplication();

        if (app == null && headers.contains(BatchRequestLibrary.APP_HEADER)) {
            errorMsg = Message.error(MessageKeys.BATCH_REQUEST_TARGET_APPLICATION_NOT_FOUND);
            return false;
        }

        // Check to see if account actually exists for account related operations
        // This excludes the "createaccount" op
        if (BatchRequestLibrary.ACCOUNT_OPS.contains(op)) {
            return validateAccount(target, app);
        }

        // validate role name
        if (headers.contains(BatchRequestLibrary.ROLES_HEADER)) {
            return validateRoleValues();
        }

        if (headers.contains(BatchRequestLibrary.ENTNAME_HEADER)) {
           return validateAttributeNames(app);
        }

        return true;
    }

    /**
     * Validate cube name and account id when op is an account op
     *
     * @param values
     * @throws GeneralException
     */
    private Identity getTargetIdentity(String op, List<String> values, List<String> headers) throws GeneralException {
        int idx = -1;

        Identity target = null;

        if (headers.contains(BatchRequestLibrary.IDENTITY_HEADER)) {
            idx = headers.indexOf(BatchRequestLibrary.IDENTITY_HEADER);

            String targetName = values.get(idx);

            QueryOptions qi = new QueryOptions();
            qi.setIgnoreCase(true);
            qi.add(Filter.eq("name", targetName));

            Iterator<Identity> rs = _context.search(Identity.class, qi);

            if (rs.hasNext()) {
                target  = rs.next();
                Util.flushIterator(rs);
            }
        }
        else if (headers.contains(BatchRequestLibrary.NATIVE_HEADER)) {
            idx = headers.indexOf(BatchRequestLibrary.NATIVE_HEADER);

            String accountId = values.get(idx);

            QueryOptions linkNI = new QueryOptions();
            linkNI.setDistinct(true);
            linkNI.setIgnoreCase(true);
            linkNI.add(Filter.ignoreCase(Filter.eq("nativeIdentity", accountId)));

            Iterator<Link> results = _context.search(Link.class, linkNI);

            if (results.hasNext()) {
                target = results.next().getIdentity();
                Util.flushIterator(results);
            }
        }

        return target;
    }

    /**
     * Some operations can be mixed in the same file some can't.
     * This checks to see if the operations are a valid mix.
     *
     * @param op
     * @throws GeneralException
     */
    private boolean checkValidOperation(String op, String lastOperationType) throws GeneralException {

        if (!op.equals(lastOperationType)) {

            if (BatchRequestLibrary.NOMIX_OPS.contains(op) || (!BatchRequestLibrary.ROLE_OPS.contains(op) && BatchRequestLibrary.ROLE_OPS.contains(lastOperationType)) ||
                    (!BatchRequestLibrary.ENT_OPS.contains(op) && BatchRequestLibrary.ENT_OPS.contains(lastOperationType)) ||
                    (!BatchRequestLibrary.ACCOUNT_OPS.contains(op) && BatchRequestLibrary.ACCOUNT_OPS.contains(lastOperationType))) {
                log.error("Invalid operation mix: " + op + " and " + lastOperationType);
                return false;
            }
        }

        lastOperationType = op;

        return true;
    }

    /**
     * Valid header fields include "operation", "application", "nativeIdentity", "cubeName"
     * "email", "roles", "entitlementName", "entitlementValue",  "password", "sunrise", "sunset"
     *
     * @param opType
     */
    public void validateHeaderFields(String opType) throws GeneralException {
        // first header field should always be 'operation'
        if (!BatchRequestLibrary.OPERATION_HEADER.equals(headers.get(0))) {
            // Localize this error since it will be shown to the user to help them diagnose
            // the issue with the file they are uploading.
            throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_VALIDATION_WRONG_HEADER, headers.get(0)));
        }

        // convert before checking
        opType = opType.trim().toLowerCase();

        // For the create identity operation the fields
        if (opType.equals(BatchRequestLibrary.OP_CREATE_IDENTITY)) {
            validateCreateIdentityHeaders();
        }
        else if (opType.equals(BatchRequestLibrary.OP_MODIFY_IDENTITY)) {
            validateModifyIdentityHeaders();
        }
        else if (opType.equals(BatchRequestLibrary.OP_CREATE_ACCOUNT)) {
            validateAccountHeaders();
        }
        else if (BatchRequestLibrary.ACCOUNT_OPS.contains(opType)) {
            validateAccountHeaders();
        }
        else if (BatchRequestLibrary.ROLE_OPS.contains(opType)) {
            validateRoleHeaders();
        }
        else if (BatchRequestLibrary.ENT_OPS.contains(opType)) {
            validateEntitlementHeaders();
        }
        else if (opType.equals(BatchRequestLibrary.OP_CHANGE_PASSWORD)) {
            validateChangePasswordHeaders();
        }
        else {
            // invalid op
            throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_INVALID_OPERATION, opType));
        }
    }

    /**
     * Check for nativeIdentity or cubeName
     * @throws GeneralException
     */
    private void checkIdentityHeader() throws GeneralException {
        // if headers doesn't contain either nativeIdentity or identityName
        if (!(headers.contains(BatchRequestLibrary.NATIVE_HEADER) || headers.contains(BatchRequestLibrary.IDENTITY_HEADER))) {
            throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD,
                    BatchRequestLibrary.NATIVE_HEADER + ", " + BatchRequestLibrary.IDENTITY_HEADER));
        }
    }

    /**
     * The base check header method that checks to see if headers contains a specific value
     *
     * @param headerVal
     * @throws GeneralException
     */
    private void checkHeader(String headerVal) throws GeneralException {
        if (!headers.contains(headerVal)) {
            throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD, headerVal));
        }
    }

    private void validateEntitlementHeaders()  throws GeneralException {
        checkHeader(BatchRequestLibrary.APP_HEADER);
        checkIdentityHeader();
        checkHeader(BatchRequestLibrary.ENTNAME_HEADER);
        checkHeader(BatchRequestLibrary.ENTVALUE_HEADER);
    }

    /**
     * Role ops require list of roles, cube name, sunrise, sunset for add roles
     * @param opType
     * @throws GeneralException
     */
    private void validateRoleHeaders()  throws GeneralException {
        checkHeader(BatchRequestLibrary.ROLES_HEADER);
        checkHeader(BatchRequestLibrary.IDENTITY_HEADER);
        // sunrise, sunset not required
    }

    /**
     * Required: operation, application, nativeIdentity or cubeName, password
     * @throws GeneralException
     */
    private void validateChangePasswordHeaders() throws GeneralException {
        checkHeader(BatchRequestLibrary.APP_HEADER);
        checkIdentityHeader();
        checkHeader(BatchRequestLibrary.PASSWORD_HEADER);
    }

    /**
     * Required: operation, application, nativeIdentity or cubeName
     * @throws GeneralException
     */
    private void validateAccountHeaders() throws GeneralException {
        checkHeader(BatchRequestLibrary.APP_HEADER);
        checkIdentityHeader();
    }

    /**
     * We assume the first two fields are operation and cubeName
     *
     * @throws GeneralException
     */
    private void validateModifyIdentityHeaders() throws GeneralException {
        // required fields are 'operation', and 'identityName'
        if (!headers.contains(BatchRequestLibrary.IDENTITY_HEADER)) {
            throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD, BatchRequestLibrary.IDENTITY_HEADER));
        }

        // use the update identity form if one exists
        Form updateForm = getForm(Configuration.UPDATE_IDENTITY_FORM);

        Map<String, String> validFieldNames = null;

        if (updateForm != null) {
            validFieldNames = getValidFieldNames(updateForm);
        }
        else {
            // the rest of the fields must be editable identity attributes?
            ObjectConfig config = ObjectConfig.getObjectConfig(Identity.class);

            List<ObjectAttribute> editables = config.getEditableAttributes();

            Iterator<ObjectAttribute> editer = editables.iterator();

            validFieldNames = new HashMap<String, String>();

            while (editer.hasNext()) {
                ObjectAttribute obat = editer.next();
                validFieldNames.put(obat.getName().toLowerCase(), obat.getName());
            }
        }

        for (int i = 1; i < headers.size(); ++i) {
            String hval = headers.get(i).trim();
            if (hval.equals(BatchRequestLibrary.IDENTITY_HEADER)) {
                continue;
            }
            if (!validFieldNames.containsKey(hval)) {
                throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_INVALID_HEADER, hval));
            }
            else {
                headers.set(i, validFieldNames.get(hval));
            }
        }
    }

    /**
     * Validation for create identity op.
     * Check the create identity form to see which fields are available
     * as well as which fields are required.
     *
     * @throws GeneralException
     */
    private void validateCreateIdentityHeaders() throws GeneralException {
        if (!headers.contains(BatchRequestLibrary.NAME_HEADER)) {
            throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD, BatchRequestLibrary.NAME_HEADER));
        }

        // Need to verify that header fields are valid identity attributes
        Form form = getForm(Configuration.CREATE_IDENTITY_FORM);

        Map<String, String> validFieldNames = getFieldNamesAndCheckRequiredFields(form);

        // make sure all fields in headerField are valid fields
        for (int i = 1; i < headers.size(); ++i) {
            String fld = headers.get(i) ;

            if (fld.equals(BatchRequestLibrary.NAME_HEADER)) {
                continue;
            }

            if (!validFieldNames.containsKey(fld)) {
                throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_INVALID_HEADER, fld));
            }
            else {
                headers.set(i, validFieldNames.get(fld));
            }
        }
    }

    /**
     * Get identity attributes
     *
     * @return
     * @throws GeneralException
     */
    public List<ObjectAttribute> getAttributes() throws GeneralException {
        if (attributes == null) {
            attributes = new ArrayList<ObjectAttribute>();
            ObjectConfig idConfig = ObjectConfig.getObjectConfig(Identity.class);

            if (idConfig != null) {

                List<ObjectAttribute> atts;
                    atts = idConfig.getSearchableAttributes();
                    Collections.sort(atts, new IdentityAttributeComparator());

                if (atts != null) {
                    for (ObjectAttribute att : atts) {
                        /** Filter out identity name **/
                        if(!attributes.contains(att))
                            attributes.add(att);
                    }
                }
            }
        }
        return attributes;
    }

    /**
     * Get the create identity form
     *
     * @return
     */
    private Form getForm(String formType) throws GeneralException {
        String formName = (String) Configuration.getSystemConfig().get(formType);
        Form form = _context.getObjectByName(Form.class, formName) ;
        return form;
    }

    /**
     * Populate validFieldNames and requireFields
     *
     * @param form
     * @return
     * @throws GeneralException
     */
    private Map<String, String> getFieldNamesAndCheckRequiredFields(Form form) throws GeneralException {
        Map<String, String> validFieldNames = new HashMap<String, String>();

        // If there is no create identity form use identity attributes
        if (form == null) {
            List<ObjectAttribute> identAttributes = getAttributes();

            for (ObjectAttribute attr : identAttributes) {
                String fldName = attr.getName().toLowerCase();

                validFieldNames.put(fldName, attr.getName());

                if (attr.isRequired()) {
                    if (!headers.contains(fldName)) {
                        throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD, fldName));
                    }
                }
            }

            return validFieldNames;
        }

        Iterator<Field> fit = form.iterateFields();

        // Gather the valid field names and check if required fields exist
        while (fit.hasNext()) {
            Field fld = fit.next();
            String fldName = fld.getName().toLowerCase();
            validFieldNames.put(fldName, fld.getName());

            // make sure it's included in the headerFields
            if (fld.isRequired()) {
                if (!headers.contains(fldName)) {
                    throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD, fldName));
                }
            }
        }

        return validFieldNames;
    }

    /**
     * Populate validFieldNames and requireFields
     *
     * @param form
     * @return
     * @throws GeneralException
     */
    private Map<String, String> getValidFieldNames(Form form) throws GeneralException {
        Map<String, String> validFieldNames = new HashMap<String, String>();

        Iterator<Field> fit = form.iterateFields();

        // Gather the valid field names and check if required fields exist
        while (fit.hasNext()) {
            Field fld = fit.next();
            String fldName = fld.getName().toLowerCase();
            validFieldNames.put(fldName, fld.getName());

            // make sure it's included in the headerFields
            if (fld.isRequired()) {
                if (!headers.contains(fldName)) {
                    throw new GeneralException(Message.error(MessageKeys.BATCH_REQUEST_MISSING_REQUIRED_FIELD, fldName));
                }
            }
        }

        return validFieldNames;
    }

    /**
     *
     * @param runConfig
     */
    private void setRunArgs(Attributes<String, Object> runConfig) {
        if (runConfig != null) {
            handleExistingCreate = (Boolean) runConfig.get("handleExistingCreate");
        }
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public Message getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(Message errorMsg) {
        this.errorMsg = errorMsg;
    }
}
