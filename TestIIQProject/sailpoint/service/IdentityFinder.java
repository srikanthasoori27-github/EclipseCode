/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Identitizer;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityExternalAttribute;
import sailpoint.object.Link;
import sailpoint.object.LinkExternalAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.SailPointObject;
import sailpoint.server.Authenticator;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;

/**
 * Helper class to help find identities
 * using an accountId that might not be that familiar to IIQ
 *
 * @author patrick.jeong
 *
 */
public class IdentityFinder {

    private static final Log LOG = LogFactory.getLog(IdentityFinder.class);

    private List<ObjectAttribute> multis;
    private SailPointContext _context;
    private Authenticator auth;

    public IdentityFinder(SailPointContext con) {
        _context = con;
    }

    /**
     * Look for an identity in the auth apps
     *
     * @param accountId
     * @throws GeneralException
     * @throws ConnectorException
     */
    public Identity findIdentity(String accountId, List<Application> authApps) throws GeneralException, ConnectorException {
        Identity id = null;
        ResourceObject obj = null;

        for (Application app : authApps) {
            List<String> authAttrs = app.getListAttributeValue(Connector.CONFIG_AUTH_SEARCH_ATTRIBUTES);

            Connector conn = ConnectorFactory.getConnector(app, null);
            if (null != authAttrs) {
                // Look locally for a match
                ObjectConfig linkConfig = Link.getObjectConfig();
                // dont attempt to search if there are no searchable
                if (!linkConfig.getSearchableAttributes().isEmpty()) {
                    QueryOptions qo = getQueryOptions(linkConfig, app, authAttrs, accountId);
                    if (qo != null) {
                        id = searchLinkConfigForIdentity(qo);
                    }

                    // now look in the external attributes table
                    if (id == null && !multis.isEmpty()) {
                        id = searchLinkConfigMultiAttrs(accountId);
                    }
                }
                if (id == null ) {
                    // check identity config
                    ObjectConfig identityConfig = Identity.getObjectConfig();
                    if (!identityConfig.getSearchableAttributes().isEmpty()) {
                        QueryOptions idqo = getQueryOptions(identityConfig, app, authAttrs, accountId);
                        if (idqo != null) {
                            id = searchIdentityConfigForIdentity(idqo);
                        }
                        // now look in the external attributes table
                        if (id == null && !multis.isEmpty()) {
                            id = searchIdentityConfigMultiAttrs(accountId);
                        }
                    }
                }
                else {
                    break; // we found a matching identity
                }

                // if we haven't found an identity locally
                // try using the connector to find it
                if (id == null) {
                    obj = searchIdentityUsingConnector(accountId, authAttrs, conn);
                }
            }
            else { // with no auth search attrs defined
                // Look for link with nativeIdentity that matches accountId first
                QueryOptions linkNI = new QueryOptions();
                linkNI.setDistinct(true);
                linkNI.add(Filter.eq("application", app));
                linkNI.add(Filter.ignoreCase(Filter.eq("nativeIdentity", accountId)));

                Iterator<Link> results = _context.search(Link.class, linkNI);

                if (results.hasNext()) {
                    id = results.next().getIdentity();
                    if (results.hasNext()) {
                        throw new GeneralException("multiple search results");
                    }
                    break; // found identity
                }

                // IIQTC-328: Enhancing exceptions management to avoid simple issues with connectors
                // Handling exceptions at this level could provide better customer experience
                // due the number of connectors.
                try {
                    obj = conn.getObject(Connector.TYPE_ACCOUNT, accountId, null);
                } catch (Exception e) {
                    obj = null;
                }
            }

            if (obj != null) { // find identity with resourceobject
                Identitizer idz = new Identitizer(_context);
                id = idz.correlate(app, obj);
                // IIQTC-328: If an identity is found, we do not need to search in other apps
                if (id != null) {
                    break;
                }
            }
        }
        return id;
    }

    private ResourceObject searchIdentityUsingConnector(String accountId, List<String> authAttrs, Connector conn)
            throws GeneralException, ConnectorException {

        ResourceObject obj = null;
        List<Filter> filters = new ArrayList<Filter>();
        for (String authAttr : authAttrs) {
            filters.add(Filter.eq(authAttr, accountId));
        }
        CloseableIterator<ResourceObject> it = null;
        try {
            it = conn.iterateObjects(Connector.TYPE_ACCOUNT, Filter.or(filters), null);
            if (it.hasNext()) {
                obj = it.next();
                if (it.hasNext()) {
                    // we have multiple matches
                    throw new GeneralException("Multiple matches");
                }
            }
        }
        catch (ConnectorException ce) {
            LOG.error("connector iterateObjects crapped out");
            throw ce;
        }
        finally {
            if (it != null) {
                it.close();
            }
        }
        return obj;
    }

    private Identity searchIdentityConfigMultiAttrs(String accountId) throws GeneralException {
        Iterator<Object[]> res = searchMultiAttrs(accountId, "Identity.id", IdentityExternalAttribute.class);
        Identity id = null;
        if (res.hasNext()) {
            id = (Identity)res.next()[0];
            if (res.hasNext()) {
                throw new GeneralException("multiple search results");
            }
        }
        return id;
    }

    private Identity searchLinkConfigMultiAttrs(String accountId) throws GeneralException {
        Link link = null;
        Iterator<Object[]> res = searchMultiAttrs(accountId, "Link.id", LinkExternalAttribute.class);
        if (res.hasNext()) {
            link = (Link)res.next()[0];
            if (res.hasNext()) {
                throw new GeneralException("multiple search results");
            }
        }
        return link.getIdentity();
    }

    private Iterator<Object[]> searchMultiAttrs(String accountId, String join, Class<? extends SailPointObject> clazz) throws GeneralException {
        QueryOptions multiQo = new QueryOptions();
        multiQo.add(Filter.join("objectId", join));

        List<Filter> filters = new ArrayList<Filter>();
        for (ObjectAttribute att : multis) {
            filters.add(Filter.and(Filter.eq("attributeName", att.getName()), Filter.eq("value", accountId)));
        }
        multiQo.add(Filter.or(filters));
        multiQo.setDistinct(true);

        List<String> props = new ArrayList<String>();
        props.add("objectId");

        Iterator<Object[]> results = _context.search(clazz, multiQo, props);
        return results;
    }

    /**
     *
     * @param app
     * @param authAttrs
     * @param accountId
     * @return
     * @throws GeneralException
     */
    private Identity searchLinkConfigForIdentity(QueryOptions qo)
            throws GeneralException {

        Identity id = null;

        List<String> props = new ArrayList<String>();
        props.add("identity");

        Iterator<Object[]> results = _context.search(Link.class, qo, props);
        if (results.hasNext()) {
            id = (Identity)results.next()[0];
            if (results.hasNext()) {
                // we have multiple matches
                throw new GeneralException("Multiple matches");
            }
        }

        return id;
    }

    /**
     *
     * @param app
     * @param authAttrs
     * @param accountId
     * @return
     * @throws GeneralException
     */
    private Identity searchIdentityConfigForIdentity(QueryOptions qo)
            throws GeneralException {

        Identity id = null;
        Iterator<Identity> results = _context.search(Identity.class, qo);

        if (results.hasNext()) {
            id = (Identity)results.next();
            if (results.hasNext()) {
                // we have multiple matches
                throw new GeneralException("Multiple matches");
            }
        }
        return id;
    }

    /**
     * We're also stashing away any multi value attributes in a seperate map
     *
     * @param objConfig
     * @param app
     * @param authAttrs
     * @param accountId
     * @return
     */
    private QueryOptions getQueryOptions(ObjectConfig objConfig, Application app, List<String> authAttrs, String accountId) {
        QueryOptions qo = new QueryOptions();
        qo.setDistinct(true);

        List<Filter> filters = new ArrayList<Filter>();

        // clear out multis list
        if (multis == null) {
            multis = new ArrayList<ObjectAttribute>();
        }
        else {
            multis.clear();
        }

        for (String attrName : authAttrs) {
            ObjectAttribute objAttr = objConfig.getObjectAttributeWithSource(app, attrName);

            if (objAttr != null && objAttr.isSearchable())  {
                if (!objAttr.isMulti()) {
                    filters.add(Filter.eq(objAttr.getName(), accountId));
                }
                else {
                    multis.add(objAttr);
                }
            }
        }
        qo.add(Filter.or(filters));
        if (filters.isEmpty()) {
            return null;
        }
        return qo;
    }
}
