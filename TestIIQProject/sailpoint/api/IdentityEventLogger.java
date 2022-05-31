/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Source;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * A helper class that can log certain identity events and retrieve them.
 *
 * Note: role sunrise/sunset events are logged in the "Default Role Assignment
 * Workflow" and "Default Role Unassignment Workflow", and just use the audit
 * log directly.
 * 
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class IdentityEventLogger {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * A common representation for a historical event on an identity.
     */
    public static class IdentityEvent {
        
        private Date date;
        private String event;
        private String source;
        private String _sourceInterface;
        private String cause;
        private String summary;
        
        /**
         * A factory-like constructor that pulls the appropriate information
         * into this identity event based on the type of audit event.
         */
        public IdentityEvent(AuditEvent auditEvent) {

            this.date = auditEvent.getCreated();
            this.source = auditEvent.getSource();
            this._sourceInterface = auditEvent.getInterface();
            
            String action = auditEvent.getAction();
            if (AuditEvent.ActionIdentityTriggerEvent.equals(action)) {
                this.event = auditEvent.getString1();
                this.cause = auditEvent.getString4();
                this.summary = auditEvent.getString2();
                if (null != auditEvent.getString3()) {
                    this.summary =
                        (null != Util.getString(this.summary))
                            ? this.summary + " " + auditEvent.getString3()
                            : auditEvent.getString3();
                }
            }
            else if (AuditEvent.ActionRoleSunrise.equals(action)) {
                this.event = "Role sunrise";
                this.summary = auditEvent.getString1();
            }
            else if (AuditEvent.ActionRoleSunset.equals(action)) {
                this.event = "Role sunset";
                this.summary = auditEvent.getString1();
            }
            else {
                throw new RuntimeException("Unhandled event type");
            }
        }

        public Date getDate() {
            return this.date;
        }
        
        public String getEvent() {
            return this.event;
        }
        
        public String getSource() {
            return this.source;
        }
        
        public String getSourceInterface() {
            return this._sourceInterface;
        }

        /**
         * Bug:20012 Source Interface comes back as 'IIQ' we need to change it to 'IdentityIQ'
         * Decided to create a one-off here instead of changing the message key because this
         * probably impacts many other places and we may be exptecting 'IIQ' in other places.
         * This seems to be the safest alternative for now.
         */
        public String getSourceInterfaceForEventsPage() {
            if (Util.isNotNullOrEmpty(_sourceInterface)) {
                if (_sourceInterface.equals(ProvisioningPlan.APP_IIQ)) {
                    return new Message(MessageKeys.IDENTITYIQ).getLocalizedMessage();
                }
            }
            return _sourceInterface;
        }
        
        public String getCause() {
            return this.cause;
        }
        
        public String getSummary() {
            return this.summary;
        }
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructor.
     */
    public IdentityEventLogger(SailPointContext ctx) {
        this.context = ctx;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // IDENTITY TRIGGER EVENTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Log that the given IdentityChangeEvent occurred on the given identity.
     */
    public void logTriggerEvent(Source source, String who,
                                IdentityChangeEvent evt, Identity id)
        throws GeneralException{
        
        if (Auditor.isEnabled(AuditEvent.ActionIdentityTriggerEvent)) {

            // If this was a delete event, fake up an identity so we can create
            // the target correctly.
            if (null == id) {
                id = new Identity();
                id.setName(evt.getDeletedObjectName());
            }

            AuditEvent ae =
                new AuditEvent(who, AuditEvent.ActionIdentityTriggerEvent, id);
            if (null != source) {
                ae.setInterface(source.getLocalizedMessage());
            }

            IdentityTrigger trigger = evt.getTrigger();
            ae.setString1(trigger.getName());
            ae.setString2(trigger.getDescription());

            String handlerClass = trigger.getHandler();
            if (null != handlerClass) {
                IdentityTriggerHandler handler =
                    (IdentityTriggerHandler) Reflection.newInstance(handlerClass);
                handler.setContext(this.context);

                ae.setString3(handler.getEventDescription(evt));
            }
            
            ae.setString4(evt.getCause());

            Auditor.log(ae);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // QUERY IDENTITY EVENTS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Return all identity events for the given identity, sorted descending by
     * date.
     */
    public List<IdentityEvent> getIdentityEvents(Identity id)
        throws GeneralException {
        
        List<IdentityEvent> events = new ArrayList<IdentityEvent>();
        
        QueryOptions qo = new QueryOptions();

        // Allow looking up with a SailPointObject target (ie - Identity:<name>),
        // the name (used now by role sunrise/sunset), or just the ID (legacy
        // by role sunrise/sunset).
        List<String> targets = new ArrayList<String>();
        targets.add(new AuditEvent(null, null, id).getTarget());
        targets.add(id.getId());
        targets.add(id.getName());
        qo.add(Filter.in("target", targets));

        // Filter by actions that we are interested in.  Note that if you add
        // something here, you'll also need to add support in the IdentityEvent
        // constructor.
        List<String> actions = new ArrayList<String>();
        actions.add(AuditEvent.ActionIdentityTriggerEvent);
        actions.add(AuditEvent.ActionRoleSunrise);
        actions.add(AuditEvent.ActionRoleSunset);
        qo.add(Filter.in("action", actions));

        // Show the most recent first.
        qo.addOrdering("created", false);

        List<AuditEvent> audits = this.context.getObjects(AuditEvent.class, qo);
        if (null != audits) {
            for (AuditEvent audit : audits) {
                events.add(new IdentityEvent(audit));
            }
        }

        return events
    }
}
