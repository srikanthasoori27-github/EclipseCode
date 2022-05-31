package sailpoint.web.identity;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import sailpoint.api.Differencer;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.IdentitySuggestItemConverter;

/**
 * This class is initialized by IdentityDTO
 * for the View Identity -> Attributes -> Forwarding Info 
 *
 */
public class ForwardingHelper {

    private IdentityDTO parent;

    private LazyLoad<String> forward;
    private LazyLoad<Boolean> forwardStart;
    private LazyLoad<Boolean> forwardEnd;
    private LazyLoad<Date> forwardStartDate;
    private LazyLoad<Date> forwardEndDate;
    private boolean forwardingError = false;

    public ForwardingHelper(IdentityDTO parent) {
        this.parent = parent;

        this.forwardStart = new LazyLoad<>(() -> {
            Identity id = this.parent.getObject();
            if (id == null)
                return false;

            Map<String, Object> prefs = id.getPreferences();
            if (prefs == null) {
                return false;
            }
            return null != prefs.get(Identity.PRF_FORWARD_START_DATE);
        });

        this.forwardEnd = new LazyLoad<>(() -> {
            Identity id = this.parent.getObject();
            if (id == null)
                return false;

            Map<String, Object> prefs = id.getPreferences();
            if (prefs == null) {
                return false;
            }
            return null != prefs.get(Identity.PRF_FORWARD_END_DATE);
        });

        this.forwardStartDate = new LazyLoad<>(() -> {
            Identity id = this.parent.getObject();
            if (null == id)
                return null;

            return (Date)id.getPreference(Identity.PRF_FORWARD_START_DATE);
        });

        this.forwardEndDate = new LazyLoad<>(() -> {
            Identity id = this.parent.getObject();
            if (null == id)
                return null;

            return (Date)id.getPreference(Identity.PRF_FORWARD_END_DATE);
        });

        this.forward = new LazyLoad<>(() -> {
            Identity id = this.parent.getObject();
            if (null == id)
                return null;

            return (String) id.getPreference(Identity.PRF_FORWARD);
        });
    }

    private String getForwardName() throws GeneralException {
        if (this.parent.getState().getForward() != null) {
            if (this.parent.getState().getForward().equals("")) {
                return null;
            }

            return this.parent.getState().getForward();
        }

        return this.forward.getValue();
    }

    public String getForwardDisplayName() throws GeneralException {
        IdentitySuggestItem ident = getForward();
        return  (ident == null) ? null : ident.getDisplayName();
    }

    public void setForwardDisplayName(String displayName) {
        //No-op
    }

    public IdentitySuggestItem getForward() throws GeneralException {
        String identityName = getForwardName();
        if (identityName != null) {
            Identity forwardingUser = this.parent.getContext().getObjectByName(Identity.class, identityName);
            if (null != forwardingUser){
                return IdentitySuggestItemConverter.createFromIdentity(forwardingUser);
            }
        }

        return null;
    }

    public void setForward(IdentitySuggestItem fwdUser)
            throws GeneralException {
        // Set to empty string when we get null, so we know it was set and dont fetch saved name
        this.parent.getState().setForward(fwdUser == null ? "" : fwdUser.getName());
    }

    public boolean getForwardStart() throws GeneralException {
        if (this.parent.getState().getForwardStart() != null) {
            return this.parent.getState().getForwardStart();
        }

        return this.forwardStart.getValue();
    }

    public void setForwardStart(boolean state) {
        this.parent.getState().setForwardStart(state);
    }

    public boolean getForwardEnd() throws GeneralException {
        if (this.parent.getState().getForwardEnd() != null) {
            return this.parent.getState().getForwardEnd();
        }

        return this.forwardEnd.getValue();
    }

    public void setForwardEnd(boolean state) {
        this.parent.getState().setForwardEnd(state);
    }

    public Date getForwardStartDate() throws GeneralException {

        if (this.parent.getState().getForwardStartDate() != null) {
            return this.parent.getState().getForwardStartDate();
        }

        if (this.forwardStartDate.getValue() != null) {
            return this.forwardStartDate.getValue();
        }

        return new Date();
    }

    public void setForwardStartDate(Date startDate) {
        if (startDate != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);

            this.parent.getState().setForwardStartDate(cal.getTime());
        } else {
            // Should this ever happen?
            this.parent.getState().setForwardStartDate(null);
        }
    }

    public Date getForwardEndDate() throws GeneralException {
        if (this.parent.getState().getForwardEndDate() != null) {
            return this.parent.getState().getForwardEndDate();
        }

        if (this.forwardEndDate.getValue() != null) {
            return this.forwardEndDate.getValue();
        }

        // Fallback to today
        return new Date();
    }

    public void setForwardEndDate(Date endDate) {
        if (endDate != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(endDate);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);

            this.parent.getState().setForwardEndDate(cal.getTime());
        } else {
            // Should this ever happen?
            this.parent.getState().setForwardEndDate(null);
        }
    }

    public boolean isForwarding() throws GeneralException {
        return (null != getForward());
    }

    public boolean isForwardingError() {
        return this.forwardingError;
    }

    public void setForwardingError(boolean error) {
        this.forwardingError = error;
    }

    void addForwardingInfoToRequest(Identity identity,
                                    AccountRequest account) throws GeneralException {
        String newForwardName = getForwardName();
        addForwardingUserToRequest(identity, newForwardName, account);
        // temporary forwarding start/end
        addForwardingDateToRequest(identity, account, Identity.PRF_FORWARD_START_DATE, getForwardStart(), getForwardStartDate(), newForwardName);
        addForwardingDateToRequest(identity, account, Identity.PRF_FORWARD_END_DATE, getForwardEnd(), getForwardEndDate(), newForwardName);
    }

    /**
     * Updates the passed AccountRequst with attribute requests for forwarding user, start, and end dates as appropriate
     * @param targetIdentity The identity whose forwarding info will be updated
     * @param newForwardIdentityName The new forwarding user
     * @param forwardStartDate The new forwarding start date
     * @param forwardEndDate The new forwarding end date
     * @param accountRequest The account request to update
     */
    public static void addForwardingInfoToRequest(Identity targetIdentity, String newForwardIdentityName, Date forwardStartDate, Date forwardEndDate, AccountRequest accountRequest) throws GeneralException {
        // forwarding
        ForwardingHelper.addForwardingUserToRequest(targetIdentity, newForwardIdentityName, accountRequest);
        ForwardingHelper.addForwardingDateToRequest(targetIdentity, accountRequest, Identity.PRF_FORWARD_START_DATE, forwardStartDate != null, forwardStartDate, newForwardIdentityName);
        ForwardingHelper.addForwardingDateToRequest(targetIdentity, accountRequest, Identity.PRF_FORWARD_END_DATE, forwardEndDate != null, forwardEndDate, newForwardIdentityName);
    }

    /**
     * Updates the passed account request with an attribute request for forwarding user
     * @param targetIdentity The identity whose forwarding info will be updated
     * @param newForwardIdentityName The name of the new forwarding identity
     * @param accountRequest The account request to update
     */
    private static void addForwardingUserToRequest(Identity targetIdentity, String newForwardIdentityName, AccountRequest accountRequest) {
        String oldForwardIdentityName = Util.otoa(targetIdentity.getPreference(Identity.PRF_FORWARD));
        if (!Differencer.equal(oldForwardIdentityName, newForwardIdentityName)) {
            AttributeRequest req = new AttributeRequest();
            // assuming preferences and attributes can't have the same name,
            // may need to revisit this and add a prefix!
            req.setName(Identity.PRF_FORWARD);
            req.setOperation(ProvisioningPlan.Operation.Set);
            req.setValue(newForwardIdentityName);
            accountRequest.add(req);
        }
    }

    /**
     * Helper for buildProvisioningPlan, build an AttributeRequest for
     * either the forwarding start or end date if we need one.
     * @param newForward 
     */
    private static void addForwardingDateToRequest(Identity ident, AccountRequest account, String pref, boolean checked, Date date, String newForward) throws GeneralException {

        boolean needsRequest = false;

        Object o = ident.getPreference(pref);
        if (!checked || (newForward == null)) {
            // need to clear the date if we currently have one
            needsRequest = (o != null);
            date = null;
        } else if (date == null) {
            // I don't think this can happen, old code would ignore
            // it but it could be treated like the !chcked case
            // and set to null?
            needsRequest = (o != null);
        } else if (!(o instanceof Date)) {
            // probably could match string representatinos but
            // it really should be a Date object?
            needsRequest = true;
        } else {
            Date current = (Date) o;
            needsRequest = (!current.equals(date));
        }

        if (needsRequest) {
            AttributeRequest req = new AttributeRequest();
            req.setName(pref);
            req.setValue(date);
            req.setOperation(ProvisioningPlan.Operation.Set);
            account.add(req);
        }
    }

    /**
     * Validates that the start date is set to a value that is before the
     * end date.
     * 
     * @return True if start is before end; false otherwise
     * @throws GeneralException
     */
    boolean validateStartAndEndDates() throws GeneralException {

        boolean valid = true;

        Identity id = this.parent.getObject();
        if (id == null)
            return true;

        // validate that the start date is BEFORE the end date

        // either may be null, so only validate if both are non-null
        if (getForwardStart() && getForwardEnd()) {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();

            start.setTime(getForwardStartDate());
            end.setTime(getForwardEndDate());

            if (!start.before(end)) {
                valid = false;
                this.forwardingError = true;
                this.parent.addMessage(new Message(Message.Type.Error,
                        MessageKeys.ERR_DATE_ORDER), null);
            }
        }

        return valid;
    }

}
