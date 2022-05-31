/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import sailpoint.integration.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.List;


/**
 * Created by ryan.pickens on 6/16/16.
 */
public class AlertDefinition extends SailPointObject {

    ActionConfig _actionConfig;

    AlertMatchConfig _matchConfig;

    String _displayName;


    //TODO: Supported Actions?
    @XMLClass
    public static enum ActionType {
        WORKFLOW("alert_action_type_workflow"),
        CERTIFICATION("alert_action_type_certification"),
        NOTIFICATION("alert_action_type_notification_only");

        ActionType(String s) {
            _messageKey = s;
        }

        String _messageKey;

        public String getMessageKey() { return _messageKey;}

    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public ActionConfig getActionConfig() { return _actionConfig; }

    public void setActionConfig(ActionConfig cnfg) { _actionConfig = cnfg; }


    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public AlertMatchConfig getMatchConfig() { return _matchConfig; }

    public void setMatchConfig(AlertMatchConfig cnfg) { _matchConfig = cnfg; }

    @XMLProperty
    public String getDisplayName() { return _displayName; }

    public void setDisplayName(String s) { _displayName = s; }
    
    /* 
     * IIQCB-2510 - LazyInitializationException when Alert Processing.
     * Load the matchConfig object rules as well as expressions during alert processing. Load
     * was being called in AlertAggregationTask.phaseProcess though it was using the default
     * no-op implementation in SailPointObject.
     */
    @Override
    public void load() {
        if (_matchConfig != null) _matchConfig.load();
    }

    //Object to store the Action Configuration
    @XMLClass
    public static class ActionConfig extends AbstractXmlObject {

        //Workflow Config
        //TODO: Should we have a config object to represent this? -rap
        public static final String ARG_WORKFLOW_NAME = "workflowName";
        public static final String ARG_WORKFLOW_ARGS = "workflowArgs";

        //Notification Config
        public static final String ARG_IDENTITY_EMAIL_RECIP = "identityEmailRecipient";
        public static final String ARG_EMAIL_TEMPLATE = "emailTemplate";
        public static final String ARG_EMAIL_VARS = "emailVariables";

        //Certification Config
        public static final String ARG_CERT_TRIGGER = "certificationTrigger";


        //Attributes to store information for the Action
        Attributes _attributes;

        //Type of Action
        ActionType _actionType;

        @XMLProperty
        public ActionType getActionType() {
            return _actionType;
        }

        public void setActionType(ActionType actionType) {
            this._actionType = actionType;
        }

        @XMLProperty(mode= SerializationMode.UNQUALIFIED)
        public Attributes getAttributes() {
            return _attributes;
        }

        public void setAttributes(Attributes atts) {
            this._attributes = atts;
        }

        public String getStringAttributeValue(String name) {
            return _attributes != null ? _attributes.getString(name) : null;
        }

        public List getStringListAttributeValue(String name) {
            return _attributes != null ? _attributes.getStringList(name) : null;
        }

        public String getWorkflowName() {
            return getStringAttributeValue(ARG_WORKFLOW_NAME);
        }

        /**
         *
         * @param s Name of the Workflow
         */
        public void setWorkflowName(String s) {
            if (_attributes == null) {
                _attributes = new Attributes<String, Object>();
            }
            _attributes.put(ARG_WORKFLOW_NAME, s);
        }

        public String getCertificationTrigger() {
            return getStringAttributeValue(ARG_CERT_TRIGGER);
        }

        /**
         *
         * @param s Name of the IdentityTrigger
         */
        public void setCertificationTrigger(String s) {
            if (_attributes == null) {
                _attributes = new Attributes<String, Object>();
            }
            _attributes.put(ARG_CERT_TRIGGER, s);
        }


        public String getEmailTemplate() {
            return getStringAttributeValue(ARG_EMAIL_TEMPLATE);
        }

        /**
         *
         * @param s Name of EmailTemplate
         */
        public void setEmailTemplate(String s) {
            if (_attributes == null) {
                _attributes = new Attributes<String, Object>();
            }
            _attributes.put(ARG_EMAIL_TEMPLATE, s);
        }


        public List<String> getEmailRecipients() {
            return getStringListAttributeValue(ARG_IDENTITY_EMAIL_RECIP);
        }

        /**
         *
         * @param recips List of Identity names
         */
        public void setEmailRecipients(List<String> recips) {
            if (_attributes == null) {
                _attributes = new Attributes<String, Object>();
            }
            _attributes.put(ARG_IDENTITY_EMAIL_RECIP, recips);
        }

    }


    /**
     * True if the ActionConfig contains emailTemplate and a list of idents to notify
     * @return True if the ActionConfig contains emailTemplate and a list of idents to notify
     */
    public boolean shouldNotify() {
        boolean notify = false;
        if (_actionConfig != null && !Util.isEmpty(_actionConfig.getAttributes())) {
            if (_actionConfig.getAttributes().get(ActionConfig.ARG_EMAIL_TEMPLATE) != null
                    && _actionConfig.getAttributes().get(ActionConfig.ARG_IDENTITY_EMAIL_RECIP) != null) {
                notify = true;
            }
        }
        return notify;
    }

}
