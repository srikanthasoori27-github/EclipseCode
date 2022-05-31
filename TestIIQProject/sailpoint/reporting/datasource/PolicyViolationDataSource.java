/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ActivityConstraint;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Filter;
import sailpoint.object.GenericConstraint;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.SODConstraint;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LocalizedDate;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * @author peter.holcomb
 *
 */
public class PolicyViolationDataSource extends SailPointDataSource<PolicyViolation> {

	private static final Log log = LogFactory.getLog(PolicyViolationDataSource.class);

	List<Filter> subReportFilters;
	BaseConstraint constraint;

	public PolicyViolationDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
		super(filters, locale, timezone);
		setScope(PolicyViolation.class);
	}

	@Override
	public void internalPrepare() throws GeneralException {


		_objects = getContext().search(PolicyViolation.class, qo);
	}

    /* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
	 */
	public Object getFieldValue(JRField jrField) throws JRException {

		String fieldName = jrField.getName();
        Policy policy = null;
        try{
            policy = _object.getPolicy(getContext());
        } catch(GeneralException e){
            log.error("Error getting policy", e);
        }
        
        Object value = null;
		if(fieldName.equals("rule") && constraint!=null) {
			return constraint.getName();
		} else if(fieldName.equals("ruleDescription") && constraint!=null) {
			return constraint.getDescription();
		} else if(fieldName.equals("compensatingControl") && constraint!=null) {
			return constraint.getCompensatingControl();
        } else if(fieldName.equals("policyDescription")) {
            if(policy!=null)
                value = policy.getDescription();
        } else if(fieldName.equals("policyName")) {
            if(policy!=null)
                value = policy.getName();
        } else if(fieldName.equals("status")){
            String status = null;
            try {
                status = getStatus();
            }
            catch (GeneralException e) {
                log.error(e);
            }
            return status;
        } else if(fieldName.equals("summary")){
            try {
                value = getSummary();
            } catch (GeneralException e) {
                log.error(e);               
            }
        } else if (fieldName.equals("violationOwner")) {
            return getViolationOwner();
        }
		
		if(value==null)
			value = super.getFieldValue(jrField);
		return value;

	}
	
	private String getViolationOwner() {

	    if (_object.getOwner() == null) {
            return null;
        }

	    return _object.getOwner().getDisplayableName(); 
	}

    private String getStatus() throws GeneralException {
        Message out = null;
        IdentityHistoryItem lastDecision = _object.getIdentity().getLastViolationDecision(getContext(), _object);
        if (lastDecision != null && lastDecision.getAction() != null){
            // if the decision was made after the last policy scan then display it. We don't want to
            // display old decisions, just those made for the current violation. If no decisions have been made on
            // the violation, display any mitigation if it is still active.
            if (lastDecision.getAction().getCreated().compareTo(_object.getCreated()) > 0 ||
                    lastDecision.getAction().isMitigation()){

                if (lastDecision.getAction().isMitigation()){
                    out = new Message(MessageKeys.REPT_VIOL_GRID_STATUS_ALLOWED,
                            new LocalizedDate(lastDecision.getAction().getMitigationExpiration(), DateFormat.SHORT, null));
                } else if (lastDecision.getAction().isRemediation()){
                     out = new Message(MessageKeys.REPT_VIOL_GRID_STATUS_REMEDIATED,
                            new LocalizedDate(lastDecision.getAction().getCreated(), DateFormat.SHORT, null));
                } else if (lastDecision.getAction().isAcknowledgment()){
                     out = new Message(MessageKeys.REPT_VIOL_GRID_STATUS_ACKNOWLEDGED,
                            new LocalizedDate(lastDecision.getAction().getCreated(), DateFormat.SHORT, null));
                }

            }
        }
        


        if (out==null)
            return MessageKeys.REPT_VIOL_GRID_STATUS_OPEN;

        return out.getLocalizedMessage(getLocale(), getTimezone());
    }

    private String getSummary() throws GeneralException{
        String summary = null;
        Policy policy = _object.getPolicy(getContext());

        // jsl - to better support custom policy summary rules
        // we always obey the PolicyViolation.description if it
        // is non-null.  There is similar logic in ViolationViewBean
        summary = _object.getDescription();
        if (summary == null) {
            if ((policy != null && policy.isType(Policy.TYPE_SOD)) || 
                ( ( _object.getLeftBundles() != null ) && ( _object.getRightBundles() != null ) ) ) {
                List<String> leftBundles = Util.csvToList(_object.getLeftBundles());
                List<String> rightBundles = Util.csvToList(_object.getRightBundles());

                Message msg = new Message(MessageKeys.POLICY_VIOLATION_SOD_SUMMARY,
                                          leftBundles, rightBundles);

                summary = msg.getLocalizedMessage(getLocale(), getTimezone());
            } else {
                BaseConstraint base = policy.getConstraint(_object);
                if ( base != null ) {
                    summary = base.getDescription();
                }
            }
        }

        // jsl - convert <br/> to newlines
        // this is a hack so we can build multi-line descriptions
        // we need to use <br/> so it will be displayed in the policy violation
        // table, but Jasper needs newlines
        if (summary != null)
            summary = summary.replaceAll("<br/>", "\n");

        return summary;
    }

    /* (non-Javadoc)
	 * @see net.sf.jasperreports.engine.JRDataSource#next()
	 */
	public boolean internalNext() throws JRException {
		boolean hasMore = false;
		if ( _objects != null ) {
			hasMore = _objects.hasNext();
			if ( hasMore ) {
				_object = _objects.next();
				try {
					String constraintId = _object.getConstraintId();
					if(constraintId!=null) {
						constraint = getContext().getObjectById(SODConstraint.class, constraintId);

                        if (constraint == null){
                            constraint = getContext().getObjectById(GenericConstraint.class, constraintId);
                        }

                        if (constraint == null){
                            constraint = getContext().getObjectById(ActivityConstraint.class, constraintId);    
                        }
                    }

                    else
						constraint = null;
				} catch (GeneralException ge) {
					log.error("Unable to fetch constraint for policy violation: " + _object.getName() + 
							" in Violation Report. " + ge.getMessage());
				}
			} else {
				_object = null;
			}
			if ( _object != null ) {
				updateProgress("Violation", _object.getName());
			}
		}
		//log.debug("Getting Next: " + hasMore);
		return hasMore;
	}

}
