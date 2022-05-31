/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.Map;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.EntitlementCollection;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LoadingMap;
import sailpoint.tools.Util;


/**
 * JSF bean to get the account ID of a user on an application.  This could just
 * be a function exposed through Facelets, but I made it a bean so that the
 * values will be cached as long as this bean is alive.
 *
 * @author Kelly Grizzle
 */
public class AccountIdBean extends BaseBean {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    private static class AccountIdLoader implements LoadingMap.Loader<String> {
        private String identityName;
        private String appName;

        public AccountIdLoader(String identityName, String appName) {
            this.identityName = identityName;
            this.appName = appName;
        }

        /**
         * jsl - The "appName" may actually be a composite key of
         * Application name and template instance identifier.
         * This was easier than adding another level of Maps.
         * To look up the acountId we have to split the map key
         * into the two parts.
         */
        public String load(Object nativeIdentity) throws GeneralException {
            String accountId = null;
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            
            if (null != nativeIdentity) {
                String realAppName = EntitlementCollection.getKeyApplication(appName);
                String instance = EntitlementCollection.getKeyInstance(appName);

                accountId = ObjectUtil.getAccountId(ctx, identityName, 
                                                    realAppName,
                                                    instance,
                                                    nativeIdentity.toString());
            }

            // Really this probably shouldn't happen, but the AccountIdMap is
            // returning null if there are two accounts with the same nativeIdentity
            // that differ only with case-sensitivity and the database performs
            // case-insensitive queries.
            if (null == Util.getString(accountId)) {
                accountId = nativeIdentity.toString();
            }

            return accountId;
        }
    }

    private static class ApplicationLoader
        implements LoadingMap.Loader<Map<String,String>> {
        private String identityName;

        public ApplicationLoader(String identityName) {
            this.identityName = identityName;
        }

        /**
         * jsl - note that the appName may actually be a composite
         * key of application/instance names.
         */
        public Map<String,String> load(Object appName) {
            Map<String,String> map = null;
            if (null != appName) {
                AccountIdLoader loader =
                    new AccountIdLoader(identityName, appName.toString());
                map = new LoadingMap<String,String>(loader);
            }
            return map;
        }
    }

    private static class IdentityLoader
        implements LoadingMap.Loader<Map<String,Map<String,String>>> {

        public Map<String,Map<String,String>> load(Object identityName) {
            Map<String,Map<String,String>> map = null;
            if (null != identityName) {
                ApplicationLoader loader =
                    new ApplicationLoader(identityName.toString());
                map = new LoadingMap<String,Map<String,String>>(loader);
            }
            return map;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // ACCOUNT ID BEAN IMPLEMENTATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Outer map keyed by Identity name.
     * Second map keyed by application name.
     * Third map keyed by nativeIdentity.
     * Result is the accountId which may be the same
     * as nativeIdentity or it may be a simpler displayName.
     */
    private Map<String,Map<String,Map<String,String>>> accountIdMap;


    /**
     * Default constructor.
     */
    public AccountIdBean() {
        super();
    }

    /**
     * Return a map that will return the account ID for a user on an application.
     * Should be referenced with: map[identityName][appName][nativeIdentity].
     *
     * jsl - this was the original public interface but I made it private
     * so we could encapsulate management of the composite key for
     * template applications.
     * 
     * @return A map that will return the account ID for a user on an
     *         application.
     *
     * UPDATE: Made this public again because it is referenced
     * by identityEntitlements.xhtml.  This file does not however deal
     * with the combination of the appName and appInstance.  Rather than
     * complicating the xhtml file, can we just remember this
     * as a property in CertificationItemBean?
     */
    public Map<String,Map<String,Map<String,String>>> getMap() {
        if (null == this.accountIdMap) {
            this.accountIdMap =
                new LoadingMap<String,Map<String,Map<String,String>>>(new IdentityLoader());
        }
        return this.accountIdMap;
    }

    /**
     * Return the preferred accountId for an account.
     */
    public String getAccountId(String identityName,
                               String appName,
                               String appInstance,
                               String nativeIdentity) {

        String accountId = null;
        if (identityName != null && appName != null && nativeIdentity != null) {

            String appKey = EntitlementCollection.getKey(appName, appInstance);
        
            Map<String,Map<String,Map<String,String>>> maps = getMap();

            accountId = maps.get(identityName).get(appKey).get(nativeIdentity);
        }
        return accountId;
    }

}
