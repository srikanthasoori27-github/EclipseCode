/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.AbstractCertifiableEntity;
import sailpoint.object.Certifiable;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.Entitlements;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.tools.GeneralException;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class AccountGroupPermissionsCertificationBuilder extends AccountGroupCertificationBuilder {
    private static final Log log = LogFactory.getLog(AccountGroupPermissionsCertificationBuilder.class);

    /**
     * Constructor.
     *
     * @param ctx CertificationContext
     */
    public AccountGroupPermissionsCertificationBuilder(SailPointContext ctx, CertificationDefinition definition) {
        super(ctx, definition);
    }

    // Following three methods base abstract methods need to be overridden.

    @Override
    protected AccountGroupCertificationContext createContext(SailPointContext context, CertificationDefinition definition, List<Identity> certifiers, String applicationName, Identity groupOwner) {
        return new AccountGroupPermissionsCertificationContext(context, definition, certifiers, applicationName, groupOwner);
    }

    @Override
    protected AccountGroupCertificationContext createContext(SailPointContext context, CertificationDefinition definition, List<Identity> certifiers, String applicationName) {
        return new AccountGroupPermissionsCertificationContext(context, definition, certifiers, applicationName);
    }

    @Override
    protected boolean isIndirectGroupAllowed() {
        return true;
    }

    /**
     * The CertificationContext to return information when building account
     * group permissions certifications.
     */
    public class AccountGroupPermissionsCertificationContext
            extends AccountGroupCertificationContext {

        /**
         * {@link sailpoint.api.certification.AccountGroupCertificationBuilder.AccountGroupCertificationContext
         *  #AccountGroupCertificationContext(sailpoint.api.SailPointContext, sailpoint.object.CertificationDefinition, java.util.List, String, sailpoint.object.Identity)}
         */
        public AccountGroupPermissionsCertificationContext(SailPointContext context, CertificationDefinition definition,
                                                           List<Identity> certifiers, String applicationName,
                                                           Identity groupOwner) {
            super(context, definition, certifiers, applicationName, groupOwner);
        }


        /**
         * {@link sailpoint.api.certification.AccountGroupCertificationBuilder.AccountGroupCertificationContext
         * #AccountGroupCertificationContext(sailpoint.api.SailPointContext, sailpoint.object.CertificationDefinition, java.util.List, String)}
         */
        public AccountGroupPermissionsCertificationContext(SailPointContext context,
                                                           CertificationDefinition definition,
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

            if (certifiable instanceof Entitlements) {
                Entitlements entitlements = (Entitlements)certifiable;
                item = new CertificationItem(entitlements, cert.getEntitlementGranularity());
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
            return super.getCertifiables(group);
        }
    }
}
