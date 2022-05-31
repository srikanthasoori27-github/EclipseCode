/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.CertificationPhaser;
import sailpoint.api.EmailSuppressor;
import sailpoint.api.Emailer;
import sailpoint.api.MessageRepository;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.CertificationPhaser.PhaseHandler;
import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.Phaseable;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;


/**
 * Base implementation of the PhaseHandler interface.  This overrides the
 * phasing methods to dispatch operations to the correct subclass method
 * depending on whether the Phaseable is a Certification or CertificationItem.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public abstract class BasePhaseHandler implements PhaseHandler {

    private static final Log log = LogFactory.getLog(BasePhaseHandler.class);
    private static final String AUTO_DECIDER_IDENTITY = "AIServices";

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    SailPointContext context;
    MessageRepository errorHandler;
    EmailSuppressor emailSuppressor;
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     * 
     * @param  context          The SailPointContext to use.
     * @param  errorHandler     The ErrorHandler to use.
     * @param  emailSuppressor  The possibly null EmailSuppressor to use.
     */
    public BasePhaseHandler(SailPointContext context,
                            MessageRepository errorHandler,
                            EmailSuppressor emailSuppressor) {
        this.context = context;
        this.errorHandler = errorHandler;
        this.emailSuppressor = emailSuppressor;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS TO OVERRIDE
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    Certification enterPhase(Certification cert) throws GeneralException {
        // No-op.
        return cert;
    }

    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    CertificationItem enterPhase(CertificationItem item) throws GeneralException {
        // No-op.
        return item;
    }

    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    Certification exitPhase(Certification cert) throws GeneralException {
        // No-op.
        return cert;
    }

    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    CertificationItem exitPhase(CertificationItem item) throws GeneralException {
        // No-op.
        return item;
    }
    
    Rule getEnterRule(Certification cert) throws GeneralException {
        return null;
    }

    Rule getExitRule(Certification cert) throws GeneralException {
        return null;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // PHASE HANDLER IMPLEMENTATION
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Dispatch to the certification and item handling methods.
     */
    public Phaseable enterPhase(Phaseable phaseable) throws GeneralException {
        if (log.isInfoEnabled()) 
            log.info("Class: " + getClass().getSimpleName() + ", enterPhase");
        
        if (phaseable instanceof Certification) {
            Certification cert = (Certification) phaseable;
            Rule rule = getEnterRule(cert);
            if (rule != null) {
               if (log.isInfoEnabled()) 
                   log.info("Cert: " + cert.getName() + ", running Enter Phase rule: " + rule.getName());
               
               runRule(rule, cert);
               // Since the rule has the potential to decache the certification,
               // we want to reattach here.  See IIQETN-1840
               cert = ObjectUtil.reattach(context, cert);
            }
            return enterPhase(cert);
        }
        else if (phaseable instanceof CertificationItem) {
            CertificationItem certItem = (CertificationItem) phaseable;
            Rule rule = getEnterRule(certItem.getCertification());
            if (rule != null) {
               if (log.isInfoEnabled()) 
                   log.info("CertItem: " + certItem.getCertification().getName() + 
                            ", running Enter Phase rule: " + rule.getName());
               
               runRule(rule, certItem);
            }
            return enterPhase(certItem);
        }
        else {
            throw new GeneralException("Unhandled phaseable: " + phaseable);
        }
    }

    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    public Certification postEnter(Certification cert) throws GeneralException {
        // No-op.
        return cert;
    }
    
    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    public Certification postExit(Certification cert) throws GeneralException {
        // No-op.
        return cert;
    }

    /**
     * Dispatch to the certification and item handling methods.
     */
    public Phaseable exitPhase(Phaseable phaseable) throws GeneralException {
        if (log.isInfoEnabled()) 
            log.info("Class: " + getClass().getSimpleName() + ", exitPhase");
        
        if (phaseable instanceof Certification) {
            Certification cert = (Certification) phaseable;
            Rule rule = getExitRule(cert);
            if (rule != null) {
               if (log.isInfoEnabled()) 
                   log.info("Cert: " + cert.getName() + ", running Exit Phase rule: " + rule.getName());
               
               runRule(rule, cert);
            }
            return exitPhase(cert);
        }
        else if (phaseable instanceof CertificationItem) {
            CertificationItem certItem = (CertificationItem) phaseable;
            Rule rule = getExitRule(certItem.getCertification());
            if (rule != null) {
               if (log.isInfoEnabled()) 
                   log.info("CertItem: " + certItem.getCertification().getName() + 
                            ", running Exit Phase rule: " + rule.getName());
               
               runRule(rule, certItem);
            }
            return exitPhase(certItem);
        }
        else {
            throw new GeneralException("Unhandled phaseable: " + phaseable);
        }
    }

    /**
     * Default to false here since we hardly ever skip phases.
     */
    public boolean isSkipped(Phaseable phaseable) throws GeneralException {
    
        return false;
    }

    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    public void refresh(Certification cert, CertificationItem item)
        throws GeneralException {
        // No-op.
    }

    /**
     * BasePhaseHandler does nothing by default.  Extend to add behavior.
     */
    public void handleRollingPhaseTransition(CertificationItem item,
                                             CertificationPhaser phaser)
        throws GeneralException {
        // No-op.
    }

    /**
     * BasePhaseHandler returns true by default since we almost always want
     * a phase's duration to be added to the next phase transition.  Override
     * as necessary.
     */
    public boolean updateNextPhaseTransition(Phaseable phaseable) {
        return true;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // UTILITIES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Send the given email with the configured EmailNotifier.
     */
    void sendEmail(EmailTemplate template, EmailOptions options)
        throws GeneralException {

        if ((null == this.emailSuppressor) ||
            this.emailSuppressor.shouldSend(template, options.getTo())) {

            new Emailer(this.context, this.errorHandler).sendEmailNotification(template, options);
        }
    }
    
    Rule getRule(String configAttrName, Certification cert)
        throws GeneralException {
        
        Rule rule = null;

        // Check the cert for the rule name, pre-5.2 certs will store the rules on
        // the attributes map
        String ruleName =
            cert.getAttributes() != null ? cert.getAttributes().getString(configAttrName) : null;
        // Silliness for checking against typo version of CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE attribute
        if( ruleName == null && configAttrName.equals( Configuration.CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE ) ) {
            if( cert.getAttributes() != null ) {
                ruleName =  cert.getAttributes().getString( Configuration.CERTIFICATION_CHALLENGE_PHASE_ENTER_RULE_LEGACY );
            }
        }

        // certifications created in 5.2+ will store the rules in the definition
        if (ruleName == null){
            CertificationDefinition def = cert.getCertificationDefinition(context);
            if (def != null && def.getAttributes() != null)
                ruleName = def.getAttributes().getString(configAttrName);
        }
        
        if (null != ruleName) {
            rule = this.context.getObjectByName(Rule.class, ruleName);
            if (null != rule) {
                rule.load();
            }
        }
        return rule;
    }
    
    @SuppressWarnings("unchecked")
    Map<String, Object> runRule(Rule rule, Certification cert) throws GeneralException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("certification", cert);
        updatePhaseInfo(params, cert);
        return (Map<String, Object>)this.context.runRule(rule, params);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> runRule(Rule rule, CertificationItem certItem) throws GeneralException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("certification", certItem.getCertification());
        params.put("certificationItem", certItem);
        updatePhaseInfo(params, certItem);
        return (Map<String, Object>)this.context.runRule(rule, params);
    }
    
    private void updatePhaseInfo(Map<String, Object> params, Phaseable phaseable) 
        throws GeneralException {
        
        MessageRepository errorHandler = new BasicMessageRepository();
        CertificationPhaser phaser = new CertificationPhaser(this.context, errorHandler, emailSuppressor);
        params.put("previousPhase", phaser.getPreviousPhase(phaseable));
        params.put("nextPhase", phaser.getNextPhase(phaseable));
    }

    protected Certification doAutoDecisions(Certification cert) throws GeneralException {
        CertificationDefinition def = cert.getCertificationDefinition(this.context);

        // If auto decisions are enabled, send the cert to the CertificationAutoDecisioner
        // to preload cert with decisions based on the recommendation.
        // TODO: If more auto-decision types are added, we should add a method in CertificationDefinition
        //  that checks all auto-decision flags in one call.
        if (def != null && def.getShowRecommendations() && def.isAutoApprove()) {
            Identity decider = this.context.getObjectByName(Identity.class, AUTO_DECIDER_IDENTITY);

            if (decider != null) {
                CertificationAutoDecisioner autoDecisioner =
                        new CertificationAutoDecisioner(this.context, cert, def, decider);
                autoDecisioner.autoDecide();

                // Since CertificationDecisioner made changes to the cert items,
                // reattach to this session.
                return ObjectUtil.reattach(this.context, cert);
            } else {
                log.error(String.format("Unable to make decisions automatically due to missing identity or workgroup: %s",
                        AUTO_DECIDER_IDENTITY));
            }
        }

        return cert;
    }
}
