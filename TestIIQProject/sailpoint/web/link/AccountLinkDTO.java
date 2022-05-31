package sailpoint.web.link;

import sailpoint.object.*;
import sailpoint.object.Application.Feature;

import static sailpoint.object.ProvisioningPlan.AccountRequest.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A DTO to represent the AccountLink model.
 */
public class AccountLinkDTO extends LinkDTO {

    private List<Operation> availableOperations = new ArrayList<Operation>();
    private boolean supportsRefresh = false;
    private boolean autoRefresh;
    private String previousAction;
    private List<String> errors;

    /**
     * Creates an AccountLinkDTO representing the provided Link
     * @param link The link to DTO-ify
     * @param isSelf If the link is for the requester
     * @param config The config to inspect for access
     */
    public AccountLinkDTO(Link link, boolean isSelf, Configuration config) {
        super(link);
        addAvailableOperations(link, isSelf, config);
    }

    /**
     * Creates an AccountLinkDTO representing the provided Link and given column configs
     * @param link The link to DTO-ify
     * @param isSelf If the link is for the requester
     * @param config The config to inspect for access
     * @param linkMap link converted into a Map
     * @param cols columnConfigs required by config UI
     */
    public AccountLinkDTO(Link link, boolean isSelf, Configuration config, Map<String, Object> linkMap, List<ColumnConfig> cols) {
        super(link, linkMap, cols);
        addAvailableOperations(link, isSelf, config);
    }
    
    private void addAvailableOperations(Link link, boolean isSelf, Configuration config) {
        List<Operation> allOperations = config.getList(Configuration.LCM_MANAGE_ACCOUNTS_ACTIONS);
        boolean showAllActions = config.getBoolean(Configuration.LCM_MANAGE_ACCOUNTS_SHOW_ALL_BUTTONS);
        List<Operation> filteredOperations = filterOperations(allOperations, isSelf, config);
        /* Manage Account is only concerned with a subset of features */
        Application application = link.getApplication();

        /* Initialize available actions */
        /* Apparently all accounts are deletable */
        if (filteredOperations.contains(Operation.Delete)) {
            availableOperations.add(Operation.Delete);
        }
        /* If the app supports unlock and the account is locked add unlock operation*/
        if (filteredOperations.contains(Operation.Unlock)) {
            if ((application.supportsFeature(Feature.UNLOCK) && link.isLocked()) || showAllActions) {
                availableOperations.add(Operation.Unlock);
            }
        }
        /* If the application supports enable add the correct enable/disable operation
         * - The source of this logic is from LinksResource.calculateDecisions. It seems funky.
         *   If show all then always show enable but only show disable if the account is enabled.  
         *   IIQETN-5408 The disable was not taking the filtered Operations into account for disable */
        boolean showEnable = link.isDisabled() && application.supportsFeature(Feature.ENABLE) && filteredOperations.contains(Operation.Enable);
        boolean showDisable = !link.isDisabled() && application.supportsFeature(Feature.ENABLE) && filteredOperations.contains(Operation.Disable);

        /* Seems like this should be either or but if the showAllActions checkbox is checked
         *  we will show the Eanble, based on the comments in the UI */
        if (showEnable || showAllActions){
            availableOperations.add(Operation.Enable);
        }
        if (showDisable){
            availableOperations.add(Operation.Disable);
        }

        /* If the app does not have the no random access feature it supports refresh */
        this.supportsRefresh = !application.supportsFeature(Feature.NO_RANDOM_ACCESS);
    }

    /**
     * Filter out operations based on the system config
     * @param allOperations The list of all available operations
     * @param isSelf True if requesting for self
     * @param config System config
     * @return List of operations that are available based on the system config
     */
    private List<Operation> filterOperations(List<Operation> allOperations, boolean isSelf, Configuration config) {
        List<Operation> filteredOperations = new ArrayList<Operation>();
        for (Operation operation : allOperations) {
            String operationKey = AccountLinkDTO.getOperationKey(operation, isSelf);
            if (config.getBoolean(operationKey)) {
                filteredOperations.add(operation);
            }
        }
        return filteredOperations;
    }

    /**
     * Build the configuration key for the operation and target
     * @param operation The operation
     * @param isSelf Whether for self or subordinate
     * @return The key to lookup in System Config with
     */
    public static String getOperationKey(Operation operation, boolean isSelf) {
        StringBuilder sb = new StringBuilder(Configuration.LCM_MANAGE_ACCOUNTS_PREFIX);
        sb.append(operation.name());
        sb.append(isSelf ? Configuration.LCM_SELF : Configuration.LCM_SUBORDINATE);
        sb.append(Configuration.LCM_OP_ENABLED_SUFFIX);
        return sb.toString();
    }

    /**
     * Returns the previous action preformed on the account
     * @return Return the previous action
     */
    public String getPreviousAction() {
        return this.previousAction;
    }

    /**
     * Set the previous action performed on the account
     * @param previousAction The previous action
     */
    public void setPreviousAction(String previousAction) {
        this.previousAction = previousAction;
    }

    /**
     * Returns the account's available operations
     * @return List of availabe operations
     */
    public List<Operation> getAvailableOperations() {
        return availableOperations;
    }

    /**
     * Returns true if the application supports refreshing accounts
     * @return True if the application supports refreshing accounts
     */
    public boolean getSupportsRefresh() {
        return supportsRefresh;
    }

    /**
     * Return whether this account should be automatically refreshed (ie - re-aggregated) when displayed.  This will
     * only return true if the account actually supports refresh.
     */
    public boolean isAutoRefresh() {
        return this.autoRefresh && this.supportsRefresh;
    }

    public void setAutoRefresh(boolean autoRefresh) {
        this.autoRefresh = autoRefresh;
    }

    /**
     * Returns the errors from the last account action
     * @return the errors from the last account action
     */
    public List<String> getErrors() {
        return this.errors;
    }

    /**
     * Sets error messages from the last account action
     * @param errors Errors from the last account action
     */
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    /**
     * Set the available operations list on the link
     * @param availableOperations
     */
    public void setAvailableOperations(List<Operation> availableOperations) {
        this.availableOperations = availableOperations;
    }
}
