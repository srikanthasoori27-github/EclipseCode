/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.EntitlementDescriber;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationAction.RemediationAction;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Filter;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.policy.AccountPolicyExecutor;
import sailpoint.reporting.RemediationProgressReport;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 * djs: NOTE currently returns CertificationItem objects
 */
public class RemediationDataSource extends SailPointDataSource<CertificationItem> {

    private static final Log log = LogFactory.getLog(RemediationDataSource.class);
    private Attributes<String,Object> _args;
    private List<String> _appNames;

    public RemediationDataSource(List<Filter> filters, Locale locale, TimeZone timezone, Attributes<String,Object> args) {
        super(filters, locale, timezone);
        setScope(CertificationItem.class);
        this._args = args;


        /** If the user wants to filter by application, we need to pull in the application names
         * and use that to filter the objects on the report.
         */
        String appsString = this._args.getString(RemediationProgressReport.ATTRIBUTE_APPLICATIONS);
        if(appsString!=null) {
            try {
                _appNames = getApplicationNames(appsString, getContext());
            } catch (GeneralException ge) {
                log.warn("Unable to load application names from filter: " + ge.getMessage());
            }
        }
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for Remediations...");
        qo.setOrderBy("parent.identity");
        _objects = getContext().search(CertificationItem.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {

        String fieldName = jrField.getName();
        CertificationAction action = _object.getAction();
        EntitlementSnapshot snapshot = _object.getExceptionEntitlements();

        Object value = null;
        try { 
            if(fieldName.equals("identity")){
                /**If it's an account group membership cert, get the identity from the target name **/
                if(_object.getType().equals(CertificationItem.Type.AccountGroupMembership)) {
                    value = _object.getTargetName();
                } else {
                    value = _object.getIdentity();
                }
            }
            if(fieldName.equals("recipient")) {
                if(action!=null)
                    value = action.getOwnerName();
            } else if(fieldName.equals("requestor")) {
                if(action!=null)
                    value = action.getActorName();
            } else if(fieldName.equals("requestID")) {
                if(action!=null) {
                    // NOTE - this assumes that all account reqs under the 
                    // plan use the same integration.  Might have to rework
                    // this after some field testing.
                    ProvisioningPlan plan = action.getRemediationDetails();
                    if(plan!=null && plan.getAccountRequests()!=null){
                        if(!plan.getAccountRequests().isEmpty())
                            value = plan.getAccountRequests().get(0).getRequestID();
                    }
                }
            } else if(fieldName.equals("type")) {
                if(action!=null) {
                    RemediationAction type = (RemediationAction)action.getRemediationAction(); 
                    if(type!=null) {
                        if (type.equals(RemediationAction.SendProvisionRequest)) {
                            // same caveat as for the requestID above
                            ProvisioningPlan plan = action.getRemediationDetails();
                            if(plan!=null && plan.getAccountRequests()!=null){
                                if(!plan.getAccountRequests().isEmpty())
                                 value = plan.getAccountRequests().get(0).getTargetIntegration();
                            }
                        } 

                        if (null == value)
                            value = type.getDisplayName();
                    }
                }
            } else if(fieldName.equals("details")) {
                if(action!=null) {
                    value =  action.getDescription();
                }
            } else if(fieldName.equals("expiration")) {
                Certification cert = (Certification)_object.getParent().getCertification();
                Date date = cert.calculatePhaseEndDate(Certification.Phase.Remediation);
                getContext().decache(cert);
                value = date;
            } else if(fieldName.equals("status")) {

                Message state = new Message(MessageKeys.WORK_ITEM_STATE_OPEN);
                if(action!=null && action.isRemediationCompleted()) {
                    state = new Message(MessageKeys.WORK_ITEM_STATE_FINISHED);
                }
                value = state.getLocalizedMessage(getLocale(), null);
            } else if(fieldName.equals("application")) {

                if(snapshot!=null)
                    value = snapshot.getApplication();

                if(value==null && _object.getPolicyViolation()!=null
                        && _object.getPolicyViolation().getRelevantApps()!=null) {
                    value = Util.listToCsv(_object.getPolicyViolation().getRelevantApps());
                }
            } else if(fieldName.equals("instance")) {

                if(snapshot!=null)
                    value = snapshot.getInstance();

            }else if(fieldName.equals("account")) {
                if(snapshot!=null)
                    value = snapshot.getDisplayableName();

                /** Try to get the accounts from the account policy violation **/
                if(value==null && _object.getPolicyViolation()!=null) {
                    PolicyViolation violation = _object.getPolicyViolation();
                    if(violation.getArgument(AccountPolicyExecutor.VIOLATION_ACCOUNTS)!=null) {
                        value = Util.listToCsv((List)violation.getArgument(AccountPolicyExecutor.VIOLATION_ACCOUNTS));
                    }                    
                }

            } else if(fieldName.equals("entitlement")) {
                if(_object.getBundle()!=null) {
                    value = getMessage(MessageKeys.LABEL_NAMED_ROLE, _object.getBundle());
                }else if(_object.getViolationSummary()!=null) { 
                    value = getMessage(MessageKeys.POLICY_VIOLATION)+": "+_object.getViolationSummary();

                }else if(snapshot!=null) {
                    value = EntitlementDescriber.summarize(snapshot).getLocalizedMessage();
                }
            } else if (fieldName.equals("newValue")) {
                value = DataSourceUtil.getRemediationModifiableNewValue(action);
            } else if(fieldName.equals("comments")) {
                if(action.getComments()!=null && !action.getComments().equals("")) {
                    List<Map<String, String>> commentsList = new ArrayList<Map<String, String>>();
                    Map<String, String> comments = new HashMap<String, String>();
                    comments.put("comments", action.getComments());
                    commentsList.add(comments);                    
                    value = commentsList;
                }
            } else if(fieldName.equals("commentsString") ) {
                if(action.getComments()!=null && !action.getComments().equals("")) {
                    value = action.getComments();
                }
            }else if(fieldName.equals("completionComments") ) {
                if(action.getCompletionComments()!=null && !action.getCompletionComments().equals("")) {
                    List<Map<String, String>> commentsList = new ArrayList<Map<String, String>>();
                    Map<String, String> comments = new HashMap<String, String>();
                    comments.put("comments", action.getCompletionComments());
                    commentsList.add(comments);                    
                    value = commentsList;
                }
            } else if(fieldName.equals("completionCommentsString") ) {
                if(action.getCompletionComments()!=null && !action.getCompletionComments().equals("")) {
                    value = action.getCompletionComments();
                }
            }
        } catch(GeneralException ge) {
            log.info("General Exception encountered while getting field name: " + fieldName + ". Exception: " + ge.getMessage());
        }
        if(value==null) 
            value = super.getFieldValue(jrField);
        return value;

    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    public boolean internalNext() throws JRException {
        try {
            _object = null;
            if (_objects != null) {
                while (_objects.hasNext()) {
                    CertificationItem nextItem = _objects.next();
                    if (nextItem.referencesApplications(this._appNames, getContext())) {
                        _object = nextItem;
                        updateProgress("Remediation", _object.getName());
                        return true;
                    }
                }
            }

            return false;
        } catch (GeneralException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Takes a list of application ids and converts it to a list of application names **/
    public static List<String> getApplicationNames(String appsString, SailPointContext ctx) 
            throws GeneralException {
        List<String> appNames = new ArrayList<String>();
        List<String> props = Arrays.asList("name");
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.in("id", Util.stringToList(appsString)));

        Iterator<Object[]> rows = ctx.search(Application.class, ops, props);
        while(rows.hasNext()) {
            Object[] row = rows.next();
            appNames.add((String)row[0]);
        }

        return appNames;
    }

}
