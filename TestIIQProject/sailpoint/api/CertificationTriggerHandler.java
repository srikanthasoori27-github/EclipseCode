/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDefinition.CertifierSelectionType;
import sailpoint.object.Identity;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;


/**
 * An IdentityTriggerHandler that creates one or more certifications for the
 * changed identity.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationTriggerHandler extends AbstractIdentityTriggerHandler {

    private static final Log log = LogFactory.getLog(CertificationTriggerHandler.class);


    /**
     * Default constructor.
     */
    public CertificationTriggerHandler() {
        super();
    }

    /**
     * Create one or more certifications for the changed identity.
     */
    protected void handleEventInternal(IdentityChangeEvent event,
                                       IdentityTrigger trigger)
        throws GeneralException {

        List<Identity> certifiers = getManagerTransferCertifiers(event, trigger);

        // Create a cert with the non-null identity from the event.
        Identity identity = event.getObject();
        if (identity == null) {
            if (event.getObjectName() != null) {
                identity = context.getObjectByName(Identity.class, event.getObjectName());
            }
        }

        // If we have a previous and a new, prefer the one that has a manager.
        if ((null != event.getOldObject()) && (null != event.getNewObject()) &&
            (null == event.getNewObject().getManager())) {
            identity = event.getOldObject();
        }
        // IIQTC-367: Objects related or inside Certifications like their children objects
        // need to be persisted as part of the whole Certification objects. For that
        // reason the explicit save mode needs to be disabled, and the previous configuration
        // restored after that.
        boolean currentSaveMode = this.context.getPersistenceOptions().isExplicitSaveMode();
        this.context.getPersistenceOptions().setExplicitSaveMode(false);
        createCertification(identity, certifiers, trigger);
        this.context.getPersistenceOptions().setExplicitSaveMode(currentSaveMode);
    }

    /**
     *
     * @param trigger
     * @param identity
     * @return Certificationid
     * @throws GeneralException
     */
    public String handleAlertEvent(IdentityTrigger trigger, Identity identity)
        throws GeneralException {

        return createCertification(identity, null, trigger);
    }

    /**
     * For a manager transfer cert, if the user actually changed managers (ie -
     * has different old and new managers) we need to create a cert with both
     * managers as certifiers.  This will return a list of certifiers (the
     * previous and new manager) if this is the case, otherwise this returns
     * null.
     */
    private List<Identity> getManagerTransferCertifiers(IdentityChangeEvent event,
                                                        IdentityTrigger trigger)
        throws GeneralException {

        List<Identity> certifiers = null;

        Identity oldMgr =
            (null != event.getOldObject()) ? event.getOldObject().getManager() : null;
        Identity newMgr =
            (null != event.getNewObject()) ? event.getNewObject().getManager() : null;

        // Only need to return multiple certifiers if there was an old and a new
        // manager.  Otherwise, we'll just use the manager from the identity.
        if ((null != oldMgr) && (null != newMgr)) {

            // If this is a manager change and the definition is using a manager
            // selector, create a certification with both managers as certifies.
            CertificationDefinition definition =
                trigger.getCertificationDefinition(this.context);
            CertificationScheduler scheduler = new CertificationScheduler(this.context);
            //CertificationScheduleDTO def = scheduler.definitionToDTO(definition);
            boolean isManagerSelector =
                CertifierSelectionType.Manager.equals(definition.getCertifierSelectionType());

            if (IdentityTrigger.Type.ManagerTransfer.equals(trigger.getType()) &&
                isManagerSelector) {

                // Shouldn't happen, but let's do a sanity check.
                if (oldMgr.equals(newMgr)) {
                    if (log.isWarnEnabled())
                        log.warn("Manager transfer event detected with no manager change: " + event);
                }
                else {
                    certifiers = new ArrayList<Identity>();
                    certifiers.add(oldMgr);
                    certifiers.add(newMgr);
                }
            }
        }

        return certifiers;
    }

    /**
     * Create a certification for the given identity using the given trigger.
     */
    private String createCertification(Identity toCertify, List<Identity> certifiers,
                                     IdentityTrigger trigger)
        throws GeneralException {

        Message errorMessage = null;

        CertificationBuilderFactory factory =
            new CertificationBuilderFactory(this.context);

        CertificationBuilder builder =
            factory.getBuilder(trigger, toCertify, certifiers);

        builder.init();
        try{
            Certificationer c = new Certificationer(this.context);
            Iterator<CertificationContext> certCtxs = builder.getContexts();

            if (null != certCtxs) {
                while (certCtxs.hasNext()) {
                    CertificationContext certCtx = certCtxs.next();
                    Certification cert =
                        c.generateCertification(trigger.getOwner(), certCtx);

                    if (null != cert) {
                        c.start(cert);
                        String certId = cert.getId();
                        // The CertificationExecutor reconnects the context here.
                        // Since we're likely in the middle of an identity refresh
                        // or aggregation let's tread a little more lightly.  Just
                        // decache.  A full decache may be called for, but I don't
                        // want to cause the refresh to explode.
                        this.context.decache(cert);
                        return certId;
                    }
                }
            }
        } catch(Throwable t){
            // Catch any exception so we can store the exception message and an appropriate
            // status on the CertificationGroup object.
            errorMessage = new Message(Message.Type.Error , MessageKeys.ERR_EXCEPTION, t);

            // re-throw the exception to it can be handled by the caller
            if (t instanceof GeneralException)
                throw (GeneralException)t;
            else
                throw new GeneralException(t);
        }finally{
            List<Message> errors = new ArrayList<Message>();
            if (errorMessage != null)
                errors.add(errorMessage);
            builder.finalize(errors.isEmpty(), errors);
        }

        return null;
    }

    /**
     * Generate a description about what this handler is doing.
     */
    public String getEventDescription(IdentityChangeEvent event)
        throws GeneralException {

        return "Created certification for " + event.getIdentityFullName();
    }
}
