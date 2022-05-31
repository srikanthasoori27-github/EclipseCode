/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject.FilterReason;
import sailpoint.object.Attributes;
import sailpoint.object.LiveReport;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.QueryOptions;
import sailpoint.object.Sort;
import sailpoint.reporting.ReportHelper;
import sailpoint.service.ProvisioningTransactionDTO;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

public class ProvisioningTransactionObjectDataSource extends ProjectionDataSource implements JavaDataSource {

    private static Log log = LogFactory.getLog(ProvisioningTransactionObjectDataSource.class);

    private static final String SECRET_VALUE = "********";

   /* Field names from the report */
    private static final String APPLICATION_NAME = "applicationName";
    private static final String IDENTITY_NAME = "identityName";
    private static final String IDENTITY_DISPLAY_NAME = "identityDisplayName";
    private static final String INTEGRATION = "integration";
    private static final String NATIVE_IDENTITY = "nativeIdentity";
    private static final String ACCOUNT_DISPLAY_NAME = "accountDisplayName";
    private static final String NAME = "name";
    private static final String STATUS = "status";
    private static final String TYPE = "type";
    private static final String OPERATION = "operation";
    private static final String SOURCE = "source";
    private static final String CREATED = "created";
    private static final String FORCED = "forced";
    private static final String RETRY_COUNT = "retryCount";
    private static final String WORK_ITEM = "manualWorkItem";
    private static final String TICKET_ID = "ticketId";
    private static final String TIMED_OUT = "timedOut";
    private static final String ERROR_MESSAGES = "errorMessages";
    private static final String CERTIFICATION_ID = "certificationId";
    private static final String CERTIFICATION_NAME = "certificationName";
    private static final String REQUEST_OPERATION = "requestOperation";
    private static final String REQUEST_NAME = "requestName";
    private static final String REQUEST_VALUE = "requestValue";
    private static final String REQUEST_RESULT = "requestResult";

    private Iterator<ProvisioningTransaction> ptoIterator;
    private Iterator<GenericRequest> requestIterator;
    private ProvisioningTransaction currentPTO;
    private GenericRequest currentRequest;
    private boolean hasData;

    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
    String groupBy, List<Sort> sort) throws GeneralException {
        super.setTimezone((TimeZone)arguments.get(JRParameter.REPORT_TIME_ZONE));
        super.setLocale((Locale) arguments.get(JRParameter.REPORT_LOCALE));

        ReportHelper helper = new ReportHelper(context, getLocale(), getTimezone());
        QueryOptions ops = helper.getFilterQueryOps(report, arguments);

        init(ProvisioningTransaction.class, ops, report.getGridColumns(), getLocale(), getTimezone());
        hasData = false;
    }

    public Object getFieldValue( String fieldName ) throws GeneralException {
        if( Util.isNullOrEmpty(fieldName) ) {
            throw new GeneralException( "Field name should not be null" );
        }
        /* Identity attributes */
        if( fieldName.equals(APPLICATION_NAME)) {
            return currentPTO.getApplicationName();
        } else if ( fieldName.equals(IDENTITY_NAME)) {
            return currentPTO.getIdentityName();
        } else if( fieldName.equals(IDENTITY_DISPLAY_NAME)) {
            return currentPTO.getIdentityDisplayName();
        } else if( fieldName.equals(INTEGRATION)) {
            return currentPTO.getIntegration();
        } else if( fieldName.equals(NATIVE_IDENTITY)) {
            return currentPTO.getNativeIdentity();
        } else if( fieldName.equals(ACCOUNT_DISPLAY_NAME)) {
            return currentPTO.getAccountDisplayName();
        } else if( fieldName.equals(NAME)) {
            return Util.stripLeadingChar(currentPTO.getName(), '0');
        } else if( fieldName.equals(STATUS)) {
            return currentPTO.getStatus();
        } else if( fieldName.equals(TYPE)) {
            return currentPTO.getType();
        } else if( fieldName.equals(OPERATION)) {
            return currentPTO.getOperation();
        } else if( fieldName.equals(SOURCE)) {
            return currentPTO.getSource();
        } else if( fieldName.equals(CREATED)) {
            return currentPTO.getCreated();
        } else if( fieldName.equals(FORCED)) {
            return currentPTO.isForced();
        } else if( fieldName.equals(RETRY_COUNT)) {
            return currentPTO.getAttributes().get(ProvisioningTransaction.ATT_RETRY_COUNT);
        } else if(fieldName.equals(WORK_ITEM)) {
            return currentPTO.getAttributes().get(ProvisioningTransaction.ATT_MANUAL_WORK_ITEM);
        } else if(fieldName.equals(TICKET_ID)) {
            return currentPTO.getAttributes().get(ProvisioningTransaction.ATT_TICKET_ID);
        } else if(fieldName.equals(TIMED_OUT)) {
            return currentPTO.getAttributes().get(ProvisioningTransaction.ATT_TIMED_OUT);
        } else if(fieldName.equals(CERTIFICATION_ID)) {
            return currentPTO.getCertificationId();
        } else if(fieldName.equals(CERTIFICATION_NAME)) {
            return currentPTO.getAttributes().get(ProvisioningTransaction.ATT_CERT_NAME);
        } else if(fieldName.equals(ERROR_MESSAGES)) {
            List<String> errorMessages = new ArrayList<String>();
            ProvisioningResult planResult = (ProvisioningResult) currentPTO.getAttributes()
                    .get(ProvisioningTransaction.ATT_PLAN_RESULT);
            if (planResult != null) {
                for (Message error : Util.iterate(planResult.getErrors())) {
                    errorMessages.add(error.getKey());
                }
            }

            AbstractRequest request = (AbstractRequest) currentPTO.getAttributes()
                    .get(ProvisioningTransaction.ATT_REQUEST);
            if (request != null && request.getResult() != null) {
                for (Message error : Util.iterate(request.getResult().getErrors())) {
                    errorMessages.add(error.getKey());
                }
            }
            return errorMessages.isEmpty() ? "" : errorMessages.toString();
        }
        // request attributes
        if (null != currentRequest) {
            if(fieldName.equals(REQUEST_OPERATION)) {
                return currentRequest.getOp();
            } else if(fieldName.equals(REQUEST_RESULT)) {
                String result = "";
                AbstractRequest parentRequest = (AbstractRequest) currentPTO.getAttributes().get(ProvisioningTransaction.ATT_REQUEST);
                FilterReason filterReason = (FilterReason) currentRequest.get(ProvisioningProject.ATT_FILTER_REASON);

                // Populate the Result field normally as the UI would if there are no filtered requests,
                // otherwise prepend the reason with a filtered label
                if (parentRequest != null && filterReason == null) {
                    ProvisioningResult planResult = (ProvisioningResult) currentPTO.getAttributes().get(ProvisioningTransaction.ATT_PLAN_RESULT);
                    boolean isTimedOut = currentPTO.getAttributes().getBoolean(ProvisioningTransaction.ATT_TIMED_OUT);

                    result = ProvisioningTransactionService.calculateGenericRequestResult(planResult, parentRequest.getResult(),
                            currentRequest.getResult(), ProvisioningTransactionDTO.getDefaultResult(currentPTO.getStatus()), isTimedOut);
                } else if (filterReason != null) {
                    Message msg = new Message(filterReason.getMessageKey());
                    result = "Filtered - " +  msg.getLocalizedMessage(getLocale(), getTimezone());
                }

                return result;
            }
            if(currentRequest instanceof AttributeRequest) {
                if(fieldName.equals(REQUEST_NAME)) {
                    return currentRequest.getName();
                } else if(fieldName.equals(REQUEST_VALUE)) {
                    return currentRequest.isSecret() ? SECRET_VALUE : currentRequest.getValue();
                }
            } else if(currentRequest instanceof PermissionRequest) {
                if(fieldName.equals(REQUEST_NAME)) {
                    return ((PermissionRequest)currentRequest).getTarget();
                } else if(fieldName.equals(REQUEST_VALUE)) {
                    return ((PermissionRequest)currentRequest).getRights();
                }
            }
        }

        //if it gets here 
        return null;
    }

    @Override
    public boolean next() throws JRException {
        if( !hasData ) {
            try {
                getData();
            } catch ( GeneralException e ) {
                throw new JRException( "Unable to initialize DataSource", e );
            }
        }
        if( ptoIterator == null ) {
            return false;
        }
        // should only happen first run
        if( requestIterator == null ) {
            if (ptoIterator.hasNext()) {
                currentPTO = ptoIterator.next();
                getRequests(currentPTO);
            } else {
                return false;
            }
        }
        boolean hasNextRequest = requestIterator.hasNext(); 
        boolean hasNextPTO = ptoIterator.hasNext(); 
        if( hasNextRequest ) {
            currentRequest = requestIterator.next();
        } else if (hasNextPTO) {
            currentPTO = ptoIterator.next();
            getRequests(currentPTO);
            if (requestIterator.hasNext()) {
                currentRequest = requestIterator.next();
            } else {
                // if there are no requests for the pto print it out anyway with no request info
                currentRequest = null;
            }
        }
        return hasNextPTO || hasNextRequest;
    }

    private void getData() throws GeneralException {
        ptoIterator = getContext().getObjects(ProvisioningTransaction.class, getBaseQueryOptions()).iterator();
        hasData = true;
    }

    private void getRequests(ProvisioningTransaction pto) {
        AbstractRequest request = (AbstractRequest) pto.getAttributes().get(ProvisioningTransaction.ATT_REQUEST);
        AbstractRequest filteredRequest = (AbstractRequest) pto.getAttributes().get(ProvisioningTransaction.ATT_FILTERED);
        List<GenericRequest> allReqs = new ArrayList<GenericRequest>();

        if (request != null) {
            List<AttributeRequest> attReqs = request.getAttributeRequests();
            List<PermissionRequest> permReqs = request.getPermissionRequests();
            if (null != attReqs) {
                allReqs.addAll(attReqs);
            }
            if (null != permReqs) {
                allReqs.addAll(permReqs);
            }
        }

        if (filteredRequest != null) {
            List<AttributeRequest> filteredAttReqs = filteredRequest.getAttributeRequests();
            List<PermissionRequest> filteredPermReqs = filteredRequest.getPermissionRequests();
            if (null != filteredAttReqs) {
                allReqs.addAll(filteredAttReqs);
            }
            if (null != filteredPermReqs) {
                allReqs.addAll(filteredPermReqs);
            }
        }

        requestIterator = allReqs.iterator();
    }

    @Override
    public void setLimit(int startRow, int pageSize) {
        // preview is disabled so this is not necessary but required to extend Java datasource
    }
}
