/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object holding various options for the generation
 * and management of work items.  These may be referenced
 * by TaskDefinition, Policy or any other object with
 * an associated background task that may need to 
 * create work items.
 *
 * Author: Jeff
 * 
 * NOTE: This has a lot in common with NotificationConfig
 * should try to merge these, or maybe subclass that someday.
 *
 * For policies this evolved into a little more general
 * "alert config" by adding the concept of an optional
 * work item.  If you have a notificationTemplate set
 * but also have noWorkItem set a notificadtion email will
 * be sent but no corresponding work item will be opened.
 * In that case WorkItemConfig is a bit of a misnomer, but
 * that is the unusual case.
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class WorkItemConfig extends SailPointObject implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(WorkItemConfig.class);

    public final static String STYLE_NONE = "none";
    public final static String STYLE_REMINDER = "reminder";
    public final static String STYLE_ESCALATION = "escalation";
    public final static String STYLE_BOTH = "both";

    //
    // Defaults used when changing styles 
    //
    
    public static final int DEFAULT_MAX_REMINDERS = 3;
    public static final int DEFAULT_HOURS_BETWEEN_REMINDERS = 24;
    public static final int DEFAULT_HOURS_TILL_ESCALATION = 24 * 3;

    /**
     * Special transient property to keep track of the desired 
     * style of notification while the config is being edited in the UI.
     * The style helps the UI show only those fields that are necessary
     * and allows it to show clearer labels for the confusing
     * hoursTillEscalation and maxReminders properties. This must be persisted
     * on the HttpSession so it is an XML property but it is not
     * mapped into Hibernate.
     */
    String _escalationStyle;


    // TODO: Initial WorkItem.Level ?
    // TODO: Initial WorkItem.Type ?
    //
    // These feel like they can be set appropriately by whatever
    // is generating the work item.


    /**
     * These can inherit from other configs.
     * @ignore
     * !! Actually this doesn't work so well with integer fields.
     * Leave it in for now but we probably need to do "inheritance"
     * by copying.
     */
    WorkItemConfig _parent;

    /**
     * Default list of identities that are to be assigned work items.
     * 
     * @ignore
     * Note that there is some unfortunate ambiguity between this
     * and the _owner field that is inherited from SailPointObject.
     * We could consider _owner as the owner of the configuration itself
     * but not the work item owner, or it could just be an alternative
     * to specifying the work item owner without needing a list.
     * The first meaning is consistent with everything else but it then
     * means that code has to be careful not to set _owner when they
     * mean _owners.  If you only have one to set cal addOwner().
     */
    List<Identity> _owners;

    /**
     * Rule for generating the owner list. This will have
     * precedence over the _owners list if both are set.
     */
    Rule _ownerRule;

    /**
     * Email template used for the initial notification of the
     * work item assignment. If the notificationOnly option is
     * set (in subclasses like PolicyAlert), this will be the notification 
     * template.
     */
    EmailTemplate _notificationEmail;

    /**
     * When true, disables the generation of work items, only
     * notifications will be sent. This is usually off, but
     * some light weight custom policies might want notifications
     * without work item baggage.
     */
    boolean _noWorkItem;

    /**
     * Message template for rendering the description of the 
     * work item.
     */
    // could have a reference to a shared MessageTemplate object
    // but that seems like overkill
    String _descriptionTemplate;

    /**
     * Number of hours the workitem is allowed to live before
     * the reminder and escalation process begins. If this is not 
     * greater than zero, reminders and escalations will not be sent.  
     * If this is set and reminders or escalation are not configured it
     * effectively becomes the expiration time for the item.
     */
    int _hoursTillEscalation;

    /**
     * Number of hours between reminders or escalations.
     * If this is zero, the default interval will be taken
     * from the system configuration parameter defaultWakeUpInterval.
     * Note that the unit of the sysconfig parameter is milliseconds
     * where here we have hours.
     */
    int _hoursBetweenReminders;

    /**
     * The maximum number of reminder emails that will be sent before
     * the escalation process is begun. If this is zero, and 
     * _hoursTillEscalation is non-zero, there will be no reminders,
     * and escalation begins immediately.
     */
    int _maxReminders;

    /**
     * Email template used for reminder emails. If not specified, the one
     * specified in the global configuration is used. The input to the template
     * is the WorkItem.
     *
     * @ignore
     * TODO: We should move this out of SystemConfiguration and instead
     * have a global default WorkItemConfig that we fall back to.
     */
    EmailTemplate _reminderEmail;

    /**
     * Email template used when escalating a work item. If not specified, the
     * one specified in the global configuration is used. The input to the
     * template is the WorkItem.
     */
    EmailTemplate _escalationEmail;

    /**
     * Rule that calculates the new owner after the maximum number of reminders
     * has been exceeded. If null, or if the rule returns null, keep sending
     * reminders.
     */
    // TODO: If we decide to use a global config for this,
    // then we'll have a conflict between the meaning of
    // null as "use global config" and "remind forever"
    Rule _escalationRule;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public WorkItemConfig() {
    }

    public void load() {
        
        if (_owners != null) {
            // don't need the whole thing, just the name
            // ?? really
            for (Identity owner : _owners) {
                // for some strange reason we're seeing nulls in here
                // so be careful
                if (owner != null)
                    owner.getName();
            }
        }

        if (_ownerRule != null)
            _ownerRule.load();

        if (_escalationRule != null)
            _escalationRule.load();

        if (_notificationEmail != null)
            _notificationEmail.load();

        if (_reminderEmail != null)
            _reminderEmail.load();

        if (_escalationEmail != null)
            _escalationEmail.load();
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitWorkItemConfig(this);
    }

    public boolean isNameUnique() {
        return false;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setParent(WorkItemConfig config) {
        _parent = config;
    }

    public WorkItemConfig getParent() {
        return _parent;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public void setOwners(List<Identity> owners) {
        _owners = owners;
    }

    public List<Identity> getOwners() {
        return _owners;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setOwnerRule(Rule rule) {
        _ownerRule = rule;
    }

    public Rule getOwnerRule() {
        return _ownerRule;
    }

    @XMLProperty
    public void setNoWorkItem(boolean b) {
        _noWorkItem = b;
    }

    public boolean isNoWorkItem() {
        return _noWorkItem;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public void setDescriptionTemplate(String s) {
        _descriptionTemplate = s;
    }

    public String getDescriptionTemplate() {
        return _descriptionTemplate;
    }

    @XMLProperty
    public void setHoursTillEscalation(int i) {
        _hoursTillEscalation = i;
    }

    public int getHoursTillEscalation() {
        return _hoursTillEscalation;
    }

    @XMLProperty
    public void setHoursBetweenReminders(int i) {
        _hoursBetweenReminders = i;
    }

    public int getHoursBetweenReminders() {
        return _hoursBetweenReminders;
    }

    @XMLProperty
    public void setMaxReminders(int i) {
        _maxReminders = i;
    }

    public int getMaxReminders() {
        return _maxReminders;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname = "NotificationEmailTemplateRef")
    public void setNotificationEmail(EmailTemplate et) {
        _notificationEmail = et;
    }

    public EmailTemplate getNotificationEmail() {
        return _notificationEmail;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname = "ReminderEmailTemplateRef")
    public void setReminderEmail(EmailTemplate et) {
        _reminderEmail = et;
    }

    public EmailTemplate getReminderEmail() {
        return _reminderEmail;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname = "EscalationEmailTemplateRef")
    public void setEscalationEmail(EmailTemplate et) {
        _escalationEmail = et;
    }

    public EmailTemplate getEscalationEmail() {
        return _escalationEmail;
    }

    @XMLProperty(mode = SerializationMode.REFERENCE, xmlname = "EscalationRuleRef")
    public void setEscalationRule(Rule r) {
        _escalationRule = r;
    }

    public Rule getEscalationRule() {
        return _escalationRule;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // JSF Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    public void addOwner(Identity id) {
        if (id != null) {
            if (_owners == null)
                _owners = new ArrayList<Identity>();
            _owners.add(id);
        }
    }

    /**
     * Return true if there is nothing interesting in this
     * config.  
     * @ignore
     * A kludge to avoid creating unnecessary
     * objects since we have to bootstrap one of these for the UI.
     * Ugh, I hate this, but until these can live on their own we
     * have no way to ask that they be deleted in the UI.
     */
    public boolean isEmpty() {

        return (_parent == null &&
                (_owners == null || _owners.size() == 0) &&
                _ownerRule == null &&
                _descriptionTemplate == null &&
                _hoursTillEscalation == 0 &&
                _hoursBetweenReminders == 0 &&
                _maxReminders == 0 &&
                _reminderEmail == null &&
                _notificationEmail == null &&
                _escalationEmail == null &&
                _escalationRule == null);
    }
    

    /**
     * Special transient property to keep track of the desired 
     * style of notification while the config is being edited in the UI.
     * This is initially derived by looking at the fields that were
     * persisted, thereafter it can be changed in the UI and will not
     * be recalculated. This is necessary because some of the style
     * decisions are dependent on references to objects that might not
     * be set yet.
     *
     * It is an XMLProperty so that it can be persisted in the HttpSession
     * during editing, but is not stored in Hibernate.
     */
    @XMLProperty
    public String getEscalationStyle() {
        if (_escalationStyle == null) {
            _escalationStyle = STYLE_NONE;
            if (_hoursTillEscalation > 0) {
                if (_escalationRule != null) {
                    if (_maxReminders == 0)
                        _escalationStyle = STYLE_ESCALATION;
                    else
                        _escalationStyle = STYLE_BOTH;
                }
                else 
                    _escalationStyle = STYLE_REMINDER;
            }
        }
        return _escalationStyle;
    }
    
    /** 
     * Modify the escalation style in the UI.
     */
    public void setEscalationStyle(String style) {

        _escalationStyle = style;
    }

    /**
     * Default fields according to a newly selected style.
     * 
     * @ignore
     * This isn't actually used because styles are selected on
     * the client, the server doesn't get a style change until the
     * form is posted.  Then we don't want to do any defaulting because
     * we want them to enter a correct value.  Not sure where
     * we could hook a call to this, maybe when it is first created
     * but then it defaults to NONE anyway.
     */
    public void defaultStyleProperties() {
        
        if (STYLE_REMINDER.equals(_escalationStyle) || 
            STYLE_BOTH.equals(_escalationStyle)) {

            if (_maxReminders <= 0)
                _maxReminders = DEFAULT_MAX_REMINDERS;
            
            // !! we have a SystemConfig default for this, should
            // we let this stay zero?
            if (_hoursBetweenReminders <= 0)
                _hoursBetweenReminders = DEFAULT_HOURS_BETWEEN_REMINDERS;
        }

        if (STYLE_ESCALATION.equals(_escalationStyle) || 
            STYLE_BOTH.equals(_escalationStyle)) {
                
            if (_hoursTillEscalation <= 0)
                _hoursTillEscalation = DEFAULT_HOURS_TILL_ESCALATION;
        }
    }

    /**
     * Called by the UI when changes are ready to be save and 
     * perform validation relevant to the selected style.
     * Clear fields that are not relevant to the selected style.
     * 
     * @ignore
     * Without this we would revert to one of the escalation styles
     * the next time you edited the object rather than leaving it
     * at the last selected style.  This needs to be consistent
     * with the style rules in getEscalationStyle().
     * 
     * Leaving unused references to EmailTemplates is less severe but 
     * we could remove them to reduce clutter.  I'm going to leave them
     * for now so we can preserve old selections if they change their mind.
     */
    public void cleanupStyleProperties() {

        if (_escalationStyle == null || STYLE_NONE.equals(_escalationStyle)) {
            _hoursTillEscalation = 0;
            _escalationRule = null;
            _maxReminders = 0;
        }
        else if (STYLE_REMINDER.equals(_escalationStyle)) {
            _escalationRule = null;
        }
        else if (STYLE_ESCALATION.equals(_escalationStyle)) {
            _maxReminders = 0;
        }
        else if (STYLE_BOTH.equals(_escalationStyle)) {
            // leave them all behind
        }
    }


}
