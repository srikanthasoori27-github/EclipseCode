/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Certifiable;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityEntitlement.AggregationState;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class AccountGroupMembershipCertificationBuilder extends AccountGroupCertificationBuilder {
    private static final Log log = LogFactory.getLog(AccountGroupMembershipCertificationBuilder.class);

    /**
     * Creates a new AccountGroupMembershipCertificationBuilder.
     *
     * @param ctx CertificationContext
     * @param definition CertificationDefinition
     */
    public AccountGroupMembershipCertificationBuilder(SailPointContext ctx, CertificationDefinition definition) {
        super(ctx, definition);
    }

    // Following three methods base abstract methods need to be overridden.

    @Override
    protected AccountGroupCertificationContext createContext(SailPointContext context, CertificationDefinition definition, List<Identity> certifiers, String applicationName, Identity groupOwner) {
        return new AccountGroupMembershipCertificationContext(context, definition, certifiers, applicationName, groupOwner);
    }

    @Override
    protected AccountGroupCertificationContext createContext(SailPointContext context, CertificationDefinition definition, List<Identity> certifiers, String applicationName) {
        return new AccountGroupMembershipCertificationContext(context, definition, certifiers, applicationName);
    }

    @Override
    protected boolean isIndirectGroupAllowed() {
        return false;
    }

    /**
     * The CertificationContext to return information when building account
     * group membership certifications.
     */
    public class AccountGroupMembershipCertificationContext
            extends AccountGroupCertificationContext {

        /**
         * {@link sailpoint.api.certification.AccountGroupCertificationBuilder.AccountGroupCertificationContext
         *  #AccountGroupCertificationContext(sailpoint.api.SailPointContext, sailpoint.object.CertificationDefinition, java.util.List, String, sailpoint.object.Identity)}
         */
        public AccountGroupMembershipCertificationContext(SailPointContext context, CertificationDefinition definition,
                                                          List<Identity> certifiers, String applicationName,
                                                          Identity groupOwner) {
            super(context, definition, certifiers, applicationName, groupOwner);
        }


        /**
         * {@link sailpoint.api.certification.AccountGroupCertificationBuilder.AccountGroupCertificationContext
         * #AccountGroupCertificationContext(sailpoint.api.SailPointContext, sailpoint.object.CertificationDefinition, java.util.List, String)}
         */
        public AccountGroupMembershipCertificationContext(SailPointContext context, CertificationDefinition definition,
                                                          List<Identity> certifiers, String applicationName) {
            super(context, definition, certifiers, applicationName);
        }

        /**
         * Create a CertificationItem for the given Certifiable object.
         *
         * @param cert        The Certification to which we're adding the item.
         * @param certifiable The Certifiable for which to create the item.
         * @param groupEntity  The entity on which the Certifiable was found.
         * @return A CertificationItem created from the given Certifiable object.
         */
        protected CertificationItem createCertificationItemInternal(Certification cert,
                                                                    Certifiable certifiable,
                                                                    AbstractCertifiableEntity groupEntity)
                throws GeneralException {

            CertificationItem item = null;

            if (certifiable instanceof Entitlements) {//TQM: I don't think this is ever true
                Entitlements entitlements = (Entitlements)certifiable;
                item = new CertificationItem(entitlements,
                        cert.getEntitlementGranularity());
            } else if (certifiable instanceof MembershipCertifiable) {
                MembershipCertifiable wrapper = (MembershipCertifiable)certifiable;
                EntitlementGroup entitlementGroup = wrapper.getEntitlementGroup();
                item = new CertificationItem(entitlementGroup,
                        cert.getEntitlementGranularity());
                item.setTargetName(wrapper.getIdentityName());
                item.setTargetId(wrapper.getIdentityId());
                item.setTargetDisplayName(wrapper.getAccount());
                item.setType(CertificationItem.Type.AccountGroupMembership);
            } else {
                throw new IllegalArgumentException("Unknown certifiable type on " +
                        groupEntity.getName() + ": " + certifiable);
            }

            return item;
        }

        /**
         *
         * @param entity
         * @return
         * @throws GeneralException
         */
        public List<Certifiable> getCertifiables(AbstractCertifiableEntity entity)
                throws GeneralException {
            if (log.isDebugEnabled())
                log.debug("getCertifiables for Account Group " + entity.getName());

            if ( entity == null || ! ( entity instanceof ManagedAttribute ) )
                throw new GeneralException("Unable to determine certifiables " +
                        "for an object that is not an ManagedAttribute: " + entity);

            ManagedAttribute group = (ManagedAttribute)entity;
            return getMembersCertifiables(group);

        }

        private List<Certifiable> getMembersCertifiables(ManagedAttribute group)
                throws GeneralException {

            Application app = group.getApplication();
            String refAttr = group.getAttribute();
            String attrValue = group.getValue();

            QueryOptions ops = new QueryOptions();
            ops.add(getGroupMemberFilter(app, refAttr, attrValue));

            List<Certifiable> certifiables = new ArrayList<Certifiable>();
            List<String> fields =
                    Arrays.asList("instance", "nativeIdentity", "displayName", "identity.name", "identity.id");
            Iterator<Object[]> it = context.search(IdentityEntitlement.class, ops, fields);
            while ( it.hasNext() ) {
                Object[] row = it.next();

                String instance = (String)row[0];
                String nativeIdentity = (String)row[1];
                String displayName = (String)row[2];
                String identityName = (String)row[3];
                String identityId = (String)row[4];

                displayName = displayName != null ? displayName : nativeIdentity;

                Attributes<String,Object> attrs = new Attributes<String,Object>();
                attrs.put(refAttr, attrValue);
                EntitlementGroup entGroup =
                        new EntitlementGroup(app,
                                instance,
                                nativeIdentity,
                                displayName,
                                null,
                                attrs);
                certifiables.add(new MembershipCertifiable(entGroup, identityName, identityId, displayName));
            }
            return certifiables;
        }

        /**
         * Create a Filter that looks at the correlation key for the group
         * attribute on the identity links to find which users are members of
         * the group.
         */
        Filter getGroupMemberFilter(Application app, String attrName,
                                    String attrValue)
                throws GeneralException {

            Filter filter =
                    Filter.and( Filter.eq("application", app),
                            Filter.ignoreCase(Filter.eq("name", attrName)),
                            Filter.eq("aggregationState", AggregationState.Connected),
                            Filter.ignoreCase(Filter.eq("value", attrValue))
                    );

            return filter;
        }
    }

    /**
     * For account group membership certs we need both the 
     * EntitlementGroup and the Identity.  The identity information
     * will be stored on the certification item so that we 
     * can we use it when displaying remediations.
     * The identity used here is the identity from the link
     * used in the creation of the EntitlmentGroup.
     */
    public class MembershipCertifiable implements Certifiable {
        String _identityName;
        String _identityId;
        EntitlementGroup _group;
        String _account;

        public MembershipCertifiable(EntitlementGroup group, String identityName, String identityId, String account) {
            _identityName = identityName;
            _identityId = identityId;
            _group = group;
            _account = account;
        }

        public String getIdentityName() {
            return _identityName;
        }

        public String getIdentityId() {
            return _identityId;
        }

        public EntitlementGroup getEntitlementGroup() {
            return _group;
        }

        public String getAccount() {
            return _account;
        }
    }
}
