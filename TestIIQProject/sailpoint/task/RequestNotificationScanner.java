/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import sailpoint.api.Explanator;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RoleEventGenerator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A task that will look for existing Request objects that deal with sunsetting a role or entitlement.
 * If a request object is that needs a notification and has an event type of to sunset,
 * then an email notification will be sent.
 */
public class RequestNotificationScanner extends AbstractTaskExecutor {

    private boolean terminate;
    private static Log log = LogFactory.getLog(RequestNotificationScanner.class);
    /**
     * Task result attribute for number of notifications sent.
     */
    private static final String RET_SENT = "sent";
    private static final String REQUEST_PROP_NOTIF_NEEDED = "notificationNeeded";
    public static final String EMAIL_TEMPLATE_ARG_REQUESTOR = "requestor";
    public static final String EMAIL_TEMPLATE_ARG_REQUESTEE = "requestee";
    public static final String EMAIL_TEMPLATE_ARG_ROLE = "role";
    public static final String EMAIL_TEMPLATE_ARG_ENTITLEMENT = "entitlement";
    public static final String EMAIL_TEMPLATE_ARG_SUNSET_DATE = "sunsetDate";

    /**
     * Allowed event types that this Scanner will process.
     */
    public static final List<String> allowedEvents = 
            Arrays.asList(RoleEventGenerator.EVENT_TYPE_ROLE_DEASSIGNMENT,
                          RoleEventGenerator.EVENT_TYPE_ROLE_DEPROVISIONING,
                          RoleEventGenerator.EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT);

    /**
     * Default constructor.
     */
    public RequestNotificationScanner() {
        super();
    }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#execute(sailpoint.api.SailPointContext, sailpoint.object.TaskSchedule, sailpoint.object.TaskResult, sailpoint.object.Attributes)
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
                        TaskResult result, Attributes<String, Object> args)
        throws Exception {

        Configuration sysConfig = context.getConfiguration();
        int sent = 0;

        if (sysConfig != null) {
            int days = sysConfig.getInt(Configuration.ROLE_SUNSET_NOTIFICATION_DAYS_REMINDER);

            // don't bother if the days set is to 0. The option will default to 0 if not found
            if (days > 0) {
                EmailTemplate template = getConfiguredTemplate(context);
                if (template == null) {
                    log.warn("Configured template for sunset expirations reminders not found. Skipping scanner execution.");
                } else {
                    QueryOptions qo = new QueryOptions(Filter.eq(REQUEST_PROP_NOTIF_NEEDED, true));
                    IncrementalObjectIterator<Request> requests = new IncrementalObjectIterator<>(context, Request.class, qo);

                    while (!terminate && requests != null && requests.hasNext()) {
                        Request currentRequest = requests.next();
                        final String eventType = currentRequest.getString(Request.ATT_EVENT_TYPE);

                        if (allowedEvents.contains(eventType)) {
                            if (isWithinNotificationRange(currentRequest.getEventDate(), days)) {
                                sendEmailNotification(currentRequest, template, context);
                                sent++;
                            }

                        }
                        // decache for every 20 updated request objects
                        if (sent % 20 == 0) {
                            context.decache();
                        }
                    }
                }
            }
        }
        result.setAttribute(RET_SENT, sent);
        result.setTerminated(terminate);
    }

    /**
     * Checks to see if the configured days before sunset is within the sunset date from today.
     * @param sunsetDate The sunset date of the Request
     * @param daysBefore The number of days before sunset to notify
     * @return True if today is between the sunset date and the number of days configured before the sunset date.
     */
    private boolean isWithinNotificationRange(Date sunsetDate, int daysBefore) {
        if (sunsetDate != null && daysBefore > 0) {
            //this will be the minimum date for the range to send a notification
            Date minimumDate = Util.incrementDateByDays(sunsetDate, -daysBefore);
            Date now = new Date();
            return Util.isDateBetween(now, minimumDate, sunsetDate);
        } else {
            return false;
        }

    }

    /**
     * Sets the Request to no longer needing a notification and sends an email notification
     *
     * @param request The Request object being processed
     * @param template The email template to use for the message
     * @param context The SailPoint Context
     * @throws GeneralException
     */
    private void sendEmailNotification(Request request, EmailTemplate template, SailPointContext context) throws GeneralException {

        Request locked = ObjectUtil.transactionLock(context, Request.class, request.getId());

        if (locked != null) {
            try {
                // Re-attach the template to this context since starting the transaction lock above
                // can get the object to have lazy loading issues
                context.attach(template);

                String requestee = locked.getString(RoleEventGenerator.ARG_IDENTITY_NAME);
                String requestor = locked.getString(RoleEventGenerator.ARG_ASSIGNER);

                final Filter requesteeFilter = Filter.eq(Identity.ATT_USERNAME, requestee);
                final Filter requestorFilter = Filter.eq(Identity.ATT_USERNAME, requestor);

                List<String> addresses = populateEmailAddresses(requestee, requestor, requesteeFilter, requestorFilter, context);
                Map<String, Object> variables = populateEmailOptions(requestee, requestor, requesteeFilter, requestorFilter,
                                                                     locked, context);
                EmailOptions options = new EmailOptions(addresses, variables);
                context.sendEmailNotification(template, options);

                // mark it as false after sending the notification
                locked.setNotificationNeeded(false);

                context.saveObject(locked);
            } finally {
                //commit to release the lock
                context.commitTransaction();
            }

        }

    }

    /**
     * Gets the configured email template for the sunset expiraton notification
     * @param context The SailPoint Context
     * @return The Email template that is configured through Global Settings
     * @throws GeneralException
     */
    private EmailTemplate getConfiguredTemplate(SailPointContext context) throws GeneralException {
        Configuration sysConfig = Configuration.getSystemConfig();
        if (sysConfig != null &&
                sysConfig.containsKey(Configuration.SUNSET_EXPIRATION_REMINDER_EMAIL_TEMPLATE)) {
            String template = sysConfig.getString(Configuration.SUNSET_EXPIRATION_REMINDER_EMAIL_TEMPLATE);
            return context.getObjectByName(EmailTemplate.class, template);
        }

        return null;
    }

    /**
     * Populates a map of arguments needed by the email template to create the body of the message.
     *
     * First it will try to get the display names of the requestee and the requestor.
     *
     * Then it will try and get an Entitlement display name if the Request is an attribute deassignment.
     * Otherwise it will try and get a Role display name.
     *
     * And finally it will put the sunset date on the options map.
     * 
     * @param requestee Name of the Requestee
     * @param requestor Name of the Requestor
     * @param requesteeFilter Filter to get the Requestee from the DB
     * @param requestorFilter Filter to get the Requestor from the DB
     * @param request The Request object being processed
     * @param context The SailPointContext
     * @return A Map of email template arguments needed for the body of the message.
     * @throws GeneralException 
     */
    private Map<String, Object> populateEmailOptions(String requestee, String requestor,
            Filter requesteeFilter, Filter requestorFilter, Request request, SailPointContext context) throws GeneralException {
        Map<String, Object> options = new HashMap<>();

        // Get and populate the identity names on the template
        String requesteeDisplayName = ObjectUtil.getStringPropertyFromObject(requesteeFilter,
                                                                 Identity.ATT_DISPLAYNAME, context, Identity.class);

        String requestorDisplayName = ObjectUtil.getStringPropertyFromObject(requestorFilter,
                                                                 Identity.ATT_DISPLAYNAME, context, Identity.class);

        if (Util.isNotNullOrEmpty(requesteeDisplayName)) {
            options.put(EMAIL_TEMPLATE_ARG_REQUESTEE, requesteeDisplayName);
        } else {
            // Fall back to the Username if displayName is not found
            options.put(EMAIL_TEMPLATE_ARG_REQUESTEE, requestee);
        }

        if (Util.isNotNullOrEmpty(requestorDisplayName)) {
            options.put(EMAIL_TEMPLATE_ARG_REQUESTOR, requestorDisplayName);
        } else {
            options.put(EMAIL_TEMPLATE_ARG_REQUESTOR, requestor);
        }

        final String eventType = request.getString(Request.ATT_EVENT_TYPE);

        // If its an attribute deassignment, get the entitlement display name in real time
        if (RoleEventGenerator.EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT.equals(eventType)) {
            String appId = request.getString(RoleEventGenerator.ARG_APPLICATION);
            String entName = request.getString(RoleEventGenerator.ARG_NAME);
            String entValue = request.getString(RoleEventGenerator.ARG_VALUE);
            String entDisplayName = Explanator.getDisplayValue(appId, entName, entValue);

            if (Util.isNotNullOrEmpty(entDisplayName)) {
                options.put(EMAIL_TEMPLATE_ARG_ENTITLEMENT, entDisplayName);
            }
        } else {
            // Otherwise, it is a role based event so get the role display name for the template
            String roleName = request.getString(RoleEventGenerator.ARG_ROLE_NAME);
            final String BUNDLE_PROP_NAME = "name";
            final String BUNDLE_PROP_DISPLAY_NAME = "displayName";
            final Filter bundleFilter = Filter.eq(BUNDLE_PROP_NAME, roleName);

            String roleDisplayName = ObjectUtil.getStringPropertyFromObject(bundleFilter, BUNDLE_PROP_DISPLAY_NAME,
                                                                      context, Bundle.class);
            if (Util.isNotNullOrEmpty(roleDisplayName)) {
                options.put(EMAIL_TEMPLATE_ARG_ROLE, roleDisplayName);
            } else {
                // Fall back to the role name
                options.put(EMAIL_TEMPLATE_ARG_ROLE, roleName);
            }

        }

        // Finally set the sunset date for the template
        options.put(EMAIL_TEMPLATE_ARG_SUNSET_DATE, request.getDate(RoleEventGenerator.ARG_DATE));

        return options;
    }

    /**
     * Uses the Request object that contains all the arguments to populate the list of email addresses
     * to send the notification to.
     *
     * This method will try and extract the Requestee by argument RoleEventGenerator.ARG_IDENTITY_NAME
     * and Requestor by argument RoleEventGenerator.ARG_ASSIGNER
     *
     * @param requestee The name of the Requestee
     * @param requestor The name of the Requestor
     * @param requesteeFilter Filter to get the Requestee from the DB
     * @param requestorFilter Filter to get the Requestor from the DB
     * @param context The SailPoint context
     * @return The List of email addresses to send the notification to.
     * @throws GeneralException
     */
    private List<String> populateEmailAddresses(String requestee, String requestor,
            Filter requesteeFilter, Filter requestorFilter, SailPointContext context) throws GeneralException {
        List<String> addresses = new ArrayList<>();

        String requesteeEmail = ObjectUtil.getStringPropertyFromObject(requesteeFilter, Identity.ATT_EMAIL,
                                                                 context, Identity.class);

        String requestorEmail = ObjectUtil.getStringPropertyFromObject(requestorFilter, Identity.ATT_EMAIL,
                                                                 context, Identity.class);

        if (Util.isNotNullOrEmpty(requesteeEmail)) {
            addresses.add(requesteeEmail);
        } else if (log.isWarnEnabled()) {
            log.warn(String.format("The Requestee, %s, does not have an email configured. Ignoring notification.",
                    requestee));
        }

        if (Util.isNotNullOrEmpty(requestorEmail)) {
            addresses.add(requestorEmail);
        } else if (log.isWarnEnabled()) {
            log.warn(String.format("The Requestor, %s, does not have an email configured. Ignoring notification.",
                    requestor));
        }

        return addresses;
    }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#terminate()
     */
    public boolean terminate() {
        this.terminate = true;
        return true;
    }
}
