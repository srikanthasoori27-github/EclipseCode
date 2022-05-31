package sailpoint.service;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.service.identity.TargetAccountDTO;
import sailpoint.service.identityrequest.IdentityRequestService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;
import sailpoint.workflow.IdentityLibrary;

/**
 * Service to analyze and get interesting data out of ApprovalItem objects.  For convenience, 
 * we have both static and instance methods for data retrieval.  Much of this code was refactored out
 * of ApprovalSetDTO to share between JSF and REST approval work items. 
 */
public class ApprovalItemsService {

    private static Log log = LogFactory.getLog(ApprovalItemsService.class);

    /**
     * An attribute in the ApprovalItem attribute map that indicates that the
     * item at one point had a sunrise or sunset (and possibly still does).
     */
    public static final String ATT_HAD_SUNRISE_SUNSET = "hadSunriseSunset";
    public static final String ATT_BATCH_REQUEST = "batchRequest";

    /* Approval item names that indicate Role approval item type */
    private static final List<String> ROLE_TYPE_APPROVAL_ITEM_NAMES =
            new ArrayList<String>(Arrays.asList(
                    ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES,
                    ProvisioningPlan.ATT_IIQ_DETECTED_ROLES));

    private ApprovalItem approvalItem;
    private SailPointContext context;

    /**
     * Constructor if using instance of this service
     * @param context SailPointContext
     * @param approvalItem ApprovalItem to analyze
     */
    public ApprovalItemsService(SailPointContext context, ApprovalItem approvalItem) throws InvalidParameterException {
        if (approvalItem == null) {
            throw new InvalidParameterException("approvalItem");
        }
        if (context == null) {
            throw new InvalidParameterException("context");
        }
        
        this.approvalItem = approvalItem;
        this.context = context;
    }

    /**
     * Get the display value for an approval item with one value
     * @param approvalItem ApprovalItem to analyze
     * @return Display Value
     */
    public static String getDisplayValue(ApprovalItem approvalItem, Locale locale) {
        String displayValue = null;
        if (approvalItem.getValue() instanceof String && !Util.isAnyNullOrEmpty((String)approvalItem.getValue())) {
            displayValue = getDisplayValue(approvalItem, (String)approvalItem.getValue(), locale);
        /*
         * bug25596 : End user added a list of values for custom processing and that's
         * causing trouble in the mobile ui. We are processing to return the list of values
         * as a CSV string, to prevent from getting a:
         * sailpoint.tools.xml.PersistentArrayList cannot be cast to java.lang.String
         * exception and allow approval processing in the UI. But we are not displaying
         * the values in the UI, unless the customer says they require to be displayed.
         */

        } else if (null != approvalItem.getValue() && approvalItem.getValue() instanceof List) {
            ArrayList<String> approvalItemValuesArrayList = (ArrayList) approvalItem.getValue();
            Set displayNameSet = new HashSet();
            for (String approvalItemValueItem : approvalItemValuesArrayList) {
                if(approvalItemValueItem.length() > 0) {
                    displayNameSet.add(getDisplayValue(approvalItem, approvalItemValueItem, locale));
                }
            }
            displayValue = Util.otos(displayNameSet);
        }
        return displayValue;
    }

    /**
     * Get the display value for a single value in the approval item value list.
     * The method tries to create a useful display value. For Entitlements we try 
     * create a complex display that can help approvers understand the entitlement.
     * When it is not an entitlement or if we do not create the complex display we 
     * will just display the value or the displayValue if we have it.  
     * @param approvalItem ApprovalItem to analyze
     * @param value A value - potentially a string or list. 
     * @return Display value for the value given
     */
    public static String getDisplayValue(ApprovalItem approvalItem, Object value, Locale locale) {
        String displayValue = null;
        if (approvalItem != null) {
            //If it is role we use this section
            if (isRole(approvalItem) && (null != value)) {
                if (approvalItem.getDisplayValue() != null){
                    displayValue = approvalItem.getDisplayValue();
                } else {
                    displayValue = value.toString();
                }
                
                //if is matches any of these operation types, and it is an entitlement lets create one
            } else if ((isCreate(approvalItem) || isModify(approvalItem) || isAddOperation(approvalItem) ||
                    isRemoveOperation(approvalItem)) && (value instanceof String)) {
                // Try to split the value
                Pair<String, String> valuePair = splitValue((String)value);

                // If no first in the pair, this is just a straight value without attribute name
                boolean isNameValuePair = Util.isNotNullOrEmpty(valuePair.getFirst());
                String attributeName = (isNameValuePair) ? valuePair.getFirst() : approvalItem.getName();
                String attributeValue = valuePair.getSecond();

                // Attempt to get a display value for the attribute value
                String explainedValue = Explanator.getDisplayValue(approvalItem.getApplication(), attributeName, attributeValue);

                StringBuilder displayValueBuilder = new StringBuilder();
                if (isNameValuePair) {
                    // Attempt to get an attribute display name from identity config
                    ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
                    String valueName = identityConfig.getDisplayName(attributeName, locale);
                    displayValueBuilder.append(attributeName);
                    // If we found a display name, surround it in parentheses
                    if (!Util.nullSafeEq(attributeName, valueName)) {
                        displayValueBuilder.append(" (" + valueName + ")");
                    }
                    // Keep the equals and single quote
                    displayValueBuilder.append(" = '");
                }

                // IIQMAG-2329 make the explained (more readable) value the primary value
                displayValueBuilder.append(explainedValue);

                if (isNameValuePair) {
                    displayValueBuilder.append("'");
                }
                // If we found a display value, surround it in parentheses
                if (!Util.nullSafeEq(explainedValue, attributeValue)) {
                    // IIQMAG-2329 make the attribute value the secondary value
                    displayValueBuilder.append(" (" + attributeValue + ")");
                } else if (!isNameValuePair && approvalItem.getDisplayValue() != null){
                    displayValue = approvalItem.getDisplayValue();
                }
                if(displayValue == null) {
                    displayValue = displayValueBuilder.toString();
                }
                //Last but not least we will just make a simple one
            } else if (value instanceof String) {
                if (approvalItem.getDisplayValue() != null){
                    displayValue = approvalItem.getDisplayValue();
                } else {
                    displayValue = Explanator.getDisplayValue( approvalItem.getApplication(), approvalItem.getName(), (String) value );
                } 
            }
        }

        // If we still ended up with nothing ... try to toString() the value.
        if ((null == displayValue) && (null != value)) {
            displayValue = value.toString();
        }

        return displayValue;
    }

    /**
     * Given the value, attempt to split it into a name/value pair. This is to handle the case 
     * that the value is of the form: groupmbr = 'benefitcommittee'
     * @param originalValue String value
     * @return Pair object, with name as first (or null), and value with quotes trimmed as second
     */
    private static Pair<String, String> splitValue (String originalValue) {
        String[] fields = originalValue.split(" = '");
        String nameField = null;
        String valueField;
        if (fields.length == 2) {
            nameField = fields[0];
            valueField = fields[1];
            // Remove the extra single quote if present
            if (valueField.charAt(valueField.length() - 1) ==  '\'') {
                valueField = valueField.substring(0, valueField.length() - 1);
            } 
        } else {
            valueField = originalValue;
        }

        return new Pair<String, String>(nameField, valueField);        
    }

    /**
     * Get the display name for the Application targeted by this approval item.
     * If displaying an IdentityItem in the UI or auditing an item, this
     * method should be called to get the applicationName.  It maps
     * our internal System name "IIQ" to a friendlier version.
     *
     * @ignore We don't want to expose our internal IIQ appname
     * so this method will help resolve IIQ to IdentityIQ.
     */
    public static String getApplicationDisplayName(ApprovalItem approvalItem) {
        String applicationName = null;
        if ( approvalItem != null ) {
            applicationName = approvalItem.getApplication();
            if ( Util.nullSafeEq(applicationName, ProvisioningPlan.APP_IIQ, false) ) {
                applicationName = BrandingServiceFactory.getService().getApplicationName();
            }
        }
        return applicationName;
    }

    /**
     * Get the display name for the account targeted by the approval item.  If this is for a 
     * new account, return a summary string.
     * @param context SailPointContext
     * @param approvalItem ApprovalItem to analyze
     * @param identity Identity targeted by the approval item
     * @param locale Locale for localization
     * @param timeZone TimeZone for localization
     * @return String display name for the account
     */
    public static String getAccountDisplayName(SailPointContext context, ApprovalItem approvalItem, Identity identity, Locale locale, TimeZone timeZone) {
        String accountDisplayName;
        if (approvalItem.getNativeIdentity() == null) {
            accountDisplayName = getMessage(MessageKeys.LCM_REQUEST_ENTITLEMENTS_SUMMARY_NEW_ACCOUNT, locale, timeZone);
        } else {
            try {
                accountDisplayName = getAccountDisplayNameFromLink(context, approvalItem, identity);
            } catch ( GeneralException e ) {
                /* Below we handle the case of an exception or if there 
                 * was no matching link by returning the native identity */
                log.debug("Error getting account display name", e);
                accountDisplayName = null;
            }
            if( accountDisplayName == null ) {
                accountDisplayName = approvalItem.getNativeIdentity();
            }
        }

        return accountDisplayName;
    }

    /**
     * Get the description for the value of a single value approval item
     * @param context SailPointContext
     * @param approvalItem ApprovalItem to analyze
     * @param locale Locale for localization
     * @return Description
     */
    public static String getDescription(SailPointContext context, ApprovalItem approvalItem, Locale locale) throws GeneralException {
        if (approvalItem == null || approvalItem.getValue() instanceof Collection) {
            // If no approval item or its multi valued, no description
            return null;
        }
        
        return getDescription(context, approvalItem, (String)approvalItem.getValue(), locale);
    }

    /**
     * Get the description for the given value of an approval item
     * @param context SailPointContext
     * @param approvalItem ApprovalItem to analyze
     * @param value Value to use for description
     * @param locale Locale for localization
     * @return Description
     */
    public static String getDescription(SailPointContext context, ApprovalItem approvalItem, String value, Locale locale) throws GeneralException {
        String description = null;
        if (approvalItem != null) {
            if (isRole(approvalItem) && value != null) {
               Localizer localizer = new Localizer(context, value, true);
               description = localizer.getLocalizedValue(Localizer.ATTR_DESCRIPTION, locale);
               if (Util.isNullOrEmpty(description) && Util.isNotNullOrEmpty((String)(approvalItem.getAttribute("id")))) {
                   //Fall back to using a potential id to find the description
                   localizer = new Localizer(context, (String)approvalItem.getAttribute("id"), false);
                   description = localizer.getLocalizedValue(Localizer.ATTR_DESCRIPTION, locale);
               }
            } else if (value != null &&
                    approvalItem.getApplication() != null &&
                    approvalItem.getName() != null) {
                description = Explanator.getDescription(approvalItem.getApplication(), approvalItem.getName(), value, locale );
            }
        }
        
        /* Localizer can return empty string instead of null if there is no description,. 
         * We want null for the description in all cases if it doesn't exist. */
        return Util.getString(description);

    }

    /**
     * Get the localized operation string for the approval item
     * @param approvalItem ApprovalItem to analyze
     * @param locale Locale for localization
     * @param timeZone TimeZone for localization
     * @return String representation of the operation
     */
    public static String getOperation(ApprovalItem approvalItem, Locale locale, TimeZone timeZone) {
        String operation = null;
        if (approvalItem.getOperation() != null) {
            String opMessageKey = "provisioning_plan_op_" + approvalItem.getOperation().toLowerCase();
            operation = getMessage(opMessageKey, locale, timeZone);
        }
        return operation;
    }

    /**
     * Check if approval item contains a request for account creation
     * @param approvalItem ApprovalItem to analyze
     * @return True if approval item contains account creation
     */
    public static boolean isCreate(ApprovalItem approvalItem) {
        return ProvisioningPlan.AccountRequest.Operation.Create.toString().equals(approvalItem.getOperation()) ||
                isForceNewAccount(approvalItem);
    }
    
    /**
     * Check if approval item contains a request for account modification
     * @param approvalItem ApprovalItem to analyze
     * @return True if approval item contains account modification
     */
    public static boolean isModify(ApprovalItem approvalItem) {
        return ProvisioningPlan.AccountRequest.Operation.Modify.toString().equals(approvalItem.getOperation());
    }
    
    /**
     * Check if approval item contains an operation to add a permission or attribute
     * @param approvalItem ApprovalItem to analyze
     * @return True if approval item operation is to add a permission or attribute
     */
    public static boolean isAddOperation(ApprovalItem approvalItem) {
        return ProvisioningPlan.Operation.Add.toString().equals(approvalItem.getOperation());
    }
    
    /**
     * Check if approval item contains an operation to remove a permission or attribute
     * @param approvalItem ApprovalItem to analyze
     * @return True if approval item operation is to remove a permission or attribute
     */
    public static boolean isRemoveOperation(ApprovalItem approvalItem) {
        return ProvisioningPlan.Operation.Remove.toString().equals(approvalItem.getOperation());
    }

    /**
     * Check if approval item forces a new account 
     * @param approvalItem ApprovalItem
     * @return True if account creation is forced
     */
    public static boolean isForceNewAccount(ApprovalItem approvalItem) {
        Attributes<String,Object> attributes = approvalItem.getAttributes();
        return (null != attributes) ? attributes.getBoolean(ProvisioningPlan.ARG_FORCE_NEW_ACCOUNT, false) : false;
    }

    /**
     * Check if approval item is for a role
     * @param approvalItem ApprovalItem
     * @return True if approval item is for a role.
     */
    public static boolean isRole(ApprovalItem approvalItem) {
        boolean isRole = false;
        if (approvalItem != null && approvalItem.getName() != null) {
            isRole = ROLE_TYPE_APPROVAL_ITEM_NAMES.contains(approvalItem.getName());
        }
        return isRole;
    }

    /**
     * Check if approval item is an account delete or disable from a Lifecycle flow
     * @param approvalItem ApprovalItem to analyze
     * @return True if approval item is an account delete or disable from Lifecycle
     */
    public static boolean isLifecycleDeleteOrDisableOperation(ApprovalItem approvalItem) {

        boolean isLifecycleDelete = false;
        String flowName = (String)approvalItem.getAttribute(IdentityLibrary.VAR_FLOW);
        if (null != flowName) {
            if (flowName.contains("Lifecycle") && 
                    (ProvisioningPlan.AccountRequest.Operation.Delete.toString().equals(approvalItem.getOperation()) ||
                            ProvisioningPlan.AccountRequest.Operation.Disable.toString().equals(approvalItem.getOperation()))) {
                isLifecycleDelete = true;
            }
        }
        return isLifecycleDelete;
    }

    /**
     * Get the account display name for an existing link on the identity
     * @param context SailPointContext
     * @param approvalItem ApprovalItem
     * @param identity Identity 
     * @return Link display name, or null
     * @throws GeneralException
     */
    private static String getAccountDisplayNameFromLink(SailPointContext context, ApprovalItem approvalItem, Identity identity ) throws GeneralException {
        LinkService linksService = new LinkService(context);
        return linksService.getAccountDisplayName(identity, approvalItem.getApplication(), approvalItem.getInstance(), approvalItem.getNativeIdentity());
    }
    
    /**
     * Get localized string
     * @param messageKey Message Key
     * @param locale Locale
     * @param timeZone TimeZone
     * @return Localized string
     */
    private static String getMessage(String messageKey, Locale locale, TimeZone timeZone) {
        return new Message(messageKey).getLocalizedMessage(locale, timeZone);
    }

    /**
     * Get the display value for an approval item with one value
     * 
     * @return Display Value
     */
    public String getDisplayValue(Locale locale) {
        return getDisplayValue(this.approvalItem, locale);
    }

    /**
     * Get the display value for a single value in the approval item value list 
     * @param value String value 
     * @return Display value for the value given
     */
    public String getDisplayValue(String value, Locale locale) {
        return getDisplayValue(this.approvalItem, value, locale);
    }

    /**
     * Get the display name for the Application targeted by this approval item.
     * If displaying an IdentityItem in the UI or auditing an item, this
     * method should be called to get the applicationName.  It maps
     * our internal System name "IIQ" to a friendlier version.
     *
     * @ignore We don't want to expose our internal IIQ appname
     * so this method will help resolve IIQ to IdentityIQ.
     */
    public String getApplicationDisplayName() {
        return getApplicationDisplayName(this.approvalItem);
    }

    /**
     * Get the display name for the account targeted by the approval item.  If this is for a 
     * new account, return a summary string.
     * @param identity Identity targeted by the approval item
     * @param locale Locale for localization
     * @param timeZone TimeZone for localization
     * @return String display name for the account
     */
    public String getAccountDisplayName(Identity identity, Locale locale, TimeZone timeZone) {
        return getAccountDisplayName(this.context, this.approvalItem, identity, locale, timeZone);
    }

    /**
     * Get the description for the value of a single value approval item
     * @param locale Locale for localization
     * @return Description
     */
    public String getDescription(Locale locale) throws GeneralException {
        return getDescription(this.context, this.approvalItem, locale);
    }

    /**
     * Get the description for the given value of an approval item
     * @param value Value to use for description
     * @param locale Locale for localization
     * @return Description
     */
    public String getDescription(String value, Locale locale) throws GeneralException {
        return getDescription(this.context, this.approvalItem, value, locale);
    }

    /**
     * Get the localized operation string for the approval item
     * @param locale Locale for localization
     * @param timeZone TimeZone for localization
     * @return String representation of the operation
     */
    public String getOperation(Locale locale, TimeZone timeZone) {
        return getOperation(this.approvalItem, locale, timeZone);
    }

    /**
     * Check if approval item contains a request for account creation
     * @return True if approval item contains account creation
     */
    public boolean isCreate() {
        return isCreate(this.approvalItem);
    }

    /**
     * Check if approval item forces a new account 
     * @return True if account creation is forced
     */
    public boolean isForceNewAccount() {
        return isForceNewAccount(this.approvalItem);
    }

    /**
     * Check if approval item is for a role
     * @return True if approval item is for a role.
     */
    public boolean isRole() {
        return isRole(this.approvalItem);
    }

    /**
     * Get a list of TargetAccount objects from the matching IdentityRequestItem
     * @param identityRequest IdentityRequest to match
     * @param targetIdentity Identity this is targeting
     * @return List of TargetAccount objects
     * @throws GeneralException
     */
    public List<TargetAccountDTO> getTargetAccounts(IdentityRequest identityRequest, Identity targetIdentity) throws GeneralException {
        List<TargetAccountDTO> targetAccounts = new ArrayList<>();
        if (identityRequest == null) {
            throw new InvalidParameterException("request");
        }
        // Target accounts only make sense for role approvals.
        if (this.isRole()) {
            IdentityRequestService identityRequestService = new IdentityRequestService(this.context, identityRequest);
            targetAccounts.addAll(identityRequestService.getTargetAccounts(this.approvalItem, targetIdentity));
        }

        return targetAccounts;
    }

    /**
     * Get the application for this approval item
     * @return Application, or null if it is not definied
     * @throws GeneralException
     */
    public Application getApplication() throws GeneralException {
        Application application = null;
        if (this.approvalItem.getApplication() != null && !isIIQApplication()) {
            application = this.context.getObjectByName(Application.class, this.approvalItem.getApplication());   
        }
        return application;
    }

    /**
     * Check if this targets IdentityIQ application or not (for roles)
     * @return True if this is targeting IdentityIQ application 
     */
    public boolean isIIQApplication() {
        return Util.nullSafeEq(this.approvalItem.getApplicationName(), ProvisioningPlan.APP_IIQ, false);
    }

    /**
     * Get the role that this approval targets
     * @param identityRequestId the identity request id
     * @return Bundle targeted by this approval item, or null if not a role approval
     * @throws GeneralException
     */
    public Bundle getAccessRole(String identityRequestId) throws GeneralException {
        Bundle accessRole = null;

        if (this.isRole() && this.approvalItem.getValue() != null) {
            /*
             * bug25596 : End user added a list of values for custom processing and that's
             * causing trouble in the mobile ui. We don't expect to have a role's info in the values list, so there
             * is no role to return in this case; leave accessRole null.
             */
            if (approvalItem.getValue() instanceof List) {
                log.warn("List of values are not supported as arguments, a String value is the expected.");
            }
            else if (approvalItem.getValue() instanceof String && !Util.isAnyNullOrEmpty((String)approvalItem.getValue())) {
                // Normally we can just look up the role based on the role name in the approval item value.
                accessRole = this.context.getObjectByName(Bundle.class, (String) this.approvalItem.getValue());

                // If we didn't find a role using that role name, it's possible the role was renamed after the approval
                // was created. So get the original role id from the identity request item and try looking it up that
                // way.
                if (accessRole == null) {
                    IdentityRequest identityRequest = this.context.getObjectByName(IdentityRequest.class, identityRequestId);
                    IdentityRequestService identityRequestService = new IdentityRequestService(this.context, identityRequest);
                    IdentityRequestItem identityRequestItem = identityRequestService.findIdentityRequestItem(approvalItem);
                    if (identityRequestItem != null) {
                        String roleId = identityRequestItem.getStringAttribute("id");
                        if (roleId != null) {
                            accessRole = this.context.getObjectById(Bundle.class, roleId);
                        }
                    }
                }
            }
        }
        return accessRole;
    }

    /**
     * Get the managed attribute that this approval targets
     * @return ManagedAttribute, or null if this is not an entitlement approval
     * @throws GeneralException
     */
    public ManagedAttribute getAccessEntitlement() throws GeneralException {
        if (!this.isRole() && 
                this.approvalItem.getApplication() != null &&
                this.approvalItem.getName() != null &&
                this.approvalItem.getValue() != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("application.name", this.approvalItem.getApplication()));
            ops.add(Filter.ne("type", ManagedAttribute.Type.Permission.name()));
            ops.add(Filter.eq("attribute", this.approvalItem.getName()));
            ops.add(Filter.eq("value", this.approvalItem.getValue()));
            
            //Should only be one
            List<ManagedAttribute> managedAttributes =  this.context.getObjects(ManagedAttribute.class, ops);
            if (managedAttributes != null && managedAttributes.size() > 0) {
                if (managedAttributes.size() > 1) {
                    log.debug("More than one managed attribute found for approval item: " + this.approvalItem.getId());
                }
                return managedAttributes.get(0);
            }
        }
        
        return null;
    }

    /**
     * Return the number of comments (including the requester comment) for this
     * approval item.
     */
    public int getCommentCount() {
        int requesterCount = (hasRequesterComment()) ? 1 : 0;
        return Util.size(this.approvalItem.getComments()) + requesterCount;
    }

    /**
     * Return whether there is a non-null requester comment.
     */
    private boolean hasRequesterComment() {
        return !Util.isNullOrEmpty(this.approvalItem.getRequesterComments());
    }

    /**
     * Return the comments for this approval item, including the requester
     * comment if it is available.
     *
     * @param  item  The WorkItem this approval item is a part of.
     *
     * @return A non-null list of comments for this approval item.
     */
    public List<Comment> getComments(WorkItem item) {
        List<Comment> comments = new ArrayList<Comment>();

        // If there is a requester comment, it is always the oldest so add it first.
        if (hasRequesterComment()) {
            // The requester is the author and the date is the creation date of
            // the work item.
            String author =
                (null != item.getRequester()) ? item.getRequester().getDisplayableName() : null;
            Date date = item.getCreated();
            Comment comment =
                new Comment(this.approvalItem.getRequesterComments(), author);
            comment.setDate(date);
            comments.add(comment);
        }

        // Now add all of the "real" comments.
        if (null != this.approvalItem.getComments()) {
            comments.addAll(this.approvalItem.getComments());
        }

        return comments;
    }

    /**
     * Add a comment to this approval item.  The comment will be HTML escaped
     * before being stored.
     *
     * @param  item       The WorkItem for this approval.
     * @param  commenter  The identity making the comment.
     * @param  comment    The comment text.
     *
     * @return The comment that was added.
     *
     * @throws InvalidParameterException  If the comment was null or empty.
     */
    public Comment addComment(WorkItem item, Identity commenter, String comment)
        throws GeneralException, InvalidParameterException {
        if (item == null) {
            throw new InvalidParameterException("item");
        }
        
        // Throw if there is no comment.
        comment = Util.trimWhitespace(comment);
        if (Util.isNullOrEmpty(comment)) {
            throw new InvalidParameterException(new Message(MessageKeys.ERR_WORK_ITEM_NO_COMMENT));
        }

        Comment c = new Comment(comment, commenter.getDisplayableName());
        ApprovalItem itemToEdit = findApprovalItem(item, this.approvalItem.getId());
        if (itemToEdit == null) {
            // Use GeneralException here since this is really weird case of work item getting changed out 
            // from under us or wrong work item
            throw new GeneralException("Could not find the approval item on the work item");
        }

        itemToEdit.add(c);
        this.context.saveObject(item);
        this.context.commitTransaction();

        return c;
    }

    /**
     * Get the assignment note for an identity request
     * @param identityRequest Identity Request containing the assignment note
     * @param targetIdentity the identity with the assignment note only required if operation is "Remove"
     * @return Assignment note, or null
     * @throws GeneralException
     */
    public String getAssignmentNote(IdentityRequest identityRequest, Identity targetIdentity) throws GeneralException {
        String assignmentNote = null;
        if (isRole() && null != identityRequest) {
            IdentityRequestService identityRequestService = new IdentityRequestService(this.context, identityRequest);
            assignmentNote = identityRequestService.getAssignmentNote(this.approvalItem, targetIdentity);
        }
        return assignmentNote;
    }

    /**
     * Set the sunrise and/or sunset dates on the approval item
     * @param workItem WorkItem that holds the approval item
     * @param sunrise Sunrise Date, or null
     * @param sunset Sunset Date, or null
     * @throws GeneralException
     */
    public void setSunriseSunset(WorkItem workItem, Date sunrise, Date sunset) throws GeneralException {
        if (workItem == null) {
            throw new InvalidParameterException("workItem");
        }

        ApprovalItem itemToEdit = findApprovalItem(workItem, this.approvalItem.getId());
        if (itemToEdit == null) {
            // Use GeneralException here since this is really weird case of work item getting changed out 
            // from under us or wrong work item
            throw new GeneralException("Could not find the approval item on the work item");
        }

        boolean valueChanged = false;
        if (!Util.nullSafeEq(itemToEdit.getStartDate(), sunrise, true)) {
            itemToEdit.setStartDate(sunrise);
            valueChanged = true;
        }

        if (!Util.nullSafeEq(itemToEdit.getEndDate(), sunset, true)) {
            itemToEdit.setEndDate(sunset);
            valueChanged = true;
        }

        if (valueChanged) {
            // Save the fact that this item had sunrise/sunset dates if they
            // were both cleared.
            if ((null == sunrise) && (null == sunset)) {
                itemToEdit.setAttribute(ATT_HAD_SUNRISE_SUNSET, true);
            }

            this.context.saveObject(workItem);
            this.context.commitTransaction();
        }
    }

    /**
     * Get the risk score weight for the role targeted by the approval item.
     * If this is not a role item, return null.
     * @param identityRequestId the identity request id
     * @return Integer risk score weight, or null if not a role
     * @throws GeneralException
     */
    public Integer getRiskScoreWeight(String identityRequestId) throws GeneralException {
        Integer riskScoreWeight = null;
        if (this.isRole()) {
            Bundle bundle = getAccessRole(identityRequestId);
            if (bundle != null) {
                riskScoreWeight = bundle.getRiskScoreWeight();
            }
        }
        return riskScoreWeight;        
    }

    /**
     * Get the owner for the access item targeted by the approval item.
     * For roles, return role owner.
     * For entitlements, return managed attribute owner if it exists, otherwise
     * return the application owner.
     * @param identityRequestId the identity request id
     * @return Identity owner, or null
     * @throws GeneralException
     */
    public Identity getOwner(String identityRequestId) throws GeneralException {
        Identity owner = null;

        if (isRole()) {
            Bundle role = getAccessRole(identityRequestId);
            if (role != null) {
                owner = getAccessRole(identityRequestId).getOwner();
            }
        }
        else if (!isRole()) {
            ManagedAttribute entitlement = getAccessEntitlement();
            Application application = getApplication();
            if (entitlement != null) {
                owner = entitlement.getOwner();
            }
            if (owner == null && application != null) {
                owner = application.getOwner();
            } 
        }
        
        return owner;
    }

    /**
     * Get the id of the target item
     * @param identityRequestId the identity request id
     * @return String id of target item
     * @throws GeneralException
     */
    public String getTargetItemId(String identityRequestId) throws GeneralException {
        SailPointObject requestedObject = this.isRole() ? this.getAccessRole(identityRequestId) :this.getAccessEntitlement();
        return requestedObject != null ? requestedObject.getId() : null;
    }

    /**
     * Find the approval item on the given work item
     * @param workItem WorkItem
     * @param approvalItemId ID of the approval item
     * @return ApprovalItem
     */
    public ApprovalItem findApprovalItem(WorkItem workItem, String approvalItemId) throws InvalidParameterException {
        ApprovalItem foundItem = null;
        if (workItem == null) {
            throw new InvalidParameterException("workItem");
        }

        ApprovalSet set = workItem.getApprovalSet();
        if (set != null) {
            for (ApprovalItem approvalItem : Util.safeIterable(set.getItems())) {
                if (approvalItem.getId().equals(approvalItemId)) {
                    foundItem = approvalItem;
                    break;
                }
            }
        }
        
        return foundItem;
    }
}
