/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class LinkAndEntitlementTools {

    static private final Log log = LogFactory.getLog(LinkAndEntitlementTools.class);

    private static final String KEY_APP = "application";
    private static final String KEY_NATIVE_ID = "nativeIdentity";
    private static final String KEY_NEW_PARENT = "newParent";

    private final SailPointContext _context;

    /**
     * Update the Link objects for the identity if determined
     * that the provisioning plan resulted in a move of a native identity.
     * If a move has occurred, then the native identity o the associated
     * Link objects will be updated.
     * @param context persistence context
     * @param plan the ProvisioningPlan that was recently executed
     * @param identityName the identity whose Link objects should be updated
     * @throws GeneralException
     */
    public static void updateLinks(SailPointContext context,
                                   ProvisioningPlan plan,
                                   String identityName) throws GeneralException {
        LinkAndEntitlementTools updater = new LinkAndEntitlementTools(context);
        updater.doUpdateLinks(plan, identityName);
    }

    private LinkAndEntitlementTools(SailPointContext context) {
        _context = context;
    }

    /**
     * Update the Link objects for the identity if determined
     * that the provisioning plan resulted in a move of a native identity.
     * If a move has occurred, then the native identity o the associated
     * Link objects will be updated.
     * @param plan the ProvisioningPlan that was recently executed
     * @param identityName the identity whose Link objects should be updated
     * @throws GeneralException
     */
    private void doUpdateLinks(ProvisioningPlan plan,
                               String identityName) throws GeneralException {

        // find the apps/nativeIdentity that were intended to be renamed in plan,
        // place into changeMapList
        List<Map<String,String>> changeMapList = new ArrayList<>();
        for (AccountRequest acctReq : Util.safeIterable(plan.getAccountRequests())) {
            Map<String,String> changeMap = buildChangeMap(acctReq);
            if (changeMap != null) {
                changeMapList.add(changeMap);
            }
        }

        if (!Util.isEmpty(changeMapList)) {
            if (Util.isNotNullOrEmpty(identityName)) {
                Identity lockedIdentity = null;
                try {
                    lockedIdentity = ObjectUtil.lockIdentityByName(_context, identityName);
                    if (lockedIdentity != null) {
                        for(Map<String,String> changeMap : Util.safeIterable(changeMapList)) {
                            processChangeMap(lockedIdentity, changeMap);
                        }
                    }
                }
                catch (Throwable t) {
                    log.error("Unable to update link", t);
                }
                finally {
                    // releasing the lock will also commit the removal
                    // of the link
                    try {
                        if (lockedIdentity != null)
                            ObjectUtil.unlockIdentity(_context, lockedIdentity);

                    }
                    catch (Throwable t) {
                        log.error("Unable to release identity lock after link updates", t);
                    }
                }
            }
        }

    }

    /**
     * If the given AccountRequest is a modify and sets the AC_NewParent attribute,
     * construct a map for convenience that contaims the application name, and
     * original native id, and new native id
     * @param acctReq the AccountRequest to examine
     * @return the change map if it was a move request == otherwise null
     */
    private Map<String,String> buildChangeMap(AccountRequest acctReq) {
        String appName = acctReq.getApplication();
        String nativeIdentity = acctReq.getNativeIdentity();
        if (ProvisioningPlan.ObjectOperation.Modify == acctReq.getOp()) {
            for (AttributeRequest attrReq : Util.safeIterable(acctReq.getAttributeRequests())) {
                String attrName = attrReq.getName();
                if ("AC_NewParent".equals(attrName) && ProvisioningPlan.Operation.Set == attrReq.getOp()) {
                    String newParent = attrReq.getValue().toString();
                    Map<String,String> changeMap = new HashMap<>();
                    changeMap.put(KEY_APP, appName);
                    changeMap.put(KEY_NATIVE_ID, nativeIdentity);
                    changeMap.put(KEY_NEW_PARENT, newParent);
                    return changeMap;
                }
            }
        }
        return null;
    }

    /**
     * Update the link (correct the native identity) of the given identity
     * if we find that the changes in the given proposed changeMap have actually
     * occurred.
     * @param identity the identity whose Link should be updated
     * @param changeMap the proposed changes to check to see if occurred
     * @throws GeneralException
     * @throws ConnectorException
     */
    private void processChangeMap(Identity identity, Map<String,String> changeMap)
            throws GeneralException, ConnectorException, InvalidNameException {
        String appName = changeMap.get(KEY_APP);
        String origNativeId = changeMap.get(KEY_NATIVE_ID);
        String newParent = changeMap.get(KEY_NEW_PARENT);
        if (Util.isNotNullOrEmpty(appName) &&
                Util.isNotNullOrEmpty(origNativeId) &&
                Util.isNotNullOrEmpty(newParent))
        {
            IdentityService identitySvc = new IdentityService(_context);
            Application app = _context.getObjectByName(Application.class, appName);
            if (app != null) {
                try {
                    Link matchingLink = findMatchingLink(identity, identitySvc, app, origNativeId);
                    if (matchingLink != null) {
                        String uuid = matchingLink.getUuid();
                        if (Util.isNotNullOrEmpty(uuid)) {
                            String newNativeId = buildNewNativeId(origNativeId, newParent);
                            ResourceObject ro = getAccountResourceObject(app, newNativeId);
                            if (ro != null) {
                                String liveUuid = ro.getUuid();
                                if (uuid.equalsIgnoreCase(liveUuid)) {
                                    // update the link
                                    matchingLink.setNativeIdentity(newNativeId);
                                    updateIdentityEntitlements(matchingLink, origNativeId);
                                }
                            }
                        }
                    }
                }
                finally {
                    _context.decache(app);
                }
            }
            else {
                log.warn("Cannot find application " + appName);
            }
        }
    }
    /**
     * @return Return an iterator over the entitlements for the identity on the application for the
     * given native id. Only connected or disconnected entitlement, roles
     */
    public static Iterator<Object[]> getIdentityEntitlementIdIterator(SailPointContext context,
                                                                Identity identity,
                                                                String appName,
                                                                String nativeId) throws GeneralException
    {
        log.debug("Enter getIdentityEntitlementIdIterator");

        Iterator<Object[]> rows = null;

        QueryOptions qo = new QueryOptions();
        if (identity != null && appName != null && nativeId != null)
        {
            log.debug("...Build Query Options");
            //EXCLUDE DETECTED AND ASSIGNED ROLES AS IDENTITY ENTITLEMENTS
            qo.addFilter(Filter.ignoreCase(Filter.eq("identity", identity)));
            qo.addFilter(Filter.ignoreCase(Filter.eq("application.name", appName)));

            ArrayList<Filter> list = new ArrayList<>();
            list.add(Filter.ignoreCase(Filter.eq("aggregationState", "Connected")));
            list.add(Filter.ignoreCase(Filter.eq("aggregationState", "Disconnected")));
            qo.addFilter(Filter.or(list));

            //BELOW QO ENSURES WE ARE GETTING ONLY ENT NOT ROLES
            qo.addFilter(Filter.notnull("value"));
            qo.addFilter(Filter.notnull("name"));
            qo.addFilter(Filter.eq("nativeIdentity", nativeId));

            log.debug("...Perform Search");
            rows = context.search(IdentityEntitlement.class, qo,"id");
            log.debug("Exit getIdentityEntitlementIdIterator");
        }
        return rows;
    }

    private void updateIdentityEntitlements(Link link, String oldNativeId)
            throws GeneralException {
        Iterator<Object[]> rows = getIdentityEntitlementIdIterator(
                _context, link.getIdentity(), link.getApplicationName(), oldNativeId);
        log.debug("Updating IdentityEntitlements");
        while(rows.hasNext()) {
            Object[] row = rows.next();
            if(row != null && row.length > 0) {
                String id = (String)row[0];
                log.debug("Changing IdentityEntitlement nativeIdentity for id " +
                                id + " from " + oldNativeId + " to " + link.getNativeIdentity());
                IdentityEntitlement identityEntitlement = _context.getObjectById(IdentityEntitlement.class, id);
                identityEntitlement.setNativeIdentity(link.getNativeIdentity());
            }
        }

        log.debug("Exit updateIdentityEntitlements");
    }

    /**
     * Get the ResourceObject for the given application and native identity
     * @param app the application from which to get the ResoutceObject
     * @param nativeIdentity the native identity of the account to get the ResourceObject for
     * @return the requested ResourceObject, or null if not found
     * @throws GeneralException
     * @throws ConnectorException failed to construct  or call the constructor
     */
    private ResourceObject getAccountResourceObject(Application app, String nativeIdentity) throws GeneralException, ConnectorException {
        if (Util.isNullOrEmpty(nativeIdentity)) {
            return null;
        }

        ResourceObject ro = null;
        String acctSchema = Connector.TYPE_ACCOUNT;
        Connector connector = ConnectorFactory.getConnector(app, null);
        try {
            ro = connector.getObject(acctSchema, nativeIdentity, null);
        }
        catch (ObjectNotFoundException o) {
            ro = null;
        }

        return ro;
    }

    /**
     * Given the original native id DN, and the new OU parent, construct
     * the updated native id DN
     * @param origNativeId the original native id distinguished name
     * @param newParent the new OU that the CN of the origNativeId should be moved to
     * @return the new DN that represents the moved DN
     */
    private String buildNewNativeId(String origNativeId, String newParent) throws InvalidNameException {
        String newNativeId = null;
        LdapName origDN = new LdapName(origNativeId);
        LdapName newParentDN = new LdapName(newParent);
        String cn = origDN.getSuffix(origDN.size() - 1).toString();
        if (Util.isNotNullOrEmpty(cn) && !newParentDN.isEmpty()) {
            newParentDN.add(newParentDN.size(), cn);
            newNativeId = newParentDN.toString();
        }
        return newNativeId;
    }

    /**
     * Find the Link, if any, that the given identity has for the given application and native id
     * @param identity
     * @param svc
     * @param app
     * @param nativeIdentity
     * @return
     * @throws GeneralException
     */
    private Link findMatchingLink(Identity identity, IdentityService svc, Application app, String nativeIdentity)
            throws GeneralException
    {
        if (Util.isNullOrEmpty(nativeIdentity)) {
            return null;
        }
        Link matchingLink = null;
        List<Link> links = svc.getLinks(identity, app);
        for (Link link : Util.safeIterable(links)) {
            String linkNativeIdentity = link.getNativeIdentity();
            if (Util.isNotNullOrEmpty(linkNativeIdentity)) {
                if (nativeIdentity.toLowerCase().equalsIgnoreCase(linkNativeIdentity)) {
                    matchingLink = link;
                    break;
                }
            }
        }
        return matchingLink;
    }

}
