package sailpoint.web.accessrequest;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccessRequestAccountInfo;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningTarget;
import sailpoint.service.AttachmentConfigDTO;
import sailpoint.service.AttachmentDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class to hold common fields for representations
 * of requested roles and entitlements
 */
public abstract class RequestedAccessItem implements AccessItem {

    public static final String ID_KEY = "id";
    public static final String SUNRISE_KEY = "sunrise";
    public static final String SUNSET_KEY = "sunset";
    public static final String COMMENT_KEY = "comment";
    public static final String ACCOUNT_SELECTIONS_KEY = "accountSelections";
    public static final String ATTACHMENTS = "attachments";
    public static final String ATTACHMENT_CONFIG_LIST = "attachmentConfigList";

    private String id;
    private Date sunrise;
    private Date sunset;
    private String comment;
    private List<AccessRequestAccountInfo> accountSelections;
    private List<AttachmentDTO> attachments;
    private List<AttachmentConfigDTO> attachmentConfigList;
    
    private Map<String, ProvisioningTarget> provisioningTargetMap;
    
    /**
     * Constructor.
     * @param data Map of properties.
     * @throws GeneralException
     */
    protected RequestedAccessItem(Map<String, Object> data) throws GeneralException {
        if (data != null) {
            id = (String) data.get(ID_KEY);
            sunrise = Util.getDate(data, SUNRISE_KEY);
            sunset = Util.getDate(data, SUNSET_KEY);
            comment = (String)data.get(COMMENT_KEY);
            if (null != data.get(ACCOUNT_SELECTIONS_KEY)) {
                accountSelections = convertAccountSelections(data.get(ACCOUNT_SELECTIONS_KEY));
            }
            if (null != data.get(ATTACHMENTS)) {
                attachments = new ArrayList<>();
                for (Map attachment : (List<Map>) data.get(ATTACHMENTS)) {
                    attachments.add(new AttachmentDTO(attachment));
                }
            }
            attachmentConfigList = (List<AttachmentConfigDTO>) data.get(ATTACHMENT_CONFIG_LIST);
        }
        
        validateState();
    }

    /**
     * Sub classes should provide custom initialization of ProvisioningTargets with their
     * identifying information.
     * @param context SailPointContext
     * @return ProvisioningTarget
     * @throws GeneralException
     */
    protected abstract ProvisioningTarget initializeProvisioningTarget(SailPointContext context) throws GeneralException;

    /**
     * Given a list of RequestedAccessItems, get a map of ProvisioningTargets keyed by Identity ID
     * @param context SailPointContext
     * @param requestedItems List of RequestedAccessItems
     * @return Map of lists of ProvisioningTargets keyed by Identity id
     * @throws GeneralException
     */
    public static Map<String, List<ProvisioningTarget>> getProvisioningTargets(SailPointContext context,
                                                                               List<? extends RequestedAccessItem> requestedItems)
            throws GeneralException {
        Map<String,List<ProvisioningTarget>> targets = new HashMap<String,List<ProvisioningTarget>>();

        for (RequestedAccessItem requestedItem : Util.safeIterable(requestedItems)) {
            Map<String, ProvisioningTarget> currentTargets =
                    requestedItem.getProvisioningTargets(context);
            if (null != currentTargets) {
                for (Map.Entry<String,ProvisioningTarget> entry : currentTargets.entrySet()) {
                    String identityId = entry.getKey();
                    List<ProvisioningTarget> targetsForIdentity = targets.get(identityId);
                    if (null == targetsForIdentity) {
                        targetsForIdentity = new ArrayList<ProvisioningTarget>();
                        targets.put(identityId, targetsForIdentity);
                    }
                    targetsForIdentity.add(entry.getValue());
                }
            }
        }

        return targets;
    }
    
    /**
     * Returns requested item id
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns a list of AccessRequestAccountInfo objects representing account selections
     * for this requested item
     */
    public List<AccessRequestAccountInfo> getAccountSelections() {
        return this.accountSelections;
    }

    /**
     * 
     * @return the sunrise date, or null if no date has been set
     */
    public Date getSunrise() {
        return sunrise;
    }

    /**
     *
     * @return the sunset date, or null if no date has been set
     */
    public Date getSunset() {
        return sunset;
    }

    /**
     *
     * @return the comment, or null if no comment has been set
     */
    public String getComment() {
        return comment;
    }

    /**
     *
     * @return List of attachments associated with the requested item
     */
    public List<AttachmentDTO> getAttachments() { return this.attachments; }

    /**
     *
     * @return Return the list of descriptions that was prompted with the attachments
     */
    public List<AttachmentConfigDTO> getAttachmentConfigList() { return this.attachmentConfigList; }


    /**
     * Based on {@link #getAccountSelections()}, get a map of ProvisioningTargets
     * keyed off of Identity ids
     * @param context SailPointContext
     * @return Map of ProvisioningTargets keyed by Identity id
     * @throws GeneralException
     */
    public Map<String, ProvisioningTarget> getProvisioningTargets(SailPointContext context) throws GeneralException {

        if (this.accountSelections == null) {
            return null;
        }

        if (this.provisioningTargetMap == null) {
            this.provisioningTargetMap = new HashMap<String, ProvisioningTarget>();
            for (AccessRequestAccountInfo accountSelection : this.accountSelections) {
                String identityId = accountSelection.getIdentityId();
                ProvisioningTarget target;
                if (this.provisioningTargetMap.containsKey(identityId)) {
                    target = this.provisioningTargetMap.get(identityId);
                } else {
                    target = initializeProvisioningTarget(context);
                    this.provisioningTargetMap.put(identityId, target);
                }

                target.addAccountSelection(createAccountSelection(accountSelection, context));
            }
        }

        return this.provisioningTargetMap;
    };

    /**
     * Verify invariants. Subclasses should extend to validate as necessary.
     * @throws sailpoint.tools.GeneralException
     */
    protected void validateState() throws GeneralException {
        if (id == null) {
            throw new GeneralException("Id is required");
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<AccessRequestAccountInfo> convertAccountSelections(Object accountSelectionsData) throws GeneralException {
        if (accountSelectionsData == null || !(accountSelectionsData instanceof List)) {
            throw new GeneralException("invalid accountSelections data");
        }
        
        List<AccessRequestAccountInfo> convertedSelections = new ArrayList<AccessRequestAccountInfo>();
        for (Object accountSelection: (List)accountSelectionsData) {
            if (!(accountSelection instanceof Map)) {
                throw new GeneralException("invalid accountSelectionsData");
            }
            convertedSelections.add(new AccessRequestAccountInfo((Map)accountSelection));
        }
        return convertedSelections;
    }
    
    private AccountSelection createAccountSelection(AccessRequestAccountInfo accountInfo, SailPointContext context)
            throws GeneralException {

        AccountSelection accountSelection;
        Application app = context.getObjectByName(Application.class, accountInfo.getApplicationName());
        if (accountInfo.isCreateAccount()) {
            accountSelection = new AccountSelection(app);
            accountSelection.setDoCreate(true);
        } else {
            Identity identity = context.getObjectById(Identity.class, accountInfo.getIdentityId());
            IdentityService identityService = new IdentityService(context);
            Link link = identityService.getLink(identity, app, accountInfo.getInstance(), accountInfo.getNativeIdentity());
            if (link == null) {
                throw new GeneralException("Invalid account selection, could not find link.");
            }
            accountSelection = new AccountSelection(app, link);
        }
        accountSelection.setRoleName(accountInfo.getRoleName());
        return accountSelection;
    }
}