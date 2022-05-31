/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Explanator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ManagedResource;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.QueryOptions;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class LinkService {

    // Silly constant that indicates that all apps are disabled for auto-refresh.
    public static final String AUTO_REFRESH_DISABLE_ALL_APPS = "All Applications";

    private static Log log = LogFactory.getLog(LinkService.class);
    private SailPointContext context;
    private PlanCompiler compiler;
    private Boolean anyPasswordIntegrations;
    private Boolean anyUnlockIntegrations;

    // Flags to indicate universal IntegrationConfig objects were found. The
    // lifespan of a LinksService instance is expected to be short, so these
    // flags will never be reset to 'false' after initialization
    private Boolean anyUniversalConfig;
    private Boolean anyPWUniversalConfig;

    public LinkService(SailPointContext context) {
        this.context = context;
    }

    public SailPointContext getContext() {
        return this.context;
    }
    
    /**
     * Get the display name for a link, if it exists
     * @param identity Identity the link belongs to
     * @param applicationName Name of the application
     * @param instance Name of the instance, or null
     * @param nativeIdentity NativeIdentity
     * @return Display name for the Link, or native identity if no display name exists
     * @throws GeneralException
     */
    public String getAccountDisplayName(Identity identity, String applicationName, String instance, String nativeIdentity) 
    throws GeneralException {
        String accountDisplayName = null;
        QueryOptions ops = new QueryOptions();
        if (identity != null) {
            ops.add(Filter.eq("identity", identity));
        }
        ops.add(Filter.eq("application.name", applicationName));
        if(instance == null) {
            ops.add(Filter.isnull( "instance"));
        } else {
            ops.add(Filter.eq("instance", instance));
        }
        ops.add(Filter.eq("nativeIdentity", nativeIdentity));
        Iterator<Object[]> links = this.context.search(Link.class, ops, "displayName");
        if (links != null && links.hasNext()) {
            Object[] link = links.next();
            accountDisplayName = (String)link[0];

            if (links.hasNext()) {
                //Shouldn't have more than one Identity Entitlement for these filters, but just in case. 
                String identityIdentifier = identity != null ? identity.getId() : "(null)";
                String instanceString = Util.isNullOrEmpty(instance) ? "" : ", instance: " + instance;
                log.debug("Found more than one link for identity: " + identityIdentifier +
                        ", application: " + applicationName +
                        instanceString +
                        ", nativeIdentity: " + nativeIdentity);
                Util.flushIterator(links);
            }
        }

        return Util.isNullOrEmpty(accountDisplayName) ? nativeIdentity : accountDisplayName;
    }
    
 
    /**
     * Get account attributes, stores non-null value entry into result map
     * @param linkId link id
     * @return Map of link attributes that has non-null value
     * @throws GeneralException
     */
    public Map<String, Object> getAccountDetails(String linkId) throws GeneralException {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        Link link = getContext().getObjectById(Link.class, linkId);
        Attributes<String, Object> attributes = link.getAttributes();
        Iterator<Map.Entry<String, Object>> it = attributes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> attr = (Map.Entry<String, Object>)it.next();
            if(attr.getValue() != null) {
                String attrName = attr.getKey();
                Object attrValue = attr.getValue();
                // attrValue could be a Persistence List, if that's the case we need
                // to have the displayable name for each value in the list
                if (attrValue instanceof ArrayList<?>) {
                    List<String> displayNames = new ArrayList<String>();
                    Iterator iterator = ((ArrayList) attrValue).iterator();
                    while(iterator.hasNext()) {
                        String disName = Explanator.getDisplayValue(link.getApplicationId(), attrName, iterator.next().toString());
                        displayNames.add(disName);
                    }
                    resultMap.put(attrName, displayNames);
                }
                else {
                    String displayName = Explanator.getDisplayValue(link.getApplicationId(), attrName, attrValue.toString());
                    resultMap.put(attrName, displayName);
                }                    
            }
        }
        return resultMap;
    }

    /**
     * Get the account that matches the IdentityRequestItem
     * @param links The list of links to search
     * @param identityRequestItem The identity request item to match
     * @return The matching link if no matching link is found null is returned
     */
    public Link getMatchingLink(Collection<Link> links, IdentityRequestItem identityRequestItem) {
        for (Link link : links) {
            if (Util.nullSafeEq(identityRequestItem.getApplication(), link.getApplicationName(), true) &&
                    Util.nullSafeEq(identityRequestItem.getInstance(), link.getInstance(), true) &&
                    Util.nullSafeEq(identityRequestItem.getNativeIdentity(), link.getNativeIdentity(), true)) {
                return link;
            }
        }
        /* Maybe the link was deleted out from underneath the request.  Nothing better to do than return null */
        return null;
    }

    /**
     * Returns the most recent identity request item associated with the link
     * @param {Link} link of the given identity
     * @param {String} type of the request
     * @param {boolean} true to only return the most recent verifiable identity request item.  
     * Note that if every item in the request is unverifiable, then the most recent item, verifiable or not, is returned  
     * @return {IdentityRequestItem} most recent identity request item
     * @throws GeneralException
     */
    public IdentityRequestItem getPreviousRequestItem(Link link, String type, boolean excludeUnverifiables) throws GeneralException{
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("nativeIdentity", link.getNativeIdentity()));
        ops.add(Filter.eq("identityRequest.type", type));
        ops.add(Filter.eq("application", link.getApplicationName()));
        if (link.getInstance() == null) {
            ops.addFilter(Filter.isnull("instance"));
        } else {
            ops.add(Filter.eq("instance", link.getInstance()));
        }
        ops.addOrdering("created", false);

        Iterator<IdentityRequestItem> requestItems = getContext().search(IdentityRequestItem.class, ops);
        IdentityRequestItem requestItem = null;
        IdentityRequestItem firstFound = null;
        while (requestItems.hasNext()) {
            requestItem = requestItems.next();
            if (firstFound == null) {
                firstFound = requestItem;
            }
            if (!excludeUnverifiables) {
                Util.flushIterator(requestItems);
                return requestItem;
            } else if (!requestItem.isNonverifiable()) {
                Util.flushIterator(requestItems);
                return requestItem;
            }
        }

        // If we managed to get here with a non-null request item, then every single request item
        // must have been unverifiable.  In that case, fall back to the most recent request item.
        if (requestItem != null) {
            requestItem = firstFound;
        }

        return requestItem;
    }

    /**
     * Returns the most recent identity request item associated with the link
     * @param {Link} link of the given identity
     * @param {String} type of the request
     * @return {IdentityRequestItem} most recent identity request item
     * @throws GeneralException
     */
    public IdentityRequestItem getPreviousRequestItem(Link link, String type) throws GeneralException{
        return getPreviousRequestItem(link, type, false);
    }
    
    /*
     * Given an existing Set of application ids, add to it all applications that natively support provisioning.
     * @param applicationIds Non null Set of ids this method will modify and add values to.
     * @param passwordOnly Flag indicating only applications with the PASSWORD feature flag are to be added. Otherwise the
     * PROVISIONING feature flag will need to be indicated.
     */
    private Set<String> getProvisioningApplicationsNatively(boolean passwordOnly) throws GeneralException {
        Set<String> applicationIds = new HashSet<String>();
        QueryOptions opts = new QueryOptions();
        opts.add(Filter.eq("supportsProvisioning",true));
        Iterator<Object[]> provisioningApps = context.search(Application.class, opts, "id,featuresString");
        while (provisioningApps.hasNext()) {
            Object[] next = provisioningApps.next();
            String id = (String)next[0];
            String featureString = (String)next[1];
            // Confirm provisioning is supported with the feature string
            if (!passwordOnly && !Util.isNullOrEmpty(featureString) && featureString.contains(Feature.PROVISIONING.toString())
                    || passwordOnly && !Util.isNullOrEmpty(featureString) && featureString.contains(Feature.PASSWORD.toString())) {
                applicationIds.add(id);
            }
        }
        return applicationIds;
    }
    
    /*
     * Return a Set of application ids that are managed by an IntegrationConfig.
     * @param passwordOnly Flag indicating only IntegrationConfigs that support setting password are evaluated
     * @return A Set of application ids, or an empty set if none are found
     */
    private Set<String> getProvisioningApplicationsFromIntegrations(boolean passwordOnly) throws GeneralException {
        // This method has an internal contract that whenever any universal ICs are found, the corresponding 'anyUniversalMgr'
        // flag is set to true.
        Set<String> applicationIds = new HashSet<String>();
        // get a list of all IC objects
        List<IntegrationConfig> intConfigs = context.getObjects(IntegrationConfig.class, new QueryOptions());
        if (!Util.isEmpty(intConfigs)) {
            for (IntegrationConfig intConfig : intConfigs) {
                // for each one, if any are universal managers that correspond to our operation, set the appropriate flag
                // and fill up the applicationIds list with everything
                if (!passwordOnly || passwordOnly && intConfig.supportedOperation(IntegrationConfig.OP_SET_PASSWORD)) {
                    if (intConfig.isUniversalManager()) {
                        if (!passwordOnly) {
                            // For non-password operations, which at this point is assumed to be full provisioning, set
                            // anyUniversalConfig to true
                            this.anyUniversalConfig = true;
                        } else {
                            // Else just set PWUniversalConfig to true
                            this.anyPWUniversalConfig = true;
                        }
                        // a Universal manager means it manages all applications. So stop processing and just return all Application ids
                        QueryOptions opts = new QueryOptions();
                        if (!Util.isEmpty(applicationIds)) {
                            opts.add(Filter.not(Filter.in("id", applicationIds)));
                        }
                        Iterator<Object[]> remainingIds = context.search(Application.class, opts, "id");
                        while (remainingIds.hasNext()) {
                            String remainingId = (String)remainingIds.next()[0];
                            applicationIds.add(remainingId);
                        }
                        return applicationIds;
                    }
                    List<ManagedResource> resources = intConfig.getResources();
                    if (resources != null) {
                        for (ManagedResource resource : resources) {
                            Application app = resource.getApplication();
                            if (app != null) {
                                applicationIds.add(app.getId());
                            }
                        }
                    }
                }
            }
        }
        // At this point, there can be no universal configs. Set our flags to indicate so.
        this.anyPWUniversalConfig = false;
        this.anyUniversalConfig = false;
        return applicationIds;
    }
    
    /**
     * Returns a list of Application objects supporting account provisioning.
     * @param passwordOnly Flag indicating only password
     * @return List of Application IDs supporting the provided operation
     * @throws GeneralException
     */
    public Set<String> getProvisioningApplications(boolean passwordOnly) throws GeneralException {
        
        // 1. Go through all IntegrationConfigs and find their applications
        Set<String> applicationIds = getProvisioningApplicationsFromIntegrations(passwordOnly);
        if (anyUniversalConfig(passwordOnly)) {
            // If this method returned true, that means a universal IC was found and all applications
            // are in our list. We can stop looking.
            return applicationIds;
        }

        // 2. Add applications that support provisioning natively
        Set<String> provisioningApps = getProvisioningApplicationsNatively(passwordOnly);
        if (!Util.isEmpty(provisioningApps)) {
            applicationIds.addAll(provisioningApps);
        }

        // 3. Finally, find any application with a proxy that is in the list we already have
        if (!Util.isEmpty(applicationIds)) {
            // IIQSR-211: Each of the databases have a limit on the number of parameters that can be passed.
            // Seems like oracle may be the smallest number which is around 1000 parameters.  If the number of
            // IDs returned in the applicationIds is greater than the database specific limit on the query, then
            // an error will be thrown about too many parameters.  To get past this issue, we will batch the IDs
            // into search groups of 100 and then combine the results after each search is returned.
            int end = 0;
            int queryEach = 100;
            int listSize = applicationIds.size();

            List<String> applicationIdsList = new ArrayList<String>(applicationIds);
            for (int start = 0; start < listSize; start += queryEach) {
                // Ternary: end value is either the listSize or start incremented by queryEach, whichever is lower
                end = ((start + queryEach) > listSize) ? listSize : start + queryEach;

                QueryOptions opts = new QueryOptions();
                opts.add(Filter.in("proxy.id", applicationIdsList.subList(start, end)));

                Iterator<Object[]> proxiedApps = context.search(Application.class, opts, "id");
                while (proxiedApps.hasNext()) {
                    String proxiedApp = (String) proxiedApps.next()[0];
                    applicationIds.add(proxiedApp);
                }
            }
        }

        return applicationIds;
    }

    /**
     * Check if there are any integration configs that support the SetPassword
     * operation.
     */
    public boolean anyPasswordIntegrations() throws GeneralException {

        if (null == this.anyPasswordIntegrations) {
            this.anyPasswordIntegrations =
                    getPlanCompiler().hasIntegrationSupportingOperation(IntegrationConfig.OP_SET_PASSWORD);
        }
        return this.anyPasswordIntegrations == null ? false : this.anyPasswordIntegrations;
    }

    /**
     * Check if passwords can be set for the given link.
     */
    public boolean supportsSetPassword(Link link) throws GeneralException {
        return supportsSetPassword(link.getApplication());
    }

    public boolean supportsSetPassword(Application app) throws GeneralException {
        PlanCompiler compiler = getPlanCompiler();
        IntegrationConfig cfg =
            compiler.getResourceManager(IntegrationConfig.OP_SET_PASSWORD, app.getName());
        return (null != cfg && cfg.supportedOperation(IntegrationConfig.OP_SET_PASSWORD));
    }
    
    /**
     * Check if there are any integration configs that support Unlock
     * @return
     * @throws GeneralException
     */
    public boolean anyUnlockIntegrations() throws GeneralException {
        
        if(this.anyUnlockIntegrations == null) {
            this.anyUnlockIntegrations =
                    getPlanCompiler().hasIntegrationSupportingOperation(AccountRequest.Operation.Unlock.toString());
        }
        return this.anyUnlockIntegrations == null ? false : this.anyUnlockIntegrations;
    }

    /**
     * Check if there are any integration configs that are universal
     * @param passwordOnly If true, will return if there are any universal integration configs that
     *      support {@link IntegrationConfig#OP_SET_PASSWORD}. If false, will return true if any
     *      integration config is universal.
     * @return
     */
    public boolean anyUniversalConfig(boolean passwordOnly) throws GeneralException {
        // getProvisioningApplicationsFromIntegrations will populate our flags as a secondary operation
        if ((passwordOnly && this.anyPWUniversalConfig == null) || this.anyUniversalConfig == null) {
            getProvisioningApplicationsFromIntegrations(passwordOnly);
        }
        // it's a little terse, but ANDing the 'passwordOnly' flag to our 'anyUniversal' flags ensures
        // only one operand of our OR statement can be true, and it can only be the operand driven by passwordOnly
        return (passwordOnly && this.anyPWUniversalConfig) || (!passwordOnly && this.anyUniversalConfig);
    }

    /**
     * Check whether the given application is configured to be auto-refreshed when displayed on the manage accounts
     * page.  This checks the configuration to see if it is disabled, and will also only returns apps that do not
     * have the NO_RANDOM_ACCESS feature.
     *
     * @param  app  The application to check.
     *
     * @return True if accounts on the given application should be auto-refreshed, false otherwise.
     */
    public boolean isAutoRefreshEnabled(Application app) throws GeneralException {
        // Auto-refresh is enabled unless explicitly set otherwise.
        boolean enabled = true;

        Configuration config = getContext().getConfiguration();
        @SuppressWarnings("unchecked")
        List<String> disabledAppIds =
            (List<String>) config.getList(Configuration.LCM_MANAGE_ACCOUNTS_DISABLE_AUTO_REFRESH_STATUS);

        // Look for the secret "All Applications" string - this means that all apps are disabled.
        if ((Util.size(disabledAppIds) == 1) && disabledAppIds.contains(AUTO_REFRESH_DISABLE_ALL_APPS)) {
            enabled = false;
        }
        else {
            // Otherwise, check whether the app is listed as disabled.
            enabled = !Util.nullSafeContains(disabledAppIds, app.getId());

            // Only allow auto-refresh if the app supports reading single accounts.
            if (enabled) {
                enabled = !app.supportsFeature(Feature.NO_RANDOM_ACCESS);
            }
        }

        return enabled;
    }

    /**
     * create new plan compiler
     * @return PlanCompiler
     */
    private PlanCompiler getPlanCompiler() {
        if (null == this.compiler) {
            this.compiler = new PlanCompiler(this.context);
        }
        return this.compiler;
    }
}
